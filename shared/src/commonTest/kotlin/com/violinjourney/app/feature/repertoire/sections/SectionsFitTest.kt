package com.violinjourney.app.feature.repertoire.sections

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * How the tab «Репертуар» stands in its window and how its tiles share a column (spec 3.36.4, 5.29 R4). The widths of the words are
 * those of Manrope 800 at 16 sp with the tracking of titleMedium (0.15 sp), measured on `manrope_variable.ttf`: «Произведения» —
 * 120.1 dp, the widest word of the ten languages; «Stricharten» 93.2; «d'arco» 50.5. A width follows the size in proportion, and a
 * system font scales it (linearly here; the curve of Android 14 gives the same sizes at 1.15 and 1.3). The words of a row of one's
 * own — the widest word of its count, Manrope 600 at 13 sp with the tracking of bodySmall (0.4 sp): «выучено» 57.4, «gelernt:»
 * 52.9, «appris» 46.3, «empty» 41.8, «aprendido:» 71.5; the first letter of its name before the ellipsis, «M…», is 25.9.
 */
class SectionsFitTest {
    /** What the left column needs at [fontScale]: [wordAt16] of the names (at 13 sp on a lone tile), the words of the own row and the button. */
    private fun need(wordAt16: Float, ownText: Float, button: Float, fontScale: Float = 1f): Dp =
        SectionsLayout.leftNeed(widestWord = (wordAt16 * TileFit.MIN_NAME_SP / TileFit.NAME_SP * fontScale).dp, ownText = (ownText * fontScale).dp, button = button.dp)

    /** Russian at font 1.0: the row of one's own (88 + 36 + 57.4 = 181.4) is wider than the lone tile (170.6) and the button. */
    private val russianNeed = need(PIECES, RU_COUNT_WORD, RU_BUTTON_COMPACT)

    private fun layout(width: Number, height: Int, loading: Boolean = false, hasTime: Boolean = true, leftNeed: Dp = russianNeed) =
        SectionsLayout.of(width.toFloat().dp, height.dp, loading = loading, hasTime = hasTime, leftNeed = leftNeed)

    /** The content of the left column of landscape in a window [width] wide: the time column and the fields of 16 taken away. */
    private fun leftContent(width: Int): Dp = width.dp - SectionsLayout.TimeColumn - 32.dp

    /** The content of one column upright: up to 560, the fields of 16 taken away. */
    private fun uprightContent(width: Int): Dp = minOf(width.dp, 560.dp) - 32.dp

    private fun word(atSixteen: Float, fontScale: Float = 1f): (Float) -> Dp = { sizeSp -> (atSixteen * sizeSp / TileFit.NAME_SP * fontScale).dp }

    @Test
    fun `taller than wide is upright whatever the data`() {
        assertEquals(SectionsLayout.Kind.Upright, layout(412, 892))
        assertEquals(SectionsLayout.Kind.Upright, layout(360, 640, loading = true))
        assertEquals(SectionsLayout.Kind.Upright, layout(600, 600), "a square is not wider than tall")
    }

    @Test
    fun `what the left column needs is the widest of the lone tile the own row and the button`() {
        assertEquals(170.58f, TileFit.oneColumnNeed((PIECES * 13f / 16f).dp).value, 0.01f, "12 + 36 + 10 + Произведения at 13 sp 97.6 + 14 and a pixel of slack")
        assertEquals(181.4f, SectionsLayout.ownRowNeed(RU_COUNT_WORD.dp).value, 0.01f, "14 + 36 + 12 + выучено 57.4 + 12 + the bar of 36 + 14")
        assertEquals(181.4f, russianNeed.value, 0.01f, "Russian at 1.0: the row of one's own")
        assertEquals(165.8f, need(STROKES, EN_COUNT_WORD, EN_BUTTON_COMPACT).value, 0.01f, "English: the row of one's own")
        assertEquals(176.9f, need(STRICHARTEN, DE_COUNT_WORD, DE_BUTTON_COMPACT).value, 0.01f, "German: the row of one's own")
        assertEquals(170.3f, need(MORCEAUX, FR_COUNT_WORD, FR_BUTTON_COMPACT).value, 0.01f, "French: the row of one's own")
        assertEquals(199.85f, need(PIECES, RU_COUNT_WORD, RU_BUTTON_COMPACT, fontScale = 1.3f).value, 0.01f, "Russian at 1.3: the lone tile")
        assertEquals(210f, need(STROKES, EN_COUNT_WORD, 210f).value, 0.001f, "a wide button")
    }

