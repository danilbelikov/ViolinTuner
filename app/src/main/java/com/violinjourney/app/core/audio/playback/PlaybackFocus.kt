package com.violinjourney.app.core.audio.playback

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * The sound of a player of ours as one of the phone's (spec 3.10): while it plays, other apps' music stops, and a call,
 * another player or pulled-out headphones pause it — it never falls through to the speaker. Main thread only. One per
 * player: each holds its own request.
 */
interface PlaybackFocus {
    /**
     * Asks for the sound; [onLost] runs, on the main thread, when a call, another player or pulled-out headphones take
     * it away. False — refused (a call is on): the player stays quiet.
     */
    fun take(onLost: () -> Unit): Boolean

    /** Gives the sound back; nothing when it is not held. */
    fun give()

    companion object {
        /** For tests and players nobody hears: always granted, never lost. */
        val None: PlaybackFocus = object : PlaybackFocus {
            override fun take(onLost: () -> Unit) = true

            override fun give() = Unit
        }
    }
}

/**
 * [PlaybackFocus] by `AudioManager`: `AUDIOFOCUS_GAIN`, as a music player asks — others stop rather than wait for us, as
 * a take is listened to again and again, paused and played. After a loss the player waits for its button; nothing
 * resumes by itself. Ducking is left to the system. The backing played into a take asks for nothing: nothing may
 * silence it mid-take.
 */
class AndroidPlaybackFocus @Inject constructor(@ApplicationContext private val context: Context) : PlaybackFocus {
    private val audio = context.getSystemService(AudioManager::class.java)
    private var request: AudioFocusRequest? = null
    private var onLost: (() -> Unit)? = null

    /** Headphones pulled out: Android says so before the sound moves to the speaker. */
    private val noisy = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) lose()
        }
    }

    override fun take(onLost: () -> Unit): Boolean {
        this.onLost = onLost
        if (request != null) return true
        val asked = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(MediaAttributes.MUSIC)
            .setOnAudioFocusChangeListener({ change ->
                if (change == AudioManager.AUDIOFOCUS_LOSS || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) lose()
            }, Handler(Looper.getMainLooper()))
            .build()
        if (audio.requestAudioFocus(asked) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            this.onLost = null
            return false
        }
        request = asked
        ContextCompat.registerReceiver(context, noisy, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY), ContextCompat.RECEIVER_NOT_EXPORTED)
        return true
    }

    override fun give() {
        val held = request ?: return
        request = null
        onLost = null
        audio.abandonAudioFocusRequest(held)
        context.unregisterReceiver(noisy)
    }

    private fun lose() {
        val call = onLost
        give()
        call?.invoke()
    }
}
