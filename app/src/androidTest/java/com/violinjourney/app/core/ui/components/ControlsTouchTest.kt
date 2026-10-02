package com.violinjourney.app.core.ui.components

import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.ViewConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.violinjourney.app.core.ui.icons.AppIcons
import com.violinjourney.app.core.ui.theme.ViolinTheme
import com.violinjourney.app.feature.home.TwoWay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The controls of the redesign (spec 3.36.1, 5.29): chips of 40 and 44 answer over 48 dp and read as radio buttons, an action chip
 * as a button; a whole row is one target; a dimmed button says why and is not pressed; every button of 48 is at least 48; the line of
 * a missing permission asks with a button of 48.
 *
 * The 48 of a chip is checked in what the chip lays out itself — the slot it takes in a column, and a touch in the strip between
 * its capsule and a neighbour right under it — not in the touch bounds of its node: Compose widens those to 48 by itself for any
 * target lower than that (the lesson of stage 101, SegmentedSwitchTest), so they read 48 whatever the chip lays out. Without its
 * own 48 the chip would lose that strip to the neighbour: a direct hit beats a widened one.
 */
@RunWith(AndroidJUnit4::class)
class ControlsTouchTest {
    @get:Rule
    val compose = createComposeRule()

    private var presses = 0
    private var neighbour = 0
    private var chosen by mutableStateOf(false)
    private var on by mutableStateOf(false)

    /**
     * [chip] over a neighbour of 48 that is pressed as well: the chip takes a slot of 48 in the column, and a touch [beyond] its
     * visible capsule — in the strip its own 48 leaves around it — goes to the chip, not to the neighbour.
     */
    private fun assertTheChipTakesFortyEight(beyond: Dp, chip: @Composable () -> Unit) {
        compose.setContent {
            ViolinTheme {
                Column(Modifier.fillMaxWidth()) {
                    Box(Modifier.testTag(SLOT)) { chip() }
                    Box(Modifier.fillMaxWidth().height(48.dp).testTag(NEIGHBOUR).clickable { neighbour++ })
                }
            }
        }
        compose.onNodeWithTag(SLOT).assertHeightIsEqualTo(48.dp)
        val capsule = compose.onNodeWithTag(TAG).getUnclippedBoundsInRoot()
        val slot = compose.onNodeWithTag(SLOT).getUnclippedBoundsInRoot()
        assertTrue("the capsule is lower than its slot: ${capsule.height}", capsule.height < slot.height)
        compose.onRoot().performTouchInput {
            click(Offset(((capsule.left + capsule.right) / 2).toPx(), (capsule.bottom + beyond).toPx()))
        }
        compose.runOnIdle { assertEquals("the strip under the capsule is the chip's", 0, neighbour) }
    }

    @Test
    fun aFilterChipAnswersOverFortyEightAndReadsAsARadioButton() {
        // the capsule of 40 in a slot of 48: 4 dp of it under the capsule
        assertTheChipTakesFortyEight(beyond = 2.dp) {
            AppChip("Учу", selected = chosen, onClick = { chosen = true }, modifier = Modifier.testTag(TAG), count = 2)
        }
        compose.onNodeWithTag(TAG)
            .assertIsSelected()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
    }

    /**
     * The chip of a place of the shop (spec 3.36.7, 5.29 R7): a chosen filter chip with a cross of 18 inside it — pressed over 48 as any
     * chip; a press anywhere on it takes the place off; TalkBack hears that it is chosen and what the press does («Снять фильтр»).
     *
     * What Android hands TalkBack is read from the node the Compose view gives it, not from the semantics of Compose: a radio button (or
     * a tab) that is chosen already loses its press there — «cannot be chosen again» — and its label with it, so the chip of R7 read as
     * a radio button was told to be neither pressable nor what it does (the review of stage 119). As a button that says it is chosen it
     * keeps both.
     */
    @Test
    fun aPlaceChipClearsItsFilterAndSaysSo() {
        var view: View? = null
        // the capsule of 40 in a slot of 48: 4 dp of it under the capsule
        assertTheChipTakesFortyEight(beyond = 2.dp) {
            view = LocalView.current
            AppChip(PLACE, selected = true, onClick = { presses++ }, modifier = Modifier.testTag(TAG), trailing = AppIcons.Close, onClickLabel = CLEAR)
        }
        compose.runOnIdle { assertEquals("the touch in the strip", 1, presses) }
        val chip = compose.onNodeWithTag(TAG)
            .assertIsSelected()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .assert(SemanticsMatcher("its press says what it does") { it.config.getOrNull(SemanticsActions.OnClick)?.label == CLEAR })
        val id = chip.fetchSemanticsNode().id
        val info = checkNotNull(compose.runOnUiThread { view!!.accessibilityNodeProvider?.createAccessibilityNodeInfo(id) }) { "no node for TalkBack" }
        // the class of this node stays android.view.View: Compose hands the role of a node with children (the word, the cross) to a
        // child node of its own, as for any button with words in it — the role is the Button of the semantics asserted above
        assertTrue("TalkBack, Switch Access and Voice Access may press it", info.isClickable)
        assertEquals(
            "TalkBack says what the press does",
            CLEAR,
            info.actionList.firstOrNull { it.id == AccessibilityNodeInfo.ACTION_CLICK }?.label?.toString(),
        )
        assertTrue("and that it is chosen: «${info.stateDescription}»", info.isCheckable && info.stateDescription != null)
        chip.performClick()
        compose.runOnIdle { assertEquals("a press on the word, not only on the cross", 2, presses) }
    }

