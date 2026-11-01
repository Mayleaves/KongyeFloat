"""v1.29 acceptance: only owned controls/DOM, generated documents; no phone media export."""
import os,runpy,time,json,pathlib
os.environ['KONGYE_ADB_SERIAL']='525114fb';q=runpy.run_path('scripts/v118-device.py');adb,receiver,values,main=[q[k] for k in ['adb','receiver','values','main']];initial=values();saved={k:initial.get(k) for k in ['theme','size_percent','edge_percent','opacity','x','y','custom_art','fade','snap','haptic','enhanced_overlay','home_tools','gesture_tap','gesture_double','gesture_long','gesture_up','gesture_down']};extra={k:initial.get(k) for k in ['menu_tools','menu_order','ocr_keep_lines','record_resolution','gif_resolution','gif_fps','record_bitrate','record_hide','recording_mode','last_record_uri','last_gif_path']};pathlib.Path('.tools/v129-state.json').write_text(json.dumps(saved),encoding='utf-8');pathlib.Path('.tools/v129-extra.json').write_text(json.dumps(extra),encoding='utf-8');count=0

def check(ok,label):
 global count
 assert ok,label
 count+=1;print('PASS:',label,flush=True)
def overlay():receiver('nodes');return json.loads(values()['qa_overlay_nodes'])
def press(label):
 n=next(n for n in overlay() if n['label']==label);q['tap']((n['x1']+n['x2'])/2,(n['y1']+n['y2'])/2);time.sleep(.25)
def snap():receiver('qa_v129_snapshot');return values()
try:
 main();time.sleep(.4);receiver('qa_v126_snapshot')
 if any(n['label']=='启动悬浮球' for n in q['nodes']()):q['click']('启动悬浮球');time.sleep(.7)
 receiver('qa_v122_screen');press('置顶截图结果');n=next(n for n in overlay() if n['label']=='拖动截图结果窗口');yy=(n['y1']+n['y2'])//2;adb('shell','input','swipe',str(n['x2']-12),str(yy),'1',str(yy),'650');time.sleep(.7);press('展开置顶截图标签');v=snap();check(v['qa_v129_shell_alpha']=='0' and float(v['qa_v129_card_alpha'])>0,'no separate outer shell while inner floating card appears');time.sleep(.6);check(snap()['qa_v129_shell_alpha']=='0','expanded floating window has no outer wrapper frame');receiver('qa_v122_close')
 receiver('qa_v126_close_menu');receiver('qa_v126_menu');press('管理快捷菜单工具');v=snap();print('BADGES',v.get('qa_v129_badges'),v.get('qa_v129_clipped'),flush=True);check(v['qa_v129_badges']=='19' and v['qa_v129_clipped']=='0','all nineteen shortcut selection badges have full unclipped bounds');press('通知栏');press('通知栏');check(snap()['qa_v129_clipped']=='0','badge bounds remain complete after selection rebuild');receiver('qa_v126_close_menu')
 # Home uses source fade/scale, keeps actual tile/grid and page, and persists actual drag order.
 q['prefs']({'home_tools':json.dumps(['region','longshot','record','gif_record','recent_apps','favorite_apps','notes','browser'])});main();time.sleep(.4);before=snap();nodes=q['nodes']();a=next(n for n in nodes if n['label']=='区域截图');b=next(n for n in nodes if n['label']=='长截图');x,y=(a['x1']+a['x2'])//2,(a['y1']+a['y2'])//2;tx,ty=(b['x1']+b['x2'])//2,(b['y1']+b['y2'])//2;adb('shell','input','motionevent','DOWN',str(x),str(y));time.sleep(.9);v=snap();check(abs(float(v['qa_v129_drag_alpha'])-.28)<.03 and abs(float(v['qa_v129_drag_scale'])-.92)<.03,'home drag source uses same fade and scale as shortcut drag')
 for i in range(1,9):adb('shell','input','motionevent','MOVE',str(round(x+(tx-x)*i/8)),str(round(y+(ty-y)*i/8)))
 adb('shell','input','motionevent','UP',str(tx),str(ty));time.sleep(.5);v=snap();check(json.loads(values()['home_tools'])[:2]==['longshot','region'],'home drag persists actual new order');check(all(v[k]==before[k] for k in ['qa_v129_home_root','qa_v129_home_grid','qa_v129_home_tile']),'home drag retains page, grid and source tile identities');check(float(v['qa_v129_drag_alpha'])==1 and float(v['qa_v129_drag_scale'])==1,'home drag source restores normal appearance after drop')
 # Open actual About through Settings, with no diagnostic activity between app and desktop.
 q['click']('设置');time.sleep(.8);q['find']('关于空叶');q['click']('关于空叶');time.sleep(1.2);v=snap();identity=v['qa_v129_page'];check(v['qa_v129_visible']=='AboutActivity','actual About page is registered as visible app page');receiver('qa_v129_sleep_stage');time.sleep(3);receiver('qa_v129_about');time.sleep(.2);a=json.loads(json.loads(values()['qa_v129_about']));print('ABOUT_TIMING',a,flush=True);check(a['z']=='2.6s' and bool(a['flies']) and all(t==2600 for t in a['flies']),'fireflies and potted-plant Z each use 2.6 second blink duration')
 receiver('qa_v126_gesture_toggle');v=snap();check(float(v['qa_v129_about_y'])<0,'open-app gesture slides About upward to exit whole app');time.sleep(.9);check(snap()['qa_v129_visible']=='','About app task leaves foreground after exit transition');receiver('qa_v126_gesture_toggle');time.sleep(.15);v=snap();check(v['qa_v129_visible']=='AboutActivity' and v['qa_v129_page']==identity and float(v['qa_v129_about_y'])>0,'next gesture restores same About page from bottom');time.sleep(.7);check(abs(float(snap()['qa_v129_about_y']))<1,'About entrance finishes aligned');adb('shell','input','keyevent','4');time.sleep(.5);check(snap()['qa_v129_visible']=='MainActivity','normal About Back still returns to main app')
finally:
 receiver('qa_v122_close');receiver('qa_v126_close_menu');receiver('qa_v126_close_page');receiver('qa_v126_restore',extra);q['prefs'](saved);main()
print(count,'checks passed; no phone media exported.',flush=True)
