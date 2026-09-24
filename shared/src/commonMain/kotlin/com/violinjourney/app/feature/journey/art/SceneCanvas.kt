package com.violinjourney.app.feature.journey.art

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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntSize
import com.violinjourney.app.core.concurrent.PlatformLock
import com.violinjourney.app.core.concurrent.withLock
import com.violinjourney.app.core.domain.journey.JourneyStop
import com.violinjourney.app.core.domain.journey.StopView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The grid every postcard is drawn on (handoff): the horizon of the outdoor ones is at y 190. */
object SceneGrid {
    const val WIDTH = 412f
    const val HEIGHT = 260f
    const val HORIZON = 190f
}

/**
 * How a layer is painted in its palette — the brush that fills it (null: nothing to fill), its stroke,
 * whether it lives: worked out once, when the scene is made ready. A frame only reads it: a postcard is
 * drawn up to thirty times a second, and a colour looked up and mixed with the air every time was most
 * of what its frame cost (docs/plan-performance.md).
 */
class LayerPaint(val fill: Brush?, val stroke: Color?, val strokeStyle: Stroke?, val alive: Boolean)

/** A scene with its paths built once: the postcard is drawn every frame it is on screen, parsed never again. */
class PreparedScene(val scene: Scene, val mode: SceneMode, val paths: List<Path>, val bounds: List<Rect>) {
    val paints: List<LayerPaint> = paintsOf(scene, mode, bounds)

    /** A place drawn for the whole screen has a high sky (spec 3.23): its stars and clouds are drawn over it. */
    val highSky: Boolean = scene.layers.any { it.fill == SceneLayer.SKY_HIGH }

    /** The layer the sky's own life is drawn over: the high sky where there is one, the band's sky otherwise. */
    val skyLifeAt: Int = scene.layers.indexOfFirst { it.fill == if (highSky) SceneLayer.SKY_HIGH else SceneLayer.SKY }

    /** The order to draw it in with what stands still baked (docs/plan-performance.md). */
    val steps: List<SceneStep> = SceneStrata.plan(
        scene.layers, bounds, paints.map { it.alive },
        skyLifeAt = if (scene.aerial) skyLifeAt else -1,
        skyLife = if (scene.aerial) SceneMotion.skyLifeReach(highSky) else null,
    )
}

private object SceneCache {
    private val lock = PlatformLock()
    private val scenes = HashMap<String, PreparedScene>()

    fun get(key: String): PreparedScene? = lock.withLock { scenes[key] }

    fun put(key: String, scene: PreparedScene) = lock.withLock {
        scenes[key] = scene
    }
}

/** Paths by their text: a composed room is put together again at every purchase, its paths are parsed once. */
private object PathCache {
    private val lock = PlatformLock()
    private val paths = HashMap<String, Path>()

    fun get(d: String): Path = lock.withLock { paths.getOrPut(d) { PathParser().parsePathString(d).toPath() } }
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
    val baking = rememberSceneBaking(prepared)
    val watched = modifier.watchedBy(seconds)
    Canvas(watched.clipToBounds().background(Color(NIGHT)).semantics { contentDescription = description }) {
        if (prepared == null) return@Canvas
        val (zoom, panX, panY) = camera?.invoke() ?: Triple(if (whole) SceneCamera.wholeZoom(size.width, size.height) else 1f, 0f, 0f)
        val k = SceneCamera.cover(size.width, size.height) * zoom
        val t = seconds?.value
        val left = (size.width - SceneGrid.WIDTH * k) / 2
        val top = SceneCamera.top(zoom, size.width, size.height, outdoors = prepared.scene.aerial && !centred) + panY
        val seen = seen(left, top, panX, k)
        if (baking != null && t != null && panX == 0f) drawBaked(baking, left, top, k, t, seen) else translate(left, top) { drawScene(prepared, k, panX, t, seen = seen) }
        if (overlay != null) translate(left, top) { translate(panX, 0f) { scale(k, k, pivot = Offset.Zero) { overlay(t) } } }
    }
}

/**
 * Draws the layers of [prepared] at [k] pixels a unit, from the origin: for a thing shown alone, on a
 * shelf, and for the picture behind Live, where [lightAlpha] puts the lamps and chandeliers out as
 * the light in the hall goes down (spec 3.27) and [seen] — what of the grid the box shows — leaves
 * out what the framing cuts off.
 */
fun DrawScope.drawPrepared(prepared: PreparedScene, k: Float, seconds: Float? = null, lightAlpha: Float = 1f, seen: Rect? = null) =
    drawScene(prepared, k, 0f, seconds, lightAlpha, seen)

