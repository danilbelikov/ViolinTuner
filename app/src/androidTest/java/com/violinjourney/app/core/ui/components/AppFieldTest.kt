package com.violinjourney.app.core.ui.components

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.violinjourney.app.core.ui.theme.ViolinTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The one field of the app (spec 3.36.1, 5.29): its caption is a part of it for TalkBack; it types into the value its caller holds;
 * its error is a grey line under it, and the field itself is not marked as an error; its least height, by which the forms measure
 * the room over the keyboard, is that of a field of one line with its caption.
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

    /**
     * The forms of R4 (spec 3.36.4): a caption of parts — «Композитор · необязательно» — is said in its own words, «Композитор,
     * необязательно», still as a part of the field; the dot is read by nobody.
     */
    @Test
    fun aCaptionOfPartsIsSaidInItsOwnWords() {
        compose.setContent {
            ViolinTheme { AppField(value = value, onValueChange = { value = it }, label = SHOWN, labelDescription = SAID) }
        }
        compose.onNode(hasSetTextAction() and hasText(SAID)).assertExists()
        compose.onAllNodesWithText(SHOWN, useUnmergedTree = true).assertCountEquals(0)
    }

    @Test
    fun anErrorIsALineNotAnErrorOfTheField() {
        show(error = ERROR)
        compose.onNodeWithText(ERROR, substring = true, useUnmergedTree = true).assertExists()
        compose.onNode(hasSetTextAction()).assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Error))
    }

    /**
     * The forms lying over a keyboard (5.29 R4) send their zone under the fields where the room would not hold it and a field: the
     * least height of a field they measure by is the height of a field of one line with its caption and a placeholder as it is laid
     * out — 82 at the font of 1.0 — and it grows with the font as the field does (1.3).
     */
    @Test
    fun theLeastHeightIsThatOfAFieldOfOneLineWithItsCaption() {
        var fontScale by mutableFloatStateOf(1f)
        var least = Dp.Unspecified
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                ViolinTheme {
                    least = appFieldLeastHeight()
                    AppField(value = value, onValueChange = { value = it }, label = LABEL, placeholder = HINT)
                }
            }
        }
        compose.waitForIdle()
        compose.runOnIdle { assertEquals("82 at the font of 1.0: $least", 82f, least.value, 1f) }
        for (scale in listOf(1f, LARGE_FONT)) {
            compose.runOnIdle { fontScale = scale }
            compose.waitForIdle()
            val field = compose.onNode(hasSetTextAction()).getUnclippedBoundsInRoot()
            compose.runOnIdle { assertEquals("at the font of $scale: $least", (field.bottom - field.top).value, least.value, 1f) }
        }
    }

    private companion object {
        const val LARGE_FONT = 1.3f
        const val LABEL = "Название раздела"
        const val ERROR = "Нужно название"
        const val HINT = "Как вас зовут?"
        const val SHOWN = "Композитор · необязательно"
        const val SAID = "Композитор, необязательно"
    }
}
