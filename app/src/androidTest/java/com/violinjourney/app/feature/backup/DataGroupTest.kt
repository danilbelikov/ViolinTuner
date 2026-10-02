package com.violinjourney.app.feature.backup

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.violinjourney.app.core.backup.BackupPart
import com.violinjourney.app.core.backup.RestorePhase
import com.violinjourney.app.core.ui.format.Formats
import com.violinjourney.app.core.ui.theme.ViolinTheme
import com.violinjourney.app.shared.resources.Res
import com.violinjourney.app.shared.resources.analytics_row
import com.violinjourney.app.shared.resources.analytics_row_caption
import com.violinjourney.app.shared.resources.analytics_row_off
import com.violinjourney.app.shared.resources.backup_part_data_short
import com.violinjourney.app.shared.resources.backup_part_sheets
import com.violinjourney.app.shared.resources.backup_percent
import com.violinjourney.app.shared.resources.backup_phase_check
import com.violinjourney.app.shared.resources.backup_phase_part
import com.violinjourney.app.shared.resources.backup_row_last
import com.violinjourney.app.shared.resources.backup_row_never
import com.violinjourney.app.shared.resources.backup_row_restore
import com.violinjourney.app.shared.resources.backup_row_save
import com.violinjourney.app.shared.resources.backup_row_saving
import com.violinjourney.app.shared.resources.backup_row_wait_restore
import com.violinjourney.app.shared.resources.dot_separator
import com.violinjourney.app.shared.resources.restore_row_caption
import com.violinjourney.app.shared.resources.restore_row_running
import com.violinjourney.app.shared.resources.restore_step_extracting
import com.violinjourney.app.shared.resources.restore_step_finishing
import com.violinjourney.app.shared.resources.restore_step_verifying
import com.violinjourney.app.testing.TestWindow
import com.violinjourney.app.testing.assertWordsWhole
import java.util.Locale
import kotlinx.datetime.TimeZone
import org.jetbrains.compose.resources.stringResource
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The group «Данные» of «Настройки» (spec 3.36.8, 5.29 R8) by what an eye, a finger and TalkBack meet: until the date of the last
 * copy is read its caption holds its line and says nothing — not «ещё не сохраняли»; until the settings are read the statistics hold
 * the place of their switch and their caption and toggle nothing; one job at a time is seen before a touch both ways, and the row of a
 * restore on its way leads back to its screen, not to the system's «Открыть»; every phase of a copy and of a restore says its words; a
 * caption whose word does not stand at 13 sp steps down rather than break. Laid out in a window of its own ([TestWindow]); the words read
 * in the composition, in the language of the app or the one the test sets.
 */
@RunWith(AndroidJUnit4::class)
class DataGroupTest {
    @get:Rule
    val compose = createComposeRule()

    private var data by mutableStateOf(DataBlockState(dateRead = true))
    private var window by mutableStateOf(DpSize(412.dp, 868.dp))
    private var analytics by mutableStateOf<Boolean?>(true)
    private var fontScale by mutableFloatStateOf(1f)
    private var openedBackup = 0
    private var openedRunningRestore = 0
    private var pickedCopy = 0
    private val toggled = mutableListOf<Boolean>()

    /** The words of the group in the language of the test, read where it is laid out. */
    private val words = mutableMapOf<String, String>()

