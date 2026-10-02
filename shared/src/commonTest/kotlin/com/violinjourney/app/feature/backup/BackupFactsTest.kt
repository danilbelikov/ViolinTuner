package com.violinjourney.app.feature.backup

import com.violinjourney.app.core.backup.BackupContents
import com.violinjourney.app.core.backup.BackupCounts
import com.violinjourney.app.core.backup.BackupJob
import com.violinjourney.app.core.backup.BackupManifest
import com.violinjourney.app.core.backup.BackupPart
import com.violinjourney.app.core.backup.BackupProgress
import com.violinjourney.app.core.backup.RestorePhase
import com.violinjourney.app.core.backup.SaveFailure
import com.violinjourney.app.feature.backup.BackupFacts.Chip
import com.violinjourney.app.feature.backup.BackupFacts.Fact
import com.violinjourney.app.feature.backup.BackupFacts.Missing
import com.violinjourney.app.feature.backup.BackupFacts.Step
import com.violinjourney.app.feature.backup.BackupFacts.StepName
import com.violinjourney.app.feature.backup.BackupFacts.StepState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * What the screens of a copy and a restore say, as rules (spec 3.36.8, 5.29 R8): no chip and no word of a loss says a zero; a copy's
 * chips say videos and pages only when they are in it, and what it left out only when the data had it; «Без видео копия займёт …» only
 * for a place that was full under a copy that had video of some weight; «≈» from a megabyte up; the line of phases in the order a copy
 * is written, the parts without files left out, «Проверка» last, the step of now by the phase of the job; «Проверяем» only on the way
 * without a safety net.
 */
class BackupFactsTest {
    private val all = BackupPart.entries.toSet()
    private val counts = BackupCounts(sessions = 64, takes = 6, pieces = 12, pages = 48, practiceDays = 41, trophies = 3, level = 9, withSound = 50, videos = 6)

    private fun manifest(parts: Set<BackupPart> = all, counts: BackupCounts = this.counts) =
        BackupManifest(1, "1.4", 13, 0L, "Pixel 10a", parts + BackupPart.DATA, counts, mapOf(BackupPart.DATA to 12L, BackupPart.VIDEO to 3_000L))

    private fun facts(vararg pairs: Pair<Fact, Int>) = pairs.map { (fact, count) -> Chip(fact, count) }

    @Test
    fun `a saved copy says its days recordings videos pieces and pages`() {
        val chips = BackupFacts.savedChips(manifest())
        assertEquals(facts(Fact.DAYS to 41, Fact.SESSIONS to 64, Fact.VIDEOS to 6, Fact.PIECES to 12, Fact.PAGES to 48), chips.facts)
        assertTrue(chips.missing.isEmpty(), "nothing is left out")
    }

    @Test
    fun `no chip and no word of a loss says a zero`() {
        val few = BackupCounts(sessions = 5, pieces = 0, pages = 0, practiceDays = 0, level = 1, withSound = 0, videos = 0)
        assertEquals(facts(Fact.SESSIONS to 5), BackupFacts.savedChips(manifest(counts = few)).facts)
        assertEquals(facts(Fact.LEVEL to 1, Fact.SESSIONS to 5), BackupFacts.passportChips(manifest(counts = few)).facts, "the level is never zero")
        assertEquals(facts(Fact.LEVEL to 1, Fact.SESSIONS to 5), BackupFacts.nowChips(few))
        assertEquals(facts(Fact.SESSIONS to 5), BackupFacts.lost(few))
        assertEquals(facts(Fact.PIECES to 2, Fact.DAYS to 38), BackupFacts.lost(BackupCounts(pieces = 2, practiceDays = 38)))
    }

    @Test
    fun `no chip says a zero of recordings either`() {
        // pieces and days without a single recording: the chips of the copy, of the passport and of the app now leave them out
        val noRecordings = BackupCounts(pieces = 2, practiceDays = 3, level = 2)
        assertEquals(facts(Fact.DAYS to 3, Fact.PIECES to 2), BackupFacts.savedChips(manifest(counts = noRecordings)).facts)
        assertEquals(facts(Fact.DAYS to 3, Fact.LEVEL to 2, Fact.PIECES to 2), BackupFacts.nowChips(noRecordings))
        assertEquals(facts(Fact.DAYS to 3, Fact.LEVEL to 2, Fact.PIECES to 2), BackupFacts.passportChips(manifest(counts = noRecordings)).facts)
    }

