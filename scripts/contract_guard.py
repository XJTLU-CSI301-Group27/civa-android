#!/usr/bin/env python
"""Authorize OpenAPI changes against accepted interface-change records (IC).

This adds the authorization gate that contract_diff.py deliberately omits:
every changed operation/schema must be covered by an ACCEPTED IC record's
allowedOperations/allowedSchemas/allowedSymbols. A `draft` command generates a
DETECTED record from two files. It does not yet verify the IC's contractDigest
or enumerate routing/dependency violations; those remain separate checks
(see PROJECT_DOC.md section 14.6).
"""

import argparse
import datetime
import hashlib
import json
import subprocess
import sys
from pathlib import Path

import yaml

from contract_diff import load, loads, _operations, _components, _changes

AUTHORIZED_STATUSES = frozenset(('ACCEPTED', 'IMPLEMENTING', 'VERIFIED', 'CLOSED'))
METADATA_FIELDS = frozenset((
    'id', 'type', 'status', 'allowedOperations', 'allowedSchemas', 'allowedSymbols',
    'contractDigest', 'baseSha', 'headSha',
))


class UniqueKeyLoader(yaml.SafeLoader):
    pass


def _construct_mapping(loader, node, deep=False):
    mapping = {}
    for key_node, value_node in node.value:
        key = loader.construct_object(key_node, deep=deep)
        if key in mapping:
            raise ValueError(f'duplicate YAML key: {key}')
        mapping[key] = loader.construct_object(value_node, deep=deep)
    return mapping


UniqueKeyLoader.add_constructor(yaml.resolver.BaseResolver.DEFAULT_MAPPING_TAG, _construct_mapping)


def load_ics(directory):
    """Parse each IC record's YAML front matter; reject duplicates and unknown fields."""
    records = []
    root = Path(directory)
    if not root.is_dir():
        return records
    for path in sorted(root.glob('IC-*.md')):
        records.append(_validate_ic(_front_matter(path.read_text(encoding='utf-8'), path), path))
    return records


def _front_matter(text, path):
    if not text.startswith('---\n'):
        raise ValueError(f'{path}: missing YAML front matter')
    end = text.find('\n---', 4)
    if end == -1:
        raise ValueError(f'{path}: unterminated front matter')
    data = yaml.load(text[4:end], Loader=UniqueKeyLoader)
    if data is None:
        return {}
    if not isinstance(data, dict):
        raise ValueError(f'{path}: front matter must be a mapping')
    return data


def _validate_ic(front, path):
    unknown = set(front) - METADATA_FIELDS
    if unknown:
        raise ValueError(f'{path}: unknown metadata field(s): {", ".join(sorted(unknown))}')
    ic_id = front.get('id')
    if not isinstance(ic_id, str) or not ic_id:
        raise ValueError(f'{path}: id is required')
    digest = front.get('contractDigest')
    if digest is not None and (not isinstance(digest, str) or not digest.startswith('sha256:')):
        raise ValueError(f'{path}: contractDigest must be null or sha256:<64hex>')
    return {
        'id': ic_id,
        'status': front.get('status'),
        'allowedOperations': _string_list(front.get('allowedOperations'), path, 'allowedOperations'),
        'allowedSchemas': _string_list(front.get('allowedSchemas'), path, 'allowedSchemas'),
        'allowedSymbols': _string_list(front.get('allowedSymbols'), path, 'allowedSymbols'),
        'contractDigest': digest,
    }


def _string_list(value, path, field):
    if value is None:
        return []
    if not isinstance(value, list) or not all(isinstance(item, str) for item in value):
        raise ValueError(f'{path}: {field} must be a list of strings')
    return value


def authorized_names(ics):
    operations, schemas, symbols = set(), set(), set()
    for ic in ics:
        if ic['status'] in AUTHORIZED_STATUSES:
            operations.update(ic['allowedOperations'])
            schemas.update(ic['allowedSchemas'])
            symbols.update(ic['allowedSymbols'])
    return operations, schemas, symbols


