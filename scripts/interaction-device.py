"""v1.3 Sony acceptance: own UI and numeric receipts, no screenshot or media export."""
import importlib.util,pathlib,re,subprocess,sys,time,json
sys.stdout.reconfigure(encoding='utf-8',line_buffering=True)
spec=importlib.util.spec_from_file_location('f',pathlib.Path(__file__).with_name('features-device.py'));f=importlib.util.module_from_spec(spec);spec.loader.exec_module(f)
a,adb,click,prefs=f.a,f.adb,f.click,f.prefs

def bounds(node):return list(map(int,re.findall(r'\d+',node.get('bounds'))))
def tap(x,y):adb('shell','input','tap',str(round(x)),str(round(y)));time.sleep(.5)
def swipe(x,y,xx,yy,duration=500):adb('shell','input','swipe',str(round(x)),str(round(y)),str(round(xx)),str(round(yy)),str(duration));time.sleep(.3)
def wait(key,test,seconds=20):
    until=time.monotonic()+seconds
    while time.monotonic()<until:
        v=prefs([key]).get(key,'')
        if test(v):return v
        time.sleep(.25)
    raise AssertionError('Timed out: '+key)
def node(description):return next(n for n in a.tree().iter('node') if n.get('content-desc')==description)
def close_result():click('关闭截图结果')
def result():
    f.diagnostic('region');click('识别并查看');wait('last_ocr_state',lambda x:x in ('done','failed'));a.check(prefs(['last_ocr_state'])['last_ocr_state']=='done','OCR ready')

def picker(value):
    for _ in range(30):
        root=a.tree()
        if any(n.get('text')==value for n in root.iter('node')):click(value);return
        lists=[n for n in root.iter('node') if n.get('class')=='android.widget.ListView']
        if not lists:raise AssertionError('Missing native picker')
        x1,y1,x2,y2=bounds(lists[0]);swipe((x1+x2)/2,y1+(y2-y1)*.85,(x1+x2)/2,y1+(y2-y1)*.2,300)
    raise AssertionError('Picker item not found: '+value)

def held():
    f.diagnostic();x1,y1,x2,y2=a.ball_bounds();x,y=(x1+x2)//2,(y1+y2)//2
    p=subprocess.Popen([a.device.ADB,'-s',a.device.SERIAL,'shell','input','swipe',str(x),str(y),str(x),str(y),'1800'],stdout=subprocess.DEVNULL)
    time.sleep(1);a.check(prefs(['quick_visible']).get('quick_visible')=='true','menu exists while finger is held');p.wait();time.sleep(.3)
    a.check(prefs(['quick_visible']).get('quick_visible')=='false','menu disappears as soon as finger releases')

def manager():
    original=json.loads(prefs(['quick_actions']).get('quick_actions','[]'));f.diagnostic('manage_quick')
    root=a.tree();white=[n for n in root.iter('node') if n.get('content-desc','').startswith('设置快捷位置')];grey=[n for n in root.iter('node') if n.get('content-desc','').startswith('最近应用位置')]
    a.check(len(white)==12 and len(grey)==3,'management displays 12 configurable and 3 recent circles')
    # Exercise one slot and restore exactly its original value through the same picker.
    idx=11;before=(original+['']*12)[idx]
    click('设置快捷位置12',True);click('软件功能');picker('区域截图')
    selected=json.loads(prefs(['quick_actions'])['quick_actions']);a.check(selected[idx]=='region','white circle accepts a software function')
    click('设置快捷位置12',True);click('本地应用');picker('空叶悬浮球')
    selected=json.loads(prefs(['quick_actions'])['quick_actions']);a.check(selected[idx]=='app:com.kongye.floatball','white circle accepts a local application')
    click('设置快捷位置12',True)
    if not before:click('清空此位置')
    elif before.startswith('app:'):raise AssertionError('Restore this pre-existing application through picker: '+before)
    else:click('软件功能');labels={'region':'区域截图','longshot':'长截图','settings':'打开空叶软件页面','notes':'便签'};picker(labels[before])
    click('完成');a.check(json.loads(prefs(['quick_actions'])['quick_actions'])[idx]==before,'test slot restored')

