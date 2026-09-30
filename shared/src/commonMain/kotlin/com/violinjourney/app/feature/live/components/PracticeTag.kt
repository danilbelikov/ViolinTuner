package com.violinjourney.app.feature.live.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import com.violinjourney.app.core.ui.format.Formats
import com.violinjourney.app.core.ui.icons.AppIcons
import com.violinjourney.app.shared.resources.Res
import com.violinjourney.app.shared.resources.practice_chip_label
import com.violinjourney.app.shared.resources.practice_start
import com.violinjourney.app.shared.resources.practice_tag_start
import com.violinjourney.app.shared.resources.practice_timer_description
import org.jetbrains.compose.resources.stringResource

/**
 * The practice tag right of the record key (spec 3.12, 3.36.6): a card of the bottom row ([LiveCard]), [width] wide — the same form
 * and width as «Что играю» on the other side. No practice: glass, the stopwatch, «Начать» and under it «занятие»; a tap starts one
 * here, and the glass turns into paper (300 ms) with the time running from 0:00. A practice runs: paper, the velvet dot, the time in
 * tabular figures and under it «занятие»; a tap leads to «Закончить занятие». It dims with the [light] of the room, in glass and in
 * paper. Not [enabled] — a take is recorded, and leaving Live would end it — it looks the same and tells TalkBack it is not
 * available; the view model answers such a tap with nothing all the same.
 */
@Composable
fun PracticeTag(
    practiceMs: Long?,
    width: Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    reduceMotion: Boolean = false,
    enabled: Boolean = true,
    light: () -> Float = CardWhole,
) {
    // while the paper gives way to the glass the card keeps the face it had, with the last time on it
    val label = stringResource(Res.string.practice_chip_label)
    val stopwatch = remember { CardLead.Icon(AppIcons.Timer) }
    val time = practiceMs?.let(Formats::timer)
    val face = if (time != null) {
        CardFace(paper = true, lead = CardLead.Dot, first = time, second = label, firstGives = LiveCardWords.First.WHOLE, tabular = true)
    } else {
        CardFace(paper = false, lead = stopwatch, first = stringResource(Res.string.practice_tag_start), second = label)
    }
    val description = if (time != null) stringResource(Res.string.practice_timer_description, time) else stringResource(Res.string.practice_start)
    LiveCard(
        face = face,
        width = width,
        description = description,
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        swapMs = LiveMotion.PRACTICE_TAG_FILL_MS,
        reduceMotion = reduceMotion,
        light = light,
    )
}
