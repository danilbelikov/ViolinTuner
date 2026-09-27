package com.violinjourney.app.feature.live

import androidx.compose.ui.Modifier
import kotlin.test.Test
import kotlin.test.assertNotEquals

class LiveFrameRateIosTest {
    @Test
    fun `on iOS Live asks for its own frame rate`() {
        assertNotEquals<Modifier>(Modifier, Modifier.liveFrameRate())
    }
}
