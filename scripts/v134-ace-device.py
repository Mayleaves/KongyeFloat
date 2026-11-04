"""Ace2: exit tail, cleared surface, original-window masking and timing; no media export."""
import os,runpy,time,json,pathlib,re
os.environ['KONGYE_ADB_SERIAL']='525114fb';q=runpy.run_path('scripts/v118-device.py');adb,receiver,values=[q[k] for k in ['adb','receiver','values']];count=0
fields=['theme','size','size_percent','edge_percent','edge_gap','opacity','x','y','custom_art','fade','snap','haptic','enhanced_overlay','home_tools','gesture_tap','gesture_double','gesture_long','gesture_up','gesture_down'];saved={k:values().get(k) for k in fields};pathlib.Path('.tools/v134-ace-test-state.json').write_text(json.dumps(saved),encoding='utf-8');receiver('qa_v123_snapshot');original_running=values().get('qa_v123_running')=='true'
home=adb('shell','cmd','package','resolve-activity','--brief','-a','android.intent.action.MAIN','-c','android.intent.category.HOME').splitlines()[-1].split('/')[0]
def check(ok,label):
 global count
 assert ok,label
 count+=1;print('PASS:',label,flush=True)
def unlocked():
 state=adb('shell','dumpsys','window','policy');return bool(re.search(r'^\s*showing=false',state,re.M))
def state():
 receiver('qa_v133_state');return json.loads(values()['qa_v133_state'])
def layer():
 receiver('qa_v133_layer');return json.loads(values()['qa_v133_layer'])
def foreground():
 receiver('qa_v129_snapshot');return values().get('qa_v129_visible','')
def wait_page(name):
 end=time.monotonic()+5
 while time.monotonic()<end:
  if foreground()==name:return
  time.sleep(.1)
 raise AssertionError('Own page not foreground: '+name)
def home_active():
 text=adb('shell','dumpsys','activity','activities',home);return any('topResumedActivity=' in line and home+'/' in line for line in text.splitlines())
def exit_check(label):
 identity=state()['identity'];receiver('qa_v134_capture');time.sleep(.22);active=layer()
 check(active.get('exists') and active.get('attached') and active.get('handoff'),label+' app task is handed off while its visual exit layer is attached')
 check(not active['source_focus'] and home_active(),label+' real launcher is resumed behind the exit before completion')
 check(active['format']==-3 and active['flags']&24==24,label+' exit surface has alpha and never steals focus or touches')
 time.sleep(1.2);frames=json.loads(values()['qa_v134_frames']);moving=[x for x in frames if x.get('image_y',0)<0 and not x.get('finishing')]
 check(len(moving)>=15 and all(x['source_alpha']==0 and x['source_visibility']!=0 for x in moving),label+' original Activity remains hidden through the entire upward exit')
 check(all(x.get('host_y',0)==0 for x in frames),label+' compositor window stays stationary while only its image child moves')
 check(len({x['host'] for x in frames if 'host' in x})==1,label+' one surface is retained without intermediate window replacement')
 check(all(b['image_y']<=a['image_y']+.01 for a,b in zip(moving,moving[1:])),label+' image advances upward without jumps back to old content')
 cleared=[x['uncovered_alpha'] for x in frames if 'uncovered_alpha' in x];check(bool(cleared) and all(x==0 for x in cleared),label+' exposed surface pixels are fully cleared')
 final=[x['final_alpha'] for x in frames if 'final_alpha' in x];check(bool(final) and all(x==0 for x in final),label+' last rendered frame is transparent before window removal')
 check(int(values()['qa_exit_empty_time'])>=int(values()['qa_v134_start']),label+' final empty buffer commit is from this exit, not a cached receipt')
 check(all(x['source_alpha']==0 and x['source_visibility']!=0 for x in frames if not x['layer']),label+' original window stays hidden after overlay cleanup')
 import statistics
 gaps=[(b['frame_ns']-a['frame_ns'])/1e6 for a,b in zip(moving,moving[1:])];timing={'case':label,'frames':len(moving),'median_ms':round(statistics.median(gaps),2),'p95_ms':round(sorted(gaps)[int(len(gaps)*.95)],2),'max_ms':round(max(gaps),2)};print('TIMING',json.dumps(timing),flush=True)
 check(not frames[-1]['layer'] and home_active(),label+' exit removes its temporary window and leaves live desktop')
 receiver('qa_v133_enter');wait_page(label.split()[0]);time.sleep(.75);restored=state()
 check(restored['identity']==identity and abs(restored['y'])<1 and restored['window_focus'] and restored['window_alpha']==1 and restored['decor_visibility']==0,label+' next gesture returns to same page with aligned content')
 return restored
