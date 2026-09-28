#!/usr/bin/env python3
"""Builds docs/redesign/mockups/journey-home.html from src.html (style, symbols, main script) + this body."""
import base64, os, re
HERE = os.path.dirname(os.path.abspath(__file__))
SP = os.path.dirname(HERE)
OUT = '/Users/danil/AndroidStudioProjects/ViolinTuner/docs/redesign/mockups/journey-home.html'
src = open(f'{SP}/src.html').read()
style = src[src.index('<style>') + 7:src.index('</style>')]
symbols = src[src.index('<svg width="0"'):src.index('</svg>', src.index('<svg width="0"'))]
script = src[src.index('(function(){\n  var root'):src.rindex('</script>')]
extra = open(f'{HERE}/extra.css').read()

def b64(p, mime='image/jpeg'):
    return f'data:{mime};base64,' + base64.b64encode(open(p, 'rb').read()).decode()

COVER = b64(f'{SP}/cover.jpg')
ROOM = b64(f'{SP}/room.jpg')
NOW_HOME = b64(f'{HERE}/now_home.jpg')

SB = '<div class="statusbar"><span>18:42</span><span class="sb-icons"><svg class="ic ic-s"><use href="#i-sb-signal"/></svg><svg class="ic" style="width:26px"><use href="#i-sb-battery"/></svg></span></div>'
HI = '<div class="home-indicator"></div>'

def ic(name, cls='ic', style=''):
    st = f' style="{style}"' if style else ''
    return f'<svg class="{cls}"{st}><use href="#{name}"/></svg>'

def bal(n, hot=False):
    return f'<span class="bal{" hot" if hot else ""} tnum">{ic("i-tact")}{n}</span>'

def topbar(title, right='', back=True, note=''):
    nb = f' data-note="{note}"' if note else ''
    b = f'<button class="ibtn" aria-label="Назад">{ic("i-chev-l")}</button>' if back else ''
    return f'<div class="topbar"{nb}>{b}<span class="t">{title}</span>{right}</div>'

SKY = ('<svg class="sky" viewBox="0 0 350 120" preserveAspectRatio="none"><path fill="rgba(20,16,40,.55)" d="M0 120V70h30V40h14v30h20V55h26v65z"/>'
       '<path fill="rgba(20,16,40,.8)" d="M60 120V62l40-30 40 30v58zM150 120V48h18V30l8-12 8 12v18h18v72zM230 120V66h50V50h24v16h46v54z"/>'
       '<g fill="#FFD48A"><rect x="80" y="80" width="6" height="9"/><rect x="112" y="80" width="6" height="9"/><rect x="166" y="70" width="5" height="8"/>'
       '<rect x="250" y="84" width="6" height="8"/><rect x="290" y="84" width="6" height="8"/></g></svg>')

def pcard(label, h, day=False, extra='', cls=''):
    d = ' day' if day else ''
    return f'<div class="pic pcard{d} {cls}" style="height:{h}px">{SKY}<span class="lbl">{label}</span>{extra}</div>'

def room(h, img=None, extra='', pos='center 55%', note=''):
    nb = f' data-note="{note}"' if note else ''
    return f'<div class="pic"{nb} style="height:{h}px;background-image:url({img or COVER});background-position:{pos}">{extra}</div>'

# предметы лавки — простые силуэты
ITEMS = {
 'lamp': '<svg width="56" height="64" viewBox="0 0 56 64"><path d="M16 8h24l8 18H8z" fill="#F2E6C8"/><rect x="26" y="26" width="4" height="30" fill="#6b5a4a"/><rect x="16" y="56" width="24" height="5" rx="2" fill="#6b5a4a"/><circle cx="28" cy="30" r="14" fill="rgba(255,210,140,.25)"/></svg>',
 'violin': '<svg width="40" height="70" viewBox="0 0 40 70"><rect x="18" y="2" width="4" height="22" fill="#2c2430"/><path d="M20 22c-9 0-12 6-10 12-4 3-5 10 0 16 3 4 7 6 10 6s7-2 10-6c5-6 4-13 0-16 2-6-1-12-10-12z" fill="#B8672A"/><path d="M17 38h6" stroke="#2c2430" stroke-width="2"/></svg>',
 'cat': '<svg width="64" height="48" viewBox="0 0 64 48"><ellipse cx="30" cy="34" rx="22" ry="12" fill="#D98A4A"/><circle cx="48" cy="24" r="10" fill="#D98A4A"/><path d="M41 18l2-9 5 6M52 15l4-7 1 9" fill="#D98A4A"/><path d="M8 38c-6-2-6-10 0-12" stroke="#D98A4A" stroke-width="4" fill="none"/></svg>',
 'metro': '<svg width="44" height="64" viewBox="0 0 44 64"><path d="M14 4h16l10 56H4z" fill="#7a4a2a"/><path d="M22 52 30 12" stroke="#E6E4EE" stroke-width="2"/><circle cx="28" cy="20" r="3" fill="#C9B45C"/></svg>',
 'plant': '<svg width="50" height="66" viewBox="0 0 50 66"><path d="M14 44h22l-3 20H17z" fill="#B8672A"/><path d="M25 44C25 30 12 26 8 14c10 2 16 10 17 18 1-12 8-22 18-26-2 14-10 22-18 38" fill="#5E9E6A"/></svg>',
 'candle': '<svg width="40" height="64" viewBox="0 0 40 64"><rect x="14" y="24" width="12" height="34" rx="2" fill="#F2E6C8"/><path d="M20 8c4 6 4 10 0 14-4-4-4-8 0-14z" fill="#FFC56A"/><circle cx="20" cy="16" r="12" fill="rgba(255,200,120,.25)"/><rect x="8" y="56" width="24" height="5" rx="2" fill="#C9B45C"/></svg>',
 'piano': '<svg width="70" height="56" viewBox="0 0 70 56"><rect x="4" y="6" width="62" height="40" rx="3" fill="#2C2430"/><rect x="8" y="26" width="54" height="10" fill="#F2F0EA"/><g fill="#2C2430"><rect x="14" y="26" width="3" height="6"/><rect x="22" y="26" width="3" height="6"/><rect x="34" y="26" width="3" height="6"/><rect x="42" y="26" width="3" height="6"/><rect x="50" y="26" width="3" height="6"/></g><rect x="8" y="46" width="4" height="8" fill="#2C2430"/><rect x="58" y="46" width="4" height="8" fill="#2C2430"/></svg>',
 'chand': '<svg width="64" height="60" viewBox="0 0 64 60"><path d="M32 0v14" stroke="#C9B45C" stroke-width="2"/><path d="M10 30c6 10 38 10 44 0" stroke="#C9B45C" stroke-width="3" fill="none"/><g fill="#FFD48A"><circle cx="10" cy="26" r="4"/><circle cx="22" cy="32" r="4"/><circle cx="42" cy="32" r="4"/><circle cx="54" cy="26" r="4"/></g><circle cx="32" cy="30" r="22" fill="rgba(255,210,140,.18)"/></svg>',
 'rug': '<svg width="72" height="40" viewBox="0 0 72 40"><path d="M14 6h44l12 28H2z" fill="#8a2f3a"/><path d="M20 12h32l7 16H13z" fill="none" stroke="#E0A070" stroke-width="2"/></svg>',
}

