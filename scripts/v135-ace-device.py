"""Generated translation text and own edge/window metadata; no phone media export."""
import os,runpy,time,json,pathlib,shlex
os.environ['KONGYE_ADB_SERIAL']='525114fb';q=runpy.run_path('scripts/v118-device.py');adb,receiver,values=[q[k] for k in ['adb','receiver','values']];count=0
saved={k:values().get(k) for k in ['theme','size','size_percent','edge_percent','edge_gap','opacity','x','y','custom_art','fade','snap','haptic','enhanced_overlay','home_tools','gesture_tap','gesture_double','gesture_long','gesture_up','gesture_down']};pathlib.Path('.tools/v135-ace-test-state.json').write_text(json.dumps(saved),encoding='utf-8');receiver('qa_v123_snapshot');running=values().get('qa_v123_running')=='true'
def check(ok,label):
 global count
 assert ok,label
 count+=1;print('PASS:',label,flush=True)
def send(cmd,**data):
 args=['shell','am','broadcast','-n','com.kongye.floatball/.OverlayCheckReceiver','--es','command','qa_v135_'+cmd]
 for key,value in data.items():args+=['--es',key,shlex.quote(value)]
 adb(*args);time.sleep(.08)
def state():receiver('qa_v135_state');return json.loads(values()['qa_v135_translation'])
def completed():
 end=time.monotonic()+18
 while time.monotonic()<end:
  result=state()
  if result['phase']=='complete':return result
  if result['phase']=='failed':raise AssertionError(result)
  time.sleep(.4)
 raise AssertionError(state())
try:
 q['main']();time.sleep(.8);receiver('qa_v134_awake')
 for text,source,target in [('sad','en','zh'),('Hello, world.','en','zh'),('吴玫','zh','en'),('こんにちは','ja','zh'),('안녕하세요','ko','zh')]:
  send('show',text=text);s=completed();check(s['detected']==source and s['target']==target,text+' automatically detects supported source and selects target');check(bool(s['output']) and s['output']!=text and s['status']=='翻译完成',text+' returns a real model translation');print('RESULT',json.dumps(s,ensure_ascii=False),flush=True)
 send('show',text='sad');s=completed();send('swap');swapped=completed();check(swapped['source']=='zh' and swapped['target']=='en' and swapped['input']==s['output'],'language swap uses detected source and moves translated text back to input');check(bool(swapped['output']),'reverse translation completes without another translate click')
 send('show',text='Hello, world.');send('choose',source='en',target='ja');send('choose',source='en',target='ko');s=completed();time.sleep(1);check(s['target']=='ko' and state()['output']==s['output'],'rapid target change ignores obsolete results')
 send('choose',source='en',target='en');s=completed();check(s['output']=='Hello, world.','same-language translation returns original text immediately')
 send('close');receiver('qa_v135_models');time.sleep(.2);v=values();check(all(v.get('qa_v135_model_'+x)=='true' for x in ['zh','ja','ko']),'Chinese Japanese and Korean models remain available after switching clients')
 q['prefs']({'size_percent':'100','edge_percent':'200','custom_art':'false','fade':'false','snap':'true'});q['main']();time.sleep(.8);q['click']('悬浮球');time.sleep(.6);nodes=q['nodes']();v=values();check(any(n['label']=='200%' for n in nodes),'edge control displays and retains 200 percent');check(abs(float(v['qa_preview_shift'])+int(v['qa_preview_width']))<=1,'200 percent preview leaves a full ball width inside the edge')
 receiver('qa_v123_service_REFRESH');time.sleep(.7);receiver('qa_v123_snapshot');v=values();check(v.get('edge_percent')=='200','running ball configuration retains the new upper bound')
 pid=adb('shell','pidof','com.kongye.floatball').strip().split()[0];check('FATAL EXCEPTION' not in adb('logcat','-d','--pid='+pid,'-t','150'),'no fatal exception in current app process')
finally:
 send('close');q['prefs'](saved)
 if not running:receiver('qa_v123_service_STOP');time.sleep(.7)
 receiver('qa_v134_awake_off');q['main']()
print(count,'translation/edge checks passed; original configuration restored; no phone media exported.',flush=True)
