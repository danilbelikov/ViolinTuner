# R6 Live: план этапов 114–116 (спека 3.36.6, 5.29 R6)

Пути ниже сокращены. **S** = `/Users/danil/AndroidStudioProjects/ViolinTuner/shared/src/commonMain/kotlin/com/violinjourney/app`. **CT** = `…/shared/src/commonTest/kotlin/com/violinjourney/app`. **IT** = `…/shared/src/iosTest/kotlin/com/violinjourney/app`. **I** = `…/shared/src/iosMain/kotlin/com/violinjourney/app`. **A** = `…/app/src/main/java/com/violinjourney/app`. **T** = `…/app/src/test/java/com/violinjourney/app`. **RES** = `…/app/src/main/res/values*/strings.xml` (источник — `values-ru`).

## 0. Что должно быть до старта

1. R4 и R5 закоммичены, этапы 104 и 105 тоже. От них R6 ожидает такое API:
   - `AppButton(…, compact, leading)`;
   - `AppChip.Choice(text, selected, onClick, modifier, inSheet, enabled, onLongClick?, onLongClickLabel?)` — R6 зовёт без двух последних;
   - `AppSheet(value, onHide, modifier, slideAway, dismissible, scroll, contentPadding, keepHandleRoom)`;
   - `SectionLabel(text, modifier)`, `AppSheetCard`, `GlassPlate(modifier, strong, shape, contentPadding)`;
   - `SegmentedSwitch`, `ListRow`, `ReasonLine`, `AppSwitchMark` из R4/R5 R6 не нужны: переключатель Live — свой прибор, строки листа — свой общий список.
2. **R4 удаляет `RecordTakeRow`** (`TakeBlocks.kt:103-217`, по `plan-r4.md`, D13 и строка 349). После этого у `S/feature/live/components/RecordButton.kt` остаётся один вызов — `LiveScreen.kt:101`. Перед этапом 115 проверить: `grep -rn "RecordButton(" shared/src`.
3. Статус R3. Если этап 107 сделан, то `recap_source_repertoire` и `ViolinTheme.done` уже есть. Если нет, R6 заводит строку сам, а галочку таблетки красит в `venueColors.brass`. Проверка: `grep -rn "recap_source_repertoire\|val done:" S RES`.
4. Коммиты по этапам: `feat(live): … (stage 11x, spec 3.36.6)` и `docs: stage 11x is done …`. В индексе лежат `.idea/*`, поэтому пути всегда указывать явно.

## 1. Мои решения

- **Р1. `-liveMode tuning` — первым коммитом этапа 114, а не в 116.**
  - Причина: `simctl` не умеет касаться, и без аргумента нельзя снять «до» «Настройки» на iOS.
  - `LiveViewModel` получает последним параметром `initialMode: LiveMode = LiveMode.PLAY` (`LiveViewModel.kt:40-51`); `target` на строке 70 становится `MutableStateFlow(LiveTarget(mode = initialMode))`.
  - `LiveState` и `LiveGauge` не меняются. `HiltLiveViewModel` не трогается: параметр со значением по умолчанию.
- **Р2. Стекло приборов — параметрами R1.**
  - `GlassPlate(…, edge: Boolean = false, ground: Color? = null)` и публичный `Modifier.glass(shape, strong = false, edge = false, ground = null)` в `GlassPlate.kt`.
  - Обводка — новый токен `GlassEdge` = белый 12 % (`Color.kt`). Это то же число спеки, а не сырой цвет в интерфейсе.
  - Подложку для `-PplainLive` даёт `liveGlassGround()`: `surfaceContainer`, когда `LocalLivePlain` = true.
  - Сделано функцией цвета, а не `@Composable Modifier`, чтобы не поймать lint `ComposableModifierFactory`.
- **Р3. Мягкие тени запекаются с самого начала** (клавиша 14, карточки 10, карточка «нет разрешения» 40).
  - `Modifier.softShadow(…)` через `drawWithCache` + `Paint.blurMask` в `ImageBitmap`, раз на размер — по образцу `StartPracticeButton.glowOf`.
  - Спека предлагает это как запасной ход «если замер покажет рост». Делаю сразу: на iOS размытие иначе проигрывается каждый кадр окна.
- **Р4. Обод клавиши 3 dp — внутри 76 (`border`), а не снаружи, как CSS-кольцо макета.** Тогда видимая клавиша, касание и ряд — все 76.
- **Р5. Новый `LiveRecordKey`.** Если после R4 у `RecordButton` нет вызовов, `RecordButton.kt` и его мерки в `LiveDimens` удаляются. Если вызов остался — `RecordButton` не трогается.
- **Р6. Одна карточка на двоих.** `LiveCard` (стекло → бумага, обод, ход) и чистый `LiveCardWords` (авторазмер 13,5 → 12 sp шагом 0,5, затем перенос первой строки). `BlockBookmark` и `PracticeTag` становятся тонкими обёртками над ней.
- **Р7. Шкала переезжает в столбец кольца.**
  - Запас под кольцом считает чистая `reservedUnderRing(scaleShown)`.
  - `scaleShown` — `animateFloatAsState` 200 мс (`STRING_ROW_EXPAND_MS`). Иначе кольцо на малом экране прыгнет на 44 dp при смене режима: сейчас его плавно сжимает раскрытие `ScaleSlot` под ним.
