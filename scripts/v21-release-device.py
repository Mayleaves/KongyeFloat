"""Sony release regression using own observed controls; no diagnostics/media export."""
import runpy,pathlib,json,time,re
m=runpy.run_path(str(pathlib.Path(__file__).with_name('v21-device.py')));a,v,adb,win,layer=m['a'],m['v'],m['adb'],m['win'],m['layer'];legacy=m['m']['m'];positions=json.loads(pathlib.Path('.tools/v21-positions.json').read_text(encoding='utf-8'));right=True;d=2.625

def check(ok,label):a.check(ok,label)
def visible(title):
 try:return m['visible'](title)
 except AssertionError:return False

def tap(stage,label):
 n=next(n for n in positions[stage] if n['label']==label);v.tap((n['x1']+n['x2'])/2,(n['y1']+n['y2'])/2)

def tile(label):
 a.main();legacy['click']('首页');legacy['find_click'](label)

def camera(label):
 global right
 x1,y1,x2,y2=win('空叶录制控制');expanded=x2-x1>250
 if label=='expand':
  if not expanded:right=x1+27*d>540
  v.tap(x1+((96 if right else 10)*d if expanded else 0)+27*d,y1+(116*d if expanded else 0)+27*d);return
 if not expanded:camera('expand');return camera(label)
 dx,dy=((118 if right else 32)*d,254*d);v.tap(x1+dx,y1+dy)

a.main();time.sleep(1);check(layer('空叶悬浮球')==2032 and visible('空叶悬浮球'),'release automatically uses accessibility overlay');legacy['click']('外观');legacy['find_click']('增强悬浮层');check(layer('空叶悬浮球')==2038,'release compatibility switch selects ordinary overlay');legacy['find_click']('增强悬浮层');check(layer('空叶悬浮球')==2032,'release switch restores enhanced overlay')
tile('悬浮浏览器');check(layer('空叶浏览器')==2032,'release browser uses enhanced overlay');tap('browser','置顶浏览器');tap('browser_pinned','收起浏览器');box=win('空叶浏览器');check(box[3]-box[1]<300,'release enhanced browser folds correctly');tap('browser_folded','关闭浏览器');check(not visible('空叶浏览器'),'release enhanced browser closes')
tile('录屏');check(layer('空叶录制准备:录屏选区')==2032,'release recording selector uses enhanced overlay');tap('record','录制水印');check(layer('空叶录制水印画布')==2032,'release watermark uses enhanced overlay');tap('watermark','完成水印编辑');check(not visible('空叶录制水印画布') and visible('空叶录制准备:录屏选区'),'release untouched watermark confirmation returns');tap('record','开始录屏');legacy['click']('立即开始');time.sleep(4.2);check(layer('空叶录制控制')==2032 and not visible('空叶悬浮球'),'release recording control replaces ball in enhanced layer');camera('cancel')
end=time.monotonic()+30
while time.monotonic()<end and not visible('空叶录制准备:录屏选区'):time.sleep(.3)
check(visible('空叶录制准备:录屏选区') and visible('空叶悬浮球'),'release cancel returns preparation and restores ball');x1,y1,x2,y2=win('空叶录制准备:关闭录制准备');v.tap((x1+x2)/2,(y1+y2)/2);adb('shell','input','keyevent','3');check(win('空叶悬浮球')==[954,1087,1080,1213],'release preserves original ball position');pkg=adb('shell','dumpsys','package','com.kongye.floatball');check('versionName=1.11.0' in pkg and 'DEBUGGABLE' not in pkg,'Sony runs 1.11.0 non-debug release');pid=adb('shell','pidof','com.kongye.floatball').strip();check('FATAL EXCEPTION' not in adb('shell','logcat','-b','crash','-d','--pid='+pid),'release current process has no crash')
