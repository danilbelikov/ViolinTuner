package com.violinjourney.app.core.audio

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
