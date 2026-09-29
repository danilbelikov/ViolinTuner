package com.violinjourney.app.core.domain.repertoire.scale

import com.violinjourney.app.core.domain.repertoire.Accidental
import com.violinjourney.app.core.domain.repertoire.RepertoireConfig
import com.violinjourney.app.core.domain.repertoire.Tonic
import com.violinjourney.app.feature.repertoire.scale.ScaleWords
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * On the violin — G3 … E7 (spec 5.1, 5.16) — only the third octave can be out of reach: the lowest tonic is no higher than F#4, and two
 * octaves from it end on F#6. The form has one reason for it, «Три октавы от F4 не помещаются на скрипке» (spec 3.36.4).
 */
class ScaleOctavesTest {
    private val config = RepertoireConfig()

    @Test
    fun `one and two octaves fit from every tonic with every sign`() {
        for (tonic in Tonic.entries) {
            for (accidental in Accidental.entries) {
                for (octaves in 1..2) {
                    assertTrue(
                        Scales.octavesFit(tonic, accidental, octaves, config.scaleLowestMidi, config.scaleHighestMidi),
                        "$octaves from $tonic $accidental",
                    )
                }
            }
        }
    }

    @Test
    fun `no key starts higher than F sharp 4 and three octaves fit from G3 but not from F4`() {
        for (tonic in Tonic.entries) {
            for (accidental in Accidental.entries) {
                val start = Scales.lowestTonic(tonic, accidental, config.scaleLowestMidi)
                assertTrue(start.midi <= F_SHARP_4, "the start ${ScaleWords.nameOf(start)}")
            }
        }
        assertTrue(Scales.octavesFit(Tonic.G, Accidental.NATURAL, 3, config.scaleLowestMidi, config.scaleHighestMidi))
        assertFalse(Scales.octavesFit(Tonic.F, Accidental.NATURAL, 3, config.scaleLowestMidi, config.scaleHighestMidi))
    }

    private companion object {
        const val F_SHARP_4 = 66
    }
}
