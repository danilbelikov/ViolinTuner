package com.example.violintuner.core.domain.repertoire

enum class Tonic { C, D, E, F, G, A, B }

enum class Accidental { FLAT, NATURAL, SHARP }

enum class KeyMode { MAJOR, MINOR }

/**
 * The key of a piece, shown the German way musicians write it (spec 5.9): "G-dur", "a-moll",
 * "fis-moll". Major keys are capitalised, minor ones are not; a sharp adds "is", a flat "es".
 * The irregular ones are the point of doing it by rule rather than by ear: B natural is "H",
 * B flat is plain "B", and E and A flat drop a vowel — "Es", "As".
 */
data class MusicalKey(val tonic: Tonic, val accidental: Accidental, val mode: KeyMode) {
    val germanName: String
        get() {
            val name = when (accidental) {
                Accidental.NATURAL -> if (tonic == Tonic.B) "H" else tonic.name
                Accidental.SHARP -> (if (tonic == Tonic.B) "H" else tonic.name) + "is"
                Accidental.FLAT -> when (tonic) {
                    Tonic.B -> "B"
                    Tonic.E -> "Es"
                    Tonic.A -> "As"
                    else -> tonic.name + "es"
                }
            }
            return when (mode) {
                KeyMode.MAJOR -> "$name-dur"
                KeyMode.MINOR -> "${name.lowercase()}-moll"
            }
        }
}
