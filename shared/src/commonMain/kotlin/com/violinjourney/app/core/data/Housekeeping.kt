package com.violinjourney.app.core.data

import com.violinjourney.app.core.audio.backing.BackingPcm
import com.violinjourney.app.core.audio.playback.SessionWaveforms
import com.violinjourney.app.core.audio.share.ShareFiles
import com.violinjourney.app.core.data.profile.AvatarFiles
import com.violinjourney.app.core.domain.backing.BackingRepository
import com.violinjourney.app.core.domain.progress.ProfileRepository
import com.violinjourney.app.core.domain.repertoire.RepertoireRepository
import com.violinjourney.app.core.domain.session.SessionRepository
import com.violinjourney.app.core.recording.video.VideoFiles
import com.violinjourney.app.core.recording.video.VideoThumbRuleStore
import com.violinjourney.app.core.recording.video.VideoThumbs
import com.violinjourney.app.core.time.WallClock
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * What of the app's files no one will read again, and its sweeping: the sound and the waveforms of recordings that are
 * gone, an avatar replaced, files made to be handed to other apps, shots nobody imported, backings nobody points at.
 * When — at the start and every time the app goes away — is `AppStartViewModel`'s; what goes is decided here. Also the
 * one file made anew rather than swept: the thumbnail of a video made by an older rule (spec 3.38).
 */
class Housekeeping(
    private val sessions: SessionRepository,
    private val waveforms: SessionWaveforms,
    private val avatarFiles: AvatarFiles,
    private val profile: ProfileRepository,
    private val shareFiles: ShareFiles,
    private val repertoire: RepertoireRepository,
    private val backings: BackingRepository,
    private val backingPcm: BackingPcm,
    private val videoFiles: VideoFiles,
    private val thumbRules: VideoThumbRuleStore,
    private val clock: WallClock,
    private val io: CoroutineDispatcher,
) {
    /** Once a start: what the recordings and the profile left behind, and the thumbnails of an older rule. */
    suspend fun atStart() {
        coroutineScope {
            launch { sessions.deleteOrphanAudio() }
            // waveforms are reckoned from the sound and kept beside it; those of sessions that are gone go too
            launch { waveforms.deleteOrphans(sessions.sessions.first().mapNotNull { it.audioPath }.toSet()) }
            launch { avatarFiles.deleteOrphans(referenced = profile.profile.first().avatarFile) }
            launch { remakeThumbs() }
        }
    }

    /**
     * The thumbnails of videos made by a rule older than [VideoThumbs.RULE] — the first frame until 0.92 — are made anew once (spec
     * 3.38, 5.31): one video after another, the newest recording first, so the top of «Записи» is right soonest; then the rule is
     * marked. Only the videos whose file is there (the summary has its sound); one whose frame cannot be had keeps what it had, and
     * the pass goes on. A pass cut short marks nothing and begins again at the next start; a copy of an old version brings old
     * thumbnails and settings without the mark, so they are made anew too.
     */
    private suspend fun remakeThumbs() {
        if (thumbRules.rule.first() >= VideoThumbs.RULE) return
        val names = sessions.sessions.first().filter { it.audioPath != null }.mapNotNull { it.videoPath }.distinct()
        withContext(io) {
            for (name in names) {
                ensureActive()
                videoFiles.existing(name)?.let(videoFiles::makeThumb)
            }
        }
        thumbRules.markRule(VideoThumbs.RULE)
    }

    /**
     * At the start and every time the app goes away (spec 5.11): a phone that is not restarted for days would otherwise
     * keep every video prepared for sending — a second copy of a take each — until the next cold start.
     */
    suspend fun sweepTemporaries() {
        coroutineScope {
            launch { shareFiles.sweep(clock.millis()) }
            launch { repertoire.deleteOrphanFiles() }
            // backings nobody points at any more — a replaced one whose takes are gone too (spec 3.32)
            // and their prepared sound with them: kept while the backing is, as a take under it is listened to again (spec 5.25)
            launch {
                val kept = backings.deleteUnused()
                withContext(io) { backingPcm.deleteOrphans(kept) }
            }
        }
    }
}
