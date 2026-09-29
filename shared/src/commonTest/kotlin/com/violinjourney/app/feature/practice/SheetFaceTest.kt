package com.violinjourney.app.feature.practice

import com.violinjourney.app.core.domain.practice.PracticeConfig
import com.violinjourney.app.core.domain.practice.PracticeConfig.Companion.MS_PER_MINUTE
import com.violinjourney.app.core.domain.journey.TaktSources
import com.violinjourney.app.core.domain.practice.PracticeEntry
import com.violinjourney.app.core.domain.practice.PracticeRecap
import com.violinjourney.app.core.domain.practice.RecapRoad
import com.violinjourney.app.core.domain.progress.Profile
import com.violinjourney.app.core.domain.progress.Progress
import com.violinjourney.app.core.domain.progress.ProgressConfig
import com.violinjourney.app.core.domain.progress.Trophy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.YearMonth

/**
 * The one frame of the sheets of «Занятия» (spec 3.36.3): which face it shows for the state — the gift too, and not under «Занятие не
 * закончено» — what a swipe of each face means, and which faces have a way back to the one under them.
 */
class SheetFaceTest {
    private val config = PracticeConfig()
    private val today = LocalDate(2026, 9, 17)
    private val day = LocalDate(2026, 9, 16)

    private fun state(
        sheet: PracticeSheet?,
        sheetsAway: Boolean = false,
        selected: LocalDate? = null,
        trophies: List<Trophy> = listOf(Trophy(1, today, shown = true)),
    ): PracticeState = PracticeReducer.stateOf(
        entries = listOf(PracticeEntry(day, 0, 50 * MS_PER_MINUTE, manual = false)),
        sessions = emptyList(),
        runningSince = null,
        month = YearMonth(2026, 9),
        selectedDate = selected,
        sheet = sheet,
        today = today,
        zone = TimeZone.of("Europe/Moscow"),
        config = config,
        trophies = trophies,
        profile = Profile.EMPTY,
        avatarPath = null,
        progressConfig = ProgressConfig(),
        sheetsAway = sheetsAway,
    )

    private val summary = PracticeReducer.summarySheet(1_000, 47 * MS_PER_MINUTE, config)
    private val edit = PracticeReducer.editSheet(day, 50 * MS_PER_MINUTE, config)
    private val profile = PracticeSheet.Profile(nameDraft = "Аня", importingPhoto = false)

    /** A recap of 47 minutes: what it is does not matter here, only that it is one. */
    private val recap = PracticeRecap(
        durationMs = 47 * MS_PER_MINUTE,
        dayTotalMs = null,
        takts = 94,
        sources = TaktSources(notesInTune = 0, notesTakts = 0, minutes = 47, timeTakts = 94, pieces = 0, piecesTakts = 0),
        road = RecapRoad.NotStarted,
        streakDays = 1,
        streakExtended = true,
        levelBefore = Progress.levelOf(0, ProgressConfig()),
        levelAfter = Progress.levelOf(47 * MS_PER_MINUTE, ProgressConfig()),
    )

    /** The trophy of 1 h given and not seen yet: the gift of the state, when no sheet is open. */
    private val unseen = listOf(Trophy(1, today, shown = false))

    @Test
    fun `each sheet has its face and a record away hides them`() {
        assertNull(SheetFaces.of(state(null)))
        assertEquals(SheetFace.Summary(summary), SheetFaces.of(state(summary)))
        val dayState = state(PracticeSheet.Day(day), selected = day)
        assertEquals(SheetFace.Day(dayState.selected!!), SheetFaces.of(dayState))
        assertEquals(SheetFace.EditTime(edit), SheetFaces.of(state(edit, selected = day)))
        val path = state(PracticeSheet.Path)
        assertEquals(SheetFace.Path(path.header), SheetFaces.of(path))
        // a record opened from the sheet of the day is on screen: the frame steps aside, the model keeps the sheet
        assertNull(SheetFaces.of(state(PracticeSheet.Day(day), sheetsAway = true, selected = day)))
        // since stage 107 «Трофеи», «Имя и фото» and the recap are faces of the frame too
        val trophies = state(PracticeSheet.Trophies)
        assertEquals(SheetFace.Trophies(trophies.trophies, trophies.header.totalMs), SheetFaces.of(trophies))
        val named = state(profile)
        assertEquals(SheetFace.Profile(profile, named.header), SheetFaces.of(named))
        assertEquals(SheetFace.Recap(recap), SheetFaces.of(state(PracticeSheet.Recap(recap))))
    }

    @Test
    fun `the gift has a face only without a sheet and not under the prompt of the app`() {
        val free = state(null, trophies = unseen)
        val gift = free.gift
        assertNotNull(gift, "a trophy not seen, no sheet: the gift is due")
        assertEquals(SheetFace.Gift(gift), SheetFaces.of(free))
        // any sheet of «Занятия» holds it — the reducer offers none, the sheet is the face
        assertEquals(SheetFace.Path(state(PracticeSheet.Path, trophies = unseen).header), SheetFaces.of(state(PracticeSheet.Path, trophies = unseen)))
        // «Занятие не закончено» lies over it: the frame shows nothing until the prompt has gone
        assertNull(SheetFaces.of(free, appPromptShown = true))
        // the prompt does not hold the other faces: they are asked for on this screen
        assertEquals(SheetFace.Summary(summary), SheetFaces.of(state(summary), appPromptShown = true))
    }

