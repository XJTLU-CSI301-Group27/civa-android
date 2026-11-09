"""Exercise Room's exported SQLite schema and the DAO's actual query strings."""

import json
import re
import sqlite3
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parent.parent
SCHEMA = ROOT / 'core-data/schemas/edu.xjtlu.civa.core.data.LocalDatabase/1.json'
DAO = ROOT / 'core-data/src/main/java/edu/xjtlu/civa/core/data/LocalDataDao.java'


def query(name):
    text = DAO.read_text(encoding='utf-8')
    match = re.search(r'@Query\("([^"]+)"\)\s+(?:CacheEntry|int|List<PendingWrite>)\s+' + re.escape(name) + r'\(', text)
    if match is None:
        raise ValueError(f'missing DAO query: {name}')
    return match.group(1)


class LocalDatabaseSqlTest(unittest.TestCase):
    def test_user_isolation_and_cache_expiry_leave_pending_intents(self):
        schema = json.loads(SCHEMA.read_text(encoding='utf-8'))['database']
        with sqlite3.connect(':memory:') as db:
            for entity in schema['entities']:
                db.execute(entity['createSql'].replace('${TABLE_NAME}', entity['tableName']))
                for index in entity.get('indices', []):
                    db.execute(index['createSql'].replace('${TABLE_NAME}', entity['tableName']))
            db.execute('INSERT INTO cache_entries VALUES (?, ?, ?, ?, ?)', ('a-page', 'a', 'home', '{}', 100))
            db.execute('INSERT INTO cache_entries VALUES (?, ?, ?, ?, ?)', ('b-page', 'b', None, '{}', 200))
            db.execute('INSERT INTO pending_writes VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)',
                       ('key-a', 'a', 'home', 'confirmCooking', '{}', 100, 0, None, None))
            with self.assertRaises(sqlite3.IntegrityError):
                db.execute('INSERT INTO pending_writes VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)',
                           ('key-a', 'a', 'home', 'confirmCooking', '{"changed":true}', 101, 0, None, None))
            args = {'key': 'a-page', 'userId': 'a', 'householdId': 'home'}
            self.assertIsNotNone(db.execute(query('cache'), args).fetchone())
            self.assertIsNone(db.execute(query('cache'), {**args, 'userId': 'b'}).fetchone())
            self.assertIsNone(db.execute(query('cache'), {**args, 'householdId': None}).fetchone())
            self.assertEqual(1, db.execute(query('deleteCacheBefore'), {'cutoff': 150}).rowcount)
            self.assertEqual(1, len(db.execute(query('pendingForUser'), {'userId': 'a'}).fetchall()))
            self.assertEqual([], db.execute(query('pendingForUser'), {'userId': 'b'}).fetchall())
            self.assertEqual(0, db.execute(query('deletePending'), {'userId': 'b', 'key': 'key-a'}).rowcount)
            self.assertEqual(1, db.execute(query('markHouseholdForReview'), {'userId': 'a', 'householdId': 'home'}).rowcount)
            self.assertEqual(1, db.execute('SELECT needsReview FROM pending_writes').fetchone()[0])
            self.assertEqual(1, db.execute(query('deletePending'), {'userId': 'a', 'key': 'key-a'}).rowcount)
            self.assertIsNotNone(db.execute(query('cache'), {'key': 'b-page', 'userId': 'b', 'householdId': None}).fetchone())


if __name__ == '__main__':
    unittest.main()
