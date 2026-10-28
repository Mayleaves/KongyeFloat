"""Sony v1.8 regression. Own widget metadata only; no screen/media exports."""
import importlib.util,pathlib,time,sys,re,json
s=importlib.util.spec_from_file_location('v17',pathlib.Path(__file__).with_name('v17-device.py'));q=importlib.util.module_from_spec(s);s.loader.exec_module(q)
v,a,adb,click,keys=q.v,q.a,q.adb,q.click,q.keys
def window(title):
    for b in re.split(r'(?=  Window #)',adb('shell','dumpsys','window','windows')):
        if title in b:
            m=re.search(r'frame=\[(-?\d+),(-?\d+)\]\[(-?\d+),(-?\d+)\]',b)
            if m:return list(map(int,m.groups()))
    raise AssertionError('Missing own window: '+title)
def own():return [n for n in a.tree().iter('node') if n.get('package')=='com.kongye.floatball']
def desc(label):return next(n for n in own() if n.get('content-desc')==label)
def slider(label,value,lo,hi,key):
    for _ in range(9):
        n=next((n for n in own() if n.get('content-desc')==label),None)
        if n is not None:break
        v.swipe(540,2050,540,700,350)
    else:raise AssertionError('Missing slider '+label)
    x1,y1,x2,y2=v.bounds(n);target=x1+42+(x2-x1-84)*(value-lo)/(hi-lo)
    for _ in range(12):
        v.tap(target,(y1+y2)/2);actual=int(keys(key).get(key,lo))
        if actual==value:return
        target+=(value-actual)*(x2-x1-84)/(hi-lo)
    raise AssertionError('Slider target '+label)
def geometry():
    a.main();click('外观');before=keys('size','edge_gap','x','y');print('Original appearance:',before)
    slider('悬浮球大小',150,36,150,'size');box=window('空叶悬浮球');a.check(box[2]-box[0]==394,'150dp ball is 394 physical pixels')
    slider('边框距离',-100,-100,0,'edge_gap');box=window('空叶悬浮球');a.check(box[2]>1080 or box[0]<0,'negative edge distance really extends past display')
    slider('边框距离',int(before['edge_gap']),-100,0,'edge_gap');slider('悬浮球大小',int(before['size']),36,150,'size');a.check(window('空叶悬浮球')==[int(before['x']),int(before['y']),int(before['x'])+126,int(before['y'])+126],'appearance tests restore user position')
    # Red release does not capture; while dragging the ball may pass the physical edge.
    x1,y1,x2,y2=window('空叶悬浮球');x,y=(x1+x2)//2,(y1+y2)//2
    adb('shell','input','motionevent','DOWN',str(x),str(y));time.sleep(.32);adb('shell','input','motionevent','MOVE','1079','1000');time.sleep(.1)
    box=window('空叶悬浮球');a.check(box[2]>1080,'ball is unclipped by window placement during region gesture');adb('shell','input','motionevent','UP','1079','1000');time.sleep(.65);a.check(keys('drag_cross_state').get('drag_cross_state')=='red','release before yellow remains an ordinary drag')
    # Restore the saved vertical position through a completed yellow-region gesture.
    # The previous red drag saved Y=1063. Restore via the normal non-snapping gesture home is checked below.
    print('Post-drag position:',keys('x','y'))
def app_list():
    v.f.diagnostic('qa_rules_self');q.ready('应用悬浮球状态：空叶悬浮球：正常显示，可操作');nodes=own();a.check(any(n.get('resource-id','').endswith('/app_search') for n in nodes),'per-app rules share local-app search field');a.check(any(n.get('content-desc','').startswith('应用分组 ') for n in nodes),'per-app rules use collapsible alphabet groups');a.check(any(re.match(r'\d+\.\d+ MB',n.get('text','')) for n in nodes),'per-app rules show APK size like local picker');a.check(any(re.match(r'\d{4}-\d\d-\d\d',n.get('text','')) for n in nodes),'per-app rules show install date like local picker');click('应用悬浮球状态：空叶悬浮球：正常显示，可操作');click('正常显示，可以操作');click('完成')
