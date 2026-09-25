package com.violinjourney.app.ios

import com.violinjourney.app.core.analytics.Analytics
import com.violinjourney.app.core.analytics.NoOpAnalytics
import com.violinjourney.app.core.audio.FakePitchSource
import com.violinjourney.app.core.audio.FakeScenario
import com.violinjourney.app.core.audio.IosMicPitchSource
import com.violinjourney.app.core.audio.PitchSource
import com.violinjourney.app.core.audio.backing.BackingPcm
import com.violinjourney.app.core.audio.dsp.MpmDetector
import com.violinjourney.app.core.audio.dsp.PitchDetectorFactory
import com.violinjourney.app.core.audio.playback.IosSessionPlayer
import com.violinjourney.app.core.audio.playback.IosSessionWaveforms
import com.violinjourney.app.core.audio.playback.IosVideoPicture
import com.violinjourney.app.core.audio.playback.SessionPlayerFactory
import com.violinjourney.app.core.audio.playback.SessionWaveforms
import com.violinjourney.app.core.audio.playback.VideoPictureFactory
import com.violinjourney.app.core.audio.recording.IosAacEncoder
import com.violinjourney.app.core.audio.recording.PcmEncoderFactory
import com.violinjourney.app.core.audio.share.ShareFiles
import com.violinjourney.app.core.data.journey.RoomHomeRepository
import com.violinjourney.app.core.data.journey.RoomJourneyRepository
import com.violinjourney.app.core.data.practice.RoomPieceBlockRepository
import com.violinjourney.app.core.data.practice.RoomPracticeRepository
import com.violinjourney.app.core.data.progress.RoomTrophyRepository
import com.violinjourney.app.core.data.repertoire.RoomRepertoireRepository
import com.violinjourney.app.core.data.session.RoomSessionRepository
import com.violinjourney.app.core.data.sound.RoomSoundRepository
import com.violinjourney.app.core.domain.IntonationConfig
import com.violinjourney.app.core.domain.backing.Backing
import com.violinjourney.app.core.domain.backing.BackingConfig
import com.violinjourney.app.core.domain.backing.NoBackings
import com.violinjourney.app.core.domain.journey.JourneyConfig
import com.violinjourney.app.core.domain.practice.FinishPracticeAsk
import com.violinjourney.app.core.domain.practice.PracticeConfig
import com.violinjourney.app.core.domain.practice.PracticeFinisher
import com.violinjourney.app.core.domain.progress.ProgressConfig
import com.violinjourney.app.core.domain.progress.TrophyAwarder
import com.violinjourney.app.core.domain.repertoire.RepertoireConfig
import com.violinjourney.app.core.domain.sound.SoundConfig
import com.violinjourney.app.core.domain.sound.SoundSettings
import com.violinjourney.app.core.domain.venue.Venues
import com.violinjourney.app.core.io.PlatformFile
import com.violinjourney.app.core.recording.RecordingWatch
import com.violinjourney.app.core.recording.TakePipeline
import com.violinjourney.app.core.settings.DataStoreBlockStore
import com.violinjourney.app.core.settings.DataStorePracticeNotesStore
import com.violinjourney.app.core.settings.DataStoreProfileRepository
import com.violinjourney.app.core.settings.DataStoreRunningPracticeStore
import com.violinjourney.app.core.settings.DataStoreSettingsRepository
import com.violinjourney.app.core.settings.DataStoreStandHintStore
import com.violinjourney.app.core.settings.DataStoreVenueStore
import com.violinjourney.app.core.settings.SettingsConfigSource
import com.violinjourney.app.core.settings.SettingsRepository
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.violinjourney.app.core.time.SystemWallClock
import com.violinjourney.app.core.time.WallClock
import com.violinjourney.app.feature.history.HistorySectionAsk
import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.Platform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO

/**
 * What Hilt puts together on Android, by hand: one instance of everything that is one per app, in the order Hilt
 * would make them. Screens take their view models from here (see [IosNavHost]); a new pipeline is made per screen.
 */
@OptIn(ExperimentalNativeApi::class)
internal class IosGraph(fakeScenario: FakeScenario?) {
    val io = Dispatchers.IO
    val clock: WallClock = SystemWallClock

    val intonationConfig = IntonationConfig()
    val practiceConfig = PracticeConfig()
    val progressConfig = ProgressConfig()
    val repertoireConfig = RepertoireConfig()
    val journeyConfig = JourneyConfig()
    val soundConfig = SoundConfig()
    val backingConfig = BackingConfig()

