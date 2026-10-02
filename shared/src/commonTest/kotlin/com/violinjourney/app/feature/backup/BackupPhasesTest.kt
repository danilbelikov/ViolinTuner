package com.violinjourney.app.feature.backup

import com.violinjourney.app.core.backup.BackupCounts
import com.violinjourney.app.core.backup.BackupJob
import com.violinjourney.app.core.backup.BackupManifest
import com.violinjourney.app.core.backup.BackupPart
import com.violinjourney.app.core.backup.BackupProgress
import com.violinjourney.app.core.backup.RestorePhase
import com.violinjourney.app.core.backup.SaveFailure
import com.violinjourney.app.core.backup.underWay
import com.violinjourney.app.shared.resources.Res
import com.violinjourney.app.shared.resources.backup_part_data_short
import com.violinjourney.app.shared.resources.backup_phase_check
import com.violinjourney.app.shared.resources.backup_phase_part
import com.violinjourney.app.shared.resources.restore_step_extracting
import com.violinjourney.app.shared.resources.restore_step_finishing
import com.violinjourney.app.shared.resources.restore_step_verifying
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The phase of a copy or a restore on its way, read from the job itself (spec 3.36.8; 3.20 «Вход» promised it in the row of «Данные»):
 * «Разбор, занятия, прогресс» before and over the data, «Видео 7 из 12» over the files of a part, «Проверка» once the archive is read
 * back, the steps of a restore — and a restore unpacked and waiting for its restart, still on its way; the percent of the bytes; which
 * row of «Данные» waits for the other; the words of each phase.
 */
class BackupPhasesTest {
    private fun saving(progress: BackupProgress? = null, verifying: Boolean = false) =
        BackupJob.Saving("копия.zip", visible = true, verifying = verifying, progress = progress)

    /** The passport of a copy, for the jobs that carry one. */
    private val manifest = BackupManifest(1, "1.0", 13, 0L, "Pixel 10a", setOf(BackupPart.DATA), BackupCounts(), mapOf(BackupPart.DATA to 100L))

    /** [done] of 100 bytes moved, the file [index] of [count] of the [part]. */
    private fun progress(part: BackupPart, index: Int, count: Int, done: Long) = BackupProgress(part, index, count, doneBytes = done, totalBytes = 100)

    @Test
    fun `a copy before its first progress and over its data is on the data`() {
        assertEquals(JobPhase.Data, BackupPhases.current(saving()))
        assertEquals(JobPhase.Data, BackupPhases.current(saving(progress(BackupPart.DATA, 1, 3, 2))))
    }

    @Test
    fun `a copy over the files of a part says the part and the number of the file`() {
        assertEquals(JobPhase.Files(BackupPart.VIDEO, 7, 12), BackupPhases.current(saving(progress(BackupPart.VIDEO, 7, 12, 56))))
        assertEquals(JobPhase.Files(BackupPart.SHEETS, 3, 48), BackupPhases.current(saving(progress(BackupPart.SHEETS, 3, 48, 20))))
    }

    @Test
    fun `a copy whose archive is read back is on its check whatever its last progress`() {
        assertEquals(JobPhase.Check, BackupPhases.current(saving(progress(BackupPart.VIDEO, 12, 12, 100), verifying = true)))
        assertEquals(JobPhase.Check, BackupPhases.current(saving(verifying = true)))
    }

    @Test
    fun `a copy on its way is running with its percent and phase`() {
        val running = BackupPhases.runningOf(saving(progress(BackupPart.VIDEO, 7, 12, 56)))
        assertEquals(DataRunning(restore = false, percent = 56, phase = JobPhase.Files(BackupPart.VIDEO, 7, 12)), running)
        assertEquals(DataRunning(restore = false, percent = 0, phase = JobPhase.Data), BackupPhases.runningOf(saving()))
    }

    @Test
    fun `a restore on its way is running with its step`() {
        val restoring = BackupJob.Restoring(RestorePhase.EXTRACTING, checked = false, progress = progress(BackupPart.AUDIO, 4, 9, 38))
        assertEquals(DataRunning(restore = true, percent = 38, phase = JobPhase.Restore(RestorePhase.EXTRACTING)), BackupPhases.runningOf(restoring))
        val checking = BackupJob.Restoring(RestorePhase.VERIFYING, checked = true)
        assertEquals(DataRunning(restore = true, percent = 0, phase = JobPhase.Restore(RestorePhase.VERIFYING)), BackupPhases.runningOf(checking))
    }