def item(kind, name, st, cls='', city=''):
    c = f'<span class="city">{city}</span>' if city else ''
    return f'<div class="item {cls}"><div class="art">{ITEMS[kind]}{c}</div><div class="nm">{name}</div>{st}</div>'

def price(n):
    return f'<span class="st tnum">{ic("i-tact")}{n}</span>'

def own(t):
    return f'<span class="st own">{t}</span>'

def screen(sid, title, goal, states, notes, caption=''):
    st = ''.join(states)
    cap = f'<p class="caption" style="color:var(--page-muted);max-width:390px;margin:0">{caption}</p>' if caption else ''
    ns = ''.join(f'<li data-n="{i+1}">{n}</li>' for i, n in enumerate(notes))
    return f'''
    <article class="screen" id="{sid}">
      <h2 class="screen-title">{title}</h2>
      <p class="screen-goal">{goal}</p>
      <div class="screen-body">
        <div class="phone-col">
          <div class="phone"><div class="phone-screen">
            {SB}
            {st}
            {HI}
          </div></div>
          {cap}
        </div>
        <ol class="notes">{ns}</ol>
      </div>
    </article>'''

def state(name, inner):
    return f'<div class="state" data-state="{name}">{inner}</div>'

def note(b, t, tag):
    return f'<b>{b}</b> — {t} <span class="tag">{tag}</span>'

# ============ 1. ПУТЕШЕСТВИЕ ============
def journey_state(name, enough, notes_on=True, finished=False):
    n = (lambda k: f' data-note="{k}"') if notes_on else (lambda k: '')
    right = (f'<button class="ibtn" aria-label="Карта"{n(5)}>{ic("i-map")}</button>'
             f'<button class="ibtn" aria-label="Паспорт">{ic("i-pass")}</button>')
    card = pcard('открытка Вены · Музикферайн', 236,
                 extra=f'<span class="pic-tag">остановка 4 из 16</span><span class="pic-full">{ic("i-expand","ic ic-s")}</span>')
    body = f'''
      {topbar('Путешествие', right)}
      <div class="app-scroll">
        <div{n(1)}>{card}</div>
        <div class="place-name">Вена</div>
        <div class="place-sub">Музикферайн · Австрия · с 20 сентября</div>
        <div class="fact">Золотой зал звучит так, что оркестры приезжают сюда записываться.</div>
        <div class="gap"></div>
        <div class="door"{n(3)}><div class="th" style="background-image:url({ROOM})"></div>
          <div class="main"><b>Войти в дом</b><small>Маленький деревянный домик</small></div>{ic("i-chev-r","ic chev")}</div>
        <div class="sec-row"{n(4)}><b>Пройдено</b><span class="tnum">4 из 16</span></div>
        <div class="strip">
          <div class="pc">{pcard('Зальцбург',76)}<span>Зальцбург</span><small>18 сентября</small></div>
          <div class="pc">{pcard('Милан',76,day=True)}<span>Милан</span><small>12 сентября</small></div>
          <div class="pc">{pcard('Кремона',76)}<span>Кремона</span><small>7 сентября</small></div>
        </div>
      </div>'''
    if finished:
        dock = f'''<div class="dock2"><div class="road-card"><div class="l1"><b>Мировое турне пройдено</b><span>новые города придут с обновлениями</span></div></div></div>'''
    elif enough:
        dock = f'''<div class="dock2"{n(2)}>
          <div class="road-card"><div class="l1"><span><b>до Праги</b> · поездом · 4 часа</span><span class="tnum">1 600</span></div></div>
          <button class="btn-main">{ic("i-case")}<span>В путь · Прага<small class="tnum">спишется 1 600 из 47 884</small></span></button></div>'''
    else:
        dock = f'''<div class="dock2"{n(2)}>
          <div class="road-card"><div class="l1"><span><b>до Праги</b> · поездом · 4 часа</span><span class="tnum"><b>472</b> / 1 600</span></div>
          <div class="thin"><i style="width:29.5%"></i></div></div>
          <button class="btn-tonal tnum">не хватает 1 128 · примерно 3 занятия</button></div>'''
    return state(name, body + dock)

s_journey = screen('s-journey', '1. Путешествие',
  '<b>Главное действие:</b> «В путь» &nbsp;·&nbsp; <b>За 3 секунды:</b> «я в Вене, до Праги хватает, домой — вот дверь».',
  [journey_state('Хватает', True), journey_state('Не хватает', False, False), journey_state('Маршрут пройден', True, False, True)],
  [note('Открытка — первым и крупно', 'путешествие — это «где я»; остановка и счётчик «4 из 16» лежат на самой картинке, лишних строк над ней нет.', 'иерархия'),
   note('«В путь» — внизу, во всю ширину, с ценой и остатком', 'решение «тратить или копить» принимается там, где стоит кнопка; видно, сколько спишется и сколько было.', 'закон Фиттса'),
   note('Дверь «Войти в дом» — карточкой с комнатой', 'владелец жаловался, что из путешествия неясно, как попасть домой: миниатюра комнаты узнаётся без слов.', 'узнавание'),
   note('Лента пройденных — ниже, свежие слева', 'история не спорит с дорогой вперёд, но всегда под рукой одним свайпом.', 'прогрессивное раскрытие'),
   note('«Карта» и «Паспорт» — иконками в шапке', 'их открывают иногда, поэтому не в зоне пальца; баланс не дублируется в шапке — он стоит у кнопки «В путь», где решают, тратить ли.', 'частота')],
  'Не хватает — кнопка тональная и сообщает, а не запрещает: сколько и примерно сколько занятий.')

