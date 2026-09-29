package com.violinjourney.app.feature.camera

/** A plate of glass over the shutter of the own camera (spec 3.36.4), in the order it is chosen by. */
enum class CapturePlate(
    /** The shutter sleeps under it; under the other two it does not — a press opens the microphone anew, or shoots. */
    val shutterSleeps: Boolean,
) {
    /** «Подключите наушники: минусовка через динамик попадёт в запись.» — without «Или выключите…»: there is no switch here. */
    NO_HEADPHONES(shutterSleeps = true),

    /** «Готовим минусовку…» (spec 5.25). */
    PREPARING(shutterSleeps = true),

    /** «Минусовку не удалось подготовить: не хватает места или файл повреждён.» (spec 5.25). */
    UNPREPARED(shutterSleeps = true),

    /** «Микрофон недоступен» — after a shot a lost microphone cut short; it goes with the first frame of sound of the next one. */
    MIC_UNAVAILABLE(shutterSleeps = false),

    /** «Мало места: хватит примерно на 7 мин». */
    LOW_SPACE(shutterSleeps = false),
}

/**
 * What stands over the shutter of the own camera — always one thing (spec 3.36.4): during a shot under the backing its progress;
 * before one the line «нет разрешения», else a plate in the order of [CapturePlate], else — under the backing — the line «наушники ·
 * минусовка» (a plate «Подключите наушники…» says of the headphones itself). Nothing without a backing and nothing to say. Pure.
 */
sealed interface CaptureAbove {
    data object NoPermission : CaptureAbove

    data class Plate(val plate: CapturePlate) : CaptureAbove

    /** «[наушники] Pixel Buds · [минусовка] минусовка 3:40»: what is checked before the start, in one place. */
    data object Ready : CaptureAbove

    /** «1:12 / 3:40» of the backing during a shot. */
    data object BackingProgress : CaptureAbove

    companion object {
        fun of(state: CaptureState): CaptureAbove? = when {
            state.recording -> BackingProgress.takeIf { state.underBacking && state.backingPlayedMs != null && state.backingDurationMs > 0 }
            state.cameraPermission == false || state.micPermission == false -> NoPermission
            state.underBacking && state.noHeadphones -> Plate(CapturePlate.NO_HEADPHONES)
            state.underBacking && state.preparing -> Plate(CapturePlate.PREPARING)
            state.underBacking && state.backingUnprepared -> Plate(CapturePlate.UNPREPARED)
            state.micUnavailable -> Plate(CapturePlate.MIC_UNAVAILABLE)
            state.spaceMinutes != null -> Plate(CapturePlate.LOW_SPACE)
            state.underBacking -> Ready
            else -> null
        }
    }
}

/**
 * How «[наушники] Pixel Buds · [минусовка] минусовка 3:40» stands (spec 3.36.4): one row while the length stands whole and the name
 * of the headphones keeps at least [nameLeast] of it — or all of itself, when shorter; the name gives way with an ellipsis, the
 * length never. Otherwise two lines, one under the other: the side column of 210 lying, a large font. Pure; in the units of the
 * caller.
 */
internal object ReadyLineFit {
    fun oneRow(width: Int, lengthWhole: Int, nameWhole: Int, nameLeast: Int, gap: Int): Boolean =
        lengthWhole + gap + minOf(nameWhole, nameLeast) <= width
}
