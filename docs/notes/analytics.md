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
- Что после выключения переключателя SDK замолкает, глазами не подтверждено: в logcat он пишет только про включение.
- На телефоне владельца проверен только запуск и `screen_open`: события занятия, дубля и картина кадров там ещё не случались.
- Исключение четырёх модулей (`location`, `screenshot`, `billing`, `ad-revenue`) проверено запуском: приложение живёт, SDK стартует. Что от этого не сломались дальние углы — минусовка, видео, импорт — не проверялось.
- Удаление экспортированного `PreloadInfoContentProvider` проверено сборкой и запуском на эмуляторе.
- События, кроме `screen_open` и `mic_permission`, руками на устройстве не вызывались: они закрыты юнит-тестами там, где есть что проверять (`FramePictureTest`, `PracticeEarnsTaktsTest`, `AnalyticsViewModelTest`), а вставки в репозитории — однострочные.

## Что осталось от спеки

- **Скрипт выгрузки `tools/analytics/pull.py` не написан** (спека, этап 94). Его нельзя написать вслепую: нужен OAuth-токен, номер приложения в консоли и хотя бы один реальный ответ Logs API, чтобы знать форму данных. Без него картины кадров копятся в консоли, но в таблицу для подбора порогов не превращаются — это следующий шаг после того, как ключ заработает.
- Строка «Политика конфиденциальности» в блоке «Данные» сделана (24.09.2026); страницы по её ссылке и раздела Data Safety в консоли ещё нет — `docs/release.md`, этапы 2 и 4.
- Иконка строки — временная (`AppIcons.Device`), и в «Настройках», и на странице 4 знакомства. Дизайн рисует график или щит в общем наборе (3.16).

## Как это собрано

- Слой — `core/analytics`: `Analytics` (интерфейс), `AnalyticsEvent` (sealed, весь список событий), `NoOpAnalytics`, `AppMetricaAnalytics` (единственный файл, знающий SDK), `di/AnalyticsModule`. Плюс `FramePicture` — чистый агрегатор картины кадров, и `AnalyticsViewModel` в `core/ui/analytics` для того, что видно только экрану (какой экран открыли, что ответила система про микрофон).
- **Кто шлёт**: `NoOpAnalytics` в debug и при пустом ключе; настоящий — только когда ключ есть и сборка не debug (или `-PanalyticsDebug=true`). Проверка — в `AnalyticsModule.provideAnalytics`.
- **Согласие**: `activate` с `withDataSendingEnabled(false)` в `ViolinTunerApp.onCreate`, дальше поток `analyticsEnabled` из DataStore зовёт `setDataSendingEnabled`. Так нет ни `runBlocking`, ни потерянной первой сессии.
- **Куда вставлены события**: в домен и репозитории, а не в экраны — `PracticeFinisher.save`, `TakePipeline` (дубль записан, микрофон пропал), `RoomSessionRepository.delete`, `RoomRepertoireRepository.add`, `RoomJourneyRepository.depart`, `RoomHomeRepository.buy`, `BackupManager` (копия и обе ошибки), `AacFileEncoder` (потерянный звук дубля), `PracticeViewModel.openRecap` (уровень нигде не хранится — только там известно, что он вырос).
- Внедряется параметром со значением по умолчанию `= NoOpAnalytics()`: Hilt подставляет настоящий, а прежние тесты не переписывались.
- **Правило, которое легко нарушить незаметно**: в событиях только числа и перечисления. Маршрут экрана режется `screenKeyOf` до имени без аргументов, id вещи — ключ каталога, раздел репертуара — имя enum. Никаких названий произведений, имён файлов, заметок.

## iOS (25.09.2026)

- Тот же слой: `Analytics` и все события общие, отправляет `IosAppMetricaAnalytics` через Swift-мост `AppMetricaService` (AppMetrica iOS SDK 6.7.x, `AppMetricaCore` + `AppMetricaCrashes` для ошибок). Ключ — тот же `appMetricaKey` из `local.properties`; события iOS и Android идут под одним ключом.
- **Проверено в симуляторе** (сборка с `ORG_GRADLE_PROJECT_analyticsDebug=true`): SDK активируется с ключом, стартует с запретом отправки (`restriction '3'`), поток согласия тут же разрешает (`'2'`), события приняты: `screen_open {screen=practice}`, `{screen=settings}`, `{screen=history}`.
- **Не проверено:** доставка на сервер — в симуляторе DNS не находит `startup.mobile.yandex.net`, события остаются в очереди. На iPhone владельца стоит обычная debug-сборка, которая молчит; чтобы iOS отправлял из debug, нужно `analyticsDebug=true` в `local.properties` и пересборка.

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
