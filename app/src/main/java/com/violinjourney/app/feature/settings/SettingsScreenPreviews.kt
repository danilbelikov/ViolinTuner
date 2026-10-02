package com.violinjourney.app.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.violinjourney.app.core.backup.BackupPart
import com.violinjourney.app.core.backup.RestorePhase
import com.violinjourney.app.core.domain.TolerancePreset
import com.violinjourney.app.core.domain.UserSettings
import com.violinjourney.app.core.domain.sound.BuiltInPreset
import com.violinjourney.app.core.ui.theme.ViolinTheme
import com.violinjourney.app.feature.backup.DataBlockState
import com.violinjourney.app.feature.backup.DataGroup
import com.violinjourney.app.feature.backup.DataRunning
import com.violinjourney.app.feature.backup.JobPhase
import com.violinjourney.app.feature.sound.SoundCaption
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn

// «Настройки» of the redesign (spec 3.36.8, 5.29 R8; start.html, 3): the four groups and every state of «Данные». The header follows
// the window (taller than wide — 56), so the upright previews are as tall as a phone.

/** Noon of a day of 2026 in the zone of the machine: the caption says the day the person sees. */
private fun dayOf(month: Int, day: Int): Long =
    LocalDate(2026, month, day).atStartOfDayIn(TimeZone.currentSystemDefault()).toEpochMilliseconds() + NOON_MS

private const val NOON_MS = 12 * 60 * 60 * 1_000L
private const val GIGABYTES_3_4 = 3_650_722_202L

/** The usual: copied on 12 September, 3,4 ГБ in the app. */
private val Saved = DataBlockState(dateRead = true, lastBackupAtEpochMs = dayOf(9, 12), totalBytes = GIGABYTES_3_4)

/** [read] false — the settings not read yet: the statistics and the sound hold their lines, as the route hands them over. */
@Composable
private fun Settings(
    data: DataBlockState = Saved,
    analytics: Boolean = true,
    language: Boolean = true,
    tolerance: TolerancePreset = TolerancePreset.INTERMEDIATE,
    sound: SoundCaption = SoundCaption.BuiltIn(BuiltInPreset.OFF),
    read: Boolean = true,
) = ViolinTheme {
    SettingsScreen(
        state = SettingsState(440, UserSettings.A4_OPTIONS_HZ, tolerance, sound, analyticsEnabled = analytics, read = read),
        onIntent = {},
        onBack = {},
        dataBlock = {
            DataGroup(
                state = data,
                analyticsEnabled = analytics.takeIf { read },
                onAnalyticsChange = {},
                onOpenBackup = {},
                onOpenRunningRestore = {},
                onPickCopy = {},
                onOpenPrivacy = {},
            )
        },
        onLanguageClick = if (language) ({}) else null,
    )
}

@Preview(name = "Settings · the usual: 440, ±8, «последняя — 12 сентября · в приложении 3,4 ГБ»", widthDp = 412, heightDp = 1200, locale = "ru")
@Composable
private fun SettingsPreview() = Settings()

@Preview(name = "Settings · the first screen of a phone: the groups by their labels, «Данные» in sight", widthDp = 412, heightDp = 892, locale = "ru")
@Composable
private fun SettingsFirstScreenPreview() = Settings(sound = SoundCaption.BuiltIn(BuiltInPreset.CHAMBER_HALL))

@Preview(name = "Settings · a copy on its way: «Копия сохраняется», «56 % · Видео 7 из 12», the bar; the restore asleep and why", widthDp = 412, heightDp = 1200, locale = "ru")
@Composable
private fun SettingsCopyRunningPreview() = Settings(data = Saved.copy(running = DataRunning(restore = false, percent = 56, phase = JobPhase.Files(BackupPart.VIDEO, 7, 12))))

@Preview(name = "Settings · a restore on its way: «Восстановление идёт», «38 % · Восстанавливаем»; the copy asleep and why", widthDp = 412, heightDp = 1200, locale = "ru")
@Composable
private fun SettingsRestoreRunningPreview() = Settings(data = Saved.copy(running = DataRunning(restore = true, percent = 38, phase = JobPhase.Restore(RestorePhase.EXTRACTING))))