    @Test
    fun `the bar of the own row gives way to its words in a narrow column down to the plate`() {
        assertEquals(84.dp, SectionsLayout.ownRowBar(leftContent(892), RU_COUNT_WORD.dp))
        assertEquals(84.dp, SectionsLayout.ownRowBar(leftContent(640), RU_COUNT_WORD.dp), "640 x 360 keeps the bar of the spec")
        assertEquals(46.6f, SectionsLayout.ownRowBar(leftContent(600), RU_COUNT_WORD.dp).value, 0.01f, "192 - 88 - 57.4")
        assertEquals(36.dp, SectionsLayout.ownRowBar(150.dp, RU_COUNT_WORD.dp), "never shorter than the plate")
        assertEquals(84.dp, SectionsLayout.ownRowBar(328.dp, PT_COUNT_WORD.dp), "upright on 360")
    }

    @Test
    fun `landscape with the time has two columns while the left column holds what it needs`() {
        assertEquals(484.dp, leftContent(892), "the dock of landscape html")
        assertEquals(232.dp, leftContent(640), "640 - 16 - 16 - 16 - 360")
        assertEquals(SectionsLayout.Kind.Columns, layout(892, 412))
        assertEquals(SectionsLayout.Kind.Columns, layout(640, 360), "the narrowest landscape of the spec")
        assertEquals(SectionsLayout.Kind.Columns, layout(610, 360), "640 with a cutout of 30 at its side - 202 inside")
        assertEquals(SectionsLayout.Kind.Columns, layout(603.5f, 360), "the Pixel 7 of the emulator - a cutout of 36.5")
        assertEquals(SectionsLayout.Kind.Columns, layout(600, 360), "a cutout of 40 - 192 inside")
        assertEquals(SectionsLayout.Kind.Columns, layout(592, 360), "640 with the three buttons of navigation at its side - 184 inside")
        for ((language, languageNeed) in listOf(
            "en" to need(STROKES, EN_COUNT_WORD, EN_BUTTON_COMPACT),
            "de" to need(STRICHARTEN, DE_COUNT_WORD, DE_BUTTON_COMPACT),
            "fr" to need(MORCEAUX, FR_COUNT_WORD, FR_BUTTON_COMPACT),
        )) {
            assertEquals(SectionsLayout.Kind.Columns, layout(600, 360, leftNeed = languageNeed), "$language on 600")
        }
        assertEquals(SectionsLayout.Kind.Columns, layout(610, 360, leftNeed = need(80f, 65f, 196f)), "Japanese - its button measured whole")
    }

    @Test
    fun `a window wider than tall whose left column would not hold it is one column with the time under the sections`() {
        assertEquals(SectionsLayout.Kind.Wide, layout(589, 360), "181 inside")
        assertEquals(SectionsLayout.Kind.Wide, layout(560, 360))
        assertEquals(SectionsLayout.Kind.Wide, layout(412, 336), "the upper half of a split screen")
        assertEquals(SectionsLayout.Kind.Wide, layout(457, 412), "a split screen on its side")
        val largeFont = need(PIECES, RU_COUNT_WORD, RU_BUTTON_COMPACT, fontScale = 1.3f)
        assertEquals(SectionsLayout.Kind.Wide, layout(603.5f, 360, leftNeed = largeFont), "Russian at 1.3 - Произведения would not fit its lone tile")
        assertEquals(SectionsLayout.Kind.Columns, layout(610, 360, leftNeed = largeFont), "but does with a cutout of 30")
        assertEquals(SectionsLayout.Kind.Wide, layout(600, 360, leftNeed = need(PIECES, PT_COUNT_WORD, RU_BUTTON_COMPACT)), "Portuguese on 600 - «aprendido:»")
    }

