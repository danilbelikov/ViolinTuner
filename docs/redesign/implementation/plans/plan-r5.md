# План R5 «Записи» (этапы 111–113; спека 3.36.5 и 5.29 R5, `04-records.md`)

## 0. Предусловия и границы

- **Сначала закоммитить R2.** В рабочем дереве R2 правит те же файлы, что нужны R5: `strings.xml` на десяти языках, `core/ui/icons/IconPaths.kt` и `AppIcons.kt` (там появились `ArrowRight` и `Person`), `app/src/androidTest/.../AccessibilitySemanticsTest.kt`, `feature/practice/**`. Этап 111 начинается только после коммита R2. Иначе будут конфликты в ресурсах и значках.
- **`feature/practice/**` R5 не трогает.** Карточку в «Записях этого дня» зовёт `PracticeScreen.kt:445` (`SessionCard(card, zone, onClick)`). API карточки остаётся совместимым, умолчания дают нужный вид: слово вида как во вкладке, «⋯» нет, потому что `actions = null`. Когда R2/R3 сделают лист дня, они сами передадут `place = RecordPlace.Sheet`. Это пишется в `docs/notes/redesign.md` как поручение R2.
- **R4 не сделан.** Поэтому «Удалить…» у дубля и новая панель выбора встают в прежний список дублей (`TakeBlocks.kt`, `PieceScreen.kt`). `SelectAction` у дублей остаётся до R4.
- Подраздел спеки R5 уже в git (0.83). Код идёт по нему. Мелкие уточнения к спеке — в разделе «Решения», строку этапа правит ведущий.

Сокращения путей:
- `S/` = `shared/src/commonMain/kotlin/com/violinjourney/app/`
- `A/` = `app/src/main/java/com/violinjourney/app/`
- `I/` = `shared/src/iosMain/kotlin/com/violinjourney/app/ios/`
- `T/` = `shared/src/commonTest/kotlin/com/violinjourney/app/`
- `AT/` = `app/src/test/java/com/violinjourney/app/`
- `AAT/` = `app/src/androidTest/java/com/violinjourney/app/`

---

## 1. Мои решения (в заметки этапа; где нужно — уточнением спеки)

**D1. Стрелки `ArrowUp` / `ArrowDown` — залитые, по форме стрелок Live.**
- Пути из `StatusRow.kt:48–67`, пересчитанные из 56 в 24:
  - `ARROW_UP = listOf("F M12 2.6L21.4 14.6H15.4V21.4H8.6V14.6H2.6Z")`
  - `ARROW_DOWN = listOf("F M12 21.4L2.6 9.4H8.6V2.6H15.4V9.4H21.4Z")`
- В макете стрелки линейные (`i-up`, `i-down`), но спека говорит «по форме стрелок Live», а спека выше макета.

**D2. Ряды плеера экрана записи — по спеке, а не по правилу 16 и решению 11 из `04-records.md`.**
- Ряд 2 — A/B шириной 96 и строка «Звук» на остаток ширины.
- Ряд 3 — «С минусовкой | Только скрипка» во всю ширину.
- Между рядами 1 и 2 — 10 dp, между 2 и 3 — 8 dp.

**D3. Описания для TalkBack собираются из видимых строк.**
- Итог, лист ноты и «Что уходит» читаются одним абзацем через `clearAndSetSemantics` на карточке.
- Отдельных «разговорных» строк во множественном числе (процента / центов / минуты) нет: их нет в списке новых слов 3.36.5. Примеры из спеки — это задача по смыслу, а не дословный текст.

**D4. `session_bias_mean` («в среднем %1$s ц») остаётся.** Спека числит эту строку уходящей, но это ровно шапка листа ноты («в среднем −22 ц»). Правило «уходят только те, что больше нигде не нужны» её сохраняет.

**D5. Удаление одной записи из «⋯» идёт через правила выбора.**
- Новое `SelectionIntent.DeleteOneClicked(id)` даёт `Selection(active = false, ids = {id}, confirming = true)`.
- Панель выбора не появляется, чипы не гаснут.
- Диалог — тот же, что на экране записи: «Удалить запись?» и `session_delete_text`, у видео — `video_delete_text` с размером.
- Подтверждение идёт тем же `repository.delete(ids)` одной транзакцией.
- Общий диалог `SelectionDeleteDialog` один на «Записи» и на дубли.

