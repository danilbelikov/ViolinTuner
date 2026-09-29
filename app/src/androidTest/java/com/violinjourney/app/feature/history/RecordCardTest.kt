package com.violinjourney.app.feature.history

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.violinjourney.app.core.ui.format.Formats
import com.violinjourney.app.core.ui.theme.ViolinTheme
import com.violinjourney.app.feature.history.components.CardActions
import com.violinjourney.app.feature.history.components.RecordCard
import com.violinjourney.app.feature.history.components.RecordPlace
import com.violinjourney.app.feature.history.components.SessionCard
import com.violinjourney.app.feature.practice.PracticeIntent
import com.violinjourney.app.feature.practice.SelectedDay
import com.violinjourney.app.feature.practice.components.DaySheetContent
import com.violinjourney.app.shared.resources.Res
import com.violinjourney.app.shared.resources.backing_take_mark
import com.violinjourney.app.shared.resources.card_menu
import com.violinjourney.app.shared.resources.card_menu_delete
import com.violinjourney.app.shared.resources.card_menu_share
import com.violinjourney.app.shared.resources.card_menu_sound
import com.violinjourney.app.shared.resources.record_tile_no_sound
import com.violinjourney.app.shared.resources.record_tile_take
import com.violinjourney.app.testing.assertWholeOnOneLine
import com.violinjourney.app.testing.numbers
import com.violinjourney.app.testing.textLayout
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import org.jetbrains.compose.resources.stringResource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The card of a recording of R5 (spec 3.36.5) to TalkBack, to an eye and to a finger: one description — its kind, the title, the date,
 * «под минусовку», «без звука» — and no text of the words it shows (the word of its kind, seen in the line, is not heard twice); its
 * line keeps the length whole when a long date has to give way; «⋯» a button of its own, «Ещё», gone while picking; its menu ends with
 * «Удалить…», alone for a recording without sound that is no take; in «Записи этого дня» the card only opens its recording. The words
 * are read in the composition, in the language the screen speaks (the lesson of stage 107).
 */
@RunWith(AndroidJUnit4::class)
class RecordCardTest {
    @get:Rule
    val compose = createComposeRule()

    private val words = mutableMapOf<String, String>()
    private val deleted = mutableListOf<Long>()
    private val actions = CardActions(onShare = {}, onSound = {}, onDelete = { deleted += it }, onBest = {})
    private val day = LocalDate(2026, 9, 23)

    /** A take of «Концерт» under its backing whose file is lost: every word the description may carry, and no word of its kind seen. */
    private val take = HistoryCard(
        id = 7, title = null, startedAtEpochMs = 1_790_150_700_000, date = day, durationMs = 220_000, pieceTitle = CONCERTO, pieceId = 2,
        hasAudio = false, underBacking = true,
    )

    /** A take of «Менуэт» with its sound: its line says «дубль» — the word the description begins with. */
    private val soundTake = HistoryCard(
        id = 8, title = null, startedAtEpochMs = 1_790_160_000_000, date = day, durationMs = 125_000, pieceTitle = MINUET, pieceId = 3,
        hasAudio = true,
    )

    /** A recording of its own without sound: its «⋯» has «Удалить…» alone. */
    private val silent = HistoryCard(id = 5, title = null, startedAtEpochMs = 1_790_140_000_000, date = day, durationMs = 36_000)

    private fun cardWith(title: String) = compose.onNode(
        SemanticsMatcher("the card of «$title»") { node ->
            node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty().any { title in it } &&
                node.config.getOrNull(SemanticsProperties.Role) in listOf(Role.Button, Role.Checkbox)
        },
    )

    @Test
    fun aCardIsOneDescriptionAndTheWordsItShowsAreNotHeardAgain() {
        compose.setContent {
            words[KIND] = stringResource(Res.string.record_tile_take)
            words[BACKING] = stringResource(Res.string.backing_take_mark)
            words[NO_SOUND] = stringResource(Res.string.record_tile_no_sound)
            words[DATE] = Formats.recordDate(day, withYear = false)
            ViolinTheme {
                Column {
                    SessionCard(take, TimeZone.UTC, onClick = {}, actions = actions)
                    SessionCard(soundTake, TimeZone.UTC, onClick = {}, actions = actions)
                }
            }
        }
        val said = cardWith(CONCERTO).fetchSemanticsNode().config[SemanticsProperties.ContentDescription].single()
        listOf(KIND, BACKING, NO_SOUND, DATE).forEach { key -> assertTrue("«$said» says «${words.getValue(key)}»", words.getValue(key) in said) }
        assertTrue("the kind comes first: «$said»", said.startsWith(words.getValue(KIND)))
        // «дубль» is seen in the line of the take with its sound, and heard once — first in its description
        val saidOfTake = cardWith(MINUET).fetchSemanticsNode().config[SemanticsProperties.ContentDescription].single()
        assertEquals("«$saidOfTake» says its kind once", 1, saidOfTake.split(words.getValue(KIND)).size - 1)
        // the tile, the title and the line are seen, not read: no node carries their words, the cards themselves no text at all
        listOf(CONCERTO, MINUET).forEach { title ->
            assertNull("the card of «$title» has no text of its own", cardWith(title).fetchSemanticsNode().config.getOrNull(SemanticsProperties.Text))
            compose.onAllNodesWithText(title, substring = true).assertCountEquals(0)
        }
        compose.onAllNodesWithText(words.getValue(KIND), substring = true).assertCountEquals(0)
        compose.onAllNodesWithText(Formats.duration(soundTake.durationMs), substring = true).assertCountEquals(0)
    }

