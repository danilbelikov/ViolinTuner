#!/usr/bin/env python3
"""Builds docs/redesign/mockups/live.html from src.html (shared system) + Live pieces."""
import base64, re, os

HERE = os.path.dirname(os.path.abspath(__file__))
SP = os.path.dirname(HERE)
OUT = '/Users/danil/AndroidStudioProjects/ViolinTuner/docs/redesign/mockups/live.html'

src = open(os.path.join(SP, 'src.html')).read()
style = re.search(r'<style>(.*?)</style>', src, re.S).group(1)
symbols = re.search(r'(<svg width="0" height="0".*?</svg>)', src, re.S).group(1)
scripts = re.findall(r'<script>(.*?)</script>', src, re.S)
ui_script = scripts[0][scripts[0].index('(function(){\n  var root=document.documentElement'):]

def b64(p):
    return 'data:image/jpeg;base64,' + base64.b64encode(open(os.path.join(SP, p), 'rb').read()).decode()

ROOM = b64('liveroom.jpg')
NOW = b64('live_now.jpg')

extra_symbols = '''
  <symbol id="i-up" viewBox="0 0 24 24"><path d="M12 20V5M5.5 11.5 12 5l6.5 6.5"/></symbol>
  <symbol id="i-down" viewBox="0 0 24 24"><path d="M12 4v15M5.5 12.5 12 19l6.5-6.5"/></symbol>
  <symbol id="i-gear" viewBox="0 0 24 24"><circle cx="12" cy="12" r="3.2"/><path d="M12 2.5v2.6M12 18.9v2.6M4.6 4.6l1.9 1.9M17.5 17.5l1.9 1.9M2.5 12h2.6M18.9 12h2.6M4.6 19.4l1.9-1.9M17.5 6.5l1.9-1.9"/><circle cx="12" cy="12" r="6.6"/></symbol>
  <symbol id="i-lock2" viewBox="0 0 24 24"><rect x="5" y="11" width="14" height="10" rx="2"/><path d="M8.5 11V8a3.5 3.5 0 0 1 7 0v3"/></symbol>
  <symbol id="i-mic" viewBox="0 0 24 24"><rect x="9" y="3" width="6" height="11" rx="3"/><path d="M5.5 11a6.5 6.5 0 0 0 13 0M12 17.5V21"/></symbol>
  <symbol id="i-check2" viewBox="0 0 24 24"><path d="m5 12.5 4.5 4.5L19 7.5"/></symbol>
'''
symbols = symbols.replace('</svg>', extra_symbols + '</svg>')

