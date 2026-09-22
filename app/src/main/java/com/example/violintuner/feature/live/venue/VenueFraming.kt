package com.example.violintuner.feature.live.venue

/** What is drawn behind Live: the room of the home, or a hall seen from its stage. They are framed differently. */
enum class PictureKind { ROOM, HALL }

/**
 * Where the picture stands in a box: [scale] pixels a unit of the scene's grid, and the point of the
 * grid at the top-left corner of the box.
 */
data class Framing(val scale: Float, val left: Float, val top: Float)

/**
 * How the picture fills the Live screen (handoff `sizes`, frames 29c, 29d, 29g, 29h). Not three fixed
 * frames but a rule, so that any phone gets what the frames show: upright the width decides the scale
 * and the ring stands on the window and the music stand of the room, or on the back wall of a hall;
 * lying down the scene covers the screen from the wall to the edge of the desk. The frame does not
 * follow the ring: in «Настройка» the ring moves, the room does not. Pure, in any unit of length.
 */
object VenueFraming {
    /** How much of the scene's grid is seen across an upright screen: the room a little narrower than its 412, the hall much narrower — its rows are the point. */
    private const val ROOM_WIDTH = 397f
    private const val HALL_WIDTH = 305f

    /** Upright, no more of the height than this is seen, whatever the screen: a tall phone is zoomed rather than shown the void. */
    private const val ROOM_HEIGHT = 800f
    private const val HALL_HEIGHT = 620f

    /** The point of the grid that stands where the ring usually is: the window and the music stand, the back wall of a hall. */
    private const val ROOM_RING_Y = 110f
    private const val HALL_RING_Y = 100f

    /** Where the ring usually is, as a share of the height of the upright screen. */
    const val RING_AT = 0.47f

    /** Lying down, the scene covers the screen with this much of its height: the wall, the window and the edge of the desk; the tiers of a hall. */
    private const val LANDSCAPE_HEIGHT = 190f
    private const val ROOM_LANDSCAPE_MIDDLE = 105f
    private const val HALL_LANDSCAPE_MIDDLE = 35f

    /** How far the scenes are drawn: a room from its ceiling to the floor at our feet, a hall from its ceiling to the boards. */
    private const val ROOM_TOP = -420f
    private const val ROOM_BOTTOM = 600f
    private const val HALL_TOP = -240f
    private const val HALL_BOTTOM = 480f

    private const val GRID_WIDTH = 412f
    private const val MIDDLE_X = GRID_WIDTH / 2

    fun of(kind: PictureKind, width: Float, height: Float, landscape: Boolean): Framing {
        val room = kind == PictureKind.ROOM
        val scale = if (landscape) {
            maxOf(width / GRID_WIDTH, height / LANDSCAPE_HEIGHT)
        } else {
            maxOf(width / (if (room) ROOM_WIDTH else HALL_WIDTH), height / (if (room) ROOM_HEIGHT else HALL_HEIGHT))
        }
        val seenHigh = height / scale
        val wanted = if (landscape) {
            (if (room) ROOM_LANDSCAPE_MIDDLE else HALL_LANDSCAPE_MIDDLE) - seenHigh / 2
        } else {
            (if (room) ROOM_RING_Y else HALL_RING_Y) - RING_AT * seenHigh
        }
        val top = wanted.coerceIn(if (room) ROOM_TOP else HALL_TOP, maxOf(if (room) ROOM_TOP else HALL_TOP, (if (room) ROOM_BOTTOM else HALL_BOTTOM) - seenHigh))
        return Framing(scale, MIDDLE_X - width / scale / 2, top)
    }
}
