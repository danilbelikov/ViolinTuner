package com.violinjourney.app.feature.sound.components

import kotlin.test.Test
import kotlin.test.assertEquals

/** How long the thumb of a «Звук» slider travels (handoff `anims`: «двойной тап — 200 мс к умолчанию»). */
class SliderTravelTest {
    @Test
    fun `back to where a reset asked for takes 200 ms`() {
        assertEquals(200, sliderTravelMs(target = 0.5f, resetTo = 0.5f))
    }

    @Test
    fun `any other move takes 150 ms`() {
        assertEquals(150, sliderTravelMs(target = 0.8f, resetTo = null), "a tap on the track, a step, a preset")
        assertEquals(150, sliderTravelMs(target = 0.8f, resetTo = 0.5f), "the value went elsewhere after the reset")
    }

    @Test
    fun `a reset goes to the default mark when the slider names no other place`() {
        val slider = SliderModel(label = "Зал", hint = null, valueText = "60 %", fraction = 0.6f, defaultFraction = 0.5f, bipolar = false)
        assertEquals(slider.defaultFraction, slider.resetFraction)
    }
}