live_css = r'''
/* ===== Live ===== */
.screens .screen{width:100%}
.phone-col{width:390px}
.screen.wide .phone-col{width:auto}
:root{--zin:#47C97E;--znear:#E5B03C;--zoff:#E8565C;--ok:#7CB99A;--no:#C4877F;--bone:#F1EEE6;--bone-2:#D8D0BE;--ink:#2A2430;--ink-2:#6E635C;--brass:#C9A24A;--velvet:#8E2F3F;--glass:rgba(19,19,24,.74)}
.stage{position:relative;flex:1;min-height:0;overflow:hidden;background:#101014;color:#F4F2F8}
.bd{position:absolute;inset:0;background:url(%ROOM%) center 30%/cover no-repeat;transition:filter .3s}
.off .bd{filter:grayscale(.92) brightness(.26) contrast(.95)}
.tint{position:absolute;inset:0;pointer-events:none}
.z-in .tint{background:radial-gradient(circle at 50% var(--cy,40%),rgba(71,201,126,.34) 0,rgba(71,201,126,.14) 38%,rgba(71,201,126,.05) 75%)}
.z-near .tint{background:radial-gradient(circle at 50% var(--cy,40%),rgba(229,176,60,.26) 0,rgba(229,176,60,.10) 38%,rgba(229,176,60,.04) 75%)}
.z-off .tint{background:radial-gradient(circle at 50% var(--cy,40%),rgba(232,86,92,.20) 0,rgba(232,86,92,.08) 38%,rgba(232,86,92,.03) 75%)}
.veil{position:absolute;inset:0;pointer-events:none;background:radial-gradient(circle at 50% var(--cy,40%),rgba(12,12,16,.62) 0,rgba(12,12,16,.34) 36%,rgba(12,12,16,0) 66%)}
.shade{position:absolute;left:0;right:0;top:0;height:190px;pointer-events:none;background:linear-gradient(rgba(12,12,16,.78),rgba(12,12,16,0))}
.floor{position:absolute;left:0;right:0;bottom:0;height:180px;pointer-events:none;background:linear-gradient(rgba(30,29,37,0),rgba(30,29,37,.92) 85%,#1E1D25)}
.topbar{position:absolute;top:10px;left:16px;right:16px;height:48px;display:flex;justify-content:center;align-items:center;z-index:3}
.seg{height:44px;padding:4px;border-radius:22px;background:var(--glass);-webkit-backdrop-filter:blur(8px);backdrop-filter:blur(8px);display:flex;box-shadow:0 0 0 1px rgba(255,255,255,.08) inset}
.seg span{min-width:106px;height:36px;border-radius:18px;display:grid;place-items:center;font-size:15px;font-weight:700;color:#C9C5D6}
.seg .on{background:var(--bone);color:#1C1A20}
.gear{position:absolute;right:0;top:0;width:48px;height:48px;border-radius:50%;display:grid;place-items:center}
.gear i{width:40px;height:40px;border-radius:50%;background:var(--glass);display:grid;place-items:center;box-shadow:0 0 0 1px rgba(255,255,255,.08) inset;color:#E6E4EE}
.gear .ic{width:20px;height:20px}
.plate{position:absolute;top:var(--pt,74px);left:50%;transform:translateX(-50%);height:36px;padding:0 15px;border-radius:18px;background:var(--glass);-webkit-backdrop-filter:blur(8px);backdrop-filter:blur(8px);display:flex;align-items:center;gap:9px;font-size:15px;font-weight:600;color:#E6E4EE;white-space:nowrap;z-index:3}
.plate.hide{visibility:hidden}
.dok{width:9px;height:9px;border-radius:50%;background:var(--ok)}
.dno{width:10px;height:10px;border-radius:50%;border:2px solid var(--no)}
.ring{position:absolute;left:50%;top:var(--rt,150px);width:var(--rd,286px);height:var(--rd,286px);margin-left:calc(var(--rd,286px) / -2);border-radius:50%;z-index:2}
.ring.quiet{border:2.5px solid rgba(240,238,246,.62);box-shadow:0 0 0 1px rgba(12,12,16,.35)}
.z-in .ring.lit{border:6px solid var(--zin);box-shadow:0 0 60px 14px rgba(71,201,126,.55),0 0 0 22px rgba(71,201,126,.07),inset 0 0 50px rgba(71,201,126,.32);background:radial-gradient(circle,rgba(71,201,126,.20),rgba(71,201,126,.04) 70%)}
.z-near .ring.lit{border:5px solid var(--znear);box-shadow:0 0 34px 6px rgba(229,176,60,.38),inset 0 0 30px rgba(229,176,60,.18);background:radial-gradient(circle,rgba(229,176,60,.10),transparent 70%)}
.z-off .ring.lit{border:4px solid var(--zoff);box-shadow:0 0 22px 3px rgba(232,86,92,.28),inset 0 0 20px rgba(232,86,92,.12)}
.note{position:absolute;inset:0;display:flex;align-items:center;justify-content:center;font-weight:800;font-size:var(--nf,170px);line-height:1;letter-spacing:-.05em;color:#F6F4FA;font-variant-numeric:tabular-nums}
.note .acc{font-size:.46em;align-self:flex-start;margin-top:.62em;letter-spacing:0}
.note .oct{font-size:.27em;color:#A39FB5;align-self:flex-end;margin-bottom:.62em;margin-left:.06em;letter-spacing:0}
.readout{position:absolute;left:0;right:0;top:var(--ro,470px);display:flex;justify-content:center;align-items:center;gap:16px;z-index:3;white-space:nowrap}
.word{display:flex;align-items:center;gap:8px;font-size:30px;font-weight:800;letter-spacing:-.01em}
.word .ic{width:28px;height:28px;stroke-width:3}
.word .zd{width:14px;height:14px;border-radius:50%;background:currentColor}
.z-in .word{color:var(--zin)} .z-near .word{color:var(--znear)} .z-off .word{color:var(--zoff)}
.cents{font-size:26px;font-weight:700;color:#A39FB5;min-width:62px;font-variant-numeric:tabular-nums}
.ctrls{position:absolute;left:10px;right:10px;bottom:16px;display:grid;grid-template-columns:1fr 76px 1fr;gap:8px;align-items:center;z-index:3}
.chipx{position:relative;height:60px;border-radius:18px;background:var(--glass);-webkit-backdrop-filter:blur(8px);backdrop-filter:blur(8px);box-shadow:0 0 0 1px rgba(255,255,255,.12) inset;display:flex;align-items:center;gap:7px;padding:0 10px;color:#EDEBF3;font-size:13.5px;font-weight:700;line-height:1.2;min-width:0;overflow:hidden}
.chipx .ic{width:20px;height:20px;flex:none}
.chipx .tx{min-width:0}
.chipx .tx b{display:block;white-space:nowrap;overflow:hidden;text-overflow:ellipsis;font-weight:700}
.chipx small{display:block;font-weight:600;color:#C9C5D6;font-size:12.5px;white-space:nowrap}
.chipx.paper{background:var(--bone);color:var(--ink);box-shadow:0 4px 10px rgba(0,0,0,.35)}
.chipx.paper small{color:var(--ink-2)}
.chipx .vd{width:9px;height:9px;border-radius:50%;background:var(--velvet);flex:none}
.chipx .prog{position:absolute;left:12px;right:12px;bottom:7px;height:3px;border-radius:2px;background:var(--bone-2)}
.chipx .prog i{display:block;height:100%;border-radius:2px;background:var(--brass)}
.chipx.done{box-shadow:0 0 0 2.5px var(--brass),0 4px 10px rgba(0,0,0,.35)}
.chipx.done .ok{color:#2E6B4A}
.key{justify-self:center;width:76px;height:76px;border-radius:50%;background:radial-gradient(circle at 50% 38%,#FBF9F4,var(--bone) 55%,var(--bone-2));box-shadow:0 0 0 3px #9C8452,0 7px 14px rgba(0,0,0,.55);display:grid;place-items:center}
.key i{width:28px;height:28px;border-radius:50%;background:var(--velvet)}
.key.rec{background:var(--zoff);box-shadow:0 0 0 3px #A8323F,0 7px 14px rgba(0,0,0,.55)}
.key.rec i{width:24px;height:24px;border-radius:6px;background:#fff}
.dim{opacity:.38}
.tabbar.dim .tab{opacity:.38}
.recbar{position:absolute;left:22px;right:22px;bottom:96px;height:50px;border-radius:25px;background:var(--glass);-webkit-backdrop-filter:blur(8px);backdrop-filter:blur(8px);box-shadow:0 0 0 1px rgba(255,255,255,.1) inset;display:flex;align-items:center;gap:10px;padding:0 16px;z-index:3;font-size:15px;font-weight:700;color:#EDEBF3}
.rdot{width:11px;height:11px;border-radius:50%;background:var(--zoff);box-shadow:0 0 0 4px rgba(232,86,92,.25);flex:none}
.lane{flex:1;display:flex;gap:2px;height:8px;align-items:center;min-width:0}
.lane i{height:8px;border-radius:2px;display:block}
.strings{position:absolute;top:66px;left:22px;right:22px;display:grid;grid-template-columns:repeat(4,1fr);gap:10px;z-index:3}
.str{height:58px;border-radius:16px;background:var(--glass);box-shadow:0 0 0 1px rgba(255,255,255,.1) inset;display:flex;flex-direction:column;align-items:center;justify-content:center;font-size:20px;font-weight:800;color:#EDEBF3;position:relative}
.str small{font-size:11.5px;font-weight:600;color:#BDB9CC;font-variant-numeric:tabular-nums}
.str.near{box-shadow:0 0 0 2px var(--bone) inset;background:rgba(241,238,230,.16)}
.str.lock{background:var(--bone);color:#1C1A20}
.str.lock small{color:var(--ink-2)}
.str .lk{position:absolute;top:5px;right:6px;width:14px;height:14px;stroke-width:2.4}
.scale{position:absolute;left:30px;right:30px;top:var(--sc,520px);height:44px;z-index:3}
.scale .ln{position:absolute;left:0;right:0;top:21px;height:2px;border-radius:1px;background:rgba(230,228,238,.35)}
.scale .tk{position:absolute;top:15px;width:2px;height:14px;background:rgba(230,228,238,.35)}
.scale .tol{position:absolute;top:17px;height:10px;border-radius:5px;background:rgba(71,201,126,.55)}
.scale .cur{position:absolute;top:2px;width:4px;height:40px;border-radius:2px;background:#fff;margin-left:-2px}
.z-near .scale .cur{box-shadow:0 0 14px 5px rgba(229,176,60,.55)}
.z-in .scale .cur{box-shadow:0 0 14px 5px rgba(71,201,126,.6)}
.scale .lb{position:absolute;top:46px;font-size:12px;color:#A39FB5;font-weight:600;transform:translateX(-50%)}
.permcard{position:absolute;left:26px;right:26px;top:230px;border-radius:24px;background:var(--bone);color:var(--ink);padding:22px 20px 18px;z-index:3;box-shadow:0 18px 40px rgba(0,0,0,.5)}
.permcard .mi{width:48px;height:48px;border-radius:16px;background:#E4DDCC;display:grid;place-items:center;color:var(--ink);margin-bottom:12px}
.permcard h3{font-size:22px;font-weight:800;letter-spacing:-.01em;margin-bottom:6px}
.permcard p{font-size:15px;line-height:1.45;color:#4E4650}
.permcard .pbtn{margin-top:18px;height:54px;border-radius:27px;background:#2A2430;color:#F4F2F8;display:flex;align-items:center;justify-content:center;gap:8px;font-weight:800;font-size:16px}
/* лист «Что играем» */
.sheet.tall{padding-bottom:0;max-height:92%;display:flex;flex-direction:column}
.sh-title{display:flex;align-items:baseline;justify-content:space-between;margin-bottom:6px}
.sh-title h3{font-size:22px;font-weight:800}
.sh-list{overflow:hidden;flex:1;min-height:0}
.sh-sec{font-size:13px;font-weight:700;letter-spacing:.06em;text-transform:uppercase;color:var(--text-2);margin:16px 0 4px;display:flex;justify-content:space-between}
.sh-sec em{font-style:normal;text-transform:none;letter-spacing:0;color:var(--text-2);font-weight:600}
.it{display:flex;align-items:center;gap:12px;min-height:58px;padding:8px 12px;margin:0 -12px;border-radius:14px}
.it .m{flex:1;min-width:0}
.it b{display:block;font-size:16px;font-weight:700;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}
.it small{font-size:13px;color:var(--text-2)}
.it.sel{background:var(--accent-soft);box-shadow:0 0 0 1.5px var(--accent) inset}
.pl{height:28px;padding:0 10px;border-radius:14px;display:inline-flex;align-items:center;gap:4px;font-size:13px;font-weight:700;flex:none;font-variant-numeric:tabular-nums}
.pl.done{background:var(--surface-2);color:var(--text)}
.pl.done .ic{width:15px;height:15px;stroke-width:3;color:var(--accent)}
.pl.part{color:var(--text-2)}
.pl.run{border:1.5px solid var(--text-2);color:var(--text-2)}
.goal{flex:none;margin:0 -20px;padding:12px 20px 30px;background:var(--surface);border-top:1px solid var(--border)}
.goal .gl{display:flex;justify-content:space-between;font-size:13px;color:var(--text-2);font-weight:600;margin-bottom:8px}
.gchips{display:grid;grid-template-columns:repeat(7,1fr);gap:6px}
.gchips span{height:44px;border-radius:12px;background:var(--surface-2);display:grid;place-items:center;font-size:15px;font-weight:700;color:var(--text);font-variant-numeric:tabular-nums}
.gchips span.on{background:var(--text);color:var(--bg)}
.gbtn{margin-top:12px;height:56px;border-radius:28px;background:var(--accent);color:var(--on-accent);display:flex;align-items:center;justify-content:center;gap:8px;font:800 17px var(--font)}
.now{display:flex;align-items:center;gap:10px;padding:12px 14px;border-radius:16px;background:var(--bg);margin-bottom:4px}
.now .m{flex:1;min-width:0;font-size:14px;color:var(--text-2)}
.now .m b{display:block;color:var(--text);font-size:15px}
.stopb{height:44px;padding:0 14px;border-radius:14px;border:1.5px solid var(--border);display:inline-flex;align-items:center;font-weight:700;font-size:14px;color:var(--text)}
.ghost{height:52px;display:flex;align-items:center;justify-content:center;font-weight:700;color:var(--text-2);font-size:16px}
/* landscape */
.phone.land{width:844px;height:390px}
.phone.land .statusbar{height:28px;padding:6px 40px 0 48px;font-size:13px}
@media (max-width:900px){.phone.land{zoom:.5}}
.screen.wide .screen-body{flex-direction:column}
.screen.wide .notes{width:760px;display:grid;grid-template-columns:1fr 1fr}
'''.replace('%ROOM%', ROOM)

