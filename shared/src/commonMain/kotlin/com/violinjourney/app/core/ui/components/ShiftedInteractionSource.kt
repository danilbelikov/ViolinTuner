package com.violinjourney.app.core.ui.components

import androidx.compose.foundation.interaction.Interaction
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.ui.geometry.Offset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * The presses of a whole touch target as seen by a smaller place inside it that draws the ripple — the pill of a tab, the
 * rounded place of the compact bar, the pill of a segment — as `NavigationBarItem` of Material maps them: the target reports
 * a press in its own coordinates, the place gets it moved by [shift], where the place lies in the target (set when it is
 * placed), so the ripple starts under the finger. A release or a cancel follows its press, moved the same way.
 */
internal class ShiftedInteractionSource(source: InteractionSource) : InteractionSource {
    var shift: Offset = Offset.Zero
    private val moved = mutableMapOf<PressInteraction.Press, PressInteraction.Press>()

    override val interactions: Flow<Interaction> = source.interactions.map { interaction ->
        when (interaction) {
            is PressInteraction.Press -> PressInteraction.Press(interaction.pressPosition - shift).also { moved[interaction] = it }
            is PressInteraction.Release -> moved.remove(interaction.press)?.let { PressInteraction.Release(it) } ?: interaction
            is PressInteraction.Cancel -> moved.remove(interaction.press)?.let { PressInteraction.Cancel(it) } ?: interaction
            else -> interaction
        }
    }
}