- **Р8. Высоты рядов портрета — числами в `LiveLayoutMath` (`PortraitRows`),** `LiveDimens` берёт их оттуда. Так тест «на 360 × 640 кольцо меньше на ≈ 2 dp» проверяет модель раскладки, а не арифметику в самом тесте.
- **Р9. Карточка «нет разрешения».**
  - Место считает чистая `promptTop(H, h)`.
  - Что уходит при нехватке — чистая `promptFit(...)`: сначала плашка значка, потом текст прокручивается. Раскладка карточки — свой `Layout`.
  - `ringModifier` вешается на карточку, поэтому вуаль сама берёт её центр и ширину.
- **Р10. Приглушение панели вкладок.**
  - Новый слот `LiveSlots.light(landscape, chrome)`. `LiveScreen` одалживает свет через `LifecycleResumeEffect`: при уходе с Live запись сразу уходит из RESUMED, и `takeBack` не ждёт конца кроссфейда навигации.
  - Одалживает только в портрете.
- **Р11. `LiveSwitches` — константы `false`, но экраны берут их параметрами со значением по умолчанию** (`LiveScreen(dimTabBar = …)`, `LiveScreenLayout(stringSilhouette = …)`). Тогда превью показывают включённое. Теста «оба false» нет: включение должно оставаться одной строкой.
- **Р12. Силуэт ноты — новый вид `RingContentKind.SILHOUETTE`.**
  - `NoteLabel` не меняется: альфа 0,28 даёт `graphicsLayer`.
  - Для TalkBack — `clearAndSetSemantics {}`.
  - Смена ноты при другой зафиксированной струне — мгновенная, вид тот же.
- **Р13. Лист на `AppSheet(scroll = false, contentPadding = 0)`.**
  - Общий список — новый `block/PickerList.kt`, без знания о Live (для R9).
  - Ряды целей — чистая `GoalRows`.
  - Между рядами чипов в коде `spacedBy(2.dp)`: `minimumInteractiveComponentSize` раздувает чип 44 до 48, и глазу остаются нужные 6.
- **Р14. Сверка ядра по пикселям.** В `tools/perf/snap.py` добавляется подкоманда `compare-region <a> <b> x y w h [--search 40]`: сравнивает область кольца, ноты, слова и цифр с поиском сдвига по вертикали. Кольцо в «Игре» после R6 опускается примерно на 4 dp (плашка 36 вместо 28), поэтому сравнение всего экрана тут не годится.
- **Р15. `mic_permission_text` и `tools/ios/infoplist.py` не трогаются.** Спека права против `05-live.md`, строки 49–50.
- **Р16. Текст карточки «нет разрешения» — по началу строки, как в макете.** Цвета — числа спеки: плашка значка `CtrlBoneShade`, а не `#E4DDCC` макета; текст `CtrlInkSoft`, а не `#4E4650`.
- **Р17. Лента полосы записи — без подложки дорожки** (в макете её нет); отрезки со скруглением 2.
- **Р18. Переключатель «Игра | Настройка» на узком экране.** Ширина сегмента — clamp(текст + 2 × 16, 106, (ширина ряда − 2 × 48 − 8) / 2). Если слово не входит, оно уменьшается до 12 sp через `LiveCardWords`. Иначе на 320 dp при шрифте 1,5 «Настройка» наезжает на касание шестерёнки.

## 2. Этап 114 — верх Live и «Настройка»

### 2.0 До правки UI (отдельные коммиты)

1. **Р1.**
   - `I/ios/LaunchArguments.kt`: чистая `liveModeOf(arguments, devApp): LiveMode?` — только для `.debug`, без учёта регистра, неизвестное значение → null — и `val liveMode by lazy { liveModeOf(all, IosBuild.isDevApp) }`.
   - `I/ios/IosNavHost.kt:100-106`: `LiveViewModel(…, initialMode = LaunchArguments.liveMode ?: LiveMode.PLAY)`.
   - `LiveViewModel.kt:40-51, 70` — как в Р1.
   - KDoc `MainViewController.kt:32-35` и строка iOS в `CLAUDE.md:112`: добавить `[-liveMode tuning]`.
2. **Р14** — `tools/perf/snap.py`.
3. **Снимки «до»**, один день, в `~/ViolinSnaps/r6/before/<размер>_<место>_<режим>_<сценарий>.raw`.
   - Подготовка эмулятора: анимации 0 (`animator_duration_scale`, `transition_animation_scale`, `window_animation_scale`), `violintuner_scene_seconds 12.5`, занятие не идёт.
   - Сборки: `./gradlew :app:installDebug -PfakePitch=true -PfakeScenario=X` для `IN_TUNE`, `DRIFT_SHARP`, `DRIFT_FLAT`, `SILENCE`, `NOISE`.
   - Места: комната и зал Вены (через «Играть здесь» на остановке; кремовый потолок нужен для сверки контраста).
   - Режимы: «Игра»; «Настройка» авто; «Настройка» с зафиксированной D. Нажатия — `uiautomator dump` и `input tap` по найденным границам «Настройка» и «D».
   - Размеры: портрет; landscape 892 × 412; 640 × 360 (`wm size 1280x720`, `wm density 320`, `user_rotation 1`); 360 × 640 (`wm size 720x1280`, `density 320`).
   - Ещё «Игра» с идущей записью на 360 × 640 и `-PplainLive=true` × `IN_TUNE`/`SILENCE` в портрете и landscape — для сверки ядра.
   - Скрипт — в scratchpad, не в репозитории.
4. **iOS «до»:** `xcrun simctl launch booted com.violinjourney.app.debug -openRoute live -fakeScenario {IN_TUNE, SILENCE, NOISE}`, `… -liveMode tuning`; нет разрешения — `xcrun simctl privacy booted revoke microphone com.violinjourney.app.debug`; снимок — `simctl io booted screenshot`.
5. **Замеры «до»** — раздел 5.

