"""Approved motion integration on Ace; only owned UI/scalars and generated pixels."""
import runpy,pathlib,time,json,os
os.environ['KONGYE_ADB_SERIAL']='525114fb'
q=runpy.run_path(str(pathlib.Path(__file__).with_name('v118-device.py')))
adb,values,prefs,main,nodes,click,receiver,tap=[q[k] for k in ['adb','values','prefs','main','nodes','click','receiver','tap']]
checks=0
def check(ok,label):
 global checks
 assert ok,label
 checks+=1;print('PASS:',label,flush=True)
def motion():receiver('qa_v123_snapshot');return values()
def ui():receiver('qa_v1221_ui');return json.loads(values()['qa_v1221_ui'])
def uc(label):
 n=next(n for n in ui() if n['label']==label);tap((n['x1']+n['x2'])/2,(n['y1']+n['y2'])/2)
def start(cmd):receiver('qa_v123_service_'+cmd.split('.')[-1])
original=values();saved={k:original.get(k) for k in ['theme','size_percent','edge_percent','opacity','x','y','custom_art']};pathlib.Path('.tools/v123-state.json').write_text(json.dumps(original),encoding='utf-8')
try:
 prefs({'theme':'light'});main();click('首页');receiver('qa_v123_native');check(values().get('qa_v123_native')=='3 checks passed','supplied SVG colors and resting/active celestial pixels')
 # Check real native view alpha during both state transitions.
 start('com.kongye.floatball.STOP');time.sleep(.9);main();click('首页')
 check(motion()['qa_v123_running']=='false','service rests before motion test')
 n=next(n for n in nodes() if n['label']=='启动悬浮球');adb('shell','input','tap',str((n['x1']+n['x2'])//2),str((n['y1']+n['y2'])//2));m=motion();check(m['qa_v123_running']=='true','start power enables service');check(0<float(m['qa_v123_ready_alpha'])<1,'ready text fades instead of appearing instantly');check(0<float(m['qa_v123_awake'])<1,'SVG celestial transition is in progress');time.sleep(1)
 check(float(motion()['qa_v123_awake'])==1,'active SVG ends at two suns')
 n=next(n for n in nodes() if n['label']=='关闭悬浮球');adb('shell','input','tap',str((n['x1']+n['x2'])//2),str((n['y1']+n['y2'])//2));m=motion();check(m['qa_v123_running']=='false' and m['qa_v123_service']=='true','stop keeps overlay alive briefly for exit animation');check(0<float(m['qa_v123_rest_alpha'])<1,'rest text fades in');time.sleep(1)
 m=motion();check(m['qa_v123_service']=='false' and float(m['qa_v123_awake'])==0,'service exits and SVG rests with two moons')
 start('com.kongye.floatball.RECOVER');time.sleep(.15);m=motion();check(m['qa_v123_recovering']=='true' and m['qa_v123_pulse_window']=='true','recover starts two yellow pulses in a non-touchable window');check(float(m['qa_v123_ball_scale'])>1,'recover enlarges the leaf');time.sleep(1.8);m=motion();check(m['qa_v123_recovering']=='false' and m['qa_v123_pulse_window']=='false' and float(m['qa_v123_ball_scale'])==1,'recover removes pulse window and restores scale')
 click('设置');q['find']('检查更新')
 for mode in ['current','available','error']:
  receiver('qa_v123_check_'+mode);m=motion();check(m['qa_v123_checking']=='true','checking is pending in settings: '+mode)
  check(any(n['label']=='正在检查更新' for n in nodes()),'spinner is in the version capsule: '+mode)
  if mode=='current':receiver('qa_v123_recreate');time.sleep(.25);check(motion()['qa_v123_checking']=='true','check survives Activity recreation')
  time.sleep(2);u=ui();labels=[n['label'] for n in u];check('应用内更新弹窗' in labels,'result opens only after check: '+mode)
  if mode=='current':
   check('✓ 已是最新版本' in labels and '我知道了' in labels,'latest badge and acknowledgement');check('小叶当前处于最佳状态，无需更新。' not in labels and '关闭' not in labels,'removed subtitle and old close wording');uc('我知道了')
  elif mode=='available':
   check('🎉 发现新版本' in labels and '立即下载' in labels,'new badge and actual download entry');left=next(n for n in u if n['label']=='稍后');right=next(n for n in u if n['label']=='立即下载');check(right['x1']>left['x2'],'result footer buttons are separated');uc('稍后')
  else:check('重试' in labels,'network error retains retry');uc('稍后')
  time.sleep(.25)
 check(not any('topResumedActivity=' in line and 'UpdateActivity' in line for line in adb('shell','dumpsys','activity','activities').splitlines()),'closed result returns to settings')
 # Use a real launcher task, rather than the helper's CLEAR_TOP Activity replacement.
 adb('shell','am','force-stop','com.kongye.floatball')
 def launch():adb('shell','am','start','-W','-a','android.intent.action.MAIN','-c','android.intent.category.LAUNCHER','-f','0x10200000','-n','com.kongye.floatball/.MainActivity')
 launch();time.sleep(2);click('设置');q['find']('检查更新');receiver('qa_v123_check_current');adb('shell','input','keyevent','3');time.sleep(2.1)
 check(not any('topResumedActivity=' in line and 'UpdateActivity' in line for line in adb('shell','dumpsys','activity','activities').splitlines()),'background check does not cover other apps')
 launch();time.sleep(.6);check('✓ 已是最新版本' in [n['label'] for n in ui()],'normal launcher return delivers completed result');uc('我知道了')
 for phase in ['downloading','ready','available_long']:
  adb('shell','am','start','-W','-f','0x18000000','-n','com.kongye.floatball/.DiagnosticActivity','--es','feature','qa_v1221_update_'+phase);time.sleep(.4);labels=[n['label'] for n in ui()]
  if phase=='downloading':
   check('更新下载进度' in labels,'download progress retained');uc('取消下载');check('立即下载' in [n['label'] for n in ui()],'cancel returns to available')
  elif phase=='ready':check('安装更新' in labels,'install entry retained without executing it')
  else:check('更新内容' in labels and '立即下载' in labels,'long release notes keep download action')
  uc('稍后')
finally:
 prefs(saved);main();click('首页');start('com.kongye.floatball.REFRESH');time.sleep(.8)
print(str(checks)+' checks passed; no phone media exported.',flush=True)
