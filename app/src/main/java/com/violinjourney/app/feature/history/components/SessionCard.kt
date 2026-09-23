package com.violinjourney.app.feature.history.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.violinjourney.app.R
import com.violinjourney.app.core.ui.format.Formats
import com.violinjourney.app.core.ui.icons.AppIcon
import com.violinjourney.app.core.ui.icons.AppIcons
import com.violinjourney.app.core.ui.theme.ViolinTheme
import com.violinjourney.app.feature.history.HistoryCard
import java.time.ZoneId

private val CardCorner = 16.dp
private val CardRing = 1.5.dp
private val BestStar = 14.dp
private const val BEST_IN_MS = 150
private const val BEST_OUT_MS = 120
private const val BEST_FROM_SCALE = 0.6f
private const val HIGHLIGHT_FADE_MS = 1_500
private const val SELECT_FADE_MS = 150
private const val TILE_MORPH_MS = 200
private const val PRESS_MS = 100
private const val PRESSED_SCALE = 0.98f
private const val TABULAR_FIGURES = "tnum"

/** Card of one recording under a date: the list of «Записи» (spec 3.11, 3.21) and the day's records on the practice screen. */
@Composable
fun SessionCard(
    card: HistoryCard,
    zone: ZoneId,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    actions: CardActions? = null,
    selected: Boolean? = null,
    onLongClick: (() -> Unit)? = null,
) {
    RecordCard(
        card = card,
        // The date stands once, above the group (the day header of «Записи», the picked day of «Занятия»): the card adds the time.
        title = card.title ?: card.pieceTitle ?: stringResource(R.string.record_default_title),
        meta = stringResource(R.string.record_meta, Formats.timeOfDay(card.startedAtEpochMs, zone), Formats.duration(card.durationMs)),
        onClick = onClick,
        modifier = modifier,
        actions = actions,
        selected = selected,
        onLongClick = onLongClick,
    )
}

/**
 * The one card of a recording — in «Записи», on the practice screen, a take on the screen of its
 * piece (spec 3.21, handoff 22b1). Quiet on purpose: a tile of one colour, a title, one line — no
 * score, no zone, no bars. A take marked as the best carries a star after its title; [highlighted]
 * belongs to a take recorded a moment ago.
 *
 * [selected] is null outside the selection mode (spec 3.18). Inside it the card is a checkbox:
 * the tile turns into a mark of the same shape, a tap picks instead of opening, «⋯» is gone.
 * [onLongClick] is how the mode is entered from a card; the press itself shrinks the card a little.
 */
@Composable
fun RecordCard(
    card: HistoryCard,
    title: String,
    meta: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    actions: CardActions? = null,
    highlighted: Boolean = false,
    selected: Boolean? = null,
    onLongClick: (() -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(CardCorner)
    val selecting = selected != null
    // Sound can be shared and processed, a take can be marked as the best: a free recording without sound has nothing to offer.
    val menu = actions != null && !selecting && (card.hasAudio || (card.take && actions.onBest != null))
    val words = listOfNotNull(
        stringResource(if (card.take) R.string.record_tile_take else R.string.record_tile_record),
        stringResource(R.string.take_best).takeIf { card.best },
        stringResource(
            when {
                !card.hasAudio -> R.string.record_tile_no_sound
                card.hasVideo -> R.string.record_tile_only_video
                else -> R.string.record_tile_sound
            },
        ),
    ).joinToString()
    // A picked card looks like a fresh take (the handoff gives both the same fill and ring), only it gets there faster.
    val lit = if (selecting) selected == true else highlighted
    val fade = tween<Color>(if (selecting) SELECT_FADE_MS else HIGHLIGHT_FADE_MS)
    val background by animateColorAsState(if (lit) ViolinTheme.repertoireColors.takeNew else colors.surfaceContainer, fade, label = "recordBackground")
    val ring by animateColorAsState(if (lit) colors.primary else colors.primary.copy(alpha = 0f), fade, label = "recordRing")
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed && onLongClick != null && !selecting) PRESSED_SCALE else 1f, tween(PRESS_MS), label = "recordPress")
    val longClickLabel = stringResource(R.string.selection_select)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(shape)
            .background(background)
            .border(CardRing, ring, shape)
            .then(
                if (selected != null) {
                    Modifier.toggleable(value = selected, role = Role.Checkbox, onValueChange = { onClick() })
                } else {
                    Modifier.combinedClickable(
                        interactionSource = interaction,
                        indication = LocalIndication.current,
                        role = Role.Button,
                        onLongClickLabel = longClickLabel.takeIf { onLongClick != null },
                        onLongClick = onLongClick,
                        onClick = onClick,
                    )
                },
            )
            .padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = if (menu) 4.dp else 16.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Crossfade(targetState = selected, animationSpec = tween(TILE_MORPH_MS), label = "recordTile") { mark ->
            if (mark == null) {
                RecordTile(hasAudio = card.hasAudio, hasVideo = card.hasVideo, modifier = Modifier.semantics { contentDescription = words })
            } else {
                SelectionMark(mark)
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    modifier = Modifier.weight(1f, fill = false),
                    color = colors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold, fontFeatureSettings = TABULAR_FIGURES),
                )
                // A star, not a chip with a word: in a quiet list one accent mark is seen at once; not on the tile, which turns into a checkbox.
                AnimatedVisibility(visible = card.best, enter = fadeIn(tween(BEST_IN_MS)) + scaleIn(tween(BEST_IN_MS), initialScale = BEST_FROM_SCALE), exit = fadeOut(tween(BEST_OUT_MS))) {
                    AppIcon(AppIcons.Star, contentDescription = null, tint = colors.primary, size = BestStar)
                }
            }
            Text(
                text = meta,
                modifier = Modifier.padding(top = 2.dp),
                color = colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, fontFeatureSettings = TABULAR_FIGURES),
            )
        }
        if (actions != null && menu) CardMenuButton(card, actions)
    }
}

/**
 * What a recording is called where no date stands beside it — on its own screen and in the name
 * of the file that is shared (spec 3.21): its own name when it was given one; otherwise a take is
 * named after its piece — «Менуэт · 18 сентября» — and a free recording is «Запись · …». The
 * cards of the lists leave the date out: there it stands once, above them or in their line.
 */
@Composable
fun sessionTitle(title: String?, pieceTitle: String?, startedAtEpochMs: Long, zone: ZoneId): String {
    val date = Formats.dayAndMonth(startedAtEpochMs, zone)
    return title
        ?: pieceTitle?.let { stringResource(R.string.session_take_title, it, date) }
        ?: stringResource(R.string.session_default_title, date)
}
