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
    'ru': {
        '01-live-intune': 'Попадание видно цветом',
        '02-live-flat': 'Выше или ниже —<br>с одного взгляда',
        '03-tune': 'Настройте<br>четыре струны',
        '04-practice': 'Каждая минута<br>занятий на счету',
        '05-scale': 'Ноты гамм приложение<br>рисует само',
        '06-journey': 'Занятия ведут<br>по залам мира',
        '07-home': 'Такты обставляют<br>ваш дом',
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
h1 {{ margin: 0 0 70px; padding: 0 60px; text-align: center; font-size: 76px; line-height: 1.15;
  font-weight: 750; letter-spacing: -0.5px; }}
img {{ width: 800px; border-radius: 56px;
  box-shadow: 0 30px 90px rgba(0,0,0,.55), 0 0 0 3px rgba(255,255,255,.08); }}
</style></head><body><h1>{caption}</h1><img src="file://{shot}"></body></html>"""


def main():
    lang = sys.argv[1] if len(sys.argv) > 1 else 'ru'
    raw = os.path.abspath(os.path.join(ROOT, 'docs/store/screenshots/raw', lang))
    out = os.path.abspath(os.path.join(ROOT, 'docs/store/screenshots', lang))
    os.makedirs(out, exist_ok=True)
    with tempfile.TemporaryDirectory() as tmp:
        for name, caption in CAPTIONS[lang].items():
            shot = os.path.join(raw, name + '.png')
            if not os.path.exists(shot):
                print('no shot', shot)
                continue
            page = os.path.join(tmp, name + '.html')
            with open(page, 'w', encoding='utf-8') as f:
                f.write(PAGE.format(font=FONT, w=W, h=H, caption=caption, shot=shot))
            subprocess.run([CHROME, '--headless', '--disable-gpu', '--hide-scrollbars', '--force-device-scale-factor=1',
                            f'--window-size={W},{H}', '--allow-file-access-from-files',
                            f'--screenshot={os.path.join(out, name + ".png")}', 'file://' + page],
                           check=True, capture_output=True)
            print(os.path.join('docs/store/screenshots', lang, name + '.png'))


if __name__ == '__main__':
    main()
