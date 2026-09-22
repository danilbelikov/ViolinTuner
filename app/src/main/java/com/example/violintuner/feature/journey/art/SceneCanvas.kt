package com.example.violintuner.feature.journey.art

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.example.violintuner.core.domain.journey.JourneyStop
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The grid every postcard is drawn on (handoff): the horizon of the outdoor ones is at y 190. */
object SceneGrid {
    const val WIDTH = 412f
    const val HEIGHT = 260f
    const val HORIZON = 190f
}

/** A scene with its paths built once: the postcard is drawn every frame it is on screen, parsed never again. */
class PreparedScene(val scene: Scene, val mode: SceneMode, val paths: List<Path>, val bounds: List<Rect>)

private object SceneCache {
    private val scenes = HashMap<String, PreparedScene>()

    @Synchronized
    fun get(key: String): PreparedScene? = scenes[key]

    @Synchronized
    fun put(key: String, scene: PreparedScene) {
        scenes[key] = scene
    }
}

/** Paths by their text: a composed room is put together again at every purchase, its paths are parsed once. */
private object PathCache {
    private val paths = HashMap<String, Path>()

    @Synchronized
    fun get(d: String): Path = paths.getOrPut(d) { PathParser().parsePathString(d).toPath() }
}

/** A scene made ready to be drawn: for scenes that are composed rather than read whole — a home (spec 3.24). */
fun prepare(scene: Scene, mode: SceneMode): PreparedScene {
    val paths = scene.layers.map { PathCache.get(it.path) }
    return PreparedScene(scene, mode, paths, paths.map { it.getBounds() })
}

/**
 * Draws a prepared scene the way a postcard is drawn — covering the box, alive with [seconds],
 * seen through [camera] — and lets [overlay] draw over it in the units of the grid (the frame
 * round a thing that is being tried on).
 */
@Composable
fun ScenePicture(
    prepared: PreparedScene?,
    description: String,
    modifier: Modifier = Modifier,
    seconds: State<Float>? = null,
    camera: (() -> Triple<Float, Float, Float>)? = null,
    overlay: (DrawScope.(seconds: Float?) -> Unit)? = null,
    /** A home seen whole stands in the middle of the screen, ceiling above and floor below; a city stands on the bottom edge under its sky. */
    centred: Boolean = false,
    /** Seen whole, by its width, whatever the box: a title card is a picture, not a panorama. Ignored when there is a [camera]. */
    whole: Boolean = false,
) {
    Canvas(modifier.clipToBounds().background(Color(NIGHT)).semantics { contentDescription = description }) {
        if (prepared == null) return@Canvas
        val (zoom, panX, panY) = camera?.invoke() ?: Triple(if (whole) SceneCamera.wholeZoom(size.width, size.height) else 1f, 0f, 0f)
        val k = SceneCamera.cover(size.width, size.height) * zoom
        val t = seconds?.value
        translate((size.width - SceneGrid.WIDTH * k) / 2, SceneCamera.top(zoom, size.width, size.height, outdoors = prepared.scene.aerial && !centred) + panY) {
            drawScene(prepared, k, panX, t)
            if (overlay != null) translate(panX, 0f) { scale(k, k, pivot = Offset.Zero) { overlay(t) } }
        }
    }
}

/**
 * Draws the layers of [prepared] at [k] pixels a unit, from the origin: for a thing shown alone, on a
 * shelf, and for the picture behind Live, where [lightAlpha] puts the lamps and chandeliers out as
 * the light in the hall goes down (spec 3.27).
 */
fun DrawScope.drawPrepared(prepared: PreparedScene, k: Float, seconds: Float? = null, lightAlpha: Float = 1f) = drawScene(prepared, k, 0f, seconds, lightAlpha)

/** Loads `assets/journey/<key>.<mode>.scene` off the main thread; null while it loads and when there is no such picture. */
@Composable
fun rememberScene(sceneKey: String?, mode: SceneMode, folder: String = "journey"): PreparedScene? {
    val context = LocalContext.current
    val id = sceneKey?.let { "$it.${mode.suffix}" }
    val cacheKey = id?.let { "$folder/$it" }
    val prepared by produceState(initialValue = cacheKey?.let(SceneCache::get), cacheKey) {
        if (id == null) {
            value = null
            return@produceState
        }
        value = SceneCache.get(cacheKey!!) ?: withContext(Dispatchers.Default) {
            runCatching { context.assets.open("$folder/$id.scene").bufferedReader().use { it.readText() } }.getOrNull()?.let { text ->
                val scene = SceneParser.parse(text)
                val paths = scene.layers.map { PathParser().parsePathString(it.path).toPath() }
                PreparedScene(scene, mode, paths, paths.map { it.getBounds() }).also { SceneCache.put(cacheKey, it) }
            }
        }
    }
    return prepared
}

