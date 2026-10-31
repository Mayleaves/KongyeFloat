"""v1.24.0 approved layout and transitions, with owned UI data only."""
import runpy,pathlib,os,time,json
os.environ['KONGYE_ADB_SERIAL']='525114fb';q=runpy.run_path(str(pathlib.Path(__file__).with_name('v118-device.py')))
adb,values,prefs,main,nodes,click,receiver=[q[k] for k in ['adb','values','prefs','main','nodes','click','receiver']]
count=0
def check(ok,msg):
 global count
 assert ok,msg
 count+=1;print('PASS:',msg,flush=True)
def snapshot():receiver('qa_v124_snapshot');return values()
def switch(tab):click(tab);time.sleep(.8)
saved={k:values().get(k) for k in ['theme','size_percent','edge_percent','opacity','x','y','custom_art','fade','snap','haptic','enhanced_overlay','home_tools','gesture_tap','gesture_double','gesture_long','gesture_up','gesture_down']};pathlib.Path('.tools/v124-state.json').write_text(json.dumps(saved),encoding='utf-8')
try:
 prefs({'theme':'light'});main();switch('首页');receiver('qa_v124_native');check(values().get('qa_v124_native')=='8 checks passed','white-thumb switch pixels, crescent geometry, exact tool/action order and picker index')
 n=q['node']('悬浮球');adb('shell','input','tap',str((n['x1']+n['x2'])//2),str((n['y1']+n['y2'])//2));a=snapshot();check(0<float(a['qa_v124_page_alpha'])<1,'page content fades in during tab navigation');check(0<float(a['qa_v124_nav'])<1,'shared navigation capsule moves between tabs');time.sleep(.8);b=snapshot();check(float(b['qa_v124_page_alpha'])==1 and float(b['qa_v124_nav'])==1,'entry animation settles to correct tab');check(int(b['qa_v124_seek_count'])==3,'all three appearance controls use HTML rail style');check(b['qa_v124_tap_height']==b['qa_v124_reset_height'],'reset gesture row has same height as single tap')
 n=nodes();check(any(x['label']=='自定义图片 / GIF' for x in n),'custom image/GIF button retained');bar=max((x for x in n if x['label']=='大小'),key=lambda x:x['width']);q['tap']((bar['x1']+bar['x2'])/2,(bar['y1']+bar['y2'])/2);check(270<int(values()['size_percent'])<330,'new rail still changes real size preference');check(any(x['label'].endswith('%') and x['text_color']==-14462147 for x in nodes() if 'text_color' in x),'range percentage uses theme green')
 q['find']('触感反馈');n=nodes();check(not any('操作时提供轻微振动' in x['label'] for x in n),'haptic explanatory subtitle removed');q['find']('恢复默认手势');check(any(x['label']=='恢复默认手势' for x in nodes()),'reset action remains inside gesture group')
 before=values().get('haptic','true');click('触感反馈');check(values().get('haptic')!=before,'new white-thumb switch updates real haptic setting')
 switch('设置');n=nodes();labels=[x['label'] for x in n];check('应用内悬浮球' in labels,'app behavior entry renamed');check(any(i['name']=='app_ball' for x in n if x['label']=='应用内悬浮球' for i in x['icons']),'app behavior entry uses supplied SVG symbol');check(not any(any(t in x['label'] for t in ['无障碍开启时，在更多界面显示','为不同应用设置显示方式','调整空叶软件界面的日夜配色方案','暂时显示小叶并移回屏幕内','恢复悬浮球的默认停靠位置']) for x in n),'crossed-out setting descriptions removed');prefs({'theme':'system'});main();switch('设置');check(any(i['name']=='theme_system' for x in nodes() if x['label']=='深色模式' for i in x['icons']),'system theme retains combined sun/moon icon')
 n=q['node']('首页');adb('shell','input','tap',str((n['x1']+n['x2'])//2),str((n['y1']+n['y2'])//2));a=snapshot();check(float(a['qa_v124_page_x'])<0,'backward tab entry comes from the opposite direction');time.sleep(.8)
 prefs({'theme':'dark'});main();switch('悬浮球');check(int(snapshot()['qa_v124_seek_count'])==3,'custom rails remain present in dark mode');switch('设置');check(any(i['name']=='theme' for x in nodes() if x['label']=='深色模式' for i in x['icons']),'dark theme uses the requested outlined crescent')
 check(values().get('home_tools')==saved['home_tools'],'existing custom home order preserved');check(all(values().get(k)==saved[k] for k in ['gesture_tap','gesture_double','gesture_long','gesture_up','gesture_down']),'existing gesture bindings preserved')
finally:prefs(saved);main();switch('首页');receiver('qa_v123_service_REFRESH')
print(str(count)+' checks passed; no phone media exported.',flush=True)
