"""Reference-flow acceptance on Sony. No screen/media exports, only own controls and numeric receipts."""
import importlib.util
import pathlib
import re
import sys
import time
sys.stdout.reconfigure(encoding='utf-8', line_buffering=True)
spec=importlib.util.spec_from_file_location('features',pathlib.Path(__file__).with_name('features-device.py'))
f=importlib.util.module_from_spec(spec);spec.loader.exec_module(f)
a,adb,click,prefs=f.a,f.adb,f.click,f.prefs

def tap(x,y):
    adb('shell','input','tap',str(round(x)),str(round(y)));time.sleep(.5)

def swipe(x,y,xx,yy,duration=650):
    adb('shell','input','swipe',str(round(x)),str(round(y)),str(round(xx)),str(round(yy)),str(duration));time.sleep(.4)

def wait_value(key,predicate,seconds=15):
    until=time.monotonic()+seconds
    while time.monotonic()<until:
        value=prefs([key]).get(key,'')
        if predicate(value):return value
        time.sleep(.3)
    raise AssertionError('Receipt timed out: '+key)

def bounds(node):return tuple(map(int,re.findall(r'\d+',node.get('bounds'))))

def find(value):
    return next(n for n in a.tree().iter('node') if n.get('text')==value or n.get('content-desc')==value)

def scroll_find(value):
    for _ in range(25):
        if any(n.get('text')==value for n in a.tree().iter('node')):click(value);return
        swipe(540,2050,540,800,320)
    raise AssertionError('Cannot find own selector item: '+value)