    @Test
    fun `an empty repertoire in landscape is one column and while the data is read the columns wait for the time`() {
        assertEquals(SectionsLayout.Kind.Wide, layout(892, 412, hasTime = false))
        assertEquals(SectionsLayout.Kind.Columns, layout(892, 412, loading = true, hasTime = false))
        assertEquals(SectionsLayout.Kind.Wide, layout(560, 360, loading = true, hasTime = false))
    }

    @Test
    fun `the count stands beside the title when both fit and under it otherwise`() {
        // «Репертуар» 24 sp 125.7, the gap 12, «выучено 13 из 15» 14 sp about 111
        assertTrue(SectionsLayout.countBesideTitle(title = 126, gap = 12, count = 111, room = 484), "892 x 412")
        assertFalse(SectionsLayout.countBesideTitle(title = 126, gap = 12, count = 111, room = 232), "640 x 360")
        assertTrue(SectionsLayout.countBesideTitle(title = 126, gap = 12, count = 94, room = 232), "exactly as wide as the column")
    }

    @Test
    fun `in landscape two columns hold the widest word at 16 sp or the tiles go one under another`() {
        assertEquals(2, TileFit.columns(TileLook.Beside, leftContent(892), PIECES.dp), "892 x 412 - 164 for the name")
        assertEquals(2, TileFit.columns(TileLook.Beside, leftContent(814), PIECES.dp), "an iPhone 15 Pro Max on its side - 125")
        assertEquals(1, TileFit.columns(TileLook.Beside, leftContent(740), PIECES.dp), "a Galaxy S9 - 88 would cut Произведения")
        assertEquals(1, TileFit.columns(TileLook.Beside, leftContent(734), PIECES.dp), "an iPhone 15 without its safe sides")
        assertEquals(2, TileFit.columns(TileLook.Beside, leftContent(740), D_ARCO.dp), "the Italian words fit there")
    }

    @Test
    fun `a column narrower than 320 has one tile a row even when the words would fit`() {
        assertEquals(1, TileFit.columns(TileLook.Beside, 319.dp, 40.dp))
        assertEquals(1, TileFit.columns(TileLook.Beside, leftContent(640), D_ARCO.dp))
        assertEquals(2, TileFit.columns(TileLook.Beside, 400.dp, 40.dp))
    }

    @Test
    fun `upright tiles stand two a row whatever the words`() {
        assertEquals(2, TileFit.columns(TileLook.Upright, uprightContent(360), 1_000.dp))
    }

    @Test
    fun `the room of a name is the tile without its fields and plate`() {
        assertEquals(130.dp, TileFit.nameRoom(TileLook.Upright, uprightContent(360), 2), "(328 - 10) / 2 - 28 and a pixel of slack")
        assertEquals(164.dp, TileFit.nameRoom(TileLook.Beside, leftContent(892), 2), "(484 - 10) / 2 - 72 and a pixel of slack")
        assertEquals(159.dp, TileFit.nameRoom(TileLook.Beside, leftContent(640), 1), "232 - 72 and a pixel of slack")
    }

    @Test
    fun `upright names keep 16 sp where the widest word fits`() {
        val room = TileFit.nameRoom(TileLook.Upright, uprightContent(360), 2)
        assertEquals(16f, TileFit.nameSize(room, word(PIECES)))
        assertEquals(16f, TileFit.nameSize(TileFit.nameRoom(TileLook.Upright, uprightContent(412), 2), word(PIECES, fontScale = 1.15f)), "412 has 156")
        assertEquals(16f, TileFit.nameSize(room, word(STRICHARTEN, fontScale = 1.3f)), "German fits at 360 with 1.3")
    }

