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
}
