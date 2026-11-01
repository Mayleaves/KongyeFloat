"""Sony A202SO: own-view metadata and generated fixtures; no screen/media export."""
import os,runpy,time,json,pathlib
os.environ['KONGYE_ADB_SERIAL']='HQ629V0361'
q=runpy.run_path('scripts/v118-device.py');adb,receiver,values=[q[k] for k in ['adb','receiver','values']];saved=json.loads(pathlib.Path('.tools/v130-sony-state.json').read_text());count=0

def check(ok,label):
 global count
 assert ok,label
 count+=1;print('PASS:',label,flush=True)
def ui():
 receiver('qa_v1221_ui');return json.loads(values()['qa_v1221_ui'])
def press(nodes,label):
 n=next(n for n in nodes if n['label']==label and n['clickable']);q['tap']((n['x1']+n['x2'])/2,(n['y1']+n['y2'])/2)
def slide(label,end):
 n=max((n for n in q['nodes']() if n['label']==label),key=lambda n:n['y1']);x=n['x1']+(2 if end=='min' else n['width']-2);q['tap'](x,(n['y1']+n['y2'])/2);time.sleep(.3);receiver('qa_v130_ranges');return json.loads(values()['qa_v130_ranges'])
try:
 q['main']();time.sleep(.7);receiver('qa_v123_service_REFRESH');time.sleep(.9);receiver('qa_v130_native');check(values()['qa_v130_native']=='11','numeric version comparison and percentage boundaries pass 11 assertions')
 q['prefs']({'theme':'light','custom_art':'false','snap':'true'});q['main']();time.sleep(.5);q['click']('悬浮球');time.sleep(.7);nodes=q['nodes']();look=next(n for n in nodes if n['label']=='外观与位置设置组');title=next(n for n in nodes if n['label']=='外观与位置');gestures=next(n for n in nodes if n['label']=='手势操作')
 check(title['y2']<=look['y1'],'appearance title is outside and above the appearance card')
 check(title['text_sp']==gestures['text_sp'] and title['text_color']==gestures['text_color'],'appearance title matches gesture section font size and colour')
 r=slide('大小','min');check(r['size']==30 and values()['size_percent']=='30','actual size slider persists 30 percent minimum')
 r=slide('大小','max');check(r['size']==500 and values()['size_percent']=='500','actual size slider retains 500 percent maximum')
 q['prefs']({'size_percent':'100'});q['main']();q['click']('悬浮球');time.sleep(.7);r=slide('边框距离','max');check(r['edge']==150 and values()['edge_percent']=='150','actual edge slider persists 150 percent maximum')
 check(abs(r['preview_x']+r['preview_width']*.5)<=1,'150 percent preview has an inward gap of half the visible ball width')
 if 'ball_width' in r:
  # For a right-snapped ball, physical screen gap is half the ball width.
  width=int(adb('shell','wm','size').split(':')[-1].strip().split('x')[0]);gap=width-r['ball_x']-r['ball_width']
  check(abs(gap-r['ball_width']*.5)<=2 or abs(r['ball_x']-r['ball_width']*.5)<=2,'actual overlay uses the same 150 percent inward gap as preview')
 r=slide('边框距离','min');check(r['edge']==30 and values()['edge_percent']=='30','existing 30 percent edge minimum remains available')
 q['click']('设置');time.sleep(.6);nodes=q['nodes']();base=next(n for n in nodes if n['label']=='悬浮基础');theme=next(n for n in nodes if n['label']=='界面主题');enhanced=next(n for n in nodes if n['label']=='增强悬浮层')
 check(base['y2']<=enhanced['y1'],'floating basics heading appears above enhanced overlay')
 check(base['text_sp']==theme['text_sp'] and base['text_color']==theme['text_color'],'floating basics heading matches interface theme section style')
 for origin in ['设置','悬浮球']:
  if origin=='悬浮球':q['click'](origin);time.sleep(.7)
  receiver('qa_v130_home_motion');time.sleep(.9);frames=json.loads(values()['qa_v130_motion']);inter=[f for f in frames if 0<f['page_alpha']<1]
  check(bool(inter) and all(f['page_x']<0 and f['plant_x']<0 for f in inter),origin+' to home plant and content enter from the same left direction')
  check(all(abs(f['plant_x']-f['page_x'])<.001 and abs(f['plant_alpha']-f['page_alpha'])<.001 and abs(f['plant_scale']-f['page_scale'])<.001 for f in frames),origin+' to home plant container follows the content animation clock')
  last=frames[-1];check(last['plant_attached'] and abs(last['anchor']-last['header_height'])<1 and abs(last['plant_x'])<.001,origin+' to home ends with plant perched at the card edge')
 for mode in ['available','current','ahead']:
  receiver('qa_v130_update_'+mode);time.sleep(.5);labels=[n['label'] for n in ui()]
  if mode=='available':check('🎉 发现新版本' in labels and '立即下载' in labels and '✓ 已是最新版本' not in labels,'newer release overrides a stale comparison=0 and offers update');press(ui(),'稍后')
  elif mode=='current':check('✓ 已是最新版本' in labels and '立即下载' not in labels,'equal installed/public version is current');press(ui(),'我知道了')
  else:check('高于公开版' in labels and '更新状态：当前安装版本高于公开正式版本。' in labels and '✓ 已是最新版本' not in labels,'local test build ahead of public release has a distinct status');press(ui(),'我知道了')
  time.sleep(.4)
 # Read-only real network request from this Sony with an old installed-version argument.
 receiver('qa_v130_network_old');end=time.monotonic()+25
 while time.monotonic()<end:
  network=values().get('qa_v130_network','')
  if network:break
  time.sleep(.5)
 pathlib.Path('.tools/v130-sony-network.json').write_text(network,encoding='utf-8')
 check(network.startswith('{'),'Sony can fetch the actual public GitHub release')
 n=json.loads(network);check(n['comparison']>0 and n['downloadable'],'actual GitHub release offers an APK for older version 1.25.1')
 pid=adb('shell','pidof','com.kongye.floatball').strip().split()[0];check('FATAL EXCEPTION' not in adb('logcat','-d','--pid='+pid,'-t','100'),'Sony current app process has no fatal exception')
finally:
 try:
  if 'UpdateActivity' in (values().get('qa_v1221_ui','')):adb('shell','input','keyevent','4')
 except Exception:pass
 q['prefs'](saved);original=json.loads(pathlib.Path('.tools/v130-sony-running.json').read_text());receiver('qa_v123_snapshot')
 if original['running']=='false' and values().get('qa_v123_running')=='true':receiver('qa_v123_service_STOP');time.sleep(.9)
 q['main']()
print(count,'checks passed on Sony; original appearance settings restored, no phone media exported.',flush=True)
