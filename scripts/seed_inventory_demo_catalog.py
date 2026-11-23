"""Prepare shared canonical names/English aliases in an explicitly selected *_test DB only.

Reuses existing ingredient IDs and never rewrites inventory or recipes.
An explicit test-only repair can redirect the old apple demo aliases to a count entry,
while keeping every old gram ingredient ID and quantity unchanged.
The Android app reads this catalog through the normal ingredient API.
"""
import argparse
import subprocess
import unicodedata
import uuid

CATALOG = [
    ('鲜牛奶', 'COUNT', ['milk', 'fresh milk', '牛奶']),
    ('鸡蛋', 'COUNT', ['egg', 'eggs']),
    ('酸奶', 'COUNT', ['yogurt', 'yoghurt']),
    ('小白菜', 'G', ['bok choy', 'greens']),
    ('西兰花', 'G', ['broccoli']),
    ('西红柿', 'G', ['tomato', 'tomatoes', '番茄']),
    ('苹果', 'COUNT', ['apple', 'apples']),
    ('橙子', 'COUNT', ['orange', 'oranges']),
    ('牛肉', 'G', ['beef']),
    ('猪肉', 'G', ['pork']),
    ('羊肉', 'G', ['lamb', 'mutton']),
    ('鲜虾', 'COUNT', ['shrimp', 'prawn', '虾']),
    ('贝柱', 'COUNT', ['scallop', 'scallops']),
]


def quote(value):
    return "'" + value.replace("'", "''") + "'"


def repair_apple_count(sql):
    """Correct only the historical demo definition, never convert existing grams to items."""
    if not sql('SELECT current_database()').endswith('_test'):
        raise RuntimeError('Apple demo repair is restricted to *_test databases')
    # One transaction; reject aliases belonging to unrelated ingredients rather than stealing them.
    sql("""BEGIN;
LOCK TABLE ingredients, ingredient_aliases IN SHARE ROW EXCLUSIVE MODE;
DO $repair$
DECLARE target uuid;
BEGIN
  IF EXISTS (
    SELECT 1 FROM ingredient_aliases a JOIN ingredients i ON i.id=a.ingredient_id
    WHERE a.normalized_name IN ('苹果','apple','apples')
      AND NOT ((i.name IN ('苹果','苹果（按克）') AND i.unit='G')
               OR (i.name='苹果' AND i.unit='COUNT' AND i.active))
  ) THEN
    RAISE EXCEPTION 'Apple alias belongs to unrelated data; repair stopped';
  END IF;
  SELECT id INTO target FROM ingredients WHERE name='苹果' AND unit='COUNT' AND active
    ORDER BY id LIMIT 1;
  IF target IS NULL THEN
    target := gen_random_uuid();
    INSERT INTO ingredients(id,name,unit,active) VALUES(target,'苹果','COUNT',true);
  END IF;
  -- Preserve IDs, measurement units, batches, movements and recipe quantities.
  UPDATE ingredients SET name='苹果（按克）' WHERE name='苹果' AND unit='G';
  INSERT INTO ingredient_aliases(normalized_name,ingredient_id,display_name)
    VALUES ('苹果',target,'苹果'),('apple',target,'apple'),('apples',target,'apples')
    ON CONFLICT(normalized_name) DO UPDATE SET ingredient_id=EXCLUDED.ingredient_id;
END $repair$;
COMMIT;""")


def ensure_catalog(sql):
    result = {}
    for name, unit, aliases in CATALOG:
        primary = aliases[0]
        existing = sql("SELECT i.id::text || '|' || i.name || '|' || i.unit FROM ingredient_aliases a "
                       "JOIN ingredients i ON i.id=a.ingredient_id WHERE a.normalized_name="+quote(primary))
        if existing:
            chosen, actual_name, actual_unit = existing.split('|')
            if actual_name != name or actual_unit != unit:
                raise RuntimeError('Existing alias conflicts with demo catalog: '+primary)
        else:
            chosen = sql('SELECT id::text FROM ingredients WHERE active AND name='+quote(name)+
                         ' AND unit='+quote(unit)+' ORDER BY id LIMIT 1')
            if not chosen:
                chosen = str(uuid.uuid4())
                sql('INSERT INTO ingredients(id,name,unit,active) VALUES ('+quote(chosen)+','+
                    quote(name)+','+quote(unit)+',true)')
        uuid.UUID(chosen)
        for alias in [name, *aliases]:
            normalized = ' '.join(unicodedata.normalize('NFKC', alias).lower().split())
            old = sql('SELECT i.name || \'|\' || i.unit FROM ingredient_aliases a JOIN ingredients i '
                      'ON i.id=a.ingredient_id WHERE a.normalized_name='+quote(normalized))
            if old and old != name+'|'+unit:
                raise RuntimeError('Existing alias conflicts with demo catalog: '+alias)
            sql('INSERT INTO ingredient_aliases(normalized_name,ingredient_id,display_name) VALUES ('+
                quote(normalized)+','+quote(chosen)+','+quote(alias)+') ON CONFLICT(normalized_name) DO NOTHING')
        result[name] = chosen
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--psql', required=True)
    parser.add_argument('--test-db', required=True)
    parser.add_argument('--repair-apple-count', action='store_true',
                        help='Correct the old test-only apple defaults; preserve existing gram stock')
    args = parser.parse_args()
    if not args.test_db.endswith('_test'):
        raise RuntimeError('Only an explicitly selected *_test database is allowed')

    def sql(statement):
        p = subprocess.run([args.psql, '-X', '-q', '-v', 'ON_ERROR_STOP=1', '-t', '-A', '-d', args.test_db],
                           input=statement.encode('utf-8'), capture_output=True, timeout=30)
        if p.returncode:
            raise RuntimeError('Demo catalog query failed; no credentials are printed')
        return p.stdout.decode('utf-8').strip()

    if sql('SELECT current_database()') != args.test_db:
        raise RuntimeError('Database does not match the explicitly selected test DB')
    if args.repair_apple_count:
        repair_apple_count(sql)
        print('Corrected apple demo aliases to COUNT; existing gram IDs and stock preserved')
    ids = ensure_catalog(sql)
    print('Prepared '+str(len(ids))+' shared demo ingredients with Chinese/English names; existing data preserved')


if __name__ == '__main__':
    main()
