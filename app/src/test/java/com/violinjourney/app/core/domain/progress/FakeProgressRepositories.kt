package com.violinjourney.app.core.domain.progress

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.datetime.LocalDate

/** In-memory trophies for view model tests; behaves like the table: one trophy per mark, first date wins. */
class FakeTrophyRepository : TrophyRepository {
    override val trophies = MutableStateFlow<List<Trophy>>(emptyList())

    override suspend fun award(hours: Int, date: LocalDate) {
        trophies.update { list ->
            if (list.any { it.hours == hours }) list else (list + Trophy(hours, date, shown = false)).sortedBy { it.hours }
        }
    }

    override suspend fun markShown(hours: Int) {
        trophies.update { list -> list.map { if (it.hours == hours) it.copy(shown = true) else it } }
    }
}

class FakeProfileRepository : ProfileRepository {
    override val profile = MutableStateFlow(Profile.EMPTY)

    override suspend fun setName(name: String) {
        profile.update { it.copy(name = Profile.cleanName(name)) }
    }

    override suspend fun setAvatarFile(fileName: String?) {
        profile.update { it.copy(avatarFile = fileName) }
    }
}
