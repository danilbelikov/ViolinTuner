package com.violinjourney.app.core.audio

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class HopSplitterTest {
    @Test
    fun `blocks of any size come out as whole hops — the rest waits for the next block`() {
        val splitter = HopSplitter(4)
        val hops = ArrayList<List<Short>>()
        val take: (ShortArray) -> Unit = { hops += it.toList() }
        splitter.push(FloatArray(3) { 0f }, onHop = take)
        assertEquals(0, hops.size)
        splitter.push(FloatArray(6) { 0f }, onHop = take)
        assertEquals(2, hops.size)
        splitter.push(FloatArray(10) { 0f }, count = 3, onHop = take)
        assertEquals(3, hops.size)
    }

    @Test
    fun `a reused chunk read partly gives the hops of exact blocks`() {
        val signal = FloatArray(40) { it / 64f }
        val blocks = listOf(signal.sliceArray(0..6), signal.sliceArray(7..20), signal.sliceArray(21..39))
        val exact = ArrayList<List<Short>>()
        val whole = HopSplitter(8)
        blocks.forEach { block -> whole.push(block) { exact += it.toList() } }

        val reused = ArrayList<List<Short>>()
        val chunk = FloatArray(32) { 0.99f } // what an earlier read left behind the count must not be taken
        val partly = HopSplitter(8)
        blocks.forEach { block ->
            block.copyInto(chunk)
            partly.push(chunk, block.size) { reused += it.toList() }
        }
        assertEquals(5, exact.size)
        assertEquals(exact, reused)
    }

    @Test
    fun `samples keep their order across blocks and are scaled as PCM16`() {
        val splitter = HopSplitter(4)
        val hops = ArrayList<List<Short>>()
        splitter.push(floatArrayOf(0f, 0.5f), onHop = { hops += it.toList() })
        splitter.push(floatArrayOf(-0.5f, 1f, -1f), onHop = { hops += it.toList() })
        assertContentEquals(listOf<Short>(0, 16384, -16383, 32767), hops.single())
    }

    @Test
    fun `beyond full scale is clipped — not wrapped`() {
        val splitter = HopSplitter(2)
        var hop = emptyList<Short>()
        splitter.push(floatArrayOf(1.7f, -3f), onHop = { hop = it.toList() })
        assertContentEquals(listOf<Short>(32767, -32767), hop)
    }

    /** A broken input may hand over NaN: `roundToInt` refuses it, and a throw on the microphone's thread ends the app. */
    @Test
    fun `a sample that is not a number is silence and infinity is clipped`() {
        val splitter = HopSplitter(4)
        var hop = emptyList<Short>()
        splitter.push(floatArrayOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, 0.5f), onHop = { hop = it.toList() })
        assertContentEquals(listOf<Short>(0, 32767, -32767, 16384), hop)
    }
}
