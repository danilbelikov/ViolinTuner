package com.violinjourney.app.core.domain.repertoire

import kotlinx.coroutines.flow.MutableStateFlow

class FakeStandHintStore(seen: Boolean = false) : StandHintStore {
    override val seen = MutableStateFlow(seen)

    override suspend fun markSeen() {
        seen.value = true
    }
}
