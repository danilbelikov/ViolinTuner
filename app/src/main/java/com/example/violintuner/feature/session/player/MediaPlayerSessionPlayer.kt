package com.example.violintuner.feature.session.player

import android.media.AudioAttributes
import android.media.MediaPlayer
import android.util.Log
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** [SessionPlayer] over the platform MediaPlayer; no playback library is needed for one file. */
class MediaPlayerSessionPlayer(private val scope: CoroutineScope) : SessionPlayer {
    private val mutableState = MutableStateFlow(PlayerState())
    override val state: StateFlow<PlayerState> = mutableState.asStateFlow()

    private var player: MediaPlayer? = null
    private var ticker: Job? = null

    override fun load(file: File) {
        release()
        val created = MediaPlayer()
        player = created
        created.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build(),
        )
        created.setOnPreparedListener { prepared ->
            mutableState.value = PlayerState(ready = true, durationMs = prepared.duration.toLong())
        }
        created.setOnCompletionListener { finished ->
            // back to the start, ready to play again (spec 3.10)
            stopTicker()
            finished.seekTo(0)
            mutableState.update { it.copy(playing = false, positionMs = 0) }
        }
        created.setOnErrorListener { _, what, extra ->
            Log.w(TAG, "playback failed: what=$what extra=$extra")
            fail()
            true
        }
        try {
            created.setDataSource(file.absolutePath)
            created.prepareAsync()
        } catch (e: IOException) {
            Log.w(TAG, "cannot open ${file.name}", e)
            fail()
        } catch (e: IllegalStateException) {
            Log.w(TAG, "cannot prepare ${file.name}", e)
            fail()
        }
    }

    override fun play() {
        val current = player ?: return
        if (!state.value.ready || state.value.playing) return
        current.start()
        mutableState.update { it.copy(playing = true) }
        ticker = scope.launch {
            while (isActive) {
                mutableState.update { it.copy(positionMs = current.currentPosition.toLong()) }
                delay(POSITION_PERIOD_MS)
            }
        }
    }

    override fun pause() {
        val current = player ?: return
        if (!state.value.playing) return
        stopTicker()
        current.pause()
        mutableState.update { it.copy(playing = false, positionMs = current.currentPosition.toLong()) }
    }

    override fun seekTo(positionMs: Long) {
        val current = player ?: return
        if (!state.value.ready) return
        val target = positionMs.coerceIn(0, state.value.durationMs)
        current.seekTo(target, MediaPlayer.SEEK_CLOSEST)
        mutableState.update { it.copy(positionMs = target) }
    }

    override fun release() {
        stopTicker()
        player?.release()
        player = null
        mutableState.value = PlayerState()
    }

    private fun fail() {
        stopTicker()
        player?.release()
        player = null
        mutableState.value = PlayerState(failed = true)
    }

    private fun stopTicker() {
        ticker?.cancel()
        ticker = null
    }

    private companion object {
        const val TAG = "SessionPlayer"

        /** One bucket of the session: the cursor moves as finely as the roll is drawn. */
        const val POSITION_PERIOD_MS = 50L
    }
}
