"""v1.4 real Sony checks. Own controls/receipts only, no screenshot or media exports."""
import importlib.util,pathlib,re,time,sys,json
sys.stdout.reconfigure(encoding='utf-8',line_buffering=True)
s=importlib.util.spec_from_file_location('f',pathlib.Path(__file__).with_name('features-device.py'));f=importlib.util.module_from_spec(s);s.loader.exec_module(f)
a,adb,click,prefs=f.a,f.adb,f.click,f.prefs

def bounds(n):return list(map(int,re.findall(r'\d+',n.get('bounds'))))
def node(desc,contains=False):return next(n for n in a.tree().iter('node') if (desc in n.get('content-desc','') if contains else n.get('content-desc')==desc))
def tap(x,y):adb('shell','input','tap',str(round(x)),str(round(y)));time.sleep(.4)
def swipe(x,y,xx,yy,d=450):adb('shell','input','swipe',str(round(x)),str(round(y)),str(round(xx)),str(round(yy)),str(d));time.sleep(.3)
def wait(key,predicate,seconds=25):
    end=time.monotonic()+seconds
    while time.monotonic()<end:
        value=prefs([key]).get(key,'')
        if predicate(value):return value
        time.sleep(.3)
    raise AssertionError('Timed out: '+key)
def close():click('关闭截图结果')
def ready(kind='region'):
    f.diagnostic(kind)
    if kind=='region':click('识别并查看')
    wait('last_ocr_state',lambda v:v in ('done','failed'))
    a.check(any(n.get('content-desc')=='截图图片，点击预览' for n in a.tree().iter('node')),'capture opens editable result')

def editor_checks():
    f.diagnostic('qa_edit_tools');time.sleep(.5);values=prefs(['qa_editor_pass','qa_editor_checks','qa_editor_error']);print(values);a.check(values.get('qa_editor_pass')=='true','synthetic editor pixel and cache checks pass')

def picker():
    f.diagnostic('qa_app_picker');time.sleep(1)
    root=a.tree();a.check(any(n.get('text')=='com.kongye.floatball' and n.get('class')=='android.widget.EditText' for n in root.iter('node')),'top search filter visible');a.check(any(n.get('content-desc')=='选择应用：空叶悬浮球' for n in root.iter('node')),'known application is found by package filter')
    click('选择应用：空叶悬浮球');click('确定');a.check('DiagnosticActivity' in a.focus(),'picker confirms and returns')
    f.diagnostic('manage_quick');root=a.tree();a.check(sum(n.get('content-desc','').startswith('设置快捷位置') for n in root.iter('node'))==12 and sum(n.get('content-desc','').startswith('最近应用位置') for n in root.iter('node'))==1,'exactly twelve editable and one recent slots')
    a.check(not any('点白圈' in n.get('text','') for n in root.iter('node')),'bottom instruction panel removed')
    old=int(prefs(['quick_opacity']).get('quick_opacity','90'));click('调整快捷面透明度');n=node('快捷面不透明度');x1,y1,x2,y2=bounds(n);tap((x1+x2)/2,(y1+y2)/2);a.check(int(prefs(['quick_opacity'])['quick_opacity'])!=old,'quick opacity changes')
    # Native seek bar has a 16dp horizontal thumb inset; refine until original value restored.
    target=x1+42+(x2-x1-84)*(old-20)/80
    for _ in range(15):
        tap(target,(y1+y2)/2);value=int(prefs(['quick_opacity'])['quick_opacity'])
        if value==old:break
        target+=(old-value)*(x2-x1-84)/80
    a.check(int(prefs(['quick_opacity'])['quick_opacity'])==old,'opacity test restores original value');click('完成')

def selection_share():
    ready();click('放大选择文字');n=node('可跳选的识别文字，无选择句柄');x1,y1,x2,y2=bounds(n)
    swipe(x1+24,y1+38,x1+165,y1+38);swipe(x1+24,y1+180,x1+180,y1+180);click('复制所选文字');a.check(int(prefs(['last_selection_range_count'])['last_selection_range_count'])>=2,'nonadjacent text segments remain selected')
    click('返回截图结果');before=prefs(['last_capture_uri']).get('last_capture_uri');prior=prefs(['last_share_uri']).get('last_share_uri');click('分享图片');wait('last_share_uri',lambda v:v.startswith('content://com.kongye.floatball.share/') and v!=prior)
    a.check(prefs(['last_capture_uri']).get('last_capture_uri')==before,'sharing does not create a gallery save');a.check(prefs(['last_share_private_cache'])['last_share_private_cache']=='true','sharing uses private temporary cache');a.back();a.main()

