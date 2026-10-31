"""Owned UI and scalar performance measurements only; no phone media export."""
import runpy,pathlib,time,json
q=runpy.run_path(str(pathlib.Path(__file__).with_name('v118-device.py')))
adb,values,prefs,main,nodes,matching,node,click,receiver,tap=[q[k] for k in ['adb','values','prefs','main','nodes','matching','node','click','receiver','tap']]
checks=0
def check(ok,label):
 global checks
 if not ok:raise AssertionError(label)
 checks+=1;print('PASS:',label,flush=True)
def overlays():receiver('snapshot');return json.loads(values().get('qa_overlay_nodes','[]'))
def onode(label):return next(n for n in overlays() if n['label']==label)
def oc(label):
 n=onode(label);tap((n['x1']+n['x2'])/2,(n['y1']+n['y2'])/2)
def wait(label):
 end=time.monotonic()+8
 while time.monotonic()<end:
  if any(n['label']==label for n in overlays()):return onode(label)
  time.sleep(.1)
 raise AssertionError('Missing '+label)
def self_search():
 receiver('qa_apps_query_self');time.sleep(.2)
 end=time.monotonic()+4
 while time.monotonic()<end:
  if onode('应用启动列表')['adapter_count']==1:return
  time.sleep(.1)
 raise AssertionError('Self-package filtering did not complete')
def profile(mode):
 receiver('qa_apps_'+mode);end=time.monotonic()+8
 while time.monotonic()<end:
  result=values()
  if int(result.get('qa_apps_rows_ms','-1'))>=0:break
  time.sleep(.1)
 record={k:int(result.get('qa_apps_'+k,-1)) for k in ['shell_ms','rows_ms','total','visible_rows','cached']}
 check(0<=record['shell_ms']<800,mode+' shell responds without blocking on catalog')
 check(0<=record['rows_ms']<2000,mode+' list is ready promptly')
 check(0<record['visible_rows']<=12 and record['visible_rows']<record['total'],'only visible application rows are bound')
 print('METRICS',mode,json.dumps(record),flush=True);return record
fields=['size_percent','edge_percent','opacity','x','y','theme','custom_art','snap','fade','home_tools'];before=values();original={k:before.get(k) for k in fields};initial_self_star=None
pathlib.Path('.tools/v1211-ui-state.json').write_text(json.dumps(original),encoding='utf-8')
try:
 prefs({'theme':'light','home_tools':'["apps","region","longshot"]'});main();click('首页')
 if matching('稍作休息。'):click('启动悬浮球');time.sleep(1.2)
 current=nodes();plant=next(n for n in current if n['label'].startswith('盆栽：'));card=next(n for n in current if n['label']=='悬浮球运行状态卡片');check(abs(plant['y2']-card['y1'])<=1,'pot bottom touches status-card top')
 cold=profile('cold');check(cold['cached']==0,'cold profile really invalidates catalog');oc('关闭窗口');warm=profile('warm');check(warm['cached']==1,'warm profile reuses catalog')
 self_search();check(onode('应用启动列表')['adapter_count']==1,'search finds own app by package')
 oc('空叶悬浮球');time.sleep(.4);check(not any(n['label']=='应用启动列表' for n in overlays()),'tapping a row launches the app and dismisses panel')
 main();click('首页');click('应用启动');wait('应用启动列表');oc('选择常用软件');wait('管理常用软件');self_search();n=next(n for n in overlays() if n['label'] in ['设为常用：空叶悬浮球','取消常用：空叶悬浮球']);initial_self_star=n['label'].startswith('取消');identity=onode('应用启动列表')['identity'];oc(n['label']);opposite='设为常用：空叶悬浮球' if initial_self_star else '取消常用：空叶悬浮球';check(any(n['label']==opposite for n in overlays()),'favorite can be toggled');check(onode('应用启动列表')['identity']==identity,'favorite toggle keeps list and window');oc(opposite);check(any(n['label']==('取消常用：空叶悬浮球' if initial_self_star else '设为常用：空叶悬浮球') for n in overlays()),'favorite restored through UI');oc('关闭窗口')
 receiver('qa_apps_close_loading');time.sleep(.5);check(not any(n['label']=='应用启动列表' for n in overlays()),'closing during background load does not reopen panel')
 adb('shell','am','start','-W','-f','0x18000000','-n','com.kongye.floatball/.DiagnosticActivity','--es','feature','qa_v1211');time.sleep(.5);check(values().get('qa_v1211_result')=='11 checks passed','phone glyph stroke, hollow scissors and all six pot baselines pass')
finally:
 if initial_self_star is not None:receiver('qa_self_favorite',{'selected':initial_self_star})
 try:
  if any(n['label']=='关闭窗口' for n in overlays()):oc('关闭窗口')
 except Exception:pass
 prefs(original);main();click('首页');check(all(values().get(k)==v for k,v in original.items()),'original tools and appearance restored')
 if matching('稍作休息。'):click('启动悬浮球')
print('TOTAL:',checks,'checks passed; no phone media exported.',flush=True)
