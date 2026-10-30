"""v1.18 layout acceptance on the authorized PHK110; own view metadata, no media export."""
import runpy,pathlib,time,json,re,shlex,xml.etree.ElementTree as ET
m=runpy.run_path(str(pathlib.Path(__file__).with_name('device-test.py')));adb=m['adb'];root=pathlib.Path(__file__).resolve().parents[1]
checks=0

def check(ok,label):
 global checks
 if not ok:raise AssertionError(label)
 checks+=1;print('PASS:',label,flush=True)

def values():
 for attempt in range(8):
  try:
   tree=ET.fromstring(adb('exec-out','run-as','com.kongye.floatball','cat','shared_prefs/kongye.xml'))
   return {n.attrib['name']:(n.text or '' if n.tag=='string' else n.attrib.get('value')) for n in tree}
  except ET.ParseError:
   if attempt==7:raise
   time.sleep(.05)

def receiver(command,data=None):
 args=['shell','am','broadcast','-n','com.kongye.floatball/.OverlayCheckReceiver','--es','command',command]
 if data is not None:args+=['--es','values',shlex.quote(json.dumps(data,separators=(',',':')))]
 adb(*args);time.sleep(.08)

def prefs(data):receiver('qa_preferences',data);time.sleep(.2)

def main():adb('shell','am','start','-W','-f','0x14000000','-n','com.kongye.floatball/.MainActivity');time.sleep(.25)

def nodes():receiver('main_snapshot');return json.loads(values()['qa_main_nodes'])

def ready(label):
 end=time.monotonic()+4
 while time.monotonic()<end:
  result=matching(label)
  if result:return result
  time.sleep(.1)
 raise AssertionError('Timed out waiting for own control '+label)

def matching(label):return [n for n in nodes() if n['label']==label]

def node(label):
 found=matching(label)
 if not found:raise AssertionError('Missing own control '+label)
 nav={'首页':'nav_home','悬浮球':'leaf','设置':'nav_settings'}.get(label)
 if nav:found=[n for n in found if n['clickable'] and any(i['name']==nav for i in n['icons'])]
 return sorted(found,key=lambda n:not n['clickable'])[0]

def tap(x,y):adb('shell','input','tap',str(round(x)),str(round(y)));time.sleep(.18)

def click(label):
 n=node(label);tap((n['x1']+n['x2'])/2,(n['y1']+n['y2'])/2)

def find(label):
 for _ in range(8):
  found=matching(label)
  if found:return found[0]
  adb('shell','input','swipe','620','2100','620','950','300');time.sleep(.12)
 raise AssertionError('Missing scroll control '+label)

def icons(label):return [n['name'] for n in node(label)['icons'] if n['name']!='forward']

def drag(first,last):
 n=node(first);t=node(last);x,y=(n['x1']+n['x2'])/2,(n['y1']+n['y2'])/2;tx,ty=(t['x1']+t['x2'])/2,(t['y1']+t['y2'])/2
 adb('shell','input','motionevent','DOWN',str(round(x)),str(round(y)));time.sleep(.8)
 for i in range(1,13):adb('shell','input','motionevent','MOVE',str(round(x+(tx-x)*i/12)),str(round(y+(ty-y)*i/12)))
 adb('shell','input','motionevent','UP',str(round(tx)),str(round(ty)));time.sleep(.3)

