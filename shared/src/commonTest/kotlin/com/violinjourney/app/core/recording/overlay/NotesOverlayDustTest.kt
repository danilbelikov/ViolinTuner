package com.violinjourney.app.core.recording.overlay

import com.violinjourney.app.core.domain.Zone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NotesOverlayDustTest {
    private val config = NotesVideoConfig()
    private val geometry = NotesOverlayGeometry(1_080f, 1_920f, config)

    /** A 0–500 ms near, then a pause of 1.5 s, then B 2–12 s in tune. */
    private val overlay = NotesOverlay(
        notes = listOf(OverlayNote(69, 0, 500, Zone.NEAR, 12.0), OverlayNote(71, 2_000, 12_000, Zone.IN_TUNE, -2.0)),
        lowMidi = 66f,
        highMidi = 74f,
        scorePercent = 80,
        toleranceCents = 8,
        title = "",
        heading = "",
        date = "",
        bestMidi = null,
        drift = null,
        previous = null,
        ribbon = emptyList(),
        config = config,
    )

    private fun at(nowMs: Long) = NotesOverlayDust.particlesAt(nowMs, overlay, geometry).toList()

    @Test
    fun `the hash is the one of the mockup — bit for bit`() {
        // Math.imul and >>> of overlay.html give these
        assertEquals(1_061_416_281 / TWO_TO_32, NotesOverlayDust.hash01(0, 0, 0, 0))
        assertEquals(3_434_095_989 / TWO_TO_32, NotesOverlayDust.hash01(3, 17, 2, 1))
        assertEquals(615_394_720 / TWO_TO_32, NotesOverlayDust.hash01(120, 5, 1, 3))
        assertEquals(796_564_479 / TWO_TO_32, NotesOverlayDust.hash01(7, 0, 2, 2))
    }

    @Test
    fun `a frame is its own time alone — the same at any frame rate`() {
        val once = at(5_000)
        // stepping through other frames first — at 24 and at 60 a second — changes nothing
        (0 until 120).forEach { at(4_000L + it * 1_000L / 24) }
        assertEquals(once, at(5_000))
        (0 until 60).forEach { at(4_500L + it * 1_000L / 60) }
        assertEquals(once, at(5_000))
    }

    @Test
    fun `no particle stands right of the playhead`() {
        for (now in 0L..13_000L step 37) {
            at(now).forEach { assertTrue(it.x <= geometry.headX, "at $now: ${it.x} > ${geometry.headX}") }
        }
    }

    @Test
    fun `a piece of the capsule is dust for 700 ms — then nothing is left of it`() {
        // A is 4.9 u long: its last piece of 0.4 u crosses at 12 × 0.4 / 11 s ≈ 436 ms and is gone 700 ms later
        assertTrue(at(1_100).isNotEmpty())
        assertTrue(at(1_100).all { it.zone == Zone.NEAR })
        assertEquals(emptyList(), at(1_137))
        assertEquals(emptyList(), at(1_900))
        // nothing before the first note reaches the playhead
        assertEquals(emptyList(), at(-1))
    }

    @Test
    fun `a sounding note crumbles into about 60 particles a frame`() {
        val particles = at(5_000)
        assertTrue(particles.size in 54..63, "${particles.size}")
        assertTrue(particles.all { it.zone == Zone.IN_TUNE })
    }

    @Test
    fun `a particle is born whole at the playhead inside the capsule — and shrinks and fades`() {
        // the piece k = 0 of A crosses at 0 ms exactly
        val born = at(0)
        assertEquals(config.dustPerSegment, born.size)
        val center = geometry.pillCenterY(69, 66f, 74f)
        born.forEach {
            assertEquals(1f, it.alpha)
            assertEquals(geometry.headX, it.x)
            assertTrue(it.y >= center - geometry.pillHeight / 2 && it.y <= center + geometry.pillHeight / 2)
            assertTrue(it.side >= config.dustMinSideU * geometry.u && it.side <= config.dustMaxSideU * geometry.u)
        }
        // the same three at 350 ms — they come first: half their life, a quarter of the opacity, 70 % of the side
        val first = at(350).take(config.dustPerSegment)
        first.zip(born).forEach { (old, young) ->
            assertEquals(0.25f, old.alpha, EPSILON)
            assertEquals(young.side * 0.7f, old.side, EPSILON)
            assertTrue(old.x < young.x)
        }
    }

    private companion object {
        const val TWO_TO_32 = 4_294_967_296.0
        const val EPSILON = 1e-3f
    }
}
