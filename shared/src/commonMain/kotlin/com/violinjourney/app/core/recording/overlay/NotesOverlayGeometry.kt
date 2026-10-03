package com.violinjourney.app.core.recording.overlay

import androidx.compose.ui.geometry.Rect
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/**
 * Where the parts of «Видео с нотами» stand in a frame of [width] × [height] pixels, as the player shows it (spec 5.30).
 * Pure: the painter asks it, and so do the tests. A frame no wider than it is high is a portrait (a square too); a portrait
 * half as high again as it is wide is [tall], and keeps what it draws inside the [safe] zone (since 0.91).
 */
class NotesOverlayGeometry(val width: Float, val height: Float, private val config: NotesVideoConfig) {
    val portrait: Boolean = width <= height

    /** A frame for Shorts, Reels and TikTok: their interface lies over its edges (since 0.91). */
    val tall: Boolean = portrait && height >= config.tallRatio * width

    /** A hundredth of the short side: every size of the overlay is counted in it. */
    val u: Float = min(width, height) / HUNDRED

    /** What the interface of Shorts, Reels and TikTok leaves open over a [tall] frame; null for any other. */
    val safe: Rect? = if (tall) {
        Rect(config.safeLeftU * u, config.safeTopU * u, width - config.safeRightU * u, height - config.safeBottomU * u)
    } else {
        null
    }

    // The lane

    val scrimTop: Float = height - when {
        tall -> config.scrimTallU
        portrait -> config.scrimU
        else -> config.scrimLandscapeU
    } * u
    val laneBottom: Float = safe?.bottom ?: (height - (if (portrait) config.laneBottomU else config.laneBottomLandscapeU) * u)
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

    /** The line of the tag (since 0.90): the name, the arrow — or the dot, [inTune] — and the number after it. */
    fun tagLineWidth(nameWidth: Float, numberWidth: Float, inTune: Boolean): Float =
        nameWidth + tagSignWidth(inTune) + config.tagNumberGapU * u + numberWidth + config.tagSignGapU * u

    fun tagSignWidth(inTune: Boolean): Float = (if (inTune) config.tagDotU else config.tagArrowU) * u

    /** Where the sign of a tag whose line is [lineWidth] wide begins, the name being [nameWidth]; the line is centred on the playhead. */
    fun tagSignX(lineWidth: Float, nameWidth: Float): Float = headX - lineWidth / 2 + nameWidth + config.tagSignGapU * u

    fun tagNumberX(lineWidth: Float, nameWidth: Float, inTune: Boolean): Float =
        tagSignX(lineWidth, nameWidth) + tagSignWidth(inTune) + config.tagNumberGapU * u

    // The badge

    val badgeHeight: Float = config.badgeU * u
    val badgeLeft: Float = safe?.left ?: ((if (portrait) config.badgeInsetU else config.badgeInsetLandscapeU) * u)

    /** As far from the bottom as from the left edge; in a tall frame — over the tag, out of the way of the interface below. */
    val badgeTop: Float = if (tall) tagBottom - tagHeight - config.badgeTallGapU * u - badgeHeight else height - badgeLeft - badgeHeight
    val badgeCenterY: Float = badgeTop + badgeHeight / 2
    val spinnerCenterX: Float = badgeLeft + (config.badgePadStartU + config.badgeSpinnerU / 2) * u

    /** The radius of the middle of the spinner's line: the line stays inside its [NotesVideoConfig.badgeSpinnerU]. */
    val spinnerRadius: Float = (config.badgeSpinnerU - config.spinnerLineU) / 2 * u
    val spinnerLine: Float = config.spinnerLineU * u
    val badgeTextX: Float = badgeLeft + (config.badgePadStartU + config.badgeSpinnerU + config.badgeSpinnerGapU) * u

    fun badgeWidth(textWidth: Float): Float =
        (config.badgePadStartU + config.badgeSpinnerU + config.badgeSpinnerGapU + config.badgePadEndU) * u + textWidth

    /** The line of the app right of a badge [badgeWidth] wide (since 0.90): where it begins and how wide it may grow. */
    fun appLineLeft(badgeWidth: Float): Float = badgeLeft + badgeWidth + config.appLineGapU * u

    fun appLineMaxWidth(badgeWidth: Float): Float = appLineRight - appLineLeft(badgeWidth)

    /** Where the line of the app stops: as far from the right edge as the badge from the left; in a tall frame — short of the buttons. */
    val appLineRight: Float = safe?.right ?: (width - badgeLeft)

    val appLineHeight: Float = config.appLineHeightU * u

    /**
     * The top of all the lane draws while the video runs — its shade, the tag, the badge and the line of the app beside it; the
     * dust stays under the tag. What lies above is left to the opening title.
     */
    val laneBandTop: Float = minOf(scrimTop, tagBottom - tagHeight, badgeTop)

