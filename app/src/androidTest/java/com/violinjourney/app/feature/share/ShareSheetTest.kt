package com.violinjourney.app.feature.share

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.violinjourney.app.core.domain.sound.BuiltInPreset
import com.violinjourney.app.core.ui.components.AppSheetCard
import com.violinjourney.app.core.ui.format.Formats
import com.violinjourney.app.core.ui.theme.ViolinTheme
import com.violinjourney.app.feature.sound.SoundCaption
import com.violinjourney.app.shared.resources.Res
import com.violinjourney.app.shared.resources.dialog_cancel
import com.violinjourney.app.shared.resources.dot_separator
import com.violinjourney.app.shared.resources.share_backing
import com.violinjourney.app.shared.resources.share_busy
import com.violinjourney.app.shared.resources.share_large_file
import com.violinjourney.app.shared.resources.share_preparing
import com.violinjourney.app.shared.resources.share_preparing_video
import com.violinjourney.app.shared.resources.share_remaining
import com.violinjourney.app.shared.resources.share_retry
import com.violinjourney.app.shared.resources.share_send_as_shot
import com.violinjourney.app.shared.resources.share_sound_only
import com.violinjourney.app.shared.resources.share_title
import com.violinjourney.app.shared.resources.share_video_details
import com.violinjourney.app.shared.resources.share_video_original
import com.violinjourney.app.shared.resources.share_video_processed
import com.violinjourney.app.testing.textLayout
import org.jetbrains.compose.resources.stringResource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The faces of «Поделиться» (spec 3.36.5, 5.29 R5) in their frame without a window: the variants are radio buttons each with the chip of
 * its format, in their order; a file from 100 MB says its weight bold in the colour of danger and what it means, any other quietly;
 * «Готовим…» on «Продолжить» answers nothing; the file being made says its variant, its format and what is left — «Готовим видео» only
 * for a variant with the picture — and «Отмена» stops it; a failure is a plate with two ways out; the title of each face is a heading.
 * And the sheet itself in its window: while a file is made it holds under a swipe, a tap beside it and «назад», and keeps the room of
 * its handle. The words are read in the composition.
 */
@RunWith(AndroidJUnit4::class)
class ShareSheetTest {
    @get:Rule
    val compose = createComposeRule()

    private val intents = mutableListOf<ShareIntent>()
    private val words = mutableMapOf<String, String>()
    private var danger = Color.Unspecified
    private var quiet = Color.Unspecified

    private fun show(sheet: ShareSheet) = show { sheet }

    /** [sheet] is read in the composition: a test may put one face in the place of another. */
    private fun show(sheet: () -> ShareSheet) {
        compose.setContent {
            SheetWords()
            ViolinTheme {
                danger = ViolinTheme.dangerSoft
                quiet = MaterialTheme.colorScheme.onSurfaceVariant
                val shown = sheet()
                AppSheetCard(bottom = { ShareSheetButtons(shown, onIntent = { intents += it }) }) {
                    ShareSheetContent(shown, onIntent = { intents += it }, landscape = false)
                }
            }
        }
        compose.waitForIdle()
    }

    /** The words of the sheet in the language of the composition. */
    @Composable
    private fun SheetWords() {
        words[BACKING] = stringResource(Res.string.share_backing)
        words[SOUND_ONLY] = stringResource(Res.string.share_sound_only)
        words[LARGE] = stringResource(Res.string.share_large_file)
        words[BUSY] = stringResource(Res.string.share_busy)
        words[CANCEL] = stringResource(Res.string.dialog_cancel)
        words[RETRY] = stringResource(Res.string.share_retry)
        words[AS_SHOT] = stringResource(Res.string.share_send_as_shot)
        val separator = stringResource(Res.string.dot_separator)
        words[LINE] = listOf(stringResource(Res.string.share_backing), MP4, stringResource(Res.string.share_remaining, REMAINING)).joinToString(separator)
        words[DETAILS] = stringResource(Res.string.share_video_details, Formats.duration(VIDEO.durationMs), VIDEO.resolution, Formats.fileSize(BIG))
        words[ORDINARY_DETAILS] = stringResource(Res.string.share_video_details, Formats.duration(VIDEO.durationMs), VIDEO.resolution, Formats.fileSize(VIDEO.originalBytes))
        words[TITLE] = stringResource(Res.string.share_title)
        words[VIDEO_PROCESSED] = stringResource(Res.string.share_video_processed)
        words[VIDEO_ORIGINAL] = stringResource(Res.string.share_video_original)
        words[PREPARING_FILE] = stringResource(Res.string.share_preparing)
        words[PREPARING_VIDEO] = stringResource(Res.string.share_preparing_video)
    }

