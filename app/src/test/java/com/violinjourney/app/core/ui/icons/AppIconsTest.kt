package com.violinjourney.app.core.ui.icons

import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathNode
import androidx.compose.ui.graphics.vector.VectorPath
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppIconsTest {
    @Test
    fun `every icon of the handoff builds on the 24 grid`() {
        AppIcons.all.forEach { (name, build) ->
            val icon = build()
            assertEquals("the name in the list is the icon's own", name, icon.name)
            assertEquals(name, 24.dp, icon.defaultWidth)
            assertEquals(name, 24f, icon.viewportHeight, 0f)
            assertTrue("$name has no paths", icon.root.size > 0)
        }
    }

    /** The lists are kept by hand: an icon declared and left out of them would never be built by these tests. */
    @Test
    fun `every icon declared is in the lists the tests build`() {
        fun declared(type: Class<*>) = AppIcons::class.java.declaredMethods
            .filter { it.parameterCount == 0 && it.returnType == type && it.name.startsWith("get") }
            .map { it.name.removePrefix("get") }
            .toSet()

        val plain = AppIcons.all.map { it.first }
        assertEquals("no icon is listed twice", plain.size, plain.toSet().size)
        assertEquals(declared(ImageVector::class.java), plain.toSet())
        assertEquals(declared(TabIcon::class.java), AppIcons.tabs.map { it().normal.name }.toSet())
    }

    @Test
    fun `every path parses into nodes that start with a move`() {
        val vectors = AppIcons.all.map { it.second() } + AppIcons.tabs.flatMap { listOfNotNull(it().normal, it().selected, it().selectedCut) }
        vectors.forEach { vector ->
            vector.root.filterIsInstance<VectorPath>().forEach { path ->
                assertTrue("${vector.name}: empty path", path.pathData.size > 1)
                val first = path.pathData.first()
                assertTrue("${vector.name}: starts with $first", first is PathNode.MoveTo || first is PathNode.RelativeMoveTo)
            }
        }
    }

    @Test
    fun `a filled-only path has no stroke, an outline has the stroke of the set`() {
        val more = AppIcons.More.root.filterIsInstance<VectorPath>()
        assertTrue(more.all { it.fill != null && it.stroke == null })
        val back = AppIcons.Back.root.filterIsInstance<VectorPath>().single()
        assertNull(back.fill)
        assertEquals(1.8f, back.strokeLineWidth, 0f)
    }

    @Test
    fun `a selected tab fills its body and keeps the cut-out detail apart`() {
        val practice = AppIcons.TabPractice
        assertTrue(practice.normal.root.filterIsInstance<VectorPath>().all { it.fill == null })
        assertTrue(practice.selected.root.filterIsInstance<VectorPath>().any { it.fill != null })
        assertNotNull("the hand of the stopwatch", practice.selectedCut)
        assertEquals(1, practice.selectedCut!!.root.size)
        assertEquals(3, practice.selected.root.size)

        // the string runs through the filled lens of its swing, the nut and the bridge stay lines
        assertEquals(1, AppIcons.TabLive.selectedCut!!.root.size)
        assertEquals(3, AppIcons.TabLive.selected.root.size)
        // the reels and the window of the cassette are cut out of its body
        assertEquals(3, AppIcons.TabRecords.selectedCut!!.root.size)
        assertEquals(1, AppIcons.TabRecords.selected.root.size)

        // the sheet of music (spec 3.36.1): its body filled, the three lines of the staff and the note head cut out
        val repertoire = AppIcons.TabRepertoire
        assertEquals(1, repertoire.selected.root.size)
        assertTrue(repertoire.selected.root.filterIsInstance<VectorPath>().single().fill != null)
        assertEquals(4, repertoire.selectedCut!!.root.size)
        // in outline the body is not filled, but the note head is always a filled dot without a stroke
        val outline = repertoire.normal.root.filterIsInstance<VectorPath>()
        assertEquals(5, outline.size)
        assertEquals(1, outline.count { it.fill != null })
        assertTrue(outline.single { it.fill != null }.stroke == null)
    }
}
