package com.example.violintuner.feature.journey

import com.example.violintuner.core.domain.journey.JourneyRoute
import com.example.violintuner.feature.journey.art.JourneySilhouettes
import com.example.violintuner.feature.journey.art.Scene
import com.example.violintuner.feature.journey.art.SceneAnim
import com.example.violintuner.feature.journey.art.SceneLayer
import com.example.violintuner.feature.journey.art.SceneMode
import com.example.violintuner.feature.journey.art.ScenePalette
import com.example.violintuner.feature.journey.art.SceneParser
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SceneTest {
    private val assets = File("src/main/assets/journey")

    @Test
    fun `a scene reads its header and its layers, absent fields fall back`() {
        val scene = SceneParser.parse(
            "# comment\nloc=vienna;aerial=1\n" +
                "wall\t1\t\t\t\t\t\t\t\t\tM0 0h10v10Z\t\n" +
                "rgba(20,16,30,.28)\t2\t0.5\t3\t4\t2\t1\ttrim\t1.5\t1\tM1 1h2Z\tride:16:-192:516\n",
        )
        // a layer filled with a literal colour starts with «#» and is not a comment (a red tram once lost its body to this)
        assertEquals(1, SceneParser.parse("# comment\nloc=vienna;aerial=1\n#C8322B\t2\t\t\t\t\t\t\t\t\tM0 0h1v1Z\t\n").layers.size)
        assertEquals("vienna", scene.location)
        assertTrue(scene.aerial)
        assertEquals(SceneLayer("wall", 1, 1f, 0f, 0f, 1f, false, null, 0f, false, "M0 0h10v10Z"), scene.layers[0])
        assertEquals(SceneLayer("rgba(20,16,30,.28)", 2, 0.5f, 3f, 4f, 2f, true, "trim", 1.5f, true, "M1 1h2Z", SceneAnim(ride = SceneAnim.Ride(16f, -192f, 516f))), scene.layers[1])
    }

    @Test
    fun `colours are parsed, mixed towards the low sky by depth, and left alone indoors and up close`() {
        assertEquals(0xFFD9B26B, ScenePalette.parse("#D9B26B"))
        assertEquals(0x471E1014L, ScenePalette.parse("rgba(30,16,20,.28)"))
        assertNull(ScenePalette.parse("wall"))
        assertEquals(0xFF808080, ScenePalette.mix(0xFF000000, 0xFFFFFFFF, 0.5f))
        assertEquals(0xFF000000, ScenePalette.mix(0xFF000000, 0xFFFFFFFF, 0f))

        val outdoors = Scene("vienna", aerial = true, layers = emptyList())
        val near = ScenePalette.colorOf("wall", 2, outdoors, SceneMode.EVENING)
        val far = ScenePalette.colorOf("wall", 0, outdoors, SceneMode.EVENING)
        assertEquals(0xFFD9B26B, near)
        assertTrue("the far plane takes the colour of the air", far != near)
        assertEquals(near, ScenePalette.colorOf("wall", 0, outdoors.copy(aerial = false), SceneMode.EVENING))
        assertNull(ScenePalette.colorOf("noSuchToken", 1, outdoors, SceneMode.DAY))
    }

    @Test
    fun `every stop has its hall seen from the stage for Live - indoors, from the ceiling to the boards under our feet`() {
        for (stop in JourneyRoute.stops.drop(1)) for (mode in SceneMode.entries) {
            val file = File(assets, "${stop.id}Stage.${mode.suffix}.scene")
            assertTrue("${file.name} is not exported (tools/journey/stage-scenes.js)", file.isFile)
            val scene = SceneParser.parse(file.readText())
            assertEquals("${stop.id}Stage", scene.location)
            assertTrue("a hall has no air", !scene.aerial)
        }
    }

    @Test
    fun `every exported scene parses, and every colour it names is known in both palettes`() {
        val files = assets.listFiles { file -> file.name.endsWith(".scene") }.orEmpty()
        assertTrue("the scenes are exported from the handoff into the assets", files.size >= 12)
        for (file in files) {
            val scene = SceneParser.parse(file.readText())
            val mode = if (file.name.contains(".day.")) SceneMode.DAY else SceneMode.EVENING
            assertTrue(file.name, scene.layers.size > 20)
            for (layer in scene.layers) {
                if (layer.fill != SceneLayer.SKY && layer.fill != SceneLayer.GLOW && !layer.warmGlow && !layer.fillNone) {
                    assertNotNull("${file.name}: ${layer.fill}", ScenePalette.colorOf(layer.fill, layer.depth, scene, mode))
                }
                layer.stroke?.let { assertNotNull("${file.name}: stroke $it", ScenePalette.colorOf(it, layer.depth, scene, mode)) }
                assertTrue(layer.path.startsWith("M"))
            }
        }
    }

    @Test
    fun `every view of the route has its picture in both times of day, every stop its silhouette`() {
        for (stop in JourneyRoute.stops) {
            assertNotNull(stop.id, JourneySilhouettes.paths[stop.id])
            for (view in stop.views) for (mode in SceneMode.entries) {
                assertTrue("${view.scene}.${mode.suffix}", File(assets, "${view.scene}.${mode.suffix}.scene").exists())
            }
        }
    }
}
