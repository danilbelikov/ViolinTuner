package com.violinjourney.app.feature.events

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.violinjourney.app.core.ui.components.AppButton
import com.violinjourney.app.core.ui.components.AppButtonStyle
import com.violinjourney.app.core.ui.components.LocalFaceArrival
import com.violinjourney.app.core.ui.components.SectionLabel
import com.violinjourney.app.core.ui.icons.AppIcon
import com.violinjourney.app.core.ui.icons.AppIcons
import com.violinjourney.app.core.ui.theme.ViolinTheme

/**
 * One answer of the sheet of a repeat (spec 3.36.9): its words and, under them, the dates it touches — «Этот и следующие · 19, 26 окт. и
 * дальше». [style] — [AppButtonStyle.Main] for the reasonable answer of an edit, filled and first; [AppButtonStyle.Outline] for the other;
 * [AppButtonStyle.OutlineDanger] for both answers of a deletion, neither of them filled.
 */
data class ScopeAnswer(val title: String, val caption: String, val style: AppButtonStyle, val onClick: () -> Unit)

/**
 * The sheet of a repeat (spec 3.36.9, 5.29 R9 «Листы повтора»): a face of the frame of the sheets of a screen, its answers pinned at its
 * bottom ([ScopeAnswers]) — in a window no higher than 360 dp after its note, at the end of what scrolls ([scopeAnswersPinned]). Two kinds
 * of it, one look:
 *
 * - an edit of an event of a repeat (the form of an event): the label of the section — «Урок повторяется» ([label]) — the question large,
 *   the plate of what changes ([change]) and the note under it, «Прошедшие уроки не изменятся — …»;
 * - a deletion ([bin]): everything in the middle — the bin in its coral circle, «Удалить урок 19 октября?», the text of what goes and the
 *   short note.
 *
 * The words are the caller's: the word of the kind («урок», «репетиция», «событие») and the dates are said in them, whole sentences in
 * every language. A swipe, «назад» and a tap beside it are «Отмена»: nothing is saved or deleted.
 */
@Composable
fun ScopeSheetContent(
    question: String,
    note: String,
    modifier: Modifier = Modifier,
    label: String? = null,
    bin: Boolean = false,
    change: (@Composable () -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    val align = if (bin) Alignment.CenterHorizontally else Alignment.Start
    val textAlign = if (bin) TextAlign.Center else TextAlign.Start
    Column(modifier.fillMaxWidth(), horizontalAlignment = align) {
        if (bin) {
            Box(
                Modifier
                    .padding(bottom = EventsDimens.ScopeBinBottom)
                    .size(EventsDimens.ScopeBin)
                    .background(ViolinTheme.dangerSoft.copy(alpha = EventsDimens.SCOPE_BIN_ALPHA), CircleShape),
                contentAlignment = Alignment.Center,
            ) { AppIcon(AppIcons.Trash, contentDescription = null, tint = ViolinTheme.dangerSoft, size = EventsDimens.ScopeBinIcon) }
        }
        if (label != null) SectionLabel(label)
        Text(
            text = question,
            modifier = Modifier.fillMaxWidth().padding(top = if (label != null) EventsDimens.ScopeQuestionTop else 0.dp).semantics { heading() },
            color = colors.onSurface,
            textAlign = textAlign,
            style = MaterialTheme.typography.titleLarge.copy(
                fontSize = EventsDimens.ScopeQuestion, lineHeight = EventsDimens.ScopeQuestionHeight, fontWeight = FontWeight.ExtraBold,
            ),
        )
        change?.invoke()
        Text(
            text = note,
            modifier = Modifier.fillMaxWidth().padding(top = EventsDimens.ScopeNoteTop),
            color = colors.onSurfaceVariant,
            textAlign = textAlign,
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = EventsDimens.ScopeNote, lineHeight = EventsDimens.ScopeNoteHeight),
        )
    }
}

/**
 * The answers of the sheet of a repeat (spec 3.36.9, 5.29 R9): at least 60 each — two lines do not stand in 48 — the words 16 sp / 800
 * and under them 13 sp / 600 in the second level of text (on the filled one, in its own colour), each line in the middle of its button
 * (events-form.html 6), 6 apart, the first read first; «Отмена» a quiet line of 48 under them. Pinned at the bottom of the sheet; in a
 * window no higher than 360 dp they end what scrolls instead ([scopeAnswersPinned]) — the host decides. A face that has just come in the
 * place of another holds its answers for the time of a double tap: the finger that pressed the button before it does not answer it.
 */
@Composable
fun ScopeAnswers(answers: List<ScopeAnswer>, cancel: String, onCancel: () -> Unit, modifier: Modifier = Modifier) {
    val arrival = LocalFaceArrival.current
    val colors = MaterialTheme.colorScheme
    Column(modifier.fillMaxWidth().padding(top = EventsDimens.ScopeAnswersTop), verticalArrangement = Arrangement.spacedBy(EventsDimens.ScopeAnswersGap)) {
        answers.forEach { answer ->
            AppButton(
                text = answer.title,
                onClick = { if (!arrival.holds) answer.onClick() },
                modifier = Modifier.fillMaxWidth().heightIn(min = EventsDimens.ScopeAnswerMin),
                style = answer.style,
                caption = answer.caption,
                fontSize = EventsDimens.ScopeAnswerText,
                captionColor = if (answer.style == AppButtonStyle.Main) colors.onPrimary else colors.onSurfaceVariant,
                linesAlign = Alignment.CenterHorizontally,
            )
        }
        AppButton(cancel, onClick = onCancel, modifier = Modifier.fillMaxWidth(), style = AppButtonStyle.Quiet)
    }
}

/**
 * Where the answers of the sheet of a repeat stand (5.29 R9, «Уточнено на этапе 98а»): pinned at its bottom, under the thumb, the
 * content scrolling over them — but in a window no higher than 360 dp ([com.violinjourney.app.core.ui.components.DockMetrics.compact])
 * at the end of what scrolls, after the note: pinned, two answers of 60 and «Отмена» take 214 dp of a sheet of some 250, and the
 * question was left no room over them (640 × 360). The sheet of a deletion now, the sheet of an edit of the form after it.
 */
fun scopeAnswersPinned(compact: Boolean): Boolean = !compact
