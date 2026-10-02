package com.violinjourney.app.feature.backup

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.tooling.preview.Preview
import com.violinjourney.app.core.backup.BackupCandidate
import com.violinjourney.app.core.backup.BackupContents
import com.violinjourney.app.core.backup.BackupCounts
import com.violinjourney.app.core.backup.BackupFileProblem
import com.violinjourney.app.core.backup.BackupJob
import com.violinjourney.app.core.backup.BackupManifest
import com.violinjourney.app.core.backup.BackupPart
import com.violinjourney.app.core.backup.BackupProgress
import com.violinjourney.app.core.backup.RestorePhase
import com.violinjourney.app.core.backup.SaveFailure
import com.violinjourney.app.core.ui.motion.LocalReduceMotion
import com.violinjourney.app.core.ui.theme.ViolinTheme
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn

// «Копия данных» and «Восстановить из копии» of the redesign (spec 3.36.8, 5.29 R8; start.html, 4 and 5): every face of both screens,
// the tight windows and the long words. The header and the bottom zone follow the window — upright previews are as tall as a phone;
// 640 × 360 lying is the 603 × 308 the emulator leaves the app behind its cutout and bars. Still frames: motion is removed.

private const val MB = 1024L * 1024L
private const val GB = 1024L * MB

/** Noon of a day in the zone of the machine: the passport says the day the person sees. */
private fun dayOf(year: Int, month: Int, day: Int): Long =
    LocalDate(year, month, day).atStartOfDayIn(TimeZone.currentSystemDefault()).toEpochMilliseconds() + 12 * 60 * 60 * 1_000L

/** «Today» of the previews: 2 October 2026. */
private val Today = LocalDate(2026, 10, 2)

/** The app of the mockups: 64 recordings, 41 days of practice, 12 pieces with 48 pages, 6 video takes — 3,4 ГБ. */
private val Counts = BackupCounts(sessions = 64, takes = 6, pieces = 12, pages = 48, practiceDays = 41, trophies = 3, level = 9, withSound = 50, videos = 6)
private val Contents = BackupContents(
    Counts,
    mapOf(BackupPart.DATA to 12 * MB, BackupPart.SHEETS to 180 * MB, BackupPart.AUDIO to 60 * MB, BackupPart.VIDEO to (3.2 * GB).toLong()),
)

/** A young app: a copy of it weighs less than a megabyte and may be sent. */
private val SmallContents = BackupContents(
    BackupCounts(sessions = 5, pieces = 2, pages = 1, practiceDays = 7, trophies = 0, level = 2, withSound = 3, videos = 0),
    mapOf(BackupPart.DATA to 300_000L, BackupPart.SHEETS to 200_000L, BackupPart.AUDIO to 400_000L, BackupPart.VIDEO to 0L),
)

private const val FILE = "Интонация · копия · 2 октября 2026.zip"
private const val SHARE_UP_TO = 200 * MB

@Composable
private fun Copy(state: BackupState, goesOnInBackground: Boolean = true) = ViolinTheme {
    CompositionLocalProvider(LocalReduceMotion provides true) {
        BackupScreen(state = state, fileName = FILE, onIntent = {}, goesOnInBackground = goesOnInBackground)
    }
}

private fun copyState(contents: BackupContents? = Contents, parts: Set<BackupPart> = BackupPart.entries.toSet(), job: BackupJob = BackupJob.Idle, busy: Boolean = false) =
    BackupState(contents = contents, parts = parts, busy = busy, job = job, shareUpToBytes = SHARE_UP_TO)

/** A copy of everything; the parts a job is made of are always said (5.29 R8). */
private val All = BackupPart.entries.toSet()

private val NoVideo = All - BackupPart.VIDEO

private fun manifest(parts: Set<BackupPart> = BackupPart.entries.toSet(), counts: BackupCounts = Counts, madeAt: Long = dayOf(2026, 9, 12), device: String = "Pixel 10a", version: String = "1.4") =
    BackupManifest(1, version, 13, madeAt, device, parts + BackupPart.DATA, counts, Contents.bytes)

