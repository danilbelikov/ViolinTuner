package com.violinjourney.app.feature.share

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.violinjourney.app.core.domain.sound.BuiltInPreset
import com.violinjourney.app.core.ui.components.AppSheetCard
import com.violinjourney.app.core.ui.theme.ViolinTheme
import com.violinjourney.app.feature.sound.SoundCaption

// «Поделиться» of R5 (spec 3.36.5; records.html 5) in its frame without a window: the choice, the file being made, the failure — each
// face with its buttons at the bottom, as the sheet shows it.

private const val MIB = 1024L * 1024

/** «Менуэт соль мажор», 2:05 with the processing of «Камерный зал». */
private val SoundInfo = ShareInfo(
    sessionId = 1, fileName = "Менуэт соль мажор · 27 сентября.m4a", durationMs = 125_000, processedBytes = 2_100_000, originalBytes = 1_900_000,
    caption = SoundCaption.BuiltIn(BuiltInPreset.CHAMBER_HALL), message = "Менуэт соль мажор · 82 % · 27 сентября",
)

/** A video take of «Концерт ля минор» under a backing, 3:40, 1080p. */
private val VideoInfo = ShareInfo(
    sessionId = 2, fileName = "Концерт ля минор, 1 ч. · 23 сентября.m4a", durationMs = 220_000, processedBytes = 3_700_000, originalBytes = 62 * MIB,
    caption = SoundCaption.BuiltIn(BuiltInPreset.CHAMBER_HALL), message = "Концерт ля минор, 1 ч. · 76 % · 23 сентября",
    videoFileName = "Концерт ля минор, 1 ч. · 23 сентября.mp4", resolution = 1080, backing = true, backingBytes = 5_300_000,
)

@Composable
private fun Sheet(sheet: ShareSheet, landscape: Boolean = false) = ViolinTheme {
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface), contentAlignment = Alignment.BottomCenter) {
        AppSheetCard(bottom = { ShareSheetButtons(sheet, onIntent = {}) }) { ShareSheetContent(sheet, onIntent = {}, landscape = landscape) }
    }
}

@Preview(name = "Поделиться · звук: «Обработанный звук» выбран, у каждого варианта чип формата, строка файла, «Добавить текст»", locale = "ru", widthDp = 412, heightDp = 600)
@Composable
private fun SoundPreview() = Sheet(ShareSheet.Choose(SoundInfo, ShareVariant.PROCESSED, withText = true, busy = false))

@Preview(name = "Поделиться · видео с нотами (3.37): первым и выбрано, звук — с минусовкой, строка файла с разрешением файла с нотами", locale = "ru", widthDp = 412, heightDp = 860)
@Composable
private fun VideoNotesPreview() = Sheet(
    ShareSheet.Choose(
        VideoInfo.copy(notes = NotesOffer(resolution = 1080, bytes = 140L * MIB, sound = NotesSound.BACKING, tooLong = false, limitMinutes = 15)),
        ShareVariant.NOTES, withText = true, busy = false,
    ),
)

@Preview(name = "Поделиться · видео с нотами, запись длиннее 15 минут: строка приглушена и говорит почему", locale = "ru", widthDp = 412, heightDp = 860)
@Composable
private fun VideoNotesTooLongPreview() = Sheet(
    ShareSheet.Choose(
        VideoInfo.copy(notes = NotesOffer(resolution = 1080, bytes = 900L * MIB, sound = NotesSound.BACKING, tooLong = true, limitMinutes = 15)),
        ShareVariant.BACKING, withText = true, busy = false,
    ),
)

@Preview(name = "Поделиться · готовим видео с нотами", locale = "ru", widthDp = 412, heightDp = 420)
@Composable
private fun VideoNotesPreparingPreview() = Sheet(
    ShareSheet.Preparing(
        VideoInfo.copy(notes = NotesOffer(resolution = 1080, bytes = 140L * MIB, sound = NotesSound.BACKING, tooLong = false, limitMinutes = 15)),
        ShareVariant.NOTES, percent = 42, remainingSec = 40,
    ),
)

