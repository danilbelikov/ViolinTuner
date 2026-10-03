package com.violinjourney.app.feature.events

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.violinjourney.app.core.domain.events.AffectedDates
import com.violinjourney.app.core.domain.events.BuiltInKind
import com.violinjourney.app.core.domain.events.CalendarEvent
import com.violinjourney.app.core.domain.events.ChangedField
import com.violinjourney.app.core.domain.events.EditScope
import com.violinjourney.app.core.domain.events.EventSeries
import com.violinjourney.app.core.domain.events.EventsConfig
import com.violinjourney.app.core.domain.events.KindNameProblem
import com.violinjourney.app.core.domain.events.KindRef
import com.violinjourney.app.core.domain.events.KindRules
import com.violinjourney.app.core.domain.events.KindSign
import com.violinjourney.app.core.domain.events.Repeat
import com.violinjourney.app.core.domain.events.ScopeQuestion
import com.violinjourney.app.core.domain.events.StoredKind
import com.violinjourney.app.core.domain.events.draft
import com.violinjourney.app.core.ui.components.AppDialogCard
import com.violinjourney.app.core.ui.components.DialogTone
import com.violinjourney.app.core.ui.motion.LocalReduceMotion
import com.violinjourney.app.core.ui.theme.ViolinTheme
import com.violinjourney.app.feature.events.form.ChangeLine
import com.violinjourney.app.feature.events.form.EventFormReducer
import com.violinjourney.app.feature.events.form.EventFormScreen
import com.violinjourney.app.feature.events.form.EventFormSheetCard
import com.violinjourney.app.feature.events.form.EventFormState
import com.violinjourney.app.feature.events.form.FormDraft
import com.violinjourney.app.feature.events.form.FormSheet
import com.violinjourney.app.feature.events.form.KindDraft
import com.violinjourney.app.feature.events.form.Moved
import com.violinjourney.app.feature.events.form.ScopeAsk
import com.violinjourney.app.feature.events.screen.SeriesWord
import com.violinjourney.app.shared.resources.Res
import com.violinjourney.app.shared.resources.dialog_back
import com.violinjourney.app.shared.resources.dialog_discard_typed
import com.violinjourney.app.shared.resources.event_kind_delete_keeps
import com.violinjourney.app.shared.resources.event_kind_delete_text
import com.violinjourney.app.shared.resources.event_kind_delete_title
import com.violinjourney.app.shared.resources.event_kind_other
import com.violinjourney.app.shared.resources.piece_form_discard_confirm
import com.violinjourney.app.shared.resources.piece_form_discard_title
import com.violinjourney.app.shared.resources.session_delete_confirm
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import kotlinx.datetime.plus
import org.jetbrains.compose.resources.stringResource

// The form of an event and its sheets (spec 3.36.9; events-form.html 1–6, events-kinds.html 6, practice-sheets.html 10) on the data of the
// mockups: Sunday 27 September 2026; the lessons with Анна Сергеевна on Mondays at 17:00 for 45 minutes; «Осенний концерт» on 24 October at
// 18:30 in the small hall of the music school; the orchestra and the solfeggio of one's own. Over the tabs: the status bar over it, no bar
// of the tabs. A preview has no keyboard: the zone stands at the bottom of the window.

private object FormSample {
    val config = EventsConfig()
    val today = LocalDate(2026, 9, 27)
    val lesson = KindRef.BuiltIn(BuiltInKind.LESSON)
    val performance = KindRef.BuiltIn(BuiltInKind.PERFORMANCE)
    val rehearsal = KindRef.BuiltIn(BuiltInKind.REHEARSAL)
    val kinds = KindRules.all(
        listOf(StoredKind.Own(9, "Оркестр", 1, KindSign.ARC, 1), StoredKind.Own(10, "Сольфеджио", 3, KindSign.BOOK, 2), StoredKind.Own(11, "Мастер-класс", 4, KindSign.BOLT, 3)),
        config,
    )
    const val TEACHER = "Анна Сергеевна"
    const val SCHOOL = "Малый зал музыкальной школы"

