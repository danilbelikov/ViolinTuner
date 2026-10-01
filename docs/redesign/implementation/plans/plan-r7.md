# План R7: путешествие и дом (3.36.7, этапы 117–119)

Коротко:
- В R7 входят все экраны путешествия и дома: путешествие, остановка, карта, паспорт, дом, «Дома», лавка, карточка вещи, примерка и «Обставить».
- Появляется одна новая общая часть: `ShortfallPlate`.
- К общим частям R1 добавляются только параметры со значениями по умолчанию. Вид экранов R2–R6 не меняется.
- `feature/practice/**`, `JourneyWindowCard.kt`, `PriceBar` и рисунки (`art/**`, `tools/home/export.js`) не трогаются. Исключение — параметр `outline` у `HomePicture`.

Сокращения путей:
- **S** = `/Users/danil/AndroidStudioProjects/ViolinTuner/shared/src/commonMain/kotlin/com/violinjourney/app`
- **I** = `…/shared/src/iosMain/kotlin/com/violinjourney/app`
- **CT** = `…/shared/src/commonTest/kotlin/com/violinjourney/app`
- **A** = `…/app/src/main/java/com/violinjourney/app`
- **T** = `…/app/src/test/java/com/violinjourney/app`
- **AT** = `…/app/src/androidTest/java/com/violinjourney/app`
- **RES** = `…/app/src/main/res/values*/strings.xml` и `strings_home.xml`. Источник — `values-ru`; языки: `values` (en), `de`, `fr`, `es`, `it`, `pt`, `ja`, `zh`, `ko`.

## 0. Что должно быть до начала

1. **R4 и R5 закоммичены.** R7 опирается на API общих частей после них:
   - `AppButton(text, onClick, modifier, style, icon, caption, enabled, reason, compact, leading)`. `leading` добавляет R4.
   - `AppDock(dock, modifier, fade, aboveKeyboard, padSides, metrics, ground, shape, content)`. `ground` и `shape` добавляет R5; R7 берёт их умолчания.
   - `AppSheet(value, onHide, modifier, slideAway, dismissible, scroll, contentPadding, keepHandleRoom, content)`. `keepHandleRoom` добавляет R5; R7 берёт `false`.
   - `AppChip(text, selected, onClick, modifier, count, icon)` и `AppChip.Choice(…, onLongClick, onLongClickLabel)` (R5).
   - `GlassPlate(modifier, strong, shape, contentPadding, content)`.
   - `ReasonLine` (R4), `ListRow(strong)`, `SegmentedSwitch(…)` в R7 не нужны: неактивных кнопок в R7 нет.
2. **Пересечения по файлам.** `AppButtons.kt`, `AppChip.kt`, `IconPaths.kt`/`AppIcons.kt`, 10×2 файлов строк, `AccessibilitySemanticsTest`, `ControlsTouchTest`, `docs/notes/redesign.md`.
   - Этап 105 (окно дома R2) правит `S/feature/journey/JourneyWindowCard.kt` и, возможно, `journey_*` в строках. Если он идёт параллельно — сливать строки руками. Строки `journey_next_short`, `journey_enough`, `journey_soon_short`, `journey_have` R7 не меняет.
3. **Коммиты по этапам:**
   - `feat(journey): … (stage 117, spec 3.36.7)`, `feat(home): … (stage 118/119, spec 3.36.7)`, затем `docs: stage 11x is done — notes …`.
   - В индексе лежат `.idea/*`, поэтому коммитить с явными путями.
   - Снимки «было» — отдельный коммит `docs(redesign): «было» of the journey and the home for R7 -- docs/redesign`.

## 1. Мои решения, общие для трёх этапов

- **D1. Параметры `AppButton` (R1-расширение, умолчания сохраняют вид):**
  - `oneLine: Boolean = false`. Слова идут в одну строку: `Text(autoSize = TextAutoSize.StepBased(min 15 sp, max look.fontSize, step 1 sp), maxLines = 1, overflow = Ellipsis)`; подпись — 13 → 12 sp. Так уже сделано в `PathSheet.kt:196`.
  - `keep: String? = null`. Часть подписи, которую нельзя резать (число). Подпись раскладывается как «до · keep · после»: две части по краям сжимаются многоточием, число — нет. В английском `home_travel_line` число стоит посередине («%1$s → %3$s to %2$s»), поэтому простого многоточия в конце не хватает. Раскладка — приватный `KeptLine` в `AppButtons.kt`. Разбиение — чистая функция `ButtonLine.split(caption, keep): Triple<String, String, String>?` с тестом.
  - `outline: Color = Color.Unspecified` — цвет рамки контурной кнопки. Нужен для «Убрать» примерки: белый 35 %.
- **D2. `ShortfallPlate` (новая, `S/core/ui/components/ShortfallPlate.kt`):**
  ```kotlin
  fun ShortfallPlate(text: String, modifier: Modifier = Modifier, caption: String? = null,
      description: String? = null, compact: Boolean = false, leading: (@Composable () -> Unit)? = null)
  ```
  - Капсула `heightIn(min = 56 | 48)`, `surfaceContainerHigh`.
  - `text` — 16 sp, 700, `onSurface`; `caption` — 13 sp, 600, `onSurfaceVariant`. Обе строки `maxLines = 1` с autosize: 16 → 14 и 13 → 12.
  - `clearAndSetSemantics { contentDescription = description ?: text + ", " + caption }`. Ни роли, ни onClick, ни disabled, ни 0,38.
  - Знак такта передаётся слотом `leading`. `TaktIcon` живёт в `feature/journey`, а core ничего из feature не импортирует — я это проверил.
- **D3. `JourneyViewModel(journey, config, clock, venues)`** — тот же порядок, что у `StopViewModel`. **`HomeViewModel(home, journey, clock, venues, config)`**, `HomeUi.config: JourneyConfig = JourneyConfig()`.
- **D4. `TwoWay`** остаётся в `S/feature/home/HomeScreen.kt`: его импортирует `AccessibilitySemanticsTest`.
  - Новый параметр `onPicture: Boolean = true`.
  - Раскладка высотой 48 (касание), видимая капсула — 40 (поле 3 + сегмент 34), рисуется `drawBehind` по центру. Выбранная пилюля 34 — тоже `drawBehind`. Так касание 48 не раздувает видимую плашку.
  - Слово 13 sp, 700. Отступ от картины 8 по видимому краю: сверху и снизу снаружи `padding(4)`, сбоку `padding(8)`.
  - `onPicture = false` (шапка «Обставить») — прежний фон `surface` на 0,78.
  - `Role.Tab` и `selectableGroup` — как сейчас.
- **D5. «Домой» в строке остановки в landscape** — `widthIn(min = 120.dp)`, а не ровно 120: немецкое «Nach Hause» со значком в 120 не помещается. Записать в заметки.
- **D6. Лавка по месту.**
  - `HomeRoute(slot)` шлёт `HomeIntent.ShopSlotGiven(slot)` из `LaunchedEffect`.
  - Модель берёт его **один раз на свою жизнь** (`private var slotTaken`). Поворот не возвращает снятый ✕-фильтр, а «назад» и новый вход — это новая модель.
- **D7. Фокус «Обставить»** ставит обработчик `Placed`: `arrangeFocus = slot`. «Поставить» карточки тоже шлёт `Placed`, но в лавке фокус не рисуется.
  - Снимают фокус `SideSelected` и `ShopAtClicked`.
  - Уход с экрана — это конец модели маршрута «Обставить».
- **D8. Чистые функции с тестами вместо логики в композиции:**
  - `JourneyRules.sessionsLeft`, `sessionsWords`, `PictureFit.height`, `ButtonLine.split`;
  - `NextHouseLine.of`, `ShopShelves.of`, `ItemCardPlan.of`, `ArrangeOutline.thingOf` / `frameOf`.
- **D9. Карточка вещи — свой файл `S/feature/home/ItemCard.kt`** с публичным `ItemCardContent` (для превью в `AppSheetCard`). `ShelfMaterial` и `Board` становятся `internal`. Лист дома — публичный `HouseSheetContent`. Строка следующего дома — публичный `NextHouseRow(next: HomeHouse?, …)`.
  - Состояние «нет следующего дома» в каталоге сейчас недостижимо: пять домов не нарисованы. Его показывает только превью `NextHouseRow(null)`.
- **D10. `BackHandler` фаз** (`S/feature/journey/JourneyRoutes.kt:57`) не меняется: «назад» закрыт только на дороге. На прибытии и штампе системный «назад» уходит с экрана, как сейчас (перегон и прибытие уже записаны). Нижняя зона поведение «назад» не меняет.
- **D11. «Купить · 300» без знака такта** — как в макете: `AppButton` берёт `ImageVector`, а знак такта — Canvas.
- **D12. Паспорт — всегда `GridCells.Fixed(3)`**, как в спеке («три — и в landscape»), без запасного `Adaptive(104)` из `06-journey-home.md`.
- **D13. Слова через `dot_separator`, без новых шаблонов:** «скоро · 6 000», «Следующий дом · имя», строка места остановки, «вы здесь · …».
  - «до Праги 1 128» на карте берётся из прежней `journey_next_short`.
  - Сверх списка спеки — одна новая строка `journey_have_description` («472 из 1 600 тактов», для TalkBack). Записать в заметки.
