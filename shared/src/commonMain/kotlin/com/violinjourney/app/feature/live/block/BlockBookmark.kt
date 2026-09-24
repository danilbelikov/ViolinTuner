package com.violinjourney.app.feature.live.block

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.violinjourney.app.core.domain.practice.PracticeConfig.Companion.MS_PER_MINUTE
import com.violinjourney.app.core.ui.format.Formats
import com.violinjourney.app.core.ui.icons.AppIcon
import com.violinjourney.app.core.ui.icons.AppIcons
import com.violinjourney.app.core.ui.theme.ViolinTheme
import com.violinjourney.app.feature.live.components.LiveDimens
import com.violinjourney.app.feature.live.components.LiveMotion
import com.violinjourney.app.shared.resources.Res
import com.violinjourney.app.shared.resources.block_done
import com.violinjourney.app.shared.resources.block_done_description
import com.violinjourney.app.shared.resources.block_entry
import com.violinjourney.app.shared.resources.block_entry_description
import com.violinjourney.app.shared.resources.block_left_few
import com.violinjourney.app.shared.resources.block_left_many
import com.violinjourney.app.shared.resources.block_left_one
import com.violinjourney.app.shared.resources.block_running_description
import org.jetbrains.compose.resources.stringResource

/**
 * The bookmark by the record key (spec 3.28, handoff 30a2, 30b, 30d): the paper of the practice tag
 * cut like a bookmark in sheet music, its right edge forked, its shadow the same outline set down —
 * no blur. «Репертуар» is the outline alone; a running block is the paper with a line of brass along
 * its lower edge; a done one is closed by a brass rim, «готово» and a tick in ink. [width] is what the
 * row can give a filled bookmark; the outline takes the width of its word.
 */
@Composable
fun BlockBookmark(
    bookmark: Bookmark,
    width: Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    reduceMotion: Boolean = false,
) {
    // while the paper fades into the outline the state is already «Репертуар»: keep showing what was on the paper
    var lastFilled by remember { mutableStateOf<Bookmark>(Bookmark.Done("")) }
    if (bookmark !is Bookmark.Entry) SideEffect { lastFilled = bookmark }
    Crossfade(
        targetState = bookmark is Bookmark.Entry,
        modifier = modifier,
        animationSpec = tween(if (reduceMotion) 0 else LiveMotion.BOOKMARK_SWAP_MS),
        label = "bookmark",
    ) { entry ->
        if (entry) {
            EntryBookmark(onClick)
        } else {
            FilledBookmark(if (bookmark is Bookmark.Entry) lastFilled else bookmark, width, onClick, reduceMotion)
        }
    }
}

@Composable
private fun EntryBookmark(onClick: () -> Unit) {
    val paper = ViolinTheme.venueColors.bone
    val label = stringResource(Res.string.block_entry)
    val description = stringResource(Res.string.block_entry_description)
    val style = entryStyle()
    // the word stands whole in every language: the outline is as wide as it needs, never narrower than the handoff's
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val textWidth = with(density) { measurer.measure(label, style, maxLines = 1).size.width.toDp() }
    val width = maxOf(LiveDimens.BookmarkEntryMinWidth, textWidth + LiveDimens.BookmarkTextStart + LiveDimens.BookmarkTextEnd)
    Box(
        modifier = Modifier
            .size(width, LiveDimens.BookmarkHeight + LiveDimens.BookmarkShadowRoom)
            .drawBehind {
                val outline = bookmarkPath(Size(size.width, LiveDimens.BookmarkHeight.toPx()), LiveDimens.BookmarkNotch.toPx(), LiveDimens.BookmarkCorner.toPx())
                shadow(outline)
                drawPath(outline, paper.copy(alpha = EDGE_ALPHA), style = Stroke(LiveDimens.BookmarkEdge.toPx()))
            }
            .clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = description },
    ) {
        Text(
            text = label,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = LiveDimens.BookmarkTextStart, bottom = LiveDimens.BookmarkShadowRoom),
            color = paper,
            maxLines = 1,
            style = style,
        )
    }
}