/**
 * A postcard (spec 3.23, handoff 26b): the layers of the scene in their order, filling the box
 * like a photograph does — cropped, never stretched. What has no picture yet is drawn from its
 * silhouette against the evening sky: a sketch, and honest about being one.
 */
@Composable
fun Postcard(
    stop: JourneyStop,
    description: String,
    modifier: Modifier = Modifier,
    mode: SceneMode = SceneMode.EVENING,
    inside: Boolean = false,
    /** Seconds of a living postcard ([rememberSceneSeconds]); null — a still one. Read while drawing. */
    seconds: State<Float>? = null,
    /** Zoom, pan x, pan y in pixels — already within [SceneCamera.clamp]; null — the whole card, centred. Read while drawing. */
    camera: (() -> Triple<Float, Float, Float>)? = null,
) {
    val view = stop.views.firstOrNull { it.inside == inside } ?: stop.views.firstOrNull()
    val prepared = rememberScene(view?.scene, mode)
    val silhouette = remember(stop.id) { JourneySilhouettes.paths[stop.id].orEmpty().map { PathParser().parsePathString(it).toPath() } }
    Canvas(
        modifier = modifier
            .clipToBounds()
            .background(Color(NIGHT))
            .semantics { contentDescription = description },
    ) {
        // xMidYMid slice: the grid covers the box, its centre stays the centre
        val (zoom, panX, panY) = camera?.invoke() ?: Triple(1f, 0f, 0f)
        val k = SceneCamera.cover(size.width, size.height) * zoom
        val t = seconds?.value
        val top = SceneCamera.top(zoom, size.width, size.height, outdoors = prepared?.scene?.aerial ?: true)
        translate((size.width - SceneGrid.WIDTH * k) / 2, top + panY) {
            when {
                // the planes are shifted one by one — that is the parallax — in pixels, then scaled
                prepared != null -> drawScene(prepared, k, panX, t)
                view == null -> translate(panX, 0f) { scale(k, k, pivot = Offset.Zero) { drawSketch(silhouette, mode) } }
            }
        }
    }
}

private fun DrawScope.drawScene(prepared: PreparedScene, k: Float, panX: Float, seconds: Float?, lightAlpha: Float = 1f) {
    val scene = prepared.scene
    val sky = ScenePalette.token("sky", scene.location, prepared.mode) ?: NIGHT
    val skyLow = ScenePalette.token("skyLow", scene.location, prepared.mode) ?: NIGHT
    val glow = ScenePalette.token("glow", scene.location, prepared.mode) ?: ScenePalette.TRANSPARENT_GLOW
    scene.layers.forEachIndexed { index, layer ->
        val path = prepared.paths[index]
        val bounds = prepared.bounds[index]
        val brush: Brush? = when {
            layer.fill == SceneLayer.SKY -> Brush.verticalGradient(listOf(Color(sky), Color(skyLow)), startY = 0f, endY = SceneGrid.HORIZON)
            layer.fill == SceneLayer.GLOW -> radial(glow, bounds)
            layer.warmGlow -> radial(ScenePalette.WARM_GLOW, bounds)
            else -> ScenePalette.colorOf(layer.fill, layer.depth, scene, prepared.mode)?.let { Color(it) }?.let { androidx.compose.ui.graphics.SolidColor(it) }
        }
        val alive = seconds != null && SceneMotion.moves(layer, prepared.mode)
        val own = if (layer.fill == SceneLayer.GLOW || layer.warmGlow) layer.opacity * lightAlpha else layer.opacity
        val alpha = if (alive) own * SceneMotion.alpha(layer, index, prepared.mode, seconds!!) else own
        val drift = if (alive) SceneMotion.drift(layer, index, seconds!!) else 0f
        val draw: DrawScope.() -> Unit = {
            if (!layer.fillNone && brush != null) drawPath(path, brush, alpha = alpha)
            val stroke = layer.stroke?.let { ScenePalette.colorOf(it, layer.depth, scene, prepared.mode) }
            if (stroke != null && layer.strokeWidth > 0f) {
                drawPath(path, Color(stroke), alpha = alpha, style = Stroke(layer.strokeWidth, cap = StrokeCap.Round, pathEffect = layer.anim?.dash?.let { PathEffect.dashPathEffect(floatArrayOf(it.first, it.second)) }))
            }
        }
        // the sky's own life goes right over the sky, under everything else
        if (layer.fill == SceneLayer.SKY && seconds != null && scene.aerial) {
            translate(SceneCamera.shift(panX, 0), 0f) { scale(k, k, pivot = Offset.Zero) { drawSkyLife(prepared.mode, seconds) } }
        }
        val moved = if (alive && layer.anim != null) SceneMotion.moved(layer.anim, bounds.center.x, bounds.center.y, index, seconds!!) else null
        translate(SceneCamera.shift(panX, layer.depth) + drift * k, 0f) {
            scale(k, k, pivot = Offset.Zero) {
                if (moved != null) {
                    if (moved.alpha <= 0f) return@scale
                    val own: DrawScope.() -> Unit = {
                        if (!layer.fillNone && brush != null) drawPath(path, brush, alpha = alpha * moved.alpha)
                    }
                    translate(moved.dx, moved.dy) {
                        when {
                            moved.flap != 1f -> scale(1f, moved.flap, pivot = bounds.center) { own() }
                            moved.degrees != 0f -> rotate(moved.degrees, pivot = Offset(moved.pivotX, moved.pivotY)) { own() }
                            else -> own()
                        }
                    }
                    return@scale
                }
                if (layer.tx != 0f || layer.ty != 0f || layer.scale != 1f) {
                    translate(layer.tx, layer.ty) { scale(layer.scale, layer.scale, pivot = Offset.Zero) { draw() } }
                } else {
                    draw()
                }
            }
        }
    }
}

