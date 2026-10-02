# R8 «Знакомство, онбординг, „Настройки“, копия» (3.36.8, этапы 120–122): план

Сокращения путей:
- **S** = `/Users/danil/AndroidStudioProjects/ViolinTuner/shared/src/commonMain/kotlin/com/violinjourney/app`
- **SI** = `…/shared/src/iosMain/kotlin/com/violinjourney/app`
- **CT** = `…/shared/src/commonTest/kotlin/com/violinjourney/app`
- **A** = `…/app/src/main/java/com/violinjourney/app`
- **T** = `…/app/src/test/java/com/violinjourney/app`
- **AT** = `…/app/src/androidTest/java/com/violinjourney/app`
- **RES** = `…/app/src/main/res/values{,-ru,-de,-es,-fr,-it,-ja,-ko,-pt,-zh}/strings.xml`

Где `07-start.md` спорит с 3.36.8, прав 3.36.8. Главные такие места:
- `SegmentedSwitch` в R8 меняется.
- Полоска допуска уходит под подпись, а не прячется.
- Подпись строки «Данных» не обрезается.
- Пока состав считается, нижняя зона пуста.
- Причина «нельзя» стоит над кнопкой.

## 0. Что должно быть до начала и какой API от R4/R5 я жду

1. **Коммиты.** R2 (103) закоммичен. R4 и R5 закоммичены до этапа 120: общие файлы — значки, `strings.xml` ×10, `ListRow`, `SegmentedSwitch`, `AccessibilitySemanticsTest`, `ControlsPreviews`.
2. **API R1, которым R8 пользуется как есть:**
   - `AppDock(dock, modifier, fade, aboveKeyboard, padSides, metrics)`, `DockScope.compact` и `buttonHeight`, `LocalDockInset`, `currentDockMetrics().copy(side = 16.dp)`;
   - `AppButton(text, onClick, modifier, style, icon, caption, enabled, reason, compact)` со стилями `Main / Outline / Text / Danger / DangerFilled`;
   - `ListRow`, `ListGroup`, `LocalListGroupGround`, `SectionLabel`, `AppDialog` (через `BackupParts.ConfirmDialog`).
3. **Что жду от R4 и R5 (если имя в коде другое — беру то, что в коде, второе не завожу):**
   - `SegmentedSwitch(containerColor: Color = surfaceContainer, …)` — R4 D3. R5 D9 называет его `container`: в коде будет одно имя.
   - `SegmentedSwitch(descriptions: List<String>? = null)` — R5.
   - `SegmentedSwitch(strong: Boolean = false)` — R5, насыщенность 800.
   - `AppSwitchMark` — R4, переключатель 52×32 цветов строки.
   - `ListRow(strong: Boolean = false)` — R4, 700.
   - `ReasonLine` (R4) R8 не зовёт: причины у R8 — через `AppButton(reason = …)`.
   - Чего нет в коде к началу R8, R8 добавляет сам, с тем же именем и прежним умолчанием.
4. **Что добавляет R8 в общих частях** (все параметры необязательные, умолчание даёт прежний вид):
   - `SegmentedSwitch(sublabels: List<String>? = null)` — вторая строка подписи. Только при ней слова всех сегментов берут один размер, 14 → 12 sp шагом 0,5 (новая `SegmentLabelSize`), а ниже 12 переносятся. В ветке `strong = true` добавляется `fontFeatureSettings = "tnum"`: у A/B R5 цифр нет, их вид не меняется.
   - `ListRowEnd.Value(text, chevron: Boolean = false)` — значение и шеврон `textTertiary` вместе.
   - `ListRow(below: (@Composable () -> Unit)? = null)` — содержимое под подписью; не приглушается.
   - Новые файлы `S/core/ui/components/ScreenHeader.kt`, `SegmentLabelSize.kt`, `ToleranceBarMath.kt`; значок `AppIcons.Chart`.
5. **Спека уже на месте** (0.86). Отклонения по ходу — в `docs/notes/redesign.md`. Коммиты:
   - `feat(onboarding|settings|backup): … (stage 12x, spec 3.36.8)` и `docs: stage 12x …`;
   - пути указывать явно: в индексе лежат `.idea/*`.

## 1. Мои решения

- **D1. Полоса хода.**
  - Живая полоса стоит вне `HorizontalPager`. В портрете — поверх пейджера. Её отступ сверху ставит `Modifier.offset { IntOffset(0, (artHeightAt(position(), artHeights) + 16.dp.toPx()).roundToInt()) }` (`OnboardingIntro.kt:178`): значение читается в фазе раскладки, при листании ничего не пересобирается.
  - Место под полосу на каждой странице держит её невидимый двойник `OnboardingProgress(…, placeholder = true)`: альфа 0, `clearAndSetSemantics {}`, без анимации. Высота двойника совпадает с живой полосой и при переносе подписи (de, крупный шрифт), поэтому первый кадр не прыгает.
- **D2. Landscape.**
  - Над правой колонкой один ряд `LandscapeTopRow`: `Row { OnboardingProgress(weight 1) ; skip }`. «Пропустить» переезжает в этот ряд, его прежние `graphicsLayer`-альфа и семантика сохраняются.
  - В настройке тот же ряд, но с невидимым двойником `SkipButton`. Так длина полосы одна на все семь экранов.
- **D3. Заливка отрезков.**
  - `animateColorAsState` за `BAR_FILL_MS = 200`.
  - Скачок больше чем на один экран («Пропустить») — за `2 × SKIP_HALF_MS = 450`.
  - При `LocalReduceMotion` — сразу.
  - Цвета: пройденный — `primary.copy(.45f).compositeOver(surfaceContainerHigh)`; текущий — `primary`; впереди — `surfaceContainerHigh`.
- **D4. Высота картинки настройки.**
  - Чистая функция `SetupArtHeight.of(step, windowHeight, compact)`: 220 / 200 / 150; ниже 760 dp или на узком экране — не выше 200; ниже 520 dp — 0.
  - Анимация: `Animatable` + `Modifier.layout`, то есть без пересборки по кадрам, 300 мс вместе с `AnimatedContent` картинки.
- **D5. Два вида контролов.**
  - `A4Selector` и `TolerancePresetList` получают `look: ChoiceLook = ChoiceLook.Settings` (`enum class ChoiceLook { Settings, Onboarding }`).
  - В 120 ветка `Settings` остаётся прежним видом. В 121 она становится `SegmentedSwitch`, прежний вид удаляется.
- **D6. Полоска допуска.** Одно решение «рядом или под подписью» на все три карточки: по самому широкому названию, чтобы полоски не стояли в разных местах. Функция `ToleranceBarMath.beside(...)`.
- **D7. «Гц» под числом** — `Formats.language.sound.hz`: слово уже есть для всех языков форматов. Новой строки нет.
- **D8. Подсказки** — две строки, «lead» жирным и «text», склеиваются через пробел (как `IntroRow`).
- **D9. Метки групп «Настроек»** рисует `SettingsScreen`.
  - Новый stateless `DataGroup` рисует только `ListGroup`.
  - `DataBlock` (с ViewModel) лишь зовёт его; сигнатура `DataBlock` и слот `dataBlock` прежние, поэтому `AppNavHost` и `IosNavHost` не меняются.
- **D10. Фазы.**
  - `DataBlockState.running: DataRunning?` вместо `runningPercent` + `restoring`; фаза — `JobPhase`.
  - Всё выводится из одного чистого `BackupPhases`: строка «Данных» и строка фаз экрана копии.
- **D11. Состав копии в самой работе.**
  - `BackupJob.Saving(parts, filled)` и `SaveFailed(parts)`.
  - `filled` — части, у которых есть файлы. Его ставит `moveOn` сразу после `store.prepare`.
  - Строка фаз строится из самой работы, а не из `contents`: на экране, открытом заново, она не мигает.
- **D12. Выбор на экране копии.** `BackupViewModel` при каждом `Saving` или `SaveFailed` берёт из них `parts`. Переключатели в это время и так закрыты: `PartToggled` работает только при `Idle`.
- **D13. `BackupFacts` — чистые правила (commonTest):**
  - чипы «Копия сохранена», паспорта и «Сейчас в приложении»;
  - отбор нулей для `countsLine` и для «Заменить данные?»;
  - вес «Без видео…»;
  - приблизительность итога: меньше 1 МБ — без «≈».
  - `backup_failed_without_video` = «Без видео копия займёт %1$s.» — туда подставляется то же слово итога, поэтому «≈ меньше 1 МБ» не бывает.
- **D14. Стили кнопок.**
  - «Закрыть» — `AppButtonStyle.Text`.
  - «Удалить текущие данные и восстановить» и «Начать с чистого приложения» — `AppButtonStyle.Danger`, с корзиной по правилу 3.16.
  - «Отправить…» оставляет значок `Share`.
  - Во время короткой копии «Отправить…» не убирается, а приглушается: зона не прыгает.
- **D15. Нижняя зона копии.**
  - Пока состав считается — в зоне `Spacer(buttonHeight)`.
  - Карточка последствия появляется без анимации: у экрана нет своего движения (5.29).
