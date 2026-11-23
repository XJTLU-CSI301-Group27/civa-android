"""C inventory native UI smoke test against an explicitly guarded PostgreSQL *_test DB.

Requires an already running backend pointing to that same isolated database,
the current debug APK installed on the named emulator, and PG* credentials.
Uses synthetic fixtures only; never runs on a development/production database.
"""
import argparse
import datetime as dt
from decimal import Decimal
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
from seed_inventory_demo_catalog import CATALOG, ensure_catalog
from inventory_test_fixtures import cleanup_ephemeral_foods


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--adb', required=True)
    parser.add_argument('--psql', required=True)
    parser.add_argument('--serial', default='emulator-5554')
    parser.add_argument('--base-url', default='http://127.0.0.1:8080')
    parser.add_argument('--test-db', required=True)
    parser.add_argument('--keep-fixtures', action='store_true', help='Keep the demo account/common stock, but clean owned random-name foods; credentials stay in ignored build directory')
    parser.add_argument('--add-only', action='store_true', help='Run addition/edit/viewport regression with explicitly API-prepared balances; does not claim to rerun consumption cases')
    parser.add_argument('--depletion-only', action='store_true', help='Run real zero-stock confirmation/return/multiple-batch/refresh regression and clean its synthetic family')
    parser.add_argument('--replenishment-only', action='store_true', help='Verify native egg depletion then native re-add shows the new positive batch in All')
    parser.add_argument('--grouping-only', action='store_true', help='Verify ingredient totals, original batch dates, scoped deductions, filters and wide/phone layout')
    args = parser.parse_args()
    if not args.test_db.endswith('_test'):
        raise RuntimeError('Only a database ending in _test is allowed')
    if args.depletion_only and (args.add_only or args.keep_fixtures):
        raise RuntimeError('--depletion-only runs independently and always cleans its synthetic fixtures')
    if args.replenishment_only and (args.add_only or args.depletion_only or args.keep_fixtures):
        raise RuntimeError('--replenishment-only runs independently and always cleans its synthetic fixtures')
    if args.grouping_only and (args.add_only or args.depletion_only or args.replenishment_only or args.keep_fixtures):
        raise RuntimeError('--grouping-only runs independently and always cleans its synthetic fixtures')
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
    catalog = ensure_catalog(sql)
    english = {name: aliases[0] for name, unit, aliases in CATALOG}
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

    def find(tree, *, resource=None, text=None, contains=None, clazz=None, description=None):
        return next((n for n in tree.iter('node') if
                     (resource is None or n.get('resource-id') == 'edu.xjtlu.civa:id/' + resource) and
                     (text is None or n.get('text') == text) and
                     (contains is None or contains in n.get('text', '')) and
                     (clazz is None or n.get('class') == clazz) and
                     (description is None or n.get('content-desc') == description)), None)

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
        adb('shell', 'input', 'text', value.replace(' ', '%s'))
        adb('shell', 'input', 'keyevent', '4')

    def capture(name):
        (output / name).write_bytes(adb('exec-out', 'screencap', '-p', binary=True))

    def replace(node, value, hide=True):
        tap(node)
        adb('shell', 'input', 'keycombination', '113', '29')
        adb('shell', 'input', 'keyevent', '67')
        if value:
            adb('shell', 'input', 'text', value.replace(' ', '%s'))
        if hide:
            adb('shell', 'input', 'keyevent', '4')

    def bounds(node):
        return tuple(map(int, re.findall(r'\d+', node.get('bounds'))))

    def fit_fridge():
        tree = screen()
        outer = find(tree, resource='ac_workspace')
        cold = find(tree, resource='ac_cold_compartment')
        frozen = find(tree, resource='ac_frozen_compartment')
        if any(n is None for n in (outer, cold, frozen)):
            raise RuntimeError('Both fridge compartments must be present on the first screen')
        left, top, right, bottom = bounds(outer)
        for node in (cold, frozen):
            x1,y1,x2,y2 = bounds(node)
            if not (left <= x1 < x2 <= right and top <= y1 < y2 <= bottom):
                raise RuntimeError('Fridge compartment extends beyond the visible workspace')
        if abs((bounds(cold)[3]-bounds(cold)[1])-(bounds(frozen)[3]-bounds(frozen)[1])) > 2:
            raise RuntimeError('Cold and frozen compartments must have equal heights')
        if any(n.get('class')=='android.widget.ScrollView' and
               n.get('resource-id')!='edu.xjtlu.civa:id/ac_detail_scroll' for n in tree.iter('node')):
            raise RuntimeError('Main fridge screen must not need vertical scrolling; only the independent detail pane may scroll')

    def until(check, description):
        deadline=time.monotonic()+25
        while time.monotonic()<deadline:
            if check(): return
            time.sleep(.3)
        raise RuntimeError(description)

    def open_inventory():
        # Let the existing login/household navigation finish before selecting the feature.
        # A resumed login callback may redirect the first selection back to household.
        for attempt in range(3):
            tap(wait(resource='app_inventory'))
            time.sleep(.5)
            if find(screen(),resource='ac_add') is not None:
                if attempt:
                    print('NOTE: reselected Inventory after existing login/household navigation settled',flush=True)
                return
        raise RuntimeError('Existing navigation did not enter Inventory')

    owner = home = None
    kept = False
    dictionary = []
    named = {}
    username = 'c_ui_' + secrets.token_hex(5)
    password = 'Civa-' + secrets.token_hex(12)
    try:
        owner = api('POST', '/auth/register', {'username': username, 'displayName': 'C UI test',
                    'password': password, 'zoneId': 'Asia/Shanghai'})['id']
        uuid.UUID(owner)
        token = api('POST', '/auth/login', {'username': username, 'password': password})['token']
        # Check ownership before exercising any UI writes.
        if sql("SELECT count(*) FROM users WHERE id='"+owner+"'") != '1':
            raise RuntimeError('Backend is not using the explicitly guarded test DB')
        adb('shell', 'wm', 'size', '1600x1200')
        adb('shell', 'wm', 'density', '160')
        adb('shell', 'am', 'force-stop', 'edu.xjtlu.civa')
        adb('shell', 'am', 'start', '-W', '-n', 'edu.xjtlu.civa/.MainActivity')
        fill(wait(resource='ef_username'), username)
        fill(wait(resource='ef_password'), password)
        tap(wait(resource='ef_submit'))
        wait(resource='ef_create')
        open_inventory()
        add=wait(resource='ac_add')
        if add.get('enabled') != 'true':
            raise RuntimeError('Fresh-account add entry must explain the missing household, not silently disable')
        tap(add)
        wait(text='先设置家庭')
        if sql("SELECT count(*) FROM memberships WHERE user_id='"+owner+"'") != '0':
            raise RuntimeError('No-household prompt unexpectedly created membership')
        tap(wait(text='前往家庭'))
        fill(wait(resource='ef_household_name'), 'C UI isolated test')
        tap(wait(resource='ef_create'))
        wait(resource='app_title')
        home=sql("SELECT household_id::text FROM memberships WHERE user_id='"+owner+"' AND active")
        uuid.UUID(home)
        open_inventory()
        wait(resource='ac_cold_compartment')
        fit_fridge()
        print('PASS: fresh account add explains missing household; existing family setup and empty fridge work', flush=True)

        today = dt.datetime.now(dt.timezone(dt.timedelta(hours=8))).date()
        fixtures = [('鲜牛奶','COUNT','3',0),('鸡蛋','COUNT','10' if args.grouping_only else '6',0),('酸奶','COUNT','2',0),
                    ('小白菜','G','300',1),('西兰花','G','400',1),('西红柿','G','500',1),
                    ('苹果','COUNT','3',2),('橙子','COUNT','4',2),('牛肉','G','500',3),
                    ('猪肉','G','350',3),('羊肉','G','400',3),('鲜虾','COUNT','12',4),('贝柱','COUNT','8',4)]
        locations=['冷藏 · 蛋奶层','冷藏 · 蔬菜层','冷藏 · 水果层','冷冻 · 肉类抽屉','冷冻 · 海鲜抽屉']
        for name, unit, quantity, shelf in fixtures:
            ingredient = catalog[name]
            alias = english[name]
            days=-1 if name=='酸奶' else 2 if name in ('鲜牛奶','小白菜') else 10
            if args.grouping_only and name=='鸡蛋': days=2
            batch=api('POST', '/households/'+home+'/inventory-batches', {'ingredientId':ingredient,
                'quantity':quantity, 'unit':unit, 'location':locations[shelf], 'expiresOn':(today+dt.timedelta(days=days)).isoformat()})
            named[name]={'id':batch['id'],'ingredient':ingredient,'alias':alias}
        beef_id=named['牛肉']['id']; milk_id=named['鲜牛奶']['id']
        def balance(batch):
            return Decimal(sql("SELECT quantity::text FROM inventory_batches WHERE id='"+batch+"'"))
        def count():
            return int(sql("SELECT count(*) FROM inventory_batches WHERE household_id='"+home+"'"))
        def select_food_batch(name, batch):
            tap(wait(text=name))
            tree=screen()
            if find(tree, text='关闭') is not None and find(tree, contains='批次 '+batch[:8]) is not None:
                tap(wait(contains='批次 '+batch[:8]))
        def open_change(label, amount):
            tap(wait(text=label))
            replace(wait(resource='ac_quantity_input'), amount)
        def save_balance(batch, expected):
            tap(wait(text='确认保存'))
            until(lambda: balance(batch)==Decimal(expected), 'UI write did not save the expected balance')
            wait(text='剩余 '+expected+' '+('瓶' if batch==milk_id else '克'))

        def deplete_last_milk():
            tap(wait(text='鲜牛奶')); wait(text='剩余 3 瓶')
            movements=int(sql("SELECT count(*) FROM inventory_movements WHERE batch_id='"+milk_id+"'"))
            tap(wait(text='记录消耗')); tap(wait(text='全部用完'))
            wait(text='确认后剩余 0 瓶'); tap(wait(text='确认保存'))
            wait(text='确认食材已用完'); wait(contains='所有存储将全部用完')
            if balance(milk_id)!=3 or int(sql("SELECT count(*) FROM inventory_movements WHERE batch_id='"+milk_id+"'"))!=movements:
                raise RuntimeError('Zero-stock prompt wrote before final confirmation')
            tap(wait(text='返回修改用量'))
            if wait(resource='ac_quantity_input').get('text')!='3':
                raise RuntimeError('Returning from depletion lost the entered amount')
            replace(wait(resource='ac_quantity_input'),'1'); save_balance(milk_id,'2')
            tap(wait(text='记录消耗')); tap(wait(text='全部用完')); tap(wait(text='确认保存'))
            wait(text='确认食材已用完')
            if balance(milk_id)!=2: raise RuntimeError('Exhaustion prompt changed stock before confirming')
            tap(wait(text='确认用完')); until(lambda:balance(milk_id)==0,'Final confirmed depletion failed')
            until(lambda:find(screen(),text='鲜牛奶') is None,'Exhausted milk remained in fridge or detail')
            if sql("SELECT count(*) FROM inventory_batches WHERE id='"+milk_id+"'")!='1':
                raise RuntimeError('Depletion deleted batch history instead of hiding the empty card')
            if int(sql("SELECT count(*) FROM inventory_movements WHERE batch_id='"+milk_id+"'"))!=movements+2:
                raise RuntimeError('Depletion failed to preserve exact traceable movements')
            tap(wait(resource='ac_refresh')); time.sleep(1)
            if find(screen(),text='鲜牛奶') is not None: raise RuntimeError('Refresh resurrected zero stock')
            print('PASS: zero prompt writes nothing; return preserves input and allows 3->2; final confirm saves zero, hides card/detail and preserves history after refresh',flush=True)

        if args.grouping_only:
            egg_id=named['鸡蛋']['id']
            second_beef=api('POST','/households/'+home+'/inventory-batches',{
                'ingredientId':named['牛肉']['ingredient'],'quantity':'250','unit':'G',
                'location':locations[3],'expiresOn':(today-dt.timedelta(days=1)).isoformat()})['id']
            def card(name, amount, unit):
                nodes=[n for n in screen().iter('node') if n.get('content-desc','').startswith(name+'，剩余')]
                if len(nodes)!=1 or not nodes[0].get('content-desc','').startswith(name+'，剩余'+amount+unit):
                    raise RuntimeError('Expected exactly one '+name+' card totaling '+amount+unit)
                return nodes[0]
            tap(wait(resource='ac_refresh'));time.sleep(1);tap(wait(resource='ac_open'))
            card('鸡蛋','10','个')
            before=count();tap(wait(resource='ac_add'));replace(wait(resource='ac_food_name'),'egg')
            wait(description='单位：个');replace(wait(resource='ac_quantity_input'),'2')
            new_expiry=(today+dt.timedelta(days=20)).isoformat()
            replace(wait(resource='ac_expiry_input'),new_expiry);tap(wait(text='确认保存'))
            until(lambda:count()==before+1,'Native addition did not create the second egg batch')
            wait(text='剩余 2 个');wait(text='总库存 12 个 · 2批')
            eggs=[b for b in api('GET','/households/'+home+'/inventory-batches?size=100')['items']
                  if b['ingredientId']==named['鸡蛋']['ingredient']]
            new_egg=next(b for b in eggs if b['id']!=egg_id)
            old_egg=next(b for b in eggs if b['id']==egg_id)
            if len(eggs)!=2 or balance(egg_id)!=10 or Decimal(new_egg['quantity'])!=2 or new_egg['expiresOn']!=new_expiry:
                raise RuntimeError('Display grouping merged or rewrote stored egg batches')
            card('鸡蛋','12','个');card('牛肉','750','克');fit_fridge()
            capture('c-fridge-grouped-wide.png')
            tap(card('鸡蛋','12','个'));wait(text='鸡蛋 · 共 12 个 · 2批')
            rows=[n.get('text','') for n in screen().iter('node')]
            for batch in eggs:
                row=next((t for t in rows if '批次 '+batch['id'][:8] in t),'')
                created=dt.datetime.fromisoformat(batch['createdAt'].replace('Z','+00:00')).astimezone(dt.timezone(dt.timedelta(hours=8))).strftime('%Y-%m-%d %H:%M:%S')
                if '入库 '+created not in row or '到期 '+batch['expiresOn'] not in row or batch['quantity']+' 个' not in row:
                    raise RuntimeError('Batch row lost its original quantity, received timestamp or expiry')
            capture('c-fridge-grouped-batches-wide.png');tap(wait(text='关闭'))
            tap(card('牛肉','750','克'));wait(text='牛肉 · 共 750 克 · 2批')
            tap(wait(contains='批次 '+second_beef[:8]));wait(text='剩余 250 克')
            if wait(text='已过期，请检查处理').get('enabled')!='false':
                raise RuntimeError('Grouped expired batch bypassed consumption guard')
            wait(text='未过期可用 500 克')
            tap(wait(text='临期'));wait(text='鸡蛋');card('鸡蛋','10','个')
            tap(wait(text='全部'));wait(text='鸡蛋');card('鸡蛋','12','个')
            print('PASS: native egg 10+2 is one 12-count card; both original timestamps/expiry dates retained; freezer totals 750g with expired consumption blocked; filter totals stay scoped',flush=True)
            adb('shell','wm','size','1080x2340');adb('shell','wm','density','420');time.sleep(1)
            wait(resource='ac_cold_compartment');fit_fridge();tap(card('鸡蛋','12','个'))
            wait(text='鸡蛋 · 共 12 个 · 2批');capture('c-fridge-grouped-batches-phone.png')
            tap(wait(contains='批次 '+egg_id[:8]));wait(text='剩余 10 个');wait(text='总库存 12 个 · 2批')
            tap(wait(text='记录消耗'));replace(wait(resource='ac_quantity_input'),'3');tap(wait(text='确认保存'))
            until(lambda:balance(egg_id)==7,'Selected egg batch did not deduct 3')
            wait(text='剩余 7 个');wait(text='总库存 9 个 · 2批')
            if balance(new_egg['id'])!=2: raise RuntimeError('Consumption changed the other batch')
            adb('shell','input','keyevent','4');card('鸡蛋','9','个');capture('c-fridge-grouped-phone.png')
            tap(card('鸡蛋','9','个'));tap(wait(contains='批次 '+egg_id[:8]));wait(text='剩余 7 个')
            tap(wait(text='记录消耗'));tap(wait(text='全部用完'));tap(wait(text='确认保存'))
            wait(contains='其他批次如有剩余');tap(wait(text='确认用完'))
            until(lambda:balance(egg_id)==0,'First grouped batch did not deplete')
            until(lambda:find(screen(),text='冰箱里的小档案') is None,'Depleted group batch detail did not close')
            card('鸡蛋','2','个');tap(card('鸡蛋','2','个'));wait(text='剩余 2 个')
            tap(wait(text='记录消耗'));tap(wait(text='全部用完'));tap(wait(text='确认保存'))
            wait(contains='所有存储将全部用完');tap(wait(text='确认用完'))
            until(lambda:balance(new_egg['id'])==0,'Last grouped batch did not deplete')
            until(lambda:not any(n.get('content-desc','').startswith('鸡蛋，剩余') for n in screen().iter('node')),
                  'Empty ingredient group remained visible')
            tap(wait(resource='ac_refresh'));time.sleep(1)
            if any(n.get('content-desc','').startswith('鸡蛋，剩余') for n in screen().iter('node')):
                raise RuntimeError('Refresh restored an empty ingredient group')
            if sql("SELECT count(*) FROM inventory_batches WHERE ingredient_id='"+named['鸡蛋']['ingredient']+"' AND household_id='"+home+"'")!='2':
                raise RuntimeError('Grouped depletion deleted history')
            print('PASS: phone group picks a specific batch; 12->9->2->hidden totals track actual deductions; other batch stays unchanged; last confirmation/refresh preserve hidden-zero and history behavior',flush=True)
            return

        if args.replenishment_only:
            egg_id=named['鸡蛋']['id']
            adb('shell','wm','size','1080x2340');adb('shell','wm','density','420');time.sleep(1)
            tap(wait(resource='ac_refresh'));time.sleep(1)
            tap(wait(resource='ac_open'));tap(wait(text='鸡蛋'));wait(text='剩余 6 个')
            tap(wait(text='记录消耗'));tap(wait(text='全部用完'));tap(wait(text='确认保存'))
            wait(text='确认食材已用完')
            if balance(egg_id)!=6: raise RuntimeError('Egg depletion wrote before confirmation')
            tap(wait(text='确认用完'));until(lambda:balance(egg_id)==0,'Egg depletion was not saved')
            until(lambda:find(screen(),text='鸡蛋') is None,'Used-up egg card/detail did not disappear')
            before=count();tap(wait(resource='ac_add'));replace(wait(resource='ac_food_name'),'egg')
            wait(description='单位：个');replace(wait(resource='ac_quantity_input'),'2')
            tap(wait(text='确认保存'));until(lambda:count()==before+1,'New egg batch was not created')
            wait(text='剩余 2 个')
            eggs=[b for b in api('GET','/households/'+home+'/inventory-batches?size=100')['items']
                  if b['ingredientId']==named['鸡蛋']['ingredient']]
            positive=[b for b in eggs if Decimal(b['quantity'])>0]
            if len(eggs)!=2 or len(positive)!=1 or Decimal(positive[0]['quantity'])!=2 or balance(egg_id)!=0:
                raise RuntimeError('Replenishment rewrote old eggs or failed to create the new positive batch')
            wait(contains=positive[0]['id'][:8])
            # The native detail sheet is modal on phones; close it before
            # inspecting the underlying All filter and fridge tiles.
            adb('shell','input','keyevent','4')
            if wait(text='全部').get('checked')!='true':
                raise RuntimeError('Replenished eggs are not in All view')
            for refresh in (False,True):
                if refresh:
                    tap(wait(resource='ac_refresh'));time.sleep(1)
                tree=screen()
                descriptions=[n.get('content-desc','') for n in tree.iter('node')]
                if not any(s.startswith('鸡蛋，剩余2个') for s in descriptions) or any(s.startswith('鸡蛋，剩余0个') for s in descriptions):
                    raise RuntimeError('All view shows old zero eggs instead of the new 2-count batch')
            tap(wait(contains='蛋奶 · 每日补给'));wait(contains='批次 '+positive[0]['id'][:8])
            if find(screen(),contains='批次 '+egg_id[:8]) is not None:
                raise RuntimeError('Egg picker still lists the exhausted old batch')
            tap(wait(text='关闭'))
            print('PASS: native egg 6->0 confirmation hides old card; native add egg 2 selects new batch; All and refresh show 2 not 0; old zero history remains outside picker',flush=True)
            return

        if args.depletion_only:
            tap(wait(resource='ac_refresh')); time.sleep(1)
            tap(wait(resource='ac_open')); wait(text='鲜牛奶')
            deplete_last_milk()
            extra=[]
            for amount,days in [('2',5),('1',6)]:
                extra.append(api('POST','/households/'+home+'/inventory-batches',{
                    'ingredientId':named['鲜牛奶']['ingredient'],'quantity':amount,'unit':'COUNT',
                    'location':locations[0],'expiresOn':(today+dt.timedelta(days=days)).isoformat()})['id'])
            adb('shell','wm','size','1080x2340');adb('shell','wm','density','420');time.sleep(1)
            tap(wait(resource='ac_refresh'));time.sleep(1)
            select_food_batch('鲜牛奶',extra[0]);wait(text='剩余 2 瓶')
            tap(wait(text='记录消耗'));tap(wait(text='全部用完'));tap(wait(text='确认保存'))
            wait(contains='其他批次如有剩余')
            if balance(extra[0])!=2 or balance(extra[1])!=1:
                raise RuntimeError('Multi-batch depletion wrote before confirming')
            tap(wait(text='确认用完'));until(lambda:balance(extra[0])==0,'Selected batch was not depleted')
            until(lambda:find(screen(),text='剩余 0 瓶') is None and find(screen(),text='冰箱里的小档案') is None,
                  'Narrow depleted detail did not close')
            tap(wait(text='鲜牛奶'));wait(text='剩余 1 瓶')
            if balance(extra[1])!=1: raise RuntimeError('Another batch was incorrectly consumed or removed')
            adb('shell','input','keyevent','4')
            tap(wait(contains='蛋奶 · 每日补给'));wait(contains='批次 '+extra[1][:8])
            tree=screen()
            if any(find(tree,contains='批次 '+item[:8]) is not None for item in [milk_id,extra[0]]):
                raise RuntimeError('Empty batches remained in the shelf batch picker')
            tap(wait(text='关闭'));tap(wait(text='鲜牛奶'));wait(text='剩余 1 瓶')
            tap(wait(text='记录消耗'));replace(wait(resource='ac_quantity_input'),'1');tap(wait(text='确认保存'))
            wait(contains='所有存储将全部用完')
            adb('shell','input','keyevent','4')
            if balance(extra[1])!=1 or wait(resource='ac_quantity_input').get('text')!='1':
                raise RuntimeError('Back from final confirmation wrote stock or lost input')
            tap(wait(text='确认保存'));tap(wait(text='确认用完'))
            until(lambda:balance(extra[1])==0,'Last remaining batch failed to deplete')
            until(lambda:find(screen(),text='鲜牛奶') is None,'Last food remained visible')
            tap(wait(resource='ac_refresh'));time.sleep(1)
            if find(screen(),text='鲜牛奶') is not None: raise RuntimeError('Empty milk returned after refresh')
            print('PASS: phone confirmation closes depleted detail; only selected batch disappears; remaining batch stays; picker hides zero; Back cancels final write; last batch disappears after confirm/refresh',flush=True)
            print('PASS: focused depletion regression completed against real Android, HTTP and PostgreSQL; synthetic fixtures will be cleaned',flush=True)
            return

        if args.add_only:
            for batch, amount, reason in [(beef_id,'400','CORRECT'),(milk_id,'0','CONSUME')]:
                api('POST','/households/'+home+'/inventory-batches/'+batch+'/movements',
                    {'afterQuantity':amount,'reason':reason,'expectedVersion':1})
            tap(wait(resource='ac_refresh')); time.sleep(1)
            tap(wait(resource='ac_open')); wait(text='牛肉')
            print('SETUP: addition-only balances prepared through API; consumption cases are not rerun',flush=True)
        else:
            tap(wait(resource='ac_refresh')); time.sleep(1)
            fit_fridge(); capture('c-fridge-closed-wide.png')
            tap(wait(description='打开冷藏门')); wait(text='鲜牛奶')
            wait(description='打开冷冻门')
            tap(wait(description='打开冷冻门')); wait(text='牛肉')
            tap(wait(text='冷藏门 · 已打开')); wait(description='打开冷藏门')
            tap(wait(text='冷藏门 · 关闭')); wait(text='鲜牛奶')
            fit_fridge()
            print('PASS: overall fridge fits; cold and frozen doors open/close independently with all five shelves', flush=True)

            tap(wait(text='临期')); wait(text='小白菜')
            if find(screen(), text='酸奶') is not None: raise RuntimeError('Expired batch leaked into soon filter')
            tap(wait(text='已过期')); wait(text='酸奶'); tap(wait(text='酸奶'))
            expired=wait(text='已过期，请检查处理')
            if expired.get('enabled') != 'false': raise RuntimeError('Expired food must not be consumable')
            tap(wait(text='未过期')); wait(text='鲜牛奶')
            if find(screen(), text='酸奶') is not None: raise RuntimeError('Expired batch leaked into usable filter')
            tap(wait(text='全部')); wait(text='牛肉')
            print('PASS: all/usable/soon/expired filters and expired consumption guard', flush=True)

            tap(wait(text='牛肉')); wait(text='剩余 500 克'); capture('c-fridge-open-wide.png')
            if len([n for n in screen().iter('node') if n.get('text')=='记录消耗'])!=1:
                raise RuntimeError('Expected exactly one consume button')
            open_change('记录消耗','501'); tap(wait(text='确认保存')); wait(resource='ac_form_error')
            if balance(beef_id)!=500: raise RuntimeError('Overdraw changed inventory')
            replace(wait(resource='ac_quantity_input'),'150'); wait(text='确认后剩余 350 克')
            tap(wait(text='取消'))
            if balance(beef_id)!=500: raise RuntimeError('Cancelled consumption changed inventory')
            open_change('记录消耗','150'); wait(text='确认后剩余 350 克'); save_balance(beef_id,'350')
            wait(contains='最近消耗：500 → 350')
            capture('c-fridge-consumed-wide.png')
            print('PASS: single consume entry; overdraw rejected inline; cancel preserves stock; 500-150=350 and real recent movement shown', flush=True)

            fresh=next(b for b in api('GET','/households/'+home+'/inventory-batches?size=100')['items'] if b['id']==beef_id)
            api('PUT','/households/'+home+'/inventory-batches/'+beef_id,{'location':fresh['location'],
                'expiresOn':fresh['expiresOn'],'expectedVersion':fresh['version']})
            open_change('记录消耗','50'); tap(wait(text='确认保存')); wait(contains='库存已被其他成员修改')
            if balance(beef_id)!=350: raise RuntimeError('Stale UI overwrote inventory')
            open_change('记录丢弃','50'); save_balance(beef_id,'300')
            open_change('库存修正','400'); save_balance(beef_id,'400')
            wait(contains='最近修正：300 → 400')
            print('PASS: stale write rejected; discard and correction save traceable actual balances', flush=True)

            deplete_last_milk()

        before=count()
        tap(wait(text='已过期')); wait(text='酸奶')
        tap(wait(resource='ac_add'))
        for typed, expected_unit in [('beef','单位：克'),('egg','单位：个'),('apple','单位：个'),('apples','单位：个'),('milk','单位：瓶')]:
            replace(wait(resource='ac_food_name'),typed)
            wait(description=expected_unit)
            if wait(resource='ac_food_name').get('text')!=typed:
                raise RuntimeError('Typing food name was overwritten')
        # Confirm without clicking a candidate: exact English names must save directly.
        replace(wait(resource='ac_quantity_input'),'0'); tap(wait(text='确认保存')); wait(text='新增数量必须大于0')
        replace(wait(resource='ac_quantity_input'),'2')
        replace(wait(resource='ac_expiry_input'),'2026-02-30'); tap(wait(text='确认保存')); wait(contains='日期请填写有效的')
        if count()!=before: raise RuntimeError('Invalid add created a batch')
        replace(wait(resource='ac_expiry_input'),(today+dt.timedelta(days=7)).isoformat())
        tap(wait(text='确认保存')); until(lambda:count()==before+1,'Adding same ingredient failed to create a separate batch')
        wait(text='剩余 2 瓶')
        if wait(text='全部').get('checked')!='true':
            raise RuntimeError('Adding from a filtered view did not update the visible filter state')
        batches=api('GET','/households/'+home+'/inventory-batches?size=100')['items']
        added=next(b for b in batches if b['ingredientId']==named['鲜牛奶']['ingredient'] and b['id']!=milk_id)
        if added['location']!=locations[0] or balance(milk_id)!=0: raise RuntimeError('Default shelf or independent batch balance is wrong')
        tap(wait(resource='ac_add')); tap(wait(text='取消'))
        if count()!=before+1: raise RuntimeError('Cancelled add created inventory')
        print('PASS: typed beef/egg/milk resolve without list selection, units update correctly; positive quantity/date validation, independent milk batch and cancellation verified', flush=True)

        tap(wait(text='修改位置 / 到期日'))
        replace(wait(resource='ac_expiry_input'),'')
        tap(wait(text='确认保存'))
        until(lambda:sql("SELECT expires_on IS NULL FROM inventory_batches WHERE id='"+added['id']+"'")=='t','Clearing expiry did not persist')
        wait(text='到期日期未知')
        tap(wait(resource='ac_refresh')); time.sleep(1)
        if balance(beef_id)!=400 or balance(added['id'])!=2 or balance(milk_id)!=0: raise RuntimeError('Refresh lost saved stock')
        tap(wait(contains='蛋奶 · 每日补给'))
        wait(contains='批次 '+added['id'][:8]); tap(wait(text='关闭'))
        print('PASS: edit/unknown expiry persists; refresh preserves stock; overflow shelf exposes every batch', flush=True)

        before=count(); tap(wait(resource='ac_add'))
        replace(wait(resource='ac_food_name'),'beef'); wait(description='单位：克')
        replace(wait(resource='ac_quantity_input'),'50'); tap(wait(text='确认保存'))
        until(lambda:count()==before+1,'Typed beef did not create inventory without list selection')
        wait(text='剩余 50 克')
        new_beef=[b for b in api('GET','/households/'+home+'/inventory-batches?size=100')['items']
                  if b['ingredientId']==named['牛肉']['ingredient'] and b['id']!=beef_id]
        if len(new_beef)!=1 or new_beef[0]['unit']!='G' or new_beef[0]['location']!=locations[3]:
            raise RuntimeError('Typed beef saved the wrong standard ID, unit or location')
        print('PASS: typing beef and confirming creates real 50 gram beef batch without choosing a list item',flush=True)

        before=count(); tap(wait(resource='ac_add'))
        replace(wait(resource='ac_food_name'),'apple'); wait(description='单位：个')
        replace(wait(resource='ac_quantity_input'),'1'); tap(wait(text='确认保存'))
        until(lambda:count()==before+1,'Typed apple did not create count inventory')
        wait(text='剩余 1 个')
        apples=[b for b in api('GET','/households/'+home+'/inventory-batches?size=100')['items']
                if b['ingredientId']==named['苹果']['ingredient'] and b['id']!=named['苹果']['id']]
        if len(apples)!=1 or apples[0]['unit']!='COUNT' or Decimal(apples[0]['quantity'])!=1 or apples[0]['location']!=locations[2]:
            raise RuntimeError('Typed apple saved the wrong standard ID, unit, quantity or fruit shelf')
        print('PASS: apple/apples automatically use count; typing apple saves a real one-item fruit batch',flush=True)

        before=count(); tap(wait(resource='ac_add'))
        replace(wait(resource='ac_food_name'),'beef'); tap(wait(resource='ac_unit_kg'))
        wait(description='单位：千克'); replace(wait(resource='ac_quantity_input'),'1.25')
        # The input and three unit choices share the same horizontal row.
        input_bounds=bounds(wait(resource='ac_quantity_input'))
        for control in ['ac_unit_g','ac_unit_kg','ac_unit_count']:
            item_bounds=bounds(wait(resource=control))
            if item_bounds[0] < input_bounds[2] or item_bounds[1]>=input_bounds[3] or item_bounds[3]<=input_bounds[1]:
                raise RuntimeError('Unit choices must stay beside the quantity input, not on another row')
        for control, label in [('ac_unit_g','g'),('ac_unit_kg','kg'),('ac_unit_count','pcs')]:
            if wait(resource=control).get('text')!=label:
                raise RuntimeError('Unit buttons must read g / kg / pcs')
        if wait(resource='ac_unit_count').get('enabled')!='false':
            raise RuntimeError('Canonical mass stock must not silently accept count')
        tap(wait(text='确认保存')); until(lambda:count()==before+1,'Kilogram entry did not save')
        wait(text='剩余 1250 克')
        if not any(b['ingredientId']==named['牛肉']['ingredient'] and b['unit']=='G' and Decimal(b['quantity'])==1250
                   for b in api('GET','/households/'+home+'/inventory-batches?size=100')['items']):
            raise RuntimeError('Kilogram entry was not converted exactly to canonical grams')
        print('PASS: quantity and three buttons share one row; 1.25 kg saves exactly 1250 g; incompatible count is disabled',flush=True)

        before=count(); fruit_name='fresh pear '+secrets.token_hex(4)
        tap(wait(resource='ac_add')); replace(wait(resource='ac_food_name'),fruit_name)
        wait(text='冷藏 · 水果层'); tap(wait(resource='ac_unit_count'))
        replace(wait(resource='ac_quantity_input'),'2'); tap(wait(text='确认保存'))
        until(lambda:count()==before+1,'New pear name did not save')
        wait(text='剩余 2 个')
        fruit_id=sql("SELECT id::text FROM ingredients WHERE name='"+fruit_name+"'")
        uuid.UUID(fruit_id); dictionary.append(fruit_id)
        if not any(b['ingredientId']==fruit_id and b['unit']=='COUNT' and b['location']==locations[2]
                   for b in api('GET','/households/'+home+'/inventory-batches?size=100')['items']):
            raise RuntimeError('New pear entry was not categorized as fruit')
        print('PASS: previously unknown English pear name automatically selects fruit shelf and saves two items',flush=True)

        before=count(); fresh_name='oat flakes '+secrets.token_hex(4)
        tap(wait(resource='ac_add')); replace(wait(resource='ac_food_name'),fresh_name)
        replace(wait(resource='ac_quantity_input'),'75'); tap(wait(text='确认保存'))
        wait(text='请选择数量右侧的单位')
        if count()!=before: raise RuntimeError('Unknown unit was silently guessed')
        tap(wait(resource='ac_unit_g'))
        tap(wait(text='确认保存')); until(lambda:count()==before+1,'New typed English food did not save')
        wait(text='剩余 75 克')
        created_id=sql("SELECT id::text FROM ingredients WHERE name='"+fresh_name+"'")
        uuid.UUID(created_id); dictionary.append(created_id)
        created_batches=api('GET','/households/'+home+'/inventory-batches?size=100')['items']
        if not any(b['ingredientId']==created_id and b['unit']=='G' and Decimal(b['quantity'])==75 for b in created_batches):
            raise RuntimeError('New name was not linked to a real standard ingredient and stock batch')
        print('PASS: arbitrary previously unknown English name plus explicit unit creates real shared ingredient and household stock',flush=True)

        for width,height,density,label in [(800,1280,240,'narrow'),(1280,800,160,'tablet-landscape'),(1080,2340,420,'phone')]:
            adb('shell','wm','size',str(width)+'x'+str(height)); adb('shell','wm','density',str(density)); time.sleep(2)
            wait(resource='ac_cold_compartment'); fit_fridge()
            capture('c-fridge-'+label+'.png')
        select_food_batch('牛肉',beef_id); wait(text='剩余 400 克')
        if find(screen(),text='记录消耗') is None: raise RuntimeError('Narrow food details did not open')
        adb('shell','input','keyevent','4'); fit_fridge()
        print('PASS: complete fridge fits narrow, landscape tablet and phone without scrolling; narrow details open separately', flush=True)
        removed, retained = cleanup_ephemeral_foods(sql, owner, home, dictionary)
        if retained:
            raise RuntimeError('A generated ingredient acquired external references; preserved for inspection')
        if any(sql("SELECT count(*) FROM ingredients WHERE id='"+i+"'")!='0' for i in removed):
            raise RuntimeError('Random test food remained in the shared dictionary')
        dictionary=[]
        if any(i['id'] in removed for i in api('GET','/ingredients?q=pear&size=100')['items']):
            raise RuntimeError('Removed test food remained in the ingredient API')
        tap(wait(resource='ac_refresh')); time.sleep(1)
        tap(wait(resource='ac_add')); replace(wait(resource='ac_food_name'),'pear')
        if any(re.search(r'(fresh pear|oat flakes) [0-9a-f]{8}',n.get('text','')) for n in screen().iter('node')):
            raise RuntimeError('Search candidates still display generated test names')
        tap(wait(text='取消'))
        print('PASS: generated foods removed from DB/API/native candidates even with --keep-fixtures; no random pear suffix in search',flush=True)
        if args.keep_fixtures:
            (work/'preview-session.json').write_text(json.dumps({'username':username,'password':password,
                'householdId':home,'ownerId':owner,'ingredientIds':list(catalog.values())+dictionary,'ownedIngredientIds':dictionary,'sharedCatalog':True,
                'baseUrl':args.base_url,'database':args.test_db},ensure_ascii=False,indent=2),encoding='utf-8')
            kept=True
        print('PASS: '+('addition/edit/layout checks' if args.add_only else 'all demo inventory checks')+' completed against native app and real isolated database', flush=True)
    finally:
        if home and dictionary and not kept:
            cleanup_ephemeral_foods(sql, owner, home, dictionary)
            dictionary=[]
        if home and not kept:
            sql("DELETE FROM inventory_movements WHERE household_id='"+home+"'; DELETE FROM inventory_batches WHERE household_id='"+home+"'; DELETE FROM memberships WHERE household_id='"+home+"'; DELETE FROM households WHERE id='"+home+"';")
        if owner and not kept:
            sql("DELETE FROM idempotency_requests WHERE actor_id='"+owner+"'; DELETE FROM preferences WHERE user_id='"+owner+"'; DELETE FROM sessions WHERE user_id='"+owner+"'; DELETE FROM users WHERE id='"+owner+"';")


if __name__ == '__main__':
    main()
