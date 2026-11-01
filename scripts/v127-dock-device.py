import os,runpy,json,time,pathlib
os.environ['KONGYE_ADB_SERIAL']='525114fb';q=runpy.run_path('scripts/v118-device.py');adb,receiver,values,main=[q[k] for k in ['adb','receiver','values','main']];count=0
def check(ok,msg):
 global count
 assert ok,msg
 count+=1;print('PASS:',msg,flush=True)
def overlay():receiver('nodes');return json.loads(values()['qa_overlay_nodes'])
def activity():receiver('qa_v1221_ui');return json.loads(values()['qa_v1221_ui'])
def press(label,source=overlay):
 n=next(n for n in source() if n['label']==label);q['tap']((n['x1']+n['x2'])/2,(n['y1']+n['y2'])/2);time.sleep(.3)
def dock(label):
 n=next(n for n in overlay() if n['label']==label);yy=(n['y1']+n['y2'])//2;adb('shell','input','swipe',str(n['x2']-12),str(yy),'1',str(yy),'650');time.sleep(.75)
try:
 main();receiver('qa_v126_snapshot')
 if any(n['label']=='启动悬浮球' for n in q['nodes']()):q['click']('启动悬浮球');time.sleep(.8)
 receiver('qa_v126_browser');time.sleep(.7);press('置顶浏览器');dock('拖动浏览器窗口');n=next(n for n in overlay() if n['label']=='展开置顶浏览器标签');check(120<n['width']<130 and 160<n['height']<175,'browser morphs to requested 42x56dp icon capsule');press('展开置顶浏览器标签');time.sleep(.7);check(any(n['label']=='拖动浏览器窗口' for n in overlay()),'browser expands to usable pinned content');press('关闭浏览器')
 adb('shell','am','start','-W','-n','com.kongye.floatball/.DiagnosticActivity','--es','feature','qa_show_last_record');time.sleep(.7);press('置顶录制结果',activity);adb('shell','input','keyevent','3');time.sleep(.35);dock('拖动录制结果窗口');n=next(n for n in overlay() if n['label']=='展开置顶录制标签');check(n['height']<200 and any(i['name']=='record' for i in n['icons']),'recorded video morphs to video-icon capsule above launcher');press('展开置顶录制标签');time.sleep(.7);press('录屏视频预览，点击放大');check(any(n['label']=='录制大图预览暗色背景' for n in overlay()),'expanded recording still supports large video preview');adb('shell','input','keyevent','4');time.sleep(.4);press('取消置顶录制结果');time.sleep(.5);press('关闭录制结果',activity);main()
 receiver('qa_v126_gesture_toggle');receiver('qa_v126_snapshot');v=values();check(float(v.get('qa_v126_main_y','0'))<0,'SVG-guided close transition starts moving toward top edge');time.sleep(1.2);receiver('qa_v126_gesture_toggle');time.sleep(.2);receiver('qa_v126_snapshot');check(float(values()['qa_v126_main_y'])>0,'SVG-guided entrance moves upward from bottom');time.sleep(1);receiver('qa_v126_snapshot');check(abs(float(values()['qa_v126_main_y']))<1,'SVG-guided entrance finishes at actual app screen')
finally:receiver('qa_v126_close_page');main()
print(count,'additional checks passed; no phone media exported.',flush=True)