- **D14. Обводка считается внутри `HomePicture`** из размера холста: `k = SceneCamera.cover(w, h)`, `left = (w − SceneGrid.WIDTH·k)/2`, `top = SceneCamera.top(1f, w, h, outdoors = false)`.
  - Сигнатура `overlay` в `SceneCanvas.kt` не меняется. `outline` работает только без `camera` (в «Обставить» её нет); с `camera` параметр не учитывается.

## 2. Этап 117 — путешествие, нижняя зона, вступление, прибытие, штамп

### 2.0 Первый шаг, до правки кода

1. **Сборка «до».**
   - `ANDROID_SERIAL=emulator-5554 ./gradlew :app:assembleDebug -PfakePitch=true`.
   - Скопировать APK в `$SCRATCH/r7/before.apk` (`$SCRATCH` = scratchpad сессии). По нему можно переснять «до» в день снимков «после»: дом днём и вечером зависит от часов (07–19), ёлка — от даты.
2. **Состояния данных — скрипт `$SCRATCH/r7/state.sh <имя>`.**
   - Базовая копия: `run-as … tar -cf - -C /data/data/com.violinjourney.app.debug databases files/datastore > base.tar`, как в `docs/plan-performance.md`.
   - Для каждого состояния: `am force-stop`, вернуть `base.tar`, забрать `violin.db` вместе с `-wal` и `-shm`, изменить `sqlite3` на Mac, положить обратно без `-wal` и `-shm`.

   | Состояние | SQL |
   |---|---|
   | `enough` | база (по макету — Вена, 47 884) |
   | `short` | `INSERT INTO journey_earnings(atEpochMs,notesPlayed,notesInTune,durationMs,takts,piecesPaid) VALUES(<now>,0,0,0,<472 − баланс>,0)` |
   | `end` | вставить `journey_arrivals` всех остановок до `sydney` с `price = 0` |
   | `home` | `DELETE FROM journey_arrivals WHERE stopId <> 'home'` |
   | `intro` | `DELETE FROM journey_arrivals` |
   | `extras` | `journey_extras` для `vienna` `SECOND_TIME` и `SECOND_VIEW` с `price = 0` |
   | `gift` | `DELETE FROM home_purchases WHERE id = 'vln_student'` |
   | `tokyo` | `end` без `sydney` — для штампа Сиднея |

   - В конце — вернуть `base.tar`. Только эмулятор.
3. **Снимки «до» для `snap.py`.**
   - Настройки: `settings put global animator_duration_scale 0`, `settings put global violintuner_scene_seconds 12.5`.
   - Команда: `python3 tools/perf/snap.py take $SCRATCH/r7/before/<экран>_<раскладка>.raw`.
   - Экраны: путешествие (`enough`, `short`, `end`, `home`, `intro`); прибытие и штамп (из `enough` — «В путь», дорога 2 с); штамп Сиднея (из `tokyo`); остановка; её полный экран; карта; паспорт; дом (`gift` и база); лавка; карточка вещи — хватает и не хватает (люстра 900 при `short`); примерка; «Обставить»; «Дома»; лист дома.
   - Раскладки: портрет 412×892; landscape 892×412 (`accelerometer_rotation 0` + `user_rotation 1`); 640×360 и 360×640 (`wm size 720x1280` + `wm density 320`).
   - Касания — по тексту через `uiautomator dump`, как в CLAUDE.md.
4. **Снимки «было»** в `docs/redesign/current/`: 450×1000 (landscape 1000×450) JPG, через `sips -Z 1000 -s format jpeg`.
   - Файлы: `journey.jpg`, `journey_short.jpg`, `journey_end.jpg`, `journey_home.jpg`, `journey_intro.jpg`, `journey_arrival.jpg`, `journey_stamp.jpg`, `journey_stop.jpg`, `journey_stop_full.jpg`, `journey_map.jpg`, `journey_passport.jpg`, `journey_land.jpg`, `journey_small.jpg`, `home_shop.jpg`, `home_shop_card.jpg`, `home_shop_card_short.jpg`, `home_try.jpg`, `home_arrange.jpg`, `home_houses.jpg`, `home_house_sheet.jpg`, `home_land.jpg`, `home_small.jpg`.
   - Обновить `docs/redesign/README.md:26`, `open-questions.md` (пункт 16 — снято) и строку «снимки «было»» в `06-journey-home.md:15`.
   - Сверить живые экраны со списком «Уже так — не переделывать». Расхождения — ведущему и в заметки; работу это не останавливает.
5. **Замеры «до».**
   - `python3 tools/perf/measure.py journey-before 10`, `home-before`, `stop-before`, `arrange-before` — анимации включены, `scene_seconds` удалена.
   - Лучше делать на эмуляторе с видеокартой `emulator-5560` (`-gpu host -read-only`, данные — tar с 5554), как в `docs/plan-performance.md`: сравнивать запись кадра p50 и CPU, а не кадры в секунду.

### 2.1 Домен и контракт

- **`S/core/domain/journey/Journey.kt`.**
  - В `JourneyConfig` (строки 6–20) добавить `val taktsPerSessionHint: Int = 300` с KDoc «5.18: подсказка “примерно N занятий”».
  - В `JourneyRules` после `canDepart` (строка 149) добавить:
    ```kotlin
    fun sessionsLeft(missing: Long, config: JourneyConfig): Int =
        if (missing <= 0) 0 else ((missing + config.taktsPerSessionHint - 1) / config.taktsPerSessionHint).toInt().coerceAtLeast(1)
    ```
- **`S/feature/journey/JourneyContract.kt:25–41`.** В `JourneyState` — `val sessionsLeft: Int = 0`.
- **`S/feature/journey/JourneyViewModels.kt`.**
  - `stateOf(progress, phase, config)` (38–54): `sessionsLeft = JourneyRules.sessionsLeft(missing, config)`.
  - Конструктор (73–77) — `(journey, config: JourneyConfig, clock, venues)`; строка 86 передаёт `config`.
- **`A/feature/journey/HiltJourneyViewModel.kt`** — `config: JourneyConfig` в `@Inject`. Hilt его уже даёт `HiltStopViewModel`.
- **`I/ios/IosNavHost.kt:383`** — `JourneyViewModel(graph.journey, graph.journeyConfig, graph.clock, graph.venues)`.
- **Новый `S/feature/journey/JourneyWords.kt`:**
  ```kotlin
  internal fun sessionsWords(n: Int): StringResource =
      Formats.plural(n, Res.string.journey_sessions_one, Res.string.journey_sessions_few, Res.string.journey_sessions_many)

  @Composable fun sessionsInWords(n: Int): String = stringResource(sessionsWords(n), n)
  ```

### 2.2 Общие части

- **`S/core/ui/components/AppButtons.kt`.**
  - Параметры `oneLine`, `keep` из D1 в `AppButton` (81–109) и `StyledButton` (111–171).
  - Ветка `withCaption && oneLine`: первая строка — autosize, подпись — `KeptLine(caption, keep, style 13 → 12)`.
  - Параметр `outline` добавляется на этапе 119.
  - Объект `ButtonLine` с `split` — там же.
- **Новый `S/core/ui/components/ShortfallPlate.kt`** (D2).
- **`S/core/ui/icons/IconPaths.kt`** — `val STAMP = listOf("M9 3h6v5l-1 4h-4L9 8z", "M4 14h16v4H4z", "M6 21h12")` (путь `i-stamp` макета).
- **`S/core/ui/icons/AppIcons.kt`** — `val Stamp` рядом с `Map`/`Passport` (43–48); `"Stamp" to { Stamp }` в `all` рядом с 124–129.

### 2.3 `S/feature/journey/JourneyParts.kt` (добавить)

- `PictureFit.height(available: Dp, dock: Dp, min: Dp, max: Dp): Dp = (available − dock − 120.dp).coerceIn(min, max)`. Константы `PostcardMin` 180, `PostcardMax` 240, `RoomMin` 200, `RoomMax` 290.
- `GlassSquare(icon, contentDescription, onClick?, modifier)` — 48, `AppShapes.S`, `ViolinTheme.glass`, значок 24 `onSurface`. Используется на этапе 118.
- `BalancePill(balance)` — 34, капсула `ViolinTheme.accentSoft`, поля 12, `TaktAmount` (знак 15, 14 sp, 800, accent, tnum). Используется на этапе 118.

### 2.4 Экран путешествия — `S/feature/journey/JourneyScreen.kt`

- **`JourneyTopBar` (137–147).**
  - Высота 56, в landscape 48: `LocalWindowInfo.current.containerSize` шире, чем высота.
  - Заголовок `titleLarge.copy(18.sp, ExtraBold)`, одна строка, `semantics { heading() }`.
  - Константа `TopBarHeight` (99) заменяется парой 56/48.
