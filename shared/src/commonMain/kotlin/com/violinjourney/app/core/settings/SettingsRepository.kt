package com.violinjourney.app.core.settings

import com.violinjourney.app.core.domain.TolerancePreset
import com.violinjourney.app.core.domain.UserSettings
import com.violinjourney.app.core.domain.VideoQuality
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface SettingsRepository {
    /** Current settings and every later change; starts with defaults on a fresh install. */
    val settings: Flow<UserSettings>

    /** Values outside [UserSettings.A4_OPTIONS_HZ] are rejected. */
    suspend fun setA4(hz: Int)

    suspend fun setTolerance(preset: TolerancePreset)

    suspend fun setOnboardingDone(done: Boolean)

    /** The switch of «Помогать улучшать приложение» (spec 3.34); takes effect at once. */
    suspend fun setAnalyticsEnabled(enabled: Boolean)

    /** «Качество видео» (spec 3.19); the next shot takes it. */
    suspend fun setVideoQuality(quality: VideoQuality)
}

/** «Качество видео» (spec 3.19) and its every change: what the cameras of the screens that shoot are given. */
val SettingsRepository.videoQuality: Flow<VideoQuality>
    get() = settings.map { it.videoQuality }.distinctUntilChanged()
