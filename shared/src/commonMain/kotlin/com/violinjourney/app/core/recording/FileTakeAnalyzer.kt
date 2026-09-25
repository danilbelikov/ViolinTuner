package com.violinjourney.app.core.recording

import com.violinjourney.app.core.audio.dsp.PitchDetectorFactory
import com.violinjourney.app.core.domain.IntonationConfig
import com.violinjourney.app.core.domain.repertoire.RepertoireConfig
import com.violinjourney.app.core.io.PlatformFile
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/** Analyses the sound of a media file into a session. An interface for the sake of the importer's tests. */
interface FileTakeAnalyzer {
    /**
     * [audioFileName] is what the session will keep as its sound — the name of [file] once it is
     * stored. [onProgress] comes from the analysing thread.
     */
    suspend fun analyze(
        file: PlatformFile,
        config: IntonationConfig,
        startedAtEpochMs: Long,
        audioFileName: String,
        onProgress: (FileAnalysisProgress) -> Unit,
    ): FileAnalysisResult
}

/** The sound of a media file opened for reading, PCM16 mono; [release] lets the decoder go. */
interface OpenedPcm : PcmSource {
    fun release()
}

/** Opens the sound track of a file with the platform's decoder; null when it has none it can read. */
fun interface PcmFileOpener {
    fun open(file: PlatformFile): OpenedPcm?
}

/**
 * [TakeFileAnalysis] over the sound track of a real file: the platform's decoder finds the track among the
 * others of the container — a video is read like a recording. A detector of its own: they keep buffers.
 */
class DecodingFileTakeAnalyzer(
    private val detectorFactory: PitchDetectorFactory,
    private val repertoireConfig: RepertoireConfig,
    private val dispatcher: CoroutineDispatcher,
    private val opener: PcmFileOpener,
) : FileTakeAnalyzer {
    override suspend fun analyze(
        file: PlatformFile,
        config: IntonationConfig,
        startedAtEpochMs: Long,
        audioFileName: String,
        onProgress: (FileAnalysisProgress) -> Unit,
    ): FileAnalysisResult = withContext(dispatcher) {
        val decoder = opener.open(file) ?: return@withContext FileAnalysisResult.CannotOpen
        try {
            TakeFileAnalysis.run(
                source = decoder,
                config = config,
                detector = detectorFactory.create(config),
                startedAtEpochMs = startedAtEpochMs,
                supportedRatesHz = repertoireConfig.videoSampleRatesHz,
                audioFileName = audioFileName,
                onProgress = onProgress,
            )
        } finally {
            decoder.release()
        }
    }
}