- **Шапка `IdleContent` (152–159)** — без `TaktAmount` баланса; остаются `BarIcon(Map)` и `BarIcon(Passport)`.
- **`IdleContent`, портрет (170–178)** — `Box(TopCenter) { AppDock(… ) { … } }`:
  - `dock = { WayDock(calm, onIntent) }`, `modifier = widthIn(max = 560).fillMaxHeight()`, `metrics = currentDockMetrics().copy(side = 16.dp)`, fade 28.
  - Контент: `Column(verticalScroll, padding(16, 0, 16, LocalDockInset + 24), spacedBy 16) { Place(state, PictureFit.height(maxHeight, LocalDockInset, 180, 240)); Passed(state) }`.
- **`IdleContent`, landscape (161–169).** `Row(padding h16, spacedBy 16)`:
  - слева `AppDock(dock = { WayDock }, Modifier.width(360).fillMaxHeight(), padSides = false)` с `Place(state, 180.dp)` в прокрутке;
  - справа `Column(weight 1, verticalScroll) { Passed }` — только «Пройдено» и лента.
- **`Place` (185–212).**
  - Таблетка остановки: `GlassPlate(Modifier.align(TopStart).padding(10).height(28), contentPadding = h11)` с текстом 12 sp, 700.
  - У открытки — `clearAndSetSemantics { contentDescription = journey_card_description(city) + ", " + текст таблетки; role = Button; onClick }`.
  - Город — 26 sp, 800, −0,02 em. «место · страна · с даты» — 14 sp `onSurfaceVariant`. Факт — 14 sp `onSurfaceVariant`, строка 1,45.
- **`HomeDoor` (219–238).**
  - Заливки нет нигде: всегда `surfaceContainer`, скругление 16, поля 8/12/8/8, `heightIn(min = 64)`.
  - Миниатюра 64×48, скругление 10. «Войти в дом» — 15/700, подпись — 13 `onSurfaceVariant`, значок `Door` — 24 `onSurfaceVariant`.
- **`Way` (249–292) делится на две части.**
  - **`Passed(state, onIntent)`:**
    - «Пройдено N из 16» — 16/800, 22 сверху, `heading()`.
    - Лента: `LazyRow(spacedBy 10)` миниатюр 112×76 (скругление 12, неподвижные). Под миниатюрой город 13/700 и `Formats.dayAndMonth(arrivedAtEpochMs)` 12 `onSurfaceVariant`.
    - TalkBack: «Зальцбург, 18 сентября». У «Дома» — прежний тап `StopClicked(HOME)`.
    - Пустая лента — `journey_no_cards`.
  - **`WayDock` (DockScope):**
    - `next == null` → `PathCard`: «Мировое турне пройдено» 16/800 и справа баланс `TaktAmount` 16/800 accent (строка ≥ 48), под ними «новые города…» 13 `onSurfaceVariant`. Кнопки нет.
    - Иначе `PathCard`: `journey_next(cityToOf(i+1), roadOf(i+1))` 14 `onSurfaceVariant`; справа при `canDepart` — цена, иначе `journey_have(balance, price)` 14/700 `onSurface` со знаком 14. При нехватке — `PriceBar` 8 под строкой.
    - TalkBack карточки: «до Праги, поездом, 4 часа, 472 из 1 600 тактов» — `journey_have_description`, « · » → «, ».
    - `canDepart` → `AppButton(journey_depart(cityOf(i+1)), icon = Travel, caption = journey_depart_spend(price, balance), keep = caption, oneLine = true, compact = compact, fillMaxWidth)`.
    - Иначе → `ShortfallPlate(journey_missing(takts(missing)), caption = sessionsInWords(state.sessionsLeft), description = journey_missing(taktsInWords(missing)) + ", " + sessions, compact, leading = { TaktIcon(18.dp) })`.
- **`IntroContent` (307–325)** — `AppDock(widthIn(560))`. Содержимое прежнее. «Собрать футляр» — Main, без значка.
- **`ArrivalContent` (349–377)** — `AppDock(widthIn(560))`, `dock = { AppButton(journey_stamp, icon = Stamp, Modifier.graphicsLayer { alpha = text.value }) }`: кнопка проявляется вместе с текстом, сама зона без альфы.
- **`StampContent` (380–415).**
  - «Паспорт · страница N» — 13/700 `onSurfaceVariant`. Рамка 220 и штамп 168 — прежние.
  - «Штамп № 4» — 22/800. `next != null` → `journey_stamp_leg(cityToOf(i+1), taktsInWords(price))`, иначе `journey_stamp_last`. Текст 15 `onSurfaceVariant`, 4 сверху.
  - `dock = { Column(spacedBy 4) { AppButton(journey_done); AppButton(venue_play_here, style = Text, icon = Theatre) } }`.
- **Константа `STAMPS_PER_PAGE`** — без изменений.

### 2.5 Строки

Добавить на десяти языках (раздел 5):
- `journey_depart_spend`, `journey_sessions_one|few|many`;
- `journey_tour_done`, `journey_tour_more`;
- `journey_stamp_number`, `journey_stamp_leg`, `journey_stamp_last`;
- `journey_have_description`.

Удалить во всех языках: `journey_soon`, `journey_stamp_text`, `journey_stamp_text_last`. `journey_depart` — текст прежний, аргумент теперь город. Проверка: `python3 tools/i18n/check.py <тег>` ×9.

### 2.6 Тесты этапа 117

- `CT/core/domain/journey/JourneyTest.kt` — `sessionsLeft`:
  - 1 128 → 4, 1 → 1, 300 → 1, 301 → 2;
  - 0 и −5 → 0;
  - `JourneyConfig(taktsPerSessionHint = 250)`: 1 128 → 5.
- `CT/feature/journey/JourneyWordsTest.kt` (новый):
  - ru: 1 и 21 → `one`; 2, 4, 22 → `few`; 5, 11, 14, 25 → `many`;
  - en: 1 → `one`, 2 → `many`;
  - fr: 1 → `one`;
  - ja: 1 → `many`.
- `CT/core/ui/components/ButtonLineTest.kt` — `split`: число в середине, в конце, `keep` не найден, `keep` равен всей строке.
- `CT/feature/journey/PictureFitTest.kt`:
  - 360×640, жесты: доступно 536, зона 132 → комната 284;
  - три кнопки: 512 → 260;
  - открытка 240;
  - нижние и верхние границы.
- `T/feature/journey/JourneyViewModelTest.kt:53` — новый конструктор. Новый тест: дома `sessionsLeft == 1` (не хватает 300); с `taktsPerSessionHint = 100` — 3; при `canDepart` — 0.
- `AT/core/ui/AccessibilitySemanticsTest.kt` (только компиляция) — `aShortfallPlateIsWordsNotADisabledButton`: нет `Role`, `Disabled`, `OnClick`; `contentDescription` — из `description`.
- Зелёными остаются `AppIconsTest` (Stamp в `all`), `LocalizationTest`, `JourneyMapMathTest`.

### 2.7 Превью этапа 117

- **Новый `A/feature/journey/JourneyScreenPreviews.kt`.** Выборка через `JourneyReducer.stateOf(progress, phase, JourneyConfig())`, обёртка `ViolinTheme { CompositionLocalProvider(LocalReduceMotion provides true) { … } }`. Состояния:
  - хватает (Вена, 47 884), не хватает (472), маршрут пройден, остановка «Дом» (Кремона), вступление;
  - прибытие, штамп, штамп Сиднея;
  - landscape 892×412 и 640×360, 360×640, de 360×1,3, fr 360.
- **`A/core/ui/components/ComponentsPreviews.kt`:** `ShortfallPlate` — одна строка, две строки, `compact` (окно ≤ 360); `AppButton(oneLine, keep)` — ru «Санкт-Петербург → до Москвы 1 000» и en на 360 при 1,3.

### 2.8 Проверки этапа 117

- `./gradlew :app:assembleDebug :app:compileDebugAndroidTestKotlin :app:testDebugUnitTest :shared:testAndroidHostTest :shared:iosSimulatorArm64Test :app:lintDebug`.
- Эмулятор, состояния через `state.sh`:
  - `enough` / `short` / `end` / `home`: зона видна при прокрутке, карточка пути, «спишется …», плашка в две строки;
  - `intro` → «Собрать футляр»; дорога → прибытие → штамп → «Готово» / «Играть здесь»; `tokyo` → штамп Сиднея;
  - landscape 892×412 (зона в левой колонке, справа только лента) и 640×360 (кнопка 48, зона > 64), 360×640 (открытка 240), `font_scale 1.3`, de и fr: строки кнопок не режут числа.
- `snap.py compare` с «до»: дорога и заставки — 0 %; «Занятия» (окно R2) — 0 %.
- iOS: xcodebuild Debug, `violin.db` с эмулятора (`PRAGMA wal_checkpoint`), `-openRoute journey -sceneSeconds 12.5`, снимок.
- Заметки «Этап 117» в `docs/notes/redesign.md`; коммит.

## 3. Этап 118 — остановка, карта, паспорт, дом, «Дома»

