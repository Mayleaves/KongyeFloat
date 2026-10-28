"""Sony 1.5 regression: own UI only; screen/media export remains disabled."""
import importlib.util,pathlib,time,re,sys
sys.stdout.reconfigure(encoding='utf-8',line_buffering=True)
s=importlib.util.spec_from_file_location('v',pathlib.Path(__file__).with_name('v14-device.py'));v=importlib.util.module_from_spec(s);s.loader.exec_module(v)
a,adb,click,prefs=v.a,v.adb,v.click,v.prefs

def result_actions():
    root=a.tree();items=[(v.bounds(n)[0],n.get('content-desc')) for n in root.iter('node') if n.get('content-desc') in ('保存图片','分享图片','编辑图片')]
    a.check([desc for _,desc in sorted(items)]==['保存图片','分享图片','编辑图片'],'result action order is save, share, edit')

def text():
    v.ready('screenshot');result_actions();click('编辑图片');click('添加文字');n=v.node('图片编辑画布');x1,y1,x2,y2=v.bounds(n);v.tap((x1+x2)/2,(y1+y2)/2);time.sleep(.6)
    root=a.tree();a.check(any(n.get('text')=='编辑图片' for n in root.iter('node')) and any(n.get('content-desc')=='图片编辑画布' for n in root.iter('node')),'text entry keeps image editor and canvas visible')
    a.check(any(n.get('content-desc')=='图片文字输入' for n in root.iter('node')),'inline text field appears')
    ime=adb('shell','dumpsys','input_method');a.check('mInputShown=true' in ime or 'mIsInputViewShown=true' in ime,'keyboard opens inside image editor')
    adb('shell','input','text','123');click('完成文字输入');click('应用编辑');a.check('text' in prefs(['last_edit_marks']).get('last_edit_marks',''),'inline text applies to image');click('返回截图结果')
    # New editor session starts with previous image as its baseline. Add then delete a second label.
    click('编辑图片');click('添加文字');n=v.node('图片编辑画布');x1,y1,x2,y2=v.bounds(n);v.tap((x1+x2)/2,(y1+y2)/2);adb('shell','input','text','456');click('删除此文字');click('应用编辑');a.check(prefs(['last_edit_marks']).get('last_edit_marks')=='','delete removes inline text annotation');click('返回截图结果');v.close()

def selection():
    v.ready('screenshot');click('放大选择文字');click('全选文字');click('复制所选文字');before=int(prefs(['last_selection_copy_length'])['last_selection_copy_length']);n=v.node('可跳选的识别文字，无选择句柄');x1,y1,x2,y2=v.bounds(n)
    # A drag through selected text subtracts a range instead of re-adding it.
    v.swipe(x1+22,y1+25,x1+220,y1+90);click('复制所选文字');a.check(int(prefs(['last_selection_range_count'])['last_selection_range_count'])>=1 and int(prefs(['last_selection_copy_length'])['last_selection_copy_length'])<before,'drag over selected text subtracts from selection');click('返回截图结果');v.close()