    val lessons = EventSeries(1, lesson, Repeat.WEEKLY, LocalDate(2026, 9, 28), null, LocalDate(2026, 12, 20), 17 * 60, 45, "", TEACHER)
    val events: List<CalendarEvent> = (0 until 12).map { week ->
        CalendarEvent(week + 1L, lesson, LocalDate(2026, 9, 28).plus(7 * week, DateTimeUnit.DAY), 17 * 60, 45, "", TEACHER, "", 1, false, 100)
    } + listOf(
        CalendarEvent(20, performance, LocalDate(2026, 10, 24), 18 * 60 + 30, 90, "Осенний концерт", SCHOOL, "", null, false, 200),
        CalendarEvent(21, KindRef.Custom(9), LocalDate(2026, 10, 24), 11 * 60, 120, "", "ДК", "", null, false, 201),
        CalendarEvent(22, KindRef.Custom(10), LocalDate(2026, 10, 1), 16 * 60, 60, "", "ДМШ № 3", "", null, false, 202),
    )

    fun draft(
        kind: KindRef = lesson,
        date: LocalDate = LocalDate(2026, 9, 28),
        start: Int? = 17 * 60,
        duration: Int? = 45,
        repeat: Repeat = Repeat.NONE,
        until: LocalDate? = null,
        place: String = "",
        title: String = "",
    ) = FormDraft(kind, date, start, duration, repeat, until, place, title, notes = "")

    fun state(
        draft: FormDraft,
        sheet: FormSheet? = null,
        isNew: Boolean = true,
        edited: CalendarEvent? = null,
    ): EventFormState = EventFormReducer.stateOf(
        draft = draft, sheet = sheet, dialog = null, loading = false, isNew = isNew, edited = edited, editedSeries = lessons.takeIf { edited?.seriesId == 1L },
        initialDate = draft.date, events = events, kinds = kinds, today = today, focusNotes = false, config = config,
    )

    /** practice-sheets.html 10: the weekly lesson in four taps. */
    val newLesson = state(draft(repeat = Repeat.WEEKLY, place = TEACHER))

    /** The weekly lesson up to 31 December: the line of the repeat with its end and its count, one sentence (review of stage 98б). */
    val lessonUntil = state(draft(repeat = Repeat.WEEKLY, until = LocalDate(2026, 12, 31), place = TEACHER))

    /** A lesson at 23:30 for 1 h 30 min: the end past midnight beside «Длительность» (review of stage 98б). */
    val lateLesson = state(draft(start = 23 * 60 + 30, duration = 90, place = TEACHER))

    /** events-form.html 1: a performance with its place and its title. */
    val concert = state(draft(kind = performance, date = LocalDate(2026, 10, 24), start = 18 * 60 + 30, duration = 90, place = SCHOOL, title = "Осенний концерт"))

    /** events-form.html 1: «весь день» — no length. */
    val allDay = state(
        draft(
            kind = performance, date = LocalDate(2026, 11, 21), start = null, duration = null, place = "Москва, Малый зал консерватории",
            title = "Отборочный тур Международного конкурса юных скрипачей имени Л. Когана",
        ),
    )

    /** events-form.html 1: a length of one's own on its chip with the pencil. */
    val ownLength = state(draft(kind = rehearsal, date = LocalDate(2026, 10, 24), start = 11 * 60, duration = 150, place = SCHOOL, title = "Генеральная репетиция"))

    /** An edit of a lesson of the repeat: its line without «до…», the notes. */
    val editLesson = events[3].copy(notes = "Этюд Кайзера № 3 — до конца, деташе у колодки.").let { event ->
        state(EventFormReducer.draftOf(event, lessons).copy(startMinutes = 17 * 60 + 30), isNew = false, edited = event)
    }

