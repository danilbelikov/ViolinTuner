package com.example.violintuner.core.domain.backing

import kotlinx.coroutines.flow.Flow

/**
 * An accompaniment file — «минусовка» (spec 3.32). One copy of the file per row, whoever points at it:
 * a piece and any number of takes; it goes when nobody does. The file is named, not located.
 */
data class Backing(
    val id: Long = 0,
    val fileName: String,
    /** The name the file came with, without its extension: what the piece screen shows. */
    val title: String,
    val durationMs: Long,
    val sampleRate: Int,
    val channels: Int,
    val sizeBytes: Long,
    val addedAtEpochMs: Long,
)

/** Where the sound goes out while a take is recorded: decides whether a take under the backing may be made at all. */
enum class BackingOutput {
    WIRED,
    USB,
    BLUETOOTH,

    /** The phone's own speaker or earpiece: the backing would reach the microphone (spec 3.32, the owner's decision). */
    SPEAKER,
    ;

    val isHeadphones: Boolean get() = this != SPEAKER

    /** Only wireless headphones lag behind what the system's clocks say; wired ones are counted by the system itself. */
    val isWireless: Boolean get() = this == BLUETOOTH
}

/** The headphones the sound goes to now: their kind and the name they give themselves (the key of their latency). */
data class AudioRoute(val output: BackingOutput, val deviceName: String?)

/**
 * The backing of a take: which file sounded, how far it is shifted against the violin and how loud it is
 * mixed (spec 3.32). [recordedOffsetMs] is the shift worked out while recording — «Как записано» goes back to it.
 */
data class TakeBacking(
    val sessionId: Long,
    val backingId: Long,
    val offsetMs: Int,
    val recordedOffsetMs: Int,
    val gainDb: Float,
    /** How far the backing played before it stopped: headphones taken off stop it, the take goes on. */
    val playedMs: Long,
    val output: BackingOutput,
    val deviceName: String?,
    /** What the headphones were believed to lag when the take was made — the part of [recordedOffsetMs] they gave. */
    val latencyMs: Int = 0,
)

/** A piece's backing and whether takes are to be made under it (the chip «С минусовкой», remembered per piece). */
data class PieceBacking(val pieceId: Long, val backingId: Long, val enabled: Boolean)

interface BackingRepository {
    val backings: Flow<List<Backing>>

    val pieceBackings: Flow<List<PieceBacking>>

    val takeBackings: Flow<List<TakeBacking>>

    suspend fun backing(id: Long): Backing?

    /** Stores a backing whose file is already in place; the id. */
    suspend fun add(backing: Backing): Long

    /** Puts [backingId] on the piece (replacing what was there) or takes it off with null; the chip is switched on with a new one. */
    suspend fun setForPiece(pieceId: Long, backingId: Long?)

    suspend fun setEnabled(pieceId: Long, enabled: Boolean)

    suspend fun saveTake(take: TakeBacking)

    suspend fun setTakeMix(sessionId: Long, offsetMs: Int, gainDb: Float)

    /** Rows nobody points at any more, with their files: housekeeping at start. The names of the files that are still needed. */
    suspend fun deleteUnused(): Set<String>
}

/** Every number of the backing (spec 5.25). None of it is about intonation. */
data class BackingConfig(
    val maxDurationMs: Long = 60 * 60_000L,
    /** Free space asked for on top of the file itself. */
    val spareBytes: Long = 50L * 1024 * 1024,
    val defaultGainDb: Float = -6f,
    val minGainDb: Float = -24f,
    val maxGainDb: Float = 6f,
    val gainStepDb: Float = 0.5f,
    val minOffsetMs: Int = -2_000,
    val maxOffsetMs: Int = 2_000,
    val offsetStepMs: Int = 5,
    /** Wireless headphones never set: a guess in the middle of what they usually lag (150–300 ms). */
    val defaultWirelessLatencyMs: Int = 200,
    /** A shift changed while playing glides in over this much, without a click. */
    val shiftFadeMs: Int = 30,
)

/** The copies of backing files: `files/backings/<uuid>.<extension>`. Named, not located. */
interface BackingFiles {
    /** A fresh name for a copy that is about to be written, keeping the source's [extension] (the extractor goes by it). */
    fun newFile(extension: String): java.io.File

    fun existing(name: String): java.io.File?

    fun delete(name: String)

    /** Files no row names: an import cut short. Young ones are left alone — their row may be a moment away. */
    fun deleteOrphans(kept: Set<String>)
}

/** For code that has no backings: tests of other screens, builds before them. */
object NoBackings : BackingRepository {
    override val backings: Flow<List<Backing>> = kotlinx.coroutines.flow.flowOf(emptyList())
    override val pieceBackings: Flow<List<PieceBacking>> = kotlinx.coroutines.flow.flowOf(emptyList())
    override val takeBackings: Flow<List<TakeBacking>> = kotlinx.coroutines.flow.flowOf(emptyList())

    override suspend fun backing(id: Long): Backing? = null

    override suspend fun add(backing: Backing): Long = 0

    override suspend fun setForPiece(pieceId: Long, backingId: Long?) = Unit

    override suspend fun setEnabled(pieceId: Long, enabled: Boolean) = Unit

    override suspend fun saveTake(take: TakeBacking) = Unit

    override suspend fun setTakeMix(sessionId: Long, offsetMs: Int, gainDb: Float) = Unit

    override suspend fun deleteUnused(): Set<String> = emptySet()
}
