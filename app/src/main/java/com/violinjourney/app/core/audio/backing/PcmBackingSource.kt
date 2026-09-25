package com.violinjourney.app.core.audio.backing

/** A [BackingSource] over the prepared PCM file. */
class PcmBackingSource(private val reader: BackingPcmReader) : BackingSource {
    override fun read(position: Long, count: Int, gain: Float, left: FloatArray, right: FloatArray) =
        reader.read(position, count, gain, left, right)
}
