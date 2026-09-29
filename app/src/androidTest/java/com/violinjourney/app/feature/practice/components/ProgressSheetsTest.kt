package com.violinjourney.app.feature.practice.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.WindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.violinjourney.app.core.domain.journey.JourneyConfig
import com.violinjourney.app.core.domain.journey.JourneyRules
import com.violinjourney.app.core.domain.practice.PracticeConfig.Companion.MS_PER_HOUR
import com.violinjourney.app.core.domain.practice.PracticeConfig.Companion.MS_PER_MINUTE
import com.violinjourney.app.core.domain.practice.PracticeRecap
import com.violinjourney.app.core.domain.practice.RecapRoad
import com.violinjourney.app.core.domain.progress.Profile
import com.violinjourney.app.core.domain.progress.Progress
import com.violinjourney.app.core.domain.progress.ProgressConfig
import com.violinjourney.app.core.text.codePointLength
import com.violinjourney.app.core.ui.theme.ViolinTheme
import com.violinjourney.app.feature.practice.PracticeIntent
import com.violinjourney.app.feature.practice.PracticeSheet
import com.violinjourney.app.shared.resources.Res
import com.violinjourney.app.shared.resources.home_travel
import com.violinjourney.app.shared.resources.profile_done
import com.violinjourney.app.shared.resources.profile_other_photo
import com.violinjourney.app.shared.resources.profile_pick_photo
import com.violinjourney.app.shared.resources.profile_remove_photo
import org.jetbrains.compose.resources.stringResource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * «Имя и фото» and the recap by their layout and their answers (spec 3.36.3, 5.29 R3): the field of the name takes the focus at once
 * only in a window taller than wide, tells the model what is typed — cut at 24 — and the keyboard's «Готово» closes the sheet; one
 * «Выбрать фото» without a photo, «Другое фото» and «Убрать фото» with one, both dimmed while a photo is copied; «Другое фото», «Убрать
 * фото» and «В дорогу →» are buttons of 48 by their own layout — Material's minimum touch size is taken away here, or a button of 40
 * would be widened to 48 by it and the test could never fail (the lesson of stage 101); the recap of a low window keeps «Готово» at the
 * bottom of its right column, in sight without scrolling.
 *
 * The words are read where the sheets read them, in the composition, in the language they speak.
 */
@RunWith(AndroidJUnit4::class)
class ProgressSheetsTest {
    @get:Rule
    val compose = createComposeRule()

    private val intents = mutableListOf<PracticeIntent>()
    private val words = mutableMapOf<String, String>()

    /** A window of [width] × [height]: what «Имя и фото» asks to know whether it stands in portrait. */
    private class Window(private val size: IntSize) : WindowInfo {
        override val isWindowFocused: Boolean = true
        override val containerSize: IntSize get() = size
    }

    @Composable
    private fun window(width: Dp, height: Dp): WindowInfo = with(LocalDensity.current) { Window(IntSize(width.roundToPx(), height.roundToPx())) }

    @Composable
    private fun readWords() {
        words[PICK_PHOTO] = stringResource(Res.string.profile_pick_photo)
        words[OTHER_PHOTO] = stringResource(Res.string.profile_other_photo)
        words[REMOVE_PHOTO] = stringResource(Res.string.profile_remove_photo)
        words[TRAVEL] = stringResource(Res.string.home_travel)
        words[DONE] = stringResource(Res.string.profile_done)
    }

    private fun word(key: String): String = words.getValue(key)

