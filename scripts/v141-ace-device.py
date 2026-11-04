"""Live subtitle regression using generated text, with no phone media export."""
import json
import re
import runpy
import time

q = runpy.run_path('scripts/v118-device.py')
adb, receiver, values = (q[key] for key in ('adb', 'receiver', 'values'))

def cmd(name):
    receiver('qa_v141_' + name)

def state():
    cmd('state')
    return json.loads(values()['qa_v141_state'])

def wait_for(predicate, timeout=24):
    end = time.monotonic() + timeout
    while time.monotonic() < end:
        current = state()
        if predicate(current):
            return current
        time.sleep(.3)
    raise AssertionError(current)

def report(message):
    print('PASS:', message, flush=True)

def tap_ball():
    receiver('qa_v140_state')
    ball = json.loads(values()['qa_v140_state'])['ball']
    q['tap'](ball['cx'], ball['cy'])

def settings():
    tap_ball()
    wait_for(lambda s: s['confirm'])
    cmd('click_字幕设置')

def choose(group, label):
    cmd('click_' + group)
    cmd('click_' + group + '，' + label)

q['main']()
receiver('qa_v134_awake')
receiver('qa_v123_service_REFRESH')
cmd('reset_style')
try:
    q['find']('字幕翻译')
    q['click']('字幕翻译')
    wait_for(lambda s: s['start'] and s['action_available'])
    report('Home tile opens real region editor and subtitle is bindable in gestures')
    cmd('fixture')
    cmd('click_确认')
    first = wait_for(lambda s: s.get('translation') and 'hello world' in s.get('recognized', '').lower())
    assert first['frame'] and not first['cover']
    assert first['caption_top'] + first['caption_height'] <= first['area_top']
    assert 'outside' not in first['recognized'].lower()
    assert first['translation'].lower() != first['recognized'].lower()
    report('Actual cropped English OCR translates into Chinese above the default dashed region')

    cmd('next')
    second = wait_for(lambda s: 'good morning' in s.get('recognized', '').lower() and s.get('translation') not in ('', first['translation']))
    report('Translation updates when the source subtitle changes')
    settings()
    cmd('click_显示虚线框')
    cmd('click_译文覆盖选区')
    cmd('click_靠右')
    cmd('click_白色')
    cmd('opacity')
    cmd('click_完成')
    over = wait_for(lambda s: s.get('translation') == second['translation'] and s.get('cover'))
    assert not over['frame'] and over['alignment'] == 2 and over['background'] == -1, over
    assert 30 <= over['opacity'] <= 50, over
    assert abs(over['caption_top'] - over['area_top']) <= 1
    time.sleep(2)
    assert 'good morning' in state()['recognized'].lower()
    report('Cover placement, frame toggle, alignment and color apply without reading back the translation')

    settings()
    cmd('click_调整识别区域')
    width, height = map(int, re.findall(r'(\d+)x(\d+)', adb('shell', 'wm', 'size'))[-1])
    adb('shell', 'input', 'swipe', str(round(width * .90)), str(round(height * .46)),
        str(round(width * .84)), str(round(height * .46)), '450')
    cmd('click_确认')
    resized = wait_for(lambda s: s.get('translation') == second['translation'] and s['area_width'] < over['area_width'] and s.get('recognized') == second['recognized'])
    assert resized['area_width'] > 100
    report('Resizing an ongoing session applies a new OCR region')

    cmd('blank')
    wait_for(lambda s: s.get('recognized') == '' and s.get('translation') == '')
    report('Caption clears when source text disappears')
    cmd('next')
    wait_for(lambda s: s.get('translation') == second['translation'])
    cmd('secure')
    wait_for(lambda s: s.get('translation') == '' and ('保护' in s.get('status', '') or '禁止' in s.get('status', '')))
    cmd('unsecure')
    wait_for(lambda s: s.get('translation') == second['translation'])
    report('Protected window clears the caption and capture resumes after protection is removed')

    settings()
    cmd('click_完成')
    wait_for(lambda s: not s['panel'] and s.get('translation') == second['translation'])
    for language, label, text in [('ja', '日文', 'おはようございます'), ('ko', '韩文', '안녕하세요')]:
        settings()
        choose('源语言', label)
        choose('翻译为', label)
        cmd('japanese' if language == 'ja' else 'korean')
        cmd('click_完成')
        wait_for(lambda s: text in s.get('recognized', '').replace(' ', '') and text in s.get('translation', '').replace(' ', ''))
        report('Bundled ' + language + ' OCR recognizes the original script')
    tap_ball()
    wait_for(lambda s: s['confirm'])
    cmd('click_退出翻译')
    wait_for(lambda s: not s['active'] and not s['panel'])
    report('Tapping the real ball opens subtitle settings and exit')
    cmd('landscape')
    time.sleep(1.6)
    cmd('fixture')
    baseline = wait_for(lambda s: s.get('status_bar') is False and s.get('fixture_focused'))
    cmd('menu')
    receiver('snapshot')
    assert any(node['label'] == '快捷菜单' for node in json.loads(values()['qa_overlay_nodes']))
    behind_menu = state()
    assert behind_menu['fixture_focused'] and not behind_menu['status_bar'] and not behind_menu['nav_bar'], behind_menu
    receiver('qa_v126_close_menu')
    report('Quick menu leaves the immersive app focused and system bars hidden')
    cmd('start')
    original = wait_for(lambda s: s['start'] and s.get('screen_width', 0) > s.get('screen_height', 0))
    assert not original['status_bar'] and not original['nav_bar'] and original['fixture_focused'], original
    width, height = original['screen_width'], original['screen_height']
    adb('shell', 'input', 'swipe', str(round(width * .32)), str(round(height * .45)),
        str(round(width * .35)), str(round(height * .49)), '450')
    moved = wait_for(lambda s: s.get('crop_top', 0) > original['crop_top'])
    assert moved['crop_width'] == original['crop_width'] and moved['crop_left'] > original['crop_left'], moved
    cmd('click_重置')
    reset = wait_for(lambda s: s.get('crop_top') == original['crop_top'])
    assert reset['crop_left'] == original['crop_left'] and reset['crop_width'] == original['crop_width']
    cmd('click_确认')
    confirmed = wait_for(lambda s: not s['panel'] and s['area_top'] == reset['crop_top'])
    assert confirmed['area_left'] == reset['crop_left'] and confirmed['area_width'] == reset['crop_width']
    assert confirmed['fixture_top'] == baseline['fixture_top'] and not confirmed['status_bar']
    settings()
    opened = wait_for(lambda s: s['panel'] and not s['confirm'])
    assert opened['fixture_focused'] and not opened['status_bar'] and not opened['nav_bar'], opened
    choose('源语言', '英文')
    choose('翻译为', '中文')
    cmd('color_keyboard')
    typing = wait_for(lambda s: s.get('ime'))
    assert not typing['status_bar'], typing
    adb('shell', 'input', 'keyevent', '4')
    wait_for(lambda s: not s.get('ime'))
    cmd('click_完成')
    wait_for(lambda s: 'hello world' in s.get('recognized', '').lower() and s.get('translation'))
    report('Immersive landscape keeps bars hidden, drags and resets the frame, and reads the exact confirmed region')
    settings()
    cmd('click_译文覆盖选区')
    cmd('click_完成')
    cmd('long_caption')
    long_caption = wait_for(lambda s: 'bookstore' in s.get('recognized', '').lower()
                            and '？' in s.get('translation', '') and len(s.get('caption_lines', [])) >= 2)
    assert not any(line.lstrip().startswith(tuple('，。！？；：）”’')) for line in long_caption['caption_lines'])
    cmd('punctuation')
    sample = values()['qa_v141_punctuation']
    assert sample == '他说：“明天晚上 8:30，带上 3.14 元去 KTV……行吗？”\n网址是 https://example.com/a?x=2，邮箱是 a.b@example.com。\nHello, world!', sample
    report('Long translated captions wrap without leading punctuation; Chinese formatting keeps numbers and links')
    tap_ball()
    cmd('click_退出翻译')
    cmd('restore_orientation')
    time.sleep(1.4)
    cmd('menu')
    receiver('snapshot')
    assert any(node['label'] == '字幕翻译' and node['clickable'] for node in json.loads(values()['qa_overlay_nodes']))
    report('Subtitle tool is visible in the shortcut menu')
    receiver('qa_v126_close_menu')
    q['main']()
    adb('shell', 'input', 'keyevent', '3')
    time.sleep(.4)
    cmd('open_motion')
    time.sleep(1.2)
    frames = json.loads(values()['qa_v141_entry_frames'])
    moving = [frame for frame in frames if frame['y'] > 1]
    assert len(moving) >= 5 and frames[-1]['y'] == 0 and all(frame['scale'] == 1 for frame in frames)
    assert 170 <= moving[-1]['ms'] - moving[0]['ms'] <= 330
    receiver('qa_v134_exit')
    time.sleep(1.4)
    frames = json.loads(values()['qa_v134_frames'])
    moving = [frame for frame in frames if frame.get('image_y', 0) < -1 and not frame.get('finishing')]
    assert len(moving) >= 5 and not frames[-1]['layer'] and any(frame.get('handoff') for frame in moving)
    assert 170 <= moving[-1]['ms'] - moving[0]['ms'] <= 330
    report('Gesture entry and exit slide in the reference direction at the shorter timing')
finally:
    cmd('unsecure')
    cmd('stop')
    cmd('restore_orientation')
    cmd('restore_style')
    receiver('qa_v126_close_menu')
    receiver('qa_v134_awake_off')
    q['main']()
