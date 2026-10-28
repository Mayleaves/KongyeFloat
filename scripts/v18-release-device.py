"""Final release smoke: only own controls, no debug entry or media exports."""
import runpy,time,re,sys
m=runpy.run_path(str(__file__).replace('v18-release-device.py','v18-device.py'));a,adb,click,v=m['a'],m['adb'],m['click'],m['v']
def tile(label):
    a.main()
    for _ in range(8):
        if any(n.get('text')==label for n in m['own']()):click(label);return
        v.swipe(540,2050,540,650,350)
    raise AssertionError('Missing main tool '+label)
if '--tail' not in sys.argv:
    tile('录屏');a.check('录制帮助' not in adb('shell','dumpsys','window','windows'),'release recording has no help question icon');click('切换到 GIF 录制');a.check(any(n.get('content-desc')=='录制水印' for n in m['own']()),'release GIF toolbar ends with watermark');a.check(any(n.get('content-desc')=='录制时隐藏图标' for n in m['own']()),'release GIF has hide-recording-control option');click('切换到录屏');adb('shell','input','keyevent','3');time.sleep(.3);click('开始录屏');click('立即开始');time.sleep(11.5);click('展开录制控制');click('停止录制');m['q'].ready('播放录屏预览')
blocks=[b for b in re.split(r'(?=  Window #)',adb('shell','dumpsys','window','windows')) if 'MainActivity' in b.splitlines()[0]];a.check(not any('isVisible=true' in b or 'isOnScreen=true' in b for b in blocks),'release result does not show main page behind preview');bx1,by1,bx2,by2=v.bounds(m['desc']('播放录屏预览'));v.tap((bx1+bx2)/2,(by1+by2)/2);time.sleep(1.5);v.tap((bx1+bx2)/2,(by1+by2)/2);nodes=m['own']();clock=next(n.get('text') for n in nodes if re.fullmatch(r'\d\d:\d\d / \d\d:\d\d',n.get('text','')));print('Release player time:',clock);a.check(not clock.startswith('00:00 /'),'release video playback time advances');click('录屏视频预览，点击放大');m['q'].ready('返回录制结果');x1,y1,x2,y2=v.bounds(m['desc']('放大录屏画面，可双指缩放移动'));a.check(x2-x1>900 and y2-y1>1500,'release direct video tap opens large preview');click('返回录制结果');click('关闭录制结果');a.check('MainActivity' not in a.focus(),'release preview close returns to previous app')
m['tasks']();a.check(m['window']('空叶悬浮球')==[954,1087,1080,1213],'final release preserves ball position');pkg=adb('shell','dumpsys','package','com.kongye.floatball');a.check('versionName=1.8.0' in pkg and 'DEBUGGABLE' not in pkg,'Sony runs signed 1.8.0 release');pid=adb('shell','pidof','com.kongye.floatball').strip();a.check('FATAL EXCEPTION' not in adb('shell','logcat','-b','crash','-d','--pid='+pid),'final release process has no crash')
