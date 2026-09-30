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
}
