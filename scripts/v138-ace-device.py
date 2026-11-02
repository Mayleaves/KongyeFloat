"""PP-OCRv5 benchmark and result UX using generated samples only. No phone media export."""
import os,runpy,time,json,pathlib,re
os.environ['KONGYE_ADB_SERIAL']='525114fb';q=runpy.run_path('scripts/v118-device.py');receiver,values,adb=[q[k] for k in ['receiver','values','adb']];count=0
saved={k:values().get(k) for k in ['ocr_keep_lines','ocr_chinese_punctuation']};pathlib.Path('.tools/v138-state.json').write_text(json.dumps(saved),encoding='utf-8')
def check(ok,label):
 global count
 assert ok,label
 count+=1;print('PASS:',label,flush=True)
def compact(s):return re.sub(r'\s+','',s)
def distance(a,b):
 prev=list(range(len(b)+1))
 for i,c in enumerate(a,1):
  cur=[i]
  for j,d in enumerate(b,1):cur.append(min(cur[-1]+1,prev[j]+1,prev[j-1]+(c!=d)))
  prev=cur
 return prev[-1]
def state():receiver('qa_v138_state');return json.loads(values().get('qa_v138_state','{}'))
def completed():
 end=time.monotonic()+40;first=None
 while time.monotonic()<end:
  s=state()
  if s:
   if first is None:first=s
   if not s['recognizing']:return first,s
  time.sleep(.2)
 raise AssertionError('Result timeout '+str(state()))
try:
 q['main']();time.sleep(.6);receiver('qa_v134_awake');receiver('qa_v126_restore',{'ocr_keep_lines':'false','ocr_chinese_punctuation':'true'});receiver('qa_v138_benchmark')
 end=time.monotonic()+100
 while time.monotonic()<end:
  v=values();raw=v.get('qa_v138_benchmark','')
  if raw.startswith('['):break
  if raw=='failed':raise AssertionError(v.get('qa_v138_error'))
  time.sleep(1)
 check(raw.startswith('['),'both engines completed all synthetic font samples without initialization failures');rows=json.loads(raw);pathlib.Path('.tools/v138-final-benchmark.json').write_text(json.dumps(rows,ensure_ascii=False,indent=2),encoding='utf-8')
 check(len(rows)==6,'sans WenKai and KaiTi evaluated at 28px and 40px')
 summary=[];mlTotal=ppTotal=combinedTotal=denominator=0
 for r in rows:
  expected=compact(r['expected']);n=len(expected);errors={k:distance(expected,compact(r[k])) for k in ['ml','paddle','combined']};mlTotal+=errors['ml'];ppTotal+=errors['paddle'];combinedTotal+=errors['combined'];denominator+=n
  check(errors['combined']<errors['ml'],r['font']+' '+str(r['size'])+'px combined character+punctuation errors lower than original ML Kit')
  check('吴玫正在阅读，今天心情很好。' in r['combined'],r['font']+' '+str(r['size'])+'px exact name and Chinese punctuation retained')
  check('中文标点：逗号，句号。问号？感叹号！' in r['combined'],r['font']+' '+str(r['size'])+'px Chinese comma colon question and exclamation correct')
  check('3.14' in r['combined'] and '12:30' in r['combined'] and 'example.com' in r['combined'],r['font']+' '+str(r['size'])+'px decimal clock and URL stay unmodified')
  summary.append({'font':r['font'],'size_px':r['size'],'characters':n,'errors':errors,'ml_ms':r['ml_ms'],'paddle_ms':r['paddle_ms']});print('BENCHMARK',json.dumps(summary[-1],ensure_ascii=False),flush=True)
 pathlib.Path('.tools/v138-summary.json').write_text(json.dumps({'samples':summary,'total_characters':denominator,'ml_errors':mlTotal,'paddle_errors':ppTotal,'combined_errors':combinedTotal},ensure_ascii=False,indent=2),encoding='utf-8')
 receiver('qa_v138_result');first,s=completed();print('GENERATED_RESULT',json.dumps(s,ensure_ascii=False),flush=True)
 check(s['status']=='识别完成','actual RegionResult runs enhanced engines to completion')
 check(s['raw'].count('原文,不要改!')==1,'exact accessible ASCII punctuation stays untouched and appears once')
 check('吴玫正在阅读，今天心情很好。' in s['raw'] and '霞鹜文楷：你好，世界！这是测试。' in s['raw'],'actual result window contains Paddle-corrected WenKai characters and punctuation')
 check('中文标点：逗号，句号。问号？感叹号！' in s['raw'] and '3.14' in s['raw'],'actual result includes full Chinese punctuation without changing decimal')
 check(first['window']==s['window'],'enhanced completion updates same result window')
 receiver('qa_v138_punctuation_fixture');receiver('qa_v138_toggle_punctuation');off=state();check('中文标点:逗号,问号?金额 3.14 元' in off['raw'],'punctuation can be disabled without re-running OCR')
 receiver('qa_v138_toggle_punctuation');on=state();check('中文标点：逗号，问号？金额 3.14 元' in on['raw'],'punctuation can be enabled on cached OCR text')
 check('原文,不要改!' in on['raw'],'punctuation toggle still leaves original source unchanged')
 receiver('qa_v138_close');receiver('qa_v138_result');time.sleep(.1);receiver('qa_v138_close');time.sleep(1.2);check(state()=={},'closing an in-flight enhanced job leaves no late result window')
 pid=adb('shell','pidof','com.kongye.floatball').strip().split()[0];check('FATAL EXCEPTION' not in adb('logcat','-d','--pid='+pid,'-t','150'),'current app process has no fatal exception')
finally:
 receiver('qa_v138_close');receiver('qa_v126_restore',saved);receiver('qa_v134_awake_off');q['main']()
print(count,'Paddle/combined/font/punctuation checks passed; original text options restored; no phone media export.',flush=True)
