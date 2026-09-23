package com.violinjourney.app.core.domain.repertoire

import kotlinx.coroutines.flow.Flow

/**
 * Whether the music stand has already shown where to tap (spec 3.15): the page-turning zones
 * are not drawn, so the very first visit outlines them once — and never again.
 */
interface StandHintStore {
    val seen: Flow<Boolean>

    suspend fun markSeen()
}
