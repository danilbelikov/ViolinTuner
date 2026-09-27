# Заметки по коду · Обезличенная статистика

Спека — `docs/spec/analytics.md` (3.34, 5.27), этапы 93–95.

## Что проверено с настоящим ключом (эмулятор и телефон владельца, 23.09.2026)

**На телефоне владельца** (поставлено `adb install -r -t` с его согласия, данные не тронуты): ключ принят, `Updated data sending enabled: true`, событие `screen_open {screen=practice}` принято SDK и доставлено — `Event sent: screen_open with value {"screen":"practice"}`. Там стоит debug-сборка с `-PanalyticsDebug=true`, то есть с включёнными логами SDK; чтобы телефон замолчал, достаточно поставить обычную debug-сборку без флага.

**Ловушка, которая выглядит как поломка:** пока экран телефона выключен, интерфейс не рисуется, навигация не сообщает об экранах — и `screen_open` не приходит, хотя сессии уходят. Проверять аналитику экранов на спящем телефоне бессмысленно; `dumpsys window | grep mAwake` отвечает на вопрос за секунду.

## Что проверено на эмуляторе

- Ключ из `local.properties` доходит до сборки, SDK активируется.
- **Цепочка согласия работает от начала до конца.** В логе подряд: `Update config with value {…"data_sending_enabled":false…}` — то есть библиотека стартует немой, — и сразу `Updated data sending enabled: true` от потока из DataStore. Значит, ни первая сессия не теряется, ни отправка не идёт до согласия.
- События приходят с правильными именами и значениями: `Event received: screen_open. With value: {screen=onboarding}`, `mic_permission. With value: {result=granted}`, `screen_open {screen=practice}`.
- **Доставка подтверждена**: `Event sent: screen_open with value {"screen":"practice"}` и следом `Event removed from db` — из своей очереди SDK удаляет событие только после того, как сервер его принял.

## Что не проверено

- **В консоль AppMetrica никто не заходил** — что события видно в отчётах, подтверждено только со стороны телефона.
- `live_frames` на устройстве не вызывался ни разу: нужно провести на Live больше 30 секунд с живыми кадрами. `mic_unavailable`, события занятия, дубля, репертуара, дороги, лавки и копии на устройстве тоже не вызывались — они закрыты юнит-тестами, а вставки однострочные.
- Что после выключения переключателя SDK замолкает, глазами не подтверждено: в logcat он пишет только про включение. С `ConsentGate` от этого больше ничего не зависит: при выключенном согласии приложение ничего ему не отдаёт.
- На телефоне владельца проверен только запуск и `screen_open`: события занятия, дубля и картина кадров там ещё не случались.
- Исключение четырёх модулей (`location`, `screenshot`, `billing`, `ad-revenue`) проверено запуском: приложение живёт, SDK стартует. Что от этого не сломались дальние углы — минусовка, видео, импорт — не проверялось.
- Удаление экспортированного `PreloadInfoContentProvider` проверено сборкой и запуском на эмуляторе.
- События, кроме `screen_open` и `mic_permission`, руками на устройстве не вызывались: они закрыты юнит-тестами там, где есть что проверять (`FramePictureTest`, `PracticeEarnsTaktsTest`, `AnalyticsViewModelTest`), а вставки в репозитории — однострочные.

## Что осталось от спеки

- **Скрипт выгрузки `tools/analytics/pull.py` не написан** (спека, этап 94). Его нельзя написать вслепую: нужен OAuth-токен, номер приложения в консоли и хотя бы один реальный ответ Logs API, чтобы знать форму данных. Без него картины кадров копятся в консоли, но в таблицу для подбора порогов не превращаются — это следующий шаг после того, как ключ заработает.
- Строка «Политика конфиденциальности» в блоке «Данные» сделана (24.09.2026); страницы по её ссылке и раздела Data Safety в консоли ещё нет — `docs/release.md`, этапы 2 и 4.
- Иконка строки — временная (`AppIcons.Device`), и в «Настройках», и на странице 4 знакомства. Дизайн рисует график или щит в общем наборе (3.16).

