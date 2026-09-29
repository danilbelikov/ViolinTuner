package com.violinjourney.app.feature.sound

import com.violinjourney.app.core.audio.fx.SoundMeters
import com.violinjourney.app.core.audio.playback.PlayerState
import com.violinjourney.app.core.audio.playback.SessionPlayer
import com.violinjourney.app.core.domain.sound.SoundSettings
import com.violinjourney.app.core.io.PlatformFile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** «Пока держишь» of A (spec 3.17), one rule for «Звук» and the recording (spec 3.36.5). */
class OriginalHoldTest {
    private class Player : SessionPlayer {
        override val state = MutableStateFlow(PlayerState(ready = true, processed = true))
        override val meters = MutableStateFlow<SoundMeters?>(null)
        override fun load(file: PlatformFile) = Unit
        override fun play() = Unit
        override fun pause() = Unit
        override fun seekTo(positionMs: Long) = Unit
        override fun setSound(settings: SoundSettings) = Unit
        override fun setOriginal(original: Boolean) = state.update { it.copy(original = original) }
        override fun release() = Unit
    }

    private val player = Player()
    private val hold = OriginalHold()
    private val original get() = player.state.value.original

    @Test
    fun `a hold plays the original for as long as it lasts`() {
        hold.select(player, original = true, held = true)
        assertTrue(original)
        hold.select(player, original = false, held = true)
        assertFalse(original)
    }

    @Test
    fun `the end of a press that held nothing leaves the choice alone`() {
        hold.select(player, original = true, held = false)
        hold.select(player, original = false, held = true)
        assertTrue(original, "a tap on A: its press ends too, and A stays")
    }

    @Test
    fun `a hold that began at A does not take A away when it ends`() {
        hold.select(player, original = true, held = false)
        hold.select(player, original = true, held = true)
        hold.select(player, original = false, held = true)
        assertTrue(original, "A was chosen before the finger came")
    }

    @Test
    fun `a tap in the middle of a hold ends it`() {
        hold.select(player, original = true, held = true)
        hold.select(player, original = false, held = false)
        hold.select(player, original = true, held = false)
        hold.select(player, original = false, held = true)
        assertTrue(original, "the hold was over: the let-go that follows keeps the tapped A")
    }

    @Test
    fun `without a player nothing happens`() {
        hold.select(null, original = true, held = true)
        assertFalse(original)
    }
}
