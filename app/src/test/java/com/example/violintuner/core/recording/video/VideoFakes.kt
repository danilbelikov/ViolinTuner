package com.example.violintuner.core.recording.video

import com.example.violintuner.core.domain.IntonationConfig
import com.example.violintuner.core.domain.Zone
import com.example.violintuner.core.domain.session.NewSession
import com.example.violintuner.core.domain.session.RecordingBar
import com.example.violintuner.core.domain.session.SessionAnalyzer
import com.example.violintuner.core.domain.session.SessionSample
import com.example.violintuner.core.recording.FileAnalysisProgress
import com.example.violintuner.core.recording.FileAnalysisResult
import com.example.violintuner.core.recording.FileTakeAnalyzer
import java.io.File
import kotlinx.coroutines.delay

/** Videos that are only names: nothing touches a disk. */
class FakeVideoFiles : VideoFiles {
    var info: VideoInfo? = VideoInfo(durationMs = 60_000, width = 1920, height = 1080, createdAtEpochMs = null, hasSound = true)
    var free = Long.MAX_VALUE
    var sizes = mutableMapOf<String, Long>()
    var importFails = false
    var adoptFails = false
    val discarded = mutableListOf<String>()
    val thumbs = mutableSetOf<String>()
    private var next = 1

    override fun newCameraFile() = File("/cache/camera/shot-${next++}.mp4")
    override fun adopt(cameraFile: File): File? = if (adoptFails) null else File("/files/sessions/video-${next++}.mp4")
    override fun sizeOf(uri: String): Long? = sizes[uri]
    override fun freeBytes(): Long = free
    override suspend fun import(uri: String): File? {
        delay(COPY_MS)
        return if (importFails) null else File("/files/sessions/video-${next++}.mp4")
    }
    override fun info(file: File): VideoInfo? = info
    override fun makeThumb(file: File): Boolean = thumbs.add(file.name)
    override fun thumbOf(name: String): File? = File("/files/sessions/$name-thumb.jpg").takeIf { name in thumbs }
    override fun existing(name: String): File? = File("/files/sessions/$name")
    override fun discard(file: File) {
        discarded += file.name
    }

    companion object {
        const val COPY_MS = 300L
    }
}

/** An analysis that takes [tookMs] of virtual time, reports progress ten times and ends as told. */
class FakeFileTakeAnalyzer(var tookMs: Long = 2_000, var outcome: FileAnalysisResult? = null) : FileTakeAnalyzer {
    var calls = 0

    override suspend fun analyze(
        file: File,
        config: IntonationConfig,
        startedAtEpochMs: Long,
        audioFileName: String,
        onProgress: (FileAnalysisProgress) -> Unit,
    ): FileAnalysisResult {
        calls++
        repeat(STEPS) { step ->
            delay(tookMs / STEPS)
            onProgress(FileAnalysisProgress((step + 1f) / STEPS, listOf(RecordingBar((step + 1f) / STEPS / 2, Zone.IN_TUNE))))
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
