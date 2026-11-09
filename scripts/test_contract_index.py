import copy
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

from contract_diff import load
from contract_index import render


class ContractIndexTest(unittest.TestCase):
    def test_draft_index_and_invalid_owner(self):
        root = Path(__file__).resolve().parent.parent
        contract = load(root / 'contracts/openapi.json')
        text = render(contract)
        self.assertEqual(41, sum(line.startswith('| ') for line in text.splitlines()) - 2)
        self.assertIn('multipart/form-data: object', text)
        self.assertIn('image/jpeg: binary, image/png: binary', text)
        self.assertIn('application/json: PlanSlot[]', text)
        broken = copy.deepcopy(contract)
        del broken['paths']['/api/v1/auth/login']['post']['x-owner']
        with self.assertRaisesRegex(ValueError, 'missing x-owner'):
            render(broken)

        with tempfile.TemporaryDirectory() as directory:
            index = Path(directory) / 'index.md'
            command = [sys.executable, str(root / 'scripts/contract_index.py'),
                       str(root / 'contracts/openapi.json'), str(index)]
            self.assertEqual(0, subprocess.run(command, capture_output=True).returncode)
            self.assertEqual(0, subprocess.run(command + ['--check'], capture_output=True).returncode)
            index.write_text('out of date', encoding='utf-8')
            self.assertNotEqual(0, subprocess.run(command + ['--check'], capture_output=True).returncode)


if __name__ == '__main__':
    unittest.main()