- **D16. Строка части копии** — своя `PartRow` в `ListGroup`: `ListRow` не умеет «до двух строк» и «всегда» 13/700 `textTertiary`. Переключатель — `AppSwitchMark` (R4).
- **D17. Токены.** Удаляются `BackupOff`, `BackupColors.off`, `OnboardingDotIdle`, `ViolinTheme.onboardingDotIdle`: после R8 их никто не зовёт.
- **D18. Шапка.** `ScreenHeader(title: String?, onBack: (() -> Unit)?, close: Boolean = false)`:
  - 56, в landscape (окно шире, чем выше) — 48;
  - «назад» или ✕ — 48; название 20 sp / 800, одна строка с многоточием, `heading()`;
  - без кнопки название начинается с 16.

---

## 2. Этап 120 — знакомство и настройка

### 2.0 Первым делом, до правки кода: снимки «до» и замер

Снимки «до», эмулятор `emulator-5554`, сборка текущей HEAD:
1. Подготовка:
   - `./gradlew :app:assembleDebug`, `adb install -r`;
   - `cmd locale set-app-locales com.violinjourney.app.debug --locales ru-RU`;
   - все три `*_animation_scale 0`.
2. Семь экранов открываются через «Настройки» → «Пройти онбординг снова» (данные при этом целы).
3. **Landscape 640 × 360:** `wm size 945x1680` и `user_rotation 1`. Файлы `start_land640_{intro1,intro2,intro4,setup_mic,setup_a4,setup_tol,settings,backup,restore}.jpg`.
4. **Крупный шрифт 1,3:** `settings put system font_scale 1.3`. Файлы `start_font13_{intro1,intro2,intro4,setup_mic,setup_a4,setup_tol,settings_top,settings_bottom,backup,restore}.jpg`.
5. **Первый запуск и восстановление в пустое приложение — только эмулятор:**
   - сначала новая копия «Сохранить в…» → «Загрузки»;
   - затем `pm clear com.violinjourney.app.debug`;
   - снять `start_firstrun_intro1.jpg`;
   - «У меня есть копия данных» → свежая копия → `start_restore_empty.jpg`, `start_restore_progress.jpg`, `start_restore_done.jpg`;
   - данные вернутся этим же восстановлением.
6. **По возможности:**
   - копия больше 64 МБ → `start_backup_progress.jpg` и `start_settings_running.jpg`;
   - `run-as … toybox fallocate -l <байты> files/filler` → `start_restore_noroom.jpg`, «Отправить…» → `start_backup_failed.jpg`; затем `rm files/filler`.
   - Не вышло — в заметках: «было» у них — код и 3.20.
7. Всё — jpg через `sips -s format jpeg`, в `docs/redesign/current/`. Коммит `docs(redesign): the missing «было» of R8 …`, путь `-- docs/redesign/current`.
8. Вернуть `wm size reset`, `font_scale 1.0`, `user_rotation 0`.

Замер «до» (картины есть только в онбординге), анимации включены (scale 1):
- `python3 tools/perf/measure.py r8-before-intro1 10`, `…-intro3 10` (идёт герой), `…-setup5 10`;
- `…-swipe 12` — со скриптом `input swipe` 900→200 каждые 1,5 с;
- записать кадры/с, запись кадра p50/p90 и CPU главного потока в `docs/notes/redesign.md`.

### 2.1 Код

**`S/feature/onboarding/OnboardingContract.kt`**
- :19–20 — рядом с `indexInPart` новое `val number: Int get() = ordinal + 1`.
- :62 — KDoc `Finished`: «open «Занятия» (spec 3.25)».

**`S/feature/onboarding/OnboardingRoute.kt`**
- :22 — KDoc `onHaveBackup`: «null — только в превью, обе платформы его дают».
- После :36 добавить:
  ```kotlin
  val micCheck = rememberMicPermissionCheck()
  var micAllowed by remember { mutableStateOf(micCheck()) }
  LaunchedEffect(state.step) { micAllowed = micCheck() }
  LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { micAllowed = micCheck() }
  ```
- :55 — передать `micAllowed` в `OnboardingScreen`.

**`S/feature/onboarding/OnboardingProgress.kt` — новый, чистый + composable**
- `internal object OnboardingProgressMath`:
  - `fun segmentState(i: Int, current: OnboardingStep): Segment` — `DONE`, `CURRENT` или `AHEAD`;
  - `fun partGapAfter(i) = i == 3`;
  - `fun fillMs(from: Int, to: Int, still: Boolean) = if (still) 0 else if (abs(to - from) > 1) 450 else 200`.
- `@Composable internal fun OnboardingProgress(step, modifier, placeholder = false)`:
  - семь отрезков по 6 dp, капсула, зазор 6, между 4-м и 5-м — 18;
  - через 8 — подпись 12 sp / 700, заглавные, 0,06 em: часть (`text-2`) слева, счёт (`textTertiary`) справа;
  - подпись — свой `Layout`: не помещается в строку — счёт под частью;
  - `clearAndSetSemantics { contentDescription = "Знакомство, экран 1 из 7" }`.
- `internal object SetupArtHeight { fun of(step, windowHeight: Dp, compact: Boolean): Dp }`.

**`S/feature/onboarding/OnboardingParts.kt`**
- :60–61 — убрать импорты `onboarding_page_description` и `onboarding_setup_progress`.
- :79–81 → `SetupArtSteps = listOf(220.dp, 200.dp, 150.dp)`, `SetupArtCompactMax = 200.dp`; `SetupArtCompactBelow` (760) остаётся.
- :86–91 → `ProgressSegment 6`, `ProgressGap 6`, `ProgressPartGap 18`, `ProgressTop 16`, `ProgressToLabel 8`, `ProgressToTitle 12`.
- :93–95 → `RowPlate 40`, `RowPlateLandscape 36`, `RowPlateCorner 12`; `RowIcon` — 22.
- Новые размеры: `HintCorner 14`, `HintPadding 12/14`, `HintIcon 18`, `HintTop 14`, `FootToCta 6`, `A4Gap 8`, `A4MinHeight 64`, `A4Top 18`.
- :101–103 — `CtaHeight`, `CtaCorner`, `CtaPaddingLandscape` удалить.
- :126 → `ProgressLabel` (12 sp / 700, 0,06 em).
- :136 — `IntroRow(icon, lead, text, highlighted: Boolean = false)`.
- :145 — у строки копии `highlighted = true`.
- :149 — `AppIcons.Device` → `AppIcons.Chart`.
- :152–209 — удалить `PageDots`, `SetupProgress`, `setupBarColor`. `BAR_FILL_MS` переезжает в `OnboardingProgress.kt`.
- :235–242 — плашка значка строки:
  - обычная: `size(RowPlate/Landscape)`, `background(ViolinTheme.accentSoft, RoundedCornerShape(12))`, значок `primary`;
  - `highlighted`: плашка `primary`, значок `onPrimary`.
- :286–295 — `OnboardingCta` → `AppButton(text, onClick, style = Main, icon = …, compact = currentDockMetrics().compact)`: в портрете во всю ширину, в landscape по ширине слов.
- :299–303 — `BackupLink` → `AppButton(style = Text)`, 48.
- Новое `HintCard(icon, lead: StringResource, text: StringResource)`:
  - `surfaceContainer`, скругление 14, поля 12 / 14;
  - значок 18 `onSurfaceVariant`;
  - 14 sp, строка 1,45, `onSurfaceVariant`, lead — `onSurface` / 700.

**`S/feature/onboarding/OnboardingIntro.kt`**
- :131–155 (landscape) — справа `Box { IntroPager(...); LandscapeTopRow(step, skip) }`. Строка :153 уходит в ряд.
- :156–174 (портрет) — после пейджера `OnboardingProgress(step, Modifier.align(TopCenter).widthIn(max = 560).fillMaxWidth().padding(horizontal = padding).offset { … })` (D1).
- :243–251 (`PortraitPage`):
  - `PageDots` из прокрутки убрать;
  - над прокруткой `Spacer(16)` + `OnboardingProgress(step, placeholder = true)` + `Spacer(12)`.
- :252 — перед кнопкой: если `step == LIVE`, то `OnboardingFoot` + `Spacer(6)`. Прокрутка «Пока играете…» больше не несёт.
- :270–272 (`LandscapePage`) — вместо `PageDots` двойник полосы в `Box(heightIn(min = SkipHeight + SkipInset))`. Перед рядом кнопки — «Пока играете…» по центру, 6 до кнопки.
- :326 — `OnboardingFoot(LIVE)` из `PageWords` удалить.

**`S/feature/onboarding/OnboardingScreen.kt`**
- :80–85 — новый параметр `micAllowed: Boolean = false`, прокинуть в `OnboardingSetup` и `SetupWords`.
- :114–141 (landscape) — та же раскладка, что у знакомства: `Box { Column(...) ; LandscapeTopRow(step, skip = null) }`. Старый верхний отступ `PaddingCompact` сменяется местом под ряд.
- :145–149 → `val target = SetupArtHeight.of(step, maxHeight, compact)`:
  - `val art = remember { Animatable(target.value) }`;
  - `LaunchedEffect(target) { if (still) art.snapTo(target.value) else art.animateTo(target.value, tween(PART_CROSSFADE_MS)) }`;
  - :154 `SetupArt(... Modifier.fillMaxWidth().layout { … высота = art.value.dp.roundToPx() })`;
  - при `target == 0` — прежний `Spacer(padding)`.
