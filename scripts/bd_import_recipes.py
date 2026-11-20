"""Explicit, repeatable import into an existing household using the documented shared schema.

DB_URL/DB_USERNAME/DB_PASSWORD match the existing launcher. No credentials in arguments/logs.
Does not create users, households, fake cooking records, plans, or personal meal entries.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
from urllib.parse import urlparse
from uuid import UUID, uuid5, NAMESPACE_URL

ROOT = Path(__file__).resolve().parents[1]
FOLDER = ROOT/'server/src/main/resources/bd-starter'


def literal(value):
    return "'" + str(value).replace("'", "''") + "'"


def sql(home, actor):
    manifest = json.loads((FOLDER/'manifest.json').read_text(encoding='utf-8'))
    result = ['BEGIN;', "SET LOCAL lock_timeout='10s';", 'CREATE TEMP TABLE bd_new_recipe_ids(id UUID PRIMARY KEY) ON COMMIT DROP;',
              f'SELECT id FROM households WHERE id={literal(home)}::uuid FOR UPDATE;',
              f"DO $$ BEGIN IF NOT EXISTS(SELECT 1 FROM memberships WHERE household_id={literal(home)}::uuid AND user_id={literal(actor)}::uuid AND active AND role='OWNER') THEN RAISE EXCEPTION 'Active household owner required'; END IF; END $$;"]
    for recipe in manifest['recipes']:
        content = (FOLDER/recipe['file']).read_bytes()
        if hashlib.sha256(content).hexdigest() != recipe['sourceSha256']:
            raise ValueError('Source digest mismatch: '+recipe['file'])
        recipe_id = str(uuid5(NAMESPACE_URL, 'https://civa.local/starter/'+str(home)+'/'+recipe['path']))
        steps = ['来源：'+recipe['sourceUrl']+' · HowToCook / Unlicense',
                 '导入为一份计划单位，未标准化实际人数、用量或热量；原文如下。', content.decode('utf-8')]
        tags = 'ARRAY['+','.join(literal(tag) for tag in recipe['tags'])+']::text[]'
        result.append(f"WITH inserted AS (INSERT INTO recipes(id,household_id,author_id,name,steps,yield_servings,duration_minutes,tags,total_kcal) VALUES ({literal(recipe_id)}::uuid,{literal(home)}::uuid,{literal(actor)}::uuid,{literal(recipe['name'])},{literal(json.dumps(steps,ensure_ascii=False))}::jsonb,1,{int(recipe['durationMinutes'])},{tags},NULL) ON CONFLICT(id) DO NOTHING RETURNING id) INSERT INTO bd_new_recipe_ids SELECT id FROM inserted;")
        for position, name in enumerate(recipe['ingredients']):
            result.append(f"INSERT INTO recipe_ingredients(recipe_id,ingredient_id,position,quantity,unit,track_stock) SELECT {literal(recipe_id)}::uuid,i.id,{position},0,i.unit,false FROM ingredient_aliases a JOIN ingredients i ON i.id=a.ingredient_id WHERE a.normalized_name={literal(name)} AND EXISTS(SELECT 1 FROM bd_new_recipe_ids WHERE id={literal(recipe_id)}::uuid) ON CONFLICT(recipe_id,ingredient_id) DO NOTHING;")
        result.append(f"DO $$ BEGIN IF EXISTS(SELECT 1 FROM bd_new_recipe_ids WHERE id={literal(recipe_id)}::uuid) AND (SELECT count(*) FROM recipe_ingredients WHERE recipe_id={literal(recipe_id)}::uuid) < {len(recipe['ingredients'])} THEN RAISE EXCEPTION 'Starter ingredient mapping missing; run migrations and inspect canonical aliases'; END IF; END $$;")
    result.append('COMMIT;')
    return '\n'.join(result)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--household', required=True, type=UUID)
    parser.add_argument('--actor', required=True, type=UUID)
    parser.add_argument('--print-sql', action='store_true', help='Review SQL; no database changes')
    args = parser.parse_args()
    statement = sql(args.household, args.actor)
    if args.print_sql:
        print(statement)
        return
    raw = os.environ.get('DB_URL', '').removeprefix('jdbc:')
    url = urlparse(raw)
    if url.scheme not in ('postgresql','postgres') or not url.hostname or not url.path.strip('/') or url.username or url.password or url.query or url.fragment:
        raise SystemExit('Set DB_URL to an explicit PostgreSQL URL without embedded credentials or query')
    psql = os.environ.get('CIVA_PSQL') or shutil.which('psql')
    if not psql and os.name == 'nt':
        candidate = Path(os.environ.get('ProgramFiles','C:/Program Files'))/'PostgreSQL/16/bin/psql.exe'
        if candidate.is_file():
            psql = str(candidate)
    if not psql or not os.environ.get('DB_USERNAME') or not os.environ.get('DB_PASSWORD'):
        raise SystemExit('Require psql (or CIVA_PSQL), DB_USERNAME, and DB_PASSWORD')
    env = os.environ.copy()
    env['PGPASSWORD'] = env['DB_PASSWORD']
    env['PGCLIENTENCODING'] = 'UTF8'
    process = subprocess.run([psql,'-X','--no-password','--set','ON_ERROR_STOP=1','-h',url.hostname,'-p',str(url.port or 5432),'-U',env['DB_USERNAME'],'-d',url.path.strip('/')],
                             input=statement, text=True, encoding='utf-8', env=env, capture_output=True)
    if process.returncode:
        # psql output can contain complete source INSERTs. Keep it out of routine logs.
        raise SystemExit('Import failed and transaction rolled back; inspect database availability, owner, migrations and alias mappings. No credentials printed.')
    print('Imported six attributed recipes into the selected household; repeated imports preserve existing edits.')


if __name__ == '__main__':
    main()
