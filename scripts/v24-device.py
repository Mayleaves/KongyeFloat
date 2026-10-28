"""Ace regression: own UI, own generated content; media never exported."""
import runpy,pathlib,json,time,re,sys
m=runpy.run_path(str(pathlib.Path(__file__).with_name('v23-device.py')));q,a,v,adb,keys,ui=m['q'],m['a'],m['v'],m['adb'],m['keys'],m['ui'];click=ui['click'];own=ui['own'];state=json.loads(pathlib.Path('.tools/v24-state.json').read_text(encoding='utf-8'));d=476/160

def check(ok,label):a.check(ok,label)
def receiver(command,tools=None):
 args=['shell','am','broadcast','-n','com.kongye.floatball/.OverlayCheckReceiver','--es','command',command]
 if tools is not None:args+=['--es','tools',','.join(tools)]
 adb(*args);time.sleep(.2)
def home_tools(ids):receiver('home',ids);a.main();click('首页')
def node(label):return next(n for n in own() if n.get('content-desc')==label or n.get('text')==label)
def drag(from_label,to_label):
 x1,y1,x2,y2=v.bounds(node(from_label));tx1,ty1,tx2,ty2=v.bounds(node(to_label));x,y=(x1+x2)/2,(y1+y2)/2;tx,ty=(tx1+tx2)/2,(ty1+ty2)/2;adb('shell','input','motionevent','DOWN',str(round(x)),str(round(y)));time.sleep(.75)
 for step in range(1,13):adb('shell','input','motionevent','MOVE',str(round(x+(tx-x)*step/12)),str(round(y+(ty-y)*step/12)));time.sleep(.03)
 adb('shell','input','motionevent','UP',str(round(tx)),str(round(ty)));time.sleep(.7)
def restore_accessibility():
 current=adb('shell','settings','get','secure','enabled_accessibility_services');service='com.kongye.floatball/com.kongye.floatball.AssistService';values=[] if current in ('','null') else current.split(':')
 if service not in values:adb('shell','settings','put','secure','enabled_accessibility_services',':'.join(values+[service]));time.sleep(1)

def layout():
 for ids in [['region','longshot','auto_longshot'],['region','longshot','auto_longshot','screenshot','record']]:
  home_tools(ids);box=v.bounds(node('首页工具列表'));height=box[3]-box[1];check(abs(height-round((((len(ids)+3)//4)*110+16)*d))<5,'home rows stay compact with '+str(len(ids))+' tools');first=v.bounds(node('区域截图'));second=v.bounds(node('长截图'));third=v.bounds(node('自动滚动长截图'));check(abs((second[0]-first[0])-(third[0]-second[0]))<=1,'partial row keeps equal columns');
 drag('录屏','区域截图');order=json.loads(keys('home_tools')['home_tools']);check(order==['record','region','longshot','auto_longshot','screenshot'],'long press drag reorders across rows');adb('shell','input','keyevent','3');a.main();click('首页');check(json.loads(keys('home_tools')['home_tools'])==order,'reopening preserves reordered tools');click('外观');m['find']('检查更新');check('更新检查功能待接入，当前未发起网络请求。' in [n.get('text') for n in own()],'update entry clearly remains placeholder');click('知道了');m['find']('GitHub');check('GitHub 地址待配置，后续再接入仓库入口。' in [n.get('text') for n in own()],'GitHub entry clearly remains placeholder');click('知道了');home_tools(json.loads(state['home_tools']));restore_accessibility()

def cross():
 restore_accessibility();v.f.diagnostic('qa_record_pattern');x1,y1,x2,y2=q['win']('空叶悬浮球');x,y=(x1+x2)//2,(y1+y2)//2;adb('shell','input','motionevent','DOWN',str(x),str(y));time.sleep(.33);adb('shell','input','motionevent','MOVE','550','1100');time.sleep(.65);receiver('cross');p=keys('qa_cross_ball_x','qa_cross_ball_y','qa_cross_x','qa_cross_y');print('Cross geometry:',p,flush=True);gap=round(24*d);check(abs(float(p['qa_cross_x'])-(int(p['qa_cross_ball_x'])-gap))<=1 and abs(float(p['qa_cross_y'])-(int(p['qa_cross_ball_y'])-gap))<=1,'Ace cross is 24dp above and left of actual ball');adb('shell','input','motionevent','UP','550','1100');time.sleep(.8);q['action']('close_tools');args=['shell','am','broadcast','-n','com.kongye.floatball/.OverlayCheckReceiver','--es','command','position','--ei','x',state['x'],'--ei','y',state['y']];adb(*args);time.sleep(.5);check(q['win']('空叶悬浮球')[:2]==[int(state['x']),int(state['y'])],'cross test restores original ball position')

def feedback():
 restore_accessibility();adb('shell','am','start','-W','-f','0x10008000','-n','com.kongye.floatball/.DiagnosticActivity');time.sleep(.7);v.f.diagnostic('region');q['own_ready']('识别并查看');q['own_click']('识别并查看');q['own_ready']('复制文字');
 end=time.monotonic()+25
 while time.monotonic()<end and keys('last_ocr_state').get('last_ocr_state')=='recognizing':time.sleep(.3)
 q['own_click']('复制文字');p=keys('qa_feedback_message','qa_feedback_visible');print('Copy feedback:',p,flush=True);check(p.get('qa_feedback_message')=='文字已复制' and p.get('qa_feedback_visible')=='true','copy feedback is visible above screenshot result');check(q['layer']('空叶操作反馈')==2032,'feedback uses enhanced layer');q['own_click']('关闭截图结果');v.f.diagnostic('qa_v24_fixture');ui['ready']('保存录制');click('保存录制');time.sleep(.2);p=keys('qa_feedback_message','qa_feedback_visible','last_record_saved_uri');print('Save feedback:',p,flush=True);check(p.get('qa_feedback_message','').startswith('GIF 已保存') and p.get('qa_feedback_visible')=='true','explicit GIF save reports visible success');check(bool(p.get('last_record_saved_uri')),'GIF save succeeded before success feedback');receiver('cleanup_saved_fixture');click('关闭录制结果');restore_accessibility()

if __name__=='__main__':{'layout':layout,'cross':cross,'feedback':feedback}[sys.argv[1]]()
