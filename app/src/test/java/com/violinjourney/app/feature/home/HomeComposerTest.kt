package com.violinjourney.app.feature.home

import com.violinjourney.app.core.domain.home.HomeCatalog
import com.violinjourney.app.core.domain.home.HomeRules
import com.violinjourney.app.core.domain.home.HomeState
import com.violinjourney.app.feature.home.art.HomeComposer
import com.violinjourney.app.feature.home.art.HouseArt
import com.violinjourney.app.feature.journey.PathBounds
import com.violinjourney.app.feature.journey.art.SceneLayer
import com.violinjourney.app.feature.journey.art.SceneMode
import com.violinjourney.app.feature.journey.art.ScenePalette
import java.io.File
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeComposerTest {
    private val today = LocalDate(2026, 9, 21)
    private fun art(house: String, mode: SceneMode) = HouseArt.parse(File("../shared/src/commonMain/composeResources/files/home/$house.${mode.suffix}.scene").readText())
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
    fun `the room of Live has no violin - it is in the player's hands, neither on its stand nor in the case`() {
        val art = art("rent", SceneMode.EVENING)
        val withStand = loaded.copy(purchased = setOf("vln_student"), choices = mapOf("violin" to "vln_student"))
        for (state in listOf(loaded, withStand)) {
            val layers = HomeComposer.compose(art, HomeRules.standing(state, "rent", false, today), false, SceneMode.EVENING, withViolin = false).scene.layers
            assertFalse(layers.any { it === art.caseViolins.getValue("case_black").first() })
            assertFalse(layers.any { it === art.items.getValue("vln_student").layers.first() })
            // the case itself stays, open and empty
            assertTrue(layers.any { it === art.items.getValue("case_black").layers.first() })
        }
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
        // the pattern lies in its mark — one layer since spec 3.29, where it was dozens
        val lilies = art("rent", SceneMode.EVENING).wallPatterns.getValue("wp_damask")
        assertTrue(evening.layers.containsAll(lilies) && plain.layers.none { it in lilies })
        assertTrue(plain.overrides["wallHome"] == null || plain.overrides["wallHome"] == HomeCatalog.byId.getValue("wp_plum").palette["wallHome"])
    }

    @Test
    fun `a thing tried on takes the place of what stood there - whole and alive, the room a little darker, a glow under it`() {
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
        assertEquals(oak.layers, layers.drop(thing).take(oak.layers.size))
        // it lives as it will in the room: a still metronome in the try-on read as a broken one (spec 3.29)
        val metronome = art.items.getValue("metronome").layers
        val tried = HomeComposer.compose(art, HomeRules.standing(loaded, "rent", false, today), false, SceneMode.EVENING, ghost = HomeCatalog.byId.getValue("metronome")).scene.layers
        val swinging = metronome.filter { it.anim?.swing != null }
        assertTrue(swinging.isNotEmpty() && tried.containsAll(swinging))
    }

    @Test
    fun `a thing tried on stands in its depth - what is in front stays in front and takes the veil on itself`() {
        val art = art("rent", SceneMode.EVENING)
        fun tried(state: HomeState, ghost: String) =
            HomeComposer.compose(art, HomeRules.standing(state, "rent", false, today), false, SceneMode.EVENING, ghost = HomeCatalog.byId.getValue(ghost)).scene.layers
        // a layer by its outline and its fill, whatever its shade and its movement (the plaid takes the chair's swing)
        fun SceneLayer.of(part: List<SceneLayer>) = part.any { it.path == path && it.fill == fill }
        fun List<SceneLayer>.at(part: List<SceneLayer>) = indexOfFirst { it.path == part.first().path && it.fill == part.first().fill }
        fun List<SceneLayer>.ofPart(part: List<SceneLayer>) = filter { it.of(part) }
        fun dimOf(layers: List<SceneLayer>) = layers.indexOfFirst { it.fill.startsWith("rgba(14,14,18") }

        // a view: behind the glazing bars and the curtains, which stay in front of it, veiled as the room
        val view = tried(loaded, "view_garden")
        val garden = art.items.getValue("view_garden").layers
        val bars = art.items.getValue("window_simple").layers
        val curtain = art.items.getValue("curtain_plum").layers
        assertTrue(view.at(garden) in 0 until view.at(bars))
        assertTrue(view.ofPart(bars).isNotEmpty() && view.ofPart(bars).all { it.shade == HomeComposer.TRY_ON_DIM })
        assertTrue(view.ofPart(curtain).isNotEmpty() && view.ofPart(curtain).all { it.shade == HomeComposer.TRY_ON_DIM })
        assertTrue("the thing itself is not veiled", view.ofPart(garden).all { it.shade == 0f })
        // behind the veil nothing is shaded: the veil does it
        assertTrue(view.take(dimOf(view)).all { it.shade == 0f })

        // a window tried on has its own backing, before the view
        val arched = tried(loaded, "window_arched")
        val backing = art.backs.getValue("window_arched")
        assertTrue(arched.at(backing) in 0 until arched.at(art.items.getValue("view_city").layers))

        // the plaid stays on a chair tried on; the violin stays on its stand in front of a rug tried on
        val plaid = loaded.copy(purchased = setOf("plaid"), choices = mapOf("chairTop" to "plaid"))
        val rocking = tried(plaid, "rocking")
        assertTrue(rocking.at(art.items.getValue("plaid").layers) > rocking.at(art.items.getValue("rocking").layers))
        val violin = loaded.copy(purchased = setOf("vln_student"), choices = mapOf("violin" to "vln_student"))
        val rug = tried(violin, "rug_persian")
        val student = art.items.getValue("vln_student").layers
        assertTrue(rug.at(student) > rug.at(art.items.getValue("rug_persian").layers))
        assertTrue(rug.ofPart(student).all { it.shade == HomeComposer.TRY_ON_DIM })

        // the cat darkens with the room
        val cat = loaded.copy(purchased = setOf("cat_ginger"), choices = mapOf("pet" to "cat_ginger"))
        val desk = tried(cat, "desk_oak")
        val ginger = art.items.getValue("cat_ginger").layers
        assertTrue(desk.ofPart(ginger).isNotEmpty() && desk.ofPart(ginger).all { it.shade == HomeComposer.TRY_ON_DIM })

        // a case tried on holds the student's violin as it will, part of the thing: not veiled
        val velvet = tried(loaded, "case_velvet")
        val lying = art.caseViolins.getValue("case_velvet")
        assertTrue(velvet.at(lying) > velvet.at(art.items.getValue("case_velvet").layers))
        assertTrue(velvet.ofPart(lying).all { it.shade == 0f })

        // without a thing tried on nothing is shaded
        assertTrue(HomeComposer.compose(art, HomeRules.standing(cat, "rent", false, today), false, SceneMode.EVENING).scene.layers.all { it.shade == 0f })
    }

    @Test
    fun `the veil of a try-on is the shade colour at the try-on alpha - what is in front is shaded by the same`() {
        val art = art("rent", SceneMode.EVENING)
        val layers = HomeComposer.compose(art, HomeRules.standing(loaded, "rent", false, today), false, SceneMode.EVENING, ghost = HomeCatalog.byId.getValue("desk_oak")).scene.layers
        val veil = ScenePalette.parse(layers.first { it.fill.startsWith("rgba(14,14,18") }.fill)!!
        assertEquals(ScenePalette.SHADE and 0xFFFFFF, veil and 0xFFFFFF)
        assertEquals(HomeComposer.TRY_ON_DIM, (veil ushr 24) / 255f, 1f / 255)
    }

    @Test
    fun `what lies on a rocking chair rocks with it - in the room and in the try-on (spec 3 29)`() {
        val art = art("rent", SceneMode.EVENING)
        val swing = art.items.getValue("rocking").layers.firstNotNullOf { it.anim?.swing }
        val plaid = art.items.getValue("plaid").layers
        fun plaidIn(layers: List<SceneLayer>) = layers.filter { layer -> plaid.any { it.path == layer.path && it.fill == layer.fill } }
        val rocking = loaded.copy(purchased = setOf("rocking", "plaid"), choices = mapOf("chair" to "rocking", "chairTop" to "plaid"))
        val inRoom = plaidIn(HomeComposer.compose(art, HomeRules.standing(rocking, "rent", false, today), false, SceneMode.EVENING).scene.layers)
        assertEquals(plaid.size, inRoom.size)
        assertTrue(inRoom.all { it.anim?.swing == swing })
        // on the plain chair it lies still; tried on the rocking chair, the chair takes it along
        val plain = loaded.copy(purchased = setOf("plaid"), choices = mapOf("chairTop" to "plaid"))
        assertTrue(plaidIn(HomeComposer.compose(art, HomeRules.standing(plain, "rent", false, today), false, SceneMode.EVENING).scene.layers).all { it.anim == null })
        val tried = HomeComposer.compose(art, HomeRules.standing(plain, "rent", false, today), false, SceneMode.EVENING, ghost = HomeCatalog.byId.getValue("rocking")).scene.layers
        assertTrue(plaidIn(tried).all { it.anim?.swing == swing })
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

    @Test
    fun `a pattern covers the wall from the cornice to the floor and the floor to the viewer (spec 3 29)`() {
        for (house in listOf("rent", "wood")) for (mode in SceneMode.entries) {
            val art = art(house, mode)
            HomeCatalog.items.filter { it.pattern }.forEach { item ->
                val floor = item.slot == "floor"
                val layers = (if (floor) art.floorPatterns else art.wallPatterns).getValue(item.id)
                val reach = PathBounds.ofAll(layers.map { it.path })!!
                if (floor) {
                    assertTrue("${item.id} in $house: $reach", reach.top <= FLOOR_TOP + EDGE && reach.bottom >= FLOOR_BOTTOM - EDGE)
                } else {
                    assertTrue("${item.id} in $house: $reach", reach.bottom >= FLOOR_TOP - EDGE)
                    // wooden panels go half way up the wall on purpose; a repeating pattern goes up to the cornice
                    if (item.id != "wp_panels") assertTrue("${item.id} in $house: $reach", reach.top <= WALL_TOP + EDGE)
                }
            }
        }
    }

    @Test
    fun `a pet lives where no other thing stands - on the windowsill or on the floor (spec 3 29)`() {
        val places = HomeCatalog.items.filter { it.slot != "pet" }.map { it.slot }.toSet()
        HomeCatalog.items.filter { it.slot == "pet" }.forEach { pet ->
            assertTrue("${pet.id} stands at ${pet.at}, where a thing of that place stands", (pet.at ?: pet.slot) !in places)
        }
        assertEquals(setOf("sillL", "pet"), HomeCatalog.items.filter { it.slot == "pet" }.map { it.at }.toSet())
    }

    @Test
    fun `the chandelier hangs clear of the things of the walls in every home (spec 3 29)`() {
        for (house in HomeCatalog.houses.filter { it.drawn }) {
            val art = art(house.id, SceneMode.EVENING)
            val chandelier = art.items.getValue("chandelier")
            HomeCatalog.items.filter { it.drawn && (it.slot == "wallM" || it.slot == "wallL") && HomeRules.slotIn(it.slot, house.id) }.forEach { item ->
                val thing = art.items.getValue(item.id)
                val meet = chandelier.left < thing.right && thing.left < chandelier.right && chandelier.top < thing.bottom && thing.top < chandelier.bottom
                assertFalse("the chandelier meets ${item.id} in ${house.id}", meet)
            }
        }
    }

    @Test
    fun `the chandelier brings its own framing for a shelf - its rod would make it a dot there (spec 3 29)`() {
        val art = art("rent", SceneMode.EVENING)
        val chandelier = art.items.getValue("chandelier")
        val shelf = chandelier.shelf!!
        assertTrue(shelf.height < (chandelier.bottom - chandelier.top) / 2)
        assertTrue(shelf.bottom <= chandelier.bottom + EDGE && shelf.left <= chandelier.left)
        assertNull(art.items.getValue("metronome").shelf)
    }

    private companion object {
        const val WALL_TOP = -120f
        const val FLOOR_TOP = 200f
        const val FLOOR_BOTTOM = 600f

        /** How far short of an edge a pattern may stop: a motif is round, a row of the floor ends where the next begins. */
        const val EDGE = 12f
    }
}
