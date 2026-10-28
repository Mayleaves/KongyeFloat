"""PHK110 acceptance. Own app controls/synthetic page only; no media export."""
import runpy,pathlib,time,json,sys
q=runpy.run_path(str(pathlib.Path(__file__).with_name('v21-device.py')));a,v,adb,keys=q['a'],q['v'],q['adb'],q['keys'];ui=q['m']['m'];click=ui['click'];own=ui['own'];d=476/160

def check(ok,text):a.check(ok,text)
def find(label):
 for _ in range(8):
  if any(n.get('text')==label or n.get('content-desc')==label for n in own()):click(label);return
  v.swipe(620,2200,620,950,350)
 raise AssertionError('Missing own label '+label)
def home():a.main();click('首页')
def manager():find('管理首页工具')
def tools():
 home();manager();find('自动滚动长截图');click('确定');check(len(json.loads(keys('home_tools')['home_tools']))==9,'home supports nine selected tools');home();check(any(n.get('content-desc')=='自动滚动长截图' for n in own()),'ninth tool renders in incomplete final row');manager();find('区域截图');click('取消');check('region' in json.loads(keys('home_tools')['home_tools']),'cancel does not change home selection');adb('shell','am','force-stop','com.kongye.floatball');home();check(len(json.loads(keys('home_tools')['home_tools']))==9,'home selection persists after app restart');manager();click('恢复默认');check(len(json.loads(keys('home_tools')['home_tools']))==8,'restore default tools works');click('手势');check(not any(n.get('text')=='管理边缘快捷' for n in own()),'gesture page removes management row');click('上划');texts=[n.get('text') for n in own()];check('管理边缘快捷' not in texts and '停止录屏' not in texts,'gesture picker removes both unwanted actions');click('打开空叶软件页面');check(keys('gesture_up')['gesture_up']=='settings','up gesture maps correctly after choice filtering');home();adb('shell','input','keyevent','3');x1,y1,x2,y2=q['win']('空叶悬浮球');x=(x1+x2)/2;y=(y1+y2)/2;v.swipe(x,y,x,y-180,140);time.sleep(.7);check('MainActivity' in a.focus(),'up swipe opens app');adb('shell','input','keyevent','3')

def wait_count(n,seconds=35):
 end=time.monotonic()+seconds
 while time.monotonic()<end:
  value=int(keys('manual_count').get('manual_count','0'))
  if value>=n:return value
  time.sleep(.3)
 raise AssertionError('Not enough captured parts: '+str(keys('manual_count','manual_auto_running','manual_auto_added')))
def gesture_handle(label,target):
 n=q['own_node'](label);x=(n['x1']+n['x2'])/2;y=(n['y1']+n['y2'])/2;v.swipe(x,y,x,target,550)
def capture():
 # Plain DiagnosticActivity displays only the generated long scroll list.
 v.f.diagnostic();v.f.diagnostic('longshot');q['own_ready']('自动滚动长截图');check(not any(n['label']=='编辑截图顺序' for n in q['nodes']()),'live toolbar replaces reorder with automatic scroll');gesture_handle('调整上边',720);gesture_handle('调整下边',2230);q['own_click']('添加截图');wait_count(1);q['own_click']('自动滚动长截图');count=wait_count(3);q['own_click']('停止自动滚动');time.sleep(.4);state=keys('manual_count','manual_auto_running','manual_auto_added','qa_scroll_y');print('Auto scroll result:',state,flush=True);check(state['manual_auto_running']=='false' and int(state['manual_auto_added'])>=2,'auto scrolling can stop with added cropped parts');check(int(state.get('qa_scroll_y','0'))>0,'actual generated page was scrolled');q['own_click']('长截图缩略预览');q['own_ready']('调整截图顺序和删除');q['own_click']('调整截图顺序和删除');q['own_ready']('删除第1张');before=keys('manual_order')['manual_order'];n=q['own_node']('截图第1张，长按拖动排序');t=q['own_node']('截图第2张，长按拖动排序');x=(n['x1']+n['x2'])/2;y=(n['y1']+n['y2'])/2;xx=(t['x1']+t['x2'])/2;yy=(t['y1']+t['y2'])/2;adb('shell','input','motionevent','DOWN',str(round(x)),str(round(y)));time.sleep(.8)
 for step in range(1,13):
  adb('shell','input','motionevent','MOVE',str(round(x+(xx-x)*step/12)),str(round(y+(yy-y)*step/12)));time.sleep(.025)
 adb('shell','input','motionevent','UP',str(round(xx)),str(round(yy)));time.sleep(.5);check(keys('manual_order')['manual_order']!=before,'auto-captured parts remain drag reorderable');old=int(keys('manual_count')['manual_count']);q['own_click']('删除第1张');check(int(keys('manual_count')['manual_count'])==old-1,'auto-captured parts remain individually deletable');q['own_click']('确认拼接');q['own_ready']('截图图片，点击预览');check(any(n['label']=='分享图片' for n in q['nodes']()),'automatic long image enters editable share result');check(not any(n['label']=='放大选择文字' for n in q['nodes']()),'long image has no OCR');q['own_click']('关闭截图结果');check(q['visible']('空叶悬浮球'),'closing result restores ball')

if __name__=='__main__':{'tools':tools,'capture':capture}[sys.argv[1]]()
