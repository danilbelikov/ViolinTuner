package com.violinjourney.app.feature.live.block

import com.violinjourney.app.core.domain.practice.BlockRules
import com.violinjourney.app.core.domain.practice.PieceBlock
import com.violinjourney.app.core.domain.practice.PracticeBlocks
import com.violinjourney.app.core.domain.practice.PracticeConfig
import com.violinjourney.app.core.domain.practice.RunningPractice
import com.violinjourney.app.core.domain.practice.SavedBlock
import com.violinjourney.app.core.domain.repertoire.Piece
import com.violinjourney.app.core.domain.repertoire.PieceGroup
import com.violinjourney.app.core.domain.repertoire.PieceSection
import com.violinjourney.app.core.domain.repertoire.PieceStats
import com.violinjourney.app.core.domain.repertoire.PieceStatus
import com.violinjourney.app.core.domain.repertoire.SectionRef
import com.violinjourney.app.core.domain.session.SessionSummary
import kotlin.random.Random
import kotlin.time.Instant
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.Test

/** The bookmark and «Что играем» (spec 3.28, handoff 30b, 30e). */
class BlockReducerTest {
    private val config = PracticeConfig()
    private val zone: TimeZone = TimeZone.of("Europe/Moscow")
    private val min = 60_000L
    // 2026-09-22 18:00 Moscow: the practice began at 17:26
    private val practiceStart = Instant.parse("2026-09-22T14:26:00Z").toEpochMilliseconds()
    private val running = RunningPractice(practiceStart, lastSoundEpochMs = null)
    private val today = LocalDate(2026, 9, 22)
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

    private fun session(id: Long, pieceId: Long?, startedAt: Long) = SessionSummary(
        id = id, title = null, startedAtEpochMs = startedAt, durationMs = 60_000, a4Hz = 440.0, toleranceCents = 8.0, nearCents = 20.0,
        scorePercent = 80, nearPercent = 10, offPercent = 10, maeCents = 5.0, biasCents = 0.0, previewZones = emptyList(), audioPath = null,
        pieceId = pieceId,
    )

    /** Handoff 30e1: the scale and Kaiser done today, the minuet stopped at seven minutes, the concerto running. */
    private val saved = listOf(
        SavedBlock(3, today, at(-120), 10 * min, 10 * min, done = true, paid = true),
        SavedBlock(1, today.minus(1, DateTimeUnit.DAY), at(-2000), 20 * min, 20 * min, done = true, paid = true),
    )
    private val blocks = PracticeBlocks(
        practiceStartedAtEpochMs = practiceStart,
        current = PieceBlock(2, at(21), 20 * min),
        finished = listOf(PieceBlock(4, at(0), 15 * min, endedAtEpochMs = at(15)), PieceBlock(1, at(15), 10 * min, endedAtEpochMs = at(22) - 60_000)),
    )

    private fun state(ui: BlockReducer.Ui = BlockReducer.Ui(sheetOpen = true), now: Long = at(34), running: RunningPractice? = this.running, current: PracticeBlocks? = blocks) =
        BlockReducer.stateOf(running, current, saved, BlockReducer.shelfOf(pieces, groups, sessions = emptyList()), ui = ui, nowEpochMs = now, zone = { zone }, config = config)

    @Test
    fun `without a practice the bookmark says «Репертуар» and a tap offers a practice`() {
        val state = state(running = null)
        assertEquals(Bookmark.Entry, state.bookmark)
        assertEquals(BlockSheet.Offer, state.sheet)
        assertNull(state(running = null, ui = BlockReducer.Ui()).sheet)
    }

