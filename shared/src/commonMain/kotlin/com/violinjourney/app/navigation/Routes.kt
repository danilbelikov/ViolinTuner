package com.violinjourney.app.navigation

import com.violinjourney.app.core.domain.events.BuiltInKind
import com.violinjourney.app.core.domain.journey.JourneyRoute
import com.violinjourney.app.core.domain.repertoire.PieceSection
import com.violinjourney.app.core.domain.repertoire.SectionRef
import com.violinjourney.app.feature.backup.RestoreViewModel
import com.violinjourney.app.feature.camera.CaptureViewModel
import com.violinjourney.app.feature.events.form.EventFormViewModel
import com.violinjourney.app.feature.events.screen.EventViewModel
import com.violinjourney.app.feature.home.HomeViewModel
import com.violinjourney.app.feature.journey.StopViewModel
import com.violinjourney.app.feature.repertoire.RepertoireViewModel
import com.violinjourney.app.feature.repertoire.SectionKeys
import com.violinjourney.app.feature.repertoire.form.PieceFormViewModel
import com.violinjourney.app.feature.repertoire.piece.PieceViewModel
import com.violinjourney.app.feature.repertoire.scale.ScaleFormViewModel
import com.violinjourney.app.feature.repertoire.stand.StandViewModel
import com.violinjourney.app.feature.session.SessionViewModel
import com.violinjourney.app.feature.sound.SoundViewModel
import kotlinx.datetime.LocalDate

/**
 * The routes of the screens above the tabs, one table for both graphs — `AppNavHost` on Android and `IosNavHost` on
 * iOS build their screens on the `*_PATTERN`s and go to them by the builders below; only the `navigate { popUpTo … }`
 * around them is each platform's. The tabs and the onboarding are [TopLevelDestination] and [ONBOARDING_ROUTE].
 *
 * The name of a route, up to its first '/' or '?', is also the key of `screen_open` ([com.violinjourney.app.core.analytics.screenKeyOf],
 * spec 5.27): renaming a route renames a screen in the statistics, on both platforms at once. `RoutesTest` keeps the list.
 */
object Routes {
    const val SESSION = "session"
    const val SESSION_PATTERN = "$SESSION/{${SessionViewModel.ARG_SESSION_ID}}"
    const val SOUND = "sound"
    const val SOUND_PATTERN = "$SOUND?${SoundViewModel.ARG_SESSION_ID}={${SoundViewModel.ARG_SESSION_ID}}"
    const val PIECE = "piece"
    const val PIECE_PATTERN = "$PIECE/{${PieceViewModel.ARG_PIECE_ID}}"
    const val CAPTURE = "capture"
    const val CAPTURE_PATTERN = "$CAPTURE/{${CaptureViewModel.ARG_PIECE_ID}}"
    const val STAND = "stand"
    const val STAND_PATTERN = "$STAND/{${StandViewModel.ARG_PIECE_ID}}?${StandViewModel.ARG_PAGE}={${StandViewModel.ARG_PAGE}}"
    const val PIECE_FORM = "pieceForm"
    const val PIECE_FORM_PATTERN = "$PIECE_FORM?${PieceFormViewModel.ARG_PIECE_ID}={${PieceFormViewModel.ARG_PIECE_ID}}" +
        "&${PieceFormViewModel.ARG_FOCUS_NOTES}={${PieceFormViewModel.ARG_FOCUS_NOTES}}" +
        "&${PieceFormViewModel.ARG_SECTION}={${PieceFormViewModel.ARG_SECTION}}"
    const val SCALE_FORM = "scaleForm"
    const val SCALE_FORM_PATTERN = "$SCALE_FORM?${ScaleFormViewModel.ARG_PIECE_ID}={${ScaleFormViewModel.ARG_PIECE_ID}}" +
        "&${ScaleFormViewModel.ARG_FOCUS_NOTES}={${ScaleFormViewModel.ARG_FOCUS_NOTES}}"
    const val SECTION = "section"
    const val SECTION_PATTERN = "$SECTION/{${RepertoireViewModel.ARG_SECTION}}"
    const val JOURNEY = "journey"
    const val JOURNEY_MAP = "journeyMap"
    const val JOURNEY_PASSPORT = "journeyPassport"
    const val JOURNEY_STOP = "journeyStop"
    const val JOURNEY_STOP_PATTERN = "$JOURNEY_STOP/{${StopViewModel.ARG_STOP_ID}}"
    const val HOME = "home"
    const val HOME_SHOP = "homeShop"
    const val HOME_SHOP_PATTERN = "$HOME_SHOP?${HomeViewModel.ARG_SLOT}={${HomeViewModel.ARG_SLOT}}"
    const val HOME_ARRANGE = "homeArrange"
    const val HOME_HOUSES = "homeHouses"
    const val SPLASH_AWAY = "splashAway"
    const val SPLASH_HOME = "splashHome"
    const val SETTINGS = "settings"
    const val BACKUP = "backup"
    const val RESTORE = "restore"
    const val RESTORE_PATTERN = "$RESTORE?${RestoreViewModel.ARG_URI}={${RestoreViewModel.ARG_URI}}"
    const val EVENT = "event"
    const val EVENT_PATTERN = "$EVENT/{${EventViewModel.ARG_EVENT_ID}}"
    const val EVENT_FORM = "eventForm"
    const val EVENT_FORM_PATTERN = "$EVENT_FORM?${EventFormViewModel.ARG_EVENT_ID}={${EventFormViewModel.ARG_EVENT_ID}}" +
        "&${EventFormViewModel.ARG_DATE}={${EventFormViewModel.ARG_DATE}}" +
        "&${EventFormViewModel.ARG_KIND}={${EventFormViewModel.ARG_KIND}}" +
        "&${EventFormViewModel.ARG_FOCUS_NOTES}={${EventFormViewModel.ARG_FOCUS_NOTES}}"