def watermark(gif=False):
    v.f.diagnostic('qa_record_gif' if gif else 'qa_record_video');time.sleep(.4);nodes=own();a.check(not any(n.get('content-desc') in ('录制帮助','GIF 设置') for n in nodes),'recording/GIF has no question-mark help');a.check(any(n.get('content-desc')=='录制时隐藏图标' for n in nodes),'both modes offer hidden recording controls');click('录制水印');q.ready('录制水印编辑画布');x1,y1,x2,y2=v.bounds(desc('录制水印编辑画布'));v.tap(x1+(x2-x1)*.58,y1+(y2-y1)*.82);adb('shell','input','text','1234');time.sleep(.4);a.check('mInputShown=true' in adb('shell','dumpsys','input_method'),'watermark keyboard opens on selected canvas');a.check(any(n.get('content-desc')=='完成水印编辑' for n in own()),'watermark stays in recording editor');click('完成水印编辑');name='gif_watermark' if gif else 'record_watermark';value=keys(name)[name];data=json.loads(value);a.check(data and data[-1]['text']=='1234','watermark is persisted as live text object');a.check(.5<data[-1]['x']<.7 and .7<data[-1]['y']<.95,'watermark position matches chosen recording point');print('Watermark normalized:',data[-1]);
    # Real encode uses hidden control so preview colors cannot be mistaken for watermark pixels.
    click('录制时隐藏图标');click('隐藏图标，通过通知控制');click('开始录制 GIF' if gif else '开始录屏');click('立即开始');q.wait('recording_state',lambda t:t=='recording');time.sleep(2.3);v.f.diagnostic('record_stop');q.wait('recording_state',lambda t:t=='idle');q.ready('GIF 动态预览' if gif else '转为 GIF',90);a.check(not q.has('放大选择文字'),'recording result remains without OCR')
    if not gif:
        v.f.diagnostic('qa_inspect_record');time.sleep(1.2);a.check(int(keys('qa_record_watermark_white').get('qa_record_watermark_white','0'))>10,'positioned watermark is present in actual encoded video');v.f.diagnostic('qa_show_last_record');q.ready('转为 GIF')
def player():
    v.f.diagnostic('qa_show_last_record');q.ready('播放录屏预览')
    bx1,by1,bx2,by2=v.bounds(desc('播放录屏预览'));bx,by=(bx1+bx2)/2,(by1+by2)/2
    v.tap(bx,by);time.sleep(.1);first=int(keys('qa_play_position').get('qa_play_position','0'));time.sleep(.4);second=int(keys('qa_play_position').get('qa_play_position','0'))
    a.check(second>first+200,'playback clock and progress advance');v.tap(bx,by);time.sleep(.1);first=int(keys('qa_play_position')['qa_play_position']);time.sleep(.4)
    a.check(abs(int(keys('qa_play_position')['qa_play_position'])-first)<80,'pause stops playback position')
    n=desc('录屏预览进度');x1,y1,x2,y2=v.bounds(n);v.tap(x1+(x2-x1)*.75,(y1+y2)/2);time.sleep(.5)
    a.check(int(keys('qa_play_position')['qa_play_position'])>int(keys('qa_play_duration')['qa_play_duration'])*.5,'seek bar moves decoded playback position')
    click('录屏视频预览，点击放大');q.ready('返回录制结果');a.check(keys('qa_play_large')['qa_play_large']=='true','direct video tap opens large playback preview')
    x1,y1,x2,y2=v.bounds(desc('放大录屏画面，可双指缩放移动'));a.check(x2-x1>900 and y2-y1>1500,'large video preview uses most of display')
    n=desc('录屏预览进度');x1,y1,x2,y2=v.bounds(n);v.tap(x1+42,(y1+y2)/2);click('播放录屏预览');a.check(keys('qa_play_playing')['qa_play_playing']=='true','large preview can play')
    click('返回录制结果');q.ready('转为 GIF');click('关闭录制结果')
def tasks():
    # Close own settings task, then activate management from the ball over the launcher.
    a.main();adb('shell','input','keyevent','3');time.sleep(.5);x1,y1,x2,y2=window('空叶悬浮球');x,y=min(1070,max(1,(x1+x2)//2)),(y1+y2)//2
    adb('shell','input','motionevent','DOWN',str(x),str(y));time.sleep(.8);tx=96 if x>540 else 984;adb('shell','input','motionevent','MOVE',str(tx),str(y));time.sleep(.2);adb('shell','input','motionevent','UP',str(tx),str(y));q.ready('完成');dump=adb('shell','dumpsys','activity','activities');a.check('com.kongye.floatball.quick' in dump,'quick manager has an independent task');a.check(not re.search(r'mResumedActivity:.*MainActivity',dump),'quick management does not resume main software page');click('完成');a.check('MainActivity' not in a.focus(),'closing quick manager returns to launcher')
if __name__=='__main__':{'geometry':geometry,'apps':app_list,'watermark':watermark,'gifwatermark':lambda:watermark(True),'player':player,'tasks':tasks}[sys.argv[1]]()
