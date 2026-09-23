package com.violinjourney.app.core.domain.session

import com.violinjourney.app.core.domain.ViolinString

enum class Finger { OPEN, FIRST, SECOND, THIRD, FOURTH, HIGHER_POSITION }

data class StringFinger(val string: ViolinString, val finger: Finger) {
    companion object {
        /**
         * First-position guess (spec 5.5): the highest open string not above the note, the finger
         * by semitones from it. The app cannot know where a note was really stopped, so in
         * positions this is wrong by design; the spec says so to the user-facing copy as well.
         */
        fun of(midi: Int): StringFinger {
            val string = ViolinString.entries.lastOrNull { it.midi <= midi } ?: ViolinString.G3
            val finger = when (midi - string.midi) {
                in Int.MIN_VALUE..0 -> Finger.OPEN // a flat G string reads as F#3: still the open G
                1, 2 -> Finger.FIRST
                3, 4 -> Finger.SECOND
                5, 6 -> Finger.THIRD
                SEMITONES_TO_FOURTH_FINGER -> Finger.FOURTH
                else -> Finger.HIGHER_POSITION
            }
            return StringFinger(string, finger)
        }

        // Below the E string this distance is the next open string, so it only occurs on E.
        private const val SEMITONES_TO_FOURTH_FINGER = 7
    }
}
