package com.violinjourney.app.feature.live

import com.violinjourney.app.core.domain.CentsReadout
import com.violinjourney.app.core.domain.IntonationConfig
import com.violinjourney.app.core.domain.IntonationReading
import com.violinjourney.app.core.domain.LoudnessMeter
import com.violinjourney.app.core.domain.PitchFrame

/**
 * Turns a reading of the engine into what Live shows (spec 3.14, 5.8): adds the loudness the
 * ring breathes with, the calm cents and a count of notes. Lives in the pipeline beside the
 * engine, on its thread, and like the engine takes time from the frames only. One per
 * configuration; not thread-safe.
 */
class LiveReadout(private val config: IntonationConfig) {
    private val loudness = LoudnessMeter(config)
    private val cents = CentsReadout(config)
    private var soundingMidi: Int? = null
    private var noteSerial = 0

    /** What a frame shows: the words of [signal] and the numbers of [gauge] ([LiveGauge] says why they are apart). */
    class Shown(val signal: LiveSignal, val gauge: LiveGauge = LiveGauge())

    fun shownOf(frame: PitchFrame, reading: IntonationReading): Shown {
        // the meter follows every frame, silent ones too, so a note after a rest breathes from where the sound is
        val level = loudness.process(frame.tMs, frame.rms)
        return when (reading) {
            IntonationReading.Silence -> Shown(LiveSignal.Silence).also { noteEnded() }
            IntonationReading.TooNoisy -> Shown(LiveSignal.TooNoisy).also { noteEnded() }
            is IntonationReading.Active -> {
                // A note after silence and a change of note both count; the same note held
                // through a pitch gap does not. The screen sends a wave when the count moves,
                // so the event survives conflate() between here and there.
                if (reading.note.midi != soundingMidi) {
                    soundingMidi = reading.note.midi
                    noteSerial++
                }
                Shown(
                    signal = LiveSignal.Sounding(
                        note = reading.note,
                        zone = reading.zone,
                        direction = reading.direction,
                        displayCents = cents.update(frame.tMs, reading.note.midi, reading.cents),
                        holdComplete = reading.holdProgress >= 1.0,
                        noteSerial = noteSerial,
                    ),
                    gauge = LiveGauge(
                        cents = reading.cents,
                        level = level,
                        glowTarget = LiveReducer.glowTargetOf(reading.zone, reading.holdProgress, config),
                    ),
                )
            }
        }
    }

    /** With the engine: a reopened source starts from nothing. The count goes on — it only ever has to differ. */
    fun reset() {
        loudness.reset()
        noteEnded()
    }

    private fun noteEnded() {
        soundingMidi = null
        cents.reset()
    }
}
