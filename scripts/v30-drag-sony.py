"""Sony pull-response checks: own touch/window metadata, no media export."""
import os,runpy,json,time,re,pathlib
os.environ.setdefault('KONGYE_ADB_SERIAL','HQ629V0361')
q=runpy.run_path('scripts/v118-device.py');a,r,values=[q[k] for k in ['adb','receiver','values']]
checks=0;metrics={}
def check(ok,label):
 global checks
 assert ok,label
 checks+=1;print('PASS:',label,flush=True)
def state():r('qa_drag_state');return json.loads(values()['qa_drag_state'])
def frames():
 end=time.monotonic()+4
 while time.monotonic()<end:
  p=values()
  if 'qa_drag_frames' in p:return json.loads(p['qa_drag_frames'])
  time.sleep(.15)
 raise AssertionError('No touch trace')
def swipe(dx,dy,duration):
 s=state();x,y=s['x']+s['w']//2,s['y']+s['h']//2;r('qa_drag_monitor')
 a('shell','input','swipe',str(x),str(y),str(x+dx),str(y+dy),str(duration));return frames()

q['main']();time.sleep(.7);r('qa_v134_awake');r('qa_v123_service_REFRESH');time.sleep(.7)
p=values();keys=['x','y','snap','fade','haptic','custom_art','size_percent','edge_percent','gesture_up','gesture_down','gesture_long','gesture_tap']
saved={k:p.get(k) for k in keys};pathlib.Path('.tools/sony-pull-original.json').write_text(json.dumps(saved),encoding='utf-8')
try:
 width,height=map(int,re.findall(r'(\d+)x(\d+)',a('shell','wm','size'))[-1])
 base={'x':'0','y':str(height//3),'snap':'true','fade':'false','haptic':'false','custom_art':'false','size_percent':'100','edge_percent':'100','gesture_up':'none','gesture_down':'none','gesture_long':'quick','gesture_tap':'menu'}
 for side,dx,x in [('left',240,0),('right',-240,width)]:
  q['prefs'](dict(base,x=str(x)));time.sleep(.3);f=swipe(dx,25,600)
  dragging=[v for v in f if v.get('pressed') and v.get('dragging')]
  check(bool(dragging),side+' pull starts dragging')
  first=min(v['age'] for v in dragging);metrics[side+'_first_drag_ms']=first
  check(first<200,side+' follows before old 280 ms delay ('+str(first)+' ms)')
  check(all(abs(v['x']-(v['initialX']+round(v['pointerX']-v['downX'])))<=1 and abs(v['y']-(v['initialY']+round(v['pointerY']-v['downY'])))<=1 for v in dragging),side+' each drag update directly uses finger displacement without spring lag')
  end=state();check(end['scale']==1,side+' existing pull animation restores original size')
  check(values().get('drag_cross_state')=='red',side+' moving release never arms a screenshot')
 q['prefs'](base);time.sleep(.3)
 for label,dy in [('up',-120),('down',120)]:
  f=swipe(3,dy,120);check(not any(v.get('dragging') for v in f if v.get('pressed')),label+' quick gesture is not mistaken for dragging')
 q['prefs'](base);time.sleep(.3);s=state();x,y=s['x']+s['w']//2,s['y']+s['h']//2;r('qa_drag_monitor')
 a('shell','input','motionevent','DOWN',str(x),str(y));time.sleep(.85);a('shell','input','motionevent','UP',str(x),str(y));f=frames()
 check(any(v.get('holdTriggered') for v in f),'stationary long press still opens edge shortcuts')
 check(values().get('quick_visible')=='false','edge shortcuts close on release')
finally:
 a('shell','input','motionevent','UP','20','20');r('qa_drag_cancel');q['prefs'](saved);r('qa_v134_awake_off')
check(all(values().get(k)==v for k,v in saved.items()),'original Sony appearance, position and gestures restored')
pathlib.Path('.tools/sony-pull-results.json').write_text(json.dumps(metrics),encoding='utf-8')
print('TOTAL:',checks,'checks passed.',json.dumps(metrics),flush=True)
