package com.violinjourney.app.feature.repertoire.form

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.violinjourney.app.core.domain.repertoire.PieceDraft
import com.violinjourney.app.core.domain.repertoire.PieceSection
import com.violinjourney.app.core.domain.repertoire.SectionRef
import com.violinjourney.app.core.ui.theme.ViolinTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The author field of the form follows the draft where the view model rewrites it (spec 3.22): a move into «Штрихи» clears
 * the author, so a move there and back shows the empty field that will be saved. Typing is the field's own otherwise.
 */
@RunWith(AndroidJUnit4::class)
class PieceFormScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private fun stateIn(section: PieceSection, composer: String) = PieceFormState(
        loading = false, isNew = false, draft = PieceDraft(title = "Étude No. 2", composer = composer, section = section),
        titleError = false, canSave = true, dialog = null, maxTitleLength = 80, maxComposerLength = 60, maxNotesLength = 2000,
        focusNotes = false, savedTitle = "Étude No. 2", section = SectionRef.BuiltIn(section),
    )

    @Test
    fun aMoveIntoStrokesAndBackShowsTheAuthorThatWillBeSaved() {
        var state by mutableStateOf(stateIn(PieceSection.ETUDES, composer = "Kreutzer"))
        compose.setContent { ViolinTheme { PieceFormScreen(state = state, onIntent = {}) } }
        compose.onNodeWithText("Kreutzer").assertExists()

        // what the view model does on a move into «Штрихи» and back to «Этюды»
        compose.runOnIdle { state = stateIn(PieceSection.STROKES, composer = "") }
        compose.waitForIdle()
        compose.runOnIdle { state = stateIn(PieceSection.ETUDES, composer = "") }
        compose.waitForIdle()
        compose.onNodeWithText("Kreutzer").assertDoesNotExist()
    }

    @Test
    fun typedAuthorStaysWhenTheSectionChangesOutsideStrokes() {
        var state by mutableStateOf(stateIn(PieceSection.ETUDES, composer = "Kreutzer"))
        compose.setContent {
            ViolinTheme {
                PieceFormScreen(
                    state = state,
                    onIntent = { intent -> if (intent is PieceFormIntent.ComposerChanged) state = state.copy(draft = state.draft.copy(composer = intent.text)) },
                )
            }
        }
        compose.onNodeWithText("Kreutzer").performTextReplacement("Kayser")
        compose.runOnIdle { state = stateIn(PieceSection.PIECES, composer = state.draft.composer) }
        compose.waitForIdle()
        compose.onNodeWithText("Kayser").assertExists()
    }
}