try:
 check(unlocked(),'Ace2 unlocked for gesture acceptance');receiver('qa_v134_awake');q['main']();wait_page('MainActivity');time.sleep(.7);receiver('qa_v123_service_REFRESH');time.sleep(.8)
 q['prefs']({'size_percent':'100','edge_percent':'100','custom_art':'false','fade':'false','gesture_up':'settings'});q['main']();wait_page('MainActivity')
 for theme in ['light','dark']:
  q['prefs']({'theme':theme});q['main']();wait_page('MainActivity');adb('shell','input','keyevent','3');time.sleep(.4);receiver('qa_v133_enter');wait_page('MainActivity');time.sleep(.8);s=state();check(s['theme_translucent'] and s['format']==-3,theme+' actual runtime window keeps translucent format');exit_check('MainActivity '+theme)
 q['click']('设置');time.sleep(.8);q['find']('关于空叶');q['click']('关于空叶');wait_page('AboutActivity');time.sleep(1.2);exit_check('AboutActivity dark');adb('shell','input','keyevent','4');wait_page('MainActivity')
 # Cancel an exit after task handoff and restore immediately.
 receiver('qa_v133_exit');time.sleep(.2);check(layer().get('exists'),'rapid reopen starts while exit layer exists');receiver('qa_v133_enter');wait_page('MainActivity');time.sleep(.8);check(not layer()['exists'] and state()['window_focus'],'rapid reopen cancels overlay with no orphan window')
 # Real touch gesture on our floating ball, rather than calling AppMotion directly.
 receiver('qa_v123_snapshot');v=values();receiver('qa_v133_exit');time.sleep(1.3)
 def swipe_ball():
  receiver('qa_v123_snapshot');v=values();density=float(adb('shell','wm','density').split(':')[-1].strip())/160;side=round(36*density);x=int(v['qa_v123_ball_x'])+side//2;y=int(v['qa_v123_ball_y'])+side//2;adb('shell','input','swipe',str(x),str(y),str(x),str(y-round(85*density)),'120')
 swipe_ball();wait_page('MainActivity');time.sleep(.8);check(state()['window_focus'],'real upward ball swipe opens our app')
 receiver('qa_v134_arm');swipe_ball();time.sleep(1.2)
 receipt=values()
 frames=json.loads(receipt['qa_v134_frames'])
 started=next(frame for frame in frames if frame['layer'])
 first=next(frame for frame in frames if frame.get('image_y',0)<0 and not frame.get('finishing'))
 released=int(receipt['qa_touch_time'])-int(receipt['qa_v134_start'])
 preparation=started['ms']-released
 latency=first['ms']-released
 print(f'GESTURE_RELEASE_TO_PREPARATION_MS {preparation}, RELEASE_TO_MOTION_MS {latency}',flush=True)
 check(0<=preparation<160 and first['handoff'],'real upward exit begins its window preparation during the ball return')
 check(home_active() and not layer()['exists'],'real gesture ends on live desktop with no remaining transition window')
 pid=adb('shell','pidof','com.kongye.floatball').strip().split()[0];check('FATAL EXCEPTION' not in adb('logcat','-d','--pid='+pid,'-t','100'),'Ace2 current process has no fatal exception')
finally:
 receiver('qa_v133_enter');time.sleep(.8);q['prefs'](saved)
 if not original_running:receiver('qa_v123_service_STOP');time.sleep(.9)
 receiver('qa_v134_awake_off')
 if unlocked():q['main']()
print(count,'checks passed on Ace2; original configuration/running state restored; no phone media exported.',flush=True)
