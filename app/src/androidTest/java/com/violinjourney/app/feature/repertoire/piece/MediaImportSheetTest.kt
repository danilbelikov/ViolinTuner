package com.violinjourney.app.feature.repertoire.piece

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.violinjourney.app.core.recording.MediaImport
import com.violinjourney.app.core.recording.TakeOwner
import com.violinjourney.app.core.ui.theme.ViolinTheme
import com.violinjourney.app.shared.resources.Res
import com.violinjourney.app.shared.resources.file_copying_slow
import com.violinjourney.app.shared.resources.video_copying
import com.violinjourney.app.shared.resources.video_copying_slow
import org.jetbrains.compose.resources.stringResource
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The sheet of a file on its way in while it is added (spec 3.19, 0.94): «Добавляем видео…» that goes on for three seconds says why it may
 * take long and that the screen may be left; the analysis takes the line over with its seconds; a file says it in its own words. The
 * words are read in the composition, in the language the sheet speaks.
 */
@RunWith(AndroidJUnit4::class)
class MediaImportSheetTest {
    @get:Rule
    val compose = createComposeRule()

    private var import by mutableStateOf<MediaImport>(MediaImport.Idle)
    private val words = mutableMapOf<String, String>()

    @Composable
    private fun ReadWords() {
        words[ADDING] = stringResource(Res.string.video_copying)
        words[VIDEO_SLOW] = stringResource(Res.string.video_copying_slow)
        words[FILE_SLOW] = stringResource(Res.string.file_copying_slow)
    }

    private fun show(of: ImportWords) {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            ReadWords()
            ViolinTheme { MediaImportSheet(import, of) {} }
        }
        compose.mainClock.advanceTimeByFrame()
    }

    private fun adding(copying: Boolean = true) = MediaImport.Working(TakeOwner.Piece(1), shot = false, copying = copying, visible = true)

    @Test
    fun addingThatGoesOnForThreeSecondsSaysTheScreenMayBeLeft() {
        import = adding()
        show(ImportWords.VIDEO_TAKE)
        compose.mainClock.advanceTimeBy(2_500)
        compose.onNodeWithText(words.getValue(ADDING)).assertExists()
        compose.onAllNodesWithText(words.getValue(VIDEO_SLOW)).assertCountEquals(0)
        compose.mainClock.advanceTimeBy(700)
        compose.onNodeWithText(words.getValue(VIDEO_SLOW)).assertExists()

        // the analysis takes the line over: the seconds left, once they are known — not the hint
        import = adding(copying = false)
        compose.mainClock.advanceTimeBy(500)
        compose.onAllNodesWithText(words.getValue(VIDEO_SLOW)).assertCountEquals(0)
    }

    @Test
    fun aFileSaysItInItsOwnWords() {
        import = adding()
        show(ImportWords.SOUND_FILE)
        compose.mainClock.advanceTimeBy(3_200)
        compose.onNodeWithText(words.getValue(FILE_SLOW)).assertExists()
        compose.onAllNodesWithText(words.getValue(VIDEO_SLOW)).assertCountEquals(0)
    }

    private companion object {
        const val ADDING = "adding"
        const val VIDEO_SLOW = "video slow"
        const val FILE_SLOW = "file slow"
    }
}