STATUSBAR = '<div class="statusbar"><span>18:42</span><span class="sb-icons"><svg class="ic ic-s"><use href="#i-sb-signal"/></svg><svg class="ic" style="width:26px"><use href="#i-sb-battery"/></svg></span></div>'

def tabbar(dim=False):
    return f'''<nav class="tabbar{' dim' if dim else ''}">
  <div class="tab"><div class="pill"><svg class="ic"><use href="#i-watch"/></svg></div>Занятия</div>
  <div class="tab is-active"><div class="pill"><svg class="ic"><use href="#i-string"/></svg></div>Live</div>
  <div class="tab"><div class="pill"><svg class="ic"><use href="#i-sheet"/></svg></div>Репертуар</div>
  <div class="tab"><div class="pill"><svg class="ic"><use href="#i-tape"/></svg></div>Записи</div>
</nav>'''

def seg(mode='play', dim=False, n=None, gear_n=None):
    a = 'on' if mode == 'play' else ''
    b = 'on' if mode == 'tune' else ''
    note = f' data-note="{n}"' if n else ''
    gn = f' data-note="{gear_n}"' if gear_n else ''
    return f'''<div class="topbar{' dim' if dim else ''}"><div class="seg"{note}><span class="{a}">Игра</span><span class="{b}">Настройка</span></div>
<div class="gear"{gn}><i><svg class="ic"><use href="#i-gear"/></svg></i></div></div>'''

