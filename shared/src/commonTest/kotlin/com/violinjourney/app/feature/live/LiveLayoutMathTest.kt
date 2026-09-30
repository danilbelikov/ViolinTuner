package com.violinjourney.app.feature.live

import com.violinjourney.app.feature.live.LiveLayoutMath.LandscapeColumn
import com.violinjourney.app.feature.live.LiveLayoutMath.PortraitRows
import com.violinjourney.app.feature.live.components.LiveDimens
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.Test

class LiveLayoutMathTest {
    // status row 48 + the gap between it and the ring 20 (handoff Live 2)
    private val statusAndSpacing = 48f + 20f

    @Test
    fun `base screen keeps the handoff sizes`() {
        // 412 x 892 portrait: the indicator block gets roughly 412 x 560
        assertEquals(300f, LiveLayoutMath.ringDiameter(300f, 364f, 560f, statusAndSpacing), 0f)
        assertEquals(1f, LiveLayoutMath.noteScale(300f), 0f)
    }

    @Test
    fun `small phone shrinks the ring to the free height`() {
        // 320 x 568: about 300 dp are left for the indicator block
        val ring = LiveLayoutMath.ringDiameter(300f, 272f, 300f, statusAndSpacing)
        assertEquals(232f, ring, 0f)
        assertEquals(232f / 300f, LiveLayoutMath.noteScale(ring), 1e-6f)
    }

    @Test
    fun `narrow screen shrinks the ring to the free width`() {
        assertEquals(250f, LiveLayoutMath.ringDiameter(300f, 250f, 700f, statusAndSpacing), 0f)
    }

    @Test
    fun `ring never grows beyond the design nor collapses`() {
        assertEquals(300f, LiveLayoutMath.ringDiameter(300f, 2_000f, 2_000f, 0f), 0f)
        assertEquals(96f, LiveLayoutMath.ringDiameter(300f, 400f, 120f, statusAndSpacing), 0f)
    }

    @Test
    fun `landscape ring leaves room for its halo inside the panel — and a note is never enlarged`() {
        val ring = LiveLayoutMath.designRing(landscape = true, tuning = false, noMic = false)
        assertEquals(260f, ring, 0f)
        // Handoff 12f: the halo of a 260 ring is 380 across, the panel is 400.
        assertTrue(ring * GlowMath.EXTENT <= 400f)
        assertEquals(1f, LiveLayoutMath.noteScale(320f), 0f)
    }

    @Test
    fun `design ring per state`() {
        assertEquals(300f, LiveLayoutMath.designRing(landscape = false, tuning = false, noMic = false), 0f)
        assertEquals(260f, LiveLayoutMath.designRing(landscape = false, tuning = true, noMic = false), 0f)
        assertEquals(200f, LiveLayoutMath.designRing(landscape = true, tuning = true, noMic = true), 0f)
    }

    @Test
    fun `orientation follows the shape of the screen`() {
        assertTrue(LiveLayoutMath.isLandscape(892f, 412f))
        assertFalse(LiveLayoutMath.isLandscape(412f, 892f))
        assertFalse(LiveLayoutMath.isLandscape(600f, 600f))
    }

    @Test
    fun `the room under the ring is the word and as much of the scale as is shown`() {
        assertEquals(68f, LiveLayoutMath.reservedUnderRing(0f), 0f)
        assertEquals(112f, LiveLayoutMath.reservedUnderRing(1f), 0f)
        assertEquals(90f, LiveLayoutMath.reservedUnderRing(0.5f), 0f)
    }

    @Test
    fun `the scale moved under the word leaves the ring as it was at the same height of its place`() {
        // before R6 the scale of 44 stood under the place of the ring, which kept 68 for the word; now it is inside, 44 taller
        listOf(150f, 186f, 228f, 300f, 398f, 510f).forEach { place ->
            assertEquals(
                LiveLayoutMath.ringDiameter(260f, 328f, place, 68f),
                LiveLayoutMath.ringDiameter(260f, 328f, place + 44f, LiveLayoutMath.reservedUnderRing(1f)),
                0f,
                "a place of $place",
            )
        }
    }

    @Test
    fun `on the base screen the ring of Настройка stays 260`() {
        // 412 x 892: Live is 788 high above the tabs; the ring may be 412 - 2 x 16 wide
        val place = LiveLayoutMath.ringBlockHeight(788f, tuning = true)
        assertEquals(510f, place, 0f)
        val ring = LiveLayoutMath.ringDiameter(LiveLayoutMath.designRing(landscape = false, tuning = true, noMic = false), 380f, place, LiveLayoutMath.reservedUnderRing(1f))
        assertEquals(260f, ring, 0f)
    }

