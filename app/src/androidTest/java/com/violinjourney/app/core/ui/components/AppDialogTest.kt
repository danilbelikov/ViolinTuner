package com.violinjourney.app.core.ui.components

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.violinjourney.app.core.ui.theme.ViolinTheme
import com.violinjourney.app.shared.resources.Res
import com.violinjourney.app.shared.resources.dialog_name_needed
import org.jetbrains.compose.resources.stringResource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The dialogs of the redesign (spec 3.36.1): a field that cannot be confirmed empty dims its button and says why; the field is
 * named by its caption; renaming a recording confirms an empty field — it gives back the name by date; «Удалить» of the delete
 * dialog answers.
 */
@RunWith(AndroidJUnit4::class)
class AppDialogTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun anEmptyNameDimsTheButtonAndSaysWhy() {
        var reason = ""
        compose.setContent {
            reason = stringResource(Res.string.dialog_name_needed)
            ViolinTheme {
                FieldDialog(
                    title = TITLE, label = LABEL, initial = "", confirm = CREATE, onConfirm = {}, onDismiss = {},
                    maxLength = 24, confirmEnabled = false,
                )
            }
        }
        compose.onNodeWithText(CREATE).assertIsNotEnabled()
        compose.onNodeWithText(reason).assertExists()
        compose.onNodeWithText("0 / 24").assertExists()
    }

    /**
     * The field that takes the focus is named by its caption, with the reason and the counter: the caption above the frame is a
     * part of the field, not a text beside it, so TalkBack says what goes into it (spec 3.36.1).
     */
    @Test
    fun theFieldIsNamedByItsCaption() {
        var reason = ""
        compose.setContent {
            reason = stringResource(Res.string.dialog_name_needed)
            ViolinTheme {
                FieldDialog(
                    title = TITLE, label = LABEL, initial = "", confirm = CREATE, onConfirm = {}, onDismiss = {},
                    maxLength = 24, confirmEnabled = false,
                )
            }
        }
        compose.onNode(hasSetTextAction() and hasText(LABEL)).assertExists()
        compose.onNode(hasSetTextAction() and hasText(reason) and hasText("0 / 24")).assertExists()
    }

    @Test
    fun renamingConfirmsAnEmptyField() {
        val confirmed = mutableListOf<String>()
        compose.setContent {
            ViolinTheme {
                FieldDialog(
                    title = TITLE, label = LABEL, initial = "Менуэт — чистый прогон", confirm = SAVE,
                    onConfirm = { confirmed += it }, onDismiss = {},
                )
            }
        }
        compose.onNodeWithText("Менуэт — чистый прогон").performTextReplacement("")
        compose.onNodeWithText(SAVE).assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(listOf(""), confirmed) }
    }

    @Test
    fun theDeleteDialogCallsItsConfirm() {
        var deleted = false
        compose.setContent {
            ViolinTheme { DeleteDialog(title = "Удалить запись?", text = null, onConfirm = { deleted = true }, onDismiss = {}, confirm = DELETE) }
        }
        compose.onNodeWithText(DELETE).performClick()
        compose.runOnIdle { assertTrue(deleted) }
    }

    private companion object {
        const val TITLE = "Новый раздел"
        const val LABEL = "Название раздела"
        const val CREATE = "Создать"
        const val SAVE = "Сохранить"
        const val DELETE = "Удалить"
    }
}
