"""Targeted, no-screenshot Sony acceptance checks. Run only on the named device."""
import importlib.util
import pathlib
import re
import sys
import time
import xml.etree.ElementTree as ET

sys.stdout.reconfigure(encoding='utf-8')
spec = importlib.util.spec_from_file_location('device', pathlib.Path(__file__).with_name('device-test.py'))
device = importlib.util.module_from_spec(spec)
spec.loader.exec_module(device)
adb, tap_text, verify_text = device.adb, device.tap_text, device.verify_text

def tree():
    adb('shell','uiautomator','dump','/data/local/tmp/kongye-ui.xml')
    return ET.fromstring(adb('exec-out','cat','/data/local/tmp/kongye-ui.xml'))

def ball_bounds():
    dump=adb('shell','dumpsys','window','windows')
    for block in re.split(r'(?=  Window #)',dump):
        if 'package=com.kongye.floatball' not in block or 'ty=APPLICATION_OVERLAY' not in block:
            continue
        match=re.search(r'frame=\[(\d+),(\d+)\]\[(\d+),(\d+)\]',block)
        if match:
            bounds=tuple(map(int,match.groups()))
            if bounds[2]-bounds[0]==bounds[3]-bounds[1] and 60<=bounds[2]-bounds[0]<=250:
                return bounds
    raise AssertionError('Floating ball window is missing')

