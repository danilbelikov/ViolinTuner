package com.example.violintuner.feature.repertoire.sections

import com.example.violintuner.core.domain.repertoire.SectionCount
import com.example.violintuner.core.domain.repertoire.SectionRef

/** One card of the sections screen (spec 3.22, handoff 24b). */
data class SectionCard(
    val ref: SectionRef,
    /** Null for a built-in section: its name is a word of the interface. */
    val name: String?,
    val count: SectionCount,
)

/** The way into the repertoire: its sections, each with how far it has come. */
data class SectionsState(
    /** True until the stored pieces have been read once. */
    val loading: Boolean,
    val cards: List<SectionCard>,
    /** Everything in every section: «выучено 9 из 27» at the top. */
    val total: SectionCount,
    /** Non-null while «Новый раздел» is open: what has been typed so far. */
    val newName: String? = null,
    val maxNameLength: Int,
) {
    val canCreate: Boolean get() = !newName.isNullOrBlank()
}

sealed interface SectionsIntent {
    data class SectionClicked(val ref: SectionRef) : SectionsIntent

    data object AddClicked : SectionsIntent

    data class NameChanged(val text: String) : SectionsIntent

    data object CreateConfirmed : SectionsIntent

    data object DialogDismissed : SectionsIntent
}

sealed interface SectionsEffect {
    data class OpenSection(val ref: SectionRef) : SectionsEffect
}
