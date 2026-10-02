package com.violinjourney.app.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.violinjourney.app.core.domain.TolerancePreset
import com.violinjourney.app.core.domain.UserSettings
import com.violinjourney.app.core.domain.sound.BuiltInPreset
import com.violinjourney.app.core.ui.components.ListGroup
import com.violinjourney.app.core.ui.components.ListRow
import com.violinjourney.app.core.ui.components.ListRowEnd
import com.violinjourney.app.core.ui.format.Formats
import com.violinjourney.app.core.ui.format.languageName
import com.violinjourney.app.core.ui.icons.AppIcons
import com.violinjourney.app.core.ui.theme.ViolinTheme
import com.violinjourney.app.feature.backup.DataBlockState
import com.violinjourney.app.feature.backup.DataGroup
import com.violinjourney.app.feature.sound.SoundCaption
import com.violinjourney.app.feature.sound.captionName
import com.violinjourney.app.shared.resources.Res
import com.violinjourney.app.shared.resources.analytics_row
import com.violinjourney.app.shared.resources.analytics_row_caption
import com.violinjourney.app.shared.resources.backup_new_records_many
import com.violinjourney.app.shared.resources.backup_row_app_size
import com.violinjourney.app.shared.resources.backup_row_restore
import com.violinjourney.app.shared.resources.backup_row_save
import com.violinjourney.app.shared.resources.backup_row_stale
import com.violinjourney.app.shared.resources.dot_separator
import com.violinjourney.app.shared.resources.privacy_row
import com.violinjourney.app.shared.resources.privacy_row_caption
import com.violinjourney.app.shared.resources.restore_row_caption
import com.violinjourney.app.shared.resources.settings_group_intonation
import com.violinjourney.app.shared.resources.settings_language
import com.violinjourney.app.shared.resources.settings_restart_caption
import com.violinjourney.app.shared.resources.settings_restart_onboarding
import com.violinjourney.app.shared.resources.sound_settings_row
import com.violinjourney.app.shared.resources.sound_settings_row_caption
import com.violinjourney.app.testing.TEST_WINDOW
import com.violinjourney.app.testing.TestWindow
import com.violinjourney.app.testing.assertWordsWhole
import com.violinjourney.app.testing.textLayout
import java.util.Locale
import kotlin.math.abs
import kotlinx.datetime.TimeZone
import org.jetbrains.compose.resources.stringResource
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * «Настройки» of R8 (spec 3.36.8, 5.29 R8) by what an eye and a finger meet: the header 56 upright and 48 lying, the first label of a
 * group 8 under it; lying on the emulator's 640 × 360 (603 × 308 behind its cutout and bars, 603 × 336 where the status bar is hidden)
 * one column of 480 in the middle and the last row reached by the scroll; the note of «Эталон A4» on the title's line where both stand,
 * under it where they do not; at the font 1.3 on 360 no word of a row breaks — titles and captions of «Записи», «Данные» and
 * «Приложение», in Russian, German and French: an old copy with its weight on three lines, the statistics on more, «Политика
 * конфиденциальности» and «Datenschutzerklärung» a step smaller; on 320 at 1.5 «Язык» whole at its 16 sp, its value giving way;
 * until the settings are read the sound of the recordings unsaid. Laid out in a window of its own size ([TestWindow]); the words read
 * in the composition.
 */
