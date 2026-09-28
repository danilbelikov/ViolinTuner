package com.violinjourney.app.feature.practice.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.filter
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.violinjourney.app.core.domain.practice.ForgottenEndings
import com.violinjourney.app.core.domain.practice.ForgottenPractice
import com.violinjourney.app.core.domain.practice.PracticeConfig
import com.violinjourney.app.core.domain.practice.PracticeConfig.Companion.MS_PER_HOUR
import com.violinjourney.app.core.domain.practice.PracticeConfig.Companion.MS_PER_MINUTE
import com.violinjourney.app.core.domain.practice.RunningPractice
import com.violinjourney.app.core.ui.format.Formats
import com.violinjourney.app.core.ui.theme.ViolinTheme
import com.violinjourney.app.feature.practice.PracticePrompt
import com.violinjourney.app.feature.practice.PracticePromptIntent
import com.violinjourney.app.feature.practice.PracticeReducer
import com.violinjourney.app.shared.resources.Res
import com.violinjourney.app.shared.resources.practice_continue
import com.violinjourney.app.shared.resources.practice_end_at
import com.violinjourney.app.shared.resources.practice_end_at_length
import com.violinjourney.app.shared.resources.practice_end_now_length
import com.violinjourney.app.shared.resources.practice_forgotten_running
import com.violinjourney.app.shared.resources.practice_forgotten_silent
import com.violinjourney.app.shared.resources.practice_forgotten_sounded
import com.violinjourney.app.shared.resources.practice_forgotten_title
import com.violinjourney.app.shared.resources.practice_set_length
import com.violinjourney.app.shared.resources.practice_stop
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import org.jetbrains.compose.resources.stringResource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * «Занятие не закончено» as a bottom sheet (spec 3.36.3): a swipe, «назад» and a tap beside it are exactly one «Продолжаю
 * заниматься»; the line says which of the three it is and the buttons carry what each ending saves — no number under a minute; the
 * buttons are 56 and 48 by layout, not by the touch Compose widens by itself; a sheet answered slides away with its numbers, and
 * «Закончить занятие» takes its place in the same frame. What the sheet must say is written here — the times and lengths of the
 * mockup — not worked out by the rule the sheet reads its numbers with.
 */
@RunWith(AndroidJUnit4::class)
class ForgottenPracticeSheetTest {
    @get:Rule
    val compose = createComposeRule()

    private val zone = TimeZone.UTC
    private val config = PracticeConfig()
    private val now = Instant.parse("2026-09-27T20:50:00Z").toEpochMilliseconds()
    private val start = now - 3 * MS_PER_HOUR - 12 * MS_PER_MINUTE
    private val intents = mutableListOf<PracticePromptIntent>()
    private var prompt by mutableStateOf<PracticePrompt?>(null)

    /** The numbers of the view model: the rule of the app works them out, and an answer takes them away with the prompt. */
    private var numbers by mutableStateOf<ForgottenEndings?>(null)

    /** The words of the sheet in the language of the device, read where the sheet reads them. */
    private val words = mutableMapOf<String, String>()

    private fun show(practice: RunningPractice) {
        compose.setContent {
            ViolinTheme {
                words[TITLE] = stringResource(Res.string.practice_forgotten_title).uppercase()
                words[SUMMARY_TITLE] = stringResource(Res.string.practice_stop).uppercase()
                // begun at 17:38, 3 h 12 min ago; the violin last at 18:42 — 1 h 4 min in
                words[RUNNING] = stringResource(Res.string.practice_forgotten_running, Formats.minutesInWords(3 * MS_PER_HOUR + 12 * MS_PER_MINUTE))
                words[SOUNDED] = stringResource(Res.string.practice_forgotten_sounded, "18:42")
                words[SILENT] = stringResource(Res.string.practice_forgotten_silent)
                words[AT_MARK] = stringResource(Res.string.practice_end_at_length, "18:42", Formats.minutesInWords(MS_PER_HOUR + 4 * MS_PER_MINUTE))
                // a mark 40 s in: the time, and no length
                words[AT_SHORT_MARK] = stringResource(Res.string.practice_end_at, "17:38")
                words[NOW] = stringResource(Res.string.practice_end_now_length, Formats.minutesInWords(3 * MS_PER_HOUR + 12 * MS_PER_MINUTE))
                words[SET_LENGTH] = stringResource(Res.string.practice_set_length)
                words[CONTINUE] = stringResource(Res.string.practice_continue)
                PracticePromptHost(
                    prompt = prompt,
                    endings = { numbers },
                    stepMinutes = config.editStepMinutes,
                    onIntent = { intent ->
                        intents += intent
                        // the view model's answer: a sign of life, the sheet goes
                        if (intent == PracticePromptIntent.Continue) prompt = null
                    },
                    zone = zone,
                )
            }
        }
        compose.waitForIdle()
        numbers = ForgottenPractice.endings(practice, now, config)
        prompt = PracticePrompt.Forgotten(practice)
        compose.waitForIdle()
        compose.onNodeWithText(words.getValue(TITLE)).assertIsDisplayed()
    }

