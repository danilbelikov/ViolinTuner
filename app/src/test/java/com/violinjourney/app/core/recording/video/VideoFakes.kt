package com.violinjourney.app.core.recording.video

import com.violinjourney.app.core.domain.IntonationConfig
import com.violinjourney.app.core.domain.Zone
import com.violinjourney.app.core.domain.session.NewSession
import com.violinjourney.app.core.domain.session.RecordingBar
import com.violinjourney.app.core.domain.session.RecordingRibbon
import com.violinjourney.app.core.domain.session.SessionAnalyzer
import com.violinjourney.app.core.domain.session.SessionSample
import com.violinjourney.app.core.recording.FileAnalysisProgress
import com.violinjourney.app.core.recording.FileAnalysisResult
import com.violinjourney.app.core.recording.FileTakeAnalyzer
import java.io.File
import kotlinx.coroutines.delay

/** Videos that are only names: nothing touches a disk. */
class FakeVideoFiles : VideoFiles {
    var info: VideoInfo? = VideoInfo(durationMs = 60_000, width = 1920, height = 1080, createdAtEpochMs = null, hasSound = true)
    var free = Long.MAX_VALUE
    var sizes = mutableMapOf<String, Long>()
    var importFails = false
    var adoptFails = false
    /** What the platform throws instead of answering: a provider, a file system, AVFoundation on a duration of NaN. */
    var adoptThrows: Exception? = null
    var importThrows: Exception? = null
    var sizeThrows: Exception? = null
    /** Asked first by `info()`: it throws, or does what the player does meanwhile; null falls back to [info]. */
    var onInfo: ((File) -> VideoInfo?)? = null
    val discarded = mutableListOf<String>()
    val thumbs = mutableSetOf<String>()
    private var next = 1

    /** The container the camera writes: `.mov` on an iPhone. Adopted files keep it, as `IosVideoFiles.adopt` does. */
    var cameraExtension = ".mp4"

    /** Where the camera writes: a bare path by default; a real folder for a test that wants the shot on a disk. */
    var cameraFolder = File("/cache/camera")

    override fun newCameraFile() = File(cameraFolder, "shot-${next++}$cameraExtension")
    override fun adopt(cameraFile: File): File? {
        adoptThrows?.let { throw it }
        return if (adoptFails) null else File("/files/sessions/video-${next++}.${cameraFile.extension}")
    }
    override fun sizeOf(uri: String): Long? = sizeThrows?.let { throw it } ?: sizes[uri]
    /** How often the room was asked: never on the caller's thread of `picked`. */
    var freeAsked = 0
    override fun freeBytes(): Long = free.also { freeAsked++ }
    override suspend fun import(uri: String): File? {
        delay(COPY_MS)
        importThrows?.let { throw it }
        return if (importFails) null else File("/files/sessions/video-${next++}.mp4")
    }
    /** The picks let go without coming in, in order. */
    val released = mutableListOf<String>()
    override fun release(uri: String) {
        released += uri
    }
    override fun info(file: File): VideoInfo? = onInfo?.invoke(file) ?: info
    /** Every thumbnail made, in order — made anew too; [thumbFails] names the videos whose frame cannot be had. */
    val thumbsMade = mutableListOf<String>()
    var thumbFails = emptySet<String>()
    override fun makeThumb(file: File): Boolean {
        thumbsMade += file.name
        return file.name !in thumbFails && thumbs.add(file.name)
    }
    override fun thumbOf(name: String): File? = File("/files/sessions/$name-thumb.jpg").takeIf { name in thumbs }
    /** How often a video was looked for: the piece screen does it when its takes change, not on every tap. */
    var existingCalls = 0

    /** Videos that are really there, by name, with their sizes: [existing] gives a file of that many bytes; the others are a bare path. */
    val present = mutableMapOf<String, Long>()
    override fun existing(name: String): File? {
        existingCalls++
        val bytes = present[name] ?: return File("/files/sessions/$name")
        return File.createTempFile("video", ".mp4").apply {
            deleteOnExit()
            writeBytes(ByteArray(bytes.toInt()))
        }
    }
    override fun discard(file: File) {
        discarded += file.name
    }

    companion object {
        const val COPY_MS = 300L
    }
}

/** An analysis that takes [tookMs] of virtual time, reports progress ten times and ends as told — or throws [failWith] halfway. */
class FakeFileTakeAnalyzer(var tookMs: Long = 2_000, var outcome: FileAnalysisResult? = null) : FileTakeAnalyzer {
    var calls = 0
    /** A codec that gives up in the middle of the file. */
    var failWith: Exception? = null

    override suspend fun analyze(
        file: File,
        config: IntonationConfig,
        startedAtEpochMs: Long,
        audioFileName: String,
        onProgress: (FileAnalysisProgress) -> Unit,
    ): FileAnalysisResult {
        calls++
        repeat(STEPS) { step ->
            if (step == STEPS / 2) failWith?.let { throw it }
            delay(tookMs / STEPS)
            onProgress(FileAnalysisProgress((step + 1f) / STEPS, RecordingRibbon(listOf(RecordingBar(step + 1, Zone.IN_TUNE)), span = 2f * STEPS)))
        }
        return outcome ?: FileAnalysisResult.Recorded(sessionOf(config, startedAtEpochMs, audioFileName))
    }

    companion object {
        const val STEPS = 10

        fun sessionOf(config: IntonationConfig, startedAtEpochMs: Long, audioFileName: String): NewSession {
            val samples = List(60) { SessionSample(69, 1.0) }
            val analysis = SessionAnalyzer.analyze(samples, config)
            return NewSession(
                startedAtEpochMs = startedAtEpochMs, durationMs = 3_000, config = config, samples = samples, metrics = analysis.metrics!!,
                previewZones = SessionAnalyzer.previewZones(analysis.segments, config), audioPath = audioFileName,
            )
        }
    }
}
