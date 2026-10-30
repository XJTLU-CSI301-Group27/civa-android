#!/usr/bin/env python
"""Report OpenAPI changes in two files. This is not an authorization gate."""

import argparse
import hashlib
import json
import sys
from pathlib import Path
from urllib.parse import unquote

METHODS = frozenset(('get', 'put', 'post', 'delete', 'options', 'head', 'patch', 'trace'))


def _object(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            raise ValueError(f'duplicate JSON key: {key}')
        result[key] = value
    return result


def load(path):
    return loads(Path(path).read_text(encoding='utf-8'))


def loads(text):
    def reject_constant(value):
        raise ValueError(f'invalid JSON constant: {value}')
    document = json.loads(text, object_pairs_hook=_object, parse_constant=reject_constant)
    if not isinstance(document, dict) or document.get('openapi') != '3.0.3' or not isinstance(document.get('paths'), dict):
        raise ValueError('expected an OpenAPI 3.0.3 object with paths')
    if not isinstance(document.get('components', {}), dict):
        raise ValueError('components must be an object')
    return document


def _fingerprint(value, strip=False):
    if strip:
        value = _strip_descriptive(value)
    data = json.dumps(value, sort_keys=True, ensure_ascii=False, separators=(',', ':'), allow_nan=False).encode('utf-8')
    return hashlib.sha256(data).hexdigest()


DESCRIPTIVE_KEYS = frozenset(('description', 'summary', 'example', 'title'))


def _strip_descriptive(node):
    """Drop pure documentation keys so doc-only edits do not change a behavioral digest."""
    if isinstance(node, dict):
        return {key: _strip_descriptive(value) for key, value in node.items() if key not in DESCRIPTIVE_KEYS}
    if isinstance(node, list):
        return [_strip_descriptive(value) for value in node]
    return node


def _resolve(document, reference):
    if not isinstance(reference, str) or not reference.startswith('#/'):
        raise ValueError('external or invalid reference is not supported')
    current = document
    for part in unquote(reference[2:]).split('/'):
        token = part.replace('~1', '/').replace('~0', '~')
        if isinstance(current, dict) and token in current:
            current = current[token]
        elif isinstance(current, list) and token.isdigit() and int(token) < len(current):
            current = current[int(token)]
        else:
            raise ValueError(f'unresolved reference: {reference}')
    return current


def _references(document, value):
    found = {}

    def visit(node):
        if isinstance(node, dict):
            if '$ref' in node:
                reference = node['$ref']
                if not isinstance(reference, str):
                    raise ValueError('reference must be a string')
                if reference not in found:
                    target = _resolve(document, reference)
                    found[reference] = target
                    visit(target)
            for key, child in node.items():
                if key != '$ref':
                    visit(child)
        elif isinstance(node, list):
            for child in node:
                visit(child)

    visit(value)
    return found


def _operations(document, strip=False):
    operations, ids = {}, set()
    for path, item in document['paths'].items():
        if not isinstance(path, str) or not path.startswith('/') or not isinstance(item, dict) or '$ref' in item:
            raise ValueError('unsupported path item')
        shared = {key: value for key, value in item.items() if key not in METHODS}
        for method in METHODS:
            if method not in item:
                continue
            operation = item[method]
            if not isinstance(operation, dict) or not isinstance(operation.get('operationId'), str) or not operation['operationId']:
                raise ValueError(f'missing operationId: {method.upper()} {path}')
            operation_id = operation['operationId']
            if operation_id in ids:
                raise ValueError(f'duplicate operationId: {operation_id}')
            ids.add(operation_id)
            security = operation.get('security', document.get('security', []))
            if not isinstance(security, list):
                raise ValueError(f'invalid security: {operation_id}')
            schemes = document.get('components', {}).get('securitySchemes', {})
            if not isinstance(schemes, dict):
                raise ValueError('securitySchemes must be an object')
            used_schemes = {}
            for requirement in security:
                if not isinstance(requirement, dict):
                    raise ValueError(f'invalid security requirement: {operation_id}')
                for name in requirement:
                    if name not in schemes:
                        raise ValueError(f'undefined security scheme: {name}')
                    used_schemes[name] = schemes[name]
            payload = {'path': path, 'method': method, 'shared': shared, 'operation': operation,
                       'effectiveSecurity': security, 'securitySchemes': used_schemes}
            payload['references'] = _references(document, payload)
            operations[f'{method.upper()} {path}'] = {'operationId': operation_id, 'digest': _fingerprint(payload, strip)}
    return operations


def _components(document, strip=False):
    result = {}
    for kind, entries in document.get('components', {}).items():
        if not isinstance(entries, dict):
            raise ValueError(f'component group must be an object: {kind}')
        for name, value in entries.items():
            result[f'{kind}/{name}'] = _fingerprint({'value': value, 'references': _references(document, value)}, strip)
    return result


def _changes(before, after, item_type):
    changes = []
    for name in sorted(before.keys() | after.keys()):
        old, new = before.get(name), after.get(name)
        if old == new:
            continue
        changes.append({'type': item_type, 'name': name,
                        'change': 'ADDED' if old is None else 'REMOVED' if new is None else 'MODIFIED',
                        'before': old, 'after': new})
    return changes


def compare(base, head):
    changes = _changes(_operations(base), _operations(head), 'operation')
    changes += _changes(_components(base), _components(head), 'component')
    return {'status': 'CHANGES_DETECTED' if changes else 'NO_CHANGES', 'authorization': 'UNVERIFIED',
            'scope': 'OpenAPI file comparison only; not an IC, public-surface, routing, dependency or CI gate',
            'changes': changes}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--base', required=True, help='baseline OpenAPI JSON file')
    parser.add_argument('--head', required=True, help='candidate OpenAPI JSON file')
    parser.add_argument('--output', required=True, help='output report path')
    args = parser.parse_args()
    try:
        if Path(args.output).resolve() in (Path(args.base).resolve(), Path(args.head).resolve()):
            raise ValueError('output must differ from both input files')
        report = compare(load(args.base), load(args.head))
        Path(args.output).write_text(json.dumps(report, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    except (ValueError, OSError, json.JSONDecodeError) as exc:
        print(f'contract comparison failed: {exc}', file=sys.stderr)
        return 2
    print(f"{report['status']}; authorization {report['authorization']}; {len(report['changes'])} changes")
    return 1 if report['changes'] else 0


if __name__ == '__main__':
    sys.exit(main())
