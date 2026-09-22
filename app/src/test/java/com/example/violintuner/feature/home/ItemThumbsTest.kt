package com.example.violintuner.feature.home

import com.example.violintuner.core.domain.home.HomeCatalog
import com.example.violintuner.feature.home.art.HouseArt
import com.example.violintuner.feature.home.art.ItemThumbs
import com.example.violintuner.feature.journey.art.SceneLayer
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** What stands on a shelf of the shop and in the card of a thing (spec 3.29, 5.22). */
class ItemThumbsTest {
    private val arts = listOf("rent", "wood").associateWith { HouseArt.parse(File("src/main/assets/home/$it.eve.scene").readText()) }
    private fun item(id: String) = HomeCatalog.byId.getValue(id)
    private fun thumb(id: String, glows: Boolean = false) = ItemThumbs.of(item(id), arts.getValue(ItemThumbs.houseOf(item(id))), glows)!!

    @Test
    fun `every thing of the catalogue has a picture for its shelf`() {
        HomeCatalog.items.forEach { item ->
            val shown = ItemThumbs.of(item, arts.getValue(ItemThumbs.houseOf(item)))
            assertNotNull(item.id, shown)
            assertTrue(item.id, shown!!.scene.layers.isNotEmpty() && shown.box.width > 0f && shown.box.height > 0f)
        }
    }

    @Test
    fun `the fireplace and the vane stand as they do in the wooden house - the rented room has no place for them`() {
        assertEquals("wood", ItemThumbs.houseOf(item("fireplace")))
        assertEquals("wood", ItemThumbs.houseOf(item("vane")))
        assertEquals("rent", ItemThumbs.houseOf(item("metronome")))
        assertEquals("rent", ItemThumbs.houseOf(item("wp_damask")))
    }

    @Test
    fun `curtains keep their own colour on a shelf - three curtains are not one`() {
        val colours = listOf("curtain_velvet", "curtain_sand", "curtain_teal").map { thumb(it).scene.overrides.getValue("curtain") }
        assertEquals(colours.toSet().size, colours.size)
        assertEquals(item("curtain_teal").palette.getValue("curtain"), colours.last())
    }

    @Test
    fun `a window, a view and curtains are shown as the window whole, the thing sold in its place`() {
        val rent = arts.getValue("rent")
        val arched = thumb("window_arched")
        assertTrue(arched.sample && arched.box == ItemThumbs.WINDOW_BOX)
        assertTrue(arched.scene.layers.containsAll(rent.backs.getValue("window_arched")))
        assertTrue(arched.scene.layers.containsAll(rent.items.getValue("window_arched").layers))
        assertTrue(arched.scene.layers.containsAll(rent.items.getValue("view_city").layers))
        val sea = thumb("view_sea").scene.layers
        assertTrue(sea.containsAll(rent.items.getValue("view_sea").layers) && sea.containsAll(rent.items.getValue("window_simple").layers))
        assertFalse(sea.containsAll(rent.items.getValue("view_city").layers))
    }

    @Test
    fun `a wall and a floor are shown as a corner of the room with them - panels and patterns seen`() {
        val damask = thumb("wp_damask")
        assertTrue(damask.sample && damask.box == ItemThumbs.WALL_BOX)
        assertEquals(item("wp_damask").palette.getValue("wallHome"), damask.scene.overrides.getValue("wallHome"))
        assertTrue(damask.scene.layers.containsAll(arts.getValue("rent").wallPatterns.getValue("wp_damask")))
        assertTrue(thumb("wp_panels").scene.layers.containsAll(arts.getValue("rent").wallPatterns.getValue("wp_panels")))
        val tile = thumb("floor_tile")
        assertTrue(tile.sample && tile.box == ItemThumbs.FLOOR_BOX)
        assertTrue(tile.scene.layers.containsAll(arts.getValue("rent").floorPatterns.getValue("floor_tile")))
    }

    @Test
    fun `a lamp keeps its halo in its card and leaves it off a shelf, where the picture's edge would cut it`() {
        fun halos(glows: Boolean) = thumb("floorlamp", glows).scene.layers.count { it.fill == SceneLayer.GLOW || it.warmGlow }
        assertEquals(0, halos(glows = false))
        assertTrue(halos(glows = true) > 0)
    }

    @Test
    fun `the chandelier is framed by its ring and candles, not by its long rod`() {
        val chandelier = arts.getValue("rent").items.getValue("chandelier")
        val shown = thumb("chandelier")
        assertEquals(chandelier.shelf, shown.box)
        assertFalse(shown.sample)
        val metronome = arts.getValue("rent").items.getValue("metronome")
        assertEquals(metronome.bottom, thumb("metronome").box.bottom)
    }
}
