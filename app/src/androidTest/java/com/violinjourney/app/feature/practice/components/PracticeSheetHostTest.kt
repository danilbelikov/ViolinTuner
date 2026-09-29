package com.violinjourney.app.feature.practice.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.WindowInfo
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.violinjourney.app.core.domain.journey.JourneyConfig
import com.violinjourney.app.core.domain.journey.JourneyRules
import com.violinjourney.app.core.domain.practice.PracticeConfig
import com.violinjourney.app.core.domain.practice.PracticeConfig.Companion.MS_PER_HOUR
import com.violinjourney.app.core.domain.practice.PracticeConfig.Companion.MS_PER_MINUTE
import com.violinjourney.app.core.domain.practice.PracticeEntry
import com.violinjourney.app.core.domain.practice.PracticeRecap
import com.violinjourney.app.core.domain.practice.RecapRoad
import com.violinjourney.app.core.domain.progress.Profile
import com.violinjourney.app.core.domain.progress.Progress
import com.violinjourney.app.core.domain.progress.ProgressConfig
import com.violinjourney.app.core.domain.progress.Trophy
import com.violinjourney.app.core.ui.motion.LocalReduceMotion
import com.violinjourney.app.core.ui.theme.ViolinTheme
import com.violinjourney.app.feature.practice.PracticeIntent
import com.violinjourney.app.feature.practice.PracticeReducer
import com.violinjourney.app.feature.practice.PracticeSheet
import com.violinjourney.app.feature.practice.PracticeState
import com.violinjourney.app.shared.resources.Res
import com.violinjourney.app.shared.resources.gift_thanks
import com.violinjourney.app.shared.resources.home_travel
import com.violinjourney.app.shared.resources.path_all_trophies
import com.violinjourney.app.shared.resources.path_name_photo
import com.violinjourney.app.shared.resources.practice_stop
import com.violinjourney.app.shared.resources.profile_done
import com.violinjourney.app.shared.resources.profile_other_photo
import com.violinjourney.app.shared.resources.profile_pick_photo
import com.violinjourney.app.shared.resources.profile_remove_photo
import com.violinjourney.app.shared.resources.progress_trophy_names
import com.violinjourney.app.shared.resources.recap_title
import com.violinjourney.app.shared.resources.trophies_count
import com.violinjourney.app.shared.resources.trophies_heading_description
import com.violinjourney.app.shared.resources.trophies_title
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.YearMonth
import org.jetbrains.compose.resources.stringArrayResource
import org.jetbrains.compose.resources.stringResource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The one frame of «Занятия» since stage 107 (spec 3.36.3): «Трофеи» take the place of «Мой путь» and «назад» gives it back, the
 * recap takes the place of «Закончить занятие» — in the same frame each time, never a window of their own that slides away or rises
 * from the bottom. A face that has just come in the place of another does not take the second tap of a double tap (5.29 R3); the
 * recap of a low window stands in two columns with its one «Готово» at the bottom of the right one; the name typed in «Имя и фото»
 * is stored when the sheet is swiped away; a photo on disk is one before it is decoded. The model is the test's: a sheet written
 * once, as the view model writes it.
 *
 * The words are read where the sheet reads them, in the composition: a context read when the test is made may still speak the
 * language of the device, while the activity of the test — and with it the sheet — speaks the one chosen for the app in the system.
 */
@RunWith(AndroidJUnit4::class)
class PracticeSheetHostTest {
    @get:Rule
    val compose = createComposeRule()

    private val today = LocalDate(2026, 9, 27)
    private val intents = mutableListOf<PracticeIntent>()
    private var sheet by mutableStateOf<PracticeSheet?>(null)

    /** The table of trophies: 1 h and 10 h given and seen — «Трофеи, 2 из 10» — unless a test says otherwise. */
    private var trophies by mutableStateOf(listOf(Trophy(1, today, shown = true), Trophy(10, today, shown = true)))

    /** The file of the photo of the profile: the screen may not have decoded it yet ([PracticeSheetHost] gets no picture here). */
    private var avatarPath by mutableStateOf<String?>(null)

    /** The view model's answer to an intent, written once as it writes it; none unless a test gives one. */
    private var answer: (PracticeIntent) -> Unit = {}

