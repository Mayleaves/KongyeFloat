"""Sony UI and padded 119dp GIF capture regression. Own synthetic media only; no media export."""
import runpy,pathlib,time,json,re,struct,shlex
m=runpy.run_path(str(pathlib.Path(__file__).with_name('v23-device.py')));q,adb,keys,v=m['q'],m['adb'],m['keys'],m['v'];d=420/160
root=pathlib.Path(__file__).resolve().parents[1]
state=json.loads((root/'.tools/v30-state.json').read_text(encoding='utf8'))
fields=['size','opacity','edge_gap','x','y','home_tools','gesture_up','gesture_down','gesture_tap','gesture_double','gesture_long','snap','fade','haptic','enhanced_overlay','theme','custom_art']
def check(ok,name):
 if not ok:raise AssertionError(name)
 print('PASS:',name,flush=True)
def receiver(command,values=None):
 args=['shell','am','broadcast','-n','com.kongye.floatball/.OverlayCheckReceiver','--es','command',command]
 if values is not None:args+=['--es','values',shlex.quote(json.dumps(values,separators=(',',':')))]
 adb(*args);time.sleep(.15)
def prefs(values):receiver('qa_preferences',values);time.sleep(.5)
def main():adb('shell','am','start','-W','-f','0x14000000','-n','com.kongye.floatball/.MainActivity');time.sleep(.5)
def nodes():receiver('main_snapshot');return json.loads(keys('qa_main_nodes').get('qa_main_nodes','[]'))
def click(label):
 found=[n for n in nodes() if n['label']==label];found.sort(key=lambda n:not n['clickable'])
 if not found:raise AssertionError('Missing main control '+label)
 n=found[0];v.tap((n['x1']+n['x2'])/2,(n['y1']+n['y2'])/2)
def find(label):
 for _ in range(8):
  if any(n['label']==label for n in nodes()):click(label);return
  v.swipe(540,2060,540,850,350)
 raise AssertionError('Missing main control '+label)
def down(x,y):adb('shell','input','motionevent','DOWN',str(round(x)),str(round(y)));time.sleep(.33)
def move(x,y):adb('shell','input','motionevent','MOVE',str(round(x)),str(round(y)))
def up(x,y):adb('shell','input','motionevent','UP',str(round(x)),str(round(y)))
def receipt():receiver('cross');return keys('qa_cross_x','qa_cross_y','qa_cross_ball_x','qa_cross_ball_y')
def fixture():
 gif=bytearray(b'GIF89a'+struct.pack('<HHBBB',128,128,0x81,0,0)+bytes([0,0,0,40,122,89,86,122,171,0,0,0])+b'\x21\xff\x0bNETSCAPE2.0\x03\x01\x00\x00\x00')
 for color in (1,2):
  codes=[code for _ in range(40*40) for code in (4,color)]+[5];encoded=bytearray();acc=bits=0
  for code in codes:
   acc|=code<<bits;bits+=3
   while bits>=8:encoded.append(acc&255);acc>>=8;bits-=8
  if bits:encoded.append(acc&255)
  gif+=b'\x21\xf9\x04\x09'+struct.pack('<H',30)+b'\0\0'+b','+struct.pack('<HHHHB',44,44,40,40,0)+b'\x02'
  for start in range(0,len(encoded),255):block=encoded[start:start+255];gif.append(len(block));gif+=block
  gif+=b'\0'
 gif+=b';';p=root/'.tools/padded-capture-fixture.gif';p.write_bytes(gif);adb('push',str(p),'/data/local/tmp/kongye-padded-capture.gif')
 adb('shell','run-as','com.kongye.floatball','cp','/data/local/tmp/kongye-padded-capture.gif','files/ball-art')