# ============ 2. ДОРОГА, ПРИБЫТИЕ, ШТАМП ============
MAPSVG = '''<svg viewBox="0 0 368 520" style="position:absolute;inset:0;width:100%;height:100%">
  <path d="M40 420 C 90 360, 110 330, 150 300" stroke="var(--accent)" stroke-width="3" fill="none" opacity=".9"/>
  <path d="M150 300 C 190 270, 220 250, 250 200" stroke="var(--accent)" stroke-width="3" fill="none" stroke-dasharray="1 0"/>
  <path d="M250 200 C 270 170, 300 150, 330 120" stroke="var(--border)" stroke-width="2" stroke-dasharray="5 6" fill="none"/>
  <g fill="var(--accent)"><circle cx="40" cy="420" r="6"/><circle cx="150" cy="300" r="8"/></g>
  <circle cx="250" cy="200" r="9" fill="none" stroke="var(--accent)" stroke-width="3"/>
  <g transform="translate(196 262) rotate(-36)"><rect x="-16" y="-7" width="32" height="14" rx="5" fill="#E6E4EE"/><rect x="-10" y="-4" width="6" height="5" fill="#131318"/><rect x="0" y="-4" width="6" height="5" fill="#131318"/></g>
  <g font-family="Manrope" font-size="13" font-weight="700" fill="#E6E4EE"><text x="160" y="318">Вена</text><text x="262" y="196">Прага</text></g>
</svg>'''
s_road = screen('s-road', '2. Дорога, прибытие, штамп',
  '<b>Главное действие:</b> поставить штамп &nbsp;·&nbsp; <b>За 3 секунды:</b> «я доехал до Праги».',
  [state('Дорога · 2 с', f'<div class="map-full" data-note="1">{MAPSVG}<div class="cap"><b>Вена → Прага</b><span>поездом · 4 часа</span></div></div>'),
   state('Прибытие', f'''<div class="app-scroll" style="display:flex;flex-direction:column;justify-content:center;padding-top:30px">
      <div data-note="2">{pcard("открытка Праги · Рудольфинум",300)}</div>
      <div class="place-name" style="text-align:center">Прага</div>
      <div class="place-sub" style="text-align:center">Рудольфинум · Чехия</div>
      <div class="fact" style="text-align:center">Зал Дворжака — дом Чешской филармонии с 1896 года.</div></div>
      <div class="dock2"><button class="btn-main" data-note="3">{ic("i-stamp")}<span>Поставить штамп</span></button></div>'''),
   state('Штамп', f'''<div class="app-scroll" style="display:flex;flex-direction:column;align-items:center;justify-content:center;text-align:center">
      <div class="label">Паспорт</div>
      <div class="stamp c2" style="width:170px;height:170px;font-size:18px;margin:26px 0" data-note="4">Прага<small>27.09.2026</small></div>
      <div style="font-size:22px;font-weight:800">Штамп № 5</div>
      <div class="muted tnum" style="margin-top:4px">До Праги — 1 600 тактов</div></div>
      <div class="dock2"><button class="btn-main">Готово</button></div>''')],
  [note('Дорога — без кнопок и выхода, 2 секунды', 'это единственное «кино» приложения; оно короткое, и отменять нечего: такты уже списаны при отправлении.', 'ожидание'),
   note('Открытка проявляется первой, текст — за ней', 'сначала радость места, потом слова; взгляд идёт сверху вниз по порядку появления.', 'пик-конец'),
   note('«Поставить штамп» — одно действие на экране', 'человек сам ставит точку в поездке — маленький ритуал делает награду своей.', 'эффект усилия'),
   note('Штамп крупно, номер и цена — под ним', 'итог в одной фразе: где, какой по счёту и чего стоило.', 'пик-конец')],
  '«Убрать анимации»: дорога — неподвижный кадр 0,8 с, открытка и штамп — сразу.')

# ============ 3. ОСТАНОВКА ============
def stop_state(name, notes_on=True, full=False):
    n = (lambda k: f' data-note="{k}"') if notes_on else (lambda k: '')
    if full:
        return state(name, f'''<div class="full pcard" style="background:linear-gradient(180deg,#1d1a3c 0%,#46306a 45%,#b56a5a 78%,#e0a070 100%)">{SKY.replace('class="sky"','class="sky" style="height:40%"')}<span class="lbl" style="position:absolute;left:50%;top:44%;transform:translate(-50%,-50%);color:rgba(255,255,255,.7);font-size:13px;font-weight:700">панорама Вены · потяните — осмотреться</span></div>
          <div class="full-top">{'<button class="ibtn" style="color:#E6E4EE">'+ic("i-collapse")+'</button>'}<span class="t">Вена · Музикферайн</span></div>
          <div class="full-bot"{n(5)}><div class="halves"><span class="seg"><span class="on">вечер</span><span>день</span></span><span class="seg"><span class="on">снаружи</span><span>внутри</span></span></div><small>Щипок — ближе · потяните — осмотреться</small></div>''')
    return state(name, f'''
      {topbar('Вена', bal('47 884'))}
      <div class="app-scroll">
        <div{n(1)}>{pcard('открытка Вены · Музикферайн',236, extra='<span class="seg" style="position:absolute;left:8px;bottom:8px;top:auto;right:auto"><span class="on">вечер</span><span>день</span></span><span class="pic-full">'+ic("i-expand","ic ic-s")+'</span>')}</div>
        <div class="place-sub" style="margin-top:12px">Музикферайн · Австрия · 4 из 16 · с 20 сентября</div>
        <div class="fact">Золотой зал звучит так, что оркестры приезжают сюда записываться.</div>
        <div class="sec-row"{n(3)}><b>Дополнения</b><span>маленькие цели в дороге</span></div>
        <div class="addon"><div class="main"><b>Второе время суток</b><small>день вместо вечера</small></div><span class="done-chip">{ic("i-check","ic",'width:13px;height:13px;stroke-width:3')}открыто</span></div>
        <div class="addon"><div class="main"><b>Второй вид</b><small>Золотой зал изнутри</small></div><button class="price tnum">{ic("i-tact")}400</button></div>
        <div class="addon"><div class="main"><b>Сувенир в паспорт</b><small>наклейка рядом со штампом</small></div><button class="price tnum">{ic("i-tact")}150</button></div>
      </div>
      <div class="dock2"{n(2)}>
        <button class="btn-main">{ic("i-string")}<span>Играть здесь<small>Live на сцене Золотого зала</small></span></button>
        <div class="halves"><button class="btn-out"{n(4)}>{ic("i-home","ic ic-s")}Домой</button></div>
      </div>''')

s_stop = screen('s-stop', '3. Остановка',
  '<b>Главное действие:</b> «Играть здесь» &nbsp;·&nbsp; <b>За 3 секунды:</b> «это Вена, я могу играть на этой сцене».',
  [stop_state('Остановка'), stop_state('На весь экран', True, True)],
  [note('Открытка с переключателем прямо на картинке', '«вечер | день» меняет то, на что смотришь, — поэтому он лежит на ней, а не в списке ниже.', 'близость'),
   note('«Играть здесь» — главная кнопка остановки', 'зал ценен тем, что на его сцене можно играть; действие внизу, под пальцем.', 'главная задача'),
   note('Дополнения — строками с ценой справа', 'три маленькие цели читаются как список покупок: что, зачем, сколько; купленное — чипом «открыто».', 'сканируемость'),
   note('«Домой» — второй кнопкой, контурной', 'дверь домой нужна на каждом экране дороги, но тише главного.', 'иерархия'),
   note('Полный экран — чипы внизу, подсказка прячется', 'картине отдан весь экран, управление — у большого пальца и исчезает само через 3 с.', 'минимализм')])