def bookmark(kind='idle', n=None, dim=False):
    note = f' data-note="{n}"' if n else ''
    d = ' dim' if dim else ''
    if kind == 'idle':
        return f'<div class="chipx{d}"{note}><svg class="ic"><use href="#i-sheet"/></svg><div class="tx"><b>Что играю</b><small>выбрать</small></div></div>'
    if kind == 'run':
        return f'<div class="chipx paper{d}"{note}><svg class="ic"><use href="#i-sheet"/></svg><div class="tx"><b>Ре мажор</b><small>ещё 7 мин</small></div><span class="prog"><i style="width:53%"></i></span></div>'
    if kind == 'done':
        return f'<div class="chipx paper done"{note}><svg class="ic ok" style="stroke-width:3"><use href="#i-check2"/></svg><div class="tx"><b>Ре мажор</b><small>готово</small></div></div>'

def tag(kind='idle', n=None, dim=False):
    note = f' data-note="{n}"' if n else ''
    d = ' dim' if dim else ''
    if kind == 'idle':
        return f'<div class="chipx{d}"{note}><svg class="ic"><use href="#i-watch"/></svg><div class="tx"><b>Начать</b><small>занятие</small></div></div>'
    return f'<div class="chipx paper{d}"{note}><span class="vd"></span><div class="tx"><b class="tnum">24:18</b><small>занятие</small></div></div>'

def key(kind='idle', n=None, dim=False):
    note = f' data-note="{n}"' if n else ''
    d = ' dim' if dim else ''
    cls = 'key rec' if kind == 'rec' else 'key'
    return f'<div class="{cls}{d}"{note}><i></i></div>'

def ctrls(bm, tg, ky):
    return f'<div class="ctrls">{bm}{ky}{tg}</div>'

def note_html(letter='A', acc='', octv='4'):
    a = f'<span class="acc">{acc}</span>' if acc else ''
    return f'<div class="note">{letter}{a}<span class="oct">{octv}</span></div>'

def readout(zone, cents, n=None, cn=None):
    note = f' data-note="{n}"' if n else ''
    cnn = f' data-note="{cn}"' if cn else ''
    if zone == 'in':
        w = '<span class="zd"></span>в строе'
    elif zone == 'up':
        w = '<svg class="ic"><use href="#i-up"/></svg>выше'
    else:
        w = '<svg class="ic"><use href="#i-down"/></svg>ниже'
    return f'<div class="readout"><span class="word"{note}>{w}</span><span class="cents"{cnn}>{cents}</span></div>'

def lane():
    segs = [('zin', 30), ('zin', 22), ('znear', 12), ('zin', 26), ('zoff', 8), ('zin', 18), ('znear', 14), ('zin', 34), ('zin', 16), ('znear', 10)]
    return '<span class="lane">' + ''.join(f'<i style="width:{w}px;background:var(--{c})"></i>' for c, w in segs) + '</span>'

def stage(off, zone_cls, inner, cy='40%'):
    return f'<div class="stage {"off" if off else "lit"} {zone_cls}" style="--cy:{cy}"><div class="bd"></div><div class="tint"></div><div class="veil"></div><div class="shade"></div><div class="floor"></div>{inner}</div>'

def state(name, body):
    return f'<div class="state" data-state="{name}">{body}</div>'

def phone(states, land=False):
    return f'''<div class="phone{' land' if land else ''}"><div class="phone-screen">{STATUSBAR}{''.join(states)}<div class="home-indicator"></div></div></div>'''

# ---------------- Screen 1: Live · Игра ----------------
s1 = []
# Silence: light on, no practice
s1.append(state('Тишина', stage(False, '',
    seg('play', n=1, gear_n=None) +
    '<div class="plate" data-note="2"><span class="dok"></span>Играйте…</div>' +
    '<div class="ring quiet"></div>' +
    ctrls(bookmark('idle', n=6), tag('idle', n=7), key('idle', n=5))) + tabbar()))
# In tune: light off, practice + block running
s1.append(state('В строе', stage(True, 'z-in',
    seg('play', dim=True) +
    '<div class="plate hide"><span class="dok"></span>Играйте…</div>' +
    '<div class="ring lit" data-note="3">' + note_html('A', '', '4') + '</div>' +
    readout('in', '+3', n=4) +
    ctrls(bookmark('run', dim=True), tag('run', dim=True), key('idle', dim=True))) + tabbar(dim=True)))
s1.append(state('Выше · рядом', stage(True, 'z-near',
    seg('play', dim=True) +
    '<div class="ring lit">' + note_html('F', '#', '5') + '</div>' +
    readout('up', '+14') +
    ctrls(bookmark('run', dim=True), tag('run', dim=True), key('idle', dim=True))) + tabbar(dim=True)))
s1.append(state('Ниже · мимо', stage(True, 'z-off',
    seg('play', dim=True) +
    '<div class="ring lit">' + note_html('D', '', '5') + '</div>' +
    readout('down', '−27') +
    ctrls(bookmark('run', dim=True), tag('run', dim=True), key('idle', dim=True))) + tabbar(dim=True)))
s1.append(state('Подход готов', stage(True, 'z-in',
    seg('play', dim=True) +
    '<div class="ring lit">' + note_html('D', '', '4') + '</div>' +
    readout('in', '−1') +
    ctrls(bookmark('done', n=8), tag('run', dim=True), key('idle', dim=True))) + tabbar(dim=True)))
s1.append(state('Слишком шумно', stage(False, '',
    seg('play') +
    '<div class="plate"><span class="dno"></span>Слишком шумно</div>' +
    '<div class="ring quiet"></div>' +
    ctrls(bookmark('idle'), tag('idle'), key('idle'))) + tabbar()))
s1.append(state('Микрофон недоступен', stage(False, '',
    seg('play') +
    '<div class="plate"><span class="dno"></span>Микрофон недоступен</div>' +
    '<div class="ring quiet"></div>' +
    ctrls(bookmark('idle'), tag('idle'), key('idle'))) + tabbar()))
s1.append(state('Нет разрешения', stage(False, '',
    seg('play') +
    '''<div class="permcard" data-note="9"><div class="mi"><svg class="ic"><use href="#i-mic"/></svg></div>
<h3>Дайте мне услышать скрипку</h3>
<p>Чтобы показать ноту и попадание, нужен микрофон. Звук не уходит с телефона.</p>
<div class="pbtn"><svg class="ic ic-s"><use href="#i-mic"/></svg>Разрешить доступ</div></div>''' +
    ctrls(bookmark('idle', dim=True), tag('idle', dim=True), key('idle', dim=True))) + tabbar()))