def _op_digests(document):
    result = {}
    for entry in _operations(document, strip=True).values():
        result[entry['operationId']] = entry['digest']
    return result


def contract_digest(base, head, allowed_operations, allowed_schemas):
    """sha256:<hex> over the before/after fragments of the allowed operations and schemas."""
    base_ops, head_ops = _op_digests(base), _op_digests(head)
    base_comps, head_comps = _components(base, strip=True), _components(head, strip=True)
    fragments = {}
    for operation in sorted(allowed_operations):
        fragments['operation:' + operation] = [base_ops.get(operation), head_ops.get(operation)]
    for schema in sorted(allowed_schemas):
        fragments['schema:' + schema] = [base_comps.get('schemas/' + schema), head_comps.get('schemas/' + schema)]
    canonical = json.dumps(fragments, sort_keys=True, ensure_ascii=False, separators=(',', ':'))
    return 'sha256:' + hashlib.sha256(canonical.encode('utf-8')).hexdigest()


def changes(base, head):
    """Diff two OpenAPI documents into operation and component change rows."""
    rows = _changes(_operations(base), _operations(head), 'operation')
    rows += _changes(_components(base), _components(head), 'component')
    return rows


def load_ref(ref, path='contracts/openapi.json'):
    """Load the contract from a file path or a Git ref (SHA/branch/tag)."""
    if Path(ref).is_file():
        return load(ref)
    result = subprocess.run(['git', 'show', f'{ref}:{path}'], capture_output=True, text=True)
    if result.returncode != 0:
        raise ValueError(f'cannot read {path} at ref {ref}: {result.stderr.strip()}')
    return loads(result.stdout)


def authorize(base, head, ics):
    rows = changes(base, head)
    operations, schemas, symbols = authorized_names(ics)
    violations = []
    for change in rows:
        if change['type'] == 'operation':
            entry = change['before'] if change['before'] is not None else change['after']
            authorized = entry['operationId'] in operations
        else:
            kind, _, short = change['name'].partition('/')
            authorized = (short in schemas) if kind == 'schemas' else (short in symbols)
        if not authorized:
            violations.append(change)
    digest_violations = []
    for ic in ics:
        if ic['status'] in AUTHORIZED_STATUSES and ic['contractDigest'] is not None:
            actual = contract_digest(base, head, ic['allowedOperations'], ic['allowedSchemas'])
            if actual != ic['contractDigest']:
                digest_violations.append({'id': ic['id'], 'expected': ic['contractDigest'], 'actual': actual})
    return {
        'status': 'FAIL' if (violations or digest_violations) else 'PASS',
        'authorizedChanges': len(rows) - len(violations),
        'unauthorizedChanges': len(violations),
        'changes': rows,
        'violations': violations,
        'digestViolations': digest_violations,
        'scope': 'OpenAPI change authorization and digest verification against accepted IC records; routing and dependency checks not yet implemented',
    }


