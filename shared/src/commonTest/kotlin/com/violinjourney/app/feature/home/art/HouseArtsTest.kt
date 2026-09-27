package com.violinjourney.app.feature.home.art

import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest

class HouseArtsTest {
    @Test
    fun `the tiles of one frame asking for one file at once get one art - the file is read once`() = runTest {
        val paths = mutableListOf<String>()
        val arts = HouseArts { path ->
            paths += path
            // a read takes time: every other tile asks meanwhile
            delay(100)
            ""
        }
        val asked = List(5) { async { arts.obtain("rent.eve", EmptyCoroutineContext) } }.map { it.await() }
        val first = assertNotNull(asked.first())
        asked.forEach { assertSame(first, it, "one instance: the keys of the pictures made from it compare it by identity") }
        assertSame(first, arts.peek("rent.eve"), "the first frame of the next visit finds that same one")
        assertSame(first, arts.obtain("rent.eve", EmptyCoroutineContext))
        assertEquals(listOf("home/rent.eve.scene"), paths)
    }

    @Test
    fun `a file that is not there is nothing and is asked for again`() = runTest {
        var reads = 0
        val arts = HouseArts { reads++; null }
        assertNull(arts.obtain("palace.day", EmptyCoroutineContext))
        assertNull(arts.obtain("palace.day", EmptyCoroutineContext))
        assertNull(arts.peek("palace.day"))
        assertEquals(2, reads)
    }
}
