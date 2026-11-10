"""v3 generated-data acceptance. Never opens or reads the user's real vault."""
import os,runpy,time,json,re,xml.etree.ElementTree as ET
os.environ.setdefault('KONGYE_ADB_SERIAL','525114fb')
q=runpy.run_path('scripts/v118-device.py')
adb,receiver,values=[q[k] for k in ['adb','receiver','values']]
TARGET='com.kongye.floatball.fillfixture'
def cmd(name):receiver('qa_v3_'+name)
def check(ok,label):
    assert ok,label
    print('PASS:',label,flush=True)
def state():cmd('state');return json.loads(values()['qa_v3_state'])
def overlay():receiver('nodes');return json.loads(values().get('qa_overlay_nodes','[]'))
def press(label):
    end=time.monotonic()+5
    while time.monotonic()<end:
        nodes=[n for n in overlay() if n['label']==label]
        if nodes:
            n=next((n for n in nodes if n['clickable']),nodes[0]);q['tap']((n['x1']+n['x2'])/2,(n['y1']+n['y2'])/2);return
        time.sleep(.1)
    raise AssertionError('Missing generated-test control: '+label)
def fixture_click(label):
    adb('shell','uiautomator','dump','/data/local/tmp/kongye-v3-test.xml')
    root=ET.fromstring(adb('exec-out','cat','/data/local/tmp/kongye-v3-test.xml'))
    for n in root.iter('node'):
        if n.get('package')=='com.kongye.docs.docktest' and n.get('text')==label:
            a,b,c,d=map(int,re.findall(r'\d+',n.get('bounds')));q['tap']((a+c)/2,(b+d)/2);return
    raise AssertionError('Fixture unlock not shown')
def login(unsupported=False):
    adb('shell','am','force-stop',TARGET)
    adb('shell','am','start','-n',TARGET+'/com.kongye.fixture.FixtureLogin','--ez','unsupported',str(unsupported).lower())
    time.sleep(.7)
def begin(mode='unlocked',unsupported=False):
    cmd('config_'+mode);login(unsupported);cmd('fill');time.sleep(.8)
def wait_feedback(text):
    end=time.monotonic()+5
    while time.monotonic()<end:
        if values().get('qa_feedback_message')==text:return
        time.sleep(.15)
    raise AssertionError('Unexpected fill result: '+str(values().get('qa_feedback_message')))
def wait_filled(reveals=1):
    end=time.monotonic()+6
    while time.monotonic()<end:
        s=state()
        if s['account'] and s['password'] and s['reveals']==reveals and s['binds']==1:return s
        time.sleep(.15)
    raise AssertionError('Generated-data fill did not finish: '+str(s))

q['main']();time.sleep(1);receiver('qa_v134_awake');receiver('qa_v123_service_REFRESH');time.sleep(.7);cmd('setup')
try:
    cmd('contract');check(values().get('qa_v3_contract')=='true','domain parsing rejects credentials/search/script schemes; destination changes rejected')
    begin();s=state()
    check(any(n['label']=='login.example.com' for n in overlay()),'browser address control supplies the exact domain')
    check(s['defaults']==['region','longshot','record','subtitle','recent_apps','favorite_apps','notes','browser'],'v3 eight defaults match the phone configuration captured before development; existing custom choices remain separate')
    check(s['menu_available'] and s['gesture_available'],'password fill available in shortcut catalogue and gestures')
    check(s['reveals']==0 and not s['password'],'opening chooser never reveals or fills a password')
    press('填充 测试密码（模拟数据）');s=wait_filled();wait_feedback('账号密码已填入')
    check(s['account'] and s['password'] and s['binds']==1,'explicit selection fills both fields and binds successful first use')

    begin('empty');check(state()['reveals']==0,'unmatched login shows search without revealing')
    press('搜索密码空间');adb('shell','input','text','fixture');time.sleep(.8)
    press('填充 测试密码（模拟数据）');s=wait_filled();wait_feedback('账号密码已填入')
    check(s['account'] and s['password'] and s['binds']==1,'manual search with keyboard restores login geometry, fills, and binds')

    begin('locked');press('填充 测试密码（模拟数据）');time.sleep(.8);fixture_click('测试解锁');s=wait_filled(2);wait_feedback('账号密码已填入')
    check(s['password'] and s['reveals']==2,'locked entry unlocks then reacquires the original login form before filling')
    begin('locked');press('填充 测试密码（模拟数据）');time.sleep(.8);fixture_click('取消测试解锁');wait_feedback('已取消密码填充，密码空间未解锁')
    check(not state()['password'],'cancelled unlock does not fill')

    begin('unlocked',True);press('填充 测试密码（模拟数据）');wait_feedback('这里填不了');s=state()
    check(not s['account'] and not s['password'] and s['binds']==0,'unsupported text action fails without partial fill or binding')
    begin();login();press('填充 测试密码（模拟数据）');wait_feedback('页面或输入框已改变，请重新点击填充密码');s=state()
    check(s['reveals']==0 and not s['password'],'switching login windows prevents reveal and fill')
    begin();cmd('change_site');time.sleep(.25);press('填充 测试密码（模拟数据）');wait_feedback('页面或输入框已改变，请重新点击填充密码')
    check(state()['reveals']==0,'changing site within the same browser window prevents reveal')

    for mode in ['disabled','denied','missing']:
        begin(mode)
        if mode=='missing':press('填充 测试密码（模拟数据）')
        time.sleep(.7);s=state();check(not s['password'] and s['binds']==0,mode+' state does not fill or bind')

    begin();press('取消密码填充');check(state()['reveals']==0,'cancelling the chooser never reveals')
    for kind in ['Screen','Region','Long']:
        q['main']();cmd('image_'+kind);time.sleep(.25);press('复制截图');time.sleep(.8)
        q['main']();cmd('image_clip');p=values()
        check(p.get('qa_v3_image_clip_ok')=='true' and p.get('qa_v3_image_closed')=='true',kind+' copies a real PNG content URI and closes the result window')

    q['main']();receiver('qa_v123_snapshot');p=values();x,y=p.get('x'),p.get('y');before=int(p.get('qa_pull_count','0'))
    balls=[n for n in overlay() if n['label'].startswith('空叶悬浮球：')]
    check(bool(balls),'floating ball visible for edge-pull check')
    n=balls[0];cx,cy=(n['x1']+n['x2'])//2,(n['y1']+n['y2'])//2
    receiver('qa_v123_snapshot')
    # Use the current edge; preserve the user's saved position after exercising the drag.
    width=int(re.findall(r'(\d+)x\d+',adb('shell','wm','size'))[-1]);dest=cx-240 if cx>width/2 else cx+240
    adb('shell','input','swipe',str(cx),str(cy),str(dest),str(cy),'800');time.sleep(.7);receiver('qa_v123_snapshot');p=values()
    check(int(p.get('qa_pull_count','0'))>before and float(p['qa_v123_ball_scale'])==1,'inward edge drag plays jelly and returns to full size')
    q['prefs']({'x':x,'y':y});
finally:
    cmd('restore');receiver('qa_v134_awake_off');q['main']()