def card():
    result();root=a.tree();editor=next(n for n in root.iter('node') if n.get('class')=='android.widget.EditText');image=node('区域截图图片，点击放大');a.check(bounds(editor)[3]<=bounds(image)[1],'text stays above image')
    click('收起文字');a.check(prefs(['result_text_folded'])['result_text_folded']=='true','text folds independently');click('展开文字');click('收起图片');a.check(prefs(['result_image_folded'])['result_image_folded']=='true','image folds independently');click('展开图片')
    click('收起全部');a.check(prefs(['result_all_folded'])['result_all_folded']=='true','whole result collapses');click('展开全部')
    click('放大选择文字');a.verify_text('选择文字');root=a.tree();target=node('可选择的识别文字');x1,y1,x2,y2=bounds(target);swipe(x1+25,y1+42,x1+190,y1+42,600);click('复制所选文字');a.check(0<int(prefs(['last_selection_copy_length'])['last_selection_copy_length'])<40,'symbol copies only the drag-selected portion');click('返回截图结果')
    click('置顶截图结果');a.check(prefs(['result_pinned'])['result_pinned']=='true','result pins');title=node('拖动截图结果窗口');x1,y1,x2,y2=bounds(title);swipe((x1+x2)/2,(y1+y2)/2,(x1+x2)/2+50,(y1+y2)/2-160,600)
    a.check(int(prefs(['result_y']).get('result_y','-1'))>=0,'pinned result can be moved')
    tap(1060,400);a.check(prefs(['result_pinned'])['result_pinned']=='true','outside tap keeps pinned result')
    click('取消置顶');tap(1060,400);a.check(not any(n.get('content-desc')=='关闭截图结果' for n in a.tree().iter('node')),'outside tap closes unpinned transparent result')

def image_edit():
    result();click('编辑图片');a.verify_text('编辑图片');canvas=node('图片编辑画布，画笔涂画或拖动裁剪四边');x1,y1,x2,y2=bounds(canvas);swipe(x1+(x2-x1)*.3,y1+(y2-y1)*.45,x1+(x2-x1)*.7,y1+(y2-y1)*.55,400);click('裁剪')
    # Rendered screenshot region is wide and short; its fitted top lies near canvas centre.
    root=a.tree();canvas=node('图片编辑画布，画笔涂画或拖动裁剪四边');x1,y1,x2,y2=bounds(canvas)
    swipe(x1+4,(y1+y2)/2,x1+100,(y1+y2)/2,500);click('应用编辑')
    a.check(int(prefs(['last_edit_width'])['last_edit_width'])>0,'crop and brush edits render to a new bitmap')
    before=prefs(['last_capture_uri']).get('last_capture_uri');click('保存图片');wait('last_capture_uri',lambda x:x and x!=before)
    a.check(prefs(['last_capture_kind'])['last_capture_kind']=='RegionEdited','edited image saves to gallery');close_result()

def driver(kind):
    f.diagnostic();x1,y1,x2,y2=a.ball_bounds();x,y=(x1+x2)//2,(y1+y2)//2
    adb('shell','input','motionevent','DOWN',str(x),str(y))
    if kind=='qa_hold_manage':
        time.sleep(.9);a.check(prefs(['quick_visible']).get('quick_visible')=='true','orbit appears during held touch')
        target=984 if x<540 else 96
        adb('shell','input','motionevent','MOVE',str(target),str(y));time.sleep(.3)
        a.check(prefs(['last_quick_hover']).get('last_quick_hover')=='manage_quick','hover enlarges management icon and name')
        adb('shell','input','motionevent','UP',str(target),str(y));time.sleep(.5)
        a.check(prefs(['quick_visible']).get('quick_visible')=='false' and 'QuickSettingsActivity' in a.focus(),'release executes management and hides orbit')
    else:
        time.sleep(.1);adb('shell','input','motionevent','MOVE',str(x-60 if x>540 else x+60),str(y+30));time.sleep(.25)
        adb('shell','input','motionevent','MOVE','650','1050');time.sleep(.1)
        a.check(prefs(['drag_cross_state']).get('drag_cross_state')=='red','moving cross is red')
        time.sleep(.55);a.check(prefs(['drag_cross_state']).get('drag_cross_state')=='yellow','stationary cross becomes yellow')
        adb('shell','input','motionevent','MOVE','320','1750');time.sleep(.2);adb('shell','input','motionevent','UP','320','1750');time.sleep(.7)
        wait('last_ocr_state',lambda x:x in ('done','failed'));a.check(any(n.get('content-desc')=='关闭截图结果' for n in a.tree().iter('node')),'dragging from armed corner opens region result directly');close_result()