    private fun showNamePhoto(
        width: Dp = 412.dp,
        height: Dp = 892.dp,
        hasPhoto: Boolean = false,
        importing: Boolean = false,
        minimumTouch: Boolean = true,
    ) {
        compose.setContent {
            readWords()
            ViolinTheme {
                CompositionLocalProvider(
                    LocalWindowInfo provides window(width, height),
                    LocalMinimumInteractiveComponentSize provides if (minimumTouch) 48.dp else Dp.Unspecified,
                ) {
                    NamePhotoSheetContent(
                        sheet = PracticeSheet.Profile(nameDraft = "", importingPhoto = importing),
                        hasPhoto = hasPhoto,
                        photo = null,
                        onIntent = { intents += it },
                        onPickPhoto = { intents += PracticeIntent.ProfilePhotoPicked(PICKED) },
                    )
                }
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun inPortraitTheNameTakesTheFocusAtOnce() {
        showNamePhoto(width = 412.dp, height = 892.dp)
        compose.onNode(hasSetTextAction()).assertIsFocused()
    }

    @Test
    fun inLandscapeTheNameWaitsForATouch() {
        showNamePhoto(width = 892.dp, height = 412.dp)
        compose.onNode(hasSetTextAction()).assertIsNotFocused()
    }

    @Test
    fun thePhotoButtonsAre48ByTheirOwnLayout() {
        showNamePhoto(hasPhoto = true, minimumTouch = false)
        compose.onNodeWithText(word(OTHER_PHOTO)).assertHeightIsAtLeast(BUTTON.dp)
        compose.onNodeWithText(word(REMOVE_PHOTO)).assertHeightIsAtLeast(BUTTON.dp)
    }

    /** What is typed goes to the model as it is typed — cut at 24 (spec 3.13) — and the keyboard's «Готово» is «Готово» of the sheet. */
    @Test
    fun theNameTypedGoesToTheModelAndTheKeyboardsDoneClosesTheSheet() {
        showNamePhoto(width = 892.dp, height = 412.dp)
        compose.onNode(hasSetTextAction()).performTextInput(NAME)
        compose.runOnIdle { assertEquals(NAME, intents.filterIsInstance<PracticeIntent.ProfileNameChanged>().last().text) }
        compose.onNode(hasSetTextAction()).performTextReplacement(TOO_LONG)
        compose.runOnIdle {
            val last = intents.filterIsInstance<PracticeIntent.ProfileNameChanged>().last().text
            assertEquals("cut at the limit: «$last»", Profile.MAX_NAME_LENGTH, last.codePointLength())
            assertTrue("the start of what was typed", TOO_LONG.startsWith(last))
        }
        compose.onNode(hasSetTextAction()).performImeAction()
        compose.runOnIdle { assertEquals(PracticeIntent.ProfileClosed, intents.last()) }
    }

    /** No photo (spec 3.36.3): one action under the avatar — «Выбрать фото»; nothing to take away. */
    @Test
    fun withoutAPhotoThereIsOnlyPickAPhoto() {
        showNamePhoto(hasPhoto = false)
        compose.onNodeWithText(word(PICK_PHOTO)).assertIsDisplayed().assertIsEnabled().performClick()
        compose.onAllNodesWithText(word(REMOVE_PHOTO)).assertCountEquals(0)
        compose.onAllNodesWithText(word(OTHER_PHOTO)).assertCountEquals(0)
        compose.runOnIdle { assertEquals(listOf<PracticeIntent>(PracticeIntent.ProfilePhotoPicked(PICKED)), intents) }
    }

    /** A photo: «Другое фото» picks another, «Убрать фото» tells the model to take it away. */
    @Test
    fun withAPhotoItIsChangedOrTakenAway() {
        showNamePhoto(hasPhoto = true)
        compose.onAllNodesWithText(word(PICK_PHOTO)).assertCountEquals(0)
        compose.onNodeWithText(word(REMOVE_PHOTO)).assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(listOf<PracticeIntent>(PracticeIntent.ProfilePhotoRemoved), intents) }
        compose.onNodeWithText(word(OTHER_PHOTO)).assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(PracticeIntent.ProfilePhotoPicked(PICKED), intents.last()) }
    }

    /** While a picked photo is copied (spec 3.36.3) both buttons are dimmed and pressed in vain — without a photo too. */
    @Test
    fun whileAPhotoIsCopiedItsButtonsWait() {
        showNamePhoto(hasPhoto = true, importing = true)
        compose.onNodeWithText(word(OTHER_PHOTO)).assertIsNotEnabled().performClick()
        compose.onNodeWithText(word(REMOVE_PHOTO)).assertIsNotEnabled().performClick()
        compose.runOnIdle { assertTrue("nothing asked: $intents", intents.isEmpty()) }
    }

    @Test
    fun whileTheFirstPhotoIsCopiedPickAPhotoWaits() {
        showNamePhoto(hasPhoto = false, importing = true)
        compose.onNodeWithText(word(PICK_PHOTO)).assertIsNotEnabled()
    }

    @Test
    fun theWayOfTheRecapIsAButtonOf48ByItsOwnLayout() {
        compose.setContent {
            readWords()
            ViolinTheme {
                CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
                    RecapSheetContent(recap(), onTravel = {}, onDone = {}, low = false, animated = false)
                }
            }
        }
        compose.onNodeWithText(word(TRAVEL)).assertHeightIsAtLeast(BUTTON.dp)
    }

