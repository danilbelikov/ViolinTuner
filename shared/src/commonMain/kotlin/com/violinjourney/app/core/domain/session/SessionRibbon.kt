package com.violinjourney.app.core.domain.session

import com.violinjourney.app.core.domain.IntonationConfig
import com.violinjourney.app.core.domain.Zone
import com.violinjourney.app.core.domain.ZoneClassifier

/**
 * The mini ribbon of a stored recording (spec 3.9): the rule of [SessionRecorder]'s ribbon — a piece is a run of one note in
 * one zone, buckets without a note take no room — applied to the samples the recording keeps. The summary of «Видео с нотами»
 * draws it (spec 3.37), so the ribbon a player saw while recording is the one the video ends with. Nothing is joined: the
 * ribbon is drawn once, and pieces narrower than a pixel only blend.
 */
object SessionRibbon {
    fun of(samples: List<SessionSample?>, config: IntonationConfig): List<RecordingBar> {
        val pieces = ArrayList<RecordingBar>()
        var midi = NO_NOTE
        var zone = Zone.IN_TUNE
        var count = 0
        for (sample in samples) {
            if (sample == null) {
                if (count > 0) pieces += RecordingBar(count, zone)
                count = 0
                midi = NO_NOTE
                continue
            }
            val sampleZone = ZoneClassifier.classify(sample.cents, config)
            if (count > 0 && (sample.midi != midi || sampleZone != zone)) {
                pieces += RecordingBar(count, zone)
                count = 0
            }
            if (count == 0) {
                midi = sample.midi
                zone = sampleZone
            }
            count++
        }
        if (count > 0) pieces += RecordingBar(count, zone)
        return pieces
    }

    private const val NO_NOTE = -1
}
