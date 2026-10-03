# План · Видео с нотами (spec 3.37, 5.30; этапы 123–126)

Спека — `docs/spec/overlay.md`, макет — `docs/design/project/overlay/project/overlay.html`, идея и выбор владельца — `docs/ideas/video-overlay/` (B + D). Заметки по коду — `docs/notes/overlay.md`. Где план спорит со спекой, права спека.

## Решения владельца (03.10.2026)

- Из вариантов идеи — B «лента нот» и D «карточка итога».
- Android перекодирует картинку Media3 Transformer 1.9.0 (`media3-transformer`, `media3-effect`): та же версия, что у `media3-common` и `media3-muxer`, которые уже приносит CameraX 1.6.2 (Guava тоже уже в сборке), — APK больше примерно на 3 МБ. Свой MediaCodec + OpenGL отклонён: HDR и причуды кодеров.

## Что решено без владельца (поправимо одной строкой спеки)

1. «Видео с нотами» — **вариант** в «Поделиться», первым и выбранным, как «С минусовкой» в 3.32. Флажок «ноты на видео» отклонён: с «Видео как снято» он давал бы файл, который уже не «как снято», а у записи без обработки других видео-вариантов нет.
2. Итог — **3 с после конца видео** на остановленном последнем кадре, а не поверх последних 3 с: игра ничем не закрыта.
3. Плитка «Тактов» из идеи убрана: видео-дубли тактов не дают (3.23). Итог — **строками**, а не плитками: «↑ F#5 +24 ц» в треть колонки не влезало, а строки выдерживают немецкий и французский.
4. «Прошлый дубль» — только дубль **с тем же допуском** (иначе проценты несравнимы); худший результат без «−N» — без стыжения.
5. **Ярлык текущей ноты над чертой** — добавлен к B: при 11 u/с подпись не влезает уже в капсулу восьмой (≈ 3 u), без ярлыка ноту на быстром месте не назвать.
6. Бейдж — итоговый балл записи, не бегущий: бегущий процент в начале прыгает 0 → 100 → 67 и спорит с итогом.
7. Цвет капсулы — зона среднего отклонения ноты, как на ленте экрана записи; контура отклонения внутри нет (не читается на видео и спорит с подписью).
8. Пределы: запись до 15 минут, не больше 1080p, не больше 30 к/с, H.264, SDR. Вторая версия видео не хранится — кеш на час.
9. Вариант — у **любой записи с видео**: видео-дубли и видео событий (3.35).

## Как устроено

```
SessionDetails (сохранённый разбор) + дубли произведения + слова на языке интерфейса
        │  NotesOverlays.of(...)                                   — commonMain, чистый
        ▼
NotesOverlay (ноты, диапазон, итог, полоска)   NotesOverlayGeometry(w, h) — u, полоса, ярлык, бейдж, строки
        │                                              │
        └──────────────► NotesOverlayPainter (DrawScope + TextMeasurer) ◄── OverlayText (Manrope платформы)
                                 │ один рисунок на кадр
        ┌────────────────────────┴───────────────────────────┐
Android: MediaNotesVideoRenderer                       iOS: IosNotesVideoRenderer
  Media3: [видео без звука → последний кадр 3 с]          AVAssetReader (BGRA, BT.709)
  + OverlayEffect(BitmapOverlay ← painter)                → Skia прямо в CVPixelBuffer (painter)
  + Presentation ≤1080p, ≤30 к/с, HDR→SDR, H.264          → AVAssetWriter (H.264, preferredTransform)
  → VideoMuxer.splice со звуком варианта                   + звук сэмплами без перекодирования
        └────────────────────────┬───────────────────────────┘
                     ShareViewModel: вариант NOTES, кеш, прогресс, отмена
```

Звук варианта готовит существующий `SoundRenderer` во временный `.m4a` (`render` / `renderWithBacking`); без обработки звуком служит само видео — его дорожка копируется как есть.

## Этап 123 — домен и рисунок (общий модуль)

Новое, пакет `com.violinjourney.app.core.recording.overlay` (`shared/src/commonMain`):

