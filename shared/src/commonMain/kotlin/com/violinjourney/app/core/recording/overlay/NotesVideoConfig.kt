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
    /**
     * After the name in the tag (since 0.90): [tagSignGapU] of air, the arrow [tagArrowU] wide in the colour of the zone — or, in
     * tune, the dot [tagDotU] — then [tagNumberGapU] and the mean deviation of the note, [tagNumberU] high and grey.
     */
    val tagSignGapU: Float = 1.4f,
    val tagArrowU: Float = 2.6f,
    val tagDotU: Float = 1.6f,
    val tagNumberGapU: Float = 0.8f,
    val tagNumberU: Float = 3.2f,
    val tagNumberAlpha: Float = 0.75f,

    // The dust (since 0.90): a capsule crumbles at the playhead, every [dustSegmentU] of it into [dustPerSegment] particles
    val dustSegmentU: Float = 0.4f,
    val dustPerSegment: Int = 3,
    val dustLifeMs: Long = 700,
    /** Their own speed on top of the lane's: left by up to [dustDriftU] u a second, up or down by up to [dustSpreadU]; and falling. */
    val dustDriftU: Float = 4f,
    val dustSpreadU: Float = 6f,
    val dustFallU: Float = 14f,
    /** The side of a particle, from [dustMinSideU] to [dustMaxSideU], shrinking to [dustEndShare] of it by the end of its life. */
    val dustMinSideU: Float = 0.5f,
    val dustMaxSideU: Float = 1f,
    val dustEndShare: Float = 0.4f,
    /** The corners of a particle, as a share of its side. */
    val dustCorner: Float = 0.25f,

    // The opening title (since 0.90): the name and the date of the recording on a shade of their own at the top
    val openingScrimU: Float = 36f,
    val openingScrimLandscapeU: Float = 33f,
    val openingScrimAlpha: Float = 0.55f,
    val openingTitleU: Float = 6f,
    val openingDateU: Float = 3.4f,
    val openingDateGapU: Float = 1.6f,
    val openingDateAlpha: Float = 0.7f,
    /** The top of the line of the name: this far under the room of the line of the app (since 0.93; before — 9 u, lying 6 u, from the top). */
    val openingUnderAppLineU: Float = 3f,
    /** It comes in from [openingInMs] to [openingShownMs], dropping [openingDropU] into place; stays to [openingOutMs]; is gone at [openingEndMs]. */
    val openingInMs: Long = 300,
    val openingShownMs: Long = 900,
    val openingOutMs: Long = 3_400,
    val openingEndMs: Long = 4_000,
    val openingDropU: Float = 1.5f,
    /** A video shorter than [openingShortVideoMs] ends the title [openingLeadMs] before its own end; one shorter than [openingMinVideoMs] has none. */
    val openingShortVideoMs: Long = 5_000,
    val openingMinVideoMs: Long = 2_000,
    val openingLeadMs: Long = 1_000,
    /** The shortest a shortened coming in or going out may be. */
    val openingMinFadeMs: Long = 200,

    // The line of the app in the top right corner (since 0.93; 0.90–0.92 — beside a badge at the bottom left, gone since): dimmed,
    // its lines to the right, no wider than [appLineMaxWidthU]; [appLineInsetU] from the top and the right edge. The top of the frame
    // has no shade of its own, so the letters carry a soft shadow: on a window or a white wall the dimmed line would be gone
    val appLineInsetU: Float = 5f,
    val appLineInsetLandscapeU: Float = 4f,
    val appLineMaxWidthU: Float = 48f,
    val appLineTextU: Float = 2.6f,
    val appLineHeightU: Float = 3.2f,
    val appLineAlpha: Float = 0.45f,
    val appLineNameAlpha: Float = 0.6f,
    val appLineShadowAlpha: Float = 0.6f,
    val appLineShadowBlurU: Float = 0.5f,
    /** The line of the app, in the corner and in the summary, wraps to no more than this. */
    val appLineMaxLines: Int = 2,

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

    // The signature of the summary (since 0.90): the icon and the line of the app, centred, at the bottom of the frame
    val signatureBottomU: Float = 6f,
    val signatureBottomLandscapeU: Float = 4f,
    val iconU: Float = 7f,
    val iconCornerU: Float = 1.6f,
    val iconGapU: Float = 2f,
    val signatureTextU: Float = 2.8f,
    val signatureLineU: Float = 3.9f,
    val signatureAlpha: Float = 0.6f,
    val signatureNameAlpha: Float = 0.9f,
    /** Between the block of the summary and the signature, at the least: the block is centred in what is left above. */
    val signatureGapU: Float = 4f,

    // The tall frame (since 0.91): a portrait at least [tallRatio] times as high as it is wide — what goes to Shorts, Reels and
    // TikTok, whose interface lies over the frame. All that is drawn keeps inside the safe zone: [safeTopU] under the top,
    // [safeBottomU] over the bottom, [safeLeftU] from the left edge and [safeRightU] from the right — the column of buttons.
    // Measured on a screenshot of YouTube Shorts on an iPhone (`docs/ideas/video-overlay/img.png`): the interface at the bottom
    // ≈ 26 u, at the top ≈ 31 u, the buttons ≈ 16 u, the edges cropped ≈ 5 u; the rest is room for the captions of Reels and TikTok.
    // The buttons stand in the lower half: the line of the app in the top right corner keeps [safeLeftU] from the right edge too
    // (since 0.93) — what the crop of the edges asks, and no more.
    val tallRatio: Float = 1.5f,
    val safeTopU: Float = 32f,
    val safeBottomU: Float = 36f,
    val safeLeftU: Float = 8f,
    val safeRightU: Float = 18f,
    /** The shade under the lane of a tall frame, which stands higher with its tag (0.91–0.92 — 92 u: a badge stood over the tag). */
    val scrimTallU: Float = 86f,
    /** How far the shade of the opening of a tall frame reaches: the name stands under the line of the app, under the safe zone's top. */
    val openingScrimTallU: Float = 60f,

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