private fun saving(part: BackupPart, index: Int, count: Int, verifying: Boolean = false) = BackupJob.Saving(
    FILE,
    visible = true,
    verifying = verifying,
    progress = BackupProgress(part, index, count, doneBytes = (1.9 * GB).toLong(), totalBytes = (3.4 * GB).toLong()),
    remainingSec = 180,
    parts = BackupPart.entries.toSet(),
    filled = BackupPart.entries.toSet(),
)

// ——— «Копия данных» ———

@Preview(name = "Copy · the parts: «≈ 3,4 ГБ», the shares, four rows, «Сохранить в…» and why not «Отправить…»", widthDp = 412, heightDp = 892, locale = "ru")
@Composable
private fun CopyChoosePreview() = Copy(copyState())

@Preview(name = "Copy · counting: a spinner for the total, no rows, the zone of a button held empty", widthDp = 412, heightDp = 892, locale = "ru")
@Composable
private fun CopyCountingPreview() = Copy(copyState(contents = null))

@Preview(name = "Copy · a young app: «меньше 1 МБ» without «≈», «Отправить…»", widthDp = 412, heightDp = 892, locale = "ru")
@Composable
private fun CopySmallPreview() = Copy(copyState(contents = SmallContents))

@Preview(name = "Copy · video off: its share grey, its dot ringed, the card of what it costs; «≈ 252 МБ» with «Отправить…» gone", widthDp = 412, heightDp = 892, locale = "ru")
@Composable
private fun CopyVideoOffPreview() = Copy(copyState(parts = NoVideo))

@Preview(name = "Copy · everything off but the data: three phrases in one card", widthDp = 412, heightDp = 892, locale = "ru")
@Composable
private fun CopyAllOffPreview() = Copy(copyState(parts = setOf(BackupPart.DATA)))

@Preview(name = "Copy · a take is recorded: «Сначала закончите запись» over the dimmed «Сохранить в…», no «Отправить…»", widthDp = 412, heightDp = 892, locale = "ru")
@Composable
private fun CopyBusyPreview() = Copy(copyState(contents = SmallContents, busy = true))

@Preview(name = "Copy · a short copy on its way: «Сохраняем…», «Отправить…» dimmed, the zone does not jump", widthDp = 412, heightDp = 892, locale = "ru")
@Composable
private fun CopyShortPreview() = Copy(copyState(contents = SmallContents, job = BackupJob.Saving(FILE, visible = false, parts = All)))

@Preview(name = "Copy · nothing to save: the tile with the archive in the middle, no zone", widthDp = 412, heightDp = 892, locale = "ru")
@Composable
private fun CopyNothingPreview() = Copy(copyState(contents = BackupContents(BackupCounts(), mapOf(BackupPart.DATA to 40_000L))))

@Preview(name = "Copy · on its way: «… · Видео 7 из 12 · Проверка», 56 %, two cards, «Отменить»", widthDp = 412, heightDp = 892, locale = "ru")
@Composable
private fun CopyProgressPreview() = Copy(copyState(job = saving(BackupPart.VIDEO, 7, 12)))

@Preview(name = "Copy · its check: «Отменить» asleep under «Проверяем файл»", widthDp = 412, heightDp = 892, locale = "ru")
@Composable
private fun CopyCheckPreview() = Copy(copyState(job = saving(BackupPart.VIDEO, 12, 12, verifying = true)))

@Preview(name = "Copy · on its way on iOS: «Не сворачивайте приложение…»", widthDp = 412, heightDp = 892, locale = "ru")
@Composable
private fun CopyProgressIosPreview() = Copy(copyState(job = saving(BackupPart.AUDIO, 3, 9)), goesOnInBackground = false)

