"""Splash touch stream, note persistence and HTML feedback regression with generated text."""
import json
import os
import runpy
import time

q=runpy.run_path('scripts/v118-device.py')
receiver,values=q['receiver'],q['values']
def cmd(value):receiver('qa_v205_'+value)
def state():
    cmd('state')
    return json.loads(values()['qa_v205_state'])
def check(value,label):
    assert value,label
    print('PASS:',label,flush=True)
def press(label):
    receiver('nodes')
    item=next(n for n in json.loads(values()['qa_overlay_nodes']) if n['label']==label and n['enabled'])
    q['tap']((item['x1']+item['x2'])/2,(item['y1']+item['y2'])/2)

q['main']()
time.sleep(2.2)
receiver('qa_v134_awake')
receiver('qa_v123_service_REFRESH')
q['main']()
try:
    cmd('touch')
    check(values().get('qa_v205_touch')=='true','splash taps and touch held across fade never activate underlying button; next new tap works')
    receiver('qa_v202_feedback_short')
    time.sleep(.4)
    current=state()
    check(current['success'] and current['badge_color']==-8017817,'success uses the HTML sage badge and check mark')
    receiver('qa_v202_feedback_long')
    current=state()
    check(not current['success'] and current['badge_color']==-603835,'unsuccessful feedback uses HTML amber badge and icon D')
    time.sleep(3.1)
    check('badge_color' not in state(),'feedback exit removes the overlay')

    cmd('note_fixture')
    press('清空便签')
    current=state()
    check(current['note_empty'] and current['panel'],'clear note empties persisted content and keeps editor open')
    check(current['success'] and values().get('qa_feedback_message')=='便签已清空','clear note reports successful state')
    press('关闭窗口')
    cmd('notes')
    check(state()['note_empty'],'reopening the note retains empty content')
    press('清空便签')
    current=state()
    check(current['panel'] and not current['success'],'clearing an already empty note keeps editor with amber feedback')
    press('复制便签')
    check(state()['panel'] and not state()['success'],'empty note copy retains its editor and unsuccessful feedback')
finally:
    cmd('restore')
    receiver('qa_v134_awake_off')
    q['main']()
