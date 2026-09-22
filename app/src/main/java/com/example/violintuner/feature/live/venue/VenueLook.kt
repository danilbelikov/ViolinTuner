package com.example.violintuner.feature.live.venue

import com.example.violintuner.core.domain.Zone

/**
 * How the picture behind Live answers the light (spec 3.27, 5.20; handoff `venue.*`). Pure numbers
 * and colour arithmetic; the screen draws with them.
 *
 * Putting the light out is one affine map of every colour: the saturation goes down to
 * [DIM_SATURATION], then the colour is mixed with the surface by [DIM_MIX]. Done to the whole
 * picture at once it is exactly what the handoff does to each colour of each layer — an affine map
 * commutes with alpha blending — and the picture itself need not be drawn again while it changes.
 */
object VenueLook {
    /** `venue.dim.mix`: the share of the surface in every colour of the picture when the light is out. */
    const val DIM_MIX = 0.76f

    /** `venue.dim.sat`: how much of the saturation is left. */
    const val DIM_SATURATION = 0.12f

    /** `venue.dim.lightDrop`: the glowing layers (lamps, candles, chandeliers) are put out separately. */
    const val LIGHT_DROP = 0.25f

    /** `venue.zone.tint`: the whole dark picture moves towards the zone colour by this × glow. */
    const val ZONE_TINT = 0.18f

    /** `venue.zone.light`: the light of the zone over the picture, blended as screen, × glow at its centre… */
    const val ZONE_LIGHT = 0.22f

    /** …this much at [ZONE_LIGHT_MID_STOP] of its reach, and nothing at the edge. */
    const val ZONE_LIGHT_MID = 0.07f
    const val ZONE_LIGHT_MID_STOP = 0.45f

    /** The light of the zone reaches this far, in radii of the veil. */
    const val ZONE_LIGHT_REACH = 1.35f

    /** `venue.zone.offScale`: a miss lights the hall less — it must not be the most dramatic frame. */
    const val OFF_SCALE = 0.7f

    /** `venue.veil`: the dark under the controls while the light is on, at the centre of the ring… */
    const val VEIL = 0.68f

    /** …and at [VEIL_MID_STOP] of its reach (0.68 × 0.68), nothing at the edge. */
    const val VEIL_MID = 0.4624f
    const val VEIL_MID_STOP = 0.42f

    /**
     * `venue.scrim.top`: the curtain over the top of the frame while the light is on — under the switcher, the
     * practice tag and the status line, upright and lying down alike. The veil is a circle round the ring and hardly
     * reaches the top edge: on the cream ceiling of Vienna the status line read 1.1 : 1; with the curtain and the
     * plate under the line it reads 5.2 : 1 (handoff venue, second version).
     */
    const val CURTAIN = 0.58f

    /** The share of the frame's height the curtain covers, fading from [CURTAIN] at the top edge to nothing. */
    const val CURTAIN_HEIGHT = 0.24f

    /** The reach of the veil, in diameters of the ring: 300 dp for the ring of 300 in the room, a little more in the bright halls. */
    const val VEIL_ROOM = 1f
    const val VEIL_HALL = 1.2f

    /** `venue.chrome.dim`: the controls one does not touch while playing; the recording and the note stay whole. */
    const val CHROME_DIM = 0.38f

    private const val LUMA_R = 0.2126f
    private const val LUMA_G = 0.7152f
    private const val LUMA_B = 0.0722f
    private const val CHANNEL_MAX = 255f

    /** The saturation kept at [darkness] 0..1. */
    fun saturationKept(darkness: Float): Float = 1f - darkness.coerceIn(0f, 1f) * (1f - DIM_SATURATION)

    /** The share of the surface mixed in at [darkness]. */
    fun surfaceShare(darkness: Float): Float = DIM_MIX * darkness.coerceIn(0f, 1f)

    /**
     * The colour matrix that puts the light out by [darkness] 0..1 over a surface of [surfaceRgb]
     * (three channels 0..1): 4 × 5, row-major, the offsets in 0..255 as Android and Compose read them.
     */
    fun dimMatrix(darkness: Float, surfaceRgb: FloatArray): FloatArray {
        val keep = saturationKept(darkness)
        val mix = surfaceShare(darkness)
        val grey = (1f - mix) * (1f - keep)
        val own = (1f - mix) * keep
        val luma = floatArrayOf(LUMA_R, LUMA_G, LUMA_B)
        val m = FloatArray(MATRIX_SIZE)
        for (row in 0 until CHANNELS) {
            for (column in 0 until CHANNELS) m[row * ROW + column] = grey * luma[column] + if (row == column) own else 0f
            m[row * ROW + OFFSET] = mix * surfaceRgb[row] * CHANNEL_MAX
        }
        m[ALPHA_ROW * ROW + ALPHA_ROW] = 1f
        return m
    }

    /** One colour (three channels 0..1) as [dimMatrix] leaves it: desaturated, then mixed with the surface. */
    fun dim(rgb: FloatArray, darkness: Float, surfaceRgb: FloatArray): FloatArray {
        val keep = saturationKept(darkness)
        val mix = surfaceShare(darkness)
        val y = LUMA_R * rgb[0] + LUMA_G * rgb[1] + LUMA_B * rgb[2]
        return FloatArray(CHANNELS) { i -> ((y + (rgb[i] - y) * keep) * (1f - mix) + surfaceRgb[i] * mix).coerceIn(0f, 1f) }
    }

    /** How much of the glowing layers is left at [darkness]. */
    fun lightAlpha(darkness: Float): Float = 1f - (1f - LIGHT_DROP) * darkness.coerceIn(0f, 1f)

    /** How much a zone lights the picture: a miss less than a hit. */
    fun zoneScale(zone: Zone?): Float = if (zone == Zone.OFF) OFF_SCALE else 1f

    /** The alpha of the zone colour laid over the whole picture: nothing while the light is on, the zone colour rising the way the light goes out. */
    fun tintAlpha(glow: Float, darkness: Float, zoneScale: Float): Float = ZONE_TINT * glow * darkness * zoneScale

    /** The alpha of the zone light at its centre and at its middle stop. */
    fun zoneLightAlphas(glow: Float, darkness: Float, zoneScale: Float): Pair<Float, Float> {
        val k = glow * darkness * zoneScale
        return ZONE_LIGHT * k to ZONE_LIGHT_MID * k
    }

    /** The alpha of the veil at its centre and at its middle stop: it lives only while the light is on. */
    fun veilAlphas(darkness: Float): Pair<Float, Float> {
        val on = 1f - darkness.coerceIn(0f, 1f)
        return VEIL * on to VEIL_MID * on
    }

    /** The alpha of the curtain at the top edge: it lives only while the light is on, like the veil. */
    fun curtainAlpha(darkness: Float): Float = CURTAIN * (1f - darkness.coerceIn(0f, 1f))

    /** The controls one does not touch while playing, as the light goes out. */
    fun chromeAlpha(darkness: Float): Float = 1f - (1f - CHROME_DIM) * darkness.coerceIn(0f, 1f)

    private const val CHANNELS = 3
    private const val ROW = 5
    private const val OFFSET = 4
    private const val ALPHA_ROW = 3
    private const val MATRIX_SIZE = 20
}