    @Test
    fun `a number keeps to the word after it and nothing else does`() {
        val nb = ' '
        assertEquals(
            "64${nb}записи · 41${nb}день занятий · 3${nb}трофея · 12${nb}МБ",
            BackupFacts.keptNumbers("64 записи · 41 день занятий · 3 трофея · 12 МБ"),
            "a caption breaks between its counts and inside a phrase, never after a number",
        )
        assertEquals("Kopie vom 12.${nb}September 2025", BackupFacts.keptNumbers("Kopie vom 12. September 2025"), "a German day keeps to its month")
        assertEquals("Копия от 12${nb}сентября", BackupFacts.keptNumbers("Копия от 12 сентября"), "only after a number: «от» still breaks")
        assertEquals("≈${nb}3,4${nb}ГБ · Загрузки", BackupFacts.keptNumbers("≈${nb}3,4 ГБ · Загрузки"))
        assertEquals("不到 1${nb}MB", BackupFacts.keptNumbers("不到 1 MB"))
        assertEquals("без видео", BackupFacts.keptNumbers("без видео"), "words without numbers are as they were")
    }

    @Test
    fun `a loss names recordings pieces and days in this order`() {
        assertEquals(facts(Fact.SESSIONS to 64, Fact.PIECES to 12, Fact.DAYS to 41), BackupFacts.lost(counts))
    }

    @Test
    fun `videos and pages are chips only when they are in the copy`() {
        // the counts of a passport are of the whole app: six videos in the app are not six videos in a copy without video
        val withoutMedia = BackupFacts.savedChips(manifest(parts = setOf(BackupPart.AUDIO)))
        assertEquals(facts(Fact.DAYS to 41, Fact.SESSIONS to 64, Fact.PIECES to 12), withoutMedia.facts)
        assertEquals(listOf(Missing.VIDEO, Missing.SHEETS), withoutMedia.missing)
    }

    @Test
    fun `what is left out is missed only when the data had it`() {
        val nothingOf = BackupCounts(sessions = 3, practiceDays = 2, withSound = 0, videos = 0, pages = 0, pieces = 4)
        assertTrue(BackupFacts.savedChips(manifest(parts = emptySet(), counts = nothingOf)).missing.isEmpty(), "no videos, no sound, no pages: nothing is missed")
        assertEquals(listOf(Missing.VIDEO, Missing.AUDIO, Missing.SHEETS), BackupFacts.passportChips(manifest(parts = emptySet())).missing)
        assertEquals(listOf(Missing.AUDIO), BackupFacts.passportChips(manifest(parts = all - BackupPart.AUDIO)).missing)
        // pieces without a single page of sheets: no «без фото нот»
        val noPages = counts.copy(pages = 0)
        assertEquals(listOf(Missing.VIDEO), BackupFacts.passportChips(manifest(parts = setOf(BackupPart.AUDIO), counts = noPages)).missing)
    }

    private val contents = BackupContents(counts, mapOf(BackupPart.DATA to 12_000_000L, BackupPart.SHEETS to 180_000_000L, BackupPart.AUDIO to 60_000_000L, BackupPart.VIDEO to 3_200_000_000L))

    @Test
    fun `without video is offered for a place that was full under a copy with video`() {
        val full = BackupJob.SaveFailed(SaveFailure.NO_SPACE, parts = all)
        assertEquals(252_000_000L, BackupFacts.withoutVideoBytes(full, contents))
        // the same copy without its sheets weighs without them too: the parts are the job's
        assertEquals(72_000_000L, BackupFacts.withoutVideoBytes(full.copy(parts = all - BackupPart.SHEETS), contents))
    }

    @Test
    fun `without video is not offered where it would not help or say the truth`() {
        // the phone itself was short for «Отправить…»: another place is what helps
        assertNull(BackupFacts.withoutVideoBytes(BackupJob.SaveFailed(SaveFailure.NO_SPACE, missingBytes = 190_000_000L, parts = all), contents))
        // no video in the copy that failed
        assertNull(BackupFacts.withoutVideoBytes(BackupJob.SaveFailed(SaveFailure.NO_SPACE, parts = all - BackupPart.VIDEO), contents))
        // video of no weight
        assertNull(BackupFacts.withoutVideoBytes(BackupJob.SaveFailed(SaveFailure.NO_SPACE, parts = all), contents.copy(bytes = contents.bytes + (BackupPart.VIDEO to 0L))))
        // another reason
        SaveFailure.entries.filter { it != SaveFailure.NO_SPACE }.forEach { reason ->
            assertNull(BackupFacts.withoutVideoBytes(BackupJob.SaveFailed(reason, parts = all), contents), "$reason")
        }
        // not weighed yet
        assertNull(BackupFacts.withoutVideoBytes(BackupJob.SaveFailed(SaveFailure.NO_SPACE, parts = all), null))
    }