- :158–175 — в колонке слов: `Spacer(if (art > 0) 16 else 0)`, `OnboardingProgress(step)`, `Spacer(12)`, затем прокрутка.
- :222 — `SetupProgress(...)` удалить.
- :241–244 — «Микрофон»: после текста, если `!micAllowed`, `HintCard(AppIcons.Mic, onboarding_mic_hint_lead, onboarding_mic_hint_text)` на 14 сверху.
- :245–248 — «Эталон»: текст `onboarding_a4_text` (новый). После кнопок — `HintCard(AppIcons.Info, onboarding_a4_hint_lead, onboarding_a4_hint_text)`.
- :249–252 — «Допуск»: под заголовком строки нет. `onboarding_tolerance_text` — после карточек, 13 sp `text-2`, 10 сверху.
- :257–265 — `A4Selector(..., look = ChoiceLook.Onboarding)`, `TolerancePresetList(..., look = ChoiceLook.Onboarding)`.
- :276–280 — значок кнопки: у `MICROPHONE` — `AppIcons.Mic`.

**`S/core/ui/components/SettingsControls.kt`**
- `enum class ChoiceLook { Settings, Onboarding }`; параметр `look = Settings` у обеих функций.
- **Кнопки эталона онбординга** (`A4Buttons`):
  - `Row(Modifier.height(IntrinsicSize.Min).selectableGroup(), spacedBy(8))`, в нём четыре `Column(weight 1, heightIn(min = 64), fillMaxHeight)`;
  - скругление 14, `surfaceContainer`, рамка 1,5 `outlineVariant`;
  - выбранная — `accentSoft`, рамка 1,5 `primary`, число `primary`;
  - число 20 sp / 800 tnum, под ним `Formats.language.sound.hz` 11 sp / 600 `onSurfaceVariant`;
  - `selectable(role = RadioButton)`; детям `clearAndSetSemantics {}`, у кнопки `contentDescription = a4_option_description`.
- **Карточки допуска онбординга** (`ToleranceCards`):
  - ≥ 76, скругление 18, поля 12 / 16, зазор 14, между карточками 10;
  - радио 22: кольцо 2 `outlineVariant`; выбранное — кольцо `primary` и точка 11;
  - название 17 / 800, подпись 13 `text-2`, число 17 / 800 tnum; у выбранной число `primary`;
  - полоска 84 × 10, скругление 5, дорожка `surfaceContainerHigh`, отрезок `ViolinTheme.zoneColors.inTune` по центру шириной `ToleranceBarMath.segmentDp(cents)`;
  - рядом или под подписью (6 сверху, слева) — `ToleranceBarMath.beside(rowPx, widestNamePx, fixedPx)`, ширины — `rememberTextMeasurer`. Ключи — стиль и плотность: на iOS Manrope приходит кадром позже, как у `TabLabels`;
  - описание карточки: «Средний, чувствуется вибрато, плюс-минус 8 центов»; содержимое `clearAndSetSemantics {}`.
- В 120 ветка `Settings` — прежний код :55–167 без изменений.

**`S/core/ui/components/ToleranceBarMath.kt` — новый:**
- `TRACK_DP = 84f`, `SCALE_CENTS = 17f`;
- `segmentDp(cents) = TRACK_DP * cents / SCALE_CENTS` — ≈ 59,3 / 39,5 / 14,8;
- `beside(rowWidth, widestName, fixed): Boolean`.

**Значки**
- `S/core/ui/icons/IconPaths.kt`, после :194: `/** строка статистики, 4-й экран знакомства (i-chart) */ val CHART = listOf("M3 20h18", "M6 16v-5", "M11 16V6", "M16 16v-8")`.
- `S/core/ui/icons/AppIcons.kt`, после :80: `val Chart by lazy { icon("Chart", IconPaths.CHART) }` и `"Chart" to { Chart }` в `all` (после `"Info"`).

**Тема**
- `S/core/ui/theme/Color.kt:110` — `OnboardingDotIdle` удалить.
- `AppTheme.kt:62–65` — `onboardingDotIdle` удалить.

### 2.2 Строки этапа 120
Новые: `onboarding_part_intro`, `onboarding_part_setup`, `onboarding_progress_count`, `onboarding_progress_description`, `onboarding_mic_hint_lead/_text`, `onboarding_a4_hint_lead/_text`, `tolerance_cents_spoken_one/few/many`.

Новый текст: `onboarding_a4_text`, `onboarding_tolerance_text`.

Удалить во всех десяти: `onboarding_page_description`, `onboarding_setup_progress`.

Переводы — раздел 5.

### 2.3 Тесты
- **CT/feature/onboarding/OnboardingProgressTest.kt:**
  - номер = `ordinal + 1`, всего 7;
  - часть: 1–4 — знакомство, 5–7 — настройка;
  - отрезки `DONE / CURRENT / AHEAD`, разрыв только после 4-го;
  - `fillMs`: 200, скачок — 450, `still` — 0.
- **CT/feature/onboarding/SetupArtHeightTest.kt:** 220 / 200 / 150 на 892; ниже 760 — не выше 200; ниже 520 — 0; узкий экран — не выше 200.
- **CT/core/ui/components/ToleranceBarMathTest.kt:**
  - ширины ±0,1;
  - монотонность по центам;
  - рядом — при названии уже остатка; иначе под подписью, ровно на границе — рядом.
- `OnboardingViewModelTest`, `OnboardingFlowTest` — зелёные без правок. `AppIconsTest` — `Chart` в списке. `LocalizationTest` и `python3 tools/i18n/check.py <tag>` ×9.
- **AT/core/ui/AccessibilitySemanticsTest.kt** (только `:app:compileDebugAndroidTestKotlin`):
  - по образцу `showIntroduction` (:387) новый тест: у `MICROPHONE` один узел с описанием «Настройка, экран 5 из 7», у `WELCOME` — «Знакомство, экран 1 из 7»;
  - прежние тесты «Пропустить» (:317, :323) — зелёные.

### 2.4 Превью — `A/feature/onboarding/OnboardingScreenPreviews.kt`
- В `OnboardingPreview` (:15) параметр `micAllowed = false`.
- Добавить:
  - «36e2 · microphone, allowed» (без карточки);
  - «36a · welcome, de» (EINFÜHRUNG);
  - «live, landscape» («Пока играете…» у кнопки);
  - «tolerance, landscape 640 × 360» (полоска под подписью);
  - «tolerance, fr 360 × 640» (Intermédiaire);
  - «a4, 360 × 640, font 1.3» («Гц» под числом, кнопки выше).
- Прежние 36g1–36g4 и «tolerance, small phone, large font» остаются.

### 2.5 Проверка этапа 120
- Сборка и тесты: `./gradlew :app:assembleDebug :app:compileDebugAndroidTestKotlin :app:testDebugUnitTest :shared:testAndroidHostTest :shared:iosSimulatorArm64Test :app:lintDebug`.
- **Эмулятор:**
  - семь экранов в портрете, 892 × 412, 640 × 360, 360 × 640, 320 × 544 при 1,5, 1,3, de / fr;
  - полоса едет с краем картинки при листании (landscape: до «Пропустить» на всех семи);
  - «Пропустить» заливает 2–4 разом; переход в настройку — растворением; «назад» из 5-го;
  - высота картинки 220 → 200 → 150;
  - карточка «Микрофона»: при выданном разрешении её нет — `pm grant … RECORD_AUDIO`, потом `pm revoke`;
  - «убрать анимации»;
  - TalkBack или `uiautomator dump` — описание полосы.
- **Замер «после»** — те же четыре прогона `measure.py`. Допуск — разброс около 10 % (`plan-performance.md`). Layout Inspector: при листании полоса и страницы не пересобираются (растут только счётчики раскладки).
- **iOS:**
  - xcodebuild Debug, установка;
  - `xcrun simctl launch booted com.violinjourney.app.debug -openRoute onboarding` — экран 1 с полосой. Если маршрут не откроется — свежая установка (`simctl uninstall`) показывает его сама;
  - снимок `simctl io booted screenshot`;
  - остальные шаги без касаний не открыть — в заметках: «не проверено на iOS, проверено превью».

---

## 3. Этап 121 — «Настройки» и блок «Данные»

### 3.0 Перед правкой
Снимки `snap.py take` (raw) тех экранов, где стоят `ListRow` и `SegmentedSwitch` с умолчаниями, — после этапа они должны совпасть на 0,000 %:
- лист «Мой путь»;
- форма произведения;
- «Звук» записи.

### 3.1 Общие части
- **`S/core/ui/components/SegmentedSwitch.kt:67–75`** — `sublabels: List<String>? = null` (плюс параметры R4/R5, если их ещё нет).
  - При `sublabels` строку оборачивает `BoxWithConstraints`: место сегмента = (ширина − 2·4 − 2·(n−1)) / n − 8.
  - `val sp = remember(labels, room, style, density) { SegmentLabelSize.of(room) { measurer.widthOf(labels.maxBy…) } }`.
  - :136–147 — `Column { Text(label, sp, 800) ; Text(sublabel, 12 sp, 700, tnum) }` того же цвета, что слово.
  - Слово: если не влезло и в 12 — перенос, сегмент растёт (`IntrinsicSize.Min` уже есть).
