"""Verify the explicit apple fixture repair in an isolated *_test database only."""
import argparse
import json
import subprocess

from seed_inventory_demo_catalog import ensure_catalog, repair_apple_count


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--psql', required=True)
    parser.add_argument('--test-db', required=True)
    args = parser.parse_args()
    if not args.test_db.endswith('_test'):
        raise RuntimeError('Only an explicitly selected *_test database is allowed')

    def sql(statement):
        result = subprocess.run(
            [args.psql, '-X', '-q', '-v', 'ON_ERROR_STOP=1', '-t', '-A', '-d', args.test_db],
            input=statement.encode('utf-8'), capture_output=True, timeout=30)
        if result.returncode:
            raise RuntimeError('Fixture verification SQL failed; credentials are not printed')
        return result.stdout.decode('utf-8').strip()

    if sql('SELECT current_database()') != args.test_db:
        raise RuntimeError('Connected database does not match the test database')

    def stock_snapshot():
        return [sql("SELECT coalesce(jsonb_agg(to_jsonb(t) ORDER BY id),'[]'::jsonb)::text FROM "
                    + table + ' t') for table in ('inventory_batches', 'inventory_movements')]

    before = stock_snapshot()
    old_units = json.loads(sql("SELECT coalesce(jsonb_object_agg(id::text,unit),'{}'::jsonb)::text FROM ingredients"))
    repair_apple_count(sql)
    catalog = ensure_catalog(sql)
    if stock_snapshot() != before:
        raise RuntimeError('Repair changed existing stock or movement rows')
    current_units = json.loads(sql("SELECT coalesce(jsonb_object_agg(id::text,unit),'{}'::jsonb)::text FROM ingredients"))
    if any(current_units.get(key) != unit for key, unit in old_units.items()):
        raise RuntimeError('Repair changed an existing ingredient ID or measurement unit')
    for alias in ('apple', 'apples', '苹果'):
        actual = sql("SELECT i.id::text || '|' || i.unit FROM ingredient_aliases a JOIN ingredients i "
                     "ON i.id=a.ingredient_id WHERE a.normalized_name='" + alias + "'")
        if actual != catalog['苹果'] + '|COUNT':
            raise RuntimeError('Apple aliases did not resolve to one count ingredient')
    repair_apple_count(sql)
    if ensure_catalog(sql)['苹果'] != catalog['苹果'] or stock_snapshot() != before:
        raise RuntimeError('Repeated repair was not idempotent')
    print('PASS: apple/apple plural/Chinese resolve to COUNT; old IDs, units, stock and movements preserved; repeated repair is idempotent')


if __name__ == '__main__':
    main()