    private fun top(text: String): Dp = compose.onNodeWithText(text).getUnclippedBoundsInRoot().top

    private fun sounded() = show(RunningPractice(start, lastSoundEpochMs = start + MS_PER_HOUR + 4 * MS_PER_MINUTE))

    @Test
    fun aSwipeIsExactlyOneContinue() {
        sounded()
        compose.onNodeWithText(words.getValue(TITLE)).performTouchInput {
            swipe(start = center, end = Offset(center.x, center.y + SWIPE.dp.toPx()), durationMillis = SWIPE_MS)
        }
        compose.waitForIdle()
        compose.onNodeWithText(words.getValue(TITLE)).assertDoesNotExist()
        compose.runOnIdle { assertEquals(listOf<PracticePromptIntent>(PracticePromptIntent.Continue), intents) }
    }

    @Test
    fun backIsOneContinue() {
        sounded()
        Espresso.pressBack()
        compose.waitForIdle()
        compose.onNodeWithText(words.getValue(TITLE)).assertDoesNotExist()
        compose.runOnIdle { assertEquals(listOf<PracticePromptIntent>(PracticePromptIntent.Continue), intents) }
    }

    @Test
    fun aTapOnTheScrimIsContinue() {
        sounded()
        // the window of the sheet, tapped near its top: the scrim over the screen, far above the short sheet
        compose.onAllNodes(isRoot()).filter(hasAnyDescendant(hasText(words.getValue(TITLE)))).onFirst().performTouchInput {
            click(Offset(centerX, SCRIM_TAP.dp.toPx()))
        }
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(listOf<PracticePromptIntent>(PracticePromptIntent.Continue), intents) }
    }

    @Test
    fun theSoundedSheetSaysWhenAndItsButtonsWhatTheySave() {
        sounded()
        compose.onNodeWithText(words.getValue(RUNNING)).assertIsDisplayed()
        compose.onNodeWithText(words.getValue(SOUNDED)).assertIsDisplayed()
        compose.onNodeWithText(words.getValue(AT_MARK)).assertIsDisplayed().performClick()
        compose.onNodeWithText(words.getValue(NOW)).assertIsDisplayed().performClick()
        compose.onNodeWithText(words.getValue(CONTINUE)).assertIsDisplayed().performClick()
        compose.runOnIdle {
            assertEquals(
                listOf(PracticePromptIntent.EndAtLastSound, PracticePromptIntent.EndNow, PracticePromptIntent.Continue),
                intents,
            )
        }
    }

    @Test
    fun withoutASoundTheFirstAnswerSetsTheLength() {
        show(RunningPractice(start, lastSoundEpochMs = null))
        compose.onNodeWithText(words.getValue(SILENT)).assertIsDisplayed()
        compose.onNodeWithText(words.getValue(SET_LENGTH)).assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(listOf<PracticePromptIntent>(PracticePromptIntent.SetLength), intents) }
    }

    @Test
    fun aMarkUnderAMinuteHasNoNumber() {
        show(RunningPractice(start, lastSoundEpochMs = start + 40_000))
        // «Закончить в 17:38» — the whole words of the button, the time and no length: the answer would be «Слишком коротко»
        compose.onNodeWithText(words.getValue(AT_SHORT_MARK)).assertIsDisplayed()
    }

    /**
     * An answer takes the prompt and its numbers away at once — «Закончить сейчас» its numbers first, as its save clears the store —
     * while the sheet slides away with the face it had (spec 3.36.3: the numbers change at once, nothing else moves): all the way down
     * it keeps «Идёт 3 ч 12 мин» and the numbers on its buttons, no blank line, no buttons without numbers.
     */
    @Test
    fun anAnsweredSheetSlidesAwayWithItsNumbers() {
        sounded()
        compose.mainClock.autoAdvance = false
        numbers = null
        compose.mainClock.advanceTimeByFrame()
        prompt = null
        repeat(SOME_FRAMES) {
            compose.mainClock.advanceTimeByFrame()
            compose.onNodeWithText(words.getValue(TITLE)).assertExists()
            compose.onNodeWithText(words.getValue(RUNNING)).assertExists()
            compose.onNodeWithText(words.getValue(AT_MARK)).assertExists()
            compose.onNodeWithText(words.getValue(NOW)).assertExists()
        }
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        compose.onNodeWithText(words.getValue(TITLE)).assertDoesNotExist()
    }

    /**
     * «Указать, сколько играли» (spec 3.36.3): «Закончить занятие» — taller, with its stepper — takes the place of «Занятие не
     * закончено» in the same frame. The view model's answer is a new prompt written once, as `AppStartViewModel` writes it; frame by
     * frame the label on top never goes below the lower of the places the two faces settle at, and neither face was hidden on the way.
     */
    @Test
    fun theSummaryTakesThePlaceOfTheSheetInTheSameFrame() {
        show(RunningPractice(start, lastSoundEpochMs = null))
        compose.onNodeWithText(words.getValue(SET_LENGTH)).performClick()
        compose.waitForIdle()
        val stood = top(words.getValue(TITLE))
        compose.mainClock.autoAdvance = false
        prompt = PracticePrompt.Summary(PracticeReducer.summarySheet(start, 3 * MS_PER_HOUR + 12 * MS_PER_MINUTE, config))
        var lowest = stood
        repeat(FACE_FRAMES) {
            compose.mainClock.advanceTimeByFrame()
            listOf(TITLE, SUMMARY_TITLE).map(words::getValue).forEach { label ->
                if (compose.onAllNodesWithText(label).fetchSemanticsNodes().isNotEmpty()) lowest = maxOf(lowest, top(label))
            }
        }
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        compose.onNodeWithText(words.getValue(SUMMARY_TITLE)).assertIsDisplayed()
        val settled = top(words.getValue(SUMMARY_TITLE))
        assertTrue("in place: at most $lowest, the faces stand at $stood and $settled", lowest <= maxOf(stood, settled) + 1.dp)
        // no Continue, no SummaryHidden: nothing hid either face
        compose.runOnIdle { assertEquals(listOf<PracticePromptIntent>(PracticePromptIntent.SetLength), intents) }
    }

    @Test
    fun theButtonsAre56And48ByLayout() {
        sounded()
        compose.onNodeWithText(words.getValue(AT_MARK)).assertHeightIsAtLeast(56.dp)
        compose.onNodeWithText(words.getValue(NOW)).assertHeightIsAtLeast(56.dp)
        compose.onNodeWithText(words.getValue(CONTINUE)).assertHeightIsAtLeast(48.dp)
    }

    private companion object {
        const val TITLE = "title"
        const val SUMMARY_TITLE = "summaryTitle"
        const val RUNNING = "running"
        const val SOUNDED = "sounded"
        const val SILENT = "silent"
        const val AT_MARK = "atMark"
        const val AT_SHORT_MARK = "atShortMark"
        const val NOW = "now"
        const val SET_LENGTH = "setLength"
        const val CONTINUE = "continue"
        const val SWIPE = 700
        const val SWIPE_MS = 150L
        const val SCRIM_TAP = 40

        /** Frames into a slide of 250 ms or so: the sheet is still on its way down. */
        const val SOME_FRAMES = 3

        /** Frames enough for a slide down and up (≈ 0.6 s) to show. */
        const val FACE_FRAMES = 40
    }
}