@Preview(name = "Copy · on its way, the photos of sheets and the sound off: «Разбор, занятия, прогресс · Видео 2 из 12 · Проверка»", widthDp = 412, heightDp = 892, locale = "ru")
@Composable
private fun CopyProgressFewPartsPreview() = Copy(
    copyState(job = saving(BackupPart.VIDEO, 2, 12).copy(parts = setOf(BackupPart.DATA, BackupPart.VIDEO), filled = setOf(BackupPart.DATA, BackupPart.VIDEO))),
)

@Preview(name = "Copy · «Остановить?»", widthDp = 412, heightDp = 892, locale = "ru")
@Composable
private fun CopyStopPreview() = Copy(copyState(job = saving(BackupPart.VIDEO, 7, 12)).copy(stopDialog = true))

@Preview(name = "Copy · saved to «Загрузки» without video: chips — no «6 видео», a dashed «без видео»; ✕ in the header", widthDp = 412, heightDp = 892, locale = "ru")
@Composable
private fun CopySavedPreview() = Copy(
    copyState(job = BackupJob.Saved(FILE, bytes = 252 * MB, place = "Загрузки", manifest = manifest(parts = NoVideo))),
)

@Preview(name = "Copy · saved, the place not told: only the weight; everything in it", widthDp = 412, heightDp = 892, locale = "ru")
@Composable
private fun CopySavedNoPlacePreview() = Copy(copyState(job = BackupJob.Saved(FILE, bytes = (3.4 * GB).toLong(), place = null, manifest = manifest())))

@Preview(name = "Copy · failed: no room where it went — «Без видео копия займёт ≈ 252 МБ.»", widthDp = 412, heightDp = 892, locale = "ru")
@Composable
private fun CopyFailedPlacePreview() = Copy(copyState(job = BackupJob.SaveFailed(SaveFailure.NO_SPACE, parts = All)))

@Preview(name = "Copy · failed: «Отправить…» short of the phone — its title says the phone, as its text", widthDp = 412, heightDp = 892, locale = "ru")
@Composable
private fun CopyFailedSharePreview() = Copy(copyState(contents = SmallContents, job = BackupJob.SaveFailed(SaveFailure.NO_SPACE, missingBytes = 190 * MB, parts = All)))

@Preview(name = "Copy · failed: the phone itself full", widthDp = 412, heightDp = 892, locale = "ru")
@Composable
private fun CopyFailedPhonePreview() = Copy(copyState(job = BackupJob.SaveFailed(SaveFailure.PHONE_FULL, parts = All)))

@Preview(name = "Copy · failed: the place went away", widthDp = 412, heightDp = 892, locale = "ru")
@Composable
private fun CopyFailedGonePreview() = Copy(copyState(job = BackupJob.SaveFailed(SaveFailure.UNAVAILABLE, parts = All)))

@Preview(name = "Copy · failed: anything else", widthDp = 412, heightDp = 892, locale = "ru")
@Composable
private fun CopyFailedPreview() = Copy(copyState(job = BackupJob.SaveFailed(SaveFailure.FAILED, parts = All)))

@Preview(name = "Copy · lying 892 × 412: one column of 560 in the middle, the zone as wide", widthDp = 892, heightDp = 412, locale = "ru")
@Composable
private fun CopyLandscapePreview() = Copy(copyState(parts = NoVideo))

@Preview(name = "Copy · 640 × 360 lying (603 × 308): the header 48, buttons 48, the zone of two rows", widthDp = 603, heightDp = 308, locale = "ru")
@Composable
private fun CopyLowPreview() = Copy(copyState(contents = SmallContents))

@Preview(name = "Copy · 640 × 360 lying (603 × 308) on its way: the percent 40", widthDp = 603, heightDp = 308, locale = "ru")
@Composable
private fun CopyLowProgressPreview() = Copy(copyState(job = saving(BackupPart.VIDEO, 7, 12)))

