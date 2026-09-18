package com.example.violintuner.core.data.session

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** Summary row of a session: everything the history list shows, stored computed (spec 6). */
@Entity(tableName = "sessions", indices = [Index("startedAtEpochMs"), Index("pieceId")])
data class SessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String?,
    val startedAtEpochMs: Long,
    val durationMs: Long,
    val a4Hz: Double,
    val toleranceCents: Double,
    val nearCents: Double,
    val scorePercent: Int,
    val nearPercent: Int,
    val offPercent: Int,
    val maeCents: Double,
    val biasCents: Double,
    /** One letter per note, see [SessionMapper]. */
    val previewZones: String,
    val audioPath: String?,
    /** The piece this session is a take of; no foreign key, see `MIGRATION_3_4`. */
    val pieceId: Long? = null,
)

/** Samples of a session as a [com.example.violintuner.core.domain.session.SampleCodec] blob. */
@Entity(
    tableName = "session_samples",
    foreignKeys = [
        ForeignKey(
            entity = SessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
class SamplesEntity(
    @PrimaryKey val sessionId: Long,
    val bucketMs: Long,
    val data: ByteArray,
)
