"""Remove explicitly owned, random-name UI fixtures from a guarded *_test DB only."""
import argparse
import json
from pathlib import Path
import re
import subprocess
import uuid


def cleanup_ephemeral_foods(sql, owner, home, ingredients):
    if not sql('SELECT current_database()').endswith('_test'):
        raise RuntimeError('Fixture cleanup requires an explicitly isolated *_test database')
    owner = str(uuid.UUID(owner))
    home = str(uuid.UUID(home))
    # A random suffix alone is never proof of ownership. Check the test actor's
    # successful createIngredient response and its household before deleting anything.
    removed, retained = [], []
    for value in ingredients:
        ingredient = str(uuid.UUID(value))
        sql("""BEGIN;
DO $cleanup$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM ingredients WHERE id='%s') THEN RETURN; END IF;
  IF NOT EXISTS (SELECT 1 FROM users u JOIN memberships m ON m.user_id=u.id
                 WHERE u.id='%s' AND u.username ~ '^c_ui_[a-z0-9]+$'
                   AND m.household_id='%s') THEN
    RAISE EXCEPTION 'Fixture actor/household not verified';
  END IF;
  PERFORM 1 FROM ingredients WHERE id='%s' FOR UPDATE;
  IF NOT EXISTS (
    SELECT 1 FROM ingredients i JOIN idempotency_requests r
      ON r.response_body->>'id'=i.id::text AND r.response_body->>'name'=i.name
    WHERE i.id='%s' AND i.name ~ '^(fresh pear|oat flakes) [0-9a-f]{8}$'
      AND r.actor_id='%s' AND r.http_status=201
  ) THEN RAISE EXCEPTION 'Ingredient creation ownership not verified'; END IF;
  -- Never delete an ingredient used by another household, a recipe or preferences.
  IF EXISTS (SELECT 1 FROM inventory_batches WHERE ingredient_id='%s' AND household_id<>'%s')
    OR EXISTS (SELECT 1 FROM recipe_ingredients WHERE ingredient_id='%s')
    OR EXISTS (SELECT 1 FROM preferences WHERE '%s'::uuid=ANY(excluded_ingredient_ids))
  THEN RETURN; END IF;
  DELETE FROM inventory_movements WHERE household_id='%s'
    AND batch_id IN (SELECT id FROM inventory_batches WHERE ingredient_id='%s' AND household_id='%s');
  DELETE FROM inventory_batches WHERE ingredient_id='%s' AND household_id='%s';
  DELETE FROM ingredient_aliases WHERE ingredient_id='%s';
  DELETE FROM ingredients WHERE id='%s';
  DELETE FROM idempotency_requests WHERE actor_id='%s'
    AND (response_body->>'id'='%s' OR response_body->>'ingredientId'='%s');
END $cleanup$;
COMMIT;""" % (ingredient, owner, home, ingredient, ingredient, owner, ingredient, home,
              ingredient, ingredient, home, ingredient, home, ingredient, home,
              ingredient, ingredient, owner, ingredient, ingredient))
        target = removed if sql("SELECT count(*) FROM ingredients WHERE id='"+ingredient+"'") == '0' else retained
        target.append(ingredient)
    return removed, retained


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--psql', required=True)
    parser.add_argument('--test-db', required=True)
    parser.add_argument('--session', type=Path, required=True)
    parser.add_argument('--protect-session', type=Path, required=True)
    args = parser.parse_args()
    if not args.test_db.endswith('_test'):
        raise RuntimeError('Only an explicitly selected *_test database is allowed')
    session = json.loads(args.session.read_text(encoding='utf-8'))
    protected = json.loads(args.protect_session.read_text(encoding='utf-8'))
    if session['database'] != args.test_db or protected['database'] != args.test_db:
        raise RuntimeError('Session database does not match')
    if session['householdId'] == protected['householdId']:
        raise RuntimeError('Refusing to clean the user-visible preview household')
    if not re.fullmatch(r'c_ui_[a-z0-9]+', session['username']):
        raise RuntimeError('Only an explicitly recorded synthetic test actor is allowed')

    def sql(statement):
        result = subprocess.run([args.psql, '-X', '-q', '-v', 'ON_ERROR_STOP=1', '-t', '-A',
                                 '-d', args.test_db], input=statement.encode('utf-8'),
                                capture_output=True, timeout=30)
        if result.returncode:
            raise RuntimeError('Fixture cleanup SQL failed; credentials are not printed')
        return result.stdout.decode('utf-8').strip()

    if sql('SELECT current_database()') != args.test_db:
        raise RuntimeError('Connected database does not match')
    protected_home = str(uuid.UUID(protected['householdId']))

    def snapshot():
        return [sql("SELECT coalesce(jsonb_agg(to_jsonb(t) ORDER BY id),'[]'::jsonb)::text FROM "
                    + table + " t WHERE household_id='"+protected_home+"'")
                for table in ('inventory_batches', 'inventory_movements')]

    before = snapshot()
    removed, retained = cleanup_ephemeral_foods(sql, session['ownerId'], session['householdId'],
                                              session.get('ownedIngredientIds', []))
    if snapshot() != before:
        raise RuntimeError('User-visible stock changed during cleanup; inspect before proceeding')
    session['ownedIngredientIds'] = retained
    session['ingredientIds'] = [i for i in session['ingredientIds'] if i not in removed]
    args.session.write_text(json.dumps(session, ensure_ascii=False, indent=2), encoding='utf-8')
    print('PASS: removed '+str(len(removed))+' owned random-name test ingredients; retained '+
          str(len(retained))+' externally referenced ingredients; user-visible stock/movements unchanged')


if __name__ == '__main__':
    main()
