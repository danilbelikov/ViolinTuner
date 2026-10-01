package com.violinjourney.app.feature.journey

import androidx.compose.runtime.Composable
import com.violinjourney.app.core.ui.format.Formats
import com.violinjourney.app.shared.resources.Res
import com.violinjourney.app.shared.resources.journey_sessions_few
import com.violinjourney.app.shared.resources.journey_sessions_many
import com.violinjourney.app.shared.resources.journey_sessions_one
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * «примерно 1 занятие / 2 занятия / 5 занятий» (spec 3.36.7, 5.18): the form for [n] in the language of the interface
 * ([Formats.plural]) — under the plate of what is missing on the journey, in the sheet of a house and in the card of a thing.
 */
internal fun sessionsWords(n: Int): StringResource =
    Formats.plural(n, Res.string.journey_sessions_one, Res.string.journey_sessions_few, Res.string.journey_sessions_many)

/** «примерно 4 занятия» — [n] from [com.violinjourney.app.core.domain.journey.JourneyRules.sessionsLeft]. */
@Composable
fun sessionsInWords(n: Int): String = stringResource(sessionsWords(n), n)
