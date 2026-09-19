package com.example.violintuner.feature.share

import com.example.violintuner.feature.sound.SoundCaption
import java.io.File

enum class ShareVariant {
    /** What is heard in the app: the processed sound — with the picture, when the recording is a video take. */
    PROCESSED,

    /** The file as it was recorded or shot. */
    ORIGINAL,

    /** A video take only (spec 3.19): its sound alone, an `.m4a` like any recording's — processed when the processing does something. */
    SOUND,
}

/** What the sheet says about the recording to be sent. */
data class ShareInfo(
    val sessionId: Long,
    /** As the receiver will see it: «Менуэт соль мажор · 18 сентября.m4a». */
    val fileName: String,
    val durationMs: Long,
    /** Of the processed file — an estimate from its length and bit rate; of the original — its real size. */
    val processedBytes: Long,
    val originalBytes: Long,
    /** Which processing the recording has: named under «Обработанный звук». */
    val caption: SoundCaption,
    /** «Менуэт · 84 % · 18 сентября» — what goes along when the box is ticked. */
    val message: String,
    /** Set for a video take: the name of the `.mp4`; [fileName] is then the name of «Только звук». */
    val videoFileName: String? = null,
    /** The shorter side of the picture — «1080p»; zero when unknown. */
    val resolution: Int = 0,
    /** The processing does something. A video take shows its sheet even when it does not: there is still a choice to make. */
    val processed: Boolean = true,
) {
    val video: Boolean get() = videoFileName != null

    fun fileNameOf(variant: ShareVariant): String = if (video && variant != ShareVariant.SOUND) videoFileName!! else fileName

    /** An estimate for what is rendered, the real size for what is sent as it is. A processed video weighs what its picture does. */
    fun bytesOf(variant: ShareVariant): Long = when {
        video && variant == ShareVariant.PROCESSED -> originalBytes
        variant == ShareVariant.ORIGINAL -> originalBytes
        else -> processedBytes
    }
}

sealed interface ShareSheet {
    val info: ShareInfo

    /** The choice. [busy] — a short preparation is under way: the button says «Готовим…» instead of a progress screen flashing by. */
    data class Choose(override val info: ShareInfo, val variant: ShareVariant, val withText: Boolean, val busy: Boolean) : ShareSheet

    data class Preparing(override val info: ShareInfo, val percent: Int, val remainingSec: Int?) : ShareSheet

    /** Not a toast: a line in the sheet with two ways out. */
    data class Failed(override val info: ShareInfo) : ShareSheet
}

sealed interface ShareIntent {
    data class VariantSelected(val variant: ShareVariant) : ShareIntent

    data class TextToggled(val withText: Boolean) : ShareIntent

    data object ContinueClicked : ShareIntent

    /** «Отмена» of a preparation: at once, the unfinished file goes. */
    data object CancelClicked : ShareIntent

    data object RetryClicked : ShareIntent

    data object SendOriginalClicked : ShareIntent

    data object Dismissed : ShareIntent
}

sealed interface ShareEffect {
    /** Hand [file] to the system share sheet, with [text] when there is one. */
    data class Send(val file: File, val text: String?) : ShareEffect
}
