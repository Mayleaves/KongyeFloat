"""Changed update layouts and animated header; owned metadata only, no downloads/media export."""
import runpy,pathlib,time,json,os
os.environ['KONGYE_ADB_SERIAL']='525114fb'
q=runpy.run_path(str(pathlib.Path(__file__).with_name('v118-device.py')))
adb,values,prefs,main,nodes,click,receiver,tap=[q[k] for k in ['adb','values','prefs','main','nodes','click','receiver','tap']]
checks=0
def check(ok,label):
 global checks
 assert ok,label
 checks+=1;print('PASS:',label,flush=True)
def feature(name):adb('shell','am','start','-W','-f','0x18000000','-n','com.kongye.floatball/.DiagnosticActivity','--es','feature',name);time.sleep(.3)
def ui():receiver('qa_v1221_ui');return json.loads(values()['qa_v1221_ui'])
def unode(name):return next(n for n in ui() if n['label']==name)
def uc(name):
 n=unode(name);tap((n['x1']+n['x2'])/2,(n['y1']+n['y2'])/2)
def motion():receiver('qa_v1221_motion');return values()
original=values();saved={k:original.get(k) for k in ['theme','home_tools','custom_art','size_percent','edge_percent','opacity','x','y']};pathlib.Path('.tools/v1222-state.json').write_text(json.dumps(original),encoding='utf-8')
try:
 prefs({'theme':'light'});main();click('设置');click('首页');edit=next(n for n in nodes() if n['label']=='管理首页工具');check(any(i['name']=='tool_sliders' for i in edit['icons']),'home tool editor uses the requested sliders symbol')
 click('设置');q['find']('检查更新');n=nodes();badge=next(n for n in n if n['label']=='当前版本 v1.22.2');check(badge['text_sp']==12 and badge['height']<100,'installed version appears as compact capsule');prefs({'theme':'system'});main();click('设置');check('theme_system' in [i['name'] for n in nodes() if n['label']=='深色模式' for i in n['icons']],'system mode uses the redrawn original sun-moon symbol');prefs({'theme':'light'})
 for mode in ['current','current_ahead','available','available_long','downloading','ready','error','checking']:
  feature('qa_v1221_update_'+mode);u=ui();labels=[n['label'] for n in u]
  check('应用内更新弹窗' in labels and '更新图标波纹' in labels,mode+' shows custom modal and pulse icon')
  check('更新检查状态' not in labels and '已是最新' not in labels and '有新版本' not in labels,'crossed-out status selector is absent')
  if mode in ['current','available']:
   one=motion();time.sleep(.3);two=motion();allowed=two.get('qa_motion_allowed')=='true';check(two.get('qa_update_pulse_running')==str(allowed).lower() and (not allowed or one['qa_update_pulse_phase']!=two['qa_update_pulse_phase']),'header ring pulses according to system animation setting')
  if mode.startswith('current'):
   check(all(t in labels for t in ['当前安装版本','公开稳定版本','小叶当前处于最佳状态，无需更新。','Release','关闭']),'latest uses paired cards and success explanation')
   check('立即下载' not in labels and '重试' not in labels,'latest does not offer download or retry')
   check(('领先' in labels)==(mode=='current_ahead'),'leading badge reflects actual comparison')
  if mode in ['available','available_long']:
   check(all(t in labels for t in ['当前版本','最新版本','NEW','更新内容','稍后','立即下载']),'available keeps real comparison, release notes and download action')
   left=unode('稍后');right=unode('立即下载');check(right['x1']-left['x2']>16,'footer buttons remain separated')
   bounds=unode('应用内更新弹窗');check(bounds['y1']>50 and bounds['y2']<2700,'long content keeps modal away from screen edges')
  check(not any('安装时由系统' in n['label'] or '后续更新支持' in n['label'] for n in u),'crossed-out explanatory strips remain absent')
  if mode=='downloading':
   check('更新下载进度' in labels,'download progress remains');uc('取消下载');check('立即下载' in [n['label'] for n in ui()],'cancellation returns to available state');uc('稍后')
  else:
   if mode=='ready':check('安装更新' in labels,'downloaded package retains install entry')
   if mode=='error':check('重试' in labels,'error retains retry')
   uc('稍后' if '稍后' in labels else '关闭')
  check(motion().get('qa_update_pulse_running')=='false','ripple stops after dialog closes')
 prefs({'theme':'dark'});feature('qa_v1221_update_current_ahead');check('领先' in [n['label'] for n in ui()],'dark latest comparison retains leading state');uc('关闭')
 check(not values().get('qa_v1221_error'),'no fixture or animation errors')
finally:prefs(saved);main();click('设置');click('首页')
print(str(checks)+' checks passed',flush=True)