def longshot():
    v.f.diagnostic('longshot');x1,y1,x2,y2=v.window_bounds('空叶长截图:手动长截图工具栏');x,y=(x1+x2)//2,(y1+y2)//2
    for count in (1,2,3):v.tap(x,y);v.wait('manual_count',lambda value:value==str(count))
    a1,b1,a2,b2=v.window_bounds('空叶长截图:长截图缩略预览');v.tap((a1+a2)/2,(b1+b2)/2);click('调整截图顺序和删除')
    source=v.node('截图第1张，长按拖动排序');target=v.node('截图第2张，长按拖动排序');sx1,sy1,sx2,sy2=v.bounds(source);tx1,ty1,tx2,ty2=v.bounds(target)
    adb('shell','input','draganddrop',str((sx1+sx2)//2),str((sy1+sy2)//2),str((tx1+tx2)//2),str((ty1+ty2)//2),'700');time.sleep(.5);a.check(prefs(['manual_order'])['manual_order']=='2,1,3','preview editor drag reorders individual shots');click('删除第3张');a.check(prefs(['manual_count'])['manual_count']=='2','preview editor deletes individual shots');click('确认拼接');v.wait('document_kind',lambda value:value=='Long');root=a.tree();a.check(any(n.get('text')=='拼接长图' for n in root.iter('node')),'confirm goes directly to shareable result');result_actions();v.close()

def red_cancel():
    a.main();original=a.ball_bounds();x1,y1,x2,y2=original;x,y=(x1+x2)//2,(y1+y2)//2
    adb('shell','input','motionevent','DOWN',str(x),str(y));time.sleep(.32);adb('shell','input','motionevent','MOVE','540','1200');adb('shell','input','motionevent','UP','540','1200');time.sleep(.8)
    a.check(prefs(['drag_cross_state']).get('drag_cross_state')=='red','drag released before yellow arm')
    own=adb('shell','dumpsys','window','windows');a.check('空叶截图十字' not in own,'early release removes cross');a.check(not any(n.get('text')=='识别并查看' or n.get('content-desc')=='截图图片，点击预览' for n in a.tree().iter('node')),'red-only release opens no capture')
    # Restore original edge position using a continuing native drag (no still pause).
    bx1,by1,bx2,by2=a.ball_bounds();bx,by=(bx1+bx2)//2,(by1+by2)//2;adb('shell','input','motionevent','DOWN',str(bx),str(by));time.sleep(.32);adb('shell','input','motionevent','MOVE',str(x),str(y));adb('shell','input','motionevent','UP',str(x),str(y));time.sleep(.8);a.check(a.ball_bounds()==original,'red cancellation test restores ball position')

def release():
    def held(target=None,index=None):
        x1,y1,x2,y2=a.ball_bounds();x,y=(x1+x2)//2,(y1+y2)//2
        if index is not None:
            dx,dy=[(40,-142),(90,-118),(34,-87),(79,-54),(130,-76),(150,-29),(97,1),(148,26),(78,53),(130,77),(91,120),(40,144)][index];factor=1080/510;tx=dx*factor if x<540 else 1080-dx*factor;ty=max(168*factor,min(2520-170*factor,y))+dy*factor
        else:tx,ty=(984 if x<540 else 96),y
        adb('shell','input','motionevent','DOWN',str(x),str(y));time.sleep(.85);adb('shell','input','motionevent','MOVE',str(round(tx)),str(round(ty)));time.sleep(.2);adb('shell','input','motionevent','UP',str(round(tx)),str(round(ty)));time.sleep(.5)
    def wait_result(title):
        end=time.monotonic()+25
        while time.monotonic()<end:
            root=a.tree()
            if any(n.get('text')==title for n in root.iter('node')):return
            time.sleep(.3)
        raise AssertionError('Release result missing: '+title)
    a.main();original=a.ball_bounds();held();root=a.tree()
    def slot(label):return int(re.search(r'设置快捷位置(\d+)',next(n.get('content-desc') for n in root.iter('node') if n.get('content-desc','').endswith('：'+label))).group(1))-1
    screen_index,long_index=slot('整屏截图'),slot('长截图');click('完成');held(index=screen_index);wait_result('整屏截图');result_actions();click('编辑图片');click('添加文字');x1,y1,x2,y2=v.bounds(v.node('图片编辑画布'));v.tap((x1+x2)/2,(y1+y2)/2)
    root=a.tree();a.check(any(n.get('text')=='编辑图片' for n in root.iter('node')) and any(n.get('content-desc')=='图片文字输入' for n in root.iter('node')),'release keeps text entry in image editor');ime=adb('shell','dumpsys','input_method');a.check('mInputShown=true' in ime or 'mIsInputViewShown=true' in ime,'release keyboard opens inline');adb('shell','input','text','789');click('完成文字输入');click('应用编辑');click('返回截图结果');click('分享图片');a.check('ChooserActivity' in a.focus() or 'ResolverActivity' in a.focus(),'release share opens chooser');a.back();a.main();held(index=long_index)
    x1,y1,x2,y2=v.window_bounds('空叶长截图:手动长截图工具栏');x,y=(x1+x2)//2,(y1+y2)//2;v.tap(x,y);time.sleep(1);v.tap(x,y);time.sleep(1);a1,b1,a2,b2=v.window_bounds('空叶长截图:长截图缩略预览');v.tap((a1+a2)/2,(b1+b2)/2);click('调整截图顺序和删除');root=a.tree();a.check(any(n.get('content-desc')=='删除第2张' for n in root.iter('node')) and any(n.get('content-desc')=='截图第1张，长按拖动排序' for n in root.iter('node')),'release preview editor retains per-frame delete and drag order');click('删除第2张');click('确认拼接');wait_result('拼接长图');result_actions();v.close();a.main();a.check(a.ball_bounds()==original,'release preserves ball position');pkg=adb('shell','dumpsys','package','com.kongye.floatball');a.check('versionName=1.5.0' in pkg and 'DEBUGGABLE' not in pkg,'Sony runs 1.5.0 release');pid=adb('shell','pidof','com.kongye.floatball').strip();a.check('FATAL EXCEPTION' not in adb('shell','logcat','-b','crash','-d','--pid='+pid),'release process has no crash')

if __name__=='__main__':{'release':release,'text':text,'selection':selection,'long':longshot,'red':red_cancel,'checks':v.editor_checks}[sys.argv[1]]()
