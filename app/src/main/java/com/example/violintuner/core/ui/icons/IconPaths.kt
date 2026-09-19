package com.example.violintuner.core.ui.icons

/**
 * Path data of the icon set, copied verbatim from the `ICONS` array of the handoff
 * (`docs/design/project/polish`, frame 14a): a 24 × 24 grid, stroke 1.8, round caps and joins.
 * A path that starts with [FILLED] is a filled shape without a stroke (dots, a note head).
 * Generated from the handoff — change an icon there, not here.
 */
internal object IconPaths {
    const val FILLED = "F "

    /** назад — все экраны поверх вкладок */
    val BACK = listOf("M15 5 8 12l7 7")

    /** закрыть — форма произведения */
    val CLOSE = listOf("M6 6l12 12M18 6 6 18")

    /** чип статуса (14 dp) */
    val CHEVRON_DOWN = listOf("M6 9.5l6 6 6-6")

    /** месяц назад */
    val CHEVRON_LEFT = listOf("M14.5 6 8.5 12l6 6")

    /** месяц вперёд; строка настроек */
    val CHEVRON_RIGHT = listOf("M9.5 6l6 6-6 6")

    /** ещё — сворачивание заметок, меню записи */
    val MORE = listOf("F M5 10.8a1.2 1.2 0 1 0 0 2.4 1.2 1.2 0 1 0 0-2.4z", "F M12 10.8a1.2 1.2 0 1 0 0 2.4 1.2 1.2 0 1 0 0-2.4z", "F M19 10.8a1.2 1.2 0 1 0 0 2.4 1.2 1.2 0 1 0 0-2.4z")

    /** Изменить · Переименовать · Изменить время */
    val PENCIL = listOf("M4 20l.9-4.1L15.7 5.1a1.6 1.6 0 0 1 2.3 0l.9.9a1.6 1.6 0 0 1 0 2.3L8.1 19.1 4 20z", "M14.2 6.6l3.2 3.2")

    /** Удалить · Удалить произведение · Убрать фото · пюпитр */
    val TRASH = listOf("M4 7h16", "M9 7V4.8a.8.8 0 0 1 .8-.8h4.4a.8.8 0 0 1 .8.8V7", "M6 7l.9 12.2a1 1 0 0 0 1 .8h8.2a1 1 0 0 0 1-.8L18 7", "M10 11v5M14 11v5")

    /** Добавить произведение · плитка · заметку · время */
    val PLUS = listOf("M12 5v14M5 12h14")

    /** Готово (пюпитр) · выбранный пункт меню */
    val CHECK = listOf("M5 12.5 9.5 17 19 7.5")

    /** Сфотографировать */
    val CAMERA = listOf("M4 9a2 2 0 0 1 2-2h2l1.5-2.2h5L16 7h2a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V9z", "M12 9.3a3.2 3.2 0 1 0 0 6.4 3.2 3.2 0 1 0 0-6.4z")

    /** Из галереи · Выбрать фото */
    val GALLERY = listOf("M4 7a2 2 0 0 1 2-2h12a2 2 0 0 1 2 2v10a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V7z", "M4 16.5 9 11.5l4 4 2.5-2.5 4.5 4.5", "F M15.5 8.3a1.3 1.3 0 1 0 0 2.6 1.3 1.3 0 1 0 0-2.6z")

    /** заглушка миниатюры нот · «Добавить фото нот» */
    val SHEET = listOf("M6 3h8l5 5v12a1 1 0 0 1-1 1H6a1 1 0 0 1-1-1V4a1 1 0 0 1 1-1z", "M14 3v5h5", "M8 12.5h8M8 15.5h8M8 18.5h5")

    /** онбординг · нет разрешения · настройки */
    val MIC = listOf("M9 6a3 3 0 0 1 6 0v5a3 3 0 0 1-6 0V6z", "M5.5 11.5a6.5 6.5 0 0 0 13 0", "M12 18v3M9 21h6")

    /** плеер сессии */
    val PLAY = listOf("M8 5.5v13L18.5 12 8 5.5z")

    /** плеер сессии */
    val PAUSE = listOf("M7 5h3v14H7zM14 5h3v14h-3z")

    /** зафиксированная струна («Настройка») */
    val LOCK = listOf("M5 13a2 2 0 0 1 2-2h10a2 2 0 0 1 2 2v6a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2v-6z", "M8 11V8a4 4 0 0 1 8 0v3", "F M12 15a1.2 1.2 0 1 0 0 2.4 1.2 1.2 0 1 0 0-2.4z")

    /** Начать занятие */
    val TIMER = listOf("M12 6.5a7 7 0 1 0 0 14 7 7 0 1 0 0-14z", "M12 13.5V10", "M10 3h4M12 3v3.5")

