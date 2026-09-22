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
import androidx.compose.ui.geometry.Size
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
import com.example.violintuner.core.domain.journey.StopView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The grid every postcard is drawn on (handoff): the horizon of the outdoor ones is at y 190. */
object SceneGrid {
    const val WIDTH = 412f
    const val HEIGHT = 260f
    const val HORIZON = 190f
}

/** A scene with its paths built once: the postcard is drawn every frame it is on screen, parsed never again. */
class PreparedScene(val scene: Scene, val mode: SceneMode, val paths: List<Path>, val bounds: List<Rect>) {
    /** A place drawn for the whole screen has a high sky (spec 3.23): its stars and clouds are drawn over it. */
    val highSky: Boolean = scene.layers.any { it.fill == SceneLayer.SKY_HIGH }

    /** The layer the sky's own life is drawn over: the high sky where there is one, the band's sky otherwise. */
    val skyLifeAt: Int = scene.layers.indexOfFirst { it.fill == if (highSky) SceneLayer.SKY_HIGH else SceneLayer.SKY }
}

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
        val left = (size.width - SceneGrid.WIDTH * k) / 2
        val top = SceneCamera.top(zoom, size.width, size.height, outdoors = prepared.scene.aerial && !centred) + panY
        translate(left, top) {
            drawScene(prepared, k, panX, t, seen = seen(left, top, panX, k))
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

/** The view of [stop] asked for — inside or out — or its main one where it has only that. */
fun viewOf(stop: JourneyStop, inside: Boolean): StopView? = stop.views.firstOrNull { it.inside == inside } ?: stop.views.firstOrNull()

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
    /**
     * Zoom, pan x, pan y in pixels, for the full screen: kept within the scene's [SceneFrame] here as
     * well, whatever the box has become since; null — the card's band covering the box. Read while drawing.
     */
    camera: (() -> Triple<Float, Float, Float>)? = null,
) {
    val view = viewOf(stop, inside)
    val prepared = rememberScene(view?.scene, mode)
    val silhouette = remember(stop.id) { JourneySilhouettes.paths[stop.id].orEmpty().map { PathParser().parsePathString(it).toPath() } }
    Canvas(
        modifier = modifier
            .clipToBounds()
            .background(Color(NIGHT))
            .semantics { contentDescription = description },
    ) {
        // xMidYMid slice: the card's band covers the box, its centre stays the centre
        val frame = prepared?.scene?.frame ?: SceneFrame.CARD
        val (wanted, wantedX, wantedY) = camera?.invoke() ?: Triple(SceneCamera.COVER_ZOOM, 0f, 0f)
        val zoom = wanted.coerceIn(frame.openZoom(size.width, size.height), SceneCamera.MAX_ZOOM)
        val (panX, panY) = frame.clamp(wantedX, wantedY, zoom, size.width, size.height)
        val k = SceneCamera.cover(size.width, size.height) * zoom
        val t = seconds?.value
        val left = (size.width - SceneGrid.WIDTH * k) / 2
        val top = frame.originY(zoom, size.width, size.height) + panY
        translate(left, top) {
            when {
                // the planes are shifted one by one — that is the parallax — in pixels, then scaled
                prepared != null -> drawScene(prepared, k, panX, t, seen = seen(left, top, panX, k))
                view == null -> translate(panX, 0f) { scale(k, k, pivot = Offset.Zero) { drawSketch(silhouette, mode) } }
            }
        }
    }
}

/** What of the grid a box shows, for the near plane — the grid's origin at [left], [top] in the box, [panX] its sideways pan. */
private fun DrawScope.seen(left: Float, top: Float, panX: Float, k: Float): Rect =
    Rect((-left - panX) / k, -top / k, (size.width - left - panX) / k, (size.height - top) / k)

/**
 * Draws the layers of [prepared] in their order. [seen] — what of the grid the box shows (the near
 * plane's view, in units of the grid): a layer that stays where it is drawn and lies outside it is
 * skipped, so a card showing only its band does not pay for the sky over it and the square below it
 * (spec 3.23). Null — every layer is drawn.
 */