### 3.1 Общее

- **`S/feature/home/HomeScreen.kt:166–182` — `TwoWay`** по D4. Вызовы: `Picture` → `TopEnd`; `FullscreenHome:268` на стекле; `ShopScreen.kt:435` (примерка, до 119 — прежнее место); `ArrangeScreen.kt:461` → `onPicture = false`.
- **`Balance(ui)` (160–163)** → `BalancePill(ui.balance)`.
- **`BuyButton` (483–492).**
  ```kotlin
  fun BuyButton(price: Int, balance: Long, label: String, onBuy: () -> Unit, modifier: Modifier = Modifier, compact: Boolean = false)
  ```
  - Хватает → `AppButton(label, onBuy, modifier, Main, oneLine = true, compact = compact)`.
  - Не хватает → `ShortfallPlate(shop_missing(takts(price − balance)), modifier, description = shop_missing(taktsInWords(…)), compact, leading = { TaktIcon(18) })`.
  - Параметр `height` уходит. Старая строка «ещё примерно …» в `ShopScreen.kt:380–398` живёт до этапа 119.

### 3.2 Модель дома

- `S/feature/home/HomeContract.kt:13–35` — `HomeUi.config`.
- `S/feature/home/HomeViewModel.kt:26–33` — `config: JourneyConfig` в конструкторе и в `look`. Компаньон (117–119) становится публичным под `ARG_SLOT` на этапе 119.
- `A/feature/home/HiltHomeViewModel.kt` — `config` в `@Inject`.
- `I/ios/IosNavHost.kt:437` — `HomeViewModel(graph.home, graph.journey, graph.clock, graph.venues, graph.journeyConfig)`.

### 3.3 Остановка — `S/feature/journey/JourneyPlaces.kt`

- **`StopScreen` (85–122).**
  - Шапка — `JourneyTopBar(city) { BalancePill }`.
  - Портрет: `AppDock(widthIn 560, side 16)` с `dock = { StopDock }`; контент `Column(verticalScroll, padding 16 / inset + 24, spacedBy 12) { StopCard(PictureFit(… , 180, 240)); StopWords; Extras }`.
  - Landscape: слева `AppDock(width 360, padSides = false, dock = { Row { PlayHere(weight 1); Домой(widthIn(min = 120)) } })` с `StopCard(180)`; справа прокрутка `StopWords + Extras`.
  - Без прибытия (`arrivedAtEpochMs == null`) зоны нет — как сейчас (`PlayHere` 160).
- **`StopCard` (125–151).**
  - Плашки `FilterChip` уходят. На открытке: `StopPlates` — `Layout` из двух `TwoWay`, `align(BottomStart).padding(start 8, bottom 4)`.
  - Раскладка: ширина первой + 8 + вторая ≤ ширина открытки − 8 − 8 − 48 − 8 → одним рядом; иначе вторая над первой.
  - `GlassSquare(Fullscreen, journey_fullscreen)` справа снизу, 8 от угла, по касанию — `PostcardClicked`.
- **`PlayHere` (157–173) → `StopDock`.**
  - `AppButton(venue_play_here, icon = Theatre, caption = venue_play_here_caption(hallOf(index)), oneLine, compact)`.
  - `AppButton(journey_go_home, Outline, compact = true, icon = House)` → `HomeClicked`.
  - `venue_play_here_hint` уходит. `hallOf(i) = stringArrayResource(Res.array.venue_halls)[i]` — в `JourneyParts.kt`.
- **`StopWords` (176–186)** — одна строка: `listOf(place, country, journey_stop_meta(…)).joinToString(dot_separator)`, 14 `onSurfaceVariant`, без `maxLines`. Факт — 14 `onSurfaceVariant`, 1,45.
- **`Extras` (189–219).**
  - Заголовок: `Row(Bottom) { «Дополнения» 16/800 heading; Spacer(weight); journey_extras_hint 13 }`, 22 сверху.
  - Строки — отдельные карточки ≥ 60, `surfaceContainer`, скругление 16, поля 8/8/8/14, между строками 8. Название 15/700, подпись 13.
  - Подпись по виду: `SECOND_TIME` → `journey_extra_time_note`; `SECOND_VIEW` → `stop.views[0].inside ? journey_extra_view_outside : journey_extra_view_inside`; `SOUVENIR` → `journey_extra_souvenir_note`.
  - Справа:
    - куплено → чип 28 `accentSoft` «✓ открыто» (галочка 13, 12/800 accent);
    - хватает → `PillButton` (капсула 40, касание 48, рамка 1,5 `outlineVariant`, `TaktAmount` 14/800, знак accent) → `BuyClicked`;
    - не хватает → `TaktAmount` 14/700 `onSurfaceVariant` без капсулы.
  - Строка: `clearAndSetSemantics` с одним описанием; при «хватает» — `role = Button` и `onClick`.
  - `PillButton` — `internal` в `JourneyParts.kt`, его же зовёт «Жить здесь».
- **Полный экран — `S/feature/journey/JourneyFullscreen.kt:154–167`.** `FilterChip` → `TwoWay` (по плашке на каждую открытую пару), `touched()` сохраняется. Импорты `FilterChip` и `FilterChipDefaults` уходят. Затемнения и кнопка закрытия прежние.

### 3.4 Карта — `MapScreen` (282–294)

- Портрет: `Column { JourneyMapCanvas(Modifier.weight(1f)); HereCard(padding(start 16, end 16, bottom 16)) }`.
- Landscape: `Row { Box(width 392, padding 16, BottomStart) { HereCard(width 360) }; JourneyMapCanvas(weight 1) }`.
- `JourneyMap.kt` не меняется: схема вписывается в свой ящик через `minOf`.
- **`HereCard`:**
  - ≥ 64, `surfaceContainer`, `AppShapes.M`, поля 8/12/8/8.
  - `StopPostcard(state.current)` 64×48, скругление 10, неподвижная. Город `cityOf(currentIndex)` — 15/700.
  - Строка — `AnnotatedString`: [`journey_here` — accent, 700] + `dot_separator` + одно из трёх:
    - `next == null` → `journey_here_end`;
    - `canDepart` → `journey_here_enough(cityTo)`;
    - иначе → `journey_next_short(cityTo, takts(missing))`.
  - Шеврон 24 `textTertiary`. Касание → `JourneyIntent.StopClicked(state.current.id)`.
  - TalkBack: «Вена, вы здесь, до Праги хватает», кнопка.

### 3.5 Паспорт — `PassportScreen` (223–268)

- `GridCells.Fixed(3)`, `StampView(size = 96.dp)`, `verticalArrangement` 14, `horizontalArrangement` 6.
- Подпись 12/600 `onSurfaceVariant`. Следующая (`index == currentIndex + 1 && stop.available`) — второй строкой `journey_passport_next`, 12/700 accent.
- У недостигнутой — `clearAndSetSemantics { journey_stamp_locked_description(city) [+ ", " + следующая] }`.

### 3.6 Дом — `S/feature/home/HomeScreen.kt`

- **`HomeScreen` (186–212).**
  - Портрет: `Box(TopCenter) { AppDock(widthIn 560, side 16, dock = { HomeDock }) { Column(verticalScroll, padding 16 / inset + 24, spacedBy 14) { Picture(PictureFit(…, 200, 290)); About } } }`.
  - Landscape: `Row(padding h16, spacedBy 16)`:
    - слева `BoxWithConstraints(weight 1)` с `AppDock(fade = 0.dp, padSides = false, dock = { if (maxWidth ≥ 440) HomeDockRow else HomeDock })` и `Picture(fillMaxSize, padding(bottom = LocalDockInset))`;
    - справа `Column(width 320, verticalScroll) { About }` — без кнопок.
  - Загрузка — только шапка (строка 194, как сейчас).
- **`HomeDock`:**
  - `AppButton(home_travel, icon = Travel, caption = travelLine, keep = число или null, oneLine, compact, fillMaxWidth)` — прежние `home_travel_end` / `enough` / `line` (строки 301–310).
  - `Row(spacedBy 10) { AppButton(home_shop, Outline, Shop, compact = true, weight 1); AppButton(home_arrange, Outline, Arrange, compact = true, weight 1) }`.
- **`HomeDockRow`** — `Row(height(IntrinsicSize.Min), spacedBy 10)`: главная (`weight 1`, `fillMaxHeight`, `compact = compact`) и две контурные «по слову», `compact = true` (контурные зоны — поля 16, 15 sp, 5.29 R7 «Общее»; ревью этапа 118: `compact = compact` давал в окне 412 поля 24), тоже `fillMaxHeight` — высоту главной.
- **`Picture` (215–229).**
  - `TwoWay` → `align(TopEnd).padding(top = 4, end = 8)`.
  - `GlassSquare(Fullscreen)` → `BottomEnd` 8, по касанию — `FullscreenClicked`.