# ============ 4. КАРТА ============
BIGMAP = '''<svg viewBox="0 0 350 560" style="width:100%;height:100%">
  <rect width="350" height="560" rx="18" fill="#1a1a26"/>
  <path d="M60 470 C90 430 100 400 120 380 S150 330 170 320 S200 290 215 270 S240 240 255 225" stroke="var(--accent)" stroke-width="3" fill="none"/>
  <path d="M255 225 C265 205 262 190 250 175" stroke="var(--accent)" stroke-width="2.5" stroke-dasharray="6 6" fill="none"/>
  <path d="M250 175 C230 160 210 150 190 140 S150 130 120 150 S80 180 70 140 S100 90 150 80 S260 70 300 60" stroke="#4a4758" stroke-width="2" stroke-dasharray="4 7" fill="none"/>
  <g fill="var(--accent)"><circle cx="60" cy="470" r="6"/><circle cx="120" cy="380" r="6"/><circle cx="170" cy="320" r="6"/><circle cx="215" cy="270" r="6"/></g>
  <circle cx="255" cy="225" r="10" fill="var(--accent)"/><circle cx="255" cy="225" r="18" fill="none" stroke="var(--accent)" stroke-opacity=".35" stroke-width="6"/>
  <circle cx="250" cy="175" r="8" fill="#131318" stroke="var(--accent)" stroke-width="2.5"/>
  <g fill="#4a4758"><circle cx="190" cy="140" r="5"/><circle cx="120" cy="150" r="5"/><circle cx="70" cy="140" r="5"/><circle cx="150" cy="80" r="5"/><circle cx="300" cy="60" r="5"/></g>
  <g font-family="Manrope" font-size="12" font-weight="700" fill="#A39FB5"><text x="70" y="490">Дом</text><text x="130" y="398">Кремона</text><text x="180" y="338">Милан</text><text x="224" y="288">Зальцбург</text></g>
  <g font-family="Manrope" font-weight="800" fill="#E6E4EE"><text x="272" y="232" font-size="15">Вена</text><text x="264" y="170" font-size="13" fill="var(--accent)">Прага →</text></g>
</svg>'''
s_map = screen('s-map', '4. Карта маршрута',
  '<b>Главное действие:</b> посмотреть, где я на маршруте &nbsp;·&nbsp; <b>За 3 секунды:</b> «я здесь, следующая — Прага».',
  [state('Карта', f'''{topbar('Карта', '<span class="caption tnum" style="padding-right:12px">4 из 16</span>')}
     <div style="flex:1;padding:0 20px 20px;min-height:0" data-note="1">{BIGMAP}</div>
     <div class="dock2" style="padding-top:0"><div class="door" data-note="2"><div class="th pcard" style="position:relative;overflow:hidden">{SKY}</div><div class="main"><b>Вена</b><small>вы здесь · до Праги хватает</small></div>{ic("i-chev-r","ic chev")}</div></div>''')],
  [note('Три вида линии: пройдено, следующее, впереди', 'сплошная, акцентный пунктир и тихий пунктир — различие формой, а не только цветом.', 'доступность'),
   note('Внизу — карточка «вы здесь»', 'ответ на главный вопрос карты не надо искать на схеме; тап — на экран остановки.', 'зона большого пальца'),
   note('Подписи только у пройденных, текущей и следующей', 'шестнадцать названий сразу превратили бы схему в список; дальние — точками.', 'когнитивная нагрузка')])

# ============ 5. ПАСПОРТ ============
stamps = (f'<div class="stamp">Кремона<small>07.09</small></div><div class="stamp c2">Милан<small>12.09</small></div>'
          f'<div class="stamp c3">Зальцбург<small>18.09</small></div><div class="stamp c4" data-note="1">Вена<small>20.09</small></div>'
          f'<div class="stamp fut" data-note="2">Прага<small>следующая</small></div><div class="stamp fut">Лейпциг</div>'
          f'<div class="stamp fut">Берлин</div><div class="stamp fut">Амстердам</div><div class="stamp fut">Париж</div>'
          + ''.join(f'<div class="stamp fut">{c}</div>' for c in ['Лондон','Петербург','Москва','Нью-Йорк','Буэнос-Айрес','Токио','Сидней']))
s_pass = screen('s-pass', '5. Паспорт',
  '<b>Главное действие:</b> полюбоваться собранным &nbsp;·&nbsp; <b>За 3 секунды:</b> «4 штампа из 16».',
  [state('Паспорт', f'''{topbar('Паспорт', '<span class="caption tnum" style="padding-right:12px">4 из 16</span>')}
     <div class="app-scroll"><div class="pass-grid">{stamps}</div></div>''')],
  [note('Штампы цветные, слегка повёрнутые, с датой', 'это коллекция, а не таблица: каждая отметка — как настоящая, узнаётся по месту и дню.', 'эстетика и удобство'),
   note('Впереди — пунктирный круг с названием', 'будущее видно, но форма другая: отличие не держится только на яркости.', 'доступность'),
   note('Три в ряд, крупно', 'штампы по 96 dp читаются без увеличения и легко нажимаются — тап открывает остановку.', 'закон Фиттса')])