    @Test
    fun `a swipe of each face says what it means`() {
        assertEquals(PracticeIntent.SummaryHidden, SheetFaces.hideIntent(SheetFace.Summary(summary)))
        val dayState = state(PracticeSheet.Day(day), selected = day)
        assertEquals(PracticeIntent.DayHidden, SheetFaces.hideIntent(SheetFace.Day(dayState.selected!!)))
        // «Время за день» — «Отмена»: the day is not changed, the sheet of the day comes back
        assertEquals(PracticeIntent.EditTimeCancelled, SheetFaces.hideIntent(SheetFace.EditTime(edit)))
        assertEquals(PracticeIntent.PathHidden, SheetFaces.hideIntent(SheetFace.Path(state(PracticeSheet.Path).header)))
        // «Имя и фото» — «Готово»: the name is stored, «Мой путь» comes back; «Трофеи» — «Мой путь» back
        assertEquals(PracticeIntent.ProfileClosed, SheetFaces.hideIntent(SheetFace.Profile(profile, state(profile).header)))
        assertEquals(PracticeIntent.TrophiesClosed, SheetFaces.hideIntent(SheetFace.Trophies(emptyList(), 0)))
        // the recap — «Готово»; the gift — «Спасибо» for its own trophy
        assertEquals(PracticeIntent.RecapClosed, SheetFaces.hideIntent(SheetFace.Recap(recap)))
        val gift = requireNotNull(state(null, trophies = unseen).gift)
        assertEquals(PracticeIntent.GiftAccepted(1), SheetFaces.hideIntent(SheetFace.Gift(gift)))
    }

    @Test
    fun `only a sheet over another has a way back`() {
        assertEquals(PracticeIntent.EditTimeCancelled, SheetFaces.backIntent(SheetFace.EditTime(edit)))
        // over «Мой путь»: «назад» gives it back in place
        assertEquals(PracticeIntent.ProfileClosed, SheetFaces.backIntent(SheetFace.Profile(profile, state(profile).header)))
        assertEquals(PracticeIntent.TrophiesClosed, SheetFaces.backIntent(SheetFace.Trophies(emptyList(), 0)))
        assertNull(SheetFaces.backIntent(SheetFace.Summary(summary)))
        assertNull(SheetFaces.backIntent(SheetFace.Day(state(PracticeSheet.Day(day), selected = day).selected!!)))
        assertNull(SheetFaces.backIntent(SheetFace.Path(state(PracticeSheet.Path).header)))
        // the recap and the gift have no parent to go back to: «назад» is «Готово» and «Спасибо»
        assertNull(SheetFaces.backIntent(SheetFace.Recap(recap)))
        assertNull(SheetFaces.backIntent(SheetFace.Gift(requireNotNull(state(null, trophies = unseen).gift))))
    }

    @Test
    fun `a face is its kind but each gift of a row is a face of its own`() {
        // a step of the stepper is the same face: its main button is not held and its scroll stays
        val stepped = PracticeReducer.summarySheet(1_000, 42 * MS_PER_MINUTE, config)
        assertEquals(SheetFaces.keyOf(SheetFace.Summary(summary)), SheetFaces.keyOf(SheetFace.Summary(stepped)))
        // the recap in the place of «Закончить занятие», the gift in the place of the recap — new faces
        assertNotEquals(SheetFaces.keyOf(SheetFace.Summary(summary)), SheetFaces.keyOf(SheetFace.Recap(recap)))
        val row = state(null, trophies = listOf(Trophy(1, today, shown = false), Trophy(10, today, shown = false)))
        val first = SheetFace.Gift(requireNotNull(row.gift))
        assertNotEquals(SheetFaces.keyOf(SheetFace.Recap(recap)), SheetFaces.keyOf(first))
        // «Колок» after «Канифоль»: the same kind, another face
        val second = SheetFace.Gift(requireNotNull(state(null, trophies = listOf(Trophy(1, today, shown = true), Trophy(10, today, shown = false))).gift))
        assertNotEquals(SheetFaces.keyOf(first), SheetFaces.keyOf(second))
        assertEquals(SheetFaces.keyOf(first), SheetFaces.keyOf(SheetFace.Gift(requireNotNull(row.gift))))
    }

    @Test
    fun `only the name and photo in a low window give their buttons to what scrolls over the keyboard`() {
        val named = SheetFace.Profile(profile, state(profile).header)
        assertTrue(SheetFaces.buttonsInContentOverKeyboard(named, lowWindow = true))
        // in portrait the keyboard leaves «Готово» over it room enough
        assertFalse(SheetFaces.buttonsInContentOverKeyboard(named, lowWindow = false))
        // nothing else is typed into: their buttons stay pinned
        assertFalse(SheetFaces.buttonsInContentOverKeyboard(SheetFace.Summary(summary), lowWindow = true))
        assertFalse(SheetFaces.buttonsInContentOverKeyboard(SheetFace.EditTime(edit), lowWindow = true))
        assertFalse(SheetFaces.buttonsInContentOverKeyboard(SheetFace.Recap(recap), lowWindow = true))
    }
}
