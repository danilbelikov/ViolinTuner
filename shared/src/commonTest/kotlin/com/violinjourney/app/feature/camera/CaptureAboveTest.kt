package com.violinjourney.app.feature.camera

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Over the shutter of the own camera stands always one thing, in one order (spec 3.36.4). */
class CaptureAboveTest {
    private val ready = CaptureState(
        cameraPermission = true, micPermission = true, backingTitle = "Вивальди — фортепиано", backingDurationMs = 220_000,
        underBacking = true, headphonesName = "Pixel Buds",
    )

    @Test
    fun `under the backing with all in place the line names the headphones and the backing`() {
        assertEquals(CaptureAbove.Ready, CaptureAbove.of(ready))
    }

    @Test
    fun `without the backing and with nothing to say nothing stands there`() {
        assertNull(CaptureAbove.of(ready.copy(underBacking = false)))
    }

    @Test
    fun `the line «нет разрешения» comes before every plate and takes the place of the line`() {
        val all = ready.copy(micPermission = false, noHeadphones = true, preparing = true, micUnavailable = true, spaceMinutes = 7)
        assertEquals(CaptureAbove.NoPermission, CaptureAbove.of(all))
        assertEquals(CaptureAbove.NoPermission, CaptureAbove.of(ready.copy(cameraPermission = false, underBacking = false)))
    }

    @Test
    fun `the plates come one at a time in their order`() {
        var state = ready.copy(noHeadphones = true, preparing = true, backingUnprepared = true, micUnavailable = true, spaceMinutes = 7)
        val seen = mutableListOf<CapturePlate>()
        while (true) {
            val plate = (CaptureAbove.of(state) as? CaptureAbove.Plate)?.plate ?: break
            seen += plate
            state = when (plate) {
                CapturePlate.NO_HEADPHONES -> state.copy(noHeadphones = false)
                CapturePlate.PREPARING -> state.copy(preparing = false)
                CapturePlate.UNPREPARED -> state.copy(backingUnprepared = false)
                CapturePlate.MIC_UNAVAILABLE -> state.copy(micUnavailable = false)
                CapturePlate.LOW_SPACE -> state.copy(spaceMinutes = null)
            }
        }
        assertEquals<List<CapturePlate>>(CapturePlate.entries, seen)
        assertEquals(CaptureAbove.Ready, CaptureAbove.of(state), "and then the line")
    }

    @Test
    fun `the plates of the backing are not there without it — «Снять видео»`() {
        val plain = ready.copy(underBacking = false, noHeadphones = true, preparing = true, backingUnprepared = true)
        assertNull(CaptureAbove.of(plain))
        assertEquals(CaptureAbove.Plate(CapturePlate.LOW_SPACE), CaptureAbove.of(plain.copy(spaceMinutes = 7)))
    }

    // the shutter's sleep is decided by CaptureState.canRecord: each plate alone must leave it as the plate says
    @Test
    fun `the shutter sleeps under the plates of the backing and not under the microphone or the space`() {
        assertTrue(CapturePlate.NO_HEADPHONES.shutterSleeps && CapturePlate.PREPARING.shutterSleeps && CapturePlate.UNPREPARED.shutterSleeps)
        assertFalse(CapturePlate.MIC_UNAVAILABLE.shutterSleeps || CapturePlate.LOW_SPACE.shutterSleeps)
        assertTrue(ready.canRecord, "nothing over the shutter but the line")
        for (plate in CapturePlate.entries) {
            val alone = when (plate) {
                CapturePlate.NO_HEADPHONES -> ready.copy(noHeadphones = true)
                CapturePlate.PREPARING -> ready.copy(preparing = true)
                CapturePlate.UNPREPARED -> ready.copy(backingUnprepared = true)
                CapturePlate.MIC_UNAVAILABLE -> ready.copy(micUnavailable = true)
                CapturePlate.LOW_SPACE -> ready.copy(spaceMinutes = 7)
            }
            assertEquals(CaptureAbove.Plate(plate), CaptureAbove.of(alone), "$plate stands alone")
            assertEquals(!plate.shutterSleeps, alone.canRecord, "the shutter under $plate")
        }
    }

    @Test
    fun `during a shot under the backing its progress and nothing of the rest`() {
        val shooting = ready.copy(recording = true, backingPlayedMs = 72_000, micUnavailable = true, spaceMinutes = 7)
        assertEquals(CaptureAbove.BackingProgress, CaptureAbove.of(shooting))
        assertNull(CaptureAbove.of(shooting.copy(underBacking = false)), "a plain video: the timer alone")
        assertNull(CaptureAbove.of(shooting.copy(backingPlayedMs = null)), "the backing not started yet")
    }

    // the review of stage 109: «минусовка 3:40» with its sign 124.6 dp, «Pixel Buds» 87.6 (Manrope 700, 13 sp); the name keeps 64 + 22
    @Test
    fun `in the side column lying the headphones and the backing stand one under the other`() {
        // 210 − 2 · 16: in one row the name kept 41 dp — «P…»
        assertFalse(ReadyLineFit.oneRow(width = 178, lengthWhole = 125, nameWhole = 88, nameLeast = 86, gap = 12))
    }

    @Test
    fun `upright they share one row and a long name gives way with an ellipsis`() {
        assertTrue(ReadyLineFit.oneRow(width = 320, lengthWhole = 125, nameWhole = 88, nameLeast = 86, gap = 12))
        assertTrue(ReadyLineFit.oneRow(width = 320, lengthWhole = 125, nameWhole = 180, nameLeast = 86, gap = 12), "«Galaxy Buds2 Pro (Danil)»")
        // «acompañamiento 3:40» at the font 1.3 — 212.9
        assertTrue(ReadyLineFit.oneRow(width = 320, lengthWhole = 213, nameWhole = 109, nameLeast = 86, gap = 12))
        assertFalse(ReadyLineFit.oneRow(width = 300, lengthWhole = 213, nameWhole = 109, nameLeast = 86, gap = 12))
    }

    @Test
    fun `a short name needs no more than itself`() {
        assertTrue(ReadyLineFit.oneRow(width = 178, lengthWhole = 125, nameWhole = 40, nameLeast = 86, gap = 12))
    }
}
