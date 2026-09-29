package com.violinjourney.app.feature.repertoire.piece

import androidx.compose.ui.unit.dp
import com.violinjourney.app.core.ui.components.SegmentFit
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The words of an element's screen never break by the letter (the review of stage 109, spec 3.36.4): the name of the backing beside
 * the notes lying, and the two tiles of an element without pages. Widths in dp — Manrope (PIL, the wght axis, the tracking of the
 * style): «клавесин» 15 sp / 700 — 71.3, «минусовка» 13 sp / 400 — 70.0; «Сфотографировать» 13 sp / 700 — 128.6 at the font 1.0,
 * 149.3 at 1.15 and 170.1 at 1.3 (the curve of Android 14), «Из галереи» 73.1 / 84.9 / 96.7, «галереи» 54.1 / 62.9 / 71.6.
 */
class PieceFitTest {
    private val nameWord = 71.3.dp

    @Test
    fun `in 640 by 360 the backing stands over the notes — its name would keep 42 dp`() {
        // the right column: 640 − 300 − 16
        val content = 324.dp
        assertEquals(42.3f, BackingFit.nameColumn(BackingFit.cardBeside(content)).value, 0.1f)
        assertFalse(BackingFit.besideNotes(content, nameWord))
        assertFalse(BackingFit.besideNotes(content - 40.dp, nameWord), "and with a cutout at its side")
    }

    @Test
    fun `in 892 by 412 and 740 by 360 the backing and the notes stand side by side 4 to 3`() {
        assertTrue(BackingFit.besideNotes(576.dp, nameWord))
        assertEquals(186.3f, BackingFit.nameColumn(BackingFit.cardBeside(576.dp)).value, 0.1f)
        assertTrue(BackingFit.besideNotes(424.dp, nameWord), "a Galaxy S9 on its side")
    }

    @Test
    fun `a name of one long word stands over the notes where it would break beside them`() {
        // «Concerto_in_A_minor.mp3» — 203.6
        assertFalse(BackingFit.besideNotes(576.dp, 203.6.dp))
    }

    private val around = listOf(16f, 16f)

    private fun names(camera: Float, gallery: Float, galleryWord: Float): (Float) -> List<SegmentFit.Label> = { sizeSp ->
        val k = sizeSp / EmptySheetsFit.NAME_SP
        listOf(SegmentFit.Label(camera * k, camera * k), SegmentFit.Label(gallery * k, galleryWord * k))
    }

    private val ruAt10 = names(128.6f, 73.1f, 54.1f)
    private val ruAt115 = names(149.3f, 84.9f, 62.9f)
    private val ruAt13 = names(170.1f, 96.7f, 71.6f)

    private fun plan(row: Float, tile: Float?, labels: (Float) -> List<SegmentFit.Label>) =
        EmptySheetsFit.plan(row, tile, gap = 8f, around = around, slack = 1f, labelsAt = labels)

    @Test
    fun `upright the tiles keep 150 while both names fit them`() {
        val plan = plan(328f, tile = 150f, ruAt10)
        assertEquals(EmptySheetsFit.NAME_SP, plan.sizeSp)
        assertEquals(listOf(150f, 150f), plan.widths)
    }

    @Test
    fun `upright with a larger font the tiles widen to the row rather than break «Сфотографировать»`() {
        for ((font, labels) in listOf("1_15" to ruAt115, "1_3" to ruAt13)) {
            val plan = plan(328f, tile = 150f, labels)
            assertEquals(EmptySheetsFit.NAME_SP, plan.sizeSp, "at $font")
            assertTrue(plan.widths[0] >= labels(13f)[0].word + 17f, "«Сфотографировать» whole at $font: ${plan.widths}")
            assertTrue(abs(plan.widths.sum() - 320f) < 0.01f, "the row: ${plan.widths}")
        }
    }

    @Test
    fun `lying the tiles share the column by their words and step down only at a large font`() {
        // the left column of 300 without its fields: 268, halves of 130 leave «Сфотографировать» 114
        val plain = plan(268f, tile = null, ruAt10)
        assertEquals(EmptySheetsFit.NAME_SP, plain.sizeSp)
        assertTrue(plain.widths[0] >= 128.6f + 17f, "«Сфотографировать» whole: ${plain.widths}")
        assertTrue(plain.widths[1] >= 54.1f + 17f, "«галереи» whole: ${plain.widths}")

        val large = plan(268f, tile = null, ruAt13)
        assertEquals(12f, large.sizeSp)
        val at12 = ruAt13(12f)
        assertTrue(large.widths[0] >= at12[0].word + 17f && large.widths[1] >= at12[1].word + 17f, "whole words at 12 sp: ${large.widths}")
    }
}
