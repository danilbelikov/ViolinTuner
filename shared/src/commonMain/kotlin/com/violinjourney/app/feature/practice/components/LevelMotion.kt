package com.violinjourney.app.feature.practice.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.violinjourney.app.core.ui.motion.LocalReduceMotion
import com.violinjourney.app.shared.resources.Res
import com.violinjourney.app.shared.resources.progress_level_names
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringArrayResource

/**
 * How far the level has come as it is shown — the arc of the ring in the path row, the bar of «Мой путь» (spec 3.36.2): [filled]
 * of the level (read while drawing), the level named by the number in the ring, [shownLevel], and the words that go with it,
 * [shownCaptions]. They move together: when a level is passed the arc runs to its end, rests, and only then the new level is
 * named and the arc starts it from nothing. [moving] covers the whole of it — the pause on a full arc too, when [filled] stands
 * still: the shine of the bar gives way to all of it (spec 3.16).
 */
@Stable
class LevelProgress<T> internal constructor(
    val filled: Animatable<Float, AnimationVector1D>,
    level: Int,
    captions: T,
) {
    var shownLevel by mutableIntStateOf(level)
        internal set
    var shownCaptions: T by mutableStateOf(captions)
        internal set
    var moving by mutableStateOf(false)
        internal set
}

/**
 * The motion of the level (spec 3.36.2, 5.29 «Время»): growth within a level over 600 ms; a level passed — to the end in 400,
 * a pause of 200, the new number, growth from nothing in 400; a level down — at once ([LevelMotionMath]). Motion is for what changes before the user's eyes: what is
 * already true when it appears — the first data after loading, a return to the tab — is shown as it is ([animate] false while
 * only the place is held, and the first change after it is taken still); someone with four hundred hours must not watch the ring
 * climb from level one on every visit. With «убрать анимации» — at once.
 */
@Composable
fun <T> rememberLevelProgress(level: Int, fraction: Float, captions: T, animate: Boolean): LevelProgress<T> {
    val target = fraction.coerceIn(0f, 1f)
    val progress = remember { LevelProgress(Animatable(target), level, captions) }
    val still = LocalReduceMotion.current
    // armed a composition after [animate] turns true: the change that comes with the first data is not motion
    var armed by remember { mutableStateOf(false) }
    val motion = armed && !still
    LaunchedEffect(animate) { armed = animate }
    LaunchedEffect(level, target, captions) {
        progress.moving = true
        try {
            when (LevelMotionMath.step(progress.shownLevel, level, motion)) {
                LevelStep.AtOnce -> {
                    // the number and the words with the arc, never one without the other
                    progress.shownLevel = level
                    progress.shownCaptions = captions
                    progress.filled.snapTo(target)
                }
                LevelStep.LevelUp -> {
                    progress.filled.animateTo(1f, tween(ProgressMotion.BAR_LEVEL_UP_FILL_MS, easing = FastOutSlowInEasing))
                    delay(ProgressMotion.BAR_LEVEL_UP_PAUSE_MS)
                    progress.filled.snapTo(0f)
                    progress.shownLevel = level
                    progress.shownCaptions = captions
                    progress.filled.animateTo(target, tween(ProgressMotion.BAR_LEVEL_UP_GROW_MS, easing = FastOutSlowInEasing))
                }
                LevelStep.Grow -> {
                    // the same level: its number stays, the words of what is left change at once
                    progress.shownCaptions = captions
                    progress.filled.animateTo(target, tween(ProgressMotion.BAR_GROW_MS, easing = FastOutSlowInEasing))
                }
            }
            progress.shownLevel = level
            progress.shownCaptions = captions
        } finally {
            progress.moving = false
        }
    }
    return progress
}

/** How the shown level goes to the new one. */
internal enum class LevelStep {
    /** Number, words and arc together, without motion. */
    AtOnce,

    /** Within the level: the arc over 600 ms, the number stays. */
    Grow,

    /** A level passed: the arc to its end, a pause, then the new number and words, and the arc from nothing. */
    LevelUp,
}

/** The choice of [LevelStep], pure, with a test. */
internal object LevelMotionMath {
    /**
     * Without [motion] — at once. A level down — a day's time edited to less (spec 3.13: the level is worked out honestly and may
     * drop) — at once too: it is a correction, not growth, and the ring moves only as it grows (5.29 «Время»); the number in the
     * ring and the words under it must never tell two levels. A level up passes the end of the arc; the same level grows.
     */
    fun step(shownLevel: Int, level: Int, motion: Boolean): LevelStep = when {
        !motion || level < shownLevel -> LevelStep.AtOnce
        level > shownLevel -> LevelStep.LevelUp
        else -> LevelStep.Grow
    }
}

/** The name of a level, «Гаммы», by its number from 1. */
@Composable
internal fun levelName(level: Int): String = stringArrayResource(Res.array.progress_level_names).getOrElse(level - 1) { "" }
