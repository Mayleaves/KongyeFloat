"""v1.26 UI acceptance, generated documents and app-owned metadata only."""
import os,runpy,time,json,pathlib
os.environ['KONGYE_ADB_SERIAL']='525114fb'
q=runpy.run_path(str(pathlib.Path(__file__).with_name('v118-device.py')))
adb,receiver,values,prefs,main,click,nodes=[q[k] for k in ['adb','receiver','values','prefs','main','click','nodes']]
fields=['theme','size_percent','edge_percent','opacity','x','y','custom_art','fade','snap','haptic','enhanced_overlay','home_tools','gesture_tap','gesture_double','gesture_long','gesture_up','gesture_down']
saved={k:values().get(k) for k in fields};initial=values();extra={k:initial.get(k) for k in ['record_resolution','gif_resolution','gif_fps','record_bitrate','record_hide','recording_mode','menu_tools','translation_terms']};pathlib.Path('.tools/v126-state.json').write_text(json.dumps(saved),encoding='utf-8');pathlib.Path('.tools/v126-extra.json').write_text(json.dumps(extra),encoding='utf-8')
count=0
def check(ok,msg):
 global count
 assert ok,msg
 count+=1;print('PASS:',msg,flush=True)
def overlay():receiver('nodes');return json.loads(values()['qa_overlay_nodes'])
def press(s):
 n=next(n for n in overlay() if n['label']==s);q['tap']((n['x1']+n['x2'])/2,(n['y1']+n['y2'])/2);time.sleep(.25)
