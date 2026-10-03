#!/usr/bin/env python3
"""Store screenshots: a caption over a phone with a raw shot of the app, rendered by headless Chrome in Manrope, the app's
own font. The shots go in as they are: no piece of the interface is lifted out or enlarged.

    python3 tools/store/frames.py ru [01 02 …]             # RuStore (and Google Play): 1080 × 1920, 9:16
    python3 tools/store/frames.py en appstore [01 02 …]    # App Store: 1320 × 2868, the 6.9" iPhone

RuStore reads docs/store/screenshots/raw/<lang>-v2/*.png — the Android emulator at its own 1080 × 2400 — and writes
docs/store/screenshots/<lang>/; the App Store reads raw/<lang>-ios/*.png — the iPhone 17 Pro Max simulator, 1320 × 2868 —
and writes docs/store/screenshots/appstore-<lang>/. Both are seeded by tools/store/seed.py, the session of the recap by
tools/store/session.py (docs/store/listing.md, «Как переснять»)."""
import os
import subprocess
import sys
import tempfile

from PIL import Image

ROOT = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..'))
CHROME = '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome'
FONT = os.path.join(ROOT, 'app/src/main/res/font/manrope_variable.ttf')
W = 1080  # the page is laid out 1080 wide and scaled to the store's width
# per store: the page's height, the file's size, the raw shot's size, the screen inside the phone (px of the page), the
# rounding of the screen's corners as a share of its width, the size of the type (the tall iPhone page takes it larger),
# the folders, and what the raw shot needs
STORES = {
    'rustore': dict(h=1920, out=(1080, 1920), shot=(1080, 2400), phone=680, corner=58 / 680, type=1, raw='{}-v2', dir='{}',
                    # the demo status bar of the emulator draws a second wifi icon (its mobile slot): its box, painted
                    # over with the bar's colour
                    extra_wifi=(810, 44, 867, 92), island=False),
    'appstore': dict(h=2347, out=(1320, 2868), shot=(1320, 2868), phone=820, corner=55 / 440, type=1.3, raw='{}-ios',
                     dir='appstore-{}', extra_wifi=None, island=False),
}

# backgrounds by what the screen is about: the zone's green, the app's violet, the warm of the road and the home
THEMES = {
    'green': ('#1d5a40', '#10251c', '#0d0f12', '#7be3a8'),
    'violet': ('#4a3a8c', '#221b42', '#111016', '#c3b2ff'),
    'warm': ('#7a4f2a', '#2e2017', '#111016', '#ffc98a'),
    'amber': ('#6d5420', '#2a2112', '#111016', '#ffcf6b'),
}

