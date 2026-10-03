package com.violinjourney.app.feature.sound

import com.violinjourney.app.core.audio.playback.PlayerState
import com.violinjourney.app.core.domain.backing.BackingOutput
import com.violinjourney.app.core.domain.events.SessionEvent
import com.violinjourney.app.core.domain.sound.BuiltInPreset
import com.violinjourney.app.core.domain.sound.EqBand
import com.violinjourney.app.core.domain.sound.ReverbSpace
import com.violinjourney.app.core.domain.sound.SoundBlock
import com.violinjourney.app.core.domain.sound.SoundParam
import com.violinjourney.app.core.domain.sound.SoundSettings
import com.violinjourney.app.feature.history.HistoryCard
import kotlinx.datetime.LocalDate

/** Whose sound the screen sets (spec 3.17): that of one recording, or the default of all of them. */
enum class SoundMode { RECORDING, EVERYONE }

sealed interface PresetRef {
    data class BuiltIn(val preset: BuiltInPreset) : PresetRef

    data class User(val id: Long) : PresetRef
}

/** One chip of the preset row; [userName] is null for a built-in one — its name is a UI string. */
data class PresetChip(val ref: PresetRef, val userName: String?, val selected: Boolean)

/** What a caption says of some settings: a name where they are a preset, «свои настройки» where they are not. */
sealed interface SoundCaption {
    data class BuiltIn(val preset: BuiltInPreset) : SoundCaption

    data class User(val name: String) : SoundCaption

    data object Custom : SoundCaption
}

/** A recording by what its title is built from; the screen builds the title in its locale. */
data class RecordingName(
    val sessionId: Long,
    val title: String?,
    val pieceTitle: String?,
    val startedAtEpochMs: Long,
    /** The sound being set is that of a video take: the header says so (spec 3.19). */
    val hasVideo: Boolean = false,
    /** The thumbnail of that video: its frame stands in the tile of «Слушать на» (spec 3.38). */
    val thumbPath: String? = null,
    /** The event it is a recording of (spec 3.35): «Осенний концерт · 24 октября» until it is given a name of its own. */
    val event: SessionEvent? = null,
)

sealed interface SoundDialog {
    /** «Свои → Как у всех» and «Сбросить»: the one place that asks, for the recording's own settings are forgotten. */
    data object BackToEveryone : SoundDialog

    /** «Сбросить» of the default: every recording that follows it goes back to the sound as recorded. */
    data object ResetEveryone : SoundDialog

    data object SavePreset : SoundDialog

    data class DeletePreset(val id: Long, val name: String) : SoundDialog

    /** «Слушать на…»: which recording the default is tried on — a bottom sheet of the cards of the recordings (spec 3.36.5). */
    data object PickRecording : SoundDialog
}

/**
 * The cards of «Звук» that open and close (spec 3.36.5): the four blocks of the chain and «Минусовка» of a take under a backing —
 * [BACKING] is a card of the screen, not a [SoundBlock] of the sound: it has no switch, the switch «С минусовкой | Только скрипка»
 * of the player decides whether it is heard.
 */
enum class SoundCard {
    EQ,
    COMPRESSOR,
    REVERB,
    OUTPUT,
    BACKING,
    ;

    companion object {
        fun of(block: SoundBlock): SoundCard = when (block) {
            SoundBlock.EQ -> EQ
            SoundBlock.COMPRESSOR -> COMPRESSOR
            SoundBlock.REVERB -> REVERB
            SoundBlock.OUTPUT -> OUTPUT
        }
    }
}

data class SoundState(
    /** True until the settings have been read once. */
    val loading: Boolean,
    val mode: SoundMode,
    /** The recording the screen is about; in [SoundMode.EVERYONE] — the one it is listened on, if any. */
    val recording: RecordingName?,
    /** [SoundMode.RECORDING] only: true — «Свои для записи», false — «Как у всех». */
    val own: Boolean,
    val settings: SoundSettings,
    val caption: SoundCaption,
    val chips: List<PresetChip>,
    /** The settings are no preset: the chip «Свои» stands first, and «Сохранить как пресет» is there to be pressed. */
    val custom: Boolean,
    val canReset: Boolean,
    /** For a few seconds after the first change: «свои настройки · сохраняются сами». */
    val savedHint: Boolean,
    /** The cards that are open: none when the screen opens, and opening one closes none of the others (spec 3.36.5). */
    val expanded: Set<SoundCard> = emptySet(),
    val band: EqBand,
    /** «Подробно» of the compressor is open. */
    val details: Boolean,
    /**
     * Null — there is nothing to listen to (no recording with sound), the file is still being opened, or it cannot be played. Its
     * position is to the whole second; the exact one is [SoundViewModel.position].
     */
    val player: PlayerState?,
    /**
     * The screen plays a recording — known as soon as its file is given to the player, before the player is ready: the panel of the
     * player stands at the bottom from the first frame (its first row empty until the player is ready), and nothing under it jumps
     * when the player comes. False once the file cannot be played: the panel goes.
     */
    val listening: Boolean = false,
    /** The player waits for the backing's sound to be made (spec 5.25): «Готовим минусовку…» where it will be. */
    val preparingBacking: Boolean = false,
    /** Null until reckoned: the player at the bottom then shows a plain slider. */
    val waveform: List<Float>?,
    /**
     * [SoundMode.EVERYONE]: the recordings the default can be tried on — those with sound whose file is there — newest first, as the
     * cards of «Записи» show them (spec 3.36.5): the sheet «Слушать на…» lists them under their days.
     */
    val recordings: List<HistoryCard>,
    /** The day the cards of [recordings] are dated against: the chip «сегодня» and the year of the sheet «Слушать на…». */
    val today: LocalDate,
    /** [SoundMode.EVERYONE]: how many recordings follow the default — «для 23 записей». */
    val affected: Int,
    val dialog: SoundDialog?,
    /** A take under a backing (spec 3.32): its block «Минусовка», last. Null for anything else. */
    val backing: BackingBlockState? = null,
) {
    /**
     * A take under a backing whose sound could not be prepared — no room for it, a damaged or missing copy (spec 3.32):
     * the player is ready but plays the violin alone, so the block says so instead of offering sliders that change nothing.
     */
    val backingUnavailable: Boolean get() = backing != null && player?.hasBacking == false
}