- `NotesVideoConfig.kt` — все числа 5.30: доли u портрета и landscape (затемнение, полоса, отступы, бейдж), капсула, подпись и условие «влезает», непрозрачности, скорость 11 u/с, черта, ярлык (размеры, удержание 300 мс, проявление 150 мс), бейдж, итог (3000 мс, 400 мс, вуаль 0,9, колонка, строки, кегли), «лучшая нота» (1000 мс, ≥ 0,5), диапазон (± 1, не уже 8), выход (1080, 30 к/с, 0,08 бит/пиксель, 1–6 Мбит/с, звук 128 кбит/с по умолчанию), предел 15 мин, старт оценки времени 0,6, доли прогресса 0,10 / 0,95.
- `NotesOverlay.kt` — модель и сборка: `NotesOverlay(notes, lowMidi, highMidi, scorePercent, toleranceCents, title, best, drift, previous, ribbon, durationMs)`, `OverlayNote(midi, startMs, endMs, zone)`, `OverlayDrift(midi, meanCents, zone)`, `OverlayPrevious(scorePercent, gain)`; `NotesOverlays.of(details, takes, config, title)` — сегменты и проблемные ноты из `SessionAnalyzer` (как экран записи), зоны — `ZoneClassifier` с `config.forSession(summary)`; лучшая нота, прошлый дубль (то же произведение, раньше, тот же допуск, самый поздний; разница > 0). `currentAt(tMs)` с удержанием, `tagAlpha(tMs)` с проявлением.
- `core/domain/session/SessionRibbon.kt` — `of(samples, config): List<RecordingBar>`: отрезки «одна нота в одной зоне», без нот места не занимают — то же правило, что в `SessionRecorder.add`, но по сохранённым отсчётам (слияние длинных записей не нужно: рисуется на ~900 px, отрезки уже пикселя сливаются сглаживанием).
- `NotesOverlayGeometry.kt` — чистая геометрия в пикселях кадра (простые `Box`, без типов Compose): u, портрет или landscape, затемнение, полоса, x капсулы по времени, y по высоте, «подпись влезает», черта, ярлык по ширине текста, бейдж, колонка и строки итога.
- `NotesOverlayPainter.kt` — `DrawScope`-рисовальщик кадра: лента (затемнение, капсулы, подписи, черта, ярлык, бейдж) или итог (вуаль, название, балл, строка допуска, полоска, строки, стрелка, точка, «+N»). Цвета — сырые токены общего модуля (`ZoneInTune`/`ZoneNear`/`ZoneOff`, `Glass`): рисовальщик живёт вне композиции, `LiveTheme` ему недоступна. Текст — `TextMeasurer` (своя ЛРУ-память раскладок).
- `OverlayText.kt` — `FontFamily.Resolver` и Manrope платформы; `OverlayWords.kt` — слова, прочитанные `getString(Res.string…)` на языке интерфейса (`suspend load()`).
- Android: `OverlayText` даёт приложение (`createFontFamilyResolver(context)` и `Manrope` из `Type.kt`) через `@Provides`. iOS: `IosOverlayText` — байты `Res.font.manrope_variable` (`getFontResourceBytes`) → skiko `Font(identity, data, weight, variationSettings)` для 600/700/800, `createFontFamilyResolver()`.
- `@Preview` (app, `feature/share/NotesOverlayPreviews.kt`): лента в портрете, landscape и квадрате; итог полный, без прошлого дубля, без «уходящих»; на условной картинке, кадр 1080 × 1920 уменьшен.
- Тексты (ru + 9 языков по `docs/i18n-glossary.md`): `share_notes` «Видео с нотами», `share_notes_caption_backing` / `_processed` / `_original` («лента нот и итог в конце · с минусовкой / звук с обработкой / звук как записан»), `share_notes_too_long` (с числом минут — формы через `Formats.plural`), `overlay_badge` «в строе %1$d %%», `overlay_best_note` «Лучшая нота», `overlay_drift_none` «ничего», `overlay_previous_take` «Прошлый дубль»; повторно — `session_drift_title`, `session_summary_in_tune`, `session_summary_tolerance`, `session_cents_value`, `dot_separator`.
- Тесты `commonTest`: `NotesOverlayTest` (диапазон и минимум 8, удержание 300 мс и разрыв дольше, проявление ярлыка, лучшая нота — порог 1 с и 0,5, равенства; «ничего»; прошлый дубль — другой допуск, другое произведение, более поздний, отрицательная разница без «+»; запись без произведения), `SessionRibbonTest` (совпадает с полосой `SessionRecorder` на тех же показаниях), `NotesOverlayGeometryTest` (u, портрет / квадрат / landscape, x по времени, y по высоте, «влезает», ярлык не уже 10,24 u, бейдж, колонка min(…, 84 u), строки).

