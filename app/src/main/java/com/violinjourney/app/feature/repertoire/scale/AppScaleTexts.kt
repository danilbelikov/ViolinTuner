package com.violinjourney.app.feature.repertoire.scale

import android.content.Context
import com.violinjourney.app.R
import com.violinjourney.app.core.domain.repertoire.scale.ScaleSpec
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/** The title of a scale for the view model on Android: the app's words, read through its context as before. */
class AppScaleTexts @Inject constructor(@ApplicationContext private val context: Context) : ScaleTexts {
    override fun titleOf(spec: ScaleSpec): String = ScaleWords.title(spec) { word, args -> context.getString(idOf(word), *args) }

    private fun idOf(word: ScaleWord): Int = when (word) {
        ScaleWord.TITLE -> R.string.scale_title
        ScaleWord.SUFFIX_HARMONIC -> R.string.scale_suffix_harmonic
        ScaleWord.SUFFIX_MELODIC -> R.string.scale_suffix_melodic
        ScaleWord.KIND_MAJOR -> R.string.scale_kind_major
        ScaleWord.KIND_NATURAL -> R.string.scale_kind_natural
        ScaleWord.KIND_HARMONIC -> R.string.scale_kind_harmonic
        ScaleWord.KIND_MELODIC -> R.string.scale_kind_melodic
        ScaleWord.OCTAVE_ONE -> R.string.scale_octave_one
        ScaleWord.OCTAVE_FEW -> R.string.scale_octave_few
        ScaleWord.OCTAVE_MANY -> R.string.scale_octave_many
        ScaleWord.RANGE -> R.string.scale_range
    }
}