## Как это собрано

- Слой — `core/analytics`: `Analytics` (интерфейс), `AnalyticsEvent` (sealed, весь список событий), `NoOpAnalytics`, `AppMetricaAnalytics` (единственный файл, знающий SDK), `di/AnalyticsModule`. Плюс `FramePicture` — чистый агрегатор картины кадров (с 27.09.2026 `clarity_median` считается только по кадрам не тише порога тишины: на тихих детектор больше не запускается, их clarity — 0; а у кадра, чей грубый пик NSDF ниже 0,75, clarity — высота пика на целом лаге, до ~0,09 ниже уточнённой: MPM такие кадры не уточняет; медианы до и после этой даты между собой не сравнивать, порог тишины подбирается по `rms_peak_dbfs`, гистограммы clarity тихих кадров быть не может), и `AnalyticsViewModel` в `core/ui/analytics` для того, что видно только экрану (какой экран открыли, что ответила система про микрофон).
- **Кто шлёт**: `NoOpAnalytics` в debug и при пустом ключе; настоящий — только когда ключ есть и сборка не debug (или `-PanalyticsDebug=true`). Проверка — в `AnalyticsModule.provideAnalytics`.
- **Согласие**: `activate` с `withDataSendingEnabled(false)` в `ViolinTunerApp.onCreate`, дальше поток `analyticsEnabled` из DataStore зовёт `setDataSendingEnabled`. Так нет ни `runBlocking`, ни потерянной первой сессии. С 26.09.2026 согласие держит и само приложение — `ConsentGate` (`commonMain/core/analytics`) в `AppMetricaAnalytics` и `IosAppMetricaAnalytics`: до первого чтения настроек `track`, `error` и iOS-падение прошлого запуска ждут (не больше 64), выключенное согласие закрывает ворота раньше, чем глушит SDK, а включённое открывает после; при выключенном в SDK не уходит ничего, ждавшее выбрасывается. Копит ли немой SDK отданное ему — больше ни от чего не зависит. Падения на Android SDK ловит сам, мимо ворот: их держит только флаг SDK.
- **Тексты ошибок**: `error()` отдаёт исключение через `ErrorText.scrub` — пути, `content://`- и `file://`-адреса и имена в кавычках («…», “…”, "…", '…' после разделителя — апостроф в «can't» не кавычка) заменяются на `<path>`, `<uri>`, `<name>`, коды причин остаются. На Android уходит копия исключения `ReportedFailure` с классом в тексте и прежними кадрами (цепочка причин — не длиннее 8), на iOS — строка; iOS-падения прошлого запуска — тоже с очищенными текстом и причинами. Падения, которые собирает сам SDK (Android, KSCrash), не очищаются. Журнал logcat не менялся: он локален.
- **Файл настроек, который не читается** (сбой носителя, битый файл из копии), заменяется новым (`DataStoreSettingsRepository.startOverWhenUnreadable`, обе платформы) — с `analytics_enabled = false`, а не с согласием по умолчанию: потерянный файл не может сказать, выключал ли человек статистику, а выключенное держится, пока человек сам не вернёт (3.34, правило 2). Остальное — как после чистой установки: онбординг заново, эталон и допуск по умолчанию, идущее занятие потеряно; база и файлы целы. Статистике об этом не сообщается: она сама следует согласию из этого файла. Проверено тестами (`DataStoreSettingsRepositoryTest`, `IosStorageTest`) и на эмуляторе (26.09.2026): файл сохранить (`run-as … cp files/datastore/user_settings.preferences_pb files/datastore/settings.bak`), испортить (`run-as … sh -c 'printf "\n\177" > files/datastore/user_settings.preferences_pb'`), запустить — открывается знакомство, в logcat `Settings: the settings file could not be read and starts over`, в файле остаётся один ключ `analytics_enabled = false`; потом файл вернуть.
- **Куда вставлены события**: в домен и репозитории, а не в экраны — `PracticeFinisher.save`, `TakePipeline` (дубль записан, микрофон пропал), `RoomSessionRepository.delete`, `RoomRepertoireRepository.add`, `RoomJourneyRepository.depart`, `RoomHomeRepository.buy`, `BackupManager` (копия и обе ошибки), `AacFileEncoder` (потерянный звук дубля), `PracticeViewModel.openRecap` (уровень нигде не хранится — только там известно, что он вырос).
- Внедряется параметром со значением по умолчанию `= NoOpAnalytics()`: Hilt подставляет настоящий, а прежние тесты не переписывались.
- **Правило, которое легко нарушить незаметно**: в событиях только числа и перечисления. Маршрут экрана режется `screenKeyOf` до имени без аргументов, id вещи — ключ каталога, раздел репертуара — имя enum. Никаких названий произведений, имён файлов, заметок.

## iOS (25.09.2026)

- Тот же слой: `Analytics` и все события общие, отправляет `IosAppMetricaAnalytics` через Swift-мост `AppMetricaService` (AppMetrica iOS SDK 6.7.x, `AppMetricaCore` + `AppMetricaCrashes` для ошибок). Ключ — тот же `appMetricaKey` из `local.properties`; события iOS и Android идут под одним ключом.
- **Проверено в симуляторе** (сборка с `ORG_GRADLE_PROJECT_analyticsDebug=true`): SDK активируется с ключом, стартует с запретом отправки (`restriction '3'`), поток согласия тут же разрешает (`'2'`), события приняты: `screen_open {screen=practice}`, `{screen=settings}`, `{screen=history}`.
- **Не проверено:** доставка на сервер — в симуляторе DNS не находит `startup.mobile.yandex.net`, события остаются в очереди. На iPhone владельца стоит приложение `com.violinjourney.app.debug` (конфигурация Debug или Profile); оно молчит по bundle id (`IosBuild.sendsStatistics`, суффикс `.debug`), а не по типу Kotlin-фреймворка — release-фреймворк конфигурации Profile отправку не включает. Чтобы iOS слал из приложения `.debug`, нужно `analyticsDebug=true` в `local.properties` и пересборка.

### Падения Kotlin (26.09.2026)

- Необработанное исключение Kotlin/Native (в том числе из корутины — `processUnhandledException`) кончается `abort()`, и KSCrash внутри `AppMetricaCrashes` видит только SIGABRT с кадрами корутин: ни типа, ни текста, ни места, где бросили. Поэтому `KotlinCrashes` (`iosMain/core/analytics`) ставит `setUnhandledExceptionHook` первой строкой `MainViewController`: крючок синхронно пишет тип, текст, цепочку причин и кадры в `Caches/last-kotlin-crash.txt` (Caches не входит ни в резервную копию телефона, ни в копию данных 3.20) и завершает процесс как раньше — `terminateWithUnhandledException`. Запись не удалась — падение остаётся таким, как было, второго исключения в крючке нет.
- На следующем старте запись читается и удаляется, а `IosAppMetricaAnalytics.crashed` отдаёт её в `AnalyticsService.reportUnhandledException` → `AppMetricaService.swift` → `AppMetricaCrashes.crashes().reporter(for: key).pluginExtension().reportUnhandledException` — тот же путь, что у плагинов Unity и Flutter: отдельное падение (событие типа 26, как у KSCrash), названное классом исключения, с кадрами (`StackTraceElement`), `platform = "kotlin"`, версия Kotlin и причины — в окружении. **Не `AppMetricaCrashes.crashes().pluginExtension()`**: модуль падений настраивает себя на очереди библиотеки уже после `activate`, и отчёт со старта получает «Crash reporter is not configured» (так и было в симуляторе); репортёр ключа есть сразу, модуль потом берёт тот же. Блок `onFailure` обязан быть не nil: SDK зовёт его без проверки, когда не может собрать отчёт. В сборке без статистики запись только пишется в журнал (`Kotlin crash of the last run: …`). Обычные `reportError` со старта по той же причине молча терялись (модуль ещё не `activated`); с `ConsentGate` они ждут первого чтения согласия, то есть уходят чуть позже — что это успевает после активации модуля, в симуляторе не проверено.
- Почему не из самого крючка: SDK шлёт позже, из своей очереди, а процесса к тому времени нет; и адрес этого запуска бессмыслен в следующем (ASLR). Поэтому каждый кадр хранится как образ и смещение в нём (`dladdr` в умирающем процессе): имя класса и функции — из символа `kfun:…`, если сборка их сохранила, файл и строка — из строки стека, если в сборке есть отладочные сведения; иначе в поле файла — `ViolinJourney+0x1a2b`. **Консоль AppMetrica такие кадры сама не символизирует**: для выпущенной (обрезанной при архивации) сборки имена находятся руками — `atos -o <dSYM>/Contents/Resources/DWARF/ViolinJourney -l 0x100000000 <0x100000000 + смещение>` по dSYM того же архива. Загрузка dSYM в консоль для отчётов KSCrash — шаг публикации, ещё не сделан.
- Одно падение Kotlin даёт в консоли два отчёта: SIGABRT от KSCrash (в момент падения) и «kotlin»-падение с типом и кадрами (при следующем запуске, если приложение открыли снова). Текст исключения и причин проходит `ErrorText.scrub`, как причина в `error()`; отчёты о падениях, которые SDK собирает сам на Android, — нет.
- **Проверено в симуляторе** (26.09.2026): подложенная запись в `Library/Caches` читается при старте и удаляется; в обычной Debug-сборке — строка в журнале; в сборке с `ORG_GRADLE_PROJECT_analyticsDebug=true` и **выдуманным** ключом (`ORG_GRADLE_PROJECT_appMetricaKey=$(uuidgen)` — настоящий ключ для таких проверок не брать, иначе подложенное падение уйдёт в консоль владельца) отчёт лёг в очередь SDK: `Library/Application Support/io.appmetrica/main/data.sqlite`, таблица `events`, `type = 26`. Валидатор SDK при этом пишет «There were suspicious errors while AMADecodedCrash serialization» (у падения плагина нет образов и сведений о системе) — это предупреждение, отчёт всё равно упакован. Строки, которые сделала проверка, из очереди симулятора удалены.
- **Не проверено на устройстве**: настоящее падение → запись → отчёт при следующем старте, и как отчёт выглядит в консоли (в симуляторе хост AppMetrica не находится). Запись, чтение, разбор кадров и вызов моста закрыты `KotlinCrashesTest`.

### Манифест приватности (26.09.2026)

- У приложения свой `iosApp/iosApp/PrivacyInfo.xcprivacy` (фаза Resources, лежит в корне `.app`). `NSPrivacyTracking = false`, доменов отслеживания нет. Что собирает само приложение (3.34), всё без привязки к личности и без отслеживания: `ProductInteraction` (Analytics — экраны, занятия, дубли, произведения, дорога, лавка, копия), `OtherDiagnosticData` (Analytics — `live_frames`, `mic_unavailable`, ошибки, которые приложение переживает), `CrashData` (AppFunctionality — падения через `AppMetricaCrashes`).
- Причины API из списка Apple «required reason» — за код самого приложения: Kotlin общего модуля со слинкованными okio, SQLite, Skia и ICU и Swift `iosApp`. SystemBootTime `35F9.1` — `NSProcessInfo.systemUptime` (часы прогресса в `IosGraph`), `mach_absolute_time` в Skia. DiskSpace `E174.1` + `85F4.1` — `volumeAvailableCapacityForImportantUsage` вместе с `NSFileSystemFreeSize` (`PlatformFile.availableBytes`) перед копией, видео и минусовкой и «хватит примерно на N мин» на экране съёмки; `statfs` в SQLite. FileTimestamp `C617.1` + `3B52.1` — `NSFileModificationDate` (возраст сирот), `stat`/`fstat`/`lstat` в okio, SQLite, Skia, ICU; размер копии в папке, выбранной в «Файлах». UserDefaults не объявлен: в коде приложения его нет, у AppMetrica свой `CA92.1`. **Новый вызов API из этого списка = новая причина в манифесте**, иначе загрузка в App Store Connect получит ITMS-91053 (в письме будет названа категория).
- У SDK свои манифесты: в `.app` лежат бандлы `AppMetrica_*` (Core и его модули, Crashes) и `KSCrash_*` (Core, Recording, RecordingCore); SwiftProtobuf не входит — `AppMetricaProductFlow` не подключён. **Объединение собираемых типов** — то, что приложение вместе с ними сообщает Apple; всё без привязки к личности и без отслеживания (сверено по манифестам из собранного `.app`, AppMetrica 6.7.0, KSCrash 2.5.1):

  | Тип | Цель | Кто объявляет |
  |---|---|---|
  | CoarseLocation (грубая геопозиция по IP) | Analytics | AppMetricaCore |
  | ProductInteraction | Analytics; AppFunctionality | приложение, AppMetricaCore; AppMetricaHostState |
  | PurchaseHistory | Analytics | AppMetricaCore |
  | OtherDataTypes | Analytics, AppFunctionality | AppMetricaCore, AppMetricaCrashes, AppMetricaHostState |
  | CrashData | AppFunctionality | приложение, AppMetricaCrashes, KSCrash |
  | PerformanceData | AppFunctionality | AppMetricaCrashes, KSCrash |
  | OtherDiagnosticData | Analytics; AppFunctionality | приложение, AppMetricaCrashes; KSCrash |
  | DeviceID | AppFunctionality | KSCrash (Recording, RecordingCore) |

- **Ярлыки App Privacy в App Store Connect** владелец заполняет не по памяти и не по этой таблице, а по отчёту Xcode: `xcodebuild archive` → Organizer → архив → **Generate Privacy Report** — он сводит манифест приложения и всех SDK той версии, что в архиве.
- Продукт `AppMetricaAdSupport` не подключать: он объявляет Tracking = true и IDFA, это противоречит `NSPrivacyTracking = false` и 5.27. `ITSAppUsesNonExemptEncryption` не выставлен — это заявление владельца; без него App Store Connect спрашивает про шифрование при каждой загрузке.

## Проверка руками на эмуляторе

```
./gradlew :app:assembleDebug -PanalyticsDebug=true -PappMetricaKey=<ключ>
ANDROID_SERIAL=emulator-5554 adb install -r app/build/outputs/apk/debug/app-debug.apk
adb logcat -s AppMetrica            # «Initializing», «Activate AppMetrica with APIKey …»
adb shell run-as com.violinjourney.app.debug cat files/datastore/user_settings.preferences_pb | strings
```

Последняя строка показывает, появился ли ключ `analytics_enabled` после тапа по переключателю в «Настройках» → «Данные».

**У эмулятора бывает мёртвая сеть, и это выглядит как молчащая аналитика.** Признак — `adb shell ping -c 1 report.appmetrica.yandex.net` отвечает `unknown host`. Перезагрузка (`adb reboot`) не помогает; помогает перезапуск AVD со своим DNS:

```
adb -s emulator-5554 emu kill
~/Library/Android/sdk/emulator/emulator -avd Pixel_7 -dns-server 8.8.8.8 -no-boot-anim &
```

Пока сеть мертва, события копятся в базе SDK и никуда не уходят — в логе видно `Event saved to db`, но нет `Event sent`. Это не код.

Порядок строк в логе, по которым читается вся цепочка: `Event received: <имя>` (SDK принял) → `Event saved to db` (лежит в очереди) → `Event sent` и `Event removed from db` (сервер принял, из очереди удалено). Шлёт пачками, не сразу: своей очереди приходится ждать минуты.

Итоговый список разрешений приложения — в слитом манифесте:
`app/build/intermediates/merged_manifest/debug/processDebugMainManifest/AndroidManifest.xml`. Там должны быть `INTERNET` и `ACCESS_NETWORK_STATE` и не должно быть `AD_ID` и `preloadinfo`.
