package com.violinjourney.app.feature.backup

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
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
import com.violinjourney.app.core.backup.BackupContents
import com.violinjourney.app.core.backup.BackupCounts
import com.violinjourney.app.core.backup.BackupJob
import com.violinjourney.app.core.backup.BackupManifest
import com.violinjourney.app.core.backup.BackupPart
import com.violinjourney.app.core.backup.BackupProgress
import com.violinjourney.app.core.backup.SaveFailure
import com.violinjourney.app.core.ui.format.FormatLanguage
import com.violinjourney.app.core.ui.format.Formats
import com.violinjourney.app.core.ui.theme.ViolinTheme
import com.violinjourney.app.shared.resources.Res
import com.violinjourney.app.shared.resources.backup_busy_recording
import com.violinjourney.app.shared.resources.backup_cancel_action
import com.violinjourney.app.shared.resources.backup_count_days_few
import com.violinjourney.app.shared.resources.backup_count_days_many
import com.violinjourney.app.shared.resources.backup_count_days_one
import com.violinjourney.app.shared.resources.backup_count_pages_few
import com.violinjourney.app.shared.resources.backup_count_pages_many
import com.violinjourney.app.shared.resources.backup_count_pages_one
import com.violinjourney.app.shared.resources.backup_count_pieces_few
import com.violinjourney.app.shared.resources.backup_count_pieces_many
import com.violinjourney.app.shared.resources.backup_count_pieces_one
import com.violinjourney.app.shared.resources.backup_count_sessions_few
import com.violinjourney.app.shared.resources.backup_count_sessions_many
import com.violinjourney.app.shared.resources.backup_count_sessions_one
import com.violinjourney.app.shared.resources.backup_count_takes_few
import com.violinjourney.app.shared.resources.backup_count_takes_many
import com.violinjourney.app.shared.resources.backup_count_takes_one
import com.violinjourney.app.shared.resources.backup_count_trophies_few
import com.violinjourney.app.shared.resources.backup_count_trophies_many
import com.violinjourney.app.shared.resources.backup_count_trophies_one
import com.violinjourney.app.shared.resources.backup_done
import com.violinjourney.app.shared.resources.backup_failed_phone_space_title
import com.violinjourney.app.shared.resources.backup_failed_space_inside
import com.violinjourney.app.shared.resources.backup_failed_space_title
import com.violinjourney.app.shared.resources.backup_failed_without_video
import com.violinjourney.app.shared.resources.backup_part_always
import com.violinjourney.app.shared.resources.backup_part_audio
import com.violinjourney.app.shared.resources.backup_part_data
import com.violinjourney.app.shared.resources.backup_part_sheets
import com.violinjourney.app.shared.resources.backup_part_video
import com.violinjourney.app.shared.resources.backup_phase_verifying_file
import com.violinjourney.app.shared.resources.backup_retry
import com.violinjourney.app.shared.resources.backup_save_to
import com.violinjourney.app.shared.resources.backup_saving_button
import com.violinjourney.app.shared.resources.backup_share
import com.violinjourney.app.shared.resources.backup_total_value
import com.violinjourney.app.shared.resources.dot_separator
import com.violinjourney.app.testing.TEST_WINDOW
import com.violinjourney.app.testing.TestWindow
import com.violinjourney.app.testing.assertWholeOnOneLine
import com.violinjourney.app.testing.assertWordsWhole
import com.violinjourney.app.testing.textLayout
import java.util.Locale
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * «Копия данных» of R8 (spec 3.36.8, 5.29 R8) by what an eye, a finger and TalkBack meet: at the font 1.3 «всегда» stands whole on one
 * line and the name of the data on two lines on 412 (it was four, and «всегд / а», on the emulator 30.09); no name and no caption of a
 * part breaks a word on 360 at 1.3 and on 320 × 544 at 1.5, in Russian, German and French — there «всегда» goes under the caption where
 * the words do not stand beside it (fr), and stays beside them where they do (ru); a number keeps to its word; lying on the emulator's
 * 640 × 360 — 603 × 308 behind its cutout and bars, 603 × 336 where the status bar is hidden — the zone is two rows of 48 at the bottom
 * and the last part scrolls whole above it; a failed «Отправить…» says the phone in its title as in its text and offers no copy without
 * video, while a full place with video in the copy says what it would weigh without it; while a take is recorded the reason stands over
 * «Сохранить в…»; while a short copy goes «Отправить…» is dimmed, not gone; while the parts are counted the zone holds the room of a
 * button, empty; on «Проверка» «Отменить» sleeps under «Проверяем файл»; a part is a switch with its name and caption; a screen on its
 * way out keeps the face it was closed on, and a face that came in the place of another holds its zone for a double tap — the face the
 * screen opened on too. Laid out in a window of its own size ([TestWindow]); the words read in the composition, in the language of the
 * test and its formats.
 */
