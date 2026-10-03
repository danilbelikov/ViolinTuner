# Проверенные находки ревью «Занятий»

Приложение к `../REVIEW.md`. Ревью 27.09.2026: 12 направлений и 4 добора по итогам критика полноты; каждую находку отдельный агент пытался опровергнуть (вердикты: confirmed — подтверждено как есть; adjusted — суть верна, поля поправлены проверяющим). Здесь всё как есть — с доказательствами, замерами и заметкой проверяющего; в `REVIEW.md` те же находки сведены в одну таблицу без повторов, со ссылками на id.

| Файл | Направление | Находок | Блокеры | Заметно | Мелочи |
|---|---|---|---|---|---|
| [header.md](header.md) | Шапка, трофеи, профиль, подарок | 16 | 0 | 5 | 11 |
| [card.md](card.md) | Карточка «где мы» | 15 | 0 | 6 | 9 |
| [action.md](action.md) | Главное действие, листы окончания, итог | 12 | 0 | 4 | 8 |
| [calendar.md](calendar.md) | Сводка, календарь, выбранный день, лист «Время за день» | 17 | 3 | 3 | 11 |
| [hierarchy.md](hierarchy.md) | Иерархия, порядок, сгиб, прыжки | 14 | 0 | 8 | 6 |
| [a11y.md](a11y.md) | Доступность: контраст, размеры, касания, TalkBack | 18 | 2 | 5 | 11 |
| [color.md](color.md) | Цвет и токены | 10 | 1 | 4 | 5 |
| [motion.md](motion.md) | Движение | 9 | 0 | 1 | 8 |
| [unity.md](unity.md) | Единство с другими экранами | 10 | 1 | 0 | 9 |
| [layouts.md](layouts.md) | Раскладки: landscape, 360×640, шрифт, языки, панели | 15 | 4 | 8 | 3 |
| [future.md](future.md) | Запас на будущее: «Гриф», напоминание, события | 18 | 0 | 12 | 6 |
| [states.md](states.md) | Состояния: пустое, город, итог, подарок, забытое | 19 | 1 | 8 | 10 |
| [gap1.md](gap1.md) | Добор 1 — касания | 8 | 0 | 1 | 7 |
| [gap2.md](gap2.md) | Добор 2 — листы в низком окне | 7 | 2 | 2 | 3 |
| [gap3.md](gap3.md) | Добор 3 — путь с ярлыка Live | 8 | 0 | 1 | 7 |
| [gap4.md](gap4.md) | Добор 4 — iOS | 10 | 1 | 5 | 4 |

Всего 206 находок, опровергнуто 2 (a11y, color — в своих файлах).

## Критик полноты