- **`S/core/ui/components/SegmentLabelSize.kt`** — `object SegmentLabelSize { MAX 14, MIN 12, STEP 0.5; fun of(room, widthAt): Float }`, по образцу `navigation/TabLabels.kt`.
- **`S/core/ui/components/ListRow.kt`:**
  - :65 → `data class Value(val text: String, val chevron: Boolean = false)`;
  - :156–163 — при `chevron` `Row { Text ; Spacer(4) ; AppIcon(ChevronRight, tint = textTertiary) }`, всё под `dim`;
  - :92–103 — параметр `below: (@Composable () -> Unit)? = null`;
  - после :143 — `if (below != null) { Spacer(8); below() }`.
- **`S/core/ui/components/ScreenHeader.kt`** — D18. `LocalWindowInfo`: ширина больше высоты → 48.

### 3.2 «Настройки»
Переписать **`S/feature/settings/SettingsScreen.kt`** целиком (:59–211):
- `ScreenHeader(stringResource(nav_settings), onBack)`.
- Под шапкой колонка `widthIn(max = 480).verticalScroll().padding(horizontal = 16, bottom = 24)`, по центру.
- `GroupLabel(text, first)` = `SectionLabel`, отступы 22 сверху (у первой — 8), 8 до группы.
- **«Интонация»** — `ListGroup` из двух `ChoiceRow`:
  - строка красит себя `LocalListGroupGround.current`, поля 14 / 16;
  - сверху `TitleWithNote(title, note)` — свой `Layout`: название 16 / 700 и подпись 13 `text-2` справа по базовой линии названия; не помещается — подпись под названием, 2 сверху;
  - ниже 10 — контрол;
  - семантика: у строки названия `clearAndSetSemantics {}`, у контрола `semantics { contentDescription = "$title, $note"; isTraversalGroup = true }`;
  - эталон: `A4Selector(look = Settings)` → `SegmentedSwitch(labels = "440".."443", descriptions = a4_option_description, fontSize = 15, strong = true, containerColor = colorScheme.surface)`;
  - допуск: `TolerancePresetList(look = Settings)` → `SegmentedSwitch(labels = названия, sublabels = tolerance_cents, descriptions = "$name, " + Formats.plural(cents, tolerance_cents_spoken_*), strong = true, containerColor = surface)`;
  - прежний вид `A4Selector` и `PresetCard` для «Настроек» удалить.
- **«Записи»** — `ListGroup { ListRow(sound_settings_row, icon = Sound, caption = sound_settings_row_caption(captionName(state.sound))) }`.
- **«Данные»** — `GroupLabel(backup_block_title)` + `dataBlock()`.
- **«Приложение»** — `ListGroup`:
  - `onLanguageClick?.let { ListRow(settings_language, it, icon = Globe, end = ListRowEnd.Value(languageName(Formats.language.tag), chevron = true)) }`;
  - `ListRow(settings_restart_onboarding, icon = Repeat, caption = settings_restart_caption)`.
- Удаляются `BackBar`, `Section`, `OutlinedButton` и `Fork` / `Target` в этом файле.

`SettingsRoute.kt`, `SettingsContract.kt`, `SettingsViewModel.kt` не меняются.

### 3.3 Контракт, ViewModel и блок «Данные»
**`S/feature/backup/BackupContract.kt:136–145`:**
```kotlin
sealed interface JobPhase {
    data object Data : JobPhase
    data class Files(val part: BackupPart, val index: Int, val count: Int) : JobPhase
    data object Check : JobPhase
    data class Restore(val phase: RestorePhase) : JobPhase
}
data class DataRunning(val restore: Boolean, val percent: Int, val phase: JobPhase)
data class DataBlockState(
    val dateRead: Boolean = false, val lastBackupAtEpochMs: Long? = null, val newSinceStale: Int = 0,
    val running: DataRunning? = null, val totalBytes: Long? = null,
) { val saveWaits get() = running?.restore == true; val restoreWaits get() = running?.restore == false }
```

**`S/feature/backup/BackupPhases.kt` — новый, чистый.**
- `fun current(job: BackupJob.Saving): JobPhase`: `verifying` → `Check`; нет прогресса или часть `DATA` → `Data`; иначе `Files`.
- `fun runningOf(job: BackupJob): DataRunning?`.
- Функции строки фаз добавит этап 122.

**`BackupViewModels.kt:219–233`** — `DataBlockState(dateRead = true, …, running = BackupPhases.runningOf(job))`. Начальное `DataBlockState()` даёт `dateRead = false`.

**`BackupRoutes.kt:121–223`:**
- `DataBlock` (с ViewModel, `rememberBackupSystem`, `LifecycleEventEffect`) зовёт новый `@Composable fun DataGroup(state, analyticsEnabled, onAnalyticsChange, onOpenBackup, onOpenRunningRestore, onPickCopy, onOpenPrivacy, modifier)`:
  1. **«Сохранить копию» / «Копия сохраняется»:**
     - значок: `leading = { AppIcon(SaveCopy, tint = primary) }`;
     - подпись:
       - идёт копия — «56 % · Видео 7 из 12» (`backup_percent` · `backup_phase_part(partShortName, i, n)` / `backup_part_data_short` / `backup_phase_check`);
       - `saveWaits` — `backup_row_wait_restore`;
       - `!dateRead` — пустая строка (держит место);
       - иначе — `[last | never | stale] + (· backup_row_app_size(fileSize) если totalBytes != null)`;
     - строка: `enabled = !saveWaits`; `below` — полоса 4 / r2 `primary` на `surfaceContainerHigh` при копии.
  2. **«Восстановить из копии» / «Восстановление идёт»:**
     - подпись:
       - идёт восстановление — «38 % · Восстанавливаем» (`restore_step_*`);
       - `restoreWaits` — `restore_row_wait_copy`;
       - иначе — `restore_row_caption`;
     - строка: `enabled = !restoreWaits`, полоса при восстановлении;
     - касание: восстановление идёт — `onOpenRestore("")`, иначе `system.pickCopy()`.
  3. **Статистика:** `ListRow(analytics_row, icon = Chart, caption = if (on) analytics_row_caption else analytics_row_off, end = Toggle(on), onClick = { onChange(!on) })`.
  4. **Политика:** `ListRow(privacy_row, icon = Lock, caption = privacy_row_caption, onClick = system.openPrivacyPolicy)`.
- `AnalyticsRow` и `DataRow` удалить.

`docs/notes/backup.md`: фаза в строке «Данных» — доделка до 3.20 «Вход».

### 3.4 Строки этапа 121
Новые: `settings_group_intonation`, `settings_group_records`, `settings_group_app`, `settings_a4_note`, `settings_tolerance_note`, `settings_restart_caption`, `backup_row_app_size`, `backup_row_saving`, `restore_row_running`, `restore_row_caption`, `restore_row_wait_copy`, `backup_row_wait_restore`, `analytics_row_off`, `backup_phase_part`, `backup_phase_check`.

Новый текст: `settings_restart_onboarding`.

Удалить: `settings_a4_text`, `settings_tolerance_text`, `backup_row_total`, `backup_row_running`.

### 3.5 Тесты
- **CT/core/ui/components/SegmentLabelSizeTest.kt:** влезает в 14 → 14; 13,5; до 12; не влезает и в 12 → 12 (перенос).
- **CT/feature/backup/BackupPhasesTest.kt:**
  - без прогресса → `Data`; `DATA` → `Data`; `VIDEO 7/12` → `Files(VIDEO, 7, 12)`; `verifying` → `Check`;
  - `Restoring(EXTRACTING)` → `Restore(EXTRACTING)`, `restore = true`, процент;
  - `saveWaits` / `restoreWaits`.
- **T/feature/backup/BackupViewModelsTest.kt:**
  - `the date not read yet is not never-saved`: начальное `dateRead = false`; после первого значения `dateRead = true`, `lastBackupAtEpochMs = null`;
  - `a copy on its way turns the row into its progress`: `saveTo` с медленным `FakeBackupDocuments` → `running.restore == false`, `restoreWaits`;
  - `a restore on its way dims the copy` — через тот же путь, что тест :98.
- `SettingsViewModelTest` — зелёный без правок.
- **AT/core/ui/components/SegmentedSwitchTest** (компиляция): `sublabels` и `descriptions` — узел несёт описание.
- **`AccessibilitySemanticsTest`** (компиляция): метки групп и шапка «Настроек» — `heading`; сегмент допуска — «Средний, плюс-минус 8 центов», «выбрано».

### 3.6 Превью
- **`A/feature/settings/SettingsScreenPreviews.kt`** (передаёт `dataBlock = { DataGroup(…) }`):
  - обычное: 12 сентября, 3,4 ГБ;
  - копия идёт (`Files(VIDEO, 7, 12)`, 56 %); восстановление идёт (`EXTRACTING`, 38 %);
  - давно без копии (9 новых); вес не посчитан; дата не прочитана;
  - статистика выключена; без «Языка»;
  - fr 360 при 1,3; de 360 («Aus Kopie wiederherstellen»); landscape 892 × 412.
