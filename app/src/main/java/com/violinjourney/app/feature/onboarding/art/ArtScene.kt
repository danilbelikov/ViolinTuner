package com.violinjourney.app.feature.onboarding.art

/**
 * A scene of «Знакомство» as the handoff draws it (series 36, `ONBOARDING_ART`): layers of SVG paths on a
 * grid of [OnboardingArtData.WIDTH] × [OnboardingArtData.HEIGHT]. Written by tools/onboarding/export.js.
 */
internal class ArtScene(val gradients: Map<String, ArtGradient>, val layers: List<ArtLayer>)

/** A stop of a gradient: [color] is `#RRGGBB`, or [ZONE] — the colour of the zone of the moment (36b). */
internal data class ArtStop(val offset: Float, val color: String, val alpha: Float)

internal sealed interface ArtGradient {
    val stops: List<ArtStop>

    data class Linear(val x1: Float, val y1: Float, val x2: Float, val y2: Float, override val stops: List<ArtStop>) : ArtGradient

    data class Radial(val cx: Float, val cy: Float, val r: Float, override val stops: List<ArtStop>) : ArtGradient
}

/**
 * One path. [fill] is `#RRGGBB`, `g:<gradient>` or [ZONE]; [depth] is how far it stands (0 the sky, 1 the
 * far land, 2 the near) and so how fast it moves when the pages are swiped; [motion] — how it lives
 * ([ArtMotion.parse]); [fade] — the band where the picture melts into the screen, drawn over everything.
 */
internal data class ArtLayer(
    val d: String,
    val depth: Int,
    val fill: String? = null,
    val stroke: String? = null,
    val strokeWidth: Float = 0f,
    val opacity: Float = 1f,
    val motion: String? = null,
    val evenOdd: Boolean = false,
    val fade: Boolean = false,
)

internal const val ZONE = "zone"
internal const val GRADIENT_PREFIX = "g:"
