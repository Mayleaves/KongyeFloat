"""Formal Ace pointer anchoring checks using own synthetic UI only."""
import runpy,pathlib,time
m=runpy.run_path(str(pathlib.Path(__file__).with_name('v24-device.py')));q,v,adb,a,ui=m['q'],m['v'],m['adb'],m['a'],m['ui'];check=m['check']

def down(x,y):adb('shell','input','motionevent','DOWN',str(round(x)),str(round(y)));time.sleep(.33)
def move(x,y):adb('shell','input','motionevent','MOVE',str(round(x)),str(round(y)))
def up(x,y):adb('shell','input','motionevent','UP',str(round(x)),str(round(y)))
def close():ui['ready']('关闭截图结果',30);ui['click']('关闭截图结果');m['restore_accessibility']()

a.main();m['restore_accessibility']();before=q['win']('空叶悬浮球');x1,y1,x2,y2=before;ox,oy=(x2-x1)/2,(y2-y1)/2;down(x1+ox,y1+oy);move(550,1100);time.sleep(.65);ball=q['win']('空叶悬浮球');check(abs(ball[0]+ox-550)<=1 and abs(ball[1]+oy-1100)<=1,'release center press stays on ball');check(q['visible']('空叶截图十字'),'release shows independent plus overlay');move(750,1450);time.sleep(.2);up(750,1450);close();check(q['win']('空叶悬浮球')==before,'release screenshot restores original ball position');x1,y1,x2,y2=before;down(x1+20,y1+90);move(600,1300);time.sleep(.65);ball=q['win']('空叶悬浮球');check(abs(ball[0]+20-600)<=1 and abs(ball[1]+90-1300)<=1,'release preserves off-center finger position');move(800,1600);time.sleep(.2);up(800,1600);close();check(q['win']('空叶悬浮球')==before,'release off-center capture restores original position');pkg=adb('shell','dumpsys','package','com.kongye.floatball');check('versionName=1.14.1' in pkg and 'DEBUGGABLE' not in pkg,'Ace runs 1.14.1 non-debug release');pid=adb('shell','pidof','com.kongye.floatball').strip();check('FATAL EXCEPTION' not in adb('shell','logcat','-b','crash','-d','--pid='+pid),'release process has no crash');adb('shell','input','keyevent','3')
