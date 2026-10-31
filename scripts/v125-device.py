"""v1.25.0 own UI/DOM and generated longshot documents; no display capture or media export."""
import os,runpy,time,json,pathlib
os.environ['KONGYE_ADB_SERIAL']='525114fb'
q=runpy.run_path(str(pathlib.Path(__file__).with_name('v118-device.py')))
adb,values,prefs,receiver,main,click,nodes=[q[k] for k in ['adb','values','prefs','receiver','main','click','nodes']]
count=0
saved={k:values().get(k) for k in ['theme','size_percent','edge_percent','opacity','x','y','custom_art','fade','snap','haptic','enhanced_overlay','home_tools','gesture_tap','gesture_double','gesture_long','gesture_up','gesture_down']}
pathlib.Path('.tools/v125-state.json').write_text(json.dumps(saved),encoding='utf-8')
def check(ok,msg):
 global count
 assert ok,msg
 count+=1;print('PASS:',msg,flush=True)
def nav(s):click(s);time.sleep(.8)
def overlay():receiver('nodes');return json.loads(values()['qa_overlay_nodes'])
def press(s):
 n=next(n for n in overlay() if n['label']==s);q['tap']((n['x1']+n['x2'])/2,(n['y1']+n['y2'])/2);time.sleep(.3)
def about(cmd='about'):
 receiver('qa_v125_'+cmd);time.sleep(.15);return json.loads(json.loads(values()['qa_v125_about']))
def open_about():
 adb('shell','am','start','-W','-n','com.kongye.floatball/.DiagnosticActivity','--es','feature','qa_v1221_about');time.sleep(1.2)
try:
 receiver('qa_v125_close');prefs({'theme':'light'});main();nav('悬浮球');receiver('qa_v125_layout');v=values()
 expected='管理边缘快捷|快捷菜单|打开空叶|返回|主页|最近任务|通知栏|快捷设置|锁屏|整屏截图|区域截图|长截图|滚动截图|录屏|录制 GIF|停止录屏|最近软件|常用软件|应用启动|悬浮浏览器|便签|打开文件'
 check(v['qa_v125_quick_order']==expected,'quick function picker has all 22 actions in requested order')
 check(v['qa_v125_haptic_glyph']=='false','haptic row has no leading icon')
 check(int(v['qa_v125_fade_height'])<=84 and v['qa_v125_fade_height']==v['qa_v125_snap_height'],'fade and snap rows reduced to 28dp')
 q['find']('恢复默认手势');check(not any('按住小叶约' in n['label'] for n in nodes()),'bottom capture hint removed, restore gesture action retained')
 nav('首页');receiver('qa_v1231_jump');n=nodes();check(any(any(msg in p['label'] for msg in ['早安！','上午好，','午后阳光','花骨朵','晚风轻拂','夜深了']) for p in n),'plant click shows time-matched apk.html greeting')
 time.sleep(2.3);check(not any(any(msg in p['label'] for msg in ['早安！','上午好，','午后阳光','花骨朵','晚风轻拂','夜深了']) for p in nodes()),'temporary plant greeting disappears')
 receiver('qa_v125_stitch');time.sleep(.3);press('完成截图');n=overlay();check(any(p['label']=='长截图结果未置顶' for p in n) and not any(p['label']=='完成拼接' for p in n),'top-left check directly opens final longshot result with new title')
 check(all(any(p['label']==s for p in n) for s in ['保存图片','分享图片','编辑图片']) and not any('OCR' in p['label'] for p in n),'longshot result retains save/share/edit without OCR')
 receiver('qa_v125_close');receiver('qa_v125_stitch');press('长截图缩略预览');press('调整截图顺序和删除');n=overlay()
 check(any(p['label']=='删除第1张' for p in n) and any('长按拖动排序' in p['label'] for p in n),'thumbnail still opens drag ordering and individual deletion')
 press('删除第2张');check(values()['manual_count']=='2','individual longshot deletion updates capture count')
 # Use owned overlay coordinates for an actual cross-cell drag.
 n=overlay();a=next(p for p in n if p['label']=='截图第1张，长按拖动排序');b=next(p for p in n if p['label']=='截图第2张，长按拖动排序');x,y=(a['x1']+a['x2'])//2,(a['y1']+a['y2'])//2;tx,ty=(b['x1']+b['x2'])//2,(b['y1']+b['y2'])//2
 adb('shell','input','motionevent','DOWN',str(x),str(y));time.sleep(.8)
 for i in range(1,9):adb('shell','input','motionevent','MOVE',str(round(x+(tx-x)*i/8)),str(round(y+(ty-y)*i/8)))
 adb('shell','input','motionevent','UP',str(tx),str(ty));time.sleep(.5);check(values()['manual_order']=='3,1','longshot drag changes image order')
 press('确认拼接');check(any(p['label']=='长截图结果未置顶' for p in overlay()),'confirm reordered/deleted captures opens final result')
 receiver('qa_v125_close');receiver('qa_v122_region');time.sleep(.6);check(any(p['label']=='区域截图结果未置顶' for p in overlay()),'region result title is region screenshot');receiver('qa_v125_close')
 open_about();a=about();check('1.25.0' in a['version'] and a['stages']==6 and a['paths']>0,'supplied offline about page loads real version and six vector stages')
 check(a['notes']==0 and a['statusbar']==0 and abs(a['width']-a['viewport'])<=1 and a['height']==a['viewportHeight'],'about fills app viewport without desktop frame or sample status bar')
 check(a['networkBlocked'] and not a['fileAccess'],'about page works with network/file access blocked')
 a=about('about_next');check(a['name']=='成长','about time selector updates live specimen')
 original=int(a['taps'][1:]);about('about_logo');about('about_logo');a=about('about_logo');check(int(a['taps'][1:])==original+3 and a['leaves']>=15,'about icon tap counter and vector leaf shower work')
 a=about('about_night');check(a['night'],'seven version taps activate supplied night scene')
 about('about_back');time.sleep(.4);check('AboutActivity' not in adb('shell','dumpsys','activity','activities').split('topResumedActivity')[-1].splitlines()[0],'HTML back button leaves about page')
 prefs({'theme':'dark'});open_about();check(about()['night'],'about opens in actual app dark theme');adb('shell','input','keyevent','4')
 check(values().get('home_tools')==saved['home_tools'] and all(values().get(k)==saved[k] for k in ['gesture_tap','gesture_double','gesture_long','gesture_up','gesture_down']),'existing tool order and gesture bindings preserved')
finally:
 receiver('qa_v125_close');prefs(saved);main();nav('首页');receiver('qa_v123_service_REFRESH')
print(str(count)+' checks passed; no phone media exported.',flush=True)
