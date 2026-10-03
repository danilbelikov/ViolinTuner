package com.violinjourney.app.feature.events

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.violinjourney.app.core.domain.Zone
import com.violinjourney.app.core.domain.events.BuiltInKind
import com.violinjourney.app.core.domain.events.CalendarEvent
import com.violinjourney.app.core.domain.events.EditScope
import com.violinjourney.app.core.domain.events.EventsConfig
import com.violinjourney.app.core.domain.events.KindRef
import com.violinjourney.app.core.domain.events.KindRules
import com.violinjourney.app.core.domain.events.SessionEvent
import com.violinjourney.app.core.domain.repertoire.Piece
import com.violinjourney.app.core.domain.repertoire.PieceStatus
import com.violinjourney.app.core.domain.session.SessionSummary
import com.violinjourney.app.core.ui.components.AppButtonStyle
import com.violinjourney.app.core.ui.components.appButtonWidth
import com.violinjourney.app.core.ui.format.Formats
import com.violinjourney.app.core.ui.theme.ViolinTheme
import com.violinjourney.app.feature.events.screen.AddRecordSheetContent
import com.violinjourney.app.feature.events.screen.EventIntent
import com.violinjourney.app.feature.events.screen.EventReducer
import com.violinjourney.app.feature.events.screen.EventScreen
import com.violinjourney.app.feature.events.screen.EventSheet
import com.violinjourney.app.feature.events.screen.EventSheetCard
import com.violinjourney.app.feature.events.screen.EventState
import com.violinjourney.app.feature.events.screen.RecordWay
import com.violinjourney.app.feature.events.screen.SeriesWord
import com.violinjourney.app.feature.repertoire.piece.TakeState
import com.violinjourney.app.shared.resources.Res
import com.violinjourney.app.shared.resources.card_menu
import com.violinjourney.app.shared.resources.dialog_cancel
import com.violinjourney.app.shared.resources.event_add_mic
import com.violinjourney.app.shared.resources.block_played_title
import com.violinjourney.app.shared.resources.event_add_record
import com.violinjourney.app.shared.resources.event_add_to_program
import com.violinjourney.app.shared.resources.event_can_add_note
import com.violinjourney.app.shared.resources.event_mic_reason
import com.violinjourney.app.shared.resources.event_program
import com.violinjourney.app.shared.resources.event_program_remove
import com.violinjourney.app.shared.resources.event_recording_description
import com.violinjourney.app.shared.resources.event_series_delete_q_lesson
import com.violinjourney.app.shared.resources.event_series_following_lesson
import com.violinjourney.app.shared.resources.event_series_from_date
import com.violinjourney.app.shared.resources.event_series_past_short_lesson
import com.violinjourney.app.shared.resources.event_series_this_lesson
import com.violinjourney.app.shared.resources.event_rest_on_weekdays
import com.violinjourney.app.shared.resources.nav_history
import com.violinjourney.app.shared.resources.piece_edit
import com.violinjourney.app.shared.resources.piece_field_notes
import com.violinjourney.app.shared.resources.piece_notes_add
import com.violinjourney.app.shared.resources.practice_day_add
import com.violinjourney.app.shared.resources.practice_pair_description
import com.violinjourney.app.shared.resources.session_take_title
import com.violinjourney.app.shared.resources.take_grant_permission
import com.violinjourney.app.testing.TEST_WINDOW
import com.violinjourney.app.testing.TestWindow
import com.violinjourney.app.testing.assertWordsWhole
import com.violinjourney.app.testing.textLayout
import java.util.Locale
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import org.jetbrains.compose.resources.stringArrayResource
import org.jetbrains.compose.resources.stringResource
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The screen of an event (spec 3.36.9, 5.29 R9) where its layout is the risk: upright, a performance after its day is opened for its
 * records — «Записи», «Программа», «Заметки» in this order and «Добавить запись» pinned at 56 — its cards only opened, with no «⋯» and
 * no long press; the name wraps whole on 360 × 640 at the font 1.3; lying, the left column is min(372, 45 % of the window) — 372 at
 * 892 × 412, 288 at 640 × 360, 271 in the window of the emulator 603 × 308 — and the bottom zone is the right column's, its button of
 * 48 in a window no higher than 360; a running recording is the bar that says «Идёт запись, 0:05»; a row of the programme is one line
 * with an ellipsis, its cross a target of 48. The sheet «Добавить запись» without the microphone: the reason whole and «Разрешить
 * доступ» a word of 48 of its own, the row itself not pressed; with it, the row records. The sheet of a repeat: its answers grow with
 * their words, never cut, in German at 1.3 too. Laid out in a window of its own size ([TestWindow]); the words read in the composition.
 *
 * After the review of stage 98a: lying, the name comes to the bar only once its column has scrolled it away, and keeps its words whole
 * at 1.3; «+ Добавить» keeps its word whole beside «Что играли» and the title gives way; the cross reaches 12 into the card; in a low
 * window the answers of a repeat end what scrolls under the question, in a taller one they are pinned; each line of an answer stands in
 * the middle of its button, and each answer says its own scope.
 */
