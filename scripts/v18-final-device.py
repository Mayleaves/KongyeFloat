import runpy,time,re,json,sys
m=runpy.run_path(str(__file__).replace('v18-final-device.py','v18-device.py'))
v,a,adb,click,keys=m['v'],m['a'],m['adb'],m['click'],m['keys']
def edges():
    a.main();v.f.diagnostic('qa_record_pattern');x1,y1,x2,y2=m['window']('空叶悬浮球');x,y=(x1+x2)//2,(y1+y2)//2
    adb('shell','input','motionevent','DOWN',str(x),str(y));time.sleep(.32);adb('shell','input','motionevent','MOVE','600','1200');time.sleep(.6)
    a.check(keys('drag_cross_state')['drag_cross_state']=='yellow','still finger arms yellow cross')
    adb('shell','input','motionevent','MOVE','0','0');adb('shell','input','motionevent','UP','0','0');time.sleep(1.5);m['q'].ready('编辑图片');values=keys('document_width','document_height');print('Corner region:',values)
    a.check(abs(int(values['document_width'])-600)<=2 and abs(int(values['document_height'])-1200)<=2,'drag screenshot reaches physical top-left corner');click('关闭截图结果');a.check(m['window']('空叶悬浮球')==[954,1087,1080,1213],'capture restores original ball position')
def background():
    v.f.diagnostic('qa_record_gif');a.check(m['window']('空叶录制准备:录制水印位置预览')[2]>0,'watermark position preview is present');click('录制水印');m['q'].ready('录制水印编辑画布');nodes=m['own']();n=next(n for n in nodes if n.get('content-desc')=='录制水印编辑画布');x1,y1,x2,y2=v.bounds(n)
    value=json.loads(keys('gif_watermark')['gif_watermark'])[0];x=x1+value['x']*(x2-x1)+12;y=y1+(value['y']-value['size']*.4)*(y2-y1);v.tap(x,y)
    a.check('mInputShown=true' in adb('shell','dumpsys','input_method'),'existing watermark can reopen keyboard');n=m['desc']('录制水印编辑画布');at=v.bounds(n);a.check(at[3]<2520-300,'watermark canvas stays above keyboard');click('完成水印编辑');a.check(m['window']('空叶录制准备:录制水印位置预览')[2]>0,'watermark position preview is present')
    click('录制时隐藏图标');click('显示录制控制图标');adb('shell','input','keyevent','3');time.sleep(.4);click('开始录制 GIF');click('立即开始');time.sleep(4.5);click('展开录制控制');click('停止录制');m['q'].ready('GIF 动态预览',90)
    dump=adb('shell','dumpsys','window','windows');blocks=[b for b in re.split(r'(?=  Window #)',dump) if 'MainActivity' in b.splitlines()[0]];a.check(not any('isOnScreen=true' in b or 'isVisible=true' in b for b in blocks),'recording result does not display main software page underneath');print('Main activity window count:',len(blocks));click('关闭录制结果');a.check('MainActivity' not in a.focus(),'closing recording result returns to prior launcher')
def restore():
    for gif,key in [(False,'record_watermark'),(True,'gif_watermark')]:
        v.f.diagnostic('qa_record_gif' if gif else 'qa_record_video');data=json.loads(keys(key).get(key) or '[]')
        # Remove only the synthetic test labels created by this run.
        while data and all(item.get('text')=='1234' for item in data):
            click('录制水印');m['q'].ready('录制水印编辑画布');x1,y1,x2,y2=v.bounds(m['desc']('录制水印编辑画布'));item=data[-1];v.tap(x1+item['x']*(x2-x1)+12,y1+(item['y']-item['size']*.4)*(y2-y1));click('删除选中标注');click('完成水印编辑');data=json.loads(keys(key).get(key) or '[]')
        a.check(not keys(key).get(key),'test watermark removed via its delete control');click('录制时隐藏图标');click('显示录制控制图标');x1,y1,x2,y2=m['window']('空叶录制准备:关闭录制准备');v.tap((x1+x2)/2,(y1+y2)/2)
    a.main();a.check(keys('size','edge_gap','x','y','gesture_tap')=={'size':'48','edge_gap':'0','x':'954','y':'1087','gesture_tap':'none'},'user appearance, position and tap binding preserved')
if __name__=='__main__':{'edges':edges,'background':background,'restore':restore}[sys.argv[1]]()
