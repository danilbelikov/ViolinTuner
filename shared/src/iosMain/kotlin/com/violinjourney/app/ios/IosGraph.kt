package com.violinjourney.app.ios

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.violinjourney.app.core.analytics.Analytics
import com.violinjourney.app.core.analytics.IosAppMetricaAnalytics
import com.violinjourney.app.core.analytics.NoOpAnalytics
import com.violinjourney.app.core.audio.FakePitchSource
import com.violinjourney.app.core.audio.FakeScenario
import com.violinjourney.app.core.audio.IosMicPitchSource
import com.violinjourney.app.core.audio.IosRecordingRate
import com.violinjourney.app.core.audio.PitchSource
import com.violinjourney.app.core.audio.backing.BackingPlaybackFactory
import com.violinjourney.app.core.audio.backing.IosAudioRoutes
import com.violinjourney.app.core.audio.backing.IosBackingFiles
import com.violinjourney.app.core.audio.backing.IosBackingImporter
import com.violinjourney.app.core.audio.backing.IosBackingPcm
import com.violinjourney.app.core.audio.backing.IosBackingPlayback
import com.violinjourney.app.core.audio.dsp.MpmDetector
import com.violinjourney.app.core.audio.dsp.PitchDetectorFactory
import com.violinjourney.app.core.audio.playback.IosSessionPlayer
import com.violinjourney.app.core.audio.playback.IosSessionWaveforms
import com.violinjourney.app.core.audio.playback.IosVideoPicture
import com.violinjourney.app.core.audio.playback.SessionPlayerFactory
import com.violinjourney.app.core.audio.playback.VideoPictureFactory
import com.violinjourney.app.core.audio.recording.IosAacEncoder
import com.violinjourney.app.core.audio.recording.PcmEncoderFactory
import com.violinjourney.app.core.audio.share.IosSoundRenderer
import com.violinjourney.app.core.backup.BackupConfig
import com.violinjourney.app.core.backup.BackupManager
import com.violinjourney.app.core.backup.IosBackupDocuments
import com.violinjourney.app.core.backup.IosBackupStore
import com.violinjourney.app.core.data.backing.RoomBackingRepository
import com.violinjourney.app.core.data.journey.RoomHomeRepository
import com.violinjourney.app.core.data.journey.RoomJourneyRepository
import com.violinjourney.app.core.data.practice.RoomPieceBlockRepository
import com.violinjourney.app.core.data.practice.RoomPracticeRepository
import com.violinjourney.app.core.data.progress.RoomTrophyRepository
import com.violinjourney.app.core.data.repertoire.RoomRepertoireRepository
import com.violinjourney.app.core.data.session.RoomSessionRepository
import com.violinjourney.app.core.data.sound.RoomSoundRepository
import com.violinjourney.app.core.di.ElapsedClock
import com.violinjourney.app.core.domain.IntonationConfig
import com.violinjourney.app.core.domain.backing.BackingConfig
import com.violinjourney.app.core.domain.journey.JourneyConfig
import com.violinjourney.app.core.domain.practice.FinishPracticeAsk
import com.violinjourney.app.core.domain.practice.PracticeConfig
import com.violinjourney.app.core.domain.practice.PracticeFinisher
import com.violinjourney.app.core.domain.progress.ProgressConfig
import com.violinjourney.app.core.domain.progress.TrophyAwarder
import com.violinjourney.app.core.domain.repertoire.RepertoireConfig
import com.violinjourney.app.core.domain.sound.SoundConfig
import com.violinjourney.app.core.domain.venue.Venues
import com.violinjourney.app.core.io.PlatformFile
import com.violinjourney.app.core.recording.DecodingFileTakeAnalyzer
import com.violinjourney.app.core.recording.IosPcmFileOpener
import com.violinjourney.app.core.recording.RecordingWatch
import com.violinjourney.app.core.recording.TakePipeline
import com.violinjourney.app.core.recording.video.AnalysisSpeed
import com.violinjourney.app.core.recording.video.VideoTakeImporter
import com.violinjourney.app.core.settings.DataStoreBackupPrefs
import com.violinjourney.app.core.settings.DataStoreBlockStore
import com.violinjourney.app.core.settings.DataStorePracticeNotesStore
import com.violinjourney.app.core.settings.DataStoreProfileRepository
import com.violinjourney.app.core.settings.DataStoreRunningPracticeStore
import com.violinjourney.app.core.settings.DataStoreSettingsRepository
import com.violinjourney.app.core.settings.DataStoreStandHintStore
import com.violinjourney.app.core.settings.DataStoreVenueStore
import com.violinjourney.app.core.settings.SettingsConfigSource
import com.violinjourney.app.core.settings.SettingsRepository
import com.violinjourney.app.core.time.SystemWallClock
import com.violinjourney.app.core.time.WallClock
import com.violinjourney.app.feature.history.HistorySectionAsk
import com.violinjourney.app.feature.share.RenderSpeed
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSProcessInfo
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask

/**
 * What Hilt puts together on Android, by hand: one instance of everything that is one per app, in the order Hilt
 * would make them. Screens take their view models from here (see [IosNavHost]); a new pipeline and a new microphone
 * are made per screen.
 */
internal class IosGraph(fakeScenario: FakeScenario?, private val statistics: IosAppMetricaAnalytics?) {
    val io = Dispatchers.IO
    val clock: WallClock = SystemWallClock

    val intonationConfig = IntonationConfig()
    val practiceConfig = PracticeConfig()
    val progressConfig = ProgressConfig()
    val repertoireConfig = RepertoireConfig()
    val journeyConfig = JourneyConfig()
    val soundConfig = SoundConfig()
    val backingConfig = BackingConfig()

    /** AppMetrica where the build has a key and may send (spec 5.27); silence otherwise. */
    val analytics: Analytics = statistics ?: NoOpAnalytics()

    /** Where the data lie: Application Support of the app. */
    val dataDirectory = PlatformFile(IosStorage.dataDirectory())

    // DataStore allows one instance per file: its scope ends with this graph, before a new one opens the file again
    private val storageScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val database = IosStorage.database()
    private val dataStore: DataStore<Preferences> = IosStorage.settings(scope = storageScope)

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

    val sessions = RoomSessionRepository(database.sessionDao(), intonationConfig, audioFiles, clock, analytics, io)
    val practice = RoomPracticeRepository(database.practiceDao())
    val blockHistory = RoomPieceBlockRepository(database.pieceBlockDao())
    val trophies = RoomTrophyRepository(database.trophyDao())
    val repertoire = RoomRepertoireRepository(database.repertoireDao(), sheetFiles, repertoireConfig, clock, analytics)
    val sound = RoomSoundRepository(database.soundDao(), soundConfig, clock)
    val journey = RoomJourneyRepository(database.journeyDao(), analytics)
    val home = RoomHomeRepository(database.journeyDao(), analytics)

    // The backing of a piece (spec 3.32): the same repository as on Android, over the files and the sound of iOS.
    private val caches = PlatformFile(NSSearchPathForDirectoriesInDomains(NSCachesDirectory, NSUserDomainMask, true).first() as String)
    val backingFiles = IosBackingFiles(dataDirectory, clock)
    val backings = RoomBackingRepository(database.backingDao(), backingFiles, io)
    val backingPcm = IosBackingPcm(caches, backingFiles, clock, backingConfig)
    val backingImporter = IosBackingImporter(dataDirectory, backingFiles, backingConfig, clock)
    val audioRoutes = IosAudioRoutes()
    private val backingPlayback = BackingPlaybackFactory { IosBackingPlayback(audioRoutes, logStats = IosBuild.isDevApp) }

    val venues = Venues(venueStore, journey)
    val finishAsk = FinishPracticeAsk()
    val sectionAsk = HistorySectionAsk()

    /** One per app, as on Android: its lock takes the answers to a practice one at a time (spec 5.6). */
    val finisher = PracticeFinisher(
        practice, runningPractice, clock, practiceNotes, journey, journeyConfig, blockStore, blockHistory, practiceConfig, analytics,
    )
    val awarder = TrophyAwarder(trophies, progressConfig, clock)
    val recordingWatch = RecordingWatch()