    private fun word(key: String): String = words.getValue(key)

    private val radios = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton)

    /**
     * A video take under a backing (spec 3.36.5): four variants — «С минусовкой» first and chosen — each a radio button with the chip of
     * its format: three videos made or sent as «.mp4», the sound alone «.m4a».
     */
    @Test
    fun theVariantsAreRadioButtonsEachWithTheFormatOfItsFile() {
        show(ShareSheet.Choose(VIDEO, ShareVariant.BACKING, withText = true, busy = false))
        compose.onAllNodes(radios).assertCountEquals(VARIANTS)
        compose.onNode(radios and hasText(word(BACKING))).assertIsSelected()
        compose.onAllNodes(radios and hasText(MP4)).assertCountEquals(VIDEO_VARIANTS)
        compose.onNode(radios and hasText(M4A)).performClick()
        compose.runOnIdle { assertEquals(ShareVariant.SOUND, (intents.last() as ShareIntent.VariantSelected).variant) }
        compose.onNode(radios and hasText(word(SOUND_ONLY))).assertIsDisplayed()
    }

    /** The variants stand in their order (spec 3.36.5): from «как слышно» to «как было» — «С минусовкой» first, «Только звук» last. */
    @Test
    fun theVariantsStandFromAsHeardToAsItWas() {
        show(ShareSheet.Choose(VIDEO, ShareVariant.BACKING, withText = true, busy = false))
        val tops = listOf(BACKING, VIDEO_PROCESSED, VIDEO_ORIGINAL, SOUND_ONLY).map { key -> compose.onNode(radios and hasText(word(key))).getUnclippedBoundsInRoot().top }
        assertEquals("one under the other, in this order: $tops", tops.sortedBy { it.value }, tops)
        assertEquals("four rows, not one over another: $tops", tops.size, tops.distinct().size)
    }

    /** From 100 MB (spec 3.19, 5.29 R5): the weight in the line of the file is bold in the colour of danger, and says what it means. */
    @Test
    fun aLargeFileSaysItsWeightBoldAndWhatItMeans() {
        show(ShareSheet.Choose(VIDEO.copy(originalBytes = BIG), ShareVariant.PROCESSED, withText = true, busy = false))
        compose.onNodeWithText(word(LARGE)).assertIsDisplayed()
        val style = compose.onNodeWithText(word(DETAILS), useUnmergedTree = true).textLayout().layoutInput.style
        assertEquals(FontWeight.Bold, style.fontWeight)
        assertEquals("in the colour of danger", danger, style.color)
    }

    /** Below 100 MB the weight is said quietly — neither bold nor coloured — and nothing warns (spec 3.36.5). */
    @Test
    fun anOrdinaryFileSaysItsWeightQuietly() {
        show(ShareSheet.Choose(VIDEO, ShareVariant.PROCESSED, withText = true, busy = false))
        val style = compose.onNodeWithText(word(ORDINARY_DETAILS), useUnmergedTree = true).textLayout().layoutInput.style
        assertEquals(FontWeight.Normal, style.fontWeight)
        assertEquals(quiet, style.color)
        compose.onAllNodesWithText(word(LARGE)).assertCountEquals(0)
    }

    /** A short preparation (spec 3.17): «Готовим…» on the button, dimmed, pressed in vain. */
    @Test
    fun whileAShortPreparationRunsTheButtonSaysSoAndAnswersNothing() {
        show(ShareSheet.Choose(VIDEO, ShareVariant.BACKING, withText = true, busy = true))
        compose.onNodeWithText(word(BUSY)).assertIsNotEnabled().performClick()
        compose.runOnIdle { assertEquals(emptyList<ShareIntent>(), intents) }
    }

    /** The file being made (spec 3.36.5): «С минусовкой · .mp4 · осталось около 20 с» under the bar; «Отмена» stops it. */
    @Test
    fun theFileBeingMadeSaysItsVariantItsFormatAndWhatIsLeft() {
        show(ShareSheet.Preparing(VIDEO, ShareVariant.BACKING, percent = PERCENT, remainingSec = REMAINING))
        compose.onNodeWithText(word(LINE), useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText(word(CANCEL)).performClick()
        compose.runOnIdle { assertEquals(ShareIntent.CancelClicked, intents.last()) }
    }

    /**
     * «Готовим видео» only for a variant with the picture (spec 3.36.5): the sound alone of a video take is a file — «Готовим файл»;
     * the video with its processed sound — «Готовим видео».
     */
    @Test
    fun theFileBeingMadeIsCalledAVideoOnlyWhenItHasThePicture() {
        var sheet by mutableStateOf<ShareSheet>(ShareSheet.Preparing(VIDEO, ShareVariant.SOUND, percent = PERCENT, remainingSec = null))
        show { sheet }
        compose.onNodeWithText(word(PREPARING_FILE)).assertIsDisplayed()
        compose.onAllNodesWithText(word(PREPARING_VIDEO)).assertCountEquals(0)
        sheet = ShareSheet.Preparing(VIDEO, ShareVariant.PROCESSED, percent = PERCENT, remainingSec = null)
        compose.waitForIdle()
        compose.onNodeWithText(word(PREPARING_VIDEO)).assertIsDisplayed()
        compose.onAllNodesWithText(word(PREPARING_FILE)).assertCountEquals(0)
    }

    /** The title of every face is the heading of the sheet for TalkBack (spec 3.36.5): the choice, the file being made — one node — and the failure. */
    @Test
    fun theTitleOfEachFaceIsAHeading() {
        var sheet by mutableStateOf<ShareSheet>(ShareSheet.Choose(VIDEO, ShareVariant.BACKING, withText = true, busy = false))
        show { sheet }
        compose.onNode(isHeading() and hasText(word(TITLE))).assertIsDisplayed()
        sheet = ShareSheet.Preparing(VIDEO, ShareVariant.BACKING, percent = PERCENT, remainingSec = REMAINING)
        compose.waitForIdle()
        compose.onNode(isHeading() and hasText(word(PREPARING_VIDEO))).assertIsDisplayed()
        sheet = ShareSheet.Failed(VIDEO)
        compose.waitForIdle()
        compose.onNode(isHeading() and hasText(word(TITLE))).assertIsDisplayed()
    }

    /**
     * The sheet in its window (spec 3.36.5): while a file is made — «Готовим…» on «Продолжить», then its progress — it holds: a swipe, a
     * tap beside it and «назад» do not close it, and it tells its owner nothing; it keeps the room of its handle — its top edge stays where
     * it stood with the handle. Let go, a swipe hides it again and tells its owner.
     */
    @Test
    fun whileAFileIsMadeTheSheetHoldsAndKeepsTheRoomOfItsHandle() {
        var sheet by mutableStateOf<ShareSheet?>(null)
        compose.setContent {
            SheetWords()
            ViolinTheme {
                ShareSheetFrame(sheet) { intent ->
                    intents += intent
                    // as the view model does with the choice: a swipe drops it
                    if (intent == ShareIntent.Dismissed) sheet = null
                }
            }
        }
        compose.waitForIdle()
        val choice = ShareSheet.Choose(VIDEO, ShareVariant.BACKING, withText = true, busy = false)
        sheet = choice
        compose.waitForIdle()
        val top = sheetTop()

        sheet = choice.copy(busy = true)
        compose.waitForIdle()
        assertEquals("the sheet keeps its height: the room of its handle stays", top.value, sheetTop().value, 0.5f)
        holdsUnderEveryWayOut(word(TITLE))
        sheet = ShareSheet.Preparing(VIDEO, ShareVariant.BACKING, percent = PERCENT, remainingSec = REMAINING)
        compose.waitForIdle()
        holdsUnderEveryWayOut(word(PREPARING_VIDEO))
        compose.runOnIdle { assertTrue("nobody was asked to hide it: $intents", ShareIntent.Dismissed !in intents) }

        sheet = choice
        compose.waitForIdle()
        swipeDown(word(TITLE))
        compose.onNodeWithText(word(TITLE)).assertDoesNotExist()
        compose.runOnIdle { assertEquals(ShareIntent.Dismissed, intents.last()) }
    }

    /** The top edge of the sheet: the pane Material gives it. */
    private fun sheetTop() = compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.PaneTitle)).getUnclippedBoundsInRoot().top

    private fun swipeDown(text: String) {
        compose.onNodeWithText(text).performTouchInput {
            swipe(start = center, end = Offset(center.x, center.y + SWIPE.dp.toPx()), durationMillis = SWIPE_MS)
        }
        compose.waitForIdle()
    }

    /** A swipe down the sheet, a tap on the scrim at the top of its window and «назад»: the face titled [title] is still there. */
    private fun holdsUnderEveryWayOut(title: String) {
        swipeDown(title)
        compose.onNode(isDialog()).performTouchInput { click(Offset(centerX, 1f)) }
        compose.waitForIdle()
        Espresso.pressBack()
        compose.waitForIdle()
        compose.onNodeWithText(title).assertIsDisplayed()
    }

    /** What did not work (spec 3.36.5): a plate, «Ещё раз» and — of a video — «Отправить как снято» under it. */
    @Test
    fun aFailureOfAVideoIsAPlateWithTwoWaysOut() {
        show(ShareSheet.Failed(VIDEO))
        compose.onNodeWithText(word(RETRY)).performClick()
        compose.runOnIdle { assertEquals(ShareIntent.RetryClicked, intents.last()) }
        compose.onNodeWithText(word(AS_SHOT)).performClick()
        compose.runOnIdle { assertEquals(ShareIntent.SendOriginalClicked, intents.last()) }
        compose.onAllNodesWithText(word(CANCEL)).assertCountEquals(0)
    }

    private companion object {
        const val BACKING = "backing"
        const val SOUND_ONLY = "soundOnly"
        const val LARGE = "large"
        const val BUSY = "busy"
        const val CANCEL = "cancel"
        const val RETRY = "retry"
        const val AS_SHOT = "asShot"
        const val LINE = "line"
        const val DETAILS = "details"
        const val ORDINARY_DETAILS = "ordinaryDetails"
        const val TITLE = "title"
        const val VIDEO_PROCESSED = "videoProcessed"
        const val VIDEO_ORIGINAL = "videoOriginal"
        const val PREPARING_FILE = "preparingFile"
        const val PREPARING_VIDEO = "preparingVideo"
        const val SWIPE = 700
        const val SWIPE_MS = 150L
        const val MP4 = ".mp4"
        const val M4A = ".m4a"
        const val VARIANTS = 4
        const val VIDEO_VARIANTS = 3
        const val PERCENT = 42
        const val REMAINING = 20
        const val BIG = 612L * 1024 * 1024

        /** A video take of «Концерт ля минор» under a backing, 3:40, 1080p, 62 MB, with the processing of «Камерный зал». */
        val VIDEO = ShareInfo(
            sessionId = 2, fileName = "Концерт ля минор · 23 сентября.m4a", durationMs = 220_000, processedBytes = 3_700_000,
            originalBytes = 62L * 1024 * 1024, caption = SoundCaption.BuiltIn(BuiltInPreset.CHAMBER_HALL), message = "Концерт ля минор · 76 % · 23 сентября",
            videoFileName = "Концерт ля минор · 23 сентября.mp4", resolution = 1080, backing = true, backingBytes = 5_300_000,
        )
    }
}
