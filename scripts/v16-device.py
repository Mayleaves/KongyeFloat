"""Sony 1.6 native regression. Own UI only; never exports screenshots/media."""
import importlib.util,pathlib,sys,time,re
sys.stdout.reconfigure(encoding='utf-8',line_buffering=True)
s=importlib.util.spec_from_file_location('v',pathlib.Path(__file__).with_name('v14-device.py'));v=importlib.util.module_from_spec(s);s.loader.exec_module(v)
a,adb,click,prefs=v.a,v.adb,v.click,v.prefs

def has(desc):return any(n.get('content-desc')==desc for n in a.tree().iter('node'))
def center(desc):
    x1,y1,x2,y2=v.bounds(v.node(desc));return ((x1+x2)/2,(y1+y2)/2)
def native_text(start=True):
    if start:v.ready('screenshot')
    click('编辑图片');click('添加文字');x,y=center('图片编辑画布');v.tap(x,y);time.sleep(.4)
    root=a.tree();a.check(any(n.get('text')=='编辑图片' for n in root.iter('node')) and not any(n.get('content-desc')=='图片文字输入' for n in root.iter('node')),'text uses canvas IME without separate input field')
    ime=adb('shell','dumpsys','input_method');a.check('mInputShown=true' in ime or 'mIsInputViewShown=true' in ime,'canvas opens keyboard directly')
    adb('shell','input','text','123');time.sleep(.3);a.check(has('删除选中标注') and has('缩放选中标注'),'text frame exposes corner controls')
    # Tap blank once to finish input/deselect, without adding a mark.
    x1,y1,x2,y2=v.bounds(v.node('图片编辑画布'));v.tap(x1+12,y1+12);a.check(not has('删除选中标注') and not has('缩放选中标注'),'blank tap hides text frame controls')
    click('应用编辑');a.check(prefs(['last_edit_marks'])['last_edit_marks']=='text,','native keyboard input is rendered as one text annotation');click('返回截图结果');v.close()

def rectangle():
    v.ready('screenshot');click('编辑图片');click('画框');x1,y1,x2,y2=v.bounds(v.node('图片编辑画布'));sx=x1+(x2-x1)*.43;sy=y1+(y2-y1)*.32;ex=x1+(x2-x1)*.57;ey=y1+(y2-y1)*.65;v.swipe(sx,sy,ex,ey)
    a.check(has('删除选中标注') and has('缩放选中标注'),'rectangle exposes corner controls');before=v.bounds(v.node('缩放选中标注'));v.tap(x1+12,y1+12);a.check(not has('删除选中标注'),'blank tap hides rectangle selection');v.tap((sx+ex)/2,(sy+ey)/2);a.check(has('删除选中标注'),'tap existing rectangle restores controls');rx,ry=center('缩放选中标注');v.swipe(rx,ry,rx+70,ry+60);after=v.bounds(v.node('缩放选中标注'));a.check(after[0]>before[0] or after[1]>before[1],'native corner drag resizes annotation');click('删除选中标注');click('应用编辑');a.check(prefs(['last_edit_marks'])['last_edit_marks']=='','native delete removes rectangle');click('返回截图结果');v.close()

def record():
    a.main();root=a.tree()
    for _ in range(5):
        if any(n.get('text')=='录屏' for n in root.iter('node')):break
        v.swipe(560,2050,560,700,400);root=a.tree()
    a.check(sum(n.get('text')=='录屏' for n in root.iter('node'))==1 and not any(n.get('text') in ('无声录屏','带声音录屏') for n in root.iter('node')),'home has one recording entry');click('录屏');root=a.tree();a.check({'录屏声音：无声','录屏声音：手机内部声音','录屏声音：麦克风声音'}.issubset({n.get('content-desc') for n in root.iter('node')}),'unified recording offers all three audio modes')
    for label in ('手机内部声音','麦克风声音','无声'):
        click('录屏声音：'+label);root=a.tree();a.check(next(n for n in root.iter('node') if n.get('content-desc')=='录屏声音：'+label).get('checked')=='true','recording audio selectable: '+label)
    click('取消');a.main()

