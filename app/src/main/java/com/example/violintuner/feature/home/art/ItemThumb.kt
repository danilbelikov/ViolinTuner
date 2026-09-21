package com.example.violintuner.feature.home.art

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import com.example.violintuner.core.domain.home.HomeCatalogData
import com.example.violintuner.core.domain.home.HomeItem
import com.example.violintuner.feature.journey.art.Scene
import com.example.violintuner.feature.journey.art.SceneMode
import com.example.violintuner.feature.journey.art.ScenePalette
import com.example.violintuner.feature.journey.art.drawPrepared
import com.example.violintuner.feature.journey.art.prepare

private const val FILL = 0.78f
private const val MAX_SCALE = 3.2f

/**
 * A thing alone, as it stands on a shelf of the shop: the very layers it has in the room, fitted
 * to the box — it is known at once, not by a photograph (handoff 27b). Walls and floors are
 * colours: a swatch. [silhouette] — a thing not brought yet: its shape in one tone.
 */
@Composable
fun ItemThumb(item: HomeItem, modifier: Modifier = Modifier, silhouette: Color? = null) {
    val art = rememberHouseArt(HOUSE_OF_THUMBS, SceneMode.EVENING)
    val drawn = art?.items?.get(item.id)
    val prepared = remember(drawn) {
        // glows and floor shadows belong to the room, not to the thing on a shelf
        drawn?.let { d -> prepare(Scene(ScenePalette.HOUSE, aerial = false, layers = d.layers.filter { it.fill != "GLOW" && !it.fill.startsWith("rgba(20,16,30") && !it.warmGlow }.map { it.copy(anim = null) }), SceneMode.DAY) }
    }
    val layer = rememberGraphicsLayer()
    Canvas(modifier.fillMaxSize()) {
        if (drawn != null && prepared != null) {
            val w = drawn.right - drawn.left
            val h = drawn.bottom - drawn.top
            val k = minOf(size.width * FILL / w, size.height * FILL / h, MAX_SCALE * density)
            val picture: androidx.compose.ui.graphics.drawscope.DrawScope.() -> Unit = {
                translate((size.width - w * k) / 2 - drawn.left * k, (size.height - h * k) / 2 - drawn.top * k) { drawPrepared(prepared, k) }
            }
            if (silhouette == null) {
                picture()
            } else {
                layer.record { picture() }
                layer.colorFilter = ColorFilter.tint(silhouette, BlendMode.SrcIn)
                drawLayer(layer)
            }
        } else if (item.palette.isNotEmpty()) {
            val main = item.palette["wallHome"] ?: item.palette["floorHome"] ?: item.palette.values.first()
            val lit = item.palette["wallLitHome"] ?: item.palette["floorLine"] ?: main
            val side = minOf(size.width, size.height) * 0.62f
            val topLeft = Offset((size.width - side) / 2, (size.height - side) / 2)
            val tone = { c: Long -> silhouette ?: Color(c) }
            drawRoundRect(tone(main), topLeft, Size(side, side), CornerRadius(side * 0.16f))
            drawRoundRect(tone(lit), topLeft, Size(side / 2, side), CornerRadius(side * 0.16f))
        } else {
            // what the room came with and has no colour of its own: the room's tone
            val side = minOf(size.width, size.height) * 0.62f
            drawRoundRect(silhouette ?: Color(HomeCatalogData.tokens.getValue(if (item.slot == "floor") "floorHome" else "wallHome")), Offset((size.width - side) / 2, (size.height - side) / 2), Size(side, side), CornerRadius(side * 0.16f))
        }
    }
}

/** Things are shown as they stand in the rented room: every home draws them the same, only elsewhere. */
private const val HOUSE_OF_THUMBS = "rent"