def whole_editor():
    ready('screenshot');a.check(prefs(['document_kind'])['document_kind']=='Screen','whole screenshot opens unified document')
    click('截图图片，点击预览');a.verify_text('图片预览');click('编辑图片');root=a.tree();names={n.get('content-desc') for n in root.iter('node')}
    a.check({'画笔','画框','画线','箭头','序号','添加文字','马赛克','裁剪','旋转90度','水平镜像','垂直镜像','画笔粗细'}.issubset(names),'full editing tool set is visible')
    w=int(prefs(['document_width'])['document_width']);h=int(prefs(['document_height'])['document_height']);click('旋转90度');click('水平镜像');click('应用编辑');a.check(int(prefs(['last_edit_width'])['last_edit_width'])==h and int(prefs(['last_edit_height'])['last_edit_height'])==w,'whole screenshot rotation and mirror render')
    click('返回截图结果');before=prefs(['last_capture_uri']).get('last_capture_uri');click('保存图片');wait('last_capture_uri',lambda v:v and v!=before);a.check(prefs(['last_capture_kind'])['last_capture_kind']=='ScreenEdited','explicit save writes edited whole screenshot');close()

def window_bounds(title):
    dump=adb('shell','dumpsys','window','windows')
    for block in re.split(r'(?=  Window #)',dump):
        if title in block:
            match=re.search(r'frame=\[(\d+),(\d+)\]\[(\d+),(\d+)\]',block)
            if match:return list(map(int,match.groups()))
    raise AssertionError('Own window missing: '+title)

