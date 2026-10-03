package com.violinjourney.app.core.recording.overlay

import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/**
 * Where the parts of «Видео с нотами» stand in a frame of [width] × [height] pixels, as the player shows it (spec 5.30).
 * Pure: the painter asks it, and so do the tests. A frame no wider than it is high is a portrait (a square too).
 */
class NotesOverlayGeometry(val width: Float, val height: Float, private val config: NotesVideoConfig) {
    val portrait: Boolean = width <= height

    /** A hundredth of the short side: every size of the overlay is counted in it. */
    val u: Float = min(width, height) / HUNDRED

    // The lane

    val scrimTop: Float = height - (if (portrait) config.scrimU else config.scrimLandscapeU) * u
    val laneBottom: Float = height - (if (portrait) config.laneBottomU else config.laneBottomLandscapeU) * u
    val laneTop: Float = laneBottom - (if (portrait) config.laneU else config.laneLandscapeU) * u
    val pillHeight: Float = config.pillU * u
    val headX: Float = width * config.headShare
    private val pxPerMs: Float = config.speedUPerSecond * u / MS_PER_SECOND

    /** Where a moment [atMs] of the video stands while the frame of [nowMs] is shown: «now» is the playhead. */
    fun x(atMs: Long, nowMs: Long): Float = headX + (atMs - nowMs) * pxPerMs

    /** The span of the video the lane shows at [nowMs] — what lies wholly outside it is off the frame. */
    fun shownFromMs(nowMs: Long): Long = nowMs - ceil(headX / pxPerMs).toLong()

    fun shownToMs(nowMs: Long): Long = nowMs + ceil((width - headX) / pxPerMs).toLong()

    /** The end of a capsule: cut short so that two notes in a row stay two. */
    fun pillRight(endMs: Long, nowMs: Long): Float = x(endMs, nowMs) - config.pillGapU * u

    /** The middle of the capsule of [midi] between [lowMidi] and [highMidi]: the low edge of the lane is the lowest note. */
    fun pillCenterY(midi: Int, lowMidi: Float, highMidi: Float): Float {
        val share = if (highMidi > lowMidi) (midi - lowMidi) / (highMidi - lowMidi) else HALF
        return laneBottom - pillHeight / 2 - share * (laneBottom - laneTop - pillHeight)
    }

    /** The name of a note goes in only where it and its air fit the capsule. */
    fun labelFits(labelWidth: Float, pillWidth: Float): Boolean = labelWidth + config.labelRoomU * u <= pillWidth

    val labelInset: Float = config.labelInsetU * u

    // The playhead and the tag

    val tagHeight: Float = config.tagU * u
    val tagBottom: Float = laneTop - config.tagGapU * u
    val playheadWidth: Float = config.playheadU * u
    val playheadTop: Float = tagBottom
    val playheadBottom: Float = laneBottom + config.playheadOverhangU * u

    fun tagWidth(textWidth: Float): Float = max(config.tagMinWidthU * u, textWidth + config.tagPadU * u)

    // The badge

    val badgeHeight: Float = config.badgeU * u
    val badgeLeft: Float = (if (portrait) config.badgeInsetU else config.badgeInsetLandscapeU) * u
    val badgeTop: Float = height - badgeLeft - badgeHeight
    val badgeDotCenterX: Float = badgeLeft + (config.badgePadStartU + config.badgeDotU / 2) * u
    val badgeTextX: Float = badgeLeft + (config.badgePadStartU + config.badgeDotU + config.badgeDotGapU) * u

    fun badgeWidth(textWidth: Float): Float =
        (config.badgePadStartU + config.badgeDotU + config.badgeDotGapU + config.badgePadEndU) * u + textWidth

    // The summary

    val columnWidth: Float = min(width - config.summaryMarginsU * u, config.summaryMaxWidthU * u)
    val columnLeft: Float = (width - columnWidth) / 2

    /** The block of the summary with [rows] rows: title, score, the line of the tolerance, the strip, the rows. */
    fun summaryHeight(rows: Int): Float = summaryRowsTopOffset() + rows * config.rowU * u

    /** Where the block of [rows] rows begins: in the middle of the frame. */
    fun summaryTop(rows: Int): Float = (height - summaryHeight(rows)) / 2

    fun titleBaseline(rows: Int): Float = summaryTop(rows) + config.lineBaselineU * u

    fun scoreBaseline(rows: Int): Float = summaryTop(rows) + (config.lineU + config.scoreGapU + config.scoreBaselineU) * u

    fun toleranceBaseline(rows: Int): Float =
        summaryTop(rows) + (config.lineU + config.scoreGapU + config.scoreLineU + config.lineBaselineU) * u

    fun stripTop(rows: Int): Float = summaryTop(rows) + (config.lineU + config.scoreGapU + config.scoreLineU + config.lineU + config.stripGapU) * u

    val stripHeight: Float = config.stripU * u

    fun rowTop(rows: Int, index: Int): Float = summaryTop(rows) + summaryRowsTopOffset() + index * config.rowU * u

    fun rowBaseline(rows: Int, index: Int): Float = rowTop(rows, index) + (config.rowU / 2 + config.rowBaselineU) * u

    private fun summaryRowsTopOffset(): Float =
        (config.lineU + config.scoreGapU + config.scoreLineU + config.lineU + config.stripGapU + config.stripU + config.rowsGapU) * u

    private companion object {
        const val HUNDRED = 100f
        const val MS_PER_SECOND = 1_000f
        const val HALF = 0.5f
    }
}
