"""PHK110 final release smoke test; no debug components or media export."""
import runpy,pathlib,json,time,re
m=runpy.run_path(str(pathlib.Path(__file__).with_name('v23-device.py')));q,a,v,adb,ui=m['q'],m['a'],m['v'],m['adb'],m['ui'];positions=json.loads(pathlib.Path('.tools/v23-positions.json').read_text(encoding='utf-8'));check=m['check'];click=ui['click'];own=ui['own']

def tap(label):
 n=next(n for nodes in positions.values() for n in nodes if n['label']==label);v.tap((n['x1']+n['x2'])/2,(n['y1']+n['y2'])/2)
def exists(title):
 try:return q['visible'](title)
 except AssertionError:return False

a.main();click('首页');m['manager']();m['find']('自动滚动长截图');click('确定');a.main();click('首页');check(any(n.get('content-desc')=='自动滚动长截图' for n in own()),'release supports custom ninth home tool');m['manager']();click('恢复默认');click('手势');check(not any(n.get('text')=='管理边缘快捷' for n in own()),'release gestures remove management entry');click('上划');check(all(n.get('text') not in ('管理边缘快捷','停止录屏') for n in own()),'release gesture action picker excludes both removed actions');click('打开空叶软件页面');click('首页');n=next(n for n in own() if n.get('content-desc')=='长截图');x1,y1,x2,y2=v.bounds(n)
# UI Automator may disconnect accessibility; restore only the service enabled before tests.
current=adb('shell','settings','get','secure','enabled_accessibility_services');service='com.kongye.floatball/com.kongye.floatball.AssistService';values=[] if current in ('','null') else current.split(':')
if service not in values:adb('shell','settings','put','secure','enabled_accessibility_services',':'.join(values+[service]))
time.sleep(1);v.tap((x1+x2)/2,(y1+y2)/2);time.sleep(.7);check(exists('空叶长截图:手动长截图工具栏'),'release longshot toolbar opens');tap('自动滚动长截图');time.sleep(.5);check(exists('空叶长截图:停止自动滚动'),'release toolbar starts automatic scroll session');tap('停止自动滚动');time.sleep(.5);check(exists('空叶长截图:手动长截图工具栏') and not exists('空叶长截图:停止自动滚动'),'release stop returns manual toolbar');tap('取消长截图');time.sleep(.5);check(exists('空叶悬浮球'),'release closing longshot restores ball');check(q['win']('空叶悬浮球')==[1118,585,1240,707],'release retains PHK110 original ball position and size');pkg=adb('shell','dumpsys','package','com.kongye.floatball');check('versionName=1.13.0' in pkg and 'DEBUGGABLE' not in pkg,'PHK110 runs 1.13.0 non-debug release');pid=adb('shell','pidof','com.kongye.floatball').strip();check('FATAL EXCEPTION' not in adb('shell','logcat','-b','crash','-d','--pid='+pid),'release current process has no crash');adb('shell','input','keyevent','3')
