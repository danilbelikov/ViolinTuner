package com.violinjourney.app.core.domain.repertoire

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
    /** Bars of the level indicator of a blind take, and how long one bar stands for. */
    val levelBars: Int = 14,
    val levelBarMs: Long = 50,
    /** A take that has just been recorded stays highlighted in the list for this long. */
    val newTakeHighlightMs: Long = 1_900,
    /** The controls of the music stand hide after this long without a touch. */
    val standPanelHideMs: Long = 3_000,
    /** A video take (spec 5.13): what its sound track may be sampled at — the rates the detector is tuned for. */
    val videoSampleRatesHz: Set<Int> = setOf(44_100, 48_000),
    /** Room that has to stay free after a video has been copied in. */
    val videoFreeSpaceMarginBytes: Long = 50L * 1024 * 1024,
    /** A video file without a session is not an orphan until it is this old: it may be the only copy of a shot. */
    val orphanVideoMinAgeMs: Long = 24 * 60 * 60_000L,
    /** A section of the player's own (spec 5.16). */
    val maxGroupNameLength: Int = 24,
    /** A scale starts at the lowest tonic the violin has and ends on the instrument: G3 … E7 (spec 5.1, 5.16). */
    val scaleLowestMidi: Int = 55,
    val scaleHighestMidi: Int = 100,
)
