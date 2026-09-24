package com.violinjourney.app.feature.repertoire.scale

import androidx.compose.runtime.Composable
import com.violinjourney.app.core.domain.repertoire.scale.Scale
import com.violinjourney.app.core.domain.repertoire.scale.ScaleKind
import com.violinjourney.app.core.domain.repertoire.scale.ScaleNote
import com.violinjourney.app.core.domain.repertoire.scale.ScaleSpec
import com.violinjourney.app.core.ui.format.Formats
import com.violinjourney.app.shared.resources.Res
import com.violinjourney.app.shared.resources.scale_kind_harmonic
import com.violinjourney.app.shared.resources.scale_kind_major
import com.violinjourney.app.shared.resources.scale_kind_melodic
import com.violinjourney.app.shared.resources.scale_kind_natural
import com.violinjourney.app.shared.resources.scale_octave_few
import com.violinjourney.app.shared.resources.scale_octave_many
import com.violinjourney.app.shared.resources.scale_octave_one
import com.violinjourney.app.shared.resources.scale_range
import com.violinjourney.app.shared.resources.scale_suffix_harmonic
import com.violinjourney.app.shared.resources.scale_suffix_melodic
import com.violinjourney.app.shared.resources.scale_title
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * The words of a scale (spec 3.22). Its title is put together by the app and stored like any
 * title — «G-dur · 3 октавы», «a-moll гармонический · 2 октавы» — so the view model needs the
 * words without being a screen; tests give it a fake.
 */
interface ScaleTexts {
    fun titleOf(spec: ScaleSpec): String
}

/** The words a scale is said in; each platform reads them where it keeps them. */
enum class ScaleWord { TITLE, SUFFIX_HARMONIC, SUFFIX_MELODIC, KIND_MAJOR, KIND_NATURAL, KIND_HARMONIC, KIND_MELODIC, OCTAVE_ONE, OCTAVE_FEW, OCTAVE_MANY, RANGE }

/** How a scale is said, with [word] giving the words (a formatted string resource, by its [ScaleWord]). */
object ScaleWords {
    inline fun title(spec: ScaleSpec, word: (ScaleWord, Array<out Any>) -> String): String {
        val suffix = when (spec.kind) {
            ScaleKind.HARMONIC_MINOR -> " " + word(ScaleWord.SUFFIX_HARMONIC, emptyArray())
            ScaleKind.MELODIC_MINOR -> " " + word(ScaleWord.SUFFIX_MELODIC, emptyArray())
            else -> ""
        }
        return word(ScaleWord.TITLE, arrayOf(spec.key.germanName + suffix, octaves(spec.octaves, word)))
    }

    inline fun subtitle(spec: ScaleSpec, word: (ScaleWord, Array<out Any>) -> String): String =
        word(ScaleWord.TITLE, arrayOf(kind(spec.kind, word), octaves(spec.octaves, word)))

    inline fun kind(kind: ScaleKind, word: (ScaleWord, Array<out Any>) -> String): String = word(
        when (kind) {
            ScaleKind.MAJOR -> ScaleWord.KIND_MAJOR
            ScaleKind.NATURAL_MINOR -> ScaleWord.KIND_NATURAL
            ScaleKind.HARMONIC_MINOR -> ScaleWord.KIND_HARMONIC
            ScaleKind.MELODIC_MINOR -> ScaleWord.KIND_MELODIC
        },
        emptyArray(),
    )

    inline fun octaves(count: Int, word: (ScaleWord, Array<out Any>) -> String): String =
        word(Formats.plural(count, ScaleWord.OCTAVE_ONE, ScaleWord.OCTAVE_FEW, ScaleWord.OCTAVE_MANY), arrayOf(count))

    /** «G3 – G6»: Latin names like everywhere in the app, «#» for a sharp (Manrope has no ♯), «b» for a flat. */
    inline fun range(scale: Scale, word: (ScaleWord, Array<out Any>) -> String): String =
        word(ScaleWord.RANGE, arrayOf(nameOf(scale.lowest), nameOf(scale.highest)))

    fun nameOf(note: ScaleNote): String = note.letter.name + when (note.alter) {
        -1 -> "b"
        1 -> "#"
        2 -> "x"
        else -> ""
    } + note.octave
}

private fun resourceOf(word: ScaleWord): StringResource = when (word) {
    ScaleWord.TITLE -> Res.string.scale_title
    ScaleWord.SUFFIX_HARMONIC -> Res.string.scale_suffix_harmonic
    ScaleWord.SUFFIX_MELODIC -> Res.string.scale_suffix_melodic
    ScaleWord.KIND_MAJOR -> Res.string.scale_kind_major
    ScaleWord.KIND_NATURAL -> Res.string.scale_kind_natural
    ScaleWord.KIND_HARMONIC -> Res.string.scale_kind_harmonic
    ScaleWord.KIND_MELODIC -> Res.string.scale_kind_melodic
    ScaleWord.OCTAVE_ONE -> Res.string.scale_octave_one
    ScaleWord.OCTAVE_FEW -> Res.string.scale_octave_few
    ScaleWord.OCTAVE_MANY -> Res.string.scale_octave_many
    ScaleWord.RANGE -> Res.string.scale_range
}

/** «мажор · 3 октавы»: the second line of a scale's card and of its screen. */
@Composable
fun scaleSubtitle(spec: ScaleSpec): String = ScaleWords.subtitle(spec) { w, args -> stringResource(resourceOf(w), *args) }

@Composable
fun scaleTitle(spec: ScaleSpec): String = ScaleWords.title(spec) { w, args -> stringResource(resourceOf(w), *args) }

@Composable
fun scaleKindLabel(kind: ScaleKind): String = ScaleWords.kind(kind) { w, args -> stringResource(resourceOf(w), *args) }

@Composable
fun scaleRange(scale: Scale): String = ScaleWords.range(scale) { w, args -> stringResource(resourceOf(w), *args) }
