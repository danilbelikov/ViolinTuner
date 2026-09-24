package com.violinjourney.app.feature.home.art

import androidx.compose.ui.geometry.Rect
import com.violinjourney.app.core.domain.home.HomeCatalog
import com.violinjourney.app.core.domain.home.HomeItem
import com.violinjourney.app.core.domain.home.HomeRules
import com.violinjourney.app.feature.journey.art.Scene
import com.violinjourney.app.feature.journey.art.SceneLayer
import com.violinjourney.app.feature.journey.art.SceneMode
import com.violinjourney.app.feature.journey.art.ScenePalette

/**
 * What a thing shows on a shelf of the shop and in its card: a scene and the box of the grid it is
 * framed by. A [sample] fills its picture like a card of samples — a corner of the room with a wall
 * or a floor, the window whole; otherwise it is the thing alone, standing on the board.
 */
class ItemThumbScene(val scene: Scene, val box: Rect, val sample: Boolean)

/**
 * The rules of what stands on a shelf (spec 3.29, 5.22). Things are shown as they stand in the rented
 * room, in the evening: the shop is lit by its lamps. What is a colour or a part of the window is
 * shown in its setting — the thing alone would be a swatch or a bare glazing bar. Pure.
 */
object ItemThumbs {
    private const val WALLPAPER = "wallpaper"
    private const val FLOOR = "floor"

    /** The window whole: its frame, the view and the curtains, the one of them sold in its place and the others the room's own. */
    private val WINDOW_PARTS = linkedMapOf("window" to "window_simple", "view" to "view_city", "curtain" to "curtain_plum")

    // the vignettes, in units of the grid of the rented room: as wide as a shelf's picture for its height (100 × 76)
    val WALL_BOX = Rect(96f, 70f, 316f, 237f)
    val FLOOR_BOX = Rect(96f, 170f, 316f, 337f)
    val WINDOW_BOX = Rect(206f, 15f, 390f, 155f)

    /** The home a thing is shown in: the rented room, or the first home that has a place for it — the fireplace needs a chimney. */
    fun houseOf(item: HomeItem): String =
        HomeCatalog.houses.firstOrNull { it.drawn && HomeRules.slotIn(item.slot, it.id) && item.at.let { at -> at == null || HomeRules.slotIn(at, it.id) } }?.id
            ?: HomeCatalog.START_HOUSE

    /**
     * The picture of [item] from [art] — the art of [houseOf] it, in the evening; null for a thing that home does not draw.
     * [glows] — the halos of lamps and flames: in the card of a thing; on a shelf the edge of a small picture would cut them.
     */
    fun of(item: HomeItem, art: HouseArt, glows: Boolean = false): ItemThumbScene? = when {
        item.slot == WALLPAPER -> vignette(art, listOf(item), WALL_BOX)
        item.slot == FLOOR -> vignette(art, listOf(item), FLOOR_BOX)
        item.slot in WINDOW_PARTS -> vignette(art, WINDOW_PARTS.map { (slot, own) -> if (slot == item.slot) item else HomeCatalog.byId.getValue(own) }, WINDOW_BOX)
        else -> art.items[item.id]?.let { drawn ->
            ItemThumbScene(
                Scene(ScenePalette.HOUSE, aerial = false, layers = if (glows) drawn.layers else drawn.layers.filter { it.fill != SceneLayer.GLOW && !it.warmGlow }, overrides = item.palette),
                drawn.shelf ?: Rect(drawn.left, drawn.top, drawn.right, drawn.bottom),
                sample = false,
            )
        }
    }

    private fun vignette(art: HouseArt, standing: List<HomeItem>, box: Rect): ItemThumbScene =
        ItemThumbScene(HomeComposer.compose(art, standing.sortedBy { it.z }, outside = false, mode = SceneMode.EVENING).scene, box, sample = true)
}
