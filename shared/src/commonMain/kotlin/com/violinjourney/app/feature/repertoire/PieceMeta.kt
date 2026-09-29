package com.violinjourney.app.feature.repertoire

import com.violinjourney.app.core.domain.repertoire.scale.ScaleKind

/** One part of the line under the name of an element (spec 3.36.4): «И. С. Бах · G-dur · [metronome] 100». */
sealed interface MetaPart {
    /** The composer or the author, as typed. */
    data class Composer(val name: String) : MetaPart

    /** The key in German notation, «G-dur». */
    data class Key(val name: String) : MetaPart

    /** The kind of a scale, «мажор»: its key and its octaves are its very title. */
    data class Kind(val kind: ScaleKind) : MetaPart

    /** The tempo — the metronome of the icon set and the figure (spec 3.16), not the glyph ♩. */
    data class Tempo(val bpm: Int) : MetaPart
}

/**
 * What the line under the name of an element says (spec 3.36.4): only what is set, in one order — the composer, the key, the tempo.
 * A scale says its kind and its tempo (was «мажор · 3 октавы»: the octaves are in its title); a bow stroke only its tempo — it has
 * neither author nor key. Nothing set — no parts, and the line is not there. Pure: the list of a section and, from stage 109, the
 * screen of the element put it into words.
 */
object PieceMeta {
    fun parts(composer: String, keyName: String?, tempoBpm: Int?, scaleKind: ScaleKind? = null, stroke: Boolean = false): List<MetaPart> = buildList {
        when {
            scaleKind != null -> add(MetaPart.Kind(scaleKind))
            stroke -> Unit
            else -> {
                if (composer.isNotBlank()) add(MetaPart.Composer(composer))
                if (keyName != null) add(MetaPart.Key(keyName))
            }
        }
        if (tempoBpm != null) add(MetaPart.Tempo(tempoBpm))
    }
}