    /** Закончить занятие */
    val FLAG = listOf("M6 21V4", "M6 4.5c2.5-1.5 5-1.5 7.5 0s5 1.5 6.5.5v9c-1.5 1-4 1-6.5-.5s-5-1.5-7.5 0")

    /** диалог «Занятие не закончено» */
    val CLOCK = listOf("M12 4a8 8 0 1 0 0 16 8 8 0 1 0 0-16z", "M12 7.5V12l3 2")

    /** заголовок листа «Трофеи» */
    val TROPHY = listOf("M7 4h10v6a5 5 0 0 1-10 0V4z", "M7 6H4.5v2a3 3 0 0 0 3 3", "M17 6h2.5v2a3 3 0 0 1-3 3", "M12 15v3M9 20.5h6M10 18h4")

    /** «Дней подряд» (14 dp у числа, только при ≥ 3) */
    val FLAME = listOf("M12 3.5c-.5 4.5-5.5 6.5-5.5 11.5a5.5 5.5 0 0 0 11 0c0-2.5-1.5-4.5-3-5.5 0 2-1 3-2 3.5-1-1.5-.5-4-.5-9.5z")

    /** чип «дубль» (14 dp) */
    val NOTE = listOf("M10 17.5V5l7 2.5v3.5l-7-2.5", "F M7.5 15.5a2.5 2 0 1 0 0 4 2.5 2 0 1 0 0-4z")

    /** отметка «лучший дубль» (14 dp, primary) */
    val STAR = listOf("M12 3.5l2.6 5.4 5.9.8-4.3 4.1 1.1 5.9L12 16.9l-5.3 2.8 1.1-5.9-4.3-4.1 5.9-.8L12 3.5z")

    /** темп «♩ = 96» → значок 14 dp + число */
    val METRONOME = listOf("M8 21 10 3h4l2 18H8z", "M8 21h8", "M12 14l4.5-7", "F M12 12.8a1.2 1.2 0 1 0 0 2.4 1.2 1.2 0 1 0 0-2.4z")

    /** пюпитр — вход с экрана произведения (не обязателен) */
    val STAND = listOf("M5 5h14l-1.5 8h-11L5 5z", "M12 13v7M9 21h6", "M8.5 8.5h7")

    /** эталон A4 (настройки) */
    val FORK = listOf("M9 3v7a3 3 0 0 0 6 0V3", "M12 13v8M9.5 21h5")

    /** допуск (настройки) */
    val TARGET = listOf("M12 4a8 8 0 1 0 0 16 8 8 0 1 0 0-16z", "M12 8a4 4 0 1 0 0 8 4 4 0 1 0 0-8z", "F M12 10.8a1.2 1.2 0 1 0 0 2.4 1.2 1.2 0 1 0 0-2.4z")

    /** Пройти онбординг снова */
    val REPEAT = listOf("M4 12a8 8 0 0 1 14.5-4.5", "M20 4v4h-4", "M20 12a8 8 0 0 1-14.5 4.5", "M4 20v-4h4")

    /** What a path of a tab icon does when its tab is selected. */
    enum class Selected { AS_IS, FILL, CUT }

    class TabPath(val d: String, val selected: Selected = Selected.AS_IS)

    /** вкладка Live */
    val TAB_LIVE = listOf(
        TabPath("M12 8a4 4 0 1 0 0 8 4 4 0 1 0 0-8z", Selected.FILL),
        TabPath("M17.7 6.3a8 8 0 0 1 0 11.4"),
        TabPath("M6.3 6.3a8 8 0 0 0 0 11.4"),
    )

    /** вкладка Занятия */
    val TAB_PRACTICE = listOf(
        TabPath("M12 6.5a7 7 0 1 0 0 14 7 7 0 1 0 0-14z", Selected.FILL),
        TabPath("M12 13.5V10", Selected.CUT),
        TabPath("M10 3h4M12 3v3.5"),
    )

    /** вкладка Записи */
    val TAB_RECORDS = listOf(
        TabPath("M4 6h8M4 12h6M4 18h5"),
        TabPath("M15 17V7.5l5-1.5v3"),
        TabPath("F M13 15a2 2 0 1 0 0 4 2 2 0 1 0 0-4z", Selected.FILL),
    )

    /** вкладка Настройки */
    val TAB_SETTINGS = listOf(
        TabPath("M4 7h9M18.5 7H20M4 17h6M14.5 17H20"),
        TabPath("M15.5 4.8a2.2 2.2 0 1 0 0 4.4 2.2 2.2 0 1 0 0-4.4z", Selected.FILL),
        TabPath("M10.5 14.8a2.2 2.2 0 1 0 0 4.4 2.2 2.2 0 1 0 0-4.4z", Selected.FILL),
    )
}
