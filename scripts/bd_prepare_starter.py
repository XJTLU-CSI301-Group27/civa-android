"""Rebuild bundled recipe metadata from the six checked-in Unlicense source files.

This is an explicit developer preparation step, never a runtime network fetch.
"""
import hashlib
import json
from pathlib import Path
from urllib.parse import quote
from uuid import uuid5, NAMESPACE_URL

ROOT = Path(__file__).resolve().parents[1]
FOLDER = ROOT / 'server/src/main/resources/bd-starter'
DEFINITIONS = [
    ('西红柿炒鸡蛋', 'dishes/vegetable_dish/西红柿炒鸡蛋.md', 15, ['酸甜', '家常'], ['西红柿','鸡蛋','食用油','盐','白砂糖','葱']),
    ('酸辣土豆丝', 'dishes/vegetable_dish/酸辣土豆丝.md', 20, ['酸辣', '家常'], ['土豆','大蒜','青椒','红椒','干辣椒','葱','生抽','陈醋','盐','食用油']),
    ('可乐鸡翅', 'dishes/meat_dish/可乐鸡翅.md', 40, ['咸甜', '荤菜'], ['鸡翅中','可乐','白砂糖','生抽','老抽','盐','生姜','料酒','葱','食用油']),
    ('鸡蛋羹', 'dishes/vegetable_dish/鸡蛋羹/鸡蛋羹.md', 15, ['清淡', '蒸菜'], ['鸡蛋','盐','香油','生抽','白醋','藤椒油']),
    ('蛋炒饭', 'dishes/staple/蛋炒饭.md', 25, ['咸香', '主食'], ['米饭','鸡蛋','火腿','黄瓜','胡萝卜','食用油','盐','胡椒粉','生抽','葱']),
    ('糖拌西红柿', 'dishes/vegetable_dish/糖拌西红柿/糖拌西红柿.md', 15, ['酸甜', '凉菜'], ['西红柿','白砂糖']),
]


def build():
    recipes = []
    for name, source_path, minutes, tags, ingredients in DEFINITIONS:
        source = (FOLDER / (name + '.txt')).read_bytes()
        recipes.append(dict(name=name, path=source_path, sourceUrl='https://github.com/Anduin2017/HowToCook/blob/master/'+quote(source_path),
                            sourceSha256=hashlib.sha256(source).hexdigest(), file=name+'.txt', durationMinutes=minutes,
                            yieldServings='1', totalKcal=None, tags=tags, ingredients=ingredients))
    (FOLDER/'manifest.json').write_text(json.dumps(dict(source='Anduin2017/HowToCook', license='Unlicense',
        fetchedAt='2026-11-20', note='One planning portion per recipe; quantities and calories are not normalized. All imported ingredients are untracked; consult preserved source for actual amounts.', recipes=recipes), ensure_ascii=False, indent=2)+'\n', encoding='utf-8')
    names = sorted({name for recipe in recipes for name in recipe['ingredients']})
    lines = ['-- Additive standard dictionary for the explicit BD starter import. Never rewrites existing aliases.',
             '-- Existing canonical mappings take precedence; imported recipe lines are untracked, quantity=0.']
    for name in names:
        key = str(uuid5(NAMESPACE_URL, 'https://civa.local/ingredients/'+name))
        unit = 'COUNT' if name == '鸡蛋' else 'ML' if name in ['食用油','可乐','生抽','老抽','陈醋','料酒','香油','白醋','藤椒油'] else 'G'
        lines.append(f"INSERT INTO ingredients(id,name,unit,active) SELECT '{key}'::uuid,'{name}','{unit}',true WHERE NOT EXISTS(SELECT 1 FROM ingredient_aliases WHERE normalized_name='{name}') ON CONFLICT(id) DO NOTHING;")
        lines.append(f"INSERT INTO ingredient_aliases(normalized_name,ingredient_id,display_name) SELECT '{name}',id,'{name}' FROM ingredients WHERE id='{key}'::uuid ON CONFLICT(normalized_name) DO NOTHING;")
    migration = ROOT/'server/src/main/resources/db/migration/V20261120134000__BD_starter_ingredient_dictionary.sql'
    migration_text = '\n'.join(lines)+'\n'
    if migration.exists() and migration.read_text(encoding='utf-8') != migration_text:
        raise SystemExit('Refusing to rewrite an existing migration. Add a new forward migration for dictionary changes.')
    if not migration.exists():
        migration.write_text(migration_text,encoding='utf-8')


if __name__ == '__main__':
    build()
