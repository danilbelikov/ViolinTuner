package com.violinjourney.app.core.domain.progress

import kotlinx.coroutines.flow.Flow

/** Who practises: shown in the header of the practice screen only (spec 3.13). */
data class Profile(
    /** Trimmed; empty when the user gave none. */
    val name: String,
    /** File name inside the avatar folder, not a path; null without a photo. */
    val avatarFile: String?,
) {
    companion object {
        const val MAX_NAME_LENGTH = 24
        val EMPTY = Profile(name = "", avatarFile = null)

        /** What a typed name becomes when stored: no edge spaces, at most [MAX_NAME_LENGTH] characters. */
        fun cleanName(raw: String): String = raw.trim().take(MAX_NAME_LENGTH).trim()
    }
}

interface ProfileRepository {
    val profile: Flow<Profile>

    /** Stored through [Profile.cleanName]. */
    suspend fun setName(name: String)

    /** Null removes the photo. */
    suspend fun setAvatarFile(fileName: String?)
}