- **`A/core/ui/components/ControlsPreviews.kt`:**
  - :183, :222, :239 — `Device` → `Chart`;
  - новый «Rows · value with chevron, progress under caption».
- **`ComponentsPreviews.kt`:** «Segments · settings: A4 numbers, tolerance word + ±N on the ground colour» (412, fr 360, 360 при 1,3).

### 3.7 Проверка этапа 121
- Сборка и тесты — как в 2.5.
- `snap.py compare` трёх экранов из 3.0 — 0,000 %.
- **Эмулятор:**
  - «Настройки» в портрете, 640 × 360, 360 × 640, 1,3, fr / de;
  - эталон и допуск действуют сразу — зона на Live;
  - «Язык» — системный экран;
  - копия больше 64 МБ → вернуться в «Настройки» → «Копия сохраняется» и приглушённая «Восстановить»;
  - статистика вкл / выкл;
  - TalkBack или `uiautomator dump`.
- **iOS:** `-openRoute settings` — снимок: «Язык» стоит всегда, значение — язык приложения.

---

## 4. Этап 122 — копия, восстановление и проверка R8

### 4.1 Работа копии — `S/core/backup/BackupManager.kt`
- :49–60 `Saving` + `val parts: Set<BackupPart> = BackupPart.entries.toSet()`, `val filled: Set<BackupPart> = emptySet()`.
- :70 `SaveFailed(reason, missingBytes = 0, val parts: Set<BackupPart> = BackupPart.entries.toSet())`.
- :201 — `BackupJob.Saving(fileName, visible = false, parts = parts + BackupPart.DATA)`.
- После :212 — `self.moveOn { (it as? Saving)?.copy(filled = prepared.entries.mapTo(HashSet()) { e -> e.part }) }`.
- :217, :232, :283, :290 — `SaveFailed(…, parts = it.parts)` внутри `moveOn`, где `it` — это `Saving`.
- `A/core/backup/BackupService.kt` не меняется.

### 4.2 ViewModel копии — `BackupViewModels.kt:58–68`
В `update` добавить `parts = (job as? Saving)?.parts ?: (job as? SaveFailed)?.parts ?: it.parts` (D12).

### 4.3 Чистые правила — `S/feature/backup/BackupFacts.kt`, новый
- `enum class Fact { DAYS, LEVEL, SESSIONS, PIECES, VIDEOS, PAGES }`; `data class Chip(val fact: Fact, val count: Int)`; `enum class Missing { VIDEO, AUDIO, SHEETS }`.
- `savedChips(manifest)`:
  - дни, записи, произведения — только ненулевые;
  - `VIDEOS` — если `VIDEO` в `parts` и видео больше 0; `PAGES` — если `SHEETS` в `parts` и страниц больше 0;
  - `missing`: видео не в копии, а видео были; звук не в копии, а записи со звуком были; ноты не в копии, а страницы были.
- `passportChips(manifest)` — дни, уровень, записи, произведения и тот же `missing`.
- `nowChips(counts)`.
- `lost(counts)` — записи, произведения, дни, только ненулевые: для `countsLine` и «Заменить данные?».
- `withoutVideoBytes(job: SaveFailed, contents: BackupContents?): Long?` — только если `reason == NO_SPACE`, `missingBytes == 0`, `VIDEO` в `job.parts` и видео весит больше 0 → `contents.bytesOf(job.parts - VIDEO)`.
- `approximate(bytes) = bytes >= 1 МиБ`.
- `copySteps(job: Saving): List<Step>`:
  - части `BackupPart.entries`, у которых `it == DATA || (it in job.parts && it in job.filled)` (пустой `filled` — все `parts`), плюс `Check`;
  - состояния `DONE / CURRENT / NEXT` по `BackupPhases.current`.
- `restoreSteps(job: Restoring)` — `VERIFYING` только при `checked`; `EXTRACTING`, `FINISHING`; состояние по `ordinal`.

### 4.4 Общие части — `BackupParts.kt`
- :101–113 `countsLine` — через `BackupFacts.lost`, без нулей.
- :132–148 `ScreenTopBar` и `ScreenTitle` удалить (их место — `ScreenHeader`).
- :150–198 `JobProgress` переписать:
  - `PhaseRow(steps)` — одна `AnnotatedString` 13 sp / 700 через « · »: пройденные `text-2`, текущая `primary`, будущие `textTertiary`; высота строки 17; 12 под шапкой; `clearAndSetSemantics { contentDescription = backup_phase_now(текущая) }`;
  - процент 56 sp / 800, −0,03 em, tnum, `onSurface` (в окне не выше 360 — 40), без семантики;
  - полоса 10 / r5 на `surfaceContainerHigh`, 14 сверху и 8 снизу: `LinearProgressIndicator(progress, gapSize = 0.dp, drawStopIndicator = {})`, без прогресса — неопределённая;
  - строка `backup_done_of · remaining` — 13 sp `text-2` tnum;
  - дальше `InfoCard`-ы: первая 22 сверху, между ними 12.
- :200–219 `DoneMark` и `ProblemBlock` → `ResultTile(kind: Done | Problem | Empty, compact)`:
  - 88 / r28, значок 44; при `compact` — 56 и 28;
  - `Done` — `accentSoft` + `Check` `primary`; `Problem` — `dangerSoft.copy(.16f)` + `Alert` `dangerSoft`; `Empty` — `accentSoft` + `Archive` `primary`;
  - 24 сверху, 16 снизу.
- `ResultTitle` — 28 / 800, −0,02 em, `heading`. `ResultText` — 16 sp, строка 1,5, `text-2`.
- :221–227 `TwoButtons` и :246–253 `IconLine` удалить.
- Новые:
  - `InfoCard(text, icon = Info)` — `surfaceContainer`, r18, поля 14, значок 20 `text-2`, 14 sp, строка 1,45;
  - `FactChips(chips, missing)` — `@OptIn(ExperimentalLayoutApi::class) FlowRow(spacedBy 6)`, 10 сверху. Чип 30, капсула, `surfaceContainerHigh`, 13 / 700 `onSurface`. «без …» — без заливки, пунктир 1,5 `outlineVariant` (`drawBehind` + `PathEffect.dashPathEffect`, штрихи как у `WeekBars`), `text-2`;
  - `Modifier.dashedCard(r18)`.
- `ConfirmDialog` — прежний (R1).

### 4.5 `BackupScreen.kt` (:116–330 целиком)
Каркас: `Column { ScreenHeader(...) ; Crossfade(face, 250, widthIn(max = 560)) { face -> AppDock(dock = {…}, metrics = currentDockMetrics().copy(side = 16.dp)) { Column(verticalScroll, padding(horizontal = 16, bottom = LocalDockInset + 16)) {…} } } }`. Лицо «нечего сохранять» — без `AppDock`.

- **Выбор (`choose`):**
  - «Копия займёт» 13 `text-2`. Итог 34 / 800, −0,02 em, tnum: `approximate` ? `backup_total_value(fileSize)` : `fileSize`. Итог и подпись — одно описание.
  - Пока `contents == null` — вместо итога `CircularProgressIndicator(20)`, строк нет, в зоне `Spacer(buttonHeight)`.
  - `WeightBar`: высота 12, r6, зазор 2, своей дорожки нет, выключенная доля — `surfaceContainerHigh`; 10 сверху, 16 до группы; `clearAndSetSemantics {}`.
  - `ListGroup` из четырёх `PartRow`:
    - точка 10 цветом доли (выключенная — `surfaceContainerHigh` + обводка 1,5 `outlineVariant`), зазор 14;
    - название 16 / 700; подпись 13 `text-2` tnum, `maxLines = 2`: `dataLine · fileSize`, «12 произведений · 48 страниц · 180 МБ», `sessionsWord(withSound) · вес`, «6 дублей · вес»;
    - справа «всегда» 13 / 700 `textTertiary` или `AppSwitchMark`;
    - `toggleable(enabled = job == Idle, role = Switch)`; строка не тускнеет.
  - `InfoCard` последствий (прежние `backup_without_*`) 12 сверху, только когда что-то выключено; без анимации.
  - Зона:
    - `AppButton(saving ? backup_saving_button : backup_save_to, icon = SaveCopy, enabled = !busy && !saving, reason = busy ? backup_busy_recording : null, compact)`;
    - при `!busy`: `canShare` → `AppButton(backup_share, Text, icon = Share, enabled = !saving)`, иначе `backup_too_big_to_share` 13 `text-2` по центру.
- **Нечего сохранять:** по центру `ResultTile(Empty)` + заголовок + текст по центру; зоны нет.
- **Прогресс:**
  - шапка «Копия данных» со стрелкой;
  - `PhaseRow(BackupFacts.copySteps(job))` + `JobProgress`;
  - карточки: (`goesOnInBackground` ? `backup_can_leave_title` : `backup_stay_title`), `backup_can_leave_text`;
  - зона: `AppButton(backup_cancel_action, Outline, enabled = job.stoppable, reason = if (job.verifying) backup_phase_verifying_file else null)`.