Хорошо покрыты: соответствие макетам по шапке, карточке, листам и календарю; иерархия и сгиб в портрете и landscape; крупный шрифт, маленький экран, переводы; контраст и TalkBack; цвет и токены (поиск сырых Color(0x…) в practice, JourneyWindowCard, SessionCard и AppBottomBar ничего не дал, утечки есть только у surfaceContainerHighest / surfaceContainerLow); движение (LocalReduceMotion учтён в кнопке, блике, огоньке, точке, RolledValue и итоге; исключение — GiftSheet, states-17); запас под «Гриф» и события. Вырез в landscape на Android закрыт через Scaffold contentWindowInsets = safeDrawing (MainActivity.kt:123-124), а на 12-landscape наложений нет. Невыснятые состояния (пустое, город, итог, «Подарок», диалог) разобраны по коду и макетам. Для итога визуальной сверки с recap/* почти нет: только states-05/06/07 и unity-07. Пробелы: целиком не проверен критерий «Касания»; геометрия листов в landscape посчитана, но не сверена с правилами M3 и уже противоречит сама себе; не пройден сквозной путь с ярлыка Live (spec 0.75); не рассмотрен iOS. Замечание к числам: в тексте задания статус-бар ≈43 dp и панель ≈86 dp, но на 04 и 17 статус-бар ≈52 dp, а панель ≈104 dp. Находки hierarchy и motion-05 (область прокрутки 52…810, сгиб 758) это учитывают; остальные расчёты «до сгиба» перед итоговым ревью стоит сверить с этими значениями. Находки a11y-16 и color-09 в сводке пропущены, их номеров нет.

Противоречия между находками — разобраны в `REVIEW.md`, раздел «Как читались противоречия»:

- calendar-01 против layouts-02 — лист «Время за день» в landscape. По calendar-01 лист встаёт наполовину (~205 dp), и видны только заголовок и степпер. По layouts-02 за краем только «Отмена», а «Сохранить» видна. По логике якорей M3 (PartiallyExpanded при содержимом выше половины окна) скорее прав calendar-01. Разрешить до того, как ставить «блокер» (пробел 2).
- header-13 против unity-09 — отступ шапки сверху. По header-13 16 dp на «Занятиях» — отклонение от хэндоффа (12 dp). По unity-09 16 dp — норма (как видимый верх Live), а выбивается «Записи» с 12 dp. Направление исправления противоположное.
- a11y-09 против header-09 — доля полосы уровня. a11y-09: TalkBack скажет «94 процента». header-09: полоса заполнена на 89 %. По spec 5.7 уровень 5 идёт от 25 до 50 ч, 47 ч 17 мин — это 89 %, и на 12-landscape полоса тоже на ≈89 %. Кроме того, озвучка процента прямо противоречит spec 3.13 («Процентов и очков нет»), так что a11y-09 — факт против спеки, а не только повтор.
- header-06 против layouts-14 — строка трофеев в landscape. По header-06 она не помещается по-русски уже с 250 ч («5 трофеев · следующий 500 ч»), текст переносится, и хвост пропадает. По layouts-14 обрезка начнётся с «6 трофеев · следующий 1000 ч», по Clip. Порог и механизм расходятся.
- layouts-13, a11y-02 и calendar-05 по-разному называют кегли автоподбора сводки на одном кадре 23 (360 dp): 16 / 14 sp против ≈15 / ≈13 / 18 sp. Перед цитированием в REVIEW.md свести к одному замеру.
- hierarchy-14 против future-01 / future-15 — высота напоминания о событии. hierarchy-14 берёт ≈100 dp, и тогда «кнопка уходит на 36 dp под сгиб». future-01/15 берут 68 и 140 dp, и тогда «видна 33 из 56 dp». Итоговому ревью нужна одна модель.
- card-03, action-04, color-05 и hierarchy-08 ругают акцентную строку «— в путь» и залитую акцентную таблетку. Но spec home.md 3.25 (строка 67) требует ровно этого: «строка … акцентом, таблетка акцентная». То же с motion-01: он против живой карточки, а motion-02 показывает, что 3.25/3.29 (более поздние разделы) её предписывают. Всё это вкус против спеки: решение потребует правки спеки, а не только кода.
- header-02 и header-15 ставят в вину, что у контура трофея нет подписи часов («50 ч», «1 ч»). Это принятое расхождение, plan-progress.md:10. При этом spec 3.13 для пустого состояния прямо пишет «только приглушённый «1 ч»», то есть план и спека спорят между собой. Это надо назвать, а не подавать как новую находку.
- action-05 и a11y-04 считают «Закончить занятие» слишком тихой. Spec 3.12 прямо требует контурную кнопку, и color-05 отмечает, что по макету она нейтральная. Решение против спеки нужно помечать как её изменение.
- states-11 («немецкие лапки» в practice_empty): текст дословно из spec 3.12, где „…“ — вложенные кавычки внутри «…». Находка верна типографски, но строка следует спеке.

## Съёмка на эмуляторе

Снято 24 кадров (17–39, оглавление — `../screenshots/INDEX-extra.md`). Не снято: All of part 2 (frames 40–52: recap, city, empty state, restore check). The backup was made and verified. But from 00:04 another agent was working on the same emulator and the same debug app at the same time. At 00:04:22 and 00:13:39 it installed com.violinjourney.app.debug over the old one, and ran connected PlaybackTest instrumentation. At 00:14:39 it set the app locale to '', force-stopped and relaunched the app. Then it kept tapping, swiping, taking uiautomator dumps and screencaps (logcat adbd lines, 00:13–00:16). If I ran pm clear plus the tar restore under it, its session would break, and its installs, launches and taps could interleave with the clear/restore window and with my 75 s recap flow. Part 2 should be re-run once the emulator is free. Also note: the restore command in the task, `adb shell run-as … sh -c 'cd … && rm -rf … && tar -xf …'`, needs an extra layer of quoting: `adb shell "run-as pkg sh -c '…'"`. Without it the remote shell drops the inner quotes, so only `cd` runs as the app, and rm/tar run as the shell user in its own cwd.

Данные эмулятора: No restore was needed because part 2 did not run and nothing destructive happened. Backup: /private/tmp/claude-501/-Users-danil-AndroidStudioProjects-ViolinTuner/9a22e600-0898-4eeb-9cc7-72071e1d83fa/scratchpad/emu-backup.tar and emu-backup-2.tar (identical, sha1 a1a2fa54…, 3.5 MB). They hold databases/violin.db, -wal and -shm, files/datastore/user_settings.preferences_pb, 3 session m4a files, a backing wav, files/profileInstalled, files/waveforms and app_dxmaker_cache. PRAGMA integrity_check on the backup is ok. Runtime permissions granted then: RECORD_AUDIO, CAMERA, ACCESS_LOCAL_NETWORK (POST_NOTIFICATIONS was not granted). After part 1 the live DB was pulled read-only and compared with the backup: counts are identical (practice_entries 65 rows / 2837 min = 47 h 17 min, sessions 4, journey_arrivals 4, journey_earnings 66, home_purchases 18, trophies 2, pieces 10, max date 2026-09-24). Reference counts are in the scratchpad file ref-counts.txt, the current ones in now-counts.txt.

Система: Everything on the emulator is back to its starting values: font_scale 1.0, accelerometer_rotation 1, user_rotation 0, wm size 1080x2400 and density 420 with no override. The app locale is [] (system default) as at the start. The other agent set it to '' at 00:14; I had been using ru-RU, de-DE, fr-FR and ja-JP. No practice was saved and no time was edited; all three practices ended under a minute with 'Слишком коротко'. /sdcard/ui.xml is left in place because the other agent writes to the same path.

Наблюдения агента съёмки:

- 01-idle-top.png is not the true top. It was scrolled to the end, and at 412 dp the page scrolls only ~650 px. 17 is the real top: header, room card, start button and summary are above the fold, the calendar starts at the fold.
- Landscape: the left column order differs from portrait (header → trophies row → start → summary → room card). The summary tiles clip their values without an ellipsis ('17 ч 27 ми', 'Дней подря'), and the card text is ellipsized ('хватает до Праг…').
- Landscape running: the timer takes the left column and 'Закончить занятие' goes below the fold. The right column keeps its old scroll position and selected day.
- At midnight the 'today' ring moved from 26 to 27 without a restart, and the landscape selection (24th) stayed put.
- Small screen 360×640: the summary is already below the fold when idle. When running, only the timer is visible and 'Закончить занятие' needs a scroll.
- Small screen: summary values change type size per tile to fit ('2 ч 42 мин' larger than '17 ч 27 мин'), so the row looks uneven. The same happens in de and fr.
- Small screen, day block: '1 ч 22 мин' wraps onto two lines next to 'Изменить время'. In fr, 'Pas de pratique' wraps; at font 1.3, 'Не занимались' wraps.
- Time-for-day sheet on the small screen opens half-expanded, with Сохранить and Отмена hidden below and the hint under the gesture bar. Only dragging the sheet reveals the buttons.
- Add-time sheet at normal size (39): Отмена sits under the gesture handle because the sheet has no navigation-bar inset, and Сохранить is enabled at '0 мин'.
- German small screen: the main header number is truncated ('47 Std. 17 M..'), and trophies collapse to two icons plus '+1'.
- Japanese: CJK falls back to the system font while digits stay Manrope ('47時間 17分' mixes fonts). The taller line height pushes the whole page down ~10 px.
- Font 1.3: the card text is ellipsized ('в пу…'), the 'Дней подряд' label is smaller than its neighbours, and calendar digits nearly fill the cells.
- Font 2.0 breaks the layout: the header total is ellipsized ('47 ч 17 м…'), and summary values are hard-clipped without an ellipsis. Calendar digits overflow the cells and overlap the activity dots. The 'сегодня' chip breaks letter by letter into a vertical column, 'Не занимались' breaks mid-word ('Не зани/мали/сь'), and 'Добавить время' is clipped by the button's fixed height. The level-circle digit and the timer do not follow the system font; the timer is intentional, the circle perhaps not.
- Font 2.0: 'до 6 уровня — 2 ч 43 мин' moves to its own line under 'Уровень 5 · Гаммы'. That is acceptable, but the header gets tall.
- After returning from Live, the green mic privacy indicator stays in the status bar for a few seconds on the Practice screen (37). This is system behaviour, but it shows up in screenshots.
- The profile sheet (38) shows avatar '?' while the header circle shows the level '5'. The header circle is the level, not the avatar, and it is not obvious that tapping 'За скрипкой' opens the profile.
- Emulator note: the release package com.violinjourney.app (empty data, English) is also installed. When the debug process was killed by another agent's instrumentation, it came to the front and was captured by mistake; that shot was retaken as 28. uiautomator dump fails with 'null root node' while instrumentation runs, and the stale /sdcard/ui.xml can mislead.
