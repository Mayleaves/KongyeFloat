"""Final production UI smoke: only app-owned controls; no screen-image export."""
import os,runpy,time,re,xml.etree.ElementTree as ET
os.environ.setdefault('KONGYE_ADB_SERIAL','525114fb')
q=runpy.run_path('scripts/device-test.py');adb=q['adb']
def nodes():
    for attempt in range(3):
        try:adb('shell','uiautomator','dump','--compressed','/data/local/tmp/kongye-v3-release.xml');break
        except RuntimeError:
            if attempt==2:raise
            time.sleep(.8)
    return [n for n in ET.fromstring(adb('exec-out','cat','/data/local/tmp/kongye-v3-release.xml')).iter('node') if n.get('package')=='com.kongye.floatball']
def find(label):
    found=[n for n in nodes() if n.get('content-desc')==label or n.get('text')==label]
    return next((n for n in found if n.get('clickable')=='true'),found[0] if found else None)
def bounds(n):return list(map(int,re.findall(r'\d+',n.get('bounds'))))
def tap(label):
    n=find(label)
    assert n is not None,'Missing own control: '+label
    a,b,c,d=bounds(n);adb('shell','input','tap',str((a+c)//2),str((b+d)//2));time.sleep(.6)
adb('shell','am','start','-W','-n','com.kongye.floatball/.MainActivity');time.sleep(1)
tap('首页')
if find('启动悬浮球') is not None:tap('启动悬浮球');time.sleep(.5)
tap('区域截图');tap('识别并查看');time.sleep(2)
labels=['复制截图','分享图片','编辑图片','保存图片']
buttons=[find(label) for label in labels]
if any(n is None or bounds(n)==[0,0,0,0] for n in buttons):
    width,height=map(int,re.findall(r'(\d+)x(\d+)',adb('shell','wm','size'))[-1]);adb('shell','input','swipe',str(width*3//4),str(height*3//4),str(width*3//4),str(height//3),'350');time.sleep(.5);buttons=[find(label) for label in labels]
assert all(n is not None for n in buttons),'All four screenshot actions present'
boxes=[bounds(n) for n in buttons]
assert [b[0] for b in boxes]==sorted(b[0] for b in boxes),'Copy, share, edit, save order'
assert max(b[1] for b in boxes)-min(b[1] for b in boxes)<=3,'Single row'
assert max(b[2]-b[0] for b in boxes)-min(b[2]-b[0] for b in boxes)<=3,'Equal widths'
print('PASS: production screenshot actions are copy, share, edit, save in one equal-width row')
tap('复制截图')
assert find('关闭截图结果') is None,'Copy closes result'
print('PASS: production screenshot copy closes the result window')
info=adb('shell','dumpsys','package','com.kongye.floatball')
assert 'versionName=3.0' in info and 'versionCode=75 ' in info and 'pkgFlags=[ DEBUGGABLE' not in info
print('PASS: v3.0 version 75 production package installed')
