package com.violinjourney.app.feature.home

import androidx.compose.ui.geometry.Rect
import com.violinjourney.app.core.domain.home.HomeCatalog
import com.violinjourney.app.core.domain.home.HomeItem
import com.violinjourney.app.feature.journey.art.SceneCamera
import com.violinjourney.app.feature.journey.art.SceneGrid

/**
 * The outline of «Обставить» (spec 3.36.7, 5.29 R7): the tile touched last outlines on the room the frame of the thing it put there,
 * so the row and the corner of the room are tied by the eye. Pure, with a test; drawn by `HomePicture`.
 */
internal object ArrangeOutline {
    /**
     * The thing [focus] outlines on a picture put together of [shown] — the things standing in the home it shows, its side, its day
     * (`HomeRules.standing`): the thing the tile put, once the picture shows it standing in that place. Null where there is nothing to
     * outline: «пусто» ([ArrangeFocus.itemId] empty), a place of a colour ([com.violinjourney.app.core.domain.home.HomeSlot.palette] —
     * walls and floor have no frame of their own), and a thing the picture does not show there: not yet — the tap is written to the
     * store and the room put together again off the main thread, and until then the picture shows what the place held, which is not
     * the one to outline —, not in its season (the tree before December: what stood there stands in its place), not in this home or on
     * this side.
     */
    fun thingOf(focus: ArrangeFocus, shown: List<HomeItem>): HomeItem? {
        if (focus.itemId.isEmpty()) return null
        val place = HomeCatalog.slotById[focus.slot] ?: return null
        if (place.palette) return null
        return shown.firstOrNull { it.id == focus.itemId && it.slot == focus.slot }
    }

    /**
     * The frame drawn round [box] — the box of the thing in units of the grid — with [pad] round it, within [seen], what of the grid the
     * picture shows: null where the thing is wholly out of sight; cut by the edge of the picture, the frame goes round what is seen of it.
     */
    fun frameOf(box: Rect, seen: Rect, pad: Float): Rect? {
        if (!box.overlaps(seen)) return null
        return box.inflate(pad).intersect(seen)
    }

    /**
     * Where the grid stands in a picture [width] × [height] px of a home without a camera, as `ScenePicture` draws it: covering the box at
     * the zoom of 1, or — [whole] — seen whole by its width (the room of «Обставить», spec 5.29 R7); the room in the middle of the
     * height. Null for a picture of no size.
     */
    fun gridIn(width: Float, height: Float, whole: Boolean): GridInPicture? {
        val zoom = if (whole) SceneCamera.wholeZoom(width, height) else SceneCamera.COVER_ZOOM
        val scale = SceneCamera.cover(width, height) * zoom
        if (!(scale > 0f)) return null
        return GridInPicture(
            scale = scale,
            left = (width - SceneGrid.WIDTH * scale) / 2,
            top = SceneCamera.top(zoom, width, height, outdoors = false),
            width = width,
            height = height,
        )
    }
}

/** The grid of a home in a picture [width] × [height] px: [scale] px a unit, its origin at [left], [top] px of the picture. */
internal data class GridInPicture(val scale: Float, val left: Float, val top: Float, val width: Float, val height: Float) {
    /** What of the grid the picture shows, in units of the grid, less [inset] units at every edge. */
    fun seen(inset: Float = 0f): Rect = Rect(-left / scale, -top / scale, (width - left) / scale, (height - top) / scale).deflate(inset)
}
