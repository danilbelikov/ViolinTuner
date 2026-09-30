package com.violinjourney.app.feature.sound

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.violinjourney.app.core.audio.fx.SoundMeters
import com.violinjourney.app.core.audio.playback.PlayerState
import com.violinjourney.app.core.domain.backing.BackingConfig
import com.violinjourney.app.core.domain.sound.BuiltInPreset
import com.violinjourney.app.core.domain.sound.EqBand
import com.violinjourney.app.core.domain.sound.SoundConfig
import com.violinjourney.app.core.domain.sound.SoundParam
import com.violinjourney.app.core.domain.sound.SoundParams
import com.violinjourney.app.core.domain.sound.SoundPresets
import com.violinjourney.app.core.domain.sound.SoundSettings
import com.violinjourney.app.core.domain.sound.UserPreset
import com.violinjourney.app.core.ui.components.AppSheetCard
import com.violinjourney.app.core.ui.motion.LocalReduceMotion
import com.violinjourney.app.core.ui.theme.ViolinTheme
import com.violinjourney.app.feature.history.HistoryCard
import kotlin.math.abs
import kotlin.math.sin
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant

// «Звук» of R5 (spec 3.36.5; records.html 4, landscape.html «Остальные экраны») on the data of the mockup: «Менуэт соль мажор», a take
// of 2:05 recorded on Sunday 27 September 2026 at 18:42, its own settings — «Камерный зал» with the air lifted; the player at 1:05.
// Each screen as a phone shows it: the status bar over it. Still frames: motion is removed.

private val Moscow = TimeZone.of("Europe/Moscow")
private val Today = LocalDate(2026, 9, 27)
private fun at(time: String): Long = LocalDateTime.parse(time).toInstant(Moscow).toEpochMilliseconds()

private val Config = SoundConfig()
private val Hall = SoundPresets.settingsOf(BuiltInPreset.CHAMBER_HALL, Config)

/** «Камерный зал» with the air lifted by 3 dB and the volume off: settings of its own, nobody's preset. */
private val Own: SoundSettings = SoundParams.set(SoundParam.AIR_GAIN, 3.0, Hall, Config).let { it.copy(output = it.output.copy(enabled = false)) }
private val MyHall = UserPreset(id = 7, name = "Мой зал", settings = SoundParams.set(SoundParam.REVERB_MIX, 0.3, Hall, Config))

private val Take = RecordingName(1, title = null, pieceTitle = "Менуэт соль мажор", startedAtEpochMs = at("2026-09-27T18:42:00"))
private val Wave = List(120) { abs(sin(it * 0.21)).toFloat() * 0.8f + 0.2f * abs(sin(it * 1.7)).toFloat() }
private val Player = PlayerState(ready = true, positionMs = 65_000, durationMs = 125_000, processed = true)

private val Recording = SoundState(
    loading = false, mode = SoundMode.RECORDING, recording = Take, own = true, settings = Own, caption = SoundCaption.Custom,
    chips = SoundReducer.chipsOf(Own, listOf(MyHall), Config), custom = true, canReset = true, savedHint = false,
    band = EqBand.BODY, details = false, player = Player, listening = true, waveform = Wave, recordings = emptyList(), today = Today,
    affected = 0, dialog = null,
)

/** «Концерт ля минор» under a backing of the piano, heard with it, recorded in Pixel Buds. */
private val UnderBacking = Recording.copy(
    recording = Take.copy(pieceTitle = "Концерт ля минор, 1 ч."),
    own = false, settings = Hall, caption = SoundCaption.BuiltIn(BuiltInPreset.CHAMBER_HALL), chips = SoundReducer.chipsOf(Hall, emptyList(), Config),
    custom = false, canReset = false,
    player = Player.copy(durationMs = 220_000, positionMs = 44_000, hasBacking = true),
    backing = BackingBlockState(-6f, 240, 200, title = "фортепиано", durationMs = 220_000, recordedWith = RecordedWith.Wireless("Pixel Buds", 200)),
)

