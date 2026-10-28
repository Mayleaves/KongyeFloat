"""Sony 1.9 acceptance: only own controls/state; no screen or media export."""
import runpy,pathlib,time,sys,re,json,xml.etree.ElementTree as ET
m=runpy.run_path(str(pathlib.Path(__file__).with_name('v18-device.py')));v,a,adb,keys=m['v'],m['a'],m['adb'],m['keys'];d=2.625
def tree():
    for _ in range(2):
        output=adb('shell','uiautomator','dump','/data/local/tmp/kongye-ui.xml')
        if 'dumped to:' in output:return ET.fromstring(adb('exec-out','cat','/data/local/tmp/kongye-ui.xml'))
        time.sleep(.3)
    raise AssertionError('UI dump did not produce fresh XML')
a.tree=tree
def own():return [n for n in tree().iter('node') if n.get('package')=='com.kongye.floatball']
def node(label):return next(n for n in own() if n.get('content-desc')==label or n.get('text')==label)
def click(label):v.click(label)
def find_click(label):
    for _ in range(8):
        if any(n.get('text')==label or n.get('content-desc')==label for n in own()):click(label);return
        v.swipe(540,2050,540,800,400)
    raise AssertionError('Missing scrolled control '+label)
def win(title):
    for block in re.split(r'(?=  Window #)',adb('shell','dumpsys','window','windows')):
        if title not in block.splitlines()[0]:continue
        found=re.search(r'frame=\[(-?\d+),(-?\d+)\]\[(-?\d+),(-?\d+)\]',block)
        if found:return list(map(int,found.groups()))
    raise AssertionError('Missing own window: '+title)
def wm_tap(title):x1,y1,x2,y2=win(title);v.tap((x1+x2)/2,(y1+y2)/2)
def ready(label,seconds=40):
    end=time.monotonic()+seconds
    while time.monotonic()<end:
        if any(n.get('content-desc')==label or n.get('text')==label for n in own()):return
        time.sleep(.2)
    raise AssertionError('Missing control '+label)
def record_click(label):
    p=keys('qa_record_core_x','qa_record_core_y','qa_record_expanded');x1,y1,x2,y2=win('空叶录制控制');cx=int(p['qa_record_core_x']);cy=int(p['qa_record_core_y']);expanded=p['qa_record_expanded']=='true'
    if label=='展开录制控制':x,y=cx+27*d,cy+27*d
    else:
        if not expanded:record_click('展开录制控制');return record_click(label)
        right=cx>50*d
        x,y={'暂停录制':((118 if right else 32)*d,30*d),'继续录制':((118 if right else 32)*d,30*d),'停止录制':((32 if right else 118)*d,86*d),'编辑录制画面':((32 if right else 118)*d,198*d),'退出录制':((118 if right else 32)*d,254*d)}[label]
    v.tap(x1+x,y1+y)
def close_setup():wm_tap('空叶录制准备:关闭录制准备')
def options():
    a.main();v.f.diagnostic('qa_record_video');time.sleep(.3)
    for label,choice,key,expected in [('录制分辨率','480p 上限','record_resolution','480'),('录制声音','麦克风','recording_mode','microphone'),('录制时隐藏图标','显示录制控制图标','record_hide','false')]:
        click(label);a.check(win('空叶录制选项:'+label)[2]>0,'inline choice window '+label);dump=adb('shell','dumpsys','window','windows');block=next(b for b in re.split(r'(?=  Window #)',dump) if '空叶录制准备:录屏选区' in b.splitlines()[0]);a.check('isVisible=true' in block,'selection remains visible during '+label);click(choice);a.check(keys(key)[key]==expected,'inline option applied '+choice)
    click('录制声音');click('无声');close_setup()