    @Test
    fun `on a phone of 360 x 640 the rows of R6 take 2 dp from the ring of Настройка and 8 from the ring of Игра`() {
        // Live is 506 high. Before R6: the top row 60, the pegs 76, the status line 40 (the plate of 26 in a line of 28) and the
        // keys 100 — and the scale of 44 under the place of the ring, which kept 68 under the ring for the word
        val oldPlace = { tuning: Boolean -> 506f - 60f - (if (tuning) 76f + 44f else 0f) - 40f - 100f }
        val width = 360f - 32f
        val tuningRing = LiveLayoutMath.designRing(landscape = false, tuning = true, noMic = false)
        val playRing = LiveLayoutMath.designRing(landscape = false, tuning = false, noMic = false)
        assertEquals(118f, LiveLayoutMath.ringDiameter(tuningRing, width, oldPlace(true), 68f), 0f)
        assertEquals(238f, LiveLayoutMath.ringDiameter(playRing, width, oldPlace(false), 68f), 0f)
        val newTuning = LiveLayoutMath.ringDiameter(tuningRing, width, LiveLayoutMath.ringBlockHeight(506f, tuning = true), LiveLayoutMath.reservedUnderRing(1f))
        val newPlay = LiveLayoutMath.ringDiameter(playRing, width, LiveLayoutMath.ringBlockHeight(506f, tuning = false), LiveLayoutMath.reservedUnderRing(0f))
        assertEquals(116f, newTuning, 0f)
        assertEquals(230f, newPlay, 0f)
    }

    /**
     * The sizes of the screen are the numbers of the model: [LiveDimens] takes them from [PortraitRows], and the keys — the record
     * key of 72 on its hard shadow of 4, sized on their own — make the row of the model. That the screen lays its rows out so and
     * its ring is the ring of the model is measured on the screen itself: `LiveTopRowTest` (instrumented), 360 × 640.
     */
    @Test
    fun `the sizes of the screen are the numbers of the model`() {
        assertEquals(PortraitRows.TOP, (LiveDimens.SwitcherTopPadding + LiveDimens.TopRowHeight).value, 0f)
        assertEquals(PortraitRows.STRINGS, (LiveDimens.StringRowTopPadding + LiveDimens.StringButtonHeight).value, 0f)
        assertEquals(PortraitRows.STATUS, (LiveDimens.StatusLineTopPadding + LiveDimens.StatusLineHeight).value, 0f)
        assertEquals(PortraitRows.KEYS, (LiveDimens.RecordPaddingVertical * 2 + LiveDimens.RecordButtonSize + LiveDimens.RecordShadow).value, 0f)
        assertEquals(PortraitRows.WORD, (LiveDimens.IndicatorSpacing + LiveDimens.StatusRowHeight).value, 0f)
        assertEquals(PortraitRows.SCALE, (LiveDimens.ScaleTopGap + LiveDimens.ScaleHeight).value, 0f)
        assertEquals(PortraitRows.STATUS_PLATE, LiveDimens.StatusPlateHeight.value, 0f)
    }

    @Test
    fun `the right column of landscape is tighter in a low window whatever the mode`() {
        val tall = LandscapeColumn(padding = 16f, gap = 8f, scale = 36f)
        val low = LandscapeColumn(padding = 8f, gap = 4f, scale = 28f)
        // 892 x 412 with the buttons at the side leaves Live 388; 640 x 360 leaves it 336
        assertEquals(tall, LiveLayoutMath.landscapeColumn(412f))
        assertEquals(tall, LiveLayoutMath.landscapeColumn(388f))
        assertEquals(low, LiveLayoutMath.landscapeColumn(360f))
        assertEquals(low, LiveLayoutMath.landscapeColumn(336f))
        // a phone 393 wide lying down with its buttons at the side (Live ≈ 368) and an iPhone of 393 (372): the tall column would
        // leave the word of «Настройка» 38 and 42 of its 48 — they are low too
        assertEquals(low, LiveLayoutMath.landscapeColumn(368f))
        assertEquals(low, LiveLayoutMath.landscapeColumn(372f))
        // the tall column takes 330 in «Настройка»: from 378 up it leaves the word its whole row
        assertEquals(low, LiveLayoutMath.landscapeColumn(377.9f))
        assertEquals(tall, LiveLayoutMath.landscapeColumn(378f))
    }

    @Test
    fun `a higher landscape window never leaves the word less of its row than a lower one`() {
        // whatever the column: the room of the word in «Настройка», counted up to its whole row of 48, only grows with the window
        var lower = LiveLayoutMath.landscapeWordRoom(300f, tuning = true).coerceAtMost(PortraitRows.WORD_ROW)
        for (tenths in 3_001..5_000) {
            val height = tenths / 10f
            val room = LiveLayoutMath.landscapeWordRoom(height, tuning = true).coerceAtMost(PortraitRows.WORD_ROW)
            assertTrue(room >= lower, "a window of $height leaves the word $room, a lower one $lower")
            lower = room
        }
    }

    @Test
    fun `on a landscape of 640 x 360 the word and the cents keep their room in Настройка`() {
        val room = LiveLayoutMath.landscapeWordRoom(336f, tuning = true)
        assertTrue(room in 46f..50f, "the word has $room")
        assertEquals(50f, room, 0f)
        // the column of a high window would leave it 6 of the 40 it needs
        assertEquals(144f, LiveLayoutMath.landscapeWordRoom(336f, tuning = false), 0f)
        // 892 x 412 with the buttons at the side: Live 388 high
        assertEquals(58f, LiveLayoutMath.landscapeWordRoom(388f, tuning = true), 0f)
        // the phone of 393 lying down: 82, its whole row, where the column of 16 / 8 / 36 left it 38
        assertEquals(82f, LiveLayoutMath.landscapeWordRoom(368f, tuning = true), 0f)
    }
}
