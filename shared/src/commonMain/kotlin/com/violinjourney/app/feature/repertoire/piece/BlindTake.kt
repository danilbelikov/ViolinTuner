package com.violinjourney.app.feature.repertoire.piece

import com.violinjourney.app.core.domain.IntonationConfig
import com.violinjourney.app.core.domain.IntonationReading
import com.violinjourney.app.core.domain.LoudnessMeter
import com.violinjourney.app.core.domain.TargetMode
import com.violinjourney.app.core.domain.repertoire.RepertoireConfig
import com.violinjourney.app.core.recording.TakeOwner
import com.violinjourney.app.core.recording.TakePipeline
import kotlinx.coroutines.flow.Flow

/**
 * A recording made blind (spec 3.15, plan D14): a take of a piece, and a recording of an event («Записать звук», spec 3.35, 3.36.9) —
 * one chain for both. Of all the engine reads, only «слишком шумно» reaches the screen, beside how loud it is; no note, no zone, no
 * cents. Whether it may record — the wish, the microphone — stays with each screen.
 */
object BlindTake {
    /** The chain of [takes] for [intonation], its sessions [owner]'s; the levels — [config]'s bars. */
    fun chain(takes: TakePipeline, intonation: IntonationConfig, owner: TakeOwner, config: RepertoireConfig): Flow<TakePipeline.Output<BlindShown>> {
        val meter = LoudnessMeter(intonation)
        val history = LevelHistory(config.levelBars, config.levelBarMs)
        val silent = List(config.levelBars) { 0f }
        return takes.run(
            config = intonation,
            owner = owner,
            targetMode = { TargetMode.Chromatic },
            // a lost microphone ends the take quietly (spec 3.15) and the bar goes: nothing is said under it (spec 3.36.4)
            unavailable = BlindShown(silent, problem = null),
            onRestart = {
                meter.reset()
                history.reset()
            },
        ) { frame, reading ->
            BlindShown(
                levels = history.add(frame.tMs, meter.process(frame.tMs, frame.rms)),
                problem = TakeProblem.TOO_NOISY.takeIf { reading == IntonationReading.TooNoisy },
            )
        }
    }
}
