"""Small ADB helper for this app's real-device acceptance testing.

Screen-image export is disabled by the user's preference. UI inspection is
limited to known test controls; only explicit assertion results are printed.
"""
import pathlib
import subprocess
import sys
import time
import re
import os
import xml.etree.ElementTree as ET

sys.stdout.reconfigure(encoding='utf-8')

ROOT = pathlib.Path(__file__).resolve().parents[1]
ADB = os.environ.get('KONGYE_ADB_PATH', r'D:\Program\Android\Sdk\platform-tools\adb.exe')
SERIAL = os.environ.get('KONGYE_ADB_SERIAL')
if not SERIAL:
    devices = subprocess.run([ADB, 'devices'], capture_output=True, check=True, text=True).stdout.splitlines()[1:]
    sony = []
    for line in devices:
        parts = line.split()
        if len(parts) >= 2 and parts[1] == 'device':
            maker = subprocess.run([ADB, '-s', parts[0], 'shell', 'getprop', 'ro.product.manufacturer'], capture_output=True, check=True, text=True).stdout.strip()
            if maker.lower() == 'sony':sony.append(parts[0])
    if len(sony) != 1:raise RuntimeError('Connect one Sony test phone, or set KONGYE_ADB_SERIAL explicitly.')
    SERIAL = sony[0]

def adb(*args, binary=False):
    result = subprocess.run([ADB, '-s', SERIAL, *args], capture_output=True, timeout=40)
    if result.returncode:
        raise RuntimeError(result.stderr.decode('utf-8', 'replace'))
    return result.stdout if binary else result.stdout.decode('utf-8', 'replace').strip()

def snapshot(name):
    raise RuntimeError('Screen-image export is disabled by user preference.')

def tap_text(value):
    adb('shell', 'uiautomator', 'dump', '/data/local/tmp/kongye-ui.xml')
    tree = ET.fromstring(adb('exec-out', 'cat', '/data/local/tmp/kongye-ui.xml'))
    for node in tree.iter('node'):
        if node.get('text') == value or node.get('content-desc') == value:
            x1, y1, x2, y2 = map(int, re.findall(r'\d+', node.get('bounds')))
            adb('shell', 'input', 'tap', str((x1+x2)//2), str((y1+y2)//2))
            time.sleep(.7)
            return
    raise RuntimeError(f'Cannot find {value}')

def verify_text(value):
    adb('shell', 'uiautomator', 'dump', '/data/local/tmp/kongye-ui.xml')
    tree = ET.fromstring(adb('exec-out', 'cat', '/data/local/tmp/kongye-ui.xml'))
    matches = [n for n in tree.iter('node') if value in n.get('text', '')]
    if not matches:
        raise AssertionError(f'Expected test text not found: {value}')
    print('PASS:', value)

if __name__ == '__main__':
    if sys.argv[1] == 'snapshot':
        snapshot(sys.argv[2])
    elif sys.argv[1] == 'tap':
        print(adb('shell', 'input', 'tap', sys.argv[2], sys.argv[3]))
        time.sleep(.7)
    elif sys.argv[1] == 'shell':
        print(adb('shell', *sys.argv[2:]))
    elif sys.argv[1] == 'text':
        tap_text(sys.argv[2])
    elif sys.argv[1] == 'verify':
        verify_text(sys.argv[2])