- **`FullscreenHome` (259–269).** Кнопка выхода — `GlassSquare(FullscreenExit, home_fullscreen_close)`; `TwoWay` на стекле; затемнения прежние.
- **`About` (281–373).**
  - Имя дома — 26/800. Строка вещей — 14 `onSurfaceVariant`. Кнопки (297–321) уходят в зону.
  - `GiftCard` (вместо 322–332):
    - градиент 135° от `accentSoft` к `surfaceContainer`, внутренняя рамка 1 accent на 35 %, скругление 16, поля 12/12/12/14;
    - `ItemThumb(GIFT)` 64; заголовок 15/700, текст 13;
    - `AppButton(home_gift_take, Main, compact = true)` → `GiftTaken`.
  - Лента «Привезено…» (333–346) удаляется.
  - `NextHouseRow(HomeRules.nextHouse(ui.home), ui.balance, onClick)` вместо 347–372. `TextButton` «Все дома» удаляется.
  - **`NextHouseRow`** (публичный):
    - ≥ 64, `surfaceContainer`, скругление 16, поля 10/14;
    - `HouseSilhouette` 52×44 `textTertiary` (не нарисованный — контуром);
    - заголовок `home_next_house + dot + houseName` — 15/700, одна строка, многоточие;
    - состояние по `NextHouseLine.of(house, balance)`: `Soon` → «скоро · 6 000»; `Short` → «1 200 / 3 000» и `PriceBar` 6 под строкой; `Enough` → `houses_enough(takts)` 700 accent;
    - шеврон 24 `textTertiary`; строка кликабельна → `HousesClicked`;
    - `null` → строка `home_all_houses` + `ChevronRight`.
    - TalkBack: «Следующий дом, Квартира с эркером, скоро, 6 000 тактов».
- **`NextHouseLine`** (новый `S/feature/home/NextHouseLine.kt`) — `sealed`:
  - `Soon(price)`, `Short(balance, price, fraction)`, `Enough(price)`;
  - `of(house, balance) = !drawn → Soon; balance ≥ price → Enough; else Short`.
  - Его же используют строки «Домов».

### 3.7 «Дома» — `HousesScreen` (416–462)

- Строки: `surfaceContainer`, скругление 18, поля 14, зазор 14, между строками 12.
- Силуэт 84×52 — цвета прежние. Имя — 16/800, до двух строк.
- Текущий дом: чип 22 `accentSoft` «здесь живу» (11/800 accent) на 4 над именем и рамка 1,5 accent.
- Не нарисованный: вместо заметки «скоро · 6 000»; строка не нажимается, полосы нет.
- Нарисованный и не купленный: справа `NextHouseLine` (`Short` → «1 200 / 3 000» 13/700 `onSurfaceVariant`, `Enough` → «хватает · …» accent) и шеврон 24; при `Short` — `PriceBar` 6, 8 под заметкой.
- Купленный и не текущий: `PillButton(houses_live)` 40/48, 14/700.
- Строка `semantics(mergeDescendants)`; кнопка остаётся отдельным узлом.
- `ModalBottomSheet` (458–460) → `AppSheet(value = ui.houseCard, onHide = { HouseCardClosed })`.
- `HouseCard` (465–480) → публичный `HouseSheetContent(house, ui, onIntent)`:
  - картинка 200, «дом 1 из 6», имя (`heading`), заметка, «Что нового», `houses_moving_text`;
  - `BuyButton(fillMaxWidth)`;
  - при нехватке — `sessionsInWords(JourneyRules.sessionsLeft(price − balance, ui.config))` 13 `onSurfaceVariant`, 6 под кнопкой.

### 3.8 Строки этапа 118

- Новые: `journey_extras_hint`, `journey_extra_time_note`, `journey_extra_view_inside`, `journey_extra_view_outside`, `journey_extra_souvenir_note`, `venue_play_here_caption`, `journey_go_home`, `journey_here`, `journey_here_enough`, `journey_here_end`, `journey_passport_next`, `houses_enough`.
- `journey_places`, седьмой элемент (Лейпциг) — запятая вместо « · » на десяти языках (раздел 5).
- Удалить: `venue_play_here_hint`, `home_brought`.
- `docs/notes/venue.md:5,23`: `venue_halls` теперь нужен остановке.

### 3.9 Тесты этапа 118

- `T/feature/home/HomeViewModelTest.kt:52` — новый конструктор. Новый тест `theUiCarriesTheConfigOfTheGraph`: `JourneyConfig(taktsPerSessionHint = 250)` → `state.value.config`.
- `CT/feature/home/NextHouseLineTest.kt`: `wood` 3 000 при 1 200 → `Short` (0,4), при 3 000 → `Enough`; `flat` → `Soon`.
- `T/LocalizationTest.kt` — новый тест: ни один элемент `journey_places` ни в одном языке не содержит « · ».
- `AT/core/ui/components/ControlsTouchTest.kt` (компиляция) — `aTwoWayOnAPictureAnswersOverFortyEight`: высота ≥ 48, `Role.Tab`, `selected`.
- Зелёными остаются `HomeRulesTest`, `JourneyMapMathTest`, `JourneyViewModelTest` (остановка), `AccessibilitySemanticsTest.aTwoWaySwitchSaysWhichSideIsChosen`.

### 3.10 Превью этапа 118

- **`JourneyScreenPreviews.kt`** добавляет:
  - остановку: без дополнений, с купленными, две плашки на 360 dp (de), нехватку, Кремону («вид снаружи»), landscape;
  - её полный экран;
  - карту: город, дом, маршрут пройден, landscape;
  - паспорт.
- **Новый `A/feature/home/HomeScreenPreviews.kt`:**
  - дом: обычное, подарок, снаружи, ничего не куплено, `NextHouseRow(null)`, landscape 892×412 и 640×360 (две строки), 360×640;
  - «Дома»; `HouseSheetContent` в `AppSheetCard` — хватает и не хватает.

### 3.11 Проверки этапа 118

- Эмулятор, состояние `extras`: плашки на открытке (ряд, а на 360 de — вторая над первой), полный экран, «Играть здесь» → Live в зале, «Домой» → заставка → дом.
- Карта: карточка не закрывает Сидней и Буэнос-Айрес на 360×640; касание — остановка; дома — заставка.
- Паспорт: три колонки и «следующая».
- Дом: зона, комната 260–285 на 360×640, «Комната | Снаружи» справа сверху, подарок (`gift`), строка следующего дома; landscape: строка из трёх на 892, две строки на 640×360, затухания нет.
- «Дома»: лист дома, смахивание не переезжает, переезд работает.
- `snap.py`: экран путешествия после 117 → после 118 — 0 %.
- iOS: `-openRoute journeyStop/vienna`, `journeyMap`, `journeyPassport`, `home`, `homeHouses`.

## 4. Этап 119 — лавка, карточка вещи, примерка, «Обставить» и проверка R7

### 4.1 Маршрут

- **`S/navigation/Routes.kt`** (после 51 и 84):
  ```kotlin
  const val HOME_SHOP_PATTERN = "$HOME_SHOP?${HomeViewModel.ARG_SLOT}={${HomeViewModel.ARG_SLOT}}"
  fun homeShop(slot: String? = null): String =
      if (slot == null) HOME_SHOP else "$HOME_SHOP?${HomeViewModel.ARG_SLOT}=${encodeQuery(slot)}"
  ```
  `HomeViewModel.ARG_SLOT = "slot"`.
- **`A/navigation/AppNavHost.kt:355–368` и `I/ios/IosNavHost.kt:397–409`:**
  - `Routes.HOME_SHOP_PATTERN` с `navArgument(ARG_SLOT) { type = StringType; nullable = true; defaultValue = null }`.
  - `HomeRoute(slot = entry.savedStateHandle.get<String>(ARG_SLOT), onOpenShopAt = { navController.navigate(Routes.homeShop(it)) { launchSingleTop = true } }, …)`.
  - Для остальных трёх маршрутов — прежнее.
- **`S/feature/home/HomeRoute.kt`.**
  - Параметры `slot: String? = null`, `onOpenShopAt: (String) -> Unit`.
  - `LaunchedEffect(viewModel, slot) { slot?.let { viewModel.onIntent(HomeIntent.ShopSlotGiven(it)) } }`.
  - Эффект `OpenShopAt`.
  - `BackHandler` (63) — прежний.

### 4.2 Контракт и модель

- **`HomeContract.kt`.**
  - `HomeUi`: `slot: String? = null`, `arrangeFocus: String? = null`.
  - Интенты: `ShopSlotGiven(slot)`, `SlotFilterCleared`, `ShopAtClicked(slot)`.
  - Эффект: `OpenShopAt(slot)`.
- **`HomeViewModel.kt`:**
  - `BackClicked`, ветка `tryOn` (56) → `copy(card = it.tryOn, tryOn = null, tryMode = null)` — так же, как `TryClosed`.
  - `CategorySelected` (73) → `copy(category = group, slot = null)`.
  - `SlotFilterCleared` → `slot = null`.
  - `ShopSlotGiven` — один раз (D6), только если `HomeCatalog.slotById` знает место.
  - `SideSelected` (61) → и `arrangeFocus = null`.
  - `Placed` (80) → `place(…)` и `arrangeFocus = slot`.
  - `ShopAtClicked` → `arrangeFocus = null` и эффект `OpenShopAt`.

