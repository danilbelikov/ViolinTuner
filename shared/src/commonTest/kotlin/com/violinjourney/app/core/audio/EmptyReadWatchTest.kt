package com.violinjourney.app.core.audio

import com.violinjourney.app.core.domain.IntonationConfig
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.Test

class EmptyReadWatchTest {
    private val watch = EmptyReadWatch(IntonationConfig(), 48_000)

    @Test
    fun `the wait after an empty read is one hop`() {
        assertEquals(10, watch.waitMs) // 512 samples at 48 kHz
    }

    @Test
    fun `two seconds of empty reads give the input up`() {
        repeat(200) { assertFalse(watch.isDead(0), "read ${it + 1}") }
        assertTrue(watch.isDead(0))
    }

    @Test
    fun `a read with samples starts the count again`() {
        repeat(150) { watch.isDead(0) }
        assertFalse(watch.isDead(512))
        repeat(200) { assertFalse(watch.isDead(0)) }
    }

    @Test
    fun `the wait is never zero at a high rate`() {
        assertEquals(1, EmptyReadWatch(IntonationConfig(hopSizeSamples = 1), 96_000).waitMs)
    }
}
