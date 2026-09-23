package com.example.violintuner.feature.sound

import com.example.violintuner.core.audio.playback.PlayerState
import com.example.violintuner.core.domain.sound.BuiltInPreset
import com.example.violintuner.core.domain.sound.EqBand
import com.example.violintuner.core.domain.sound.ReverbSpace
import com.example.violintuner.core.domain.sound.SoundBlock
import com.example.violintuner.core.domain.sound.SoundParam
import com.example.violintuner.core.domain.sound.SoundSettings

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
)

sealed interface SoundDialog {
    /** «Свои → Как у всех» and «Сбросить»: the one place that asks, for the recording's own settings are forgotten. */
    data object BackToEveryone : SoundDialog

    /** «Сбросить» of the default: every recording that follows it goes back to the sound as recorded. */
    data object ResetEveryone : SoundDialog

    data object SavePreset : SoundDialog

    data class DeletePreset(val id: Long, val name: String) : SoundDialog

    /** «Слушать на…»: which recording the default is tried on. */
    data object PickRecording : SoundDialog
}

data class SoundState(
    /** True until the settings have been read once. */
    val loading: Boolean,
    val mode: SoundMode,
    /** The recording the screen is about; in [SoundMode.EVERYONE] — the one it is listened on, if any. */
    val recording: RecordingName?,
    /** [SoundMode.RECORDING] only: true — «Свои для этой записи», false — «Как у всех». */
    val own: Boolean,
    val settings: SoundSettings,
    val caption: SoundCaption,
    val chips: List<PresetChip>,
    /** The settings are no preset: the chip «Свои» stands first, and «Сохранить как пресет» is there to be pressed. */
    val custom: Boolean,
    val canReset: Boolean,
    /** For a few seconds after the first change: «свои настройки · сохраняются сами». */
    val savedHint: Boolean,
    val expanded: SoundBlock?,
    val band: EqBand,
    /** «Подробно» of the compressor is open. */
    val details: Boolean,
    /** Null — there is nothing to listen to (no recording with sound), or the file cannot be played. */
    val player: PlayerState?,
    /** Null until reckoned: the mini player then shows a plain slider. */
    val waveform: List<Float>?,
    /** [SoundMode.EVERYONE]: recordings the default can be tried on, newest first. */
    val recordings: List<RecordingName>,
    /** [SoundMode.EVERYONE]: how many recordings follow the default — «для 23 записей». */
    val affected: Int,
    val dialog: SoundDialog?,
    /** A take under a backing (spec 3.32): its block «Минусовка», last. Null for anything else. */
    val backing: BackingBlockState? = null,
)

/** The block «Минусовка» of a take: how loud and how far shifted the backing is mixed, and what the headphones could learn. */
data class BackingBlockState(
    val gainDb: Float,
    val offsetMs: Int,
    /** The shift worked out while recording: «Как записано». */
    val recordedOffsetMs: Int,
    /** Headphones whose latency this take's shift would correct: «Запомнить для …»; null — nothing to remember. */
    val rememberFor: String? = null,
    /** Their latency now and what it becomes: «200 → 1000 мс». */
    val rememberFromMs: Int = 0,
    val rememberToMs: Int = 0,
    /** Just remembered: the row says so instead of vanishing, until the shift moves again. */
    val remembered: Boolean = false,
)

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

    data class BlockHeaderClicked(val block: SoundBlock) : SoundIntent

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

    /** «Запомнить для …»: the difference goes into the latency of these headphones. */
    data object BackingRememberClicked : SoundIntent
}

sealed interface SoundEffect {
    data object Close : SoundEffect

    data class Share(val sessionId: Long) : SoundEffect
}