    @Test
    fun `upright names on 360 step down together until the widest word fits`() {
        val room = TileFit.nameRoom(TileLook.Upright, uprightContent(360), 2)
        // 1.15: 16 sp - 138 and 15.5 sp - 134 are wider than 130; 15 sp - 129.5 fits
        assertEquals(15f, TileFit.nameSize(room, word(PIECES, fontScale = 1.15f)))
        // 1.3: 14 sp - 136.6 and 13.5 sp - 131.7 are still wider; 13 sp - 126.9 fits
        assertEquals(13f, TileFit.nameSize(room, word(PIECES, fontScale = 1.3f)))
    }

    @Test
    fun `below 13 sp the names do not go`() {
        val room = TileFit.nameRoom(TileLook.Upright, uprightContent(360), 2)
        assertEquals(13f, TileFit.nameSize(room, word(PIECES, fontScale = 1.5f)))
        assertEquals(13f, TileFit.nameSize(0.dp) { 1_000.dp })
    }

    @Test
    fun `sizes are tried from 16 down by half an sp`() {
        val asked = mutableListOf<Float>()
        val size = TileFit.nameSize(TileFit.nameRoom(TileLook.Upright, uprightContent(360), 2)) { sizeSp ->
            asked += sizeSp
            word(PIECES, fontScale = 1.15f)(sizeSp)
        }
        assertEquals(15f, size)
        assertEquals(listOf(16f, 15.5f, 15f), asked)
    }

    @Test
    fun `the words of the names are split at spaces only`() {
        assertEquals(
            listOf("Произведения", "Coups", "d'archet", "Golpes de", "arco", "練習曲"),
            TileFit.words(listOf("Произведения", "Coups d'archet", "Golpes de arco", "練習曲", "Coups")),
            "a no-break space keeps its words together - a name without spaces is one word - a word is measured once",
        )
    }

    @Test
    fun `the bars grow from nothing the first time and stand grown after that`() {
        assertEquals(0f, PieceTimeGrowth.start(reduceMotion = false, grownBefore = false))
        assertEquals(1f, PieceTimeGrowth.start(reduceMotion = false, grownBefore = true), "scrolled out of sight and back")
        assertEquals(1f, PieceTimeGrowth.start(reduceMotion = true, grownBefore = false))
    }

    @Test
    fun `each bar starts 40 ms after the one above and the rows past the folded ones stand`() {
        assertEquals(500, PieceTimeGrowth.totalMs(3), "420 + 2 x 40")
        assertEquals(420, PieceTimeGrowth.totalMs(1))
        assertEquals(0f, PieceTimeGrowth.progress(0f, 0, 3))
        assertEquals(1f, PieceTimeGrowth.progress(1f, 2, 3))
        // at 80 ms of 500 the first row is 80 / 420 grown, the third not yet started
        assertEquals(80f / 420f, PieceTimeGrowth.progress(80f / 500f, 0, 3), 0.0001f)
        assertEquals(0f, PieceTimeGrowth.progress(80f / 500f, 2, 3))
        assertEquals(1f, PieceTimeGrowth.progress(0f, 3, 3), "the fourth row comes with «Все N» grown")
    }

    private companion object {
        const val PIECES = 120.1f
        const val STRICHARTEN = 93.2f
        const val D_ARCO = 50.5f

        const val STROKES = 60.8f
        const val MORCEAUX = 79.1f

        /** The widest word of the count of a row of one's own, 13 sp / 600. */
        const val RU_COUNT_WORD = 57.4f
        const val EN_COUNT_WORD = 41.8f
        const val DE_COUNT_WORD = 52.9f
        const val FR_COUNT_WORD = 46.3f
        const val PT_COUNT_WORD = 71.5f

        /** The button of the zone of 48 — 16 + 20 + 8 + its widest word at 15 sp + 16: «репертуар» 81.9, «Repertoire» 80.5, «hinzufügen» 85.4, «répertoire» 76.6. */
        const val RU_BUTTON_COMPACT = 141.9f
        const val EN_BUTTON_COMPACT = 140.5f
        const val DE_BUTTON_COMPACT = 145.4f
        const val FR_BUTTON_COMPACT = 136.6f
    }
}
