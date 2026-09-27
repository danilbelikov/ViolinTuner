package com.violinjourney.app.core.audio.backing

import android.media.MediaPlayer
import android.util.Log
import com.violinjourney.app.core.audio.playback.MediaAttributes
import com.violinjourney.app.core.audio.playback.PlaybackFocus
import java.io.File
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Listening to the backing on the piece screen (spec 3.32) with `MediaPlayer`, prepared off the main thread. It holds the
 * phone's sound like the player of takes ([PlaybackFocus]): a call, another player or pulled-out headphones stop it.
 * Main thread only; its callbacks come there too.
 */
class MediaBackingPreview @Inject constructor(private val focus: PlaybackFocus) : BackingPreview {
    private val mutablePlaying = MutableStateFlow(false)
    override val playing: StateFlow<Boolean> = mutablePlaying.asStateFlow()
    private var player: MediaPlayer? = null

    override fun toggle(file: File) {
        if (player != null) {
            stop()
            return
        }
        // refused while a call is on: the button stays «play»
        if (!focus.take(onLost = ::stop)) return
        val created = MediaPlayer()
        player = created
        // the button shows «stop» at once, while the file is prepared
        mutablePlaying.value = true
        try {
            created.setAudioAttributes(MediaAttributes.MUSIC)
            created.setDataSource(file.absolutePath)
            created.setOnPreparedListener { if (player === it) it.start() }
            created.setOnCompletionListener { if (player === it) stop() }
            created.setOnErrorListener { failed, what, extra ->
                Log.w(TAG, "cannot play ${file.name}: $what/$extra")
                if (player === failed) stop()
                true
            }
            created.prepareAsync()
        } catch (e: IOException) {
            Log.w(TAG, "cannot play ${file.name}", e)
            stop()
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "cannot play ${file.name}", e)
            stop()
        } catch (e: IllegalStateException) {
            Log.w(TAG, "cannot play ${file.name}", e)
            stop()
        }
    }

    override fun stop() {
        // legal in every state, a preparation under way included
        player?.release()
        player = null
        mutablePlaying.value = false
        focus.give()
    }

    private companion object {
        const val TAG = "BackingPreview"
    }
}
