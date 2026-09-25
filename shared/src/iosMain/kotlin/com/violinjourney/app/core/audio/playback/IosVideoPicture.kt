package com.violinjourney.app.core.audio.playback

import com.violinjourney.app.core.io.PlatformFile
import kotlin.math.abs
import kotlin.math.roundToInt
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
import platform.AVFoundation.AVPlayerLayer
import platform.AVFoundation.AVURLAsset
import platform.AVFoundation.AVAssetTrack
import platform.AVFoundation.currentTime
import platform.AVFoundation.muted
import platform.AVFoundation.pause
import platform.AVFoundation.rate
import platform.AVFoundation.replaceCurrentItemWithPlayerItem
import platform.AVFoundation.naturalSize
import platform.AVFoundation.preferredTransform
import platform.AVFoundation.seekToTime
import platform.AVFoundation.tracksWithMediaType
import platform.CoreGraphics.CGRectApplyAffineTransform
import platform.CoreGraphics.CGRectMake
import platform.CoreMedia.CMTimeGetSeconds
import platform.CoreMedia.kCMTimeZero
import platform.CoreMedia.CMTimeMakeWithSeconds
import platform.Foundation.NSURL
import platform.UIKit.UIView
import platform.QuartzCore.CATransaction

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
 * is played by the session player through the chain. A picture that drifts from the sound is put back in place.
 */
@OptIn(ExperimentalForeignApi::class)
class IosVideoPicture(file: PlatformFile) : VideoPicture {
    private val mutableState = MutableStateFlow(VideoState())
    override val state: StateFlow<VideoState> = mutableState.asStateFlow()

    private val asset = AVURLAsset(uRL = NSURL.fileURLWithPath(file.path), options = null)
    private val player = AVPlayer(playerItem = null)
    private var layer: AVPlayerLayer? = null
    private var surface: VideoSurfaceHandle? = null

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
    }

    override fun follow(positionMs: Long, playing: Boolean) {
        if (state.value.failed) return
        val at = CMTimeGetSeconds(player.currentTime()) * MS_PER_SECOND
        if (abs(at - positionMs) > if (playing) DRIFT_PLAYING_MS else DRIFT_STILL_MS) {
            player.seekToTime(CMTimeMakeWithSeconds(positionMs / MS_PER_SECOND, TIMESCALE), toleranceBefore = kCMTimeZero.readValue(), toleranceAfter = kCMTimeZero.readValue())
        }
        player.rate = if (playing) 1f else 0f
        if (!state.value.showing && layer?.readyForDisplay == true) mutableState.update { it.copy(showing = true) }
    }

    override fun release() {
        surface?.view?.playerLayer = null
        surface = null
        player.pause()
        player.replaceCurrentItemWithPlayerItem(null)
    }

    private companion object {
        const val MS_PER_SECOND = 1_000.0
        const val TIMESCALE = 600
        const val DRIFT_PLAYING_MS = 150.0
        const val DRIFT_STILL_MS = 40.0
    }
}
