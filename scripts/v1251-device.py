"""Focused v1.25.1 UI/animation acceptance; own app metadata and generated pixels only."""
import os,runpy,time,json,pathlib
os.environ['KONGYE_ADB_SERIAL']='525114fb'
q=runpy.run_path(str(pathlib.Path(__file__).with_name('v118-device.py')))
adb,receiver,values,prefs,main,click=[q[k] for k in ['adb','receiver','values','prefs','main','click']]
saved={k:values().get(k) for k in ['theme','size_percent','edge_percent','opacity','x','y','custom_art','fade','snap','haptic','enhanced_overlay','home_tools','gesture_tap','gesture_double','gesture_long','gesture_up','gesture_down']}
pathlib.Path('.tools/v1251-state.json').write_text(json.dumps(saved),encoding='utf-8')
count=0
def check(ok,msg):
 global count
 assert ok,msg
 count+=1;print('PASS:',msg,flush=True)
def snap():receiver('qa_v1251_snapshot');return values()
try:
 prefs({'theme':'light'});main();time.sleep(.5);receiver('qa_v1251_native');check(values().get('qa_v1251_native')=='3 checks passed','generated icon pixels: hollow moon, three-knob quick settings and two-knob quick menu')
 for _ in range(3):receiver('qa_v1231_jump')
 a=snap();check(float(a['qa_v1251_bubble_alpha'])==0 and int(a['qa_v1251_bubble_due'])>0,'plant taps do not show or reset the greeting bubble')
 end=time.monotonic()+26;shown=None
 while time.monotonic()<end:
  a=snap()
  if float(a['qa_v1251_bubble_alpha'])>.1:shown=a;break
  time.sleep(.25)
 check(shown is not None,'greeting appears automatically after a random delay without another tap')
 check(shown['qa_v1251_bubble_scheduled']=='true' and 30000<int(shown['qa_v1251_bubble_due'])<=90000,'following greeting is scheduled with a new random 35–90 second interval')
 time.sleep(2.5);check(float(snap()['qa_v1251_bubble_alpha'])==0,'random greeting fades away after its short display')
 click('悬浮球');time.sleep(.8);a=snap();check(int(a['qa_v1251_snap_top'])>int(a['qa_v1251_snap_bottom']) and a['qa_v1251_snap_box_bottom']=='0' and int(a['qa_v1251_card_bottom'])<int(a['qa_v1251_snap_top']),'snap row has more space above and a compact lower edge')
 receiver('qa_v125_layout');order=values()['qa_v125_quick_order'].split('|');check(len(order)==21 and '停止录屏' not in order and order[14:17]==['录制 GIF','最近软件','常用软件'],'quick function list removes stop recording and preserves requested order')
 adb('shell','am','start','-W','-n','com.kongye.floatball/.DiagnosticActivity','--es','feature','qa_v1221_about');time.sleep(1.2);receiver('qa_v125_about');time.sleep(.15);a=json.loads(json.loads(values()['qa_v125_about']));check(a['info']==0 and a['stages']==6 and '1.25.1' in a['version'],'about removes upper-right privacy entry while retaining supplied specimen page and actual version')
 receiver('qa_v125_about_back');time.sleep(.3);check('AboutActivity' not in adb('shell','dumpsys','activity','activities').split('topResumedActivity')[-1].splitlines()[0],'about HTML back control still returns')
 check(values().get('home_tools')==saved['home_tools'] and all(values().get(k)==saved[k] for k in ['gesture_tap','gesture_double','gesture_long','gesture_up','gesture_down']),'existing tool order and gesture bindings preserved')
finally:prefs(saved);main();click('首页');time.sleep(.8);receiver('qa_v123_service_REFRESH')
print(str(count)+' checks passed; no phone media exported.',flush=True)
