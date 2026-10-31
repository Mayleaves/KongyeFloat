"""1.21 acceptance: owned UI metadata and generated test content; no screen/media export."""
import runpy,pathlib,time,json,re,xml.etree.ElementTree as ET
q=runpy.run_path(str(pathlib.Path(__file__).with_name('v118-device.py')))
adb,values,prefs,main,nodes,matching,node,click,find,receiver,tap=[q[k] for k in ['adb','values','prefs','main','nodes','matching','node','click','find','receiver','tap']]
checks=0
def check(ok,label):
 global checks
 if not ok:raise AssertionError(label)
 checks+=1;print('PASS:',label,flush=True)
def feature(name):adb('shell','am','start','-W','-f','0x18000000','-n','com.kongye.floatball/.DiagnosticActivity','--es','feature',name);time.sleep(1)
def overlays():receiver('snapshot');return json.loads(values().get('qa_overlay_nodes','[]'))
def onode(label):return next(n for n in overlays() if n['label']==label)
def oc(label):
 n=onode(label);tap((n['x1']+n['x2'])/2,(n['y1']+n['y2'])/2)
def wait_overlay(label):
 end=time.monotonic()+10
 while time.monotonic()<end:
  if any(n['label']==label for n in overlays()):return onode(label)
  time.sleep(.15)
 raise AssertionError('Missing overlay '+label)
def ui():
 adb('shell','uiautomator','dump','/data/local/tmp/kongye-v121-ui.xml');return [n for n in ET.fromstring(adb('exec-out','cat','/data/local/tmp/kongye-v121-ui.xml')).iter('node') if n.get('package')=='com.kongye.floatball']
def text(n):return n.get('text') or n.get('content-desc') or ''
def uc(label):
 found=[n for n in ui() if text(n)==label];n=next((n for n in found if n.get('clickable')=='true'),found[0]);b=list(map(int,re.findall(r'\d+',n.attrib['bounds'])));tap((b[0]+b[2])/2,(b[1]+b[3])/2)
def title_id():return next(n['identity'] for n in nodes() if n['label']=='首页' and not n['clickable'])
fields=['size','size_percent','edge_percent','opacity','edge_gap','x','y','theme','custom_art','snap','fade','home_tools'];v=values();original={k:v.get(k) for k in fields};pathlib.Path('.tools/v121-ui-state.json').write_text(json.dumps(original),encoding='utf-8')
try:
 prefs({'theme':'light','size_percent':100,'edge_percent':100,'custom_art':False,'fade':False,'home_tools':'["region","longshot","files"]'});main();click('首页')
 if matching('稍作休息。'):click('启动悬浮球');time.sleep(.6)
 hour=int(adb('shell','date','+%H'));stage=5 if hour<6 or hour>=21 else (hour-6)//3;name=['发芽','成长','茂盛','花苞','开花','休息'][stage];check(bool(matching('盆栽：'+name)),'header plant follows local hour')
 before=title_id();requests=int(values().get('qa_bitmap_requests','0'));click('区域截图');wait_overlay('识别并查看');check(int(values().get('qa_bitmap_requests','0'))==requests,'opening region selector does not capture a frame');check(title_id()==before,'region tool does not rebuild home behind overlay');oc('取消')
 before=title_id();click('打开文件');time.sleep(.8);top=next((l for l in adb('shell','dumpsys','activity','activities').splitlines() if 'topResumedActivity' in l),'');check('com.kongye.floatball' not in top and ('PickerActivity' in top or 'DocumentsActivity' in top or 'documentsui' in top),'file tool reaches system picker');adb('shell','input','keyevent','4');time.sleep(.5);check(title_id()==before,'file picker round trip keeps home view instance')
 click('管理首页工具');u=ui();buttons=[n for n in u if text(n) in ['恢复默认','取消','确定']];check(len(buttons)==3,'home tool dialog retains three actions');d=476/160
 for n in buttons:
  b=list(map(int,re.findall(r'\d+',n.attrib['bounds'])));check(b[3]-b[1]<=round(38*d),'dialog action has compact height');check(b[3]<2772-round(24*d),'dialog action stays above screen bottom')
 uc('取消');click('悬浮球');check(bool(matching('100%')),'size and edge show percentages');prefs({'size_percent':500,'edge_percent':30});main();click('悬浮球');nodes();v=values();check(int(v['qa_preview_width'])==round(180*d),'500 percent preview is 180dp');check(abs(float(v['qa_preview_shift'])-round(int(v['qa_preview_width'])*.7))<=1,'30 percent leaves three tenths visible');check(bool(matching('500%')) and bool(matching('30%')),'new range endpoints are visible');prefs({'size_percent':100,'edge_percent':100})
 feature('qa_v121');check(values().get('qa_v121_result')=='15 checks passed','supplied icon, six SVG stages and native splash render')
 feature('qa_update_current');u=ui();check(any(n.get('content-desc')=='更新状态：已是最新版本。' for n in u),'current version says latest');check(not any(text(n) in ['重新检查','更新','重试'] for n in u),'latest state has no retry/update action');uc('关闭')
 feature('qa_update_available');u=ui();check(any(text(n)=='更新' for n in u),'new version exposes update action');check(not any(text(n)=='重新检查' for n in u),'update dialog removes recheck action');uc('关闭')
 feature('longshot');wait_overlay('选择横拼接');before={n['label']:n['identity'] for n in overlays() if n['label'] in ['手动长截图工具栏','移动截图区域','添加截图']};oc('选择横拼接');check(onode('选择横拼接')['selected'],'horizontal axis selected');after={n['label']:n['identity'] for n in overlays() if n['label'] in before};check(before==after,'axis switch keeps crop, toolbar and add control attached');oc('选择竖拼接');check(onode('选择竖拼接')['selected'],'vertical axis selected');oc('取消长截图')
 # The following screenshot contains only the app-generated diagnostic rows and stays on the phone.
 feature('region');wait_overlay('识别并查看');requests=int(values().get('qa_bitmap_requests','0'));oc('识别并查看');wait_overlay('关闭截图结果');check(int(values().get('qa_bitmap_requests','0'))==requests+1,'confirm region captures exactly once');oc('关闭截图结果')
 # A launcher cold launch gets the supplied animation; navigation and gesture entry do not.
 adb('shell','am','start','-W','-f','0x10008000','-a','android.intent.action.MAIN','-c','android.intent.category.LAUNCHER','-n','com.kongye.floatball/.MainActivity');receiver('main_snapshot');launch=json.loads(values().get('qa_main_nodes','[]'));check(any(n['label']=='空叶开屏动画' for n in launch),'launcher entry displays supplied animation');time.sleep(1.8);check(bool(matching('首页')) and any(n['label'].startswith('盆栽：') for n in nodes()),'splash finishes into home with timed plant')
finally:
 try:
  labels={n['label'] for n in overlays()}
  for label in ['取消长截图','关闭截图结果','取消']:
   if label in labels:oc(label);break
 except Exception:pass
 prefs(original);main();click('首页');restored=values();check(all(restored.get(k)==v for k,v in original.items()),'original tools and appearance restored')
 if matching('稍作休息。'):click('启动悬浮球')
print('TOTAL:',checks,'checks passed; no phone media exported.',flush=True)
