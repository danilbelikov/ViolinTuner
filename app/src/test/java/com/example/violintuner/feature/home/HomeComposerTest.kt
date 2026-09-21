package com.example.violintuner.feature.home

import com.example.violintuner.core.domain.home.HomeCatalog
import com.example.violintuner.core.domain.home.HomeRules
import com.example.violintuner.core.domain.home.HomeState
import com.example.violintuner.feature.home.art.HomeComposer
import com.example.violintuner.feature.home.art.HouseArt
import com.example.violintuner.feature.journey.art.SceneLayer
import com.example.violintuner.feature.journey.art.SceneMode
import com.example.violintuner.feature.journey.art.ScenePalette
import java.io.File
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeComposerTest {
    private val today = LocalDate.of(2026, 9, 21)
    private fun art(house: String, mode: SceneMode) = HouseArt.parse(File("src/main/assets/home/$house.${mode.suffix}.scene").readText())
    private val loaded = HomeState.EMPTY.copy(loaded = true)
    private fun everything() = loaded.copy(purchased = HomeCatalog.items.map { it.id }.toSet())

    @Test
    fun `every thing of the catalogue is drawn in every home that has its place, at both times of day`() {
        for (house in HomeCatalog.houses.filter { it.drawn }) for (mode in SceneMode.entries) {
            val art = art(house.id, mode)
            assertTrue(art.room.isNotEmpty() && art.outside.isNotEmpty() && art.hero.isNotEmpty())
            HomeCatalog.items.filter { it.drawn && HomeRules.slotIn(it.slot, house.id) }.forEach { item ->
                val drawn = art.items[item.id]
                assertNotNull("${item.id} is not drawn in ${house.id}", drawn)
                assertTrue(drawn!!.layers.isNotEmpty() && drawn.right > drawn.left && drawn.bottom > drawn.top)
            }
            HomeCatalog.items.filter { it.pattern }.forEach { item ->
                val patterns = if (item.slot == "floor") art.floorPatterns else art.wallPatterns
                assertTrue("${item.id} has no pattern in ${house.id}", patterns[item.id].orEmpty().isNotEmpty())
            }
            assertTrue(art.floorPatterns[HouseArt.DEFAULT_FLOOR].orEmpty().isNotEmpty())
        }
    }

    @Test
    fun `every colour of every home resolves, whatever is bought`() {
        for (house in listOf("rent", "wood")) for (mode in SceneMode.entries) for (outside in listOf(false, true)) {
            val art = art(house, mode)
            for (item in HomeCatalog.items) {
                val state = everything().copy(choices = mapOf(item.slot to item.id))
                val scene = HomeComposer.compose(art, HomeRules.standing(state, house, outside, today), outside, mode, porchCat = HomeRules.catOnPorch(state, house)).scene
                scene.layers.forEach { layer ->
                    if (layer.fill != SceneLayer.SKY && layer.fill != SceneLayer.GLOW && !layer.fillNone) {
                        assertNotNull("«${layer.fill}» in $house with ${item.id}", ScenePalette.colorOf(layer.fill, layer.depth, scene, mode))
                    }
                    assertFalse("a mark was left in the scene", layer.fill.startsWith("@"))
                }
            }
        }
    }

    @Test
    fun `the room is put together back to front - furniture, then the violin, then the pet - and the traveller is not at home`() {
        val state = loaded.copy(purchased = setOf("cat_ginger", "piano", "vln_student"), choices = mapOf("pet" to "cat_ginger", "floorL" to "piano", "violin" to "vln_student"))
        val art = art("rent", SceneMode.EVENING)
        val layers = HomeComposer.compose(art, HomeRules.standing(state, "rent", false, today), false, SceneMode.EVENING).scene.layers
        fun at(part: List<SceneLayer>) = layers.indexOfFirst { it === part.first() }
        assertTrue(at(art.items.getValue("piano").layers) in 0 until at(art.items.getValue("vln_student").layers))
        assertTrue(at(art.items.getValue("cat_ginger").layers) > at(art.items.getValue("vln_student").layers))
        assertEquals(-1, at(art.hero))
        // what came with the room is there, what was not chosen is not; the rug is for the shop now
        assertTrue(at(art.items.getValue("desk_simple").layers) >= 0)
        assertEquals(-1, at(art.items.getValue("desk_oak").layers))
        assertEquals(-1, at(art.items.getValue("rug_plum").layers))
    }

    @Test
    fun `until a violin stands on its stand the student's one lies in the open case`() {
        val art = art("rent", SceneMode.EVENING)
        val lying = art.caseViolins.getValue("case_black")
        fun layersOf(state: HomeState, ghost: String? = null) = HomeComposer.compose(art, HomeRules.standing(state, "rent", false, today), false, SceneMode.EVENING, ghost = ghost?.let { HomeCatalog.byId.getValue(it) }).scene.layers
        assertTrue(layersOf(loaded).any { it === lying.first() })
        val withViolin = loaded.copy(purchased = setOf("vln_student"), choices = mapOf("violin" to "vln_student"))
        assertTrue(layersOf(withViolin).none { it === lying.first() })
        // trying a violin on takes it out of the case as well; a case put away takes the violin with it
        assertTrue(layersOf(loaded, ghost = "vln_master").none { it === lying.first() })
        assertTrue(layersOf(loaded.copy(choices = mapOf("case" to ""))).none { it === lying.first() })
    }

    @Test
    fun `wallpaper is a colour of the scene and a pattern in its mark - the day lifts the tone`() {
        val state = loaded.copy(purchased = setOf("wp_damask", "floor_tile"), choices = mapOf("wallpaper" to "wp_damask", "floor" to "floor_tile"))
        val evening = HomeComposer.compose(art("rent", SceneMode.EVENING), HomeRules.standing(state, "rent", false, today), false, SceneMode.EVENING).scene
        val day = HomeComposer.compose(art("rent", SceneMode.DAY), HomeRules.standing(state, "rent", false, today), false, SceneMode.DAY).scene
        val damask = HomeCatalog.byId.getValue("wp_damask")
        assertEquals(damask.palette.getValue("wallHome"), evening.overrides["wallHome"])
        assertTrue(day.overrides.getValue("wallHome") != evening.overrides.getValue("wallHome"))
        val plain = HomeComposer.compose(art("rent", SceneMode.EVENING), HomeRules.standing(loaded, "rent", false, today), false, SceneMode.EVENING).scene
        assertTrue(evening.layers.size > plain.layers.size)
        assertTrue(plain.overrides["wallHome"] == null || plain.overrides["wallHome"] == HomeCatalog.byId.getValue("wp_plum").palette["wallHome"])
    }

    @Test
    fun `a thing tried on takes the place of what stood there - whole and still, the room a little darker, a glow under it`() {
        val art = art("rent", SceneMode.EVENING)
        val oak = art.items.getValue("desk_oak")
        val composed = HomeComposer.compose(art, HomeRules.standing(loaded, "rent", false, today), false, SceneMode.EVENING, ghost = HomeCatalog.byId.getValue("desk_oak"))
        val layers = composed.scene.layers
        assertNotNull(composed.ghost)
        assertTrue(layers.none { it === art.items.getValue("desk_simple").layers.first() })
        val dim = layers.indexOfFirst { it.fill.startsWith("rgba(14,14,18") }
        val glow = layers.indexOfFirst { it.fill == SceneLayer.GLOW && it.anim?.flick != null && it.path.startsWith("M${(oak.left + oak.right) / 2 - ((oak.right - oak.left) * 0.9f + 14f)}") }
        val thing = layers.indexOfFirst { layer -> layer.path == oak.layers.first().path && layer.fill == oak.layers.first().fill }
        assertTrue("dim $dim, glow $glow, thing $thing", dim >= 0 && glow > dim && thing > glow)
        assertTrue(layers.drop(thing).take(oak.layers.size).all { it.opacity == oak.layers[layers.drop(thing).indexOf(it)].opacity && it.anim == null })
    }

    @Test
    fun `from outside the window of the rented room has our curtains - and the cat sits on the porch of the wooden house`() {
        val teal = HomeCatalog.byId.getValue("curtain_teal")
        val state = loaded.copy(purchased = setOf("curtain_teal", "cat_grey"), houses = setOf("wood"), choices = mapOf("curtain" to "curtain_teal", "pet" to "cat_grey"))
        val rent = HomeComposer.compose(art("rent", SceneMode.EVENING), HomeRules.standing(state, "rent", true, today), true, SceneMode.EVENING, porchCat = HomeRules.catOnPorch(state, "rent"), curtains = HomeRules.placed(state)["curtain"]).scene
        assertTrue(rent.aerial)
        assertTrue(rent.layers.any { it.fill.equals("#%06X".format(teal.palette.getValue("curtain") and 0xFFFFFF), ignoreCase = true) })
        val woodArt = art("wood", SceneMode.EVENING)
        val wood = HomeComposer.compose(woodArt, HomeRules.standing(state, "wood", true, today), true, SceneMode.EVENING, porchCat = HomeRules.catOnPorch(state, "wood")).scene
        assertTrue(wood.layers.any { it === woodArt.porch.getValue("cat_grey").first() })
    }

    @Test
    fun `the view is seen through the window - behind the glazing bars, in front of the backing`() {
        val art = art("rent", SceneMode.EVENING)
        val layers = HomeComposer.compose(art, HomeRules.standing(loaded, "rent", false, today), false, SceneMode.EVENING).scene.layers
        val window = art.items.getValue("window_simple").layers
        val view = art.items.getValue("view_city").layers
        val backing = layers.indexOfFirst { it === art.backs.getValue("window_simple").first() }
        val sky = layers.indexOfFirst { it === view.first() }
        val bars = layers.indexOfFirst { it === window.first() }
        assertTrue(backing in 0 until sky && sky < bars)
    }
}
