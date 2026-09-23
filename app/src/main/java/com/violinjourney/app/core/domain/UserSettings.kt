package com.violinjourney.app.core.domain

/** Width of the green zone the player picks (spec 3.7); the default equals spec 5.2. */
enum class TolerancePreset(val cents: Int) {
    BEGINNER(12),
    INTERMEDIATE(8),
    PRO(3),
}

/** What the player chooses in onboarding and settings. */
data class UserSettings(
    val a4Hz: Int = DEFAULT_A4_HZ,
    val tolerance: TolerancePreset = TolerancePreset.INTERMEDIATE,
    val onboardingDone: Boolean = false,
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
