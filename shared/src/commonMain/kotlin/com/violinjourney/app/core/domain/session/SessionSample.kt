package com.violinjourney.app.core.domain.session

/**
 * One time bucket of a recorded session (spec 5.5): the note the engine showed and the mean
 * smoothed deviation from it. A bucket without a note is a null in the sample list.
 */
data class SessionSample(val midi: Int, val cents: Double)
