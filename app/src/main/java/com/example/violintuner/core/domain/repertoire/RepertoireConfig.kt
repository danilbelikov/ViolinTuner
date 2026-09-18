package com.example.violintuner.core.domain.repertoire

/** Every number of the repertoire, with starting values from docs/spec.md 5.9. None of it is about intonation. */
data class RepertoireConfig(
    val maxTitleLength: Int = 80,
    val maxComposerLength: Int = 60,
    val maxNotesLength: Int = 2_000,
    val minTempoBpm: Int = 20,
    val maxTempoBpm: Int = 300,
    /** Long side of a stored sheet page: small print has to stay readable when zoomed in. */
    val pageMaxSidePx: Int = 2_560,
    val pageJpegQuality: Int = 88,
    /** Long side of the thumbnail shown in the strip and in the list. */
    val thumbMaxSidePx: Int = 320,
    /** Photo files younger than this are not orphans yet: their import may still be writing its row. */
    val orphanPhotoMinAgeMs: Long = 10 * 60_000L,
    /** The progress line and chart appear from this many takes on. */
    val progressFromTakes: Int = 2,
    /** Notes longer than this many lines fold on the piece screen. */
    val notesCollapsedLines: Int = 6,
    /** The controls of the music stand hide after this long without a touch. */
    val standPanelHideMs: Long = 3_000,
)
