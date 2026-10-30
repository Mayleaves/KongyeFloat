"""1.18.1 UI refinement acceptance. Own controls only; no screenshot/media export."""
import runpy,pathlib,time,json
q=runpy.run_path(str(pathlib.Path(__file__).with_name('v118-device.py')))
adb,check,values,prefs,main,nodes,matching,node,click,ready,find,icons=[q[n] for n in ['adb','check','values','prefs','main','nodes','matching','node','click','ready','find','icons']]
root=pathlib.Path(__file__).resolve().parents[1]
fields=['theme','home_tools','x','y','size','gesture_tap','gesture_double','gesture_long','gesture_up','gesture_down']
original={k:values().get(k) for k in fields};(root/'.tools/v1181-state.json').write_text(json.dumps(original),encoding='utf-8')
try:
 prefs({'theme':'light','home_tools':json.dumps(['region','longshot','auto_longshot','gif_record','recent_apps','favorite_apps','quick_settings','notes'])});main()
 titles=[n for n in matching('首页') if abs(n.get('text_sp',0)-15)<.01];check(len(titles)==1,'home has 15sp upper-left heading')
 if matching('稍作休息。'):check(icons('启动悬浮球')==['play'],'resting button uses outlined play');click('启动悬浮球')
 ready('随时待命。');check(bool(matching('随时待命。')),'running label ends with full stop');click('关闭悬浮球');ready('稍作休息。');check(bool(matching('稍作休息。')),'resting label ends with full stop');check(icons('启动悬浮球')==['play'],'resting button is play instead of power')
 n=nodes();cols=[next(p for p in n if p['label'].startswith(prefix)) for prefix in ['悬浮窗，','无障碍，','通知，']];chips=[p for p in n if p['label'] in ['✓ 已开启','去开启'] and abs(p.get('text_sp',0)-10)<.01]
 check(len(chips)==3,'permissions keep three compact status chips');check(all(0<c['width']<cols[i]['width']*.8 for i,c in enumerate(chips)),'permission chips wrap their text instead of filling each column');check(len({c['y1'] for c in chips})==1,'permission chips align on one row')
 for title,expected in [('区域截图','region'),('长截图','longshot'),('自动滚动长截图','auto_longshot'),('录制 GIF','gif_record'),('最近软件','recent_apps'),('常用软件','favorite_apps'),('快捷设置','quick_settings')]:check(icons(title)==[expected],title+' uses its requested glyph')
 click('悬浮球');check(len([n for n in matching('悬浮球') if abs(n.get('text_sp',0)-15)<.01])==1,'ball page has 15sp upper-left heading')
 for title,expected in [('单击','gesture_tap'),('双击','gesture_double'),('长按','gesture_hold'),('上划','up'),('下划','down')]:find(title);check(icons(title)==[expected],title+' has a leading gesture icon')
 find('恢复默认手势');restore=node('恢复默认手势');group=node('手势操作设置组');check(group['x1']<=restore['x1'] and restore['x2']<=group['x2'] and group['y1']<=restore['y1'] and restore['y2']<=group['y2'],'restore gestures action is inside gesture card');check(len(matching('恢复默认手势'))==1,'no separate duplicate restore action')
 # Reset a temporary test binding, then restore all original bindings in finally.
 prefs({'gesture_tap':'notes'});main();click('悬浮球');find('恢复默认手势');click('恢复默认手势');check('gesture_tap' not in values(),'in-card restore button resets gesture binding')
 click('设置');check(len([n for n in matching('设置') if abs(n.get('text_sp',0)-16)<.01])==1,'settings upper-left heading increased to 16sp')
finally:
 prefs(original);main();click('首页');check(all(values().get(k)==v for k,v in original.items()),'original tools, gesture bindings and preferences restored')
 if matching('稍作休息。'):click('启动悬浮球');ready('随时待命。')

print('TOTAL:',check.__globals__['checks'],'checks passed; no phone screenshot or media exported.',flush=True)
