"""ABI update selection and isolated cache cleanup; synthetic OCR, no phone media export."""
import os,runpy,time,json,pathlib
os.environ['KONGYE_ADB_SERIAL']='525114fb'
q=runpy.run_path('scripts/v118-device.py');adb,receiver,values=[q[k] for k in ['adb','receiver','values']];count=0
fixture=pathlib.Path('.tools/v139-update-fixtures-v2');names=json.loads((fixture/'names.json').read_text());remote='/data/local/tmp/kongye-v139-fixtures';owned='files/qa-v139-update-fixtures'
def check(ok,label):
 global count
 assert ok,label
 count+=1;print('PASS:',label,flush=True)
saved={k:values().get(k) for k in ['size_percent','edge_percent','opacity','custom_art','theme','ocr_keep_lines','ocr_chinese_punctuation']}
pathlib.Path('.tools/v139-release-state.json').write_text(json.dumps(saved),encoding='utf-8')
try:
 q['main']();receiver('qa_v134_awake')
 receiver('qa_v139_assets');r=json.loads(values()['qa_v139_assets'])
 for key,suffix in [('arm64','arm64-v8a.apk'),('arm32','armeabi-v7a.apk'),('x86','x86.apk'),('unknown','universal.apk'),('invalidPreferred','universal.apk')]:check(r[key].endswith('-'+suffix),'update asset choice '+key)
 check(r['newVersion']>0,'architecture selection preserves version comparison')
 adb('push',str(fixture),remote);adb('shell','run-as','com.kongye.floatball','mkdir','-p',owned)
 for name in names.values():adb('shell','run-as','com.kongye.floatball','cp',remote+'/'+name,owned+'/'+name)
 receiver('qa_v139_cache');r=json.loads(values()['qa_v139_cache']);remaining=set(r['remaining'])
 check(r['removed']==2,'only already-installed and older update packages removed')
 for key in ['future','unlabelled','partial','foreign','invalid']:check(names[key] in remaining,'cache cleanup retains '+key+' fixture')
 for key in ['older','current']:check(names[key] not in remaining,'cache cleanup removes '+key+' fixture')
 # Exercise the unchanged production OCR path with the retained native libraries.
 receiver('qa_v138_result');deadline=time.monotonic()+45
 while time.monotonic()<deadline:
  receiver('qa_v138_state');state=json.loads(values().get('qa_v138_state','{}'))
  if state and not state['recognizing']:break
  time.sleep(.3)
 check(state.get('status')=='识别完成','both-engine production result completes')
 check('吴玫正在阅读，今天心情很好。' in state.get('raw',''),'WenKai name and punctuation retained')
 check('3.14' in state.get('raw','') and 'example.com' in state['raw'],'decimal and URL retained')
 check(state['raw'].count('原文,不要改!')==1,'accessible original text remains exact and unique')
 after=values();check(all(after.get(k)==v for k,v in saved.items()),'personal settings remain unchanged')
 pid=adb('shell','pidof','com.kongye.floatball').strip().split()[0];check('FATAL EXCEPTION' not in adb('logcat','-d','--pid='+pid,'-t','100'),'no fatal exception in tested process')
finally:
 receiver('qa_v138_close');receiver('qa_v134_awake_off')
 for name in names.values():
  adb('shell','run-as','com.kongye.floatball','rm','-f',owned+'/'+name)
  adb('shell','rm','-f',remote+'/'+name)
 adb('shell','rm','-f',remote+'/names.json');adb('shell','rmdir',remote);adb('shell','run-as','com.kongye.floatball','rmdir',owned)
print(count,'storage/ABI/OCR checks passed; isolated fixture archives removed; no phone media export.',flush=True)
