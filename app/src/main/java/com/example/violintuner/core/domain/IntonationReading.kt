package com.example.violintuner.core.domain

/** What the target is chosen from. */
sealed interface TargetMode {
    /** "Play" mode: nearest chromatic note. */
    data object Chromatic : TargetMode

    /** "Tuning" mode: open strings only; [locked] pins the target (spec 3.5). */
    data class Strings(val locked: ViolinString? = null) : TargetMode
}

/** Domain output per frame; the Live screen maps it to the states of spec 3.4. */
sealed interface IntonationReading {
    data object Silence : IntonationReading

    data object TooNoisy : IntonationReading

    data class Active(
        val note: Note,
        /** Smoothed deviation from [note]; positive is sharp. */
        val cents: Double,
        val zone: Zone,
        /** Null while [zone] is IN_TUNE. */
        val direction: Direction?,
        /** Hold ring fill, 0..1. */
        val holdProgress: Double,
        /**
         * True when this is the previous reading kept on screen through a pitch gap, not a new
         * measurement. The screen shows it all the same; session recording skips it.
         */
        val held: Boolean = false,
    ) : IntonationReading
}