**D6. `RecordCard` получает параметр места `place: RecordPlace { Records, Takes, Sheet }`.**
- От места зависят слово «дубль» (в `Takes` его нет) и подложка (`Sheet` — `colorScheme.surface` #131318).
- Умолчание — `Records`. Благодаря этому `PracticeScreen` не нужно менять.

**D7. Плеер экрана записи и «Звука» — это `AppDock` с двумя новыми параметрами.**
- Новые параметры: `ground: Color` (`surfaceContainer`) и `shape` (скругление 24 только сверху). Метрики свои: `DockMetrics(12, 16, 12, 56)`, в компактном виде — `(8, 16, 10, 48)`. Затухание — `fade = 0`.
- Содержимое кончается у верха плеера: прокрутке даётся `padding(bottom = LocalDockInset.current)` снаружи.
- Затухания нет, поэтому ничего под плеером не прячется. `bringIntoView` блока «Звука» и прокрутка ленты считают видимым только то, что над плеером.
- Сообщение iOS встаёт над плеером само, через `DockPlace`.

**D8. `AppSheet` получает `keepHandleRoom: Boolean = false`.**
- Пока лист держится (`dismissible = false`), вместо ручки стоит пустое место той же высоты (49 dp).
- Лист «Поделиться» не прыгает на 29 dp, когда начинается «Готовим…». Иначе было бы мигание, а спека его запрещает (5.29 «ничего не мигает»).

**D9. `SegmentedSwitch` расширяется необязательными параметрами; вид остаётся один.**
- `container: Color` — сегменты на панели плеера лежат на #131318, а не на цвете панели.
- `strong: Boolean` — насыщенность 800 для сегментов плеера.
- `prefixes: List<String>?` — «A» / «B» размером 15 sp, 800, перед словом 14 sp, 700.
- `descriptions: List<String>?` — «A, оригинал» и «B, обработка» для TalkBack.
- `onHold: ((index: Int, held: Boolean) -> Unit)?` — «пока держишь».
- `shrinkToTwoLines: Boolean` — не влезло в строку 14 sp → две строки 12 sp в той же высоте.
- Все умолчания дают прежнее поведение.

**D10. «Пока держишь» — общий помощник.**
- `S/feature/sound/OriginalHold.kt`, класс `OriginalHold`: логика из `SoundViewModel.kt:346–361` без изменений.
- Его зовут и `SoundViewModel`, и `SessionViewModel`.

**D11. Ряды плеера — в новом `S/feature/sound/components/PlayerRows.kt`.**
- `MiniPlayer.kt` переименовать через `git mv`. Прежний `MiniPlayer` исчезает в 113, а до того зовёт `SeekWave` из нового файла.
- В `PlayerBar.kt` остаются `PlayerBar` и `AbSwitch` — их зовёт только весь экран видео, а он не меняется.

**D12. Цвета видео.**
- Подложки панели полноэкранного видео — сплошное `ViolinTheme.glass` вместо градиентов `videoColors.panel`; текст — `onSurface`.
- Удаляются `VideoPanel` и `VideoSizeWarn` (`Color.kt:94–95`), а также поля `panel` и `sizeWarn` (`VideoColors.kt`).

**D13. «Звук»: раскрытые блоки — множество.**
- Вводится `enum class SoundCard { EQ, COMPRESSOR, REVERB, OUTPUT, BACKING }`. `BACKING` — ключ экрана, а не `SoundBlock` домена.
- `SoundState.expanded: Set<SoundCard> = emptySet()`.
- `SoundIntent.CardToggled(card)` заменяет `BlockHeaderClicked`.

**D14. «Слушать на…» показывает настоящие карточки записей.**
- `SoundState.recordings: List<HistoryCard>` (было `List<RecordingName>`) плюс `today: LocalDate`.
- Для этого `SoundViewModel` получает `clock: WallClock`.

**D15. Значки листа ноты.**
- Значок совета — `Info` 20 цветом зоны ноты, как в макете.
- В шапке листа — стрелка по знаку среднего, а при зоне «в строе» — точка 8.

**D16. Пресеты — `AppChip.Choice`.**
- «Свои» — выбранный чип без действия.
- «Сохранить как пресет» — чип-действие (`selected = null`, `enabled = custom`) без значка.
- `AppChip.Choice` получает `onLongClick` и `onLongClickLabel`: свой пресет удаляется долгим нажатием, как сейчас.

**D17. Полоска без отдельной базовой линии.** День без записей — отметка 3 dp цвета `surfaceContainerHigh` у низа поля. Отдельная линия в 5.29 не названа.

**D18. «Выбрать» в строке заголовка — `AppButton(style = Text)`.** Прежний `SelectAction` остаётся только у дублей (до R4).

**D19. «За две недели» — новый ключ `history_strip_title`.** Прежний `history_chart_title` удаляется, чтобы `check.py` поймал переводы, которые не обновили.

**D20. «Готовим…» на «Продолжить» — `AppButton(enabled = false)`, то есть 0,38 в своих цветах.** Прежде кнопка красилась в `primaryContainer`; так её гасит общий вид кнопок R1.

**D21. Порог большого файла — одна константа.** `ShareInfo.LARGE_BYTES = 100 МиБ` (была приватной в `ShareHost.kt:92`). Её же читает подпись «Удалить…» в меню экрана записи.

**D22. Противоречие в документах.** Правило 16 и решение 11 в `04-records.md` расходятся со спекой. Прав раздел спеки (так сказано в шапке `redesign.md`); в заметки пишется одной строкой.

---

## 2. Этап 111 — вкладка «Записи» и общая карточка

### 2.1 Шаг 0: снимки «до» — до любой правки кода

Порядок действий:
1. Сборка и установка: `./gradlew :app:installDebug -PfakePitch=true -PfakeScenario=IN_TUNE`.
2. Выключить анимации: `adb shell settings put global animator_duration_scale 0`, то же для `transition_animation_scale` и `window_animation_scale`.
3. Данные эмулятора — как в R1 (этап 101, «Проверить»): свободная запись, дубль, видео-дубль, дубль под минусовкой, пресет.
4. Недостающее сделать самим:
   - «без звука» — `run-as com.violinjourney.app.debug rm files/sessions/<файл свободной записи>`;
   - «видео потеряно» — удалить файл одного видео-дубля;
   - длинная запись (≥ 1 мин) — для экрана прогресса «Поделиться».
5. Каждый кадр снимать дважды: `python3 tools/perf/snap.py take <папка вне репо>/r5-before/<имя>.raw` и `adb exec-out screencap -p` → JPG (`sips -s format jpeg`) в `docs/redesign/current/`.
6. Все «до» и «после» — в один календарный день или при одной и той же дате эмулятора: дата меняет полоску и вставки.

Список кадров (`docs/redesign/current/`):

| Раздел | Кадры |
|---|---|
| Вкладка | `rec_tab`, `rec_tab_pick`, `rec_tab_filter_empty` (под «Эта неделя»), `rec_tab_small` (360 × 640), `rec_tab_land` (892 × 412), `rec_tab_land_small` (640 × 360), `rec_tab_fr13` (fr, шрифт 1,3) |
| Экран записи | `rec_session_sound`, `rec_session_sound_bottom` (прокручено до «Переименовать · Удалить»), `rec_session_video`, `rec_session_video_scrolled`, `rec_session_backing`, `rec_session_backing_preparing` (если удаётся поймать после `run-as … rm -rf cache/backing*`), `rec_session_silent`, `rec_session_video_lost`, `rec_session_small`, `rec_session_land` (видео 892 × 412), `rec_session_land_small` |
| Лист ноты | `rec_note_sheet`, `rec_note_sheet_video` |
| «Звук» | `rec_sound`, `rec_sound_eq`, `rec_sound_backing`, `rec_sound_everyone`, `rec_sound_pick` (диалог), `rec_sound_small`, `rec_sound_land` |
| «Поделиться» | `rec_share_sound`, `rec_share_video`, `rec_share_backing`, `rec_share_progress` (ошибку снимать, только если её удаётся вызвать) |
| Контроль | `live_play` (сверка `snap.py` в конце: R5 Live не трогает) |

Команды режимов:
- 360 × 640: `wm size 720x1280 && wm density 320`;
- landscape: `settings put system accelerometer_rotation 0; settings put system user_rotation 1`;
- крупный шрифт: `settings put system font_scale 1.3`;
- язык: `cmd locale set-app-locales com.violinjourney.app.debug --locales fr` (и `de`).

Коммит: `docs(redesign): «было» of the records, the recording, «Звук» and «Поделиться» for R5` через `git add docs/redesign` и `git commit -- docs/redesign`. Сырые `.raw` в репозиторий не кладутся.

### 2.2 Код

**`S/feature/history/HistoryContract.kt`**
- `:6` → `enum class HistoryFilter { ALL, TAKES, VIDEO, LIVE }`.
- `HistoryState` (`:40–60`): поля те же, конструктор совместим с `AccessibilitySemanticsTest`. Уточнить KDoc: `totalCount` отличает «пусто совсем» от «пусто под фильтром»; добавить `val stripTotal get() = days.sumOf { it.count }`.
- `HistoryIntent` (`:64–74`): `+ data object OpenLiveClicked`. «Показать все записи» = `FilterSelected(ALL)`.
- `HistoryEffect` (`:76–78`): `+ data object OpenLive`.

**`S/feature/history/HistoryReducer.kt`**
- `passes` (`:53–58`) → `passes(filter, session: SessionSummary)`:
  - `TAKES` → `pieceId != null`;
  - `VIDEO` → `videoPath != null`;
  - `LIVE` → `pieceId == null && videoPath == null`.
- Вызов `:39` → `.filter { passes(filter, it) }`.
- Убрать импорты `HistoryWeeks`, `DateTimeUnit`, `minus`. `HistoryWeeks` не удалять — им пользуется `PracticeStats`.

**`S/core/domain/IntonationConfig.kt:70`** — удалить `historyMonthDays`.

**`S/feature/history/Selection.kt`**
- `SelectionIntent` (`:16–38`): `+ data class DeleteOneClicked(val id: Long)` — «Удалить…» у карточки.
- `reduce` (`:42–64`):
  - `DeleteOneClicked` → если режим выбора открыт или `id !in visible` — без изменений; иначе `Selection(active = false, ids = setOf(id), confirming = true)`;
  - `DeleteDismissed` → если `!selection.active`, то `Selection()`, иначе прежнее.
- `prune` (`:73–78`): при `!active && confirming && ids.any { it !in visible }` → `Selection()`.

**`S/feature/history/HistoryViewModel.kt`**
- `onIntent` (`:94–106`): `OpenLiveClicked` → `effectChannel.trySend(HistoryEffect.OpenLive)`.
- `select()` (`:108–114`) не меняется: для одной записи срабатывает тот же `repository.delete(current.ids)`.

**`S/feature/history/HistoryRoute.kt`**
- Новый параметр `onOpenLive: () -> Unit`, эффект `OpenLive` (`:42–44`).
- `CardActions(…, onDelete = { viewModel.onIntent(HistoryIntent.Select(SelectionIntent.DeleteOneClicked(it))) })` (`:66–68`).

**`S/feature/history/components/CardMenu.kt`**
- `CardActions` (`:37–41`): `+ val onDelete: ((Long) -> Unit)? = null`.
- `CardMenuButton` (`:49–86`):
  - цель 48 и значок 24 `onSurfaceVariant` (было 40);
  - `AppMenu(danger = actions.onDelete?.let { MenuDanger(card_menu_delete, …, divider = hasItems) })`, где `hasItems = (card.take && onBest != null) || card.hasAudio`;
  - порядок: «Отметить лучшим» / «Снять отметку», затем «Поделиться» и «Звук…», затем черта и «Удалить…».

**`S/core/ui/components/AppMenu.kt`** (расширение R1)
- `class MenuDanger(text, onClick, icon = Trash, caption: String? = null, captionStrong: Boolean = false, divider: Boolean = true)`.
- `MenuRows`: черта только при `divider`.
- `MenuRow`: у опасного пункта подпись 13 sp `onSurfaceVariant`, а при `captionStrong` — 700 `dangerSoft`. Подпись нужна в 112, но API ставится сразу.

**Новый `S/feature/history/components/RecordLine.kt`** (чистый)
```kotlin
enum class RecordPlace { Records, Takes, Sheet }
enum class RecordKindWord { TAKE, VIDEO }
data class RecordLineParts(val kind: RecordKindWord?, val noSound: Boolean, val backing: Boolean)
object RecordLine {
    fun partsOf(card: HistoryCard, place: RecordPlace): RecordLineParts
}
```
- `kind`:
  - `hasVideo` → `VIDEO`;
  - `underBacking` → `null` («под минусовку» стоит вместо «дубль»);
  - `take && place != Takes` → `TAKE`;
  - иначе `null`.
- `noSound = !hasAudio`.
- `backing = underBacking`.

**`S/feature/history/components/SessionCard.kt`** — `RecordCard` (`:120–233`) и `SessionCard` (`:79–101`)
- Новые параметры: `place: RecordPlace = Records`, `current: Boolean = false` (обводка 1,5 акцентом — лист «Слушать на…»). Параметр `meta` у `RecordCard` остаётся «время · длительность».
- Вид:
  - высота ≥ 64, скругление 18 (`AppShapes.M`, было 16 на `:63`);
  - подложка `surfaceContainer`, у `Sheet` — `colorScheme.surface`;
  - поля 8 / 4 / 8 / 12 (сверху / справа / снизу / слева; было `:182`), зазор 12;
  - название 15 sp, 700, одна строка;
  - строка 13 sp `onSurfaceVariant` `tnum`: одна `Text` из `buildAnnotatedString` — `meta` + « · дубль» / « · видео» + « · без звука» + « · » [значок `Backing` 14 через `InlineTextContent`] «под минусовку»; `maxLines = 1`, многоточие с конца, так что время и длительность видны всегда;
  - звезда 14 акцентом — как было.
- «⋯»: `menu = actions?.onDelete != null && !selecting` (прежнее условие `:136` уходит, «⋯» теперь есть у каждой карточки с действиями).
- TalkBack (`:137–148` → новое):
  - описание на всей карточке: вид (`record_tile_take` / `record_tile_only_video` / `record_tile_record`), «лучший», название, `spokenDate`, время, длительность, `backing_take_mark`, «без звука»;
  - плитка и тексты под `clearAndSetSemantics {}` — видимое слово второй раз не читается;
  - «⋯» — отдельная кнопка «Ещё».
- `SessionCard` прокидывает `place`, умолчание — `Records`.

**`S/feature/history/components/RecordTile.kt:17–23`** — `EMPTY(72.dp, 32.dp)`, было 56 / 30.

**`S/feature/history/components/SelectionBar.kt`**
- `:84`: «Выбрано: N» 17 sp, 800, `maxLines = 1`, многоточие.
- `:86–100`: «Выбрать все / Снять все» — высота 48 (было 40).
- `:102`: корзина окрашена `dangerSoft`, без выбора — 0,38.
- Новые функции:
  - `RecordDeleteDialog(videoBytes: Long?, onConfirm, onDismiss)` — `DeleteDialog(session_delete_title, video_delete_text | session_delete_text)`. Тем же `SessionScreen` заменит свой приватный диалог в 112.
  - `SelectionDeleteDialog(selection, cards: List<HistoryCard>, takes: Boolean, onIntent: (SelectionIntent) -> Unit)` — при `active` — прежний «Удалить N записей / дублей?», иначе — `RecordDeleteDialog` для одной карточки.

**`S/feature/history/components/DailyChart.kt`** — `DailyChartMath` (`:46–83`) и рисование (`:97–169`)
- Константы: `FIELD = 28f`, `GAP = 4f`, `MIN_BAR = 3f`, `EMPTY_MARK = 3f`, `CORNER = 3f`, `LABELS_GAP = 5f`, `LABELS_HEIGHT = 14f`.
- `barHeight(count, top)` → `(FIELD * count / top).coerceIn(MIN_BAR, FIELD)` или `null` при нуле.
- `barWidth(width, count) = (width - GAP * (count - 1)) / count`; `barLeft(index) = index * (barWidth + GAP)`.
- `numbered` удаляется; `labelled` остаётся.
- Цвета: день с записями — `primaryContainer`, сегодня — `primary`, пустой — отметка `surfaceContainerHigh`.
- Подписи 11 sp, 600, `textTertiary`; «сегодня» — `primary`.
- Рост 300 мс со ступенью 15 и `grown` — как сейчас.

**`S/feature/history/HistoryScreen.kt`** — переписать `:75–369`

`TitleRow(title, canSelect, onSelect, compact)`:
- высота 56 (landscape 52 = `SelectionBarHeight.Landscape`);
- `TabTitle(weight 1f)` с многоточием; TalkBack читает полностью;
- `AppButton("Выбрать", style = Text)` — не сжимается;
- «Выбрать» есть, только когда `!loading && cards.isNotEmpty()`.

`StripCard(state)` вместо `ChartCard` (`:288–317`):
- `surfaceContainer`, скругление 18, поля 14 / 16, сверху 8;
- строка «За две недели» 13 sp `onSurfaceVariant` и итог `history_count_*` 15 sp, 800 `tnum`;
- под ними `DailyChart`;
- `semantics(mergeDescendants)`: прежнее описание вместе с итогом.

`KindChips` вместо `Filters` (`:319–350`):
- `FlowRow(selectableGroup, spacedBy(8))` из четырёх `AppChip`;
- видимый отступ сверху 14, снизу 4;
- касание 48 даёт 8 видимых между строками при переносе.

`DayHeader` (`:226–261`) — сделать `internal` в `components/DayHeader.kt`: его возьмёт лист «Слушать на…».

`CountRow` (`:263–286`) удалить.

`EmptyAll(inColumn: Boolean)` вместо `EmptyHistory` (`:352–369`):
- `RecordTile(EMPTY)` без звука;
- `history_empty_title` 20 sp, 800;
- `history_empty_text` 15 sp `onSurfaceVariant` по центру, поля 32.

`EmptyUnderFilter(filter, onShowAll)`:
- сверху 40, плитка 72: знак видео у `VIDEO`, нота у остальных;
- заголовок 18 sp, 800 и текст 14 sp;
- `AppButton("Показать все записи", style = Text)`.

Раскладка:
- **Портрет:**
  - при `totalCount == 0` — `AppDock(dock = { AppButton(history_open_live, icon = AppIcons.TabLive.normal) { onIntent(OpenLiveClicked) } })`, внутри заголовок и пустое по центру;
  - иначе `LazyColumn`: заголовок, полоска, чипы, группы (вставка и карточки через 6, `animateItem(fadeOutSpec = tween(150), placementSpec = tween(250))`), пустое под фильтром;
  - при загрузке — только заголовок.
- **Landscape:** `Row`:
  - левая `Column(360)`: заголовок (компактный), полоска, чипы; `dimmedWhen(selecting)`;
  - правая `LazyColumn`: `contentPadding(top = 52)`, чтобы панель выбора во всю ширину не закрывала первую карточку;
  - пусто — заголовок слева, в правой колонке по центру плитка, слова и главная «Открыть Live» (не нижняя зона).
- Выбор: `SelectionBar` поверх (`:207–209`); полоска и чипы — `dimmedWhen(selecting)`.
- Диалог (`:211–219`) → `SelectionDeleteDialog(…, takes = false)`.

**Экран произведения (старый список дублей)**
- `S/feature/repertoire/piece/TakeBlocks.kt:351` → `RecordCard(…, place = RecordPlace.Takes)`.
- `PieceRoute.kt:125–130` → `CardActions(…, onDelete = { leaving(); viewModel.onIntent(PieceIntent.Select(SelectionIntent.DeleteOneClicked(it))) })`.
- `PieceScreen.kt:329–338` → `SelectionDeleteDialog(…, takes = true)`.
- `PieceViewModel.select()` (`:529–537`) не меняется: его защита «во время дубля выбор не открывается» закрывает и «⋯».

**Навигация**
- `A/navigation/AppNavHost.kt:142–149`: `HistoryRoute(onOpenLive = { navController.navigateToTopLevel(TopLevelDestination.LIVE) }, …)`.
- `I/IosNavHost.kt:150–158`: то же.

### 2.3 Строки (ru | en | de | fr | es | it | pt | ja | ko | zh)

| Ключ | Переводы |
|---|---|
| `history_filter_takes` | Дубли \| Takes \| Takes \| Prises \| Tomas \| Take \| Takes \| テイク \| 테이크 \| 录制 |
| `history_filter_video` | Видео \| Video \| Video \| Vidéo \| Vídeo \| Video \| Vídeo \| 動画 \| 영상 \| 视频 |
| `history_filter_live` | С Live \| From Live \| Aus Live \| Depuis Live \| De Live \| Da Live \| Do Live \| Liveから \| Live에서 \| 来自 Live |
| `history_strip_title` | За две недели \| Last two weeks \| Letzte zwei Wochen \| Sur deux semaines \| Últimas dos semanas \| Ultime due settimane \| Últimas duas semanas \| この2週間 \| 최근 2주 \| 近两周 |
| `history_empty_title` | Здесь появятся ваши записи \| Your recordings will appear here \| Hier erscheinen Ihre Aufnahmen \| Vos enregistrements apparaîtront ici \| Aquí aparecerán tus grabaciones \| Qui compariranno le tue registrazioni \| Suas gravações aparecerão aqui \| ここに録音が並びます \| 여기에 녹음이 나타납니다 \| 你的录音会出现在这里 |
| `history_open_live` | Открыть Live \| Open Live \| Live öffnen \| Ouvrir Live \| Abrir Live \| Apri Live \| Abrir o Live \| Live を開く \| Live 열기 \| 打开 Live |
| `history_empty_takes_title` | Дублей пока нет \| No takes yet \| Noch keine Takes \| Pas encore de prises \| Aún no hay tomas \| Ancora nessun take \| Ainda não há takes \| テイクはまだありません \| 아직 테이크가 없습니다 \| 还没有录制 |
| `history_empty_video_title` | Видео пока нет \| No videos yet \| Noch keine Videos \| Pas encore de vidéos \| Aún no hay vídeos \| Ancora nessun video \| Ainda não há vídeos \| 動画はまだありません \| 아직 영상이 없습니다 \| 还没有视频 |
| `history_empty_live_title` | Записей с Live пока нет \| No recordings from Live yet \| Noch keine Aufnahmen aus Live \| Pas encore d\'enregistrements depuis Live \| Aún no hay grabaciones de Live \| Ancora nessuna registrazione da Live \| Ainda não há gravações do Live \| Live からの録音はまだありません \| 아직 Live 녹음이 없습니다 \| 还没有来自 Live 的录音 |
| `history_show_all` | Показать все записи \| Show all recordings \| Alle Aufnahmen zeigen \| Voir tous les enregistrements \| Mostrar todas las grabaciones \| Mostra tutte le registrazioni \| Mostrar todas as gravações \| すべての録音を表示 \| 모든 녹음 보기 \| 显示全部录音 |
| `card_menu_delete` | Удалить… \| Delete… \| Löschen… \| Supprimer… \| Eliminar… \| Elimina… \| Excluir… \| 削除… \| 삭제… \| 删除… |
| `backing_take_mark` | под минусовку \| with backing \| mit Begleitung \| avec accomp. \| con acompañamiento \| con la base \| com playback \| 伴奏つき \| 반주와 함께 \| 带伴奏 |

Длинные тексты пустых состояний:

`history_empty_text`
- ru: Запишите игру кнопкой на Live или дубль на экране произведения — после записи будет разбор: что держится, а что уходит.
- en: Record your playing with the button on Live, or a take on the screen of a piece — then comes the analysis: what holds and what drifts.
- de: Nehmen Sie Ihr Spiel mit der Taste in Live auf oder einen Take auf der Seite eines Stücks — danach folgt die Auswertung: was sitzt und was abdriftet.
- fr: Enregistrez votre jeu avec le bouton de Live ou une prise sur l\'écran d\'un morceau : ensuite vient l\'analyse — ce qui tient et ce qui dérive.
- es: Graba tu interpretación con el botón de Live o una toma en la pantalla de una obra: después verás el análisis, qué se mantiene y qué se desvía.
- it: Registra con il pulsante di Live o un take nella schermata di un brano: poi arriva l\'analisi — cosa tiene e cosa sfugge.
- pt: Grave com o botão do Live ou um take na tela de uma peça — depois vem a análise: o que se mantém e o que escapa.
- ja: Live の録音ボタンか、曲の画面のテイクで録音してください。録音後に分析が表示されます — 安定している音と、ずれていく音。
- ko: Live의 녹음 버튼이나 곡 화면의 테이크로 녹음해 보세요. 녹음이 끝나면 분석이 나옵니다 — 안정적인 음과 벗어나는 음.
- zh: 用 Live 的录音按钮录下演奏，或在曲目页面录制——录完会有分析：哪些音稳，哪些音跑。

`history_empty_takes_text`
- ru: Дубль записывают на экране произведения — кнопкой внизу.
- en: A take is recorded on the screen of a piece — with the button at the bottom.
- de: Ein Take wird auf der Seite eines Stücks aufgenommen — mit der Taste unten.
- fr: Une prise s\'enregistre sur l\'écran d\'un morceau, avec le bouton en bas.
- es: Una toma se graba en la pantalla de una obra, con el botón de abajo.
- it: Un take si registra nella schermata di un brano, con il pulsante in basso.
- pt: Um take é gravado na tela de uma peça, com o botão embaixo.
- ja: テイクは曲の画面で、下のボタンで録音します。
- ko: 테이크는 곡 화면에서 아래 버튼으로 녹음합니다.
- zh: 录制在曲目页面进行——用底部的按钮。

`history_empty_video_text`
- ru: Видео-дубль снимают на экране произведения — кнопкой рядом с записью дубля.
- en: A video take is shot on the screen of a piece — with the button beside the take button.
- de: Ein Video-Take wird auf der Seite eines Stücks gefilmt — mit der Taste neben der Take-Taste.
- fr: Une prise vidéo se filme sur l\'écran d\'un morceau, avec le bouton à côté de celui de la prise.
- es: Una toma de vídeo se graba en la pantalla de una obra, con el botón junto al de la toma.
- it: Un take video si gira nella schermata di un brano, con il pulsante accanto a quello del take.
- pt: Um take em vídeo é gravado na tela de uma peça, com o botão ao lado do botão do take.
- ja: 動画テイクは曲の画面で、テイクのボタンの隣のボタンで撮影します。
- ko: 영상 테이크는 곡 화면에서 테이크 버튼 옆의 버튼으로 촬영합니다.
- zh: 视频录制在曲目页面进行——用录制按钮旁边的按钮。

`history_empty_live_text`
- ru: Нажмите кнопку записи на Live — здесь появится запись с разбором.
- en: Press the record button on Live — the recording and its analysis will appear here.
- de: Tippen Sie in Live auf die Aufnahmetaste — hier erscheint die Aufnahme mit Auswertung.
- fr: Touchez le bouton d\'enregistrement de Live : l\'enregistrement et son analyse apparaîtront ici.
- es: Pulsa el botón de grabar en Live: aquí aparecerá la grabación con su análisis.
- it: Premi il pulsante di registrazione in Live: qui comparirà la registrazione con l\'analisi.
- pt: Toque no botão de gravar no Live — a gravação com a análise aparecerá aqui.
- ja: Live の録音ボタンを押すと、ここに分析つきの録音が表示されます。
- ko: Live에서 녹음 버튼을 누르면 분석과 함께 녹음이 여기에 나타납니다.
- zh: 在 Live 按下录音按钮——带分析的录音会出现在这里。

Удалить во всех языках: `history_filter_this_week`, `history_filter_month`, `history_empty_filter`, `history_empty`, `history_chart_title`, `backing_take_meta`, `record_tile_sound`.

Проверка: `python3 tools/i18n/check.py <тег>` для девяти переводов и `LocalizationTest`.

### 2.4 Тесты

**commonTest (идут и на iOS):**
- `T/feature/history/HistoryReducerTest.kt`: тесты `:86` и `:91` (неделя и месяц) заменить на:
  - дубли — это записи, привязанные к произведению, и видео-дубли тоже;
  - «Видео» — любая запись с видео, в том числе с пропавшим файлом (`videoPath` есть, `audioPath == null`);
  - звуковой дубль удалённого произведения (`pieceId = null`) — под «С Live», его видео-дубль — только под «Видео»;
  - отвязанная запись под минусовкой — под «С Live» и с `underBacking`;
  - `:96` — полоска и счётчик не зависят от фильтра (с `TAKES`);
  - `:110` — «пусто совсем» и «пусто под фильтром» (`VIDEO`) — разные состояния.
- `T/feature/history/SelectionRulesTest.kt`:
  - «Удалить…» спрашивает про одну запись, не открывая выбор;
  - отказ сбрасывает; подтверждение закрывает;
  - игнорируется для невидимой карточки и при открытом выборе;
  - `prune` сбрасывает, если запись исчезла;
  - «Выбрать все» — только под фильтром.
- `T/feature/history/components/DailyChartMathTest.kt`: переписать под новую геометрию:
  - поле 28, минимум 3, пустой день — `null`;
  - 14 столбиков с зазором 4 ровно заполняют ширину, последний кончается у края, на узкой карточке не перекрываются;
  - понедельники и «сегодня» — прежний тест;
  - тест `numbered` удалить.
- Новый `T/feature/history/components/RecordLineTest.kt`: свободная со звуком — без слова; дубль — «дубль» во вкладке и без него в дублях; видео-дубль — «видео» везде; без звука — после вида; под минусовку — знак в конце вместо «дубль»; видео под минусовку — «видео» и знак; отвязанная под минусовку — знак, без «дубль».

**app/test:**
- `AT/feature/history/HistoryViewModelTest.kt`:
  - `:113`, `:218`, `:234` — фильтры `TAKES`, `VIDEO`, `LIVE` вместо `THIS_WEEK` и `MONTH`;
  - новые: «Удалить…» у карточки спрашивает про одну и удаляет её одним вызовом `delete(setOf(id))`; отказ ничего не удаляет; «Открыть Live» даёт `HistoryEffect.OpenLive`.
- `AT/feature/repertoire/PieceViewModelTest.kt`: «Удалить…» у дубля спрашивает про него одного и удаляет, произведение остаётся; пока идёт дубль, «Удалить…» не открывается.

**androidTest (только компиляция):**
- Новый `AAT/feature/history/RecordCardTest.kt`:
  - у карточки одно описание (вид, название, дата, «под минусовку», «без звука»), текстового узла со словом вида нет;
  - «⋯» — отдельная кнопка «Ещё», в выборе её нет;
  - четыре чипа видны на 360 dp при шрифте 1,3 (`fr`).
- `AccessibilitySemanticsTest` не правится: его вызовы совместимы.

### 2.5 Превью

**`A/feature/history/HistoryScreenPreviews.kt`** — `:54` использует `THIS_WEEK`, переписать. Состояния:
- обычное 412 × 892;
- выбор;
- загрузка;
- пусто совсем (с нижней зоной);
- пусто под «Дубли», «Видео», «С Live»;
- 360 × 640;
- fr 360 при 1,3 (перенос чипов, многоточие заголовка);
- landscape 892 × 412, landscape с выбором, landscape пусто, 640 × 360.

**Новый `A/feature/history/RecordCardPreviews.kt`:**
- все виды карточки: свободная, дубль, «лучший», видео, без звука, дубль без звука, под минусовку, видео под минусовку, отвязанная;
- выбранная и свежая;
- `place = Takes` и `place = Sheet` (на #131318), `current`;
- длинное имя на 360, de и fr;
- `AppMenuCard` меню: дубль; запись со звуком; без звука — одно «Удалить…» без черты.

### 2.6 Проверка на эмуляторе

- Фильтры: переходы, перенос чипов на 360 / fr / de / 1,3.
- «Выбрать» появляется и прячется; выбор; «Выбрать все» под фильтром.
- «Удалить…» из «⋯» у записи, у видео (диалог с размером), у дубля в произведении; карточка уходит с затуханием.
- Пусто: «Открыть Live» ведёт на Live, «Показать все» включает «Все».
- Landscape обоих размеров.
- «Записи этого дня» на «Занятиях» выглядят по-новому без правки их кода.

Коммит: `feat(records): «Записи» by kind, the two-week strip and one card for every list (stage 111, spec 3.36.5)`.

---

## 3. Этап 112 — экран записи и лист ноты

### 3.1 Общие части (R1)

**`S/core/ui/icons/IconPaths.kt`, `AppIcons.kt`**
- `ARROW_UP` и `ARROW_DOWN` (D1), `val ArrowUp` и `val ArrowDown`.
- Обе строки добавить в `AppIcons.all` рядом с `ArrowRight` из R2.

**`S/core/ui/components/SegmentedSwitch.kt:67–75`** — параметры из D9:
- при `onHold != null` сегмент вместо `selectable` получает `pointerInput(detectTapGestures(onTap = onSelect, onLongPress = { onHold(i, true) }, onPress = { press в interactionSource; tryAwaitRelease; onHold(i, false) }))` и `semantics(mergeDescendants) { role = RadioButton; selected; onClick; contentDescription = descriptions[i] }` — это переносится из `MiniPlayer.kt:215–266`;
- `shrinkToTwoLines`: подпись меряется `rememberTextMeasurer`; если в одну строку 14 sp не влезает — 12 sp и `maxLines = 2`.

**`S/core/ui/components/AppDock.kt:97–104`, `:145–179`**
- Новые параметры: `ground: Color = Color.Unspecified` (→ `surface`) и `shape: Shape = RectangleShape`.
- В `DockZone` вместо `drawRect(ground)` — `drawOutline(shape.createOutline(size, layoutDirection, this), ground)`.

### 3.2 Контракт и модель

**`S/feature/session/SessionContract.kt`**
- `RollSegment` (`:11–25`): `+ val sameNoteCount: Int = 1`.
- `SessionContent` (`:30–57`): `+ hasVideo: Boolean = false`, `+ underBacking: Boolean = false`, `+ nearCents: Int = 20` (порог «Размах больше N ц»).
- `SessionState.Loaded` (`:67–90`): `+ waveform: List<Float>? = null`.
- `SessionIntent` (`:93–138`):
  - `OriginalSelected(val original: Boolean, val held: Boolean = false)`;
  - `+ data class ProblemNoteClicked(val note: Note)`;
  - `+ data object OpenPieceClicked`.
- `SessionEffect` (`:140–149`): `+ data class OpenPiece(val pieceId: Long)`.
- `SoundRow` (`:169`): `+ val processed: Boolean` — обработка что-то делает по настройкам записи, известно до готовности плеера.

**`S/feature/session/SessionContentMapper.kt`**
- В `contentOf` (`:16–64`) заполнить `sameNoteCount` (число сегментов той же ноты), `hasVideo = summary.videoPath != null`, `nearCents = config.nearCents.roundToInt()`.
- Новая чистая функция: `fun worstSegmentOf(note: Note, segments: List<RollSegment>): Int?` — наибольший |среднее|, при равенстве — самый длинный, дальше — первый.

**`S/feature/session/PianoRollMath.kt`** (рядом с `:48–52`)
- `fun scrollToShow(startMs, endMs, scroll): Float?`: `null`, если столбик виден целиком; иначе `(x(start) - viewport * CURSOR_MIN).coerceIn(0f, maxScroll)`.
- `fun rowScrollToShow(row, scrollDp, viewportHeight): Float?` — для лент больше 12 строк.

**Новый `S/feature/sound/OriginalHold.kt`** — D10.

**`S/feature/session/SessionViewModel.kt`**
- Конструктор (`:39–53`): `+ private val waveforms: SessionWaveforms` перед `compute`.
- `onIntent` (`:84–134`):
  - `OriginalSelected` → `hold.select(player, original, held)`;
  - `ProblemNoteClicked` → `selectedSegment = SessionContentMapper.worstSegmentOf(...)`;
  - `OpenPieceClicked` → `content.pieceId?.let { effect OpenPiece(it) }`.
- `load()` (`:156–175`): `underBacking = backings.takeBackings.first().any { it.sessionId == sessionId }` в `content.copy(...)` — строка минусовки известна независимо от плеера и файла звука.
- `startPlayer()` (`:238–277`):
  - новая корутина `waveforms.of(file)?.toList()` → `waveform`;
  - в `combine` (`:256–263`) — `SoundRow(caption, own, processed = !SoundRules.isNeutral(effective.settings))`.

**`S/feature/session/SessionRoute.kt`** — `+ onOpenPiece: (Long) -> Unit`, обработка эффекта.

**`A/feature/session/HiltSessionViewModel.kt:20–33`** — `+ waveforms: SessionWaveforms` в конструктор и в позиционный вызов.

**Навигация**
- `A/navigation/AppNavHost.kt:158–165` → `onOpenPiece = navController::navigateToPieceOf`. Новый помощник:
  ```kotlin
  fun NavHostController.navigateToPieceOf(pieceId: Long) {
      if (!popBackStack(Routes.piece(pieceId), inclusive = false)) navigateToPiece(pieceId)
  }
  ```
  Navigation 2.10: `popBackStack` по маршруту с подставленными аргументами ищет ровно этот экран.
- `I/IosNavHost.kt:166–179`: то же и `waveforms = graph.waveforms`.

### 3.3 UI

**Новый `S/feature/sound/components/PlayerRows.kt`** — сюда переезжает `SeekWave` из `MiniPlayer.kt:146–209`; `MiniPlayer.kt` пока зовёт его отсюда.
- `object PlayerDockMetrics { val Regular = DockMetrics(12.dp, 16.dp, 12.dp, 56.dp); val Compact = DockMetrics(8.dp, 16.dp, 10.dp, 48.dp); fun compact(windowHeight) = windowHeight < DockMetrics.LowBelow }`.
- `PlayRow(player, position, waveform, compact, onPlayPause, onSeek, timeTrailing: (@Composable () -> Unit)? = null, trailing: @Composable RowScope.() -> Unit = {})`:
  - «play» 56 / 48, значок 26 / 22 `onPrimary`;
  - волна 34 / 24: столбики 2 через 1, пройденное — `primary`, остальное — `textTertiary` на 55 %;
  - время 12 sp, 600, `tnum`, сверху 2: позиция слева, длительность справа.
- `PreparingRow(compact)`: круг `surfaceContainerHigh` размером с «play», крутилка 22 (обод 3, `primary` и `primary` на 25 %), «Готовим минусовку…» 15 sp, 800; `liveRegion` один раз.
- `SoundLine(row, processed, onClick)`: 52, скругление 14, фон `colorScheme.surface`; значок 18 (`primary`, если обработка работает); «Звук» 14 sp, 700 и « · подпись» 600 `onSurfaceVariant` одной строкой с многоточием; шеврон 18 `textTertiary`. Подпись — логика `SessionScreen.kt:588–593`.
- `SoundIconButton(row, processed, onClick)`: 24 в цели 48, `contentDescription = sound_session_icon`.
- `AbSegment(original, onOriginal(original, held), compact, words: Boolean, enabled)` — на `SegmentedSwitch(container = surface, strong = true, prefixes/descriptions, onHold, compact)`.
- `BackingSegment(heard, onHeard, compact, enabled)` — на `SegmentedSwitch`; заменяет `BackingHeardSwitch` (`BackingControls.kt:55–88`).

**Новый `S/feature/session/components/RecordPlayer.kt`**
- `DockScope.RecordPlayer(state: Loaded, position, compact, onIntent)`.
- Обычный вид:
  - ряд 1 — `PlayRow` или, при `preparingBacking`, `PreparingRow`;
  - `Column(spacedBy(8))`: ряд 2 — `AbSegment(width 96)` при `(player?.processed ?: sound?.processed) == true` плюс `SoundLine(weight 1f)` при `sound != null`; ряд 3 — `BackingSegment` при `player?.hasBacking == true || (preparingBacking && content.underBacking)`.
- Компактный вид: ряд 1 — «play» 48, волна 24 с временем, компактный A/B, `SoundIconButton`; ряд 2 — компактная минусовка.
- Во время подготовки A/B и минусовка — `alpha(0.38)` и `enabled = false`; строка «Звук» работает.

**`S/feature/session/components/SessionCards.kt`** — переписать. Карточки — `surfaceContainer`, скругление 18, поля 16.
- `SummaryCard(content, compact: Boolean)`:
  - балл 60 sp, 800, −0,04 em, `tnum` и «%» 26 sp, 700 `onSurfaceVariant`; справа по нижнему краю через 16 — «в строе» и `session_summary_meta` 13 sp;
  - полоса долей 10, скругление 6, зазор 2, сверху 16, цвета `zoneColors`;
  - легенда `FlowRow`: точка 8, слово 13 sp `onSurfaceVariant` и число 700 `onSurface`, зазор 14;
  - черта 1 `outlineVariant` с отступом 14; значок 22 цвета `biasZone` (`ArrowDown` / `ArrowUp`, без смещения — точка 8 `inTune`); строка `session_bias_*_line` 15 sp, 700; `session_advice_line` 13 sp;
  - компактный вид (landscape): поля 12 / 16, балл 44 sp, «%» 22 sp, полоса сверху 10, значок 20 и отступ 8 без черты, `session_advice_line_short`;
  - `clearAndSetSemantics` — абзац из видимых строк (D3).
- `NotesCard(content, selectedSegment, onSegment, cursor, follow)`: `SectionLabel("Ноты")` и справа `session_notes_hint` / `session_notes_hint_violin` 13 sp, дальше `PianoRoll`.
- `DriftCard(notes, onNote)`:
  - строки ≥ 56 с разделителем 1: стрелка 20 цвета зоны, `session_problem_note` 15 sp, 600, `session_cents_value` 14 sp, 800 цвета зоны, шеврон 24 `textTertiary`;
  - пусто — строка `session_no_problem_notes` без шеврона.
- `StringsCard(content)`: четыре клетки на `colorScheme.surface`, скругление 12, поля 8 / 0, зазор 8; буква 12 sp, 700 и процент 16 sp, 800 цвета зоны; «—».
- `SessionStatCards` и `ProblemNotes` удаляются.

**`S/feature/session/components/PianoRoll.kt`**
- `:53` — скругление 18.
- Новый слот `label: (@Composable () -> Unit)?` над делениями.
- `LaunchedEffect(selectedSegment, math)` → `scrollToShow` и `rowScrollToShow` — прокрутка к сегменту «Что уходит».

**`S/feature/session/components/VideoBlock.kt`**
- `:59` и `:138–152`: «на весь экран» — квадрат 48 `AppShapes.S`, фон `ViolinTheme.glass`, значок 24 `onSurface`, 8 от угла.
- `:122–137`: в ожидании — только затемнение, без крутилки и надписи.
- `VideoMissingRow` (`:192–209`) → `DashedPlate(icon, title?, text)`: пунктир 1,5 `outlineVariant`, скругление 14, поля 12 / 14, значок 24 `onSurfaceVariant` (`Info` у строки без звука, `VideoOff` у видео); строка без звука 14 sp; у видео заголовок 15 sp, 700 и пояснение 13 sp.

**`S/feature/session/components/VideoScreens.kt`**
- Строка сжатия (`:181–215`): балл 22 sp, 800 и «%», дальше `video_row_flat` / `_sharp` / `_none`.
- `:282–331`: подложки панели — сплошное `glass`, текст `onSurface` (D12).

**`S/feature/session/components/NoteSheet.kt`** — переписать.
- `NoteSheet` → `AppSheet(value = segment, onHide = NoteSheetDismissed, contentPadding = 20)`.
- `NoteSheetContent`:
  - нота `note.name` 48 sp, 800, −0,03 em; «в среднем −22 ц» 17 sp, 800 цвета зоны со стрелкой 20 (в строе — точка), зазор 14;
  - строка `session_note_on_string` · `session_note_times_*` 13 sp;
  - три плитки: фон `surface`, скругление 14, поля 10, подпись 12 sp, 600, число 17 sp, 800, сверху 14;
  - совет: плашка `surfaceContainerHigh`, скругление 14, поля 12 / 14, `Info` 20 цвета зоны, первая фраза 700 `onSurface` + `_more` `onSurfaceVariant`, 14 sp;
  - `AppSheetButtons(video_listen_place | video_watch_place, mainIcon = PlayCircle, mainEnabled = !preparing, mainReason = backing_preparing?)`; без звука — без кнопки.

**`S/feature/session/SessionScreen.kt`**
- `TopBar` (`:317–401`):
  - высота 56 (landscape 48), название 17 sp, 800;
  - подзаголовок всегда (12 sp, 600 `tnum`): `session_video_subtitle` / `session_take_subtitle` / `record_meta`, при `underBacking` — значок `Backing` 14 впереди и «под минусовку» для TalkBack;
  - звезда, «Поделиться» (при `player != null`) и «⋯» с `AppMenu`: «Переименовать» (`Pencil`), «К произведению» (`AppIcons.TabRepertoire.normal`, при `pieceId != null`), опасный пункт `MenuDanger(card_menu_delete, caption = sizeLineOf(video), captionStrong = sizeBytes >= ShareInfo.LARGE_BYTES)`.
- `LoadedContent` (`:195–312`):
  - **Портрет:** при звуке, который играется, — `AppDock(dock = { RecordPlayer(...) }, fade = 0, ground = surfaceContainer, shape = top24, metrics = PlayerDockMetrics...)`; внутри `Column(padding bottom = LocalDockInset)` → прокрутка: пунктирная плашка первой, видео (`StickyVideo`, ≤ 40 %), итог, ноты, «Что уходит», «По струнам»; между карточками 12. Без звука, со звуком, который не играется, и с потерянным видео — без нижней зоны, пунктир первым.
  - **Landscape** (`twoColumns = w > h && hasAudio && !soundFailed && video?.lost != true`): шапка 48 во всю ширину, `Row`:
    - левая `min(w/2, 456)`: `AppDock(fade = 16 у звука / 0 у видео)`; у звука — прокрутка с `SummaryCard(compact)`, у видео — кадр высотой с остаток (`VideoLayoutMath.fit(aspect, width, maxHeight)` в `BoxWithConstraints`) на `videoColors.field`, у непоказываемого — пунктир и сжатый итог;
    - правая прокручивается: у видео первым итог, затем ноты, «Что уходит», «По струнам»;
    - иначе — одна колонка ≤ 560.
  - `NoteSheet` (`:299–306`) — через `AppSheet`.
- Удалить: `Summary` (`:403–461`), `PlayerAndSound` (`:463–482`), `Actions` / `Action` (`:491–538`), `SoundEntry` (`:569–602`), `SilentLine` (`:604–611`), `LANDSCAPE_VIDEO_SHARE`, `ScoreStyle`.
- Приватный `DeleteDialog` (`:558–567`) → `RecordDeleteDialog`.

**`S/feature/share/ShareContract.kt`** — `ShareInfo.companion LARGE_BYTES` (D21).

### 3.4 Строки

| Ключ | ru \| en \| de \| fr \| es \| it \| pt \| ja \| ko \| zh |
|---|---|
| `session_video_subtitle` | видео · %1$s · %2$s \| video · … \| Video · … \| vidéo · … \| vídeo · … \| video · … \| vídeo · … \| 動画 · … \| 영상 · … \| 视频 · … |
| `session_menu_piece` | К произведению \| To the piece \| Zum Stück \| Aller au morceau \| Ir a la obra \| Vai al brano \| Ir para a peça \| 曲へ移動 \| 곡으로 이동 \| 前往曲目 |
| `session_summary_in_tune` | в строе \| in tune \| sauber \| juste \| afinado \| intonato \| afinado \| 合っています \| 정확 \| 准 |
| `session_summary_meta` | %1$s · допуск ±%2$d ц \| %1$s · tolerance ±%2$d c \| %1$s · Toleranz ±%2$d ct \| %1$s · tolérance ±%2$d cts \| %1$s · tolerancia ±%2$d c \| %1$s · tolleranza ±%2$d c \| %1$s · tolerância ±%2$d c \| %1$s · 許容範囲 ±%2$d セント \| %1$s · 허용 범위 ±%2$d센트 \| %1$s · 容差 ±%2$d 音分 |
| `session_legend_near` | рядом \| near \| knapp \| proche \| cerca \| vicino \| perto \| 近い \| 근접 \| 接近 |
| `session_legend_off` | мимо \| off \| daneben \| à côté \| fuera \| fuori \| fora \| 外れ \| 벗어남 \| 偏离 |
| `session_bias_flat_line` | В среднем ниже на %1$d ц \| %1$d c flat on average \| Im Schnitt %1$d ct zu tief \| En moyenne %1$d cts trop bas \| De media, %1$d c bajo \| In media calante di %1$d c \| Em média %1$d c abaixo \| 平均で %1$d セント低い \| 평균 %1$d센트 낮음 \| 平均偏低 %1$d 音分 |
| `session_bias_sharp_line` | В среднем выше на %1$d ц \| %1$d c sharp on average \| Im Schnitt %1$d ct zu hoch \| En moyenne %1$d cts trop haut \| De media, %1$d c alto \| In media crescente di %1$d c \| Em média %1$d c acima \| 平均で %1$d セント高い \| 평균 %1$d센트 높음 \| 平均偏高 %1$d 音分 |
| `session_bias_none_line` | Без смещения \| No bias \| Keine Abweichung \| Pas de décalage \| Sin desviación \| Nessuno scostamento \| Sem desvio \| ずれなし \| 편차 없음 \| 无偏差 |
| `session_advice_flat` | Пальцы чуть ближе к подставке \| Fingers a little closer to the bridge \| Finger etwas näher zum Steg \| Doigts un peu plus près du chevalet \| Dedos un poco más cerca del puente \| Dita un po\' più vicine al ponticello \| Dedos um pouco mais perto do cavalete \| 指を少し駒寄りに \| 손가락을 브리지 쪽으로 조금 \| 手指稍靠近琴码 |
| `session_advice_sharp` | Пальцы чуть ближе к порожку \| Fingers a little closer to the nut \| Finger etwas näher zum Sattel \| Doigts un peu plus près du sillet \| Dedos un poco más cerca de la cejuela \| Dita un po\' più vicine al capotasto \| Dedos um pouco mais perto da pestana \| 指を少しナット寄りに \| 손가락을 너트 쪽으로 조금 \| 手指稍靠近上弦枕 |
| `session_advice_none` | В среднем ровно по центру \| Right in the centre on average \| Im Schnitt genau in der Mitte \| En moyenne pile au centre \| De media, justo en el centro \| In media proprio al centro \| Em média bem no centro \| 平均でちょうど中央 \| 평균적으로 정확히 가운데 \| 平均正好居中 |
| `session_advice_line` | %1$s. Средняя ошибка — %2$s ц. \| %1$s. Mean error — %2$s c. \| %1$s. Mittlerer Fehler — %2$s ct. \| %1$s. Erreur moyenne : %2$s cts. \| %1$s. Error medio: %2$s c. \| %1$s. Errore medio — %2$s c. \| %1$s. Erro médio — %2$s c. \| %1$s。平均誤差 %2$s セント。 \| %1$s. 평균 오차 %2$s센트. \| %1$s。平均误差 %2$s 音分。 |
| `session_advice_line_short` | %1$s · ошибка %2$s ц \| %1$s · error %2$s c \| %1$s · Fehler %2$s ct \| %1$s · erreur %2$s cts \| %1$s · error %2$s c \| %1$s · errore %2$s c \| %1$s · erro %2$s c \| %1$s · 誤差 %2$s セント \| %1$s · 오차 %2$s센트 \| %1$s · 误差 %2$s 音分 |
| `session_notes_label` | Ноты \| Notes \| Töne \| Notes \| Notas \| Note \| Notas \| 音 \| 음 \| 音符 |
| `session_notes_hint` | тап — подробно \| tap for details \| tippen — Details \| touchez : détails \| toca: detalles \| tocca — dettagli \| toque — detalhes \| タップで詳細 \| 탭하면 자세히 \| 点按查看详情 |
| `session_notes_hint_violin` | только скрипка · тап — подробно \| violin only · tap for details \| nur Geige · tippen — Details \| violon seul · touchez : détails \| solo violín · toca: detalles \| solo violino · tocca — dettagli \| só violino · toque — detalhes \| ヴァイオリンのみ · タップで詳細 \| 바이올린만 · 탭하면 자세히 \| 仅小提琴 · 点按查看详情 |
| `session_drift_title` | Что уходит \| What drifts \| Was abdriftet \| Ce qui dérive \| Lo que se desvía \| Cosa sfugge \| O que escapa \| ずれていく音 \| 벗어나는 음 \| 跑音的地方 |
| `session_drift_open` | подробно \| details \| Details \| détails \| detalles \| dettagli \| detalhes \| 詳細 \| 자세히 \| 详情 |
| `video_row_flat` | в строе · ниже на %1$d ц \| in tune · %1$d c flat \| sauber · %1$d ct zu tief \| juste · %1$d cts trop bas \| afinado · %1$d c bajo \| intonato · calante di %1$d c \| afinado · %1$d c abaixo \| 合っています · %1$d セント低い \| 정확 · %1$d센트 낮음 \| 准 · 偏低 %1$d 音分 |
| `video_row_sharp` | в строе · выше на %1$d ц \| in tune · %1$d c sharp \| sauber · %1$d ct zu hoch \| juste · %1$d cts trop haut \| afinado · %1$d c alto \| intonato · crescente di %1$d c \| afinado · %1$d c acima \| 合っています · %1$d セント高い \| 정확 · %1$d센트 높음 \| 准 · 偏高 %1$d 音分 |
| `video_row_none` | в строе · без смещения \| in tune · no bias \| sauber · keine Abweichung \| juste · pas de décalage \| afinado · sin desviación \| intonato · nessuno scostamento \| afinado · sem desvio \| 合っています · ずれなし \| 정확 · 편차 없음 \| 准 · 无偏差 |
| `session_note_on_string` | на струне %1$s · %2$s \| on the %1$s string · %2$s \| auf der %1$s-Saite · %2$s \| sur la corde %1$s · %2$s \| en la cuerda %1$s · %2$s \| sulla corda %1$s · %2$s \| na corda %1$s · %2$s \| %1$s線 · %2$s \| %1$s현 · %2$s \| %1$s 弦 · %2$s |
| `session_note_times_one / few / many` | %1$d раз / раза / раз за запись \| %1$d time / times / times in the recording \| %1$d-mal in der Aufnahme (все три) \| %1$d fois dans l\'enregistrement (все три) \| %1$d vez / veces / veces en la grabación \| %1$d volta / volte / volte nella registrazione \| %1$d vez / vezes / vezes na gravação \| 録音中 %1$d 回 \| 녹음 중 %1$d번 \| 录音中 %1$d 次 |
| `sound_session_icon` | Звук: %1$s \| Sound: %1$s \| Klang: %1$s \| Son : %1$s \| Sonido: %1$s \| Suono: %1$s \| Som: %1$s \| サウンド：%1$s \| 사운드: %1$s \| 声音：%1$s |

Советы. Прежние ключи получают новые значения (первая фраза), вторая фраза — новый ключ `_more`:

| Ключ | ru \| en \| de \| fr \| es \| it \| pt \| ja \| ko \| zh |
|---|---|
| `session_tip_flat` | Палец стоит низко. \| The finger sits low. \| Der Finger sitzt zu tief. \| Le doigt est trop bas. \| El dedo está bajo. \| Il dito è basso. \| O dedo está baixo. \| 指が低めです。 \| 손가락이 낮습니다. \| 手指偏低。 |
| `session_tip_flat_more` | Чуть ближе к подставке — и нота сядет. \| A little closer to the bridge and the note will settle. \| Etwas näher zum Steg — dann sitzt der Ton. \| Un peu plus près du chevalet, et la note tombera juste. \| Un poco más cerca del puente y la nota quedará en su sitio. \| Un po\' più vicino al ponticello e la nota andrà a posto. \| Um pouco mais perto do cavalete e a nota se acerta. \| 少し駒寄りにすれば音が収まります。 \| 브리지 쪽으로 조금 옮기면 음이 자리를 잡습니다. \| 稍靠近琴码，音就准了。 |
| `session_tip_sharp` | Палец стоит высоко. \| The finger sits high. \| Der Finger sitzt zu hoch. \| Le doigt est trop haut. \| El dedo está alto. \| Il dito è alto. \| O dedo está alto. \| 指が高めです。 \| 손가락이 높습니다. \| 手指偏高。 |
| `session_tip_sharp_more` | Чуть ближе к порожку — и нота сядет. \| A little closer to the nut and the note will settle. \| Etwas näher zum Sattel — dann sitzt der Ton. \| Un peu plus près du sillet, et la note tombera juste. \| Un poco más cerca de la cejuela y la nota quedará en su sitio. \| Un po\' più vicino al capotasto e la nota andrà a posto. \| Um pouco mais perto da pestana e a nota se acerta. \| 少しナット寄りにすれば音が収まります。 \| 너트 쪽으로 조금 옮기면 음이 자리를 잡습니다. \| 稍靠近上弦枕，音就准了。 |
| `session_tip_wandering` | В среднем в строе, но нота плавает. (прежний текст + точка; переводы тоже с точкой) |
| `session_tip_wandering_more` | Размах больше %1$d ц — ровной её не назвать. \| The range is over %1$d c — not an even note yet. \| Die Spanne liegt über %1$d ct — noch kein ruhiger Ton. \| L\'amplitude dépasse %1$d cts : la note n\'est pas encore stable. \| El rango supera los %1$d c: aún no es una nota estable. \| L\'escursione supera %1$d c: non è ancora una nota ferma. \| A oscilação passa de %1$d c — ainda não é uma nota estável. \| 揺れ幅が %1$d セントを超えています — まだ安定した音とは言えません。 \| 흔들림이 %1$d센트를 넘습니다 — 아직 고른 음이 아닙니다. \| 摆动超过 %1$d 音分——还不算稳。 |
| `session_tip_stable` | Стабильно, вибрато ровное. (прежний текст + точка) |

Новые значения прежних ключей:

| Ключ | ru \| en \| de \| fr \| es \| it \| pt \| ja \| ko \| zh |
|---|---|
| `sound_ab_original` | A, оригинал \| A, original \| A, Original \| A, original \| A, original \| A, originale \| A, original \| A、オリジナル \| A, 원본 \| A，原声 |
| `sound_ab_processed` | B, обработка \| B, processed \| B, bearbeitet \| B, traité \| B, procesado \| B, elaborato \| B, processado \| B、加工 \| B, 처리 \| B，处理后 |
| `backing_heard_with` | С минусовкой \| With backing \| Mit Begleitung \| Avec accomp. \| Con acompañamiento \| Con la base \| Com playback \| 伴奏つき \| 반주와 함께 \| 带伴奏 |
| `backing_heard_violin` | Только скрипка \| Violin only \| Nur Geige \| Violon seul \| Solo violín \| Solo violino \| Só violino \| ヴァイオリンのみ \| 바이올린만 \| 仅小提琴 |

Удалить во всех языках: `session_meta`, `session_bias_none`, `session_bias_hint_none`, `session_bias_hint_flat`, `session_bias_hint_sharp`, `session_card_distribution`, `session_card_mean_error`, `session_bias_small_none`, `session_bias_small_down`, `session_bias_small_up`, `session_problem_notes`, `session_note_target`, `session_note_mean`, `session_note_position`, `video_row_time`, `session_action_delete`.

Остаются: `session_bias_mean` (D4), `session_action_rename`, `session_card_per_string`, `video_size`, `video_size_lost`.

### 3.5 Тесты

**commonTest:**
- Новый `T/feature/session/SessionContentMapperTest.kt`: `worstSegmentOf` (наибольший |среднее|; при равенстве — длиннее; чужие ноты не считаются; ноты нет — `null`); `sameNoteCount`; `hasVideo`; `nearCents` берётся из записи.
- `T/feature/session/PianoRollMathTest.kt`: `scrollToShow` (виден — `null`, ставит на `CURSOR_MIN`, зажим у концов) и `rowScrollToShow`.
- Новый `T/feature/sound/components/PlayerDockMetricsTest.kt`: ниже 700 — компактный, 700 и выше — обычный.

**app/test:**
- `AT/feature/session/SessionViewModelTest.kt` — в конструктор `:134` передать `FakeSessionWaveforms()`. Новые тесты:
  - волна считается из файла, а у записи без звука её нет;
  - строка «Что уходит» открывает сегмент, где нота уходит сильнее всего;
  - «К произведению» — у дубля с произведением, у свободной записи и у записи удалённого произведения его нет;
  - A, удерживаемое, играет оригинал только пока держат;
  - `underBacking` известен до готовности плеера;
  - `SoundRow.processed` известен до готовности плеера;
  - `hasVideo`.
- `AT/core/ui/icons/AppIconsTest.kt`: `ArrowUp` и `ArrowDown` — залитые формы из `AppIcons.all`.
- Остаются зелёными: `PianoRollMathTest`, `VideoLayoutMathTest`, `SessionAnalyzerTest`, `SoundViewModelTest` (A/B через `OriginalHold`).

**androidTest (только компиляция):**
- `SegmentedSwitchTest`: `onHold` — нажатие даёт `(0, true)`, отпускание `(0, false)`; `descriptions`; компактный — 48.
- `PianoRollTapTest` — без правок.

### 3.6 Превью

**`A/feature/session/SessionScreenPreviews.kt`** — все состояния:
- звуковой дубль;
- свободная запись;
- видео и видео при прокрутке (`scrolledPx` задать);
- «Готовим минусовку»;
- под минусовку;
- без звука; потерянное видео; непоказываемое;
- «Что уходит» пусто;
- компактный плеер 360 × 640;
- landscape: звук 892 × 412, видео, 640 × 360 при 1,3 (итог прокручивается над плеером);
- лист ноты в `AppSheetCard`: звук, видео, без звука, во время подготовки.

**`A/core/ui/components/ComponentsPreviews.kt`:**
- меню с подписью и с большим файлом;
- сегменты: A/B на панели, крупный A/B со словами, компактный, две строки 12 sp.

### 3.7 Проверка на эмуляторе

- Прокрутка портрета: плеер не закрывает ленту.
- «Что уходит» → лист: лента прокручена, нота обведена.
- A/B удержанием.
- «К произведению»: из «Записей» открывает экран произведения; из произведения → дубль → «К произведению» возвращает на тот же экран, второго не открывая, и «назад» ведёт дальше по стеку.
- «Удалить…» с размером видео (≥ 100 МБ — положить большой файл через `run-as`).
- «Готовим минусовку…»: сброс кэша минусовки, крутилка одна на экране.
- Потерянное видео; landscape обоих размеров; компактный плеер на 360 × 640; шрифт 1,3; de и fr.
- На iOS сообщение «Отмечен как лучший» стоит над плеером.

Коммит: `feat(records): the recording with its player at the bottom, one summary card and «Что уходит» that opens the note (stage 112, spec 3.36.5)`.

---

## 4. Этап 113 — «Звук», «Поделиться» и проверка R5

### 4.1 «Звук»

**`S/feature/sound/SoundContract.kt`**
- `SoundState` (`:57–99`):
  - `expanded: Set<SoundCard> = emptySet()`;
  - `recordings: List<HistoryCard>`;
  - `+ today: LocalDate`.
- `SoundCard` (D13).
- `BackingBlockState` (`:101–107`): `+ title: String`, `+ durationMs: Long`, `+ recordedWith: RecordedWith?`.
- `sealed interface RecordedWith { data class Wireless(val name: String, val latencyMs: Int); data object Wired }`.
- `object RecordedWithRule { fun of(output: BackingOutput, deviceName: String?, latencyMs: Int): RecordedWith? }`:
  - `WIRED` и `USB` → `Wired`;
  - `BLUETOOTH` с именем → `Wireless`;
  - без имени и `SPEAKER` → `null`.
- `SoundIntent`: `BlockHeaderClicked` (`:140`) → `CardToggled(card: SoundCard)`.

**`S/feature/sound/SoundViewModel.kt`**
- Конструктор (`:53–68`): `+ clock: WallClock`.
- `:157` → переключение карточки в множестве.
- `loadBacking` (`:202–208`): `title`, `durationMs` из `backing`; `recordedWith` из `found.output`, `deviceName`, `latencyMs`.
- `follow()` (`:234–259`): `combine(…, backings.takesUnderBacking)`; `recordings = playable.map { HistoryReducer.cardOf(it, today, zone, pieceTitle, best = it.id in bestIds, underBacking = it.id in under) }`; `today = clock.today()`.
- `selectOriginal` (`:346–361`) → `OriginalHold`.

**Подключение модели**
- `A/feature/sound/HiltSoundViewModel.kt`: `+ clock: WallClock`.
- `I/IosNavHost.kt:195–200`: `clock = graph.clock`.

**`S/feature/sound/SoundScreen.kt`** — переписать `:116–544`.

`TopBar`:
- высота 56 (landscape 48), заголовок «Звук» (`sound_session_row`) / «Звук записей» 17 sp, 800;
- подзаголовок: у записи `Row(Text(name, weight(fill = false), многоточие), Text(" · $mode"))`, перед ним значок видео, если он был; у «Звука записей» — `sound_affected_*`, а при нуле — `sound_everyone_all`;
- справа «Поделиться» (запись и `player != null`) или `AppButton(Text, "Сбросить", enabled = canReset)`.

`Scope`:
- `SegmentedSwitch(shrinkToTwoLines = true)` «Как у всех | Свои для записи» и подпись 12 sp;
- у «Звука записей» — `ListenOnRow`: ≥ 56, `surfaceContainer`, скругление 18, поля 8 / 14, снизу 14; `RecordTile` 40; подпись «Слушать на» (с « · последняя со звуком») 13 sp; название 15 sp, 700; `AppButton(Text, "Другая")` при `recordings.size > 1`;
- записей нет — `sound_listen_none`.

`Presets`:
- `Row(horizontalScroll, selectableGroup, spacedBy(8))`, сверху 12, снизу 14;
- «Свои» — `Choice(selected = true)`; пресеты — `Choice(onLongClick)`; «Сохранить как пресет» — `Choice(selected = null, enabled = custom)`.

`Blocks`: убрать `sound_order` (`:436–440`); `SoundBlocks(expanded = set)` и `BackingCard`.

Нижняя зона: `AppDock(fade 0, ground = surfaceContainer, shape = top24, metrics)`:
- ряд 1 — `PlayRow` или `PreparingRow`;
- `AbSegment(words = true)` во всю ширину: «A оригинал | B обработка», выключен, если обработка ничего не делает; в подготовке — 0,38, если `!SoundRules.isNeutral(settings)`;
- через 8 — `BackingSegment`;
- индикатор выхода через 10; в компактном виде — справа от времени (`timeTrailing`).

Раскладка:
- **Портрет:** шапка, дальше прокрутка (`Scope`, `Presets`, `Blocks`) с `padding(bottom = LocalDockInset)`.
- **Landscape:** левая 340 — `AppDock` { шапка 48 + прокрутка пресетов }, правая прокрутка — `Scope` и `Blocks`.
- Нет плеера и нет подготовки — без нижней зоны.

`ShareButton` (`:446–462`) удалить.

`PickRecording` (`:506–539`) → `AppSheet(value = dialog as? PickRecording, onHide = DialogDismissed, scroll = false)`: `SectionLabel(sound_listen_on)`; `LazyColumn`: `DayHeader` и `RecordCard(place = Sheet, current = id == recording.sessionId, actions = null, onClick = RecordingPicked)`.

**`S/feature/sound/components/SoundBlocks.kt`** — `BlockCard` (`:205–282`)
- Скругление 18, между блоками 10.
- Шапка ≥ 64, поля 8 / 12 / 8 / 16: номер — круг 22 на `colorScheme.surface`, 11 sp, 800; название 16 sp, 800; краткое 13 sp, одна строка; `Switch`; шеврон 24.
- Выключенный — номер, название и значения `textTertiary`, без прозрачности.
- Содержимое — поля 0 / 16 / 16.
- `bringIntoView` оставить.
- Новый `BackingCard(state, unavailable, expanded, …)`: номер 5, без выключателя; краткое `title · Formats.duration(durationMs)`; при неудаче — без шеврона и не нажимается, под названием `backing_take_unprepared`.

**`S/feature/sound/components/BackingControls.kt`**
- `BackingBlock` (`:108–161`) → содержимое `BackingCard`: ползунки; «Как записано» — `AppButton(Soft)`; строка наушников 13 sp со значком `Headphones` 16, сверху 10.
- Удалить `BackingHeardSwitch` (заменён в 112), `BackingPreparingRow`, `BackingUnavailableBlock`.

**`S/feature/sound/components/ParamSlider.kt`**
- Константы `:76–81`: `RowHeight` 52, `TrackHeight` 6, `DefaultMark` 16 × 2 `textTertiary`, `StepButton`: видимые 36 со скруглением 10 на `colorScheme.surface` в цели 48.
- Ручка 20 белая с ореолом 4 `primary` на 35 %.
- Подпись 14 sp, 700; значение 14 sp, 800.

**`MiniPlayer.kt`** → `PlayerRows.kt` (git mv); `MiniPlayer`, `MiniPlayerMetrics`, `HoldableAb` удалить.

**`S/core/ui/components/AppChip.kt`** — `Choice(+ onLongClick: (() -> Unit)? = null, onLongClickLabel: String? = null)` через `combinedClickable` с ролью `RadioButton` и `selected`.

### 4.2 «Поделиться»

**`S/feature/share/ShareContract.kt`**
- `Preparing(info, variant: ShareVariant, percent, remainingSec)` (`:72`).
- `ShareInfo.extensionOf(variant) = "." + fileNameOf(variant).substringAfterLast('.')`.

**`S/feature/share/ShareViewModel.kt`**
- `Dismissed` (`:215–218`): `Preparing` и `Choose(busy)` — ничего; `Choose(!busy)` и `Failed` → `sheet = null`, без `job.cancel`.
- `showProgress(info, variant, …)` (`:259–262`) и `holdProgress(info, variant)` (`:373–378`).
- Вызовы в `writingAlone` (`:240`), `prepare` (`:302`, `:312`), `sendOriginal` / `sendProcessed` (`:269`, `:289`).

**`S/feature/share/ShareHost.kt`** (`:100–330`)
- `AppSheet(value = sheet, onHide = Dismissed, dismissible = !(sheet is Preparing || (sheet as? Choose)?.busy == true), keepHandleRoom = true)` и публичный `ShareSheetContent(sheet, onIntent, landscape)`.
- `Choose`:
  - заголовок 20 sp, 800;
  - `Variant`: ≥ 64 (landscape 56), поля 10 / 14, фон `surface`, скругление 18, рамка 1,5 (у выбранного — `primary` и фон `primary` на 10 %); радио 22 (рамка 2 `textTertiary`, выбранное — 6 `primary`); название 15 sp, 700; подпись 13 sp; чип формата у каждого (24, скругление 7, `surfaceContainerHigh`, 12 sp, 800) — `info.extensionOf(v)`;
  - подпись «Только звук» — `share_sound_with_processing` / `share_sound_as_recorded`;
  - строка файла: сверху 12, значок 24; ≥ `LARGE_BYTES` — 700 `dangerSoft` и `share_large_file`;
  - «Добавить текст»: строка ≥ 48, флажок 22 (скругление 6, `primary`), 14 sp, под ним `info.message` 13 sp;
  - `AppButton(Main, "Продолжить" | "Готовим…", enabled = !busy)`, сверху 14.
- `Preparing`:
  - `share_preparing` / `share_preparing_video` (видео, если `info.video && variant != SOUND`) и «42 %» 20 sp, 800 `primary`;
  - полоса 8, скругление 5, сверху 18;
  - строка «название варианта · .mp4 · осталось около N с» (без оценки — без «осталось»);
  - `AppButton(Outline, "Отмена")` 56 во всю ширину, сверху 16.
- `Failed`: плашка `surfaceContainerHigh`, скругление 14, `Alert` 24 `dangerSoft`, заголовок 15 sp, 700 и текст 14 sp; «Ещё раз» — `Main`, под ним «Отправить оригинал» / «Отправить как снято» — `Outline`, зазор 10.

**`S/core/ui/components/AppSheet.kt:68–77`, `:96`, `:105`** — `keepHandleRoom` (D8).

**Тема:** `Color.kt:94–95` и `VideoColors.kt` — D12.

### 4.3 Строки

| Ключ | ru \| en \| de \| fr \| es \| it \| pt \| ja \| ko \| zh |
|---|---|
| `sound_mode_own` (новое значение) | Свои для записи \| Own for the recording \| Eigene für die Aufnahme \| Propres à l\'enregistrement \| Propios de la grabación \| Proprie della registrazione \| Próprios da gravação \| この録音だけ \| 이 녹음만 \| 仅此录音 |
| `sound_listen_other` | Другая \| Another \| Andere \| Autre \| Otra \| Un\'altra \| Outra \| 別の録音 \| 다른 녹음 \| 换一条 |
| `sound_everyone_all` | для всех записей \| for all recordings \| für alle Aufnahmen \| pour tous les enregistrements \| para todas las grabaciones \| per tutte le registrazioni \| para todas as gravações \| すべての録音に \| 모든 녹음에 \| 适用于全部录音 |
| `sound_ab_original_word` | оригинал \| original \| Original \| original \| original \| originale \| original \| オリジナル \| 원본 \| 原声 |
| `sound_ab_processed_word` | обработка \| processed \| bearbeitet \| traité \| procesado \| elaborato \| processado \| 加工 \| 처리 \| 处理后 |
| `backing_recorded_in` | Записано в %1$s \| Recorded in %1$s \| Aufgenommen mit %1$s \| Enregistré avec %1$s \| Grabado con %1$s \| Registrato con %1$s \| Gravado com %1$s \| %1$s で録音 \| %1$s(으)로 녹음 \| 用 %1$s 录制 |
| `backing_recorded_in_latency` | Записано в %1$s · %2$s учтено \| Recorded in %1$s · %2$s allowed for \| Aufgenommen mit %1$s · %2$s berücksichtigt \| Enregistré avec %1$s · %2$s pris en compte \| Grabado con %1$s · %2$s compensados \| Registrato con %1$s · %2$s compensati \| Gravado com %1$s · %2$s compensados \| %1$s で録音 · %2$s 補正済み \| %1$s(으)로 녹음 · %2$s 보정됨 \| 用 %1$s 录制 · 已补偿 %2$s |
| `backing_recorded_wired` | Записано в проводных наушниках \| Recorded in wired headphones \| Mit kabelgebundenen Kopfhörern aufgenommen \| Enregistré avec un casque filaire \| Grabado con auriculares con cable \| Registrato con cuffie a filo \| Gravado com fones com fio \| 有線ヘッドホンで録音 \| 유선 헤드폰으로 녹음 \| 用有线耳机录制 |
| `share_preparing_video` | Готовим видео \| Preparing the video \| Video wird vorbereitet \| Préparation de la vidéo \| Preparando el vídeo \| Preparazione del video \| Preparando o vídeo \| 動画を準備しています \| 영상 준비 중 \| 正在准备视频 |
| `share_sound_with_processing` | с обработкой \| processed \| bearbeitet \| avec traitement \| procesado \| elaborato \| processado \| 加工あり \| 처리됨 \| 已处理 |
| `share_sound_as_recorded` | как записан \| as recorded \| wie aufgenommen \| tel qu\'enregistré \| tal como se grabó \| come registrato \| como foi gravado \| 録音したまま \| 녹음한 그대로 \| 原样 |
| `share_add_text` | Добавить текст \| Add text \| Text hinzufügen \| Ajouter le texte \| Añadir texto \| Aggiungi il testo \| Adicionar texto \| テキストを追加 \| 텍스트 추가 \| 附上文字 |

В `backing_recorded_in_latency` значение `%2$s` — это `SoundFormats.signedMs` («+200 мс»).

Удалить во всех языках: `sound_order`, `sound_everyone_subtitle`, `sound_dialog_pick_title` (заголовок листа — `sound_listen_on`), `share_with_text`, `share_sound_only_processed`, `share_sound_only_caption`.

### 4.4 Тесты

**commonTest:**
- Новый `T/feature/sound/RecordedWithRuleTest.kt`: беспроводные с именем и задержкой; с именем и нулём; проводные и USB; беспроводные без имени → `null`.
- Новый `T/feature/share/ShareInfoTest.kt`: чип формата у каждого варианта; `.mov` для «как снято» с iPhone.

**app/test:**
- `AT/feature/sound/SoundViewModelTest.kt` (в конструктор — `clock`):
  - при открытии все блоки свёрнуты, раскрытие одного другие не сворачивает, «Минусовка» сворачивается как все;
  - строка наушников из `take_backings`;
  - название и длительность минусовки в блоке;
  - карточки «Слушать на»: только те, что играются, новые сверху, с датой, «лучшим» и «под минусовку»;
  - прежние тесты правятся только там, где контракт сменила спека (`recordings`, `CardToggled`).
- `AT/feature/share/ShareViewModelTest.kt`:
  - `:366` — убрать часть «a closed sheet forgets its work»;
  - новые: смахивание во время подготовки её не останавливает, файл уходит; смахивание при «Готовим…» на кнопке — то же; смахивание выбора и ошибки только скрывает лист и ничего не отправляет; `Preparing.variant` — выбранный вариант (минусовка, видео с обработкой).

**androidTest (только компиляция):**
- `AppSheetTest`: `keepHandleRoom` — содержимое не сдвигается, когда лист начинает держаться.
- `ControlsTouchTest`: долгое нажатие на `Choice` зовёт `onLongClick`.

### 4.5 Превью

- Новый `A/feature/sound/SoundScreenPreviews.kt`: обзор (всё свёрнуто); эквалайзер; «Минусовка» со строкой наушников; «Минусовка» не удалась; «Звук записей»; «Звук записей» без записей; подготовка; компактный 360 × 640; landscape 892 × 412; лист «Слушать на…» в `AppSheetCard`.
- Новый `A/feature/share/ShareSheetPreviews.kt`: звук; видео с минусовкой (четыре варианта); большой файл; подготовка с «осталось» и без; ошибка звука; ошибка видео; landscape (строки 56).

### 4.6 Проверка R5 целиком — последний шаг

1. Сборка и тесты: `./gradlew :app:assembleDebug :app:compileDebugAndroidTestKotlin :app:testDebugUnitTest :shared:testAndroidHostTest :shared:iosSimulatorArm64Test :app:lintDebug`; `check.py` на девять тегов.
2. Снимки «после» — тот же список и те же режимы, что в 2.1, в папку `r5-after/`, в ту же дату; сравнивать попарно глазами.
3. `snap.py compare` для `live_play` — должно быть 0 %.
4. Сценарии:
   - удаление из «⋯»;
   - фильтры;
   - лист ноты;
   - «Готовим минусовку…»;
   - подготовка файла со смахиванием, «назад» и тапом мимо — лист держится, файл уходит;
   - «Отмена»;
   - «Слушать на…»: выбор и смахивание;
   - сброс «Звука записей»;
   - в режимах 892 × 412, 640 × 360, 360 × 640, при 1,3 и на de / fr.
5. Симулятор iOS: сборка по `CLAUDE.md`, данные с эмулятора; `-openRoute history`, `session/<звук>`, `session/<видео>`, `session/<минусовка>`, `session/<без звука>`, `'sound?sessionId=<id>'`, `'sound?sessionId=-1'`; снимки `simctl io booted screenshot`. Касаний `simctl` не умеет — меню, листы и сообщение над плеером смотреть на iPhone владельца с его согласия.
6. После проверки вернуть `accelerometer_rotation 1`, `user_rotation 0`, `wm size reset`, `wm density reset`, `font_scale 1.0`, язык.
7. Заметки в `docs/notes/redesign.md` — «Этапы 111–113 (R5)»: что сделано, решения D1–D22, отклонения, «Проверено» и «Не проверено» (TalkBack и VoiceOver на слух, телефон).

Коммиты:
- `feat(records): «Звук» with the player at the bottom and numbered blocks, «Поделиться» in one sheet that holds while it prepares (stage 113, spec 3.36.5)`;
- `docs: stage 113 is done and with it R5`.

---

## 5. Риски

- **Параллельная работа R2.** Конфликты в `strings.xml` ×10, `IconPaths`, `AppIcons`, `AccessibilitySemanticsTest`. Начинать после коммита R2; в `feature/practice/**` не входить (D6).
- **Смена `dismissible` у показанного `ModalBottomSheet`.** В M3 1.4 свойства окна (`shouldDismissOnBackPress`) могут не обновиться на лету. Проверить на эмуляторе «назад» во время подготовки. Если не держится — отдельный `BackHandler`, который съедает «назад», пока лист держится.
- **Расширение общих частей R1.** Затронуты `SegmentedSwitch`, `AppDock`, `AppSheet`, `AppMenu`, `AppChip`: все новые параметры с прежними умолчаниями. Прогнать `SegmentedSwitchTest`, `AppDockTest`, `AppSheetTest`, `ControlsTouchTest`, `AppDialogTest` на эмуляторе, а не только скомпилировать, — иначе можно сломать формы R4 и листы R3.
- **`popBackStack(Routes.piece(id))` с аргументами.** Опирается на поведение Navigation 2.10 (и JetBrains-навигации на iOS). Проверить руками путь «произведение → дубль → К произведению». Запасной вариант — сверка с `previousBackStackEntry`.
- **Landscape 640 × 360 с минусовкой и шрифтом 1,3.** Компактный плеер из двух рядов и сжатый итог могут не влезть. Итог прокручивается с затуханием 16 — проверить, что плеер не сжимается.
- **Волна для длинных записей и видео на Android.** Первый расчёт может быть долгим; до конца расчёта стоит обычный ползунок, это задумано.
- **TalkBack.** Абзацы собраны из видимых строк, а не звучат дословно, как в спеке (D3). Отметить в заметках; на слух не проверено.
- **Строки карточки в CJK.** Встроенный знак минусовки через `InlineTextContent` и многоточие — проверить превью на ja и zh.
- **Полноэкранное видео.** Меняется подложка панели (стекло вместо градиента): сверка «до / после» покажет разницу, это ожидаемо.

---

### Critical Files for Implementation
- /Users/danil/AndroidStudioProjects/ViolinTuner/shared/src/commonMain/kotlin/com/violinjourney/app/feature/history/HistoryScreen.kt
- /Users/danil/AndroidStudioProjects/ViolinTuner/shared/src/commonMain/kotlin/com/violinjourney/app/feature/history/components/SessionCard.kt
- /Users/danil/AndroidStudioProjects/ViolinTuner/shared/src/commonMain/kotlin/com/violinjourney/app/feature/session/SessionScreen.kt
- /Users/danil/AndroidStudioProjects/ViolinTuner/shared/src/commonMain/kotlin/com/violinjourney/app/feature/sound/SoundScreen.kt
- /Users/danil/AndroidStudioProjects/ViolinTuner/shared/src/commonMain/kotlin/com/violinjourney/app/feature/share/ShareHost.kt