    // AppMetrica on iOS is a separate library: until it is agreed on, the iOS app sends nothing (spec 3.34).
    val analytics: Analytics = NoOpAnalytics()

    private val database = IosStorage.database()
    private val dataStore: DataStore<Preferences> = IosStorage.settings()

    val settings: SettingsRepository = DataStoreSettingsRepository(dataStore)
    val configSource = SettingsConfigSource(intonationConfig, settings)
    val runningPractice = DataStoreRunningPracticeStore(dataStore)
    val blockStore = DataStoreBlockStore(dataStore)
    val profiles = DataStoreProfileRepository(dataStore)
    val standHints = DataStoreStandHintStore(dataStore)
    val practiceNotes = DataStorePracticeNotesStore(dataStore)
    private val venueStore = DataStoreVenueStore(dataStore)

    val audioFiles = IosSessionAudioFiles(repertoireConfig)
    val avatarFiles = IosAvatarFiles(io, clock)
    val sheetFiles = IosSheetFiles(io, repertoireConfig)

    val sessions = RoomSessionRepository(database.sessionDao(), intonationConfig, audioFiles, clock, analytics)
    val practice = RoomPracticeRepository(database.practiceDao())
    val blockHistory = RoomPieceBlockRepository(database.pieceBlockDao())
    val trophies = RoomTrophyRepository(database.trophyDao())
    val repertoire = RoomRepertoireRepository(database.repertoireDao(), sheetFiles, repertoireConfig, clock, analytics)
    val sound = RoomSoundRepository(database.soundDao(), soundConfig, clock)
    val journey = RoomJourneyRepository(database.journeyDao(), analytics)
    val home = RoomHomeRepository(database.journeyDao(), analytics)

    // The backing (spec 3.32) needs a player and a decoder of its own on iOS; until then a piece has none.
    val backings = NoBackings

    val venues = Venues(venueStore, journey)
    val finishAsk = FinishPracticeAsk()
    val sectionAsk = HistorySectionAsk()
    val finisher = PracticeFinisher(
        practice, runningPractice, clock, practiceNotes, journey, journeyConfig, blockStore, blockHistory, practiceConfig, analytics,
    )
    val awarder = TrophyAwarder(trophies, progressConfig, clock)
    val recordingWatch = RecordingWatch()

    val pitchSource: PitchSource = if (fakeScenario != null) {
        FakePitchSource(fakeScenario, intonationConfig)
    } else {
        // MPM, as on Android (DetectorComparisonTest)
        IosMicPitchSource(PitchDetectorFactory(::MpmDetector), PcmEncoderFactory(::IosAacEncoder), logStats = Platform.isDebugBinary)
    }

    val playerFactory = SessionPlayerFactory { scope -> IosSessionPlayer(scope, soundConfig) }
    val pictureFactory = VideoPictureFactory(::IosVideoPicture)

    /** One per screen, as on Android: it holds that screen's wish to record. */
    fun takes() = TakePipeline(
        pitchSource, sessions, audioFiles, runningPractice, practiceConfig, clock, Dispatchers.Default, recordingWatch,
        practiceNotes, journeyConfig, backings, null, backingConfig, analytics,
    )

    val waveforms = IosSessionWaveforms({ IosFolders.folder(WAVEFORMS_FOLDER) }, io)
    // The files for «Поделиться» and the prepared backings come later on iOS; until then there is nothing of theirs to sweep.
    val shareFiles = object : ShareFiles {
        override fun processed(audioName: String, settings: SoundSettings, fileName: String): PlatformFile =
            PlatformFile("${IosFolders.folder(SHARE_FOLDER)}/$fileName")

        override suspend fun original(audio: PlatformFile, fileName: String): PlatformFile? = null

        override suspend fun sweep(nowEpochMs: Long) = Unit
    }
    val backingPcm = object : BackingPcm {
        override fun cached(backing: Backing, sampleRate: Int): PlatformFile? = null

        override fun prepare(backing: Backing, sampleRate: Int): PlatformFile? = null

        override fun deleteOrphans(keptFiles: Set<String>) = Unit
    }

    private companion object {
        const val SHARE_FOLDER = "share"
        const val WAVEFORMS_FOLDER = "waveforms"
    }
}
