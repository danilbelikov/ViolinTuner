package com.violinjourney.app.core.audio

import android.content.Context
import android.media.AudioManager
import com.violinjourney.app.core.domain.IntonationConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/** The sample rates a take's microphone is tried at, in order: the phone's own first when it is one of ours. */
object SampleRates {
    fun candidates(nativeHz: Int?, supportedHz: List<Int>): List<Int> =
        (listOfNotNull(nativeHz) + supportedHz).filter { it in supportedHz }.distinct()
}

/**
 * The rate a take will be recorded at on this phone, known before the microphone is opened: the backing is made
 * ready for the mix at it alone (spec 5.25) — a phone records at one rate, and a second copy would only take room.
 */
fun interface RecordingRate {
    fun likelyHz(): Int
}

class AndroidRecordingRate @Inject constructor(
    @ApplicationContext private val context: Context,
    private val config: IntonationConfig,
) : RecordingRate {
    override fun likelyHz(): Int {
        val native = context.getSystemService(AudioManager::class.java).getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE)?.toIntOrNull()
        return SampleRates.candidates(native, config.supportedSampleRatesHz).first()
    }
}
