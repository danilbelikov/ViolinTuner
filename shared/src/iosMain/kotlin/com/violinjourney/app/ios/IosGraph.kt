package com.violinjourney.app.ios

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
import com.violinjourney.app.core.backup.DataLayout
import com.violinjourney.app.core.backup.IosBackupDocuments
import com.violinjourney.app.core.backup.IosBackupStore
import com.violinjourney.app.core.data.Housekeeping
import com.violinjourney.app.core.data.backing.RoomBackingRepository
import com.violinjourney.app.core.data.events.RoomEventRepository
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
import com.violinjourney.app.core.domain.events.EventsConfig
import com.violinjourney.app.core.domain.journey.JourneyConfig
import com.violinjourney.app.core.domain.practice.FinishPracticeAsk
import com.violinjourney.app.core.domain.practice.PracticeConfig
import com.violinjourney.app.core.domain.practice.PracticeFinisher
import com.violinjourney.app.core.domain.progress.ProgressConfig
import com.violinjourney.app.core.domain.progress.TrophyAwarder
import com.violinjourney.app.core.domain.repertoire.RepertoireConfig
import com.violinjourney.app.core.domain.sound.SoundConfig
import com.violinjourney.app.core.domain.venue.Venues
import com.violinjourney.app.core.io.PickedCopies
import com.violinjourney.app.core.io.PlatformFile
import com.violinjourney.app.core.recording.DecodingFileTakeAnalyzer
import com.violinjourney.app.core.recording.IosPcmFileOpener
import com.violinjourney.app.core.recording.RecordingWatch
import com.violinjourney.app.core.recording.TakePipeline
import com.violinjourney.app.core.recording.audio.AudioTakeImporter
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
import com.violinjourney.app.feature.share.RenderSpeed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
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
    val eventsConfig = EventsConfig()

    /** AppMetrica where the build has a key and may send (spec 5.27); silence otherwise. */
    val analytics: Analytics = statistics ?: NoOpAnalytics()

    /** Where the data lie: Application Support of the app. */
    val dataDirectory = PlatformFile(IosStorage.dataDirectory())

    // DataStore allows one instance per file: its scope ends with this graph, before a new one opens the file again
    private val storage = IosDataStorage()
    private val storageScope = storage.scope
    private val database = storage.database
    private val dataStore = storage.dataStore

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
    val events = RoomEventRepository(database.eventDao(), eventsConfig, clock, analytics, io)
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

    /** One per app, as on Android: its lock takes the answers to a practice one at a time (spec 5.6). */
    val finisher = PracticeFinisher(
        repository = practice, store = runningPractice, clock = clock, notes = practiceNotes, journey = journey,
        journeyConfig = journeyConfig, blocks = blockStore, blockHistory = blockHistory, config = practiceConfig,
        analytics = analytics,
    )
    val awarder = TrophyAwarder(trophies, progressConfig, clock)
    val recordingWatch = RecordingWatch()

    /** The rate the microphone last got on the route of now; 48 kHz, which it asks for, on a route not heard yet (spec 5.25). */
    val recordingRate = IosRecordingRate(intonationConfig.supportedSampleRatesHz, audioRoutes)

    private val newPitchSource = pitchSources(fakeScenario, intonationConfig, logStats = IosBuild.isDevApp, onInputRate = recordingRate::heard, analytics = analytics)

    val videoFiles = IosVideoFiles(repertoireConfig, io)
    val elapsed = ElapsedClock { (NSProcessInfo.processInfo.systemUptime * MS_PER_SECOND).toLong() }
    val analysisSpeed = AnalysisSpeed()
    val fileAnalyzer = DecodingFileTakeAnalyzer(PitchDetectorFactory(::MpmDetector), repertoireConfig, Dispatchers.Default, IosPcmFileOpener)
    val videoImporter = VideoTakeImporter(
        files = videoFiles, analyzer = fileAnalyzer, sessions = sessions, configSource = configSource,
        practice = runningPractice, practiceConfig = practiceConfig, repertoireConfig = repertoireConfig,
        intonationDefaults = intonationConfig, clock = clock, elapsed = elapsed, speed = analysisSpeed,
        dispatcher = Dispatchers.Default, analytics = analytics,
    )

    /** «Звук из файла» of an event (spec 3.35, 5.28): one for the app, as the importer of videos. */
    val audioImporter = AudioTakeImporter(
        files = IosPickedSounds(io), analyzer = fileAnalyzer, sessions = sessions, configSource = configSource,
        repertoireConfig = repertoireConfig, intonationDefaults = intonationConfig, clock = clock, elapsed = elapsed, speed = analysisSpeed,
        io = io, dispatcher = Dispatchers.Default, analytics = analytics,
    )

    val playerFactory = SessionPlayerFactory { scope -> IosSessionPlayer(scope, soundConfig, backingConfig) }
    val pictureFactory = VideoPictureFactory(::IosVideoPicture)

    /** One per screen, as on Android: it holds that screen's wish to record, and listens through a microphone of its own. */
    fun takes() = TakePipeline(
        pitchSource = newPitchSource(), sessionRepository = sessions, audioFiles = audioFiles,
        runningPractice = runningPractice, practiceConfig = practiceConfig, clock = clock, dispatcher = Dispatchers.Default,
        watch = recordingWatch, practiceNotes = practiceNotes, journeyConfig = journeyConfig, backings = backings,
        backingPlaybackFactory = backingPlayback, backingConfig = backingConfig, analytics = analytics,
    )

    // reckoned again from the sound whenever it is missing: not for the backup of the phone (spec 5.14)
    val waveforms = IosSessionWaveforms({ IosFolders.deviceOnlyFolder(DataLayout.WAVEFORMS) }, io)
    val shareFiles = IosShareFiles(io)
    val housekeeping = Housekeeping(sessions, waveforms, avatarFiles, profiles, shareFiles, repertoire, backings, backingPcm, clock, io)
    val renderer = IosSoundRenderer(soundConfig, io)
    val renderSpeed = RenderSpeed()

    // A copy of the data (spec 3.20): the same manager as on Android, over the files and the pickers of iOS.
    val backupConfig = BackupConfig()
    val backupPrefs = DataStoreBackupPrefs(dataStore)
    val backupStore = IosBackupStore(dataDirectory, database, sessions, repertoire, practice, trophies, progressConfig, clock, io)
    val backupManager = BackupManager(
        store = backupStore, documents = IosBackupDocuments(), prefs = backupPrefs, keepAlive = IosKeepAlive,
        config = backupConfig, clock = clock, elapsed = elapsed, io = io, analytics = analytics,
    )

    init {
        storageScope.launch(Dispatchers.Main) { backupManager.job.collect { if (!backupManager.running) IosKeepAlive.stop() } }
        // a take on its way to the database is not put to sleep halfway when the app leaves (spec 3.9, 3.32)
        storageScope.launch(Dispatchers.Main) { IosTakeKeepAlive.follow(recordingWatch) }
        // the consent lives in these settings: it is followed as long as they are this graph's
        statistics?.followConsent(settings, storageScope)
        // What a crash or a dropped pick left of the pickers' copies. Only here, as a graph is made: no picker is open and
        // no import runs, and the copies an older build left keep the dates of their originals (PickedCopies.sweep).
        storageScope.launch { PickedCopies.sweep(clock.millis()) }
    }

    /**
     * Lets the settings go and waits until they have, then the database — so that a copy can be put in their place and a
     * new graph open them. Its screens and their view models are gone before this is called (`AppGraph.restart`).
     */
    suspend fun close() = storage.close()

    private companion object {
        const val MS_PER_SECOND = 1_000.0
    }
}

/**
 * A new source for every call, as Hilt's unscoped provider gives on Android: each screen that records hears through
 * its own microphone — its own engine, sound of the take and clock of the take — so a Live still listening for its two
 * seconds after the screen went away shares nothing with the screen that came next. The fake scenario too is one per
 * screen, as on Android.
 */
internal fun pitchSources(
    fakeScenario: FakeScenario?,
    config: IntonationConfig,
    logStats: Boolean,
    onInputRate: (Int) -> Unit = {},
    analytics: Analytics,
): () -> PitchSource {
    if (fakeScenario != null) return { FakePitchSource(fakeScenario, config) }
    // MPM, as on Android (DetectorComparisonTest)
    val detectors = PitchDetectorFactory(::MpmDetector)
    val encoders = PcmEncoderFactory(::IosAacEncoder)
    return { IosMicPitchSource(detectors, encoders, logStats, onInputRate, analytics) }
}