def session_watermark():
    v.f.diagnostic('qa_record_video');click('录制水印');ready('录制水印编辑画布');x1,y1,x2,y2=v.bounds(node('录制水印编辑画布'));v.tap(x1+(x2-x1)*.4,y1+(y2-y1)*.35);adb('shell','input','text','1234');click('完成水印编辑');a.check(win('空叶录制准备:录制水印位置预览')[2]>0,'current session keeps watermark preview');a.check(not keys('record_watermark','gif_watermark'),'watermark is not written to saved settings');close_setup();v.f.diagnostic('qa_record_gif');missing=False
    try:win('空叶录制准备:录制水印位置预览')
    except AssertionError:missing=True
    a.check(missing,'next GIF session starts with no watermark');close_setup()
def begin(gif=False):
    v.f.diagnostic('qa_record_gif' if gif else 'qa_record_video');
    if gif:
        click('录制分辨率');a.check(win('空叶录制选项:录制分辨率')[2]>0,'GIF resolution options stay inline');click('240p 上限')
    click('录制时隐藏图标');click('显示录制控制图标');click('开始录制 GIF' if gif else '开始录屏');click('立即开始');m['q'].wait('recording_state',lambda s:s=='recording');time.sleep(.5)
def replacement():
    a.main();before=keys('x','y');begin();a.check(keys('qa_ball_visible','qa_ball_touchable')=={'qa_ball_visible':'false','qa_ball_touchable':'false'},'recording ball replaces normal ball and its touch window');original=win('空叶录制控制');p=keys('qa_record_core_x','qa_record_core_y');x=original[0]+int(p['qa_record_core_x'])+27*d;y=original[1]+int(p['qa_record_core_y'])+27*d;v.swipe(x,y,max(130,x-400),max(240,y-300),650);after=win('空叶录制控制');a.check(after[:2]!=original[:2],'drag beginning on recording icon moves control');record_click('展开录制控制');record_click('暂停录制');a.check(keys('recording_state')['recording_state']=='paused','pause still works after drag');record_click('继续录制');a.check(keys('recording_state')['recording_state']=='recording','resume still works after drag');record_click('退出录制');m['q'].wait('recording_state',lambda s:s=='idle');time.sleep(.5);a.check(keys('qa_ball_visible')['qa_ball_visible']=='true','exit restores ordinary ball');a.check('RecordingResultActivity' not in a.focus(),'exit does not open preview');a.check(keys('x','y')==before,'recording drag preserves original ordinary ball position')
def video_result(gif=False):
    begin(gif);time.sleep(1);record_click('停止录制');m['q'].wait('recording_state',lambda s:s=='idle');ready('GIF 动态预览' if gif else '播放录屏预览',90);n=node('GIF 动态预览' if gif else '录屏视频预览，点击放大');x1,y1,x2,y2=v.bounds(n);print('Media viewport:',gif,[x1,y1,x2,y2]);a.check(x2-x1>900 and y2-y1>1400,'large default media viewport '+str(gif));click('关闭录制结果')
def recovery():
    a.main();before=keys('x','y');v.f.diagnostic('qa_drop_ball');time.sleep(2);a.check(win('空叶悬浮球')[2]>0,'watchdog restores detached overlay');a.main();click('外观');find_click('找回悬浮球');a.check(keys('qa_ball_visible')['qa_ball_visible']=='true','find ball action makes ball visible');x1,y1,x2,y2=win('空叶悬浮球');x,y=(x1+x2)/2,(y1+y2)/2;adb('shell','input','motionevent','DOWN',str(round(x)),str(round(y)));time.sleep(.32);adb('shell','input','motionevent','MOVE',str(int(before['x'])-63),str(int(before['y'])-63));adb('shell','input','motionevent','UP',str(int(before['x'])-63),str(int(before['y'])-63));time.sleep(.7);a.check(keys('x','y')==before,'find-ball check restores user position')
