package com.violinjourney.app.feature.live

import androidx.compose.ui.Modifier
import kotlin.test.Test
import kotlin.test.assertSame

class LiveFrameRateTest {
    @Test
    fun `Android draws Live as it did`() {
        assertSame(Modifier, Modifier.liveFrameRate())
    }
}