### 4.3 Лавка — `S/feature/home/ShopScreen.kt`

- **Чипы (264–269):** `Row(horizontalScroll, padding(h16, bottom 10), spacedBy 8, selectableGroup)`.
  - Первым при `ui.slot != null` — `AppChip(slotName(slot), selected = true, onClick = SlotFilterCleared, trailing = AppIcons.Close, onClickLabel = shop_place_clear, Modifier.semantics { contentDescription = shop_place(slotName) })`.
  - Дальше «Всё» и девять рядов; выбраны при `slot == null` и совпадении `category`.
- **Полки (271–293):** `ShopShelves.of(ui.category, ui.slot)` (новый `S/feature/home/ShopShelves.kt`, чистый):
  - ряды → вещи без `startItems`, при `slot` — только `it.slot == slot`, пустые ряды выпадают.
  - «Полка ваша» — только при `slot == null`. Строка о тактах — как сейчас.
- **`Tile` (327–333):** таблетка города — `background(ViolinTheme.glassStrong, CircleShape)`, 9,5 sp, 700, `onSurface`.
- **`ModalBottomSheet` (295–297)** → `AppSheet(value = ui.card, onHide = { CardClosed }) { ItemCardContent(it, ui, onIntent) }`.
- **`TAKTS_A_SESSION` (227–228)** и **`sessionsLeftWords` (511–516)** удаляются.

### 4.4 Карточка вещи — новый `S/feature/home/ItemCard.kt`

- **`ItemCardPlan.of(item, tag, chosen, here, inSeason, balance, next: JourneyStop?, config)`** — чистый. Поля:
  - `chip` — `STANDING` / `STANDING_OUTSIDE` / `OWNED` / `null`;
  - `placeNote` — `NEEDS_CHIMNEY` / `NO_PLACE` / `WAITS_SEASON` / `null`;
  - `money` — `After(left, toNext: StillEnough | More(n) | null)` / `Balance(balance)` / `null`;
  - `buttons` — `None` / `Put` / `TryAndBuy` / `Buy` / `TryAndTake`;
  - `sessions` — число занятий для подсказки.
- **Ветки** — по 3.36.7 «Карточка вещи»:
  - OWNED и `here && !chosen` → `Put`, без чипа;
  - OWNED в остальных случаях → чип `OWNED` и подпись у «Места»;
  - STANDING → только чип;
  - GIFT → `TryAndTake`, без строк о тактах;
  - PRICE и `!here` → `Buy` во всю ширину.
- **Разметка:**
  - картинка 180 на материале полки;
  - «из Вены» — 13/700 accent; имя — 22/800, до двух строк, `heading`; чип 28 `accentSoft`; описание — 14 `onSurfaceVariant`;
  - строки ≥ 40 с чертой 1 `outlineVariant` сверху: ключ 14 `onSurfaceVariant`, значение 14/700 tnum справа до двух строк; подпись места — 13;
  - ряд кнопок (`spacedBy 10`, 14 сверху): `AppButton(shop_try, Outline, weight 1)` и `BuyButton`/`AppButton(shop_take)` с `weight 1.4f`; одна кнопка — во всю ширину;
  - `sessions > 0` → `sessionsInWords` 13, 6 под рядом.

### 4.5 Примерка — `TryOn` (413–449)

- Сверху — `Row` на `Brush.verticalGradient(surface 0.8 → 0)`:
  - «назад» 48 (`AppIcons.Back`, `onClickLabel = shop_try_remove`) → `TryClosed`;
  - `shop_try_title(itemName)` 16/800, одна строка, многоточие;
  - `TwoWay(evening, day)` на стекле.
- Имя и место над кнопками (440–441) уходят.
- Снизу — прежнее затемнение:
  - `AppButton(shop_try_remove, Outline, outline = White.copy(0.35f))` по слову — нужен параметр `outline` из D1;
  - `BuyButton(weight 1)` без подсказки о занятиях.

### 4.6 «Обставить» — `ArrangeScreen` (456–509)

- Комната: `HomePicture(…, outline = ui.arrangeFocus)`, 210, скругление 20, над прокруткой.
- Подпись мест — 13 `onSurfaceVariant`, 8 до первого места.
- Место: `Column(padding vertical 12)` с чертой 1 `outlineVariant` снизу.
  - Имя — 15/700, многоточие.
  - При `more > 0` — `Row(heightIn 48, clickable(role = Button)) { arrange_in_shop(more) 13/700 accent; ArrowRight 16 accent }` → `ShopAtClicked(slot.id)`.
- Ряд плиток (`horizontalScroll`, `selectableGroup`, `padding(vertical 3)` под кольцо): «пусто» **первой** (условие прежнее — строка 495), затем вещи.
- **`Choice` (522–538):**
  - 72×72, скругление 14, `surfaceContainer`, без подписи;
  - выбранная — рамка 2 accent, снаружи кольцо 3 accent на 25 %, круг 22 accent с галочкой 14 `onPrimary` в правом верхнем углу;
  - «пусто» — пунктир 1,5 `outlineVariant` и слово 12/700 `textTertiary`;
  - городская вещь — таблетка на `glassStrong`;
  - `selectable(role = RadioButton)`, `contentDescription = listOfNotNull(имя, город).joinToString(", ")`.

### 4.7 Обводка

- **Новый `S/feature/home/ArrangeOutline.kt`:**
  - `thingOf(slot, state, house, outside, date): HomeItem?`. `null`, если: `slotById[slot].palette`; `placed[slot] == null`; `!inSeason(chosen)`; вещи нет в `standing(…)`.
  - `frameOf(box: Rect, seen: Rect, pad: Float): Rect?` — `null`, если `box` не пересекает `seen`; иначе рамка с полем, обрезанная по `seen`.
- **`S/feature/home/art/HomePicture.kt:57–78`** — параметр `outline: String? = null`:
  - `framed = remember(state, house, outside, time.date, outline) { ArrangeOutline.thingOf(…) }`, `box = art?.items?.get(framed.id)`.
  - `overlay` для `ScenePicture`: `k`, `left`, `top`, `seen` по D14. Затем `drawRoundRect(primary, …, CornerRadius(10.dp.toPx() / k), Stroke(2.dp.toPx() / k, pathEffect = dash(6 / k, 4 / k)))`, поле 4 dp / k.
  - Без анимации и без дыхания.

### 4.8 Общие части этапа 119

- **`S/core/ui/components/AppChip.kt:49–86`** — `trailing: ImageVector? = null`, `onClickLabel: String? = null`.
  - После слова — значок 18 цвета слов.
  - С `onClickLabel`: `clickable(onClickLabel, role = RadioButton).semantics { selected = selected }` вместо `selectable`.
- **`AppButtons.kt`** — параметр `outline` (D1).

### 4.9 Строки этапа 119

- Новые: `shop_row_place`, `shop_row_after`, `shop_row_to`, `shop_still_enough`, `shop_still_more`, `shop_row_balance`, `shop_place_clear`, `shop_try_title`.
- Удалить: `shop_left_after_road`, `shop_sessions_one`, `_few`, `_many`, `_one_counted`.

### 4.10 Тесты этапа 119

- `CT/navigation/RoutesTest.kt`:
  - строка 61: `fill(HOME_SHOP_PATTERN, ARG_SLOT to "deskR") == homeShop("deskR")`, `homeShop(null) == HOME_SHOP`;
  - строки 69/73: `HOME_SHOP_PATTERN` → ключ `homeShop`.
- `T/feature/home/HomeViewModelTest.kt`:
  - `backFoldsWhatIsOpenBeforeItLeaves` (113–128): первое «назад» → `tryOn == null`, `card == pouf`; второе → `card == null`; третье → `Close`;
  - `aShopForOnePlaceShowsOnlyItsThings`: `ShopSlotGiven` ставит место, второй раз не меняет; `CategorySelected(null)`, `CategorySelected(group)` и `SlotFilterCleared` снимают;
  - `theArrangeFocus…`: `Placed` ставит; `Placed` другого места переносит; `SideSelected` снимает; `ShopAtClicked` снимает и шлёт `OpenShopAt`.
- `CT/feature/home/ArrangeOutlineTest.kt`:
  - обои и пол → `null`; «пусто» (`deskR` = `""`) → `null`;
  - ёлка в октябре → `null`, в декабре → `xmas`;
  - шторы → вещь; камин в `rent` → `null`;
  - `frameOf`: целиком внутри, срезана краем, целиком снаружи.
- `CT/feature/home/ShopShelvesTest.kt` — по месту только его вещи; ряды без них выпадают; стартовых вещей нет.
- `CT/feature/home/ItemCardPlanTest.kt`:
  - лампа 300 при 47 884 → `StillEnough`;
  - покупка съедает дорогу → `More`;
  - люстра 900 при 472 → `Balance(472)`, `sessions 2`;
  - `next = null`; нет места; не сезон; купленная; купленная без места; стоит; подарок.