def browser():
    v.f.diagnostic('browser');ready('搜索引擎：必应');x1,y1,x2,y2=v.bounds(next(n for n in own() if n.get('class')=='android.webkit.WebView'));print('Browser viewport:',[x1,y1,x2,y2]);a.check(x2-x1>900 and y2-y1>1700,'browser devotes most of screen to web content');a.check('bing.com' in keys('qa_browser_url').get('qa_browser_url',''),'browser defaults to Bing');click('搜索引擎：Google');a.check(keys('browser_engine')['browser_engine']=='google','Google can be selected inline');click('搜索引擎：必应');a.check(keys('browser_engine')['browser_engine']=='bing','Bing can be selected inline');click('关闭浏览器')
def image_preview():
    v.f.diagnostic('screenshot');time.sleep(1.5);click('截图图片，点击预览');n=next(n for n in own() if n.get('content-desc','').startswith('长截图大图预览'));x1,y1,x2,y2=v.bounds(n);print('Image zoom viewport:',[x1,y1,x2,y2]);a.check(x2-x1>900 and y2-y1>1900,'image preview fills most of screen');click('返回截图结果');click('关闭截图结果')
def hidden_recover():
    a.main();v.f.diagnostic('qa_record_video');click('录制时隐藏图标');click('隐藏图标，通过通知控制');click('开始录屏');click('立即开始');m['q'].wait('recording_state',lambda t:t=='recording');time.sleep(.4);missing=False
    try:win('空叶录制控制')
    except AssertionError:missing=True
    a.check(missing and keys('qa_ball_visible')['qa_ball_visible']=='false','hide option hides replacement and ordinary ball');a.main();click('外观');find_click('找回悬浮球');a.check(win('空叶录制控制')[2]>0,'find during recording restores recording control');record_click('退出录制');m['q'].wait('recording_state',lambda t:t=='idle');v.f.diagnostic('qa_record_video');click('录制时隐藏图标');click('显示录制控制图标');close_setup()
def long_preview():
    v.f.diagnostic('longshot');x1,y1,x2,y2=win('空叶长截图:手动长截图工具栏')
    for count in [1,2]:v.tap((x1+x2)/2,(y1+y2)/2);m['q'].wait('manual_count',lambda t:t==str(count))
    wm_tap('空叶长截图:长截图缩略预览');ready('调整截图顺序和删除');n=next(n for n in own() if n.get('content-desc','').startswith('长截图大图预览'));box=v.bounds(n);print('Long preview:',box);a.check(box[3]-box[1]>2100,'stitch preview has enlarged view area');click('完成拼接');ready('截图图片，点击预览');a.check(not any(n.get('content-desc')=='放大选择文字' for n in own()),'large stitched result remains image-only');click('关闭截图结果')
def restore_options():
    state=json.loads(pathlib.Path('.tools/v19-state.json').read_text(encoding='utf-8'));v.f.diagnostic('qa_record_video');click('录制分辨率');click(state.get('record_resolution','720')+'p 上限');click('录制声音');click('无声');click('录制时隐藏图标');click('显示录制控制图标');close_setup();a.main();current=keys('size','edge_gap','x','y','gesture_tap','gesture_up','gesture_down','recording_mode','record_hide');a.check(all(current.get(k)==state.get(k) for k in current),'test leaves original ball, gestures and recording choices intact')
def negative_edge():
    a.main();click('外观');before=keys('edge_gap','x','y');m['slider']('边框距离',-100,-100,0,'edge_gap');box=win('空叶悬浮球');visible=max(0,min(1080,box[2])-max(0,box[0]));print('Negative edge window:',box,'visible px:',visible);a.check(visible>=30,'negative edge keeps a touchable visible part');m['slider']('边框距离',int(before['edge_gap']),-100,0,'edge_gap');a.check(keys('edge_gap','x','y')==before,'negative edge check restores prior setting and position')
if __name__=='__main__':{'options':options,'watermark':session_watermark,'record':replacement,'video':video_result,'gif':lambda:video_result(True),'recover':recovery,'browser':browser,'image':image_preview,'hidden':hidden_recover,'long':long_preview,'restore':restore_options,'edge':negative_edge}[sys.argv[1]]()