@RunWith(AndroidJUnit4::class)
class EventScreenTest {
    @get:Rule
    val compose = createComposeRule()

    /** The language of the device, given back after every test. */
    private val deviceLanguage: Locale = Locale.getDefault()

    @After
    fun backToTheLanguageOfTheDevice() = Locale.setDefault(deviceLanguage)

    private val config = EventsConfig()
    private val kinds = KindRules.all(emptyList(), config)
    private val zone = TimeZone.of("Europe/Moscow")
    private val today = LocalDate(2026, 10, 24)
    private val intents = mutableListOf<EventIntent>()
    private val words = mutableMapOf<String, String>()

    private fun word(key: String): String = words.getValue(key)

    private fun concert(title: String = CONCERT) = CalendarEvent(
        id = 1, kind = KindRef.BuiltIn(BuiltInKind.PERFORMANCE), date = today, startMinutes = 18 * 60 + 30, durationMinutes = 90, title = title,
        place = "Малый зал музыкальной школы", notes = "Сбор в 17:30, аккомпанирует Ирина Павловна.", seriesId = null, detached = false,
        createdAtEpochMs = 0,
    )

    private fun piece(id: Long, title: String, composer: String) = Piece(
        id = id, title = title, composer = composer, key = null, tempoBpm = null, status = PieceStatus.LEARNING, notes = "", createdAtEpochMs = 0,
        updatedAtEpochMs = id,
    )

    private val record = SessionSummary(
        id = 9, title = null, startedAtEpochMs = LocalDateTime(2026, 10, 24, 19, 2).toInstant(zone).toEpochMilliseconds(), durationMs = 220_000,
        a4Hz = 440.0, toleranceCents = 8.0, nearCents = 20.0, scorePercent = 80, nearPercent = 15, offPercent = 5, maeCents = 4.0,
        biasCents = 1.0, previewZones = listOf(Zone.IN_TUNE), audioPath = "9.m4a", eventId = 1,
    )

    /** A lesson of today, after it: «Заметки», «Что играли» with «+ Добавить», «Записи» with «+ Добавить». */
    private fun lesson() = concert().copy(kind = KindRef.BuiltIn(BuiltInKind.LESSON), title = "", place = "Анна Сергеевна", seriesId = null)

    private fun state(event: CalendarEvent = concert(), pieceTitle: String = "Концерт ля минор, 1 ч.") = EventReducer.loadedOf(
        event = event, kinds = kinds, series = emptyList(), programIds = listOf(3), pieces = listOf(piece(3, pieceTitle, "А. Вивальди")),
        groups = emptyList(), sessions = listOf(record), recordEvent = SessionEvent(1, event.title, event.date, event.kind, null), today = today,
        zone = zone, config = config, notesCollapsedLines = 6,
    )

    /** [event] with nothing of its own yet: no programme, no records. */
    private fun bare(event: CalendarEvent) = EventReducer.loadedOf(
        event = event, kinds = kinds, series = emptyList(), programIds = emptyList(), pieces = emptyList(), groups = emptyList(),
        sessions = emptyList(), recordEvent = SessionEvent(event.id, event.title, event.date, event.kind, null), today = today, zone = zone,
        config = config, notesCollapsedLines = 6,
    )

    private val idle = TakeState.idle(micPermission = true, bars = 14)

