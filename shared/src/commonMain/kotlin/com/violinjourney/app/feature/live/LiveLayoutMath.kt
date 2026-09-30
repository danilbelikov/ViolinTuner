package com.violinjourney.app.feature.live

/** Pure sizing rules of the Live screen. All values are in dp. */
object LiveLayoutMath {
    /**
     * Ring diameters of the handoff: portrait play, portrait tuning, landscape. Without the permission there is no ring (spec
     * 3.36.6): the card «нет разрешения» stands in its place ([promptTop], [promptFit]).
     */
    const val RING_PORTRAIT = 300f
    const val RING_TUNING = 260f

    /** 260, not the 320 of v1: the halo reaches 1.46 R and has to fit the ring panel. */
    const val RING_LANDSCAPE = 260f

    /** The 176 sp note of the handoff is drawn for this ring; smaller rings scale it down. */
    private const val NOTE_REFERENCE_RING = 300f
    private const val MIN_RING = 96f

    /**
     * The rows of portrait Live around the ring, top to bottom (spec 5.29 R6): the parts, and the rows they make with the air above
     * them. The ring gets the height they leave ([ringBlockHeight]); the sizes of the screen (`LiveDimens`) are taken from here, so
     * what this model says of the ring is what the screen draws.
     */
    object PortraitRows {
        /** Air above the top row, the string row and the plate of the status line, and above and below the keys. */
        const val AIR = 12f

        /** The row of the switcher and the gear: the touch of the gear holds its height. */
        const val TOP_ROW = 48f
        const val STRING_BUTTON = 58f
        const val STATUS_PLATE = 36f

        /** The row of the record key of 76, whose soft shadow takes no room — as much as the key of 72 on its hard shadow of 4 took before R6. */
        const val KEY_ROW = 76f

        /** The word and its cents, [WORD_GAP] under the ring. */
        const val WORD_ROW = 48f
        const val WORD_GAP = 20f

        /** The scale of «Настройка», [SCALE_GAP] under the word and the cents. */
        const val SCALE_ROW = 36f
        const val SCALE_GAP = 8f

        /** The strip of a take on its glass (spec 3.36.6, 5.29 R6), [RECORDING_GAP] under the place of the ring, over the keys. */
        const val RECORDING_STRIP = 50f
        const val RECORDING_GAP = 8f

        /** The switcher and the gear under their air: 60. */
        const val TOP = AIR + TOP_ROW

        /** The strings of «Настройка» under their air: 70 (the pegs of 3.27 were 76). */
        const val STRINGS = AIR + STRING_BUTTON

        /** The line of the status under its air: 48 (was 40, a plate of 26 in a line of 28). */
        const val STATUS = AIR + STATUS_PLATE

        /** The keys with the air above and below them: 100. */
        const val KEYS = AIR + KEY_ROW + AIR

        /** What stays under the ring inside its own place: the word and the cents with the gap above them, 68. */
        const val WORD = WORD_GAP + WORD_ROW

        /** The scale under the word in «Настройка», 44: inside the place of the ring, no longer over the keys. */
        const val SCALE = SCALE_GAP + SCALE_ROW

        /** The strip of a take with the air above it: 58 (was ≈ 32, a line of the timer) — the ring gives it up while a take runs. */
        const val RECORDING = RECORDING_GAP + RECORDING_STRIP
    }

    /**
     * The bottom row (spec 3.36.6, 5.29 R6): [KEY_ROW_SIDE] at its sides, the record key of [PortraitRows.KEY_ROW] in the middle
     * and the two cards [CARD_TO_KEY] from it, as wide as each other and never wider than [CARD_MAX]; a card narrower than
     * [CARD_ICON_FROM] hides its icon.
     */
    const val KEY_ROW_SIDE = 10f
    const val CARD_TO_KEY = 8f
    const val CARD_MAX = 150f
    const val CARD_ICON_FROM = 120f