    /**
     * The recap in the room a low window leaves it (spec 3.36.3): two columns, and «Готово» — one — at the bottom of the right one,
     * in sight without scrolling: where the room ends, not under the right column's last card, while the left column is taller than
     * the room and scrolls by itself.
     */
    @Test
    fun inALowWindowDoneStandsAtTheBottomOfTheRightColumnInSight() {
        var dones = 0
        compose.setContent {
            readWords()
            ViolinTheme {
                Box(Modifier.size(LOW_ROOM_WIDTH.dp, LOW_ROOM_HEIGHT.dp).testTag(ROOM)) {
                    RecapSheetContent(recap(), onTravel = {}, onDone = { dones++ }, low = true, animated = false)
                }
            }
        }
        val room = compose.onNodeWithTag(ROOM).getUnclippedBoundsInRoot()
        compose.onAllNodesWithText(word(DONE)).assertCountEquals(1)
        val done = compose.onNodeWithText(word(DONE)).assertIsDisplayed().getUnclippedBoundsInRoot()
        assertEquals("at the bottom of the room", room.bottom.value, done.bottom.value, 1f)
        assertTrue("in the right column: ${done.left} of ${room.left}–${room.right}", done.left >= room.left + (room.right - room.left) / 2 - 1.dp)
        compose.onNodeWithText(word(DONE)).performClick()
        compose.runOnIdle { assertEquals(1, dones) }
    }

    /** Enough for Prague: «Хватает до Праги» and «В дорогу →». */
    private fun recap(): PracticeRecap {
        val duration = 65 * MS_PER_MINUTE
        val sources = JourneyRules.taktsBySource(380, duration, JourneyConfig(), 1)
        val progress = ProgressConfig()
        return PracticeRecap(
            durationMs = duration, dayTotalMs = null, takts = sources.total, sources = sources,
            road = RecapRoad.Leg(nextIndex = 5, price = 3_000, balanceBefore = 3_000 - sources.total.toLong(), balanceAfter = 3_000),
            streakDays = 8, streakExtended = false,
            levelBefore = Progress.levelOf(40 * MS_PER_HOUR, progress), levelAfter = Progress.levelOf(40 * MS_PER_HOUR + duration, progress),
        )
    }

    private companion object {
        const val BUTTON = 48
        const val NAME = "Аня"

        /** 32 letters: more than the 24 a name may have. */
        const val TOO_LONG = "Анна-Мария Владиславовна Иванова"
        const val PICKED = "content://picked"

        /**
         * The room of the recap in a low window: a phone of 640 × 360 on its side, under the status bar, the handle and the fields of
         * the sheet — 240 high, lower than what was earned; as wide as fits a screen in portrait, where the test runs.
         */
        const val LOW_ROOM_WIDTH = 400
        const val LOW_ROOM_HEIGHT = 240
        const val ROOM = "room"

        const val PICK_PHOTO = "pickPhoto"
        const val OTHER_PHOTO = "otherPhoto"
        const val REMOVE_PHOTO = "removePhoto"
        const val TRAVEL = "travel"
        const val DONE = "done"
    }
}