notes1 = '''
<li data-n="1"><b>Переключатель — дымчатое стекло с костяным бегунком</b> вместо кленовой планки: оранжевое дерево сверху на периферии читалось как янтарный «рядом». Цвет на экране теперь значит только интонацию. <span class="tag">сигнал без шума</span></li>
<li data-n="2"><b>Строка «Играйте…» на стеклянной плашке</b> — единственный текст в тишине; с нотой она гаснет, но место за ней остаётся, и кольцо не прыгает. <span class="tag">стабильность раскладки</span></li>
<li data-n="3"><b>Кольцо и нота — самое крупное и самое яркое</b>; комната гаснет в серый, от кольца ложится свет зоны. С пюпитра край глаза видит только цвет и размер. <span class="tag">фигура и фон</span></li>
<li data-n="4"><b>Слово с формой (точка, стрелка вверх, стрелка вниз) и серые цифры правее</b> — цвет всегда дублирован формой, а цифры тише слова и не зовут гоняться за нулём. <span class="tag">доступность</span></li>
<li data-n="5"><b>Клавиша записи — единственная кость и латунь в нижнем ряду</b>, 76 dp, посередине под большим пальцем. Бархатная точка внутри говорит «запись», но не спорит с красным «мимо». <span class="tag">закон Фиттса</span></li>
<li data-n="6"><b>«Что играю» вместо «Репертуар»</b> — на Live теперь не то же слово, что у вкладки; кнопка говорит, что делает: выбрать, что играть сейчас. <span class="tag">ясность слов</span></li>
<li data-n="7"><b>Слева и справа от клавиши — две одинаковые стеклянные карточки</b>: «что играю · записать · сколько занимаюсь». Когда идёт, карточка становится бумагой — «живое» отличается формой и светом. <span class="tag">сходство</span></li>
<li data-n="8"><b>«Готово» — единственное, что не гаснет вместе со светом</b>: латунный обод и галочка приходят во время игры, и их должен заметить край глаза. Нижняя панель вкладок гаснет со всеми. <span class="tag">изоляция</span></li>
<li data-n="9"><b>Без разрешения — бумажная карточка ниже центра, кнопка в зоне пальца</b>: единственное состояние Live, где нужны слова и касание. <span class="tag">один шаг</span></li>
'''

# ---------------- Screen 2: запись ----------------
s2 = []
s2.append(state('Запись идёт', stage(True, 'z-near',
    seg('play', dim=True) +
    '<div class="ring lit">' + note_html('E', '', '5') + '</div>' +
    readout('up', '+11') +
    f'<div class="recbar" data-note="1"><span class="rdot"></span><span>запись</span><span class="tnum">1:24</span>{lane()}</div>' +
    ctrls(bookmark('run', dim=True), tag('run', dim=True), key('rec', n=2))) + tabbar(dim=True)))
s2.append(state('Пауза в записи', stage(True, '',
    seg('play', dim=True, n=3) +
    '<div class="plate hide"><span class="dok"></span>Играйте…</div>' +
    '<div class="ring quiet"></div>' +
    f'<div class="recbar"><span class="rdot"></span><span>запись</span><span class="tnum">1:31</span>{lane()}</div>' +
    ctrls(bookmark('run', dim=True), tag('run', dim=True), key('rec'))) + tabbar(dim=True)))
notes2 = '''
<li data-n="1"><b>Полоса записи — над клавишей, на своём стекле: точка, слово «запись», время и лента нот цветами зон.</b> Слово отличает её от таймера занятия, лента показывает, где нота уплыла. <span class="tag">распознавание</span></li>
<li data-n="2"><b>Клавиша становится красной со «стопом»</b> — остановить можно там же, где начали, тем же пальцем. <span class="tag">постоянство</span></li>
<li data-n="3"><b>Свет не загорается до «стопа», переключатель и вкладки приглушены</b>: запись — выступление, в паузе ничего не зовёт тапнуть и оборвать дубль. <span class="tag">защита от ошибок</span></li>
'''

# ---------------- Screen 3: Настройка ----------------
def scale(pos_pct, tol_pct=16, n=None):
    note = f' data-note="{n}"' if n else ''
    ticks = ''.join(f'<span class="tk" style="left:{p}%"></span>' for p in (0, 25, 75, 100))
    return f'''<div class="scale"{note}><span class="ln"></span>{ticks}<span class="tol" style="left:{50-tol_pct/2}%;width:{tol_pct}%"></span><span class="cur" style="left:{pos_pct}%"></span>
<span class="lb" style="left:0%">−50</span><span class="lb" style="left:50%">0</span><span class="lb" style="left:100%">+50</span></div>'''

s3 = []
s3.append(state('Авто · звучит A', f'''<div class="stage off z-near" style="--cy:42%;--rt:176px;--rd:236px;--nf:140px;--ro:440px;--sc:494px"><div class="bd"></div><div class="tint"></div><div class="veil"></div><div class="shade"></div><div class="floor"></div>
{seg('tune', dim=True)}
<div class="strings" data-note="1"><div class="str dim">G<small>196</small></div><div class="str dim">D<small>294</small></div><div class="str near">A<small>440</small></div><div class="str dim">E<small>659</small></div></div>
<div class="ring lit">{note_html('A','','4')}</div>
{readout('up','+14')}
{scale(64, n=2)}
{ctrls(bookmark('idle', dim=True), tag('run', dim=True), key('idle', dim=True, n=3))}
</div>''' + tabbar(dim=True)))
s3.append(state('Зафиксирована D · тишина', f'''<div class="stage lit" style="--cy:42%;--rt:176px;--rd:236px;--nf:140px;--ro:440px;--sc:494px"><div class="bd"></div><div class="tint"></div><div class="veil"></div><div class="shade"></div><div class="floor"></div>
{seg('tune')}
<div class="strings"><div class="str">G<small>196</small></div><div class="str lock" data-note="4"><svg class="ic lk"><use href="#i-lock2"/></svg>D<small>294</small></div><div class="str">A<small>440</small></div><div class="str">E<small>659</small></div></div>
<div class="plate" style="--pt:136px" data-note="5"><span class="dok"></span>D · 294 Гц · тап — снять</div>
<div class="ring quiet">{note_html('D','','4').replace('class="note"','class="note" style="opacity:.28"')}</div>
{ctrls(bookmark('idle'), tag('run'), key('idle', dim=True))}
</div>''' + tabbar()))
notes3 = '''
<li data-n="1"><b>Ряд из четырёх струн — стеклянные кнопки; пока звучит нота, гаснут все, кроме ближайшей</b>, обведённой костью: она и есть ответ «какую струну я тяну», а не прибор для касания. <span class="tag">сходство</span></li>
<li data-n="2"><b>Шкала сразу под словом и числом</b> — слово, число и бегунок читаются как один прибор; зелёная пилюля — ширина допуска, бегунок светится цветом зоны. <span class="tag">близость</span></li>
<li data-n="3"><b>Клавиша записи приглушена</b> — в «Настройке» записи нет, и это видно до касания, а не по тосту. <span class="tag">видимость состояния</span></li>
<li data-n="4"><b>Зафиксированная струна — залитая кость с замком</b>: отличается от «ближайшей» и формой, и заливкой. <span class="tag">цвет + форма</span></li>
<li data-n="5"><b>Подсказка короче: «D · 294 Гц · тап — снять»</b>; нота струны в кольце — бледным силуэтом, чтобы было видно, к чему тянуть. <span class="tag">узнавание</span></li>
'''

