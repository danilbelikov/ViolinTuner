package com.violinjourney.app.feature.camera

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.violinjourney.app.core.ui.format.Formats
import com.violinjourney.app.core.ui.theme.ViolinTheme
import com.violinjourney.app.shared.resources.Res
import com.violinjourney.app.shared.resources.backing_headphones_wired
import com.violinjourney.app.shared.resources.backing_needs_headphones
import com.violinjourney.app.shared.resources.capture_backing_length
import com.violinjourney.app.shared.resources.capture_grant
import com.violinjourney.app.shared.resources.capture_no_permission
import com.violinjourney.app.shared.resources.capture_record
import com.violinjourney.app.shared.resources.live_mic_unavailable
import org.jetbrains.compose.resources.stringResource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The own camera of R4 (spec 3.36.4) by its phrases and answers: without the permissions the line «нет разрешения» stands over the
 * sleeping shutter and «Разрешить доступ» asks — there is no «Открыть настройки» of its own any more; under the backing the line
 * over the shutter names the headphones and the backing for TalkBack as one phrase; without headphones a plate stands instead of it
 * and the shutter sleeps; after a shot the lost microphone cut short the plate says so and the shutter answers. The viewfinder is an
 * empty box; the words are read in the composition, in the language of the screen.
 */
@RunWith(AndroidJUnit4::class)
class CaptureScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val intents = mutableListOf<CaptureIntent>()
    private val words = mutableMapOf<String, String>()

    private fun word(key: String): String = words.getValue(key)

    private fun show(state: CaptureState, width: Dp = 412.dp, height: Dp = 800.dp) {
        compose.setContent {
            words[NO_PERMISSION] = stringResource(Res.string.capture_no_permission)
            words[GRANT] = stringResource(Res.string.capture_grant)
            words[SHUTTER] = stringResource(Res.string.capture_record)
            words[NEEDS] = stringResource(Res.string.backing_needs_headphones)
            words[MIC_GONE] = stringResource(Res.string.live_mic_unavailable)
            words[LINE] = stringResource(Res.string.backing_headphones_wired, BUDS) + ", " +
                stringResource(Res.string.capture_backing_length, Formats.duration(READY.backingDurationMs))
            ViolinTheme {
                Box(Modifier.requiredSize(width, height)) {
                    CaptureScreen(state = state, viewfinder = { Box(it) }, onIntent = { intents += it })
                }
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun withoutThePermissionsTheLineAsksAndTheShutterSleeps() {
        show(READY.copy(micPermission = false))
        compose.onNodeWithText(word(NO_PERMISSION)).assertExists()
        compose.onNodeWithContentDescription(word(SHUTTER)).assertIsNotEnabled()
        compose.onAllNodes(hasContentDescription(word(LINE))).assertCountEquals(0)
        compose.onNodeWithText(word(GRANT)).performClick()
        assertEquals(listOf<CaptureIntent>(CaptureIntent.GrantClicked), intents)
    }

    @Test
    fun underTheBackingTheLineOverTheShutterNamesTheHeadphonesAndTheBacking() {
        show(READY)
        compose.onNodeWithContentDescription(word(LINE)).assertExists()
        compose.onNodeWithContentDescription(word(SHUTTER)).assertIsEnabled().performClick()
        assertEquals(listOf<CaptureIntent>(CaptureIntent.RecordClicked), intents)
    }

    @Test
    fun withoutHeadphonesAPlateStandsInsteadOfTheLineAndTheShutterSleeps() {
        show(READY.copy(noHeadphones = true, headphonesName = null))
        compose.onNodeWithText(word(NEEDS)).assertExists()
        compose.onAllNodes(hasContentDescription(word(LINE))).assertCountEquals(0)
        compose.onNodeWithContentDescription(word(SHUTTER)).assertIsNotEnabled()
    }

    @Test
    fun afterAShotTheLostMicrophoneCutShortThePlateSaysSoAndTheShutterAnswers() {
        show(READY.copy(micUnavailable = true))
        compose.onNodeWithText(word(MIC_GONE)).assertExists()
        compose.onAllNodesWithText(word(NO_PERMISSION)).assertCountEquals(0)
        compose.onNodeWithContentDescription(word(SHUTTER)).assertIsEnabled().performClick()
        assertEquals(listOf<CaptureIntent>(CaptureIntent.RecordClicked), intents)
    }

    // the review of stage 109: lying, the line stands in the side column of 210 — its reason takes three or four lines there, and two
    // of them cut it with an ellipsis; the whole reason is the main phrase of the screen
    @Test
    fun lyingTheLineWithoutThePermissionsSaysItsWholeReason() {
        show(READY.copy(cameraPermission = false), width = 892.dp, height = 412.dp)
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(word(NO_PERMISSION), useUnmergedTree = true).fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
        val layout = layouts.single()
        assertFalse("the reason is cut on line ${layout.lineCount}", layout.isLineEllipsized(layout.lineCount - 1))
        compose.onNodeWithText(word(GRANT)).assertExists()
    }

    private companion object {
        const val BUDS = "Pixel Buds"
        const val NO_PERMISSION = "noPermission"
        const val GRANT = "grant"
        const val SHUTTER = "shutter"
        const val NEEDS = "needs"
        const val MIC_GONE = "micGone"
        const val LINE = "line"

        val READY = CaptureState(
            title = "Концерт ля минор, 1 ч.", cameraPermission = true, micPermission = true,
            backingTitle = "Вивальди — фортепиано", backingDurationMs = 220_000, underBacking = true, headphonesName = BUDS,
        )
    }
}
