package com.violinjourney.app.feature.share

import android.content.Context
import com.violinjourney.app.R
import com.violinjourney.app.core.ui.format.Formats
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Clock
import javax.inject.Inject

/** The same names the screens show: a take is called after its piece, a free session is «Сессия · …». */
class AppShareTexts @Inject constructor(
    @ApplicationContext private val context: Context,
    private val clock: Clock,
) : ShareTexts {

    override fun title(title: String?, pieceTitle: String?, startedAtEpochMs: Long): String {
        val date = Formats.dayAndMonth(startedAtEpochMs, clock.zone)
        return title
            ?: pieceTitle?.let { context.getString(R.string.session_take_title, it, date) }
            ?: context.getString(R.string.session_default_title, date)
    }

    override fun message(title: String?, pieceTitle: String?, scorePercent: Int, startedAtEpochMs: Long): String = context.getString(
        R.string.share_message,
        title ?: pieceTitle ?: context.getString(R.string.share_message_session),
        scorePercent,
        Formats.dayAndMonth(startedAtEpochMs, clock.zone),
    )
}
