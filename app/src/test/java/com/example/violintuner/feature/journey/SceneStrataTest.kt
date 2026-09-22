package com.example.violintuner.feature.journey

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.vector.PathNode
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.IntRect
import com.example.violintuner.feature.journey.art.SceneAnim
import com.example.violintuner.feature.journey.art.SceneBaking
import com.example.violintuner.feature.journey.art.SceneLayer
import com.example.violintuner.feature.journey.art.SceneMode
import com.example.violintuner.feature.journey.art.SceneMotion
import com.example.violintuner.feature.journey.art.SceneParser
import com.example.violintuner.feature.journey.art.SceneStep
import com.example.violintuner.feature.journey.art.SceneStrata
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SceneStrataTest {
    private fun layer(anim: String = "") = SceneLayer("wall", 1, 1f, 0f, 0f, 1f, false, null, 0f, false, "M0 0Z", SceneAnim.parse(anim))

    /** The steps as the order the layers are drawn in, the sky's life as −1. */
    private fun order(steps: List<SceneStep>): List<Int> = steps.flatMap { step ->
        when (step) {
            is SceneStep.Still -> step.layers.toList()
            is SceneStep.Alive -> step.layers.toList()
            SceneStep.SkyLife -> listOf(-1)
        }
    }

    @Test
    fun aStillLayerThatNeverTouchesALivingOneSinksUnderIt_soTheStillOnesBakeTogether() {
        val bounds = listOf(Rect(0f, 0f, 10f, 10f), Rect(20f, 0f, 30f, 10f), Rect(40f, 0f, 50f, 10f))
        val steps = SceneStrata.plan(List(3) { layer() }, bounds, listOf(false, true, false), -1, null)
        assertEquals(2, steps.size)
        assertEquals(listOf(0, 2), (steps[0] as SceneStep.Still).layers.toList())
        assertEquals(listOf(1), (steps[1] as SceneStep.Alive).layers.toList())
    }

    @Test
    fun whatOverlapsKeepsItsOrder_aLampInFrontOfTheStreetStaysInFrontOfTheTram() {
        // the tram rides the whole street: the lamp at the other end of it is still in its way
        val layers = listOf(layer(), layer("ride:16:-200:200"), layer())
        val bounds = listOf(Rect(0f, 0f, 400f, 100f), Rect(0f, 60f, 40f, 90f), Rect(200f, 50f, 210f, 95f))
        val steps = SceneStrata.plan(layers, bounds, listOf(false, true, false), -1, null)
        assertEquals(listOf(0, 1, 2), order(steps))
        assertEquals(3, steps.size)
    }

    @Test
    fun theSkysLifeStaysOverItsSkyAndUnderWhatIsDrawnOverIt() {
        val bounds = listOf(Rect(0f, 0f, 412f, 190f), Rect(100f, 20f, 140f, 180f))
        val steps = SceneStrata.plan(List(2) { layer() }, bounds, listOf(false, false), 0, SceneMotion.skyLifeReach(high = true))
        assertEquals(listOf(0, -1, 1), order(steps))
        assertTrue(steps[1] == SceneStep.SkyLife)
    }

    @Test
    fun aSharpCornerOfAnOutlineIsWithinTheReach() {
        // a stroke of 2 may miter out to 4 at a corner: half the width times the limit of 4
        val outlined = SceneLayer("wall", 1, 1f, 0f, 0f, 1f, false, "trim", 2f, false, "M0 0Z", null)
        assertEquals(Rect(-5f, -5f, 15f, 15f), SceneStrata.reachOf(outlined, Rect(0f, 0f, 10f, 10f)))
    }

    @Test
    fun theReachOfAStepIsAllItsLayersTogether() {
        val bounds = listOf(Rect(0f, 0f, 10f, 10f), Rect(50f, 20f, 60f, 40f), Rect(-5f, 5f, 0f, 8f))
        assertEquals(Rect(-6f, -1f, 61f, 41f), SceneStrata.reachOf(List(3) { layer() }, bounds, listOf(0, 1, 2)))
        assertEquals(Rect(-1f, -1f, 11f, 11f), SceneStrata.reachOf(List(3) { layer() }, bounds, listOf(0)))
        assertEquals(null, SceneStrata.reachOf(List(3) { layer() }, bounds, emptyList()))
    }

    @Test
    fun aBakedPictureTakesTheWholePixelsItsLayersTouch_withinTheBox() {
        // two pixels a unit, the grid's origin at 10, 20 of a box 100 × 80
        val view = SceneBaking.View(100f, 80f, 10f, 20f, 2f)
        assertEquals(IntRect(12, 24, 31, 41), SceneBaking.pixelsOf(Rect(1.2f, 2.4f, 10.4f, 10.3f), view))
        // what reaches beyond the box is cut at its edges
        assertEquals(IntRect(0, 0, 100, 80), SceneBaking.pixelsOf(Rect(-100f, -100f, 500f, 500f), view))
        // wholly beside the box, or above it: nothing to bake
        assertEquals(null, SceneBaking.pixelsOf(Rect(50f, 0f, 60f, 10f), view))
        assertEquals(null, SceneBaking.pixelsOf(Rect(0f, -40f, 10f, -10f), view))
    }

    @Test
    fun everySceneKeepsEveryLayerOnce_andNoTwoLayersThatMeetChangePlaces() {
        val assets = File("src/main/assets")
        // the rooms of the home are a catalogue the home puts together; its title cards are scenes like the postcards
        val files = File(assets, "journey").listFiles { f -> f.name.endsWith(".scene") }.orEmpty().toList() +
            File(assets, "home").listFiles { f -> f.name.startsWith("splash") }.orEmpty().toList()
        assertTrue(files.size > 90)
        for (file in files) {
            val scene = SceneParser.parse(file.readText())
            val mode = if (file.name.contains(".day.")) SceneMode.DAY else SceneMode.EVENING
            val bounds = scene.layers.map { boundsOf(it.path) }
            val alive = scene.layers.map { SceneMotion.moves(it, mode) }
            val high = scene.layers.any { it.fill == SceneLayer.SKY_HIGH }
            val skyAt = if (scene.aerial) scene.layers.indexOfFirst { it.fill == if (high) SceneLayer.SKY_HIGH else SceneLayer.SKY } else -1
            val sky = if (scene.aerial) SceneMotion.skyLifeReach(high) else null
            val steps = SceneStrata.plan(scene.layers, bounds, alive, skyAt, sky)
            val drawn = order(steps).filter { it >= 0 }
            assertEquals("${file.name}: every layer once", scene.layers.indices.toList(), drawn.sorted())
            val place = IntArray(scene.layers.size).also { at -> drawn.forEachIndexed { i, index -> at[index] = i } }
            val reach = scene.layers.mapIndexed { i, l -> SceneStrata.reachOf(l, bounds[i]) }
            for (a in scene.layers.indices) for (b in a + 1 until scene.layers.size) {
                if (reach[a].overlaps(reach[b])) assertTrue("${file.name}: layers $a and $b meet but changed places", place[a] < place[b])
            }
            // the steps take turns: still, living, still… never two of a kind in a row (the sky's life is living)
            val kinds = steps.map { it is SceneStep.Still }
            assertTrue(file.name, kinds.zipWithNext().none { (x, y) -> x && y })
        }
    }

    /** Where a path lies, from its points and control points (a little too wide for curves, never too narrow). */
    private fun boundsOf(d: String): Rect {
        var x = 0f
        var y = 0f
        var startX = 0f
        var startY = 0f
        var left = Float.MAX_VALUE
        var top = Float.MAX_VALUE
        var right = -Float.MAX_VALUE
        var bottom = -Float.MAX_VALUE
        fun see(px: Float, py: Float) {
            left = minOf(left, px); right = maxOf(right, px); top = minOf(top, py); bottom = maxOf(bottom, py)
        }
        for (node in PathParser().parsePathString(d).toNodes()) {
            when (node) {
                is PathNode.MoveTo -> { x = node.x; y = node.y; startX = x; startY = y; see(x, y) }
                is PathNode.RelativeMoveTo -> { x += node.dx; y += node.dy; startX = x; startY = y; see(x, y) }
                is PathNode.LineTo -> { x = node.x; y = node.y; see(x, y) }
                is PathNode.RelativeLineTo -> { x += node.dx; y += node.dy; see(x, y) }
                is PathNode.HorizontalTo -> { x = node.x; see(x, y) }
                is PathNode.RelativeHorizontalTo -> { x += node.dx; see(x, y) }
                is PathNode.VerticalTo -> { y = node.y; see(x, y) }
                is PathNode.RelativeVerticalTo -> { y += node.dy; see(x, y) }
                is PathNode.CurveTo -> { see(node.x1, node.y1); see(node.x2, node.y2); x = node.x3; y = node.y3; see(x, y) }
                is PathNode.RelativeCurveTo -> { see(x + node.dx1, y + node.dy1); see(x + node.dx2, y + node.dy2); x += node.dx3; y += node.dy3; see(x, y) }
                is PathNode.ReflectiveCurveTo -> { see(node.x1, node.y1); x = node.x2; y = node.y2; see(x, y) }
                is PathNode.RelativeReflectiveCurveTo -> { see(x + node.dx1, y + node.dy1); x += node.dx2; y += node.dy2; see(x, y) }
                is PathNode.QuadTo -> { see(node.x1, node.y1); x = node.x2; y = node.y2; see(x, y) }
                is PathNode.RelativeQuadTo -> { see(x + node.dx1, y + node.dy1); x += node.dx2; y += node.dy2; see(x, y) }
                is PathNode.ReflectiveQuadTo -> { x = node.x; y = node.y; see(x, y) }
                is PathNode.RelativeReflectiveQuadTo -> { x += node.dx; y += node.dy; see(x, y) }
                is PathNode.ArcTo -> { see(x - node.horizontalEllipseRadius, y - node.verticalEllipseRadius); see(x + node.horizontalEllipseRadius, y + node.verticalEllipseRadius); x = node.arcStartX; y = node.arcStartY; see(x - node.horizontalEllipseRadius, y - node.verticalEllipseRadius); see(x + node.horizontalEllipseRadius, y + node.verticalEllipseRadius) }
                is PathNode.RelativeArcTo -> { see(x - node.horizontalEllipseRadius, y - node.verticalEllipseRadius); see(x + node.horizontalEllipseRadius, y + node.verticalEllipseRadius); x += node.arcStartDx; y += node.arcStartDy; see(x - node.horizontalEllipseRadius, y - node.verticalEllipseRadius); see(x + node.horizontalEllipseRadius, y + node.verticalEllipseRadius) }
                PathNode.Close -> { x = startX; y = startY }
            }
        }
        return if (left > right) Rect.Zero else Rect(left, top, right, bottom)
    }
}