def release():
    a.main();original=a.ball_bounds();x1,y1,x2,y2=original;x,y=(x1+x2)//2,(y1+y2)//2
    swipe(x,y,x,y-180,120);time.sleep(.5);a.check('MainActivity' not in a.focus(),'release swipe hides main page')
    swipe(x,y,x,y-180,120);time.sleep(.5);a.check('MainActivity' in a.focus(),'release swipe opens main page again')
    adb('shell','input','motionevent','DOWN',str(x),str(y));time.sleep(.9)
    a.check('空叶按住快捷' in adb('shell','dumpsys','window','windows'),'release holds icon-only orbit open')
    target=984 if x<540 else 96;adb('shell','input','motionevent','MOVE',str(target),str(y));time.sleep(.2);adb('shell','input','motionevent','UP',str(target),str(y));time.sleep(.5)
    a.check('QuickSettingsActivity' in a.focus(),'release slide selects opposite management');root=a.tree();a.check(sum(n.get('content-desc','').startswith('设置快捷位置') for n in root.iter('node'))==12,'release white configuration slots available');click('完成')
    x1,y1,x2,y2=a.ball_bounds();x,y=(x1+x2)//2,(y1+y2)//2
    adb('shell','input','motionevent','DOWN',str(x),str(y));time.sleep(.1);adb('shell','input','motionevent','MOVE',str(x-60 if x>540 else x+60),str(y+30));time.sleep(.25);adb('shell','input','motionevent','MOVE','650','1050');time.sleep(.65);adb('shell','input','motionevent','MOVE','320','1750');time.sleep(.2);adb('shell','input','motionevent','UP','320','1750');time.sleep(.8)
    until=time.monotonic()+25
    while time.monotonic()<until:
        root=a.tree()
        if any(n.get('class')=='android.widget.EditText' and n.get('enabled')=='true' for n in root.iter('node')):break
        time.sleep(.3)
    a.check(any(n.get('content-desc')=='关闭截图结果' for n in root.iter('node')),'release armed drag opens OCR result')
    click('放大选择文字');target=node('可选择的识别文字');x1,y1,x2,y2=bounds(target);swipe(x1+25,y1+42,x1+180,y1+42,450);click('复制所选文字');click('返回截图结果')
    click('置顶截图结果');title=node('拖动截图结果窗口');x1,y1,x2,y2=bounds(title);swipe((x1+x2)/2,(y1+y2)/2,(x1+x2)/2+35,(y1+y2)/2-90,450);tap(10,300)
    a.check(any(n.get('content-desc')=='取消置顶' for n in a.tree().iter('node')),'release pinned result survives outside tap')
    click('收起全部');a.check(any(n.get('content-desc')=='展开全部' for n in a.tree().iter('node')),'release result fully collapses');click('展开全部');click('取消置顶');tap(10,300)
    a.check(not any(n.get('content-desc')=='关闭截图结果' for n in a.tree().iter('node')),'release unpinned transparent result closes outside')
    a.check(a.ball_bounds()==original,'release gesture restores original ball position');a.main()
    package=adb('shell','dumpsys','package','com.kongye.floatball');a.check('versionName=1.3.0' in package and 'DEBUGGABLE' not in package,'Sony runs signed 1.3.0 release')
    pid=adb('shell','pidof','com.kongye.floatball').strip();a.check('FATAL EXCEPTION' not in adb('shell','logcat','-b','crash','-d','--pid='+pid),'release process has no crash')

if __name__=='__main__':
    {'release':release,'held':held,'manager':manager,'card':card,'edit':image_edit,'hold-manage':lambda:driver('qa_hold_manage'),'drag':lambda:driver('qa_drag_crop')}[sys.argv[1]]()