- **Сохранено:**
  - `ScreenHeader(title = null, onBack = DoneClicked, close = true)`;
  - `ResultTile(Done)`, «Копия сохранена»;
  - карточка файла: плашка 44 / r12 `surfaceContainerHigh`, `Archive` 24 `primary`; имя 15 / 700, 2 строки; `backup_saved_place` или только вес;
  - `FactChips(savedChips)`, `InfoCard(backup_saved_advice)`;
  - зона — «Готово».
- **Не получилось:**
  - шапка со стрелкой, `ResultTile(Problem)`, прежние заголовок и текст всех четырёх причин (:318–328);
  - `withoutVideoBytes?.let { InfoCard(backup_failed_without_video(total words)) }`;
  - зона: «Ещё раз» `Main`, «Закрыть» `Text`.
- **Диалог «Остановить?»** — прежний (:159–168).
- **Низкое окно (не выше 360):** плитка 56, процент 40, кнопки — `compact` из `DockScope`.

### 4.6 `RestoreScreen.kt` (:129–388)
Каркас тот же. Шапка по лицу:
- паспорт — `restore_title` + стрелка (`CloseClicked`);
- прогресс — `restore_progress_title` без стрелки;
- готово — пустая 56;
- неудача — `restore_title` без стрелки;
- «Открываем ваши данные…» (:146–153) без изменений.

- **Чтение:** крутилка 20 + `restore_reading`, зоны нет.
- **Не та копия:** `ResultTile(Problem)` + прежние тексты (:216–218); зона — `restore_pick_another` `Main`.
- **`PassportCard`** (:289–324 переписать):
  - `surfaceContainer`, r18, поля 16;
  - «Копия от …» 17 / 800: `restore_copy_from(Formats.recordDate(date, withYear = date.year != today.year))`;
  - справа вес 13 `text-2` tnum (`copy.fileBytes ?: manifest.totalBytes`);
  - ниже, 2 сверху: `[device] · restore_copy_version(appVersion)`;
  - `FactChips(passportChips)`;
  - одно описание: «Копия от …, 3,4 ГБ, Pixel 10a, версия 1.4: …, без видео».
  - `RestoreScreen` получает параметр `today: LocalDate = SystemWallClock.today()` — превью задают дату сами.
- **Поверх данных:**
  - `ReplacesRow`: 30, `ArrowRight` 18 + `restore_replaces` 13 / 600 `textTertiary`, по центру;
  - `NowCard`: пунктир, без заливки, `restore_now_title` `text-2`, вес `current.totalBytes`, `nowChips`;
  - предупреждение: `dangerSoft.copy(WARNING_ALPHA)` поверх `surfaceContainer`, r18, поля 14, `Alert` 20 `dangerSoft`, `restore_warning` 14 `onSurface`, 12 сверху;
  - зона — колонка с зазором 8: `AppButton(restore_save_first, Outline, icon = SaveCopy)` и `AppButton(restore_button, DangerFilled, icon = Restore, enabled = waitFor == null, reason = waitFor)`.
- **Пустое приложение:** паспорт + `restore_empty_app`; зона — `AppButton(restore_button, Main, icon = Restore, enabled/reason)`.
- **Нет места** (`missingBytes > 0 && missingEvenUnsafeBytes == 0`):
  - вместо паспорта `ResultTile(Problem)` + `restore_no_room_title` + `restore_no_room_text`;
  - зона с зазором 8: «Понятно» `Main` (`CloseClicked`), страховка `Outline`, `AppButton(restore_unsafe_button, Danger, enabled = waitFor == null, reason = waitFor)`.
- **Нет места совсем** (`missingEvenUnsafeBytes > 0`): прежние заголовок и текст; в зоне только «Понятно».
- **Прогресс:**
  - `PhaseRow(restoreSteps)` + `JobProgress`;
  - карточки: (`restore_can_leave_title` | `restore_stay_title`), затем (`restore_can_stop` | `restore_cannot_stop`) — последней, прямо над зоной;
  - зона: `AppButton(backup_stop_confirm, Outline, enabled = job.stoppable)` без `reason`: причина — карточка над ней.
- **Готово:** `ResultTile(Done)` + `restore_done_title` + `FactChips(passportChips(job.manifest))`.
- **Неудача:**
  - данные на месте — `restore_failed_*`; зона: «Ещё раз» `Main`, «Закрыть» `Text`;
  - худший случай — `restore_failed_lost_*`; зона: `restore_retry_same` `Main`, `restore_start_clean` `Danger`.
- **Диалоги.** :179 — `stringResource(restore_confirm_text, countsLine(ready.current.counts))`, один аргумент. `UNSAFE` и `STOP` — прежние.
- `BackHandler` в `RestoreRoute` (`BackupRoutes.kt:115–117`) не меняется.

### 4.7 Прочее
- `S/core/ui/theme/BackupColors.kt:12,14` — `off` удалить; `Color.kt:101` — `BackupOff` удалить.
- `SI/feature/backup/BackupSystem.ios.kt` не меняется.

### 4.8 Строки этапа 122
Новые: `backup_cancel_action`, `backup_failed_without_video`, `backup_phase_now`, `backup_chip_no_video`, `backup_chip_no_audio`, `restore_copy_from`, `restore_copy_version`, `restore_replaces`.

Новый текст: `restore_confirm_text` — один `%1$s` вместо двух.

Удалить: `backup_text`, `backup_saving_title`, `restore_made`, `restore_without_video`, `restore_without_audio`.

Оставить — их зовут уведомление или экраны: `backup_phase_data`, `backup_phase_files`, `restore_phase_verifying`, `restore_phase_extracting`, `backup_cancel`, `restore_now_title`, `restore_without_sheets`, `backup_block_title`, `a4_option_description`, `tolerance_cents`.

### 4.9 Тесты
- **T/core/backup/BackupManagerTest.kt**, новый `the job knows the parts of its copy and those with files`:
  - `saveTo(uri, {DATA, AUDIO})` → `Saving.parts == {DATA, AUDIO}`, `filled` — части с файлами у `FakeBackupStore`;
  - `SaveFailed` (полная карта, как тест :96) несёт те же `parts`;
  - `share` при нехватке (:228) — `parts` тоже.
- **`BackupViewModelsTest`:**
  - `a screen opened again on a failed copy retries the same parts`: копия без видео падает → новый `BackupViewModel` → `state.parts` без видео → `RetryClicked` → `PickPlace` → `PlacePicked` → в `FakeBackupDocuments` архив без видео;
  - `a screen opened again on a running copy shows its choice`.
- **CT/feature/backup/BackupFactsTest.kt:**
  - нули не попадают ни в чипы, ни в `lost`;
  - «6 видео» — только при видео в копии; «без видео» — только если видео были; «без фото нот» — только при страницах;
  - `withoutVideoBytes`: `NO_SPACE` в месте сохранения — да; в памяти телефона — нет; без видео в составе — нет; видео 0 байт — нет;
  - `approximate` на границе 1 МиБ;
  - `copySteps`: порядок записи, части без файлов выпадают, `Check` последним, текущая — по фазе;
  - `restoreSteps`: `VERIFYING` только при `checked`.
- `LocalizationTest` — плейсхолдеры `restore_confirm_text` на всех десяти.
- `:app:compileDebugAndroidTestKotlin` — новые тесты в `AccessibilitySemanticsTest`: паспорт — одно описание; шапки и «Копия сохранена» — `heading`.

### 4.10 Превью — новый `A/feature/backup/BackupScreenPreviews.kt`
**Копия:**
- состав считается; малая копия с «Отправить…»; видео выключено (карточка); большая (объяснение);
- `busy` (причина над кнопкой); короткая «Сохраняем…»; нечего сохранять;
- прогресс `VIDEO 7/12`; «Проверка» (приглушённая «Отменить» + «Проверяем файл»); прогресс iOS (`goesOnInBackground = false`); «Остановить?»;
- сохранено: с местом и без видео, без места;
- не получилось: `NO_SPACE` с «Без видео…», `NO_SPACE` в памяти телефона, `PHONE_FULL`, `UNAVAILABLE`, `FAILED`;
- landscape 892 × 412 (выбор); 640 × 360 (прогресс: процент 40, плитка 56, кнопки 48); fr 360 × 640.

**Восстановление:**
- чтение; поверх данных; поверх данных при `waitFor`; пустое приложение ± `waitFor`;
- не та копия ×3; нет места; нет места при `waitFor`; нет места совсем;
- прогресс: безопасный; без страховки на «Проверяем»; «нельзя остановить»; iOS;
- «Заменить данные?»; «Удалить всё и восстановить?»; готово; «Открываем…»;
- неудача: данные на месте; худший случай;
- копия прошлого года; landscape 640 × 360 поверх данных (зона ≈ 120); de 360 (многоточие в шапке).

### 4.11 Проверка R8 целиком
- Сборка и тесты — как в 2.5, плюс iOS xcodebuild.
- **Снимки «после»** тех же экранов, что «до», в те же сутки. Имена `after_start_*` в `docs/redesign/current/` или в скретч; решает ведущий.
  - Режимы: портрет, 892 × 412, 640 × 360, 360 × 640, 320 × 544 при 1,5, 1,3, de / fr.
  - Сверка со снимками «до» глазами, по списку 3.36.8.
  - Попиксельно (`snap.py`) — только неизменное: «Открываем ваши данные…» и экраны из 3.0.