@Preview(name = "Settings · old copy: «последняя — 12 августа · с тех пор 9 новых записей · в приложении 3,4 ГБ», grey words, no badge", widthDp = 412, heightDp = 1200, locale = "ru")
@Composable
private fun SettingsStalePreview() = Settings(data = Saved.copy(lastBackupAtEpochMs = dayOf(8, 12), newSinceStale = 9))

@Preview(name = "Settings · never saved, not weighed yet: «ещё не сохраняли», no weight", widthDp = 412, heightDp = 1200, locale = "ru")
@Composable
private fun SettingsNotWeighedPreview() = Settings(data = DataBlockState(dateRead = true))

@Preview(name = "Settings · the date not read yet: the caption keeps its line empty", widthDp = 412, heightDp = 1200, locale = "ru")
@Composable
private fun SettingsDateNotReadPreview() = Settings(data = DataBlockState())

@Preview(name = "Settings · nothing read yet: the date of the copy, the statistics and the sound hold their lines; no switch yet", widthDp = 412, heightDp = 1200, locale = "ru")
@Composable
private fun SettingsNothingReadPreview() = Settings(data = DataBlockState(), read = false)

@Preview(name = "Settings · restored, waiting for the restart: «Восстановление идёт», «100 % · Почти готово»; the copy asleep and why", widthDp = 412, heightDp = 1200, locale = "ru")
@Composable
private fun SettingsRestoredPreview() = Settings(data = Saved.copy(running = DataRunning(restore = true, percent = 100, phase = JobPhase.Restore(RestorePhase.FINISHING))))

@Preview(name = "Settings · statistics off: «выключено — ничего не отправляется»", widthDp = 412, heightDp = 1200, locale = "ru")
@Composable
private fun SettingsAnalyticsOffPreview() = Settings(analytics = false)

@Preview(name = "Settings · Android before 13: no «Язык», «Пройти знакомство снова» alone in «Приложение»", widthDp = 412, heightDp = 1200, locale = "ru")
@Composable
private fun SettingsNoLanguagePreview() = Settings(language = false)

@Preview(name = "Settings · fr, 360 at 1.3: «Intermédiaire» whole — the segments by their words; notes under their titles", widthDp = 360, heightDp = 1500, locale = "fr", fontScale = 1.3f)
@Composable
private fun SettingsFrenchLargeFontPreview() = Settings(data = Saved.copy(lastBackupAtEpochMs = dayOf(8, 12), newSinceStale = 9))

@Preview(name = "Settings · de, 360: «Aus Kopie wiederherstellen», «Kammerton A4» and its note", widthDp = 360, heightDp = 1300, locale = "de")
@Composable
private fun SettingsGermanPreview() = Settings()

@Preview(name = "Settings · ru, 360 at 1.3: every caption whole, «Данные» held", widthDp = 360, heightDp = 1500, locale = "ru", fontScale = 1.3f)
@Composable
private fun SettingsRussianLargeFontPreview() = Settings(data = Saved.copy(lastBackupAtEpochMs = dayOf(8, 12), newSinceStale = 9))

@Preview(name = "Settings · ru, 320 at 1.5: the names of the tolerance a step under 12 sp, whole, each «±N ц» one size with its name", widthDp = 320, heightDp = 1700, locale = "ru", fontScale = 1.5f)
@Composable
private fun SettingsSmallPhoneLargeFontPreview() = Settings()

@Preview(name = "Settings · de, 320 at 1.5: «Sprache» whole beside «Deutsch» cut; long titles a step smaller", widthDp = 320, heightDp = 1800, locale = "de", fontScale = 1.5f)
@Composable
private fun SettingsGermanSmallPhoneLargeFontPreview() = Settings()

@Preview(name = "Settings · landscape 892 × 412: one column of 480 in the middle, header 48", widthDp = 892, heightDp = 412, locale = "ru")
@Composable
private fun SettingsLandscapePreview() = Settings()

@Preview(name = "Settings · landscape 640 × 360 behind its cutout and bars (603 × 308)", widthDp = 603, heightDp = 308, locale = "ru")
@Composable
private fun SettingsLowLandscapePreview() = Settings()
