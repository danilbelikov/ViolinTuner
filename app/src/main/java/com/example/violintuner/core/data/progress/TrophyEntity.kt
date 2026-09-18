package com.example.violintuner.core.data.progress

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One trophy that has been given (spec 6). The mark is the key: a mark has one trophy, ever.
 * [awardedDate] is ISO `yyyy-MM-dd`, local at the moment of the award.
 */
@Entity(tableName = "trophies")
data class TrophyEntity(
    @PrimaryKey val hours: Int,
    val awardedDate: String,
    val shown: Boolean,
)
