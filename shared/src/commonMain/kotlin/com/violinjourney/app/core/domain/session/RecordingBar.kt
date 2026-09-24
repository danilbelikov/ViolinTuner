package com.violinjourney.app.core.domain.session

import com.violinjourney.app.core.domain.Zone

/** One note on the mini bar of the recording strip: its share of the bar and its zone. */
data class RecordingBar(val fraction: Float, val zone: Zone)