### 2.1 Код

- **Тема.**
  - `S/core/ui/theme/Color.kt:58-59`: `GlassCaption = Color(0xFFC9C5D6)` и `GlassEdge = Color(0x1FFFFFFF)`; в `AppTheme.kt:90-96` — `ViolinTheme.glassCaption` и `glassEdge`.
  - `LivePalette.kt`: `KeyRim = Color(0xFF9C8452)`.
  - `VenueColors.kt:14-39`: поля `keyRim`, `glassCaption`, новый KDoc («стекло и кость R6»).
  - В конце этапа, когда уйдут последние вызовы (`ModeSwitcher`, `StringRow`, `CentsScale`), удалить `CtrlEbony`, `CtrlEbonyEdge`, `CtrlMaple`, `CtrlMapleLit`, `CtrlMapleDark`, `CtrlRuler`, `CtrlNickel`, `CtrlCaption`, `CtrlMuted` и поля `ebony`, `ebonyEdge`, `maple`, `mapleLit`, `mapleDark`, `ruler`, `nickel`, `caption`, `muted`.
- **`S/core/ui/components/GlassPlate.kt:44-61`** — Р2; превью «с обводкой» и `ground` добавить в `A/core/ui/components/GlassSheetPreviews.kt`.
- **Новые файлы:**
  - `S/feature/live/components/LiveGlass.kt` — `LocalLivePlain` и `liveGlassGround()`.
  - `S/feature/live/LiveSwitches.kt` — `object LiveSwitches { const val STRING_SILHOUETTE = false }`.
- **`components/LiveDimens.kt`:**
  - новые: `TopRowHeight 48`, `SwitcherHeight 44`, `SwitcherCorner 22`, `SwitcherInset 4`, `SwitcherSliderHeight 36`, `SwitcherSegmentMin 106`, `SwitcherSegmentPadding 16`; `GearSize 40`; `StringButtonHeight 58`, `StringButtonCorner 16`, `StringButtonGap 10`, `StringRowSide 22`, `StringNearEdge 2`, `StringLock 14`, `StringLockTop 5`, `StringLockEnd 6`;
  - строка состояния: `StatusLineHeight 36`, `StatusPlateHeight 36`, `StatusPlateCorner 18`, `StatusPlatePadding 15`, `StatusDotReady 9`, `StatusDotBlocked 10`, `StatusLineGap 9`;
  - шкала: `ScaleTopGap 8`, `ScaleHeightLow 28`, `ScaleLine 2`, `ScalePillHeight 10`, `ScaleTickHeight 14`;
  - landscape: `LandscapePaddingVerticalLow 8`, `LandscapeSpacingLow 4`;
  - `SILHOUETTE_ALPHA = 0.28f`;
  - удалить: `SwitcherBorder`, `GearBorder`, `StringButtonWidth`, `StringButtonEdge`, `StringPegHead*`, `StringLockBadge*`, `ScaleRuler*`, `ScaleBottomPadding`.
- **`components/ModeSwitcher.kt`** — переписать строки 43–107:
  - внешний `Box` 48 (касание сегментов — вся высота), внутри капсула 44 `Modifier.glass(capsule, edge = true, ground = liveGlassGround())` с отступом 4;
  - бегунок 36/18 `venueColors.bone`, переезд `animateDpAsState` 200 мс;
  - слово 15 sp / 700: выбранное — `ink`, невыбранное — `glassCaption`;
  - ширина сегмента — Р18; `lerp`-«темнение планки» уходит: неактивный — только 0,4 через `chrome`.
- **`components/SettingsGear.kt:43-51`:** диск 40 на `Modifier.glass(CircleShape, edge = true, …)`, значок 20 `onSurface`; `RIM_ALPHA` (строка 24) уходит.
- **`components/StatusLine.kt`:**
  - строки 57–93: высота 36, плашка 36/18, поля 15, **без обводки**, текст `onSurface` вместо `onSurfaceVariant` (строка 85);
  - точки (строки 98–105): 9 заливкой / 10 кольцом с обводкой 2, зазор 9;
  - в `LiveTypography.kt` `statusLine` → 15 sp, `SemiBold`.
- **`components/StringRow.kt`** — переписать:
  - `StringRow(tuning, onStringClick, light: () -> Float, base: Float, modifier, topPadding)`;
  - `Row(fillMaxWidth, spacedBy 10)`, кнопки `weight(1f)` высотой 58, `glass(RoundedCornerShape(16), edge = true)`;
  - буква 20 sp / 800 (lineHeight 22), частота 11,5 sp / 600 табличными (lineHeight 14): при шрифте 1,5 это 54 dp — входит в 58;
  - ближайшая: `border(2, bone)` + подложка `bone.copy(alpha = .16f)`; зафиксированная: заливка `bone`, буква `ink`, частота `inkSoft`, замок — прежний глиф из `LockBadge` (строки 172–208) без кружка, 14 dp `ink`, `TopEnd` со сдвигом (−6, 5), прежний «хлопок» `LOCK_POP`;
  - приглушение — `Modifier.chrome(base, light)` на каждой кнопке; цель (при разрешении на микрофон) — `chrome(1f, Whole)`;
  - головки колков (строки 147–153) уходят.
- **`components/CentsScale.kt:83-98`:**
  - параметр `height: Dp = ScaleHeight`;
  - линия 2 dp `onSurface` 35 % во всю ширину, пилюля 10 `zoneColors.inTune` 55 %, засечка 2 × 14 `onSurface` 35 %; `wood.ruler` и `mapleDark` уходят;
  - бегунок кость 8 × 24 и ореол — прежние.