    val date = state(newLesson.draft, sheet = FormSheet.Date(YearMonth(2026, 9), LocalDate(2026, 9, 28)))
    val dateConcert = state(concert.draft, sheet = FormSheet.Date(YearMonth(2026, 10), LocalDate(2026, 10, 24)))
    val time = state(concert.draft, sheet = FormSheet.Time(18 * 60 + 30, allDay = false))
    val timeAllDay = state(allDay.draft, sheet = FormSheet.Time(18 * 60 + 30, allDay = true))
    val timeLate = state(concert.draft, sheet = FormSheet.Time(23 * 60 + 30, allDay = false))
    val duration = state(ownLength.draft, sheet = FormSheet.Duration(150))
    val durationCeiling = state(ownLength.draft, sheet = FormSheet.Duration(480))
    val until = state(newLesson.draft, sheet = FormSheet.Until(YearMonth(2026, 12), LocalDate(2026, 12, 31)))
    val untilNone = state(newLesson.draft, sheet = FormSheet.Until(YearMonth(2026, 9), null))
    val kindBuiltIn = state(newLesson.draft, sheet = FormSheet.Kind(EventFormReducer.kindDraftOf(kinds.first())))
    val kindOwn = state(
        draft(kind = KindRef.Custom(10), date = LocalDate(2026, 10, 1), start = 16 * 60, duration = 60, place = "ДМШ № 3"),
        sheet = FormSheet.Kind(KindDraft(KindRef.Custom(10), "Сольфеджио", 3, KindSign.BOOK)),
    )
    val kindTaken = state(newLesson.draft, sheet = FormSheet.Kind(KindDraft(null, "урок", 6, KindSign.HAT), problem = KindNameProblem.Taken("Урок")))

    private val october19 = events[3]

    val scopeEdit = state(
        editLesson.draft,
        sheet = FormSheet.Scope(
            ScopeAsk(
                question = ScopeQuestion.Both(EditScope.FOLLOWING), word = SeriesWord.LESSON, date = october19.date, moved = null,
                change = ChangeLine(
                    ChangedField.TIME, october19.draft(), october19.draft().copy(startMinutes = 17 * 60 + 30), Repeat.WEEKLY, Repeat.WEEKLY, kinds.first(), kinds.first(),
                ),
                following = AffectedDates(listOf(LocalDate(2026, 10, 19), LocalDate(2026, 10, 26)), andOn = true),
            ),
        ),
        isNew = false,
        edited = october19,
    )
    val scopeMove = state(
        editLesson.draft,
        sheet = FormSheet.Scope(
            ScopeAsk(
                question = ScopeQuestion.Both(EditScope.ONLY_THIS), word = SeriesWord.LESSON, date = october19.date,
                moved = Moved(LocalDate(2026, 10, 20), 18 * 60, DayOfWeek.MONDAY, 17 * 60),
                change = ChangeLine(
                    ChangedField.DATE, october19.draft(), october19.draft().copy(date = LocalDate(2026, 10, 20), startMinutes = 18 * 60), Repeat.WEEKLY, Repeat.WEEKLY,
                    kinds.first(), kinds.first(),
                ),
                following = AffectedDates(listOf(LocalDate(2026, 10, 19), LocalDate(2026, 10, 26)), andOn = true),
            ),
        ),
        isNew = false,
        edited = october19,
    )
    /** A teacher given to a lesson that had none: «Преподаватель — → Анна Сергеевна» (review of stage 98б). */
    val scopeTeacher = state(
        editLesson.draft,
        sheet = FormSheet.Scope(
            ScopeAsk(
                question = ScopeQuestion.Both(EditScope.FOLLOWING), word = SeriesWord.LESSON, date = october19.date, moved = null,
                change = ChangeLine(
                    ChangedField.PLACE, october19.draft().copy(place = ""), october19.draft(), Repeat.WEEKLY, Repeat.WEEKLY, kinds.first(), kinds.first(),
                ),
                following = AffectedDates(listOf(LocalDate(2026, 10, 19), LocalDate(2026, 10, 26)), andOn = true),
            ),
        ),
        isNew = false,
        edited = october19,
    )
    val scopeStep = state(
        editLesson.draft,
        sheet = FormSheet.Scope(
            ScopeAsk(
                question = ScopeQuestion.FollowingOnly, word = SeriesWord.LESSON, date = october19.date, moved = null,
                change = ChangeLine(ChangedField.REPEAT, october19.draft(), october19.draft(), Repeat.WEEKLY, Repeat.NONE, kinds.first(), kinds.first()),
                following = AffectedDates(listOf(LocalDate(2026, 10, 19), LocalDate(2026, 10, 26)), andOn = true),
            ),
        ),
        isNew = false,
        edited = october19,
    )
}

