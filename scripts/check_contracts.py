#!/usr/bin/env python
"""Consistency check for the candidate OpenAPI contract and public-surface draft."""

import json
import sys
from pathlib import Path

from contract_diff import load, _operations, _components

ROOT = Path(__file__).resolve().parent.parent


def main():
    try:
        document = load(ROOT / 'contracts/openapi.json')
        # Force operationId uniqueness, reference resolution and security-scheme checks.
        _operations(document)
        _components(document)
        surface = json.loads((ROOT / 'contracts/public-surface.draft.json').read_text(encoding='utf-8'))
        if surface.get('schemaVersion') != 1:
            raise ValueError('public-surface schemaVersion must be 1')
        if not isinstance(surface.get('symbols'), list):
            raise ValueError('public-surface symbols must be a list')
    except (ValueError, OSError, json.JSONDecodeError) as exc:
        print(f'checkContracts failed: {exc}', file=sys.stderr)
        return 1
    print('contracts consistent')
    return 0


if __name__ == '__main__':
    sys.exit(main())