- **`LiveLayoutMath.kt`:**
  - `object PortraitRows { TOP = 60f; STRINGS = 70f; STATUS = 48f; KEYS = 100f; RECORDING = 58f; WORD = 68f; SCALE = 44f }`;
  - `ringBlockHeight(liveHeightDp, tuning, recording)`;
  - `reservedUnderRing(scaleShown: Float) = WORD + SCALE · scaleShown`;
  - `data class LandscapeColumn(padding, gap, scale)` и `landscapeColumn(heightDp)` — при высоте ≤ 360 это 8 / 4 / 28, иначе 16 / 8 / 36;
  - `landscapeWordRoom(heightDp, tuning)`;
  - `designRing` в этом этапе пока с `noMic`.
- **`LiveScreenLayout.kt`:**
  - строки 170–210: `CompositionLocalProvider(LocalLivePlain provides !showVenue)`; новый параметр `stringSilhouette: Boolean = LiveSwitches.STRING_SILHOUETTE`; `landscapeColumn(maxHeight.value)` передаётся в `LandscapeLayout`;
  - строки 301–317: у ряда `.height(TopRowHeight)`;
  - строки 318–330: `StringRow(…, light = chrome, base = chromeAlpha)` с полями 22, `chrome` с ряда снят;
  - строки 339–374: при разрешении на микрофон `Column(spacedBy 20/10) { Ring; Column(fillMaxWidth) { StatusRow; ScaleSlot(top 8, бока 24) } }`, запас = `reservedUnderRing(scaleShown)`;
  - строки 375–384: нижний `ScaleSlot` остаётся только без разрешения — у низа места, 0,3, как сейчас;
  - строки 500–512 и 534–541 (landscape): поля и зазоры из `LandscapeColumn`, `StringRow(topPadding = gap)` без `StringPegHeadRise`; строка 566 — `ScaleSlot(height = column.scale)`;
  - строки 622–651 и 689–724: `Ring(…, silhouette: Note?)`, где силуэт = `stringSilhouette && mode == TUNING && signal == Silence && tuning.lockedString != null`, и новый вид `SILHOUETTE`.
- **`LiveScreen.kt`:** параметр `silhouette` передаётся в `LiveScreenLayout`.
- **Строки** (`tuning_hint_locked` × 10):

| язык | текст |
|---|---|
| ru | `%1$s · %2$d Гц · тап — снять` |
| en | `%1$s · %2$d Hz · tap to unlock` |
| de | `%1$s · %2$d Hz · tippen – lösen` |
| fr | `%1$s · %2$d Hz · touchez pour libérer` |
| es | `%1$s · %2$d Hz · toca para soltar` |
| it | `%1$s · %2$d Hz · tocca per sbloccare` |
| pt | `%1$s · %2$d Hz · toque para soltar` |
| ja | `%1$s · %2$d Hz · タップで解除` |
| ko | `%1$s · %2$d Hz · 탭하면 해제` |
| zh | `%1$s · %2$d Hz · 轻触解除` |

### 2.2 Тесты

- **`CT/feature/live/LiveLayoutMathTest.kt`:**
  - `reservedUnderRing(0f) = 68`, `(1f) = 112`;
  - перенос шкалы при той же высоте места кольцо не меняет: `ringDiameter(260, w, box, 68) == ringDiameter(260, w, box + 44, reservedUnderRing(1f))` для нескольких `box`;
  - 412, высота Live 788, «Настройка» → 260;
  - 360, высота Live 506: «Настройка» 118 → 116 (−2), «Игра» 238 → 230 (−8); старые ряды 60 + 76 + 40 + 100 и шкала 44 снаружи — числами в тесте с комментарием;
  - `landscapeColumn(412)` = 16/8/36, `(336)` = 8/4/28, `(360)` — низкое окно;
  - `landscapeWordRoom(336, tuning = true)` в пределах 46…50 (выходит 50).
- **`CT/core/ui/theme/PaletteContrastTest.kt`:**
  - `GlassCaption` на `Glass` поверх `#131318` ≥ 6,5; поверх белого < 4,5 — документируется, это повод для снимка Вены;
  - `OnSurface` на `Glass` поверх белого ≥ 4,5;
  - `KeyRim` темнее `CtrlBrass`.
- **`IT/ios/LaunchArgumentsTest.kt`:** `liveModeOf`: `tuning` / `TUNING` → `TUNING`; не `.debug` → null; мусор → null.
- **`T/feature/live/LiveViewModelTest.kt`:** «открывается в заказанном режиме»: `mode == TUNING`, `canRecord == false`.
- Остаются зелёными: `LiveReducerTest`, `LiveChromeTest`, `TabBarLightTest`, `LocalizationTest`. `VenueLookTest` не трогается.

### 2.3 Превью

- **Новый `A/feature/live/components/LiveControlsPreviews.kt`:**
  - переключатель: «Игра», «Настройка», во время записи (0,4), 320 × шрифт 1,5;
  - плашка: можно, нельзя, зафиксирована D, без плашки (`plain`);
  - ряд струн: авто без цели, ближайшая A звучит, D в тишине, приглушённый светом `{ 0.38f }` (цель целая), без разрешения;
  - шкала: тишина, в строе, без разрешения, 28.
- **`A/feature/live/LiveScreenPreviews.kt`:** у хелпера `LivePreview` появляются `silhouette` и `showVenue`; новые превью — «Tuning · D locked · silhouette ON» (рядом с существующим «12e1»), «Plain build» портрет и landscape; проверить существующие «Landscape · Tuning» 892 × 412 и «Landscape · low 640 x 336».

### 2.4 Проверка

