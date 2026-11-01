"""Package the supplied about page, retaining its SVG/CSS/JS and removing desktop chrome."""
import pathlib, re, sys
source=pathlib.Path(sys.argv[1])
html=source.read_text(encoding='utf-8-sig')
html=re.sub(r'\n    <aside class="notes">.*?</aside>', '', html, flags=re.S)
html=re.sub(r'\n      <div class="statusbar">.*?\n      </div>', '', html, count=1, flags=re.S)
html=html.replace('class="phone" id="phone"','class="phone __APP_DARK__" id="phone"')
html=html.replace('版本 1.24.0','版本 __APP_VERSION__')
html=html.replace("$('back').addEventListener('click', () => toast('返回设置'));", "$('back').addEventListener('click', () => location.href='https://about.kongye.local/back');")
start=html.index('    /* ---------- 状态栏时间 ---------- */')
end=html.index('    setStage(stage);',start)
html=html[:start]+html[end:]
css='''
    html,body{width:100%;height:100%;min-height:0;margin:0;padding:0;overflow:hidden;display:block;background:var(--bg)}
    .layout{width:100%;max-width:none;height:100%;display:block}
    .phone{width:100%;height:100%;min-height:0;border:0;border-radius:0;box-shadow:none}
    .topbar{justify-content:space-between;height:56px}
    .suspended *,.suspended *::before,.suspended *::after{animation-play-state:paused!important}
'''
css+='''
    .tap-lv{opacity:0;pointer-events:none;transition:opacity .45s ease,transform .3s ease}.tap-lv.show{opacity:1}
    .ff,.fx .firefly,.win.is-night .ff{opacity:0;animation:none!important;transition:opacity 1.5s ease-in-out}
'''.replace('\n+','\n')
html=html.replace('    function showTaps() {','    let clickedThisVisit=false,tapHideTimer;\n    function showTaps() {').replace("toggle('show', logoTaps > 0)","toggle('show', clickedThisVisit && logoTaps > 0)").replace('      logoTaps++;','      clickedThisVisit=true;logoTaps++;')
html=html.replace('    setStage(stage);', '''
    const twinkling=new WeakSet();
    function randomFireflies(){if(document.hidden)return;document.querySelectorAll('.ff,.fx .firefly').forEach(dot=>{if(twinkling.has(dot)||Math.random()>.32)return;twinkling.add(dot);dot.style.opacity='0';dot.style.left=(6+Math.random()*88)+'%';dot.style.top=(10+Math.random()*62)+'%';const glow=dot.animate([{opacity:0},{opacity:.35+Math.random()*.5,offset:.4},{opacity:0}],{duration:2600,easing:'ease-in-out'});glow.onfinish=()=>{dot.style.opacity='0';twinkling.delete(dot);};});}
    setInterval(randomFireflies,1300);setTimeout(randomFireflies,1500);
    setStage(stage);''')
html=html.replace("      clickedThisVisit=true;logoTaps++;","      clickedThisVisit=true;logoTaps++;clearTimeout(tapHideTimer);tapHideTimer=setTimeout(()=>{clickedThisVisit=false;showTaps();},2000);")
html=html.replace('</style>',css+'  </style>')
html=html.replace('<meta charset="utf-8">','<meta charset="utf-8"><meta http-equiv="Content-Security-Policy" content="default-src \'none\'; script-src \'unsafe-inline\'; style-src \'unsafe-inline\'; img-src data:; media-src blob:; connect-src \'none\'">')
dest=pathlib.Path(__file__).resolve().parents[1]/'app/src/main/assets/about.html'
dest.write_text(html,encoding='utf-8')
print('Packaged supplied about page; no external resources or desktop demonstration controls.')
