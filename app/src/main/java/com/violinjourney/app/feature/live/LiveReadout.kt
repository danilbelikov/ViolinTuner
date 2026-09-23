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
class LiveReadout(config: IntonationConfig) {
    private val loudness = LoudnessMeter(config)
    private val cents = CentsReadout(config)
    private var soundingMidi: Int? = null
    private var noteSerial = 0

    fun signalOf(frame: PitchFrame, reading: IntonationReading): LiveSignal {
        val level = loudness.process(frame.tMs, frame.rms)
        return when (reading) {
            IntonationReading.Silence -> LiveSignal.Silence.also { noteEnded() }
            IntonationReading.TooNoisy -> LiveSignal.TooNoisy.also { noteEnded() }
            is IntonationReading.Active -> {
                // A note after silence and a change of note both count; the same note held
                // through a pitch gap does not. The screen sends a wave when the count moves,
                // so the event survives conflate() between here and there.
                if (reading.note.midi != soundingMidi) {
                    soundingMidi = reading.note.midi
                    noteSerial++
                }
                LiveSignal.Sounding(
                    note = reading.note,
                    cents = reading.cents,
                    zone = reading.zone,
                    direction = reading.direction,
                    holdProgress = reading.holdProgress,
                    displayCents = cents.update(frame.tMs, reading.note.midi, reading.cents),
                    level = level,
                    noteSerial = noteSerial,
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
