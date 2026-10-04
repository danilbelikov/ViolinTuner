package com.violinjourney.app.core.audio.playback

import com.violinjourney.app.core.io.PlatformFile
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.time.TimeSource
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.readValue
import kotlinx.cinterop.useContents
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import platform.AVFoundation.AVLayerVideoGravityResizeAspect
import platform.AVFoundation.AVMediaTypeVideo
import platform.AVFoundation.AVPlayer
import platform.AVFoundation.AVPlayerItemStatusFailed
import platform.AVFoundation.AVPlayerItemStatusReadyToPlay
import platform.AVFoundation.AVPlayerLayer
import platform.AVFoundation.AVURLAsset
import platform.AVFoundation.AVAssetTrack
import platform.AVFoundation.currentItem
import platform.AVFoundation.currentTime
import platform.AVFoundation.muted
import platform.AVFoundation.pause
import platform.AVFoundation.rate
import platform.AVFoundation.replaceCurrentItemWithPlayerItem
import platform.AVFoundation.naturalSize
import platform.AVFoundation.nominalFrameRate
import platform.AVFoundation.preferredTransform
import platform.AVFoundation.seekToTime
import platform.AVFoundation.timeRange
import platform.AVFoundation.tracksWithMediaType
import platform.CoreGraphics.CGRectApplyAffineTransform
import platform.CoreGraphics.CGRectMake
import platform.CoreMedia.CMTimeGetSeconds
import platform.CoreMedia.CMTimeRangeGetEnd
import platform.CoreMedia.kCMTimeZero
import platform.CoreMedia.CMTimeMakeWithSeconds
import platform.Foundation.NSURL
import platform.UIKit.UIView
import platform.QuartzCore.CATransaction
import platform.darwin.DISPATCH_TIME_NOW
import platform.darwin.dispatch_after
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import platform.darwin.dispatch_time

/** Where the picture of a take is drawn on iOS: a view that keeps the layer of the player over its whole size. */
@OptIn(ExperimentalForeignApi::class)
class PictureView : UIView(frame = CGRectMake(0.0, 0.0, 1.0, 1.0)) {
    var playerLayer: AVPlayerLayer? = null
        set(value) {
            field?.removeFromSuperlayer()
            field = value
            value?.let {
                it.frame = bounds
                layer.addSublayer(it)
            }
        }

    override fun layoutSubviews() {
        super.layoutSubviews()
        CATransaction.begin()
        CATransaction.setDisableActions(true)
        playerLayer?.frame = bounds
        CATransaction.commit()
    }
}

actual class VideoSurfaceHandle(val view: PictureView)

/**
 * The picture of a video take on iOS (spec 3.19): a silent AVPlayer led by the clock of the sound — the sound itself
 * is played by the session player through the chain. It runs only while the sound is heard moving ([PictureFollow]):
 * after a play or a seek it stands where the sound will start, rather than run ahead and seek back (spec 5.13, 0.94).
 * A picture that drifts from the sound is put back in place, one seek at a time ([send]).
 */
@OptIn(ExperimentalForeignApi::class)
class IosVideoPicture(file: PlatformFile) : VideoPicture {
    private val mutableState = MutableStateFlow(VideoState())
    override val state: StateFlow<VideoState> = mutableState.asStateFlow()

    private val asset = AVURLAsset(uRL = NSURL.fileURLWithPath(file.path), options = null)
    private val player = AVPlayer(playerItem = null)
    private var layer: AVPlayerLayer? = null
    private var surface: VideoSurfaceHandle? = null

    // The main thread's, as every call here: the look for the first frame ([lookForFirstFrame]), the words of the sound ([follow])
    // and the seeks ([send]).
    private var released = false
    private var watching = false
    private var watchedMs = 0L
    private val started = TimeSource.Monotonic.markNow()

    /** The last word of the sound that said something new, and whether the picture runs since it. */
    private var heard: PictureFollow.Word? = null
    private var running = false

    /** The place a seek on its way goes to; null — none is. */
    private var seekingToMs: Long? = null
    private var seekSentAtMs = 0L

