"""Targeted new-feature checks. No screenshots, video or audio are exported."""
import importlib.util
import pathlib
import re
import sys
import time
import xml.etree.ElementTree as ET
sys.stdout.reconfigure(encoding='utf-8',line_buffering=True)
spec=importlib.util.spec_from_file_location('acceptance',pathlib.Path(__file__).with_name('acceptance-device.py'))
a=importlib.util.module_from_spec(spec);spec.loader.exec_module(a)
adb=a.adb

def click(value,contains=False):
    nodes=[n for n in a.tree().iter('node') if ((value in n.get('text','') or value in n.get('content-desc','')) if contains else n.get('text')==value or n.get('content-desc')==value)]
    nodes=[n for n in nodes if not n.get('resource-id','').endswith(('alertTitle','title_template'))]
    nodes.sort(key=lambda n:(n.get('content-desc','').startswith('预览'),not n.get('resource-id','').endswith('/text1'),n.get('clickable')!='true'))
    if not nodes:raise AssertionError('Missing test control: '+value)
    x1,y1,x2,y2=map(int,re.findall(r'\d+',nodes[0].get('bounds')))
    adb('shell','input','tap',str((x1+x2)//2),str((y1+y2)//2));time.sleep(.7)

def prefs(keys):
    root=ET.fromstring(adb('shell','run-as','com.kongye.floatball','cat','shared_prefs/kongye.xml'))
    return {n.get('name'):n.get('value',n.text or '') for n in root if n.get('name') in keys}

def diagnostic(feature=None):
    args=['shell','am','start','-W','-f','0x14000000','-n','com.kongye.floatball/.DiagnosticActivity']
    if feature:args.extend(['--es','feature',feature])
    adb(*args);time.sleep(1.2)

def themes():
    if any(n.get('resource-id','').endswith('/alertTitle') for n in a.tree().iter('node')):a.back()
    a.main();click('外观')
    for label,mode in [('深色模式','dark'),('浅色模式','light'),('定时开启','schedule'),('跟随系统','system')]:
        buttons=[n.get('text') for n in a.tree().iter('node') if n.get('text','').endswith('  ›') and any(s in n.get('text','') for s in ['跟随系统','深色模式','浅色模式','定时开启'])]
        if not buttons:raise AssertionError('Missing theme selector')
        click(buttons[0]);click(label);a.check(prefs(['theme']).get('theme')==mode,'theme mode '+mode+' persists')
        if mode=='schedule':a.verify_text('开启时间');a.verify_text('结束时间')

def captures():
    diagnostic('region');a.verify_text('保存区域')
    adb('shell','input','swipe','220','700','860','1800','450');click('保存区域');time.sleep(1)
    values=prefs(['last_capture_kind','last_capture_width','last_capture_height'])
    print('Region receipt:',values);a.check(values.get('last_capture_kind')=='Region' and abs(int(values.get('last_capture_width','0'))-640)<=8 and abs(int(values.get('last_capture_height','0'))-1100)<=8,'region capture saves selected dimensions within touch-injection rounding')
    diagnostic('longshot');before=int(prefs(['qa_scroll_y']).get('qa_scroll_y','0'));click('开始长截图');a.verify_text('已截取第 1 屏');click('继续一屏');time.sleep(1.7)
    status=[n.get('text') for n in a.tree().iter('node') if n.get('text','').startswith(('已拼接','无法可靠对齐','已到页面','无障碍','内存不足'))]
    print('Long-shot status:',status)
    a.check(any('已拼接 2 屏' in text for text in status),'scroll capture aligns the second frame')
    metrics=prefs(['qa_scroll_y','last_long_shift','last_long_source_width','last_long_output_width'])
    if all(k in metrics for k in ['qa_scroll_y','last_long_shift','last_long_source_width','last_long_output_width']):
        expected=(int(metrics['qa_scroll_y'])-before)*int(metrics['last_long_output_width'])/int(metrics['last_long_source_width'])
        print('Scroll displacement check:',{'expected':round(expected,2),'matched':metrics['last_long_shift']})
        a.check(abs(expected-int(metrics['last_long_shift']))<=6,'stitch alignment matches actual page scroll')
    click('完成保存');time.sleep(1)
    values=prefs(['last_capture_kind','last_capture_width','last_capture_height']);print('Long-shot receipt:',values);a.check(values.get('last_capture_kind')=='Long' and int(values.get('last_capture_height','0'))>1600,'long screenshot is published')

def favorite_self():
    def selected():
        root=ET.fromstring(adb('shell','run-as','com.kongye.floatball','cat','shared_prefs/kongye.xml'))
        return any(n.get('name')=='favorites' and any(v.text=='com.kongye.floatball' for v in n) for n in root)
    original=selected()
    def find_self():
        for _ in range(25):
            root=a.tree()
            if any(n.get('text')=='空叶悬浮球' for n in root.iter('node')):click('空叶悬浮球');return
            areas=[]
            for n in root.iter('node'):
                if n.get('class')=='android.widget.ScrollView' and n.get('scrollable')=='true':
                    x1,y1,x2,y2=map(int,re.findall(r'\d+',n.get('bounds')));areas.append(((x2-x1)*(y2-y1),(x1,y1,x2,y2)))
            if not areas:raise AssertionError('Cannot scroll the app selector')
            x1,y1,x2,y2=min(areas)[1];x=(x1+x2)//2;adb('shell','input','swipe',str(x),str(y1+(y2-y1)*4//5),str(x),str(y1+(y2-y1)//5),'350')
        raise AssertionError('Self app was not found')
    if not original:diagnostic('manage_apps');find_self()
    a.check(selected(),'common app selection persists')
    diagnostic('favorite_apps');a.verify_text('常用软件');a.verify_text('空叶悬浮球');click('空叶悬浮球');a.verify_text('KONGYE  /  轻一点，自在一点')
    if not original:diagnostic('manage_apps');find_self();a.check(not selected(),'test common app restored without changing other favorites')
    a.main()

def gesture_page():
    original=prefs(['gesture_up']).get('gesture_up','recents')
    labels={'menu':'快捷菜单','settings':'打开空叶软件页面','back':'返回','home':'主页','recents':'最近任务','recent_apps':'最近打开的软件','favorite_apps':'常用软件','notifications':'通知栏','quick_settings':'快捷设置','screenshot':'整屏截图','region':'区域截图','longshot':'长截图','record_silent':'无声录屏','record_sound':'带声音录屏','record_stop':'停止录屏','text':'提取文字','notes':'便签','browser':'悬浮浏览器','apps':'应用启动','files':'打开文件','lock':'锁屏','none':'不执行'}
    if original not in labels:print('User-specific application gesture preserved; assignment test skipped.');return
    a.main();click('手势');click('上划');click('打开空叶软件页面');a.check(prefs(['gesture_up']).get('gesture_up')=='settings','gesture assignment persists')
    diagnostic();x1,y1,x2,y2=a.ball_bounds();x,y=(x1+x2)//2,(y1+y2)//2;adb('shell','input','swipe',str(x),str(y),str(x),str(y-180),'120');time.sleep(.8)
    a.check('MainActivity' in a.focus(),'gesture opens the actual app main page')
    click('手势');click('上划');click(labels[original]);a.check(prefs(['gesture_up']).get('gesture_up')==original,'original gesture restored')

def automatic_capture():
    before_uri=prefs(['last_capture_uri']).get('last_capture_uri')
    diagnostic('longshot');click('开始长截图')
    node=next(n for n in a.tree().iter('node') if n.get('text')=='完成保存');x1,y1,x2,y2=map(int,re.findall(r'\d+',node.get('bounds')));finish=(str((x1+x2)//2),str((y1+y2)//2))
    print('Finish control test point:',finish)
    click('自动拼接')
    until=time.monotonic()+15
    while time.monotonic()<until:
        if int(prefs(['last_long_frame_count']).get('last_long_frame_count','0'))>=3:break
        time.sleep(.5)
    a.check(int(prefs(['last_long_frame_count']).get('last_long_frame_count','0'))>=3,'automatic capture adds at least three frames')
    adb('shell','input','tap',*finish)
    until=time.monotonic()+12
    while time.monotonic()<until:
        if prefs(['last_capture_uri']).get('last_capture_uri')!=before_uri:break
        time.sleep(.5)
    a.check(prefs(['last_capture_uri']).get('last_capture_uri')!=before_uri,'a new screenshot receipt is published')
    values=prefs(['last_capture_kind','last_capture_height','last_capture_width']);print('Automatic long-shot receipt:',values);a.check(values.get('last_capture_kind')=='Long' and int(values.get('last_capture_height','0'))>1600,'automatic capture can finish and publish')

if __name__=='__main__':
    command=sys.argv[1]
    if command=='themes':themes()
    elif command=='click':click(sys.argv[2],len(sys.argv)>3)
    elif command=='diagnostic':diagnostic(sys.argv[2] if len(sys.argv)>2 else None)
    elif command=='prefs':print(prefs(sys.argv[2:]))
    elif command=='captures':captures()
    elif command=='favorites':favorite_self()
    elif command=='gesture-page':gesture_page()
    elif command=='automatic':automatic_capture()
