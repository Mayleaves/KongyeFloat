"""Final Sony release regression: no diagnostics, media export, or gallery writes."""
import runpy,pathlib,time,re
m=runpy.run_path(str(pathlib.Path(__file__).with_name('v19-device.py')));a,adb,v,click,own,win=m['a'],m['adb'],m['v'],m['click'],m['own'],m['win'];d=2.625;right=True
def tile(label):
    a.main();click('首页');m['find_click'](label)
def camera(label):
    global right
    x1,y1,x2,y2=win('空叶录制控制');expanded=x2-x1>250
    if label=='展开录制控制':
        if not expanded:right=x1+27*d>540
        dx=(96 if right else 10)*d if expanded else 0;dy=116*d if expanded else 0;v.tap(x1+dx+27*d,y1+dy+27*d);return
    if not expanded:camera('展开录制控制');return camera(label)
    dx,dy={'暂停录制':((118 if right else 32)*d,30*d),'继续录制':((118 if right else 32)*d,30*d),'停止录制':((32 if right else 118)*d,86*d),'退出录制':((118 if right else 32)*d,254*d)}[label];v.tap(x1+dx,y1+dy)
def visible(title):
    for b in re.split(r'(?=  Window #)',adb('shell','dumpsys','window','windows')):
        if title in b.splitlines()[0]:return bool(re.search(r'^\s+isVisible=true\s*$',b,re.M))
    return False
tile('录屏');click('录制分辨率');a.check(visible('空叶录制准备:录屏选区') and win('空叶录制选项:录制分辨率')[2]>0,'release options appear over existing selector');click('720p 上限');click('录制声音');click('无声');click('录制时隐藏图标');click('显示录制控制图标');adb('shell','input','keyevent','3');click('开始录屏');click('立即开始');time.sleep(4.5);a.check(not visible('空叶悬浮球'),'release recording hides ordinary ball')
x1,y1,x2,y2=win('空叶录制控制');v.swipe(x1+27*d,y1+27*d,220,700,650);moved=win('空叶录制控制');a.check(moved[:2]!=[x1,y1],'release recording icon can be dragged across screen');camera('展开录制控制');camera('暂停录制');camera('继续录制');camera('退出录制');time.sleep(1.8);a.check(visible('空叶悬浮球') and 'RecordingResultActivity' not in a.focus(),'release exit restores ball without opening result')
tile('录制 GIF');click('录制分辨率');click('240p 上限');a.check('录制水印位置预览' not in adb('shell','dumpsys','window','windows'),'fresh release GIF session has no watermark');adb('shell','input','keyevent','3');click('开始录制 GIF');click('立即开始');time.sleep(4.5);camera('停止录制');m['ready']('GIF 动态预览',90);box=v.bounds(m['node']('GIF 动态预览'));a.check(box[2]-box[0]>900 and box[3]-box[1]>1400,'release GIF result uses enlarged viewport');click('关闭录制结果')
tile('悬浮浏览器');m['ready']('搜索引擎：必应');box=v.bounds(next(n for n in own() if n.get('class')=='android.webkit.WebView'));a.check(box[3]-box[1]>1700,'release browser has enlarged web area');click('搜索引擎：Google');click('搜索引擎：必应');click('关闭浏览器');adb('shell','input','keyevent','3');a.check(win('空叶悬浮球')==[954,1087,1080,1213],'release keeps original ball position');pkg=adb('shell','dumpsys','package','com.kongye.floatball');a.check('versionName=1.9.0' in pkg and 'DEBUGGABLE' not in pkg,'Sony runs 1.9.0 release');pid=adb('shell','pidof','com.kongye.floatball').strip();a.check('FATAL EXCEPTION' not in adb('shell','logcat','-b','crash','-d','--pid='+pid),'release process has no crash')