/** Loads `journey/<key>.<mode>.scene` off the main thread; null while it loads and when there is no such picture. */
@Composable
fun rememberScene(sceneKey: String?, mode: SceneMode, folder: String = "journey"): PreparedScene? {
    val id = sceneKey?.let { "$it.${mode.suffix}" }
    val cacheKey = id?.let { "$folder/$it" }
    val prepared by produceState(initialValue = cacheKey?.let(SceneCache::get), cacheKey) {
        if (id == null) {
            value = null
            return@produceState
        }
        value = SceneCache.get(cacheKey!!) ?: withContext(Dispatchers.Default) {
            readSceneText("$folder/$id.scene")?.let { text ->
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
    val baking = rememberSceneBaking(prepared)
    val silhouette = remember(stop.id) { JourneySilhouettes.paths[stop.id].orEmpty().map { PathParser().parsePathString(it).toPath() } }
    Canvas(
        modifier = modifier
            .watchedBy(seconds)
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
        val seen = seen(left, top, panX, k)
        when {
            // a living picture seen without a sideways pan keeps what stands still baked
            prepared != null && baking != null && t != null && panX == 0f -> drawBaked(baking, left, top, k, t, seen)
            // the planes are shifted one by one — that is the parallax — in pixels, then scaled
            prepared != null -> translate(left, top) { drawScene(prepared, k, panX, t, seen = seen) }
            view == null -> translate(left, top) { translate(panX, 0f) { scale(k, k, pivot = Offset.Zero) { drawSketch(silhouette, mode) } } }
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
    scene.layers.forEachIndexed { index, layer ->
        if (seen == null || !outOfSight(layer, prepared.bounds[index], seen, (SceneCamera.shift(panX, layer.depth) - panX) / k)) {
            drawOne(prepared, index, k, panX, seconds, lightAlpha)
        }
        if (index == prepared.skyLifeAt && seconds != null && scene.aerial) drawSkyOf(prepared, k, panX, seconds)
    }
}

/**
 * A still picture baked, a living one drawn (docs/plan-performance.md): while the view stands, the
 * biggest still steps of the scene are pictures the GPU keeps, and a frame lays them down with the living
 * layers between them; while it moves — the first frame, a pinch, a drag — everything is drawn directly.
 * [left], [top] — where the grid's origin is in the box; the pan is none (a card, the full screen as it opens).
 */
private fun DrawScope.drawBaked(baking: SceneBaking, left: Float, top: Float, k: Float, seconds: Float, seen: Rect) {
    val prepared = baking.prepared
    val view = SceneBaking.View(size.width, size.height, left, top, k)
    if (!baking.stands(view)) {
        translate(left, top) { drawScene(prepared, k, 0f, seconds, seen = seen) }
        return
    }
    baking.bake(view) { layer, step ->
        // the picture is as large as what it holds, within the box: a row of lamps is not a screen of pixels
        val shown = step.layers.filter { index -> !outOfSight(prepared.scene.layers[index], prepared.bounds[index], seen, 0f) }
        val pixels = SceneStrata.reachOf(prepared.scene.layers, prepared.bounds, shown)?.let { SceneBaking.pixelsOf(it, view) }
        if (pixels == null) {
            layer.record(size = IntSize(1, 1)) { }
            return@bake
        }
        layer.topLeft = pixels.topLeft
        layer.record(size = pixels.size) {
            translate(left - pixels.left, top - pixels.top) { shown.forEach { index -> drawOne(prepared, index, k, 0f, null) } }
        }
    }
    prepared.steps.forEachIndexed { at, step ->
        when (step) {
            is SceneStep.Still -> {
                val layer = baking.layerOf(at)
                if (layer != null) {
                    drawLayer(layer)
                } else {
                    translate(left, top) { step.layers.forEach { index -> if (!outOfSight(prepared.scene.layers[index], prepared.bounds[index], seen, 0f)) drawOne(prepared, index, k, 0f, null) } }
                }
            }
            is SceneStep.Alive -> translate(left, top) {
                step.layers.forEach { index -> if (!outOfSight(prepared.scene.layers[index], prepared.bounds[index], seen, 0f)) drawOne(prepared, index, k, 0f, seconds) }
            }
            SceneStep.SkyLife -> translate(left, top) { drawSkyOf(prepared, k, 0f, seconds) }
        }
    }
}

/** One layer of [prepared] at [k] pixels a unit, its plane shifted for a pan of [panX]; alive with [seconds] if it lives. */
private fun DrawScope.drawOne(prepared: PreparedScene, index: Int, k: Float, panX: Float, seconds: Float?, lightAlpha: Float = 1f) {
    val layer = prepared.scene.layers[index]
    val path = prepared.paths[index]
    val bounds = prepared.bounds[index]
    val paint = prepared.paints[index]
    val alive = seconds != null && paint.alive
    val own = if (layer.fill == SceneLayer.GLOW || layer.warmGlow) layer.opacity * lightAlpha else layer.opacity
    val alpha = if (alive) own * SceneMotion.alpha(layer, index, prepared.mode, seconds!!) else own
    val drift = if (alive) SceneMotion.drift(layer, index, seconds!!) else 0f
    val moved = if (alive && layer.anim != null) SceneMotion.moved(layer.anim, bounds.center.x, bounds.center.y, index, seconds!!) else null
    translate(SceneCamera.shift(panX, layer.depth) + drift * k, 0f) {
        scale(k, k, pivot = Offset.Zero) {
            if (moved != null) {
                // a thing drawn by its outline moves as well — the runners of a rocking chair, steam over a cup (spec 3.29)
                if (moved.alpha <= 0f || (paint.fill == null && paint.stroke == null)) return@scale
                val seen = alpha * moved.alpha
                translate(moved.dx, moved.dy) {
                    when {
                        moved.flap != 1f -> scale(1f, moved.flap, pivot = bounds.center) { drawLayer(path, paint, seen) }
                        moved.degrees != 0f -> rotate(moved.degrees, pivot = Offset(moved.pivotX, moved.pivotY)) { drawLayer(path, paint, seen) }
                        else -> drawLayer(path, paint, seen)
                    }
                }
                return@scale
            }
            if (layer.tx != 0f || layer.ty != 0f || layer.scale != 1f) {
                translate(layer.tx, layer.ty) { scale(layer.scale, layer.scale, pivot = Offset.Zero) { drawLayer(path, paint, alpha) } }
            } else {
                drawLayer(path, paint, alpha)
            }
        }
    }
}

/** The sky's own life of [prepared] — over the high sky where there is one — its plane the far one. */
private fun DrawScope.drawSkyOf(prepared: PreparedScene, k: Float, panX: Float, seconds: Float) {
    translate(SceneCamera.shift(panX, 0), 0f) {
        scale(k, k, pivot = Offset.Zero) {
            if (prepared.highSky) drawHighSkyLife(prepared.scene, prepared.mode, seconds) else drawSkyLife(prepared.mode, seconds)
        }
    }
}

private fun DrawScope.drawLayer(path: Path, paint: LayerPaint, alpha: Float) {
    paint.fill?.let { drawPath(path, it, alpha = alpha) }
    if (paint.stroke != null && paint.strokeStyle != null) drawPath(path, paint.stroke, alpha = alpha, style = paint.strokeStyle)
}

/** The paints of every layer of [scene] in [mode]; [bounds] — where the layers are, for the gradients that follow them. */
private fun paintsOf(scene: Scene, mode: SceneMode, bounds: List<Rect>): List<LayerPaint> {
    val sky = ScenePalette.token("sky", scene.location, mode) ?: NIGHT
    val skyLow = ScenePalette.token("skyLow", scene.location, mode) ?: NIGHT
    val skyHigh = ScenePalette.token("skyHigh", scene.location, mode) ?: sky
    val glow = ScenePalette.token("glow", scene.location, mode) ?: ScenePalette.TRANSPARENT_GLOW
    val band = Brush.verticalGradient(listOf(Color(sky), Color(skyLow)), startY = 0f, endY = SceneGrid.HORIZON)
    return scene.layers.mapIndexed { index, layer ->
        val area = bounds[index]
        val fill = when {
            layer.fillNone -> null
            layer.fill == SceneLayer.SKY -> band
            layer.fill == SceneLayer.SKY_HIGH -> Brush.verticalGradient(listOf(Color(skyHigh), Color(sky)), startY = area.top, endY = area.bottom)
            layer.fill == SceneLayer.GLOW -> radial(glow, area)
            layer.warmGlow -> radial(ScenePalette.WARM_GLOW, area)
            else -> ScenePalette.colorOf(layer.fill, layer.depth, scene, mode)?.let { SolidColor(Color(it)) }
        }
        val stroke = layer.stroke?.takeIf { layer.strokeWidth > 0f }?.let { ScenePalette.colorOf(it, layer.depth, scene, mode) }?.let { Color(it) }
        val style = stroke?.let { Stroke(layer.strokeWidth, cap = StrokeCap.Round, pathEffect = layer.anim?.dash?.let { PathEffect.dashPathEffect(floatArrayOf(it.first, it.second)) }) }
        LayerPaint(fill, stroke, style, SceneMotion.moves(layer, mode))
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