    /** The rate the microphone last got on the route of now; 48 kHz, which it asks for, on a route not heard yet (spec 5.25). */
    val recordingRate = IosRecordingRate(intonationConfig.supportedSampleRatesHz, audioRoutes)

    private val newPitchSource = pitchSources(fakeScenario, intonationConfig, logStats = IosBuild.isDevApp, onInputRate = recordingRate::heard)

    val videoFiles = IosVideoFiles(repertoireConfig, io)
    val elapsed = ElapsedClock { (NSProcessInfo.processInfo.systemUptime * MS_PER_SECOND).toLong() }
    val analysisSpeed = AnalysisSpeed()
    val fileAnalyzer = DecodingFileTakeAnalyzer(PitchDetectorFactory(::MpmDetector), repertoireConfig, Dispatchers.Default, IosPcmFileOpener)
    val videoImporter = VideoTakeImporter(
        videoFiles, fileAnalyzer, sessions, configSource, runningPractice, repertoireConfig, intonationConfig, clock, elapsed, analysisSpeed,
        Dispatchers.Default, analytics,
    )

    val playerFactory = SessionPlayerFactory { scope -> IosSessionPlayer(scope, soundConfig, backingConfig) }
    val pictureFactory = VideoPictureFactory(::IosVideoPicture)

    /** One per screen, as on Android: it holds that screen's wish to record, and listens through a microphone of its own. */
    fun takes() = TakePipeline(
        newPitchSource(), sessions, audioFiles, runningPractice, practiceConfig, clock, Dispatchers.Default, recordingWatch,
        practiceNotes, journeyConfig, backings, backingPlayback, backingConfig, analytics,
    )

    val waveforms = IosSessionWaveforms({ IosFolders.folder(WAVEFORMS_FOLDER) }, io)
    val shareFiles = IosShareFiles(io)
    val renderer = IosSoundRenderer(soundConfig, io)
    val renderSpeed = RenderSpeed()

    // A copy of the data (spec 3.20): the same manager as on Android, over the files and the pickers of iOS.
    val backupConfig = BackupConfig()
    val backupPrefs = DataStoreBackupPrefs(dataStore)
    val backupStore = IosBackupStore(dataDirectory, database, sessions, repertoire, practice, trophies, progressConfig, clock, io)
    val backupManager = BackupManager(
        backupStore, IosBackupDocuments(), backupPrefs, IosKeepAlive, backupConfig, clock, elapsed, io,
        analytics,
    )

    init {
        storageScope.launch(Dispatchers.Main) { backupManager.job.collect { if (!backupManager.running) IosKeepAlive.stop() } }
        // the consent lives in these settings: it is followed as long as they are this graph's
        statistics?.followConsent(settings, storageScope)
    }

    /** Lets the database and the settings go, so that a copy can be put in their place and a new graph open them. */
    fun close() {
        database.close()
        storageScope.cancel()
    }

    private companion object {
        const val WAVEFORMS_FOLDER = "waveforms"
        const val MS_PER_SECOND = 1_000.0
    }
}

/**
 * A new source for every call, as Hilt's unscoped provider gives on Android: each screen that records hears through
 * its own microphone — its own engine, sound of the take and clock of the take — so a Live still listening for its two
 * seconds after the screen went away shares nothing with the screen that came next. The fake scenario too is one per
 * screen, as on Android.
 */
internal fun pitchSources(fakeScenario: FakeScenario?, config: IntonationConfig, logStats: Boolean, onInputRate: (Int) -> Unit = {}): () -> PitchSource {
    if (fakeScenario != null) return { FakePitchSource(fakeScenario, config) }
    // MPM, as on Android (DetectorComparisonTest)
    val detectors = PitchDetectorFactory(::MpmDetector)
    val encoders = PcmEncoderFactory(::IosAacEncoder)
    return { IosMicPitchSource(detectors, encoders, logStats, onInputRate) }
}
