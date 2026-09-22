package com.example.violintuner.feature.home.art

import com.example.violintuner.core.domain.home.HomeItem
import com.example.violintuner.feature.journey.art.Scene
import com.example.violintuner.feature.journey.art.SceneAnim
import com.example.violintuner.feature.journey.art.SceneLayer
import com.example.violintuner.feature.journey.art.SceneMode
import com.example.violintuner.feature.journey.art.ScenePalette
import com.example.violintuner.feature.journey.art.SceneParser

/** A thing as it is drawn in one home: its layers where it stands there, and the box they take (without glows and shadows). */
class ItemArt(val layers: List<SceneLayer>, val left: Float, val top: Float, val right: Float, val bottom: Float)

/**
 * Everything one home is drawn from at one time of day (`assets/home/<house>.<mode>.scene`,
 * **generated** by tools/home/export.js): the bare room with marks where a wall pattern and a
 * floor pattern go, the home from outside with a mark for the colour of our curtains, the
 * traveller, every thing in its place, the patterns.
 */
class HouseArt(
    val room: List<SceneLayer>,
    val outside: List<SceneLayer>,
    val hero: List<SceneLayer>,
    val items: Map<String, ItemArt>,
    val porch: Map<String, List<SceneLayer>>,
    /** The backing of a window: drawn before the view, the glazing bars after it. */
    val backs: Map<String, List<SceneLayer>>,
    /** The student's violin lying in the open case, by the id of the case: shown until a violin stands on its stand. */
    val caseViolins: Map<String, List<SceneLayer>>,
    val wallPatterns: Map<String, List<SceneLayer>>,
    val floorPatterns: Map<String, List<SceneLayer>>,
) {
    companion object {
        const val WALL_MARK = "@pat"
        const val FLOOR_MARK = "@floorpat"
        const val CURTAIN_MARK = "@curtain"
        const val DEFAULT_FLOOR = "default"

        fun parse(text: String): HouseArt {
            val room = ArrayList<SceneLayer>()
            val outside = ArrayList<SceneLayer>()
            val hero = ArrayList<SceneLayer>()
            val items = HashMap<String, ItemArt>()
            val porch = HashMap<String, List<SceneLayer>>()
            val backs = HashMap<String, List<SceneLayer>>()
            val caseViolins = HashMap<String, List<SceneLayer>>()
            val walls = HashMap<String, List<SceneLayer>>()
            val floors = HashMap<String, List<SceneLayer>>()
            var into: MutableList<SceneLayer> = room
            // a section that starts with «@name» — but «@pat» and «@floorpat» alone on a tab-separated line are marks, which are layers
            for (line in text.lineSequence()) {
                if (line.isBlank() || line.startsWith("# ") || line.startsWith("house=")) continue
                if (line.startsWith("@") && !line.contains('\t')) {
                    val f = line.split(' ')
                    val fresh = ArrayList<SceneLayer>()
                    when (f[0]) {
                        "@room" -> into = room
                        "@out" -> into = outside
                        "@hero" -> into = hero
                        "@item" -> { items[f[1]] = ItemArt(fresh, f[2].toFloat(), f[3].toFloat(), f[4].toFloat(), f[5].toFloat()); into = fresh }
                        "@porch" -> { porch[f[1]] = fresh; into = fresh }
                        "@back" -> { backs[f[1]] = fresh; into = fresh }
                        "@caseviolin" -> { caseViolins[f[1]] = fresh; into = fresh }
                        "@pat" -> { walls[f[1]] = fresh; into = fresh }
                        "@floorpat" -> { floors[f[1]] = fresh; into = fresh }
                        else -> throw IllegalArgumentException("a section this build does not know: ${f[0]}")
                    }
                    continue
                }
                into += SceneParser.layerOf(line)
            }
            return HouseArt(room, outside, hero, items, porch, backs, caseViolins, walls, floors)
        }
    }
}

/** A home put together, and the box of the thing being tried on, if any. */
class ComposedHome(val scene: Scene, val ghost: ItemArt?)

/**
 * Puts a home together (the handoff's `compose`): the base, the things back to front, the
 * traveller, then what stands in front of them — the pet. Colours of walls, floor and curtains are
 * the scene's own overrides. A thing tried on is drawn at half its density in the place of what
 * stood there. Pure.
 */
object HomeComposer {
    private const val FRONT = 5
    private const val WINDOW = "window"
    private const val DIM = "rgba(14,14,18,.12)"
    private const val GLOW_PERIOD_S = 2.4f
    private const val VIOLIN = "violin"
    private const val CASE = "case"
    private const val DAY_LIFT = 0.18f
    private const val WHITE = 0xFFFFFFFFL
    private val WALLS = setOf("wallHome", "wallLitHome")

