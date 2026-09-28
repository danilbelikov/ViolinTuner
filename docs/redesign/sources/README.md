# Исходники макетов (архив)

Из этих файлов собирались страницы `../mockups/*.html` 27.09.2026. Пути внутри скриптов указывают на временную папку той сессии, поэтому **правь готовые HTML в `../mockups/` напрямую** — они самодостаточны. Этот архив нужен, если захочется пересобрать страницу целиком: поправь пути в начале скрипта.

| Папка / файл | Страница |
|---|---|
| `src.html`, `build.py` (+ `room.jpg`, `now3.jpg`, `now4.jpg`) | `practice.html` — `src.html` же был общей основой стилей для всех разделов |
| `live/gen.py` (+ `live_now.jpg`, `liveroom.jpg`, `cover.jpg`) | `live.html` |
| `rec/` | `records.html` |
| `rep/` | `repertoire.html` |
| `jh/` | `journey-home.html` |
| `start/assemble.py` | `start.html` |
| `sheets/build.py` | `practice-sheets.html` |
| `extract.py` | вытаскивает текст любой страницы в markdown: `python3 extract.py ../mockups/live.html` |

Проверка страницы — скилл `mobile-ux-design`: `bash ~/.claude/skills/mobile-ux-design/scripts/render.sh <файл>`.