- Команды: `./gradlew :app:assembleDebug :app:compileDebugAndroidTestKotlin :app:testDebugUnitTest :shared:testAndroidHostTest :shared:iosSimulatorArm64Test :app:lintDebug`.
- Эмулятор: «Настройка» во всех четырёх размерах. На 640 × 360 у слова около 50 dp и переключатель не прыгает при смене режима.
- `snap.py compare-region` по ядру в `-PplainLive` (`IN_TUNE`, `DRIFT_*`) — 0 % при найденном сдвиге.
- iOS: `-openRoute live -fakeScenario IN_TUNE -liveMode tuning`.

## 3. Этап 115 — нижний ряд, запись, «нет разрешения», панель

### 3.1 Код

- **`LiveSwitches.kt`:** `DIM_TAB_BAR = false` — рядом с силуэтом.
- **`LiveLayoutMath.kt`:**
  - удалить `RING_NO_MIC` и параметр `noMic` (строки 11, 19–24);
  - `keyCardWidth(w) = min(150, (w − 2·10 − 76 − 2·8) / 2)`;
  - `keyCardShowsIcon(w) = w >= 120`;
  - `promptTop(H, h) = max(0, min(0.6·H − h/2, H − h))`;
  - `promptFit(available, fixed, icon, text): PromptFit(showIcon, textHeight)`.
- **Новые файлы в `components/`:**
  - `SoftShadow.kt` — Р3;
  - `LiveCardWords.kt` — Р6;
  - `LiveCard.kt`: `LiveCard(paper: Boolean, width: Dp, description, onClick, enabled, reduceMotion, rim: () -> Float, progress: (() -> Float)?, lead, first, second, firstEllipsis)`.
- **Числа `LiveCard`:**
  - 60/18, поля 10, зазор 7, значок 20 (прячется уже 120);
  - стекло — `glass(edge)`; бумага — `bone` с тенью (чёрный 0,35, сдвиг 4, размытие 10, альфа = доля бумаги); смена — кроссфейд 300;
  - первая строка 13,5 sp / 700, вторая 12,5 sp / 600; на стекле `onSurface` / `glassCaption`, на бумаге `ink` / `inkSoft`;
  - ход: 3, скругление 2, `brass` по `boneShade`, 12 от боков, 7 от низа;
  - обод «готово»: 2,5 `brass` снаружи бумаги;
  - галочка «готово» — свой Canvas по `IconPaths.CHECK`, 20, обводка 3, `ink`.
- **`block/BlockBookmark.kt`** — переписать на `LiveCard`:
  - удаляются `EntryBookmark` (99–134), `shadow` (230–233), `bookmarkPath` (235–251);
  - «Что играю» / «выбрать» со значком `AppIcons.TabRepertoire.normal`;
  - «готово»: ход 240, обод и текст 400 с задержкой 80; `lastFilled` и `lastLeft` сохраняются.
- **`components/PracticeTag.kt`** — на `LiveCard`:
  - удаляются `tagWidth` (139–157) и `tagPath` (169–184);
  - без занятия — стекло, `Timer`, «Начать» / «занятие»;
  - занятие идёт — бумага, точка 9 `velvet`, время табличными / «занятие»;
  - `enabled` — как сейчас.
- **Новый `components/LiveRecordKey.kt`:**
  - 76, радиальный блик `lerp(bone, White, .6)` → `bone` на 0,55 → `boneShade`, центр (50 %, 38 %), в `drawWithCache`;
  - обод 3 `keyRim` внутри; тень — Р3 (0,55, сдвиг 7, размытие 14), места не берёт;
  - точка 28 `velvet`; при записи — `recording` / `recordingRim`, «стоп» 24 / 6 белым;
  - морф 200 мс, ход нажатия 3 / 90 мс, TalkBack `record_start` / `record_stop`;
  - `RecordButton.kt` — по Р5.
- **`components/RecordingStrip.kt:58-111`:**
  - капсула 50/25 на `glass(edge)`, поля 16, зазор 10;
  - точка 11 + ореол 4 `recording` 25 % в пульсирующем слое;
  - «запись» и время 15 sp / 700 `onSurface`;
  - лента высотой 8, `drawRoundRect` со скруглением 2, промежуток 2, без дорожки (Р17).
- **`components/MicPermissionPrompt.kt`** — переписать, `MicGlyph` (30–46) удалить:
  - `bone`, скругление 24, поля 22 / 20 / 18, тень 0,5 / 18 / 40 (Р3);
  - плашка 48/16 `boneShade` со значком `Mic` 24 `ink`, 12 до заголовка;
  - заголовок `promptTitle`, текст `promptBody` `inkSoft`, 6 под заголовком;
  - кнопка 54, капсула `ink`: `Mic` 18 и слово 16 sp / 800 цвета `bone`, зазор 8, 18 сверху;
  - раскладка — свой `Layout` по `promptFit`; текст — `verticalScroll`;
  - в `LiveTypography.kt` `promptTitle` → 22 sp `ExtraBold`, −0,01 em; `promptBody` → 15 sp, lineHeight 21,75.
- **`LiveScreenLayout.kt`:**
  - слоты (84–99): новый `light: @Composable (landscape, chrome) -> Unit`, вызов внутри `BoxWithConstraints` (170–210);
  - `KeyRow` (409–440): `BoxWithConstraints` → `keyCardWidth(maxWidth)`; `Row(padding(horizontal = 10), spacedBy(8, CenterHorizontally))` — карточка, клавиша, карточка; `BookmarkWidthLandscape` (571) и `maxBookmark` уходят;
  - портрет без разрешения (345–374): кольца нет; `PromptPlace` — `Layout` с верхом из `promptTop`, поля 26, не шире 360, `ringModifier` на карточке; в «Настройке» — место над шкалой (8 над ней, 0,3);
  - landscape без разрешения (488–499): карточка по центру левой панели, не шире 340, с `ringModifier`; в правой колонке строки 551–553 — пусто, место держится;
  - `RecordingStripSlot` (385–394): бока 22;
  - удалить `ringSizeFor(noMic)` (607–620), `RingContentKind.NO_MIC` (689, 705, 720), импорт `MicGlyph` (65).
