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
        val ring = LiveLayoutMath.designRing(landscape = true, tuning = false)
        assertEquals(260f, ring, 0f)
        // Handoff 12f: the halo of a 260 ring is 380 across, the panel is 400.
        assertTrue(ring * GlowMath.EXTENT <= 400f)
        assertEquals(1f, LiveLayoutMath.noteScale(320f), 0f)
    }

    /** Spec 3.36.6 (it changes 3.4): without the permission there is no ring — the card «нет разрешения» stands in its place. */
    @Test
    fun `design ring per state`() {
        assertEquals(300f, LiveLayoutMath.designRing(landscape = false, tuning = false), 0f)
        assertEquals(260f, LiveLayoutMath.designRing(landscape = false, tuning = true), 0f)
        assertEquals(260f, LiveLayoutMath.designRing(landscape = true, tuning = true), 0f)
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
        val ring = LiveLayoutMath.ringDiameter(LiveLayoutMath.designRing(landscape = false, tuning = true), 380f, place, LiveLayoutMath.reservedUnderRing(1f))
        assertEquals(260f, ring, 0f)
    }

    @Test
    fun `on a phone of 360 x 640 the rows of R6 take 2 dp from the ring of Настройка and 8 from the ring of Игра`() {
        // Live is 506 high. Before R6: the top row 60, the pegs 76, the status line 40 (the plate of 26 in a line of 28) and the
        // keys 100 — and the scale of 44 under the place of the ring, which kept 68 under the ring for the word
        val oldPlace = { tuning: Boolean -> 506f - 60f - (if (tuning) 76f + 44f else 0f) - 40f - 100f }
        val width = 360f - 32f
        val tuningRing = LiveLayoutMath.designRing(landscape = false, tuning = true)
        val playRing = LiveLayoutMath.designRing(landscape = false, tuning = false)
        assertEquals(118f, LiveLayoutMath.ringDiameter(tuningRing, width, oldPlace(true), 68f), 0f)
        assertEquals(238f, LiveLayoutMath.ringDiameter(playRing, width, oldPlace(false), 68f), 0f)
        val newTuning = LiveLayoutMath.ringDiameter(tuningRing, width, LiveLayoutMath.ringBlockHeight(506f, tuning = true), LiveLayoutMath.reservedUnderRing(1f))
        val newPlay = LiveLayoutMath.ringDiameter(playRing, width, LiveLayoutMath.ringBlockHeight(506f, tuning = false), LiveLayoutMath.reservedUnderRing(0f))
        assertEquals(116f, newTuning, 0f)
        assertEquals(230f, newPlay, 0f)
    }

    /**
     * The sizes of the screen are the numbers of the model: [LiveDimens] takes them from [PortraitRows] — the keys are the record key
     * of 76, whose soft shadow takes no room, with the air of 12 above and below; the strip of a take its glass of 50 under the air of
     * 8. That the screen lays its rows out so and its ring is the ring of the model is measured on the screen itself:
     * `LivePortraitColumnTest` (instrumented), 360 × 640.
     */
    @Test
    fun `the sizes of the screen are the numbers of the model`() {
        assertEquals(PortraitRows.TOP, (LiveDimens.SwitcherTopPadding + LiveDimens.TopRowHeight).value, 0f)
        assertEquals(PortraitRows.STRINGS, (LiveDimens.StringRowTopPadding + LiveDimens.StringButtonHeight).value, 0f)
        assertEquals(PortraitRows.STATUS, (LiveDimens.StatusLineTopPadding + LiveDimens.StatusLineHeight).value, 0f)
        assertEquals(PortraitRows.KEYS, (LiveDimens.KeyRowPadding * 2 + LiveDimens.RecordKeySize).value, 0f)
        assertEquals(100f, PortraitRows.KEYS, 0f)
        assertEquals(PortraitRows.RECORDING, (LiveDimens.RecordingStripTopPadding + LiveDimens.RecordingStripHeight).value, 0f)
        assertEquals(PortraitRows.WORD, (LiveDimens.IndicatorSpacing + LiveDimens.StatusRowHeight).value, 0f)
        assertEquals(PortraitRows.SCALE, (LiveDimens.ScaleTopGap + LiveDimens.ScaleHeight).value, 0f)
        assertEquals(PortraitRows.STATUS_PLATE, LiveDimens.StatusPlateHeight.value, 0f)
    }

    @Test
    fun `on a phone of 360 x 640 a running take takes 58 from the ring`() {
        // spec 3.36.6: the strip on its glass of 50 (was ≈ 24, a line of the timer) — the ring gives it 58 with its air, not ≈ 32
        val width = 360f - 32f
        val playRing = LiveLayoutMath.designRing(landscape = false, tuning = false)
        val quiet = LiveLayoutMath.ringDiameter(playRing, width, LiveLayoutMath.ringBlockHeight(506f, tuning = false), LiveLayoutMath.reservedUnderRing(0f))
        val recording = LiveLayoutMath.ringDiameter(
            playRing, width, LiveLayoutMath.ringBlockHeight(506f, tuning = false, recording = true), LiveLayoutMath.reservedUnderRing(0f),
        )
        assertEquals(58f, PortraitRows.RECORDING, 0f)
        assertEquals(230f, quiet, 0f)
        assertEquals(172f, recording, 0f)
        // the base screen has room to spare: the ring of 300 stays
        assertEquals(
            300f,
            LiveLayoutMath.ringDiameter(playRing, 380f, LiveLayoutMath.ringBlockHeight(788f, tuning = false, recording = true), LiveLayoutMath.reservedUnderRing(0f)),
            0f,
        )
    }

    @Test
    fun `both cards of the bottom row are as wide as the row allows and never wider than 150`() {
        assertEquals(150f, LiveLayoutMath.keyCardWidth(412f), 0f)
        assertEquals(124f, LiveLayoutMath.keyCardWidth(360f), 0f)
        assertEquals(104f, LiveLayoutMath.keyCardWidth(320f), 0f)
        assertEquals(150f, LiveLayoutMath.keyCardWidth(460f), 0f)
        assertEquals(104.5f, LiveLayoutMath.keyCardWidth(321f), 0f)
        // landscape: the right column of 892 × 412 is 460 wide (150), of 640 × 360 — 321 (≈ 104), behind a side cutout ≈ 300.6
        val column = { window: Float -> window * (1f - LiveDimens.LANDSCAPE_RING_PANEL_FRACTION) - (LiveDimens.LandscapePaddingStart + LiveDimens.LandscapePaddingEnd).value }
        assertEquals(150f, LiveLayoutMath.keyCardWidth(column(892f)), 0f)
        assertEquals(104.5f, LiveLayoutMath.keyCardWidth(column(640f)), 0.1f)
        assertEquals(94.3f, LiveLayoutMath.keyCardWidth(column(603f)), 0.1f)
        // the row itself: two cards, the key and their gaps within its sides
        listOf(412f, 360f, 320f).forEach { row ->
            val card = LiveLayoutMath.keyCardWidth(row)
            assertEquals(row, card * 2 + PortraitRows.KEY_ROW + LiveLayoutMath.CARD_TO_KEY * 2 + LiveLayoutMath.KEY_ROW_SIDE * 2, 0.01f, "a row of $row")
        }
        // nothing to show in a row too narrow for the key
        assertEquals(0f, LiveLayoutMath.keyCardWidth(100f), 0f)
    }

    @Test
    fun `a card narrower than 120 hides its icon`() {
        assertTrue(LiveLayoutMath.keyCardShowsIcon(150f))
        assertTrue(LiveLayoutMath.keyCardShowsIcon(124f))
        assertTrue(LiveLayoutMath.keyCardShowsIcon(120f))
        assertFalse(LiveLayoutMath.keyCardShowsIcon(119.9f))
        assertFalse(LiveLayoutMath.keyCardShowsIcon(LiveLayoutMath.keyCardWidth(320f)))
    }

    @Test
    fun `the card without the permission has its middle at 60 percent of its place and stays whole in it`() {
        assertEquals(140f, LiveLayoutMath.promptTop(400f, 200f), 0.001f)
        // a tall card rests on the bottom of its place…
        assertEquals(50f, LiveLayoutMath.promptTop(300f, 250f), 0.001f)
        // …and one taller than its place starts at its top
        assertEquals(0f, LiveLayoutMath.promptTop(200f, 260f), 0f)
        // below the middle of its place, and the button under the thumb
        val place = 580f
        val card = 299f
        val top = LiveLayoutMath.promptTop(place, card)
        assertTrue(top + card / 2 > place / 2, "the middle of the card at ${top + card / 2} of $place")
        assertEquals(0.6f * place, top + card / 2, 0.01f)
    }

    /**
     * A card «нет разрешения» of the model, in dp: its air 22 + 18 + 18, the button 54, the plate with its gap 60, the gap over the text
     * 6; a text of [textLines] lines of [line]; a title of [titleLines] lines of 28 at 22 sp (in proportion at other sizes), its words
     * whole from [wholeFrom] down.
     */
    private fun promptParts(
        textLines: Int = 3,
        line: Float = 20f,
        titleLines: (Float) -> Int = { sp -> if (sp > 18f) 2 else 1 },
        wholeFrom: Float = 22f,
    ) = LiveLayoutMath.PromptParts(
        air = 58f,
        button = 54f,
        icon = 60f,
        textGap = 6f,
        textLines = List(textLines) { line * (it + 1) },
        titleHeight = { sp -> titleLines(sp) * sp * 28f / 22f },
        titleWhole = { sp -> sp <= wholeFrom },
    )

    private fun assertPromptFit(expected: LiveLayoutMath.PromptFit, actual: LiveLayoutMath.PromptFit, what: String) {
        assertEquals(expected.copy(air = 0f), actual.copy(air = 0f), what)
        assertEquals(expected.air, actual.air, 0.001f, "$what: the air")
    }

    @Test
    fun `where the card without the permission does not stand whole first the plate of the icon goes then the text scrolls`() {
        // the whole card: 58 + 54 + 56 (the title on two lines) + 6 + 60 (three lines of the text) = 234, and the plate 60 over it
        val parts = promptParts()
        assertPromptFit(LiveLayoutMath.PromptFit(showIcon = true, titleSp = 22f, textHeight = 60f), LiveLayoutMath.promptFit(300f, parts), "300")
        assertPromptFit(LiveLayoutMath.PromptFit(showIcon = true, titleSp = 22f, textHeight = 60f), LiveLayoutMath.promptFit(294f, parts), "294")
        // the plate gives its room to the text, all of which is seen
        assertPromptFit(LiveLayoutMath.PromptFit(showIcon = false, titleSp = 22f, textHeight = 60f), LiveLayoutMath.promptFit(293f, parts), "293")
        assertPromptFit(LiveLayoutMath.PromptFit(showIcon = false, titleSp = 22f, textHeight = 60f), LiveLayoutMath.promptFit(234f, parts), "234")
    }

    @Test
    fun `the text of the card without the permission scrolls in whole lines`() {
        // 233 leaves the text 59: two whole lines of 20 are seen, not the top of the third
        assertPromptFit(LiveLayoutMath.PromptFit(showIcon = false, titleSp = 22f, textHeight = 40f), LiveLayoutMath.promptFit(233f, promptParts()), "233")
        // exactly one line
        assertPromptFit(LiveLayoutMath.PromptFit(showIcon = false, titleSp = 22f, textHeight = 20f), LiveLayoutMath.promptFit(194f, promptParts()), "194")
    }

    @Test
    fun `where not a line of the text stands the title steps down to keep one`() {
        // 360 x 640 in «Настройка»: a place of 184 leaves the text 10 under a title of two lines at 22 — the tops of a line, no text.
        // Down to 18.5 the title keeps two lines; at 18 it goes on one, and two lines of the text stand under it.
        assertPromptFit(LiveLayoutMath.PromptFit(showIcon = false, titleSp = 18f, textHeight = 40f), LiveLayoutMath.promptFit(184f, promptParts()), "184")
    }

    @Test
    fun `where not a line of the text stands at 16 it goes and the title is as large as the title and the button allow`() {
        // a large font: a line of the text is 30, the title one line; 160 leaves a line 14 at 22 and 21.6 at 16 — the text goes, and
        // the title, the button and the air (140) stand at 22
        val parts = promptParts(line = 30f, titleLines = { 1 })
        assertPromptFit(LiveLayoutMath.PromptFit(showIcon = false, titleSp = 22f, textHeight = 0f), LiveLayoutMath.promptFit(160f, parts), "160")
        // and where the title and the button do not stand at 22, the first size at which they do
        val two = promptParts(line = 30f, titleLines = { 2 })
        assertPromptFit(LiveLayoutMath.PromptFit(showIcon = false, titleSp = 17f, textHeight = 0f), LiveLayoutMath.promptFit(156f, two), "156")
    }

    @Test
    fun `where not even the title of 16 and the button stand the title goes on down to 12 then the air gives way and the button never`() {
        // a title of two lines whatever its size: 16 sp are 40.7, and 112 of the air and the button — 152.7
        val parts = promptParts(line = 30f, titleLines = { 2 })
        assertPromptFit(LiveLayoutMath.PromptFit(showIcon = false, titleSp = 12.5f, textHeight = 0f), LiveLayoutMath.promptFit(145f, parts), "145")
        // 12 sp are 30.5: the air keeps what the title and the button leave of 120 — (120 − 54 − 30.5) / 58
        val least = 2 * 12f * 28f / 22f
        assertPromptFit(LiveLayoutMath.PromptFit(showIcon = false, titleSp = 12f, textHeight = 0f, air = (120f - 54f - least) / 58f), LiveLayoutMath.promptFit(120f, parts), "120")
        // and never less than none of it: a place lower than the least card is left to the card
        assertPromptFit(LiveLayoutMath.PromptFit(showIcon = false, titleSp = 12f, textHeight = 0f, air = 0f), LiveLayoutMath.promptFit(50f, parts), "50")
    }

    @Test
    fun `the title of the card without the permission keeps each of its words whole in its line`() {
        // a word of it is wider than its line down to 20.5 sp: 20 where there is room for all, the plate too
        val parts = promptParts(wholeFrom = 20f)
        assertPromptFit(LiveLayoutMath.PromptFit(showIcon = true, titleSp = 20f, textHeight = 60f), LiveLayoutMath.promptFit(400f, parts), "400")
        // and no size of the title where a word of it would break — in the steps for a line of the text neither
        assertTrue(LiveLayoutMath.promptFit(184f, parts).titleSp <= 20f)
    }

    @Test
    fun `the sizes of the title of the card without the permission go half a point at a time`() {
        assertEquals(listOf(22f, 21.5f, 21f, 20.5f, 20f, 19.5f, 19f, 18.5f, 18f, 17.5f, 17f, 16.5f, 16f), LiveLayoutMath.promptTitleSizes(LiveLayoutMath.PROMPT_TITLE_MIN_SP))
        assertEquals(12f, LiveLayoutMath.promptTitleSizes(LiveLayoutMath.PROMPT_TITLE_LEAST_SP).last(), 0f)
        assertEquals(21, LiveLayoutMath.promptTitleSizes(LiveLayoutMath.PROMPT_TITLE_LEAST_SP).size)
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
