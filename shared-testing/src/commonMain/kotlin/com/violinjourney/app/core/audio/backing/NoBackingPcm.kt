package com.violinjourney.app.core.audio.backing

import com.violinjourney.app.core.domain.backing.Backing
import com.violinjourney.app.core.io.PlatformFile

/** No backing's sound for tests that have no backing: nothing is prepared, nothing is swept. */
object NoBackingPcm : BackingPcm {
    override fun cached(backing: Backing, sampleRate: Int): PlatformFile? = null

    override fun prepare(backing: Backing, sampleRate: Int): PlatformFile? = null

    override fun deleteOrphans(keptFiles: Set<String>) = Unit
}