# ---------------- Screen 4: лист «Что играем» ----------------
live_bg = stage(False, '', seg('play') + '<div class="plate"><span class="dok"></span>Играйте…</div><div class="ring quiet"></div>' + ctrls(bookmark('idle'), tag('run'), key('idle'))) + tabbar()

list_items = '''
<div class="sh-sec">Произведения <em>сегодня 1</em></div>
<div class="it sel"><div class="m"><b>Концерт ля минор, 1 ч.</b><small>А. Вивальди</small></div><span class="pl done"><svg class="ic"><use href="#i-check2"/></svg>15 мин</span></div>
<div class="it"><div class="m"><b>Юмореска</b><small>А. Дворжак</small></div><span class="pl part">7 мин</span></div>
<div class="it"><div class="m"><b>Чардаш</b><small>В. Монти</small></div></div>
<div class="sh-sec">Гаммы</div>
<div class="it"><div class="m"><b>Ре мажор, 3 октавы</b></div></div>
<div class="it"><div class="m"><b>Соль мажор, 3 октавы</b></div></div>
'''
s4 = []
s4.append(state('Выбор и цель', live_bg + f'''<div class="scrim"></div><div class="sheet tall" style="height:88%">
<div class="sh-title"><h3>Что играем</h3><span class="caption tnum">занятие 24:18</span></div>
<div class="sh-list" data-note="1">{list_items}</div>
<div class="goal" data-note="2"><div class="gl"><span>Сколько играть</span><span>от 5 до 60 мин</span></div>
<div class="gchips"><span>−5</span><span>5</span><span>10</span><span class="on">15</span><span>20</span><span>30</span><span>+5</span></div>
<div class="gbtn" data-note="3">Начать · 15 мин</div></div></div>'''))
s4.append(state('Подход идёт', live_bg + f'''<div class="scrim"></div><div class="sheet tall" style="height:88%">
<div class="sh-title"><h3>Что играем</h3><span class="caption tnum">занятие 24:18</span></div>
<div class="now" data-note="4"><div class="m"><span>Сейчас</span><b>Ре мажор, 3 октавы · ещё 7 мин</b></div><span class="stopb">Остановить</span></div>
<div class="sh-list">
<div class="sh-sec">Произведения <em>сегодня 1</em></div>
<div class="it"><div class="m"><b>Концерт ля минор, 1 ч.</b><small>А. Вивальди</small></div><span class="pl done"><svg class="ic"><use href="#i-check2"/></svg>15 мин</span></div>
<div class="it"><div class="m"><b>Юмореска</b><small>А. Дворжак</small></div><span class="pl part">7 мин</span></div>
<div class="it"><div class="m"><b>Чардаш</b><small>В. Монти</small></div></div>
<div class="sh-sec">Гаммы</div>
<div class="it"><div class="m"><b>Ре мажор, 3 октавы</b></div><span class="pl run">идёт</span></div>
<div class="it"><div class="m"><b>Соль мажор, 3 октавы</b></div></div>
<div class="it"><div class="m"><b>Ля минор, 2 октавы</b></div></div>
</div></div>'''))
s4.append(state('Сначала — занятие', stage(False, '', seg('play') + '<div class="plate"><span class="dok"></span>Играйте…</div><div class="ring quiet"></div>' + ctrls(bookmark('idle'), tag('idle'), key('idle'))) + tabbar() + '''<div class="scrim"></div><div class="sheet">
<h3 style="font-size:22px;font-weight:800;margin-bottom:8px">Сначала — занятие</h3>
<p class="muted" style="font-size:15px;line-height:1.5" data-note="5">Время на произведения, гаммы и этюды идёт внутри занятия. Начните его — и выберите, что играть.</p>
<button class="start" style="margin-top:20px" data-note="6"><svg class="ic"><use href="#i-watch"/></svg>Начать занятие</button>
<div class="ghost">Не сейчас</div></div>'''))
notes4 = '''
<li data-n="1"><b>Список по разделам, «сегодня» — таблеткой с галочкой и временем</b>: сыгранное видно формой, а порядок от отметок не меняется — рука помнит, где что. <span class="tag">постоянство</span></li>
<li data-n="2"><b>Цель — одна строка из семи кнопок внизу листа</b>, приходит только после выбора элемента: до выбора решать нечего. <span class="tag">прогрессивное раскрытие</span></li>
<li data-n="3"><b>«Начать · 15 мин» — с числом внутри</b>: видно, что именно запустится; три касания от Live до подхода. <span class="tag">обратная связь</span></li>
<li data-n="4"><b>Идущий подход — сверху листа с «Остановить»</b>; выбор другого сразу его заменяет, без диалога. <span class="tag">меньше шагов</span></li>
<li data-n="5"><b>Без занятия — объяснение одной фразой</b>, а не пустой список: понятно, почему выбрать нельзя. <span class="tag">пустые состояния</span></li>
<li data-n="6"><b>«Начать занятие» — та же живая кнопка, что на «Занятиях»</b>; Live остаётся, лист сменяется выбором. <span class="tag">закон Якоба</span></li>
'''

