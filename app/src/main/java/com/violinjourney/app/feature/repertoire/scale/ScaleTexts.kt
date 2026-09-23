package com.violinjourney.app.feature.repertoire.scale

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.violinjourney.app.R
import com.violinjourney.app.core.domain.repertoire.scale.Scale
import com.violinjourney.app.core.domain.repertoire.scale.ScaleKind
import com.violinjourney.app.core.domain.repertoire.scale.ScaleNote
import com.violinjourney.app.core.domain.repertoire.scale.ScaleSpec
import com.violinjourney.app.core.ui.format.Formats
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * The words of a scale (spec 3.22). Its title is put together by the app and stored like any
 * title — «G-dur · 3 октавы», «a-moll гармонический · 2 октавы» — so the view model needs the
 * words without being a screen; tests give it a fake.
 */
interface ScaleTexts {
    fun titleOf(spec: ScaleSpec): String
}

class AppScaleTexts @Inject constructor(@ApplicationContext private val context: Context) : ScaleTexts {
    override fun titleOf(spec: ScaleSpec): String = ScaleWords.title(context, spec)
}

internal object ScaleWords {
    fun title(context: Context, spec: ScaleSpec): String {
        val suffix = when (spec.kind) {
            ScaleKind.HARMONIC_MINOR -> " " + context.getString(R.string.scale_suffix_harmonic)
            ScaleKind.MELODIC_MINOR -> " " + context.getString(R.string.scale_suffix_melodic)
            else -> ""
        }
        return context.getString(R.string.scale_title, spec.key.germanName + suffix, octaves(context, spec.octaves))
    }

    fun subtitle(context: Context, spec: ScaleSpec): String =
        context.getString(R.string.scale_title, kind(context, spec.kind), octaves(context, spec.octaves))

    fun kind(context: Context, kind: ScaleKind): String = context.getString(
        when (kind) {
            ScaleKind.MAJOR -> R.string.scale_kind_major
            ScaleKind.NATURAL_MINOR -> R.string.scale_kind_natural
            ScaleKind.HARMONIC_MINOR -> R.string.scale_kind_harmonic
            ScaleKind.MELODIC_MINOR -> R.string.scale_kind_melodic
        },
    )

    fun octaves(context: Context, count: Int): String =
        context.getString(Formats.plural(count, R.string.scale_octave_one, R.string.scale_octave_few, R.string.scale_octave_many), count)

    /** «G3 – G6»: Latin names like everywhere in the app, «#» for a sharp (Manrope has no ♯), «b» for a flat. */
    fun range(context: Context, scale: Scale): String = context.getString(R.string.scale_range, nameOf(scale.lowest), nameOf(scale.highest))

    private fun nameOf(note: ScaleNote): String = note.letter.name + when (note.alter) {
        -1 -> "b"
        1 -> "#"
        2 -> "x"
        else -> ""
    } + note.octave
}

/** «мажор · 3 октавы»: the second line of a scale's card and of its screen. */
@Composable
fun scaleSubtitle(spec: ScaleSpec): String = ScaleWords.subtitle(LocalContext.current, spec)

@Composable
fun scaleTitle(spec: ScaleSpec): String = ScaleWords.title(LocalContext.current, spec)

@Composable
fun scaleKindLabel(kind: ScaleKind): String = ScaleWords.kind(LocalContext.current, kind)

@Composable
fun scaleRange(scale: Scale): String = ScaleWords.range(LocalContext.current, scale)
