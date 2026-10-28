"""Sony v1.7 acceptance. No screenshot/media export; native controls and phone-side receipts only."""
import importlib.util,pathlib,time,sys
sys.stdout.reconfigure(encoding='utf-8',line_buffering=True)
s=importlib.util.spec_from_file_location('v',pathlib.Path(__file__).with_name('v14-device.py'));v=importlib.util.module_from_spec(s);s.loader.exec_module(v)
a,adb,prefs=v.a,v.adb,v.prefs

def click(label):
    names={'展开录制控制','暂停录制','继续录制','停止录制','编辑录制画面'}
    if label in names:
        x1,y1,x2,y2=v.window_bounds('空叶录制控制');expanded=x2-x1>300
        if label=='展开录制控制':dx,dy=(110,115) if expanded else (27,27)
        elif label in ('暂停录制','继续录制'):dx,dy=106,30
        elif label=='停止录制':dx,dy=32,87
        else:dx,dy=32,169
        v.tap(x1+round(dx*2.625),y1+round(dy*2.625));return
    v.click(label)

def has(desc):return any(n.get('content-desc')==desc for n in a.tree().iter('node'))
def ready(label,timeout=65):
    until=time.monotonic()+timeout
    while time.monotonic()<until:
        if any(n.get('content-desc')==label or n.get('text')==label for n in a.tree().iter('node')):return
        time.sleep(.4)
    raise AssertionError('release result missing: '+label)
def keys(*names):return prefs(names)
def wait(key,test,seconds=35):return v.wait(key,test,seconds)
def prepare(gif=False,audio='无声',watermark=False,tone=False,hide=False):
    v.f.diagnostic('qa_record_gif' if gif else 'qa_record_video_tone' if tone else 'qa_record_video');time.sleep(.3)
    if not gif:
        click('录制声音');click(audio);click('录制时隐藏图标');click('隐藏图标，通过通知控制' if hide else '显示录制控制图标')
    # Exercise all four native edge handles; selected rectangle stays in the generated color pattern.
    for label,dx,dy in [('调整录制左边',120,0),('调整录制右边',-180,0),('调整录制上边',0,300),('调整录制下边',0,-400)]:
        x1,y1,x2,y2=v.window_bounds('空叶录制准备:'+label);x,y=(x1+x2)//2,(y1+y2)//2;v.swipe(x,y,x+dx,y+dy)
    print('Selection:',keys('record_setup_left','record_setup_top','record_setup_right','record_setup_bottom','record_setup_width','record_setup_height'))
    if watermark:
        click('录制水印');root=a.tree();n=next(n for n in root.iter('node') if n.get('class')=='android.widget.EditText');x1,y1,x2,y2=v.bounds(n);v.tap((x1+x2)/2,(y1+y2)/2);adb('shell','input','keycombination','113','29');adb('shell','input','text','1234');adb('shell','input','keyevent','62');time.sleep(.5);a.check(next(n for n in a.tree().iter('node') if n.get('class')=='android.widget.EditText').get('text','').strip()=='1234','watermark text accepts keyboard input');click('确定')
    click('开始录制 GIF' if gif else '开始录屏');root=a.tree();allow=next((n for n in root.iter('node') if n.get('resource-id','').endswith(('/permission_allow_foreground_only_button','/permission_allow_button'))),None)
    if allow is not None:
        xx1,yy1,xx2,yy2=v.bounds(allow);v.tap((xx1+xx2)/2,(yy1+yy2)/2)
    click('立即开始');wait('recording_state',lambda x:x=='recording');time.sleep(1.3)
def stop():
    if (lambda b:b[2]-b[0]<=300)(v.window_bounds('空叶录制控制')):click('展开录制控制')
    click('停止录制');wait('recording_state',lambda x:x=='idle');wait('record_result_kind',lambda x:x in ('video','gif'));time.sleep(.7)
