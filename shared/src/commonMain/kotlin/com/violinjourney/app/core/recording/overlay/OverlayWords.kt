package com.violinjourney.app.core.recording.overlay

import androidx.compose.ui.text.font.FontFamily
import com.violinjourney.app.core.ui.format.Formats
import com.violinjourney.app.shared.resources.Res
import com.violinjourney.app.shared.resources.dot_separator
import com.violinjourney.app.shared.resources.overlay_badge
import com.violinjourney.app.shared.resources.overlay_best_note
import com.violinjourney.app.shared.resources.overlay_drift_none
import com.violinjourney.app.shared.resources.overlay_previous_take
import com.violinjourney.app.shared.resources.session_cents_value
import com.violinjourney.app.shared.resources.session_drift_title
import com.violinjourney.app.shared.resources.session_percent
import com.violinjourney.app.shared.resources.session_summary_in_tune
import com.violinjourney.app.shared.resources.session_summary_tolerance
import org.jetbrains.compose.resources.getString

/**
 * The words drawn on the picture (spec 3.37, «Языки»): read in the language of the interface when the file is made, the
 * numbers of the recording already in them. Note names stay Latin and are not here.
 */
data class OverlayWords(
    /** «в строе 82%». */
    val badge: String,
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
        suspend fun of(overlay: NotesOverlay): OverlayWords = OverlayWords(
            badge = getString(Res.string.overlay_badge, getString(Res.string.session_percent, overlay.scorePercent)),
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
 * it — on Android the app's own font resource, on iOS the same file from the shared resources.
 */
class OverlayText(val resolver: FontFamily.Resolver, val family: FontFamily)
