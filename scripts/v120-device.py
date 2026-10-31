"""1.20 own UI acceptance; never export phone screenshots, GIFs or video."""
import runpy,pathlib,time,json,re,xml.etree.ElementTree as ET
q=runpy.run_path(str(pathlib.Path(__file__).with_name('v118-device.py')))
adb,values,prefs,main,nodes,matching,node,click,find,receiver,tap=[q[k] for k in ['adb','values','prefs','main','nodes','matching','node','click','find','receiver','tap']]
checks=0
def check(ok,label):
 global checks
 if not ok:raise AssertionError(label)
 checks+=1;print('PASS:',label,flush=True)
def feature(name):adb('shell','am','start','-W','-f','0x18000000','-n','com.kongye.floatball/.DiagnosticActivity','--es','feature',name);time.sleep(.9)
def overlays():receiver('snapshot');return json.loads(values().get('qa_overlay_nodes','[]'))
def onode(label):return next(n for n in overlays() if n['label']==label)
def oc(label):
 n=onode(label);tap((n['x1']+n['x2'])/2,(n['y1']+n['y2'])/2)
def ui():
 adb('shell','uiautomator','dump','/data/local/tmp/kongye-v120-ui.xml');tree=ET.fromstring(adb('exec-out','cat','/data/local/tmp/kongye-v120-ui.xml'));return [n for n in tree.iter('node') if n.get('package')=='com.kongye.floatball']
def uc(label):
 found=[n for n in ui() if n.get('text')==label or n.get('content-desc')==label];n=next((n for n in found if n.get('clickable')=='true'),found[0]);b=list(map(int,re.findall(r'\d+',n.attrib['bounds'])));tap((b[0]+b[2])/2,(b[1]+b[3])/2)
fields=['size','opacity','edge_gap','x','y','theme','custom_art','snap','fade','home_tools'];v=values();original={k:v.get(k) for k in fields};pathlib.Path('.tools/v120-ui-state.json').write_text(json.dumps(original),encoding='utf-8')
try:
 prefs({'theme':'light','custom_art':True,'size':36,'edge_gap':0,'fade':False});main();click('悬浮球');v=values();nodes();v=values();d=476/160
 check(max(int(v['qa_preview_width']),int(v['qa_preview_height']))==round(36*d),'36dp custom GIF uses visible long edge')
 check(int(v['qa_preview_right'])==int(v['qa_preview_stage_width']) and float(v['qa_preview_shift'])==0,'custom GIF preview touches right edge at 0dp')
 prefs({'size':150});main();click('悬浮球');nodes();v=values();check(max(int(v['qa_preview_width']),int(v['qa_preview_height']))==round(150*d),'custom GIF size grows to visible 150dp')
 prefs({'size':36,'edge_gap':-100});main();click('悬浮球');nodes();check(float(values()['qa_preview_shift'])>0,'negative edge distance can hide part of custom GIF')
 prefs({'edge_gap':0,'custom_art':False});main();click('悬浮球');nodes();v=values();check(int(v['qa_preview_width'])==round(36*d) and int(v['qa_preview_height'])==round(36*d),'default ball and GIF use same dp scale')
 click('设置');find('检查更新');click('检查更新');u=ui();check(any(n.get('content-desc')=='应用内更新弹窗' for n in u),'check update opens a modal dialog')
 end=time.monotonic()+25
 while any(n.get('content-desc')=='更新状态：正在检查最新版本…' for n in u) and time.monotonic()<end:time.sleep(.5);u=ui()
 check(any(n.get('content-desc','').startswith('更新状态：') and '正在检查' not in n.get('content-desc','') for n in u),'update check completes inside dialog');uc('关闭')
 main();click('设置');find('关于空叶');click('关于空叶');u=ui();check(any(n.get('text')=='关于空叶' for n in u),'about title visible');check('com.kongye.floatball/.AboutActivity' in adb('shell','dumpsys','activity','activities'),'about opens dedicated page');uc('返回设置')
 main();click('首页');
 if matching('稍作休息。'):click('启动悬浮球');time.sleep(.6)
 feature('manage_quick');u=ui();buttons=[n for n in u if n.get('content-desc') in ['完成','调整快捷面透明度']];check(len(buttons)==2,'quick management has two controls');bounds=[list(map(int,re.findall(r'\d+',n.attrib['bounds']))) for n in buttons];bounds.sort();check(bounds[1][0]-bounds[0][2]>=round(8*d),'quick management buttons have 8dp gap');uc('完成')
 feature('qa_record_video');full=onode('录制全屏');check(not full['enabled'] and full['alpha']<.5,'full-screen control disabled and grey at full selection');check(any(i['name']=='full_screen' for i in full['icons']),'video full-screen control uses corners glyph');check(any(i['name']=='resolution' for i in onode('录制分辨率')['icons']),'recording uses new resolution glyph')
 handle=onode('调整录制右边');x=(handle['x1']+handle['x2'])/2;y=(handle['y1']+handle['y2'])/2;adb('shell','input','swipe',str(round(x)),str(round(y)),str(round(x-180)),str(round(y)),'450');time.sleep(.4);check(onode('录制全屏')['enabled'],'full-screen control enabled after resizing');oc('录制全屏');check(not onode('录制全屏')['enabled'],'restoring full screen disables the control again');oc('关闭录制准备')
 feature('qa_v120');check(values().get('qa_v120_result')=='19 checks passed','native icon, hollow scissors, button gap checks')
 # Only inspect task names for the external system file chooser; never its file list.
 feature('files');tasks=adb('shell','dumpsys','activity','activities');check('com.kongye.floatball.files' in tasks,'file picker uses isolated task');top=next((line for line in tasks.splitlines() if 'topResumedActivity' in line),'');check('com.kongye.floatball' not in top and ('PickerActivity' in top or 'DocumentsActivity' in top or 'documentsui' in top),'system file picker opens');adb('shell','input','keyevent','4');time.sleep(.4)
finally:
 try:
  if any(n['label']=='关闭录制准备' for n in overlays()):oc('关闭录制准备')
 except Exception:pass
 prefs(original);main();click('首页');check(all(values().get(k)==v for k,v in original.items()),'original appearance, size, position and tools restored')
 if matching('稍作休息。'):click('启动悬浮球')
print('TOTAL:',checks,'UI checks passed; no phone media exported.',flush=True)