    @Test
    fun `a restore unpacked and waiting for its restart is still on its way`() {
        // the manager starts nothing over it (underWay): the copy waits for it, and its row leads to the screen that restarts the app
        val restored = BackupJob.Restored(manifest)
        assertTrue(restored.underWay)
        assertEquals(DataRunning(restore = true, percent = 100, phase = JobPhase.Restore(RestorePhase.FINISHING)), BackupPhases.runningOf(restored))
        assertTrue(DataBlockState(dateRead = true, running = BackupPhases.runningOf(restored)).saveWaits, "«Сохранить копию» waits for the restart")
    }

    @Test
    fun `nothing is running when no job is on its way`() {
        assertNull(BackupPhases.runningOf(BackupJob.Idle))
        assertNull(BackupPhases.runningOf(BackupJob.SaveFailed(SaveFailure.NO_SPACE)))
        assertNull(BackupPhases.runningOf(BackupJob.RestoreFailed(dataIntact = true, uri = "u", manifest = manifest, checked = false)))
    }

    /** Every job the manager can show: what is on its way is exactly what the manager starts nothing over. */
    @Test
    fun `the row runs exactly while the manager starts nothing else`() {
        val jobs = listOf(
            BackupJob.Idle,
            saving(),
            BackupJob.Saved("копия.zip", bytes = 100, place = null, manifest = manifest),
            BackupJob.SaveFailed(SaveFailure.FAILED),
            BackupJob.Restoring(RestorePhase.VERIFYING, checked = true),
            BackupJob.Restored(manifest),
            BackupJob.RestoreFailed(dataIntact = false, uri = "u", manifest = manifest, checked = true),
        )
        jobs.forEach { job -> assertEquals(job.underWay, BackupPhases.runningOf(job) != null, "$job") }
    }

    @Test
    fun `each phase has its words`() {
        assertEquals(Res.string.backup_part_data_short.key, BackupPhases.wordsOf(JobPhase.Data).key)
        assertEquals(Res.string.backup_phase_part.key, BackupPhases.wordsOf(JobPhase.Files(BackupPart.VIDEO, 7, 12)).key)
        assertEquals(Res.string.backup_phase_check.key, BackupPhases.wordsOf(JobPhase.Check).key)
        assertEquals(Res.string.restore_step_verifying.key, BackupPhases.wordsOf(JobPhase.Restore(RestorePhase.VERIFYING)).key)
        assertEquals(Res.string.restore_step_extracting.key, BackupPhases.wordsOf(JobPhase.Restore(RestorePhase.EXTRACTING)).key)
        assertEquals(Res.string.restore_step_finishing.key, BackupPhases.wordsOf(JobPhase.Restore(RestorePhase.FINISHING)).key)
    }

    @Test
    fun `the percent is of the bytes moved and whole`() {
        assertEquals(0, BackupPhases.percentOf(null))
        assertEquals(56, BackupPhases.percentOf(progress(BackupPart.VIDEO, 7, 12, 56)))
        assertEquals(100, BackupPhases.percentOf(progress(BackupPart.VIDEO, 12, 12, 100)))
        assertEquals(99, BackupPhases.percentOf(BackupProgress(BackupPart.VIDEO, 12, 12, doneBytes = 999, totalBytes = 1_000)), "rounded down: 100 only when all is moved")
    }

    @Test
    fun `while a copy is on its way the restore waits and while a restore is the copy waits`() {
        val copying = DataBlockState(dateRead = true, running = BackupPhases.runningOf(saving()))
        assertTrue(copying.restoreWaits)
        assertFalse(copying.saveWaits)
        val restoring = DataBlockState(dateRead = true, running = BackupPhases.runningOf(BackupJob.Restoring(RestorePhase.EXTRACTING, checked = false)))
        assertTrue(restoring.saveWaits)
        assertFalse(restoring.restoreWaits)
        val idle = DataBlockState(dateRead = true)
        assertFalse(idle.saveWaits)
        assertFalse(idle.restoreWaits)
    }
}