@Composable
private fun FilledBookmark(bookmark: Bookmark, width: Dp, onClick: () -> Unit, reduceMotion: Boolean) {
    val colors = ViolinTheme.venueColors
    val done = bookmark is Bookmark.Done
    val title = when (bookmark) {
        is Bookmark.Running -> bookmark.title
        is Bookmark.Done -> bookmark.title
        Bookmark.Entry -> ""
    }
    // The line follows the clock without being animated — a tick a second moves it by a fraction of a dp; the
    // goal draws it to the edge (handoff 30d: 240 ms), then the rim and «готово» come in (400 ms, after 80).
    val progress by animateFloatAsState(
        targetValue = if (bookmark is Bookmark.Running) bookmark.progress else 1f,
        animationSpec = if (done && !reduceMotion) tween(LiveMotion.BOOKMARK_FILL_MS, easing = EaseOut) else snap(),
        label = "bookmarkProgress",
    )
    val doneness by animateFloatAsState(
        targetValue = if (done) 1f else 0f,
        // a new block after a done one starts clean at once; only the arrival at the goal is shown
        animationSpec = if (reduceMotion || !done) snap() else tween(LiveMotion.BOOKMARK_DONE_MS, delayMillis = LiveMotion.BOOKMARK_DONE_DELAY_MS, easing = EaseInOut),
        label = "bookmarkDone",
    )
    val left = (bookmark as? Bookmark.Running)?.minutesLeft ?: 0
    // while «ещё 1 мин» fades into «готово» the block is done already: keep the last words it had
    var lastLeft by remember { mutableStateOf("") }
    val leftNow = if (left > 0) stringResource(Formats.plural(left, Res.string.block_left_one, Res.string.block_left_few, Res.string.block_left_many), left) else ""
    if (leftNow.isNotEmpty()) SideEffect { lastLeft = leftNow }
    val leftText = leftNow.ifEmpty { lastLeft }
    val description = if (done) {
        stringResource(Res.string.block_done_description, title)
    } else {
        stringResource(Res.string.block_running_description, title, Formats.minutesInWords(left * MS_PER_MINUTE))
    }
    Box(
        modifier = Modifier
            .size(width, LiveDimens.BookmarkHeight + LiveDimens.BookmarkShadowRoom)
            .drawBehind {
                val height = LiveDimens.BookmarkHeight.toPx()
                val outline = bookmarkPath(Size(size.width, height), LiveDimens.BookmarkNotch.toPx(), LiveDimens.BookmarkCorner.toPx())
                shadow(outline)
                drawPath(outline, colors.bone)
                val y = height - LiveDimens.BookmarkLineFromBottom.toPx()
                val start = LiveDimens.BookmarkLineStart.toPx()
                val end = size.width - LiveDimens.BookmarkLineEnd.toPx()
                val stroke = LiveDimens.BookmarkLine.toPx()
                drawLine(colors.ink.copy(alpha = TRACK_ALPHA), Offset(start, y), Offset(end, y), stroke, StrokeCap.Round)
                if (progress > 0f) drawLine(colors.brass, Offset(start, y), Offset(start + (end - start) * progress, y), stroke, StrokeCap.Round)
                if (doneness > 0f) drawPath(outline, colors.brass.copy(alpha = doneness), style = Stroke(LiveDimens.BookmarkRim.toPx()))
            }
            .clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = description },
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = LiveDimens.BookmarkTextStart, end = LiveDimens.BookmarkTextEnd, bottom = LiveDimens.BookmarkShadowRoom),
            verticalArrangement = Arrangement.spacedBy(LiveDimens.BookmarkTextGap, Alignment.CenterVertically),
        ) {
            Text(
                text = title,
                color = colors.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, lineHeight = 14.sp),
            )
            Box {
                // «ещё 7 мин» gives way to the tick and «готово»: one cross-fade, no change of size
                Text(
                    text = leftText,
                    modifier = Modifier.graphicsLayer { alpha = 1f - doneness },
                    color = colors.ink,
                    maxLines = 1,
                    style = timeStyle(),
                )
                Row(
                    modifier = Modifier.graphicsLayer { alpha = doneness },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(LiveDimens.BookmarkTickGap),
                ) {
                    AppIcon(AppIcons.Check, contentDescription = null, size = LiveDimens.BookmarkTick, tint = colors.ink)
                    Text(text = stringResource(Res.string.block_done), color = colors.ink, maxLines = 1, style = timeStyle())
                }
            }
        }
    }
}

@Composable
private fun entryStyle(): TextStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)

@Composable
private fun timeStyle(): TextStyle =
    MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold, lineHeight = 17.sp, fontFeatureSettings = TABULAR_FIGURES)

/** The shadow the paper lies on: the same outline, set down, without blur (handoff `take.shadow`). */
private fun DrawScope.shadow(outline: Path) {
    translate(top = LiveDimens.BookmarkShadow.toPx()) { drawPath(outline, Color.Black.copy(alpha = SHADOW_ALPHA)) }
}

/**
 * The handoff's `bmPath(w, h)`: `M6 0 H w L w−15 h/2 L w h H6 Q0 h 0 h−6 V6 Q0 0 6 0 Z` — the left corners rounded,
 * the right edge forked by a notch.
 */
internal fun bookmarkPath(size: Size, notch: Float, corner: Float): Path = Path().apply {
    val w = size.width
    val h = size.height
    moveTo(corner, 0f)
    lineTo(w, 0f)
    lineTo(w - notch, h / 2f)
    lineTo(w, h)
    lineTo(corner, h)
    quadraticTo(0f, h, 0f, h - corner)
    lineTo(0f, corner)
    quadraticTo(0f, 0f, corner, 0f)
    close()
}

private const val TABULAR_FIGURES = "tnum"
private const val EDGE_ALPHA = 0.5f
private const val TRACK_ALPHA = 0.15f
private const val SHADOW_ALPHA = 0.30f
