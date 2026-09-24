#!/usr/bin/env python3
"""Store screenshots: a caption over each raw emulator shot, 1080 × 2160 (Google Play takes no more than 2:1).

    python3 tools/store/frames.py ru|en

Reads docs/store/screenshots/raw/<lang>/NN-name.png (taken on the emulator at `wm size 1080x2160`, seeded by
tools/store/seed.py) and writes docs/store/screenshots/<lang>/NN-name.png, rendered by headless Chrome in
Manrope, the app's own font."""
import os
import subprocess
import sys
import tempfile

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..')
CHROME = '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome'
FONT = os.path.abspath(os.path.join(ROOT, 'app/src/main/res/font/manrope_variable.ttf'))
W, H = 1080, 2160

CAPTIONS = {
    # (caption, the line under it); the order is the story: hear the note, the game, the music kept
    'ru': {
        '01-live': ('Улучшайте точность', 'проверяйте каждую ноту — цвет виден издалека'),
        '02-recap': ('Занятия — это игра', 'за минуты и чистые ноты — такты'),
        '03-journey': ('Путешествуйте<br>по залам мира', 'такты везут вас из города в город'),
        '04-home': ('Обставляйте<br>свой дом', 'вещи из лавки и из поездок'),
        '05-repertoire': ('Весь репертуар<br>под рукой', 'произведения, гаммы, этюды —<br>и время на каждое'),
        '06-takes': ('Записывайте дубли', 'и смотрите, как растёт чистота'),
        '07-sound': ('Обрабатывайте звук', 'эквалайзер, компрессор, зал —<br>и сразу «Поделиться»'),
        '08-tune': ('Настраивайте скрипку', 'четыре струны, авто или с фиксацией'),
    },
    'en': {
        '01-live-intune': 'See your pitch<br>by colour',
        '02-live-flat': 'Sharp or flat<br>at a glance',
        '03-tune': 'Tune the<br>four strings',
        '04-practice': 'Every practice<br>minute counts',
        '05-scale': 'Scales come with<br>their notes drawn',
        '06-journey': "Practice takes you to<br>the world's concert halls",
        '07-home': 'Bars furnish<br>your home',
    },
}

PAGE = """<!doctype html><html><head><meta charset="utf-8"><style>
@font-face {{ font-family: Manrope; src: url('file://{font}'); font-weight: 200 800; }}
html, body {{ margin: 0; width: {w}px; height: {h}px; overflow: hidden; }}
body {{
  background: radial-gradient(120% 60% at 50% 0%, #4a3a8c 0%, #241d45 45%, #121117 100%);
  font-family: Manrope, sans-serif; color: #f1eefb;
}}
body {{ display: flex; flex-direction: column; align-items: center; justify-content: center; }}
h1 {{ margin: 0; padding: 0 60px; text-align: center; font-size: 76px; line-height: 1.15;
  font-weight: 750; letter-spacing: -0.5px; }}
p {{ margin: 20px 0 0; padding: 0 60px; text-align: center; font-size: 40px; line-height: 1.3; font-weight: 500;
  color: #c9c2e8; }}
.caption {{ margin-bottom: 64px; }}
img {{ width: 800px; border-radius: 56px;
  box-shadow: 0 30px 90px rgba(0,0,0,.55), 0 0 0 3px rgba(255,255,255,.08); }}
</style></head><body><div class="caption"><h1>{caption}</h1>{sub}</div><img src="file://{shot}"></body></html>"""


def main():
    lang = sys.argv[1] if len(sys.argv) > 1 else 'ru'
    raw = os.path.abspath(os.path.join(ROOT, 'docs/store/screenshots/raw', lang))
    out = os.path.abspath(os.path.join(ROOT, 'docs/store/screenshots', lang))
    os.makedirs(out, exist_ok=True)
    with tempfile.TemporaryDirectory() as tmp:
        for name, caption in CAPTIONS[lang].items():
            caption, sub = caption if isinstance(caption, tuple) else (caption, None)
            shot = os.path.join(raw, name + '.png')
            if not os.path.exists(shot):
                print('no shot', shot)
                continue
            page = os.path.join(tmp, name + '.html')
            with open(page, 'w', encoding='utf-8') as f:
                f.write(PAGE.format(font=FONT, w=W, h=H, caption=caption, sub=f'<p>{sub}</p>' if sub else '', shot=shot))
            subprocess.run([CHROME, '--headless', '--disable-gpu', '--hide-scrollbars', '--force-device-scale-factor=1',
                            f'--window-size={W},{H}', '--allow-file-access-from-files',
                            f'--screenshot={os.path.join(out, name + ".png")}', 'file://' + page],
                           check=True, capture_output=True)
            print(os.path.join('docs/store/screenshots', lang, name + '.png'))


if __name__ == '__main__':
    main()
