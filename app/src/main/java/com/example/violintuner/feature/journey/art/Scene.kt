package com.example.violintuner.feature.journey.art

import com.example.violintuner.core.domain.home.HomeCatalogData

/** One layer of a postcard (handoff `Путешествие`, «Сетка и формат слоя»): a path with how it is filled. Pure data — colours are resolved when it is drawn. */
data class SceneLayer(
    /** A token of the palette, a literal `#rrggbb` / `rgba(…)`, or one of the special fills [SKY] and [GLOW]. */
    val fill: String,
    /** 0 — far, 1 — middle, 2 — near: what the air between us and it does to its colour, and how far it moves with the parallax. */
    val depth: Int,
    val opacity: Float,
    val tx: Float,
    val ty: Float,
    val scale: Float,
    /** A warm glow around a window or a lamp, whatever the palette. */
    val warmGlow: Boolean,
    val stroke: String?,
    val strokeWidth: Float,
    val fillNone: Boolean,
    val path: String,
    /** How the layer moves as a thing — all layers of one tram carry the same; null for a layer that stays. */
    val anim: SceneAnim? = null,
) {
    companion object {
        const val SKY = "SKY"
        const val GLOW = "GLOW"
    }
}

/**
 * The movement of a thing in a scene, written in the layer's last field as `kind:numbers` joined by `+`:
 * `ride:speed:from:to` — goes sideways at `speed` units a second and wraps, its shift running `from`…`to`
 * (far enough both ways for the thing to leave the frame); `bob:amplitude:period` — rises and falls;
 * `bird:left:span` — a bird crossing `span` units from `left`, flapping, fading at both ends;
 * `flash:period` — a light going on and off; `blink:period` — eyes: open but for a moment once a period;
 * `flick:period[:delay]` — a flame: dims to about a half and back; `rise:period[:delay]` — smoke: goes up ten units and fades;
 * `sway:amount:period` — leans sideways and back; `swing:degrees:period:px:py` — turns to and fro about a point (a pendulum); `fall:speed:top:height` — falls through `height` units below `top` and starts again from the top, fading at both ends.
 */
data class SceneAnim(
    val ride: Ride? = null,
    val bob: Bob? = null,
    val bird: Way? = null,
    val flashPeriod: Float? = null,
    val blinkPeriod: Float? = null,
    val flick: Timed? = null,
    val rise: Timed? = null,
    val sway: Bob? = null,
    val swing: Swing? = null,
    val fall: Fall? = null,
) {
    data class Ride(val speed: Float, val from: Float, val to: Float)
    data class Bob(val amplitude: Float, val period: Float)
    data class Way(val left: Float, val span: Float)
    data class Timed(val period: Float, val delay: Float)
    data class Swing(val degrees: Float, val period: Float, val pivotX: Float, val pivotY: Float)
    data class Fall(val speed: Float, val top: Float, val height: Float)

    companion object {
        fun parse(text: String): SceneAnim? {
            if (text.isBlank()) return null
            var anim = SceneAnim()
            for (part in text.split('+')) {
                val f = part.split(':')
                val n = f.drop(1).map { it.toFloat() }
                anim = when (f[0]) {
                    "ride" -> anim.copy(ride = Ride(n[0], n[1], n[2]))
                    "bob" -> anim.copy(bob = Bob(n[0], n[1]))
                    "bird" -> anim.copy(bird = Way(n[0], n[1]))
                    "flash" -> anim.copy(flashPeriod = n[0])
                    "blink" -> anim.copy(blinkPeriod = n[0])
                    "flick" -> anim.copy(flick = Timed(n[0], n.getOrElse(1) { 0f }))
                    "rise" -> anim.copy(rise = Timed(n[0], n.getOrElse(1) { 0f }))
                    "sway" -> anim.copy(sway = Bob(n[0], n[1]))
                    "swing" -> anim.copy(swing = Swing(n[0], n[1], n.getOrElse(2) { 0f }, n.getOrElse(3) { 0f }))
                    "fall" -> anim.copy(fall = Fall(n[0], n[1], n[2]))
                    else -> throw IllegalArgumentException("a movement this build does not know: ${f[0]}")
                }
            }
            return anim
        }
    }
}

/** [overrides] are colours of tokens that belong to this very scene — the wallpaper somebody chose — and win over every palette. */
data class Scene(val location: String, val aerial: Boolean, val layers: List<SceneLayer>, val overrides: Map<String, Long> = emptyMap())

/**
 * Reads a scene exported from the handoff (`assets/journey/<key>.<mode>.scene`): a header and one
 * layer per line, tab-separated. The scenes are **generated** — a picture is changed in the handoff
 * and exported again, never edited here.
 */
object SceneParser {
    fun parse(text: String): Scene {
        // a comment is «# …»; a line that starts with «#rrggbb» is a layer filled with a literal colour
        val lines = text.lineSequence().filter { it.isNotBlank() && !it.startsWith("# ") }.toList()
        val header = lines.first().split(';').associate { part -> part.substringBefore('=') to part.substringAfter('=') }
        return Scene(
            location = header.getValue("loc"),
            aerial = header["aerial"] == "1",
            layers = lines.drop(1).map(::layerOf),
        )
    }

