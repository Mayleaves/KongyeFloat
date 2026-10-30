"""1.19 acceptance: owned controls and generated document; no phone media export."""
import runpy,pathlib,time,json,re,xml.etree.ElementTree as ET
q=runpy.run_path(str(pathlib.Path(__file__).with_name('v118-device.py')))
adb,check,values,prefs,main,nodes,matching,node,click,ready,find,icons=[q[n] for n in ['adb','check','values','prefs','main','nodes','matching','node','click','ready','find','icons']]
def feature(name):adb('shell','am','start','-W','-n','com.kongye.floatball/.DiagnosticActivity','--es','feature',name);time.sleep(.6)
def overlays():q['receiver']('snapshot');return json.loads(values().get('qa_overlay_nodes','[]'))
def onode(label):
 found=[n for n in overlays() if n['label']==label]
 if not found:raise AssertionError('Missing overlay '+label)
 return sorted(found,key=lambda n:not n['clickable'])[0]
def oready(label):
 end=time.monotonic()+6
 while time.monotonic()<end:
  if any(n['label']==label for n in overlays()):return
  time.sleep(.15)
 raise AssertionError('Missing '+label)
def oc(label):
 n=onode(label);q['tap']((n['x1']+n['x2'])/2,(n['y1']+n['y2'])/2)
def ui():
 adb('shell','uiautomator','dump','/data/local/tmp/kongye-v119-ui.xml');tree=ET.fromstring(adb('exec-out','cat','/data/local/tmp/kongye-v119-ui.xml'));return [n for n in tree.iter('node') if n.get('package')=='com.kongye.floatball']
fields=['theme','x','y','home_tools','size'];initial=values();original={k:initial.get(k) for k in fields};pathlib.Path('.tools/v119-state.json').write_text(json.dumps(original),encoding='utf-8')
try:
 prefs({'theme':'light'});main()
 if matching('关闭悬浮球'):click('关闭悬浮球');ready('稍作休息。')
 for tab in ['首页','悬浮球','设置']:
  click(tab);check(len([n for n in matching(tab) if abs(n.get('text_sp',0)-16)<.01])==1,tab+' heading uses 16sp')
 click('首页');click('管理首页工具');u=ui();check(any(n.get('text')=='首页工具' for n in u),'home tool chooser opens in unified dialog');adb('shell','input','keyevent','4');time.sleep(.2)
 click('设置');find('检查更新');click('检查更新');end=time.monotonic()+24;u=ui()
 while any(n.get('content-desc')=='更新状态：正在检查最新版本…' for n in u) and time.monotonic()<end:time.sleep(.6);u=ui()
 status=next((n.get('content-desc') for n in u if n.get('content-desc','').startswith('更新状态：')),'');print('Update check:',status,flush=True);check(bool(status) and '正在检查' not in status,'in-app release check completes with a result or retry state');check('com.kongye.floatball/.UpdateActivity' in adb('shell','dumpsys','activity','activities'),'update flow stays in app');check(not any(n.get('text') in ['前往下载','发布页'] for n in u),'update does not offer browser download navigation');adb('shell','input','keyevent','4');time.sleep(.2)
 feature('qa_v119');check(values().get('qa_v119_result')=='25 checks passed','generated highlighter, verifier and dialog checks pass')
 feature('qa_edit_tools');check(values().get('qa_editor_pass')=='true','existing image editor transforms and selection regression pass');print('Editor checks:',values().get('qa_editor_checks'),flush=True)
 feature('qa_v119_document');oready('编辑图片');check(any(i['name']=='edit' for i in onode('编辑图片')['icons']),'result edit button uses new pencil');oc('编辑图片');oready('高亮');oc('高亮');check(onode('高亮')['selected'],'highlight is a selectable toolbar mode');canvas=onode('图片编辑画布');x=canvas['x1']+(canvas['x2']-canvas['x1'])*.25;y=canvas['y1']+(canvas['y2']-canvas['y1'])*.35;adb('shell','input','swipe',str(round(x)),str(round(y)),str(round(x+(canvas['x2']-canvas['x1'])*.45)),str(round(y)), '450');time.sleep(.2);check(any(n['label']=='删除选中标注' for n in overlays()),'UI highlight creates selectable annotation');oc('应用图片编辑');check('highlight,' in values().get('last_edit_marks',''),'applied image contains highlight');oc('返回截图结果');oc('关闭截图结果')
 feature('qa_record_gif');oready('GIF 录制工具栏');n=overlays();labels=[x['label'] for x in n];check('录制全屏' not in labels,'GIF removes full-screen shortcut');order=['录制分辨率','录制水印','开始录制 GIF','切换到录屏','录制时隐藏图标'];xs=[onode(label)['x1'] for label in order];check(xs==sorted(xs),'GIF toolbar order puts watermark second');oc('录制水印');oready('完成水印编辑');oc('完成水印编辑');oready('GIF 录制工具栏');check(any(n['label']=='录制水印' for n in overlays()),'watermark editor returns to GIF setup');oc('录制分辨率');oready('录制分辨率直接选择');check(any(n['label']=='240p 上限' for n in overlays()),'recording choices use inline app card');adb('shell','input','keyevent','4');time.sleep(.2);oc('切换到录屏');oready('录屏工具栏');check(any(n['label']=='录制声音' for n in overlays()),'video recording retains audio selector');oc('关闭录制准备')
finally:
 try:
  for _ in range(4):
   labels={n['label'] for n in overlays()}
   if '关闭录制准备' in labels:oc('关闭录制准备')
   elif '返回截图结果' in labels:oc('返回截图结果')
   elif '关闭截图结果' in labels:oc('关闭截图结果')
   else:break
 except Exception:pass
 prefs(original);main();click('首页');check(all(values().get(k)==v for k,v in original.items()),'original preferences restored')
 if matching('稍作休息。'):click('启动悬浮球');ready('随时待命。')
print('TOTAL:',check.__globals__['checks'],'UI checks passed; no media export.',flush=True)
