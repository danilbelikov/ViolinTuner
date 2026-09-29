package com.violinjourney.app.feature.repertoire.piece

import com.violinjourney.app.core.domain.backing.AudioRoute
import com.violinjourney.app.core.domain.backing.BackingOutput
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Why «Записать дубль» sleeps: one reason at a time, in one order (spec 3.36.4). */
class RecordReasonTest {
    private val headphones = AudioRoute(BackingOutput.WIRED, "Pixel Buds")
    private val speaker = AudioRoute(BackingOutput.SPEAKER, null)
    private val backing = BackingUi(title = "Вивальди — фортепиано", durationMs = 220_000, enabled = true, route = headphones)

    @Test
    fun `nothing holds the key back with the microphone - the headphones and the backing's sound in place`() {
        assertNull(RecordReason.of(micPermission = true, backing))
        assertNull(RecordReason.of(micPermission = null, backing), "not known yet: a tap asks")
        assertNull(RecordReason.of(micPermission = true, backing = null), "the backing is still being read")
    }

    @Test
    fun `the microphone comes first - before everything the backing could say`() {
        val worst = backing.copy(route = speaker, preparing = true, unprepared = true)
        assertEquals(RecordReason.NO_MIC, RecordReason.of(micPermission = false, worst))
        assertEquals(RecordReason.NO_MIC, RecordReason.of(micPermission = false, BackingUi(title = null, durationMs = 0, enabled = false, route = speaker)))
    }

    @Test
    fun `then the headphones - the preparing and the sound that could not be made - in this order`() {
        assertEquals(RecordReason.NO_HEADPHONES, RecordReason.of(true, backing.copy(route = speaker, preparing = true, unprepared = true)))
        assertEquals(RecordReason.PREPARING, RecordReason.of(true, backing.copy(preparing = true, unprepared = true)))
        assertEquals(RecordReason.UNPREPARED, RecordReason.of(true, backing.copy(unprepared = true)))
    }

    @Test
    fun `with the switch off or without a backing the backing holds nothing back`() {
        assertNull(RecordReason.of(true, backing.copy(enabled = false, route = speaker, preparing = true)))
        assertNull(RecordReason.of(true, BackingUi(title = null, durationMs = 0, enabled = true, route = speaker)))
        assertNull(RecordReason.of(true, backing.copy(route = speaker, problem = BackingProblem.Missing)), "a lost copy is no backing to play")
    }

    @Test
    fun `a reason is exactly what makes the backing hold the take back`() {
        listOf(
            backing, backing.copy(route = speaker), backing.copy(preparing = true), backing.copy(unprepared = true),
            backing.copy(enabled = false, route = speaker), backing.copy(problem = BackingProblem.Missing, route = speaker),
        ).forEach { case ->
            assertEquals(case.blocksRecording, RecordReason.of(true, case) != null, "$case")
        }
    }

    @Test
    fun `two sentences of a plate take a space between them - none after a wide full stop`() {
        assertEquals("Подключите наушники. Или выключите.", Sentences.join("Подключите наушники.", "Или выключите."))
        assertEquals("请连接耳机。或者关闭。", Sentences.join("请连接耳机。", "或者关闭。"))
        assertEquals("接続してください。またはオフに。", Sentences.join("接続してください。", "またはオフに。"))
    }
}
