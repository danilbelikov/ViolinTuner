package com.example.violintuner.core.data.practice

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** One saved practice (spec 6). [date] is ISO `yyyy-MM-dd`, the local date of the start, fixed at save time. */
@Entity(tableName = "practice_entries", indices = [Index("date")])
data class PracticeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,
    val startedAtEpochMs: Long,
    val durationMs: Long,
    val manual: Boolean,
)