    /** The words of the sheets in the language they speak, read where they read them. */
    private val words = mutableMapOf<String, String>()
    private var names: List<String> = emptyList()

    /** 47 h 17 min at the violin. */
    private fun state(): PracticeState = PracticeReducer.stateOf(
        entries = listOf(PracticeEntry(today, startedAtEpochMs = 0, durationMs = TOTAL, manual = true)),
        sessions = emptyList(),
        runningSince = null,
        month = YearMonth(2026, 9),
        selectedDate = null,
        sheet = sheet,
        today = today,
        zone = TimeZone.UTC,
        config = PracticeConfig(),
        trophies = trophies,
        profile = Profile.EMPTY,
        avatarPath = avatarPath,
        progressConfig = ProgressConfig(),
    )

    private fun seen(hours: Int) {
        trophies = trophies.map { if (it.hours == hours) it.copy(shown = true) else it }
    }

    /** A window of [width] × [height] for the host: the phone on its side makes the recap two columns. */
    private class Window(private val size: IntSize) : WindowInfo {
        override val isWindowFocused: Boolean = true
        override val containerSize: IntSize get() = size
    }

    @Composable
    private fun lowWindow(): WindowInfo = with(LocalDensity.current) { Window(IntSize(LOW_WIDTH.dp.roundToPx(), LOW_HEIGHT.dp.roundToPx())) }

    /**
     * The host on the screen with nothing in its frame; then [first] written into the model, and [trophiesThen] into the table of
     * trophies when given — a value that comes after the frame is there, as it comes on «Занятия». [low] — the host stands in a
     * window lower than 520 dp (the window of its sheet stays the device's).
     */
    private fun show(first: PracticeSheet?, trophiesThen: List<Trophy>? = null, low: Boolean = false) {
        compose.setContent {
            words[ALL_TROPHIES] = stringResource(Res.string.path_all_trophies)
            words[TROPHIES_HEADING] = stringResource(
                Res.string.trophies_heading_description,
                stringResource(Res.string.trophies_title),
                stringResource(Res.string.trophies_count, 2, 10),
            )
            words[SUMMARY_TITLE] = stringResource(Res.string.practice_stop).uppercase()
            words[RECAP_TITLE] = stringResource(Res.string.recap_title)
            words[DONE] = stringResource(Res.string.profile_done)
            words[THANKS] = stringResource(Res.string.gift_thanks)
            words[TRAVEL] = stringResource(Res.string.home_travel)
            words[NAME_TITLE] = stringResource(Res.string.path_name_photo).uppercase()
            words[PICK_PHOTO] = stringResource(Res.string.profile_pick_photo)
            words[OTHER_PHOTO] = stringResource(Res.string.profile_other_photo)
            words[REMOVE_PHOTO] = stringResource(Res.string.profile_remove_photo)
            names = stringArrayResource(Res.array.progress_trophy_names)
            ViolinTheme {
                // still: the shine of the level bar of «Мой путь» runs now and then for ever; the frame's own motion is Material's
                CompositionLocalProvider(LocalReduceMotion provides true) {
                    val host: @Composable () -> Unit = {
                        PracticeSheetHost(
                            state(),
                            onIntent = { intent ->
                                intents += intent
                                answer(intent)
                            },
                            zone = TimeZone.UTC,
                            photo = null,
                        )
                    }
                    if (low) CompositionLocalProvider(LocalWindowInfo provides lowWindow(), content = host) else host()
                }
            }
        }
        compose.waitForIdle()
        sheet = first
        if (trophiesThen != null) trophies = trophiesThen
        compose.waitForIdle()
    }

    private fun word(key: String): String = words.getValue(key)

    private fun top(node: SemanticsNodeInteraction): Dp = node.getUnclippedBoundsInRoot().top

    /** One of the faces on screen, and where it stands now: the lower of the two when both are there. */
    private fun lowestOf(texts: List<String>, descriptions: List<String>): Dp? {
        val tops = texts.filter { compose.onAllNodesWithText(it).fetchSemanticsNodes().isNotEmpty() }.map { top(compose.onNodeWithText(it)) } +
            descriptions.filter { compose.onAllNodesWithContentDescription(it, substring = true).fetchSemanticsNodes().isNotEmpty() }
                .map { top(compose.onNodeWithContentDescription(it, substring = true)) }
        return tops.maxOrNull()
    }

