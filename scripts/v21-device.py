"""No-root Sony overlays; only own synthetic page, no screenshot/media export."""
import runpy,pathlib,time,re,sys,json
m=runpy.run_path(str(pathlib.Path(__file__).with_name('v20-device.py')));a,v,adb,keys,win=m['a'],m['v'],m['adb'],m['keys'],m['win']

raw_keys=keys
def keys(*names):
 for attempt in range(5):
  try:return raw_keys(*names)
  except Exception:
   if attempt==4:raise
   time.sleep(.08)

def action(name,**kwargs):
 args=['shell','am','start','-W','-n','com.kongye.floatball/.OverlayCheckActivity','--es','check',name]
 for key,value in kwargs.items():args+=['--ez',key,str(value).lower()]
 adb(*args);time.sleep(.75)

def block(title):
 for b in re.split(r'(?=  Window #)',adb('shell','dumpsys','window','windows')):
  if title in b.splitlines()[0]:return b
 raise AssertionError('Missing own window '+title)

def visible(title):return bool(re.search(r'^\s+isVisible=true\s*$',block(title),re.M))
def layer(title):
 value=re.search(r'\bty=([A-Z_]+|\d+)',block(title)).group(1);return {'APPLICATION_OVERLAY':2038,'ACCESSIBILITY_OVERLAY':2032}.get(value,int(value) if value.isdigit() else -1)
def check(value,text):a.check(value,text)
def wait_window(title,seconds=8):
 end=time.monotonic()+seconds
 while time.monotonic()<end:
  try:return layer(title)
  except AssertionError:time.sleep(.15)
 raise AssertionError('Missing window '+title)
def wait(key,value,timeout=10):
 end=time.monotonic()+timeout
 while time.monotonic()<end:
  if keys(key).get(key)==value:return
  time.sleep(.2)
 raise AssertionError('Missing state '+key+': '+str(keys(key)))

