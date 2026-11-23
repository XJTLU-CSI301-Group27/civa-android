"""Open the verified C inventory demo in an Android Studio emulator without printing credentials."""
import argparse
import json
from pathlib import Path
import re
import subprocess
import time
import uuid
import xml.etree.ElementTree as ET
import zipfile


def main():
    root = Path(__file__).resolve().parents[1]
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--adb', required=True)
    parser.add_argument('--serial', required=True)
    parser.add_argument('--session', type=Path, default=root/'build/c-ui-smoke/preview-session.json')
    parser.add_argument('--open-add', action='store_true')
    parser.add_argument('--open-fridge', action='store_true', help='Open both doors without changing stock')
    parser.add_argument('--food', default='beef', help='English name to prefill without submitting stock')
    args = parser.parse_args()
    if not re.fullmatch(r'emulator-[0-9]+', args.serial):
        raise RuntimeError('Select an Android emulator; this helper does not use physical devices')
    if not re.fullmatch(r'[A-Za-z][A-Za-z0-9 _-]{0,119}', args.food):
        raise RuntimeError('Preview food must be a plain English name')
    session = json.loads(args.session.read_text(encoding='utf-8'))
    if not session['database'].endswith('_test') or not re.fullmatch(r'c_ui_[a-z0-9]+', session['username']):
        raise RuntimeError('Only the retained isolated preview account is allowed')
    if not re.fullmatch(r'[A-Za-z0-9_-]{10,72}', session['password']):
        raise RuntimeError('Preview credential format is invalid')
    uuid.UUID(session['householdId'])

    def adb(*parts):
        result = subprocess.run([args.adb, '-s', args.serial, *parts], capture_output=True, timeout=45)
        if result.returncode:
            raise RuntimeError('Emulator command failed; credentials are not printed')
        return result.stdout.decode('utf-8')

    def screen():
        adb('shell','uiautomator','dump','/sdcard/civa-preview.xml')
        return ET.fromstring(adb('shell','cat','/sdcard/civa-preview.xml'))

    def wait(resource=None, text=None):
        end=time.monotonic()+40
        while time.monotonic()<end:
            match=next((n for n in screen().iter('node') if
                (resource is None or n.get('resource-id')=='edu.xjtlu.civa:id/'+resource)
                and (text is None or n.get('text')==text)),None)
            if match is not None: return match
            time.sleep(.4)
        raise RuntimeError('Preview page did not reach expected control: '+str(resource or text))

    def tap(node):
        x1,y1,x2,y2=map(int,re.findall(r'\d+',node.get('bounds')))
        adb('shell','input','tap',str((x1+x2)//2),str((y1+y2)//2))

    def fill(resource,value):
        tap(wait(resource=resource))
        adb('shell','input','text',value.replace(' ', '%s'))
        # Back leaves the app when the soft keyboard is hidden (e.g. hardware
        # keyboard enabled). Only dismiss an input method that is actually shown.
        if re.search(r'\bmInputShown=true\b', adb('shell','dumpsys','input_method')):
            adb('shell','input','keyevent','4')

    apk=root/'app/build/outputs/apk/debug/app-debug.apk'
    with zipfile.ZipFile(apk) as package:
        dex=b''.join(package.read(name) for name in package.namelist() if re.fullmatch(r'classes[0-9]*\.dex',name))
    if b'http://10.0.2.2:8080/' not in dex or b'http://10.0.2.2:8081/' in dex:
        raise RuntimeError('Preview requires the 8080 APK; rebuild without CIVA_API_BASE_URL before opening the user emulator')
    adb('install','-r',str(apk))
    adb('shell','am','force-stop','edu.xjtlu.civa')
    adb('shell','am','start','-W','-n','edu.xjtlu.civa/.MainActivity')
    fill('ef_username',session['username']);fill('ef_password',session['password'])
    tap(wait(resource='ef_submit'));tap(wait(resource='app_inventory'))
    wait(resource='ac_cold_compartment');wait(resource='ac_frozen_compartment')
    deadline=time.monotonic()+30
    while any(n.get('resource-id')=='edu.xjtlu.civa:id/ac_loading' for n in screen().iter('node')):
        if time.monotonic()>deadline: raise RuntimeError('Preview stock is still loading')
        time.sleep(.3)
    if args.open_fridge:
        control=wait(resource='ac_open')
        if control.get('text')=='打开冰箱':
            tap(control)
        wait(text='关上冰箱')
    if args.open_add:
        tap(wait(resource='ac_add'));fill('ac_food_name',args.food)
        for control,label in [('ac_unit_g','g'),('ac_unit_kg','kg'),('ac_unit_count','pcs')]:
            if wait(resource=control).get('text') != label:
                raise RuntimeError('Quantity buttons must display g / kg / pcs')
        if args.food == 'beef' and wait(resource='ac_unit_g').get('checked') != 'true':
            raise RuntimeError('Beef entry did not select grams')
        if args.food.lower() in ('pear','pears'):
            wait(text='冷藏 · 水果层')
    print('Ready in Android Studio on '+args.serial+': updated inventory UI and isolated preview household; no stock submitted')


if __name__=='__main__':
    main()
