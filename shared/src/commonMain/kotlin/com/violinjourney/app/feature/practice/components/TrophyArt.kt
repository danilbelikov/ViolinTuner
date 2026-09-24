package com.violinjourney.app.feature.practice.components

/** What a part of a trophy is made of; the theme turns it into a color. */
enum class TrophyMaterial {
    WOOD, WOOD_LIGHT, EBONY, EBONY_LIGHT, SILVER, SILVER_LIGHT, GOLD, ROSIN, ROSIN_LIGHT, ROSIN_GLINT, HAIR, PAPER, INK,
    VELVET, VELVET_DARK, CASE,
}

data class ArtPoint(val x: Float, val y: Float)

sealed interface ArtShape {
    data class Rect(val x: Float, val y: Float, val width: Float, val height: Float, val corner: Float = 0f) : ArtShape

    data class Circle(val cx: Float, val cy: Float, val r: Float) : ArtShape

    data class Ellipse(val cx: Float, val cy: Float, val rx: Float, val ry: Float) : ArtShape

    data class Polygon(val points: List<ArtPoint>) : ArtShape

    /** Open lines; only ever stroked. */
    data class Lines(val segments: List<List<ArtPoint>>) : ArtShape
}

sealed interface ArtPaint {
    val material: TrophyMaterial
    val opacity: Float

    data class Fill(override val material: TrophyMaterial, override val opacity: Float = 1f) : ArtPaint

    data class Stroke(
        override val material: TrophyMaterial,
        val width: Float,
        val roundCap: Boolean = false,
        override val opacity: Float = 1f,
    ) : ArtPaint
}

data class ArtPart(val shape: ArtShape, val paint: ArtPaint)

/**
 * The ten trophies as primitives in a 64 × 64 box, back to front — a transcription of the
 * `TROPHIES` array of the handoff (`Прогресс.dc.html`). Kept as data rather than twenty vector
 * drawables because the trophy not yet given is the same drawing under one rule (outline, no
 * fill), and the rule lives in one place: [TrophyIcon].
 */
object TrophyArt {
    const val VIEW_BOX = 64f

    /** Outline of a trophy not yet given, in view box units: about 1 dp at 40 dp. */
    const val LOCKED_STROKE = 1.5f

    /** A stroked part keeps its weight when locked, up to this. */
    const val LOCKED_STROKE_MAX = 2.5f

    /** Hairline around ebony parts of a given trophy. */
    const val EBONY_EDGE = 1f

    /** Null for a mark the design has no drawing for. */
    fun of(hours: Int): List<ArtPart>? = BY_HOURS[hours]

    val hours: Set<Int> get() = BY_HOURS.keys

    private fun p(x: Number, y: Number) = ArtPoint(x.toFloat(), y.toFloat())

    private fun rect(x: Number, y: Number, w: Number, h: Number, corner: Number, material: TrophyMaterial, opacity: Float = 1f) =
        ArtPart(ArtShape.Rect(x.toFloat(), y.toFloat(), w.toFloat(), h.toFloat(), corner.toFloat()), ArtPaint.Fill(material, opacity))

    private fun circle(cx: Number, cy: Number, r: Number, material: TrophyMaterial, opacity: Float = 1f) =
        ArtPart(ArtShape.Circle(cx.toFloat(), cy.toFloat(), r.toFloat()), ArtPaint.Fill(material, opacity))

    private fun ring(cx: Number, cy: Number, r: Number, material: TrophyMaterial, width: Float, opacity: Float = 1f) =
        ArtPart(ArtShape.Circle(cx.toFloat(), cy.toFloat(), r.toFloat()), ArtPaint.Stroke(material, width, opacity = opacity))

    private fun ellipse(cx: Number, cy: Number, rx: Number, ry: Number, material: TrophyMaterial, opacity: Float = 1f) =
        ArtPart(ArtShape.Ellipse(cx.toFloat(), cy.toFloat(), rx.toFloat(), ry.toFloat()), ArtPaint.Fill(material, opacity))

    private fun polygon(material: TrophyMaterial, vararg points: ArtPoint) =
        ArtPart(ArtShape.Polygon(points.toList()), ArtPaint.Fill(material))

    private fun line(material: TrophyMaterial, width: Float, roundCap: Boolean, vararg points: ArtPoint) =
        ArtPart(ArtShape.Lines(listOf(points.toList())), ArtPaint.Stroke(material, width, roundCap))

    private val ROSIN = listOf(
        rect(8, 44, 48, 6, 3, TrophyMaterial.EBONY_LIGHT),
        rect(12, 20, 40, 26, 6, TrophyMaterial.ROSIN),
        rect(12, 20, 40, 10, 5, TrophyMaterial.ROSIN_LIGHT),
        circle(20, 26, 2.5, TrophyMaterial.ROSIN_GLINT, opacity = .7f),
    )

    private val PEG = listOf(
        rect(29, 22, 6, 34, 3, TrophyMaterial.EBONY_LIGHT),
        ellipse(32, 16, 13, 9, TrophyMaterial.EBONY),
        ellipse(28, 13, 4, 2, TrophyMaterial.EBONY_LIGHT),
        rect(27, 24, 10, 3, 1.5, TrophyMaterial.SILVER),
    )

    private val STRING = listOf(
        ring(32, 32, 18, TrophyMaterial.SILVER, width = 5f),
        ring(32, 32, 11, TrophyMaterial.SILVER, width = 2.5f),
        circle(50, 32, 5, TrophyMaterial.GOLD),
    )

