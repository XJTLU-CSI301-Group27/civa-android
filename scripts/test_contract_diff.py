import copy
import json
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path
from contract_diff import compare, load


class ContractDiffTest(unittest.TestCase):
    def setUp(self):
        self.base = {
            'openapi': '3.0.3',
            'security': [{'bearerAuth': []}],
            'paths': {'/items': {'get': {'operationId': 'getItems', 'responses': {'200': {'content': {'application/json': {'schema': {'$ref': '#/components/schemas/Outer'}}}}}}}},
            'components': {'securitySchemes': {'bearerAuth': {'type': 'http', 'scheme': 'bearer'}},
                           'schemas': {'Outer': {'type': 'object', 'properties': {'item': {'$ref': '#/components/schemas/Inner'}}},
                                       'Inner': {'type': 'string', 'description': 'quantity in g'}}},
        }

    def test_transitive_semantics_operations_and_security(self):
        head = copy.deepcopy(self.base)
        head['components']['schemas']['Inner']['description'] = 'quantity in kg'
        head['paths']['/items']['post'] = {'operationId': 'createItem', 'responses': {'201': {'description': 'created'}}}
        head['components']['securitySchemes']['bearerAuth']['scheme'] = 'basic'
        changes = compare(self.base, head)['changes']
        self.assertIn(('operation', 'GET /items', 'MODIFIED'), [(x['type'], x['name'], x['change']) for x in changes])
        self.assertIn(('operation', 'POST /items', 'ADDED'), [(x['type'], x['name'], x['change']) for x in changes])
        self.assertIn(('component', 'schemas/Inner', 'MODIFIED'), [(x['type'], x['name'], x['change']) for x in changes])
        self.assertEqual('UNVERIFIED', compare(self.base, head)['authorization'])

    def test_object_order_does_not_change_fingerprint(self):
        reordered = json.loads(json.dumps(self.base, sort_keys=True))
        self.assertEqual('NO_CHANGES', compare(self.base, reordered)['status'])

    def test_added_schema_and_unresolved_reference(self):
        head = copy.deepcopy(self.base)
        head['components']['schemas']['New'] = {'type': 'string'}
        self.assertIn('schemas/New', [x['name'] for x in compare(self.base, head)['changes']])
        head['components']['schemas']['Inner'] = {'$ref': '#/components/schemas/Missing'}
        with self.assertRaises(ValueError):
            compare(self.base, head)

    def test_duplicate_json_key_is_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / 'duplicate.json'
            path.write_text('{"openapi":"3.0.3","paths":{},"paths":{}}', encoding='utf-8')
            with self.assertRaises(ValueError):
                load(path)

    def test_cli_reports_changes_and_refuses_to_overwrite_input(self):
        with tempfile.TemporaryDirectory() as directory:
            base = Path(directory) / 'base.json'
            head = Path(directory) / 'head.json'
            report = Path(directory) / 'report.json'
            base.write_text(json.dumps(self.base), encoding='utf-8')
            changed = copy.deepcopy(self.base)
            changed['paths']['/items']['delete'] = {'operationId': 'deleteItem', 'responses': {'204': {'description': 'deleted'}}}
            head.write_text(json.dumps(changed), encoding='utf-8')
            script = Path(__file__).with_name('contract_diff.py')
            result = subprocess.run([sys.executable, str(script), '--base', str(base), '--head', str(head), '--output', str(report)], capture_output=True)
            self.assertEqual(1, result.returncode)
            self.assertEqual('UNVERIFIED', json.loads(report.read_text(encoding='utf-8'))['authorization'])
            result = subprocess.run([sys.executable, str(script), '--base', str(base), '--head', str(head), '--output', str(base)], capture_output=True)
            self.assertEqual(2, result.returncode)
            self.assertEqual(self.base, json.loads(base.read_text(encoding='utf-8')))


if __name__ == '__main__':
    unittest.main()
