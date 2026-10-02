package com.violinjourney.app.feature.share

import com.violinjourney.app.core.audio.share.ShareNames
import com.violinjourney.app.feature.sound.SoundCaption
import com.violinjourney.app.core.io.PlatformFile

enum class ShareVariant {
    /** A take under a backing (spec 3.32): the processed violin and the backing mixed — the video with that sound, for a video take. */
    BACKING,

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
    /** Made under a backing that is still there: «С минусовкой» is offered, and first. */
    val backing: Boolean = false,
    /** Of the file «С минусовкой» — an estimate; a video weighs what its picture does. */
    val backingBytes: Long = 0,
    /**
     * A video take's file as it was shot, in its own container: «….mov» from the camera of an iPhone. What is made here —
     * processed, under the backing — is always an `.mp4` ([videoFileName]). Null — the same as [videoFileName].
     */
    val originalVideoFileName: String? = null,
    /**
     * A recording without a picture as it was recorded, in the format of its own file (plan D48): «….mp3» of a sound brought in from a
     * file (spec 5.28), «….m4a» of a take. What is made here — the processed sound — is always an `.m4a` ([fileName]). Null — [fileName].
     */
    val originalAudioFileName: String? = null,
) {
    val video: Boolean get() = videoFileName != null

    fun fileNameOf(variant: ShareVariant): String = when {
        !video && variant == ShareVariant.ORIGINAL -> originalAudioFileName ?: fileName
        !video || variant == ShareVariant.SOUND -> fileName
        variant == ShareVariant.ORIGINAL -> originalVideoFileName ?: videoFileName!!
        else -> videoFileName!!
    }

    /**
     * The format of the file a variant makes (spec 3.36.5, the chip of each variant): «.m4a» of a sound, «.mp4» of a video made here,
     * a video as shot in the container of its own file — «.mov» from the camera of an iPhone — and a sound from a file as it came,
     * «.mp3».
     */
    fun extensionOf(variant: ShareVariant): String = "." + fileNameOf(variant).substringAfterLast('.', "").lowercase()

    /**
     * The type other apps are told the file of a variant is (Android's `Intent.type`, plan D48), by what the recording is, not by the name
     * made of its title: a recording without a picture sends sound whatever the extension of its own file — a sound from a file of a kind
     * not known here goes as any sound, never as a video ([ShareNames.soundTypeOf]); a video take sends its video, or its sound alone.
     */
    fun typeOf(variant: ShareVariant): String {
        val name = fileNameOf(variant)
        return if (video) ShareNames.mimeTypeOf(name) else ShareNames.soundTypeOf(name)
    }

    /** An estimate for what is rendered, the real size for what is sent as it is. A processed video weighs what its picture does. */
    fun bytesOf(variant: ShareVariant): Long = when {
        video && (variant == ShareVariant.PROCESSED || variant == ShareVariant.BACKING) -> originalBytes
        variant == ShareVariant.BACKING -> backingBytes
        variant == ShareVariant.ORIGINAL -> originalBytes
        else -> processedBytes
    }

    companion object {
        /**
         * From this size messengers squeeze a file or refuse it (spec 3.19): said in the colour of danger, bold — in the line of the
         * file of «Поделиться» and under «Удалить…» of a recording's «⋯» (spec 3.36.5, 5.29 R5).
         */
        const val LARGE_BYTES = 100L * 1024 * 1024

        /** A file of [bytes] is large: from [LARGE_BYTES] on, that very size included. */
        fun isLarge(bytes: Long): Boolean = bytes >= LARGE_BYTES
    }
}

sealed interface ShareSheet {
    val info: ShareInfo

    /** The choice. [busy] — a short preparation is under way: the button says «Готовим…» instead of a progress screen flashing by. */
    data class Choose(override val info: ShareInfo, val variant: ShareVariant, val withText: Boolean, val busy: Boolean) : ShareSheet

    /**
     * The file is being made — in the same sheet, which holds meanwhile (spec 3.36.5): «Готовим файл» or «Готовим видео», the percent,
     * and the line of the [variant] chosen with its format and, once there is an estimate, the seconds left ([remainingSec]).
     */
    data class Preparing(override val info: ShareInfo, val variant: ShareVariant, val percent: Int, val remainingSec: Int?) : ShareSheet

    /** Not a toast: a plate in the sheet with two ways out — «Ещё раз» and the original as it is. */
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

    /**
     * The sheet was swiped down, tapped beside or closed with «назад»: the choice and a failure only hide — nothing is sent; a file
     * being made (its progress, or «Готовим…» on «Продолжить») is not stopped by it — the sheet holds then, and «Отмена» stops it.
     */
    data object Dismissed : ShareIntent
}

sealed interface ShareEffect {
    /** Hand [file] to the system share sheet, with [text] when there is one; [type] — what the receivers are told it is ([ShareInfo.typeOf]). */
    data class Send(val file: PlatformFile, val text: String?, val type: String) : ShareEffect
}
