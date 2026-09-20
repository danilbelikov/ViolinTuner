package com.example.violintuner.feature.backup

import com.example.violintuner.core.backup.BackupCandidate
import com.example.violintuner.core.backup.BackupContents
import com.example.violintuner.core.backup.BackupFileProblem
import com.example.violintuner.core.backup.BackupJob
import com.example.violintuner.core.backup.BackupPart

/** The screen «Копия данных» (spec 3.20): what goes into the copy, its making, and how it ended. */
data class BackupState(
    /** Null until what is in the app has been counted and weighed. */
    val contents: BackupContents? = null,
    val parts: Set<BackupPart> = BackupPart.entries.toSet(),
    /** A take is being recorded, or a video analysed: a copy does not start under them. */
    val busy: Boolean = false,
    val job: BackupJob = BackupJob.Idle,
    val stopDialog: Boolean = false,
    /** Up to this a copy may go through the ordinary «Поделиться». */
    val shareUpToBytes: Long = 0,
) {
    val totalBytes: Long get() = contents?.bytesOf(parts) ?: 0
    val canShare: Boolean get() = contents != null && totalBytes <= shareUpToBytes
    val nothingToSave: Boolean get() = contents?.counts?.isEmpty == true
}

sealed interface BackupIntent {
    data class PartToggled(val part: BackupPart) : BackupIntent

    /** «Сохранить в…»: the system's «Сохранить как…» comes up. */
    data object SaveClicked : BackupIntent

    /** What the system window came back with; null when the person backed out. */
    data class PlacePicked(val uri: String?, val fileName: String) : BackupIntent

    data class ShareClicked(val fileName: String) : BackupIntent

    data object CancelClicked : BackupIntent

    data object StopDismissed : BackupIntent

    data object StopConfirmed : BackupIntent

    /** «Готово» and «Закрыть». */
    data object DoneClicked : BackupIntent

    data object RetryClicked : BackupIntent

    data object BackClicked : BackupIntent
}

sealed interface BackupEffect {
    data object PickPlace : BackupEffect

    data class ShareFile(val path: String) : BackupEffect

    data object Close : BackupEffect
}

enum class RestoreDialog { REPLACE, UNSAFE, STOP }

sealed interface RestoreStage {
    data object Reading : RestoreStage

    data class Unfit(val problem: BackupFileProblem) : RestoreStage

    /** The passport of the copy beside what is in the app now: this is changed for that. */
    data class Ready(val copy: BackupCandidate.Copy, val current: BackupContents) : RestoreStage
}

data class RestoreState(
    val stage: RestoreStage = RestoreStage.Reading,
    val busy: Boolean = false,
    val job: BackupJob = BackupJob.Idle,
    val dialog: RestoreDialog? = null,
    /** «Открываем ваши данные…»: the process is about to start anew under this screen. */
    val opening: Boolean = false,
)

sealed interface RestoreIntent {
    data object RestoreClicked : RestoreIntent

    data object UnsafeClicked : RestoreIntent

    data object DialogConfirmed : RestoreIntent

    data object DialogDismissed : RestoreIntent

    data object PickAnotherClicked : RestoreIntent

    data class FilePicked(val uri: String?) : RestoreIntent

    data object SaveFirstClicked : RestoreIntent

    data object CancelClicked : RestoreIntent

    /** «Ещё раз» and «Ещё раз с этим файлом». */
    data object RetryClicked : RestoreIntent

    data object StartCleanClicked : RestoreIntent

    data object CloseClicked : RestoreIntent
}

sealed interface RestoreEffect {
    data object PickFile : RestoreEffect

    data object OpenBackup : RestoreEffect

    data object Close : RestoreEffect

    /** The copy lies unpacked and marked: start the process anew, and it will find itself restored. */
    data object Restart : RestoreEffect
}

/** The block «Данные» of the settings. */
data class DataBlockState(
    val lastBackupAtEpochMs: Long? = null,
    /** Recordings made since a copy that has grown old; zero — nothing to hint at. */
    val newSinceStale: Int = 0,
    /** A copy or a restore is on its way: percent, for the thin bar of the row. */
    val runningPercent: Int? = null,
    val restoring: Boolean = false,
    val totalBytes: Long? = null,
)