    @Test
    fun anActionChipIsAButtonWithoutAChoice() {
        // the capsule of 44 in a slot of 48: 2 dp of it under the capsule
        assertTheChipTakesFortyEight(beyond = 1.dp) {
            AppChip.Choice("+10 мин", selected = null, onClick = { presses++ }, modifier = Modifier.testTag(TAG), inSheet = true)
        }
        compose.runOnIdle { assertEquals("the touch in the strip", 1, presses) }
        compose.onNodeWithTag(TAG)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Selected))
            .performClick()
        compose.runOnIdle { assertEquals(2, presses) }
    }

    /**
     * A preset of the user's own on «Звук» (spec 3.36.5): a chip of choice that a long press offers to remove — the press chooses, the
     * long press is heard as such and not as a choice, TalkBack is told what it does, and the chip still reads as a radio button.
     */
    @Test
    fun aChoiceWithALongPressAnswersItAndStaysARadioButton() {
        var longPresses = 0
        compose.setContent {
            ViolinTheme {
                AppChip.Choice(
                    "Мой зал", selected = false, onClick = { presses++ }, modifier = Modifier.testTag(TAG),
                    onLongClick = { longPresses++ }, onLongClickLabel = REMOVE,
                )
            }
        }
        compose.onNodeWithTag(TAG)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
            .assertIsNotSelected()
            .performTouchInput { longClick() }
        compose.runOnIdle {
            assertEquals(1, longPresses)
            assertEquals("a long press is no choice", 0, presses)
        }
        compose.onNodeWithTag(TAG).performClick()
        compose.runOnIdle { assertEquals(1, presses) }
        assertEquals(REMOVE, compose.onNodeWithTag(TAG).fetchSemanticsNode().config[SemanticsActions.OnLongClick].label)
    }

    @Test
    fun aDimmedChoiceIsNotPressed() {
        compose.setContent {
            ViolinTheme { AppChip.Choice("Гаммы", selected = false, onClick = { presses++ }, modifier = Modifier.testTag(TAG), enabled = false) }
        }
        compose.onNodeWithTag(TAG).assertIsNotEnabled().performClick()
        compose.runOnIdle { assertEquals(0, presses) }
    }

    @Test
    fun aRowWithASwitchTogglesFromItsWords() {
        compose.setContent {
            ViolinTheme {
                ListRow("Помогать улучшать приложение", onClick = { on = !on }, modifier = Modifier.testTag(TAG), end = ListRowEnd.Toggle(on))
            }
        }
        compose.onNodeWithTag(TAG).assertIsOff().assertHeightIsAtLeast(56.dp)
        compose.onNodeWithText("Помогать улучшать приложение").performClick()
        compose.onNodeWithTag(TAG).assertIsOn()
    }

    @Test
    fun aRowWithAChevronOpens() {
        compose.setContent {
            ViolinTheme { ListRow("Копия данных", onClick = { presses++ }, modifier = Modifier.testTag(TAG)) }
        }
        compose.onNodeWithTag(TAG)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .assertHeightIsAtLeast(56.dp)
            .performClick()
        compose.runOnIdle { assertEquals(1, presses) }
    }

    /**
     * «Комната | Снаружи» on a picture (spec 3.36.7, 5.29 R7): the capsule seen is 40, but the switch lays itself out 48 high and its two
     * tabs are pressed over the whole of it — the strip under the capsule is the switch's, not the neighbour's under it — with the
     * widening of touch targets off, so only its own 48 can catch it. Each half is a tab of one group, the chosen one selected.
     */
    @Test
    fun aTwoWayOnAPictureAnswersOverFortyEight() {
        val chosen = mutableListOf<Boolean>()
        compose.setContent {
            val base = LocalViewConfiguration.current
            val noWidening = remember(base) { object : ViewConfiguration by base { override val minimumTouchTargetSize = DpSize.Zero } }
            CompositionLocalProvider(LocalViewConfiguration provides noWidening) {
                ViolinTheme {
                    Column(Modifier.fillMaxWidth()) {
                        Box(Modifier.testTag(SLOT)) { TwoWay(ROOM, OUTSIDE, secondChosen = false, onChoose = { chosen += it }) }
                        Box(Modifier.fillMaxWidth().height(48.dp).testTag(NEIGHBOUR).clickable { neighbour++ })
                    }
                }
            }
        }
        compose.onNodeWithTag(SLOT).assertHeightIsEqualTo(48.dp)
        val room = compose.onNode(hasText(ROOM) and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab))
        val outside = compose.onNode(hasText(OUTSIDE) and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab))
        room.assertIsSelected().assertHeightIsAtLeast(48.dp)
        outside.assertIsNotSelected()
        // 2 dp over the bottom of the 48: under the capsule of 40, which ends 4 over it
        val slot = compose.onNodeWithTag(SLOT).getUnclippedBoundsInRoot()
        val bounds = outside.getUnclippedBoundsInRoot()
        compose.onRoot().performTouchInput { click(Offset(((bounds.left + bounds.right) / 2).toPx(), (slot.bottom - 2.dp).toPx())) }
        compose.runOnIdle {
            assertEquals("the strip under the capsule is the switch's", listOf(true), chosen)
            assertEquals("not the neighbour's", 0, neighbour)
        }
    }

    @Test
    fun aDimmedButtonSaysWhyAndIsNotPressed() {
        compose.setContent {
            ViolinTheme { AppButton("Сохранить", onClick = { presses++ }, enabled = false, reason = REASON) }
        }
        compose.onNodeWithText(REASON).assertIsDisplayed()
        compose.onNodeWithText("Сохранить").assertIsNotEnabled().performClick()
        compose.runOnIdle { assertEquals(0, presses) }
    }

    @Test
    fun theButtonsOfFortyEightAreAtLeastFortyEight() {
        compose.setContent {
            ViolinTheme {
                Column {
                    AppButton("Все", onClick = {}, Modifier.testTag("text"), style = AppButtonStyle.Text)
                    AppButton("Изменить", onClick = {}, Modifier.testTag("soft"), style = AppButtonStyle.Soft)
                    AppButton("Не сохранять", onClick = {}, Modifier.testTag("quiet"), style = AppButtonStyle.Quiet)
                    AppButton("Удалить", onClick = {}, Modifier.testTag("danger"), style = AppButtonStyle.Danger)
                }
            }
        }
        listOf("text", "soft", "quiet", "danger").forEach { compose.onNodeWithTag(it).assertHeightIsAtLeast(48.dp) }
    }

    @Test
    fun theLineOfAMissingPermissionAsksWithAButtonOfFortyEight() {
        compose.setContent {
            ViolinTheme { PermissionLine(reason = REASON, onGrant = { presses++ }, grant = GRANT) }
        }
        compose.onNodeWithText(REASON).assertIsDisplayed()
        compose.onNodeWithText(GRANT).assertHeightIsAtLeast(48.dp).performClick()
        compose.runOnIdle { assertEquals(1, presses) }
    }

    /**
     * A word at the end of a row (spec 3.36.9: «Записать звук» without the microphone): only the word is pressed — a text button of 48
     * — and the row itself is not; also an outlined coral answer of a deletion (5.29 R9) is at least 48.
     */
    @Test
    fun aWordAtTheEndOfARowIsTheOnlyTargetAndIsFortyEight() {
        var rows = 0
        compose.setContent {
            ViolinTheme {
                Column {
                    ListRow(
                        "Записать звук", onClick = { rows++ }, modifier = Modifier.testTag(TAG), caption = MIC_REASON,
                        end = ListRowEnd.TextAction(GRANT) { presses++ },
                    )
                    AppButton("Только этот урок", onClick = {}, Modifier.testTag("outlineDanger"), style = AppButtonStyle.OutlineDanger, caption = "остальные — по понедельникам")
                }
            }
        }
        compose.onNodeWithText(GRANT).assertHeightIsAtLeast(48.dp).performClick()
        compose.onNodeWithText("Записать звук").performClick()
        compose.onNodeWithTag("outlineDanger").assertHeightIsAtLeast(48.dp)
        compose.runOnIdle {
            assertEquals("the word asks", 1, presses)
            assertEquals("the row itself is not pressed", 0, rows)
        }
    }

    private companion object {
        const val TAG = "control"
        const val SLOT = "slot"
        const val NEIGHBOUR = "neighbour"
        const val REASON = "Чтобы записать дубль, нужен доступ к микрофону."
        const val GRANT = "Разрешить доступ"
        const val MIC_REASON = "Чтобы записать звук, нужен доступ к микрофону."
        const val ROOM = "Комната"
        const val OUTSIDE = "Снаружи"
        /** What a long press does, as TalkBack puts it in «дважды нажмите и удерживайте, чтобы …»: a verb, not a sentence of its own. */
        const val REMOVE = "Удалить"
        const val PLACE = "На столе, справа"
        const val CLEAR = "Снять фильтр"
    }
}
