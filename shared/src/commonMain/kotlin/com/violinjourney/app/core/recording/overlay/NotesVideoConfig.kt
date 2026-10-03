package com.violinjourney.app.core.recording.overlay

/**
 * Every number of «Видео с нотами» (spec 3.37, 5.30), with starting values from the mockup
 * `docs/design/project/overlay/project/overlay.html`. Sizes of the picture are in u — a hundredth of the short side of the
 * frame as the player shows it; times are ms of the video.
 */
data class NotesVideoConfig(
    // The shade under the lane: from the transparent top through black at [scrimMidAlpha] to [scrimBottomAlpha]
    val scrimU: Float = 65f,
    val scrimLandscapeU: Float = 47f,
    val scrimMidStop: Float = 0.35f,
    val scrimMidAlpha: Float = 0.45f,
    val scrimBottomAlpha: Float = 0.78f,

    // The lane of notes: its height and how high its bottom stands over the bottom of the frame
    val laneU: Float = 30f,
    val laneLandscapeU: Float = 18f,
    val laneBottomU: Float = 15.2f,
    val laneBottomLandscapeU: Float = 13.6f,
    /** A note is a capsule this high; its end is cut short by [pillGapU] so that two notes in a row stay two. */
    val pillU: Float = 4.4f,
    val pillGapU: Float = 0.6f,
    /** The name inside a capsule, from its start; drawn only where it and [labelRoomU] of air fit in. */
    val labelU: Float = 2.8f,
    val labelInsetU: Float = 1.6f,
    val labelRoomU: Float = 3.2f,
    /** The note under the playhead is whole; the others are dimmed to this. */
    val otherNotesAlpha: Float = 0.62f,
    /** How fast the notes ride from right to left; the playhead — «now» — stands at [headShare] of the width. */
    val speedUPerSecond: Float = 11f,
    val headShare: Float = 0.5f,
    /** The heights of the lane: the notes of the recording and a semitone of air, never fewer than [minPitchSpan] semitones. */
    val pitchPadSemitones: Int = 1,
    val minPitchSpan: Int = 8,

    // The playhead and the tag of the current note over it
    val playheadU: Float = 0.5f,
    val playheadOverhangU: Float = 1.5f,
    val playheadAlpha: Float = 0.9f,
    val tagU: Float = 6.4f,
    val tagMinWidthU: Float = 10.24f,
    /** Air on both sides of the name together. */
    val tagPadU: Float = 4f,
    /** Between the tag and the top of the lane. */
    val tagGapU: Float = 1.2f,
    val tagTextU: Float = 3.6f,
    /** A break shorter than this — a change of bow — keeps the last note current: nothing blinks between two notes. */
    val holdMs: Long = 300,
    val tagFadeMs: Long = 150,

    // The badge: «в строе 82 %» on the glass, bottom left
    val badgeU: Float = 7.2f,
    val badgePadStartU: Float = 2.6f,
    val badgePadEndU: Float = 2.8f,
    val badgeDotU: Float = 1.8f,
    val badgeDotGapU: Float = 1.4f,
    val badgeTextU: Float = 3.4f,
    val badgeInsetU: Float = 5f,
    val badgeInsetLandscapeU: Float = 4f,

    // The summary: three seconds after the end of the video, over its last frame
    val summaryMs: Long = 3_000,
    val summaryFadeMs: Long = 400,
    val veilAlpha: Float = 0.9f,
    /** The column: the width less these margins together, but never wider than [summaryMaxWidthU]. */
    val summaryMarginsU: Float = 16f,
    val summaryMaxWidthU: Float = 84f,
    /** A line of the small text of the summary: its box and where in it the baseline stands. */
    val lineU: Float = 4.4f,
    val lineBaselineU: Float = 3.4f,
    val textU: Float = 3.4f,
    val textAlpha: Float = 0.7f,
    val scoreGapU: Float = 3f,
    /** The score: its box and baseline; the number, and «%» smaller, after a gap. */
    val scoreLineU: Float = 17f,
    val scoreBaselineU: Float = 15f,
    val scoreU: Float = 18f,
    val percentU: Float = 9f,
    val percentGapU: Float = 1.2f,
    val percentAlpha: Float = 0.55f,
    val stripGapU: Float = 4f,
    val stripU: Float = 2.2f,
    val rowsGapU: Float = 3f,
    /** A row of the summary; its baseline stands this far under the middle of the row. */
    val rowU: Float = 9f,
    val rowBaselineU: Float = 1.3f,
    val ruleU: Float = 0.2f,
    val ruleAlpha: Float = 0.1f,
    val rowValueU: Float = 4.8f,
    /** Between a row's name and its value, at the least. */
    val rowGapU: Float = 2f,
    /** The arrow of «Что уходит»: its head and stem, and the air after it; the dot of «ничего» and the air after it. */
    val arrowU: Float = 3.2f,
    val arrowHeadU: Float = 2f,
    val arrowHeightU: Float = 4.2f,
    val arrowStemU: Float = 1f,
    val arrowGapU: Float = 1f,
    val dotU: Float = 2f,
    val dotGapU: Float = 1.2f,
    /** The middle of the dot over the baseline: the middle of the small letters. */
    val dotRaiseU: Float = 1.7f,
    val gainU: Float = 3.4f,

    // The rules of the summary (spec 5.30)
    /** «Лучшая нота» is one that sounded at least this long in all and was in tune at least this share of its samples. */
    val bestNoteMinMs: Long = 1_000,
    val bestNoteMinShare: Double = 0.5,

    // The file (spec 5.30)
    val maxShortSidePx: Int = 1_080,
    val maxFrameRate: Int = 30,
    val bitsPerPixel: Double = 0.08,
    val minBitrate: Int = 1_000_000,
    val maxBitrate: Int = 6_000_000,
    /** The bit rate of a sound track that does not say its own. */
    val soundBitrateGuess: Int = 128_000,
    /** A recording up to this long gets «Видео с нотами»: the picture has to be encoded again, and that is slow. */
    val maxDurationMs: Long = 15 * 60_000L,
    /** How long a render takes against the length of the video, before a render of this device has been measured. */
    val renderSpeedStart: Double = 0.6,
    /** Where the parts of the work end in the progress: the sound, then the picture; the splice takes the rest. */
    val soundShare: Float = 0.10f,
    val pictureShare: Float = 0.95f,
)