/** The status bar of the phone over the screen of the preview. */
private val StatusBar = 24.dp

@Composable
private fun Form(state: EventFormState) {
    ViolinTheme {
        CompositionLocalProvider(LocalReduceMotion provides true) {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).padding(top = StatusBar)) {
                EventFormScreen(state = state, onIntent = {})
            }
        }
    }
}

/** A face of the sheets of the form, at the bottom of the screen it rises over. */
@Composable
private fun Sheet(state: EventFormState) {
    ViolinTheme {
        CompositionLocalProvider(LocalReduceMotion provides true) {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface), contentAlignment = Alignment.BottomCenter) {
                EventFormSheetCard(state)
            }
        }
    }
}

@Preview(name = "Форма · новый урок: «Урок», «Каждую неделю», преподаватель — четыре касания", locale = "ru", device = "spec:width=412dp,height=892dp")
@Composable
private fun NewLessonPreview() = Form(FormSample.newLesson)

@Preview(name = "Форма · выступление: «Сб, 24 окт.», «18:30», «до 20:00», место и название", locale = "ru", device = "spec:width=412dp,height=892dp")
@Composable
private fun ConcertPreview() = Form(FormSample.concert)

@Preview(name = "Форма · весь день: длительности нет, длинное название со счётчиком «69 / 80»", locale = "ru", device = "spec:width=412dp,height=892dp")
@Composable
private fun AllDayPreview() = Form(FormSample.allDay)

@Preview(name = "Форма · своя длительность: чип «2 ч 30 мин» с карандашом на месте «Другая…»", locale = "ru", device = "spec:width=412dp,height=892dp")
@Composable
private fun OwnLengthPreview() = Form(FormSample.ownLength)

@Preview(name = "Форма · правка урока из повтора: «по понедельникам · без конца» без ссылки, заметки", locale = "ru", device = "spec:width=412dp,height=1100dp")
@Composable
private fun EditLessonPreview() = Form(FormSample.editLesson)

@Preview(name = "Форма · 360 × 640, шрифт 1,3: дата и время одна под другой, плитки растут", locale = "ru", fontScale = 1.3f, device = "spec:width=360dp,height=640dp")
@Composable
private fun SmallPreview() = Form(FormSample.newLesson)

@Preview(name = "Форма · de, 360, шрифт 1,3: «Unterricht» целиком, сегмент повтора в две строки", locale = "de", fontScale = 1.3f, device = "spec:width=360dp,height=640dp")
@Composable
private fun GermanPreview() = Form(FormSample.newLesson)

@Preview(name = "Форма · de, 360, шрифт 1,3: повтор до 31 декабря — «montags · bis 31. Dez. · 14 Unterrichtsstunden» одной фразой", locale = "de", fontScale = 1.3f, device = "spec:width=360dp,height=640dp")
@Composable
private fun GermanUntilPreview() = Form(FormSample.lessonUntil)

@Preview(name = "Форма · 320, шрифт 1,3: урок 23:30 на 1 ч 30 мин — «Длительность» целиком, конец справа по словам", locale = "ru", fontScale = 1.3f, device = "spec:width=320dp,height=640dp")
@Composable
private fun LateLessonNarrowPreview() = Form(FormSample.lateLesson)

