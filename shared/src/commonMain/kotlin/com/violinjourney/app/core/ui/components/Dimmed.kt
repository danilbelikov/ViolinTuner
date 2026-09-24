package com.violinjourney.app.core.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.clearAndSetSemantics

private const val DIMMED_ALPHA = 0.38f
private const val DIM_MS = 200

/**
 * Steps a part of a screen aside while something else has the floor (the selection mode, spec
 * 3.18): faded and deaf to taps, silent for TalkBack. Only presses and releases are swallowed —
 * moves pass through, so a list still scrolls when the finger starts on the dimmed part.
 */
fun Modifier.dimmedWhen(dimmed: Boolean): Modifier = composed {
    val alpha by animateFloatAsState(if (dimmed) DIMMED_ALPHA else 1f, tween(DIM_MS), label = "dimmed")
    val faded = graphicsLayer { this.alpha = alpha }
    if (!dimmed) {
        faded
    } else {
        faded
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent(PointerEventPass.Initial).changes.forEach { change ->
                            if (change.pressed != change.previousPressed) change.consume()
                        }
                    }
                }
            }
            .clearAndSetSemantics { }
    }
}
