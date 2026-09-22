package com.example.violintuner.feature.live.block

import com.example.violintuner.core.domain.practice.BlockRules
import com.example.violintuner.core.domain.practice.PieceBlock
import com.example.violintuner.core.domain.practice.PracticeBlocks
import com.example.violintuner.core.domain.practice.PracticeConfig
import com.example.violintuner.core.domain.practice.RunningPractice
import com.example.violintuner.core.domain.practice.SavedBlock
import com.example.violintuner.core.domain.repertoire.Piece
import com.example.violintuner.core.domain.repertoire.PieceGroup
import com.example.violintuner.core.domain.repertoire.PieceSection
import com.example.violintuner.core.domain.repertoire.PieceStatus
import com.example.violintuner.core.domain.repertoire.SectionRef
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The bookmark and «Что играем» (spec 3.28, handoff 30b, 30e). */
class BlockReducerTest {
    private val config = PracticeConfig()
    private val zone: ZoneId = ZoneId.of("Europe/Moscow")
    private val min = 60_000L
    // 2026-09-22 18:00 Moscow: the practice began at 17:26
    private val practiceStart = Instant.parse("2026-09-22T14:26:00Z").toEpochMilli()
    private val running = RunningPractice(practiceStart, lastSoundEpochMs = null)
    private val today = LocalDate.of(2026, 9, 22)
    private fun at(minutes: Long) = practiceStart + minutes * min

    private fun piece(id: Long, title: String, section: PieceSection, composer: String = "", updated: Long = id, groupId: Long? = null) =
        Piece(id, title, composer, key = null, tempoBpm = null, status = PieceStatus.LEARNING, notes = "", createdAtEpochMs = 0, updatedAtEpochMs = updated, section = section, groupId = groupId)

    private val pieces = listOf(
        piece(1, "Менуэт соль мажор", PieceSection.PIECES, composer = "И. С. Бах", updated = 100),
        piece(2, "Концерт ля минор, I ч.", PieceSection.PIECES, composer = "А. Вивальди", updated = 200),
        piece(3, "G-dur · 3 октавы", PieceSection.SCALES),
        piece(4, "Кайзер № 3", PieceSection.ETUDES, composer = "Г. Кайзер"),
        piece(6, "Терции", PieceSection.PIECES, groupId = 1),
    )
    private val groups = listOf(PieceGroup(1, "Двойные ноты", 0))

    /** Handoff 30e1: the scale and Kaiser done today, the minuet stopped at seven minutes, the concerto running. */
    private val saved = listOf(
        SavedBlock(3, today, at(-120), 10 * min, 10 * min, done = true, paid = true),
        SavedBlock(1, today.minusDays(1), at(-2000), 20 * min, 20 * min, done = true, paid = true),
    )
    private val blocks = PracticeBlocks(
        practiceStartedAtEpochMs = practiceStart,
        current = PieceBlock(2, at(21), 20 * min),
        finished = listOf(PieceBlock(4, at(0), 15 * min, endedAtEpochMs = at(15)), PieceBlock(1, at(15), 10 * min, endedAtEpochMs = at(22) - 60_000)),
    )

    private fun state(ui: BlockReducer.Ui = BlockReducer.Ui(sheetOpen = true), now: Long = at(34), running: RunningPractice? = this.running, current: PracticeBlocks? = blocks) =
        BlockReducer.stateOf(running, current, saved, pieces, groups, sessions = emptyList(), ui = ui, nowEpochMs = now, zone = zone, config = config)

    @Test
    fun `without a practice the bookmark says «Репертуар» and a tap offers a practice`() {
        val state = state(running = null)
        assertEquals(Bookmark.Entry, state.bookmark)
        assertEquals(BlockSheet.Offer, state.sheet)
        assertNull(state(running = null, ui = BlockReducer.Ui()).sheet)
    }