- **`LiveScreen.kt`:**
  - слоты 76–111: `BlockBookmark(width точно)`, `PracticeTag(width)`, `LiveRecordKey`;
  - `light = { l, c -> if (dimTabBar) LendTabBarLight(!l, c) }`;
  - приватный `LendTabBarLight`: `LocalTabBarLight`, `remember { Any() }`, `LifecycleResumeEffect(bar, portrait, light) { if (portrait) bar.lend(owner, light); onPauseOrDispose { bar.takeBack(owner) } }`;
  - параметр `dimTabBar = LiveSwitches.DIM_TAB_BAR`.
- **Комментарии:** KDoc `TabBarLight.kt:9-13`, `A/MainActivity.kt:124`, `I/ios/IosApp.kt:91` («Live одалживает за `DIM_TAB_BAR`»).
- **Строки:**
  - изменить: `block_entry`, `recording_strip_label`, `mic_permission_title`;
  - новые — после `block_entry_description` и `practice_chip_label`: `block_entry_hint`, `practice_tag_start`;
  - условно: `recap_source_repertoire` и одна правка `S/feature/practice/components/RecapSheet.kt:232` (`block_entry` → `recap_source_repertoire`). Это `feature/practice/**`: делать только если этап 107 не сделан, и после коммита 104/105.
  - строка в `docs/i18n-glossary.md`: `| Что играю | the entry on Live that opens «Что играем»: what I play now | What I play | Was ich spiele | Ce que je joue | Qué toco | Cosa suono | O que toco | 연주할 곡 | 演奏曲目 | 弾く曲 |`.

| ключ | ru | en | de | fr | es | it | pt | ja | ko | zh |
|---|---|---|---|---|---|---|---|---|---|---|
| block_entry | Что играю | What I play | Was ich spiele | Ce que je joue | Qué toco | Cosa suono | O que toco | 弾く曲 | 연주할 곡 | 演奏曲目 |
| block_entry_hint | выбрать | choose | wählen | choisir | elegir | scegli | escolher | 選ぶ | 고르기 | 选择 |
| practice_tag_start | Начать | Start | Starten | Commencer | Empezar | Inizia | Começar | 開始 | 시작 | 开始 |
| recording_strip_label | запись | rec | Aufnahme | enreg. | grabando | rec | gravação | 録音 | 녹음 | 录音 |
| mic_permission_title | Дайте мне услышать скрипку | Let me hear your violin | Lassen Sie mich die Geige hören | Laissez-moi entendre le violon | Déjame oír tu violín | Fammi sentire il violino | Deixe-me ouvir o violino | ヴァイオリンの音を聴かせてください | 바이올린 소리를 들려주세요 | 让我听听你的小提琴 |
| recap_source_repertoire | Репертуар | Repertoire | Repertoire | Répertoire | Repertorio | Repertorio | Repertório | レパートリー | 레퍼토리 | 曲库 |

### 3.2 Тесты

- **`LiveLayoutMathTest`:**
  - «design ring per state» — без `noMic` (правка теста вместе с 3.4 через 3.36.6, не «тест под код»);
  - карточки: 412 → 150, 360 → 124, 320 → 104, 460 → 150, 321 → 104,5;
  - значок: 124 → да, 119,9 → нет;
  - `promptTop`: (400, 200) → 140; (300, 250) → 50; (200, 260) → 0;
  - `promptFit`: сначала уходит значок, потом режется текст.
- **Новый `CT/feature/live/components/LiveCardWordsTest.kt`:** шаги 13,5 → 12; перенос, когда не входит и 12.
- **`PaletteContrastTest`:** `CtrlInk` / `CtrlInkSoft` на `CtrlBone` ≥ 12 и ≥ 4,5.
- Зелёные: `TabBarLightTest`, `LiveReducerTest`, `LiveChromeTest`, `LocalizationTest`.

### 3.3 Превью (`LiveScreenPreviews.kt`)

- Тишина, в строе, выше, ниже.
- Подход идёт, «готово», «готово» при звуке.
- «Слишком шумно», «Микрофон недоступен» (клавиша 0,4).
- Нет разрешения: портрет, landscape, «Настройка», 640 × 336 (плашка значка ушла).
- Запись и пауза в записи (плашка «Играйте…» приглушена).
- de / fr / pt / es на 360 × 576 и 320 × 500 (`locale = …`).
- Шрифт 1,5.
- «Live + панель, приглушение включено»: `Column { LiveScreen(dimTabBar = true); AppBottomBar(dimmed = light::alpha) }` под `LocalTabBarLight`.

### 3.4 Проверка

- Эмулятор:
  - запись на 360 × 640 — кольцо плавно теряет около 58 dp;
  - нет разрешения: `adb shell pm revoke com.violinjourney.app.debug android.permission.RECORD_AUDIO`, плюс флаг `user-fixed` → кнопка открывает настройки;
  - `DIM_TAB_BAR = true` — только в локальной сборке, в коммит не идёт (проверить `git diff`): панель гаснет со светом, фон панели нет, касания проходят, уход с Live — сразу 1, поворот — 1.
