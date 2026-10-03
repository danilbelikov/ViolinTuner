package com.violinjourney.app.feature.events.form

import androidx.lifecycle.SavedStateHandle
import com.violinjourney.app.core.domain.events.BuiltInKind
import com.violinjourney.app.core.domain.events.KindRef
import com.violinjourney.app.core.domain.events.KindSign
import com.violinjourney.app.core.domain.events.Repeat
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth

/**
 * The draft of the form of an event and its sheet in the saved state (as `PieceFormSaved` of R4): Android may end the process while the
 * player is in another app, and the fields bring their text back by themselves (`rememberSaveable`) — the view model has to come back with
 * the same draft and the same sheet, or «Сохранить» would store what was there before and ✕ would close without asking. Written on the
 * player's own edits only, never while an edit is still being read. Plain values only: a bundle holds them. The sheet of a repeat is kept
 * as a mark: its question is reckoned again from the draft and the storage when the form is back. The sheet «Вид» a swipe hid keeps its
 * draft too (spec 3.36.9: «черновик листа живёт, пока открыта форма»; plan D33): for the player the form never closed.
 */
internal object EventFormSaved {
    /** What outlived the process: the draft, the sheet that was up over it, and the sheet «Вид» a swipe hid ([keptKind]). */
    class Kept(val draft: FormDraft, val sheet: FormSheet?, val scopeAsked: Boolean, val keptKind: KindDraft?)

    fun write(handle: SavedStateHandle, draft: FormDraft, sheet: FormSheet?, keptKind: FormSheet.Kind?) {
        handle[KIND] = keyOf(draft.kind)
        handle[DATE] = draft.date.toString()
        handle[START] = draft.startMinutes
        handle[DURATION] = draft.durationMinutes
        handle[REPEAT] = draft.repeat.name
        handle[UNTIL] = draft.until?.toString()
        handle[PLACE] = draft.place
        handle[TITLE] = draft.title
        handle[NOTES] = draft.notes
        handle[KEPT_START] = draft.keptStart
        handle[KEPT_DURATION] = draft.keptDuration
        handle[TIME_TOUCHED] = draft.timeTouched
        writeSheet(handle, sheet)
        writeKind(handle, KEPT_KIND, keptKind?.draft)
    }

    /** Null when nothing was written: a form that was never edited starts anew. */
    fun read(handle: SavedStateHandle): Kept? {
        val date = handle.get<String>(DATE)?.let(::dateOf) ?: return null
        val draft = FormDraft(
            kind = refOf(handle.get<String>(KIND)) ?: KindRef.OTHER,
            date = date,
            startMinutes = handle.get<Int>(START),
            durationMinutes = handle.get<Int>(DURATION),
            repeat = Repeat.entries.firstOrNull { it.name == handle.get<String>(REPEAT) } ?: Repeat.NONE,
            until = handle.get<String>(UNTIL)?.let(::dateOf),
            place = handle.get<String>(PLACE).orEmpty(),
            title = handle.get<String>(TITLE).orEmpty(),
            notes = handle.get<String>(NOTES).orEmpty(),
            keptStart = handle.get<Int>(KEPT_START),
            keptDuration = handle.get<Int>(KEPT_DURATION),
            timeTouched = handle.get<Boolean>(TIME_TOUCHED) ?: false,
        )
        return Kept(draft, readSheet(handle), scopeAsked = handle.get<String>(SHEET) == SHEET_SCOPE, keptKind = readKind(handle, KEPT_KIND))
    }

    private fun writeSheet(handle: SavedStateHandle, sheet: FormSheet?) {
        handle[SHEET] = when (sheet) {
            null -> null
            is FormSheet.Date -> SHEET_DATE
            is FormSheet.Time -> SHEET_TIME
            is FormSheet.Duration -> SHEET_DURATION
            is FormSheet.Until -> SHEET_UNTIL
            is FormSheet.Kind -> SHEET_KIND
            is FormSheet.Scope -> SHEET_SCOPE
        }
        handle[SHEET_MONTH] = when (sheet) {
            is FormSheet.Date -> sheet.month.toString()
            is FormSheet.Until -> sheet.month.toString()
            else -> null
        }
        handle[SHEET_DAY] = when (sheet) {
            is FormSheet.Date -> sheet.picked.toString()
            is FormSheet.Until -> sheet.picked?.toString()
            else -> null
        }
        handle[SHEET_MINUTES] = when (sheet) {
            is FormSheet.Time -> sheet.minutes
            is FormSheet.Duration -> sheet.minutes
            else -> null
        }
        handle[SHEET_ALL_DAY] = (sheet as? FormSheet.Time)?.allDay
        writeKind(handle, SHEET_KIND_PREFIX, (sheet as? FormSheet.Kind)?.draft)
    }