/**
 * The card «Минусовка» of a take (spec 3.32, 3.36.5): how loud and how far shifted the backing is mixed; under its name the
 * backing's own — the name its file came with and its length, «фортепиано · 3:40»; in it, what the take was recorded in.
 */
data class BackingBlockState(
    val gainDb: Float,
    val offsetMs: Int,
    /** The shift worked out while recording: «Как записано». */
    val recordedOffsetMs: Int,
    /** The name the backing's file came with, without its extension. */
    val title: String,
    val durationMs: Long,
    /** What the take was recorded in: «Записано в Pixel Buds · +200 мс учтено»; null — nothing to say (wireless without a name). */
    val recordedWith: RecordedWith?,
)

/** What a take under a backing was recorded in (spec 3.36.5): the line with the headphones in the card «Минусовка». */
sealed interface RecordedWith {
    /** Wireless headphones by the name they gave themselves; [latencyMs] — what the guess of their lag added to the clocks (5.25). */
    data class Wireless(val name: String, val latencyMs: Int) : RecordedWith

    /** Wired or USB headphones: the system counts their lag itself. */
    data object Wired : RecordedWith
}

/**
 * The line of the headphones from what the take stores (spec 3.36.5; `take_backings`): wired and USB — «в проводных наушниках»;
 * wireless with a name — that name and the lag added; wireless without a name, and the speaker, which never records a take under a
 * backing (3.32) — nothing. Pure, with a test.
 */
object RecordedWithRule {
    fun of(output: BackingOutput, deviceName: String?, latencyMs: Int): RecordedWith? = when (output) {
        BackingOutput.WIRED, BackingOutput.USB -> RecordedWith.Wired
        BackingOutput.BLUETOOTH -> deviceName?.trim()?.takeIf { it.isNotEmpty() }?.let { RecordedWith.Wireless(it, latencyMs) }
        BackingOutput.SPEAKER -> null
    }
}

sealed interface SoundIntent {
    data object BackClicked : SoundIntent

    data object ScreenStopped : SoundIntent

    data object PlayPauseClicked : SoundIntent

    data class SeekRequested(val positionMs: Long) : SoundIntent

    /** A/B; [held] — A is only pressed and held: released, it goes back to B. */
    data class OriginalSelected(val original: Boolean, val held: Boolean = false) : SoundIntent

    data class ModeSelected(val own: Boolean) : SoundIntent

    data object ResetClicked : SoundIntent

    data class PresetSelected(val ref: PresetRef) : SoundIntent

    data object SavePresetClicked : SoundIntent

    data class PresetNameConfirmed(val name: String) : SoundIntent

    /** A long press on a preset of the user's own offers to remove it. */
    data class PresetLongPressed(val ref: PresetRef) : SoundIntent

    data object DialogConfirmed : SoundIntent

    data object DialogDismissed : SoundIntent

    data class BlockSwitched(val block: SoundBlock, val on: Boolean) : SoundIntent

    /** The header of a card: it opens, or closes; the others stay as they are. */
    data class CardToggled(val card: SoundCard) : SoundIntent

    data class BandSelected(val band: EqBand) : SoundIntent

    data class LowCutSwitched(val on: Boolean) : SoundIntent

    data class SpaceSelected(val space: ReverbSpace) : SoundIntent

    data object DetailsClicked : SoundIntent

    data class ParamChanged(val param: SoundParam, val value: Double) : SoundIntent

    data class ParamStepped(val param: SoundParam, val up: Boolean) : SoundIntent

    /** A double tap: back to the default of the parameter. */
    data class ParamReset(val param: SoundParam) : SoundIntent

    /** A point of the curve under the finger: frequency along, gain up and down (not for the low cut). */
    data class BandDragged(val band: EqBand, val hz: Double, val gainDb: Double) : SoundIntent

    data object ListenOnClicked : SoundIntent

    data class RecordingPicked(val sessionId: Long) : SoundIntent

    data object ShareClicked : SoundIntent

    /** «с минусовкой / только скрипка» (spec 3.32). */
    data class BackingHeardSelected(val heard: Boolean) : SoundIntent

    data class BackingGainChanged(val fraction: Float) : SoundIntent

    data class BackingGainStepped(val up: Boolean) : SoundIntent

    data object BackingGainReset : SoundIntent

    data class BackingOffsetChanged(val fraction: Float) : SoundIntent

    /** «−5 / +5». */
    data class BackingOffsetStepped(val up: Boolean) : SoundIntent

    /** «Как записано», and a double tap on the slider. */
    data object BackingOffsetRecorded : SoundIntent
}

sealed interface SoundEffect {
    data object Close : SoundEffect

    data class Share(val sessionId: Long) : SoundEffect
}