- TalkBack-описания — через `uiautomator dump`.
- iOS: `privacy revoke microphone` → карточка.

## 4. Этап 116 — лист «Что играем» и проверка R6

### 4.1 Код

- **`block/BlockContract.kt:56-70`:** `Picker` получает `practiceMs: Long`, `goalMinMinutes: Int`, `goalMaxMinutes: Int`.
- **`block/BlockReducer.kt:116-125`:** `practiceMs = running.elapsedMs(nowEpochMs)` (тот же `now`, что у хода); границы — из `PracticeConfig.blockGoalMin/MaxMinutes`.
- **Новый `block/PickerList.kt`** (публичный, без импорта `feature.live.components`):
  - `LazyListScope.pickerSections(sections, isSelected, onPick, pickable = { it.today != Running }, trailing = { TodayPill(it.today) })`;
  - `PickerSectionHead` — `SectionLabel` и «сегодня N» 13 sp / 600 `onSurfaceVariant`, 16 сверху, 4 снизу, `clearAndSetSemantics { heading(); contentDescription = "Гаммы, сегодня 1" }`;
  - `PickerRow` — ≥ 56, поля 8/12, скругление 14; название 16 sp / 700, композитор 13 sp; выбранная — `accentSoft` + внутренняя обводка 1,5 `primary`; `selectable(role = RadioButton)`, описание из частей через «, »;
  - `TodayPill` — 28, капсула, 13 sp / 700 табличными; сыгран — `surfaceContainerHigh` и галочка 15 `ViolinTheme.done` (или `venueColors.brass`); играли — `onSurfaceVariant`; идёт — рамка 1,5 `onSurfaceVariant` (было `primary`).
- **Новый `block/GoalRows.kt`:** `of(widthDp)`: при ширине ≥ 372 — 7 в ряд, ячейка (W − 36) / 7; иначе 4 в ряд, (W − 18) / 4, второй ряд из трёх той же ширины, по началу строки.
- **`block/BlockSheet.kt`:**
  - `BlockSheetHost` (105–139) → `AppSheet(sheet, onHide = SheetDismissed, slideAway = !reduceMotion, scroll = false, contentPadding = 0)`, внутри прежний `AnimatedContent` и `lastPicker`; новый параметр `calm: () -> Boolean`; `Dp.Unspecified` в landscape уходит;
  - шапка (257–265): «Что играем» 22 sp / 800 с `heading()` и справа «занятие 24:18» — `practice_chip_label` + `Formats.timer`, 13 sp табличными, по базовой линии;
  - `NowCard` (267–304): фон `surface`, `AppShapes.M`, поля 12/14; метка `block_now_label` 13 sp / 600; строка «название · ещё N мин» 15 sp / 700; `AppButton(block_stop, style = Outline, compact = true)`; описание карточки — `block_now` + « · » + остаток;
  - `pickerItems` (306–417) → `PickerList`;
  - `EmptyRepertoire` (422–446): текст по центру, внизу закреплена `AppButton(block_open_repertoire, Main)`;
  - `GoalPanel`, `GoalCells`, `GoalCellView` (448–543):
    - фон `surfaceContainer`, сверху черта 1 `outlineVariant`, поля 12/20;
    - ряд «Сколько играть» / «от 5 до 60 мин» 13 sp / 600, 8 до чипов;
    - `AppChip.Choice(inSheet = true)`: −5 / +5 с `selected = null`, быстрые — `selected`; описания — прежние `practice_step_*` и `block_goal_option`;
    - `Modifier.width(cell)`, по горизонтали `spacedBy(6)`, между рядами `spacedBy(2)` (Р13);
  - `StartButton` (545–560) → `AppButton(block_start, Main)`, 12 сверху;
  - `OfferContent` (141–186): `StartPracticeButton(onClick, calm, fillMaxWidth().height(56))` и `AppButton(block_offer_later, Quiet)`;
  - landscape: колонка 300 — «Выбрано», название, «Сколько играть · от 5 до 60 мин», чипы по 4, «Начать» у низа;
  - константы 562–573 чистятся.
- **`LiveScreen.kt:112`:** `BlockSheetHost(…, calm = { sounding || recording })` через `rememberUpdatedState`.
- **Строки:** изменить `block_section_today`; новые — `block_now_label`, `block_goal_title`, `block_goal_range`.

| ключ | ru | en | de | fr | es | it | pt | ja | ko | zh |
|---|---|---|---|---|---|---|---|---|---|---|
| block_section_today | сегодня %1$d | today %1$d | heute %1$d | aujourd\'hui %1$d | hoy %1$d | oggi %1$d | hoje %1$d | 今日 %1$d | 오늘 %1$d | 今天 %1$d |
| block_now_label | Сейчас | Now | Jetzt | En cours | Ahora | Ora | Agora | 今 | 지금 | 现在 |
| block_goal_title | Сколько играть | How long to play | Wie lange spielen | Combien de temps | Cuánto tocar | Quanto suonare | Quanto tocar | 弾く時間 | 연주 시간 | 演奏多久 |
| block_goal_range | от %1$d до %2$d мин | %1$d to %2$d min | %1$d bis %2$d Min. | de %1$d à %2$d min | de %1$d a %2$d min | da %1$d a %2$d min | de %1$d a %2$d min | %1$d〜%2$d分 | %1$d~%2$d분 | %1$d 至 %2$d 分钟 |

### 4.2 Тесты

