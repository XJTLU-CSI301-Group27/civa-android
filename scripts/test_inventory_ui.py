"""C inventory native UI smoke test against an explicitly guarded PostgreSQL *_test DB.

Requires an already running backend pointing to that same isolated database,
the current debug APK installed on the named emulator, and PG* credentials.
Uses synthetic fixtures only; never runs on a development/production database.
"""
import argparse
import datetime as dt
import json
import os
from pathlib import Path
import re
import secrets
import subprocess
import time
import urllib.error
import urllib.request
import uuid
import xml.etree.ElementTree as ET


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--adb', required=True)
    parser.add_argument('--psql', required=True)
    parser.add_argument('--serial', default='emulator-5554')
    parser.add_argument('--base-url', default='http://127.0.0.1:8080')
    parser.add_argument('--test-db', required=True)
    parser.add_argument('--keep-fixtures', action='store_true', help='Keep isolated demo account/data for interactive emulator preview; credentials stay in ignored build directory')
    args = parser.parse_args()
    if not args.test_db.endswith('_test'):
        raise RuntimeError('Only a database ending in _test is allowed')
    root = Path(__file__).resolve().parents[1]
    output = root / 'docs/verification/screenshots'
    output.mkdir(parents=True, exist_ok=True)
    work = root / 'build/c-ui-smoke'
    work.mkdir(parents=True, exist_ok=True)

    def sql(statement):
        result = subprocess.run([args.psql, '-X', '-v', 'ON_ERROR_STOP=1', '-t', '-A',
                                 '-d', args.test_db], input=statement.encode('utf-8'),
                                capture_output=True, timeout=30)
        if result.returncode:
            raise RuntimeError('Test fixture SQL failed; no credentials are printed')
        return result.stdout.decode('utf-8').strip()

    if sql('SELECT current_database()') != args.test_db:
        raise RuntimeError('Connected database does not match the test database')
    # Verify the backend uses this same DB by reading the newly created household below.
    token = None
    def api(method, path, body=None):
        headers = {'Content-Type': 'application/json'}
        if token:
            headers['Authorization'] = 'Bearer ' + token
        if method == 'POST' and not path.startswith('/auth/'):
            headers['Idempotency-Key'] = str(uuid.uuid4())
        request = urllib.request.Request(args.base_url + '/api/v1' + path,
                                         data=None if body is None else json.dumps(body).encode(),
                                         headers=headers, method=method)
        with urllib.request.urlopen(request, timeout=20) as response:
            return json.load(response)

    def adb(*command, binary=False):
        result = subprocess.run([args.adb, '-s', args.serial, *command], capture_output=True, timeout=45)
        if result.returncode:
            raise RuntimeError('ADB failed: ' + result.stderr.decode('utf-8', errors='replace'))
        return result.stdout if binary else result.stdout.decode('utf-8')

    def screen():
        adb('shell', 'uiautomator', 'dump', '/sdcard/civa-inventory-smoke.xml')
        return ET.fromstring(adb('shell', 'cat', '/sdcard/civa-inventory-smoke.xml'))

    def find(tree, *, resource=None, text=None, contains=None, clazz=None):
        return next((n for n in tree.iter('node') if
                     (resource is None or n.get('resource-id') == 'edu.xjtlu.civa:id/' + resource) and
                     (text is None or n.get('text') == text) and
                     (contains is None or contains in n.get('text', '')) and
                     (clazz is None or n.get('class') == clazz)), None)

    def wait(**selector):
        deadline = time.monotonic() + 35
        while time.monotonic() < deadline:
            tree = screen()
            item = find(tree, **selector)
            if item is not None:
                return item
            time.sleep(.4)
        raise RuntimeError('UI control not found: ' + str(selector))

    def tap(node):
        x1, y1, x2, y2 = map(int, re.findall(r'\d+', node.get('bounds')))
        adb('shell', 'input', 'tap', str((x1+x2)//2), str((y1+y2)//2))

    def fill(node, value):
        tap(node)
        adb('shell', 'input', 'text', value)
        adb('shell', 'input', 'keyevent', '4')

    def capture(name):
        (output / name).write_bytes(adb('exec-out', 'screencap', '-p', binary=True))

    owner = home = None
    kept = False
    dictionary = []
    username = 'c_ui_' + secrets.token_hex(5)
    password = 'Civa-' + secrets.token_hex(12)
    try:
        owner = api('POST', '/auth/register', {'username': username, 'displayName': 'C UI test',
                    'password': password, 'zoneId': 'Asia/Shanghai'})['id']
        uuid.UUID(owner)
        token = api('POST', '/auth/login', {'username': username, 'password': password})['token']
        home = api('POST', '/households', {'name': 'C UI isolated test'})['id']
        uuid.UUID(home)
        if sql("SELECT count(*) FROM households WHERE id='" + home + "'") != '1':
            raise RuntimeError('Backend is not using the explicitly guarded test DB')
        today = dt.datetime.now(dt.timezone(dt.timedelta(hours=8))).date()
        fixtures = [('鲜牛奶','COUNT','3',0),('鸡蛋','COUNT','6',0),('酸奶','COUNT','2',0),
                    ('小白菜','G','300',1),('西兰花','G','400',1),('西红柿','G','500',1),
                    ('苹果','G','450',2),('橙子','COUNT','4',2),('牛肉','G','500',3),
                    ('猪肉','G','350',3),('羊肉','G','400',3),('鲜虾','COUNT','12',4),('贝柱','COUNT','8',4)]
        locations=['冷藏 · 蛋奶层','冷藏 · 蔬菜层','冷藏 · 水果层','冷冻 · 肉类抽屉','冷冻 · 海鲜抽屉']
        beef_id = None
        for name, unit, quantity, shelf in fixtures:
            ingredient = str(uuid.uuid4())
            dictionary.append(ingredient)
            alias = 'c_ui_' + ingredient.replace('-', '')
            sql("INSERT INTO ingredients(id,name,unit,active) VALUES ('"+ingredient+"','"+name+"','"+unit+"',true); "
                "INSERT INTO ingredient_aliases(normalized_name,ingredient_id,display_name) VALUES ('"+alias+"','"+ingredient+"','"+name+"');")
            batch = api('POST', '/households/'+home+'/inventory-batches', {
                'ingredientId': ingredient, 'quantity': quantity, 'unit': unit, 'location': locations[shelf],
                'expiresOn': (today+dt.timedelta(days=2 if name=='小白菜' else 10)).isoformat()})
            if name == '牛肉':
                beef_id = batch['id']
        print('PASS: isolated backend fixture and canonical ingredients created', flush=True)
        adb('shell', 'wm', 'size', '1600x1200')
        adb('shell', 'wm', 'density', '160')
        adb('shell', 'am', 'force-stop', 'edu.xjtlu.civa')
        adb('shell', 'am', 'start', '-W', '-n', 'edu.xjtlu.civa/.MainActivity')
        fill(wait(resource='ef_username'), username)
        fill(wait(resource='ef_password'), password)
        tap(wait(resource='ef_submit'))
        tap(wait(resource='app_inventory'))
        wait(contains='已读取 13 个库存批次')
        capture('c-fridge-closed-wide.png')
        tap(wait(resource='ac_open'))
        time.sleep(.7)
        tap(wait(text='牛肉'))
        wait(text='剩余 500 克')
        capture('c-fridge-open-wide.png')
        if len([n for n in screen().iter('node') if n.get('text') == '记录消耗']) != 1:
            raise RuntimeError('Expected exactly one consume button')
        tap(wait(text='记录消耗'))
        fill(wait(clazz='android.widget.EditText'), '150')
        wait(text='确认后剩余 350 克')
        tap(wait(text='确认保存'))
        wait(text='剩余 350 克')
        if sql("SELECT quantity::text FROM inventory_batches WHERE id='"+beef_id+"'") != '350.000':
            raise RuntimeError('UI consumption did not save the expected server quantity')
        capture('c-fridge-consumed-wide.png')
        print('PASS: real Android consumption 500 - 150 = 350, confirmed in PostgreSQL', flush=True)
        # Change metadata through the API while the screen still holds the old version.
        fresh = next(b for b in api('GET', '/households/'+home+'/inventory-batches?size=100')['items'] if b['id'] == beef_id)
        api('PUT', '/households/'+home+'/inventory-batches/'+beef_id, {
            'location': fresh['location'], 'expiresOn': fresh['expiresOn'], 'expectedVersion': fresh['version']})
        tap(wait(text='记录消耗'))
        fill(wait(clazz='android.widget.EditText'), '50')
        tap(wait(text='确认保存'))
        wait(contains='库存已被其他成员修改')
        if sql("SELECT quantity::text FROM inventory_batches WHERE id='"+beef_id+"'") != '350.000':
            raise RuntimeError('A stale screen overwrote the newer version')
        print('PASS: stale UI version rejected, server balance preserved and conflict shown', flush=True)
        before = int(sql("SELECT count(*) FROM inventory_batches WHERE household_id='"+home+"'"))
        tap(wait(resource='ac_add'))
        edits=[n for n in screen().iter('node') if n.get('class')=='android.widget.EditText']
        if not edits:
            raise RuntimeError('Quantity field missing from add form')
        fill(edits[0], '2')
        tap(wait(text='确认保存'))
        deadline=time.monotonic()+20
        while time.monotonic()<deadline:
            if int(sql("SELECT count(*) FROM inventory_batches WHERE household_id='"+home+"'"))==before+1:
                break
            time.sleep(.3)
        else:
            raise RuntimeError('UI add did not create a separate server batch')
        print('PASS: native add form created a new real batch', flush=True)
        tap(wait(resource='ac_refresh'))
        wait(contains='已读取 14 个库存批次')
        tap(wait(resource='ac_open'))
        time.sleep(.6)
        # Open both doors regardless of the previous add location.
        if find(screen(), text='打开冰箱') is not None:
            tap(wait(text='打开冰箱')); time.sleep(.6)
        adb('shell', 'wm', 'size', '800x1280')
        adb('shell', 'wm', 'density', '240')
        time.sleep(2)
        wait(resource='ac_add')
        capture('c-fridge-narrow.png')
        if not adb('shell', 'pidof', 'edu.xjtlu.civa').strip():
            raise RuntimeError('Android process crashed during resize')
        if args.keep_fixtures:
            (work / 'preview-session.json').write_text(json.dumps({'username': username, 'password': password,
                'householdId': home, 'ownerId': owner, 'ingredientIds': dictionary,
                'baseUrl': args.base_url, 'database': args.test_db}, ensure_ascii=False, indent=2), encoding='utf-8')
            kept = True
        print('PASS: wide/narrow resize and retained inventory screen; screenshots saved', flush=True)
    finally:
        if home and not kept:
            sql("DELETE FROM inventory_movements WHERE household_id='"+home+"'; DELETE FROM inventory_batches WHERE household_id='"+home+"'; DELETE FROM memberships WHERE household_id='"+home+"'; DELETE FROM households WHERE id='"+home+"';")
        if owner and not kept:
            sql("DELETE FROM idempotency_requests WHERE actor_id='"+owner+"'; DELETE FROM preferences WHERE user_id='"+owner+"'; DELETE FROM sessions WHERE user_id='"+owner+"'; DELETE FROM users WHERE id='"+owner+"';")
        for ingredient in ([] if kept else dictionary):
            sql("DELETE FROM ingredient_aliases WHERE ingredient_id='"+ingredient+"'; DELETE FROM ingredients WHERE id='"+ingredient+"';")


if __name__ == '__main__':
    main()