private fun DrawScope.drawScene(prepared: PreparedScene, k: Float, panX: Float, seconds: Float?, lightAlpha: Float = 1f, seen: Rect? = null) {
    val scene = prepared.scene
    val sky = ScenePalette.token("sky", scene.location, prepared.mode) ?: NIGHT
    val skyLow = ScenePalette.token("skyLow", scene.location, prepared.mode) ?: NIGHT
    val skyHigh = ScenePalette.token("skyHigh", scene.location, prepared.mode) ?: sky
    val glow = ScenePalette.token("glow", scene.location, prepared.mode) ?: ScenePalette.TRANSPARENT_GLOW
    scene.layers.forEachIndexed { index, layer ->
        val path = prepared.paths[index]
        val bounds = prepared.bounds[index]
        if (seen == null || !outOfSight(layer, bounds, seen, (SceneCamera.shift(panX, layer.depth) - panX) / k)) {
            val brush: Brush? = when {
                layer.fill == SceneLayer.SKY -> Brush.verticalGradient(listOf(Color(sky), Color(skyLow)), startY = 0f, endY = SceneGrid.HORIZON)
                layer.fill == SceneLayer.SKY_HIGH -> Brush.verticalGradient(listOf(Color(skyHigh), Color(sky)), startY = bounds.top, endY = bounds.bottom)
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
        // the sky's own life goes right over the sky — the high one where there is one — under everything else
        if (index == prepared.skyLifeAt && seconds != null && scene.aerial) {
            translate(SceneCamera.shift(panX, 0), 0f) {
                scale(k, k, pivot = Offset.Zero) {
                    if (prepared.highSky) drawHighSkyLife(scene, prepared.mode, seconds) else drawSkyLife(prepared.mode, seconds)
                }
            }
        }
    }
}

/**
 * A layer that stays where it is drawn and lies wholly outside [seen] (units of the grid). [planeShift] —
 * how far its plane stands from the near one sideways, in units: the parallax moves it against the view.
 * What travels — a tram, a bird, a falling petal — is always drawn; the margin covers strokes and drifts.
 */
private fun outOfSight(layer: SceneLayer, bounds: Rect, seen: Rect, planeShift: Float): Boolean {
    if (layer.anim?.travels == true) return false
    val left = bounds.left * layer.scale + layer.tx + planeShift
    val right = bounds.right * layer.scale + layer.tx + planeShift
    val top = bounds.top * layer.scale + layer.ty
    val bottom = bounds.bottom * layer.scale + layer.ty
    return right < seen.left - SIGHT_MARGIN || left > seen.right + SIGHT_MARGIN || bottom < seen.top - SIGHT_MARGIN || top > seen.bottom + SIGHT_MARGIN
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
            drawOval(Color.White, topLeft = Offset(cloud.x - cloud.width / 2, cloud.y - h / 2), size = Size(cloud.width, h), alpha = 0.75f)
            drawOval(Color.White, topLeft = Offset(cloud.x - cloud.width * 0.28f, cloud.y - h * 1.15f), size = Size(cloud.width * 0.42f, h * 1.5f), alpha = 0.75f)
            drawOval(Color.White, topLeft = Offset(cloud.x + cloud.width * 0.02f, cloud.y - h * 0.9f), size = Size(cloud.width * 0.34f, h * 1.2f), alpha = 0.75f)
        }
    }
}

/**
 * The life of a high sky (handoff locations): 44 stars by night, thinning towards the horizon; five
 * clouds by day, the higher the paler and the slower; the two lowest as dark clouds in the evening.
 */
private fun DrawScope.drawHighSkyLife(scene: Scene, mode: SceneMode, seconds: Float) {
    val star = ScenePalette.token("star", scene.location, mode)
    if (mode == SceneMode.EVENING && star != null) {
        repeat(SceneMotion.HIGH_STARS) { index ->
            val s = SceneMotion.highStar(index, seconds)
            drawCircle(Color(star), radius = s.radius, center = Offset(s.x, s.y), alpha = s.alpha)
        }
    }
    val body = ScenePalette.token("cloud", scene.location, mode) ?: return
    val lit = ScenePalette.token("cloudLit", scene.location, mode) ?: body
    val clouds = if (mode == SceneMode.EVENING) HighSky.eveningClouds else HighSky.dayClouds
    repeat(clouds.size / SceneMotion.HIGH_CLOUD_NUMBERS) { index ->
        val c = SceneMotion.highCloud(clouds, index, seconds)
        drawOval(Color(body), topLeft = Offset(c.x - c.rx, c.y - c.rx * CLOUD_FLAT), size = Size(2 * c.rx, 2 * c.rx * CLOUD_FLAT), alpha = CLOUD_BODY + CLOUD_BODY_K * c.k)
        drawOval(Color(lit), topLeft = Offset(c.x - c.rx * (CLOUD_LIT_SHIFT + CLOUD_LIT_W), c.y - c.rx * (CLOUD_LIT_RISE + CLOUD_LIT_H)), size = Size(2 * c.rx * CLOUD_LIT_W, 2 * c.rx * CLOUD_LIT_H), alpha = CLOUD_LIT_K * c.k)
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
    drawRect(Brush.verticalGradient(listOf(Color(sky), Color(skyLow)), startY = -200f, endY = SceneGrid.HORIZON), topLeft = Offset(-600f, -600f), size = Size(1_600f, 600f + SceneGrid.HORIZON))
    drawRect(Color(ground), topLeft = Offset(-600f, SceneGrid.HORIZON), size = Size(1_600f, 400f))
    // the silhouettes live on a grid of 200 × 120 with their feet at y 110
    val k = SKETCH_SCALE
    translate((SceneGrid.WIDTH - SILHOUETTE_WIDTH * k) / 2, SceneGrid.HORIZON - SILHOUETTE_FEET * k) {
        scale(k, k, pivot = Offset.Zero) { silhouette.forEach { drawPath(it, Color(far)) } }
    }
}

private const val NIGHT = 0xFF1B1A2E

/** How far past what the box shows a layer that stays is still drawn: strokes, the drift of the water, a sway. */
private const val SIGHT_MARGIN = 24f

// a cloud of the high sky (handoff locations, `clouds()`): a flat body, and a lighter top a little to the left
private const val CLOUD_FLAT = 0.22f
private const val CLOUD_BODY = 0.5f
private const val CLOUD_BODY_K = 0.3f
private const val CLOUD_LIT_SHIFT = 0.22f
private const val CLOUD_LIT_RISE = 0.07f
private const val CLOUD_LIT_W = 0.55f
private const val CLOUD_LIT_H = 0.15f
private const val CLOUD_LIT_K = 0.35f
private const val SKETCH_SCALE = 1.5f
private const val SILHOUETTE_WIDTH = 200f
private const val SILHOUETTE_FEET = 110f
