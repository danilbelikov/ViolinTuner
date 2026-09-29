package com.violinjourney.app.feature.practice.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.violinjourney.app.core.domain.practice.ForgottenEndings
import com.violinjourney.app.core.ui.components.AppSheet
import com.violinjourney.app.core.ui.components.AppSheetButtons
import com.violinjourney.app.core.ui.components.AppSheetDefaults
import com.violinjourney.app.core.ui.components.LocalMessages
import com.violinjourney.app.core.ui.components.SectionLabel
import com.violinjourney.app.core.ui.format.Formats
import com.violinjourney.app.feature.practice.ForgottenKind
import com.violinjourney.app.feature.practice.PracticePrompt
import com.violinjourney.app.feature.practice.PracticePromptEffect
import com.violinjourney.app.feature.practice.PracticePromptIntent
import com.violinjourney.app.shared.resources.Res
import com.violinjourney.app.shared.resources.practice_continue
import com.violinjourney.app.shared.resources.practice_end_at
import com.violinjourney.app.shared.resources.practice_end_at_length
import com.violinjourney.app.shared.resources.practice_end_now
import com.violinjourney.app.shared.resources.practice_end_now_length
import com.violinjourney.app.shared.resources.practice_first_quoted
import com.violinjourney.app.shared.resources.practice_forgotten_answered
import com.violinjourney.app.shared.resources.practice_forgotten_running
import com.violinjourney.app.shared.resources.practice_forgotten_silent
import com.violinjourney.app.shared.resources.practice_forgotten_sounded
import com.violinjourney.app.shared.resources.practice_forgotten_title
import com.violinjourney.app.shared.resources.practice_set_length
import com.violinjourney.app.shared.resources.practice_too_short
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.datetime.TimeZone
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource

// «Занятие не закончено» (spec 3.36.3, 5.29 R3; practice-sheets.html 3).
private const val RUNNING_SIZE = 30
private const val RUNNING_TRACKING = -0.02
private const val RUNNING_LINE_HEIGHT = 1.2
private const val LINE_SIZE = 15
private const val LINE_HEIGHT = 22
private const val TABULAR_FIGURES = "tnum"
private val RunningTop = 6.dp
private val RunningBottom = 4.dp

/**
 * The prompt of the app over any screen, Android and iOS (spec 3.12, 3.36.3): «Занятие не закончено» as a bottom sheet, and in its
 * place, in the same frame, «Закончить занятие» of «Указать, сколько играли» or of a practice past the limit. A swipe, «назад» and a
 * tap beside it are «Продолжаю заниматься» on the first and only hide the second. [endings] — the numbers of «Занятие не закончено»,
 * read by the parts that show them; a sheet sliding away after an answer keeps the last it showed ([KeptEndings]). What the prompt
 * tells without asking — [effects] — comes as toasts of [LocalMessages].
 */
@Composable
fun PracticePromptHost(
    prompt: PracticePrompt?,
    endings: () -> ForgottenEndings?,
    stepMinutes: Int,
    onIntent: (PracticePromptIntent) -> Unit,
    // asked once: a new zone on every recomposition is a new object each time, and on iOS a read of its file
    zone: TimeZone = remember { TimeZone.currentSystemDefault() },
    effects: Flow<PracticePromptEffect> = emptyFlow(),
) {
    val messages = LocalMessages.current
    LaunchedEffect(effects) {
        effects.collect { effect ->
            when (effect) {
                PracticePromptEffect.ShowTooShort -> messages.show(getString(Res.string.practice_too_short))
            }
        }
    }
    val kept = remember { KeptEndings() }
    AppSheet(
        value = prompt,
        // what the sheet showed when it began to go down: «Указать, сколько играли» and then «назад» at once hides the question, not
        // the «Закончить занятие» that came while it slid
        onHide = { hidden ->
            when (hidden) {
                // the safe answer of the sheet (spec 3.36): a sign of life, the question comes back after another quiet hour
                is PracticePrompt.Forgotten -> onIntent(PracticePromptIntent.Continue)
                // only hidden: the practice stays, and the question comes back the next time the app opens
                is PracticePrompt.Summary -> onIntent(PracticePromptIntent.SummaryHidden)
            }
        },
        modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars).padding(top = AppSheetDefaults.TopClearance),
        bottom = { shown ->
            when (shown) {
                is PracticePrompt.Forgotten -> {
                    { ForgottenButtons(shown, { kept.of(shown, endings()) }, onIntent, zone) }
                }
                is PracticePrompt.Summary -> {
                    {
                        SummaryButtons(
                            sheet = shown.sheet,
                            onSave = { onIntent(PracticePromptIntent.SummarySaved) },
                            onDiscard = { onIntent(PracticePromptIntent.SummaryDiscarded) },
                        )
                    }
                }
            }
        },
    ) { shown ->
        when (shown) {
            is PracticePrompt.Forgotten -> ForgottenSheetContent(shown, { kept.of(shown, endings()) }, zone)
            is PracticePrompt.Summary -> SummarySheetContent(shown.sheet, stepMinutes, zone, onStep = { onIntent(PracticePromptIntent.SummaryStepped(it)) })
        }
    }
}

