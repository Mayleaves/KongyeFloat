"""v1.23.1 own geometry, local SVG animation and permission UI acceptance."""
import runpy,pathlib,time,json,os
os.environ['KONGYE_ADB_SERIAL']='525114fb'
q=runpy.run_path(str(pathlib.Path(__file__).with_name('v118-device.py')))
adb,values,prefs,main,nodes,click,receiver=[q[k] for k in ['adb','values','prefs','main','nodes','click','receiver']]
count=0
def check(ok,desc):
 global count
 assert ok,desc
 count+=1;print('PASS:',desc,flush=True)
def snap():receiver('qa_v1231_snapshot');return values()
saved={k:values().get(k) for k in ['theme','size_percent','edge_percent','opacity','x','y','custom_art']};pathlib.Path('.tools/v1231-state.json').write_text(json.dumps(saved),encoding='utf-8')
try:
 prefs({'theme':'light'});main();click('首页');receiver('qa_v1231_native');v=values();check(v.get('qa_v1231_native')=='123 checks passed','123 generated checks: odd/even banner sizes, six scenes, three permission states and click routing')
 n=nodes();strip=next(n for n in n if n['label']=='权限智能紧凑胶囊条');check(strip['height']<500,'compact permission strip replaces tall two-line controls')
 perm=[n for n in n if any(n['label'].startswith(x+'，') for x in ['悬浮窗','无障碍','通知'])];check(len(perm)==3 and len(set(x['y1'] for x in perm))==1,'three permission chips share one row')
 a=snap();time.sleep(.2);b=snap();check(b.get('qa_v1231_scene')=='true','new HTML scene loads without fallback');check(b.get('qa_v1231_motion')=='true' and a['qa_v1231_clock']!=b['qa_v1231_clock'],'time-specific SVG choreography is animated')
 receiver('qa_v1231_jump');v=snap();check(0<float(v['qa_v1231_jump'])<1,'tap starts squash/stretch jump');time.sleep(.8);check(float(snap()['qa_v1231_jump'])==1,'jump returns to its resting transform')
 for percent in [100,250]:
  prefs({'size_percent':percent,'edge_percent':100});main();click('首页');receiver('qa_v123_service_RECOVER');time.sleep(.25);v=snap();check(v.get('qa_v1231_pulse')=='true','recover pulse is visible at size '+str(percent));check(abs(float(v['qa_v1231_dx']))<2 and abs(float(v['qa_v1231_dy']))<2,'pulse and ball are concentric in actual screen coordinates at '+str(percent));check(v.get('qa_v1231_passthrough')=='true','pulse cannot intercept screen touches');time.sleep(1.6);check(snap()['qa_v1231_pulse']=='false','pulse window is removed')
 click('设置');n=nodes();check(any(n['label']=='增强悬浮层' for n in n),'settings navigation preserved');click('首页');check(snap()['qa_v1231_motion']=='true','plant motion resumes after page navigation')
 prefs({'theme':'dark'});main();click('首页');check(any(n['label']=='权限智能紧凑胶囊条' for n in nodes()),'compact strip remains available in dark mode')
finally:
 prefs(saved);main();click('首页');receiver('qa_v123_service_REFRESH');time.sleep(.8)
print(str(count)+' real-device checks plus 123 generated assertions passed; no phone media exported.',flush=True)
