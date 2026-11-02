"""Real held-pointer orbit centering using only owned view coordinates; no media export."""
import os,runpy,time,json,pathlib,re
os.environ['KONGYE_ADB_SERIAL']='525114fb';q=runpy.run_path('scripts/v118-device.py');adb,receiver,values=[q[k] for k in ['adb','receiver','values']];count=0
keys=['size','size_percent','edge_percent','edge_gap','x','y','snap','fade','custom_art','gesture_long']
saved={k:values().get(k) for k in keys};pathlib.Path('.tools/v140-settings.json').write_text(json.dumps(saved),encoding='utf-8')
def check(ok,label):
 global count
 assert ok,label
 count+=1;print('PASS:',label,flush=True)
def state(manage=False):receiver('qa_v140_manage_state' if manage else 'qa_v140_state');return json.loads(values()['qa_v140_state'])
def pointer(kind,x,y):adb('shell','input','motionevent',kind,str(round(x)),str(round(y)))
def release():pointer('MOVE',w/2,h-80);pointer('UP',w/2,h-80);time.sleep(.2)
def centered(s,label):
 p=s['slots'];ball=s['ball'];factor=(p[11]['cy']-p[0]['cy'])/286;direction=1 if ball['cx']<w/2 else -1
 inferredY=(144*p[0]['cy']+142*p[11]['cy'])/286
 inferredX=p[6]['cx']-direction*97*factor
 check(abs(inferredX-ball['cx'])<1.6,label+' horizontal origin equals ball center')
 check(abs(inferredY-ball['cy'])<1.6,label+' vertical origin equals ball center')
 check(len(p)==13,label+' keeps 12 editable slots and one recent slot')
 check(all(b['x']>=-1 and b['y']>=-1 and b['x']+b['width']<=w+1 and b['y']+b['height']<=h+1 for b in p),label+' complete circles fit screen')
 return [(b['cx'],b['cy'],b['width']) for b in p]
q['main']();receiver('qa_v134_awake');receiver('qa_v123_service_REFRESH');time.sleep(.4)
sizes=re.findall(r'(\d+)x(\d+)',adb('shell','wm','size'));w,h=map(int,sizes[-1]);receipts=[]
try:
 cases=[('right-middle',100,w-110,h*.4),('right-top',100,w-110,100),('left-bottom',100,0,h-160),('inward',200,w-500,h*.4),('small',30,0,h*.4),('large',500,w-510,h*.5)]
 for label,size,x,y in cases:
  q['prefs']({'custom_art':'false','size_percent':str(size),'edge_percent':'100','x':str(int(x)),'y':str(int(y)),'snap':'false','fade':'false','gesture_long':'quick'});time.sleep(.25)
  s=state();ball=s['ball'];pointer('DOWN',ball['cx'],ball['cy']);time.sleep(.85);s=state();check('slots' in s,label+' real long press opens orbit');centered(s,label);receipts.append({'case':label,'state':s})
  target=s['slots'][5];pointer('MOVE',target['cx'],target['cy']);time.sleep(.12);check(bool(values().get('last_quick_hover')),label+' sliding to a circle selects its target')
  release();check(values().get('quick_visible')=='false',label+' release over empty area dismisses orbit')
 # Same ball position in held UI and the management activity.
 q['prefs']({'size_percent':'100','x':str(w-110),'y':'100'});time.sleep(.2);ball=state()['ball'];pointer('DOWN',ball['cx'],ball['cy']);time.sleep(.85);held=state();heldPoints=centered(held,'held-before-management');release()
 receiver('qa_v140_manage');time.sleep(.5);managed=state(True);managedPoints=centered(managed,'management')
 check(all(abs(a[0]-b[0])<=1 and abs(a[1]-b[1])<=1 and a[2]==b[2] for a,b in zip(heldPoints,managedPoints)),'held and management use the same visible coordinates')
 adb('shell','input','keyevent','4');q['main']();pid=adb('shell','pidof','com.kongye.floatball').strip().split()[0];check('FATAL EXCEPTION' not in adb('logcat','-d','--pid='+pid,'-t','100'),'no fatal exception')
 pathlib.Path('.tools/v140-orbit-coordinates.json').write_text(json.dumps(receipts,indent=2),encoding='utf-8')
finally:
 receiver('qa_v126_close_page');q['prefs'](saved);receiver('qa_v134_awake_off');q['main']()
print(count,'orbit/device checks passed; personal settings restored; no screen export.',flush=True)
