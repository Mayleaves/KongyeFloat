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
html=html.replace('</style>',css+'  </style>')
html=html.replace('<meta charset="utf-8">','<meta charset="utf-8"><meta http-equiv="Content-Security-Policy" content="default-src \'none\'; script-src \'unsafe-inline\'; style-src \'unsafe-inline\'; img-src data:; media-src blob:; connect-src \'none\'">')
dest=pathlib.Path(__file__).resolve().parents[1]/'app/src/main/assets/about.html'
dest.write_text(html,encoding='utf-8')
print('Packaged supplied about page; no external resources or desktop demonstration controls.')