# ============ 6. ДОМ ============
def home_state(name, gift=False, notes_on=True, outside=False, full=False):
    n = (lambda k: f' data-note="{k}"') if notes_on else (lambda k: '')
    if full:
        return state(name, f'''<div class="full" style="background-image:url({COVER});background-position:center"></div>
          <div class="full-top">{'<button class="ibtn" style="color:#E6E4EE">'+ic("i-collapse")+'</button>'}<span class="t">Маленький деревянный домик</span><span class="seg"><span class="on">Комната</span><span>Снаружи</span></span></div>
          <div class="full-bot"><small>Щипок — ближе · потяните — осмотреться</small></div>''')
    seg = '<span class="seg"><span class="on">Комната</span><span>Снаружи</span></span>' if not outside else '<span class="seg"><span>Комната</span><span class="on">Снаружи</span></span>'
    pic = (room(290, extra=f'{seg}<span class="pic-full">{ic("i-expand","ic ic-s")}</span>', pos='center 60%', note=1 if notes_on else '') if not outside else
           f'<div class="pic" style="height:290px;background:linear-gradient(#27304f,#3a3552 55%,#2d3a2a 55%,#22301f)"><span class="lbl" style="position:absolute;left:50%;top:48%;transform:translate(-50%,-50%);color:rgba(255,255,255,.7);font-size:12px;font-weight:700">домик снаружи · сад и яблоня</span>{seg}<span class="pic-full">{ic("i-expand","ic ic-s")}</span></div>')
    g = (f'<div class="gift"{n(3)}>{ic("i-gift","ic")}<div class="main"><b>Первый подарок ждёт в лавке</b><small>скрипка выйдет из футляра</small></div><button class="btn-sm">Забрать</button></div>' if gift else '')
    return state(name, f'''
      {topbar('Дом', bal('47 884', hot=True))}
      <div class="app-scroll">
        {pic}
        <div class="place-name">Маленький деревянный домик</div>
        <div class="place-sub">17 вещей · 5 привезено из путешествия</div>
        {g}
        <div class="next-home"{n(4)}><svg class="sil" viewBox="0 0 52 44" fill="none" stroke="currentColor" stroke-width="2"><path d="M4 42V18L26 4l22 14v24z"/><path d="M14 42V26h10v16M30 24h10v8H30z"/></svg>
          <div class="main"><b>Квартира с эркером</b><small>скоро · 6 000</small></div>{ic("i-chev-r","ic chev")}</div>
      </div>
      <div class="dock2"{n(2)}>
        <button class="btn-main">{ic("i-case")}<span>В дорогу<small>Вена → хватает до Праги</small></span></button>
        <div class="halves"><button class="btn-out">{ic("i-shop","ic ic-s")}Лавка</button><button class="btn-out"{n(5)}>{ic("i-chair","ic ic-s")}Обставить</button></div>
      </div>''')

issues_home = [
  ('major','важно','<b>Действия посередине и уезжают при прокрутке.</b> «On the road», «Shop», «Furnish» стоят под картинкой, в середине экрана; на маленьком телефоне часть уходит под сгиб.'),
  ('minor','мелочь','<b>Баланс в правом углу шапки</b> — далеко от решения «купить или ехать», которое принимается внизу.'),
  ('major','важно','<b>Лента «Brought back from the journey» режет подписи</b> («Carved wood…», «Portrait of a c…») и повторяет то, что есть в лавке и «Обставить».'),
  ('minor','мелочь','<b>Переключатель «Room | Outside» закрывает левый верх картинки</b> — там висит портрет, одна из вещей комнаты.'),
  ('minor','мелочь','<b>«All homes» — одинокая ссылка в самом низу</b>, рядом с карточкой следующего дома, который и так ведёт туда же.'),
]
marks_home = [(94,53),(86,9),(4,65),(38,15),(28,90)]
iss = ''.join(f'<li><span class="n">{i+1}</span><span class="sev sev-{s}">{l}</span>{t}</li>' for i,(s,l,t) in enumerate(issues_home))
shotmarks = ''.join(f'<span class="shot-mark" style="left:{x}%;top:{y}%">{i+1}</span>' for i,(x,y) in enumerate(marks_home))
now_block = f'''
  <section class="block">
    <h2>Что сейчас: экран «Дом» (снято с эмулятора)</h2>
    <div class="compare">
      <div class="shot"><img src="{NOW_HOME}" alt="Экран Дом сейчас">{shotmarks}</div>
      <ol class="issues">{iss}</ol>
    </div>
    <p style="margin-top:14px">Экран путешествия, лавку и «Обставить» снять не удалось — эмулятор отвечал с большой задержкой; они разобраны по спеке и макетам хэндоффа (<code>docs/design/project/home/project/Путешествие.dc.html</code>, <code>Дом.dc.html</code>).</p>
  </section>'''

s_home = screen('s-home', '6. Дом',
  '<b>Главное действие:</b> «В дорогу» &nbsp;·&nbsp; <b>За 3 секунды:</b> «это мой дом, в нём 17 вещей, до Праги хватает».',
  [home_state('Обычное'), home_state('Подарок ждёт', gift=True, notes_on=False), home_state('Снаружи', notes_on=False, outside=True), home_state('На весь экран', notes_on=False, full=True)],
  [note('Комната крупно (290 dp), переключатель — справа', 'картинка — главное; «Комната | Снаружи» уехал из-под портрета на стене в правый угол.', 'фигура и фон'),
   note('Три действия дома — закреплены внизу', '«В дорогу» во всю ширину, «Лавка» и «Обставить» по половине — всегда под пальцем, при любой прокрутке.', 'закон Фиттса'),
   note('Подарок — одной карточкой с кнопкой', 'лавка начинается с радости: забрать можно прямо отсюда, не ища вещь на полке.', 'пустые состояния'),
   note('Следующий дом — одна строка вместо карточки и ссылки', 'тап ведёт в «Дома»: отдельная ссылка «Все дома» больше не нужна.', 'простота'),
   note('Лента «Привезено из путешествия» ушла', 'городские вещи отмечены в лавке и «Обставить» таблеткой города — дублировать их третьим списком незачем.', 'когнитивная нагрузка')])

# ============ 7. ЛАВКА ============
wood_rows = (item('violin','Кремонская скрипка, 1720',price('9 000'),city='Кремона') + item('metro','Метроном',own('в комнате')) + item('candle','Подсвечник',price('120')))
music_rows = (item('piano','Пианино',own('привезут из Вены'),cls='far',city='Вена') + item('lamp','Настольная лампа',price('300')) + item('chand','Люстра',price('1 200'),city='Вена'))
cloth_rows = (item('cat','Рыжая кошка',own('в комнате')) + item('plant','Монстера',price('180')) + item('rug','Сливовый ковёр',price('100')))
def shop_state(name, gift=False, notes_on=True):
    n = (lambda k: f' data-note="{k}"') if notes_on else (lambda k: '')
    wr = (item('violin','Ученическая скрипка','<span class="st gift">подарок</span>') + item('metro','Метроном',price('240')) + item('candle','Подсвечник',price('120'))) if gift else wood_rows
    return state(name, f'''
      {topbar('Лавка', f'<span{n(1)}>'+bal('47 884')+'</span>')}
      <div class="chips2"{n(2)}><span class="chip2 on">Всё</span><span class="chip2">Инструменты</span><span class="chip2">Музыка</span><span class="chip2">Свет</span><span class="chip2">Мебель</span></div>
      <div class="app-scroll" style="padding-top:0">
        <div class="shelf wood"{n(3)}><h4>Лавка мастера <span>инструменты</span></h4><div class="shelf-row">{wr}</div></div>
        <div class="shelf wood"><h4>Ноты и музыка <span>1 из 12 куплено</span></h4><div class="shelf-row"{n(4)}>{music_rows}</div></div>
        <div class="shelf cloth"><h4>Рынок <span>растения и мелочи</span></h4><div class="shelf-row">{cloth_rows}</div></div>
      </div>''')