def video():
    prepare(watermark=True);click('展开录制控制');click('暂停录制');wait('recording_state',lambda x:x=='paused');time.sleep(2.2);click('继续录制');wait('recording_state',lambda x:x=='recording');time.sleep(1.5);click('停止录制');wait('recording_state',lambda x:x=='idle');time.sleep(1.5)
    root=a.tree();a.check({'保存录制','分享录制','转为 GIF'}.issubset({n.get('content-desc') for n in root.iter('node')}),'recording finishes in preview with three actions');a.check(not any(n.get('text')=='文字' or n.get('content-desc')=='放大选择文字' for n in root.iter('node')),'recording preview has no OCR');values=keys('last_record_active_ms','last_record_wall_ms','last_record_private');a.check(int(values['last_record_wall_ms'])-int(values['last_record_active_ms'])>=2000,'pause time removed from recording timeline');a.check(values['last_record_private']=='true','recording stays private until explicit save')
    v.f.diagnostic('qa_inspect_record');time.sleep(1);values=keys('qa_record_pixel_0','qa_record_pixel_1','qa_record_pixel_2','qa_record_pixel_3','qa_record_frame_error','qa_video','qa_audio','qa_video_width','qa_video_height','last_record_width','last_record_height');print('On-phone frame inspection:',values)
    expected=[(255,0,0),(0,255,0),(0,0,255),(255,255,0)]
    for i,rgb in enumerate(expected):
        color=int(values['qa_record_pixel_'+str(i)]);actual=((color>>16)&255,(color>>8)&255,color&255);a.check(max(abs(x-y) for x,y in zip(rgb,actual))<55,'recorded crop orientation quadrant '+str(i))
    a.check(values['qa_audio']=='false' and values['qa_video']=='true','silent recording has video and no audio track');a.check(values['qa_video_width']==values['last_record_width'] and values['qa_video_height']==values['last_record_height'],'encoded size matches selected aspect/resolution')

def edit_field(desc,value):
    x1,y1,x2,y2=v.bounds(v.node(desc));v.tap((x1+x2)/2,(y1+y2)/2);adb('shell','input','keycombination','113','29');adb('shell','input','text',str(value));time.sleep(.2)
def result_tools():
    v.f.diagnostic('qa_show_last_record');time.sleep(.8);a.check(has('保存录制') and has('分享录制') and has('转为 GIF'),'video preview exposes save/share/GIF tools')
    before=keys('last_record_saved_uri').get('last_record_saved_uri','');click('分享录制');a.check('ChooserActivity' in a.focus() or 'ResolverActivity' in a.focus(),'video opens system share chooser');a.back();a.check(keys('last_record_saved_uri').get('last_record_saved_uri','')==before,'sharing video does not save to gallery');click('保存录制');wait('last_record_saved_uri',lambda value:value and value!=before);a.check(keys('last_record_saved_uri')['last_record_saved_uri'].startswith('content://media/'),'explicit video save publishes gallery file')
    prior=keys('last_gif_path').get('last_gif_path','');click('转为 GIF');edit_field('GIF 持续秒数',2);click('转换');wait('last_gif_path',lambda value:value and value!=prior,90);wait('record_result_kind',lambda value:value=='gif',90);a.check(has('GIF 动态预览'),'converted GIF opens animated preview');saved=keys('last_record_saved_uri')['last_record_saved_uri'];click('分享录制');a.check('ChooserActivity' in a.focus() or 'ResolverActivity' in a.focus(),'GIF opens share chooser');a.back();a.check(keys('last_share_uri')['last_share_uri'].endswith('.gif') and keys('last_record_saved_uri')['last_record_saved_uri']==saved,'GIF share uses temporary GIF URI without gallery save');click('保存录制');wait('last_record_saved_uri',lambda value:value!=saved);click('关闭录制结果')
def gif_record():
    prior=keys('last_gif_path').get('last_gif_path','');prepare(gif=True);stop();wait('last_gif_path',lambda value:value and value!=prior,90);wait('record_result_kind',lambda value:value=='gif',90);a.check(has('GIF 动态预览'),'direct GIF recording finishes as animated GIF');a.check(not has('放大选择文字'),'GIF result has no OCR');click('关闭录制结果')
def appearance():
    a.main();click('外观');before=keys('size','edge_gap');old_size=int(before.get('size','48'));old_gap=int(before.get('edge_gap','0'))
    def find_slider(desc):
        for _ in range(8):
            root=a.tree()
            if any(n.get('content-desc')==desc for n in root.iter('node')):return v.bounds(v.node(desc))
            v.swipe(540,2040,540,650,350)
        raise AssertionError('slider missing: '+desc)
    def set_slider(desc,value,lo,hi,key):
        x1,y1,x2,y2=find_slider(desc);target=x1+42+(x2-x1-84)*(value-lo)/(hi-lo)
        for _ in range(12):
            v.tap(target,(y1+y2)/2);actual=int(keys(key)[key])
            if actual==value:return
            target+=(value-actual)*(x2-x1-84)/(hi-lo)
        raise AssertionError('slider did not settle: '+desc)
    set_slider('悬浮球大小',100,36,100,'size');x1,y1,x2,y2=v.window_bounds('空叶悬浮球');a.check(x2-x1==263,'ball can reach 100dp on Sony');set_slider('悬浮球大小',old_size,36,100,'size');set_slider('边框距离',12,0,48,'edge_gap');x1,y1,x2,y2=v.window_bounds('空叶悬浮球');a.check(x1==32 or 1080-x2==32,'edge gap applies to attached side');set_slider('边框距离',old_gap,0,48,'edge_gap');a.check(int(keys('size')['size'])==old_size and int(keys('edge_gap')['edge_gap'])==old_gap,'appearance checks restore original settings')
