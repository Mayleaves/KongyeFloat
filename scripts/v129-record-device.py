"""Record only a generated in-app pattern; keep all output private on phone."""
import os,runpy,time,json,re,pathlib,xml.etree.ElementTree as ET
os.environ['KONGYE_ADB_SERIAL']='525114fb';q=runpy.run_path('scripts/v118-device.py');adb,receiver,values,main=[q[k] for k in ['adb','receiver','values','main']];assert values().get('recording_state','idle')=='idle';saved={k:values().get(k) for k in ['record_resolution','gif_resolution','gif_fps','record_bitrate','record_hide','recording_mode','last_record_uri','last_gif_path']};pathlib.Path('.tools/v129-media-original.json').write_text(json.dumps(saved),encoding='utf-8');count=0

def check(ok,label):
 global count
 assert ok,label
 count+=1;print('PASS:',label,flush=True)
def overlay():receiver('nodes');return json.loads(values()['qa_overlay_nodes'])
def press(label):
 n=next(n for n in overlay() if n['label']==label);q['tap']((n['x1']+n['x2'])/2,(n['y1']+n['y2'])/2);time.sleep(.25)
def timer():
 n=overlay();ball=next(x for x in n if x['label']=='展开录制控制');clock=next(x for x in n if re.fullmatch(r'\d{2}:\d{2}',x['label']));return n,ball,clock
try:
 main();receiver('qa_v126_snapshot');receiver('qa_v126_restore',{'record_resolution':480,'gif_resolution':240,'gif_fps':5,'record_hide':False,'recording_mode':'silent'})
 for gif in [False,True]:
  kind='GIF' if gif else 'Video';main();receiver('qa_v126_snapshot');adb('shell','am','start','-W','-n','com.kongye.floatball/.DiagnosticActivity','--es','feature','qa_record_gif' if gif else 'qa_record_video');time.sleep(1.2);press('开始录制 GIF' if gif else '开始录屏');time.sleep(.6);adb('shell','uiautomator','dump','/data/local/tmp/kongye-v129-projection.xml');tree=ET.fromstring(adb('exec-out','cat','/data/local/tmp/kongye-v129-projection.xml'));n=next(n for n in tree.iter('node') if n.get('text') in ['开始','立即开始','开始录制','Start now','Start recording'] and n.get('clickable')=='true');box=list(map(int,re.findall(r'\d+',n.get('bounds'))));q['tap']((box[0]+box[2])/2,(box[1]+box[3])/2)
  end=time.monotonic()+12
  while values().get('recording_state')!='recording' and time.monotonic()<end:time.sleep(.25)
  check(values().get('recording_state')=='recording',kind+' starts on generated test pattern');time.sleep(.5);n,ball,clock=timer();check(clock['y1']>=ball['y2'],kind+' collapsed recording timer is below ball');root=next(x for x in n if x['label']=='录制控制图标，可拖动');check(clock['y2']<=root['y2'],kind+' timer fits inside control touch window');press('展开录制控制');n,ball,clock=timer();check(clock['y1']>=ball['y2'],kind+' expanded recording timer is below ball');controls=[x for x in n if x['label'] in ['暂停录制','停止录制','编辑录制画面','取消录制']];check(len(controls)==4 and all(min(x['x2'],clock['x2'])<=max(x['x1'],clock['x1']) or min(x['y2'],clock['y2'])<=max(x['y1'],clock['y1']) for x in controls),kind+' timer does not overlap any of four controls');press('停止录制');end=time.monotonic()+40
  while time.monotonic()<end:
   v=values()
   if v.get('recording_state')=='idle' and v.get('record_result_kind')==('gif' if gif else 'video') and ((v.get('last_gif_path')!=saved.get('last_gif_path')) if gif else (v.get('last_record_uri')!=saved.get('last_record_uri'))):break
   time.sleep(.5)
  check(v.get('recording_state')=='idle' and v.get('record_result_kind')==('gif' if gif else 'video'),kind+' completes into result preview');receiver('qa_v126_close_page');main()
finally:
 if values().get('recording_state') in ['recording','paused','countdown']:
  adb('shell','am','startservice','-n','com.kongye.floatball/.ScreenRecordingService','-a','com.kongye.floatball.STOP_RECORDING');time.sleep(2)
 receiver('qa_v126_close_page');receiver('qa_v126_restore',saved);main()
print(count,'real recording checks passed; no phone media exported.',flush=True)
