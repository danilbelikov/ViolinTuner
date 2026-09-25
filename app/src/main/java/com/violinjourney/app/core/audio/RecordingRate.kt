package com.violinjourney.app.core.audio

import android.content.Context
import android.media.AudioManager
import com.violinjourney.app.core.domain.IntonationConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class AndroidRecordingRate @Inject constructor(
    @ApplicationContext private val context: Context,
    private val config: IntonationConfig,
) : RecordingRate {
    override fun likelyHz(): Int {
        val native = context.getSystemService(AudioManager::class.java).getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE)?.toIntOrNull()
        return SampleRates.candidates(native, config.supportedSampleRatesHz).first()
    }
}
