"""Independent menu selection and actual drag ordering on owned overlay."""
import os,runpy,time,json,re,xml.etree.ElementTree as ET,pathlib
os.environ['KONGYE_ADB_SERIAL']='525114fb';q=runpy.run_path(str(pathlib.Path(__file__).with_name('v118-device.py')));adb,receiver,values=q['adb'],q['receiver'],q['values']
extra={k:values().get(k) for k in ['menu_tools','home_tools']};home=extra['home_tools'];count=0
def check(ok,msg):
 global count
 assert ok,msg
 count+=1;print('PASS:',msg,flush=True)
def overlay():receiver('nodes');return json.loads(values()['qa_overlay_nodes'])
def tap(label):
 n=next(n for n in overlay() if n['label']==label);q['tap']((n['x1']+n['x2'])/2,(n['y1']+n['y2'])/2)
def dialogTap(label):
 adb('shell','uiautomator','dump','/data/local/tmp/kongye-v126-menu.xml');tree=ET.fromstring(adb('exec-out','cat','/data/local/tmp/kongye-v126-menu.xml'));found=[n for n in tree.iter('node') if n.get('package')=='com.kongye.floatball' and n.get('text')==label];n=next((n for n in found if n.get('checkable')=='true'),next((n for n in found if n.get('class')=='android.widget.Button'),found[0]));b=list(map(int,re.findall(r'\d+',n.get('bounds'))));q['tap']((b[0]+b[2])/2,(b[1]+b[3])/2)
try:
 q['main']()
 if any(n['label']=='启动悬浮球' and n.get('alpha',1)>0 for n in q['nodes']()):q['click']('启动悬浮球');time.sleep(.8)
 receiver('qa_v126_close_menu');receiver('qa_v126_menu');time.sleep(.3);receiver('qa_v126_snapshot');before=values()['qa_v126_menu'];n=overlay();a=next(n for n in n if n['label']=='返回');b=next(n for n in n if n['label']=='通知栏');x,y=(a['x1']+a['x2'])//2,(a['y1']+a['y2'])//2;tx,ty=(b['x1']+b['x2'])//2,(b['y1']+b['y2'])//2
 adb('shell','input','motionevent','DOWN',str(x),str(y));time.sleep(.8)
 for i in range(1,9):adb('shell','input','motionevent','MOVE',str(round(x+(tx-x)*i/8)),str(round(y+(ty-y)*i/8)))
 adb('shell','input','motionevent','UP',str(tx),str(ty));time.sleep(.6);receiver('qa_v126_snapshot');check(values()['qa_v126_menu'].split(',')[3]=='back','quick menu supports actual long-press drag ordering');check(values()['home_tools']==home,'menu drag does not reorder home tools')
 tap('管理快捷菜单工具');dialogTap('锁屏');dialogTap('确定');time.sleep(.4);receiver('qa_v126_snapshot');check('lock' not in values()['qa_v126_menu'].split(','),'quick menu selection can hide a tool');check(values()['home_tools']==home,'menu selection is independent from home selection')
 receiver('qa_v126_close_menu');receiver('qa_v126_source_fixture');time.sleep(1.3);check(values().get('qa_v126_source_exact')=='true' and values().get('qa_v126_source_bounded')=='true','selected WenKai original is exact and excludes outside text');q['main']();receiver('qa_v126_ocr_font');end=time.monotonic()+60
 while values().get('qa_v126_ocr')=='running' and time.monotonic()<end:time.sleep(.5)
 v=values();print('WENKAI SAMPLE:',v.get('qa_v126_ocr_accuracy'),'OLD:',v.get('qa_v126_ocr_baseline'),v.get('qa_v126_ocr'),flush=True);check(float(v['qa_v126_ocr_accuracy'])>=float(v['qa_v126_ocr_baseline']),'WenKai generated image result is at least as accurate as old recognition')
finally:
 receiver('qa_v126_close_menu');receiver('qa_v126_restore',{'menu_tools':extra['menu_tools']});q['main']()
print(str(count)+' checks passed; no phone media exported.',flush=True)
