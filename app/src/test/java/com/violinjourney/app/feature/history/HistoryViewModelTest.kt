package com.violinjourney.app.feature.history

import com.violinjourney.app.core.domain.IntonationConfig
import com.violinjourney.app.core.domain.backing.BackingOutput
import com.violinjourney.app.core.domain.backing.FakeBackingRepository
import com.violinjourney.app.core.domain.backing.TakeBacking
import com.violinjourney.app.core.domain.events.BuiltInKind
import com.violinjourney.app.core.domain.events.FakeEventRepository
import com.violinjourney.app.core.domain.events.KindRef
import com.violinjourney.app.core.domain.events.SessionEvent
import com.violinjourney.app.core.domain.repertoire.FakeRepertoireRepository
import com.violinjourney.app.core.domain.repertoire.PieceDraft
import com.violinjourney.app.core.domain.session.FakeSessionRepository
import com.violinjourney.app.core.domain.session.NewSession
import com.violinjourney.app.core.domain.session.SessionAnalyzer
import com.violinjourney.app.core.domain.session.SessionRepository
import com.violinjourney.app.core.domain.session.SessionSample
import com.violinjourney.app.core.time.FixedWallClock
import com.violinjourney.app.core.time.WallClock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModelTest {
    private val repository = FakeSessionRepository()
    private val repertoire = FakeRepertoireRepository()
    private val config = IntonationConfig()
    private val now = Instant.parse("2026-09-17T09:00:00Z")
    /**
     * Counts the reads of the time: the list is built with one read of "today" (HistoryViewModel.listed) and nothing else
     * of the view model asks the clock, so the reads count the builds. A StateFlow drops a state equal to the one it has:
     * comparing the cards could not tell a list built again from one left alone.
     */
    private class CountingClock(private val base: WallClock) : WallClock by base {
        var reads = 0
        override fun instant(): Instant = base.instant().also { reads++ }
    }

    private val clock = CountingClock(FixedWallClock(now, TimeZone.of("Europe/Moscow")))

    /** Answers for the files it is given, and counts how often it is asked. */
    private class Files(private val present: Map<String, Long> = emptyMap()) : com.violinjourney.app.core.audio.recording.SessionAudioFiles {
        var asked = 0
        override fun newFile(): java.io.File = error("not used")
        override fun existing(name: String): java.io.File? {
            asked++
            return present[name]?.let { size ->
                java.io.File.createTempFile("history", ".mp4").apply {
                    deleteOnExit()
                    writeBytes(ByteArray(size.toInt()))
                }
            }
        }
        override fun delete(name: String) = Unit
        override fun deleteOrphans(referenced: Set<String>, nowEpochMs: Long, minAgeMs: Long) = Unit
    }

    private var files = Files()
    private val backings = FakeBackingRepository()
    private val events = FakeEventRepository()

    /** What the view model asked to delete, call by call: the picked ones and one card of «Удалить…» go the same way. */
    private val deletes = mutableListOf<List<Long>>()
    private val watched = object : SessionRepository by repository {
        override suspend fun delete(ids: Collection<Long>) {
            deletes += ids.toList()
            repository.delete(ids)
        }
    }

    @Before
    fun setUp() = Dispatchers.setMain(StandardTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private suspend fun save(daysAgo: Long, cents: Double = 1.0, pieceId: Long? = null, videoPath: String? = null, eventId: Long? = null): Long {
        val samples = List(60) { SessionSample(69, cents) }
        val analysis = SessionAnalyzer.analyze(samples, config)
        return repository.save(
            NewSession(
                startedAtEpochMs = (now - (daysAgo * 86_400).seconds).toEpochMilliseconds(), durationMs = 3_000, config = config,
                samples = samples, metrics = analysis.metrics!!,
                previewZones = SessionAnalyzer.previewZones(analysis.segments, config), audioPath = null, pieceId = pieceId,
                videoPath = videoPath, eventId = eventId,
            ),
        )
    }

    private fun TestScope.viewModel(): HistoryViewModel {
        val viewModel = HistoryViewModel(watched, repertoire, config, clock, files, backings, events, background = StandardTestDispatcher(testScheduler))
        backgroundScope.launch { viewModel.state.collect {} }
        return viewModel
    }

    @Test
    fun `starts loading, then shows what is stored`() = runTest {
        save(daysAgo = 0)
        save(daysAgo = 10, cents = 30.0)
        val viewModel = viewModel()
        assertTrue(viewModel.state.value.loading)
        runCurrent()
        val state = viewModel.state.value
        assertEquals(2, state.totalCount)
        assertEquals(listOf(LocalDate(2026, 9, 17), LocalDate(2026, 9, 7)), state.cards.map { it.date })
        assertEquals(1, state.days.last().count)
        assertEquals(2, state.days.sumOf { it.count })
    }

    @Test
    fun `a recording of an event carries its event, follows it when it is renamed, and is no recording of Live`() = runTest {
        // spec 3.35, 3.36.9: named by the event until it is given a name of its own; «С Live» — without an event
        val concert = SessionEvent(4, "Осенний концерт", LocalDate(2026, 9, 17), KindRef.BuiltIn(BuiltInKind.PERFORMANCE), null)
        events.recordEvents.value = mapOf(4L to concert)
        val recording = save(daysAgo = 0, eventId = 4)
        val free = save(daysAgo = 1)
        val viewModel = viewModel()
        runCurrent()
        assertEquals(concert, viewModel.state.value.cards.single { it.id == recording }.event)
        assertNull(viewModel.state.value.cards.single { it.id == free }.event)

        events.recordEvents.value = mapOf(4L to concert.copy(title = "Академический концерт"))
        runCurrent()
        assertEquals("Академический концерт", viewModel.state.value.cards.single { it.id == recording }.event?.title)

        viewModel.onIntent(HistoryIntent.FilterSelected(HistoryFilter.LIVE))
        runCurrent()
        assertEquals(listOf(free), viewModel.state.value.cards.map { it.id })
    }

    @Test
    fun `filter narrows the cards only`() = runTest {
        val take = save(daysAgo = 0, pieceId = 7)
        save(daysAgo = 10)
        val viewModel = viewModel()
        viewModel.onIntent(HistoryIntent.FilterSelected(HistoryFilter.TAKES))
        runCurrent()
        assertEquals(HistoryFilter.TAKES, viewModel.state.value.filter)
        assertEquals(listOf(take), viewModel.state.value.cards.map { it.id })
        assertEquals(2, viewModel.state.value.totalCount)
        assertEquals(2, viewModel.state.value.stripTotal)
    }

    @Test
    fun `new and deleted sessions show up by themselves`() = runTest {
        val viewModel = viewModel()
        runCurrent()
        assertEquals(0, viewModel.state.value.totalCount)

        val id = save(daysAgo = 0)
        runCurrent()
        assertEquals(1, viewModel.state.value.totalCount)

        repository.delete(id)
        runCurrent()
        assertEquals(0, viewModel.state.value.totalCount)
    }

    @Test
    fun `tapping a card opens the session`() = runTest {
        val viewModel = viewModel()
        viewModel.onIntent(HistoryIntent.SessionClicked(42))
        assertEquals(HistoryEffect.OpenSession(42), viewModel.effects.first())
    }

    private fun HistoryViewModel.select(intent: SelectionIntent) = onIntent(HistoryIntent.Select(intent))

    @Test
    fun `picking cards lays the marks over the list instead of building it again`() = runTest {
        val first = save(daysAgo = 0)
        val video = save(daysAgo = 1)
        repository.sessions.value = repository.sessions.value.map { if (it.id == video) it.copy(videoPath = "take.mp4") else it }
        files = Files(present = mapOf("take.mp4" to 1_234))
        val viewModel = viewModel()
        runCurrent()
        val cards = viewModel.state.value.cards
        assertEquals(1_234L, cards.single { it.id == video }.videoBytes)
        val asked = files.asked
        val builds = clock.reads

        viewModel.select(SelectionIntent.CardLongPressed(first))
        viewModel.onIntent(HistoryIntent.SessionClicked(video))
        viewModel.onIntent(HistoryIntent.SessionClicked(first))
        viewModel.select(SelectionIntent.SelectAllClicked)
        runCurrent()

        assertEquals(setOf(first, video), viewModel.state.value.selection.ids)
        assertSame("the cards are the ones built before the picking", cards, viewModel.state.value.cards)
        assertEquals("the list was not built again", builds, clock.reads)
        assertEquals("no file was asked about again", asked, files.asked)
    }

    @Test
    fun `a take made under a backing says so on its card`() = runTest {
        val pieceId = repertoire.add(PieceDraft(title = "Концерт"), nowEpochMs = 1)
        val under = save(daysAgo = 0, pieceId = pieceId)
        val plain = save(daysAgo = 1, pieceId = pieceId)
        val viewModel = viewModel()
        runCurrent()
        assertTrue(viewModel.state.value.cards.none { it.underBacking })

        backings.saveTake(
            TakeBacking(
                sessionId = under, backingId = 1, offsetMs = 0, recordedOffsetMs = 0, gainDb = 0f, playedMs = 1_000,
                output = BackingOutput.WIRED, deviceName = null,
            ),
        )
        runCurrent()
        assertEquals(mapOf(under to true, plain to false), viewModel.state.value.cards.associate { it.id to it.underBacking })

        // the shift dragged on «Звук» changes nothing the list shows: it is not built again
        val builds = clock.reads
        backings.setTakeMix(under, offsetMs = 120, gainDb = -3f)
        runCurrent()
        assertEquals("a new shift of a take builds no list", builds, clock.reads)
    }

    @Test
    fun `a long press opens the selection and taps pick instead of opening`() = runTest {
        val first = save(daysAgo = 0)
        val second = save(daysAgo = 1)
        val viewModel = viewModel()
        runCurrent()

        viewModel.select(SelectionIntent.CardLongPressed(first))
        viewModel.onIntent(HistoryIntent.SessionClicked(second))
        runCurrent()

        assertEquals(Selection(active = true, ids = setOf(first, second)), viewModel.state.value.selection)
        assertTrue(viewModel.state.value.allSelected)
        // the tap inside the mode opened nothing: the first session to open is the one tapped after it
        viewModel.select(SelectionIntent.Closed)
        viewModel.onIntent(HistoryIntent.SessionClicked(first))
        assertEquals(HistoryEffect.OpenSession(first), viewModel.effects.first())
    }

    @Test
    fun `select all takes only what the filter shows`() = runTest {
        val recent = save(daysAgo = 0)
        save(daysAgo = 10, pieceId = 7)
        save(daysAgo = 11, videoPath = "video.mp4")
        val viewModel = viewModel()
        viewModel.onIntent(HistoryIntent.FilterSelected(HistoryFilter.LIVE))
        runCurrent()

        viewModel.select(SelectionIntent.SelectClicked)
        viewModel.select(SelectionIntent.SelectAllClicked)
        runCurrent()

        assertEquals(setOf(recent), viewModel.state.value.selection.ids)
    }

    /** Spec 3.18; the switch «Записи | Репертуар» that was dimmed with the filter is gone (spec 3.36.1). */
    @Test
    fun `the filter stays put while picking`() = runTest {
        save(daysAgo = 0)
        val viewModel = viewModel()
        runCurrent()
        viewModel.select(SelectionIntent.SelectClicked)

        viewModel.onIntent(HistoryIntent.FilterSelected(HistoryFilter.VIDEO))
        runCurrent()

        assertEquals(HistoryFilter.ALL, viewModel.state.value.filter)
    }

    @Test
    fun `confirming deletes the picked ones and closes the mode`() = runTest {
        val first = save(daysAgo = 0)
        val kept = save(daysAgo = 1)
        val third = save(daysAgo = 2)
        val viewModel = viewModel()
        runCurrent()

        viewModel.select(SelectionIntent.CardLongPressed(first))
        viewModel.onIntent(HistoryIntent.SessionClicked(third))
        viewModel.select(SelectionIntent.DeleteClicked)
        runCurrent()
        assertTrue(viewModel.state.value.selection.confirming)

        viewModel.select(SelectionIntent.DeleteConfirmed)
        runCurrent()

        assertEquals(listOf(kept), viewModel.state.value.cards.map { it.id })
        assertEquals(Selection(), viewModel.state.value.selection)
    }

    @Test
    fun `dismissing the dialog and closing the mode delete nothing`() = runTest {
        val id = save(daysAgo = 0)
        val viewModel = viewModel()
        runCurrent()

        viewModel.select(SelectionIntent.CardLongPressed(id))
        viewModel.select(SelectionIntent.DeleteClicked)
        viewModel.select(SelectionIntent.DeleteDismissed)
        viewModel.select(SelectionIntent.Closed)
        runCurrent()

        assertEquals(1, viewModel.state.value.totalCount)
        assertEquals(Selection(), viewModel.state.value.selection)
    }

    @Test
    fun `a picked session deleted elsewhere drops out of the selection`() = runTest {
        val first = save(daysAgo = 0)
        val second = save(daysAgo = 1)
        val viewModel = viewModel()
        runCurrent()
        viewModel.select(SelectionIntent.CardLongPressed(first))
        viewModel.onIntent(HistoryIntent.SessionClicked(second))

        repository.delete(first)
        runCurrent()

        assertEquals(setOf(second), viewModel.state.value.selection.ids)
    }

    /** Spec 3.36.5: «Удалить…» of a card asks the question of the recording's screen and deletes by the rules of picking. */
    @Test
    fun `«Удалить…» of a card asks about that one alone and deletes it in one call`() = runTest {
        val kept = save(daysAgo = 0)
        val gone = save(daysAgo = 1)
        val viewModel = viewModel()
        runCurrent()

        viewModel.select(SelectionIntent.DeleteOneClicked(gone))
        runCurrent()
        assertEquals("the question, and no mode of picking under it", Selection(ids = setOf(gone), confirming = true), viewModel.state.value.selection)

        viewModel.select(SelectionIntent.DeleteConfirmed)
        runCurrent()
        assertEquals(listOf(listOf(gone)), deletes)
        assertEquals(listOf(kept), viewModel.state.value.cards.map { it.id })
        assertEquals(Selection(), viewModel.state.value.selection)
    }

    @Test
    fun `«Отмена» of the question about one card deletes nothing and picks nothing`() = runTest {
        val id = save(daysAgo = 0)
        val viewModel = viewModel()
        runCurrent()

        viewModel.select(SelectionIntent.DeleteOneClicked(id))
        viewModel.select(SelectionIntent.DeleteDismissed)
        runCurrent()

        assertEquals(emptyList<List<Long>>(), deletes)
        assertEquals(1, viewModel.state.value.totalCount)
        assertEquals(Selection(), viewModel.state.value.selection)
        // a tap after it opens the recording, as outside the mode
        viewModel.onIntent(HistoryIntent.SessionClicked(id))
        assertEquals(HistoryEffect.OpenSession(id), viewModel.effects.first())
    }

    @Test
    fun `the question about one card goes when the card goes`() = runTest {
        save(daysAgo = 0)
        val gone = save(daysAgo = 1)
        val viewModel = viewModel()
        runCurrent()
        viewModel.select(SelectionIntent.DeleteOneClicked(gone))

        repository.delete(gone)
        runCurrent()

        assertEquals(Selection(), viewModel.state.value.selection)
        viewModel.select(SelectionIntent.DeleteConfirmed)
        runCurrent()
        assertEquals("the question went with its card: a late «Удалить» deletes nothing", emptyList<List<Long>>(), deletes)
    }

    /** What is picked goes only through the question: «Удалить» without it on the screen deletes nothing (spec 3.18). */
    @Test
    fun `picked cards are not deleted without the question`() = runTest {
        val picked = save(daysAgo = 0)
        val viewModel = viewModel()
        runCurrent()
        viewModel.select(SelectionIntent.CardLongPressed(picked))
        runCurrent()
        assertEquals(Selection(active = true, ids = setOf(picked)), viewModel.state.value.selection)

        viewModel.select(SelectionIntent.DeleteConfirmed)
        runCurrent()

        assertEquals(emptyList<List<Long>>(), deletes)
        assertEquals(listOf(picked), viewModel.state.value.cards.map { it.id })
    }

    /**
     * Spec 3.36.5: nothing recorded at all shows no chips, and the chip is «Все» again — its default. The recording made after it (on
     * Live, say, through «Открыть Live») is seen, not hidden under a chip that could not be seen when the tab was empty.
     */
    @Test
    fun `with nothing left at all the chip is «Все» again and the next recording is seen`() = runTest {
        val take = save(daysAgo = 0, pieceId = 7)
        val viewModel = viewModel()
        viewModel.onIntent(HistoryIntent.FilterSelected(HistoryFilter.TAKES))
        runCurrent()
        viewModel.select(SelectionIntent.DeleteOneClicked(take))
        viewModel.select(SelectionIntent.DeleteConfirmed)
        runCurrent()
        assertEquals(0, viewModel.state.value.totalCount)
        assertEquals(HistoryFilter.ALL, viewModel.state.value.filter)

        val fromLive = save(daysAgo = 0)
        runCurrent()
        assertEquals(HistoryFilter.ALL, viewModel.state.value.filter)
        assertEquals(listOf(fromLive), viewModel.state.value.cards.map { it.id })
    }

    /** Spec 3.36.5: nothing under a chip while there are recordings keeps the chip — the tab says so and offers «Показать все записи». */
    @Test
    fun `a chip with nothing under it keeps its choice`() = runTest {
        val take = save(daysAgo = 0, pieceId = 7)
        save(daysAgo = 1)
        val viewModel = viewModel()
        viewModel.onIntent(HistoryIntent.FilterSelected(HistoryFilter.TAKES))
        runCurrent()
        viewModel.select(SelectionIntent.DeleteOneClicked(take))
        viewModel.select(SelectionIntent.DeleteConfirmed)
        runCurrent()

        assertEquals(HistoryFilter.TAKES, viewModel.state.value.filter)
        assertEquals(emptyList<HistoryCard>(), viewModel.state.value.cards)
        assertEquals(1, viewModel.state.value.totalCount)
    }

    @Test
    fun `«Открыть Live» of an empty tab opens Live`() = runTest {
        val viewModel = viewModel()
        runCurrent()
        assertEquals(0, viewModel.state.value.totalCount)

        viewModel.onIntent(HistoryIntent.OpenLiveClicked)
        assertEquals(HistoryEffect.OpenLive, viewModel.effects.first())
    }

    @Test
    fun `the best mark is set on a take, moves to another and is cleared by a second tap`() = runTest {
        val pieceId = repertoire.add(PieceDraft(title = "Менуэт"), nowEpochMs = 0)
        val first = save(daysAgo = 1, pieceId = pieceId)
        val second = save(daysAgo = 0, pieceId = pieceId)
        val free = save(daysAgo = 2)
        val viewModel = viewModel()
        runCurrent()
        fun best() = viewModel.state.value.cards.filter { it.best }.map { it.id }

        viewModel.onIntent(HistoryIntent.BestToggled(first))
        runCurrent()
        assertEquals(listOf(first), best())

        viewModel.onIntent(HistoryIntent.BestToggled(second))
        runCurrent()
        assertEquals(listOf(second), best())
        // the list of «Записи» stays in the order of time: the mark pins a take on the screen of its piece only
        assertEquals(listOf(second, first, free), viewModel.state.value.cards.map { it.id })

        viewModel.onIntent(HistoryIntent.BestToggled(second))
        viewModel.onIntent(HistoryIntent.BestToggled(free)) // not a take: nothing to mark
        runCurrent()
        assertEquals(emptyList<Long>(), best())
    }
}