if __name__=='__main__':
 fields=['size','opacity','edge_gap','x','y','theme','home_tools','snap','fade','haptic']
 initial=values();original={key:initial.get(key) for key in fields};(root/'.tools/v118-state.json').write_text(json.dumps(original),encoding='utf-8')
 try:
  prefs({'theme':'light','size':'48','opacity':'90','edge_gap':'0','home_tools':json.dumps(['region','longshot','record','gif_record','recent_apps','favorite_apps','notes','browser'])});main()
  labels=[n['label'] for n in nodes()];check('空叶悬浮球' not in labels,'home starts with status card, without redundant page title')
  check('悬浮球运行状态卡片' in labels,'botanical status card is visible')
  if '稍作休息' in labels:click('启动悬浮球')
  check(bool(ready('随时待命')),'running state uses ready wording');click('关闭悬浮球');check(bool(ready('稍作休息')),'power button stops ball and shows resting wording');click('启动悬浮球');check(bool(ready('随时待命')),'power button starts ball and restores ready wording')
  click('关闭悬浮球');check(bool(ready('稍作休息')),'ball paused during layout checks to avoid covering controls')
  n=nodes();permissions=[next(p for p in n if p['label'].startswith(prefix)) for prefix in ['悬浮窗，','无障碍，','通知，']]
  check(len({p['y1'] for p in permissions})==1,'three permission controls form one horizontal row');check(abs((permissions[1]['x1']-permissions[0]['x1'])-(permissions[2]['x1']-permissions[1]['x1']))<=1,'permission columns are evenly distributed')
  nav=node('底部悬浮导航');check(nav['x1']>0 and nav['x2']<1240 and nav['height']==190,'navigation is inset and 64dp tall')
  check(icons('首页')==['nav_home'] and icons('设置')==['nav_settings'] and icons('悬浮球')==['leaf'],'navigation uses solid home, flower settings and original leaf')
  check(node('首页')['selected'] and not node('设置')['selected'],'navigation exposes correct selected state')
  check(icons('长截图')==['screen'] and icons('录制 GIF')==['image'] and icons('常用软件')==['leaf_outline'],'home tools match layout-review icon vocabulary')
  for label in ['区域截图','长截图','录屏','录制 GIF','最近软件','常用软件','便签','浏览器']:check(node(label)['y2']<nav['y1'],label+' is visible above floating navigation')
  drag('录屏','区域截图');check(json.loads(values()['home_tools'])[0]=='record','tool drag sorting still works after redesign')
  click('悬浮球');look=node('外观与位置设置组');check(node('外观与位置')['y1']-look['y1']<60,'ball page begins with unified appearance card')
  check(abs(node('外观与位置')['text_sp']-15)<.01,'appearance heading is 15sp');check('leaf_outline' in icons('悬浮球外观与位置实时预览'),'live preview uses layout-review leaf')
  slider=max(matching('大小'),key=lambda n:n['y1']);tap(slider['x2']-54,(slider['y1']+slider['y2'])/2);receiver('main_snapshot');live=values();check(int(live['size'])>48 and int(live['qa_preview_width'])==round(int(live['size'])*476/160),'size slider updates live preview at actual dp size')
  check(bool(matching('空闲时淡化')) and bool(matching('自动吸边')),'fade and snap stay with related appearance controls')
  find('触感反馈');check(bool(matching('触感反馈')),'gesture haptics remains available');check(node('底部悬浮导航')['y1']==nav['y1'],'navigation remains fixed while page scrolls')
  click('设置');n=nodes();titles=[a for a in n if a['label']=='设置' and 'text_sp' in a and abs(a['text_sp']-15)<.01];check(len(titles)==1,'settings page heading matches 15sp appearance heading')
  check(not matching('显示与应用'),'settings starts directly with display controls');check(icons('增强悬浮层')==['layers'] and icons('特定应用中的悬浮球')==['apps_grid'],'settings display icons match approved prototype')
  find('深色模式');check(icons('深色模式')==['theme'],'theme row uses moon icon');click('深色模式')
  # Read only this app dialog through uiautomator, since it is outside the activity root.
  def dialog_click(label):
   adb('shell','uiautomator','dump','/data/local/tmp/kongye-v118-ui.xml');tree=ET.fromstring(adb('exec-out','cat','/data/local/tmp/kongye-v118-ui.xml'))
   item=next(n for n in tree.iter('node') if n.get('package')=='com.kongye.floatball' and n.get('text')==label and n.get('resource-id')!='android:id/alertTitle');b=list(map(int,re.findall(r'\d+',item.attrib['bounds'])));tap((b[0]+b[2])/2,(b[1]+b[3])/2)
  dialog_click('深色模式');check(next(n for n in nodes() if n['label']=='设置' and abs(n.get('text_sp',0)-15)<.01)['text_color']==-1642775,'dark theme remains readable and switches immediately')
  prefs({'theme':'light'});main();click('设置');find('找回悬浮球');find('重置位置');check(icons('重置位置')==['position'],'reset position retains v1.17 crosshair icon')
  find('关于与更新');find('GitHub');check(icons('GitHub')==['github'],'GitHub has approved source icon');find('关于空叶');check(bool(matching('检查更新')) or bool(find('关于空叶')),'about and update group remains reachable')
 finally:
  prefs(original);main();click('首页');restored=values();check(all(restored.get(k)==v for k,v in original.items()),'original preferences and custom tool order restored')
  if matching('稍作休息'):click('启动悬浮球')
 print('TOTAL:',checks,'checks passed; no phone screenshot or media exported.',flush=True)
