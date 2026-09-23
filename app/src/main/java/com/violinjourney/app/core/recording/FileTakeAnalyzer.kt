package com.violinjourney.app.core.recording

import com.violinjourney.app.core.audio.dsp.PitchDetectorFactory
import com.violinjourney.app.core.audio.playback.PcmDecoder
import com.violinjourney.app.core.di.DefaultDispatcher
import com.violinjourney.app.core.domain.IntonationConfig
import com.violinjourney.app.core.domain.repertoire.RepertoireConfig
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/** Analyses the sound of a media file into a session. An interface for the sake of the importer's tests. */
interface FileTakeAnalyzer {
    /**
     * [audioFileName] is what the session will keep as its sound — the name of [file] once it is
     * stored. [onProgress] comes from the analysing thread.
     */
    suspend fun analyze(
        file: File,
        config: IntonationConfig,
        startedAtEpochMs: Long,
        audioFileName: String,
        onProgress: (FileAnalysisProgress) -> Unit,
    ): FileAnalysisResult
}

/**
 * [TakeFileAnalysis] over the sound track of a real file: [PcmDecoder] finds the track among the
 * others of the container — a video is read like a recording. A detector of its own: they keep buffers.
 */
class DecodingFileTakeAnalyzer @Inject constructor(
    private val detectorFactory: PitchDetectorFactory,
    private val repertoireConfig: RepertoireConfig,
    @DefaultDispatcher private val dispatcher: CoroutineDispatcher,
) : FileTakeAnalyzer {
    override suspend fun analyze(
        file: File,
        config: IntonationConfig,
        startedAtEpochMs: Long,
        audioFileName: String,
        onProgress: (FileAnalysisProgress) -> Unit,
    ): FileAnalysisResult = withContext(dispatcher) {
        val decoder = PcmDecoder.open(file) ?: return@withContext FileAnalysisResult.CannotOpen
        try {
            TakeFileAnalysis.run(
                source = object : PcmSource {
                    override val sampleRate = decoder.sampleRate
                    override val totalSamples = decoder.totalSamples
                    override fun read(out: ShortArray): Int = decoder.read(out)
                },
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
