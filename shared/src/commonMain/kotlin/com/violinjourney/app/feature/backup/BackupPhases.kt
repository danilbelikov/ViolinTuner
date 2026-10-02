package com.violinjourney.app.feature.backup

import com.violinjourney.app.core.backup.BackupJob
import com.violinjourney.app.core.backup.BackupPart
import com.violinjourney.app.core.backup.BackupProgress
import com.violinjourney.app.core.backup.RestorePhase
import com.violinjourney.app.shared.resources.Res
import com.violinjourney.app.shared.resources.backup_part_data_short
import com.violinjourney.app.shared.resources.backup_phase_check
import com.violinjourney.app.shared.resources.backup_phase_part
import com.violinjourney.app.shared.resources.restore_step_extracting
import com.violinjourney.app.shared.resources.restore_step_finishing
import com.violinjourney.app.shared.resources.restore_step_verifying
import org.jetbrains.compose.resources.StringResource

/**
 * What a copy or a restore is doing, from the job itself (spec 3.36.8; 3.20 «Вход» promised the phase in the row of «Данные», which
 * said only the percent until stage 121): the row of «Данные» reads it, and the line of phases of the screens of a copy and a restore
 * ([BackupFacts.copySteps], stage 122) marks its step of now by it. Pure.
 */
object BackupPhases {
    private const val PERCENT = 100

    /**
     * The phase of a copy: «Проверка» while the archive is read back; «Разбор, занятия, прогресс» before the first progress and over
     * the data; else the part whose file is moved, with its number and how many it has — as the progress of the copy counts them.
     */
    fun current(job: BackupJob.Saving): JobPhase {
        if (job.verifying) return JobPhase.Check
        val progress = job.progress ?: return JobPhase.Data
        return if (progress.part == BackupPart.DATA) JobPhase.Data else JobPhase.Files(progress.part, progress.index, progress.count)
    }

    /**
     * A copy or a restore on its way — its percent and phase; null for any other job, done or failed. A restore unpacked and marked
     * that waits for its restart ([BackupJob.Restored]) is still on its way, as the manager has it ([com.violinjourney.app.core.backup.underWay]):
     * nothing else starts over it, so the copy waits, saying why, and the row of the restore — «100 % · Почти готово» — leads to its
     * screen, which brings it to the restart (the process kept alive after its task was swiped away, stage 121).
     */
    fun runningOf(job: BackupJob): DataRunning? = when (job) {
        is BackupJob.Saving -> DataRunning(restore = false, percent = percentOf(job.progress), phase = current(job))
        is BackupJob.Restoring -> DataRunning(restore = true, percent = percentOf(job.progress), phase = JobPhase.Restore(job.phase))
        is BackupJob.Restored -> DataRunning(restore = true, percent = PERCENT, phase = JobPhase.Restore(RestorePhase.FINISHING))
        else -> null
    }

    /** Whole percents of the bytes moved, rounded down: 100 only when all are. */
    fun percentOf(progress: BackupProgress?): Int = ((progress?.fraction ?: 0f) * PERCENT).toInt()

    /**
     * The words of a phase: «Разбор, занятия, прогресс»; «%1$s %2$d из %3$d» — the short name of the part, the number of its file and
     * how many it has («Видео 7 из 12»); «Проверка»; the step of a restore — «Проверяем», «Восстанавливаем», «Почти готово».
     */
    fun wordsOf(phase: JobPhase): StringResource = when (phase) {
        JobPhase.Data -> Res.string.backup_part_data_short
        is JobPhase.Files -> Res.string.backup_phase_part
        JobPhase.Check -> Res.string.backup_phase_check
        is JobPhase.Restore -> when (phase.phase) {
            RestorePhase.VERIFYING -> Res.string.restore_step_verifying
            RestorePhase.EXTRACTING -> Res.string.restore_step_extracting
            RestorePhase.FINISHING -> Res.string.restore_step_finishing
        }
    }
}