    private fun show() {
        compose.setContent {
            val dot = stringResource(Res.string.dot_separator)
            words["save"] = stringResource(Res.string.backup_row_save)
            words["saving"] = stringResource(Res.string.backup_row_saving)
            words["restore"] = stringResource(Res.string.backup_row_restore)
            words["restoreRunning"] = stringResource(Res.string.restore_row_running)
            words["restoreCaption"] = stringResource(Res.string.restore_row_caption)
            words["waitRestore"] = stringResource(Res.string.backup_row_wait_restore)
            words["never"] = stringResource(Res.string.backup_row_never)
            words["last"] = stringResource(Res.string.backup_row_last, Formats.dayAndMonth(SEPTEMBER_12_MS, TimeZone.currentSystemDefault()))
            words["analytics"] = stringResource(Res.string.analytics_row)
            words["analyticsOn"] = stringResource(Res.string.analytics_row_caption)
            words["analyticsOff"] = stringResource(Res.string.analytics_row_off)
            words["data"] = stringResource(Res.string.backup_percent, 12) + dot + stringResource(Res.string.backup_part_data_short)
            words["sheets"] = stringResource(Res.string.backup_percent, 34) + dot +
                stringResource(Res.string.backup_phase_part, stringResource(Res.string.backup_part_sheets), 3, 48)
            words["check"] = stringResource(Res.string.backup_percent, 99) + dot + stringResource(Res.string.backup_phase_check)
            words["verifying"] = stringResource(Res.string.backup_percent, 5) + dot + stringResource(Res.string.restore_step_verifying)
            words["extracting"] = stringResource(Res.string.backup_percent, 38) + dot + stringResource(Res.string.restore_step_extracting)
            words["finishing"] = stringResource(Res.string.backup_percent, 100) + dot + stringResource(Res.string.restore_step_finishing)
            ViolinTheme {
                TestWindow(window, fontScale = fontScale) {
                    Column(Modifier.padding(horizontal = 16.dp)) {
                        DataGroup(
                            state = data,
                            analyticsEnabled = analytics,
                            onAnalyticsChange = { toggled += it },
                            onOpenBackup = { openedBackup++ },
                            onOpenRunningRestore = { openedRunningRestore++ },
                            onPickCopy = { pickedCopy++ },
                            onOpenPrivacy = {},
                        )
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    /** The language of the device, given back after every test. */
    private val deviceLanguage: Locale = Locale.getDefault()

    @After
    fun backToTheLanguageOfTheDevice() = Locale.setDefault(deviceLanguage)

    /** The row whose words are [title]: one node for TalkBack and the finger, its title and its caption in it. */
    private fun row(title: String): SemanticsNodeInteraction = compose.onNode(hasText(title) and hasClickAction())

    /** A row whose words for TalkBack are [texts] and nothing else — its caption cleared, not a no-break space, not a word too many. */
    private fun says(vararg texts: String) = SemanticsMatcher("says only ${texts.toList()}") { node ->
        node.config.getOrNull(SemanticsProperties.Text)?.map { it.text } == texts.toList()
    }

    /**
     * Until the date of the last copy is read the caption of «Сохранить копию» is held empty: not «ещё не сохраняли» (it would blink to a
     * date), silent for TalkBack, and of the height of the line that comes — at the font 1.3, where a row of two lines is taller than 56.
     */
    @Test
    fun theCaptionOfTheCopyHoldsItsLineAndSaysNothingUntilTheDateIsRead() {
        fontScale = LARGE_FONT
        data = DataBlockState()
        show()
        val save = words.getValue("save")
        val held = row(save).assert(says(save)).getUnclippedBoundsInRoot()
        compose.onAllNodesWithText(HELD_LINE, useUnmergedTree = true).assertCountEquals(0)
        compose.onAllNodesWithText(words.getValue("never"), useUnmergedTree = true).assertCountEquals(0)
        // read: «последняя — 12 сентября», one line of 412 at 1.3
        data = DataBlockState(dateRead = true, lastBackupAtEpochMs = SEPTEMBER_12_MS)
        compose.waitForIdle()
        val read = row(save).assert(says(save, words.getValue("last"))).getUnclippedBoundsInRoot()
        assertEquals("the row keeps its height when the date comes", held.height.value, read.height.value, HEIGHT_SLACK_DP)
    }

    /**
     * Until the settings are read the statistics hold the place of their caption and their switch and say neither «on» nor «off»: the
     * one who turned them off is not told they are on; a tap does nothing. Read, the row is the switch it always was.
     */
    @Test
    fun untilTheSettingsAreReadTheStatisticsSayNothingAndToggleNothing() {
        analytics = null
        show()
        val title = words.getValue("analytics")
        compose.onAllNodesWithText(words.getValue("analyticsOn"), useUnmergedTree = true).assertCountEquals(0)
        compose.onAllNodesWithText(words.getValue("analyticsOff"), useUnmergedTree = true).assertCountEquals(0)
        compose.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch)).assertCountEquals(0)
        compose.onNode(hasText(title)).performClick()
        compose.runOnIdle { assertEquals(emptyList<Boolean>(), toggled) }
        analytics = false
        compose.waitForIdle()
        compose.onNode(hasText(title) and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch)).performClick()
        compose.runOnIdle { assertEquals(listOf(true), toggled) }
    }

    /**
     * A restore on its way (spec 3.36.8): «Сохранить копию» sleeps and says why; «Восстановление идёт» with its percent and step leads
     * back to the screen of the restore — not to the system's «Открыть».
     */
    @Test
    fun aRestoreOnItsWayPutsTheCopyToSleepAndItsRowLeadsBackToItsScreen() {
        data = DataBlockState(dateRead = true, running = DataRunning(restore = true, percent = 38, phase = JobPhase.Restore(RestorePhase.EXTRACTING)))
        show()
        compose.onNode(hasText(words.getValue("save")) and hasText(words.getValue("waitRestore"))).assertIsNotEnabled().performClick()
        compose.runOnIdle { assertEquals("the copy waits: no screen of the copy", 0, openedBackup) }
        compose.onNode(hasText(words.getValue("restoreRunning")) and hasText(words.getValue("extracting")) and hasClickAction())
            .assertIsEnabled()
            .performClick()
        compose.runOnIdle {
            assertEquals("back to the screen of the restore", 1, openedRunningRestore)
            assertEquals("not the system's «Открыть»", 0, pickedCopy)
        }
    }

    /** With no job on its way «Восстановить из копии» opens the system's «Открыть», and «Сохранить копию» the screen of the copy. */
    @Test
    fun withNoJobTheRowsOpenTheSystemsPickerAndTheScreenOfTheCopy() {
        show()
        row(words.getValue("restore")).assert(says(words.getValue("restore"), words.getValue("restoreCaption"))).performClick()
        row(words.getValue("save")).performClick()
        compose.runOnIdle {
            assertEquals(1, pickedCopy)
            assertEquals(0, openedRunningRestore)
            assertEquals(1, openedBackup)
        }
    }

    /** A copy on its way: its row goes on leading to its screen; the restore sleeps (the other half: AccessibilitySemanticsTest). */
    @Test
    fun aCopyOnItsWayLeadsBackToItsScreen() {
        data = DataBlockState(dateRead = true, running = DataRunning(restore = false, percent = 12, phase = JobPhase.Data))
        show()
        row(words.getValue("saving")).assert(says(words.getValue("saving"), words.getValue("data"))).performClick()
        compose.runOnIdle { assertEquals(1, openedBackup) }
    }

    /**
     * Every phase in the words of its row: «12 % · Разбор, занятия, прогресс», «34 % · Фото нот 3 из 48», «99 % · Проверка» of a copy;
     * «5 % · Проверяем», «38 % · Восстанавливаем» and «100 % · Почти готово» — unpacked, waiting for its restart — of a restore.
     */
    @Test
    fun eachPhaseOfACopyAndOfARestoreIsSaidInItsRow() {
        show()
        val copies = listOf(
            DataRunning(restore = false, percent = 12, phase = JobPhase.Data) to "data",
            DataRunning(restore = false, percent = 34, phase = JobPhase.Files(BackupPart.SHEETS, 3, 48)) to "sheets",
            DataRunning(restore = false, percent = 99, phase = JobPhase.Check) to "check",
        )
        copies.forEach { (running, key) ->
            data = DataBlockState(dateRead = true, running = running)
            compose.waitForIdle()
            row(words.getValue("saving")).assert(says(words.getValue("saving"), words.getValue(key)))
        }
        val restores = listOf(
            DataRunning(restore = true, percent = 5, phase = JobPhase.Restore(RestorePhase.VERIFYING)) to "verifying",
            DataRunning(restore = true, percent = 38, phase = JobPhase.Restore(RestorePhase.EXTRACTING)) to "extracting",
            DataRunning(restore = true, percent = 100, phase = JobPhase.Restore(RestorePhase.FINISHING)) to "finishing",
        )
        restores.forEach { (running, key) ->
            data = DataBlockState(dateRead = true, running = running)
            compose.waitForIdle()
            row(words.getValue("restoreRunning")).assert(says(words.getValue("restoreRunning"), words.getValue(key)))
        }
    }

    /**
     * A caption whose word does not stand whole at 13 sp steps down rather than break (5.29 R8, stage 121): de «warten Sie zuerst, bis die
     * Wiederherstellung fertig ist» under «Kopie speichern» while a restore runs — «Wiederherstellung» is some 181 dp at 13 sp at the font
     * 1.5 and 164 at 12 (CoreText); in a window of 312 its line has 172. (On 320 it is 180 — a hair too few: the case of the phone.)
     */
    @Test
    fun aCaptionWhoseWordDoesNotStandStepsDownRatherThanBreak() {
        Locale.setDefault(Locale.forLanguageTag("de"))
        window = DpSize(312.dp, 640.dp)
        fontScale = LARGER_FONT
        data = DataBlockState(dateRead = true, running = DataRunning(restore = true, percent = 38, phase = JobPhase.Restore(RestorePhase.EXTRACTING)))
        show()
        val why = words.getValue("waitRestore")
        assertWordsWhole(compose.onNodeWithText(why, useUnmergedTree = true), why)
    }

    private companion object {
        /** 12 September 2026, noon UTC. */
        const val SEPTEMBER_12_MS = 1_789_214_400_000L
        const val LARGE_FONT = 1.3f
        const val LARGER_FONT = 1.5f
        const val HEIGHT_SLACK_DP = 0.5f

        /** What `ListRow` lays out for an empty caption: it must not reach TalkBack. */
        const val HELD_LINE = " "
    }
}