@Preview(name = "Поделиться · видео под минусовку: четыре варианта, «С минусовкой» первым, строка файла видео", locale = "ru", widthDp = 412, heightDp = 780)
@Composable
private fun VideoBackingPreview() = Sheet(ShareSheet.Choose(VideoInfo, ShareVariant.BACKING, withText = true, busy = false))

@Preview(name = "Поделиться · «Только звук» видео: «с обработкой», .m4a", locale = "ru", widthDp = 412, heightDp = 780)
@Composable
private fun VideoSoundPreview() = Sheet(ShareSheet.Choose(VideoInfo, ShareVariant.SOUND, withText = false, busy = false))

@Preview(name = "Поделиться · видео как снято с iPhone: .mov; без обработки — «Видео · как снято»", locale = "ru", widthDp = 412, heightDp = 600)
@Composable
private fun VideoIphonePreview() = Sheet(
    ShareSheet.Choose(
        VideoInfo.copy(backing = false, processed = false, originalVideoFileName = "Концерт ля минор, 1 ч. · 23 сентября.mov"),
        ShareVariant.ORIGINAL, withText = true, busy = false,
    ),
)

@Preview(name = "Поделиться · большой файл: размер коралловым жирным и фраза под ним", locale = "ru", widthDp = 412, heightDp = 780)
@Composable
private fun LargePreview() = Sheet(ShareSheet.Choose(VideoInfo.copy(originalBytes = 612 * MIB), ShareVariant.PROCESSED, withText = true, busy = false))

@Preview(name = "Поделиться · короткая подготовка: «Готовим…» на кнопке, 0,38, лист держится", locale = "ru", widthDp = 412, heightDp = 600)
@Composable
private fun BusyPreview() = Sheet(ShareSheet.Choose(SoundInfo, ShareVariant.PROCESSED, withText = true, busy = true))

@Preview(name = "Поделиться · готовим видео: 42 %, «С минусовкой · .mp4 · осталось около 20 с», «Отмена»", locale = "ru", widthDp = 412, heightDp = 360)
@Composable
private fun PreparingPreview() = Sheet(ShareSheet.Preparing(VideoInfo, ShareVariant.BACKING, percent = 42, remainingSec = 20))

@Preview(name = "Поделиться · готовим файл, оценки ещё нет: без «осталось»", locale = "ru", widthDp = 412, heightDp = 360)
@Composable
private fun PreparingNoEstimatePreview() = Sheet(ShareSheet.Preparing(SoundInfo, ShareVariant.PROCESSED, percent = 8, remainingSec = null))

@Preview(name = "Поделиться · не получилось: плашка, «Ещё раз» и «Отправить оригинал»", locale = "ru", widthDp = 412, heightDp = 480)
@Composable
private fun FailedPreview() = Sheet(ShareSheet.Failed(SoundInfo))

@Preview(name = "Поделиться · не получилось видео: «Отправить как снято»", locale = "ru", widthDp = 412, heightDp = 480)
@Composable
private fun FailedVideoPreview() = Sheet(ShareSheet.Failed(VideoInfo))

@Preview(name = "Поделиться · landscape: варианты по 56, лист не шире 640", locale = "ru", widthDp = 892, heightDp = 412)
@Composable
private fun LandscapePreview() = Sheet(ShareSheet.Choose(VideoInfo, ShareVariant.BACKING, withText = true, busy = false), landscape = true)

@Preview(name = "Поделиться · de, 360, шрифт 1,3", locale = "de", fontScale = 1.3f, widthDp = 360, heightDp = 900)
@Composable
private fun GermanLargePreview() = Sheet(ShareSheet.Choose(VideoInfo, ShareVariant.BACKING, withText = true, busy = false))

@Preview(name = "Поделиться · fr, 360: готовим видео", locale = "fr", widthDp = 360, heightDp = 360)
@Composable
private fun FrenchPreparingPreview() = Sheet(ShareSheet.Preparing(VideoInfo, ShareVariant.PROCESSED, percent = 64, remainingSec = 7))