@Preview(name = "Copy · 640 × 360 lying (603 × 308) saved: the tile 56", widthDp = 603, heightDp = 308, locale = "ru")
@Composable
private fun CopyLowSavedPreview() = Copy(copyState(job = BackupJob.Saved(FILE, bytes = 252 * MB, place = "Загрузки", manifest = manifest(parts = NoVideo))))

@Preview(name = "Copy · ru 360 × 640 at 1.3: «всегда» whole, the data on two lines, captions whole", widthDp = 360, heightDp = 640, locale = "ru", fontScale = 1.3f)
@Composable
private fun CopyLargeFontPreview() = Copy(copyState())

@Preview(name = "Copy · fr 360 × 640", widthDp = 360, heightDp = 640, locale = "fr")
@Composable
private fun CopyFrenchPreview() = Copy(copyState(parts = NoVideo))

@Preview(name = "Copy · de 360 × 640 at 1.3", widthDp = 360, heightDp = 640, locale = "de", fontScale = 1.3f)
@Composable
private fun CopyGermanPreview() = Copy(copyState())

@Preview(
    name = "Copy · fr 320 × 544 at 1.5: «toujours» under the caption of the data; «Son des enregistrements» — the limit (5.29 R8)",
    widthDp = 320,
    heightDp = 544,
    locale = "fr",
    fontScale = 1.5f,
)
@Composable
private fun CopyFrenchNarrowPreview() = Copy(copyState())

@Preview(name = "Copy · ru 320 × 544 at 1.5: «всегда» beside, the numbers kept to their words", widthDp = 320, heightDp = 544, locale = "ru", fontScale = 1.5f)
@Composable
private fun CopyRussianNarrowPreview() = Copy(copyState())

// ——— «Восстановить из копии» ———

@Composable
private fun Restore(state: RestoreState, goesOnInBackground: Boolean = true) = ViolinTheme {
    CompositionLocalProvider(LocalReduceMotion provides true) {
        RestoreScreen(state = state, onIntent = {}, goesOnInBackground = goesOnInBackground, today = Today)
    }
}

/** What is in the app now: 5 recordings, 5 pieces, 38 days — 410 МБ. */
private val Now = BackupContents(
    BackupCounts(sessions = 5, pieces = 5, pages = 6, practiceDays = 38, level = 5, withSound = 4, videos = 1),
    mapOf(BackupPart.DATA to 4 * MB, BackupPart.SHEETS to 40 * MB, BackupPart.AUDIO to 16 * MB, BackupPart.VIDEO to 350 * MB),
)

private val Empty = BackupContents(BackupCounts(), mapOf(BackupPart.DATA to 40_000L))

private fun candidate(manifest: BackupManifest = manifest(parts = NoVideo), missing: Long = 0) =
    BackupCandidate.Copy("content://downloads/1", FILE, (3.4 * GB).toLong(), manifest, missing)

private fun ready(current: BackupContents = Now, copy: BackupCandidate.Copy = candidate(), busy: Boolean = false, saving: Boolean = false, dialog: RestoreDialog? = null) = RestoreState(
    stage = RestoreStage.Ready(copy, current),
    busy = busy,
    job = if (saving) BackupJob.Saving(FILE, visible = true, parts = All) else BackupJob.Idle,
    dialog = dialog,
)

private fun restoring(phase: RestorePhase, checked: Boolean) = RestoreState(
    job = BackupJob.Restoring(
        phase,
        checked = checked,
        progress = BackupProgress(BackupPart.VIDEO, 4, 6, doneBytes = (1.3 * GB).toLong(), totalBytes = (3.4 * GB).toLong()),
        remainingSec = 240,
    ),
)

@Preview(name = "Restore · reading: a spinner and «Открываем копию…», no zone", widthDp = 412, heightDp = 892, locale = "ru")
@Composable
private fun RestoreReadingPreview() = Restore(RestoreState())

@Preview(name = "Restore · over the data: the passport, «→ заменит», the dashed «Сейчас в приложении», the warning; two buttons", widthDp = 412, heightDp = 892, locale = "ru")
@Composable
private fun RestoreOverDataPreview() = Restore(ready())