/**
 * The last numbers each question showed (spec 3.36.3: the numbers change at once, nothing else on the sheet moves). After an answer
 * the prompt and its numbers are gone at once — «Закончить сейчас» even before, when the save clears the store — while the sheet still
 * slides away with the face it had: it keeps saying what it said, no blank «Идёт», no buttons without their numbers. A new question
 * starts without numbers until its own first tick. Only a memory of what was read: the numbers themselves are the state the readers
 * of [of] follow.
 */
internal class KeptEndings {
    private var question: PracticePrompt.Forgotten? = null
    private var last: ForgottenEndings? = null

    /** The numbers to show on the face of [shown]: [now], or while there are none, the last ones this question had. */
    fun of(shown: PracticePrompt.Forgotten, now: ForgottenEndings?): ForgottenEndings? {
        if (shown != question) {
            question = shown
            last = null
        }
        if (now != null) last = now
        return last
    }
}

/**
 * «Занятие не закончено» (spec 3.36.3): the label, «Идёт 3 ч 12 мин» large — it follows the clock of the practice — and the line of
 * its [PracticePrompt.Forgotten.kind]: the violin sounded last at 18:42; it has not sounded at all; or the last mark was the answer
 * «Продолжаю заниматься». Its answers are [ForgottenButtons], under the thumb at the bottom of the sheet.
 */
@Composable
fun ForgottenSheetContent(prompt: PracticePrompt.Forgotten, endings: () -> ForgottenEndings?, zone: TimeZone, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val title = stringResource(Res.string.practice_forgotten_title)
    val mark = prompt.practice.lastSoundEpochMs?.let { Formats.timeOfDay(it, zone) }
    Column(modifier.fillMaxWidth().semantics { paneTitle = title }) {
        SectionLabel(title)
        RunningFor(endings)
        Text(
            text = when (prompt.kind) {
                ForgottenKind.Sounded -> stringResource(Res.string.practice_forgotten_sounded, mark.orEmpty())
                ForgottenKind.Silent -> stringResource(Res.string.practice_forgotten_silent)
                ForgottenKind.Answered -> stringResource(
                    Res.string.practice_forgotten_answered,
                    mark.orEmpty(),
                    stringResource(Res.string.practice_first_quoted, stringResource(Res.string.practice_continue)),
                )
            },
            color = colors.onSurfaceVariant,
            style = MaterialTheme.typography.bodyLarge.copy(fontSize = LINE_SIZE.sp, lineHeight = LINE_HEIGHT.sp),
        )
    }
}

/** «Идёт 3 ч 12 мин»: the only part of the content that reads the clock — a minute changes it, not the sheet. */
@Composable
private fun RunningFor(endings: () -> ForgottenEndings?) {
    val running = endings()?.runningMs
    Text(
        // before the first tick the line keeps its height, empty
        text = running?.let { stringResource(Res.string.practice_forgotten_running, Formats.minutesInWords(it)) }.orEmpty(),
        modifier = Modifier.padding(top = RunningTop, bottom = RunningBottom),
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 2,
        style = MaterialTheme.typography.headlineMedium.copy(
            fontSize = RUNNING_SIZE.sp,
            lineHeight = RUNNING_LINE_HEIGHT.em,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = RUNNING_TRACKING.em,
            fontFeatureSettings = TABULAR_FIGURES,
        ),
    )
}

/**
 * The three answers of «Занятие не закончено» (spec 3.36.3), each saying what it saves: «Закончить в 18:42 · 1 ч 4 мин» first — or,
 * when the violin never sounded, «Указать, сколько играли»; «Закончить сейчас · 3 ч 12 мин» as an outline; «Продолжаю заниматься»
 * quietly. A length under a minute has no number: the answer gives «Слишком коротко».
 */
@Composable
fun ForgottenButtons(
    prompt: PracticePrompt.Forgotten,
    endings: () -> ForgottenEndings?,
    onIntent: (PracticePromptIntent) -> Unit,
    zone: TimeZone,
    modifier: Modifier = Modifier,
) {
    val numbers = endings()
    val mark = prompt.practice.lastSoundEpochMs?.let { Formats.timeOfDay(it, zone) }
    val silent = prompt.kind == ForgottenKind.Silent || mark == null
    val atMark = numbers?.endAtMarkMs
    AppSheetButtons(
        main = when {
            silent -> stringResource(Res.string.practice_set_length)
            atMark != null -> stringResource(Res.string.practice_end_at_length, mark.orEmpty(), Formats.minutesInWords(atMark))
            else -> stringResource(Res.string.practice_end_at, mark.orEmpty())
        },
        onMain = { onIntent(if (silent) PracticePromptIntent.SetLength else PracticePromptIntent.EndAtLastSound) },
        modifier = modifier,
        second = numbers?.endNowMs?.let { stringResource(Res.string.practice_end_now_length, Formats.minutesInWords(it)) }
            ?: stringResource(Res.string.practice_end_now),
        onSecond = { onIntent(PracticePromptIntent.EndNow) },
        quiet = stringResource(Res.string.practice_continue),
        onQuiet = { onIntent(PracticePromptIntent.Continue) },
    )
}