- **Полный круг на эмуляторе:**
  - сохранение в «Загрузки» (выбор, прогресс, «Отменить» → «Остановить?», «Копия сохранена»);
  - «Отправить…» при малой копии;
  - восстановление поверх данных («Заменить данные?» с ненулевыми, страховка → копия → назад);
  - нехватка места (`fallocate`): три строки зоны, опасный путь приглушён при записи;
  - `pm clear` → «У меня есть копия данных» → пустое приложение → восстановление → перезапуск;
  - перед `pm clear` — новая копия.
- **TalkBack и «убрать анимации»** — на тех же экранах.
- **Замер:** `measure.py` онбординга (итоговый, те же четыре прогона). Сравнить с «до» и после 120: у копии и настроек картин нет, в покое 0 кадров/с — проверить на «Копия данных» и «Настройки».
- **iOS-симулятор:**
  - `-openRoute settings`, `-openRoute backup`;
  - паспорт: положить копию в контейнер (`xcrun simctl get_app_container booted com.violinjourney.app.debug data` → `Documents/copy.zip`) и `-openRoute 'restore?uri=<encodeQuery(file:///…/Documents/copy.zip)>'`: паспорт, «заменит», вторая карточка, зона;
  - «Не сворачивайте…» на прогрессе без касаний не открыть — превью iOS плюс строка в заметках. На iPhone владельца — только с его согласия.
- **Заметки:**
  - `docs/notes/redesign.md` — этапы 120–122: что сделано, отклонения, проверено и не проверено;
  - `docs/notes/onboarding.md:18` — точки и токен ушли, полоса;
  - `docs/notes/backup.md:28` — `backupColors.off` ушёл; фаза в «Данных»; `parts` и `filled` в работе; чипы; «Ещё раз» — тот же состав;
  - `07-start.md` поправить там, где код разошёлся: `SegmentedSwitch` меняется, «Гц» из `Formats`.

---

## 5. Переводы новых и изменённых строк

(В XML апострофы экранируются: `\'`.)

**Этап 120**

| ключ | ru | en | de | es | fr | it | ja | ko | pt | zh |
|---|---|---|---|---|---|---|---|---|---|---|
| onboarding_part_intro | Знакомство | Introduction | Einführung | Introducción | Introduction | Introduzione | はじめに | 소개 | Introdução | 引导 |
| onboarding_part_setup | Настройка | Setup | Einrichtung | Configuración | Configuration | Configurazione | 設定 | 설정 | Configuração | 设置 |
| onboarding_progress_count | %1$d из %2$d | %1$d of %2$d | %1$d von %2$d | %1$d de %2$d | %1$d sur %2$d | %1$d di %2$d | %1$d/%2$d | %1$d/%2$d | %1$d de %2$d | %1$d/%2$d |
| onboarding_progress_description | %1$s, экран %2$d из %3$d | %1$s, screen %2$d of %3$d | %1$s, Bildschirm %2$d von %3$d | %1$s, pantalla %2$d de %3$d | %1$s, écran %2$d sur %3$d | %1$s, schermata %2$d di %3$d | %1$s、%3$d画面中%2$d画面目 | %1$s, %3$d개 중 %2$d번째 화면 | %1$s, tela %2$d de %3$d | %1$s，第 %2$d 屏，共 %3$d 屏 |
| onboarding_mic_hint_lead | Любой ответ ведёт дальше | Either answer moves on | Jede Antwort führt weiter | Cualquier respuesta sigue adelante | Toute réponse permet de continuer | Qualsiasi risposta va avanti | どちらを選んでも先へ進めます | 어떻게 답해도 다음으로 넘어가요 | Qualquer resposta segue adiante | 无论怎么选都会继续 |
| onboarding_mic_hint_text | — разрешить можно и потом, на Live. | — you can allow it later, on Live. | – erlauben können Sie es auch später, in Live. | — se puede permitir más tarde, en Live. | — vous pourrez l\'autoriser plus tard, dans Live. | — si può consentire anche dopo, in Live. | — あとから Live で許可することもできます。 | — 나중에 Live에서 허용해도 돼요. | — dá para permitir depois, no Live. | ——之后也可以在 Live 中允许。 |
| onboarding_a4_hint_lead | Не знаете — оставьте 440. | Not sure? Keep 440. | Unsicher? Lassen Sie 440. | ¿No lo sabes? Deja 440. | Vous ne savez pas ? Gardez 440. | Nel dubbio, lascia 440. | わからなければ 440 のままで。 | 잘 모르겠다면 440 그대로 두세요. | Não sabe? Deixe 440. | 不确定就保留 440。 |
| onboarding_a4_hint_text | Оркестры часто берут 442. | Orchestras often take 442. | Orchester nehmen oft 442. | Las orquestas a menudo usan 442. | Les orchestres prennent souvent 442. | Le orchestre usano spesso 442. | オーケストラでは 442 もよく使われます。 | 오케스트라는 442를 자주 써요. | Orquestras muitas vezes usam 442. | 乐团常用 442。 |
| onboarding_a4_text (новый текст) | Частота ноты A4. От неё считаются все ноты и струны. | The frequency of the note A4. All notes and strings are measured from it. | Die Frequenz des Tons A4. Von ihr aus werden alle Töne und Saiten berechnet. | La frecuencia de la nota A4. A partir de ella se calculan todas las notas y cuerdas. | La fréquence de la note A4. Toutes les notes et les cordes en découlent. | La frequenza della nota A4. Da qui si calcolano tutte le note e le corde. | A4 の音の周波数です。すべての音と弦の高さはここから計算されます。 | A4 음의 주파수예요. 모든 음과 현이 이 값을 기준으로 계산돼요. | A frequência da nota A4. Todas as notas e cordas são calculadas a partir dela. | A4 音的频率。所有音和弦都以它为基准。 |
| onboarding_tolerance_text (новый текст) | Начните мягче — сузить всегда можно в «Настройках». | Start gently — you can always narrow it in “Settings”. | Beginnen Sie milder – enger stellen können Sie jederzeit unter „Einstellungen“. | Empieza con suavidad: siempre se puede estrechar en «Ajustes». | Commencez large — vous pourrez toujours la resserrer dans « Réglages ». | Meglio iniziare larghi: restringere si può sempre in «Impostazioni». | まずはゆるめに。「設定」でいつでも狭められます。 | 넉넉하게 시작하세요 — ‘설정’에서 언제든 좁힐 수 있어요. | Comece com mais folga — dá para estreitar a qualquer momento em “Ajustes”. | 先从宽松开始——随时可以在“设置”中收窄。 |
| tolerance_cents_spoken_one / few / many | плюс-минус %1$d цент / цента / центов | plus or minus %1$d cent / cents / cents | plus minus %1$d Cent (×3) | más o menos %1$d cent / cents / cents | plus ou moins %1$d cent / cents / cents | più o meno %1$d cent (×3) | プラスマイナス %1$d セント (×3) | 플러스마이너스 %1$d센트 (×3) | mais ou menos %1$d cent / cents / cents | 正负 %1$d 音分 (×3) |

**Этап 121**