def release():
    def held(index=None):
        x1,y1,x2,y2=a.ball_bounds();x,y=(x1+x2)//2,(y1+y2)//2
        if index is not None:
            dx,dy=[(40,-142),(90,-118),(34,-87),(79,-54),(130,-76),(150,-29),(97,1),(148,26),(78,53),(130,77),(91,120),(40,144)][index];factor=1080/510;tx=dx*factor if x<540 else 1080-dx*factor;ty=max(168*factor,min(2520-170*factor,y))+dy*factor
        else:tx,ty=(984 if x<540 else 96),y
        adb('shell','input','motionevent','DOWN',str(x),str(y));time.sleep(.85);adb('shell','input','motionevent','MOVE',str(round(tx)),str(round(ty)));time.sleep(.2);adb('shell','input','motionevent','UP',str(round(tx)),str(round(ty)));time.sleep(.5)
    def wait_result(title):
        until=time.monotonic()+25
        while time.monotonic()<until:
            if any(n.get('text')==title for n in a.tree().iter('node')):return
            time.sleep(.3)
        raise AssertionError('Missing release result '+title)
    a.main();original=a.ball_bounds();held();root=a.tree()
    def slot(label):return int(re.search(r'设置快捷位置(\d+)',next(n.get('content-desc') for n in root.iter('node') if n.get('content-desc','').endswith('：'+label))).group(1))-1
    screen_index,long_index=slot('整屏截图'),slot('长截图');a.check(sum(n.get('content-desc','').endswith('：录屏') for n in root.iter('node'))==1,'legacy quick recording slot becomes unified entry');click('完成');held(screen_index);wait_result('整屏截图')
    # Release cannot read private debug receipts, so validate actual IME and control visibility.
    click('编辑图片');click('添加文字');x,y=center('图片编辑画布');v.tap(x,y);root=a.tree();a.check(any(n.get('text')=='编辑图片' for n in root.iter('node')) and not any(n.get('content-desc')=='图片文字输入' for n in root.iter('node')),'release text editing stays on canvas');ime=adb('shell','dumpsys','input_method');a.check('mInputShown=true' in ime or 'mIsInputViewShown=true' in ime,'release canvas opens keyboard');adb('shell','input','text','123');a.check(has('删除选中标注') and has('缩放选中标注'),'release text has delete and resize corners');x1,y1,x2,y2=v.bounds(v.node('图片编辑画布'));v.tap(x1+12,y1+12);a.check(not has('删除选中标注'),'release blank tap hides annotation controls');click('应用编辑');click('返回截图结果');v.close();a.main();held(long_index)
    x1,y1,x2,y2=v.window_bounds('空叶长截图:手动长截图工具栏');v.tap((x1+x2)/2,(y1+y2)/2);time.sleep(1);v.tap((x1+x2)/2,(y1+y2)/2);time.sleep(1);a1,b1,a2,b2=v.window_bounds('空叶长截图:长截图缩略预览');v.tap((a1+a2)/2,(b1+b2)/2);click('调整截图顺序和删除');a.check(has('删除第2张') and has('截图第1张，长按拖动排序'),'release preview management retains delete and order');click('删除第2张');click('确认拼接');wait_result('拼接长图');v.close();record();a.check(a.ball_bounds()==original,'release preserves floating ball position');pkg=adb('shell','dumpsys','package','com.kongye.floatball');a.check('versionName=1.6.0' in pkg and 'DEBUGGABLE' not in pkg,'Sony runs 1.6.0 release');pid=adb('shell','pidof','com.kongye.floatball').strip();a.check('FATAL EXCEPTION' not in adb('shell','logcat','-b','crash','-d','--pid='+pid),'release process has no crash')

if __name__=='__main__':{'release':release,'text':native_text,'rect':rectangle,'record':record}[sys.argv[1]]()
