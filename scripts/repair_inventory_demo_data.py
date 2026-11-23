"""Audit/repair proven historical UI test food in *_test DBs; back up before explicit --apply.

Unlike routine test teardown, this user-requested repair can remove an unchanged
generated fixture from the retained preview family. Real/edited stock is excluded.
"""
import argparse
import datetime as dt
import json
from pathlib import Path
import subprocess
import uuid

from inventory_test_fixtures import cleanup_ephemeral_foods


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--psql', required=True)
    parser.add_argument('--test-db', required=True)
    parser.add_argument('--apply', action='store_true')
    args = parser.parse_args()
    if not args.test_db.endswith('_test'):
        raise RuntimeError('Only an explicitly selected *_test database is allowed')

    def sql(statement):
        result = subprocess.run([args.psql, '-X', '-q', '-v', 'ON_ERROR_STOP=1', '-t', '-A',
                                 '-d', args.test_db], input=statement.encode('utf-8'),
                                capture_output=True, timeout=30)
        if result.returncode:
            raise RuntimeError('Historical fixture query failed; credentials are not printed')
        return result.stdout.decode('utf-8').strip()

    if sql('SELECT current_database()') != args.test_db:
        raise RuntimeError('Connected database does not match')
    candidates = json.loads(sql("""SELECT coalesce(jsonb_agg(to_jsonb(t)),'[]'::jsonb)::text FROM (
      SELECT i.id, creator.actor_id AS owner,
        (SELECT household_id FROM memberships WHERE user_id=creator.actor_id AND active ORDER BY household_id LIMIT 1) AS home,
        (SELECT count(*) FROM inventory_batches WHERE ingredient_id=i.id) AS batches
      FROM ingredients i JOIN LATERAL (
        SELECT r.actor_id FROM idempotency_requests r JOIN users u ON u.id=r.actor_id
        WHERE r.http_status=201 AND r.response_body->>'id'=i.id::text
          AND r.response_body->>'name'=i.name AND u.username ~ '^c_ui_[a-z0-9]+$'
        ORDER BY r.created_at LIMIT 1
      ) creator ON true
      WHERE i.name ~ '^(fresh pear|oat flakes) [0-9a-f]{8}$'
      ORDER BY i.id
    ) t"""))
    eligible=[]
    for item in candidates:
        ingredient=str(uuid.UUID(item['id']));owner=str(uuid.UUID(item['owner']))
        if not item['home']:
            continue
        home=str(uuid.UUID(item['home']))
        # Only the untouched original fixture, never a batch subsequently adopted,
        # consumed, corrected, copied by another account, or used in a recipe.
        safe=sql("""SELECT NOT EXISTS (
          SELECT 1 FROM inventory_batches b JOIN ingredients i ON i.id=b.ingredient_id
          WHERE b.ingredient_id='%s' AND (b.version<>1 OR b.household_id<>'%s' OR
            NOT ((i.name LIKE 'oat flakes %%' AND b.quantity=75 AND b.unit='G') OR
                 (i.name LIKE 'fresh pear %%' AND b.quantity=2 AND b.unit='COUNT')) OR
            NOT EXISTS (SELECT 1 FROM idempotency_requests r WHERE r.actor_id='%s'
              AND r.http_status=201 AND r.response_body->>'id'=b.id::text
              AND r.response_body->>'ingredientId'=i.id::text))
        ) AND NOT EXISTS (SELECT 1 FROM recipe_ingredients WHERE ingredient_id='%s')
          AND NOT EXISTS (SELECT 1 FROM preferences WHERE '%s'::uuid=ANY(excluded_ingredient_ids))
          AND NOT EXISTS (SELECT 1 FROM inventory_movements m JOIN inventory_batches b ON b.id=m.batch_id
                          WHERE b.ingredient_id='%s')""" %
                 (ingredient, home, owner, ingredient, ingredient, ingredient))
        if safe=='t': eligible.append((ingredient,owner,home))
    print('Audit: '+str(len(candidates))+' proven historical test names, '+str(len(eligible))+' untouched fixtures eligible')
    if not args.apply or not eligible:
        return
    ids=','.join("'"+item[0]+"'" for item in eligible)
    backup={}
    conditions={
        'ingredients':'id IN ('+ids+')',
        'ingredient_aliases':'ingredient_id IN ('+ids+')',
        'inventory_batches':'ingredient_id IN ('+ids+')',
        'inventory_movements':'batch_id IN (SELECT id FROM inventory_batches WHERE ingredient_id IN ('+ids+'))',
        'idempotency_requests':"response_body->>'id' IN ("+ids+") OR response_body->>'ingredientId' IN ("+ids+")",
    }
    for table,condition in conditions.items():
        backup[table]=json.loads(sql("SELECT coalesce(jsonb_agg(to_jsonb(t)),'[]'::jsonb)::text FROM "+table+' t WHERE '+condition))
    def real_stock():
        return [sql("SELECT coalesce(jsonb_agg(to_jsonb(t) ORDER BY id),'[]'::jsonb)::text FROM "+table+
                    ' t WHERE '+condition) for table,condition in (
                        ('inventory_batches','ingredient_id NOT IN ('+ids+')'),
                        ('inventory_movements','batch_id NOT IN (SELECT id FROM inventory_batches WHERE ingredient_id IN ('+ids+'))'))]
    before=real_stock()
    path=Path(__file__).resolve().parents[1]/'build/c-ui-smoke'
    path.mkdir(parents=True,exist_ok=True)
    stamp=dt.datetime.now(dt.timezone.utc).strftime('%Y%m%dT%H%M%S%fZ')
    backup_file=path/('historical-fixtures-backup-'+stamp+'.json')
    backup_file.write_text(json.dumps({'database':args.test_db,'rows':backup},ensure_ascii=False,indent=2),encoding='utf-8')
    removed=[]
    for ingredient,owner,home in eligible:
        done,retained=cleanup_ephemeral_foods(sql,owner,home,[ingredient],untouched_only=True)
        if retained: raise RuntimeError('Fixture acquired new references; preserved for inspection')
        removed.extend(done)
    if real_stock()!=before:
        raise RuntimeError('Non-fixture stock changed during repair; inspect backup before proceeding')
    print('PASS: removed '+str(len(removed))+' proven untouched historical fixtures; other stock/movements unchanged; backup retained under ignored build/c-ui-smoke')


if __name__ == '__main__':
    main()