private fun DrawScope.drawSkyLife(mode: SceneMode, seconds: Float) {
    if (mode == SceneMode.EVENING) {
        repeat(SceneMotion.STARS) { index ->
            val star = SceneMotion.star(index, seconds)
            drawCircle(Color.White, radius = star.radius, center = Offset(star.x, star.y), alpha = star.alpha)
        }
    } else {
        repeat(SceneMotion.CLOUDS) { index ->
            val cloud = SceneMotion.cloud(index, seconds)
            val h = cloud.width * 0.22f
            // three ovals on a flat bottom
            drawOval(Color.White, topLeft = Offset(cloud.x - cloud.width / 2, cloud.y - h / 2), size = androidx.compose.ui.geometry.Size(cloud.width, h), alpha = 0.75f)
            drawOval(Color.White, topLeft = Offset(cloud.x - cloud.width * 0.28f, cloud.y - h * 1.15f), size = androidx.compose.ui.geometry.Size(cloud.width * 0.42f, h * 1.5f), alpha = 0.75f)
            drawOval(Color.White, topLeft = Offset(cloud.x + cloud.width * 0.02f, cloud.y - h * 0.9f), size = androidx.compose.ui.geometry.Size(cloud.width * 0.34f, h * 1.2f), alpha = 0.75f)
        }
    }
}

private fun radial(center: Long, bounds: Rect): Brush =
    Brush.radialGradient(listOf(Color(center), Color(ScenePalette.TRANSPARENT_GLOW)), center = bounds.center, radius = maxOf(bounds.width, bounds.height) / 2f)

/** A stop that has only its silhouette yet: the evening sky, the ground, the outline of the place. */
private fun DrawScope.drawSketch(silhouette: List<Path>, mode: SceneMode) {
    val sky = ScenePalette.token("sky", "", mode) ?: NIGHT
    val skyLow = ScenePalette.token("skyLow", "", mode) ?: NIGHT
    val far = ScenePalette.token("far", "", mode) ?: NIGHT
    val ground = ScenePalette.token("groundShade", "", mode) ?: NIGHT
    drawRect(Brush.verticalGradient(listOf(Color(sky), Color(skyLow)), startY = -200f, endY = SceneGrid.HORIZON), topLeft = Offset(-600f, -600f), size = androidx.compose.ui.geometry.Size(1_600f, 600f + SceneGrid.HORIZON))
    drawRect(Color(ground), topLeft = Offset(-600f, SceneGrid.HORIZON), size = androidx.compose.ui.geometry.Size(1_600f, 400f))
    // the silhouettes live on a grid of 200 × 120 with their feet at y 110
    val k = SKETCH_SCALE
    translate((SceneGrid.WIDTH - SILHOUETTE_WIDTH * k) / 2, SceneGrid.HORIZON - SILHOUETTE_FEET * k) {
        scale(k, k, pivot = Offset.Zero) { silhouette.forEach { drawPath(it, Color(far)) } }
    }
}

private const val NIGHT = 0xFF1B1A2E
private const val SKETCH_SCALE = 1.5f
private const val SILHOUETTE_WIDTH = 200f
private const val SILHOUETTE_FEET = 110f