@Preview(name = "Restore · over the data while a copy is saved: «Восстановить» asleep with its reason, the safety net pressed", widthDp = 412, heightDp = 892, locale = "ru")
@Composable
private fun RestoreOverDataWaitingPreview() = Restore(ready(saving = true))

@Preview(name = "Restore · into an empty app: the passport and the old line, a plain «Восстановить»", widthDp = 412, heightDp = 892, locale = "ru")
@Composable
private fun RestoreEmptyPreview() = Restore(ready(current = Empty))

@Preview(name = "Restore · into an empty app while a take is recorded", widthDp = 412, heightDp = 892, locale = "ru")
@Composable
private fun RestoreEmptyWaitingPreview() = Restore(ready(current = Empty, busy = true))

@Preview(name = "Restore · not a copy of ours", widthDp = 412, heightDp = 892, locale = "ru")
@Composable
private fun RestoreNotOursPreview() = Restore(RestoreState(stage = RestoreStage.Unfit(BackupFileProblem.NotOurs)))

@Preview(name = "Restore · made by a newer version", widthDp = 412, heightDp = 892, locale = "ru")
@Composable
private fun RestoreTooNewPreview() = Restore(RestoreState(stage = RestoreStage.Unfit(BackupFileProblem.TooNew)))

@Preview(name = "Restore · damaged", widthDp = 412, heightDp = 892, locale = "ru")
@Composable
private fun RestoreDamagedPreview() = Restore(RestoreState(stage = RestoreStage.Unfit(BackupFileProblem.Damaged)))

/**
 * A whole copy 1,8 ГБ short of the room beside the data: the media now in the app ([Heavy]) would make it, so the way without a net is
 * offered; beside a lighter app ([Now]) not even that.
 */
private val Short = candidate(manifest = manifest(), missing = (1.8 * GB).toLong())

private val Heavy = Now.copy(bytes = Now.bytes + (BackupPart.VIDEO to 3 * GB))

@Preview(name = "Restore · no room: the tile, «Не хватает 1,8 ГБ, чтобы восстановить безопасно»; «Понятно», the safety net, the coral word", widthDp = 412, heightDp = 892, locale = "ru")
@Composable
private fun RestoreNoRoomPreview() = Restore(ready(current = Heavy, copy = Short))

@Preview(name = "Restore · no room while a take is recorded: the coral word asleep with its reason", widthDp = 412, heightDp = 892, locale = "ru")
@Composable
private fun RestoreNoRoomWaitingPreview() = Restore(ready(current = Heavy, copy = Short, busy = true))

@Preview(name = "Restore · no room even without the media: only «Понятно»", widthDp = 412, heightDp = 892, locale = "ru")
@Composable
private fun RestoreNoRoomAtAllPreview() = Restore(ready(current = Now, copy = Short))

@Preview(name = "Restore · on its way, the safe way: «Восстанавливаем · Почти готово», 38 %, «можно остановить»", widthDp = 412, heightDp = 892, locale = "ru")
@Composable
private fun RestoreProgressPreview() = Restore(restoring(RestorePhase.EXTRACTING, checked = false))

@Preview(name = "Restore · without a net, checking: «Проверяем · Восстанавливаем · Почти готово»", widthDp = 412, heightDp = 892, locale = "ru")
@Composable
private fun RestoreCheckingPreview() = Restore(restoring(RestorePhase.VERIFYING, checked = true))

@Preview(name = "Restore · past the point of no return: «Остановить» asleep, its card right above", widthDp = 412, heightDp = 892, locale = "ru")
@Composable
private fun RestoreCannotStopPreview() = Restore(restoring(RestorePhase.FINISHING, checked = false))

@Preview(name = "Restore · on its way on iOS: «Не сворачивайте приложение…»", widthDp = 412, heightDp = 892, locale = "ru")
@Composable
private fun RestoreProgressIosPreview() = Restore(restoring(RestorePhase.EXTRACTING, checked = false), goesOnInBackground = false)