    /**
     * [curtains] — the curtains hung inside: from outside our window is the one in their colour (the handoff's own `compose` loses them there).
     * [withViolin] false — the room of Live (spec 3.27): the violin is in the player's hands, neither on its stand nor in the case.
     */
    fun compose(art: HouseArt, standing: List<HomeItem>, outside: Boolean, mode: SceneMode, ghost: HomeItem? = null, porchCat: HomeItem? = null, curtains: HomeItem? = null, withViolin: Boolean = true): ComposedHome {
        val present = if (withViolin) standing else standing.filter { it.slot != VIOLIN }
        val things = (if (ghost == null) present else present.filter { it.slot != ghost.slot }).sortedBy { it.z }
        val dressed = if (ghost != null && (ghost.palette.isNotEmpty() || ghost.pattern)) things + ghost else things
        val overrides = HashMap<String, Long>()
        dressed.forEach { overrides.putAll(it.palette) }
        // wallpapers give the tone of the evening; by day it is lifted towards white (handoff `dev`)
        if (mode == SceneMode.DAY) WALLS.forEach { key -> overrides[key]?.let { overrides[key] = ScenePalette.mix(it, WHITE, DAY_LIFT) } }

        val layers = ArrayList<SceneLayer>()
        if (outside) {
            val ours = curtains?.palette?.get("curtain")
            art.outside.forEach { layer -> layers += if (layer.fill == HouseArt.CURTAIN_MARK) layer.copy(fill = if (ours != null) hexOf(ours) else "curtain") else layer }
        } else {
            val wall = dressed.lastOrNull { it.slot == "wallpaper" && it.pattern }?.let { art.wallPatterns[it.id] }.orEmpty()
            val floor = dressed.lastOrNull { it.slot == "floor" && it.pattern }?.let { art.floorPatterns[it.id] } ?: art.floorPatterns[HouseArt.DEFAULT_FLOOR].orEmpty()
            art.room.forEach { layer ->
                when (layer.fill) {
                    HouseArt.WALL_MARK -> layers += wall
                    HouseArt.FLOOR_MARK -> layers += floor
                    else -> layers += layer
                }
            }
        }
        // A window is its backing, then the view, then the glazing bars (found on the emulator in the first
        // iteration, a section of its own since the second handoff).
        if (!outside) things.firstOrNull { it.slot == WINDOW }?.let { layers += art.backs[it.id].orEmpty() }
        fun draw(item: HomeItem) { art.items[item.id]?.let { layers += it.layers } }
        things.filter { it.z < FRONT }.forEach(::draw)
        // no violin yet — the student's one lies in the open case (handoff 28b)
        if (withViolin && !outside && things.none { it.slot == VIOLIN } && ghost?.slot != VIOLIN) things.firstOrNull { it.slot == CASE }?.let { layers += art.caseViolins[it.id].orEmpty() }
        // Trying on without a frame (handoff 28f): the room a little darker, a warm glow under the thing that breathes,
        // the thing itself at its full density and still.
        val ghostArt = ghost?.let { art.items[it.id] }
        if (ghostArt != null) {
            layers += SceneLayer(DIM, 2, 1f, 0f, 0f, 1f, false, null, 0f, false, "M-600 -1200h1600v2000h-1600Z")
            val rx = (ghostArt.right - ghostArt.left) * 0.9f + 14f
            val ry = (ghostArt.bottom - ghostArt.top) * 0.18f + 8f
            val cx = (ghostArt.left + ghostArt.right) / 2
            val cy = ghostArt.bottom + 2f
            layers += SceneLayer(SceneLayer.GLOW, 2, 1f, 0f, 0f, 1f, false, null, 0f, false, "M${cx - rx} ${cy}a$rx $ry 0 1 0 ${2 * rx} 0a$rx $ry 0 1 0 ${-2 * rx} 0Z", SceneAnim(flick = SceneAnim.Timed(GLOW_PERIOD_S, 0f)))
            ghostArt.layers.forEach { layers += it.copy(anim = null) }
        }
        // at home the traveller is not drawn: home is «I» (handoff 28b); in the cities he stays
        if (outside && porchCat != null) layers += art.porch[porchCat.id].orEmpty()
        things.filter { it.z >= FRONT }.forEach(::draw)
        return ComposedHome(Scene(ScenePalette.HOUSE, aerial = outside, layers = layers, overrides = overrides), ghostArt)
    }

    private fun hexOf(argb: Long): String = "#%06X".format(argb and 0xFFFFFF)
}
