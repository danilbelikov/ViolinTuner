package com.example.violintuner.feature.practice.components

/**
 * The streak flame as data: the `FLAME` array of the handoff (`docs/design/project/records`,
 * frame 19g1, rest shapes `d0`) — two filled layers on a 24 × 24 grid, bottom first, each with
 * the point it moves about. Like the trophies, it is an illustration, not an icon of the set.
 */
internal object FlameArt {
    const val GRID = 24f

    class Layer(val d: String, val pivotX: Float, val pivotY: Float)

    /** The tongue — `flame.outer`. */
    val Outer = Layer(
        d = "M12 2.5C12.6 6.3 16.5 8.1 17.4 11.7C18.3 15.3 15.7 19.5 12 19.5C8.3 19.5 5.7 15.3 6.6 11.7C7 10.2 7.8 9 8.7 8C9 9.4 9.7 10.4 10.7 11C10.6 7.8 11.1 5 12 2.5Z",
        pivotX = 12f,
        pivotY = 19.5f,
    )

    /** The core — `flame.core`, or `flame.hot` from thirty days on. */
    val Core = Layer(
        d = "M12 10.5C13.2 12.5 14.6 13.6 14.6 15.6C14.6 17.6 13.4 19 12 19C10.6 19 9.4 17.6 9.4 15.6C9.4 13.6 10.8 12.5 12 10.5Z",
        pivotX = 12f,
        pivotY = 19f,
    )
}