@Preview(name = "Restore · «Заменить данные?»: «Пропадёт то, что сейчас в приложении: 5 записей · 5 произведений · 38 дней занятий…»", widthDp = 412, heightDp = 892, locale = "ru")
@Composable
private fun RestoreConfirmPreview() = Restore(ready(dialog = RestoreDialog.REPLACE))

@Preview(name = "Restore · «Удалить всё и восстановить?»", widthDp = 412, heightDp = 892, locale = "ru")
@Composable
private fun RestoreUnsafePreview() = Restore(ready(current = Heavy, copy = Short, dialog = RestoreDialog.UNSAFE))

@Preview(name = "Restore · done: the tick, «Данные восстановлены», the chips of the copy", widthDp = 412, heightDp = 892, locale = "ru")
@Composable
private fun RestoreDonePreview() = Restore(RestoreState(job = BackupJob.Restored(manifest(parts = NoVideo))))

@Preview(name = "Restore · «Открываем ваши данные…» — the frame of the start", widthDp = 412, heightDp = 892, locale = "ru")
@Composable
private fun RestoreOpeningPreview() = Restore(RestoreState(job = BackupJob.Restored(manifest()), opening = true))

@Preview(name = "Restore · failed, the data in place: «Ещё раз», «Закрыть»", widthDp = 412, heightDp = 892, locale = "ru")
@Composable
private fun RestoreFailedPreview() = Restore(RestoreState(job = BackupJob.RestoreFailed(dataIntact = true, uri = "u", manifest = manifest(), checked = false)))

@Preview(name = "Restore · failed, the worst case: «Ещё раз с этим файлом», «Начать с чистого приложения» in coral", widthDp = 412, heightDp = 892, locale = "ru")
@Composable
private fun RestoreLostPreview() = Restore(RestoreState(job = BackupJob.RestoreFailed(dataIntact = false, uri = "u", manifest = manifest(), checked = true)))

@Preview(name = "Restore · a copy of last year: «Копия от 3 мая 2025»; no device in its passport", widthDp = 412, heightDp = 892, locale = "ru")
@Composable
private fun RestoreLastYearPreview() = Restore(ready(copy = candidate(manifest(madeAt = dayOf(2025, 5, 3), device = ""))))

@Preview(name = "Restore · 640 × 360 lying (603 × 308) over the data: a zone of ≈ 120, the passport scrolls over it", widthDp = 603, heightDp = 308, locale = "ru")
@Composable
private fun RestoreLowPreview() = Restore(ready())

@Preview(name = "Restore · 640 × 360 lying (603 × 308), no room: three rows in the zone", widthDp = 603, heightDp = 308, locale = "ru")
@Composable
private fun RestoreLowNoRoomPreview() = Restore(ready(current = Heavy, copy = Short))

@Preview(name = "Restore · de 360: «Aus Kopie wiederherstellen» with an ellipsis in the header", widthDp = 360, heightDp = 780, locale = "de")
@Composable
private fun RestoreGermanPreview() = Restore(ready())

@Preview(name = "Restore · ru 360 × 640 at 1.3 over the data", widthDp = 360, heightDp = 640, locale = "ru", fontScale = 1.3f)
@Composable
private fun RestoreLargeFontPreview() = Restore(ready())

@Preview(name = "Restore · de 360 at 1.3: «Kopie vom» / «12. September» — the day kept to its month", widthDp = 360, heightDp = 780, locale = "de", fontScale = 1.3f)
@Composable
private fun RestoreGermanLargeFontPreview() = Restore(ready())

@Preview(
    name = "Restore · no room, ru 320 × 544 at 1.5: the safety net and the coral word on all the lines they need",
    widthDp = 320,
    heightDp = 544,
    locale = "ru",
    fontScale = 1.5f,
)
@Composable
private fun RestoreNoRoomNarrowPreview() = Restore(ready(current = Heavy, copy = Short))
