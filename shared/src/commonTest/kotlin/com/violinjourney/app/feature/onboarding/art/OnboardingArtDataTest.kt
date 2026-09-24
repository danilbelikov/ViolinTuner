package com.violinjourney.app.feature.onboarding.art

import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.Test

/** The scenes written by tools/onboarding/export.js hold together: every gradient named is there, every motion is known. */
class OnboardingArtDataTest {
    private val scenes = mapOf(
        "welcome" to OnboardingArtData.welcome,
        "live" to OnboardingArtData.live,
        "road" to OnboardingArtData.road,
        "data" to OnboardingArtData.data,
        "setupMicrophone" to OnboardingArtData.setupMicrophone,
        "setupReference" to OnboardingArtData.setupReference,
        "setupTolerance" to OnboardingArtData.setupTolerance,
    )

    @Test
    fun `every fill names a gradient of its own scene or a colour`() {
        scenes.forEach { (name, scene) ->
            scene.layers.forEach { layer ->
                val fill = layer.fill ?: return@forEach
                when {
                    fill.startsWith(GRADIENT_PREFIX) -> assertTrue(fill.removePrefix(GRADIENT_PREFIX) in scene.gradients, "$name: $fill")
                    fill == ZONE -> assertEquals("live", name, "zone only on the phone on the stand")
                    else -> assertTrue(Regex("#[0-9A-Fa-f]{6}").matches(fill), "$name: $fill")
                }
            }
        }
    }

    @Test
    fun `every motion is one the app knows — and every page shares one sky`() {
        scenes.values.flatMap { it.layers }.forEach { ArtMotion.parse(it.motion) }
        val sky = scenes.values.map { scene -> scene.layers.first { it.fill == "g:sky" }.d to scene.gradients.getValue("sky") }
        assertEquals(1, sky.toSet().size)
        scenes.values.forEach { scene -> assertTrue(scene.layers.any { it.fade }) }
    }

    @Test
    fun `the phone on the stand has all three marks`() {
        val marks = OnboardingArtData.live.layers.mapNotNull { it.motion }.filter { it.startsWith("mark:") }.toSet()
        assertEquals(setOf("mark:dot", "mark:up", "mark:down"), marks)
    }
}