# ---------------- Screen 5: landscape ----------------
land_stage = f'''<div class="stage off z-in" style="--cy:50%"><div class="bd" style="background-position:center 55%"></div>
<div class="tint" style="background:radial-gradient(circle at 26% 52%,rgba(71,201,126,.34) 0,rgba(71,201,126,.12) 30%,rgba(71,201,126,.04) 60%)"></div>
<div class="veil" style="background:radial-gradient(circle at 26% 52%,rgba(12,12,16,.6) 0,rgba(12,12,16,.3) 28%,transparent 55%)"></div>
<div class="shade" style="height:120px"></div>
<div class="ring lit" style="left:26%;top:22px;--rd:296px;--nf:176px" data-note="1">{note_html('A','','4')}</div>
<div class="topbar dim" style="left:auto;right:24px;width:330px"><div class="seg"><span class="on" style="min-width:96px">Игра</span><span style="min-width:96px">Настройка</span></div><div class="gear"><i><svg class="ic"><use href="#i-gear"/></svg></i></div></div>
<div class="readout" style="left:auto;right:24px;width:330px;top:132px" data-note="2"><span class="word"><span class="zd"></span>в строе</span><span class="cents">+3</span></div>
<div class="ctrls" style="left:auto;right:18px;width:352px;bottom:14px" data-note="3">{bookmark('run', dim=True)}{key('idle', dim=True)}{tag('run', dim=True)}</div>
</div>'''
s5 = [state('В строе', land_stage)]
notes5 = '''
<li data-n="1"><b>Кольцо слева на всю высоту</b> — на пюпитре боком оно остаётся самым крупным и узнаётся так же, как в портрете. <span class="tag">постоянство</span></li>
<li data-n="2"><b>Слово и цифры — справа на уровне центра кольца</b>: взгляд скачет по одной горизонтали. <span class="tag">близость</span></li>
<li data-n="3"><b>Нижний ряд тот же, что в портрете, в правой колонке</b>; вкладок нет — с Live в landscape уходят поворотом телефона. <span class="tag">сходство</span></li>
'''

def article(aid, title, goal, phone_html, notes, cls=''):
    return f'''<article class="screen {cls}" id="{aid}">
  <h2 class="screen-title">{title}</h2>
  <p class="screen-goal">{goal}</p>
  <div class="screen-body"><div class="phone-col">{phone_html}</div><ol class="notes">{notes}</ol></div>
</article>'''

