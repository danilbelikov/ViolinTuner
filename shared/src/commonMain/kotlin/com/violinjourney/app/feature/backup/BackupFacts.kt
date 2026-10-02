package com.violinjourney.app.feature.backup

import com.violinjourney.app.core.backup.BackupContents
import com.violinjourney.app.core.backup.BackupCounts
import com.violinjourney.app.core.backup.BackupJob
import com.violinjourney.app.core.backup.BackupManifest
import com.violinjourney.app.core.backup.BackupPart
import com.violinjourney.app.core.backup.RestorePhase
import com.violinjourney.app.core.backup.SaveFailure

/**
 * What the screens of a copy and of a restore say (spec 3.36.8, 5.29 R8), as rules without words: the chips of «Копия сохранена», of
 * the passport and of «Сейчас в приложении»; what «Заменить данные?» names; the weight «Без видео копия займёт ≈ …» of a copy that did
 * not fit where it went; whether a weight is an estimate; the line of phases of a copy and of a restore. Pure; the words are the
 * screens'.
 */
object BackupFacts {
    /** A count a chip says — on a card, in this order: days of practice, the level, recordings, videos, pieces, pages of sheets. */
    enum class Fact { DAYS, LEVEL, SESSIONS, VIDEOS, PIECES, PAGES }

    data class Chip(val fact: Fact, val count: Int)

    /**
     * A part left out of a copy whose kind of thing the data had: a dashed chip — «без видео» where there were videos, «без звука» where
     * there were recordings with sound, «без фото нот» where there were pages of sheets. A part that had nothing is not missed.
     */
    enum class Missing { VIDEO, AUDIO, SHEETS }

    /** The chips of a card: what is in it, filled, and what is not, dashed after them. */
    data class Chips(val facts: List<Chip>, val missing: List<Missing>)

    /** How far a job is past a step of its line of phases. */
    enum class StepState { DONE, CURRENT, NEXT }

    /**
     * What a step of the line of phases is called: [Phase] — a phase in its words ([BackupPhases.wordsOf]), the part being moved with
     * the number of its file («Видео 7 из 12»); [Part] — a part of the copy not being moved now, by its short name alone.
     */
    sealed interface StepName {
        data class Phase(val phase: JobPhase) : StepName

        data class Part(val part: BackupPart) : StepName
    }

    data class Step(val name: StepName, val state: StepState)

    /** A megabyte, as [com.violinjourney.app.core.ui.format.Formats.fileSize] counts it: below it a weight is «меньше 1 МБ». */
    private const val BYTES_PER_MB = 1024L * 1024L

    /**
     * The chips of «Копия сохранена» (spec 3.36.8): days of practice, recordings and pieces where there are any — the counts of the
     * passport are of the whole app; videos only when video is in the copy, pages only when the photos of sheets are ([manifest] parts);
     * and dashed, the parts left out whose kind of thing the app had ([missing]).
     */
    fun savedChips(manifest: BackupManifest): Chips {
        val counts = manifest.counts
        val parts = manifest.parts
        val facts = listOfNotNull(
            Chip(Fact.DAYS, counts.practiceDays).takeIf { counts.practiceDays > 0 },
            Chip(Fact.SESSIONS, counts.sessions).takeIf { counts.sessions > 0 },
            Chip(Fact.VIDEOS, counts.videos).takeIf { BackupPart.VIDEO in parts && counts.videos > 0 },
            Chip(Fact.PIECES, counts.pieces).takeIf { counts.pieces > 0 },
            Chip(Fact.PAGES, counts.pages).takeIf { BackupPart.SHEETS in parts && counts.pages > 0 },
        )
        return Chips(facts, missing(manifest))
    }

    /**
     * The chips of the passport of a copy and of «Данные восстановлены» (spec 3.36.8): days of practice, the level, recordings and pieces
     * — the counts that are not zero; the level is never zero — and, dashed, the parts left out whose kind of thing the data had.
     */
    fun passportChips(manifest: BackupManifest): Chips = Chips(nowChips(manifest.counts), missing(manifest))

    /** The chips of «Сейчас в приложении»: the same counts as the passport's, side by side with them. */
    fun nowChips(counts: BackupCounts): List<Chip> = listOfNotNull(
        Chip(Fact.DAYS, counts.practiceDays).takeIf { counts.practiceDays > 0 },
        Chip(Fact.LEVEL, counts.level),
        Chip(Fact.SESSIONS, counts.sessions).takeIf { counts.sessions > 0 },
        Chip(Fact.PIECES, counts.pieces).takeIf { counts.pieces > 0 },
    )

    /**
     * What a restore takes away, as «Заменить данные?» and «Удалить всё и восстановить?» name it (spec 3.36.8): recordings, pieces, days
     * of practice — only those there are: «5 записей · 38 дней занятий», never «0 произведений».
     */
    fun lost(counts: BackupCounts): List<Chip> = listOfNotNull(
        Chip(Fact.SESSIONS, counts.sessions).takeIf { counts.sessions > 0 },
        Chip(Fact.PIECES, counts.pieces).takeIf { counts.pieces > 0 },
        Chip(Fact.DAYS, counts.practiceDays).takeIf { counts.practiceDays > 0 },
    )