    @Test
    fun `a weight is an estimate from a megabyte up`() {
        val megabyte = 1024L * 1024L
        assertFalse(BackupFacts.approximate(megabyte - 1), "«меньше 1 МБ» without «≈»")
        assertTrue(BackupFacts.approximate(megabyte))
        assertFalse(BackupFacts.approximate(0))
    }

    private fun saving(parts: Set<BackupPart> = all, filled: Set<BackupPart> = emptySet(), progress: BackupProgress? = null, verifying: Boolean = false) =
        BackupJob.Saving("копия.zip", visible = true, verifying = verifying, progress = progress, parts = parts + BackupPart.DATA, filled = filled)

    private fun progress(part: BackupPart, index: Int, count: Int) = BackupProgress(part, index, count, doneBytes = 56, totalBytes = 100)

    private fun part(part: BackupPart, state: StepState) = Step(StepName.Part(part), state)

    private fun phase(phase: JobPhase, state: StepState) = Step(StepName.Phase(phase), state)

    @Test
    fun `the line of a copy goes in the order of its writing with the check last`() {
        val video = saving(progress = progress(BackupPart.VIDEO, 7, 12), filled = all)
        assertEquals(
            listOf(
                phase(JobPhase.Data, StepState.DONE),
                part(BackupPart.SHEETS, StepState.DONE),
                part(BackupPart.AUDIO, StepState.DONE),
                phase(JobPhase.Files(BackupPart.VIDEO, 7, 12), StepState.CURRENT),
                phase(JobPhase.Check, StepState.NEXT),
            ),
            BackupFacts.copySteps(video),
        )
    }

    @Test
    fun `the parts of a copy without files are not on its line`() {
        // a copy of the data and the video, with photos of sheets switched on but none in the app
        val job = saving(parts = setOf(BackupPart.SHEETS, BackupPart.VIDEO), filled = setOf(BackupPart.DATA, BackupPart.VIDEO), progress = progress(BackupPart.DATA, 1, 2))
        assertEquals(
            listOf(phase(JobPhase.Data, StepState.CURRENT), part(BackupPart.VIDEO, StepState.NEXT), phase(JobPhase.Check, StepState.NEXT)),
            BackupFacts.copySteps(job),
        )
        // before its files are listed every part of the copy is on its line, and only those: the sound was switched off
        assertEquals(
            listOf(phase(JobPhase.Data, StepState.CURRENT), part(BackupPart.SHEETS, StepState.NEXT), part(BackupPart.VIDEO, StepState.NEXT), phase(JobPhase.Check, StepState.NEXT)),
            BackupFacts.copySteps(saving(parts = setOf(BackupPart.SHEETS, BackupPart.VIDEO))),
        )
    }

    @Test
    fun `the step of now follows the phase of the copy`() {
        val sheets = BackupFacts.copySteps(saving(filled = all, progress = progress(BackupPart.SHEETS, 3, 48)))
        assertEquals(listOf(StepState.DONE, StepState.CURRENT, StepState.NEXT, StepState.NEXT, StepState.NEXT), sheets.map { it.state })
        assertEquals(StepName.Phase(JobPhase.Files(BackupPart.SHEETS, 3, 48)), sheets[1].name, "the part being moved says the number of its file")
        val check = BackupFacts.copySteps(saving(filled = all, progress = progress(BackupPart.VIDEO, 12, 12), verifying = true))
        assertEquals(listOf(StepState.DONE, StepState.DONE, StepState.DONE, StepState.DONE, StepState.CURRENT), check.map { it.state })
        assertEquals(part(BackupPart.VIDEO, StepState.DONE), check[3], "a part behind says its name alone")
        val first = BackupFacts.copySteps(saving(filled = all))
        assertEquals(listOf(StepState.CURRENT, StepState.NEXT, StepState.NEXT, StepState.NEXT, StepState.NEXT), first.map { it.state })
    }

    private fun restoring(phase: RestorePhase, checked: Boolean) = BackupJob.Restoring(phase, checked)

    @Test
    fun `checking is a step only on the way without a safety net`() {
        assertEquals(
            listOf(phase(JobPhase.Restore(RestorePhase.EXTRACTING), StepState.CURRENT), phase(JobPhase.Restore(RestorePhase.FINISHING), StepState.NEXT)),
            BackupFacts.restoreSteps(restoring(RestorePhase.EXTRACTING, checked = false)),
        )
        assertEquals(
            listOf(StepState.CURRENT, StepState.NEXT, StepState.NEXT),
            BackupFacts.restoreSteps(restoring(RestorePhase.VERIFYING, checked = true)).map { it.state },
        )
        assertEquals(
            listOf(StepState.DONE, StepState.DONE, StepState.CURRENT),
            BackupFacts.restoreSteps(restoring(RestorePhase.FINISHING, checked = true)).map { it.state },
        )
    }
}
