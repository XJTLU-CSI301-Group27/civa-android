"""Negative and positive tests for the IC authorization gate (section 14.6)."""

import json
import tempfile
import unittest
from pathlib import Path

from contract_guard import authorize, load_ics, draft_document, contract_digest


def document(paths, schemas=None):
    return {
        'openapi': '3.0.3',
        'info': {'title': 't', 'version': '1'},
        'paths': paths,
        'components': {'schemas': schemas or {}},
    }


def operation(operation_id):
    return {'operationId': operation_id, 'responses': {'200': {'description': 'ok'}}}


def write(path, data):
    Path(path).write_text(json.dumps(data), encoding='utf-8')


def ic(path, ic_id, status, operations=None, schemas=None, digest='null'):
    front = (
        '---\n'
        f'id: {ic_id}\n'
        f'status: {status}\n'
        f'allowedOperations: {json.dumps(operations or [])}\n'
        f'allowedSchemas: {json.dumps(schemas or [])}\n'
        'allowedSymbols: []\n'
        f'contractDigest: {digest}\n'
        'baseSha: null\n'
        'headSha: null\n'
        '---\n'
        f'# {ic_id}\n'
    )
    Path(path).write_text(front, encoding='utf-8')


class ContractGuardTest(unittest.TestCase):
    def test_new_operation_without_ic_fails(self):
        with tempfile.TemporaryDirectory() as d:
            base = document({})
            head = document({'/x': {'post': operation('createX')}})
            report = authorize(base, head, [])
            self.assertEqual('FAIL', report['status'])
            self.assertEqual(1, report['unauthorizedChanges'])

    def test_authorized_operation_passes(self):
        with tempfile.TemporaryDirectory() as d:
            base = document({})
            head = document({'/x': {'post': operation('createX')}})
            ic(Path(d) / 'IC-1.md', 'IC-1', 'ACCEPTED', operations=['createX'])
            report = authorize(base, head, load_ics(d))
            self.assertEqual('PASS', report['status'])
            self.assertEqual(0, report['unauthorizedChanges'])

    def test_proposed_ic_does_not_authorize(self):
        with tempfile.TemporaryDirectory() as d:
            base = document({})
            head = document({'/x': {'post': operation('createX')}})
            ic(Path(d) / 'IC-1.md', 'IC-1', 'PROPOSED', operations=['createX'])
            report = authorize(base, head, load_ics(d))
            self.assertEqual('FAIL', report['status'])

    def test_schema_change_requires_authorization(self):
        with tempfile.TemporaryDirectory() as d:
            base = document({}, schemas={'User': {'type': 'object'}})
            head = document({}, schemas={'User': {'type': 'object', 'required': ['id']}})
            report = authorize(base, head, [])
            self.assertEqual('FAIL', report['status'])
            ic(Path(d) / 'IC-2.md', 'IC-2', 'ACCEPTED', schemas=['User'])
            self.assertEqual('PASS', authorize(base, head, load_ics(d))['status'])

    def test_duplicate_yaml_key_is_rejected(self):
        with tempfile.TemporaryDirectory() as d:
            path = Path(d) / 'IC-3.md'
            path.write_text('---\nid: IC-3\nid: IC-3b\nstatus: ACCEPTED\n---\n# body\n', encoding='utf-8')
            with self.assertRaises(ValueError):
                load_ics(d)

    def test_unknown_metadata_field_is_rejected(self):
        with tempfile.TemporaryDirectory() as d:
            path = Path(d) / 'IC-4.md'
            path.write_text('---\nid: IC-4\nstatus: ACCEPTED\nmystery: true\n---\n# body\n', encoding='utf-8')
            with self.assertRaises(ValueError):
                load_ics(d)

    def test_draft_lists_changed_operations_and_schemas(self):
        base = document({}, schemas={'User': {'type': 'object'}})
        head = document({'/x': {'post': operation('createX')}}, schemas={'User': {'type': 'object', 'required': ['id']}})
        text = draft_document(base, head, 'BASE', 'HEAD')
        self.assertIn('type: UNREGISTERED', text)
        self.assertIn('status: DETECTED', text)
        self.assertIn('"createX"', text)
        self.assertIn('"User"', text)
        self.assertIn('BASE', text)
        self.assertIn('HEAD', text)

    def test_digest_verification_matches_the_accepted_commitment(self):
        with tempfile.TemporaryDirectory() as d:
            base = document({})
            head = document({'/x': {'post': operation('createX')}})
            correct = contract_digest(base, head, ['createX'], [])
            ic(Path(d) / 'IC-ok.md', 'IC-ok', 'ACCEPTED', operations=['createX'], digest=json.dumps(correct))
            self.assertEqual('PASS', authorize(base, head, load_ics(d))['status'])

            wrong = 'sha256:' + '0' * 64
            ic(Path(d) / 'IC-bad.md', 'IC-bad', 'ACCEPTED', operations=['createX'], digest=json.dumps(wrong))
            report = authorize(base, head, load_ics(d))
            self.assertEqual('FAIL', report['status'])
            self.assertEqual(1, len(report['digestViolations']))

    def test_digest_ignores_description_only_changes(self):
        base = document({}, schemas={'User': {'type': 'object', 'description': 'old'}})
        head = document({}, schemas={'User': {'type': 'object', 'description': 'new'}})
        self.assertEqual(
            contract_digest(base, base, [], ['User']),
            contract_digest(base, head, [], ['User']),
        )

    def test_digest_detects_behavioral_changes(self):
        base = document({}, schemas={'User': {'type': 'object'}})
        head = document({}, schemas={'User': {'type': 'object', 'required': ['id']}})
        self.assertNotEqual(
            contract_digest(base, base, [], ['User']),
            contract_digest(base, head, [], ['User']),
        )


if __name__ == '__main__':
    unittest.main()