def tap_ball():
    x1,y1,x2,y2=ball_bounds()
    adb('shell','input','tap',str((x1+x2)//2),str((y1+y2)//2))
    time.sleep(.65)

def back():
    adb('shell','input','keyevent','4')
    time.sleep(.45)

def main():
    if 'com.kongye.floatball' in focus() and '/.MainActivity' not in focus() and '/com.kongye.floatball.MainActivity' not in focus():
        try:tap_text('关闭窗口')
        except RuntimeError:pass
    adb('shell','am','start','-W','-f','0x14000000','-n','com.kongye.floatball/.MainActivity')
    time.sleep(.5)
    if any(n.get('text')=='开启悬浮球' for n in tree().iter('node')):
        tap_text('开启悬浮球')

def focus():
    dump=adb('shell','dumpsys','window')
    match=re.search(r'mCurrentFocus=([^\n]+)',dump)
    return match.group(1) if match else ''

def check(condition,message):
    if not condition:
        raise AssertionError(message)
    print('PASS:',message,flush=True)

def clear_editor():
    editors=[n for n in tree().iter('node') if n.get('class')=='android.widget.EditText' and n.get('package')=='com.kongye.floatball']
    check(len(editors)==1,'only one editable field belonging to this app')
    x1,y1,x2,y2=map(int,re.findall(r'\d+',editors[0].get('bounds')))
    adb('shell','input','tap',str((x1+x2)//2),str((y1+y2)//2));time.sleep(.4)
    adb('shell','input','keycombination','113','29')
    adb('shell','input','keyevent','67');time.sleep(.4)

def gestures():
    main()
    # Slow held movement must drag; the ball must remain within physical bounds.
    x1,y1,x2,y2=ball_bounds();x,y=(x1+x2)//2,(y1+y2)//2
    adb('shell','input','swipe',str(x),str(y),'70','1300','1100');time.sleep(.5)
    bounds=ball_bounds();print('Drag result:',bounds,flush=True);check(bounds[0]==0 and bounds[1]>900,'held drag snaps to left edge')
    # Back gesture: dismiss the app's own About dialog. This avoids ambiguity
    # from existing Android Settings tasks having duplicate subpages.
    main();tap_text('关于与使用帮助');verify_text('空叶悬浮球  ')
    x1,y1,x2,y2=ball_bounds();x,y=(x1+x2)//2,(y1+y2)//2
    adb('shell','input','tap',str(x),str(y));time.sleep(.055)
    adb('shell','input','tap',str(x),str(y));time.sleep(.65)
    check(not any(n.get('text','').startswith('空叶悬浮球  ') for n in tree().iter('node')),'double tap performs Back')
    # Long press executes Home without dragging.
    main();x1,y1,x2,y2=ball_bounds();x,y=(x1+x2)//2,(y1+y2)//2
    adb('shell','input','swipe',str(x),str(y),str(x),str(y),'780');time.sleep(.6)
    check('com.sonymobile.launcher' in focus(),'long press performs Home')
    # Fast vertical swipes dispatch their configured global actions.
    main();x1,y1,x2,y2=ball_bounds();x,y=(x1+x2)//2,(y1+y2)//2
    adb('shell','input','swipe',str(x),str(y),str(x),str(y-180),'120');time.sleep(.7)
    check('com.sonymobile.launcher' in focus() or 'Recents' in focus(),'fast swipe up opens recent tasks')
    main();x1,y1,x2,y2=ball_bounds();x,y=(x1+x2)//2,(y1+y2)//2
    adb('shell','input','swipe',str(x),str(y),str(x),str(y+180),'120');time.sleep(.6)
    check('NotificationShade' in focus() or 'StatusBar' in focus(),'fast swipe down opens notifications')
    adb('shell','cmd','statusbar','collapse');main()

def tools():
    main();tap_ball();verify_text('工具');tap_text('应用启动')
    verify_text('搜索已安装应用')
    for attempt in range(25):
        root=tree()
        if any(n.get('text')=='空叶悬浮球' for n in root.iter('node')):break
        scrolls=[n for n in root.iter('node') if n.get('class')=='android.widget.ScrollView' and n.get('scrollable')=='true']
        if not scrolls:raise AssertionError('App list is not scrollable')
        areas=[]
        for node in scrolls:
            a,b,c,d=map(int,re.findall(r'\d+',node.get('bounds')));areas.append(((c-a)*(d-b),(a,b,c,d)))
        x1,y1,x2,y2=min(areas)[1];x=(x1+x2)//2
        adb('shell','input','swipe',str(x),str(y1+(y2-y1)*4//5),str(x),str(y1+(y2-y1)//5),'350')
        time.sleep(.3)
    verify_text('空叶悬浮球');tap_text('空叶悬浮球');verify_text('运行中')
    tap_ball();tap_text('打开文件');time.sleep(.6)
    check('documentsui' in focus(),'system file picker opens')
    back();main();tap_text('外观');verify_text('尺寸')

def scroll_to(value):
    for attempt in range(12):
        root=tree()
        if any(n.get('text')==value for n in root.iter('node')):
            tap_text(value);return
        scrolls=[n for n in root.iter('node') if n.get('class')=='android.widget.ScrollView' and n.get('scrollable')=='true']
        if not scrolls:break
        a,b,c,d=map(int,re.findall(r'\d+',scrolls[0].get('bounds')));x=(a+c)//2
        adb('shell','input','swipe',str(x),str(b+(d-b)*4//5),str(x),str(b+(d-b)//4),'320');time.sleep(.3)
    raise AssertionError('Cannot reach test setting '+value)

def appearance():
    main();tap_text('外观');tap_text('雾蓝')
    preferences=ET.fromstring(adb('shell','run-as','com.kongye.floatball','cat','shared_prefs/kongye.xml'))
    check(any(n.get('name')=='color' and int(n.get('value','0'))&0xffffffff==0xff567aab for n in preferences),'theme color is saved')
    tap_text('苔绿')
    previous=adb('shell','wm','user-rotation')
    try:
        adb('shell','wm','user-rotation','lock','1');time.sleep(1)
        bounds=ball_bounds();check(0<=bounds[0]<bounds[2]<=2520 and 0<=bounds[1]<bounds[3]<=1080,'ball stays within landscape screen')
    finally:
        if previous.startswith('lock'):adb('shell','wm','user-rotation','lock',previous.split()[-1])
        else:adb('shell','wm','user-rotation','free')
        time.sleep(.8)
    scroll_to('重置悬浮球位置');bounds=ball_bounds();check(bounds[0]>800,'ball position reset succeeds')
    tap_text('首页');tap_text('暂停悬浮球');time.sleep(.4)
    try:ball_bounds();missing=False
    except AssertionError:missing=True
    check(missing,'stopping removes the overlay')
    tap_text('开启悬浮球');check(ball_bounds()[0]>800,'restarting restores the ball')

if __name__=='__main__':
    if sys.argv[1]=='gestures':gestures()
    elif sys.argv[1]=='ball':print('Ball bounds:',ball_bounds())
    elif sys.argv[1]=='focus':print('Focus:',focus())
    elif sys.argv[1]=='tap-ball':tap_ball()
    elif sys.argv[1]=='reset':main()
    elif sys.argv[1]=='tools':tools()
    elif sys.argv[1]=='clear-editor':clear_editor()
    elif sys.argv[1]=='appearance':appearance()
