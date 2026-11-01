"""Sony regression: shared diagonal plant/card path and all settings modules; no media export."""
import os,runpy,time,json
os.environ['KONGYE_ADB_SERIAL']='HQ629V0361';q=runpy.run_path('scripts/v118-device.py');count=0

def check(ok,label):
 global count
 assert ok,label
 count+=1;print('PASS:',label,flush=True)
def frames(cmd):
 q['receiver']('qa_v131_'+cmd);time.sleep(1.1);return json.loads(q['values']()['qa_v131_'+cmd])
q['main']();time.sleep(.8)
for origin in ['设置','悬浮球']:
 q['click'](origin);time.sleep(.8);f=frames('home');active=[x for x in f if 0<x['page_alpha']<1]
 check(bool(active),origin+' to home includes real intermediate frames')
 check(all(x['header_z']>x['body_z'] for x in f),origin+' plant layer draws above the body throughout animation')
 check(all(x['plant_top']>=0 and x['plant_bottom']<=x['group_height']+.5 for x in f),origin+' entire plant fits within its fading/scaling layer')
 check(all(not x['group_clips'] and not x['header_clips'] for x in f),origin+' plant ancestors allow motion overflow')
 check(all(abs(x['plant_alpha']-x['page_alpha']*x['modules'][0]['alpha'])<.001 for x in f),origin+' plant follows the complete hero fade')
 check(all(abs(x['plant_center_screen']-x['page_anchor_screen'])<=2 for x in f[1:]),origin+' plant screen position follows the card transform including scale pivot')
 check(all(x['plant_x']<0 and x['pot_bottom_screen']>f[-1]['pot_bottom_screen'] for x in active),origin+' plant starts at lower left and approaches the upper right')
 check(active[0]['plant_center_screen']<active[-1]['plant_center_screen']<=f[-1]['plant_center_screen'],origin+' actual screen X moves right, not left')
 check(all(abs(x['pot_bottom_screen']-x['card_top_screen'])<=2 for x in f[1:]),origin+' pot bottom stays aligned to the rising hero card on intermediate frames')
 check(abs(f[-1]['plant_bottom']-f[-1]['group_height'])<.5 and f[-1]['plant_alpha']==1,origin+' pot still sits on the card edge at rest')
f=frames('settings');first,last=f[0]['modules'],f[-1]['modules'];check(len(first)>7,'settings has more than seven visible modules to cover regression')
check(all(x['alpha']==0 and x['y']>0 for x in first),'all settings modules including last about/update card start with entrance motion')
check(any(0<x['modules'][-1]['alpha']<1 for x in f),'about/update card has intermediate fade frames')
check(all(x['alpha']==1 and abs(x['y'])<.001 for x in last),'all settings modules finish fully visible and aligned')
q['click']('首页');q['click']('设置');q['click']('悬浮球');q['click']('首页');time.sleep(1);q['receiver']('qa_v124_snapshot');v=q['values']();check(float(v['qa_v124_page_alpha'])==1 and abs(float(v['qa_v124_page_x']))<.001,'rapid navigation leaves no unfinished page transform')
pid=q['adb']('shell','pidof','com.kongye.floatball').strip().split()[0];check('FATAL EXCEPTION' not in q['adb']('logcat','-d','--pid='+pid,'-t','100'),'Sony app has no fatal exception')
print(count,'checks passed on Sony; no preferences changed and no phone media exported.',flush=True)