    /**
     * The date an event saved by its form lies on (plan D24): written into the saved state of the entry of «Занятия» before the form goes,
     * taken by «Занятия» when they come back — the sheet of that day rises, the calendar on its month.
     */
    const val RESULT_EVENT_DATE = "eventSavedDate"

    fun session(sessionId: Long): String = "$SESSION/$sessionId"

    /** [sessionId] null — the sound of all recordings. */
    fun sound(sessionId: Long?): String = "$SOUND?${SoundViewModel.ARG_SESSION_ID}=${sessionId ?: SoundViewModel.EVERYONE}"

    fun piece(pieceId: Long): String = "$PIECE/$pieceId"

    /** The screen of an event (spec 3.35, 3.36.9): from a row of the sheet of the day and of the reminder on «Занятия». */
    fun event(eventId: Long): String = "$EVENT/$eventId"

    /**
     * The form of an event (spec 3.35, 3.36.9): [eventId] null — a new one, on [date] (the day of a sheet; none — today) and of [kind]
     * («Добавить выступление»; none — the kind of the last event created); an edit at its notes when [focusNotes] («Добавить заметку»).
     */
    fun eventForm(eventId: Long? = null, date: LocalDate? = null, kind: BuiltInKind? = null, focusNotes: Boolean = false): String =
        "$EVENT_FORM?${EventFormViewModel.ARG_EVENT_ID}=${eventId ?: EventFormViewModel.NEW_EVENT}" +
            "&${EventFormViewModel.ARG_DATE}=${date?.toString().orEmpty()}" +
            "&${EventFormViewModel.ARG_KIND}=${kind?.name.orEmpty()}" +
            "&${EventFormViewModel.ARG_FOCUS_NOTES}=$focusNotes"

    fun capture(pieceId: Long): String = "$CAPTURE/$pieceId"

    /** The music stand of a piece at [pageIndex] (from zero). */
    fun stand(pieceId: Long, pageIndex: Int): String = "$STAND/$pieceId?${StandViewModel.ARG_PAGE}=$pageIndex"

    /** [pieceId] null — the form of a new element of [section]. */
    fun pieceForm(pieceId: Long?, focusNotes: Boolean = false, section: SectionRef = SectionRef.BuiltIn(PieceSection.PIECES)): String =
        "$PIECE_FORM?${PieceFormViewModel.ARG_PIECE_ID}=${pieceId ?: PieceFormViewModel.NEW_PIECE}" +
            "&${PieceFormViewModel.ARG_FOCUS_NOTES}=$focusNotes&${PieceFormViewModel.ARG_SECTION}=${SectionKeys.keyOf(section)}"

    /** [pieceId] null — the form of a new scale; [focusNotes] — at its notes («Добавить заметку» of the scale's screen). */
    fun scaleForm(pieceId: Long?, focusNotes: Boolean = false): String =
        "$SCALE_FORM?${ScaleFormViewModel.ARG_PIECE_ID}=${pieceId ?: ScaleFormViewModel.NEW_SCALE}&${ScaleFormViewModel.ARG_FOCUS_NOTES}=$focusNotes"

    fun section(section: SectionRef): String = "$SECTION/${SectionKeys.keyOf(section)}"

    /**
     * The form of a new element of [section] (spec 3.22, 3.36.4): «Гаммы» — the form of a scale, every other section — the form of a
     * piece with that section. One rule for «Что добавить?» of the tab and «Добавить …» of a section's list, on both platforms.
     */
    fun newElement(section: SectionRef): String =
        if (section == SectionRef.BuiltIn(PieceSection.SCALES)) scaleForm(null) else pieceForm(null, section = section)

    /**
     * The shop (spec 3.24); [slot] — the shop by place, opened from «в лавке N →» of «Обставить» (spec 3.36.7): only the things of that
     * place on its shelves. The place is the argument of the route and nothing more — it is not kept anywhere.
     */
    fun homeShop(slot: String? = null): String = if (slot == null) HOME_SHOP else "$HOME_SHOP?${HomeViewModel.ARG_SLOT}=${encodeQuery(slot)}"

    /** A stop of the journey; the home is a section of its own (spec 3.24), and the way into it is its title card (3.25). */
    fun stop(stopId: String): String = if (stopId == JourneyRoute.HOME) SPLASH_HOME else "$JOURNEY_STOP/$stopId"

    /** [uri] — the copy the system's picker came back with; blank — a restore on its way already is come back to. */
    fun restore(uri: String): String = if (uri.isBlank()) RESTORE else "$RESTORE?${RestoreViewModel.ARG_URI}=${encodeQuery(uri)}"

    /**
     * A value inside the query of a route: everything but ASCII letters, digits and `-_.~` percent-encoded, byte by byte
     * of its UTF-8 — no '&', '=', '?', '#' or '/' of an address can cut the route; navigation decodes it back whole.
     */
    internal fun encodeQuery(value: String): String = buildString {
        value.encodeToByteArray().forEach { byte ->
            val c = byte.toInt().toChar()
            if (byte >= 0 && (c.isLetterOrDigit() || c in UNRESERVED)) append(c) else append('%').append((byte.toInt() and 0xFF).toString(16).uppercase().padStart(2, '0'))
        }
    }

    private const val UNRESERVED = "-_.~"
}