private fun card(id: Long, time: String, piece: String? = null, best: Boolean = false, video: Boolean = false, backing: Boolean = false) = HistoryCard(
    id = id, title = null, startedAtEpochMs = at(time), date = LocalDate.parse(time.substringBefore('T')), durationMs = 125_000,
    pieceTitle = piece, pieceId = piece?.let { id }, hasAudio = true, hasVideo = video, best = best, underBacking = backing,
)

private val Recordings = listOf(
    card(1, "2026-09-27T18:42:00", piece = "Менуэт соль мажор", best = true),
    card(2, "2026-09-27T09:15:00"),
    card(3, "2026-09-26T20:05:00", piece = "Концерт ля минор, 1 ч.", backing = true),
    card(4, "2026-09-26T19:40:00", piece = "Концерт ля минор, 1 ч.", video = true),
    card(5, "2026-09-24T08:30:00"),
)

/** «Звук записей»: «Камерный зал» for 23 recordings, heard on the newest. */
private val Everyone = Recording.copy(
    mode = SoundMode.EVERYONE, recording = Take, own = false, settings = Hall, caption = SoundCaption.BuiltIn(BuiltInPreset.CHAMBER_HALL),
    chips = SoundReducer.chipsOf(Hall, emptyList(), Config), custom = false, canReset = true, recordings = Recordings, affected = 23,
)

/** The status bar of the phone over the screen of the preview. */
private val StatusBar = 24.dp

@Composable
private fun Screen(state: SoundState) {
    ViolinTheme {
        CompositionLocalProvider(LocalReduceMotion provides true) {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).padding(top = StatusBar)) {
                SoundScreen(
                    state = state,
                    meters = remember { mutableStateOf<SoundMeters?>(null) },
                    onIntent = {},
                    config = Config,
                    backingConfig = BackingConfig(),
                    zone = Moscow,
                )
            }
        }
    }
}

@Preview(name = "Звук · обзор: «Свои для записи», пресеты лентой, блоки свёрнуты, плеер внизу с крупным A/B и выходом", locale = "ru", device = "spec:width=412dp,height=892dp")
@Composable
private fun OverviewPreview() = Screen(Recording)

@Preview(name = "Звук · «Как у всех»: подзаголовок «… · как у всех · Камерный зал», чип пресета выбран", locale = "ru", device = "spec:width=412dp,height=892dp")
@Composable
private fun EveryoneModePreview() = Screen(
    Recording.copy(
        own = false, settings = Hall, caption = SoundCaption.BuiltIn(BuiltInPreset.CHAMBER_HALL), custom = false, canReset = false,
        chips = SoundReducer.chipsOf(Hall, listOf(MyHall), Config),
    ),
)

@Preview(name = "Звук · первая правка: «свои настройки · сохраняются сами», название уступает многоточием", locale = "ru", device = "spec:width=360dp,height=740dp")
@Composable
private fun SavedHintPreview() = Screen(Recording.copy(savedHint = true))

@Preview(name = "Звук · эквалайзер и зал раскрыты: раскрытие одного не сворачивает другой", locale = "ru", device = "spec:width=412dp,height=1400dp")
@Composable
private fun EqPreview() = Screen(Recording.copy(expanded = setOf(SoundCard.EQ, SoundCard.REVERB)))

@Preview(name = "Звук · «Минусовка» раскрыта: «Записано в Pixel Buds · +200 мс учтено», ряд минусовки в плеере", locale = "ru", device = "spec:width=412dp,height=1100dp")
@Composable
private fun BackingPreview() = Screen(UnderBacking.copy(expanded = setOf(SoundCard.BACKING)))

@Preview(name = "Звук · «Минусовка» в проводных наушниках", locale = "ru", device = "spec:width=412dp,height=1100dp")
@Composable
private fun BackingWiredPreview() = Screen(
    UnderBacking.copy(expanded = setOf(SoundCard.BACKING), backing = UnderBacking.backing!!.copy(recordedWith = RecordedWith.Wired)),
)

@Preview(name = "Звук · «Минусовку не удалось подготовить»: без шеврона, строка под названием, ряда минусовки нет", locale = "ru", device = "spec:width=412dp,height=892dp")
@Composable
private fun BackingUnavailablePreview() = Screen(UnderBacking.copy(player = UnderBacking.player!!.copy(hasBacking = false)))