    /**
     * [next] written into the model at once; frame by frame the face on screen — [texts] and [descriptions] of both faces — never goes
     * below the lower of the places the two settle at ([stood], and where [settledOf] finds the new one). A frame that slid down and
     * rose again, or a window made anew for the new face and rising from the bottom, would take it far below.
     */
    private fun swapInPlace(stood: Dp, next: PracticeSheet?, texts: List<String>, descriptions: List<String>, settledOf: () -> SemanticsNodeInteraction) {
        compose.mainClock.autoAdvance = false
        sheet = next
        var lowest = stood
        repeat(FACE_FRAMES) {
            compose.mainClock.advanceTimeByFrame()
            lowestOf(texts, descriptions)?.let { lowest = maxOf(lowest, it) }
        }
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        val settled = top(settledOf().assertIsDisplayed())
        assertTrue("in place: at most $lowest, the faces stand at $stood and $settled", lowest <= maxOf(stood, settled) + 1.dp)
    }

    @Test
    fun theTrophiesTakeThePlaceOfMyPathAndBackGivesItBackInTheSameFrame() {
        show(PracticeSheet.Path)
        val allTrophies = word(ALL_TROPHIES)
        val trophiesHeading = word(TROPHIES_HEADING)
        val path = top(compose.onNodeWithText(allTrophies).assertIsDisplayed())
        // «Все трофеи»: the view model puts «Трофеи» over «Мой путь»
        swapInPlace(path, PracticeSheet.Trophies, listOf(allTrophies), listOf(trophiesHeading)) { compose.onNodeWithContentDescription(trophiesHeading) }

        val trophiesTop = top(compose.onNodeWithContentDescription(trophiesHeading))
        Espresso.pressBack()
        compose.waitForIdle()
        // «назад» of «Трофеи» is its way back, not a hide of the frame
        compose.runOnIdle { assertEquals(listOf<PracticeIntent>(PracticeIntent.TrophiesClosed), intents) }
        swapInPlace(trophiesTop, PracticeSheet.Path, listOf(allTrophies), listOf(trophiesHeading)) { compose.onNodeWithText(allTrophies) }
        compose.runOnIdle { assertEquals("nothing hid either face", 1, intents.size) }
    }

    @Test
    fun theRecapTakesThePlaceOfTheSummaryInTheSameFrame() {
        show(PracticeReducer.summarySheet(0, 47 * MS_PER_MINUTE, PracticeConfig()))
        val summaryTitle = word(SUMMARY_TITLE)
        val recapTitle = word(RECAP_TITLE)
        val stood = top(compose.onNodeWithText(summaryTitle).assertIsDisplayed())
        // «Сохранить»: the view model puts «Занятие сохранено» in the place of «Закончить занятие» (spec 3.31)
        swapInPlace(stood, PracticeSheet.Recap(recap()), listOf(summaryTitle), listOf(recapTitle)) {
            // the paragraph TalkBack reads for the whole recap starts with its title
            compose.onNodeWithContentDescription(recapTitle, substring = true)
        }
        compose.runOnIdle { assertTrue("nothing hid the summary: $intents", intents.isEmpty()) }
    }