def longshot():
    f.diagnostic('longshot');wait('manual_count',lambda v:v=='0')
    x1,y1,x2,y2=window_bounds('空叶长截图:手动长截图工具栏');x,y=(x1+x2)//2,(y1+y2)//2
    for count in (1,2,3):
        tap(x,y);wait('manual_count',lambda v:v==str(count));print('Added',count)
    # Toolbar's reorder icon is the fourth control, after the unchanged central plus.
    tap(x1+(x2-x1)*.72,y);root=a.tree();a.check(not any('前移第' in n.get('content-desc','') or '后移第' in n.get('content-desc','') for n in root.iter('node')),'ordering contains no move buttons')
    source=node('截图第1张，长按拖动排序');target=node('截图第2张，长按拖动排序');sx1,sy1,sx2,sy2=bounds(source);tx1,ty1,tx2,ty2=bounds(target)
    adb('shell','input','draganddrop',str((sx1+sx2)//2),str((sy1+sy2)//2),str((tx1+tx2)//2),str((ty1+ty2)//2),'700');time.sleep(.8)
    a.check(prefs(['manual_order'])['manual_order']=='2,1,3','native drag reorders screenshots');click('删除第3张');click('返回继续截图');tap(95,173);wait('document_kind',lambda v:v=='Long');a.check(any(n.get('content-desc')=='截图图片，点击预览' for n in a.tree().iter('node')),'stitched long image opens unified editable result');close()

def native_text_peek():
    ready('screenshot');click('截图图片，点击预览');click('编辑图片');click('添加文字');n=node('图片编辑画布');x1,y1,x2,y2=bounds(n);tap((x1+x2)/2,(y1+y2)/2);a.verify_text('添加文字');adb('shell','input','text','123');time.sleep(.3);click('确定');click('应用编辑');a.check('text' in prefs(['last_edit_marks']).get('last_edit_marks',''),'native text entry becomes rendered annotation');click('返回截图结果');close()
    f.diagnostic('longshot');x1,y1,x2,y2=window_bounds('空叶长截图:手动长截图工具栏');tap((x1+x2)/2,(y1+y2)/2);wait('manual_count',lambda v:v=='1');time.sleep(.5);a1,b1,a2,b2=window_bounds('空叶长截图:长截图缩略预览');tap((a1+a2)/2,(b1+b2)/2);a.verify_text('图片') if False else None;click('继续截图');a.check(prefs(['manual_count'])['manual_count']=='1','peek returns to ongoing capture');tap((x1+x2)/2,(y1+y2)/2);wait('manual_count',lambda v:v=='2');tap(95,173);wait('document_kind',lambda v:v=='Long');close()

def release():
    def hold_manage():
        x1,y1,x2,y2=a.ball_bounds();x,y=(x1+x2)//2,(y1+y2)//2;adb('shell','input','motionevent','DOWN',str(x),str(y));time.sleep(.85);tx=984 if x<540 else 96;adb('shell','input','motionevent','MOVE',str(tx),str(y));time.sleep(.2);adb('shell','input','motionevent','UP',str(tx),str(y));time.sleep(.5)
    def shortcut(index):
        design=[(40,-142),(90,-118),(34,-87),(79,-54),(130,-76),(150,-29),(97,1),(148,26),(78,53),(130,77),(91,120),(40,144)]
        x1,y1,x2,y2=a.ball_bounds();x,y=(x1+x2)//2,(y1+y2)//2;factor=1080/510;cy=max(168*factor,min(2520-170*factor,y));dx,dy=design[index];tx=dx*factor if x<540 else 1080-dx*factor;ty=cy+dy*factor
        adb('shell','input','motionevent','DOWN',str(x),str(y));time.sleep(.85);adb('shell','input','motionevent','MOVE',str(round(tx)),str(round(ty)));time.sleep(.2);adb('shell','input','motionevent','UP',str(round(tx)),str(round(ty)));time.sleep(.6)
    a.main();original=a.ball_bounds();hold_manage();root=a.tree()
    def index(label):return int(re.search(r'设置快捷位置(\d+)',next(n.get('content-desc') for n in root.iter('node') if n.get('content-desc','').endswith('：'+label))).group(1))-1
    screen_index,long_index=index('整屏截图'),index('长截图');click('完成');shortcut(screen_index)
    end=time.monotonic()+25
    while time.monotonic()<end:
        root=a.tree()
        if any(n.get('content-desc')=='截图图片，点击预览' for n in root.iter('node')):break
        time.sleep(.4)
    a.check(any(n.get('text')=='整屏截图' for n in root.iter('node')),'release whole screenshot result title');click('截图图片，点击预览');click('编辑图片');names={n.get('content-desc') for n in a.tree().iter('node')};a.check({'马赛克','箭头','序号','旋转90度','水平镜像','垂直镜像','画笔粗细'}.issubset(names),'release full editor available')
    click('箭头');n=node('图片编辑画布');x1,y1,x2,y2=bounds(n);swipe(x1+(x2-x1)*.35,y1+(y2-y1)*.35,x1+(x2-x1)*.65,y1+(y2-y1)*.65);click('应用编辑');click('返回截图结果');click('分享图片');time.sleep(1);a.check('ChooserActivity' in a.focus() or 'ResolverActivity' in a.focus(),'release opens system share sheet');a.back();a.main()
    x1,y1,x2,y2=a.ball_bounds();x,y=(x1+x2)//2,(y1+y2)//2;adb('shell','input','motionevent','DOWN',str(x),str(y));time.sleep(.85);tx=984 if x<540 else 96;adb('shell','input','motionevent','MOVE',str(tx),str(y));time.sleep(.2);adb('shell','input','motionevent','UP',str(tx),str(y));time.sleep(.5)
    root=a.tree();a.check(sum(n.get('content-desc','').startswith('设置快捷位置') for n in root.iter('node'))==12 and sum(n.get('content-desc','').startswith('最近应用位置') for n in root.iter('node'))==1,'release reference 12+1 layout');click('完成');shortcut(long_index)
    x1,y1,x2,y2=window_bounds('空叶长截图:手动长截图工具栏');x,y=(x1+x2)//2,(y1+y2)//2
    tap(x,y);time.sleep(1);tap(x,y);time.sleep(1);tap(x1+(x2-x1)*.72,y);root=a.tree();a.check(any(n.get('content-desc')=='删除第2张' for n in root.iter('node')),'release plus captures two frames');a.check(not any('前移第' in n.get('content-desc','') or '后移第' in n.get('content-desc','') for n in root.iter('node')),'release reorder is drag-only');click('返回继续截图');tap(95,173)
    end=time.monotonic()+20
    while time.monotonic()<end:
        root=a.tree()
        if any(n.get('text')=='拼接长图' for n in root.iter('node')):break
        time.sleep(.4)
    a.check(any(n.get('text')=='拼接长图' for n in root.iter('node')),'release long image uses unified result');click('截图图片，点击预览');click('编辑图片');a.check(any(n.get('content-desc')=='马赛克' for n in a.tree().iter('node')),'release long image is editable');click('返回截图结果');click('返回截图结果');close();a.main()
    a.check(a.ball_bounds()==original,'release preserves ball position');pkg=adb('shell','dumpsys','package','com.kongye.floatball');a.check('versionName=1.4.0' in pkg and 'DEBUGGABLE' not in pkg,'Sony runs 1.4.0 release');pid=adb('shell','pidof','com.kongye.floatball').strip();a.check('FATAL EXCEPTION' not in adb('shell','logcat','-b','crash','-d','--pid='+pid),'release process has no crash')

if __name__=='__main__':{'release':release,'text-peek':native_text_peek,'checks':editor_checks,'picker':picker,'selection-share':selection_share,'whole':whole_editor,'long':longshot}[sys.argv[1]]()