    /** One layer line, as the exporters write it. */
    fun layerOf(line: String): SceneLayer {
        val f = line.split('\t')
        require(f.size == FIELDS) { "a scene layer has $FIELDS fields, this one has ${f.size}" }
        return SceneLayer(
            fill = f[0],
            depth = f[1].toInt().coerceIn(0, 2),
            opacity = f[2].toFloatOrNull() ?: 1f,
            tx = f[3].toFloatOrNull() ?: 0f,
            ty = f[4].toFloatOrNull() ?: 0f,
            scale = f[5].toFloatOrNull() ?: 1f,
            warmGlow = f[6] == "1",
            stroke = f[7].ifEmpty { null },
            strokeWidth = f[8].toFloatOrNull() ?: 0f,
            fillNone = f[9] == "1",
            path = f[10],
            anim = SceneAnim.parse(f[11]),
        )
    }

    private const val FIELDS = 12
}

/** Evening — the default: lit windows make the volume by themselves, and a warm card sits well on a dark screen; day is an extra (handoff 26b). */
enum class SceneMode(val suffix: String) { EVENING("eve"), DAY("day") }

/**
 * The colours of the postcards (handoff `BASE`, `LOCPAL`, `AER`), as ARGB. Every scene is drawn
 * from tokens, so day and evening are two palettes over one drawing. Outdoors the far and the
 * middle plane are mixed towards the low sky — the air between us and them; indoors they are not.
 */
object ScenePalette {
    private val AERIAL = floatArrayOf(0.28f, 0.10f, 0f)
    const val WARM_GLOW = 0x8CFFC46EL // rgba(255,196,110,.55)
    const val TRANSPARENT_GLOW = 0x00FFC46EL

    private val day: Map<String, Long> = mapOf(
        "sky" to 0xFF79B0E6, "skyLow" to 0xFFD3E5F5, "far" to 0xFFA9BFD9, "farLit" to 0xFFC5D6E8, "ground" to 0xFF8E8A86, "groundLit" to 0xFFA8A49F,
        "groundShade" to 0xFF6E6A66, "window" to 0xFF5B6E85, "windowLit" to 0xFF7F94AB, "lamp" to 0xFF3B3A45, "lampGlass" to 0xFFE4ECF2, "glow" to 0x00FFC46EL,
        "water" to 0xFF5F9AD0, "waterLit" to 0xFFB0DCF2, "foliage" to 0xFF4E9B57, "foliageLit" to 0xFF82C873, "foliageShade" to 0xFF357A42, "trunk" to 0xFF7A5539,
        "chandelier" to 0xFFFFE9B0, "hero" to 0xFF2A2430, "heroHair" to 0xFF4A3A32, "heroCase" to 0xFF5B43B8, "bird" to 0xFF3A3A4C,
    )
    private val evening: Map<String, Long> = mapOf(
        "sky" to 0xFF2A2857, "skyLow" to 0xFFE08A63, "far" to 0xFF4C4676, "farLit" to 0xFF6A5E8C, "ground" to 0xFF3F3A48, "groundLit" to 0xFF57506A,
        "groundShade" to 0xFF2C2834, "window" to 0xFFFFD98A, "windowLit" to 0xFFFFE9B0, "lamp" to 0xFF2A2830, "lampGlass" to 0xFFFFD98A, "glow" to 0x80FFC46EL,
        "water" to 0xFF2F3F7A, "waterLit" to 0xFFE0A070, "foliage" to 0xFF2F6B48, "foliageLit" to 0xFF4F8E5F, "foliageShade" to 0xFF214D34, "trunk" to 0xFF4A3A32,
        "chandelier" to 0xFFFFE9B0, "hero" to 0xFF1E1A24, "heroHair" to 0xFF3A2C28, "heroCase" to 0xFF5B43B8, "bird" to 0xFF1B1830,
    )
    private val locations: Map<String, Map<String, Long>> = mapOf(
        "vienna" to mapOf(
            "wall" to 0xFFD9B26B, "wallLit" to 0xFFE9C98D, "wallShade" to 0xFFB58E4D, "wallBase" to 0xFFC9A15C, "trim" to 0xFFF2ECD8, "roof" to 0xFF6F7B7F,
            "roofLit" to 0xFF8C989C, "dark" to 0xFF3A3040, "gold" to 0xFFD9B26B, "goldLit" to 0xFFEBCB86, "goldShade" to 0xFFB08A45, "goldDark" to 0xFF8E6E36,
            "cream" to 0xFFF0E6D2, "creamShade" to 0xFFDCCFB4, "velvet" to 0xFF8E2F3F, "velvetDark" to 0xFF5A1E2A, "wood" to 0xFFA9713F,
        ),
        "cremona" to mapOf(
            "plaster" to 0xFFE6D5B8, "plasterShade" to 0xFFCDB891, "floor" to 0xFF8E5E3A, "floorShade" to 0xFF74492C, "wood" to 0xFFA9713F, "woodShade" to 0xFF7E5230,
            "woodDark" to 0xFF5A3A22, "varnish" to 0xFFB5672F, "varnishLit" to 0xFFD48A4C, "varnishRaw" to 0xFFD9B27A, "metal" to 0xFF6B6C78, "rope" to 0xFF8A7A66,
            "glass" to 0xFFB7C7CF,
        ),
        "milan" to mapOf(
            "velvet" to 0xFF8E2F3F, "velvetDark" to 0xFF5A1E2A, "velvetCurtain" to 0xFF6E2333, "gold" to 0xFFD9B26B, "goldLit" to 0xFFEBCB86, "cream" to 0xFFF0E6D2,
            "creamShade" to 0xFFDCCFB4, "boxDark" to 0xFF3A1E28, "wood" to 0xFFA9713F,
        ),
        "london" to mapOf(
            "brick" to 0xFFA8583F, "brickLit" to 0xFFC36F52, "brickShade" to 0xFF7F4130, "frieze" to 0xFFE8DFC8, "friezeShade" to 0xFFCFC5A9, "dome" to 0xFF8FA9B8,
            "domeLit" to 0xFFB9CDD8, "dark" to 0xFF3A3040,
        ),
        "sydney" to mapOf("sail" to 0xFFF2EFE6, "sailLit" to 0xFFFFFFFF, "sailShade" to 0xFFC9C4B6, "platform" to 0xFFB58E6B, "platformShade" to 0xFF8A6A4E),
        "home" to mapOf(
            "wallHome" to 0xFF3E3652, "wallLitHome" to 0xFF4A4262, "floorHome" to 0xFF5A4A40, "floorLine" to 0xFF4A3C34, "curtain" to 0xFF5B43B8, "woodDark" to 0xFF3A2A22,
            "metal" to 0xFF6B6C78, "paper" to 0xFFF1EEE6, "rug" to 0xFF4A3F6E, "velvet" to 0xFF8E2F3F, "varnish" to 0xFFB5672F, "caseOut" to 0xFF2A2430,
        ),
        "austria" to mapOf(
            "mount" to 0xFF5F7480, "mountLit" to 0xFF7E93A0, "mountShade" to 0xFF46585F, "snow" to 0xFFF2F5F8, "meadow" to 0xFF6FA85C, "meadowLit" to 0xFF8FC873,
            "plaster" to 0xFFF0E6D2, "roofGreen" to 0xFF4E7B6A, "wood" to 0xFFA9713F, "woodShade" to 0xFF7E5230,
        ),
    )

