package com.violinjourney.app.feature.live

/**
 * Switches of Live in code (spec 3.36.6, 5.29 R6), side by side: not in «Настройки» and not a flag of the build — what the owner
 * decides from the stand, on the phone (docs/redesign/implementation/open-questions.md, 12–13), is turned on by one line here.
 * The screens take them as parameters whose default is the switch, so their previews can show the switch turned on.
 */
object LiveSwitches {
    /**
     * The pale note of the locked string in the ring of «Настройка» in silence — «D4» at 0.28, where to pull to (question 13). Off:
     * the ring is the empty calm outline of 3.4 and 3.14.
     */
    const val STRING_SILHOUETTE = false

    /**
     * The tab bar dims with the controls of Live while the light is out (question 12): Live lends its light to the bar's
     * [com.violinjourney.app.navigation.TabBarLight] while it is resumed — the items down to 0.38, not the ground of the bar, touches
     * pass — and takes it back as it leaves. The bar is there only upright: lying down the root shows none on Live. Off: the bar in
     * full light, as before R6.
     */
    const val DIM_TAB_BAR = false
}
