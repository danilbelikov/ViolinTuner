package com.violinjourney.app.core.data.sound

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.violinjourney.app.core.data.AppDatabase
import com.violinjourney.app.core.domain.sound.BuiltInPreset
import com.violinjourney.app.core.domain.sound.SoundConfig
import com.violinjourney.app.core.domain.sound.SoundPresets
import com.violinjourney.app.core.domain.sound.SoundRules
import com.violinjourney.app.core.time.FixedWallClock
import com.violinjourney.app.core.time.WallClock
import kotlin.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.TimeZone
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Runs on a device or emulator: `./gradlew :app:connectedDebugAndroidTest`. */
@RunWith(AndroidJUnit4::class)
class RoomSoundRepositoryTest {
    private lateinit var database: AppDatabase
    private lateinit var repository: RoomSoundRepository
    private val config = SoundConfig()
    private val hall = SoundPresets.settingsOf(BuiltInPreset.CHAMBER_HALL, config)
    private val warm = SoundPresets.settingsOf(BuiltInPreset.WARM, config)

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).build()
        repository = RoomSoundRepository(database.soundDao(), config, FixedWallClock(Instant.fromEpochMilliseconds(5_000), TimeZone.UTC))
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun untilAnythingIsSetEveryRecordingSoundsAsRecorded() = runBlocking {
        assertTrue(SoundRules.isNeutral(repository.default.first()))
        assertTrue(repository.own.first().isEmpty())
        assertFalse(repository.effective(3).first().own)
    }

    @Test
    fun theDefaultAndOwnSettingsComeBackToTheLastNumber() = runBlocking {
        repository.setDefault(hall)
        repository.setOwn(3, warm)
        assertEquals(hall, repository.default.first())
        assertEquals(mapOf(3L to warm), repository.own.first())
        assertEquals(warm, repository.effective(3).first().settings)
        assertEquals(hall, repository.effective(4).first().settings)
    }

    @Test
    fun settingAgainReplacesAndClearingReturnsToTheDefault() = runBlocking {
        repository.setDefault(hall)
        repository.setOwn(3, warm)
        repository.setOwn(3, warm.copy(output = warm.output.copy(enabled = true, gainDb = 4.0)))
        assertEquals(4.0, repository.own.first().getValue(3).output.gainDb, 0.0)
        repository.clearOwn(3)
        assertTrue(repository.own.first().isEmpty())
        assertEquals(hall, repository.effective(3).first().settings)
    }

    @Test
    fun numbersOutOfRangeAreStoredClean() = runBlocking {
        repository.setOwn(3, warm.copy(output = warm.output.copy(enabled = true, gainDb = 99.0)))
        assertEquals(12.0, repository.own.first().getValue(3).output.gainDb, 0.0)
    }

    @Test
    fun presetsAreKeptInTheOrderTheyWereSavedAndCanBeRemoved() = runBlocking {
        val first = repository.savePreset("  Мой зал ", hall)!!
        val second = repository.savePreset("Для педагога", warm)!!
        assertNull(repository.savePreset("   ", warm))
        assertEquals(listOf("Мой зал", "Для педагога"), repository.presets.first().map { it.name })
        assertEquals(hall, repository.presets.first().first().settings)
        repository.deletePreset(first)
        assertEquals(listOf(second), repository.presets.first().map { it.id })
    }
}
