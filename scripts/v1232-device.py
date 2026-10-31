"""Icon palette and shutdown visibility checks; owned metadata only."""
import runpy,os,time,json,pathlib
os.environ['KONGYE_ADB_SERIAL']='525114fb';q=runpy.run_path(str(pathlib.Path(__file__).with_name('v118-device.py')))
r=q['receiver'];v=q['values'];count=0
def check(ok,text):
 global count
 assert ok,text
 count+=1;print('PASS:',text,flush=True)
saved={k:v().get(k) for k in ['theme','size_percent','edge_percent','opacity','x','y','custom_art']}
try:
 for theme in ['light','dark']:
  q['prefs']({'theme':theme});q['main']()
  for page in ['首页','悬浮球','设置']:
   q['click'](page);r('qa_v1231_icons');d=v();check(int(d['qa_v1232_tints'])>0,theme+' '+page+' icons share home tool tint');check(d['qa_v1232_flower']=='true','flower navigation remains distinct from quick-settings gear')
 q['click']('首页')
 for _ in range(3):
  r('qa_v123_service_REFRESH');time.sleep(.8);r('qa_v123_service_STOP');time.sleep(1.1);r('qa_v123_snapshot');d=v();check(d['qa_v123_service']=='false','stop destroys service after fade');check(d.get('qa_stop_visibility')=='4' and float(d['qa_stop_view_alpha'])==0 and float(d['qa_stop_window_alpha'])==0,'view and surface are hidden before removal')
 r('qa_v123_service_REFRESH');time.sleep(.8);r('qa_v123_service_STOP');time.sleep(.1);r('qa_v123_service_REFRESH');time.sleep(1.1);r('qa_v123_snapshot');d=v();check(d['qa_v123_running']=='true' and d['qa_v123_service']=='true','quick restart cancels stale shutdown callback')
finally:q['prefs'](saved);q['main']();q['click']('首页');r('qa_v123_service_REFRESH')
print(str(count)+' checks passed; no phone media exported.',flush=True)