    /**
     * The card «нет разрешения» (spec 3.36.6, 5.29 R6): its middle at [PROMPT_MIDDLE] of its place, below the middle, the button
     * under the thumb.
     */
    const val PROMPT_MIDDLE = 0.6f

    /**
     * Its title: 22 sp (spec 5.29 R6). Beyond the spec, where the card does not stand whole in its place: it steps down half a point at
     * a time to [PROMPT_TITLE_MIN_SP] to keep a whole line of the text seen, or the title and the button; below 16, down to
     * [PROMPT_TITLE_LEAST_SP] — the least size of the words of the app's buttons and cards — only where not even the title and the
     * button stand ([promptFit]).
     */
    const val PROMPT_TITLE_SP = 22f
    const val PROMPT_TITLE_MIN_SP = 16f
    const val PROMPT_TITLE_LEAST_SP = 12f
    const val PROMPT_TITLE_STEP_SP = 0.5f

    /**
     * The right column of landscape Live (spec 3.36.6, 5.29 R6): [padding] above and below it, [gap] between its rows, [scale] — the
     * height of the scale of «Настройка».
     */
    data class LandscapeColumn(val padding: Float, val gap: Float, val scale: Float)

    private val LANDSCAPE_COLUMN = LandscapeColumn(padding = 16f, gap = 8f, scale = PortraitRows.SCALE_ROW)
    private val LOW_LANDSCAPE_COLUMN = LandscapeColumn(padding = 8f, gap = 4f, scale = 28f)

    /** The air above the keys in the right column of landscape. */
    const val LANDSCAPE_KEYS_AIR = 4f

    fun isLandscape(widthDp: Float, heightDp: Float): Boolean = widthDp > heightDp

    fun designRing(landscape: Boolean, tuning: Boolean): Float = when {
        landscape -> RING_LANDSCAPE
        tuning -> RING_TUNING
        else -> RING_PORTRAIT
    }

    /**
     * The design diameter, shrunk to what is free: [reservedHeightDp] is what has to stay
     * visible under the ring inside the same block (status row, spacing).
     */
    fun ringDiameter(
        designDp: Float,
        availableWidthDp: Float,
        availableHeightDp: Float,
        reservedHeightDp: Float,
    ): Float = minOf(designDp, availableWidthDp, availableHeightDp - reservedHeightDp).coerceAtLeast(MIN_RING)

    /** Scale of the note relative to the handoff size; never enlarged, not even in the 320 ring. */
    fun noteScale(ringDp: Float): Float = (ringDp / NOTE_REFERENCE_RING).coerceAtMost(1f)

    /**
     * The height of the place of the ring in portrait (spec 5.29 R6): what the rows of [PortraitRows] leave of Live [liveHeightDp]
     * high — the strings only in «Настройка» ([tuning]), the strip of a take only while one runs ([recording]). The scale is
     * inside this place ([reservedUnderRing]).
     */
    fun ringBlockHeight(liveHeightDp: Float, tuning: Boolean, recording: Boolean = false): Float =
        liveHeightDp - PortraitRows.TOP - (if (tuning) PortraitRows.STRINGS else 0f) - PortraitRows.STATUS -
            (if (recording) PortraitRows.RECORDING else 0f) - PortraitRows.KEYS

    /**
     * What stays under the ring inside its place (spec 3.36.6: the scale right under the word and the cents): the word, and the
     * scale as far as it is shown — [scaleShown] 0…1 follows the mode in 200 ms, so the ring changes as smoothly as before, when the
     * scale unfolded under its place. At the same height of the place the ring is the same wherever the scale stands.
     */
    fun reservedUnderRing(scaleShown: Float): Float = PortraitRows.WORD + PortraitRows.SCALE * scaleShown.coerceIn(0f, 1f)