- `CT/feature/home/ShopWordsTest.kt:21–29` — тест `sessionsLeftWords` удалить, `placesWords` оставить.
- `AT/core/ui/components/ControlsTouchTest.kt` (компиляция) — `aPlaceChipClearsItsFilterAndSaysSo`: действие с меткой, `selected`, касание 48.
- Зелёными остаются `ShelfTagsTest`, `HomeComposerTest`, `HomeRulesTest`, `LocalizationTest`, `AppIconsTest`.

### 4.11 Превью этапа 119

В `HomeScreenPreviews.kt`:
- лавка: полки, первый подарок, по месту;
- `ItemCardContent` в `AppSheetCard`: хватает, не хватает (люстра 900 при 472), маршрут пройден, нет места, не сезон, купленная, куплена и места нет, стоит, подарок;
- примерка — `ShopScreen(ui.copy(tryOn = …))`;
- «Обставить»: с обводкой и без, снаружи.

В `ComponentsPreviews.kt` — `AppChip` с `trailing`.

### 4.12 Проверка R7 целиком

1. Полный набор `./gradlew …` из 2.8, `check.py` ×9.
2. **Снимки «после».** Те же состояния `state.sh` и раскладки, что в 2.0.3, плюс `font_scale 1.3`, de и fr на 360.
   - Если день сменился — переснять «до» с `before.apk`.
   - `snap.py compare`: без изменений должны остаться дорога, заставки, переезд, «Занятия» (окно R2) и Live — 0 %.
   - Остальные экраны сверять глазом, парами «до / после».
3. **Сценарии:**
   - «в лавке N →» → лавка по месту → ✕ / «Всё» / ряд → «назад» в «Обставить» без обводки;
   - обводка: касание, другое место, «Снаружи» снимает; обои, ёлка вне сезона, вещь вне кадра — без рамки; шторы — с рамкой;
   - карточка во всех статусах; «Купить» и сообщение «Куплено: …»; смахивание не покупает;
   - примерка: «назад» в шапке и системный → карточка → ещё «назад» → полка → ещё → уход из лавки.
   - TalkBack выборочно: плашка — текст, чип места «Снять фильтр», строка дополнения — одно описание.
4. **Замеры.** `measure.py journey-after`, `home-after`, `stop-after`, `arrange-after`, `arrange-outline` (фокус стоит), `shop-after`.
   - Прогоны `before.apk` / `after.apk` подряд, лучше на `emulator-5560`.
   - Критерий: запись кадра главного потока p50 и CPU в пределах разброса (≈ 10 %). Нижняя зона слоя не добавляет: в `AppDock` нет `graphicsLayer`. Обводка стоит один `drawRoundRect` за кадр.
   - По желанию — iOS Profile (симулятор): CPU процесса за 20 с на доме, до и после (`ps -o time=`).
5. **iOS.**
   - xcodebuild Debug, данные с эмулятора, `-sceneSeconds 12.5`.
   - `-openRoute journey | journeyStop/vienna | journeyMap | journeyPassport | home | homeShop | 'homeShop?slot=deskR' | homeArrange | homeHouses` — снимки.
   - Сообщение «Куплено: …» над зоной дома (подарок с «Дома») требует касания — в список «Проверить — ведущему».
6. **Заметки.** `docs/notes/redesign.md` («Этап 117 / 118 / 119»: отклонения D4, D5, D10–D13, новая строка, что не проверено — телефон и VoiceOver на слух). Поведение в `docs/notes/home.md` (примерка «назад», лавка по месту, обводка) и `docs/notes/journey.md` (зона, карточка карты). Только эмулятор, телефон владельца — нет.

## 5. Строки (ru | en | de | fr | es | it | pt | ja | zh | ko)

**Этап 117** (`strings.xml`, блок `journey_*` после строки 642):

- `journey_depart_spend`:
  - ru: спишется %1$s из %2$s
  - en: %1$s of %2$s will be spent
  - de: %1$s von %2$s werden abgebucht
  - fr: %1$s sur %2$s seront dépensées
  - es: se gastarán %1$s de %2$s
  - it: si spenderanno %1$s su %2$s
  - pt: serão gastos %1$s de %2$s
  - ja: %2$s のうち %1$s を使います
  - zh: 将用去 %1$s（共 %2$s）
  - ko: %2$s 중 %1$s 사용
- `journey_sessions_one` / `_few` / `_many`:
  - ru: примерно %1$d занятие / примерно %1$d занятия / примерно %1$d занятий
  - en: about %1$d practice / about %1$d practices / about %1$d practices
  - de: etwa %1$d Mal üben (все три)
  - fr: environ %1$d séance / environ %1$d séances ×2
  - es: alrededor de %1$d práctica / unas %1$d prácticas ×2
  - it: circa %1$d sessione / circa %1$d sessioni ×2
  - pt: cerca de %1$d prática / umas %1$d práticas ×2
  - ja: 練習 %1$d 回くらい (все три)
  - zh: 大约练 %1$d 次 (все три)
  - ko: 연습 %1$d번쯤 (все три)
- `journey_tour_done`:
  - ru: Мировое турне пройдено
  - en: World tour complete
  - de: Welttournee geschafft
  - fr: Tournée mondiale bouclée
  - es: Gira mundial completada
  - it: Giro del mondo completato
  - pt: Turnê mundial concluída
  - ja: ワールドツアー完走
  - zh: 世界巡演已走完
  - ko: 월드 투어 완주
- `journey_tour_more`:
  - ru: новые города придут с обновлениями
  - en: new cities will come with updates
  - de: neue Städte kommen mit Updates
  - fr: de nouvelles villes arriveront avec les mises à jour
  - es: llegarán nuevas ciudades con las actualizaciones
  - it: nuove città arriveranno con gli aggiornamenti
  - pt: novas cidades chegarão com as atualizações
  - ja: 新しい街はアップデートで届きます
  - zh: 新的城市会随更新到来
  - ko: 새 도시는 업데이트로 찾아옵니다
- `journey_stamp_number`:
  - ru: Штамп № %1$d
  - en: Stamp No. %1$d
  - de: Stempel Nr. %1$d
  - fr: Tampon n° %1$d
  - es: Sello n.º %1$d
  - it: Timbro n. %1$d
  - pt: Carimbo nº %1$d
  - ja: スタンプ No. %1$d
  - zh: 第 %1$d 枚印章
  - ko: 도장 %1$d번
- `journey_stamp_leg`:
  - ru: До %1$s — %2$s
  - en: To %1$s — %2$s
  - de: Bis %1$s – %2$s
  - fr: Pour %1$s — %2$s
  - es: Hasta %1$s: %2$s
  - it: Per %1$s — %2$s
  - pt: Até %1$s — %2$s
  - ja: %1$sまで — %2$s
  - zh: 到%1$s——%2$s
  - ko: %1$s까지 — %2$s
- `journey_stamp_last`:
  - ru: Дальше дорога ещё рисуется.
  - en: The road ahead is still being drawn.
  - de: Der weitere Weg wird noch gezeichnet.
  - fr: La suite de la route est encore en dessin.
  - es: Más allá, el camino aún se está dibujando.
  - it: Più avanti la strada è ancora da disegnare.
  - pt: Daqui em diante a estrada ainda está sendo desenhada.
  - ja: この先の道は、まだ描いているところです。
  - zh: 前方的路还在绘制中。
  - ko: 그다음 길은 아직 그려지는 중입니다.
- `journey_have_description` (второй аргумент — число со словом, `taktsInWords`: «1 600 тактов» / «1 600 Takte» / «1 600 小节»):
  - ru: %1$s из %2$s
  - en: %1$s of %2$s
  - de: %1$s der %2$s (ревью этапа 117: после «von» нужен дательный «Takten», а слово приходит в именительном «Takte»; родительный множественного — та же форма)
  - fr: %1$s sur %2$s
  - es: %1$s de %2$s
  - it: %1$s su %2$s
  - pt: %1$s de %2$s
  - ja: %2$s のうち %1$s
  - zh: 已有 %1$s，共需 %2$s (ревью этапа 117: строка только для TalkBack — словами, не «/»)
  - ko: %2$s 중 %1$s

**Этап 118:**

- `journey_extras_hint`:
  - ru: маленькие цели в дороге
  - en: small goals on the road
  - de: kleine Ziele unterwegs
  - fr: petits objectifs en chemin
  - es: pequeñas metas por el camino
  - it: piccoli traguardi lungo la strada
  - pt: pequenas metas pelo caminho
  - ja: 旅の小さな目標
  - zh: 旅途中的小目标
  - ko: 여행길의 작은 목표
- `journey_extra_time_note`:
  - ru: день вместо вечера
  - en: day instead of evening
  - de: Tag statt Abend
  - fr: le jour au lieu du soir
  - es: el día en vez de la tarde
  - it: il giorno invece della sera
  - pt: o dia em vez da noite
  - ja: 夕方のかわりに昼
  - zh: 白天代替傍晚
  - ko: 저녁 대신 낮
