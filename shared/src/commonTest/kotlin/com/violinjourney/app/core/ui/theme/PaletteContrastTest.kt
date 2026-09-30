package com.violinjourney.app.core.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * The numbers of spec 5.29 for the colours of the redesign: every text reads at 4.5 : 1 or better where it is allowed to stand, the
 * third level of text is kept off the colour of dialogs, the glass and the scrim have their strength, and the danger is no zone.
 */
class PaletteContrastTest {
    private fun contrast(foreground: Color, background: Color): Float {
        val a = foreground.luminance() + 0.05f
        val b = background.luminance() + 0.05f
        return max(a, b) / min(a, b)
    }

    private fun assertAtLeast(minimum: Float, foreground: Color, background: Color, what: String) {
        val ratio = contrast(foreground, background)
        assertTrue(ratio >= minimum, "$what is $ratio : 1 and must be $minimum : 1 or more")
    }

    @Test
    fun `the danger reads on a dialog and on the screen`() {
        assertAtLeast(8f, DangerSoft, SurfaceContainerHigh, "danger on a dialog or a menu")
        assertAtLeast(10f, DangerSoft, Surface, "danger on the screen")
    }

    @Test
    fun `the text of the filled dangerous button reads on it`() {
        assertAtLeast(9f, OnDanger, DangerSoft, "«Восстановить» on the danger")
    }

    @Test
    fun `the third level of text stands on the screen and on a card but not on a dialog`() {
        assertAtLeast(5.2f, TextTertiary, Surface, "the third level on the screen")
        assertAtLeast(4.5f, TextTertiary, SurfaceContainer, "the third level on a card")
        val onDialog = contrast(TextTertiary, SurfaceContainerHigh)
        assertTrue(onDialog < 4.5f, "on a dialog it is $onDialog : 1 — the reason it is not used there")
    }

    @Test
    fun `every word of a dialog reads on it`() {
        assertAtLeast(11f, OnSurface, SurfaceContainerHigh, "the title")
        assertAtLeast(4.5f, OnSurfaceVariant, SurfaceContainerHigh, "the text and «Не сохранять»")
        assertAtLeast(4.5f, Primary, SurfaceContainerHigh, "«Отмена» and the action")
        assertAtLeast(4.5f, OnSurfaceVariant, SurfaceContainer, "the reason under a field")
    }

    @Test
    fun `the brass of done reads on the ground of a sheet group`() {
        // «15 мин ✓» of «Что играли» on the ground of the screen inside a sheet (spec 5.29 R3: 7.7 : 1)
        assertAtLeast(7f, CtrlBrass, Surface, "the brass of «сделано» on a group of a sheet")
        assertAtLeast(4.5f, CtrlBrass, SurfaceContainer, "the brass on the sheet itself")
    }

    @Test
    fun `the glass and the scrim have the strength of the spec`() {
        assertEquals(0.72f, Glass.alpha, 0.005f, "the glass over pictures")
        assertEquals(0.82f, GlassStrong.alpha, 0.005f, "the strong glass over a busy picture")
        assertEquals(0.55f, SheetScrim.alpha, 0.005f, "the scrim under a sheet")
        assertEquals(Surface.copy(alpha = 1f), Glass.copy(alpha = 1f), "the glass is the background of the app, smoked")
        assertEquals(Surface.copy(alpha = 1f), GlassStrong.copy(alpha = 1f), "the strong glass is the same smoke")
    }

    @Test
    fun `the danger is no colour of a zone and the sign of recording keeps its red`() {
        listOf(ZoneInTune, ZoneNear, ZoneOff).forEach { zone -> assertNotEquals(zone, DangerSoft, "the danger may not look like a zone") }
        // a token of its own with the hex of «мимо»: the key of recording looks as it did
        assertEquals(ZoneOff, Recording)
        assertTrue(abs(contrast(OnRecording, Recording) - contrast(Color.White, ZoneOff)) < 0.001f, "the stop square is white as before")
    }

    @Test
    fun `the caption of the glass reads over a dark picture and is checked by a screenshot over the lightest one`() {
        // The room at night behind the glass of Live's controls (spec 5.29 R6): 11 : 1 over the darkest ground. The grey of the dialogs
        // (#A39FB5, which the glass may not carry, 5.29 R1) gives 7.2 here — so this bound is the caption's own, not any light grey's.
        assertAtLeast(10.5f, GlassCaption, Glass.compositeOver(Surface), "the caption of the glass over the dark room")
        assertAtLeast(4.5f, OnSurface, Glass.compositeOver(Color.White), "the words on the glass over white")
        // ≈ 4.4 : 1 over pure white at 0.72 (spec 5.29 R6); the grey of the dialogs would be 2.9, and a darker caption less than this
        assertAtLeast(4.3f, GlassCaption, Glass.compositeOver(Color.White), "the caption of the glass over pure white")
        val overWhite = contrast(GlassCaption, Glass.compositeOver(Color.White))
        assertTrue(
            overWhite < 4.5f,
            "the caption over pure white is $overWhite : 1 — the reason the cream ceiling of Vienna is checked by a screenshot (spec 5.29 R6)",
        )
    }

    @Test
    fun `the edge of the glass is white at twelve percent`() {
        assertEquals(0.12f, GlassEdge.alpha, 0.005f, "the inner edge of the glass of Live's controls")
        assertEquals(Color.White, GlassEdge.copy(alpha = 1f), "white")
    }

    @Test
    fun `the words on the paper of Live read on the bone`() {
        // the cards of the bottom row and the card «нет разрешения» (spec 5.29 R6): the first line and the title in ink, the second
        // line and the text in the soft ink (≈ 5 : 1), «Разрешить доступ» in bone on the ink, the microphone in ink on its plate
        assertAtLeast(12f, CtrlInk, CtrlBone, "the ink on the paper")
        assertAtLeast(4.5f, CtrlInkSoft, CtrlBone, "the soft ink on the paper")
        assertAtLeast(12f, CtrlBone, CtrlInk, "the word of the dark button")
        assertAtLeast(3f, CtrlInk, CtrlBoneShade, "the microphone on its plate")
    }

    @Test
    fun `the rim of the record key is darker and duller than the brass of done`() {
        // the key's rim must not argue with the brass of «сделано» beside it (spec 5.29 R6)
        assertTrue(KeyRim.luminance() < CtrlBrass.luminance(), "darker")
        assertTrue(saturation(KeyRim) < saturation(CtrlBrass), "duller: ${saturation(KeyRim)} against ${saturation(CtrlBrass)}")
    }

    /** The saturation of HSV: how far the colour is from a grey of its brightness. */
    private fun saturation(color: Color): Float {
        val most = max(color.red, max(color.green, color.blue))
        val least = min(color.red, min(color.green, color.blue))
        return if (most == 0f) 0f else (most - least) / most
    }
}