def quick():
    # The quick cloud is already open or opened through an actual long press.
    if not any(n.get('content-desc')=='边缘快捷菜单' for n in a.tree().iter('node')):
        a.main();x1,y1,x2,y2=a.ball_bounds();swipe((x1+x2)//2,(y1+y2)//2,(x1+x2)//2,(y1+y2)//2,850)
    root=a.tree();gear=next(n for n in root.iter('node') if n.get('content-desc')=='管理快捷')
    region=next(n for n in root.iter('node') if n.get('content-desc')=='快捷：区域截图')
    gx=(bounds(gear)[0]+bounds(gear)[2])/2;rx=(bounds(region)[0]+bounds(region)[2])/2
    a.check(gx<300 and rx>700,'long-press quick actions and management are on opposite edges')
    original=prefs(['quick_actions']).get('quick_actions','')
    click('管理快捷');a.verify_text('管理边缘快捷');click('添加功能');click('返回');click('完成')
    a.check('"back"' in prefs(['quick_actions']).get('quick_actions',''),'adding a quick function persists')
    click('添加功能');click('返回');click('完成')
    click('添加应用');scroll_find('空叶悬浮球');click('完成')
    a.check('app:com.kongye.floatball' in prefs(['quick_actions']).get('quick_actions',''),'adding a selected installed application persists')
    # Verify an added item actually launches the app before restoring just that item.
    click('完成');f.diagnostic('quick');click('快捷：打开 空叶悬浮球')
    a.check('MainActivity' in a.focus(),'quick application launches its actual main activity')
    f.diagnostic('manage_quick');click('添加应用');scroll_find('空叶悬浮球');click('完成')
    a.check('app:com.kongye.floatball' not in prefs(['quick_actions']).get('quick_actions',''),'test application removed from quick selection')
    click('后移快捷第1项');changed=prefs(['quick_actions']).get('quick_actions','')
    click('前移快捷第2项');restored=prefs(['quick_actions']).get('quick_actions','')
    a.check(changed!=restored,'quick items can be reordered and restored')
    a.check(prefs(['gesture_down']).get('gesture_down')=='none','existing custom downward gesture preserved')
    click('完成');print('QUICK FLOW PASSED')

def region():
    f.diagnostic()
    x1,y1,x2,y2=a.ball_bounds();swipe((x1+x2)//2,(y1+y2)//2,540,1260,850)
    a.verify_text('识别并查看');a.check(a.ball_bounds()[0]==x1,'center drag opens crop and preserves prior ball edge')
    click('识别并查看');wait_value('last_ocr_state',lambda v:v in ('done','failed'),30)
    a.check(prefs(['last_ocr_state']).get('last_ocr_state')=='done','bundled Chinese OCR completes on the phone')
    root=a.tree();editor=next(n for n in root.iter('node') if n.get('class')=='android.widget.EditText')
    text=editor.get('text','');a.check('KONGYE' in text and any('\u4e00'<=v<='\u9fff' for v in text),'OCR recognizes generated English and Chinese text')
    image=next(n for n in root.iter('node') if n.get('content-desc')=='区域截图图片，点击放大')
    a.check(bounds(editor)[3]<=bounds(image)[1],'editable text is displayed above captured image')
    ex1,ey1,ex2,ey2=bounds(editor);tap((ex1+ex2)/2,(ey1+ey2)/2)
    adb('shell','input','keycombination','113','29');adb('shell','input','text','EDITED123');a.back()
    root=a.tree();value=next(n.get('text','') for n in root.iter('node') if n.get('class')=='android.widget.EditText')
    a.check(value=='EDITED123','recognized text can be edited in place')
    click('复制文字');click('区域截图图片，点击放大');a.verify_text('返回识别结果');click('返回识别结果')
    before=prefs(['last_capture_uri']).get('last_capture_uri');click('保存图片')
    wait_value('last_capture_uri',lambda v:v and v!=before)
    a.check(prefs(['last_capture_kind']).get('last_capture_kind')=='Region','region image saves independently of edited text')
    click('关闭');print('REGION FLOW PASSED')

def handle_bounds(name):
    dump=adb('shell','dumpsys','window','windows')
    for block in re.split(r'(?=  Window #)',dump):
        if '空叶长截图:'+name not in block:continue
        match=re.search(r'frame=\[(\d+),(\d+)\]\[(\d+),(\d+)\]',block)
        if match:return tuple(map(int,match.groups()))
    raise AssertionError('Missing crop handle '+name)

def manual():
    f.diagnostic('longshot');wait_value('manual_count',lambda v:v=='0')
    a.check('DiagnosticActivity' in a.focus(),'manual session starts over the generated test page')
    for name,key,dx,dy in [('调整左边','manual_left',110,0),('调整右边','manual_right',-110,0),('调整上边','manual_top',0,160),('调整下边','manual_bottom',0,-180)]:
        before=int(prefs([key])[key]);x1,y1,x2,y2=handle_bounds(name)
        swipe((x1+x2)/2,(y1+y2)/2,(x1+x2)/2+dx,(y1+y2)/2+dy)
        after=int(prefs([key])[key]);a.check(abs(after-before)>50,'manual crop adjusts '+key)
    before=int(prefs(['qa_scroll_y']).get('qa_scroll_y','0'));swipe(320,1700,320,900,600)
    a.check(int(prefs(['qa_scroll_y']).get('qa_scroll_y','0'))>before,'underlying page remains scrollable between captures')
    # Derived from the known test display and native toolbar dimensions, not screen-image recognition.
    density=420/160;plus=(540,2520-round(174*density));left_axis=(160,2520-round(183*density));right_axis=(365,2520-round(183*density))
    tap(*left_axis);a.check(prefs(['manual_axis']).get('manual_axis')=='vertical','vertical concatenation selected')
    for count in (1,2,3):
        a.check('DiagnosticActivity' in a.focus(),'generated page remains active before capture '+str(count))
        if count>1:swipe(320,1750,320,1100,650)
        tap(*plus);wait_value('manual_count',lambda v:v==str(count))
        print('Added screenshot',count,flush=True)
    a.check(prefs(['manual_order']).get('manual_order')=='1,2,3','each plus appends exactly one screenshot')
    tap(*right_axis);a.check(prefs(['manual_axis']).get('manual_axis')=='horizontal','horizontal concatenation selected')
    tap(round(36*density),round(66*density));a.verify_text('保存长图')
    metrics=prefs(['manual_preview_width','manual_preview_height','manual_left','manual_right','manual_top','manual_bottom'])
    w=int(metrics['manual_right'])-int(metrics['manual_left']);h=int(metrics['manual_bottom'])-int(metrics['manual_top'])
    a.check(abs(int(metrics['manual_preview_width'])-w*3)<=6 and abs(int(metrics['manual_preview_height'])-h)<=2,'horizontal large preview uses all three selected regions')
    click('编辑截图顺序');a.verify_text('调整顺序 · 删除');click('前移第3张')
    a.check(prefs(['manual_order']).get('manual_order')=='1,3,2','third screenshot moves before second')
    click('删除第2张');a.check(prefs(['manual_order']).get('manual_order')=='1,2','delete removes only the selected screenshot')
    click('返回大图预览');click('继续截图');tap(*left_axis);tap(round(36*density),round(66*density))
    metrics=prefs(['manual_preview_width','manual_preview_height']);a.check(abs(int(metrics['manual_preview_width'])-w)<=2 and abs(int(metrics['manual_preview_height'])-h*2)<=4,'vertical preview recomposes after ordering and deletion')
    before=prefs(['last_capture_uri']).get('last_capture_uri');click('保存长图');wait_value('last_capture_uri',lambda v:v and v!=before)
    receipt=prefs(['last_capture_kind','last_capture_width','last_capture_height']);print('Saved dimensions:',receipt)
    a.check(receipt.get('last_capture_kind')=='Long' and abs(int(receipt['last_capture_height'])-h*2)<=4,'edited long screenshot saves to device gallery')
    a.check(a.ball_bounds()[2]==1080,'ball returns after completing long screenshot');print('MANUAL FLOW PASSED')

def release():
    """Final signed APK: use its actual UI, with no run-as/Debug-only helpers."""
    a.main();x1,y1,x2,y2=a.ball_bounds()
    swipe((x1+x2)//2,(y1+y2)//2,540,1260,850);a.verify_text('识别并查看');click('识别并查看')
    until=time.monotonic()+30;editor=None
    while time.monotonic()<until:
        editor=next((n for n in a.tree().iter('node') if n.get('class')=='android.widget.EditText' and n.get('enabled')=='true'),None)
        if editor is not None:break
        time.sleep(.4)
    a.check(editor is not None and len(editor.get('text',''))>3,'release bundled OCR recognizes the app own main page')
    click('关闭');x1,y1,x2,y2=a.ball_bounds();swipe((x1+x2)//2,(y1+y2)//2,(x1+x2)//2,(y1+y2)//2,850)
    a.verify_text('管理快捷');click('管理快捷');a.verify_text('管理边缘快捷');click('完成')
    x1,y1,x2,y2=a.ball_bounds();swipe((x1+x2)//2,(y1+y2)//2,(x1+x2)//2,(y1+y2)//2,850);click('快捷：长截图')
    for _ in range(2):
        x1,y1,x2,y2=handle_bounds('手动长截图工具栏');tap((x1+x2)/2,(y1+y2)/2);time.sleep(1)
    tap(95,173);a.verify_text('保存长图');click('编辑截图顺序')
    root=a.tree();descriptions=[n.get('content-desc','') for n in root.iter('node')]
    a.check('删除第1张' in descriptions and '删除第2张' in descriptions,'release adds two images and opens thumbnail editor')
    click('删除第2张');click('返回大图预览');click('保存长图');time.sleep(1)
    a.check(a.ball_bounds()[2]==1080,'release saves the edited capture and restores ball')
    a.main();print('RELEASE FLOW PASSED')

if __name__=='__main__':
    {'quick':quick,'region':region,'manual':manual,'release':release}[sys.argv[1]]()
