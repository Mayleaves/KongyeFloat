"""UI-only acceptance on owned views and a generated image. No phone media export."""
import runpy,pathlib,time,json,os
os.environ['KONGYE_ADB_SERIAL']='525114fb'
q=runpy.run_path(str(pathlib.Path(__file__).with_name('v118-device.py')))
adb,values,prefs,main,nodes,click,receiver,tap=[q[k] for k in ['adb','values','prefs','main','nodes','click','receiver','tap']]
checks=0
def check(ok,label):
 global checks
 assert ok,label
 checks+=1;print('PASS:',label,flush=True)
def overlays():receiver('snapshot');return json.loads(values().get('qa_overlay_nodes','[]'))
def onode(label):return next(n for n in overlays() if n['label']==label)
def oc(label):
 n=onode(label);tap((n['x1']+n['x2'])/2,(n['y1']+n['y2'])/2)
def wait(label,timeout=12):
 end=time.monotonic()+timeout
 while time.monotonic()<end:
  found=[n for n in overlays() if n['label']==label]
  if found:return found[0]
  time.sleep(.2)
 raise AssertionError('Missing '+label)
def feature(name):adb('shell','am','start','-W','-f','0x18000000','-n','com.kongye.floatball/.DiagnosticActivity','--es','feature',name);time.sleep(.6)
original=values();fields=['theme','home_tools'];saved={k:original.get(k) for k in fields}
pathlib.Path('.tools/v122-state.json').write_text(json.dumps(original),encoding='utf-8')
try:
 feature('qa_v121');check(values().get('qa_v121_result','').endswith('checks passed'),'native splash draws geometry and removes all pixels from old status chip')
 prefs({'theme':'light'});main();click('首页');n=nodes()
 check(any(x['label']=='悬浮球运行状态卡片' for x in n),'home botanical status card remains')
 state=next(x for x in n if x['label'] in ['随时待命。','稍作休息。']);check(state['text_color']==(0xFF1E3327-2**32),'status text uses HTML ink')
 power=next(x for x in n if x['label'] in ['关闭悬浮球','启动悬浮球']);check(any(i['name'].startswith('status_') for i in power['icons']),'service uses filled play or pause glyph')
 check(all(x in [z['label'] for z in n] for x in ['管理首页工具','底部悬浮导航']),'home tool management and navigation retained')
 if power['label']=='启动悬浮球':click('启动悬浮球');time.sleep(.5)
 click('设置');check(any(x['label']=='深色模式' for x in nodes()),'theme settings opens without losing controls')
 check(any(x['label']=='调整空叶软件界面的日夜配色方案' for x in nodes()),'theme row has new description')
 receiver('qa_v122_region');wait('OCR 识别文字卡片');end=time.monotonic()+20
 while time.monotonic()<end and values().get('last_ocr_state')=='recognizing':time.sleep(.3)
 n=overlays();labels=[x['label'] for x in n]
 check(all(x in labels for x in ['复制文字','分享文字','翻译文字','追加便签','重新识别文字','放大选择文字']),'OCR card keeps all text actions and adds recognition retry')
 check(all(x in labels for x in ['保存图片','分享图片','编辑图片','截图图片，点击预览','720 × 480 px']),'image card has dimensions and save/share/edit controls')
 oc('收起文字');check(not any(x['label']=='复制文字' for x in overlays()),'text section collapses');oc('展开文字')
 oc('放大选择文字');wait('全选文字');check(any(x['label']=='清空所选文字' for x in overlays()),'discontinuous selection remains reachable');oc('返回截图结果')
 oc('收起图片');check(not any(x['label']=='截图图片，点击预览' for x in overlays()),'image section collapses');oc('展开图片')
 oc('截图图片，点击预览');wait('编辑图片');oc('编辑图片');wait('应用图片编辑');check(any(x['label']=='高亮' for x in overlays()),'preview and complete image editor retained');oc('返回截图结果');oc('返回截图结果')
 oc('置顶截图结果');check(values().get('result_pinned')=='true','result pins');oc('收起全部');check(values().get('result_all_folded')=='true','pinned result folds');oc('展开全部');oc('取消置顶');oc('关闭截图结果')
 receiver('qa_v122_screen');wait('画面预览卡片');check(not any(x['label']=='OCR 识别文字卡片' for x in overlays()),'whole-screen result remains image-only');receiver('qa_v122_close')
 feature('browser');wait('浏览器地址或搜索');n=overlays();check(all(x in [z['label'] for z in n] for x in ['搜索引擎：必应','搜索引擎：Google','浏览器后退','浏览器前进','在外部浏览器打开','前往']),'browser keeps engines, history, external opening and address navigation')
 feature('qa_browser_page');time.sleep(.4);feature('qa_browser_state');check('37' in values().get('qa_browser_test_state',''),'browser renders live local page')
 oc('置顶浏览器');oc('收起浏览器');check(values().get('qa_browser_folded')=='true','browser pins and folds');oc('展开浏览器');feature('qa_browser_state');check('37' in values().get('qa_browser_test_state',''),'same web page survives pin and fold');oc('取消置顶浏览器');oc('关闭浏览器')
 prefs({'theme':'dark'});receiver('qa_v122_region');wait('画面预览卡片');check(onode('保存图片')['clickable'] and onode('保存图片')['icons'][0]['color']==0xFF83CCA8-2**32,'dark result controls use theme accent and remain clickable');receiver('qa_v122_close')
 check(values().get('qa_overlay_error')==original.get('qa_overlay_error'),'no new overlay diagnostic errors')
finally:
 receiver('qa_v122_close');prefs(saved);main();click('首页')
print(str(checks)+' checks passed',flush=True)