# A frame: its shot (or three phones in a row for the colours, each with its word under it), the theme, the caption with
# its accent word in *…* and «|» for a line break, the line under it, and badges.
FRAMES = {
    'ru': [
        dict(name='01-live', shot='01-live', theme='green', caption='Фальшь видно *сразу*',
             sub='поставьте телефон на пюпитр и играйте', badges=['без рекламы', 'без аккаунта']),
        dict(name='02-colors', rows=[('02-green', 'В строе', 'до ±8 центов', '#5ee39a'),
                                     ('02-amber', 'Чуть выше', 'от 8 до 20 центов', '#ffc659'),
                                     ('02-red', 'Мимо', 'дальше 20 центов', '#ff6b6b')],
             theme='green', caption='Не нужно *вглядываться*', sub='цвет виден краем глаза,|пока вы смотрите в ноты'),
        dict(name='03-analysis', shot='09-analysis', theme='amber', caption='Разбор каждой *записи*',
             sub='какая нота уходит и на сколько'),
        dict(name='04-tune', shot='03-tune', theme='green', caption='Тюнер для *скрипки*',
             sub='G, D, A, E — струна находится сама'),
        dict(name='05-practice', shot='04-practice', theme='violet', caption='Занимайтесь|*каждый день*',
             sub='таймер, неделя и серия дней подряд'),
        dict(name='06-journey', shot='06-journey', theme='warm', caption='Путешествуйте|по *залам мира*',
             sub='Кремона, Вена, Париж, Токио — 16 остановок'),
        dict(name='07-home', shot='07-home', theme='warm', caption='Обставьте *свой дом*', sub='вещи из лавки и из поездок'),
        dict(name='08-repertoire', shot='08b-repertoire', theme='violet', caption='Весь репертуар —|*под рукой*',
             sub='произведения, гаммы, этюды, штрихи|и сколько времени на каждое'),
        dict(name='09-takes', shot='08-piece', theme='violet', caption='Слышно, как растёт|*чистота*',
             sub='ноты, заметки и дубли к каждому произведению'),
        dict(name='10-events', shot='10-calendar', theme='violet', caption='Уроки и концерты —|*в календаре*',
             sub='программа выступления и записи с него'),
    ],
    'en': [
        dict(name='01-live', shot='01-live', theme='green', caption='Intonation|you can *see*',
             sub='put your phone on the stand and play', badges=['no ads', 'no account']),
        dict(name='02-colors', rows=[('02-green', 'In tune', 'within ±8 cents', '#5ee39a'),
                                     ('02-amber', 'A bit sharp', '8 to 20 cents', '#ffc659'),
                                     ('02-red', 'Off', 'beyond 20 cents', '#ff6b6b')],
             theme='green', caption='No need to *stare*', sub='you catch the colour out of the corner|of your eye while reading the music'),
        dict(name='03-analysis', shot='09-analysis', theme='amber', caption='Every *take* analysed',
             sub='which note drifts, and by how much'),
        dict(name='04-tune', shot='03-tune', theme='green', caption='A tuner|made for *violin*',
             sub='G, D, A, E — it finds the string itself'),
        dict(name='05-practice', shot='04-practice', theme='violet', caption='Practise|*every day*',
             sub='a timer, your week and a streak of days'),
        dict(name='06-journey', shot='06-journey', theme='warm', caption="Travel the world's|*concert halls*",
             sub='Cremona, Vienna, Paris, Tokyo — 16 stops'),
        dict(name='07-home', shot='07-home', theme='warm', caption='Furnish *your home*',
             sub='things from the shop and from your travels'),
        dict(name='08-repertoire', shot='08b-repertoire', theme='violet', caption='Your whole repertoire|*at hand*',
             sub='pieces, scales, études, bow strokes|and the time spent on each'),
        dict(name='09-takes', shot='08-piece', theme='violet', caption='Watch your|intonation *improve*',
             sub='sheet music, notes and takes|for every piece'),
        # the calendar is below the fold of the iPhone's «Practice», and the simulator cannot scroll: the concert itself
        dict(name='10-events', shot='10b-event', theme='violet', caption='Get ready for|*your concert*',
             sub='the programme, notes and recordings|in one place'),
    ],
}

PAGE = """<!doctype html><html><head><meta charset="utf-8"><style>
@font-face {{ font-family: Manrope; src: url('file://{font}'); font-weight: 200 800; }}
html, body {{ margin: 0; width: {w}px; height: {h}px; overflow: hidden; }}
body {{ position: relative; font-family: Manrope, sans-serif; color: #f4f1fb;
  background: radial-gradient(90% 55% at 50% 62%, {c1}aa 0%, transparent 70%),
              radial-gradient(130% 70% at 50% 0%, {c1} 0%, {c2} 48%, {c3} 100%); }}
.caption {{ position: absolute; left: 0; right: 0; top: calc(64px * {t}); text-align: center; }}
h1 {{ margin: 0 auto; max-width: 1040px; font-size: calc(76px * {t}); line-height: 1.08; font-weight: 800; letter-spacing: -1.5px; }}
h1 em {{ font-style: normal; color: {accent}; }}
p {{ margin: calc(18px * {t}) auto 0; max-width: 1000px; font-size: calc(38px * {t}); line-height: 1.25; font-weight: 500; color: rgba(244,241,251,.72); }}
.badges {{ display: flex; justify-content: center; gap: 14px; margin-top: calc(22px * {t}); }}
.badge {{ font-size: calc(28px * {t}); font-weight: 600; padding: 8px 22px; border-radius: 99px; color: {accent};
  background: rgba(255,255,255,.07); border: 2px solid rgba(255,255,255,.10); }}
.phone {{ position: absolute; border-radius: 74px; background: #08080b; padding: 16px;
  box-shadow: 0 40px 120px rgba(0,0,0,.6), 0 0 0 3px rgba(255,255,255,.10), inset 0 0 0 2px rgba(255,255,255,.06); }}
.phone img {{ display: block; }}
.island {{ position: absolute; left: 50%; transform: translateX(-50%); background: #000; }}
.meaning {{ position: absolute; text-align: center; }}
.meaning b {{ display: block; font-size: calc(52px * {t}); font-weight: 800; letter-spacing: -1px; }}
.meaning span {{ display: block; margin-top: 4px; font-size: calc(32px * {t}); line-height: 1.2; font-weight: 500; color: rgba(244,241,251,.72); }}
</style></head><body>
<div class="caption"><h1>{caption}</h1><p>{sub}</p>{badges}</div>
{body}
</body></html>"""


