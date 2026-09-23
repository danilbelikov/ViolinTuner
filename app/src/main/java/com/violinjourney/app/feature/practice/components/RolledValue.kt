package com.violinjourney.app.feature.practice.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import com.violinjourney.app.core.ui.motion.LocalReduceMotion
import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * A number of the summary that rolls to its new value through the ones in between (spec 3.16)
 * instead of jumping. Only a change before the eyes rolls: the first value is taken as it is,
 * and a new [scope] (another month in the calendar) starts afresh — those are other numbers,
 * not the same one grown. A change under [rollFrom] is simply shown.
 */
@Composable
fun rolledValue(target: Long, scope: Any?, durationMs: Int, rollFrom: Long): Long {
    val still = LocalReduceMotion.current
    val shown = remember(scope) { Animatable(target.toFloat()) }
    LaunchedEffect(target, scope) {
        if (still || abs(target - shown.value) < rollFrom) {
            shown.snapTo(target.toFloat())
        } else {
            shown.animateTo(target.toFloat(), tween(durationMs, easing = FastOutSlowInEasing))
        }
    }
    return shown.value.roundToLong()
}