def run():
 global had_art
 accessibility_was_enabled='com.kongye.floatball/com.kongye.floatball.AssistService' in adb('shell','settings','get','secure','enabled_accessibility_services')
 had_art=False
 try:adb('shell','run-as','com.kongye.floatball','ls','files/ball-art');had_art=True
 except RuntimeError:pass
 if had_art:adb('shell','run-as','com.kongye.floatball','cp','files/ball-art','files/ball-art.qa-v30-backup')
 try:
  # Start from a recoverable position; retained preferences are restored in finally.
  prefs({'size':'48','opacity':'90','edge_gap':'0','x':'954','y':'850','snap':'true'});main()
  if any(n['label']=='开启悬浮球' for n in nodes()):click('开启悬浮球');time.sleep(.5)
  click('悬浮球')
  labels=[n['label'] for n in nodes()];check('外观与位置设置组' in labels and '外观与位置' in labels,'preview and appearance settings share one group');check('大小' in labels and '不透明度' in labels and '边框距离' in labels,'all appearance sliders available in unified card')
  # Change actual size through the visible native slider, then inspect its live preview.
  n=max((n for n in nodes() if n['label']=='大小'),key=lambda n:n['y1']);v.tap(n['x2']-42,(n['y1']+n['y2'])/2);receiver('main_snapshot');live=keys('size','qa_preview_width');check(int(live['qa_preview_width'])==round(int(live['size'])*d),'size slider updates live preview at actual dp size')
  find('自动吸边');labels=[n['label'] for n in nodes()];check(any('自动吸边' in n for n in labels),'auto snap follows edge distance');find('触感反馈');labels=[n['label'] for n in nodes()];check('触感反馈' in labels and '手势操作' in labels,'haptics belongs to gestures')
  click('设置');labels=[n['label'] for n in nodes()];check('显示与应用' in labels and '界面主题' in labels,'settings regroup display and theme');find('悬浮球维护');find('关于与更新');check(any(n['label']=='关于空叶' for n in nodes()),'about and updates are grouped together')
  # Install only a generated transparent-padded GIF; never read or export user image pixels.
  fixture();prefs({'size':'119','opacity':'90','custom_art':'true','x':'768','y':'850'});main();click('悬浮球');receiver('main_snapshot');check(int(keys('qa_preview_width')['qa_preview_width'])==round(119*d),'119dp GIF is shown at its actual configured preview size')
  q['m']['restore_accessibility']() if 'restore_accessibility' in q['m'] else None
  # The helper restores only this app's already enabled accessibility service.
  runpy.run_path(str(pathlib.Path(__file__).with_name('v24-device.py')))['restore_accessibility']()
  v.f.diagnostic('qa_record_pattern');time.sleep(.6)
  for size in (48,119):
   prefs({'size':str(size),'custom_art':str(size==119).lower(),'x':str(1080-round(size*d)),'y':'850'});x1,y1,x2,y2=q['win']('空叶悬浮球');down((x1+x2)/2,(y1+y2)/2);move(550,1100);time.sleep(.65);p=receipt();print('Cross size '+str(size)+':',p,flush=True);check(float(p['qa_cross_x'])==403 and float(p['qa_cross_y'])==953,'cross offset independent of '+str(size)+'dp artwork');
   if size==48:
    up(550,1100);time.sleep(.5);q['action']('close_tools');v.f.diagnostic('qa_record_pattern')
  move(1078,1450);time.sleep(.15);p=receipt();check(float(p['qa_cross_x'])==1080,'119dp padded GIF reaches exact right display boundary');up(1078,1450);q['own_ready']('截图图片，点击预览');doc=keys('document_width','document_height');print('Right edge document:',doc,flush=True);check(doc.get('document_width')=='677' and doc.get('document_height')=='350','real capture includes the final right screen pixel');q['own_click']('关闭截图结果')
  v.f.diagnostic('qa_record_pattern');x1,y1,x2,y2=q['win']('空叶悬浮球');down((x1+x2)/2,(y1+y2)/2);move(550,1100);time.sleep(.65);move(800,2518);time.sleep(.15);check(float(receipt()['qa_cross_y'])==2520,'119dp GIF reaches bottom display boundary');up(800,2518);q['own_ready']('截图图片，点击预览');q['own_click']('关闭截图结果')
  v.f.diagnostic('qa_record_pattern');x1,y1,x2,y2=q['win']('空叶悬浮球');down((x1+x2)/2,(y1+y2)/2);move(550,1100);up(550,1100);time.sleep(.4);check(keys('drag_cross_state').get('drag_cross_state')=='red','early release remains cancelled');check(not any(n['window']=='空叶截图窗口' for n in q['nodes']()),'early release opens no capture window')
 finally:
  try:up(550,1100);q['action']('close_tools')
  except Exception:pass
  if had_art:
   adb('shell','run-as','com.kongye.floatball','cp','files/ball-art.qa-v30-backup','files/ball-art');adb('shell','run-as','com.kongye.floatball','rm','files/ball-art.qa-v30-backup')
  else:adb('shell','run-as','com.kongye.floatball','rm','-f','files/ball-art')
  prefs({key:state.get(key) for key in fields});check(keys(*fields)==state,'all original configuration restored');main()
  if accessibility_was_enabled:runpy.run_path(str(pathlib.Path(__file__).with_name('v24-device.py')))['restore_accessibility']()
if __name__=='__main__':run()
