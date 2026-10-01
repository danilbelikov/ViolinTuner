package com.violinjourney.app.feature.home

import androidx.compose.ui.geometry.Rect
import com.violinjourney.app.core.domain.home.HomeItem
import com.violinjourney.app.core.domain.home.HomeRules
import com.violinjourney.app.core.domain.home.HomeState
import com.violinjourney.app.feature.journey.art.SceneGrid
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.datetime.LocalDate

/**
 * The outline of «Обставить» (spec 3.36.7, 5.29 R7): the thing the tile touched last put there is outlined once the picture shows it
 * standing there — not a wall or a floor (a colour, no frame of their own), not «пусто», not the thing the place held before the tap
 * while the new room is put together, not the tree before its season, not a thing this home or this side does not show; the curtains
 * are a thing with a frame. The frame goes round what is seen of the thing, and is not drawn for a thing out of sight; the room seen
 * whole by its width shows the things at its sides.
 */
class ArrangeOutlineTest {
    private val october = LocalDate(2026, 10, 1)
    private val december = LocalDate(2026, 12, 10)
    private val loaded = HomeState.EMPTY.copy(loaded = true)

    private fun bought(vararg choices: Pair<String, String>) = loaded.copy(purchased = choices.map { it.second }.filter { it.isNotEmpty() }.toSet(), choices = mapOf(*choices))

    /** What a picture of [state] shows, as `HomePicture` puts it together: the things standing in [house] from [outside] on [date]. */
    private fun shown(state: HomeState, house: String = "rent", outside: Boolean = false, date: LocalDate = october): List<HomeItem> =
        HomeRules.standing(state, house, outside, date)

    private fun touched(slot: String, itemId: String) = ArrangeFocus(slot, itemId)

    @Test
    fun `walls and floor are colours - no outline`() {
        val state = bought("wallpaper" to "wp_warm", "floor" to "floor_dark")
        assertNull(ArrangeOutline.thingOf(touched("wallpaper", "wp_warm"), shown(state)))
        assertNull(ArrangeOutline.thingOf(touched("floor", "floor_dark"), shown(state)))
    }

    @Test
    fun `the thing a tile put is outlined - a place left bare has nothing to outline`() {
        assertEquals("notes", ArrangeOutline.thingOf(touched("deskR", "notes"), shown(bought("deskR" to "notes")))?.id)
        assertNull(ArrangeOutline.thingOf(touched("deskR", ""), shown(bought("deskR" to ""))))
        assertNull(ArrangeOutline.thingOf(touched("deskR", ""), shown(loaded)), "nothing was ever put there")
    }

    /**
     * The tap is written to the store and the room put together off the main thread: for some frames the picture shows what the place
     * held. «пусто» over the notes still drawn outlines nothing; a lamp put where the brass lamp stood is not outlined round the brass
     * lamp — only once the picture shows the new one.
     */
    @Test
    fun `a tile's thing is outlined only once the picture shows it there - never what the place held before`() {
        val notesDrawn = shown(bought("deskR" to "notes"))
        assertNull(ArrangeOutline.thingOf(touched("deskR", ""), notesDrawn), "«пусто» while the notes are still drawn")
        val brassDrawn = shown(bought("deskTop" to "lamp_brass"))
        assertNull(ArrangeOutline.thingOf(touched("deskTop", "lamp_table"), brassDrawn), "the table lamp put while the brass one is still drawn")
        assertEquals("lamp_table", ArrangeOutline.thingOf(touched("deskTop", "lamp_table"), shown(loaded))?.id, "drawn now: outlined")
    }

    @Test
    fun `the tree is outlined in its season only - before December what stood there stands in its place`() {
        val state = bought("floorR" to "xmas")
        assertNull(ArrangeOutline.thingOf(touched("floorR", "xmas"), shown(state, date = october)))
        assertEquals("xmas", ArrangeOutline.thingOf(touched("floorR", "xmas"), shown(state, date = december))?.id)
    }

    @Test
    fun `the curtains are a thing with a frame - the ones the room came with too`() {
        assertEquals("curtain_velvet", ArrangeOutline.thingOf(touched("curtain", "curtain_velvet"), shown(bought("curtain" to "curtain_velvet")))?.id)
        assertEquals("curtain_plum", ArrangeOutline.thingOf(touched("curtain", "curtain_plum"), shown(loaded))?.id)
    }

