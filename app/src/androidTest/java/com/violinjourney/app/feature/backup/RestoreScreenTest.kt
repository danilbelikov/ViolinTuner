package com.violinjourney.app.feature.backup

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.violinjourney.app.core.backup.BackupCandidate
import com.violinjourney.app.core.backup.BackupContents
import com.violinjourney.app.core.backup.BackupCounts
import com.violinjourney.app.core.backup.BackupJob
import com.violinjourney.app.core.backup.BackupManifest
import com.violinjourney.app.core.backup.BackupPart
import com.violinjourney.app.core.ui.format.FormatLanguage
import com.violinjourney.app.core.ui.format.Formats
import com.violinjourney.app.core.ui.theme.ViolinTheme
import com.violinjourney.app.shared.resources.Res
import com.violinjourney.app.shared.resources.backup_close
import com.violinjourney.app.shared.resources.backup_count_days_many
import com.violinjourney.app.shared.resources.backup_count_pieces_few
import com.violinjourney.app.shared.resources.backup_count_pieces_many
import com.violinjourney.app.shared.resources.backup_count_pieces_one
import com.violinjourney.app.shared.resources.backup_count_sessions_few
import com.violinjourney.app.shared.resources.backup_count_sessions_many
import com.violinjourney.app.shared.resources.backup_count_sessions_one
import com.violinjourney.app.shared.resources.dot_separator
import com.violinjourney.app.shared.resources.restore_busy_saving
import com.violinjourney.app.shared.resources.restore_button
import com.violinjourney.app.shared.resources.restore_confirm_text
import com.violinjourney.app.shared.resources.restore_copy_from
import com.violinjourney.app.shared.resources.restore_empty_app
import com.violinjourney.app.shared.resources.restore_no_room_ok
import com.violinjourney.app.shared.resources.restore_now_title
import com.violinjourney.app.shared.resources.restore_save_first
import com.violinjourney.app.shared.resources.restore_unsafe_button
import com.violinjourney.app.shared.resources.restore_warning
import com.violinjourney.app.testing.TEST_WINDOW
import com.violinjourney.app.testing.TestWindow
import com.violinjourney.app.testing.assertWordsWhole
import com.violinjourney.app.testing.textLayout
import java.util.Locale
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import org.jetbrains.compose.resources.stringResource
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * «Восстановить из копии» of R8 (spec 3.36.8, 5.29 R8) by what an eye and a finger meet: over the data the zone is «Сначала сохранить
 * текущие данные» over «Восстановить», 8 apart — lying on the emulator's 640 × 360 (603 × 308, and 603 × 336 without the status bar) a zone of
 * about 120 under which the passport and the second card scroll, the warning last and whole above it; while a copy is saved
 * «Восстановить» sleeps with its reason over it and the safety net is pressed; into an empty app — the main way in — only the passport and
 * the line under it, a plain «Восстановить» that asks nothing; without the room the zone is «Понятно», the safety net and the coral word,
 * three rows lying too, the coral word asleep with its reason while a copy is saved, and only «Понятно» where not even the way without a
 * net would make the room; on 320 × 544 at 1.5 the words of the zone take every line they need, none dropped; the day of the passport
 * keeps to its month; «Заменить данные?» names only what there is; a screen on its way out keeps the face it was closed on, and the face
 * it opens on holds its zone for a double tap. Laid out in a window of its own size ([TestWindow]); the words read in the composition, in
 * the language of the test and its formats.
 */