    // The opening title (since 0.90)

    val openingScrimHeight: Float = when {
        tall -> config.openingScrimTallU
        portrait -> config.openingScrimU
        else -> config.openingScrimLandscapeU
    } * u

    /**
     * The top of the em box of the name, at rest — not of its capitals; the date's em box stands under the name's own size and a gap.
     * Text is placed by its baseline: [emAscent] is the share of the em above the baseline, the font's ascent over its ascent and
     * descent (what a canvas means by the top of a line).
     */
    val openingTitleTop: Float = when {
        tall -> config.openingTopTallU
        portrait -> config.openingTopU
        else -> config.openingTopLandscapeU
    } * u
    val openingDateTop: Float = openingTitleTop + (config.openingTitleU + config.openingDateGapU) * u

    fun openingTitleBaseline(emAscent: Float): Float = openingTitleTop + config.openingTitleU * u * emAscent

    fun openingDateBaseline(emAscent: Float): Float = openingDateTop + config.openingDateU * u * emAscent

    // The summary

    /**
     * The column of the summary and of the opening title, in the middle of the frame — of the safe zone in a tall frame, where
     * it keeps its left edge and stops short of the buttons on the right.
     */
    val columnWidth: Float = safe?.let { min(it.width, config.summaryMaxWidthU * u) } ?: min(width - config.summaryMarginsU * u, config.summaryMaxWidthU * u)
    val columnLeft: Float = safe?.let { it.left + (it.width - columnWidth) / 2 } ?: ((width - columnWidth) / 2)

    /** Where text centred in the column begins: the opening's name and date, the signature. */
    fun centredLeft(textWidth: Float): Float = columnLeft + (columnWidth - textWidth) / 2

    /** The block of the summary with [rows] rows: title, score, the line of the tolerance, the strip, the rows. */
    fun summaryHeight(rows: Int): Float = summaryRowsTopOffset() + rows * config.rowU * u

    /**
     * Where the block of [rows] rows begins: in the middle of what is left above the signature [signatureHeight] high and its gap
     * (since 0.90) — the two never touch; in a tall frame, of what is left between the top of the safe zone and the signature.
     */
    fun summaryTop(rows: Int, signatureHeight: Float): Float {
        val top = safe?.top ?: 0f
        return top + (signatureTop(signatureHeight) - config.signatureGapU * u - top - summaryHeight(rows)) / 2
    }

    // the parts of a block that begins at [top]

    fun titleBaseline(top: Float): Float = top + config.lineBaselineU * u

    fun scoreBaseline(top: Float): Float = top + (config.lineU + config.scoreGapU + config.scoreBaselineU) * u

    fun toleranceBaseline(top: Float): Float = top + (config.lineU + config.scoreGapU + config.scoreLineU + config.lineBaselineU) * u

    fun stripTop(top: Float): Float = top + (config.lineU + config.scoreGapU + config.scoreLineU + config.lineU + config.stripGapU) * u

    val stripHeight: Float = config.stripU * u

    fun rowTop(top: Float, index: Int): Float = top + summaryRowsTopOffset() + index * config.rowU * u

    fun rowBaseline(top: Float, index: Int): Float = rowTop(top, index) + (config.rowU / 2 + config.rowBaselineU) * u

    // The signature of the summary (since 0.90): the icon and one or two lines of text, centred, at the bottom

    val iconSize: Float = config.iconU * u
    val iconCorner: Float = config.iconCornerU * u
    val signatureLineHeight: Float = config.signatureLineU * u

    /** How wide the text of the signature may grow: the icon and the text together are no wider than the column. */
    val signatureTextMaxWidth: Float = columnWidth - iconSize - config.iconGapU * u

    /** The signature with text [textHeight] high: as high as the icon at the least. */
    fun signatureHeight(textHeight: Float): Float = max(iconSize, textHeight)

    /** The bottom of the signature: over the bottom of the frame, or of the safe zone in a tall frame. */
    val signatureBottom: Float = safe?.bottom ?: (height - (if (portrait) config.signatureBottomU else config.signatureBottomLandscapeU) * u)

    fun signatureTop(signatureHeight: Float): Float = signatureBottom - signatureHeight

    /** Where the icon begins: the icon, its gap and the text [textWidth] wide are centred in the column. */
    fun iconLeft(textWidth: Float): Float = centredLeft(iconSize + config.iconGapU * u + textWidth)

    fun signatureTextLeft(textWidth: Float): Float = iconLeft(textWidth) + iconSize + config.iconGapU * u

    private fun summaryRowsTopOffset(): Float =
        (config.lineU + config.scoreGapU + config.scoreLineU + config.lineU + config.stripGapU + config.stripU + config.rowsGapU) * u

    private companion object {
        const val HUNDRED = 100f
        const val MS_PER_SECOND = 1_000f
        const val HALF = 0.5f
    }
}
