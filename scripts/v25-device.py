"""Ace pointer anchoring regression; only generated test content, no media export."""
import runpy,pathlib,json,time,sys
m=runpy.run_path(str(pathlib.Path(__file__).with_name('v24-device.py')));q,v,adb,keys=m['q'],m['v'],m['adb'],m['keys'];check=m['check'];d=476/160

def restore(state):
 adb('shell','am','broadcast','-n','com.kongye.floatball/.OverlayCheckReceiver','--es','command','position','--ei','x',state['x'],'--ei','y',state['y']);time.sleep(.4)
def down(x,y):adb('shell','input','motionevent','DOWN',str(round(x)),str(round(y)));time.sleep(.33)
def move(x,y):adb('shell','input','motionevent','MOVE',str(round(x)),str(round(y)))
def up(x,y):adb('shell','input','motionevent','UP',str(round(x)),str(round(y)))
def receipt():m['receiver']('cross');return keys('qa_cross_ball_x','qa_cross_ball_y','qa_cross_x','qa_cross_y')
def close():q['action']('close_tools')

def run():
 m['a'].main();m['restore_accessibility']();state=keys('size','opacity','edge_gap','x','y','home_tools','gesture_up','gesture_down','gesture_tap');pathlib.Path('.tools/v25-state.json').write_text(json.dumps(state),encoding='utf-8');v.f.diagnostic('qa_record_pattern')
 x1,y1,x2,y2=q['win']('空叶悬浮球');ox,oy=(x2-x1)/2,(y2-y1)/2;down(x1+ox,y1+oy);move(550,1100);time.sleep(.65);p=receipt();print('Center hold geometry:',p,flush=True);check(abs(int(p['qa_cross_ball_x'])+ox-550)<=1 and abs(int(p['qa_cross_ball_y'])+oy-1100)<=1,'finger stays on ball center while drawing');check(abs(float(p['qa_cross_x'])-(int(p['qa_cross_ball_x'])-round(24*d)))<=1 and abs(float(p['qa_cross_y'])-(int(p['qa_cross_ball_y'])-round(24*d)))<=1,'plus stays independently above and left of ball');check(float(p['qa_cross_x'])<550-ox and float(p['qa_cross_y'])<1100-oy,'finger is not on the plus');move(750,1450);time.sleep(.15);p=receipt();check(abs(int(p['qa_cross_ball_x'])+ox-750)<=1 and abs(int(p['qa_cross_ball_y'])+oy-1450)<=1,'second drag preserves same finger anchor');up(750,1450);q['own_ready']('截图图片，点击预览');doc=keys('document_width','document_height');check(doc.get('document_width')=='200' and doc.get('document_height')=='350','plus endpoints produce expected 200 by 350 region');q['own_click']('关闭截图结果');check(q['win']('空叶悬浮球')[:2]==[int(state['x']),int(state['y'])],'completed screenshot restores original position')
 x1,y1,x2,y2=q['win']('空叶悬浮球');down(x1+20,y1+90);move(600,1300);time.sleep(.65);p=receipt();check(abs(int(p['qa_cross_ball_x'])+20-600)<=1 and abs(int(p['qa_cross_ball_y'])+90-1300)<=1,'off-center finger position is preserved');up(600,1300);time.sleep(.7);close();restore(state)
 x1,y1,x2,y2=q['win']('空叶悬浮球');down((x1+x2)/2,(y1+y2)/2);move(550,1100);up(550,1100);time.sleep(.4);check(keys('drag_cross_state').get('drag_cross_state')=='red','release before yellow remains uncaptured');check(not any(n['window']=='空叶截图窗口' for n in q['nodes']()),'red release does not open screenshot selection');restore(state);check(keys(*state.keys())==state,'original appearance, tools and gestures remain unchanged')

if __name__=='__main__':run()
