import os,runpy,time,json,pathlib
os.environ['KONGYE_ADB_SERIAL']='525114fb';q=runpy.run_path('scripts/v118-device.py');adb,receiver,values,main=[q[k] for k in ['adb','receiver','values','main']];saved={k:values().get(k) for k in ['theme','size_percent','edge_percent','opacity','x','y','custom_art','fade','snap','haptic','enhanced_overlay','home_tools','gesture_tap','gesture_double','gesture_long','gesture_up','gesture_down']};pathlib.Path('.tools/v1291-state.json').write_text(json.dumps(saved),encoding='utf-8');count=0;down=False

def check(ok,label):
 global count
 assert ok,label
 count+=1;print('PASS:',label,flush=True)
def snap():receiver('qa_v129_snapshot');return values()
try:
 q['prefs']({'home_tools':json.dumps(['region','longshot','record','gif_record','recent_apps','favorite_apps','notes','browser'])});main();time.sleep(.6);before=snap();receiver('qa_v129_drag_shadow');v=values();check(v['qa_v129_tile_no_foreground']=='true' and v['qa_v129_shadow_border_alpha']=='0','idle drag image has fully transparent outer edges and no tile-wide foreground');n=q['nodes']();a=next(n for n in n if n['label']=='长截图');b=next(n for n in n if n['label']=='区域截图');x,y=(a['x1']+a['x2'])//2,(a['y1']+a['y2'])//2;tx,ty=(b['x1']+b['x2'])//2,(b['y1']+b['y2'])//2;adb('shell','input','motionevent','DOWN',str(x),str(y));down=True;time.sleep(.9);receiver('qa_v129_drag_shadow');v=values();check(v['qa_v129_shadow_border_alpha']=='0','held long-press drag image stays transparent around icon and label');n=next(n for n in q['nodes']() if n['label']=='长截图');check(abs(n['alpha']-.28)<.03,'home source retains same faint appearance as quick-menu source')
 for i in range(1,9):adb('shell','input','motionevent','MOVE',str(round(x+(tx-x)*i/8)),str(round(y+(ty-y)*i/8)))
 adb('shell','input','motionevent','UP',str(tx),str(ty));down=False;time.sleep(.5);v=snap();check(json.loads(values()['home_tools'])[:2]==['longshot','region'],'actual home drag ordering still persists');check(all(v[k]==before[k] for k in ['qa_v129_home_root','qa_v129_home_grid','qa_v129_home_tile']),'drag does not recreate page, grid or existing tiles');check(next(n for n in q['nodes']() if n['label']=='长截图')['alpha']==1,'dropped tool restores full opacity')
finally:
 if down:adb('shell','input','motionevent','UP',str(x),str(y));time.sleep(.3)
 q['prefs'](saved);main()
print(count,'checks passed; only own-view pixels sampled in memory, no phone media exported.',flush=True)