def phone(store, shot, left, top, width):
    shot_w, shot_h = store['shot']
    height = width * shot_h / shot_w
    radius = store['corner'] * width
    # the Dynamic Island of the iPhone: the simulator's shot draws the screen under it
    island = (f'<div class="island" style="top:{16 + width * 11 / 440:.0f}px; width:{width * 126 / 440:.0f}px; '
              f'height:{width * 37 / 440:.0f}px; border-radius:{width * 19 / 440:.0f}px"></div>') if store['island'] else ''
    return (f'<div class="phone" style="left:{left - 16}px; top:{top}px; border-radius:{radius + 16:.0f}px">'
            f'<img src="file://{shot}" style="width:{width}px; height:{height:.0f}px; border-radius:{radius:.0f}px">{island}</div>')


def cleaned(store, raw, shot, tmp):
    """The raw shot as the store should see it: on Android, one wifi icon in its status bar instead of two."""
    image = Image.open(os.path.join(raw, shot + '.png')).convert('RGB')
    if store['extra_wifi']:
        left, top, right, bottom = store['extra_wifi']
        image.paste(image.getpixel((left - 6, (top + bottom) // 2)), store['extra_wifi'])
    path = os.path.join(tmp, shot + '.png')
    image.save(path)
    return path


def styled(text):
    return text.replace('*', '<em>', 1).replace('*', '</em>', 1).replace('|', '<br>')


def render(store, frame, raw, out, tmp):
    name = frame['name']
    h = store['h']
    shot_w, shot_h = store['shot']
    c1, c2, c3, accent = THEMES[frame['theme']]
    badges = frame.get('badges', [])
    lines = frame['caption'].count('|') + frame['sub'].count('|')
    top = (360 + 50 * bool(badges) + 70 * lines) * store['type']
    if 'rows' in frame:
        # the three colours side by side: Live as it glows, and what the colour means under each phone
        width, gap = 300, 20
        x = (W - 3 * (width + 32) - 2 * gap) / 2 + 16
        block = width * shot_h / shot_w + 32 + 36 + 110 * store['type']  # the phone, its frame, the gap and the two lines under it
        y = top + (h - top - block) * 0.45  # a little above the middle of what the caption leaves
        body = ''
        for shot, word, meaning, colour in frame['rows']:
            body += phone(store, cleaned(store, raw, shot, tmp), x, y, width)
            below = y + width * shot_h / shot_w + 32 + 36
            body += (f'<div class="meaning" style="left:{x - 16 - gap / 2:.0f}px; width:{width + 32 + gap}px; top:{below:.0f}px">'
                     f'<b style="color:{colour}">{word}</b><span>{meaning}</span></div>')
            x += width + 32 + gap
    else:
        body = phone(store, cleaned(store, raw, frame['shot'], tmp), (W - store['phone']) // 2, top, store['phone'])
    badges = '<div class="badges">' + ''.join(f'<span class="badge">{b}</span>' for b in badges) + '</div>' if badges else ''
    page = os.path.join(tmp, name + '.html')
    with open(page, 'w', encoding='utf-8') as f:
        f.write(PAGE.format(font=FONT, w=W, h=h, t=store['type'], c1=c1, c2=c2, c3=c3, accent=accent, caption=styled(frame['caption']),
                            sub=styled(frame['sub']), badges=badges, body=body))
    target = os.path.join(out, name + '.png')
    out_w, out_h = store['out']
    subprocess.run([CHROME, '--headless', '--disable-gpu', '--hide-scrollbars', f'--force-device-scale-factor={out_w / W}',
                    f'--window-size={W},{h}', '--allow-file-access-from-files', f'--screenshot={target}', 'file://' + page],
                   check=True, capture_output=True)
    image = Image.open(target)
    if image.size != (out_w, out_h):  # a pixel of rounding at a fractional scale
        image.crop((0, 0, out_w, out_h)).save(target)
    # RuStore takes up to 3 MB a file
    if os.path.getsize(target) > 3_000_000:
        Image.open(target).convert('RGB').save(target[:-4] + '.jpg', quality=92)
        os.remove(target)
        target = target[:-4] + '.jpg'
    return target


def main():
    args = sys.argv[1:]
    lang = args.pop(0) if args else 'ru'
    store = STORES[args.pop(0) if args and args[0] in STORES else 'rustore']
    only = set(args)
    raw = os.path.join(ROOT, 'docs/store/screenshots/raw', store['raw'].format(lang))
    out = os.path.join(ROOT, 'docs/store/screenshots', store['dir'].format(lang))
    os.makedirs(out, exist_ok=True)
    with tempfile.TemporaryDirectory() as tmp:
        for frame in FRAMES[lang]:
            if only and frame['name'][:2] not in only:
                continue
            print(os.path.relpath(render(store, frame, raw, out, tmp), ROOT))


if __name__ == '__main__':
    main()