body = f'''
<div class="doc">
  <header class="doc-head">
    <h1>Violin Journey · Live</h1>
    <p class="doc-meta">
      <b>Задача:</b> играть и краем глаза видеть ноту и попадание; ничего не трогать, пока звучит скрипка.
      <b>Для кого:</b> скрипач, телефон на пюпитре в 50–80 см, глаза в нотах или на грифе.
      <b>Допущения:</b> комната за Live — дом владельца с эмулятора; в залах всё то же, меняется только картина. Вкладок четыре: «Занятия · Live · Репертуар · Записи». Ядро (кольцо, нота, слово, цифры, свет, числа домена) не меняется — меняются приборы вокруг и слова. Двигаются только кольцо (дыхание и волна) и полоса записи.
    </p>
  </header>
  <div class="toolbar" role="toolbar" aria-label="Режимы просмотра">
    <button type="button" id="btn-notes" aria-pressed="true">Номера</button>
    <button type="button" id="btn-theme" aria-pressed="true">Тёмная подложка</button>
    <button type="button" id="btn-thumb" aria-pressed="false">Зона большого пальца</button>
  </div>

  <section class="block">
    <h2>Что сейчас (снято с эмулятора, звучит A4)</h2>
    <div class="compare">
      <div class="shot"><img src="{NOW}" alt="Live сейчас, нота A4 в строе">
        <span class="shot-mark" style="left:52%;top:7%">1</span>
        <span class="shot-mark" style="left:50%;top:81%">2</span>
        <span class="shot-mark" style="left:17%;top:86%">3</span>
        <span class="shot-mark" style="left:80%;top:86%">4</span>
        <span class="shot-mark" style="left:56%;top:93%">5</span>
      </div>
      <ol class="issues">
        <li><span class="n">1</span><span class="sev sev-major">важно</span><b>Кленовая планка «Play | Tune» — тёплое оранжевое пятно наверху.</b> Боковым зрением её тон близок к янтарному «рядом», а цвет на Live должен значить только интонацию.</li>
        <li><span class="n">2</span><span class="sev sev-major">важно</span><b>Клавиша записи не читается как кнопка:</b> серая кость с серо-синей точкой, того же веса, что соседние ярлыки.</li>
        <li><span class="n">3</span><span class="sev sev-minor">мелочь</span><b>«Repertoire» на Live</b> станет тем же словом, что новая вкладка «Репертуар», хотя делает другое — выбирает, что играть сейчас.</li>
        <li><span class="n">4</span><span class="sev sev-minor">мелочь</span><b>Три разные формы в одном ряду</b> (закладка с вырезом, круг, ярлык с вырезом) и мелкие серые подписи на полупрозрачном фоне — около 3 : 1.</li>
        <li><span class="n">5</span><span class="sev sev-minor">мелочь</span><b>Нижняя панель в полную силу, пока звучит нота:</b> фиолетовая пилюля «Live» — второе по яркости пятно после кольца.</li>
      </ol>
    </div>
  </section>

  <section class="block">
    <h2>Карта экранов</h2>
    <div class="flow-row"><a class="flow-node" href="#s-play">Live · Игра</a><span class="flow-arrow">→<i>клавиша</i></span><a class="flow-node" href="#s-rec">Запись</a><span class="flow-arrow">→<i>стоп</i></span><span class="flow-node">Экран записи <small>records.html</small></span></div>
    <div class="flow-row"><a class="flow-node" href="#s-play">Live · Игра</a><span class="flow-arrow">→<i>«Что играю»</i></span><a class="flow-node" href="#s-sheet">Лист «Что играем»</a><span class="flow-arrow">·</span><span class="flow-arrow">→<i>без занятия</i></span><a class="flow-node" href="#s-sheet">«Сначала — занятие»</a></div>
    <div class="flow-row"><a class="flow-node" href="#s-play">Live · Игра</a><span class="flow-arrow">⇄<i>переключатель</i></span><a class="flow-node" href="#s-tune">Настройка</a><span class="flow-arrow">·</span><span class="flow-arrow">→<i>шестерёнка</i></span><span class="flow-node">Настройки <small>start.html</small></span><span class="flow-arrow">·</span><span class="flow-arrow">→<i>ярлык идущего занятия</i></span><span class="flow-node">Занятия + лист «Закончить» <small>practice-sheets.html</small></span></div>
  </section>

  <main class="screens">
    {article('s-play', '1. Live · Игра', '<b>Главное действие:</b> играть — касаний не нужно &nbsp;·&nbsp; <b>За 3 секунды краем глаза:</b> «нота A, зелёный — в строе».', phone(s1), notes1)}
    {article('s-rec', '2. Запись дубля', '<b>Главное действие:</b> остановить запись той же клавишей &nbsp;·&nbsp; <b>За 3 секунды:</b> «пишется 1:24, лента почти вся зелёная».', phone(s2), notes2)}
    {article('s-tune', '3. Настройка', '<b>Главное действие:</b> подтянуть струну до зелёной пилюли &nbsp;·&nbsp; <b>За 3 секунды:</b> «струна A, чуть выше, бегунок правее зелёного».', phone(s3), notes3)}
    {article('s-sheet', '4. Лист «Что играем»', '<b>Главное действие:</b> «Начать · 15 мин» &nbsp;·&nbsp; <b>За 3 секунды:</b> «концерт сегодня уже сыгран, юмореску начинал».', phone(s4), notes4)}
    {article('s-land', '5. Landscape', '<b>Главное действие:</b> играть &nbsp;·&nbsp; <b>За 3 секунды:</b> то же, что в портрете.', phone(s5, land=True), notes5, cls='wide')}
  </main>

  <section class="block">
    <h2>Что вынесено и что переименовано</h2>
    <table class="moved">
      <thead><tr><th>Элемент</th><th>Куда</th><th>Почему</th></tr></thead>
      <tbody>
        <tr><td>Закладка «Репертуар»</td><td>Стеклянная карточка «Что играю · выбрать»; во время подхода — бумага с названием и остатком</td><td>Вкладка теперь называется «Репертуар»; одно слово для двух разных действий путает</td></tr>
        <tr><td>«Открыть репертуар» в пустом листе «Что играем»</td><td>Ведёт на вкладку «Репертуар»</td><td>Репертуар больше не живёт внутри «Записей»</td></tr>
        <tr><td>Фигурные вырезы закладки и ярлыка к клавише</td><td>Две одинаковые скруглённые карточки</td><td>Одна форма у двух соседей одного уровня; «живое» отличается бумагой, а не вырезом</td></tr>
        <tr><td>Кленовая планка переключателя, колки струн, деревянная линейка</td><td>Дымчатое стекло с костью; шкала — светлая линия</td><td>Тёплое дерево в верхней части экрана спорило с янтарной зоной</td></tr>
      </tbody>
    </table>
  </section>

  <section class="block">
    <h2>Проверки</h2>
    <ul>
      <li><b>Контраст:</b> %CONTRAST%</li>
      <li><b>Касания:</b> переключатель 44 (+ отступы до 48), шестерёнка 48, струны 58, карточки 60, клавиша 76, строки листа 58, кнопки цели 44, «Начать» 56.</li>
      <li><b>Цвет не единственный признак:</b> точка / стрелка вверх / стрелка вниз у слова; «рядом» и «мимо» различаются ещё и силой свечения; фиксация струны — замок и заливка; «готово» — обод и галочка; сыгранное сегодня — таблетка с галочкой.</li>
      <li><b>Касаний во время игры нет:</b> пока звучит нота, всё, что можно нажать, приглушено до 0,38, включая вкладки; кроме «готово» ничего не приходит.</li>
      <li><b>Движение:</b> кольцо (дыхание, волна) и пульс точки записи — ничего больше; комната замирает, пока свет погашен.</li>
    </ul>
  </section>

  <section class="block">
    <h2>Расходится со спекой</h2>
    <ul>
      <li><b>3.1 и 3.27</b> — «активный сегмент залит акцентным цветом» и «кленовая планка»: здесь стекло с костяным бегунком. Колки струн и деревянная линейка «Настройки» — тоже стекло.</li>
      <li><b>3.28</b> — закладка называется «Что играю», а не «Репертуар»; форма — скруглённая карточка, не нотная закладка с раздвоенным краем. «Открыть репертуар» ведёт на вкладку.</li>
      <li><b>3.12</b> — ярлык занятия: скруглённая карточка той же формы, что закладка, без выреза к клавише. Смысл и поведение прежние.</li>
      <li><b>3.6 / 3.27 / раздел 4</b> — нижняя панель вкладок приглушается вместе с остальными приборами, пока свет погашен (до этого её не трогали).</li>
      <li><b>3.4</b> — NoMicPermission: заголовок из онбординга «Дайте мне услышать скрипку», кнопка ниже центра, кольцо скрыто.</li>
      <li><b>3.5</b> — подсказка «цель зафиксирована: D · 294 Гц · тап — снять» сокращена до «D · 294 Гц · тап — снять»; в тишине в кольце бледный силуэт ноты струны.</li>
    </ul>
    <p style="margin-top:10px"><b>Открытые вопросы:</b> приглушать ли вкладки — проверить с пюпитра; нужен ли силуэт ноты в «Настройке» или он спутается с настоящей нотой (на телефоне).</p>
  </section>
</div>
'''

contrast = 'нота на погашенной комнате ≈ 16 : 1; слово «в строе» #47C97E на тёмном ≈ 8 : 1, «выше» #E5B03C ≈ 8.8 : 1, «ниже» #E8565C ≈ 4.9 : 1; цифры #A39FB5 ≈ 6.8 : 1; текст на стекле (плашки, карточки) #E6E4EE ≈ 9 : 1 даже поверх светлого дерева, мелкие подписи #C9C5D6 ≈ 6.8 : 1; чернила на кости #2A2430 / #F1EEE6 ≈ 13 : 1, подпись #6E635C на кости ≈ 5.0 : 1.'
body = body.replace('%CONTRAST%', contrast)

html = f'''<!doctype html>
<html lang="ru" data-theme="dark">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Violin Journey · Live</title>
<link rel="preconnect" href="https://fonts.googleapis.com">
<link href="https://fonts.googleapis.com/css2?family=Manrope:wght@400;500;600;700;800&display=swap" rel="stylesheet">
<style>{style}{live_css}</style>
</head>
<body>
{symbols}
{body}
<script>{ui_script}</script>
</body>
</html>'''
open(OUT, 'w').write(html)
print('written', OUT, len(html))
