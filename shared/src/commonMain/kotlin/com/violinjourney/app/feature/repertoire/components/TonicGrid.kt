package com.violinjourney.app.feature.repertoire.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.coerceAtMost
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.violinjourney.app.core.domain.repertoire.Tonic
import com.violinjourney.app.core.ui.theme.AppShapes

private const val DISABLED_ALPHA = 0.38f
private const val LETTER_SIZE = 16

/**
 * How the seven tonics stand (spec 3.36.4, 5.29 R4): in one row where each gets 48 dp at the least — a row no narrower than
 * [OneRowFrom], 7 × 48 and six gaps of [MinGap]. The gaps of the row are [Gap] where it has room for them and give way down to
 * [MinGap] before a button goes under 48: a phone of 411 dp leaves the sheet «Тональность» 371 inside its fields, and its seven stand
 * in one row there as on the screen of 412 they are drawn for. Below [OneRowFrom] in two rows, «C D E F» over «G A B», [Gap] apart,
 * every button as wide as those of the first row, so that none is pressed over less than 48 (portrait 360). Pure.
 */
internal object TonicRows {
    val Cell = 48.dp
    val Gap = 6.dp

    /** The least gap of one row: a row narrower than 7 × 48 with these goes on two rows. */
    val MinGap = 4.dp

    /** The tonics of the first of two rows. */
    const val FIRST_ROW = 4

    private val count = Tonic.entries.size

    val OneRowFrom: Dp = Cell * count + MinGap * (count - 1)

    fun of(width: Dp): List<List<Tonic>> =
        if (width >= OneRowFrom) listOf(Tonic.entries.toList()) else listOf(Tonic.entries.take(FIRST_ROW), Tonic.entries.drop(FIRST_ROW))

    /** Between the buttons of a row: [Gap]; in one row only as much of it as seven buttons of 48 leave — never less than [MinGap]. */
    fun gap(width: Dp): Dp = if (of(width).size == 1) ((width - Cell * count) / (count - 1)).coerceAtMost(Gap) else Gap

    /** The width of each button: the first row shares the [width], the second takes the same. */
    fun cellWidth(width: Dp): Dp {
        val columns = of(width).first().size
        return (width - gap(width) * (columns - 1)) / columns
    }
}

/**
 * The seven tonics as buttons of 48 at a corner of 12 (spec 3.36.4, 5.29 R4): the sheet «Тональность» of a piece ([inSheet] — on
 * surfaceContainerHigh, the ground of a sheet being surfaceContainer) and the form of a scale (on surfaceContainer); the chosen one in
 * primaryContainer. [label] — the letter shown: B is «H» in the form of a scale (5.9). A tonic that cannot be picked ([enabled] false:
 * eight signs, or a scale that exists) is dimmed to 0.38 and not pressed; the reason is its caller's line under it. TalkBack reads each
 * by its letter, a radio button chosen or not.
 */
@Composable
internal fun TonicGrid(
    selected: Tonic?,
    onClick: (Tonic) -> Unit,
    modifier: Modifier = Modifier,
    inSheet: Boolean = false,
    enabled: (Tonic) -> Boolean = { true },
    label: (Tonic) -> String = { it.name },
) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val rows = TonicRows.of(maxWidth)
        val cell = TonicRows.cellWidth(maxWidth)
        // One row: the buttons at its ends and what they leave between them — the gaps of TonicRows, without a pixel of each
        // rounded up and added up past the edge. Two rows: 6 apart from the left, the second under the first three.
        val across = if (rows.size == 1) Arrangement.SpaceBetween else Arrangement.spacedBy(TonicRows.Gap)
        Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(TonicRows.Gap)) {
            rows.forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = across) {
                    row.forEach { tonic ->
                        TonicButton(label(tonic), picked = tonic == selected, enabled = enabled(tonic), inSheet = inSheet, modifier = Modifier.width(cell)) {
                            onClick(tonic)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TonicButton(letter: String, picked: Boolean, enabled: Boolean, inSheet: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val ground = when {
        picked -> colors.primaryContainer
        inSheet -> colors.surfaceContainerHigh
        else -> colors.surfaceContainer
    }
    Box(
        modifier = modifier
            .height(TonicRows.Cell)
            .then(if (enabled) Modifier else Modifier.alpha(DISABLED_ALPHA))
            .clip(AppShapes.S)
            .background(ground)
            .selectable(selected = picked, enabled = enabled, role = Role.RadioButton, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = letter,
            color = if (picked) colors.onPrimaryContainer else colors.onSurface,
            style = MaterialTheme.typography.titleMedium.copy(fontSize = LETTER_SIZE.sp, fontWeight = FontWeight.ExtraBold),
        )
    }
}
