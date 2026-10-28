"""Final Sony 1.10 release smoke checks; no diagnostic components or media export."""
import runpy,pathlib,time,re
m=runpy.run_path(str(pathlib.Path(__file__).with_name('v19-device.py')));a,adb,v,click,own,win,ready=m['a'],m['adb'],m['v'],m['click'],m['own'],m['win'],m['ready'];d=2.625;right=True

def tile(label):
    a.main();click('首页');m['find_click'](label)

def camera(label):
    global right
    x1,y1,x2,y2=win('空叶录制控制');expanded=x2-x1>250
    if label=='展开录制控制':
        if not expanded:right=x1+27*d>540
        dx=(96 if right else 10)*d if expanded else 0;dy=116*d if expanded else 0;v.tap(x1+dx+27*d,y1+dy+27*d);return
    if not expanded:camera('展开录制控制');return camera(label)
    dx,dy={'暂停录制':((118 if right else 32)*d,30*d),'继续录制':((118 if right else 32)*d,30*d),'停止录制':((32 if right else 118)*d,86*d),'取消录制':((118 if right else 32)*d,254*d)}[label];v.tap(x1+dx,y1+dy)

def visible(title):
    for b in re.split(r'(?=  Window #)',adb('shell','dumpsys','window','windows')):
        if title in b.splitlines()[0]:return bool(re.search(r'^\s+isVisible=true\s*$',b,re.M))
    return False

def recording():
    for gif in [False,True]:
        tile('录制 GIF' if gif else '录屏');start='开始录制 GIF' if gif else '开始录屏';click('录制水印');click('完成水印编辑');ready(start);a.check(not visible('空叶录制水印画布'),'release untouched watermark returns to same setup')
        click('录制水印');n=m['node']('录制水印编辑画布');x1,y1,x2,y2=v.bounds(n);v.tap(x1+(x2-x1)*.5,y1+(y2-y1)*.4);adb('shell','input','text','1234');click('水印颜色红色');click('完成水印编辑');ready(start);a.check(visible('空叶录制准备:录制水印位置预览'),'release entered/color watermark returns to setup with preview');click('录制时隐藏图标');click('显示录制控制图标');adb('shell','input','keyevent','3');click(start);click('立即开始');time.sleep(4.2);camera('取消录制');ready(start,60);a.check(not visible('空叶录制控制') and visible('空叶录制准备:录制水印位置预览'),'release cancel returns to setup keeping current watermark');a.check('RecordingResultActivity' not in a.focus(),'release cancel never opens result');m['close_setup']()
    tile('录屏');adb('shell','input','keyevent','3');click('开始录屏');click('立即开始');time.sleep(4.3);camera('停止录制');ready('转为 GIF',60);a.check('RecordingResultActivity' in a.focus(),'release stop still opens result');click('关闭录制结果')

def browser():
    tile('悬浮浏览器');ready('置顶浏览器');box=v.bounds(next(n for n in own() if n.get('class')=='android.webkit.WebView'));a.check(box[3]-box[1]>1700,'release browser has large viewport');click('置顶浏览器');click('收起浏览器');ready('展开浏览器');folded=win('空叶浏览器');a.check(folded[3]-folded[1]<300,'release pinned browser folds to title');x1,y1,x2,y2=v.bounds(m['node']('拖动浏览器窗口'));v.swipe((x1+x2)/2,(y1+y2)/2,(x1+x2)/2,(y1+y2)/2+320,650);a.check(win('空叶浏览器')[1]!=folded[1],'release pinned title can move browser');click('展开浏览器');ready('收起浏览器');box=v.bounds(next(n for n in own() if n.get('class')=='android.webkit.WebView'));a.check(box[3]-box[1]>1600,'release browser expands to large viewport');click('取消置顶浏览器');v.tap(2,1200);a.check(not visible('空叶浏览器'),'release unpinned outside tap closes browser');adb('shell','input','keyevent','3');a.check(win('空叶悬浮球')==[954,1087,1080,1213],'release retains original ball position');pkg=adb('shell','dumpsys','package','com.kongye.floatball');a.check('versionName=1.10.0' in pkg and 'DEBUGGABLE' not in pkg,'Sony runs 1.10.0 release');pid=adb('shell','pidof','com.kongye.floatball').strip();a.check('FATAL EXCEPTION' not in adb('shell','logcat','-b','crash','-d','--pid='+pid),'release current process has no crash')

if __name__=='__main__':recording();browser()