def core():
 a.main();action('page',hide=True,secure=False);time.sleep(.5);assert keys('qa_overlay_connected').get('qa_overlay_connected')=='true'
 original=keys('size','edge_gap','x','y','gesture_tap','gesture_up','gesture_down','recording_mode','record_hide','record_resolution','gif_resolution','browser_engine');pathlib.Path('.tools/v21-state.json').write_text(json.dumps(original),encoding='utf-8')
 action('ordinary');check(layer('空叶悬浮球')==2038,'compatibility mode uses ordinary overlay');check(not visible('空叶悬浮球'),'synthetic page actually hides ordinary overlay')
 action('enhanced');check(layer('空叶悬浮球')==2032 and visible('空叶悬浮球'),'enhanced ball stays visible on synthetic hide-overlays page')
 before=keys('qa_touch_time').get('qa_touch_time');x1,y1,x2,y2=win('空叶悬浮球');v.tap((x1+x2)/2,(y1+y2)/2);check(keys('qa_touch_time').get('qa_touch_time')!=before,'enhanced ball receives touch on protected synthetic page')
 adb('shell','input','motionevent','DOWN',str((x1+x2)//2),str((y1+y2)//2));time.sleep(1.2);check(wait_window('空叶按住快捷')==2032 and visible('空叶按住快捷'),'held shortcut dock also uses enhanced layer');adb('shell','input','motionevent','UP',str((x1+x2)//2),str((y1+y2)//2))
 action('capture');wait('qa_overlay_capture_done','true');r=keys('qa_overlay_capture_red','qa_overlay_capture_error');print('Unsecured synthetic capture:',r,flush=True);check(int(r['qa_overlay_capture_red'])>1000 and not r['qa_overlay_capture_error'],'allowed synthetic page captures actual pixels in no-root mode')
 action('page',hide=True,secure=True);action('capture');wait('qa_overlay_capture_done','true');r=keys('qa_overlay_capture_red','qa_overlay_capture_error');print('Secure synthetic capture:',r,flush=True);check(int(r['qa_overlay_capture_red'])==0,'no-root mode does not expose secure page pixels');check(visible('空叶悬浮球'),'secure capture returns ball to visible state')
 action('page',hide=True,secure=False);action('low');action('keyboard');time.sleep(.7);check('mInputShown=true' in adb('shell','dumpsys','input_method'),'synthetic password field opens keyboard');check(visible('空叶悬浮球') and win('空叶悬浮球')[1]==2100,'enhanced ball remains present over keyboard area');adb('shell','input','keyevent','4');action('restore')
 action('fallback');check(keys('qa_overlay_fallback_type').get('qa_overlay_fallback_type')=='2038','loss of accessibility host falls back to ordinary layer');check(layer('空叶悬浮球')==2032 and visible('空叶悬浮球'),'accessibility host reconnect restores enhanced layer')
 action('cycle');wait('qa_overlay_cycles_done','true');check(layer('空叶悬浮球')==2032 and visible('空叶悬浮球'),'20 live host changes finish with working enhanced ball');dump=adb('shell','dumpsys','window','windows');check(sum('空叶悬浮球' in b.splitlines()[0] for b in re.split(r'(?=  Window #)',dump))==1,'host switching leaves exactly one ball window');check(keys(*original.keys())==original,'all original ball and recording settings preserved')

def tools():
 action('page',hide=True,secure=False)
 for name,title in [('menu','空叶工具面板'),('browser','空叶浏览器'),('longshot','空叶长截图:手动长截图工具栏')]:
  action(name);print('Open tool:',name,flush=True)
  if name=='menu':
   dump=adb('shell','dumpsys','window','windows');matches=[b for b in re.split(r'(?=  Window #)',dump) if 'ty=2032' in b and any(k in b.splitlines()[0] for k in ['空叶工具面板'])];check(bool(matches),'menu uses accessibility overlay')
  else:check(layer(title)==2032 and visible(title),'tool uses enhanced layer: '+name)
  action('close_tools')
 action('region');time.sleep(1.2);check(layer('空叶截图窗口')==2032,'region selector uses enhanced layer');action('close_tools')

def receiver(command='snapshot'):
 adb('shell','am','broadcast','-n','com.kongye.floatball/.OverlayCheckReceiver','--es','command',command);time.sleep(.18)
def nodes():
 receiver();return json.loads(keys('qa_overlay_nodes').get('qa_overlay_nodes','[]'))
def own_node(label):
 return next(n for n in nodes() if n['label']==label)
def own_ready(label,seconds=20):
 end=time.monotonic()+seconds
 while time.monotonic()<end:
  try:return own_node(label)
  except StopIteration:time.sleep(.2)
 raise AssertionError('Missing own overlay control '+label)
def own_click(label):
 n=own_ready(label);v.tap((n['x1']+n['x2'])/2,(n['y1']+n['y2'])/2)

def recording():
 action('close_tools');action('record');own_ready('开始录屏');check(layer('空叶录制准备:录屏选区')==2032,'video preparation uses enhanced overlay');own_click('录制水印');check(layer('空叶录制水印画布')==2032,'watermark editor uses enhanced overlay');n=own_node('录制水印编辑画布');x1,y1,x2,y2=[n[k] for k in ['x1','y1','x2','y2']];v.tap(x1+(x2-x1)*.5,y1+(y2-y1)*.4);adb('shell','input','text','1234');check('mInputShown=true' in adb('shell','dumpsys','input_method'),'watermark input keyboard works in accessibility overlay');own_click('水印颜色红色');own_click('完成水印编辑');own_ready('开始录屏');check(layer('空叶录制准备:录制水印位置预览')==2032,'watermark returns to enhanced preparation');own_click('开始录屏');m['click']('立即开始');wait('recording_state','recording');check(layer('空叶录制控制')==2032 and not visible('空叶悬浮球'),'recording control replaces ball at enhanced layer');x1,y1,x2,y2=win('空叶录制控制');v.swipe(x1+71,y1+71,250,750,600);check(win('空叶录制控制')[:2]!=[x1,y1],'enhanced recording control remains movable');m['m']['record_click']('退出录制');wait('recording_state','idle');own_ready('开始录屏');check(visible('空叶悬浮球') and layer('空叶悬浮球')==2032,'cancel recording restores enhanced ball');m['close_setup']()
 action('gif_record');own_ready('开始录制 GIF');check(layer('空叶录制准备:GIF 录制选区')==2032,'GIF preparation uses enhanced overlay');own_click('开始录制 GIF');m['click']('立即开始');wait('recording_state','recording');m['m']['record_click']('停止录制');wait('recording_state','idle');m['ready']('GIF 动态预览',90);check('RecordingResultActivity' in a.focus(),'enhanced GIF recording still opens result');m['click']('关闭录制结果');action('close_tools');action('restore')

if __name__=='__main__':{'core':core,'tools':tools,'recording':recording}[sys.argv[1]]()