@RunWith(AndroidJUnit4::class)
class RestoreScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private var state by mutableStateOf(ready())
    private var box by mutableStateOf(DpSize(412.dp, 868.dp))
    private var told by mutableStateOf(DpSize(412.dp, 868.dp))
    private var fontScale by mutableFloatStateOf(1f)
    private val intents = mutableListOf<RestoreIntent>()

    /** The screen is there: false — not yet, as the screen under it stands before a press opens it. */
    private var opened by mutableStateOf(true)

    /** The words of the screen in the language of the test, read where it is laid out. */
    private val words = mutableMapOf<String, String>()

    /** The language of the device and of the formats, given back after every test. */
    private val deviceLanguage: Locale = Locale.getDefault()
    private val formatLanguage: FormatLanguage = Formats.language

    @After
    fun backToTheLanguageOfTheDevice() {
        Locale.setDefault(deviceLanguage)
        Formats.use(formatLanguage)
    }

    /** The words and the formats of [tag]: the date of the passport speaks it too. */
    private fun inLanguage(tag: String) {
        Locale.setDefault(Locale.forLanguageTag(tag))
        Formats.use(tag)
    }

    private fun show() {
        compose.setContent {
            words["saveFirst"] = stringResource(Res.string.restore_save_first)
            words["restore"] = stringResource(Res.string.restore_button)
            words["warning"] = stringResource(Res.string.restore_warning)
            words["waitCopy"] = stringResource(Res.string.restore_busy_saving)
            words["ok"] = stringResource(Res.string.restore_no_room_ok)
            words["unsafe"] = stringResource(Res.string.restore_unsafe_button)
            words["emptyApp"] = stringResource(Res.string.restore_empty_app)
            words["now"] = stringResource(Res.string.restore_now_title)
            words["close"] = stringResource(Res.string.backup_close)
            words["copyFrom"] = stringResource(Res.string.restore_copy_from, Formats.recordDate(MADE, withYear = false))
            // what is in the app now — 5 recordings, 1 piece, no days: «5 записей · 1 произведение», never «0 дней занятий»
            words["lost"] = stringResource(
                Res.string.restore_confirm_text,
                listOf(
                    stringResource(Formats.plural(5, Res.string.backup_count_sessions_one, Res.string.backup_count_sessions_few, Res.string.backup_count_sessions_many), 5),
                    stringResource(Formats.plural(1, Res.string.backup_count_pieces_one, Res.string.backup_count_pieces_few, Res.string.backup_count_pieces_many), 1),
                ).joinToString(stringResource(Res.string.dot_separator)),
            )
            words["noDays"] = stringResource(Res.string.backup_count_days_many, 0)
            ViolinTheme {
                TestWindow(box, told = told, fontScale = fontScale) {
                    if (opened) RestoreScreen(state = state, onIntent = { intents += it }, today = TODAY)
                }
            }
        }
        compose.waitForIdle()
    }

    private fun SemanticsNodeInteraction.bounds(): DpRect = getUnclippedBoundsInRoot()

    private fun window(): DpRect = compose.onNodeWithTag(TEST_WINDOW).bounds()

    /** A button of the zone by its words: one node for TalkBack and the finger. */
    private fun button(words: String): SemanticsNodeInteraction = compose.onNode(hasText(words) and hasClickAction())

    /** A text of the unmerged tree whose words are [words], a number kept to its word or not ([BackupFacts.keptNumbers]). */
    private fun wordsNode(words: String): SemanticsNodeInteraction = compose.onNode(
        SemanticsMatcher("the words «$words»") { node -> node.config.getOrNull(SemanticsProperties.Text)?.any { it.text.replace(NO_BREAK, ' ') == words.replace(NO_BREAK, ' ') } == true },
        useUnmergedTree = true,
    )

    private fun assertNear(what: String, expected: Dp, actual: Dp) =
        assertTrue("$what: $actual, expected $expected", actual in (expected - 1.dp)..(expected + 1.dp))

    private fun scrollToTheEnd() {
        compose.onNode(hasScrollAction()).performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, FAR) }
        compose.waitForIdle()
    }

    /** Over the data, upright: the safety net over «Восстановить», 8 apart; «Восстановить» 12 over the bottom. */
    @Test
    fun overTheDataTheSafetyNetStandsOverRestore() {
        show()
        val window = window()
        val saveFirst = button(words.getValue("saveFirst")).assertIsEnabled().bounds()
        val restore = button(words.getValue("restore")).assertIsEnabled().bounds()
        assertNear("8 between the two", saveFirst.bottom + 8.dp, restore.top)
        assertNear("«Восстановить» at the bottom of the zone", window.bottom - 12.dp, restore.bottom)
        assertNear("56 high", 56.dp, restore.bottom - restore.top)
    }

    /**
     * Lying in [lying], told the window is 640 × 360: the zone of two buttons of 48, 8 apart and 8 from its edges — about 120; scrolled
     * to its end, the warning is last, whole, over the zone.
     */
    private fun assertLying(lying: DpSize) {
        box = lying
        told = DpSize(640.dp, 360.dp)
        show()
        val window = window()
        val saveFirst = button(words.getValue("saveFirst")).bounds()
        val restore = button(words.getValue("restore")).bounds()
        assertNear("$lying: «Восстановить» 8 over the bottom", window.bottom - 8.dp, restore.bottom)
        assertNear("$lying: the zone of about 120", window.bottom - 112.dp, saveFirst.top)
        assertNear("$lying: buttons of 48", 48.dp, restore.bottom - restore.top)
        scrollToTheEnd()
        val warning = compose.onNodeWithText(words.getValue("warning")).bounds()
        assertTrue("$lying: the warning whole over the zone: $warning, the zone from ${saveFirst.top - 8.dp}", warning.bottom <= saveFirst.top - 8.dp + 0.5.dp)
        assertTrue("$lying: the warning under the header: $warning", warning.top >= window.top + 48.dp - 0.5.dp)
    }

    @Test
    fun lyingIn603By308TheZoneIsAbout120AndTheWarningIsLastOverIt() = assertLying(DpSize(603.dp, 308.dp))

    @Test
    fun lyingIn603By336TheZoneIsAbout120AndTheWarningIsLastOverIt() = assertLying(DpSize(603.dp, 336.dp))

    /** A copy is being saved: «Восстановить» asleep with its reason over it; the safety net pressed. */
    @Test
    fun whileACopyIsSavedRestoreSleepsWithItsReasonAndTheSafetyNetIsPressed() {
        state = ready(job = SAVING)
        show()
        val restore = button(words.getValue("restore")).assertIsNotEnabled().bounds()
        val reason = compose.onNodeWithText(words.getValue("waitCopy")).bounds()
        assertTrue("the reason over «Восстановить»: $reason, $restore", reason.bottom <= restore.top)
        button(words.getValue("saveFirst")).assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(listOf<RestoreIntent>(RestoreIntent.SaveFirstClicked), intents) }
    }

    /**
     * Into an empty app (spec 3.36.8, the main way in — «У меня есть копия данных»): the passport and the line under it; no «заменит», no
     * second card, no warning, no safety net; «Восстановить» pressed at once. The app weighs something all the same — its database and
     * settings — so a screen that told an empty app by its weight would show the warning here.
     */
    @Test
    fun intoAnEmptyAppOnlyThePassportAndAPlainRestore() {
        state = ready(current = EMPTY)
        show()
        compose.onNodeWithText(words.getValue("emptyApp")).assertExists()
        compose.onAllNodesWithText(words.getValue("warning")).assertCountEquals(0)
        compose.onAllNodesWithText(words.getValue("saveFirst")).assertCountEquals(0)
        compose.onAllNodesWithText(words.getValue("now")).assertCountEquals(0)
        button(words.getValue("restore")).assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(listOf<RestoreIntent>(RestoreIntent.RestoreClicked), intents) }
    }

    /** Without the room for the safe way: «Понятно», the safety net, the coral word — three rows, in this order, lying too. */
    @Test
    fun withoutTheRoomTheZoneIsUnderstoodTheSafetyNetAndTheCoralWord() {
        state = ready(current = HEAVY, copy = candidate(missing = 1_800 * MB))
        box = DpSize(603.dp, 308.dp)
        told = DpSize(640.dp, 360.dp)
        show()
        val window = window()
        val ok = button(words.getValue("ok")).bounds()
        val saveFirst = button(words.getValue("saveFirst")).bounds()
        val unsafe = button(words.getValue("unsafe")).assertIsEnabled().bounds()
        assertNear("the coral word 8 over the bottom", window.bottom - 8.dp, unsafe.bottom)
        assertNear("the safety net 8 over it", unsafe.top - 8.dp, saveFirst.bottom)
        assertNear("«Понятно» 8 over the safety net", saveFirst.top - 8.dp, ok.bottom)
        // no passport, no «заменит», no warning on this screen
        compose.onAllNodesWithText(words.getValue("warning")).assertCountEquals(0)
    }

    /** Without the room while a copy is saved: the coral word asleep, its reason over it; «Понятно» and the safety net pressed. */
    @Test
    fun withoutTheRoomWhileACopyIsSavedTheCoralWordSleepsWithItsReason() {
        state = ready(current = HEAVY, copy = candidate(missing = 1_800 * MB), job = SAVING)
        show()
        val unsafe = button(words.getValue("unsafe")).assertIsNotEnabled().bounds()
        val reason = compose.onNodeWithText(words.getValue("waitCopy")).bounds()
        assertTrue("the reason over the coral word: $reason, $unsafe", reason.bottom <= unsafe.top)
        button(words.getValue("ok")).assertIsEnabled()
        button(words.getValue("saveFirst")).assertIsEnabled()
    }

    /** No room even without the media in the app: only «Понятно» — no safety net, no coral word for a restore bound to fail. */
    @Test
    fun withoutTheRoomEvenWithoutTheMediaOnlyUnderstood() {
        state = ready(current = NOW, copy = candidate(missing = 1_800 * MB))
        show()
        button(words.getValue("ok")).assertIsEnabled()
        compose.onAllNodesWithText(words.getValue("saveFirst")).assertCountEquals(0)
        compose.onAllNodesWithText(words.getValue("unsafe")).assertCountEquals(0)
    }

    /**
     * 320 × 544 at 1.5 in [language] (the R8 check): «Сначала сохранить текущие данные» and «Удалить текущие данные и восстановить» need
     * three lines and more there — they take them, whole, none dropped (review of stage 122: two lines and the rest cut, «Удалить текущие
     * данные и»); the zone stays in the window.
     */
    private fun assertTheZoneSaysAllItsWords(language: String) {
        inLanguage(language)
        state = ready(current = HEAVY, copy = candidate(missing = 1_800 * MB))
        box = NARROW
        told = NARROW
        fontScale = LARGEST_FONT
        show()
        for (key in listOf("ok", "saveFirst", "unsafe")) {
            val text = words.getValue(key)
            assertWordsWhole(compose.onNodeWithText(text, useUnmergedTree = true), text)
        }
        val unsafe = button(words.getValue("unsafe")).bounds()
        assertTrue("the zone in the window: $unsafe", unsafe.bottom <= window().bottom)
    }

    @Test
    fun onANarrowPhoneAtTheLargestFontTheZoneSaysAllItsWordsInRussian() = assertTheZoneSaysAllItsWords("ru")

    @Test
    fun onANarrowPhoneAtTheLargestFontTheZoneSaysAllItsWordsInGerman() = assertTheZoneSaysAllItsWords("de")

    @Test
    fun onANarrowPhoneAtTheLargestFontTheZoneSaysAllItsWordsInFrench() = assertTheZoneSaysAllItsWords("fr")

    /**
     * The passport in German on 360 at 1.3: «Kopie vom 12. September» goes on two lines — «Kopie vom» / «12. September», the day with
     * its month (review of stage 122: «Kopie vom 12.» / «September»); no word broken, the weight on one line beside it.
     */
    @Test
    fun theDayOfThePassportKeepsToItsMonth() {
        inLanguage("de")
        box = DpSize(360.dp, 780.dp)
        told = box
        fontScale = LARGE_FONT
        show()
        val title = wordsNode(words.getValue("copyFrom"))
        assertWordsWhole(title, words.getValue("copyFrom"))
        val layout = title.textLayout()
        val text = layout.layoutInput.text.text
        for (line in 0 until layout.lineCount - 1) {
            val end = text.substring(0, layout.getLineEnd(line)).trimEnd()
            assertTrue("a line of the title ends on the day: «$end»", !end.last().isDigit() && !end.endsWith("."))
        }
    }

    /** «Заменить данные?» names what there is: «5 записей · 1 произведение», not «0 дней занятий». */
    @Test
    fun theQuestionNamesOnlyWhatThereIs() {
        inLanguage("ru")
        state = ready(current = BackupContents(BackupCounts(sessions = 5, pieces = 1, practiceDays = 0), NOW_BYTES), dialog = RestoreDialog.REPLACE)
        show()
        compose.onNodeWithText(words.getValue("lost")).assertExists()
        compose.onAllNodesWithText(words.getValue("noDays"), substring = true).assertCountEquals(0)
    }

    /**
     * «Закрыть» on a failure into an empty app: the failure is read and gone, the screen fades — and keeps the face it was closed on (the
     * lesson of stage 119; review of stage 122): no passport with «Восстановить» under the finger that pressed «Закрыть».
     */
    @Test
    fun aScreenOnItsWayOutKeepsTheFaceItWasClosedOn() {
        state = ready(current = EMPTY, job = BackupJob.RestoreFailed(dataIntact = true, uri = "content://downloads/1", manifest = manifest(), checked = false))
        show()
        button(words.getValue("close")).assertExists()
        state = ready(current = EMPTY).copy(closing = true)
        compose.waitForIdle()
        button(words.getValue("close")).assertExists()
        compose.onAllNodesWithText(words.getValue("restore")).assertCountEquals(0)
        compose.onAllNodesWithText(words.getValue("emptyApp")).assertCountEquals(0)
    }

    /**
     * A screen comes in under the finger that opened it (the lead's fix of stage 122, verified), and the face it opens on holds its zone
     * for a double tap as a face that came in the place of another does: the second tap, a frame or two after the screen came, asks for
     * nothing; once the time of a double tap has passed, the button answers.
     */
    @Test
    fun theSecondTapOfTheFingerThatOpenedTheScreenAsksForNothing() {
        opened = false
        show()
        compose.mainClock.autoAdvance = false
        opened = true
        repeat(TAP_FRAMES) { compose.mainClock.advanceTimeByFrame() }
        val finger = center(button(words.getValue("restore")).bounds())
        tapAt(finger)
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        assertEquals("the second tap asked for nothing", emptyList<RestoreIntent>(), intents)
        tapAt(finger)
        compose.waitForIdle()
        assertEquals(listOf<RestoreIntent>(RestoreIntent.RestoreClicked), intents)
    }

    private fun center(rect: DpRect): DpOffset = DpOffset((rect.left + rect.right) / 2, (rect.top + rect.bottom) / 2)

    /** A finger down and up again at [point] of the root — where it was a moment ago, whatever stands there now. */
    private fun tapAt(point: DpOffset) {
        val window = window()
        compose.onNodeWithTag(TEST_WINDOW).performTouchInput { click(Offset((point.x - window.left).toPx(), (point.y - window.top).toPx())) }
    }

    private companion object {
        const val MB = 1024L * 1024L
        const val LARGE_FONT = 1.3f
        const val LARGEST_FONT = 1.5f
        const val NO_BREAK = ' '

        /** The narrow phone of the R8 check: 320 × 544. */
        val NARROW = DpSize(320.dp, 544.dp)

        /** Far past the end of anything that scrolls. */
        const val FAR = 100_000f

        /** Frames between the first tap and the second: 30–60 ms (stage 120). */
        const val TAP_FRAMES = 2
        val TODAY = LocalDate(2026, 10, 2)
        val MADE = LocalDate(2026, 9, 12)

        val ALL = BackupPart.entries.toSet()
        val SAVING = BackupJob.Saving("копия.zip", visible = true, parts = ALL)

        val COUNTS = BackupCounts(sessions = 64, takes = 6, pieces = 12, pages = 48, practiceDays = 41, trophies = 3, level = 9, withSound = 50, videos = 6)
        val COPY_BYTES = mapOf(BackupPart.DATA to 12 * MB, BackupPart.SHEETS to 180 * MB, BackupPart.AUDIO to 60 * MB, BackupPart.VIDEO to 3_200 * MB)
        val NOW_BYTES = mapOf(BackupPart.DATA to 4 * MB, BackupPart.SHEETS to 40 * MB, BackupPart.AUDIO to 16 * MB, BackupPart.VIDEO to 350 * MB)
        val NOW = BackupContents(BackupCounts(sessions = 5, pieces = 5, practiceDays = 38, level = 5, withSound = 4, videos = 1), NOW_BYTES)

        /** An app with nothing in it to lose — that weighs something all the same. */
        val EMPTY = BackupContents(BackupCounts(), NOW_BYTES)

        /** The app now with gigabytes of video: the way without a net would make the room. */
        val HEAVY = NOW.copy(bytes = NOW_BYTES + (BackupPart.VIDEO to 3_000 * MB))

        fun manifest(): BackupManifest = BackupManifest(
            1, "1.4", 13,
            MADE.atStartOfDayIn(TimeZone.currentSystemDefault()).toEpochMilliseconds() + 12 * 60 * 60 * 1_000L,
            "Pixel 10a", ALL, COUNTS, COPY_BYTES,
        )

        fun candidate(missing: Long = 0): BackupCandidate.Copy = BackupCandidate.Copy("content://downloads/1", "копия.zip", 3_400 * MB, manifest(), missing)

        fun ready(current: BackupContents = NOW, copy: BackupCandidate.Copy = candidate(), job: BackupJob = BackupJob.Idle, dialog: RestoreDialog? = null) =
            RestoreState(stage = RestoreStage.Ready(copy, current), job = job, dialog = dialog)
    }
}