## Этап 124 — Android: картинка с нотами

- `gradle/libs.versions.toml`: `media3 = "1.9.0"` (комментарий: согласовано владельцем 03.10.2026; та же версия, что `media3-common`/`media3-muxer` у CameraX — поднимать вместе с CameraX), библиотеки `androidx-media3-transformer`, `androidx-media3-effect`, `androidx-media3-common`; `app/build.gradle.kts` — `implementation`. Сверить `:app:dependencies`: `media3-common` и `media3-muxer` остались 1.9.0.
- Общее: `NotesVideoRenderer.kt` — интерфейс `render(picture, sound, overlay, words, target, onProgress): Boolean`; `NotesVideoFormat.kt` — чистый выбор выхода (размер, чётные стороны, к/с, битрейт) и оценка размера файла, с тестом в `commonTest`.
- `app/src/main/java/com/violinjourney/app/core/recording/overlay/MediaNotesVideoRenderer.kt`:
  1. Зонд исходника (`MediaExtractor`): размер, поворот, к/с, длительность.
  2. Последний кадр — `MediaMetadataRetriever.getFrameAtTime(…, OPTION_CLOSEST)`, повёрнут, как показывает плеер (сверить стороны с поворотом), PNG в папке подготовки.
  3. Transformer на главном `Looper` (`withContext(Dispatchers.Main)` для старта и слушателя): `EditedMediaItemSequence` — видео (`setRemoveAudio(true)`, `FrameDropEffect` ≤ 30 к/с) и картинка последнего кадра (`setImageDurationMs(3000)`, `setFrameRate`); у композиции — `Presentation` до размера выхода и `OverlayEffect(NotesBitmapOverlay)`; `setHdrMode(HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_OPEN_GL)`; `DefaultEncoderFactory` с `VideoEncoderSettings.setBitrate` и запасным кодером; `setVideoMimeType(H264)`. Портрет кодируется повёрнутым (умолчание Transformer) — `VideoMuxer.splice` переносит поворот.
  4. `NotesBitmapOverlay : BitmapOverlay` — одна `Bitmap` размера выхода; на каждый `getBitmap(tUs)` — `eraseColor(0)` и рисовальщик через `CanvasDrawScope` (`generationId` меняется — Media3 перезаливает текстуру, проверено по исходникам 1.9.0); `t ≥` длительности видео — итог.
  5. Прогресс — `getProgress(ProgressHolder)` раз в 100 мс → 0,10…0,95; отмена — `cancel()`, недоделанное удаляется.
  6. `VideoMuxer.splice(картинка с нотами, звук варианта, target)` → 0,95…1.
- DI: модуль `core/di` — `NotesVideoRenderer` и `OverlayText`.
- `androidTest` — `NotesVideoRendererTest` на `TestVideo` (размер и поворот — параметрами; тон 440 Гц = A4 в строе): длительность = исходник + 3 с (± 1 кадр); одна дорожка картинки H.264 и одна звука; кадр посреди — у черты в полосе цвет «в строе»; кадр через 2 с после конца — тёмный, с белыми буквами; повёрнутое видео показывается портретом и полоса у его низа; звук «как есть» — тот же mime и длительность; отмена посреди — ни файла, ни временных.
- **Сначала — проба (spike):** видео и картинка в одной последовательности с эффектами композиции в 1.9.0; время у эффекта композиции — по всей композиции. Запасной путь — оверлей на каждом элементе со своим началом времени.

## Этап 125 — iOS: картинка с нотами

- `shared/src/iosMain/…/core/recording/overlay/IosNotesVideoRenderer.kt`:
  - `AVAssetReader`: видео — `AVAssetReaderTrackOutput` (32BGRA; ширина и высота хранимого кадра, уменьшенные до выхода; `AVVideoColorPropertiesKey` BT.709 — HDR сводится в SDR); звук — второй читатель по готовому `.m4a` или по самому видео без настроек вывода (сэмплы как есть).
  - `AVAssetWriter` (`.mp4`): видео H.264 High, битрейт 5.30, свойства цвета BT.709, `transform = preferredTransform`; адаптер пиксельных буферов BGRA; звук — вход без настроек вывода с `sourceFormatHint`.
  - Кадр: `CVPixelBufferLockBaseAddress` → `Surface.makeRasterDirect(BGRA_8888, premul, адрес, bytesPerRow)` → холст с обратным `preferredTransform` (рисуем в координатах показа) → `CanvasDrawScope` + рисовальщик → закрыть поверхность → разблокировать → `appendPixelBuffer`. Лишние кадры (> 30 к/с) пропускаются.
  - Итог: копия последнего кадра в буферы пула адаптера, 3 с при выходной частоте, итог с проявлением 400 мс.
  - Чередование: в писатель идёт та дорожка, что отстаёт по времени (`readyForMoreMediaData` — ожидание на `Dispatchers.IO`).
  - Прогресс — время кадра / (длительность + 3 с) → 0,10…1,0. Отмена — `cancelReading` / `cancelWriting`, файл удаляется. Писатель упал (фон приложения) — `false`.
