package com.violinjourney.app.feature.live

import androidx.compose.ui.Modifier
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/** The dimming of the controls must compare equal while nothing changed, or no control of Live would skip its composition. */
class LiveChromeTest {
    private val light: () -> Float = { 0.5f }

    @Test
    fun `the same dimming made twice is equal`() {
        assertEquals(Modifier.chrome(0.38f, light), Modifier.chrome(0.38f, light))
        assertEquals(Modifier.chrome(0.38f, light).hashCode(), Modifier.chrome(0.38f, light).hashCode())
    }

    @Test
    fun `another base or another light is another dimming`() {
        assertNotEquals(Modifier.chrome(0.38f, light), Modifier.chrome(1f, light))
        assertNotEquals(Modifier.chrome(0.38f, light), Modifier.chrome(0.38f) { 0.5f })
    }
}