    /**
     * The right column of landscape in a window [heightDp] high (spec 5.29 R6): padding 16, gaps 8 and the scale 36 — unless that
     * column would leave the word and the cents of «Настройка» less than their whole row ([PortraitRows.WORD_ROW], 48): then the
     * window is low, and the column takes padding 8, gaps 4 and the scale 28. In both modes, so the switcher does not jump when the
     * mode changes. Low is below 378: 640 × 360 (Live 336) and a phone 393 wide lying down with its buttons at the side (Live ≈ 368)
     * are low, 892 × 412 with the buttons at the side (Live 388) is not — and a higher window never leaves the word less of its whole
     * row than a lower one does.
     */
    fun landscapeColumn(heightDp: Float): LandscapeColumn =
        if (wordRoom(LANDSCAPE_COLUMN, heightDp, tuning = true) < PortraitRows.WORD_ROW) LOW_LANDSCAPE_COLUMN else LANDSCAPE_COLUMN

    /**
     * The height the word and the cents get in the right column of landscape [heightDp] high: what the top row, the strings of
     * «Настройка» ([tuning]), the plate of the status line, the scale, the keys and the gaps between them leave (no recording).
     * 640 × 360 (Live 336) in «Настройка» — 50; before R6 it was 3.
     */
    fun landscapeWordRoom(heightDp: Float, tuning: Boolean): Float = wordRoom(landscapeColumn(heightDp), heightDp, tuning)

    /**
     * The width of each of the two cards of the bottom row [rowWidthDp] wide (spec 5.29 R6): min(150, (W − 2·10 − 76 − 2·8) / 2) —
     * 412 → 150, 360 → 124, 320 → 104; in landscape W is the width of the right column (892 × 412 → 150, 640 × 360 → ≈ 104). Both
     * cards alike and the key in the middle: the row reads as one phrase, «что играю · записать · сколько занимаюсь».
     */
    fun keyCardWidth(rowWidthDp: Float): Float =
        minOf(CARD_MAX, (rowWidthDp - 2 * KEY_ROW_SIDE - PortraitRows.KEY_ROW - 2 * CARD_TO_KEY) / 2).coerceAtLeast(0f)

    /** Whether a card [cardWidthDp] wide shows its icon: not below 120 (spec 5.29 R6), the words need its room there. */
    fun keyCardShowsIcon(cardWidthDp: Float): Boolean = cardWidthDp >= CARD_ICON_FROM

    /**
     * Where the card «нет разрешения» [cardHeight] high stands in its place [placeHeight] high (spec 3.36.6, 5.29 R6): its middle
     * at 60 % of the place, below the middle — but the whole of it in the place: a tall card rests on its bottom, one taller still
     * starts at its top. Units are the caller's.
     */
    fun promptTop(placeHeight: Float, cardHeight: Float): Float =
        maxOf(0f, minOf(PROMPT_MIDDLE * placeHeight - cardHeight / 2, placeHeight - cardHeight))

    /**
     * What of the card «нет разрешения» stands in its place: the plate of the icon or not; the size of its title; how much of its text
     * is seen — [textHeight], the end of its last whole line seen, the rest scrolls; 0 — none: the text is out of sight, and TalkBack
     * hears it with the title; [air] — how much of the air of the card is kept (the padding over the title and under the button, the
     * gap over the button): 1, but in a place lower than the least card.
     */
    data class PromptFit(val showIcon: Boolean, val titleSp: Float, val textHeight: Float, val air: Float = 1f)

    /**
     * The parts of the card «нет разрешения», in the caller's units (pixels): [air] — its padding over the title and under the button
     * and the gap over the button (22 + 18 + 18); [button] — «Разрешить доступ», 54, never less; [icon] — the plate of the icon with the
     * gap under it; [textGap] — the gap between the title and the text, which goes with the text; [textLines] — where each line of the
     * text ends, from its top; [titleHeight] — the height of the title at a size; [titleWhole] — whether every word of it stands whole
     * in its line at a size.
     */
    class PromptParts(
        val air: Float,
        val button: Float,
        val icon: Float,
        val textGap: Float,
        val textLines: List<Float>,
        val titleHeight: (sizeSp: Float) -> Float,
        val titleWhole: (sizeSp: Float) -> Boolean,
    )