- `IosGraph` — `NotesVideoRenderer` и `IosOverlayText`.
- `iosTest` — `IosNotesVideoRendererTest`: пробное видео — как в `IosSoundRendererTest` (писатель BGRA) + тон через `writeAacTones`; проверки — как на Android (длительность, дорожки, цвет у черты по `AVAssetImageGenerator`, тёмный итог, повёрнутое видео, отмена).
- **Сначала — пробы:** Skia raster-direct по памяти `CVPixelBuffer`; `TextMeasurer` вне главного потока на iOS (запасной путь — текст `skia.Paragraph` в iosMain за тем же интерфейсом рисовальщика); время на кадр 1080 × 1920 (цель — меньше 10 мс в симуляторе на Profile).

## Этап 126 — «Поделиться»

- `ShareContract.kt`: `ShareVariant.NOTES`; `ShareInfo.notes: NotesOffer?` (`resolution`, `bytes`, `tooLong`, `sound` — минусовка / обработанный / как записан); `fileNameOf`, `extensionOf`, `typeOf`, `bytesOf` знают `NOTES` (имя и тип — как у видео).
- `ShareViewModel`: предложение — у записи с видео, если есть `NotesVideoRenderer`; по умолчанию выбран `NOTES` (если не длиннее предела); `VariantSelected` не берёт приглушённый вариант; `send` → `sendNotes`: ключ кеша `«<звук>-notes-<язык>[-backing-<id>-<сдвиг>-<громкость>]»` в `ShareFiles.processed`; звук варианта во временный `.m4a` (или само видео), `NotesOverlays.of(…)` по `sessions.details` и списку записей, слова, `NotesVideoRenderer.render` с долями прогресса; своя `RenderSpeed` (старт 0,6 — конструктор `RenderSpeed(startFactor)`, второй экземпляр — отдельным типом или квалификатором Hilt); сбой — `Failed` и `analytics.error(MEDIA, …)`, как у видео.
- `ShareHost.kt`: `variantsOf` — `NOTES` первым; заголовок и подпись по звуку; приглушённая строка (0,38, не выбирается, «недоступно» для TalkBack); строка файла — разрешение и оценка `NotesOffer`; подготовка — «Готовим видео».
- DI: `HiltShareViewModel`, `IosNavHost.shareViewModel`.
- Тесты: `ShareViewModelTest` (вариант первым и выбран; длиннее 15 минут — не выбран и не выбирается; звук — минусовка / обработка / оригинал; кеш — второй раз без подготовки, другой язык — другой файл; прогресс по долям; сбой и «Ещё раз»; отмена; без рендерера — варианта нет), `ShareInfoTest` (`NOTES`: имя, расширение, тип, размер), `ShareSheetTest` (строка, подписи, приглушённая, TalkBack), превью листа с «Видео с нотами».
- Проверка на эмуляторе (данные — архив `run-as tar`, снимок `default_boot` пересохранить): видео-дубль портретом и повёрнутый, дубль под минусовку («Concerto No. 2»), обработка включена и выключена, видео события, отмена, «Ещё раз», повторная отправка из кеша; готовый файл — `adb exec-out run-as … cat` и кадры `ffmpeg` из `imageio_ffmpeg` (в начале, посреди, в итоге) — посмотреть глазами. iOS: `iosTest` и сборка; лист в симуляторе без касаний не открыть — общий Compose проверен на Android.

## Что не проверить здесь

Скорость и нагрев на Pixel 10a и iPhone 15 Pro Max; HDR-видео (с iPhone, Pixel с «10-bit HDR»); видео из галереи, снятое другим телефоном; вид после пережатия мессенджерами; уход в фон на iOS посреди подготовки; запасной кодер Media3 на слабом телефоне.