    /**
     * Spec 3.36.5: the time — here the date of a take with a name of its own — and the length are seen always, the words end in an
     * ellipsis first. A date that cannot stand whole beside the length gives way; the length never: a line of the takes of a piece on 360
     * at a large font, beside «⋯» (the card in the field of 328, the words in 200).
     */
    @Test
    fun aLongDateGivesWayAndTheLengthStaysWhole() {
        compose.setContent {
            words[LENGTH] = Formats.duration(take.durationMs)
            ViolinTheme {
                CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, LARGE_FONT)) {
                    Box(Modifier.requiredWidth(FIELD)) {
                        RecordCard(
                            card = take.copy(title = RENAMED), title = RENAMED, start = LONG_DATE, onClick = {}, place = RecordPlace.Takes,
                            actions = actions,
                        )
                    }
                }
            }
        }
        val dateNode = compose.onNodeWithText(LONG_DATE, useUnmergedTree = true)
        val date = dateNode.textLayout()
        assertTrue("the date gives way: the case is there — ${date.numbers(dateNode)}", date.isLineEllipsized(0))
        val lengthNode = compose.onNodeWithText(words.getValue(LENGTH), substring = true, useUnmergedTree = true)
        // whole on one line and not clipped at its end (it does not wrap) — not `hasVisualOverflow`: the piece is a plain-String text,
        // and the layout it hands is laid out at the whole width of the line (testing/TextLayouts.kt)
        assertWholeOnOneLine(lengthNode, words.getValue(LENGTH))
        val card = cardWith(RENAMED).getUnclippedBoundsInRoot()
        val length = lengthNode.getUnclippedBoundsInRoot()
        assertTrue("the length stands inside the card: $length, the card $card", length.right <= card.right)
    }

    @Test
    fun theMoreButtonIsAButtonOfItsOwnAndGoesWhilePicking() {
        var picking by mutableStateOf<Boolean?>(null)
        compose.setContent {
            words[MORE] = stringResource(Res.string.card_menu)
            words[DELETE] = stringResource(Res.string.card_menu_delete)
            ViolinTheme { SessionCard(take, TimeZone.UTC, onClick = {}, actions = actions, selected = picking, onLongClick = {}) }
        }
        compose.onNodeWithContentDescription(words.getValue(MORE)).assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
        compose.onNodeWithContentDescription(words.getValue(MORE)).performClick()
        compose.onNodeWithText(words.getValue(DELETE)).performClick()
        assertEquals(listOf(7L), deleted)

        picking = false
        compose.waitForIdle()
        compose.onAllNodesWithContentDescription(words.getValue(MORE)).assertCountEquals(0)
    }

    @Test
    fun aRecordingWithoutSoundThatIsNoTakeOffersOnlyDelete() {
        compose.setContent {
            words[MORE] = stringResource(Res.string.card_menu)
            words[DELETE] = stringResource(Res.string.card_menu_delete)
            words[SHARE] = stringResource(Res.string.card_menu_share)
            words[SOUND] = stringResource(Res.string.card_menu_sound)
            ViolinTheme { SessionCard(silent, TimeZone.UTC, onClick = {}, actions = actions) }
        }
        compose.onNodeWithContentDescription(words.getValue(MORE)).performClick()
        compose.onNodeWithText(words.getValue(DELETE)).assertExists()
        compose.onAllNodesWithText(words.getValue(SHARE)).assertCountEquals(0)
        compose.onAllNodesWithText(words.getValue(SOUND)).assertCountEquals(0)
    }

    /**
     * «Записи этого дня» of the sheet of a day (spec 3.36.2, 3.36.5), as the sheet lays them: a card opens its recording, and there is no
     * «⋯» and no long press — there a recording is only opened.
     */
    @Test
    fun theSheetOfADayOnlyOpensItsRecordings() {
        val opened = mutableListOf<PracticeIntent>()
        compose.setContent {
            words[MORE] = stringResource(Res.string.card_menu)
            ViolinTheme {
                DaySheetContent(
                    SelectedDay(day, isToday = false, totalMs = 1_800_000, sessions = listOf(soundTake, silent)),
                    onIntent = { opened += it },
                    zone = TimeZone.UTC,
                )
            }
        }
        compose.onAllNodesWithContentDescription(words.getValue(MORE)).assertCountEquals(0)
        assertFalse("no long press on the sheet", SemanticsActions.OnLongClick in cardWith(MINUET).fetchSemanticsNode().config)
        cardWith(MINUET).performClick()
        assertEquals(listOf<PracticeIntent>(PracticeIntent.SessionClicked(soundTake.id)), opened)
    }

    private companion object {
        const val CONCERTO = "Концерт ля минор, 1 ч."
        const val MINUET = "Менуэт соль мажор"
        const val RENAMED = "Прогон перед концертом"

        /** A date with its year in Spanish — the longest of the ten languages (5.29 R5). */
        const val LONG_DATE = "27 de septiembre de 2025"
        const val LARGE_FONT = 1.3f

        /** The field of a list on a phone of 360: 16 from each edge. */
        val FIELD = 328.dp
        const val KIND = "kind"
        const val BACKING = "backing"
        const val NO_SOUND = "noSound"
        const val DATE = "date"
        const val LENGTH = "length"
        const val MORE = "more"
        const val DELETE = "delete"
        const val SHARE = "share"
        const val SOUND = "sound"
    }
}