s_shop = screen('s-shop', '7. Лавка',
  '<b>Главное действие:</b> выбрать вещь &nbsp;·&nbsp; <b>За 3 секунды:</b> «вот полки, у каждой вещи — цена или статус».',
  [shop_state('Полки'), shop_state('Первый подарок', gift=True, notes_on=False)],
  [note('Баланс — в шапке, всегда на виду', 'цены на полках сравниваются с одним числом, которое не уезжает при прокрутке.', 'узнавание'),
   note('Чипы рядов — одной строкой под шапкой', 'девять рядов не складываются в сетку фильтров; «Всё» первым, остальное — свайпом.', 'закон Хика'),
   note('Полки с досками, вещь нарисована как в комнате', 'узнаётся сразу, без подписи; материал полки подсказывает, какой это ряд.', 'сходство'),
   note('Статус под именем — одно из пяти', 'цена, «в комнате», «куплено», «подарок», «привезут из Вены»; вещь ещё не достигнутого города бледная — дорога зовёт.', 'постоянство')])

# ============ 8. КАРТОЧКА ВЕЩИ И ПРИМЕРКА ============
def card_state(name, enough=True, notes_on=True):
    n = (lambda k: f' data-note="{k}"') if notes_on else (lambda k: '')
    btn = (f'<div class="halves" style="margin-top:14px"><button class="btn-out"{n(3)}>Примерить</button><button class="btn-main" style="flex:1.4;min-height:50px;border-radius:25px;font-size:16px" {n(2)[1:] if notes_on else ""}><span class="tnum">Купить · 300</span></button></div>'
           if enough else
           f'<div class="halves" style="margin-top:14px"><button class="btn-out">Примерить</button><button class="btn-tonal" style="flex:1.4;min-height:50px;font-size:15px">не хватает 560</button></div><div class="caption" style="text-align:center;margin-top:8px">ещё примерно 2 занятия</div>')
    left = ('<div class="kv"'+n(4)+'><span>После покупки</span><b class="tnum">останется 47 584</b></div><div class="kv"><span>До Праги</span><b>всё ещё хватает</b></div>' if enough else
            '<div class="kv"><span>Баланс</span><b class="tnum">472</b></div><div class="kv"><span>До Праги</span><b class="tnum">1 128</b></div>')
    return state(name, f'''
      {topbar('Лавка', bal('47 884' if enough else '472'))}
      <div class="app-scroll" style="padding-top:0"><div class="shelf wood"><div class="shelf-row">{wood_rows}</div></div></div>
      <div class="scrim"></div>
      <div class="sheet">
        <div class="item-art"{n(1)}><div style="transform:scale(1.9)">{ITEMS["lamp"]}</div></div>
        <div style="font-size:22px;font-weight:800;margin-top:14px">Настольная лампа</div>
        <div class="muted" style="font-size:14px">Тёплый свет над нотами по вечерам.</div>
        <div class="kv" style="margin-top:10px"><span>Место</span><b>На столе, слева</b></div>
        {left}
        {btn}
      </div>''')

def try_state(name):
    return state(name, f'''<div class="full" style="background-image:url({COVER});background-position:30% center;filter:brightness(.88)"></div>
      <div class="try-halo" style="left:36px;top:300px;width:120px;height:120px" data-note="5"></div>
      <div style="position:absolute;left:66px;top:318px;z-index:3">{ITEMS["lamp"]}</div>
      <div class="full-top"><button class="ibtn" style="color:#E6E4EE">{ic("i-chev-l")}</button><span class="t">Примерка · Настольная лампа</span><span class="seg"><span class="on">вечер</span><span>день</span></span></div>
      <div class="full-bot" style="padding-bottom:30px"><div class="halves" style="width:100%"><button class="btn-out" style="flex:0 0 auto;padding:0 18px;color:#E6E4EE;border-color:rgba(255,255,255,.35)">Убрать</button><button class="btn-main" style="flex:1;min-height:52px;border-radius:26px;font-size:16px" data-note="6"><span class="tnum">Купить · 300</span></button></div></div>''')

s_card = screen('s-card', '8. Карточка вещи и примерка',
  '<b>Главное действие:</b> «Купить» &nbsp;·&nbsp; <b>За 3 секунды:</b> «лампа, стоит на столе, после покупки до Праги всё равно хватит».',
  [card_state('Хватает'), card_state('Не хватает', enough=False, notes_on=False), try_state('Примерка')],
  [note('Вещь крупно, на своей полке и живая', 'карточка — та же витрина, только ближе: лампа горит, кошка дышит.', 'непрерывность'),
   note('«Купить · 300» — главная, справа', 'цена в самой кнопке: человек видит, что подтверждает; «Примерить» — рядом, тише.', 'ясность'),
   note('«Примерить» перед «Купить»', 'посмотреть вещь в своей комнате до траты — честный путь, без импульсной покупки.', 'без тёмных паттернов'),
   note('«Останется» и «до Праги» — строками', 'не запрещаем тратить, а показываем цену для дороги: решение взвешенное.', 'прозрачность'),
   note('Примерка — тёплый ореол, комната темнеет на 12 %', 'вещь выделена светом, а не рамкой: видно замену, а не наложение.', 'эффект изоляции'),
   note('«Убрать» и «Купить» — внизу в один ряд', 'решение принимается там, где лежит палец, не выходя из примерки.', 'закон Фиттса')])

# ============ 9. ОБСТАВИТЬ ============
def opt(kind, on=False, scale=.7):
    ck = f'<span class="ck">{ic("i-check")}</span>' if on else ''
    return f'<div class="opt{" on" if on else ""}"><div style="transform:scale({scale})">{ITEMS[kind]}</div>{ck}</div>'
s_arr = screen('s-arrange', '9. Обставить',
  '<b>Главное действие:</b> заменить вещь на месте &nbsp;·&nbsp; <b>За 3 секунды:</b> «сверху комната, снизу места — тап меняет сразу».',
  [state('Обставить', f'''
     {topbar('Обставить', '<span class="seg solid" style="margin-right:8px"><span class="on">Комната</span><span>Снаружи</span></span>')}
     <div class="arr-pic pic" style="background-image:url({COVER});background-position:center 58%" data-note="1"><div style="position:absolute;left:44px;top:84px;width:70px;height:60px;border:2px dashed var(--accent);border-radius:10px" data-note="2"></div></div>
     <div class="app-scroll" style="padding-top:6px">
       <div class="slot"><div class="slot-h"><b>На столе, слева</b><a href="#" data-note="3">в лавке 4 →</a></div><div class="opts"><div class="opt none">пусто</div>{opt('lamp',True)}{opt('candle')}{opt('metro')}</div></div>
       <div class="slot"><div class="slot-h"><b>Питомец</b><a href="#">в лавке 6 →</a></div><div class="opts"><div class="opt none">пусто</div>{opt('cat',True,.9)}</div></div>
       <div class="slot"><div class="slot-h"><b>Ковёр</b><a href="#">в лавке 3 →</a></div><div class="opts"><div class="opt none">пусто</div>{opt('rug',True,.8)}</div></div>
       <div class="slot gone" data-note="4"><div class="slot-h"><b>Эркер</b><span class="caption">нет в этом доме · ждёт 1 вещь</span></div></div>
     </div>''')],
  [note('Комната сверху, неподвижно при прокрутке мест', 'замена видна сразу, не надо листать вверх, чтобы проверить результат.', 'обратная связь'),
   note('Место, которое меняешь, обведено на картинке', 'связь «строка ↔ угол комнаты» видна глазом, а не только по названию места.', 'близость'),
   note('«в лавке 4 →» — у каждого места', 'нечего поставить — путь в лавку сразу отфильтрован по этому месту.', 'контекст'),
   note('Места, которых нет в доме, — внизу бледно', 'купленное не пропадает: видно, что ждёт переезда.', 'прозрачность')])