    @Test
    fun `the choice lists the sections in their order — the latest activity first — a composer only where there is one`() {
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
    fun `today is marked by shape - done with its minutes — played short of the goal — running - and counted in the heads`() {
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
    fun `the bookmark follows the clock - running — done at its goal — «Репертуар» after a stop or without its element`() {
        assertEquals(Bookmark.Running("Концерт ля минор, I ч.", minutesLeft = 7, progress = 0.65f), state(now = at(34)).bookmark)
        assertEquals(Bookmark.Done("Концерт ля минор, I ч."), state(now = at(41)).bookmark)
        assertNull((state(now = at(41)).sheet as BlockSheet.Picker).now)
        assertEquals(Bookmark.Entry, state(current = BlockRules.stopped(blocks, practiceStart, at(30))).bookmark)
        val gone = BlockReducer.stateOf(running, blocks, saved, BlockReducer.shelfOf(pieces.filter { it.id != 2L }, groups, emptyList()), BlockReducer.Ui(), at(34), { zone }, config)
        assertEquals(Bookmark.Entry, gone.bookmark)
    }

    @Test
    fun `blocks of another practice are not this one's`() {
        val other = RunningPractice(practiceStart + 3_600_000, lastSoundEpochMs = null)
        assertEquals(Bookmark.Entry, state(running = other).bookmark)
    }

    @Test
    fun `a pick brings the goal — the running element is no pick`() {
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
    fun `the time of the practice in the header is read at the same moment as the line of the block`() {
        // at 34 minutes: «занятие 34:00» over «ещё 7 мин» of the concerto that began at 21 with a goal of 20
        val picker = state(now = at(34)).sheet as BlockSheet.Picker
        assertEquals(34 * min, picker.practiceMs)
        assertEquals(7, picker.now?.minutesLeft)
        // a second later both have moved with the one clock
        val later = state(now = at(34) + 1_000).sheet as BlockSheet.Picker
        assertEquals(34 * min + 1_000, later.practiceMs)
        // an empty repertoire has the time of the practice all the same (spec 3.36.6: «занятие 0:42»)
        val empty = BlockReducer.stateOf(running, null, emptyList(), BlockReducer.Shelf.EMPTY, BlockReducer.Ui(sheetOpen = true), practiceStart + 42_000, { zone }, config)
        assertEquals(42_000L, (empty.sheet as BlockSheet.Picker).practiceMs)
    }

    @Test
    fun `the bounds over the chips are the bounds of a goal in the config`() {
        val picker = state().sheet as BlockSheet.Picker
        assertEquals(5, picker.goalMinMinutes)
        assertEquals(60, picker.goalMaxMinutes)
        val other = PracticeConfig(blockGoalMinMinutes = 10, blockGoalMaxMinutes = 90)
        val wide = BlockReducer.stateOf(running, blocks, saved, BlockReducer.shelfOf(pieces, groups, emptyList()), BlockReducer.Ui(sheetOpen = true), at(34), { zone }, other)
        val bounds = (wide.sheet as BlockSheet.Picker).let { it.goalMinMinutes to it.goalMaxMinutes }
        assertEquals(10 to 90, bounds)
    }

    @Test
    fun `an empty repertoire leaves the choice empty`() {
        val empty = BlockReducer.stateOf(running, null, emptyList(), BlockReducer.Shelf.EMPTY, BlockReducer.Ui(sheetOpen = true), at(1), { zone }, config)
        assertEquals(emptyList<PickerSection>(), (empty.sheet as BlockSheet.Picker).sections)
    }

    @Test
    fun `the shelf orders each section as the takes of each piece would`() {
        val random = Random(3)
        repeat(20) {
            val many = List(30) { index ->
                piece(index + 10L, "p$index", PieceSection.entries[random.nextInt(PieceSection.entries.size)], updated = random.nextLong(0, 50))
            }
            val takes = List(200) { index ->
                session(id = index + 1L, pieceId = many[random.nextInt(many.size)].id.takeIf { random.nextInt(5) > 0 }, startedAt = random.nextLong(0, 100))
            }
            val shelf = BlockReducer.shelfOf(many, emptyList(), takes)
            for (section in shelf.sections) {
                val expected = section.pieces.sortedWith(
                    compareByDescending<Piece> { PieceStats.lastActivity(it, PieceStats.takesOf(it.id, takes)) }.thenByDescending { it.id },
                )
                assertEquals(expected.map { it.id }, section.pieces.map { it.id })
            }
            assertEquals(many.size, shelf.sections.sumOf { it.pieces.size })
        }
    }

    @Test
    fun `the ticker moves the marks and never the order`() {
        val takes = listOf(session(id = 1, pieceId = 1, startedAt = at(-5)))
        fun picker(now: Long) = BlockReducer.stateOf(
            running, blocks, saved, BlockReducer.shelfOf(pieces, groups, takes), BlockReducer.Ui(sheetOpen = true), now, { zone }, config,
        ).sheet as BlockSheet.Picker
        val early = picker(at(34))
        val late = picker(at(41))
        assertEquals(listOf(1L, 2L), early.sections[0].pieces.map { it.id }, "a take today puts the minuet first")
        assertEquals(early.sections.map { section -> section.pieces.map { it.id } }, late.sections.map { section -> section.pieces.map { it.id } })
        assertEquals(TodayMark.Running, early.sections[0].pieces[1].today)
        assertEquals(TodayMark.Done(20 * min), late.sections[0].pieces[1].today)
    }

    @Test
    fun `the time zone is asked only while the choice is open`() {
        var asked = 0
        BlockReducer.stateOf(running, blocks, saved, BlockReducer.shelfOf(pieces, groups, emptyList()), BlockReducer.Ui(), at(34), { asked++; zone }, config)
        assertEquals(0, asked)
        BlockReducer.stateOf(running, blocks, saved, BlockReducer.shelfOf(pieces, groups, emptyList()), BlockReducer.Ui(sheetOpen = true), at(34), { asked++; zone }, config)
        assertEquals(1, asked)
    }
}
