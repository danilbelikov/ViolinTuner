package com.violinjourney.app.core.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.violinjourney.app.core.ui.icons.AppIcon
import com.violinjourney.app.core.ui.icons.AppIcons

// The look of the error of a field (spec 5.29 R1, AppField): the icon of 15 in the second level, 13 sp.
private val ReasonIcon = 15.dp
private val ReasonIconGap = 5.dp
private val LineHeight = 18.sp

/** Where the icon of 15 stands by the first line of 18: in its middle. */
private val IconTop = 1.5.dp

/**
 * Why something does not answer, in a line (spec 3.36.1, 5.29 R1 and R4): the icon «i» of 15 and the words of 13 sp, both in the
 * second level of text — the look of the error of a field ([AppField]): grey, never red, for a choice that cannot be made is no
 * danger. It stands where the thing it explains is: over a sleeping button of the bottom zone («Без названия не сохранить», [centered]),
 * under a switch whose segments sleep («Сначала выберите тонику», «Три октавы от F4 не помещаются на скрипке»). The words go on as
 * many lines as they take, the icon by the first; said aloud when the line appears.
 */
@Composable
fun ReasonLine(text: String, modifier: Modifier = Modifier, centered: Boolean = false) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = modifier.semantics { liveRegion = LiveRegionMode.Polite },
        horizontalArrangement = Arrangement.spacedBy(ReasonIconGap, if (centered) Alignment.CenterHorizontally else Alignment.Start),
        verticalAlignment = Alignment.Top,
    ) {
        AppIcon(AppIcons.Info, contentDescription = null, modifier = Modifier.padding(top = IconTop), tint = colors.onSurfaceVariant, size = ReasonIcon)
        Text(
            text = text,
            // the words take what the icon leaves; centred, the pair stands in the middle
            modifier = Modifier.weight(1f, fill = false),
            color = colors.onSurfaceVariant,
            textAlign = if (centered) TextAlign.Center else TextAlign.Start,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = LineHeight),
        )
    }
}
