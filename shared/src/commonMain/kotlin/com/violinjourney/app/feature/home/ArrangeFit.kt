package com.violinjourney.app.feature.home

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import com.violinjourney.app.feature.journey.PictureFit

/**
 * Where the room and the places of «Обставить» stand under its bar (spec 3.36.7, 5.29 R7; open question 16 and the review of stage
 * 119). Pure, with a test.
 *
 * - Lying ([HomeRoomFit.lying] — a window wider than high where the room keeps 200 beside the places): the room on the left, the whole
 *   height of its column less [LyingTop] over it and [LyingUnder] under it; the places on the right in a column of
 *   [HomeRoomFit.ColumnBeside], the whole height — they scroll to the bottom.
 * - Upright: a column no wider than the column of the home, in the middle; the room on top, fixed, its fields at the sides, [Room]
 *   high — less where the places under it would not keep the caption and one whole place ([roomHeight]: in the half of a split screen
 *   the first place stood cut under a room of 210), down to [RoomLeast]; the places under it, to the bottom.
 */
internal object ArrangeFit {
    val Room = 210.dp
    val RoomLeast = PictureFit.LyingMin
    val LyingTop = 4.dp
    val LyingUnder = 16.dp

    /** The room and the places, in the box under the bar. */
    data class Frames(val room: DpRect, val places: DpRect)

    /** The room upright in a box [height] high, where the places under it keep [placesLeast] — the caption and one whole place. */
    fun roomHeight(height: Dp, placesLeast: Dp): Dp = (height - placesLeast).coerceIn(RoomLeast, Room)

    /** The frames in a box [width] × [height]: [lying] or upright in a column of [column] at most, the places keeping [placesLeast]. */
    fun frames(width: Dp, height: Dp, lying: Boolean, column: Dp, placesLeast: Dp): Frames {
        val side = HomeRoomFit.Side
        if (lying) {
            val placesLeft = width - side - HomeRoomFit.ColumnBeside
            return Frames(
                room = DpRect(side, LyingTop, side + HomeRoomFit.roomBeside(width), (height - LyingUnder).coerceAtLeast(LyingTop)),
                places = DpRect(placesLeft, 0.dp, width - side, height),
            )
        }
        val wide = minOf(width, column)
        val left = (width - wide) / 2
        val room = roomHeight(height, placesLeast)
        return Frames(
            room = DpRect(left + side, 0.dp, left + wide - side, room),
            places = DpRect(left, room, left + wide, maxOf(room, height)),
        )
    }
}
