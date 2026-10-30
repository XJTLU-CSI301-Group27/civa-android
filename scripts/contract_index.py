#!/usr/bin/env python
"""Generate a review index from the candidate OpenAPI draft, or check it is current."""

import argparse
import sys
from pathlib import Path

from contract_diff import METHODS, _operations, load


def schema_name(schema):
    if '$ref' in schema:
        return schema['$ref'].rsplit('/', 1)[-1]
    if schema.get('type') == 'array':
        return schema_name(schema['items']) + '[]'
    if schema.get('format') == 'binary':
        return 'binary'
    if schema.get('type') == 'object':
        return 'object'
    if schema.get('type'):
        return schema['type']
    raise ValueError('unsupported schema in index')


def content_name(content):
    return ', '.join(f'{media}: {schema_name(value["schema"])}'
                     for media, value in sorted(content.items())) if content else '—'


def render(document):
    # Reuse the same duplicate operation and reference checks as the candidate diff.
    _operations(document)
    rows = []
    for path, item in document['paths'].items():
        for method in sorted(METHODS):
            if method not in item:
                continue
            operation = item[method]
            owner = operation.get('x-owner')
            if not isinstance(owner, str) or not owner:
                raise ValueError(f'missing x-owner: {operation["operationId"]}')
            request = content_name(operation.get('requestBody', {}).get('content', {}))
            responses = []
            for status, response in sorted(operation['responses'].items()):
                if status.startswith('2'):
                    responses.append(f'{status} {content_name(response.get("content", {}))}')
            if not responses:
                raise ValueError(f'missing success response: {operation["operationId"]}')
            cells = (operation['operationId'], method.upper(), path, owner, request, '; '.join(responses))
            rows.append('| ' + ' | '.join(str(cell).replace('|', '\\|').replace('\n', ' ') for cell in cells) + ' |')
    return ('# OpenAPI 接口索引（候选草稿）\n\n'
            '由 `contracts/openapi.json` 生成；不代表契约已批准。\n\n'
            '| operationId | 方法 | 路径 | 负责人 | 请求体 | 成功响应 |\n'
            '| --- | --- | --- | --- | --- | --- |\n' + '\n'.join(sorted(rows)) + '\n')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('contract', type=Path)
    parser.add_argument('output', type=Path)
    parser.add_argument('--check', action='store_true')
    args = parser.parse_args()
    try:
        if args.contract.name != 'openapi.json':
            raise ValueError('expected the candidate openapi.json')
        if args.contract.resolve() == args.output.resolve():
            raise ValueError('output cannot overwrite contract')
        text = render(load(args.contract))
        if args.check:
            if not args.output.is_file() or args.output.read_text(encoding='utf-8') != text:
                raise ValueError('index is missing or out of date')
        else:
            args.output.parent.mkdir(parents=True, exist_ok=True)
            args.output.write_text(text, encoding='utf-8', newline='\n')
    except (OSError, ValueError, KeyError, TypeError) as exc:
        print(f'contract index: {exc}', file=sys.stderr)
        return 1
    return 0


if __name__ == '__main__':
    sys.exit(main())