@Preview(name = "Форма · fr, 360: «Prestation», «Toutes les deux semaines»", locale = "fr", device = "spec:width=360dp,height=640dp")
@Composable
private fun FrenchPreview() = Form(FormSample.concert)

@Preview(name = "Форма · landscape 892 × 412: шапка 48, колонка 560 по центру", locale = "ru", device = "spec:width=892dp,height=412dp")
@Composable
private fun LandscapePreview() = Form(FormSample.concert)

@Preview(name = "Лист «Дата»: урок завтра, чипы «Сегодня · Завтра · пн 5 окт.», сетка без заливки", locale = "ru", device = "spec:width=412dp,height=892dp")
@Composable
private fun DatePreview() = Sheet(FormSample.date)

@Preview(name = "Лист «Дата»: 24 октября, метки уроков, концерта и оркестра, легенда месяца", locale = "ru", device = "spec:width=412dp,height=892dp")
@Composable
private fun DateConcertPreview() = Sheet(FormSample.dateConcert)

@Preview(name = "Лист «Время»: колёса 18 : 30, «до 20:00 · 1 ч 30 мин», «Частое»", locale = "ru", device = "spec:width=412dp,height=892dp")
@Composable
private fun TimePreview() = Sheet(FormSample.time)

@Preview(name = "Лист «Время» · «Весь день» включён: фраза вместо колёс", locale = "ru", device = "spec:width=412dp,height=892dp")
@Composable
private fun TimeAllDayPreview() = Sheet(FormSample.timeAllDay)

@Preview(name = "Лист «Время» · после полуночи: «до 01:00, вс 25 окт.»", locale = "ru", device = "spec:width=412dp,height=892dp")
@Composable
private fun TimeLatePreview() = Sheet(FormSample.timeLate)

@Preview(name = "Лист «Время» · landscape 892 × 412: колёса слева, «Весь день» и «Частое» справа", locale = "ru", device = "spec:width=892dp,height=412dp")
@Composable
private fun TimeLandscapePreview() = Sheet(FormSample.time)

@Preview(name = "Лист «Длительность»: 2 ч 30 мин, «с 11:00 до 13:30», подсказка", locale = "ru", device = "spec:width=412dp,height=892dp")
@Composable
private fun DurationPreview() = Sheet(FormSample.duration)

@Preview(name = "Лист «Длительность» · потолок 8 ч: «+» 0,38 и «8 ч — самое длинное…»", locale = "ru", device = "spec:width=412dp,height=892dp")
@Composable
private fun DurationCeilingPreview() = Sheet(FormSample.durationCeiling)

@Preview(name = "Лист «Повторять до»: до 31 декабря, «последний урок — пн 28 декабря · всего 14»", locale = "ru", device = "spec:width=412dp,height=892dp")
@Composable
private fun UntilPreview() = Sheet(FormSample.until)

@Preview(name = "Лист «Повторять до» · без конца: «каждый понедельник с 28 сентября», дни до первого урока 0,38", locale = "ru", device = "spec:width=412dp,height=892dp")
@Composable
private fun UntilNonePreview() = Sheet(FormSample.untilNone)

@Preview(name = "Лист «Вид» · встроенный: только цвет и почему не переименовать", locale = "ru", device = "spec:width=412dp,height=892dp")
@Composable
private fun KindBuiltInPreview() = Sheet(FormSample.kindBuiltIn)

@Preview(name = "Лист «Вид» · свой: «Удалить вид…», имя, цвет, знаки 6 × 2", locale = "ru", device = "spec:width=412dp,height=892dp")
@Composable
private fun KindOwnPreview() = Sheet(FormSample.kindOwn)

@Preview(name = "Лист «Вид» · новый с занятым именем: причина над «Готово» 0,38", locale = "ru", device = "spec:width=412dp,height=892dp")
@Composable
private fun KindTakenPreview() = Sheet(FormSample.kindTaken)