@Preview(name = "Звук · «Готовим минусовку…»: крутилка в плеере, A/B и минусовка 0,38", locale = "ru", device = "spec:width=412dp,height=892dp")
@Composable
private fun PreparingPreview() = Screen(UnderBacking.copy(player = null, preparingBacking = true))

@Preview(name = "Звук · плеер ещё открывает файл: панель стоит, первый ряд пуст", locale = "ru", device = "spec:width=412dp,height=892dp")
@Composable
private fun OpeningPreview() = Screen(Recording.copy(player = null))

@Preview(name = "Звук · обработка ничего не делает: A/B выключен", locale = "ru", device = "spec:width=412dp,height=892dp")
@Composable
private fun NeutralPreview() {
    val off = SoundPresets.settingsOf(BuiltInPreset.OFF, Config)
    Screen(
        Recording.copy(
            own = false, settings = off, caption = SoundCaption.BuiltIn(BuiltInPreset.OFF), chips = SoundReducer.chipsOf(off, emptyList(), Config),
            custom = false, canReset = false, player = Player.copy(processed = false),
        ),
    )
}

@Preview(name = "Звук записей · «Слушать на · последняя со звуком», «Другая», «для 23 записей», «Сбросить»", locale = "ru", device = "spec:width=412dp,height=892dp")
@Composable
private fun EveryonePreview() = Screen(Everyone)

@Preview(name = "Звук записей · правка никого не касается: «для всех записей»", locale = "ru", device = "spec:width=412dp,height=892dp")
@Composable
private fun EveryoneAllPreview() = Screen(Everyone.copy(affected = 0))

@Preview(name = "Звук записей · записей со звуком нет: ни плеера, ни строки, блоки работают", locale = "ru", device = "spec:width=412dp,height=892dp")
@Composable
private fun EveryoneEmptyPreview() = Screen(
    Everyone.copy(recording = null, recordings = emptyList(), player = null, listening = false, waveform = null, affected = 0, canReset = false),
)

@Preview(name = "Звук · 360 × 640: компактный плеер — выход в строке времени, A/B со словами", locale = "ru", device = "spec:width=360dp,height=640dp")
@Composable
private fun SmallPreview() = Screen(Recording)

@Preview(name = "Звук · 360 × 640 под минусовку: компактная минусовка", locale = "ru", device = "spec:width=360dp,height=640dp")
@Composable
private fun SmallBackingPreview() = Screen(UnderBacking)

@Preview(name = "Звук · 360 × 640, fr, шрифт 1,3: «Как у всех | Свои» двумя строками мельче", locale = "fr", fontScale = 1.3f, device = "spec:width=360dp,height=640dp")
@Composable
private fun SmallFrenchPreview() = Screen(UnderBacking.copy(own = true))

@Preview(name = "Звук · 360 × 640, de, шрифт 1,3", locale = "de", fontScale = 1.3f, device = "spec:width=360dp,height=640dp")
@Composable
private fun SmallGermanPreview() = Screen(Recording)

@Preview(name = "Звук · landscape 892 × 412: слева шапка, пресеты и плеер, справа режим и блоки", locale = "ru", device = "spec:width=892dp,height=412dp")
@Composable
private fun LandscapePreview() = Screen(Recording.copy(expanded = setOf(SoundCard.COMPRESSOR)))

@Preview(name = "Звук · 640 × 360 с вырезом (≈ 603) под минусовку, шрифт 1,3: колонка 277, названия блоков целиком", locale = "ru", fontScale = 1.3f, device = "spec:width=603dp,height=360dp")
@Composable
private fun LandscapeSmallPreview() = Screen(UnderBacking)

@Preview(name = "Звук записей · landscape 892 × 412", locale = "ru", device = "spec:width=892dp,height=412dp")
@Composable
private fun LandscapeEveryonePreview() = Screen(Everyone)

@Preview(name = "Лист «Слушать на…»: карточки R5 по дням, текущая обведена", locale = "ru", widthDp = 412, heightDp = 640)
@Composable
private fun ListenOnSheetPreview() = ViolinTheme {
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface), contentAlignment = Alignment.BottomCenter) {
        AppSheetCard { ListenOnSheetContent(Recordings, current = 3, today = Today, zone = Moscow, onPick = {}) }
    }
}
