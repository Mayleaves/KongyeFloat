"""Sony 1.10 acceptance. Own controls and synthetic state only; no media export."""
import runpy,pathlib,time,sys,json,re
m=runpy.run_path(str(pathlib.Path(__file__).with_name('v19-device.py')));v,a,adb,click,keys,own,node,win,ready=m['v'],m['a'],m['adb'],m['click'],m['keys'],m['own'],m['node'],m['win'],m['ready'];d=2.625
def close_setup():m['close_setup']()
def missing(title):
    try:win(title);return False
    except AssertionError:return True
def editor_done(gif):
    click('完成水印编辑');ready('开始录制 GIF' if gif else '开始录屏');a.check(missing('空叶录制水印画布'),'watermark confirmation removes editor');a.check(keys('record_setup_kind')['record_setup_kind']==('gif' if gif else 'video'),'watermark confirmation returns to same recording mode')
def watermark():
    for gif in [False,True]:
        v.f.diagnostic('qa_record_gif' if gif else 'qa_record_video');click('录制水印');editor_done(gif);a.check(missing('空叶录制准备:录制水印位置预览'),'untouched watermark remains empty')
        click('录制水印');click('水印颜色红色');editor_done(gif);a.check(missing('空叶录制准备:录制水印位置预览'),'palette-only confirmation returns without empty object')
        click('录制水印');x1,y1,x2,y2=v.bounds(node('录制水印编辑画布'));v.tap(x1+(x2-x1)*.35,y1+(y2-y1)*.4);adb('shell','input','text','1234');click('水印颜色红色');editor_done(gif);a.check(not missing('空叶录制准备:录制水印位置预览'),'edited watermark survives confirmation')
        close_setup();v.f.diagnostic('qa_record_gif' if gif else 'qa_record_video');click('录制水印');x1,y1,x2,y2=v.bounds(node('录制水印编辑画布'));v.tap(x1+(x2-x1)*.35,y1+(y2-y1)*.4);adb('shell','input','text','1234');click('水印颜色黄色');click('删除选中标注');editor_done(gif);a.check(missing('空叶录制准备:录制水印位置预览'),'delete-all confirmation returns with no watermark');close_setup()
def cancel():
    for gif in [False,True]:
        v.f.diagnostic('qa_record_gif' if gif else 'qa_record_video');click('录制时隐藏图标');click('显示录制控制图标');x1,y1,x2,y2=win('空叶录制准备:调整录制左边');v.swipe((x1+x2)/2,(y1+y2)/2,(x1+x2)/2+120,(y1+y2)/2,400);before=keys('record_setup_kind','record_setup_left','record_setup_top','record_setup_right','record_setup_bottom','record_setup_width','record_setup_height');start='开始录制 GIF' if gif else '开始录屏'
        click(start);click('立即开始');m['record_click']('展开录制控制');print('First cancel phase:',keys('recording_state'));m['record_click']('退出录制');m['m']['q'].wait('recording_state',lambda t:t=='idle');ready(start);a.check(keys(*before.keys())==before,'cancel before/during start restores mode/crop/resolution');a.check('RecordingResultActivity' not in a.focus(),'cancel returns preparation rather than result')
        click(start);click('立即开始');m['m']['q'].wait('recording_state',lambda t:t=='recording');time.sleep(1);m['record_click']('退出录制');m['m']['q'].wait('recording_state',lambda t:t=='idle');ready(start);a.check(keys(*before.keys())==before,'cancel active recording restores current preparation');a.check(missing('空叶录制控制'),'cancel removes recording control');close_setup()
def page_state():
    v.f.diagnostic('qa_browser_state');raw=keys('qa_browser_test_state').get('qa_browser_test_state','null');value=json.loads(raw);return json.loads(value) if isinstance(value,str) else value
def browser():
    a.main();v.f.diagnostic('browser');ready('置顶浏览器');v.f.diagnostic('qa_browser_page');time.sleep(.5);v.f.diagnostic('qa_browser_scroll');time.sleep(.3);before=page_state();print('Browser initial synthetic page:',before);a.check(before[0]==37 and before[1]>=500,'synthetic page has live state before window changes')
    click('置顶浏览器');ready('取消置顶浏览器');pinned=win('空叶浏览器');a.check(keys('qa_browser_pinned')['qa_browser_pinned']=='true' and pinned[2]-pinned[0]<1080,'browser pin uses bounded floating window');click('收起浏览器');ready('展开浏览器');folded=win('空叶浏览器');print('Folded browser:',folded);a.check(folded[3]-folded[1]<300,'browser folds into header only')
    touch_before=keys('qa_touch_time').get('qa_touch_time');x1,y1,x2,y2=win('空叶悬浮球');v.tap((x1+x2)/2,(y1+y2)/2);a.check(keys('qa_touch_time').get('qa_touch_time')!=touch_before,'pinned collapsed browser lets outside touches reach ball')
    x1,y1,x2,y2=v.bounds(node('拖动浏览器窗口'));v.swipe((x1+x2)/2,(y1+y2)/2,(x1+x2)/2,(y1+y2)/2+360,650);moved=win('空叶浏览器');a.check(moved[1]!=folded[1],'pinned browser title can move window');click('展开浏览器');ready('收起浏览器');after=page_state();print('Browser restored synthetic page:',after);a.check(after[0]==37 and after[1]>=500,'pin/fold/move preserves page script and scroll state');click('取消置顶浏览器');a.check(keys('qa_browser_pinned')['qa_browser_pinned']=='false','browser can unpin');click('搜索引擎：必应');click('关闭浏览器');a.check(missing('空叶浏览器'),'browser closes cleanly after pin and fold')
def restore():
    state=json.loads(pathlib.Path('.tools/v20-state.json').read_text(encoding='utf-8'));v.f.diagnostic('qa_record_video');click('录制分辨率');click(state.get('record_resolution','720')+'p 上限');click('录制声音');click({'silent':'无声','internal':'手机内部声音','microphone':'麦克风','both':'内部声音 + 麦克风'}[state.get('recording_mode','silent')]);click('录制时隐藏图标');click('隐藏图标，通过通知控制' if state.get('record_hide')=='true' else '显示录制控制图标');close_setup();a.main();current=keys('size','edge_gap','x','y','gesture_tap','gesture_up','gesture_down','recording_mode','record_hide','record_resolution','gif_resolution','browser_engine');a.check(all(current.get(k)==state.get(k) for k in current),'original app settings remain unchanged')
if __name__=='__main__':{'watermark':watermark,'cancel':cancel,'browser':browser,'restore':restore}[sys.argv[1]]()
