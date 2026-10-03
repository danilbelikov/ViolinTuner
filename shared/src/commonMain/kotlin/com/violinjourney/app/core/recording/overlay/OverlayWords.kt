package com.violinjourney.app.core.recording.overlay

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.text.font.FontFamily
import com.violinjourney.app.core.ui.format.Formats
import com.violinjourney.app.shared.resources.Res
import com.violinjourney.app.shared.resources.dot_separator
import com.violinjourney.app.shared.resources.overlay_app_icon
import com.violinjourney.app.shared.resources.overlay_badge
import com.violinjourney.app.shared.resources.overlay_best_note
import com.violinjourney.app.shared.resources.overlay_drift_none
import com.violinjourney.app.shared.resources.overlay_previous_take
import com.violinjourney.app.shared.resources.overlay_signature
import com.violinjourney.app.shared.resources.session_cents_value
import com.violinjourney.app.shared.resources.session_drift_title
import com.violinjourney.app.shared.resources.session_percent
import com.violinjourney.app.shared.resources.session_summary_in_tune
import com.violinjourney.app.shared.resources.session_summary_tolerance
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jetbrains.compose.resources.decodeToImageBitmap
import org.jetbrains.compose.resources.getDrawableResourceBytes
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.getSystemResourceEnvironment

/**
 * The words drawn on the picture (spec 3.37, «Языки»): read in the language of the interface when the file is made, the
 * numbers of the recording already in them. Note names stay Latin and are not here.
 */
data class OverlayWords(
    /** «Анализ игры» (since 0.90; before — «в строе 82%»). */
    val badge: String,
    /** «Анализируй свою игру в приложении Violin Journey» — beside the badge and under the summary; [APP_NAME] is drawn bolder. */
    val signature: String,
    /** «в строе · допуск ±8 ц». */
    val toleranceLine: String,
    val bestNote: String,
    /** «Что уходит». */
    val drift: String,
    /** «+24 ц» of the note that drifts; its name goes before it. Null when no note does. */
    val driftCents: String?,
    /** «ничего». */
    val driftNone: String,
    val previousTake: String,
    /** «74%» of the previous take; null when there is none. */
    val previousScore: String?,
) {
    companion object {
        /** The name of the app in the signature: never translated, never broken across lines (spec 3.37). */
        const val APP_NAME = "Violin Journey"

        suspend fun of(overlay: NotesOverlay): OverlayWords = OverlayWords(
            badge = getString(Res.string.overlay_badge),
            signature = getString(Res.string.overlay_signature, APP_NAME),
            toleranceLine = getString(Res.string.session_summary_in_tune) + getString(Res.string.dot_separator) +
                getString(Res.string.session_summary_tolerance, overlay.toleranceCents),
            bestNote = getString(Res.string.overlay_best_note),
            drift = getString(Res.string.session_drift_title),
            driftCents = overlay.drift?.let { getString(Res.string.session_cents_value, Formats.signedCents(it.meanCents)) },
            driftNone = getString(Res.string.overlay_drift_none),
            previousTake = getString(Res.string.overlay_previous_take),
            previousScore = overlay.previous?.let { getString(Res.string.session_percent, it.scorePercent) },
        )
    }
}

/**
 * The font of the overlay outside any composition: Manrope as the platform loads it, and the resolver that lays text out in
 * it — on Android the app's own font resource, on iOS the same file from the shared resources; and the icon of the signature
 * (since 0.90), from the shared resources on both.
 */
class OverlayText(val resolver: FontFamily.Resolver, val family: FontFamily, val icon: ImageBitmap) {
    companion object {
        /** The icon of the app for the signature of the summary: 256 px from `tools/icon/export.js`, its corners rounded when drawn. */
        suspend fun icon(): ImageBitmap =
            getDrawableResourceBytes(getSystemResourceEnvironment(), Res.drawable.overlay_app_icon).decodeToImageBitmap()
    }
}

/** Reads [OverlayText] the first time a file is made, and keeps it: the font and the icon are not wanted before (spec 5.30). */
class OverlayTextLoader(private val load: suspend () -> OverlayText) {
    private val lock = Mutex()
    private var text: OverlayText? = null

    suspend fun get(): OverlayText = lock.withLock { text ?: load().also { text = it } }
}