def draft_document(base, head, base_ref, head_ref):
    """Build a DETECTED IC draft from the diff; never overwrites or approves."""
    rows = changes(base, head)
    operations = sorted({c['before']['operationId'] if c['before'] else c['after']['operationId']
                         for c in rows if c['type'] == 'operation'})
    schemas = sorted({c['name'].partition('/')[2] for c in rows
                      if c['type'] == 'component' and c['name'].startswith('schemas/')})
    others = sorted({c['name'] for c in rows
                     if c['type'] == 'component' and not c['name'].startswith('schemas/')})
    ic_id = 'IC-' + datetime.datetime.now().strftime('%Y%m%d%H%M%S') + '-UNREGISTERED'
    summary = '; '.join(operations + schemas + others) or '—'
    return (
        '---\n'
        f'id: {ic_id}\n'
        'type: UNREGISTERED\n'
        'status: DETECTED\n'
        f'allowedOperations: {json.dumps(operations)}\n'
        f'allowedSchemas: {json.dumps(schemas)}\n'
        'allowedSymbols: []\n'
        'contractDigest: null\n'
        f'baseSha: {base_ref}\n'
        f'headSha: {head_ref}\n'
        '---\n'
        f'# {ic_id} 未登记接口变更草稿\n'
        '类型：UNREGISTERED\n'
        '状态：DETECTED\n'
        '提出组及负责人：\n'
        f'发现时间（带时区）：{datetime.datetime.now().astimezone().isoformat()}\n'
        '关联Issue/PR：\n'
        f'基线commit / 原始head commit：{base_ref} / {head_ref}\n'
        '契约版本（前 / 后）：\n'
        '涉及operationId / schema / Java完整签名：\n'
        f'{summary}\n'
        '原需求及复用现有接口的评估：\n'
        '旧接口与新接口（包含单位、权限、错误与副作用）：\n'
        '影响调用方、数据库、缓存及UI：\n'
        '兼容性判断及未验证项：\n'
        '原始证据位置与摘要（脱敏）：\n'
        f'{summary}\n'
        '决定：采用 / 复用 / 适配 / 撤回 / 内部化\n'
        '决定者、时间、授权依据和对应commit：\n'
        '迁移与回退计划：\n'
        '实际命令、测试报告、失败与修复记录：\n'
        '最终commit / 正式契约位置：\n'
        '临时adapter负责人、删除条件及任务：\n'
        '关闭依据：\n'
    )


def _check(args):
    try:
        if Path(args.output).resolve() in (Path(args.base).resolve(), Path(args.head).resolve()):
            raise ValueError('output must differ from both input files')
        report = authorize(load_ref(args.base), load_ref(args.head), load_ics(args.changes))
        Path(args.output).write_text(json.dumps(report, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    except (ValueError, OSError, json.JSONDecodeError, yaml.YAMLError) as exc:
        print(f'contract guard failed: {exc}', file=sys.stderr)
        return 2
    print(f"{report['status']}; {report['authorizedChanges']} authorized, {report['unauthorizedChanges']} unauthorized, "
          f"{len(report['digestViolations'])} digest violations")
    return 1 if report['status'] == 'FAIL' else 0


def _draft(args):
    try:
        base = load_ref(args.base)
        head = load_ref(args.head)
        out = Path(args.out)
        out.mkdir(parents=True, exist_ok=True)
        if any(out.glob('IC-*.md')):
            raise ValueError(f'{out} already contains IC records; draft would conflict')
        name = 'IC-' + datetime.datetime.now().strftime('%Y%m%d%H%M%S') + '-UNREGISTERED.md'
        text = draft_document(base, head, args.base, args.head)
        (out / name).write_text(text, encoding='utf-8', newline='\n')
    except (ValueError, OSError, json.JSONDecodeError, yaml.YAMLError) as exc:
        print(f'contract guard draft failed: {exc}', file=sys.stderr)
        return 2
    print(f'wrote {out / name}')
    return 0


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    sub = parser.add_subparsers(dest='command', required=True)

    check = sub.add_parser('check', help='authorize base/head changes against accepted ICs')
    check.add_argument('--base', required=True, help='baseline OpenAPI JSON file')
    check.add_argument('--head', required=True, help='candidate OpenAPI JSON file')
    check.add_argument('--changes', default='contracts/changes', help='IC records directory')
    check.add_argument('--output', required=True, help='output report path')
    check.set_defaults(func=_check)

    draft = sub.add_parser('draft', help='generate a DETECTED draft from base/head files')
    draft.add_argument('--base', required=True, help='baseline OpenAPI JSON file')
    draft.add_argument('--head', required=True, help='candidate OpenAPI JSON file')
    draft.add_argument('--out', required=True, help='output directory for the draft record')
    draft.set_defaults(func=_draft)

    args = parser.parse_args()
    return args.func(args)


if __name__ == '__main__':
    sys.exit(main())
