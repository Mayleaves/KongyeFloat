import os,runpy,time,json,pathlib
os.environ['KONGYE_ADB_SERIAL']='525114fb'
q=runpy.run_path('scripts/v118-device.py');adb,receiver,values=[q[k] for k in ['adb','receiver','values']]
keys=['theme','size_percent','edge_percent','opacity','x','y','custom_art','fade','snap','haptic','enhanced_overlay','home_tools','gesture_tap','gesture_double','gesture_long','gesture_up','gesture_down']
saved={k:values().get(k) for k in keys};pathlib.Path('.tools/v1292-state.json').write_text(json.dumps(saved),encoding='utf-8');count=0

def check(ok,label):
 global count
 assert ok,label
 count+=1;print('PASS:',label,flush=True)
def layout():
 receiver('qa_v129_translation_layout');return json.loads(values()['qa_v129_translation_layout'])
def overlay():
 receiver('nodes');return json.loads(values()['qa_overlay_nodes'])
try:
 q['main']();time.sleep(.8)
 for theme in ['light','dark']:
  q['prefs']({'theme':theme});receiver('qa_v127_lines');receiver('qa_v126_translate');time.sleep(1);receiver('qa_v129_translation_long');time.sleep(.5);s=layout()
  for name in ['source','result']:
   f=s[name];check(f['multiline'] and f['max_lines']>1 and f['lines']>5,theme+' '+name+' wraps long generated text over multiple lines')
   check(f['widest']<=f['layout_width']+1 and f['layout_width']<=f['width'],theme+' '+name+' lines stay inside their field width')
   check(f['height']>=f['line_height']*2 and f['lines']*f['line_height']>f['height'],theme+' '+name+' has visible lines and scrollable overflow')
  check(s['source']['fill']!=s['result']['fill'],theme+' source and translation cards have different fills')
  ns=overlay();check(all(any(n['label']==label for n in ns) for label in ['原文','译文','翻译原文','翻译结果']),theme+' separate section titles and accessible fields remain visible')
  field=next(n for n in ns if n['label']=='翻译结果');x=(field['x1']+field['x2'])//2
  adb('shell','input','swipe',str(x),str(field['y2']-8),str(x),str(field['y1']+8),'400');time.sleep(.2)
  check(layout()['result']['scroll_y']>0,theme+' result scrolls vertically with a real swipe')
  source=next(n for n in ns if n['label']=='翻译原文');sx=(source['x1']+source['x2'])//2
  adb('shell','input','swipe',str(sx),str(source['y2']-8),str(sx),str(source['y1']+8),'400');time.sleep(.2)
  check(layout()['source']['scroll_y']>0,theme+' source also scrolls vertically with a real swipe')
  button=next(n for n in ns if n['label']=='复制' and n['clickable']);q['tap']((button['x1']+button['x2'])/2,(button['y1']+button['y2'])/2)
  copied=values();check(int(copied['last_selection_copy_length'])==len(s['result']['text']) and copied['qa_feedback_message']=='文字已复制',theme+' copy includes the entire long result and confirms success')
  receiver('qa_v122_close')
 check(count==22,'both theme layout passes completed')
finally:
 receiver('qa_v122_close');q['prefs'](saved);q['main']()
print(count,'checks passed; generated text only, no screenshots or phone media exported.',flush=True)
