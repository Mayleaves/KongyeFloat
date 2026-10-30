"""1.19.1 owned view metadata; no screenshots, user images or media are exported."""
import runpy,pathlib,json,time
q=runpy.run_path(str(pathlib.Path(__file__).with_name('v118-device.py')))
adb,check,values,prefs,main,nodes,matching,node,click,ready,find,icons=[q[n] for n in ['adb','check','values','prefs','main','nodes','matching','node','click','ready','find','icons']]
fields=['theme','custom_art','x','y'];original={k:values().get(k) for k in fields};pathlib.Path('.tools/v1191-state.json').write_text(json.dumps(original),encoding='utf-8')
try:
 prefs({'theme':'light','custom_art':'false'});main()
 if matching('稍作休息。'):click('启动悬浮球');ready('随时待命。')
 q['receiver']('snapshot');overlay=json.loads(values().get('qa_overlay_nodes','[]'));ball=next(n for n in overlay if n['label']=='空叶悬浮球：点击菜单，双击返回，按住后拖动');check([i['name'] for i in ball['icons']]==['leaf_outline'],'actual default ball uses outlined leaf');check(ball['icons'][0]['color']==int('FFFAF7EF',16)-2**32,'actual default leaf uses reference warm white')
 click('悬浮球');check(icons('悬浮球外观与位置实时预览')==['leaf_outline'],'appearance preview matches actual leaf');check(bool(matching('叶绿')),'new leaf-green default swatch is present');click('设置');check(icons('深色模式')==['sun'],'fixed light theme displays sun');find('GitHub');check(icons('GitHub')==['github'],'settings uses GitHub mark');find('找回悬浮球');check(icons('找回悬浮球')==['recover'],'recovery entry uses aligned arrow glyph')
 prefs({'theme':'dark'});main();click('设置');check(icons('深色模式')==['theme'],'fixed dark theme displays moon')
 adb('shell','am','start','-W','-n','com.kongye.floatball/.DiagnosticActivity','--es','feature','qa_v119');time.sleep(.5);result=values().get('qa_v119_result');print('Native checks:',result,flush=True);check(result=='25 checks passed','native generated checks include system light/sun and system dark/moon')
finally:
 prefs(original);main();click('首页');check(all(values().get(k)==v for k,v in original.items()),'original theme, custom-art mode and position restored')
 if matching('稍作休息。'):click('启动悬浮球');ready('随时待命。')
print('TOTAL:',check.__globals__['checks'],'UI checks passed; no media export.',flush=True)