@RunWith(AndroidJUnit4::class)
class BackupScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private var state by mutableStateOf(copyState())
    private var box by mutableStateOf(DpSize(412.dp, 868.dp))
    private var told by mutableStateOf(DpSize(412.dp, 868.dp))
    private var fontScale by mutableFloatStateOf(1f)
    private val intents = mutableListOf<BackupIntent>()

    /** The screen is there: false — not yet, as the passport under it stands before «Сначала сохранить текущие данные» opens it. */
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

    /** The words and the formats of [tag]: the dates, the units and the plurals of the captions speak it too. */
    private fun inLanguage(tag: String) {
        Locale.setDefault(Locale.forLanguageTag(tag))
        Formats.use(tag)
    }

    private fun show() {
        compose.setContent {
            words["always"] = stringResource(Res.string.backup_part_always)
            words["data"] = stringResource(Res.string.backup_part_data)
            words["sheets"] = stringResource(Res.string.backup_part_sheets)
            words["audio"] = stringResource(Res.string.backup_part_audio)
            words["video"] = stringResource(Res.string.backup_part_video)
            words["save"] = stringResource(Res.string.backup_save_to)
            words["saving"] = stringResource(Res.string.backup_saving_button)
            words["share"] = stringResource(Res.string.backup_share)
            words["done"] = stringResource(Res.string.backup_done)
            words["retry"] = stringResource(Res.string.backup_retry)
            words["cancel"] = stringResource(Res.string.backup_cancel_action)
            words["checking"] = stringResource(Res.string.backup_phase_verifying_file)
            words["busy"] = stringResource(Res.string.backup_busy_recording)
            words["placeFull"] = stringResource(Res.string.backup_failed_space_title)
            words["phoneShort"] = stringResource(Res.string.backup_failed_phone_space_title)
            words["phoneShortText"] = BackupFacts.keptNumbers(stringResource(Res.string.backup_failed_space_inside, Formats.fileSize(MISSING)))
            words["withoutVideo"] = BackupFacts.keptNumbers(
                stringResource(Res.string.backup_failed_without_video, stringResource(Res.string.backup_total_value, Formats.fileSize(WITHOUT_VIDEO))),
            )
            // what every card «Без видео…» begins with, whatever weight it says
            words["withoutVideoAny"] = stringResource(Res.string.backup_failed_without_video, MARK).substringBefore(MARK)
            // the captions of the parts of the app of the mockups, as the screen makes them
            val dot = stringResource(Res.string.dot_separator)
            fun counted(count: Int, one: StringResource, few: StringResource, many: StringResource) = Formats.plural(count, one, few, many) to count
            val data = listOf(
                counted(64, Res.string.backup_count_sessions_one, Res.string.backup_count_sessions_few, Res.string.backup_count_sessions_many),
                counted(41, Res.string.backup_count_days_one, Res.string.backup_count_days_few, Res.string.backup_count_days_many),
                counted(3, Res.string.backup_count_trophies_one, Res.string.backup_count_trophies_few, Res.string.backup_count_trophies_many),
            ).map { (resource, count) -> stringResource(resource, count) }
            words["dataCaption"] = (data + Formats.fileSize(12 * MB)).joinToString(dot)
            words["sheetsCaption"] = listOf(
                stringResource(Formats.plural(12, Res.string.backup_count_pieces_one, Res.string.backup_count_pieces_few, Res.string.backup_count_pieces_many), 12),
                stringResource(Formats.plural(48, Res.string.backup_count_pages_one, Res.string.backup_count_pages_few, Res.string.backup_count_pages_many), 48),
                Formats.fileSize(180 * MB),
            ).joinToString(dot)
            words["audioCaption"] = listOf(
                stringResource(Formats.plural(50, Res.string.backup_count_sessions_one, Res.string.backup_count_sessions_few, Res.string.backup_count_sessions_many), 50),
                Formats.fileSize(60 * MB),
            ).joinToString(dot)
            words["videoCaption"] = listOf(
                stringResource(Formats.plural(6, Res.string.backup_count_takes_one, Res.string.backup_count_takes_few, Res.string.backup_count_takes_many), 6),
                Formats.fileSize(3_200 * MB),
            ).joinToString(dot)
            ViolinTheme {
                TestWindow(box, told = told, fontScale = fontScale) {
                    if (opened) BackupScreen(state = state, fileName = FILE, onIntent = { intents += it })
                }
            }
        }
        compose.waitForIdle()
    }

    private fun SemanticsNodeInteraction.bounds(): DpRect = getUnclippedBoundsInRoot()

    private fun window(): DpRect = compose.onNodeWithTag(TEST_WINDOW).bounds()

    /** A button of the zone by its words: one node for TalkBack and the finger. */
    private fun button(words: String): SemanticsNodeInteraction = compose.onNode(hasText(words) and hasClickAction())

    /** A part by its name: the switch with its name and caption (the data — the row with «всегда»). */
    private fun partSwitch(name: String): SemanticsNodeInteraction =
        compose.onNode(hasText(name) and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch))

    /** A text of the unmerged tree whose words are [words], a number kept to its word or not ([BackupFacts.keptNumbers]). */
    private fun wordsNode(words: String): SemanticsNodeInteraction = compose.onNode(
        SemanticsMatcher("the words «$words»") { node -> node.config.getOrNull(SemanticsProperties.Text)?.any { it.text.replace(NO_BREAK, ' ') == words.replace(NO_BREAK, ' ') } == true },
        useUnmergedTree = true,
    )

    private fun assertNear(what: String, expected: Dp, actual: Dp) =
        assertTrue("$what: $actual, expected $expected", actual in (expected - 1.dp)..(expected + 1.dp))

    /** Everything that scrolls, to its end. */
    private fun scrollToTheEnd() {
        compose.onNode(hasScrollAction()).performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, FAR) }
        compose.waitForIdle()
    }

    /** No line of [node] but the last ends on a number: «3» / «трофея», de «12.» / «September» (5.29 R6, R8). */
    private fun assertNumbersKept(node: SemanticsNodeInteraction, what: String) {
        val layout = node.textLayout()
        val text = layout.layoutInput.text.text
        for (line in 0 until layout.lineCount - 1) {
            val end = text.substring(0, layout.getLineEnd(line)).trimEnd()
            assertTrue("«$what»: a line ends on a number — «$end»", end.isNotEmpty() && !end.last().isDigit() && !end.endsWith("."))
        }
    }

    private fun lay(size: DpSize, scale: Float) {
        box = size
        told = size
        fontScale = scale
    }

    /**
     * «всегда» whole on one line; the name and the caption of every part with no word broken and no number at the end of a line, but
     * the [limits] (5.29 R8): [language] in a window [size] at the font [scale].
     */
    private fun assertThePartsStandWhole(language: String, size: DpSize, scale: Float, limits: Set<String> = emptySet()) {
        inLanguage(language)
        lay(size, scale)
        show()
        assertWholeOnOneLine(compose.onNodeWithText(words.getValue("always"), useUnmergedTree = true), words.getValue("always"))
        for (key in listOf("data", "sheets", "audio", "video")) {
            val name = words.getValue(key)
            if (key !in limits) assertWordsWhole(compose.onNodeWithText(name, useUnmergedTree = true), name)
            val caption = words.getValue("${key}Caption")
            val node = wordsNode(caption)
            assertWordsWhole(node, caption)
            if ("${key}Caption" !in limits) assertNumbersKept(node, caption)
        }
    }

    /** The emulator of the before shots, 412 at 1.3: «всегда» whole, the name of the data on two lines (it was four). */
    @Test
    fun atALargeFontOn412AlwaysStandsWholeAndTheDataTakeTwoLines() {
        inLanguage("ru")
        fontScale = LARGE_FONT
        show()
        assertWholeOnOneLine(compose.onNodeWithText(words.getValue("always"), useUnmergedTree = true), words.getValue("always"))
        val name = compose.onNodeWithText(words.getValue("data"), useUnmergedTree = true)
        assertWordsWhole(name, words.getValue("data"))
        assertTrue("the name of the data on two lines: ${name.textLayout().lineCount}", name.textLayout().lineCount <= 2)
    }

    @Test
    fun atALargeFontOn360NoWordOfAPartBreaksInRussian() = assertThePartsStandWhole("ru", DpSize(360.dp, 640.dp), LARGE_FONT)

    @Test
    fun atALargeFontOn360NoWordOfAPartBreaksInGerman() = assertThePartsStandWhole("de", DpSize(360.dp, 640.dp), LARGE_FONT)

    @Test
    fun atALargeFontOn360NoWordOfAPartBreaksInFrench() = assertThePartsStandWhole("fr", DpSize(360.dp, 640.dp), LARGE_FONT)

    /** 320 × 544 at 1.5 (the R8 check): ru keeps «всегда» beside the words of the data — they stand whole there. */
    @Test
    fun onANarrowPhoneAtTheLargestFontNoWordOfAPartBreaksInRussian() {
        assertThePartsStandWhole("ru", NARROW, LARGEST_FONT)
        assertAlways(beside = true)
    }

    @Test
    fun onANarrowPhoneAtTheLargestFontNoWordOfAPartBreaksInGerman() = assertThePartsStandWhole("de", NARROW, LARGEST_FONT)

    /**
     * fr at 320 × 544 at 1.5: «enregistrements» of the caption of the data does not stand beside «toujours» even at 12 sp (148 dp in
     * 131), so «toujours» goes under the caption (review of stage 122; it broke «enregistremen / ts»). The limits of 5.29 R8: «Son des
     * enregistrements» beside its switch breaks even at 13 sp (174 in 166), and its «50 enregistrements» does not stand kept (175 in 166)
     * — the number may end a line there, a word does not break.
     */
    @Test
    fun onANarrowPhoneAtTheLargestFontNoWordOfAPartBreaksInFrench() {
        assertThePartsStandWhole("fr", NARROW, LARGEST_FONT, limits = setOf("audio", "audioCaption"))
        assertAlways(beside = false)
    }

    /** «всегда» of the data [beside] its caption, or under it. */
    private fun assertAlways(beside: Boolean) {
        val always = compose.onNodeWithText(words.getValue("always"), useUnmergedTree = true).bounds()
        val caption = wordsNode(words.getValue("dataCaption")).bounds()
        if (beside) {
            assertTrue("«всегда» beside the caption: $always, the caption $caption", always.left >= caption.right)
        } else {
            assertTrue("«всегда» under the caption: $always, the caption $caption", always.top >= caption.bottom)
        }
    }

    /**
     * Lying in [lying], told the window is 640 × 360: the zone is two rows of 48 at the bottom — «Сохранить в…» and «Отправить…», 10
     * apart, 8 from its edges — and the last part, scrolled to, stands whole above it.
     */
    private fun assertLying(lying: DpSize) {
        inLanguage("ru")
        state = copyState(contents = SMALL)
        box = lying
        told = DpSize(640.dp, 360.dp)
        show()
        val window = window()
        val share = button(words.getValue("share")).bounds()
        val save = button(words.getValue("save")).bounds()
        assertNear("$lying: «Отправить…» 8 over the bottom", window.bottom - 8.dp, share.bottom)
        assertNear("$lying: «Отправить…» 48 high", 48.dp, share.bottom - share.top)
        assertNear("$lying: «Сохранить в…» 48 high", 48.dp, save.bottom - save.top)
        assertNear("$lying: «Сохранить в…» 10 over «Отправить…»", share.top - 10.dp, save.bottom)
        scrollToTheEnd()
        val video = partSwitch(words.getValue("video")).bounds()
        assertTrue("$lying: the last part above the zone after the scroll: $video, «Сохранить в…» at $save", video.bottom <= save.top - 8.dp + 0.5.dp)
        assertTrue("$lying: the last part under the header", video.top >= window.top + 48.dp - 0.5.dp)
    }

    /** The emulator's 640 × 360 behind its cutout and bars. */
    @Test
    fun lyingIn603By308TheZoneIsTwoRowsOf48AndTheLastPartScrollsAboveIt() = assertLying(DpSize(603.dp, 308.dp))

    /** The same where the status bar is hidden. */
    @Test
    fun lyingIn603By336TheZoneIsTwoRowsOf48AndTheLastPartScrollsAboveIt() = assertLying(DpSize(603.dp, 336.dp))

    /**
     * «Отправить…» that did not fit the phone: its title says the phone, as its text does — not «там, куда сохраняли» — and no card «Без
     * видео…»: another place, not a copy without video, is what helps. The copy has video of some weight and goes under 200 МБ, so the
     * card would be there but for the phone (review of stage 122: with video of no weight it never could be, and the check said nothing).
     */
    @Test
    fun aFailedSendingSaysThePhoneInItsTitleAsInItsText() {
        state = copyState(contents = SMALL_WITH_VIDEO, job = BackupJob.SaveFailed(SaveFailure.NO_SPACE, missingBytes = MISSING, parts = ALL))
        show()
        compose.onNodeWithText(words.getValue("phoneShort")).assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
        compose.onNodeWithText(words.getValue("phoneShortText")).assertExists()
        compose.onAllNodesWithText(words.getValue("placeFull")).assertCountEquals(0)
        compose.onAllNodesWithText(words.getValue("withoutVideoAny"), substring = true).assertCountEquals(0)
        // the same copy, the place full: the card is there — what the check above would find
        state = copyState(contents = SMALL_WITH_VIDEO, job = BackupJob.SaveFailed(SaveFailure.NO_SPACE, parts = ALL))
        compose.waitForIdle()
        compose.onAllNodesWithText(words.getValue("withoutVideoAny"), substring = true).assertCountEquals(1)
    }

    /** A place that was full under a copy with video: «Без видео копия займёт ≈ 252 МБ.» — the weight of the same copy without it. */
    @Test
    fun aFullPlaceUnderACopyWithVideoSaysWhatItWouldWeighWithoutIt() {
        state = copyState(job = BackupJob.SaveFailed(SaveFailure.NO_SPACE, parts = ALL))
        show()
        compose.onNodeWithText(words.getValue("placeFull")).assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
        compose.onNodeWithText(words.getValue("withoutVideo")).assertExists()
        // a copy that had no video has nothing to leave out
        state = copyState(job = BackupJob.SaveFailed(SaveFailure.NO_SPACE, parts = ALL - BackupPart.VIDEO))
        compose.waitForIdle()
        compose.onAllNodesWithText(words.getValue("withoutVideoAny"), substring = true).assertCountEquals(0)
    }

    /** A take is recorded: «Сохранить в…» asleep with its reason over it, no «Отправить…»; the parts still switch. */
    @Test
    fun whileATakeIsRecordedTheReasonStandsOverSaveTo() {
        state = copyState(contents = SMALL, busy = true)
        show()
        val save = button(words.getValue("save")).assertIsNotEnabled().bounds()
        val reason = compose.onNodeWithText(words.getValue("busy")).bounds()
        assertTrue("the reason over the button: $reason, $save", reason.bottom <= save.top)
        compose.onAllNodesWithText(words.getValue("share")).assertCountEquals(0)
        partSwitch(words.getValue("sheets")).assertIsEnabled()
    }

    /** A short copy on its way: «Сохраняем…» not pressed, «Отправить…» dimmed in its place — the zone does not jump (D14). */
    @Test
    fun whileAShortCopyGoesSendingIsDimmedNotGone() {
        state = copyState(contents = SMALL)
        show()
        val before = button(words.getValue("share")).assertIsEnabled().bounds()
        state = copyState(contents = SMALL, job = BackupJob.Saving(FILE, visible = false, parts = ALL))
        compose.waitForIdle()
        button(words.getValue("saving")).assertIsNotEnabled()
        val during = button(words.getValue("share")).assertIsNotEnabled().bounds()
        assertEquals("«Отправить…» stays where it was", before.top.value, during.top.value, 0.5f)
        partSwitch(words.getValue("sheets")).assertIsNotEnabled()
    }

    /**
     * The parts still counted (D15; spec 3.36.8): a spinner for the total, no parts, and the zone holding the room of a button — 56 at
     * its bottom, empty: no «Сохранить в…» that would do nothing when pressed. The parts counted — the buttons stand.
     */
    @Test
    fun whileThePartsAreCountedTheZoneHoldsTheRoomOfAButtonEmpty() {
        state = copyState(contents = null)
        show()
        val window = window()
        val room = compose.onNodeWithTag(COUNTING_ZONE).bounds()
        assertNear("the room of a button", 56.dp, room.bottom - room.top)
        assertNear("at the bottom of the zone", window.bottom - 12.dp, room.bottom)
        compose.onAllNodesWithText(words.getValue("save")).assertCountEquals(0)
        compose.onAllNodesWithText(words.getValue("share")).assertCountEquals(0)
        compose.onAllNodesWithText(words.getValue("sheets")).assertCountEquals(0)
        compose.onNode(SemanticsMatcher.expectValue(SemanticsProperties.ProgressBarRangeInfo, ProgressBarRangeInfo.Indeterminate), useUnmergedTree = true).assertExists()
        state = copyState(contents = SMALL)
        compose.waitForIdle()
        compose.onAllNodesWithTag(COUNTING_ZONE).assertCountEquals(0)
        button(words.getValue("save")).assertIsEnabled()
    }

    /** «Проверка» (spec 3.36.8): «Отменить» asleep — nothing is left to cancel — and «Проверяем файл» over it: no button silently grey. */
    @Test
    fun onItsCheckCancelSleepsUnderItsReason() {
        state = copyState(
            job = BackupJob.Saving(FILE, visible = true, verifying = true, progress = BackupProgress(BackupPart.VIDEO, 12, 12, 100, 100), parts = ALL, filled = ALL),
        )
        show()
        val cancel = button(words.getValue("cancel")).assertIsNotEnabled().bounds()
        val reason = compose.onNodeWithText(words.getValue("checking")).bounds()
        assertTrue("«Проверяем файл» over «Отменить»: $reason, $cancel", reason.bottom <= cancel.top)
    }

    /** A part is a switch with its name and its caption; the whole row toggles it; the data say «всегда» and toggle nothing. */
    @Test
    fun aPartIsASwitchWithItsNameAndTheDataSayAlways() {
        show()
        partSwitch(words.getValue("sheets")).assertIsOn().performClick()
        compose.runOnIdle { assertEquals(listOf<BackupIntent>(BackupIntent.PartToggled(BackupPart.SHEETS)), intents) }
        compose.onNode(hasText(words.getValue("data")) and hasText(words.getValue("always"))).assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Role))
    }

    /**
     * «Готово» pressed: the outcome is read and gone, the screen fades for 700 ms — and keeps the face it was closed on (the lesson of
     * stage 119; review of stage 122): no choice with «Отправить…» under the finger that pressed «Готово».
     */
    @Test
    fun aScreenOnItsWayOutKeepsTheFaceItWasClosedOn() {
        state = copyState(contents = SMALL, job = BackupJob.Saved(FILE, bytes = 900_000L, place = "Загрузки", manifest = SMALL_MANIFEST))
        show()
        button(words.getValue("done")).assertExists()
        state = copyState(contents = SMALL).copy(closing = true)
        compose.waitForIdle()
        button(words.getValue("done")).assertExists()
        compose.onAllNodesWithText(words.getValue("save")).assertCountEquals(0)
        compose.onAllNodesWithText(words.getValue("share")).assertCountEquals(0)
    }

    /**
     * A double tap on «Ещё раз» (5.29 R8, as a face of a sheet holds its main button, R3): the failure read, the choice takes its place
     * and «Сохранить в…» stands where «Ещё раз» stood — the second tap, a frame or two later, asks for no second «Сохранить как…». Once
     * the time of a double tap has passed, «Сохранить в…» answers.
     */
    @Test
    fun aSecondTapOfAgainAsksForNoSecondPlace() {
        state = copyState(contents = SMALL, job = BackupJob.SaveFailed(SaveFailure.FAILED, parts = ALL))
        show()
        val finger = center(button(words.getValue("retry")).bounds())
        compose.mainClock.autoAdvance = false
        tapAt(finger)
        // the model's answer: the failure read, the choice
        state = copyState(contents = SMALL)
        repeat(TAP_FRAMES) { compose.mainClock.advanceTimeByFrame() }
        val save = button(words.getValue("save")).bounds()
        assertTrue("«Сохранить в…» is under the finger that pressed «Ещё раз»: $finger in $save", save.holds(finger))
        tapAt(finger)
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        assertEquals("the second tap asked for nothing", listOf<BackupIntent>(BackupIntent.RetryClicked), intents)
        tapAt(finger)
        compose.waitForIdle()
        assertEquals(listOf(BackupIntent.RetryClicked, BackupIntent.SaveClicked), intents)
    }

    /**
     * The screen comes in under the finger that opened it (the lead's fix of stage 122, verified): «Сохранить в…» stands where «Сначала
     * сохранить текущие данные» of the passport stood, and the second tap of a double tap on it, a frame or two after the screen came,
     * asks for no «Сохранить как…» over a screen not seen yet — its first face holds its zone as a face that came in the place of another
     * does. Once the time of a double tap has passed, «Сохранить в…» answers.
     */
    @Test
    fun theSecondTapOfTheFingerThatOpenedTheScreenAsksForNoPlace() {
        state = copyState(contents = SMALL)
        opened = false
        show()
        compose.mainClock.autoAdvance = false
        opened = true
        repeat(TAP_FRAMES) { compose.mainClock.advanceTimeByFrame() }
        val finger = center(button(words.getValue("save")).bounds())
        tapAt(finger)
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        assertEquals("the second tap asked for nothing", emptyList<BackupIntent>(), intents)
        tapAt(finger)
        compose.waitForIdle()
        assertEquals(listOf<BackupIntent>(BackupIntent.SaveClicked), intents)
    }

    private fun center(rect: DpRect): DpOffset = DpOffset((rect.left + rect.right) / 2, (rect.top + rect.bottom) / 2)

    private fun DpRect.holds(point: DpOffset): Boolean = point.x in left..right && point.y in top..bottom

    /** A finger down and up again at [point] of the root — where it was a moment ago, whatever stands there now. */
    private fun tapAt(point: DpOffset) {
        val window = window()
        compose.onNodeWithTag(TEST_WINDOW).performTouchInput { click(Offset((point.x - window.left).toPx(), (point.y - window.top).toPx())) }
    }

    private companion object {
        const val FILE = "Интонация · копия · 2 октября 2026.zip"
        const val LARGE_FONT = 1.3f
        const val LARGEST_FONT = 1.5f
        const val MB = 1024L * 1024L
        const val NO_BREAK = ' '
        const val MARK = ""

        /** The tag of the room the zone holds while the parts are counted (`BackupScreen.kt`, `COUNTING_ZONE_TAG`). */
        const val COUNTING_ZONE = "backup counting zone"

        /** Frames between the first tap and the second: 30–60 ms (stage 120). */
        const val TAP_FRAMES = 2

        /** The narrow phone of the R8 check: 320 × 544. */
        val NARROW = DpSize(320.dp, 544.dp)

        /** Far past the end of anything that scrolls. */
        const val FAR = 100_000f

        /** «Отправить…» 190 МБ short of the phone. */
        const val MISSING = 190 * MB

        /** The app of the mockups without its video. */
        const val WITHOUT_VIDEO = 252 * MB

        val ALL = BackupPart.entries.toSet()

        val COUNTS = BackupCounts(sessions = 64, takes = 6, pieces = 12, pages = 48, practiceDays = 41, trophies = 3, level = 9, withSound = 50, videos = 6)
        val CONTENTS = BackupContents(COUNTS, mapOf(BackupPart.DATA to 12 * MB, BackupPart.SHEETS to 180 * MB, BackupPart.AUDIO to 60 * MB, BackupPart.VIDEO to 3_200 * MB))

        /** A young app: a copy of it weighs less than a megabyte and may be sent. */
        val SMALL = BackupContents(
            BackupCounts(sessions = 5, pieces = 2, pages = 1, practiceDays = 7, level = 2, withSound = 3),
            mapOf(BackupPart.DATA to 300_000L, BackupPart.SHEETS to 200_000L, BackupPart.AUDIO to 400_000L, BackupPart.VIDEO to 0L),
        )

        /** The young app with a video take of 50 МБ: still a copy «Отправить…» takes, and video that weighs something. */
        val SMALL_WITH_VIDEO = SMALL.copy(counts = SMALL.counts.copy(takes = 1, videos = 1), bytes = SMALL.bytes + (BackupPart.VIDEO to 50 * MB))

        val SMALL_MANIFEST = BackupManifest(1, "1.4", 13, 0L, "Pixel 10a", ALL, SMALL.counts, SMALL.bytes)

        fun copyState(contents: BackupContents? = CONTENTS, job: BackupJob = BackupJob.Idle, busy: Boolean = false) =
            BackupState(contents = contents, busy = busy, job = job, shareUpToBytes = 200 * MB)
    }
}
