package com.violinjourney.app.core.domain.journey

import com.violinjourney.app.core.domain.IntonationReading
import com.violinjourney.app.core.domain.Note
import com.violinjourney.app.core.domain.Zone
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JourneyTest {
    private val config = JourneyConfig()

    private fun active(midi: Int, zone: Zone, held: Boolean = false) =
        IntonationReading.Active(Note(midi), cents = if (zone == Zone.IN_TUNE) 2.0 else 15.0, zone = zone, direction = null, holdProgress = 0.0, held = held)

    /** Feeds [reading] every 10 ms for [ms]; returns the time after it. */
    private fun NoteCounter.play(from: Long, ms: Long, reading: IntonationReading): Long {
        var t = from
        while (t < from + ms) {
            add(t, reading)
            t += 10
        }
        return t
    }

    @Test
    fun `a note counts when it has lasted, and is in tune when most of it was`() {
        val counter = NoteCounter(config)
        var t = counter.play(0, 400, active(69, Zone.IN_TUNE))
        t = counter.play(t, 400, active(71, Zone.NEAR))
        t = counter.play(t, 100, active(72, Zone.IN_TUNE)) // a passing note: too short to count
        // a note that starts flat and settles: more of it in tune than not
        t = counter.play(t, 100, active(74, Zone.NEAR))
        counter.play(t, 300, active(74, Zone.IN_TUNE))
        assertEquals(NoteCount(played = 3, inTune = 2), counter.finish())
    }

    @Test
    fun `silence ends a note, a held reading is not a measurement, and what was taken is not counted again`() {
        val counter = NoteCounter(config)
        var t = counter.play(0, 300, active(69, Zone.IN_TUNE))
        t = counter.play(t, 300, active(69, Zone.OFF, held = true)) // the screen keeps the note through a gap; nothing is measured
        counter.add(t, IntonationReading.Silence)
        assertEquals(NoteCount(1, 1), counter.take())
        assertEquals(NoteCount.ZERO, counter.take())
        // the same pitch after the silence is a new note
        counter.play(t + 10, 300, active(69, Zone.IN_TUNE))
        assertEquals(NoteCount.ZERO, counter.take()) // still sounding
        assertEquals(NoteCount(1, 1), counter.finish())
    }

    @Test
    fun `a note in tune is a takt and a whole minute is two`() {
        assertEquals(88 + 76, JourneyRules.taktsFor(notesInTune = 264, durationMs = 38 * 60_000L + 59_000, config))
        // three notes make a takt, rounded up: one clean note is a takt already, none is none
        assertEquals(listOf(0, 1, 1, 1, 2, 2, 2, 3), (0..7).map { JourneyRules.taktsFor(it, 0, config) })
        assertEquals(0, JourneyRules.taktsFor(0, 59_000, config))
        assertEquals(0, JourneyRules.taktsFor(-5, -1, config))
    }

    @Test
    fun `the route is linear - sixteen stops after home, prices rise, every stop has its postcard and can be reached`() {
        val stops = JourneyRoute.stops
        assertEquals(17, stops.size)
        assertEquals(JourneyRoute.HOME, stops.first().id)
        assertEquals(stops.map { it.price }.sorted(), stops.map { it.price })
        assertTrue(stops.all { it.available && it.views.isNotEmpty() })
        assertEquals(81_900, stops.sumOf { it.price }) // the handoff says 81 700; its own prices add up to this
        assertEquals(stops.size, stops.map { it.id }.toSet().size)
    }

    @Test
    fun `the journey waits at home until the case is packed, then the road costs what the next stop costs`() = runTest {
        val journey = FakeJourneyRepository()
        journey.earn(TaktEarning(1, 300, 280, 20 * 60_000L, 320))
        assertFalse("nothing moves before the intro", JourneyRules.canDepart(journey.progress.value))

        journey.start(10)
        val cremona = JourneyRules.next(journey.progress.value)!!
        assertEquals("cremona", cremona.id)
        assertTrue(JourneyRules.canDepart(journey.progress.value))
        assertTrue(journey.depart(cremona, 20))
        assertEquals(20L, journey.progress.value.balance)
        assertEquals(1, JourneyRules.currentIndex(journey.progress.value))
        assertEquals(480L, JourneyRules.missing(journey.progress.value)) // Milan is 500
        assertFalse(journey.depart(JourneyRules.next(journey.progress.value)!!, 30))
        assertFalse("no stop is skipped", journey.depart(JourneyRoute.stops[4], 30))
    }

    @Test
    fun `beyond the last drawn stop there is no next - the takts wait`() {
        val everywhere = JourneyProgress(earned = 99_999, spent = 0, arrivals = JourneyRoute.stops.filter { it.available }.map { Arrival(it.id, 1) }, extras = emptySet())
        assertNull(JourneyRules.next(everywhere))
        assertFalse(JourneyRules.canDepart(everywhere))
        assertEquals(0L, JourneyRules.missing(everywhere))
    }

    @Test
    fun `extras belong to stops that were reached - a second view only where one is drawn, a second time only where there is a picture`() {
        val progress = JourneyProgress(earned = 1_000, spent = 0, arrivals = listOf(Arrival("home", 1), Arrival("vienna", 2), Arrival("sketch", 3)), extras = emptySet())
        fun stop(id: String) = JourneyRoute.stops.first { it.id == id }
        assertEquals(JourneyExtra.entries.toList(), JourneyRules.offers(stop("vienna"), progress))
        // home is a section of its own: no extras of a stop
        assertEquals(emptyList<JourneyExtra>(), JourneyRules.offers(stop("home"), progress))
        // a stop that has only its sketch yet: nothing to see by day
        assertEquals(listOf(JourneyExtra.SOUVENIR), JourneyRules.offers(JourneyStop("sketch", 100, Transport.TRAIN, 0f, 0f, available = true), progress))
        // every hall can be seen from inside, the two rooms of Italy from outside; only home has no second view
        assertEquals(listOf("home"), JourneyRoute.stops.filter { !it.hasSecondView }.map { it.id })
        assertEquals(JourneyExtra.entries.toList(), JourneyRules.offers(stop("salzburg"), progress.copy(arrivals = progress.arrivals + Arrival("salzburg", 4))))
        assertTrue(JourneyRoute.stops.filter { stop -> stop.views.none { it.inside && it == stop.views.first() } }.all { it.hasSecondView })
        assertEquals(emptyList<JourneyExtra>(), JourneyRules.offers(stop("prague"), progress))
        assertTrue(JourneyRules.canBuy(stop("vienna"), JourneyExtra.SECOND_VIEW, progress, config))
        assertFalse(JourneyRules.canBuy(stop("vienna"), JourneyExtra.SECOND_VIEW, progress.copy(spent = 700), config))
        assertFalse(JourneyRules.canBuy(stop("vienna"), JourneyExtra.SOUVENIR, progress.copy(extras = setOf(BoughtExtra("vienna", JourneyExtra.SOUVENIR))), config))
    }
}
