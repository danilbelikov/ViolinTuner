package com.violinjourney.app.core.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/**
 * The corners of the redesign (spec 5.29): a scale of four — [S] 12, [M] 18, [L] 24, [XL] 28 — and two off it, [Control] 14 and
 * [Segment] 11. A button of 56 and a filter chip are capsules. The shapes of MaterialTheme stay as they are: overriding them would
 * move every screen at once, and each stage moves its own.
 */
object AppShapes {
    /** Items of a menu, plates of icons. */
    val S = RoundedCornerShape(12.dp)

    /** Cards, menus, groups of rows. */
    val M = RoundedCornerShape(18.dp)

    /** The player, the message of iOS. */
    val L = RoundedCornerShape(24.dp)

    /** Sheets and dialogs. */
    val XL = RoundedCornerShape(28.dp)

    /** A field of 56, a button of 48, a choice chip of 44, the container of a segmented switch. */
    val Control = RoundedCornerShape(14.dp)

    /** One segment of a segmented switch, the pill inside the container. */
    val Segment = RoundedCornerShape(11.dp)
}