    private fun show(
        state: EventState,
        size: DpSize,
        fontScale: Float = 1f,
        take: TakeState = idle,
    ) {
        val takeState = mutableStateOf(take)
        compose.setContent {
            words[RECORDS] = stringResource(Res.string.nav_history)
            words[PROGRAM] = stringResource(Res.string.event_program)
            words[NOTES] = stringResource(Res.string.piece_field_notes)
            words[ADD] = stringResource(Res.string.event_add_record)
            words[MORE] = stringResource(Res.string.card_menu)
            words[RUNNING] = stringResource(Res.string.event_recording_description, Formats.timer(RECORDED_SECONDS * 1_000L))
            words[RECORD_TITLE] = stringResource(Res.string.session_take_title, CONCERT, Formats.dayAndMonth(today))
            words[EDIT] = stringResource(Res.string.piece_edit)
            words[ADD_NOTE] = stringResource(Res.string.piece_notes_add)
            words[CAN_NOTE] = stringResource(Res.string.event_can_add_note)
            words[PLAYED] = stringResource(Res.string.block_played_title)
            ViolinTheme {
                TestWindow(size, fontScale = fontScale) {
                    EventScreen(state = state, take = takeState, onIntent = { intents += it }, zone = zone)
                }
            }
        }
        compose.waitForIdle()
    }

    private fun window() = compose.onNodeWithTag(TEST_WINDOW).getUnclippedBoundsInRoot()

    private fun heading(text: String) = compose.onNode(hasText(text) and isHeading(), useUnmergedTree = true).getUnclippedBoundsInRoot()

    private fun addButton() = compose.onNode(hasText(word(ADD)) and hasClickAction())

    @Test
    fun uprightAPerformanceAfterItsDayIsOpenedForItsRecordsAndItsCardsAreOnlyOpened() {
        show(state(), DpSize(412.dp, 892.dp))
        val records = heading(word(RECORDS))
        val program = heading(word(PROGRAM))
        val notes = heading(word(NOTES))
        assertTrue("«Записи» first: $records, $program", records.bottom <= program.top)
        assertTrue("then «Программа», then «Заметки»: $program, $notes", program.bottom <= notes.top)
        addButton().assertHeightIsAtLeast(56.dp)

        // the card of the record: only opened — no «⋯» of its own (the one «Ещё» is of the bar) and no long press
        compose.onAllNodesWithContentDescription(word(MORE)).assertCountEquals(1)
        val card = compose.onNode(
            SemanticsMatcher("the card of the record") { node ->
                node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty().any { word(RECORD_TITLE) in it }
            },
        )
        assertNull("no long press", card.fetchSemanticsNode().config.getOrNull(SemanticsActions.OnLongClick))
        card.performClick()
        assertEquals(listOf<EventIntent>(EventIntent.RecordClicked(9)), intents)
    }

    @Test
    fun theNameWrapsWholeOnASmallPhoneAtALargeFont() {
        show(state(concert(LONG_TITLE)), DpSize(360.dp, 640.dp), fontScale = 1.3f)
        val name = compose.onNode(hasText(LONG_TITLE) and isHeading(), useUnmergedTree = true)
        assertTrue("the name wraps", name.textLayout().lineCount > 1)
        assertWordsWhole(name, LONG_TITLE)
    }

    @Test
    fun lyingIn892x412TheLeftColumnIs372AndTheZoneIsTheRightColumns() = assertLying(DpSize(892.dp, 412.dp), left = 372.dp, button = 56.dp)

    @Test
    fun lyingIn640x360TheLeftColumnIs288AndTheButtonIs48() = assertLying(DpSize(640.dp, 360.dp), left = 288.dp, button = 48.dp)

    @Test
    fun lyingInTheWindowOfTheEmulatorTheLeftColumnIs45PercentOfIt() = assertLying(DpSize(603.dp, 308.dp), left = 271.35.dp, button = 48.dp)