    /**
     * The card «нет разрешения» in [available] of height (spec 3.36.6, 5.29 R6): where it does not stand whole, first the plate of the
     * icon goes, then the text scrolls inside the card — seen in whole lines, at least one: a window of part of a line would show the
     * tops of its letters, not a text. Beyond the spec, the title and «Разрешить доступ» always seen: the title steps down from 22 to
     * 16 sp to keep a line of the text; where not even one stands at 16, the text goes out of sight with its gap (TalkBack hears it
     * with the title) and the title is as large as the title and the button allow — down to 16, and below it, to 12, only where not
     * even they stand at 16; where not even 12 stands, the air of the card gives way. The button never gives an inch; a place lower
     * than the least card is left to the card, which then goes on past its place. Every size of the title keeps each of its words
     * whole in its line, as the size before it.
     */
    fun promptFit(available: Float, parts: PromptParts): PromptFit {
        val around = parts.air + parts.button
        val whole = parts.textLines.lastOrNull() ?: 0f
        val sizes = promptTitleSizes(PROMPT_TITLE_MIN_SP).filter(parts.titleWhole)
        // the largest size whose words stand whole — 22 but in a card narrow for a large font: the plate and the whole text with it
        sizes.firstOrNull()?.let { sp ->
            val card = around + parts.titleHeight(sp) + parts.textGap + whole
            if (card + parts.icon <= available) return PromptFit(showIcon = true, titleSp = sp, textHeight = whole)
            if (card <= available) return PromptFit(showIcon = false, titleSp = sp, textHeight = whole)
        }
        // the text scrolls: as many whole lines of it as stand, at least one — the title steps down to 16 to keep one
        for (sp in sizes) {
            val room = available - around - parts.titleHeight(sp) - parts.textGap
            val seen = parts.textLines.lastOrNull { it <= room } ?: continue
            return PromptFit(showIcon = false, titleSp = sp, textHeight = seen)
        }
        // not a line of the text stands: it goes out of sight, and the title is as large as the title and the button allow
        for (sp in promptTitleSizes(PROMPT_TITLE_LEAST_SP)) {
            if (parts.titleWhole(sp) && around + parts.titleHeight(sp) <= available) return PromptFit(showIcon = false, titleSp = sp, textHeight = 0f)
        }
        // not even the least title and the button: the air gives way, down to nothing
        val air = if (parts.air > 0f) ((available - parts.button - parts.titleHeight(PROMPT_TITLE_LEAST_SP)) / parts.air).coerceIn(0f, 1f) else 1f
        return PromptFit(showIcon = false, titleSp = PROMPT_TITLE_LEAST_SP, textHeight = 0f, air = air)
    }

    /** The sizes of the title of the card «нет разрешения», largest first: 22, 21.5, … down to [least]. */
    fun promptTitleSizes(least: Float): List<Float> = buildList {
        var sizeSp = PROMPT_TITLE_SP
        while (sizeSp >= least) {
            add(sizeSp)
            sizeSp -= PROMPT_TITLE_STEP_SP
        }
    }

    private fun wordRoom(column: LandscapeColumn, heightDp: Float, tuning: Boolean): Float {
        // the top row, the plate and the keys, a gap before each of the last two
        var taken = column.padding * 2 + PortraitRows.TOP_ROW + column.gap + PortraitRows.STATUS_PLATE + column.gap +
            column.gap + LANDSCAPE_KEYS_AIR + PortraitRows.KEY_ROW
        // the strings a gap under the top row, the scale a gap under the word
        if (tuning) taken += column.gap + PortraitRows.STRING_BUTTON + column.gap + column.scale
        return heightDp - taken
    }
}