    @Test
    fun `the choice lists the sections in their order, the latest activity first, a composer only where there is one`() {
        val picker = state().sheet as BlockSheet.Picker
        assertEquals(
            listOf(SectionRef.BuiltIn(PieceSection.PIECES), SectionRef.BuiltIn(PieceSection.SCALES), SectionRef.BuiltIn(PieceSection.ETUDES), SectionRef.Custom(1)),
            picker.sections.map { it.ref },
        )
        assertEquals(listOf(2L, 1L), picker.sections[0].pieces.map { it.id })
        assertEquals("А. Вивальди", picker.sections[0].pieces[0].composer)
        assertNull(picker.sections[1].pieces.single().composer)
        assertEquals("Двойные ноты", picker.sections[3].name)
    }

    @Test
    fun `today is marked by shape - done with its minutes, played short of the goal, running - and counted in the heads`() {
        val picker = state().sheet as BlockSheet.Picker
        val marks = picker.sections.flatMap { it.pieces }.associate { it.id to it.today }
        assertEquals(TodayMark.Running, marks[2L])
        assertEquals(TodayMark.Played(6 * min), marks[1L])
        assertEquals(TodayMark.Done(10 * min), marks[3L])
        assertEquals(TodayMark.Done(15 * min), marks[4L])
        assertEquals(TodayMark.None, marks[6L])
        assertEquals(listOf(0, 1, 1, 0), picker.sections.map { it.doneToday })
        assertEquals(NowLine("Концерт ля минор, I ч.", minutesLeft = 7), picker.now)
    }

    @Test
    fun `the bookmark follows the clock - running, done at its goal, «Репертуар» after a stop or without its element`() {
        assertEquals(Bookmark.Running("Концерт ля минор, I ч.", minutesLeft = 7, progress = 0.65f), state(now = at(34)).bookmark)
        assertEquals(Bookmark.Done("Концерт ля минор, I ч."), state(now = at(41)).bookmark)
        assertNull((state(now = at(41)).sheet as BlockSheet.Picker).now)
        assertEquals(Bookmark.Entry, state(current = BlockRules.stopped(blocks, practiceStart, at(30))).bookmark)
        val gone = BlockReducer.stateOf(running, blocks, saved, pieces.filter { it.id != 2L }, groups, emptyList(), BlockReducer.Ui(), at(34), zone, config)
        assertEquals(Bookmark.Entry, gone.bookmark)
    }

    @Test
    fun `blocks of another practice are not this one's`() {
        val other = RunningPractice(practiceStart + 3_600_000, lastSoundEpochMs = null)
        assertEquals(Bookmark.Entry, state(running = other).bookmark)
    }

    @Test
    fun `a pick brings the goal, the running element is no pick`() {
        val picker = state(ui = BlockReducer.Ui(sheetOpen = true, selectedId = 4, goalMinutes = 15)).sheet as BlockSheet.Picker
        assertEquals(4L, picker.selectedId)
        assertEquals("Кайзер № 3", picker.selectedTitle)
        assertEquals(15, picker.goalMinutes)
        assertEquals(listOf(5, 10, 15, 20, 30), picker.quickGoals)
        val running = state(ui = BlockReducer.Ui(sheetOpen = true, selectedId = 2, goalMinutes = 15)).sheet as BlockSheet.Picker
        assertNull(running.selectedId)
        val edge = state(ui = BlockReducer.Ui(sheetOpen = true, selectedId = 4, goalMinutes = 60)).sheet as BlockSheet.Picker
        assertEquals(false, edge.canGoalUp)
        assertEquals(true, edge.canGoalDown)
    }

    @Test
    fun `an empty repertoire leaves the choice empty`() {
        val empty = BlockReducer.stateOf(running, null, emptyList(), emptyList(), emptyList(), emptyList(), BlockReducer.Ui(sheetOpen = true), at(1), zone, config)
        assertEquals(emptyList<PickerSection>(), (empty.sheet as BlockSheet.Picker).sections)
    }
}
