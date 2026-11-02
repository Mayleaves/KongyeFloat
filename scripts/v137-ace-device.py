"""Mixed native/image region capture. Generated text only; never export phone media."""
import os,runpy,time,json,pathlib,argparse,re
parser=argparse.ArgumentParser();parser.add_argument('--restore-state');args=parser.parse_args()
os.environ['KONGYE_ADB_SERIAL']='525114fb';q=runpy.run_path('scripts/v118-device.py');adb,receiver,values=[q[k] for k in ['adb','receiver','values']];count=0
receiver('qa_v123_snapshot');v=values();saved={'running':v.get('qa_v123_running'),'ocr_keep_lines':v.get('ocr_keep_lines')}
if args.restore_state:saved=json.loads(pathlib.Path(args.restore_state).read_text(encoding='utf-8'))
pathlib.Path('.tools/v137-test-state.json').write_text(json.dumps(saved),encoding='utf-8')
def check(ok,label):
 global count
 assert ok,label
 count+=1;print('PASS:',label,flush=True)
def state():receiver('qa_v137_state');return json.loads(values().get('qa_v137_state','{}'))
def complete():
 end=time.monotonic()+25;first=None
 while time.monotonic()<end:
  s=state()
  if s:
   if first is None:first=s
   if not s['recognizing']:return first,s
  time.sleep(.15)
 raise AssertionError('OCR did not complete: '+str(state()))
def compact(s):return re.sub(r'\s+','',s)
def fixture(command='fixture'):
 receiver('qa_v137_close');q['main']();time.sleep(.5);receiver('qa_v137_'+command);time.sleep(.9);receiver('qa_v137_capture');return complete()
try:
 q['main']();time.sleep(.8);receiver('qa_v134_awake');receiver('qa_v123_service_STOP');time.sleep(.6);receiver('qa_v126_restore',{'ocr_keep_lines':'false'})
 first,s=fixture();direct=values().get('qa_v137_direct','');print('GENERATED',json.dumps({'first':first,'final':s,'direct':direct},ensure_ascii=False),flush=True)
 check(s['original_count']==5,'actual accessibility source preserves five native nodes')
 check('图片独有文字' not in direct and 'Image 246' not in direct,'image text has no accessibility source to fall back to')
 check('选区外' not in direct and 'not-read-fixture' not in direct,'outside and password nodes are excluded from source extraction')
 for text in ['原文标题 2026','原文 ABC','金额 ￥1,234.50']:
  check(compact(s['raw']).count(compact(text))==1,'exact native text is preserved once: '+text)
 check('图片独有文字789' in compact(s['raw']) and 'Image246' in compact(s['raw']),'real OCR adds image text even though native source is nonempty')
 check(s['raw'].count('重复标签')==3,'two native repeats and one image repeat in separate locations are all retained')
 check(s['status']=='识别完成' and '\n' not in s['value'],'merged result keeps completion wording and default newline removal')
 check(first['window']==s['window'] and first['card']==s['card'],'OCR completion updates existing views instead of replacing the result window')
 receiver('qa_v137_keep_lines');kept=state();check(kept['value']==kept['raw'] and '\n' in kept['value'],'keep-newlines control works on combined text')
 receiver('qa_v137_fallback');fallback=state();check('已提取原文' in fallback['status'] and compact(fallback['raw'])==compact(direct),'simulated OCR failure branch retains native source instead of clearing it')
 receiver('qa_v137_retry');_,retry=complete();check('图片独有文字789' in compact(retry['raw']) and retry['raw'].count('重复标签')==3,'real re-recognition restores all supplements without accumulating duplicates')
 check(retry['window']==s['window'] and retry['card']==s['card'],'re-recognition reuses the existing result window')
 receiver('qa_v137_translate');end=time.monotonic()+20;translated={}
 while time.monotonic()<end:
  receiver('qa_v137_translation_state');translated=json.loads(values().get('qa_v137_translation','{}'))
  if translated.get('status')=='翻译完成':break
  time.sleep(.4)
 check(translated.get('input')==retry['value'],'translation receives complete combined source including image text')
 check(translated.get('status')=='翻译完成' and bool(translated.get('output')),'combined result completes real on-device translation')
 receiver('qa_v126_restore',{'ocr_keep_lines':'false'});_,image=fixture('image_only');check(image['original_count']==0 and values().get('qa_v137_direct')=='','image-only fixture has zero extracted nodes')
 check('图片独有文字789' in compact(image['raw']) and image['raw'].count('重复标签')==1,'image-only fallback still returns complete OCR text')
 receiver('qa_v137_close');q['main']();time.sleep(.4);receiver('qa_v137_fixture');time.sleep(.8);receiver('qa_v137_capture');time.sleep(.55);receiver('qa_v137_close');time.sleep(1.4);check(state()=={},'closing during capture/recognition leaves no late result window')
 pid=adb('shell','pidof','com.kongye.floatball').strip().split()[0];check('FATAL EXCEPTION' not in adb('logcat','-d','--pid='+pid,'-t','150'),'current process has no fatal exception')
finally:
 receiver('qa_v137_close');receiver('qa_v126_restore',{'ocr_keep_lines':saved['ocr_keep_lines']})
 if saved['running']=='true':receiver('qa_v123_service_REFRESH')
 else:receiver('qa_v123_service_STOP')
 receiver('qa_v134_awake_off');q['main']()
print(count,'region fusion checks passed; original settings restored; no screenshots/videos/GIFs exported.',flush=True)
