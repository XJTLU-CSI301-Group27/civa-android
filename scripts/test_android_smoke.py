"""Device smoke test. Creates one synthetic account and household in the development backend."""
import argparse
import os
from pathlib import Path
import re
import secrets
import shutil
import subprocess
import time
import xml.etree.ElementTree as ET


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--serial', default='emulator-5554')
    parser.add_argument('--adb', default=shutil.which('adb') or str(Path(os.environ.get('ANDROID_HOME', '')) / 'platform-tools' / 'adb.exe'))
    args = parser.parse_args()

    def adb(*command):
        result = subprocess.run([args.adb, '-s', args.serial, *command], capture_output=True, text=True, encoding='utf-8', timeout=45)
        if result.returncode:
            raise RuntimeError(result.stderr.strip() or 'ADB command failed')
        return result.stdout

    def screen():
        adb('shell', 'uiautomator', 'dump', '/sdcard/civa-smoke.xml')
        return ET.fromstring(adb('shell', 'cat', '/sdcard/civa-smoke.xml'))

    def control(tree, name):
        return next((n for n in tree.iter('node') if n.get('resource-id') == 'edu.xjtlu.civa:id/' + name), None)

    def tap(tree, name):
        element = control(tree, name)
        if element is None:
            raise RuntimeError('Missing UI control: ' + name)
        left, top, right, bottom = map(int, re.findall(r'\d+', element.get('bounds')))
        adb('shell', 'input', 'tap', str((left + right) // 2), str((top + bottom) // 2))

    def fill(tree, name, value):
        tap(tree, name)
        adb('shell', 'input', 'text', value)
        adb('shell', 'input', 'keyevent', '4')

    def wait_for(name):
        deadline = time.monotonic() + 30
        while time.monotonic() < deadline:
            tree = screen()
            if control(tree, name) is not None:
                return tree
            time.sleep(1)
        raise RuntimeError('UI transition timed out: ' + name)

    if adb('shell', 'getprop', 'sys.boot_completed').strip() != '1':
        raise RuntimeError('Wait for the device to finish booting before testing.')
    adb('shell', 'am', 'force-stop', 'edu.xjtlu.civa')
    adb('shell', 'am', 'start', '-W', '-n', 'edu.xjtlu.civa/.MainActivity')
    tree = wait_for('ef_switch')
    tap(tree, 'ef_switch')
    tree = wait_for('ef_display_name')
    fill(tree, 'ef_username', 'handoff_' + secrets.token_hex(4))
    fill(tree, 'ef_display_name', 'HandoffSmoke')
    fill(tree, 'ef_password', 'Civa' + secrets.token_hex(12))
    tap(tree, 'ef_submit')
    tree = wait_for('ef_message')
    if '账号已创建' not in control(tree, 'ef_message').get('text', ''):
        raise RuntimeError('Registration failed: ' + control(tree, 'ef_message').get('text', ''))
    print('PASS: registered synthetic account', flush=True)
    tap(tree, 'ef_submit')
    tree = wait_for('ef_household_name')
    print('PASS: logged in from Android', flush=True)
    fill(tree, 'ef_household_name', 'HandoffSmoke')
    tap(tree, 'ef_create')
    tree = wait_for('app_title')
    if control(tree, 'app_title').get('text') != '今天':
        raise RuntimeError('Household creation did not reach Today.')
    print('PASS: created household and reached Today without navigation crash', flush=True)
    for tab, title in [('app_recipes', '菜谱'), ('app_inventory', '冰箱'), ('app_diary', '食记'), ('app_profile', '我的'), ('app_today', '今天')]:
        tap(tree, tab)
        tree = screen()
        if control(tree, 'app_title') is None or control(tree, 'app_title').get('text') != title:
            raise RuntimeError('Navigation failed for ' + tab)
    if not adb('shell', 'pidof', 'edu.xjtlu.civa').strip():
        raise RuntimeError('App process stopped during navigation.')
    print('PASS: all five navigation tabs; business content remains placeholders', flush=True)


if __name__ == '__main__':
    main()