def app_rules():
    a.main()
    def rule(label):
        v.f.diagnostic('qa_rules_self');time.sleep(.7);root=a.tree();entry=next(n.get('content-desc') for n in root.iter('node') if n.get('content-desc','').startswith('应用悬浮球状态：'));click(entry);click(label);a.back();a.main();time.sleep(.8)
    rule('隐身，可以操作');wait('qa_ball_app_mode',lambda value:value=='hidden');a.check(keys('qa_ball_touchable')['qa_ball_touchable']=='true' and float(keys('qa_ball_alpha')['qa_ball_alpha'])==0,'per-app hidden ball stays touchable at alpha zero');x1,y1,x2,y2=v.window_bounds('空叶悬浮球');before=keys('qa_touch_time').get('qa_touch_time');v.tap((x1+x2)/2,(y1+y2)/2);after=keys('qa_touch_time','qa_touch_event');a.check(after.get('qa_touch_time')!=before and after.get('qa_touch_event')=='UP','invisible ball receives a complete native touch');adb('shell','input','keyevent','3');wait('qa_ball_app_mode',lambda value:value=='normal');a.check(keys('qa_ball_foreground')['qa_ball_foreground']!='com.kongye.floatball','rule clears automatically in another foreground app');a.main();wait('qa_ball_app_mode',lambda value:value=='hidden')
    rule('禁用');wait('qa_ball_app_mode',lambda value:value=='disabled');a.check(keys('qa_ball_touchable')['qa_ball_touchable']=='false','per-app disabled ball is not touchable');rule('正常显示，可以操作');wait('qa_ball_app_mode',lambda value:value=='normal');a.check(keys('qa_ball_touchable')['qa_ball_touchable']=='true' and float(keys('qa_ball_alpha')['qa_ball_alpha'])>0,'normal per-app mode restores visible usable ball');a.main()
def editor():
    v.f.diagnostic('screenshot');time.sleep(.7);a.check(not has('放大选择文字'),'whole screenshot does not start OCR');click('编辑图片');x1,y1,x2,y2=v.bounds(v.node('图片编辑画布'));a.check(y2-y1>2520*.5 and x2-x1>900,'image editor uses most of the display');click('收起或展开编辑工具');xx1,yy1,xx2,yy2=v.bounds(v.node('图片编辑画布'));a.check(yy2-yy1>y2-y1,'collapsing tools expands image canvas');click('收起或展开编辑工具');click('应用编辑');click('返回截图结果');click('关闭截图结果');v.f.diagnostic('region');click('识别并查看');wait('last_ocr_state',lambda value:value in ('done','failed'));a.check(has('放大选择文字'),'region screenshot retains OCR tools');click('关闭截图结果')

def audio():
    for label,mode,hidden in [('手机内部声音','internal',True),('麦克风','microphone',False),('内部声音 + 麦克风','both',False)]:
        prepare(audio=label,tone=True,hide=hidden,watermark=True)
        if hidden:
            missing=False
            try:v.window_bounds('空叶录制控制')
            except AssertionError:missing=True
            a.check(missing,'hidden recording control is absent');v.f.diagnostic('record_stop');wait('recording_state',lambda value:value=='idle')
        else:
            if mode=='both':
                click('展开录制控制');click('暂停录制');time.sleep(1.3);click('继续录制');click('编辑录制画面');v.swipe(450,900,650,1050,450);v.tap(270,181)
            stop()
        v.f.diagnostic('qa_inspect_record');time.sleep(1.2);values=keys('qa_audio','qa_audio_peak','qa_duration_us','qa_error','last_record_audio','qa_record_watermark_white','qa_audio_duration_us','qa_video_duration_us');print('Audio inspection:',mode,values);a.check(values.get('qa_error','')=='' and values.get('qa_audio')=='true' and values.get('last_record_audio')==mode,'real AAC audio track: '+mode)
        a.check(abs(int(values['qa_audio_duration_us'])-int(values['qa_video_duration_us']))<1000000,'audio/video timelines stay aligned: '+mode)
        if mode in ('internal','both'):a.check(int(values.get('qa_audio_peak','0'))>100,'generated test tone is present: '+mode)
        a.check(int(values.get('qa_record_watermark_white','0'))>10,'watermark is encoded in lower-right video pixels')
    # Restore the newly tested recording options through their own UI.
    v.f.diagnostic('qa_record_video');click('录制声音');click('无声');click('录制时隐藏图标');click('显示录制控制图标');click('录制水印');n=next(n for n in a.tree().iter('node') if n.get('class')=='android.widget.EditText');x1,y1,x2,y2=v.bounds(n);v.tap((x1+x2)/2,(y1+y2)/2);adb('shell','input','keycombination','113','29');adb('shell','input','keyevent','67');click('确定');x1,y1,x2,y2=v.window_bounds('空叶录制准备:关闭录制准备');v.tap((x1+x2)/2,(y1+y2)/2);a.main()

