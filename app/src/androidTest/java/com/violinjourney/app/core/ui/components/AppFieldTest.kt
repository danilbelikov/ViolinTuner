package com.violinjourney.app.core.ui.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.text.input.TextFieldValue
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.violinjourney.app.core.ui.theme.ViolinTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The one field of the app (spec 3.36.1, 5.29): its caption is a part of it for TalkBack; it types into the value its caller holds;
 * its error is a grey line under it, and the field itself is not marked as an error.
 */
@RunWith(AndroidJUnit4::class)
class AppFieldTest {
    @get:Rule
    val compose = createComposeRule()

    private var value by mutableStateOf(TextFieldValue(""))

    private fun show(error: String? = null) {
        compose.setContent {
            ViolinTheme { AppField(value = value, onValueChange = { value = it }, label = LABEL, error = error, counter = "${value.text.length} / 24") }
        }
    }

    @Test
    fun theCaptionIsReadWithTheField() {
        show()
        compose.onNode(hasSetTextAction() and hasText(LABEL)).assertExists()
    }

    @Test
    fun typingGoesToTheValueOfTheCaller() {
        show()
        compose.onNode(hasSetTextAction()).performTextInput("Этюды")
        compose.runOnIdle { assertEquals("Этюды", value.text) }
        compose.onNode(hasSetTextAction() and hasText("5 / 24")).assertExists()
    }

    /**
     * «Имя и фото» (spec 3.36.3): the caption is not drawn — the hint in the field takes its place — but the field is still named by
     * it for TalkBack, and nothing on screen says the caption.
     */
    @Test
    fun aFieldWithoutItsLabelIsStillNamedByIt() {
        compose.setContent {
            ViolinTheme {
                AppField(value = value, onValueChange = { value = it }, label = LABEL, placeholder = HINT, showLabel = false, inSheet = true)
            }
        }
        compose.onNode(hasSetTextAction() and hasContentDescription(LABEL)).assertExists()
        // the words of the caption are nowhere in the tree: not drawn, said only as the name of the field
        compose.onAllNodesWithText(LABEL, useUnmergedTree = true).assertCountEquals(0)
        compose.onNode(hasSetTextAction() and hasText(HINT)).assertExists()
    }

    @Test
    fun anErrorIsALineNotAnErrorOfTheField() {
        show(error = ERROR)
        compose.onNodeWithText(ERROR, substring = true, useUnmergedTree = true).assertExists()
        compose.onNode(hasSetTextAction()).assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Error))
    }

    private companion object {
        const val LABEL = "Название раздела"
        const val ERROR = "Нужно название"
        const val HINT = "Как вас зовут?"
    }
}