    /** The seeks sent: the answer of one given up for lost is not taken for the answer of the next. */
    private var seeksSent = 0

    /** What the last seek took to land: a running picture aims that much ahead. */
    private var lastSeekMs = 0L

    /** The place waiting for its seek — asked for while one was on its way, or before the item could take one — and when it was asked. */
    private var waitingMs: Long? = null
    private var waitingAskedAtMs = 0L

    /** The place of the last frame: no seek goes past it — an exact seek to the very end of an item may never be answered. */
    private var lastFrameMs = Long.MAX_VALUE

    init {
        val track = asset.tracksWithMediaType(AVMediaTypeVideo).firstOrNull() as? AVAssetTrack
        if (track == null) {
            mutableState.value = VideoState(failed = true)
        } else {
            // as it is seen: the turn the camera recorded applied
            val (width, height) = CGRectApplyAffineTransform(
                track.naturalSize.useContents { CGRectMake(0.0, 0.0, width, height) },
                track.preferredTransform,
            ).useContents { abs(size.width).roundToInt() to abs(size.height).roundToInt() }
            mutableState.value = VideoState(width = width, height = height)
            val endMs = CMTimeGetSeconds(CMTimeRangeGetEnd(track.timeRange)) * MS_PER_SECOND
            val frameMs = MS_PER_SECOND / (track.nominalFrameRate.takeIf { it > 0f } ?: DEFAULT_FRAME_RATE)
            if (endMs.isFinite()) lastFrameMs = (endMs - frameMs).toLong().coerceAtLeast(0)
            player.replaceCurrentItemWithPlayerItem(platform.AVFoundation.AVPlayerItem(asset = asset))
            player.muted = true
        }
    }

    override fun setSurface(next: VideoSurfaceHandle?) {
        if (next === surface) return
        surface?.view?.playerLayer = null
        surface = next
        if (next == null || state.value.failed) return
        val fresh = layer ?: AVPlayerLayer.playerLayerWithPlayer(player).apply { videoGravity = AVLayerVideoGravityResizeAspect }
        layer = fresh
        next.view.playerLayer = fresh
        watchFirstFrame()
    }

    /**
     * The placeholder stays only until the first frame is ready (spec 3.19) — and that must not wait for «play»: [follow]
     * comes only when the sound's state changes, and a paused player has nothing more to say after it is ready. So from the
     * moment the layer is on a view, the main queue looks every [FIRST_FRAME_STEP_MS] whether it has a frame to show, and
     * stops at the first one, or after [FIRST_FRAME_WAIT_MS]. A poll, not KVO: Kotlin/Native cannot override
     * `observeValueForKeyPath`. An item AVPlayer could not open is a picture this phone cannot show: «не показать». A seek
     * asked for before the item was ready goes as soon as it is.
     */
    private fun watchFirstFrame() {
        if (watching) return
        watching = true
        watchedMs = 0
        lookForFirstFrame()
    }

    private fun lookForFirstFrame() {
        if (!released) send()
        val done = when {
            released || state.value.showing || state.value.failed -> true
            layer?.readyForDisplay == true -> true.also { mutableState.update { it.copy(showing = true) } }
            player.currentItem?.status == AVPlayerItemStatusFailed -> true.also { mutableState.update { it.copy(failed = true) } }
            else -> watchedMs >= FIRST_FRAME_WAIT_MS
        }
        if (done) {
            watching = false
            return
        }
        watchedMs += FIRST_FRAME_STEP_MS
        dispatch_after(dispatch_time(DISPATCH_TIME_NOW, FIRST_FRAME_STEP_MS * NANOS_PER_MS), dispatch_get_main_queue()) { lookForFirstFrame() }
    }