def release():
    def tile(label):
        a.main()
        for _ in range(7):
            root=a.tree()
            if any(n.get('text')==label for n in root.iter('node')):click(label);return
            v.swipe(540,2050,540,650,350)
        raise AssertionError('main tool not found: '+label)
    a.main();original=v.window_bounds('空叶悬浮球');tile('录屏');a.check(has('录制分辨率') and has('录制水印') and has('录制声音'),'release region recording toolbar exposes options');click('切换到 GIF 录制');a.check(has('开始录制 GIF'),'release switches into GIF selection');click('切换到录屏');click('录制声音');click('无声');click('录制时隐藏图标');click('显示录制控制图标');click('开始录屏');click('立即开始');time.sleep(4.2);dump=adb('shell','dumpsys','window','windows');block=next(b for b in __import__('re').split(r'(?=  Window #)',dump) if '空叶录制控制' in b);a.check('NOT_FOCUSABLE' in block,'recording controls preserve underlying app keyboard focus');click('展开录制控制');click('暂停录制');time.sleep(1.2);click('继续录制');time.sleep(.8);click('停止录制');ready('保存录制');a.check(has('分享录制') and has('转为 GIF') and not has('放大选择文字'),'release video preview has correct tools and no OCR');click('分享录制');a.check('ChooserActivity' in a.focus() or 'ResolverActivity' in a.focus(),'release video shares through system chooser');a.back();click('关闭录制结果')
    tile('录制 GIF');a.check(has('开始录制 GIF') and has('切换到录屏'),'release has standalone GIF tool');click('开始录制 GIF');click('立即开始');time.sleep(4.2);click('展开录制控制');click('停止录制');ready('GIF 动态预览',90);a.check(has('保存录制') and has('分享录制') and not has('放大选择文字'),'release direct GIF preview is usable');click('关闭录制结果')
    tile('区域截图');release_tail(original)

def release_tail(original=None):
    # Resume the region selection after the recorder/GIF release checks.
    if original is None:original=[954,1087,1080,1213]
    ready('识别并查看');click('识别并查看');ready('编辑图片');a.check(has('放大选择文字'),'release region retains OCR');click('编辑图片');x1,y1,x2,y2=v.bounds(v.node('图片编辑画布'));a.check(y2-y1>1260,'release editor has large image viewport');click('添加文字');v.tap((x1+x2)/2,(y1+y2)/2);adb('shell','input','text','1234');time.sleep(.4);ime=adb('shell','dumpsys','input_method');a.check('mInputShown=true' in ime or 'mIsInputViewShown=true' in ime,'release text IME opens without leaving large editor');a.check(any(n.get('text')=='编辑图片' for n in a.tree().iter('node')),'editor title remains visible above keyboard');x1,y1,x2,y2=v.bounds(v.node('图片编辑画布'));v.tap(x1+12,y1+12);click('应用图片编辑');click('返回截图结果');click('关闭截图结果');a.main();a.check(v.window_bounds('空叶悬浮球')==original,'release keeps current ball position');pkg=adb('shell','dumpsys','package','com.kongye.floatball');a.check('versionName=1.7.0' in pkg and 'DEBUGGABLE' not in pkg,'Sony runs 1.7.0 release');pid=adb('shell','pidof','com.kongye.floatball').strip();a.check('FATAL EXCEPTION' not in adb('shell','logcat','-b','crash','-d','--pid='+pid),'release process has no crash')

if __name__=='__main__':{'release':release,'release_tail':release_tail,'audio':audio,'video':video,'result':result_tools,'gif':gif_record,'appearance':appearance,'apps':app_rules,'editor':editor}[sys.argv[1]]()
