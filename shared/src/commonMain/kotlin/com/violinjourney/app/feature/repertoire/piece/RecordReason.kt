package com.violinjourney.app.feature.repertoire.piece

/**
 * Why «Записать дубль» sleeps (spec 3.36.4) — one reason at a time, in this order, over everything else in the bottom zone:
 * no microphone (the line «нет разрешения» of R1), the backing on and no headphones, its sound being prepared, its sound not to
 * be made (spec 5.25). Each is a plate but the first; the key wakes by itself when its reason goes. Pure.
 */
enum class RecordReason {
    NO_MIC,
    NO_HEADPHONES,
    PREPARING,
    UNPREPARED;

    companion object {
        /** [micPermission] null — not known yet: a tap asks, nothing sleeps. [backing] null — still being read. */
        fun of(micPermission: Boolean?, backing: BackingUi?): RecordReason? = when {
            micPermission == false -> NO_MIC
            backing == null || !backing.wanted -> null
            !backing.route.output.isHeadphones -> NO_HEADPHONES
            backing.preparing -> PREPARING
            backing.unprepared -> UNPREPARED
            else -> null
        }
    }
}

/**
 * Two sentences of one plate, the reason and the way out («Подключите наушники… Или выключите „С минусовкой“.»): a space between
 * them, none after a full stop of Chinese or Japanese, which is a space in itself. Pure.
 */
object Sentences {
    private const val WIDE_STOPS = "。！？"

    fun join(first: String, second: String): String = if (first.lastOrNull()?.let { it in WIDE_STOPS } == true) first + second else "$first $second"
}