def snapshot():receiver('qa_v126_snapshot');return values()
def nav(s):click(s);time.sleep(.75)
def close_page():receiver('qa_v126_close_page');time.sleep(.3)
try:
 receiver('qa_v126_close_page');prefs({'theme':'light'});main();time.sleep(.5);nav('悬浮球');v=snapshot();check(v['qa_v126_fade_height']==v['qa_v126_snap_height'] and v['qa_v126_fade_top']==v['qa_v126_snap_top'] and v['qa_v126_fade_bottom']==v['qa_v126_snap_bottom'],'fade and snap have identical vertical spacing')
 nav('首页');receiver('qa_v126_ocr');receiver('qa_v126_setup_gif');time.sleep(.5)
 tools=[n['label'] for n in overlay() if n['label'] in ['录制分辨率','每秒最大帧数','开始录制 GIF','切换到录屏','更多设置']];check(tools==['录制分辨率','每秒最大帧数','开始录制 GIF','切换到录屏','更多设置'],'GIF toolbar has requested five tools in order')
 press('录制分辨率');n=overlay();check(all(any(x['label']==s+'p 上限' for x in n) for s in ['240','480','720','1080']),'GIF offers 240/480/720/1080p');press('480p 上限');check(snapshot()['qa_v126_resolution']=='480','GIF resolution selection affects live recording spec')
 press('每秒最大帧数');press('15 FPS');check(snapshot()['qa_v126_fps']=='15','GIF 15 FPS selection affects real recording spec');press('更多设置');n=overlay();check(any(x['label']=='水印' for x in n) and any(x['label'].startswith('显示录制悬浮球') for x in n),'GIF more contains watermark and recording ball visibility');press('水印');time.sleep(.3);n=overlay();print('WATERMARK_LABELS', [x['label'] for x in n if '确定' in x['label'] or '完成' in x['label']],flush=True)
 # Cancel/confirm watermark using its owned done control.
 done=next((x['label'] for x in n if '确定' in x['label'] or '完成' in x['label']),None)
 if done:press(done)
 else:adb('shell','input','keyevent','4');time.sleep(.3)
 press('切换到录屏');n=overlay();labels=[x['label'] for x in n];indices=[labels.index(t) for t in ['录制分辨率','比特率','开始录屏，长按设置声音','切换到 GIF 录制','录制水印','是否显示录制悬浮球']];check(indices==sorted(indices),'video toolbar has requested six tools in order')
 press('录制分辨率');n=overlay();check(all(any(x['label']==s+'p 上限' for x in n) for s in ['480','720','1080']) and not any(x['label']=='360p 上限' for x in n),'video resolution options are 480/720/1080p');press('720p 上限');press('比特率');press('4 Mbps');check(snapshot()['qa_v126_bitrate']=='4000000','bitrate selection reaches recording spec');press('比特率');press('录制声音…');press('无声');check(snapshot()['qa_v126_audio']=='silent','audio options remain available despite reorganized toolbar');press('关闭录制准备');time.sleep(.4);main()
 # Generated screenshot result, zoom and edge docking.
 receiver('qa_v122_screen');time.sleep(.3);n=overlay();thumb=next(x for x in n if x['label']=='截图图片，点击预览');check(thumb['height']<900,'result thumbnail stays compact before large preview');press('截图图片，点击预览');n=overlay();check(any(x['label']=='图片预览暗色背景' for x in n) and not any(x['label']=='图片预览' for x in n),'large image is directly on dark canvas without preview card heading');q['tap'](10,130);time.sleep(.3);check(snapshot()['qa_v126_result_mode']=='0','dark background returns directly to compact result')
 press('置顶截图结果');n=overlay();head=next(x for x in n if x['label']=='拖动截图结果窗口');q['tap'](600,2580);time.sleep(.4);check(float(snapshot()['qa_v126_result_alpha'])<.8,'pinned window fades while interacting outside it');n=overlay();head=next(x for x in n if x['label']=='拖动截图结果窗口');start=head['x2']-20;yy=(head['y1']+head['y2'])//2;adb('shell','input','swipe',str(start),str(yy),'1',str(yy),'650');time.sleep(.4);check(any(x['label']=='展开置顶截图标签' for x in overlay()),'half-off-screen pinned result collapses to edge tag');press('展开置顶截图标签');check(any(x['label']=='画面预览卡片' for x in overlay()),'edge tag expands result into screen');receiver('qa_v122_close')
 receiver('qa_v126_auto_fixture');time.sleep(1);check(any('长截图结果' in x['label'] for x in overlay()),'automatic scrolling capture finishes into editable result preview');receiver('qa_v122_close')
 # Translation starts automatically and identifies English without manual translate click.
 receiver('qa_v126_region_text');receiver('qa_v126_translate');time.sleep(1);receiver('qa_v126_translation_snapshot');v=values();check(v['qa_v126_translation_source']=='auto' and v['qa_v126_translation_detected']=='en','translation automatically detects English on entry');check('准备' in v['qa_v126_translation_status'] or '完成' in v['qa_v126_translation_status'],'translation starts automatically after detection');press('交换翻译语言');receiver('qa_v126_translation_snapshot');v=values();check(v['qa_v126_translation_source']=='zh' and v['qa_v126_translation_target']=='en','exchange swaps source and target languages');receiver('qa_v126_name_fixture');time.sleep(.4);receiver('qa_v126_translation_snapshot');check(values()['qa_v126_translation_output']=='Wu Mei','explicit proper-name mapping avoids Wu Wei mistranslation');receiver('qa_v122_close')
 main();nav('首页');receiver('qa_v126_snapshot');v=values();check(set(v['qa_v126_home_catalogue'].split(','))==set(v['qa_v126_menu'].split(',')),'home and default quick menu offer identical full catalogue');check(values().get('home_tools')==saved['home_tools'],'home selection is independent and preserved')
 end=time.monotonic()+80
 while values().get('qa_v126_ocr')=='running' and time.monotonic()<end:time.sleep(1)
 v=values();check(float(v.get('qa_v126_ocr_accuracy','0'))>=.90 and float(v['qa_v126_ocr_accuracy'])>=float(v['qa_v126_ocr_baseline']),'generated OCR measured accuracy '+v.get('qa_v126_ocr_accuracy','0')+' vs old '+v.get('qa_v126_ocr_baseline','0')+'; '+v.get('qa_v126_ocr','missing'))
 # Dynamic about scene: no previous counter until click, random firefly controller exists.
 adb('shell','am','start','-W','-n','com.kongye.floatball/.DiagnosticActivity','--es','feature','qa_v1221_about');time.sleep(1.2);receiver('qa_v126_about_snapshot');time.sleep(.2);a=json.loads(json.loads(values()['qa_v126_about']));check(a['counter']=='0' and a['fireflies']=='function','about counter is hidden until this visit click, fireflies have random fading controller');adb('shell','input','keyevent','4')
finally:
 receiver('qa_v126_close_page');receiver('qa_v122_close');receiver('qa_v126_restore',extra);prefs(saved);main();nav('首页');receiver('qa_v123_service_REFRESH')
print(str(count)+' checks passed; no phone media exported.',flush=True)