    override fun follow(positionMs: Long, playing: Boolean) {
        if (state.value.failed) return
        val now = nowMs()
        val word = PictureFollow.Word(positionMs, playing, now)
        val pictureMs = waitingMs ?: seekingToMs ?: (CMTimeGetSeconds(player.currentTime()) * MS_PER_SECOND).toLong()
        PictureFollow.step(heard, word, pictureMs)?.let { step ->
            heard = word
            running = step.runs
            // A running picture put back in place while a seek is on its way would chase a place gone by: the word after the landing
            // tells where it is. A picture that stands goes to its place whatever is on its way.
            step.seekToMs?.takeIf { !step.runs || seekingToMs == null }?.let { ask(it, atMs = now) }
            // AVPlayer stops by itself at the end of the picture: the rate is asked of it, not remembered
            val rate = if (step.runs) 1f else 0f
            if (player.rate != rate) player.rate = rate
        }
        send()
        if (!state.value.showing && layer?.readyForDisplay == true) mutableState.update { it.copy(showing = true) }
    }

    /** The picture must be at [toMs], asked for at [atMs]: the old frame stands meanwhile, and a spinner on it if that takes long (spec 3.19, 0.94). */
    private fun ask(toMs: Long, atMs: Long) {
        waitingMs = toMs
        waitingAskedAtMs = atMs
        if (!state.value.catchingUp) mutableState.update { it.copy(catchingUp = true) }
        send()
    }

    /**
     * Sends the seek waiting in [waitingMs]: one at a time, the last place asked for and not every one a slider passed (Apple QA1820), and
     * only once the item can take it — a seek with a completion before the item is ready to play is an exception of AVFoundation. A running
     * picture aims ahead by the time since the place was asked for and by what the last seek took, so it lands where the sound is by then.
     * Never past the last frame; a seek not answered in [SEEK_GIVE_UP_MS] is given up for lost and holds no other back.
     */
    private fun send() {
        val wanted = waitingMs ?: return
        if (seekingToMs != null || player.currentItem?.status != AVPlayerItemStatusReadyToPlay) return
        waitingMs = null
        val now = nowMs()
        val lead = if (running) now - waitingAskedAtMs + lastSeekMs.coerceAtMost(MAX_LEAD_MS) else 0
        val toMs = (wanted + lead).coerceIn(0, lastFrameMs)
        seekingToMs = toMs
        seekSentAtMs = now
        val seek = ++seeksSent
        val exact = kCMTimeZero.readValue()
        player.seekToTime(CMTimeMakeWithSeconds(toMs / MS_PER_SECOND, TIMESCALE), toleranceBefore = exact, toleranceAfter = exact) { _ ->
            dispatch_async(dispatch_get_main_queue()) { landed(seek) }
        }
        dispatch_after(dispatch_time(DISPATCH_TIME_NOW, SEEK_GIVE_UP_MS * NANOS_PER_MS), dispatch_get_main_queue()) { landed(seek) }
    }

    /** Seek number [seek] is over — landed, cut short by a newer one or by the item let go, or given up for lost here. */
    private fun landed(seek: Int) {
        if (released || seek != seeksSent || seekingToMs == null) return
        seekingToMs = null
        lastSeekMs = nowMs() - seekSentAtMs
        if (waitingMs != null) send() else mutableState.update { it.copy(catchingUp = false) }
    }

    private fun nowMs(): Long = started.elapsedNow().inWholeMilliseconds

    override fun release() {
        released = true
        surface?.view?.playerLayer = null
        surface = null
        player.pause()
        player.replaceCurrentItemWithPlayerItem(null)
    }

    private companion object {
        const val MS_PER_SECOND = 1_000.0
        const val TIMESCALE = 600
        const val FIRST_FRAME_STEP_MS = 50L
        const val FIRST_FRAME_WAIT_MS = 10_000L
        const val NANOS_PER_MS = 1_000_000L

        /** A seek AVFoundation has not answered in this long is taken for lost. */
        const val SEEK_GIVE_UP_MS = 2_000L

        /** The most a running picture aims ahead for the time its seek takes. */
        const val MAX_LEAD_MS = 500L

        /** The frame rate of a track that names none. */
        const val DEFAULT_FRAME_RATE = 30f
    }
}