@RunWith(AndroidJUnit4::class)
class SettingsScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private var windowSize by mutableStateOf(DpSize(412.dp, 868.dp))
    private var fontScale by mutableFloatStateOf(1f)
    private var settings by mutableStateOf(
        SettingsState(440, UserSettings.A4_OPTIONS_HZ, TolerancePreset.INTERMEDIATE, SoundCaption.BuiltIn(BuiltInPreset.OFF), analyticsEnabled = true),
    )
    private var intonation = ""
    private var restart = ""
    private var oldCopy = ""
    private var statistics = ""
    private var statisticsCaption = ""

    /** The words of the rows of «Записи», «Данные» and «Приложение» — titles and captions — as the screen shows them. */
    private val rowWords = mutableListOf<String>()
    private var soundCaption = ""

    /** The language of the device, given back after every test. */
    private val deviceLanguage: Locale = Locale.getDefault()

    @After
    fun backToTheLanguageOfTheDevice() = Locale.setDefault(deviceLanguage)

    private fun show() {
        compose.setContent {
            intonation = stringResource(Res.string.settings_group_intonation).uppercase()
            restart = stringResource(Res.string.settings_restart_onboarding)
            statistics = stringResource(Res.string.analytics_row)
            statisticsCaption = stringResource(Res.string.analytics_row_caption)
            // «последняя — 12 августа · с тех пор 9 новых записей · в приложении 3,4 ГБ»: the longest caption of «Сохранить копию»
            oldCopy = stringResource(Res.string.backup_row_stale, Formats.dayAndMonth(LAST_COPY_MS, TimeZone.currentSystemDefault()), stringResource(Res.string.backup_new_records_many, NEW_RECORDS)) +
                stringResource(Res.string.dot_separator) + stringResource(Res.string.backup_row_app_size, Formats.fileSize(WEIGHT))
            soundCaption = stringResource(Res.string.sound_settings_row_caption, captionName(settings.sound))
            rowWords.clear()
            rowWords += listOf(
                stringResource(Res.string.sound_settings_row), soundCaption,
                stringResource(Res.string.backup_row_save), oldCopy,
                stringResource(Res.string.backup_row_restore), stringResource(Res.string.restore_row_caption),
                statistics, statisticsCaption,
                stringResource(Res.string.privacy_row), stringResource(Res.string.privacy_row_caption),
                stringResource(Res.string.settings_language),
                restart, stringResource(Res.string.settings_restart_caption),
            )
            ViolinTheme {
                TestWindow(windowSize, fontScale = fontScale) {
                    SettingsScreen(
                        state = settings,
                        onIntent = {},
                        onBack = {},
                        dataBlock = {
                            DataGroup(
                                state = DataBlockState(dateRead = true, lastBackupAtEpochMs = LAST_COPY_MS, newSinceStale = NEW_RECORDS, totalBytes = WEIGHT),
                                analyticsEnabled = true,
                                onAnalyticsChange = {},
                                onOpenBackup = {},
                                onOpenRunningRestore = {},
                                onPickCopy = {},
                                onOpenPrivacy = {},
                            )
                        },
                        onLanguageClick = {},
                    )
                }
            }
        }
        compose.waitForIdle()
    }

    private fun SemanticsNodeInteraction.bounds(): DpRect = getUnclippedBoundsInRoot()

    private fun window(): DpRect = compose.onNodeWithTag(TEST_WINDOW).bounds()

    private fun assertNear(what: String, expected: Dp, actual: Dp) =
        assertTrue("$what: $actual, expected $expected", actual in (expected - 1.dp)..(expected + 1.dp))

    @Test
    fun uprightTheHeaderIs56AndTheFirstLabelStands8UnderIt() {
        show()
        val label = compose.onNodeWithText(intonation).bounds()
        assertNear("the first label under the header of 56", window().top + 56.dp + 8.dp, label.top)
        // the column of 412 less its fields of 16, the label 4 in from the group
        assertNear("the label from the edge", window().left + 16.dp + 4.dp, label.left)
    }

    /** Lying in [lying]: the header 48, the first label 8 under it, the column of 480 in the middle; the last row reached by the scroll. */
    private fun assertLying(lying: DpSize) {
        windowSize = lying
        show()
        val window = window()
        val label = compose.onNodeWithText(intonation).bounds()
        assertNear("$lying: the first label under the header of 48", window.top + 48.dp + 8.dp, label.top)
        assertNear("$lying: the column of 480 in the middle", window.left + (lying.width - 480.dp) / 2 + 16.dp + 4.dp, label.left)
        val last = compose.onNodeWithText(restart).performScrollTo().assertIsDisplayed().bounds()
        assertTrue("$lying: «$restart» whole in the window: $last of $window", last.bottom <= window.bottom + 0.5.dp)
    }

    /** The emulator's 640 × 360 behind its cutout and bars. */
    @Test
    fun lyingIn603By308TheHeaderIs48AndTheColumnOf480StandsInTheMiddle() = assertLying(DpSize(603.dp, 308.dp))

    /** The same where the status bar is hidden. */
    @Test
    fun lyingIn603By336TheHeaderIs48AndTheColumnOf480StandsInTheMiddle() = assertLying(DpSize(603.dp, 336.dp))

    /** «Эталон A4» and «все ноты считаются от него» on one line: Russian on 412. */
    @Test
    fun theNoteStandsBesideItsTitleWhereBothStand() {
        Locale.setDefault(Locale.forLanguageTag("ru"))
        show()
        val beside = compose.onAllNodesWithTag(CHOICE_TITLE)[0].bounds()
        assertTrue("one line, the note beside: ${beside.height}", beside.height < 30.dp)
    }

    /** «Kammerton A4» and «alle Töne gehen von ihm aus» do not stand together on 360 at 1.3: the note goes under the title. */
    @Test
    fun theNoteGoesUnderItsTitleWhereBothDoNotStand() {
        Locale.setDefault(Locale.forLanguageTag("de"))
        windowSize = DpSize(360.dp, 640.dp)
        fontScale = 1.3f
        show()
        val under = compose.onAllNodesWithTag(CHOICE_TITLE)[0].bounds()
        assertTrue("the note under the title: ${under.height}", under.height > 40.dp)
    }

    /** At the font 1.3 on 360 the captions of «Данные» wrap at their spaces and nothing is cut — the old copy with its weight, the statistics. */
    @Test
    fun atALargeFontOn360TheCaptionsOfTheDataStandWhole() {
        Locale.setDefault(Locale.forLanguageTag("ru"))
        windowSize = DpSize(360.dp, 640.dp)
        fontScale = 1.3f
        show()
        val caption = compose.onNodeWithText(oldCopy, useUnmergedTree = true).performScrollTo()
        assertWordsWhole(caption, oldCopy)
        assertTrue("the old copy with its weight takes more than one line", caption.textLayout().lineCount > 1)
        assertWordsWhole(compose.onNodeWithText(statistics, useUnmergedTree = true), statistics)
        assertWordsWhole(compose.onNodeWithText(statisticsCaption, useUnmergedTree = true), statisticsCaption)
    }

    /**
     * Every title and caption of the rows of «Записи», «Данные» and «Приложение» on 360 at the font 1.3 in [language] (spec 3.36.8
     * «Маленький экран и крупный шрифт»): wrapped at spaces, nothing cut. A title whose word does not stand at 16 sp — ru
     * «конфиденциальности», de «Datenschutzerklärung» (229 dp at 16 sp in a column of 220) — steps down rather than break.
     */
    private fun noWordOfTheRowsBreaksAtALargeFontOn360(language: String) {
        Locale.setDefault(Locale.forLanguageTag(language))
        windowSize = DpSize(360.dp, 640.dp)
        fontScale = 1.3f
        show()
        rowWords.forEach { words -> assertWordsWhole(compose.onNodeWithText(words, useUnmergedTree = true), "$language: $words") }
    }

    @Test
    fun atALargeFontOn360NoWordOfTheRowsBreaksInRussian() = noWordOfTheRowsBreaksAtALargeFontOn360("ru")

    @Test
    fun atALargeFontOn360NoWordOfTheRowsBreaksInGerman() = noWordOfTheRowsBreaksAtALargeFontOn360("de")

    @Test
    fun atALargeFontOn360NoWordOfTheRowsBreaksInFrench() = noWordOfTheRowsBreaksAtALargeFontOn360("fr")

    /**
     * «Язык» on 320 × 544 at the font 1.5 in [language] (spec 3.36.8; 5.29 R8): its value — the language named in itself — gives way to
     * the word of the title, cut with an ellipsis where the two do not stand side by side ([valueCut]; null — not asked): the title
     * stands whole at its 16 sp (its drawn size — [drawnSize]). de «Sprache» beside «Deutsch» had 78 dp for 92 and broke («Sprach / e»).
     */
    private fun theWordOfLanguageStandsWholeOnANarrowPhone(language: String, valueCut: Boolean?) {
        Locale.setDefault(Locale.forLanguageTag(language))
        var title = ""
        val value = languageName(language)
        compose.setContent {
            title = stringResource(Res.string.settings_language)
            ViolinTheme {
                TestWindow(DpSize(320.dp, 544.dp), fontScale = 1.5f) {
                    Column(Modifier.padding(horizontal = 16.dp)) {
                        ListGroup {
                            ListRow(text = title, onClick = {}, icon = AppIcons.Globe, end = ListRowEnd.Value(value, chevron = true))
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
        val words = compose.onNodeWithText(title, useUnmergedTree = true)
        assertWordsWhole(words, "$language: $title")
        assertEquals("$language: «$title» at its 16 sp", WORDS_SIZE, drawnSize(title, words.textLayout()))
        if (valueCut != null) {
            val shown = compose.onNodeWithText(value, useUnmergedTree = true).textLayout()
            assertEquals("$language: «$value» gives way — cut with an ellipsis", valueCut, shown.isLineEllipsized(0))
        }
    }

    @Test
    fun onANarrowPhoneAtALargeFontTheWordOfLanguageStandsWholeInGerman() = theWordOfLanguageStandsWholeOnANarrowPhone("de", valueCut = true)

    @Test
    fun onANarrowPhoneAtALargeFontTheWordOfLanguageStandsWholeInEnglish() = theWordOfLanguageStandsWholeOnANarrowPhone("en", valueCut = true)

    @Test
    fun onANarrowPhoneAtALargeFontTheWordOfLanguageStandsWholeInPortuguese() = theWordOfLanguageStandsWholeOnANarrowPhone("pt", valueCut = true)

    /** fr «Langue» beside «Français»: some 2 dp to spare by the measure — whether the value is cut there is not asked. */
    @Test
    fun onANarrowPhoneAtALargeFontTheWordOfLanguageStandsWholeInFrench() = theWordOfLanguageStandsWholeOnANarrowPhone("fr", valueCut = null)

    /**
     * The size a one-line title of [layout] is drawn at: its `autoSize` may step it down, and the semantics give the layout drawn with
     * the style of the node (16 sp whatever was found) — the width tells: the step of 0.5 sp from 16 down to 13 at which the words,
     * measured in the same style, are as wide on one line as the layout drawn (as `JourneyScreenTest.drawnSize`).
     */
    private fun drawnSize(what: String, layout: TextLayoutResult): TextUnit {
        val drawn = layout.multiParagraph.maxIntrinsicWidth
        val steps = generateSequence(WORDS_SIZE.value) { it - SIZE_STEP }.takeWhile { it >= WORDS_LEAST.value }.map { it.sp }
        return steps.firstOrNull { abs(widthAt(layout, it) - drawn) < WIDTH_SLACK_PX }
            ?: throw AssertionError("$what: the words are $drawn px wide, at no size from $WORDS_SIZE to $WORDS_LEAST")
    }

    /** How wide the words of [layout] are on one line in their own style at [size], in px. */
    private fun widthAt(layout: TextLayoutResult, size: TextUnit): Float = compose.runOnIdle {
        val input = layout.layoutInput
        TextMeasurer(input.fontFamilyResolver, input.density, input.layoutDirection)
            .measure(input.text, input.style.copy(fontSize = size), softWrap = false, maxLines = 1)
            .multiParagraph.maxIntrinsicWidth
    }

    /** Until the settings are read the row «Звук записей» holds its caption's line and says no default — not «без обработки». */
    @Test
    fun untilTheSettingsAreReadTheSoundOfTheRecordingsSaysNothing() {
        settings = settings.copy(read = false)
        show()
        compose.onAllNodesWithText(soundCaption, useUnmergedTree = true).assertCountEquals(0)
        settings = settings.copy(read = true)
        compose.waitForIdle()
        compose.onAllNodesWithText(soundCaption, useUnmergedTree = true).assertCountEquals(1)
    }

    private companion object {
        /** The tag the title and note of a row of «Интонация» carry for tests only (`SettingsScreen.kt`): a reader hears them as the group. */
        const val CHOICE_TITLE = "settings choice title"

        /** 12 August 2026, noon UTC: a copy older than 30 days. */
        const val LAST_COPY_MS = 1_786_536_000_000L
        const val NEW_RECORDS = 9

        /** 3,4 ГБ. */
        const val WEIGHT = 3_650_722_201L

        /** The words of a row and the least they step down to (spec 5.29 R8, stage 121), 0.5 sp at a time. */
        val WORDS_SIZE = 16.sp
        val WORDS_LEAST = 13.sp
        const val SIZE_STEP = 0.5f
        const val WIDTH_SLACK_PX = 0.5f
    }
}