| ключ | ru | en | de | es | fr | it | ja | ko | pt | zh |
|---|---|---|---|---|---|---|---|---|---|---|
| settings_group_intonation | Интонация | Intonation | Intonation | Afinación | Justesse | Intonazione | 音程 | 음정 | Afinação | 音准 |
| settings_group_records | Записи | Recordings | Aufnahmen | Grabaciones | Enregistrements | Registrazioni | 録音 | 녹음 | Gravações | 录音 |
| settings_group_app | Приложение | App | App | Aplicación | Application | App | アプリ | 앱 | App | 应用 |
| settings_a4_note | все ноты считаются от него | all notes are measured from it | alle Töne gehen von ihm aus | todas las notas parten de él | toutes les notes en découlent | tutte le note partono da qui | すべての音の基準 | 모든 음의 기준 | todas as notas partem dele | 所有音以此为准 |
| settings_tolerance_note | ширина зелёной зоны | width of the green zone | Breite der grünen Zone | ancho de la zona verde | largeur de la zone verte | larghezza della zona verde | 緑のゾーンの幅 | 초록 구간의 너비 | largura da zona verde | 绿色区域的宽度 |
| settings_restart_onboarding (новый текст) | Пройти знакомство снова | Go through the introduction again | Einführung wiederholen | Repetir la introducción | Refaire l\'introduction | Ripeti l\'introduzione | 「はじめに」をもう一度見る | 소개 다시 보기 | Refazer a introdução | 重新查看引导 |
| settings_restart_caption | все семь экранов, выбор сохранится | all seven screens, your choices stay | alle sieben Bildschirme, Ihre Auswahl bleibt | las siete pantallas; tu elección se mantiene | les sept écrans, vos choix sont conservés | tutte e sette le schermate, le scelte restano | 全7画面・選んだ設定はそのまま | 7개 화면 모두, 선택은 그대로 | as sete telas, suas escolhas ficam | 全部七屏，已选设置会保留 |
| backup_row_app_size | в приложении %1$s | %1$s in the app | in der App %1$s | en la aplicación %1$s | %1$s dans l\'application | nell\'app %1$s | アプリ内 %1$s | 앱 데이터 %1$s | no app %1$s | 应用内 %1$s |
| backup_row_saving | Копия сохраняется | Saving the copy | Kopie wird gespeichert | Guardando la copia | Copie en cours d\'enregistrement | Salvataggio della copia | コピーを保存中 | 사본 저장 중 | Salvando a cópia | 正在保存副本 |
| restore_row_running | Восстановление идёт | Restore in progress | Wiederherstellung läuft | Restauración en curso | Restauration en cours | Ripristino in corso | 復元中 | 복원 중 | Restauração em andamento | 正在恢复 |
| restore_row_caption | заменит всё, что сейчас в приложении | replaces everything in the app now | ersetzt alles, was jetzt in der App ist | sustituye todo lo que hay ahora en la aplicación | remplace tout ce que contient l\'application | sostituisce tutto ciò che ora è nell\'app | いまアプリにあるものをすべて置き換えます | 지금 앱에 있는 모든 것을 바꿔요 | substitui tudo o que está no app agora | 将替换应用内现有的一切 |
| restore_row_wait_copy | сначала дождитесь, пока сохранится копия | wait until the copy has been saved first | warten Sie zuerst, bis die Kopie gespeichert ist | primero espera a que se guarde la copia | attendez d\'abord que la copie soit enregistrée | prima attendi che la copia sia salvata | 先にコピーの保存が終わるまでお待ちください | 먼저 사본 저장이 끝날 때까지 기다려 주세요 | primeiro espere a cópia ser salva | 请先等待副本保存完成 |
| backup_row_wait_restore | сначала дождитесь восстановления | wait until the restore has finished first | warten Sie zuerst, bis die Wiederherstellung fertig ist | primero espera a que termine la restauración | attendez d\'abord la fin de la restauration | prima attendi la fine del ripristino | 先に復元が終わるまでお待ちください | 먼저 복원이 끝날 때까지 기다려 주세요 | primeiro espere a restauração terminar | 请先等待恢复完成 |
| analytics_row_off | выключено — ничего не отправляется | off — nothing is sent | aus – es wird nichts gesendet | desactivado: no se envía nada | désactivé — rien n\'est envoyé | disattivato — non viene inviato nulla | オフ — 何も送信されません | 꺼짐 — 아무것도 보내지 않아요 | desativado — nada é enviado | 已关闭——不会发送任何内容 |
| backup_phase_part | %1$s %2$d из %3$d | %1$s %2$d of %3$d | %1$s %2$d von %3$d | %1$s %2$d de %3$d | %1$s %2$d sur %3$d | %1$s %2$d di %3$d | %1$s %2$d/%3$d | %1$s %2$d/%3$d | %1$s %2$d de %3$d | %1$s %2$d/%3$d |
| backup_phase_check | Проверка | Check | Prüfung | Comprobación | Vérification | Verifica | 確認 | 확인 | Verificação | 检查 |

**Этап 122** (ko и zh `backup_failed_without_video` — правка ревью этапа 122: в них встаёт и «меньше 1 МБ» — ko «1MB 미만», после которого «예요» неверно, zh «不到 1 MB»)

| ключ | ru | en | de | es | fr | it | ja | ko | pt | zh |
|---|---|---|---|---|---|---|---|---|---|---|
| backup_cancel_action | Отменить | Cancel | Abbrechen | Cancelar | Annuler | Annulla | キャンセル | 취소 | Cancelar | 取消 |
| backup_failed_without_video | Без видео копия займёт %1$s. | Without video, the copy takes %1$s. | Ohne Video braucht die Kopie %1$s. | Sin vídeo, la copia ocupa %1$s. | Sans vidéo, la copie prend %1$s. | Senza video la copia occupa %1$s. | 動画なしならコピーは %1$s です。 | 영상을 빼면 사본 크기는 %1$s입니다. | Sem vídeo, a cópia ocupa %1$s. | 不含视频，副本大小：%1$s。 |
| backup_phase_now | сейчас: %1$s | now: %1$s | jetzt: %1$s | ahora: %1$s | en cours : %1$s | ora: %1$s | 現在: %1$s | 지금: %1$s | agora: %1$s | 当前：%1$s |
| backup_chip_no_video | без видео | no video | ohne Video | sin vídeo | sans vidéo | senza video | 動画なし | 영상 없음 | sem vídeo | 不含视频 |
| backup_chip_no_audio | без звука | no sound | ohne Ton | sin sonido | sans son | senza audio | 音なし | 소리 없음 | sem som | 不含声音 |
| restore_copy_from | Копия от %1$s | Copy from %1$s | Kopie vom %1$s | Copia del %1$s | Copie du %1$s | Copia del %1$s | %1$s のコピー | %1$s 사본 | Cópia de %1$s | %1$s 的副本 |
| restore_copy_version | версия %1$s | version %1$s | Version %1$s | versión %1$s | version %1$s | versione %1$s | バージョン %1$s | 버전 %1$s | versão %1$s | 版本 %1$s |
| restore_replaces | заменит | replaces | ersetzt | sustituye a | remplace | sostituisce | 置き換え | 대체 | substitui | 将替换 |
| restore_confirm_text (новый текст) | Пропадёт то, что сейчас в приложении: %1$s. Вернуть это будет нельзя. | What is in the app now will be lost: %1$s. It cannot be brought back. | Verloren geht, was jetzt in der App ist: %1$s. Zurückholen lässt es sich nicht. | Se perderá lo que hay ahora en la aplicación: %1$s. No se podrá recuperar. | Ce qui est dans l\'application sera perdu : %1$s. Impossible de le récupérer. | Andrà perso ciò che ora è nell\'app: %1$s. Non si potrà recuperare. | いまアプリにあるものは失われます: %1$s。元に戻すことはできません。 | 지금 앱에 있는 것이 사라져요: %1$s. 되돌릴 수 없어요. | O que está no app agora será perdido: %1$s. Não será possível recuperar. | 应用内现有的内容将丢失：%1$s。无法恢复。 |

## 6. Риски

1. **Имена параметров R4 и R5 в `SegmentedSwitch`** (`containerColor` против `container`, `strong`) и в `ListRow` (`strong`). R8 берёт то, что в коде. Добавленный в `strong` tnum проверить на превью A/B R5.
2. **Полоса поверх пейджера.** Если в `offset` попадёт чтение в композиции, пейджер будет пересобираться каждый кадр — проверить Layout Inspector и `measure.py`. Двойник и живая полоса должны совпадать по высоте: одна функция, один стиль.
3. **Landscape.** Если ряд «полоса + Пропустить» не совпадёт по отступам с двойником в настройке, полоса прыгнет при переходе. Сверить снимки 4-го и 5-го экранов.
4. **Анимация высоты картинки и `AnimatedContent` с `HoldToEnd` (300 мс).** Картина режется снизу (`ArtFit.BOTTOM`) — смотреть, не мелькает ли уходящая картинка.
5. **Семантика «группы с именем» у сегмента** (`contentDescription` на `selectableGroup`). TalkBack может дать лишнюю остановку — проверить на эмуляторе. Запасной вариант — описание на строке с названием, без имени у группы.
6. **Плейсхолдеры `restore_confirm_text`** меняются во всех десяти языках одним коммитом, иначе упадёт `LocalizationTest`.
7. **`BackupJob.Saving.filled`** ставится через `moveOn` после `prepare`: копия, остановленная до этого, `filled` не получит (у неё и экрана нет). Пустой `filled` означает «все `parts`».
8. **Снимки «до» с `pm clear`** — только эмулятор и только после свежей копии в «Загрузках». Ни одна команда — на телефон владельца (`ANDROID_SERIAL=emulator-5554`).
9. **iOS-симулятор без касаний** показывает только «Настройки», выбор копии, паспорт (через файл в контейнере) и первый экран знакомства. Прогресс и «Не сворачивайте…» — превью, в заметках помечено «не проверено на устройстве».
10. **Нижняя зона восстановления на 640 × 360** (≈ 120 и ≈ 170 при нехватке места) выше 64 R1 — это отступление R8 (3.36.8). Проверить, что паспорт прокручивается и не прячется.
11. **`ChoiceLook.Settings` в этапе 120 — прежний вид.** Если 121 откладывается, «Настройки» не меняются, этапы независимы.

### Critical Files for Implementation
- /Users/danil/AndroidStudioProjects/ViolinTuner/shared/src/commonMain/kotlin/com/violinjourney/app/feature/onboarding/OnboardingIntro.kt
- /Users/danil/AndroidStudioProjects/ViolinTuner/shared/src/commonMain/kotlin/com/violinjourney/app/core/ui/components/SettingsControls.kt
- /Users/danil/AndroidStudioProjects/ViolinTuner/shared/src/commonMain/kotlin/com/violinjourney/app/feature/backup/BackupRoutes.kt
- /Users/danil/AndroidStudioProjects/ViolinTuner/shared/src/commonMain/kotlin/com/violinjourney/app/core/backup/BackupManager.kt
- /Users/danil/AndroidStudioProjects/ViolinTuner/shared/src/commonMain/kotlin/com/violinjourney/app/feature/backup/RestoreScreen.kt