- `journey_extra_view_inside`:
  - ru: вид изнутри
  - en: the view from inside
  - de: Ansicht von innen
  - fr: la vue de l\'intérieur
  - es: la vista desde dentro
  - it: la veduta dall\'interno
  - pt: a vista por dentro
  - ja: 中からの眺め
  - zh: 内部视角
  - ko: 안에서 본 풍경
- `journey_extra_view_outside`:
  - ru: вид снаружи
  - en: the view from outside
  - de: Ansicht von außen
  - fr: la vue de l\'extérieur
  - es: la vista desde fuera
  - it: la veduta dall\'esterno
  - pt: a vista por fora
  - ja: 外からの眺め
  - zh: 外部视角
  - ko: 밖에서 본 풍경
- `journey_extra_souvenir_note`:
  - ru: наклейка рядом со штампом
  - en: a sticker beside the stamp
  - de: ein Aufkleber neben dem Stempel
  - fr: un autocollant à côté du tampon
  - es: una pegatina junto al sello
  - it: un adesivo accanto al timbro
  - pt: um adesivo ao lado do carimbo
  - ja: スタンプの横に貼るシール
  - zh: 贴在印章旁的贴纸
  - ko: 도장 옆에 붙이는 스티커
- `venue_play_here_caption`: `Live · %1$s` — во всех десяти одинаково.
- `journey_go_home`:
  - ru: Домой
  - en: Home
  - de: Nach Hause
  - fr: À la maison
  - es: A casa
  - it: A casa
  - pt: Para casa
  - ja: 家へ
  - zh: 回家
  - ko: 집으로
- `journey_here`:
  - ru: вы здесь
  - en: you are here
  - de: Sie sind hier
  - fr: vous êtes ici
  - es: estás aquí
  - it: sei qui
  - pt: você está aqui
  - ja: 現在地
  - zh: 你在这里
  - ko: 현재 위치
- `journey_here_enough`:
  - ru: до %1$s хватает
  - en: enough to reach %1$s
  - de: reicht bis %1$s
  - fr: assez pour %1$s
  - es: alcanza hasta %1$s
  - it: bastano per %1$s
  - pt: dá para ir até %1$s
  - ja: %1$sまで行けます
  - zh: 够到%1$s了
  - ko: %1$s까지 갈 수 있어요
- `journey_here_end`:
  - ru: маршрут пройден
  - en: route complete
  - de: Route geschafft
  - fr: itinéraire terminé
  - es: ruta completada
  - it: percorso completato
  - pt: rota concluída
  - ja: 旅路を巡り終えました
  - zh: 路线已走完
  - ko: 여정 완주
- `journey_passport_next`:
  - ru: следующая
  - en: next
  - de: als Nächstes
  - fr: prochaine
  - es: la siguiente
  - it: prossima
  - pt: próxima
  - ja: 次
  - zh: 下一站
  - ko: 다음
- `houses_enough`:
  - ru: хватает · %1$s
  - en: enough · %1$s
  - de: reicht · %1$s
  - fr: assez · %1$s
  - es: alcanza · %1$s
  - it: bastano · %1$s
  - pt: suficiente · %1$s
  - ja: 足ります · %1$s
  - zh: 够了 · %1$s
  - ko: 충분해요 · %1$s
- `journey_places`, седьмой элемент:
  - ru: Гевандхаус, церковь Св. Фомы
  - en: Gewandhaus, St Thomas Church
  - de: Gewandhaus, Thomaskirche
  - fr: Gewandhaus, église Saint-Thomas
  - es: Gewandhaus, iglesia de Santo Tomás
  - it: Gewandhaus, chiesa di San Tommaso
  - pt: Gewandhaus, Igreja de São Tomás
  - ja: ゲヴァントハウス、聖トーマス教会
  - zh: 布商大厦音乐厅，圣托马斯教堂
  - ko: 게반트하우스, 성 토마스 교회

**Этап 119** (`strings_home.xml`):

- `shop_row_place`:
  - ru: Место
  - en: Place
  - de: Platz
  - fr: Emplacement
  - es: Lugar
  - it: Posto
  - pt: Lugar
  - ja: 場所
  - zh: 位置
  - ko: 자리
- `shop_row_after`:
  - ru: После покупки
  - en: After buying
  - de: Nach dem Kauf
  - fr: Après l\'achat
  - es: Tras la compra
  - it: Dopo l\'acquisto
  - pt: Depois da compra
  - ja: 購入後
  - zh: 购买后
  - ko: 구매 후
- `shop_row_to`:
  - ru: До %1$s
  - en: To %1$s
  - de: Bis %1$s
  - fr: Pour %1$s
  - es: Hasta %1$s
  - it: Per %1$s
  - pt: Até %1$s
  - ja: %1$sまで
  - zh: 到%1$s
  - ko: %1$s까지
- `shop_still_enough`:
  - ru: всё ещё хватает
  - en: still enough
  - de: reicht noch
  - fr: toujours assez
  - es: aún alcanza
  - it: bastano ancora
  - pt: ainda dá
  - ja: まだ足ります
  - zh: 仍然够
  - ko: 여전히 충분
- `shop_still_more`:
  - ru: ещё %1$s
  - en: %1$s more
  - de: noch %1$s
  - fr: encore %1$s
  - es: faltan %1$s
  - it: mancano %1$s
  - pt: faltam %1$s
  - ja: あと %1$s
  - zh: 还差 %1$s
  - ko: %1$s 더
- `shop_row_balance`:
  - ru: Баланс
  - en: Balance
  - de: Guthaben
  - fr: Solde
  - es: Saldo
  - it: Saldo
  - pt: Saldo
  - ja: 残高
  - zh: 余额
  - ko: 잔액
- `shop_place_clear`:
  - ru: Снять фильтр
  - en: Clear the filter
  - de: Filter entfernen
  - fr: Retirer le filtre
  - es: Quitar el filtro
  - it: Togli il filtro
  - pt: Remover o filtro
  - ja: 絞り込みを解除
  - zh: 取消筛选
  - ko: 필터 해제
- `shop_try_title`:
  - ru: Примерка · %1$s
  - en: Try-on · %1$s
  - de: Anprobe · %1$s
  - fr: Essai · %1$s
  - es: Prueba · %1$s
  - it: Prova · %1$s
  - pt: Experimentando · %1$s
  - ja: 試し置き · %1$s
  - zh: 试摆 · %1$s
  - ko: 놓아 보기 · %1$s

## 6. Риски

1. **Слияние с R4 и R5** в `AppButtons.kt` и `AppChip.kt`: параметры добавлять после их параметров. После правки прогнать на эмуляторе `ControlsTouchTest`, `AppDockTest`, `AppSheetTest`, `AccessibilitySemanticsTest`.
2. **Раскладка `KeptLine` и autosize** внутри `Button` Material: число должно оставаться целым при 1,3 (ru «Санкт-Петербург → до Москвы 1 000» на 360). Проверить превью и на эмуляторе.
3. **`AppSheet` вместо `ModalBottomSheet`** у карточки. Примерка возвращает лист заново (`TryClosed` → `card`); `ShopScreen` возвращает `TryOn` раньше листа, поэтому при «Примерить» лист исчезает сразу, как сейчас. «Назад» листа закрывает его через `onHide`.
4. **`savedStateHandle`** у записи маршрута на iOS: проверить `-openRoute 'homeShop?slot=deskR'`. Если аргумент не придёт — `entry.arguments?.read { getStringOrNull(…) }`.
5. **Состояния через SQL** — только на эмуляторе и только по копии (`base.tar`), с возвратом в конце. Не забывать `-wal` и `-shm`.
6. **Обводка при панораме** (`camera`) не сработала бы — поэтому в полном экране и примерке `outline` не передаётся.
7. **Дом днём и вечером — по часам**, ёлка — по дате: снимки «до» и «после» снимать в одной половине дня и в один день. Выручает `before.apk`.
8. **Этап 105 (окно R2)** в том же пакете `feature/journey`: `JourneyWindowCard.kt` и `PriceBar` не трогать, строки сливать руками.

### Critical Files for Implementation
- /Users/danil/AndroidStudioProjects/ViolinTuner/shared/src/commonMain/kotlin/com/violinjourney/app/feature/journey/JourneyScreen.kt
- /Users/danil/AndroidStudioProjects/ViolinTuner/shared/src/commonMain/kotlin/com/violinjourney/app/feature/journey/JourneyPlaces.kt
- /Users/danil/AndroidStudioProjects/ViolinTuner/shared/src/commonMain/kotlin/com/violinjourney/app/feature/home/HomeScreen.kt
- /Users/danil/AndroidStudioProjects/ViolinTuner/shared/src/commonMain/kotlin/com/violinjourney/app/feature/home/ShopScreen.kt
- /Users/danil/AndroidStudioProjects/ViolinTuner/shared/src/commonMain/kotlin/com/violinjourney/app/feature/home/HomeViewModel.kt
- /Users/danil/AndroidStudioProjects/ViolinTuner/shared/src/commonMain/kotlin/com/violinjourney/app/core/ui/components/AppButtons.kt