    @Test
    fun `a thing this home does not show is not outlined`() {
        val fire = bought("fire" to "fireplace")
        assertNull(ArrangeOutline.thingOf(touched("fire", "fireplace"), shown(fire, house = "rent")), "the rented room has no chimney")
        assertEquals("fireplace", ArrangeOutline.thingOf(touched("fire", "fireplace"), shown(fire, house = "wood"))?.id)
    }

    @Test
    fun `a thing outside is outlined from outside only`() {
        val state = bought("oL" to "mailbox")
        assertEquals("mailbox", ArrangeOutline.thingOf(touched("oL", "mailbox"), shown(state, outside = true))?.id)
        assertNull(ArrangeOutline.thingOf(touched("oL", "mailbox"), shown(state, outside = false)))
        assertNull(ArrangeOutline.thingOf(touched("nowhere", "mailbox"), shown(state, outside = true)), "not a place")
    }

    @Test
    fun `the frame stands round the thing with its field when the thing is in sight`() {
        val seen = Rect(0f, 0f, 400f, 260f)
        assertEquals(Rect(96f, 46f, 164f, 124f), ArrangeOutline.frameOf(Rect(100f, 50f, 160f, 120f), seen, pad = 4f))
    }

    @Test
    fun `a thing cut by the edge of the picture is outlined round what is seen of it`() {
        val seen = Rect(0f, 0f, 400f, 260f)
        assertEquals(Rect(370f, 196f, 400f, 260f), ArrangeOutline.frameOf(Rect(374f, 200f, 460f, 300f), seen, pad = 4f))
    }

    @Test
    fun `a thing wholly out of sight has no frame - its field does not bring it in`() {
        val seen = Rect(0f, 0f, 400f, 260f)
        assertNull(ArrangeOutline.frameOf(Rect(-120f, 40f, -20f, 100f), seen, pad = 4f), "left of the picture")
        assertNull(ArrangeOutline.frameOf(Rect(-60f, 40f, -2f, 100f), seen, pad = 4f), "only its field would reach in")
        assertNull(ArrangeOutline.frameOf(Rect(100f, 270f, 160f, 300f), seen, pad = 4f), "under it")
    }

    /**
     * Lying in the emulator's 640 × 360 the room beside the places is some 235 × 240: covering it at the zoom of 1 showed x 79…333 of
     * the 412 of the room — the fireplace by the left wall (6…66) and the floor lamp in the right corner (379…405) out of sight, no
     * outline and no change seen at their tap. Seen whole by its width, all of it is there, the floor and the ceiling over and under it.
     */
    @Test
    fun `the room seen whole by its width shows the things at its sides`() {
        val fireplace = Rect(6f, 120f, 66f, 210f)
        val lamp = Rect(379f, 60f, 405f, 210f)
        val covering = assertNotNull(ArrangeOutline.gridIn(235f, 240f, whole = false))
        assertTrue(covering.seen().left > fireplace.right && covering.seen().right < lamp.left, "covering: ${covering.seen()}")
        assertNull(ArrangeOutline.frameOf(fireplace, covering.seen(), pad = 4f))
        val whole = assertNotNull(ArrangeOutline.gridIn(235f, 240f, whole = true))
        val seen = whole.seen()
        assertEquals(0f, seen.left, 0.01f)
        assertEquals(SceneGrid.WIDTH, seen.right, 0.01f)
        assertTrue(seen.top < 0f && seen.bottom > SceneGrid.HEIGHT, "the ceiling and the floor too: $seen")
        assertNotNull(ArrangeOutline.frameOf(fireplace, seen, pad = 4f))
        assertNotNull(ArrangeOutline.frameOf(lamp, seen, pad = 4f))
    }

    @Test
    fun `upright on a phone the room is seen by its width either way`() {
        // 412 − 32 wide, 210 high: covering is already by the width — the same picture
        assertEquals(ArrangeOutline.gridIn(380f, 210f, whole = false), ArrangeOutline.gridIn(380f, 210f, whole = true))
        assertNull(ArrangeOutline.gridIn(0f, 0f, whole = true), "a picture of no size")
    }
}