    /** The draft of a sheet «Вид» under the keys of [prefix]: its kind («b:LESSON», «c:12», none of a new one), name, colour and sign. */
    private fun writeKind(handle: SavedStateHandle, prefix: String, kind: KindDraft?) {
        handle[prefix + KIND_REF] = kind?.ref?.let(::keyOf)
        handle[prefix + KIND_NAME] = kind?.name
        handle[prefix + KIND_COLOR] = kind?.color
        handle[prefix + KIND_SIGN] = kind?.sign?.name
    }

    /** Null when no draft of «Вид» was written under [prefix]: a colour and a sign are always there in one. */
    private fun readKind(handle: SavedStateHandle, prefix: String): KindDraft? {
        val color = handle.get<Int>(prefix + KIND_COLOR) ?: return null
        val sign = KindSign.entries.firstOrNull { it.name == handle.get<String>(prefix + KIND_SIGN) } ?: return null
        return KindDraft(refOf(handle.get<String>(prefix + KIND_REF)), handle.get<String>(prefix + KIND_NAME).orEmpty(), color, sign)
    }

    private fun readSheet(handle: SavedStateHandle): FormSheet? {
        val month = handle.get<String>(SHEET_MONTH)?.let(::monthOf)
        val day = handle.get<String>(SHEET_DAY)?.let(::dateOf)
        val minutes = handle.get<Int>(SHEET_MINUTES)
        return when (handle.get<String>(SHEET)) {
            SHEET_DATE -> if (month != null && day != null) FormSheet.Date(month, day) else null
            SHEET_TIME -> minutes?.let { FormSheet.Time(it, handle.get<Boolean>(SHEET_ALL_DAY) ?: false) }
            SHEET_DURATION -> minutes?.let { FormSheet.Duration(it) }
            SHEET_UNTIL -> month?.let { FormSheet.Until(it, day) }
            SHEET_KIND -> readKind(handle, SHEET_KIND_PREFIX)?.let { FormSheet.Kind(it) }
            // reckoned anew once the edited event is read
            else -> null
        }
    }

    /** «b:LESSON» for a built-in kind, «c:12» for one of one's own. */
    private fun keyOf(ref: KindRef): String = when (ref) {
        is KindRef.BuiltIn -> BUILT_IN + ref.kind.name
        is KindRef.Custom -> CUSTOM + ref.id
    }

    private fun refOf(key: String?): KindRef? = when {
        key == null -> null
        key.startsWith(BUILT_IN) -> BuiltInKind.entries.firstOrNull { it.name == key.removePrefix(BUILT_IN) }?.let { KindRef.BuiltIn(it) }
        key.startsWith(CUSTOM) -> key.removePrefix(CUSTOM).toLongOrNull()?.let { KindRef.Custom(it) }
        else -> null
    }

    // written here in ISO and read back by the same build: a saved state does not outlive an update of the app
    private fun dateOf(text: String): LocalDate = LocalDate.parse(text)

    private fun monthOf(text: String): YearMonth = YearMonth.parse(text)

    // None of them is a navigation argument of the form.
    private const val KIND = "eventForm.kind"
    private const val DATE = "eventForm.date"
    private const val START = "eventForm.start"
    private const val DURATION = "eventForm.duration"
    private const val REPEAT = "eventForm.repeat"
    private const val UNTIL = "eventForm.until"
    private const val PLACE = "eventForm.place"
    private const val TITLE = "eventForm.title"
    private const val NOTES = "eventForm.notes"
    private const val KEPT_START = "eventForm.keptStart"
    private const val KEPT_DURATION = "eventForm.keptDuration"
    private const val TIME_TOUCHED = "eventForm.timeTouched"
    private const val SHEET = "eventForm.sheet"
    private const val SHEET_MONTH = "eventForm.sheet.month"
    private const val SHEET_DAY = "eventForm.sheet.day"
    private const val SHEET_MINUTES = "eventForm.sheet.minutes"
    private const val SHEET_ALL_DAY = "eventForm.sheet.allDay"

    // the draft of «Вид»: of the sheet up — and of the one a swipe hid
    private const val SHEET_KIND_PREFIX = "eventForm.sheet."
    private const val KEPT_KIND = "eventForm.keptKind."
    private const val KIND_REF = "kindRef"
    private const val KIND_NAME = "kindName"
    private const val KIND_COLOR = "kindColor"
    private const val KIND_SIGN = "kindSign"
    private const val SHEET_DATE = "date"
    private const val SHEET_TIME = "time"
    private const val SHEET_DURATION = "duration"
    private const val SHEET_UNTIL = "until"
    private const val SHEET_KIND = "kind"
    private const val SHEET_SCOPE = "scope"
    private const val BUILT_IN = "b:"
    private const val CUSTOM = "c:"
}