    /** The parts left out of the copy of [manifest] whose kind of thing its data had. */
    private fun missing(manifest: BackupManifest): List<Missing> {
        val counts = manifest.counts
        val parts = manifest.parts
        return listOfNotNull(
            Missing.VIDEO.takeIf { BackupPart.VIDEO !in parts && counts.videos > 0 },
            Missing.AUDIO.takeIf { BackupPart.AUDIO !in parts && counts.withSound > 0 },
            Missing.SHEETS.takeIf { BackupPart.SHEETS !in parts && counts.pages > 0 },
        )
    }

    /**
     * What the same copy weighs without its video — «Без видео копия займёт ≈ 252 МБ.» (spec 3.36.8): only for a copy that did not fit
     * where it went ([SaveFailure.NO_SPACE] with nothing missing in the phone itself — a place the app knows nothing about), had video
     * in it ([BackupJob.SaveFailed.parts]: a screen opened anew knows it from the job) and video that weighs something; null otherwise,
     * and while what the app holds is not weighed yet.
     */
    fun withoutVideoBytes(job: BackupJob.SaveFailed, contents: BackupContents?): Long? {
        if (job.reason != SaveFailure.NO_SPACE || job.missingBytes > 0 || BackupPart.VIDEO !in job.parts) return null
        val weighed = contents ?: return null
        if ((weighed.bytes[BackupPart.VIDEO] ?: 0L) <= 0L) return null
        return weighed.bytesOf(job.parts - BackupPart.VIDEO)
    }

    /**
     * Whether a weight is said as an estimate, «≈ 252 МБ»: from a megabyte up. Below it the weight is «меньше 1 МБ» already — an
     * estimate said twice in «≈ меньше 1 МБ» (spec 3.36.8).
     */
    fun approximate(bytes: Long): Boolean = bytes >= BYTES_PER_MB

    /** The space that keeps its two words on one line. */
    private const val NO_BREAK_SPACE = ' '

    /**
     * [text] with each number kept to the word after it (spec 5.29 R6: «число и единица не разрываются»; R8, review of stage 122): the
     * space right after a number — «3 трофея», «12 МБ», «≈ 252 МБ», de «12. September» — becomes a no-break space, so a line never ends
     * on «3» with «трофея» on the next. Only after a number: «Копия от 12» and «September 2025» still break, and the separator « · » stays
     * where a caption breaks. The words of the counts, of the weights and of the dates come from the translations and from the formats
     * of every language, so it is done here, once, over what they make.
     */
    fun keptNumbers(text: String): String {
        if (' ' !in text) return text
        val kept = StringBuilder(text.length)
        text.forEachIndexed { i, char ->
            val before = text.getOrNull(i - 1)
            val afterNumber = before != null && (before.isDigit() || (before == '.' && text.getOrNull(i - 2)?.isDigit() == true))
            kept.append(if (char == ' ' && afterNumber) NO_BREAK_SPACE else char)
        }
        return kept.toString()
    }

    /**
     * The line of phases of a copy (spec 3.36.8): the parts it writes, in the order they are written — the data always, then each part of
     * the copy that has files ([BackupJob.Saving.filled]; before its files are listed — every part of it), «Проверка» last. Behind the
     * phase of now ([BackupPhases.current]) — done; the part being moved says the number of its file; after it — next.
     */
    fun copySteps(job: BackupJob.Saving): List<Step> {
        val filled = job.filled.ifEmpty { job.parts }
        val parts = BackupPart.entries.filter { it == BackupPart.DATA || (it in job.parts && it in filled) }
        val now = BackupPhases.current(job)
        val steps = parts.map { part ->
            when (now) {
                JobPhase.Data -> Step(nameOf(part), if (part == BackupPart.DATA) StepState.CURRENT else StepState.NEXT)
                is JobPhase.Files -> when {
                    part == now.part -> Step(StepName.Phase(now), StepState.CURRENT)
                    part.ordinal < now.part.ordinal -> Step(nameOf(part), StepState.DONE)
                    else -> Step(nameOf(part), StepState.NEXT)
                }
                JobPhase.Check, is JobPhase.Restore -> Step(nameOf(part), StepState.DONE)
            }
        }
        val check = Step(StepName.Phase(JobPhase.Check), if (now == JobPhase.Check) StepState.CURRENT else StepState.NEXT)
        return steps + check
    }

    /** A part named on the line while it is not being moved: the data by their phase, the others by the short name of the part. */
    private fun nameOf(part: BackupPart): StepName = if (part == BackupPart.DATA) StepName.Phase(JobPhase.Data) else StepName.Part(part)

    /**
     * The line of phases of a restore (spec 3.36.8): «Проверяем» only on the way without a safety net ([BackupJob.Restoring.checked]),
     * «Восстанавливаем», «Почти готово» — no part and no number of a file: the percent and the gigabytes say how far.
     */
    fun restoreSteps(job: BackupJob.Restoring): List<Step> =
        RestorePhase.entries.filter { it != RestorePhase.VERIFYING || job.checked }.map { phase ->
            val state = when {
                phase.ordinal < job.phase.ordinal -> StepState.DONE
                phase == job.phase -> StepState.CURRENT
                else -> StepState.NEXT
            }
            Step(StepName.Phase(JobPhase.Restore(phase)), state)
        }
}
