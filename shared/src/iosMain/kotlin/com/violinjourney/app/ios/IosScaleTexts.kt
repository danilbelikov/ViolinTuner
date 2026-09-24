package com.violinjourney.app.ios

import com.violinjourney.app.core.domain.repertoire.scale.ScaleSpec
import com.violinjourney.app.feature.repertoire.scale.ScaleTexts
import com.violinjourney.app.feature.repertoire.scale.ScaleWord
import com.violinjourney.app.feature.repertoire.scale.ScaleWords
import com.violinjourney.app.feature.repertoire.scale.resourceOf
import org.jetbrains.compose.resources.getString

/**
 * The title of a scale for the view model on iOS. The view model asks for it without suspending, and the resources of
 * Compose are read suspending: the words are read once, when the app starts, in the language of the interface.
 */
internal class IosScaleTexts private constructor(private val words: Map<ScaleWord, String>) : ScaleTexts {
    override fun titleOf(spec: ScaleSpec): String = ScaleWords.title(spec) { word, args -> format(words.getValue(word), args) }

    companion object {
        suspend fun load() = IosScaleTexts(ScaleWord.entries.associateWith { getString(resourceOf(it)) })

        private val PLACEHOLDER = Regex("""%(\d+)\$[sd]""")

        /** `%1$s`, `%2$d` of the Android strings, which the resources of Compose keep as they are. */
        fun format(template: String, args: Array<out Any>): String =
            PLACEHOLDER.replace(template) { match -> args.getOrNull(match.groupValues[1].toInt() - 1)?.toString() ?: match.value }
    }
}