@Preview(name = "Лист «Вид» · свой на 320: знаки 4 × 3", locale = "ru", device = "spec:width=320dp,height=640dp")
@Composable
private fun KindNarrowPreview() = Sheet(FormSample.kindOwn)

/** A dialog of R1 over the form, as its window shows it: the scrim and the card in the middle (a preview draws no window). */
@Composable
private fun DialogOver(state: EventFormState, card: @Composable () -> Unit) {
    ViolinTheme {
        CompositionLocalProvider(LocalReduceMotion provides true) {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).padding(top = StatusBar)) {
                EventFormScreen(state = state.copy(dialog = null), onIntent = {})
                Box(Modifier.fillMaxSize().background(ViolinTheme.sheetScrim))
                Box(Modifier.align(Alignment.Center).padding(horizontal = DialogSide)) { card() }
            }
        }
    }
}

private val DialogSide = 24.dp

@Preview(name = "«Удалить вид?» с числом событий: «Отмена» акцентом, «Удалить» кораллом", locale = "ru", device = "spec:width=412dp,height=892dp")
@Composable
private fun KindDeletePreview() = DialogOver(FormSample.kindOwn) {
    AppDialogCard(
        title = stringResource(Res.string.event_kind_delete_title),
        confirm = stringResource(Res.string.session_delete_confirm),
        onConfirm = {},
        onDismiss = {},
        text = stringResource(Res.string.event_kind_delete_text, "Сольфеджио", 12, stringResource(Res.string.event_kind_other)) + " " +
            stringResource(Res.string.event_kind_delete_keeps),
        confirmTone = DialogTone.Danger,
        bin = true,
    )
}

@Preview(name = "«Урок повторяется» · правка времени: залит «Этот и следующие · 19, 26 окт. и дальше»", locale = "ru", device = "spec:width=412dp,height=892dp")
@Composable
private fun ScopeEditPreview() = Sheet(FormSample.scopeEdit)

@Preview(name = "«Урок повторяется» · перенос: залит «Только этот урок · остальные — по понедельникам в 17:00»", locale = "ru", device = "spec:width=412dp,height=892dp")
@Composable
private fun ScopeMovePreview() = Sheet(FormSample.scopeMove)

@Preview(name = "«Урок повторяется» · новый преподаватель: «Преподаватель — → Анна Сергеевна» целиком", locale = "ru", device = "spec:width=412dp,height=892dp")
@Composable
private fun ScopeTeacherPreview() = Sheet(FormSample.scopeTeacher)

@Preview(name = "«Урок повторяется» · новый преподаватель, 360, шрифт 1,3: подпись над значениями", locale = "ru", fontScale = 1.3f, device = "spec:width=360dp,height=640dp")
@Composable
private fun ScopeTeacherNarrowPreview() = Sheet(FormSample.scopeTeacher)

@Preview(name = "«Урок повторяется» · «Не повторять»: один ответ «Этот и следующие»", locale = "ru", device = "spec:width=412dp,height=892dp")
@Composable
private fun ScopeStepPreview() = Sheet(FormSample.scopeStep)

@Preview(name = "«Урок повторяется» · перенос, de, 360, шрифт 1,3: ответы растут по словам", locale = "de", fontScale = 1.3f, device = "spec:width=360dp,height=640dp")
@Composable
private fun ScopeMoveGermanPreview() = Sheet(FormSample.scopeMove)

@Preview(name = "Форма · «Не сохранять?» нового: «Введённое не сохранится.»", locale = "ru", device = "spec:width=412dp,height=892dp")
@Composable
private fun DiscardPreview() = DialogOver(FormSample.newLesson) {
    AppDialogCard(
        title = stringResource(Res.string.piece_form_discard_title),
        confirm = stringResource(Res.string.piece_form_discard_confirm),
        onConfirm = {},
        onDismiss = {},
        text = stringResource(Res.string.dialog_discard_typed),
        dismiss = stringResource(Res.string.dialog_back),
        confirmTone = DialogTone.Quiet,
    )
}
