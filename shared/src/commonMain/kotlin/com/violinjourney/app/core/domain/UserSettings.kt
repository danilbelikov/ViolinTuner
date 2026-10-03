package com.violinjourney.app.core.domain

/** Width of the green zone the player picks (spec 3.7); the default equals spec 5.2. */
enum class TolerancePreset(val cents: Int) {
    BEGINNER(12),
    INTERMEDIATE(8),
    PRO(3),
}

/**
 * The picture of a video take shot from now on (spec 3.19, 5.13): the system camera of iOS and the app's own camera of both platforms
 * take it; the system camera of Android does not listen. [height] — the lines of the picture, what its name says.
 */
enum class VideoQuality(val height: Int) {
    P480(480),
    P720(720),
    P1080(1080),
}

/** What the player chooses in onboarding and settings. */
data class UserSettings(
    val a4Hz: Int = DEFAULT_A4_HZ,
    val tolerance: TolerancePreset = TolerancePreset.INTERMEDIATE,
    val onboardingDone: Boolean = false,
    /** «Помогать улучшать приложение» (spec 3.34): on until the player turns it off. */
    val analyticsEnabled: Boolean = true,
    /** «Качество видео» (spec 3.19): 720p until the player picks another. */
    val videoQuality: VideoQuality = VideoQuality.P720,
) {
    companion object {
        const val DEFAULT_A4_HZ = 440

        /** Reference pitches offered to the player: home and school 440, orchestras up to 443. */
        val A4_OPTIONS_HZ = listOf(440, 441, 442, 443)
    }
}

/** The config with the player's choices applied; everything else keeps the spec values. */
fun IntonationConfig.with(settings: UserSettings): IntonationConfig = copy(
    a4Hz = settings.a4Hz.toDouble(),
    toleranceCents = settings.tolerance.cents.toDouble(),
)
