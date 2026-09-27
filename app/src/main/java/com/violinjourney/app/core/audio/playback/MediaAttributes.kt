package com.violinjourney.app.core.audio.playback

import android.media.AudioAttributes

/**
 * What every player of ours says it plays — music, as media — and what Android is asked about when we want to know where
 * media go (`AndroidAudioRoutes`): one value, so the question and the players never disagree.
 */
internal object MediaAttributes {
    val MUSIC: AudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
        .build()
}