    private val BOW = listOf(
        line(TrophyMaterial.HAIR, 1.5f, false, p(14, 56), p(56, 14)),
        line(TrophyMaterial.WOOD, 3.5f, true, p(10, 54), p(54, 10)),
        polygon(TrophyMaterial.EBONY, p(8, 50), p(16, 58), p(22, 52), p(14, 44)),
        polygon(TrophyMaterial.SILVER_LIGHT, p(52, 8), p(58, 12), p(54, 14)),
    )

    private val CHINREST = listOf(
        rect(40, 38, 6, 16, 2, TrophyMaterial.SILVER),
        rect(50, 38, 6, 16, 2, TrophyMaterial.SILVER),
        rect(38, 52, 20, 4, 2, TrophyMaterial.SILVER),
        ellipse(30, 30, 22, 14, TrophyMaterial.EBONY),
        ellipse(28, 28, 14, 8, TrophyMaterial.EBONY_LIGHT),
    )

    private val TUNING_FORK = listOf(
        rect(22, 8, 5, 28, 2.5, TrophyMaterial.SILVER),
        rect(37, 8, 5, 28, 2.5, TrophyMaterial.SILVER),
        rect(22, 32, 20, 6, 3, TrophyMaterial.SILVER),
        rect(29.5, 36, 5, 20, 2.5, TrophyMaterial.SILVER),
        circle(32, 57, 4, TrophyMaterial.SILVER_LIGHT),
        rect(23.5, 10, 1.5, 22, .75, TrophyMaterial.SILVER_LIGHT, opacity = .7f),
    )

    private val MUSIC_STAND = listOf(
        polygon(TrophyMaterial.EBONY_LIGHT, p(12, 14), p(52, 14), p(48, 40), p(16, 40)),
        rect(20, 18, 24, 18, 1, TrophyMaterial.PAPER),
        rect(24, 23, 16, 1.5, 0, TrophyMaterial.INK),
        rect(24, 27, 16, 1.5, 0, TrophyMaterial.INK),
        rect(24, 31, 10, 1.5, 0, TrophyMaterial.INK),
        rect(30.5, 40, 3, 14, 0, TrophyMaterial.SILVER),
        line(TrophyMaterial.SILVER, 3f, true, p(20, 58), p(32, 52), p(44, 58)),
    )

    private val CASE = listOf(
        ArtPart(ArtShape.Rect(26f, 14f, 12f, 8f, 4f), ArtPaint.Stroke(TrophyMaterial.SILVER, 2.5f)),
        rect(8, 20, 48, 28, 10, TrophyMaterial.CASE),
        rect(12, 24, 40, 20, 7, TrophyMaterial.VELVET),
        ellipse(32, 34, 9, 5, TrophyMaterial.VELVET_DARK),
        rect(20, 46, 6, 4, 1, TrophyMaterial.SILVER),
        rect(38, 46, 6, 4, 1, TrophyMaterial.SILVER),
    )

    private val VIOLIN = listOf(
        rect(30, 6, 4, 28, 1, TrophyMaterial.EBONY),
        circle(32, 7, 3.5, TrophyMaterial.EBONY),
        ellipse(32, 36, 10, 8, TrophyMaterial.WOOD),
        ellipse(32, 49, 13, 10, TrophyMaterial.WOOD),
        rect(25, 38, 14, 10, 0, TrophyMaterial.WOOD),
        ellipse(28, 46, 5, 8, TrophyMaterial.WOOD_LIGHT, opacity = .35f),
        ArtPart(
            ArtShape.Lines(listOf(listOf(p(26, 44), p(26, 50)), listOf(p(38, 44), p(38, 50)))),
            ArtPaint.Stroke(TrophyMaterial.EBONY, 1.5f, roundCap = true),
        ),
        polygon(TrophyMaterial.EBONY, p(30, 52), p(34, 52), p(33, 58), p(31, 58)),
    )

    private val CONCERT = listOf(
        ring(32, 32, 29, TrophyMaterial.GOLD, width = 1.5f, opacity = .8f),
        line(TrophyMaterial.GOLD, 2.5f, true, p(12, 54), p(52, 12)),
        rect(30, 10, 4, 26, 1, TrophyMaterial.EBONY),
        circle(32, 11, 3, TrophyMaterial.EBONY),
        ellipse(32, 37, 9, 7, TrophyMaterial.WOOD),
        ellipse(32, 48, 12, 9, TrophyMaterial.WOOD),
        rect(26, 39, 12, 9, 0, TrophyMaterial.WOOD),
        ellipse(29, 46, 4, 7, TrophyMaterial.WOOD_LIGHT, opacity = .35f),
        ArtPart(
            ArtShape.Lines(listOf(listOf(p(27, 44), p(27, 49)), listOf(p(37, 44), p(37, 49)))),
            ArtPaint.Stroke(TrophyMaterial.EBONY, 1.5f, roundCap = true),
        ),
    )

    private val BY_HOURS: Map<Int, List<ArtPart>> = mapOf(
        1 to ROSIN, 10 to PEG, 50 to STRING, 100 to BOW, 250 to CHINREST,
        500 to TUNING_FORK, 1000 to MUSIC_STAND, 2500 to CASE, 5000 to VIOLIN, 10_000 to CONCERT,
    )
}
