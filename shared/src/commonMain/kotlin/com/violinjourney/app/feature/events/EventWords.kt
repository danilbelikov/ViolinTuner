package com.violinjourney.app.feature.events

import com.violinjourney.app.core.domain.events.BuiltInKind
import com.violinjourney.app.core.domain.events.EventName
import com.violinjourney.app.core.domain.events.KindRef
import com.violinjourney.app.core.domain.events.SessionEvent
import com.violinjourney.app.core.ui.format.Formats
import com.violinjourney.app.shared.resources.Res
import com.violinjourney.app.shared.resources.session_take_title
import org.jetbrains.compose.resources.getString

/**
 * The words of an event outside composition, in the language of the interface (plan D11, D12): the name a recording of an event keeps
 * when the event is deleted, and the name and the title of a recording sent to other apps. The screens say the same words with
 * [eventNameOf] and [eventRecordTitle]. An interface for the sake of the view models' tests, which have no resources to read.
 */
interface EventWords {
    /** «Осенний концерт», «Урок» — the title of an event, or the name of its kind. */
    suspend fun nameOf(name: EventName): String

    /** «Осенний концерт · 24 октября»: what a recording of [event] is called by default (spec 3.35, 3.36.9). */
    suspend fun recordTitleOf(event: SessionEvent): String
}

/** The words read from the resources of the app (`getString`): the language of the interface, on both platforms. */
object ResourceEventWords : EventWords {
    override suspend fun nameOf(name: EventName): String = when (name) {
        is EventName.Titled -> name.title
        is EventName.OfKind -> when (val kind = name.kind) {
            is KindRef.BuiltIn -> getString(builtInKindName(kind.kind))
            // a kind of one's own that is gone is «Другое»
            is KindRef.Custom -> name.ownName ?: getString(builtInKindName(BuiltInKind.OTHER))
        }
    }

    override suspend fun recordTitleOf(event: SessionEvent): String =
        getString(Res.string.session_take_title, nameOf(event.name), Formats.dayAndMonth(event.date))
}
