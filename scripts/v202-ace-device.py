"""Generated crop and interrupted-startup regression. No phone media export."""
import json
import os
import re
import runpy
import time

q = runpy.run_path('scripts/v118-device.py')
adb, receiver, values = [q[key] for key in ('adb', 'receiver', 'values')]
saved_images = set()

def command(name):
    receiver('qa_v202_' + name)

def state():
    command('state')
    return json.loads(values()['qa_v202_state'])

def check(ok, label):
    assert ok, label
    print('PASS:', label, flush=True)

def wait(predicate, timeout=20):
    end = time.monotonic() + timeout
    while time.monotonic() < end:
        current = state()
        if predicate(current):
            return current
        time.sleep(.2)
    raise AssertionError(current)

def press(label):
    receiver('nodes')
    entries = json.loads(values()['qa_overlay_nodes'])
    item = next(item for item in entries if item['label'] == label and item['enabled'])
    q['tap']((item['x1'] + item['x2']) / 2, (item['y1'] + item['y2']) / 2)

def images():
    listing = adb('shell', 'content', 'query', '--uri', 'content://media/external/images/media',
                  '--projection', '_id', '--where', '"owner_package_name=\'com.kongye.floatball\'"')
    return set(re.findall(r'_id=(\d+)', listing))

def drag(x, y, dx, dy):
    adb('shell', 'input', 'swipe', str(round(x)), str(round(y)),
        str(round(x + dx)), str(round(y + dy)), '320')
    time.sleep(.2)

q['main']()
receiver('qa_v134_awake')
receiver('qa_v123_service_REFRESH')
try:
    density = float(adb('shell', 'wm', 'density').split(':')[-1].strip()) / 160
    command('native')
    check(values().get('qa_v202_native') == 'true', 'rounded selection, clear interior, outside mask and exact source coordinates')
    width = state()['ball_width']
    for interruption in ('policy', 'wake'):
        command('interrupt_' + interruption)
        time.sleep(.35)
        current = state()
        check(current['scale_x'] == current['scale_y'] == 1 and current['ball_width'] == width,
              'interrupted startup restores configured ball size via ' + interruption)
        time.sleep(.6)
        check(state()['scale_x'] == 1, 'ball size remains stable after cancellation via ' + interruption)

    command('feedback_short')
    time.sleep(.5)
    current = state()
    check(current['feedback_lines'] == 1 and abs(current['feedback_height'] - round(40*density)) <= 2,
          'scheme A short feedback is a 40dp single-line capsule')
    check(current['feedback_width'] < 230*density and current['feedback_flags'] & 24 == 24,
          'feedback wraps its text and never takes focus or touches')
    command('feedback_long')
    time.sleep(.5)
    current = state()
    check(current['feedback_lines'] > 1 and current['feedback_width'] <= round(340*density)+2,
          'long error remains readable inside bounded capsule')
    time.sleep(3.1)
    check('feedback_width' not in state(), 'feedback dismisses itself after exit animation')

    command('fixture')
    current = wait(lambda s: s.get('selection') and s['toolbar'][2] > 0)
    area, bar, viewport = current['area'], current['toolbar'], current['viewport']
    check(bar[1] >= area[3] and abs(bar[3] - round(48*density)) <= 1, 'scheme A toolbar follows below the selection')
    drag((area[0]+area[2])/2, (area[1]+area[3])/2, -35*density, 60*density)
    moved = state()
    check(moved['area'][1] > area[1] and moved['toolbar'][1] > bar[1], 'dragging inside moves selection and toolbar together')
    area = moved['area']
    drag((area[0]+area[2])/2, (area[1]+area[3])/2, 0, viewport[1])
    bottom = state()
    bar = bottom['toolbar']
    check(bar[1]+bar[3] <= bottom['area'][1]+1 and bar[0] >= 0 and bar[0]+bar[2] <= viewport[0],
          'toolbar moves above selection at bottom and stays onscreen')
    press('识别并查看')
    wait(lambda s: s.get('result'))
    check(not state()['selection'], 'recognize opens existing result flow and removes selection')
    command('close')

    before = images()
    command('fixture')
    wait(lambda s: s.get('selection') and s['toolbar'][2] > 0)
    press('直接保存图片')
    wait(lambda s: not s.get('active') and not s.get('selection'))
    saved_images = images() - before
    check(len(saved_images) == 1 and not state().get('result'), 'direct save publishes one image and closes without an OCR window')
    uri = 'content://media/external/images/media/' + next(iter(saved_images))
    dimensions = adb('shell','content','query','--uri',uri,'--projection','width:height')
    check('width=576' in dimensions and 'height=512' in dimensions, 'saved pixels match the exact rectangular selection')
    check(values().get('qa_feedback_message') == '图片已保存到相册', 'direct save retains success feedback')
finally:
    command('close')
    for image in saved_images:
        adb('shell', 'content', 'delete', '--uri', 'content://media/external/images/media/' + image)
    receiver('qa_v134_awake_off')
    q['main']()