# ============ 10. ДОМА ============
s_houses = screen('s-houses', '10. Дома',
  '<b>Главное действие:</b> увидеть следующий дом &nbsp;·&nbsp; <b>За 3 секунды:</b> «живу в домике, следующий — квартира, 6 000».',
  [state('Дома', f'''
     {topbar('Дома', bal('47 884'))}
     <div class="app-scroll">
       <div class="house here" data-note="1"><div class="hp" style="background-image:url({ROOM})"></div><div class="main"><span class="here-chip">здесь живу</span><b>Маленький деревянный домик</b><small>17 вещей</small></div></div>
       <div class="house"><div class="hp" style="background-image:url({ROOM});filter:grayscale(.6) brightness(.7)"></div><div class="main"><b>Съёмная комната</b><small>первый дом · можно вернуться</small></div></div>
       <div class="house far" data-note="2"><div class="hp"><svg width="52" height="44" viewBox="0 0 52 44" fill="none" stroke="currentColor" stroke-width="2"><path d="M4 42V18L26 4l22 14v24z"/><path d="M14 42V26h10v16M30 24h10v8H30z"/></svg></div><div class="main"><b>Квартира с эркером</b><small class="tnum">6 000 · скоро</small></div></div>
       <div class="house far"><div class="hp"><svg width="52" height="44" viewBox="0 0 52 44" fill="none" stroke="currentColor" stroke-width="2"><path d="M2 42h48M8 42V20h36v22M4 20 26 6l22 14"/></svg></div><div class="main"><b>Дом у моря</b><small class="tnum">10 000 · скоро</small></div></div>
       <div class="house far"><div class="hp"><svg width="52" height="44" viewBox="0 0 52 44" fill="none" stroke="currentColor" stroke-width="2"><path d="M4 42V22L26 6l22 16v20z"/></svg></div><div class="main"><b>Шале</b><small class="tnum">15 000 · скоро</small></div></div>
     </div>''')],
  [note('Где живёте — рамкой и чипом «здесь живу»', 'текущий дом отмечен двумя признаками, а не только цветом.', 'доступность'),
   note('Дальние — силуэтом, с ценой и «скоро»', 'копить на то, что нельзя купить, приложение не предлагает: полосы накопления у них нет.', 'честность'),
   note('Список — как паспорт, по порядку цены', 'путь домов читается сверху вниз, как маршрут городов.', 'сходство')])

# ============ страница ============
flow = '''
  <section class="block">
    <h2>Карта экранов</h2>
    <div class="flow-row"><span class="flow-node">Занятия <small>окно «где мы»</small></span><span class="flow-arrow">→<i>дома</i></span><a class="flow-node" href="#s-home">Дом</a>
      <span class="flow-arrow">→</span><a class="flow-node" href="#s-shop">Лавка</a><span class="flow-arrow">→<i>тап по вещи</i></span><a class="flow-node" href="#s-card">Карточка · примерка</a></div>
    <div class="flow-row"><a class="flow-node" href="#s-home">Дом</a><span class="flow-arrow">→</span><a class="flow-node" href="#s-arrange">Обставить</a><span class="flow-arrow">→<i>в лавке 4</i></span><a class="flow-node" href="#s-shop">Лавка по месту</a>
      <span class="flow-arrow">·</span><a class="flow-node" href="#s-home">Дом</a><span class="flow-arrow">→<i>следующий дом</i></span><a class="flow-node" href="#s-houses">Дома</a></div>
    <div class="flow-row"><a class="flow-node" href="#s-home">Дом</a><span class="flow-arrow">→<i>В дорогу · заставка</i></span><a class="flow-node" href="#s-journey">Путешествие</a>
      <span class="flow-arrow">→<i>В путь</i></span><a class="flow-node" href="#s-road">Дорога · прибытие · штамп</a></div>
    <div class="flow-row"><a class="flow-node" href="#s-journey">Путешествие</a><span class="flow-arrow">→<i>открытка</i></span><a class="flow-node" href="#s-stop">Остановка</a><span class="flow-arrow">→<i>Играть здесь</i></span><span class="flow-node">Live <small>на сцене</small></span>
      <span class="flow-arrow">·</span><a class="flow-node" href="#s-map">Карта</a><span class="flow-arrow">·</span><a class="flow-node" href="#s-pass">Паспорт</a></div>
    <p style="margin-top:10px">Дом и путешествие — «переезд», а не шаг вглубь: «назад» с обоих ведёт на «Занятия» (3.25). Нижней панели на этих экранах нет — главное действие занимает её место.</p>
  </section>'''

moved = '''
  <section class="block">
    <h2>Что вынесено и куда</h2>
    <table class="moved"><thead><tr><th>Элемент</th><th>Куда</th><th>Почему</th></tr></thead><tbody>
      <tr><td>Лента «Привезено из путешествия» на «Доме»</td><td>Убрана; город — таблеткой на вещи в лавке и в «Обставить»</td><td>Третий список тех же вещей; подписи в ней обрезались</td></tr>
      <tr><td>Ссылка «Все дома»</td><td>Строка «следующий дом» ведёт в «Дома»</td><td>Два входа в одно место рядом</td></tr>
      <tr><td>«В дорогу», «Лавка», «Обставить» под картинкой</td><td>Закреплены внизу экрана</td><td>Не уезжают при прокрутке, в зоне пальца</td></tr>
      <tr><td>Карточка пути с полосой на экране путешествия</td><td>Над кнопкой «В путь» в нижней зоне; полоса — только когда не хватает</td><td>Число и решение рядом; полная полоса ничего не сообщает</td></tr>
      <tr><td>«Карта», «Паспорт» (кнопки)</td><td>Иконки в шапке (как в 3.25)</td><td>Нужны иногда</td></tr>
      <tr><td>«Домой» с экрана остановки</td><td>Контурная кнопка под «Играть здесь»</td><td>Дверь домой — на каждом экране дороги</td></tr>
    </tbody></table>
  </section>'''

