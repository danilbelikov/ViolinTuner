package com.violinjourney.app.feature.repertoire.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.em
import com.violinjourney.app.core.ui.icons.AppIcon
import com.violinjourney.app.core.ui.icons.AppIcons
import com.violinjourney.app.feature.repertoire.MetaPart
import com.violinjourney.app.feature.repertoire.scale.scaleKindLabel
import com.violinjourney.app.shared.resources.Res
import com.violinjourney.app.shared.resources.dot_separator
import com.violinjourney.app.shared.resources.piece_tempo_description
import org.jetbrains.compose.resources.stringResource

private const val METRONOME = "metronome"

/** The metronome of the tempo: as high as the words (13 sp gives 14, spec 5.29 R4), and a gap of 5 after it, in the same scale. */
private val MetronomeSize = 1.08.em
private val MetronomeRoom = 1.46.em
private const val TABULAR_FIGURES = "tnum"

/**
 * The line under the name of an element (spec 3.36.4): the [parts] of [com.violinjourney.app.feature.repertoire.PieceMeta] joined
 * by « · », the tempo as the metronome and the figure — one line, an ellipsis at its end, so the metronome goes with the words and
 * never wraps apart from them. Nothing when there are no parts. TalkBack hears the words ([metaWords]): «И. С. Бах, G-dur, темп 100».
 */
@Composable
fun MetaLine(parts: List<MetaPart>, style: TextStyle, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    if (parts.isEmpty()) return
    val separator = stringResource(Res.string.dot_separator)
    val kinds = parts.map { part -> (part as? MetaPart.Kind)?.let { scaleKindLabel(it.kind) } }
    val text = buildAnnotatedString {
        parts.forEachIndexed { index, part ->
            if (index > 0) append(separator)
            when (part) {
                is MetaPart.Composer -> append(part.name)
                is MetaPart.Key -> append(part.name)
                is MetaPart.Kind -> append(kinds[index].orEmpty())
                is MetaPart.Tempo -> {
                    appendInlineContent(METRONOME)
                    append(part.bpm.toString())
                }
            }
        }
    }
    val inline = mapOf(
        METRONOME to InlineTextContent(Placeholder(MetronomeRoom, MetronomeSize, PlaceholderVerticalAlign.TextCenter)) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) {
                AppIcon(AppIcons.Metronome, contentDescription = null, modifier = Modifier.fillMaxHeight().aspectRatio(1f), tint = color)
            }
        },
    )
    val description = metaWords(parts).joinToString(", ")
    Text(
        text = text,
        modifier = modifier.clearAndSetSemantics { contentDescription = description },
        color = color,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        inlineContent = inline,
        style = style.copy(fontFeatureSettings = TABULAR_FIGURES),
    )
}

/** The [parts] as TalkBack says them, one by one: the tempo as «темп 100» — the metronome is not read. */
@Composable
fun metaWords(parts: List<MetaPart>): List<String> = parts.map { part ->
    when (part) {
        is MetaPart.Composer -> part.name
        is MetaPart.Key -> part.name
        is MetaPart.Kind -> scaleKindLabel(part.kind)
        is MetaPart.Tempo -> stringResource(Res.string.piece_tempo_description, part.bpm)
    }
}
