package com.violinjourney.app.feature.live.block

import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import com.violinjourney.app.core.domain.practice.PracticeConfig.Companion.MS_PER_MINUTE
import com.violinjourney.app.core.ui.format.Formats
import com.violinjourney.app.core.ui.icons.AppIcons
import com.violinjourney.app.feature.live.components.CardFace
import com.violinjourney.app.feature.live.components.CardLead
import com.violinjourney.app.feature.live.components.CardWhole
import com.violinjourney.app.feature.live.components.LiveCard
import com.violinjourney.app.feature.live.components.LiveCardWords
import com.violinjourney.app.feature.live.components.LiveMotion
import com.violinjourney.app.shared.resources.Res
import com.violinjourney.app.shared.resources.block_done
import com.violinjourney.app.shared.resources.block_done_description
import com.violinjourney.app.shared.resources.block_entry
import com.violinjourney.app.shared.resources.block_entry_description
import com.violinjourney.app.shared.resources.block_entry_hint
import com.violinjourney.app.shared.resources.block_left_few
import com.violinjourney.app.shared.resources.block_left_many
import com.violinjourney.app.shared.resources.block_left_one
import com.violinjourney.app.shared.resources.block_running_description
import org.jetbrains.compose.resources.stringResource

/**
 * «Что играю» left of the record key (spec 3.28, 3.36.6): a card of the bottom row ([LiveCard]), [width] wide. No block — glass: the
 * sheet of music of the tab «Репертуар», «Что играю» and under it «выбрать»; a block runs — paper: the same sheet, the name of the
 * element in one line ending in «…», «ещё 7 мин» and the brass line along the bottom, drawing on to the goal; the goal reached — the
 * line runs to its end (240 ms), then the brass rim closes round the paper, the check takes the place of the sheet and «готово» the
 * place of the minutes (400 ms after 80). «Готово» stays whole in the dark — the end comes while the violin sounds, and it is for the
 * corner of the eye (spec 3.36.6) — while the rest of the card dims with the [light] of the room. The paper that drains into the glass
 * (a block stopped, the practice over) keeps the line and the «готово» of the last block it showed ([BookmarkPaper]).
 */
@Composable
fun BlockBookmark(
    bookmark: Bookmark,
    width: Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    reduceMotion: Boolean = false,
    light: () -> Float = CardWhole,
) {
    // while the paper drains into the glass the state is already «Что играю»: the paper keeps what the last block showed
    var lastFilled by remember { mutableStateOf<Bookmark>(bookmark.takeUnless { it is Bookmark.Entry } ?: Bookmark.Done("")) }
    if (bookmark !is Bookmark.Entry) SideEffect { lastFilled = bookmark }
    val shown = BookmarkPaper.shown(bookmark, lastFilled)
    val done = shown is Bookmark.Done
    // The line follows the clock without being animated — a tick a second moves it by a fraction of a dp; the goal draws it to the
    // end (240 ms), then the rim, the check and «готово» come in (400 ms, after 80).
    val progress = animateFloatAsState(
        targetValue = BookmarkPaper.progress(shown),
        animationSpec = if (done && !reduceMotion) tween(LiveMotion.BOOKMARK_FILL_MS, easing = EaseOut) else snap(),
        label = "bookmarkProgress",
    )
    val doneness = animateFloatAsState(
        targetValue = BookmarkPaper.done(shown),
        // a new block after a done one starts clean at once; only the arrival at the goal is shown
        animationSpec = if (reduceMotion || !done) snap() else tween(LiveMotion.BOOKMARK_DONE_MS, delayMillis = LiveMotion.BOOKMARK_DONE_DELAY_MS, easing = EaseInOut),
        label = "bookmarkDone",
    )
    val left = (bookmark as? Bookmark.Running)?.minutesLeft ?: 0
    // while «ещё 1 мин» fades into «готово» the block is done already: keep the last words it had
    var lastLeft by remember { mutableStateOf("") }
    val leftNow = if (left > 0) stringResource(Formats.plural(left, Res.string.block_left_one, Res.string.block_left_few, Res.string.block_left_many), left) else ""
    if (leftNow.isNotEmpty()) SideEffect { lastLeft = leftNow }
    val sheet = remember { CardLead.Icon(AppIcons.TabRepertoire.normal) }
    val face = when (bookmark) {
        Bookmark.Entry -> CardFace(
            paper = false,
            lead = sheet,
            first = stringResource(Res.string.block_entry),
            second = stringResource(Res.string.block_entry_hint),
        )
        is Bookmark.Running -> filledFace(sheet, bookmark.title, leftNow.ifEmpty { lastLeft }, stringResource(Res.string.block_done))
        is Bookmark.Done -> filledFace(sheet, bookmark.title, lastLeft, stringResource(Res.string.block_done))
    }
    val description = when (bookmark) {
        Bookmark.Entry -> stringResource(Res.string.block_entry_description)
        is Bookmark.Running -> stringResource(Res.string.block_running_description, bookmark.title, Formats.minutesInWords(left * MS_PER_MINUTE))
        is Bookmark.Done -> stringResource(Res.string.block_done_description, bookmark.title)
    }
    // the card dims with the light; its paper comes up to its whole as «готово» comes in, and stays so (LiveCard, CardLight)
    val doneOf = remember(doneness) { { doneness.value } }
    val progressOf = remember(progress) { { progress.value } }
    LiveCard(
        face = face,
        width = width,
        description = description,
        onClick = onClick,
        modifier = modifier,
        swapMs = LiveMotion.BOOKMARK_SWAP_MS,
        reduceMotion = reduceMotion,
        light = light,
        done = doneOf,
        progress = progressOf,
    )
}

/**
 * What the paper of «Что играю» shows (spec 3.28, 3.36.6, 5.21): the running or done block — or, while the paper drains into the glass
 * (the block stopped, the practice over), the [last] block it showed, its line where it stood and its «готово» with it, not a full line
 * for the 300 ms of the cross-fade. A new block after a done one starts clean: it is shown itself.
 */
internal object BookmarkPaper {
    /** The block the paper shows: [bookmark], or the [last] one where there is none any more. */
    fun shown(bookmark: Bookmark, last: Bookmark): Bookmark = if (bookmark is Bookmark.Entry) last else bookmark

    /** How far the brass line of [shown] reaches: the progress of a running block, to the end at «готово». */
    fun progress(shown: Bookmark): Float = if (shown is Bookmark.Running) shown.progress else 1f

    /** How far [shown] is «готово»: 1 or 0 — the rim, the check and the word come in on their way to it. */
    fun done(shown: Bookmark): Float = if (shown is Bookmark.Done) 1f else 0f
}

/** The paper of a block: the sheet, the name in one line with «…», the minutes left under it and «готово» ready over them. */
private fun filledFace(sheet: CardLead, title: String, left: String, doneWord: String) = CardFace(
    paper = true,
    lead = sheet,
    first = title,
    second = left,
    firstGives = LiveCardWords.First.ELLIPSIS,
    doneSecond = doneWord,
)