    fun token(name: String, location: String, mode: SceneMode): Long? =
        (if (location == HOUSE) houseToken(name, mode) else null)
            ?: (locations[location] ?: ExtraScenePalettes.locations[location])?.get(name) ?: (if (mode == SceneMode.DAY) day else evening)[name]

    /** The colour a layer is filled with; null for a token this build does not know — such a layer is not drawn rather than drawn wrong. */
    fun colorOf(fill: String, depth: Int, scene: Scene, mode: SceneMode): Long? {
        val literal = parse(fill)
        val base = literal ?: scene.overrides[fill] ?: token(fill, scene.location, mode) ?: return null
        // only opaque colours take the air: shadows and glows are already air
        val opaque = base ushr ALPHA_SHIFT == 0xFFL
        // the flat sky above the frame continues the gradient, whose top is the pure colour: air is not mixed into the sky itself
        if (!scene.aerial || depth >= 2 || !opaque || fill == "sky" || (literal != null && fill.startsWith("rgba"))) return base
        return mix(base, token("skyLow", scene.location, mode) ?: base, AERIAL[depth])
    }

    /** `#rrggbb` and `rgba(r,g,b,a)`; null for anything else (a token). */
    fun parse(value: String): Long? = when {
        value.startsWith("#") && value.length == 7 -> 0xFF000000L or value.substring(1).toLong(16)
        value.startsWith("rgba") -> {
            val n = Regex("[\\d.]+").findAll(value).map { it.value.toFloat() }.toList()
            if (n.size < 4) null else (Math.round(n[3] * 255).toLong() shl ALPHA_SHIFT) or (n[0].toLong() shl 16) or (n[1].toLong() shl 8) or n[2].toLong()
        }
        else -> null
    }

    fun mix(a: Long, b: Long, t: Float): Long {
        fun channel(shift: Int): Long {
            val from = (a shr shift) and 0xFF
            val to = (b shr shift) and 0xFF
            return Math.round(from + (to - from) * t).toLong().coerceIn(0, 255) shl shift
        }
        return channel(ALPHA_SHIFT) or channel(16) or channel(8) or channel(0)
    }

    /** The location of every scene composed of the home's catalogue: its tokens come from the catalogue, the day lightens some of them. */
    const val HOUSE = "house"

    private fun houseToken(name: String, mode: SceneMode): Long? =
        (if (mode == SceneMode.DAY) HomeCatalogData.dayTokens[name] else null) ?: HomeCatalogData.tokens[name]

    private const val ALPHA_SHIFT = 24
}
