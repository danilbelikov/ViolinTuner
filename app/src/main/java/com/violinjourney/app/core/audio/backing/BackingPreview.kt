package com.violinjourney.app.core.audio.backing

import android.media.AudioAttributes
import android.media.MediaPlayer
import android.util.Log
import java.io.File
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The backing listened to on the piece screen (spec 3.32): as it is, through whatever output there is — the speaker
 * too: listening is not recording. Main thread only.
 */
interface BackingPreview {
    val playing: StateFlow<Boolean>

    /** Plays [file] from its start, or stops it when it plays. */
    fun toggle(file: File)

    fun stop()
}

class MediaBackingPreview @Inject constructor() : BackingPreview {
    private val mutablePlaying = MutableStateFlow(false)
    override val playing: StateFlow<Boolean> = mutablePlaying.asStateFlow()
    private var player: MediaPlayer? = null

    override fun toggle(file: File) {
        if (player != null) {
            stop()
            return
        }
        val created = MediaPlayer()
        try {
            created.setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
            created.setDataSource(file.absolutePath)
            created.setOnCompletionListener { stop() }
            created.prepare()
            created.start()
            player = created
            mutablePlaying.value = true
        } catch (e: IOException) {
            Log.w(TAG, "cannot play ${file.name}", e)
            created.release()
        } catch (e: IllegalStateException) {
            Log.w(TAG, "cannot play ${file.name}", e)
            created.release()
        }
    }

    override fun stop() {
        player?.let { runCatching { it.stop() }; it.release() }
        player = null
        mutablePlaying.value = false
    }

    private companion object {
        const val TAG = "BackingPreview"
    }
}
