"""Owned metadata and generated release fixtures only; no screen/media export or real update download."""
import runpy,pathlib,time,json,os,re,xml.etree.ElementTree as ET
os.environ['KONGYE_ADB_SERIAL']='525114fb'
q=runpy.run_path(str(pathlib.Path(__file__).with_name('v118-device.py')))
adb,values,prefs,main,nodes,click,receiver,tap=[q[k] for k in ['adb','values','prefs','main','nodes','click','receiver','tap']]
checks=0
def check(ok,label):
 global checks
 assert ok,label
 checks+=1;print('PASS:',label,flush=True)
def feature(name):adb('shell','am','start','-W','-f','0x18000000','-n','com.kongye.floatball/.DiagnosticActivity','--es','feature',name);time.sleep(.5)
def ui():receiver('qa_v1221_ui');return json.loads(values()['qa_v1221_ui'])
def label(n):return n['label']
def unode(name):return next(n for n in ui() if label(n)==name)
def bounds(n):return [n['x1'],n['y1'],n['x2'],n['y2']]
def uc(name):
 n=unode(name);b=bounds(n);tap((b[0]+b[2])/2,(b[1]+b[3])/2)
def motion():receiver('qa_v1221_motion');return values()
original=values();saved={k:original.get(k) for k in ['theme','home_tools','custom_art','size_percent','edge_percent','opacity','x','y']};pathlib.Path('.tools/v1221-state.json').write_text(json.dumps(original),encoding='utf-8')
try:
 feature('qa_v1221_native');check(values().get('qa_v1221_native')=='6 checks passed','release notes, transparent ball geometry and custom art checks pass')
 prefs({'theme':'light'});main();click('设置');click('首页');time.sleep(.3);one=motion();time.sleep(.7);two=motion();allowed=two.get('qa_motion_allowed')=='true'
 check((two.get('qa_plant_motion')=='true' and one.get('qa_plant_sway')!=two.get('qa_plant_sway')) if allowed else two.get('qa_plant_motion')=='false','visible plant sways, respecting system animation setting')
 adb('shell','input','swipe','620','2100','620','800','300');time.sleep(.3);state=motion();check(state.get('qa_plant_visibility')!='0' and state.get('qa_plant_motion')=='false','plant stops when scrolled off the card')
 click('设置');click('首页');time.sleep(.2);state=motion();check(state.get('qa_plant_motion')==str(allowed).lower(),'plant resumes when visible')
 feature('qa_v1221_about');one=motion();time.sleep(.6);two=motion();check((two.get('qa_logo_motion')=='true' and one.get('qa_logo_y')!=two.get('qa_logo_y')) if allowed else two.get('qa_logo_motion')=='false','about logo gently floats when active')
 check(-20<float(two['qa_logo_y'])<=0 and 0<=float(two['qa_logo_rotation'])<=2.1,'about motion stays within gentle HTML amplitude')
 uc('返回设置');state=motion();check(state.get('qa_logo_motion')=='false' and float(state['qa_logo_y'])==0,'about animation stops and resets on exit')
 for mode in ['current','available','available_long','downloading','ready','error','checking']:
  feature('qa_v1221_update_'+mode);u=ui();names=[label(n) for n in u];check('应用内更新弹窗' in names and '更新版本对比' in names,mode+' keeps modal and version comparison')
  if mode=='current':check('立即下载' not in names and '重试' not in names and '关闭' in names,'latest state has no redundant retry or download')
  if mode in ['available','available_long']:
   check('立即下载' in names and '稍后' in names,'new version exposes in-app download')
   left=bounds(unode('稍后'));right=bounds(unode('立即下载'));check(right[0]-left[2]>16 and right[3]-right[1]<150,'footer actions have separate spacing and compact height')
   card=bounds(unode('应用内更新弹窗'));check(card[1]>50 and card[3]<2700,'modal keeps space from screen edges')
   check(not any('安装时由系统' in label(n) for n in u),'removed explanatory line stays absent')
  if mode=='downloading':
   check('更新下载进度' in names and '取消下载' in names,'download state keeps progress and cancellation');uc('取消下载');check('立即下载' in [label(n) for n in ui()],'cancel returns to available state');uc('稍后');continue
  if mode=='ready':check('安装更新' in names,'downloaded state keeps system-install action')
  if mode=='error':check('重试' in names,'connection failure keeps retry action')
  uc('稍后' if '稍后' in names else '关闭')
 prefs({'theme':'dark'});feature('qa_v1221_update_available');u=ui();check('立即下载' in [label(n) for n in u],'dark update modal keeps real action');uc('稍后')
 check(not values().get('qa_v1221_error'),'no motion or update fixture errors')
 for mode,expected in [('system','theme_system'),('dark','theme'),('light','sun')]:
  prefs({'theme':mode});main();click('设置');title=next(n for n in nodes() if n['label']=='深色模式');other=next(n for n in nodes() if n['label']=='增强悬浮层');check(any(i['name']==expected for i in title['icons']),mode+' theme has requested icon');check(abs(title['text_sp']-other['text_sp'])<.1,'theme title matches other setting font size')
finally:
 prefs(saved);main();click('设置');click('首页')
print(str(checks)+' checks passed',flush=True)