    /** Lying: the head in the left column of [left]; «Добавить запись» the right column's, 16 from its rule and from the edge, [button] high. */
    private fun assertLying(size: DpSize, left: Dp, button: Dp) {
        show(state(), size)
        val window = window()
        val zone = addButton().getUnclippedBoundsInRoot()
        val side = 16.dp
        val rule = 1.dp
        assertEquals("the zone begins past the left column and its rule", (left + rule + side).value, (zone.left - window.left).value, 0.6f)
        assertEquals("and ends 16 from the edge", (size.width - side).value, (zone.right - window.left).value, 0.6f)
        assertEquals("its button", button.value, zone.height.value, 0.6f)
        val name = compose.onNode(hasText(CONCERT) and isHeading(), useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertTrue("the name in the left column: $name", name.right - window.left <= left - side + 0.5.dp)
        val records = heading(word(RECORDS))
        assertTrue("the records in the right column: $records", records.left - window.left >= left)
    }

    @Test
    fun aRunningRecordingIsTheBarThatSaysItsTime() {
        show(state(), DpSize(412.dp, 892.dp), take = idle.copy(recording = true, elapsedSeconds = RECORDED_SECONDS))
        compose.onNodeWithContentDescription(word(RUNNING)).assertExists()
        assertTrue("the bar stands in the place of «${word(ADD)}»", compose.onAllNodes(hasText(word(ADD)) and hasClickAction()).fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun aRowOfTheProgrammeIsOneLineAndItsCrossIsATargetOf48() {
        val long = "Концерт для скрипки с оркестром ля минор, соч. 3 № 6, RV 356, первая часть"
        var remove = ""
        val take = mutableStateOf(idle)
        compose.setContent {
            remove = stringResource(Res.string.event_program_remove, long)
            ViolinTheme { TestWindow(DpSize(360.dp, 640.dp), fontScale = 1.3f) { EventScreen(state(pieceTitle = long), take, onIntent = { intents += it }, zone = zone) } }
        }
        compose.waitForIdle()
        val title = compose.onNodeWithText(long, useUnmergedTree = true).textLayout()
        assertEquals("one line", 1, title.lineCount)
        assertTrue("cut with an ellipsis", title.isLineEllipsized(0))
        compose.onNodeWithContentDescription(remove).assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp).performClick()
        assertEquals(listOf<EventIntent>(EventIntent.ProgramRemoved(3)), intents)
    }

    /**
     * Lying, the name stands large at the top of the left column, and the bar is without it until that column has scrolled it away (spec
     * 3.36.9: «прокрученное за шапку название встаёт в неё»; review of stage 98a — it stood there twice). On the code before the review
     * the bar had it from the first frame: two headings of the name at once.
     */
    @Test
    fun lyingTheNameComesToTheBarOnlyOnceItsColumnHasScrolledItAway() {
        show(state(concert(LONG_TITLE).copy(notes = LONG_NOTES)), DpSize(640.dp, 360.dp))
        val names = compose.onAllNodes(hasText(LONG_TITLE) and isHeading(), useUnmergedTree = true)
        names.assertCountEquals(1)
        // a drag inside the left column (288 wide): the name goes up under the bar
        compose.onNodeWithTag(TEST_WINDOW).performTouchInput {
            val x = 120.dp.toPx()
            swipe(start = Offset(x, height - 24.dp.toPx()), end = Offset(x, 64.dp.toPx()), durationMillis = 400)
        }
        compose.waitForIdle()
        // the large one stays in its column, scrolled away; the other is the bar's
        names.assertCountEquals(2)
    }

    /**
     * Lying at the font 1.3, the name keeps its words whole in the left column (spec 3.36.9: «переносится целиком»): «международного» —
     * 267 dp at 24 sp — does not fit 256 (640 × 360) or 239 (the window of the emulator), and the name is smaller instead (24 → 18 sp).
     * On the code before the review the word broke by the letter.
     */
    @Test
    fun lyingAtALargeFontALongNameKeepsItsWordsWholeIn640x360() = assertTheNameWhole(DpSize(640.dp, 360.dp))

    @Test
    fun lyingAtALargeFontALongNameKeepsItsWordsWholeInTheWindowOfTheEmulator() = assertTheNameWhole(DpSize(603.dp, 308.dp))

    private fun assertTheNameWhole(size: DpSize) {
        show(state(concert(LONG_TITLE)), size, fontScale = 1.3f)
        assertWordsWhole(compose.onNode(hasText(LONG_TITLE) and isHeading(), useUnmergedTree = true), "$size: $LONG_TITLE")
    }

    /**
     * «+ Добавить» beside «Что играли» keeps its word whole and the title gives way with an ellipsis (spec 3.36.9, «Маленький экран и
     * крупный шрифт»): in German on 360 × 640 at 1.3 «Was gespielt wurde» took 187 dp first and squeezed «Hinzufügen» to 83 dp, broken
     * by the letter; in Italian lying in the window of the emulator «Cosa hai suonato» did the same to «Aggiungi».
     */
    @Test
    fun inGermanAtALargeFontTheWordOfAddStandsWholeBesideWhatWasPlayed() = assertTheWordOfAdd("de", DpSize(360.dp, 640.dp))

    @Test
    fun inItalianLyingAtALargeFontTheWordOfAddStandsWholeBesideWhatWasPlayed() = assertTheWordOfAdd("it", DpSize(603.dp, 308.dp))

    private fun assertTheWordOfAdd(language: String, size: DpSize) {
        Locale.setDefault(Locale.forLanguageTag(language))
        var need = 0.dp
        val take = mutableStateOf(idle)
        compose.setContent {
            words[PLAYED] = stringResource(Res.string.block_played_title)
            words[ADD_TO_PROGRAM] = stringResource(Res.string.event_add_to_program)
            ViolinTheme {
                TestWindow(size, fontScale = 1.3f) {
                    need = appButtonWidth(stringResource(Res.string.practice_day_add), AppButtonStyle.Text, icon = true)
                    EventScreen(state = state(lesson()), take = take, onIntent = { intents += it }, zone = zone)
                }
            }
        }
        compose.waitForIdle()
        val add = compose.onNodeWithContentDescription(word(ADD_TO_PROGRAM)).getUnclippedBoundsInRoot()
        // the width its words need on one line, less the slack of a pixel the measure keeps
        assertTrue("$language: the button is as wide as its words — ${add.width} of $need", add.width >= need - 1.5.dp)
        val titleNode = compose.onNode(hasText(word(PLAYED)) and isHeading(), useUnmergedTree = true)
        val title = titleNode.getUnclippedBoundsInRoot()
        assertTrue("$language: the title ends before the button: $title, $add", title.right <= add.left - 8.dp + 0.6.dp)
        assertTrue("$language: the title gives way, cut with an ellipsis", titleNode.textLayout().isLineEllipsized(0))
    }

    /**
     * The cross of a row of the programme reaches 12 into the field of the card, its icon 17 from the edge (events-views.html 5:
     * `margin-right: -12px`), and the row's own press goes right up to it: the target ends 18 from the edge of the window of 360 (16 of
     * the screen, 14 of the card, 12 back). On the code before the review it ended 30 from it.
     */
    @Test
    fun theCrossOfARowOfTheProgrammeReaches12IntoTheFieldOfItsCard() {
        var remove = ""
        var row = ""
        val take = mutableStateOf(idle)
        compose.setContent {
            remove = stringResource(Res.string.event_program_remove, PIECE)
            row = stringResource(
                Res.string.practice_pair_description, stringResource(Res.string.practice_pair_description, "1", PIECE), "А. Вивальди",
            )
            ViolinTheme { TestWindow(DpSize(360.dp, 640.dp)) { EventScreen(state(), take, onIntent = { intents += it }, zone = zone) } }
        }
        compose.waitForIdle()
        val window = window()
        val cross = compose.onNodeWithContentDescription(remove).getUnclippedBoundsInRoot()
        assertEquals("the target ends 18 from the edge", 18f, (window.right - cross.right).value, 0.6f)
        assertEquals(48f, cross.width.value, 0.6f)
        val press = compose.onNodeWithContentDescription(row).getUnclippedBoundsInRoot()
        assertEquals("the row's press goes up to the cross", cross.left.value, press.right.value, 0.6f)
    }

    /** «Изменить» in the bar opens the form of the event (stage 98б). */
    @Test
    fun editInTheBarOpensTheForm() {
        show(state(), DpSize(412.dp, 892.dp))
        compose.onNode(hasText(word(EDIT)) and hasClickAction()).performClick()
        assertEquals(listOf<EventIntent>(EventIntent.EditClicked), intents)
    }

    /**
     * A rehearsal to come without notes (spec 3.36.9): «Заметки» with «Добавить заметку» alone — a rehearsal asks nothing — which opens
     * the form at its notes. Before the form was there such a kind had no part of notes at all.
     */
    @Test
    fun aRehearsalWithoutNotesOffersToAddOne() {
        val rehearsal = concert().copy(kind = KindRef.BuiltIn(BuiltInKind.REHEARSAL), date = LocalDate(2026, 10, 30), title = "", notes = "")
        show(bare(rehearsal), DpSize(412.dp, 892.dp))
        heading(word(NOTES))
        compose.onNode(hasText(word(ADD_NOTE)) and hasClickAction()).performClick()
        assertEquals(listOf<EventIntent>(EventIntent.AddNotesClicked), intents)
    }

    /** A lesson of today with nothing yet: «Можно добавить», its first row «Заметку» — the form at its notes. */
    @Test
    fun aLessonWithNothingYetOffersANoteFirst() {
        show(bare(lesson().copy(notes = "")), DpSize(412.dp, 892.dp))
        val note = compose.onNode(hasText(word(CAN_NOTE)) and hasClickAction())
        val played = compose.onNode(hasText(word(PLAYED)) and hasClickAction())
        assertTrue("«${word(CAN_NOTE)}» over «${word(PLAYED)}»", note.getUnclippedBoundsInRoot().bottom <= played.getUnclippedBoundsInRoot().top)
        note.performClick()
        assertEquals(listOf<EventIntent>(EventIntent.AddNotesClicked), intents)
    }

    /** The sheet of the deletion of a lesson of a repeat, as the card of a preview lays it, in a room of [room] in a window of [size]. */
    private fun showTheDeletionOfARepeat(size: DpSize, room: Dp) {
        val sheet = EventSheet.DeleteScope(SeriesWord.LESSON, date = LESSON_DAY, weekday = DayOfWeek.MONDAY, from = LocalDate(2026, 10, 26))
        compose.setContent {
            words[QUESTION] = stringResource(Res.string.event_series_delete_q_lesson, Formats.dayAndMonth(LESSON_DAY))
            words[THIS] = stringResource(Res.string.event_series_this_lesson)
            words[FOLLOWING] = stringResource(Res.string.event_series_following_lesson)
            words[REST] = stringArrayResource(Res.array.event_rest_on_weekdays).first()
            words[CANCEL] = stringResource(Res.string.dialog_cancel)
            ViolinTheme {
                TestWindow(size) {
                    Box(Modifier.fillMaxWidth().height(room)) {
                        EventSheetCard(state(lesson()).copy(sheet = sheet), Modifier.fillMaxHeight(), onIntent = { intents += it })
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    private fun answer(key: String) = compose.onNode(hasText(word(key), substring = true) and hasClickAction())

    /**
     * In a window no higher than 360 the answers of the sheet of a repeat end what scrolls, after the note (5.29 R9, «Уточнено на этапе
     * 98а»): pinned, 214 dp of them left the question no room in the sheet of 640 × 360 (review of stage 98a). The question stands over
     * the first answer, in sight; each answer says its scope.
     */
    @Test
    fun inALowWindowTheQuestionOfARepeatStandsOverItsAnswersAndTheyEndWhatScrolls() {
        showTheDeletionOfARepeat(DpSize(640.dp, 360.dp), room = LOW_SHEET)
        val card = window()
        val question = compose.onNodeWithText(word(QUESTION), useUnmergedTree = true).getUnclippedBoundsInRoot()
        val first = answer(THIS).getUnclippedBoundsInRoot()
        assertTrue("the question over the first answer: $question, $first", question.bottom <= first.top)
        assertTrue("the question in sight: $question", question.bottom - card.top <= LOW_SHEET)
        answer(THIS).performClick()
        answer(FOLLOWING).performClick()
        assertEquals(
            "«Только этот урок» and «Этот и следующие», each its own scope",
            listOf<EventIntent>(EventIntent.DeleteConfirmed(EditScope.ONLY_THIS), EventIntent.DeleteConfirmed(EditScope.FOLLOWING)),
            intents,
        )
    }

    /**
     * In a window higher than 360 the answers are pinned at the bottom of the sheet, under the thumb — lying in 892 × 412, a sheet shorter
     * than its face: «Отмена» 16 over its edge, the face scrolling above — and each line of an answer stands in the middle of its button
     * (events-form.html 6): on the code before the review «Только этот урок» stood at the edge of the block of its longer caption.
     */
    @Test
    fun inATallerWindowTheAnswersArePinnedAndEachLineStandsInTheMiddleOfItsButton() {
        showTheDeletionOfARepeat(DpSize(892.dp, 412.dp), room = SHORT_SHEET)
        val card = window()
        val cancel = compose.onNode(hasText(word(CANCEL)) and hasClickAction()).getUnclippedBoundsInRoot()
        assertEquals("pinned at the bottom", (card.top + SHORT_SHEET - 16.dp).value, cancel.bottom.value, 0.6f)
        val button = answer(THIS).getUnclippedBoundsInRoot()
        for (key in listOf(THIS, REST)) {
            val line = compose.onNodeWithText(word(key), useUnmergedTree = true).getUnclippedBoundsInRoot()
            assertEquals("«${word(key)}» in the middle of its button", ((button.left + button.right) / 2).value, ((line.left + line.right) / 2).value, 1f)
        }
    }

    /** Without the microphone, in [language] on 360 at 1.3: the reason whole, «Разрешить доступ» a word of 48 of its own. */
    private fun assertTheRowWithoutTheMicrophone(language: String) {
        Locale.setDefault(Locale.forLanguageTag(language))
        compose.setContent {
            words[REASON] = stringResource(Res.string.event_mic_reason)
            words[GRANT] = stringResource(Res.string.take_grant_permission)
            words[MIC] = stringResource(Res.string.event_add_mic)
            ViolinTheme {
                TestWindow(DpSize(360.dp, 640.dp), fontScale = 1.3f) {
                    Column { AddRecordSheetContent(micPermission = false, onIntent = { intents += it }) }
                }
            }
        }
        compose.waitForIdle()
        assertWordsWhole(compose.onNodeWithText(word(REASON), useUnmergedTree = true), "$language: ${word(REASON)}")
        assertWordsWhole(compose.onNodeWithText(word(GRANT), useUnmergedTree = true), "$language: ${word(GRANT)}")
        assertTrue(
            "$language: the row itself is not pressed",
            compose.onAllNodes(hasText(word(MIC)) and hasClickAction()).fetchSemanticsNodes().isEmpty(),
        )
        compose.onNode(hasText(word(GRANT)) and hasClickAction()).assertHeightIsAtLeast(48.dp).performClick()
        assertEquals(listOf<EventIntent>(EventIntent.GrantMicClicked), intents)
    }

    @Test
    fun withoutTheMicrophoneTheRowSaysWhyInRussian() = assertTheRowWithoutTheMicrophone("ru")

    @Test
    fun withoutTheMicrophoneTheRowSaysWhyInGerman() = assertTheRowWithoutTheMicrophone("de")

    @Test
    fun withoutTheMicrophoneTheRowSaysWhyInFrench() = assertTheRowWithoutTheMicrophone("fr")

    @Test
    fun withTheMicrophoneTheRowRecordsByItsPress() {
        compose.setContent {
            words[MIC] = stringResource(Res.string.event_add_mic)
            ViolinTheme { TestWindow(DpSize(412.dp, 892.dp)) { Column { AddRecordSheetContent(micPermission = true, onIntent = { intents += it }) } } }
        }
        compose.waitForIdle()
        compose.onNode(hasText(word(MIC)) and hasClickAction()).assertHeightIsAtLeast(56.dp).performClick()
        assertEquals(listOf<EventIntent>(EventIntent.AddRecordWay(RecordWay.MIC)), intents)
    }

    /** The sheet of a repeat in [language] on 360 at 1.3: the answers at least 60, growing with their words, none cut, the first first. */
    private fun assertTheAnswersOfARepeat(language: String) {
        Locale.setDefault(Locale.forLanguageTag(language))
        val chosen = mutableListOf<EditScope>()
        compose.setContent {
            val date = Formats.dayAndMonth(LocalDate(2026, 10, 19))
            words[QUESTION] = stringResource(Res.string.event_series_delete_q_lesson, date)
            words[THIS] = stringResource(Res.string.event_series_this_lesson)
            words[FOLLOWING] = stringResource(Res.string.event_series_following_lesson)
            words[REST] = stringArrayResource(Res.array.event_rest_on_weekdays).first()
            words[FROM] = stringResource(Res.string.event_series_from_date, date)
            words[CANCEL] = stringResource(Res.string.dialog_cancel)
            ViolinTheme {
                TestWindow(DpSize(360.dp, 640.dp), fontScale = 1.3f) {
                    Column(Modifier.padding(horizontal = 20.dp)) {
                        ScopeSheetContent(question = word(QUESTION), note = stringResource(Res.string.event_series_past_short_lesson), bin = true)
                        ScopeAnswers(
                            answers = listOf(
                                ScopeAnswer(word(THIS), word(REST), AppButtonStyle.OutlineDanger) { chosen += EditScope.ONLY_THIS },
                                ScopeAnswer(word(FOLLOWING), word(FROM), AppButtonStyle.OutlineDanger) { chosen += EditScope.FOLLOWING },
                            ),
                            cancel = word(CANCEL),
                            onCancel = {},
                        )
                    }
                }
            }
        }
        compose.waitForIdle()
        assertWordsWhole(compose.onNodeWithText(word(QUESTION), useUnmergedTree = true), "$language: ${word(QUESTION)}")
        listOf(THIS, REST, FOLLOWING, FROM).forEach { key ->
            assertWordsWhole(compose.onNodeWithText(word(key), useUnmergedTree = true), "$language: ${word(key)}")
        }
        val only = compose.onNode(hasText(word(THIS), substring = true) and hasClickAction()).assertHeightIsAtLeast(60.dp)
        val following = compose.onNode(hasText(word(FOLLOWING), substring = true) and hasClickAction()).assertHeightIsAtLeast(60.dp)
        assertTrue("«${word(THIS)}» first", only.getUnclippedBoundsInRoot().bottom <= following.getUnclippedBoundsInRoot().top)
        following.performClick()
        assertEquals(listOf(EditScope.FOLLOWING), chosen)
    }

    @Test
    fun theAnswersOfARepeatStandWholeInRussian() = assertTheAnswersOfARepeat("ru")

    @Test
    fun theAnswersOfARepeatStandWholeInGerman() = assertTheAnswersOfARepeat("de")

    @Test
    fun theAnswersOfARepeatStandWholeInFrench() = assertTheAnswersOfARepeat("fr")

    private companion object {
        const val CONCERT = "Осенний концерт"
        const val LONG_TITLE = "Отборочный тур международного конкурса скрипачей имени Генрика Венявского"
        const val PIECE = "Концерт ля минор, 1 ч."

        /** Notes long enough for the left column to scroll lying: six lines are seen, then «ещё». */
        const val LONG_NOTES = "Сбор в 17:30 за кулисами, аккомпанирует Ирина Павловна. Перед выходом — гаммы ре мажор и ля минор в две октавы, " +
            "медленно, смычок у колодки. В Вивальди на второй странице не торопиться, в Мелодии кульминацию вести на струне ре. " +
            "После выступления — записать, что получилось и что нет, и показать запись Анне Сергеевне."
        val LESSON_DAY = LocalDate(2026, 10, 19)

        /** The room of a sheet in 640 × 360 under the status bar and its clearance (360 − 24 − 16); one shorter than its face lying in 892 × 412. */
        val LOW_SHEET = 320.dp
        val SHORT_SHEET = 300.dp
        const val PLAYED = "played"
        const val ADD_TO_PROGRAM = "addToProgram"
        const val RECORDED_SECONDS = 5L
        const val RECORDS = "records"
        const val PROGRAM = "program"
        const val NOTES = "notes"
        const val ADD = "add"
        const val MORE = "more"
        const val RUNNING = "running"
        const val RECORD_TITLE = "recordTitle"
        const val REASON = "reason"
        const val GRANT = "grant"
        const val MIC = "mic"
        const val QUESTION = "question"
        const val THIS = "this"
        const val FOLLOWING = "following"
        const val REST = "rest"
        const val FROM = "from"
        const val CANCEL = "cancel"
        const val EDIT = "edit"
        const val ADD_NOTE = "addNote"
        const val CAN_NOTE = "canNote"
    }
}