checks = '''
  <section class="block">
    <h2>Проверки</h2>
    <ul>
      <li><b>Контраст:</b> текст 14.7 : 1 на фоне, вторичный 6.5 : 1 на карточке; надпись акцентной кнопки 7.2 : 1; текст на тёмном стекле поверх самого светлого дерева комнаты ≈ 8.6 : 1; подпись на ткани рынка 10.6 : 1; подпись на доске полки (#F1EAE0 на #3b2a1e) 11.4 : 1, статус вещи на доске (#C9BBA8 на #2e2118) 8.3 : 1; бледная вещь ещё не достигнутого города — 0.45 непрозрачности, но её статус «привезут из Вены» дублирован словами.</li>
      <li><b>Касания:</b> кнопки нижней зоны 60 и 50 dp, иконки шапки 48, строки дополнений 60, плитки «Обставить» 72, штампы 96.</li>
      <li><b>Движение (3.16):</b> на каждом экране живёт одна картина (комната или открытка), второе движение — только дыхание ореола в примерке. Кнопки не анимируются.</li>
      <li><b>Цвета зон</b> не используются нигде: путешествие и дом — не оценка.</li>
    </ul>
  </section>
  <section class="block">
    <h2>Расходится со спекой</h2>
    <ul>
      <li><b>3.24 / 3.25, экран «Дом»:</b> действия закреплены внизу (сейчас — под картинкой); лента «Привезено из путешествия» и ссылка «Все дома» убраны; «Комната | Снаружи» — в правом верхнем углу.</li>
      <li><b>3.23, экран путешествия:</b> карточка пути стала нижней зоной с кнопкой; в кнопке — сколько спишется; «не хватает» дополнено «примерно N занятий» (формулировка уже есть у карточки вещи, 3.24).</li>
      <li><b>3.27, остановка:</b> «Играть здесь» — главная кнопка внизу, «Домой» — контурная под ней.</li>
      <li><b>3.24, «Обставить»:</b> обводка места на картинке комнаты при выборе строки.</li>
      <li>Навигация: четыре вкладки «Занятия · Live · Репертуар · Записи» — общее решение для всех макетов; на экранах этого раздела вкладок нет, как и сейчас.</li>
    </ul>
  </section>
  <section class="block ideas">
    <h2>Идеи на потом (не в спеке)</h2>
    <ul><li>Чип «по карману» в лавке — показать только то, что можно купить сейчас.</li></ul>
  </section>'''

head = f'''<!doctype html>
<html lang="ru" data-theme="dark">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Путешествие и Дом</title>
<link rel="preconnect" href="https://fonts.googleapis.com">
<link href="https://fonts.googleapis.com/css2?family=Manrope:wght@400;500;600;700;800&display=swap" rel="stylesheet">
<style>{style}{extra}</style>
</head>
<body>
{symbols}
  <symbol id="i-map" viewBox="0 0 24 24"><path d="M9 4 3 6v14l6-2 6 2 6-2V4l-6 2z"/><path d="M9 4v14M15 6v14"/></symbol>
  <symbol id="i-pass" viewBox="0 0 24 24"><rect x="5" y="3" width="14" height="18" rx="2"/><circle cx="12" cy="10" r="3"/><path d="M9 16h6"/></symbol>
  <symbol id="i-case" viewBox="0 0 24 24"><rect x="3" y="8" width="18" height="12" rx="3"/><path d="M9 8V6a2 2 0 0 1 2-2h2a2 2 0 0 1 2 2v2"/><path d="M10 14h6M13 11l3 3-3 3"/></symbol>
  <symbol id="i-shop" viewBox="0 0 24 24"><path d="M3 9h18l-2-5H5z"/><path d="M3 9c0 1.7 1.3 3 3 3s3-1.3 3-3c0 1.7 1.3 3 3 3s3-1.3 3-3c0 1.7 1.3 3 3 3s3-1.3 3-3"/><path d="M5 12v8h14v-8"/></symbol>
  <symbol id="i-chair" viewBox="0 0 24 24"><path d="M6 11V7a3 3 0 0 1 3-3h6a3 3 0 0 1 3 3v4"/><path d="M4 11h16v5H4zM6 16v4M18 16v4"/></symbol>
  <symbol id="i-expand" viewBox="0 0 24 24"><path d="M4 9V4h5M20 9V4h-5M4 15v5h5M20 15v5h-5"/></symbol>
  <symbol id="i-collapse" viewBox="0 0 24 24"><path d="M9 4v5H4M15 4v5h5M9 20v-5H4M15 20v-5h5"/></symbol>
  <symbol id="i-stamp" viewBox="0 0 24 24"><path d="M9 3h6v5l-1 4h-4L9 8z"/><path d="M4 14h16v4H4zM6 21h12"/></symbol>
  <symbol id="i-check" viewBox="0 0 24 24"><path d="m5 12 5 5L20 7"/></symbol>
  <symbol id="i-gift" viewBox="0 0 24 24"><rect x="3" y="8" width="18" height="4" rx="1"/><path d="M5 12v9h14v-9M12 8v13M12 8S10.5 3 8 4s0 4 4 4zm0 0s1.5-5 4-4 0 4-4 4z"/></symbol>
</svg>
<div class="doc">
  <header class="doc-head">
    <h1>Violin Journey · Путешествие и Дом</h1>
    <p class="doc-meta"><b>Задача:</b> тихая игра поверх занятий — увидеть, где я, решить «ехать или обставлять», порадоваться собранному. <b>Для кого:</b> скрипач после занятия, спокойно, двумя руками; сюда приходят с «Занятий». <b>Допущения:</b> иллюстрации не перерисованы — комната снята с эмулятора, открытки городов — заглушки с подписью, вещи — упрощённые силуэты. Баланс 47 884 — данные эмулятора; «не хватает» — выдуманный баланс 472. Система — та же, что у «Занятий» (вариант A).</p>
  </header>
  <div class="toolbar" role="toolbar" aria-label="Режимы просмотра">
    <button type="button" id="btn-notes" aria-pressed="true">Номера</button>
    <button type="button" id="btn-theme" aria-pressed="true">Тёмная подложка</button>
    <button type="button" id="btn-thumb" aria-pressed="false">Зона большого пальца</button>
  </div>
  {now_block}
  {flow}
  <main class="screens">
    {s_home}{s_shop}{s_card}{s_arr}{s_houses}{s_journey}{s_road}{s_stop}{s_map}{s_pass}
  </main>
  {moved}
  {checks}
</div>
<script>
{script}
</script>
</body>
</html>'''
head = head.replace('{symbols}', '')
open(OUT, 'w').write(head)
print('ok', len(head))
