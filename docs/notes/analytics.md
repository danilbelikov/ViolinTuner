# Заметки по коду · Обезличенная статистика

Спека — `docs/spec/analytics.md` (3.34, 5.27), этапы 93–95.

## Что не проверено

- **Ключа у меня не было.** Всё, что касается доставки, проверялось на выдуманном UUID: SDK активируется, пишет в logcat `Activate AppMetrica with APIKey …`, приложение не падает. **Долетают ли события до консоли — не проверено никем.** Первое, что стоит сделать с настоящим ключом: положить его в `local.properties`, собрать `-PanalyticsDebug=true`, добавить эмулятор в «тестовые устройства» и посмотреть поток событий.
- **Выключенное согласие подтверждено только изнутри**: юнит-тестом на репозиторий и тем, что ключ `analytics_enabled` появляется в `user_settings.preferences_pb` после тапа. Что после `setDataSendingEnabled(false)` SDK действительно молчит, глазами не подтверждено — в logcat AppMetrica этого не пишет.
- **На телефоне владельца не проверено ничего.**
- Исключение четырёх модулей (`location`, `screenshot`, `billing`, `ad-revenue`) проверено запуском: приложение живёт, SDK стартует. Что от этого не сломались дальние углы — минусовка, видео, импорт — не проверялось.
- Удаление экспортированного `PreloadInfoContentProvider` проверено сборкой и запуском на эмуляторе.
- События, кроме `screen_open` и `mic_permission`, руками на устройстве не вызывались: они закрыты юнит-тестами там, где есть что проверять (`FramePictureTest`, `PracticeEarnsTaktsTest`, `AnalyticsViewModelTest`), а вставки в репозитории — однострочные.

## Что осталось от спеки

- **Скрипт выгрузки `tools/analytics/pull.py` не написан** (спека, этап 94). Его нельзя написать вслепую: нужен OAuth-токен, номер приложения в консоли и хотя бы один реальный ответ Logs API, чтобы знать форму данных. Без него картины кадров копятся в консоли, но в таблицу для подбора порогов не превращаются — это следующий шаг после того, как ключ заработает.
- Политика конфиденциальности и раздел Data Safety в консоли магазина (спека, этап 95) — перед публикацией, не код.
- Иконка строки — временная (`AppIcons.Device`), и в «Настройках», и на странице 4 знакомства. Дизайн рисует график или щит в общем наборе (3.16).

## Как это собрано

- Слой — `core/analytics`: `Analytics` (интерфейс), `AnalyticsEvent` (sealed, весь список событий), `NoOpAnalytics`, `AppMetricaAnalytics` (единственный файл, знающий SDK), `di/AnalyticsModule`. Плюс `FramePicture` — чистый агрегатор картины кадров, и `AnalyticsViewModel` в `core/ui/analytics` для того, что видно только экрану (какой экран открыли, что ответила система про микрофон).
- **Кто шлёт**: `NoOpAnalytics` в debug и при пустом ключе; настоящий — только когда ключ есть и сборка не debug (или `-PanalyticsDebug=true`). Проверка — в `AnalyticsModule.provideAnalytics`.
- **Согласие**: `activate` с `withDataSendingEnabled(false)` в `ViolinTunerApp.onCreate`, дальше поток `analyticsEnabled` из DataStore зовёт `setDataSendingEnabled`. Так нет ни `runBlocking`, ни потерянной первой сессии.
- **Куда вставлены события**: в домен и репозитории, а не в экраны — `PracticeFinisher.save`, `TakePipeline` (дубль записан, микрофон пропал), `RoomSessionRepository.delete`, `RoomRepertoireRepository.add`, `RoomJourneyRepository.depart`, `RoomHomeRepository.buy`, `BackupManager` (копия и обе ошибки), `AacFileEncoder` (потерянный звук дубля), `PracticeViewModel.openRecap` (уровень нигде не хранится — только там известно, что он вырос).
- Внедряется параметром со значением по умолчанию `= NoOpAnalytics()`: Hilt подставляет настоящий, а прежние тесты не переписывались.
- **Правило, которое легко нарушить незаметно**: в событиях только числа и перечисления. Маршрут экрана режется `screenKeyOf` до имени без аргументов, id вещи — ключ каталога, раздел репертуара — имя enum. Никаких названий произведений, имён файлов, заметок.

## Проверка руками на эмуляторе

```
./gradlew :app:assembleDebug -PanalyticsDebug=true -PappMetricaKey=<ключ>
ANDROID_SERIAL=emulator-5554 adb install -r app/build/outputs/apk/debug/app-debug.apk
adb logcat -s AppMetrica            # «Initializing», «Activate AppMetrica with APIKey …»
adb shell run-as com.example.violintuner cat files/datastore/user_settings.preferences_pb | strings
```

Последняя строка показывает, появился ли ключ `analytics_enabled` после тапа по переключателю в «Настройках» → «Данные».

Итоговый список разрешений приложения — в слитом манифесте:
`app/build/intermediates/merged_manifest/debug/processDebugMainManifest/AndroidManifest.xml`. Там должны быть `INTERNET` и `ACCESS_NETWORK_STATE` и не должно быть `AD_ID` и `preloadinfo`.