    /**
     * A double tap on «Готово» of the recap with a gift waiting (5.29 R3): the gift takes the place of the recap under the finger, its
     * «Спасибо» where «Готово» was — the second tap, a frame or two later, is not «Спасибо»: the trophy is not taken as seen before it
     * was seen. Once the time of a double tap has passed, «Спасибо» answers.
     */
    @Test
    fun aSecondTapOfDoneDoesNotAcceptTheGiftThatCameInItsPlace() {
        answer = { intent ->
            when (intent) {
                PracticeIntent.RecapClosed -> sheet = null
                is PracticeIntent.GiftAccepted -> seen(intent.hours)
                else -> Unit
            }
        }
        // the trophy of 1 h given with the practice and not seen: the gift waits for the recap
        show(PracticeSheet.Recap(recap()), trophiesThen = listOf(Trophy(1, today, shown = false), Trophy(10, today, shown = true)))
        val done = compose.onNodeWithText(word(DONE)).assertIsDisplayed().getUnclippedBoundsInRoot()
        compose.mainClock.autoAdvance = false
        compose.onNodeWithText(word(DONE)).performClick()
        // the model's answer reaches the frame, a frame or two — far less than a double tap
        compose.waitForIdle()
        repeat(TAP_FRAMES) { compose.mainClock.advanceTimeByFrame() }
        val thanks = compose.onNodeWithText(word(THANKS))
        assertEquals("«Спасибо» is under the finger that pressed «Готово»", done.top.value, thanks.getUnclippedBoundsInRoot().top.value, 1f)
        thanks.performClick()
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        compose.runOnIdle { assertEquals("the second tap took nothing", listOf<PracticeIntent>(PracticeIntent.RecapClosed), intents) }
        compose.onNodeWithText(word(THANKS)).assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(listOf(PracticeIntent.RecapClosed, PracticeIntent.GiftAccepted(1)), intents) }
    }

    /** Two gifts in a row: «Спасибо» twice fast accepts the first one only; «Колок» in its place is seen before it is answered. */
    @Test
    fun aSecondTapOfThanksDoesNotAcceptTheNextGiftUnseen() {
        answer = { intent -> if (intent is PracticeIntent.GiftAccepted) seen(intent.hours) }
        show(first = null, trophiesThen = listOf(Trophy(1, today, shown = false), Trophy(10, today, shown = false)))
        compose.onNodeWithContentDescription(names[0], substring = true).assertIsDisplayed()
        compose.mainClock.autoAdvance = false
        compose.onNodeWithText(word(THANKS)).performClick()
        compose.waitForIdle()
        repeat(TAP_FRAMES) { compose.mainClock.advanceTimeByFrame() }
        // «Колок» in the place of «Канифоль», and the second tap on its «Спасибо»
        compose.onNodeWithContentDescription(names[1], substring = true).assertExists()
        compose.onNodeWithText(word(THANKS)).performClick()
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        compose.runOnIdle { assertEquals("one gift answered", listOf<PracticeIntent>(PracticeIntent.GiftAccepted(1)), intents) }
        compose.onNodeWithContentDescription(names[1], substring = true).assertIsDisplayed()
        compose.onNodeWithText(word(THANKS)).performClick()
        compose.runOnIdle { assertEquals(listOf(PracticeIntent.GiftAccepted(1), PracticeIntent.GiftAccepted(10)), intents) }
    }

    /**
     * The host in a window lower than 520 dp lays the recap in two columns (spec 3.36.3): the frame does not scroll around them (its
     * own branch — a sheet that scrolled around the columns would measure their scrolls in an endless height), the buttons are not
     * pinned (one «Готово», not two), the road stands in the right column beside what was earned, «Готово» in the right column under
     * the road. Only what does not hang on the width is checked: the low window is the host's own ([LocalWindowInfo] of 892 × 412),
     * but the window of the sheet is the device's, portrait — the columns are some 180 dp wide instead of 294, the card «Хватает до
     * Праги» grows tall around its button, and heights are not those of a phone on its side. That «Готово» stands at the bottom of
     * the right column in sight in a low room is [ProgressSheetsTest.inALowWindowDoneStandsAtTheBottomOfTheRightColumnInSight].
     */
    @Test
    fun inALowWindowTheHostLaysTheRecapInTwoColumnsWithOneDone() {
        show(PracticeSheet.Recap(recap(enough = true)), low = true)
        val head = compose.onNodeWithContentDescription(word(RECAP_TITLE), substring = true).assertIsDisplayed().getUnclippedBoundsInRoot()
        val travel = compose.onNodeWithText(word(TRAVEL)).assertIsDisplayed().getUnclippedBoundsInRoot()
        compose.onAllNodesWithText(word(DONE)).assertCountEquals(1)
        val done = compose.onNodeWithText(word(DONE)).assertIsDisplayed().getUnclippedBoundsInRoot()
        // in one column the header is as wide as the sheet, and nothing stands right of it
        assertTrue("the road beside what was earned: ${travel.left} right of ${head.right}", travel.left >= head.right)
        assertTrue("«Готово» in the right column: ${done.left} right of ${head.right}", done.left >= head.right)
        assertTrue("under the road: ${done.top} under ${travel.bottom}", done.top >= travel.bottom)
        compose.runOnIdle { assertTrue("nothing hid it: $intents", intents.isEmpty()) }
    }

    /** «Имя и фото» (spec 3.13, 3.36.3): the name typed in the field is stored however the sheet is closed — a swipe too. */
    @Test
    fun aNameTypedInNamePhotoIsStoredWhenTheSheetIsSwipedAway() {
        var draft = ""
        var stored: String? = null
        answer = { intent ->
            when (intent) {
                is PracticeIntent.ProfileNameChanged -> draft = intent.text
                // the view model stores the draft and gives «Мой путь» back
                PracticeIntent.ProfileClosed -> {
                    stored = draft
                    sheet = PracticeSheet.Path
                }
                else -> Unit
            }
        }
        show(PracticeSheet.Profile(nameDraft = "", importingPhoto = false))
        compose.onNode(hasSetTextAction()).performTextInput(NAME)
        compose.onNodeWithText(word(NAME_TITLE)).performTouchInput {
            swipe(start = center, end = Offset(center.x, center.y + SWIPE.dp.toPx()), durationMillis = SWIPE_MS)
        }
        compose.waitForIdle()
        compose.runOnIdle {
            assertEquals("stored as typed", NAME, stored)
            assertEquals("closed once: $intents", 1, intents.count { it == PracticeIntent.ProfileClosed })
        }
        compose.onNodeWithText(word(ALL_TROPHIES)).assertExists()
    }

    /**
     * A photo on disk the screen has not decoded yet (spec 3.36.3): «Имя и фото» knows it by its file — «Другое фото» and «Убрать
     * фото», not «Выбрать фото» that would jump to them once the picture is there.
     */
    @Test
    fun aPhotoOnDiskHasItsButtonsBeforeItIsDecoded() {
        avatarPath = "avatar.jpg"
        show(PracticeSheet.Profile(nameDraft = "Аня", importingPhoto = false))
        compose.onNodeWithText(word(OTHER_PHOTO)).assertIsDisplayed()
        compose.onNodeWithText(word(REMOVE_PHOTO)).assertIsDisplayed()
        compose.onAllNodesWithText(word(PICK_PHOTO)).assertCountEquals(0)
    }

    /** The recap of the mockup: 47 minutes, 212 notes, two elements, Vienna → Prague, eight days; [enough] — enough for Prague. */
    private fun recap(enough: Boolean = false): PracticeRecap {
        val duration = 47 * MS_PER_MINUTE
        val sources = JourneyRules.taktsBySource(212, duration, JourneyConfig(), 2)
        val progress = ProgressConfig()
        val road = if (enough) {
            RecapRoad.Leg(nextIndex = 5, price = 3_000, balanceBefore = 3_000L - sources.total, balanceAfter = 3_000)
        } else {
            RecapRoad.Leg(nextIndex = 5, price = 3_000, balanceBefore = 1_647, balanceAfter = 1_872)
        }
        return PracticeRecap(
            durationMs = duration, dayTotalMs = null, takts = sources.total, sources = sources,
            road = road,
            streakDays = 8, streakExtended = true,
            levelBefore = Progress.levelOf(TOTAL, progress), levelAfter = Progress.levelOf(TOTAL + duration, progress),
        )
    }

    private companion object {
        const val TOTAL = 47 * MS_PER_HOUR + 17 * MS_PER_MINUTE

        /** Frames enough for a slide down and up (≈ 0.6 s) to show. */
        const val FACE_FRAMES = 40

        /** Between the two taps of a double tap: two frames, 32 ms — well inside the time of a double tap (300 ms). */
        const val TAP_FRAMES = 2

        /** A phone on its side: lower than the 520 dp of the two columns. */
        const val LOW_WIDTH = 892
        const val LOW_HEIGHT = 412

        const val NAME = "Аня"
        const val SWIPE = 700
        const val SWIPE_MS = 150L

        const val ALL_TROPHIES = "allTrophies"
        const val TROPHIES_HEADING = "trophiesHeading"
        const val SUMMARY_TITLE = "summaryTitle"
        const val RECAP_TITLE = "recapTitle"
        const val DONE = "done"
        const val THANKS = "thanks"
        const val TRAVEL = "travel"
        const val NAME_TITLE = "nameTitle"
        const val PICK_PHOTO = "pickPhoto"
        const val OTHER_PHOTO = "otherPhoto"
        const val REMOVE_PHOTO = "removePhoto"
    }
}
