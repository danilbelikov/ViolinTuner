# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Violin Intonation (рабочее название)

Android-приложение: интонационный тренажёр для скрипки. Экран Live в реальном времени
показывает ноту и попадание в неё — крупно, цветом всего экрана, читается с пюпитра
боковым зрением. Спецификация — единственный источник правды по поведению, читай её
перед любой задачей:

@docs/spec.md

Макеты и токены — в `docs/design/` (хэндофф из Claude Design; финальный Live — вариант 8).
Это веб-прототип: переноси смысл, токены и раскладку, а не HTML-структуру.

## Хэндофф дизайна
- Главный файл — `docs/design/project/Интонация.dc.html`. Пути `untitled/...` в `docs/design/README.md` устарели. `Интонация - прототип.dc.html` — интерактивный прототип, вторичен.
- Секции по `id`: `v1` — все состояния Live, `v1-land` — landscape, `v1-tune` — «Настройка», `v1-stubs` — заглушки, `dev` — таблицы для разработчика, `v2` — не реализуется.
- Точные значения лежат не в разметке, а в JS-массивах в конце файла: `tokens` (цвета), `gradients` (стопы фона), `sizes` (dp/sp, базовый экран 412×892), `anims`. Шрифт макета — Manrope.
- **Хэндофф — источник правды только по визуалу.** Его блок «Логика зон» расходится со спекой (tol 5 вместо 8, near ≤ 2·tol вместо ≤ 20, EMA α 0.18 вместо 0.3, тишина 600 мс вместо 300, шум 2 с + диалог вместо 1 с), а таблица `anims` — по таймингам (кольцо 3000 мс вместо 2 с, кроссфейд 300–350 мс вместо ~150). По поведению и числам домена всегда побеждает `docs/spec.md`.
- Имена файлов с кириллицей на macOS хранятся в NFD: в shell обращайся к ним через glob (`*.dc.html`), а не набранным именем.

## Скоуп v1
Только экран Live с режимами «Игра» и «Настройка» (spec, раздел 3). «История» и
«Настройки» — заглушки. Запись, анализ, онбординг, светлую тему не делать, даже если
удобно «заодно» (spec, раздел 7).

## Стек
- Kotlin, Jetpack Compose + Material 3 (Compose BOM), Gradle Kotlin DSL, version catalog `gradle/libs.versions.toml`
- Hilt, Coroutines/Flow, Navigation Compose
- minSdk 26, JDK 17, один модуль `app`
- Room / DataStore — только когда дойдём до истории и настроек, не раньше
- Никаких сторонних DSP-библиотек: детектор высоты тона свой (YIN и MPM), параметры — в спеке

## Архитектура
- Clean + MVI. Пакеты: `feature/live`, `feature/history` (заглушка), `feature/settings` (заглушка); общее — `core/audio`, `core/domain`, `core/ui` (тема, токены, общие компоненты).
- Экран: `LiveContract` (State / Intent / Effect), `LiveViewModel`, `LiveScreen` (stateless: принимает State и `(Intent) -> Unit`), `LiveRoute` (ViewModel + навигация).
- Доменная логика (центы, зоны, сглаживание, гистерезис, снэп к струнам) — чистый Kotlin без Android-зависимостей в `core/domain`, покрыта unit-тестами.
- Все числовые константы — в `IntonationConfig` со значениями из спеки. Магических чисел в коде нет.
- Аудио: интерфейс `PitchSource`; `MicPitchSource` (AudioRecord, фоновый диспетчер) и `FakePitchSource` (превью, тесты, разработка без микрофона). ViewModel не знает про AudioRecord.
- Кольцо, шкала, фоновый градиент — `Canvas` / `Modifier.drawBehind` в Compose, без View-интеропа.

## Compose-правила
- `@Preview` на каждое состояние Live из spec 3.4 плюс режим «Настройка» (авто и зафиксированная струна).
- Цвета зон (inTune / near / off) — семантические токены темы; `colorScheme.error` для «мимо» не использовать.
- Нота — табличные цифры (`TextStyle(fontFeatureSettings = "tnum")`), чтобы ширина не прыгала.
- Live: `FLAG_KEEP_SCREEN_ON`; для landscape отдельная раскладка, а не растянутый портрет.
- Строки UI — только через `strings.xml`, язык по умолчанию русский. Код, комментарии, имена — на английском.

## Команды
- Сборка: `./gradlew :app:assembleDebug`
- Unit-тесты: `./gradlew :app:testDebugUnitTest`
- Один класс / один тест: `./gradlew :app:testDebugUnitTest --tests "*.ZoneClassifierTest"` / `--tests "*.ZoneClassifierTest.methodName"`
- Lint: `./gradlew :app:lintDebug`. Инструментальные тесты (нужен девайс/эмулятор): `./gradlew :app:connectedDebugAndroidTest`
- Задача готова только когда обе команды зелёные. Падающий тест чинится через причину, а не через правку теста.

## Процесс
- Крупную задачу начинай с плана: список файлов, что меняется, какие тесты. Код — после подтверждения.
- Один коммит — одна логическая единица: `feat(live): ...`, `fix(audio): ...`, `test(domain): ...`.
- Зависимости и версии в `libs.versions.toml` не добавлять и не менять без явного согласия.
- В прод-коде нет `runBlocking`, `GlobalScope`, проглоченных исключений и TODO без пояснения.
- Если спека не отвечает на вопрос — спроси, а не додумывай поведение.

## Текущее состояние (на 2026-09-17; удалить раздел после шага 1 из spec §8)
Репозиторий — нетронутый шаблон Android Studio; всё в разделах «Стек» и «Архитектура» — целевое состояние, а не существующий код.
- Обе команды сейчас красные: `junit = "4.14-SNAPSHOT"` не резолвится ни из одного репозитория; `core-ktx 1.19.0` требует compileSdk 37 при текущем 36.1. Оба исправления — правка версий, то есть требуют согласия.
- В каталоге версий нет Hilt (+ KSP), Navigation Compose, Coroutines, lifecycle-viewmodel-compose — шаг 1 начинается с согласования этого списка.
- `compileOptions` — Java 11, Gradle daemon toolchain — 21; заявленный JDK 17 ещё не настроен.
- `MainActivity` не вызывает `setContent`; `ui/theme` — шаблонная тема с dynamic color и светлой схемой, её заменяет `core/ui` с токенами хэндоффа (только тёмная, без dynamic color). Пакет — `com.example.violintuner`.
- Git не инициализирован, хотя процесс предполагает коммиты.