- **`CT/feature/live/block/BlockReducerTest.kt`:** «время занятия в шапке — от того же now, что ход» (`at(34)` → 34 мин); границы цели из конфига.
- **Новый `CT/feature/live/block/GoalRowsTest.kt`:** 372 → 7 × 48; 371 → 4; 320 → 4 × 75,5; колонка landscape → 4.
- **`T/feature/live/block/BlockViewModelTest.kt`** — зелёный (эффект `OpenRepertoire`).
- `LocalizationTest`; `:app:compileDebugAndroidTestKotlin` — `HeldSheetTest` и `AppSheet` компилируются.

### 4.3 Превью

`A/feature/live/block/BlockSheetPreviews.kt` на `AppSheetCard`; помощник `picker()` получает новые поля. Состояния: выбор и цель, подход идёт, «Сначала — занятие», пустой репертуар, landscape 892 × 412 (не шире 640), 360 — два ряда целей, fr на 360 × шрифт 1,3.

### 4.4 Проверка R6 целиком

- Снимки «после» тем же скриптом в `…/r6/after`, сверка с «до» по ядру — `compare-region` — и глазами; плюс шрифт 1,3, de и fr.
- Лист: портрет, landscape 892 и 640, 360; «Открыть репертуар» — во вкладку, в том числе при идущей записи: дубль сохранился.
- Контраст над кремовым потолком Вены: `adb exec-out screencap`, область плашки и строки струн, самый светлый пиксель подложки против `#C9C5D6` и `#E6E4EE` (скрипт в scratchpad). Ниже 4,5 : 1 — новое решение в `decisions.md`, без тихого перехода на 0,82.
- Эмулятор 360 × 640 и 640 × 360; симулятор iOS: `IN_TUNE`, `-liveMode tuning`, `privacy revoke`.
- Документы:
  - `docs/notes/redesign.md`: «Этап 114–116», отклонения Р1–Р18, «Проверить» — вопросы 12–14 `open-questions.md` только на телефоне владельца (`adb install -r` и только с его согласия);
  - `05-live.md` догоняет спеку и код: убрать правку `mic_permission_text` и `infoplist`, строки 56, «Остановить» 48;
  - `CLAUDE.md`, раздел «Экран Live»: строка про `LiveSwitches`.

## 5. Производительность

- **«До»** (этап 114, после Р1 и до UI) и **«после»** (этап 116).
- **Android:** `python3 tools/perf/measure.py r6-<до|после>-<сценарий> 20` в комнате и в зале для `SILENCE` (свет горит, картина 15 кадров/с), `IN_TUNE` (темно), `VIBRATO`, «Настройка» + `IN_TUNE` (три слоя `chrome` на струнах) и без разрешения (тень карточки); DEMO — 24 с. Анимации 1, `scene_seconds` сбросить. Сравнивать время записи кадра в главном потоке и CPU — кадрам программного рендера не верить (`docs/plan-performance.md`).
- **iOS, симулятор Profile** (`com.violinjourney.app.debug`, release-фреймворк): CPU процесса за 20 с после 10 с прогрева (`ps -o time= -p <pid>`) для `SILENCE`, `IN_TUNE`, `VIBRATO`, `-liveMode tuning -fakeScenario IN_TUNE` и без разрешения.
- **Порог:** рост больше 10 %.
  - Тени уже запечены (Р3).
  - Если растёт «Настройка» — струны гасят цвет в фазе рисования (альфа в цвета фона и текста через `ColorProducer`) вместо `graphicsLayer`: на iOS альфа меньше 1 даёт `saveLayer` на кадр.
  - Если растут карточки — альфа их стекла в `drawBehind`.
- Картина (`VenueBackdrop`, `KeptPicture`, `VenueLook`) не трогается.

## 6. Риски

1. **`GlassCaption` над белым ≈ 4,4 : 1.** Вена может не пройти → решение владельца (`decisions.md`).
2. **Переход кольца при смене режима на малом экране** — Р7 (анимированный запас). Проверить 360 × 640 глазами.
3. **`AppSheet` и `SizeTransform`** («Сначала — занятие» → выбор растёт внутри листа). При `scroll = false` проверить на эмуляторе и в превью.
4. **`RecapSheet.kt` лежит в `feature/practice`.** Правка условная и только после 104/105; если 107 уже сделан — не трогать.
5. **Длинные языки на 320 × 1,5:** переключатель (Р18), «Was ich spiele», «Commencer». Проверять превью и эмулятором.
6. **`LifecycleResumeEffect` и кроссфейд навигации** — возврат света проверить глазами (выключатель включён только локально).
7. **Три слоя `chrome` на струнах на iOS** — раздел 5.
8. **R4 может не удалить `RecordTakeRow`** — тогда `RecordButton` и его мерки в `LiveDimens` остаются (Р5).
9. **Снимки «до» и «после» — в один день и на одних данных** (Вена открыта на эмуляторе); иначе сверка ядра по областям врёт.

### Critical Files for Implementation
- /Users/danil/AndroidStudioProjects/ViolinTuner/shared/src/commonMain/kotlin/com/violinjourney/app/feature/live/LiveScreenLayout.kt
- /Users/danil/AndroidStudioProjects/ViolinTuner/shared/src/commonMain/kotlin/com/violinjourney/app/feature/live/LiveLayoutMath.kt
- /Users/danil/AndroidStudioProjects/ViolinTuner/shared/src/commonMain/kotlin/com/violinjourney/app/feature/live/LiveScreen.kt
- /Users/danil/AndroidStudioProjects/ViolinTuner/shared/src/commonMain/kotlin/com/violinjourney/app/feature/live/block/BlockSheet.kt
- /Users/danil/AndroidStudioProjects/ViolinTuner/shared/src/commonMain/kotlin/com/violinjourney/app/feature/live/components/LiveDimens.kt