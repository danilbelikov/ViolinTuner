package com.violinjourney.app.feature.camera

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.tooling.preview.Preview
import com.violinjourney.app.core.ui.components.BusyPicture
import com.violinjourney.app.core.ui.motion.LocalReduceMotion
import com.violinjourney.app.core.ui.theme.ViolinTheme

// The own camera of R4 (spec 3.36.4; repertoire.html 7): a busy picture in place of the viewfinder — light, dark and colour side by
// side, as a camera's preview is — so the glass of the plates and the line «нет разрешения» is seen over what it has to hold.

private val Ready = CaptureState(
    title = "Концерт ля минор, 1 ч.", cameraPermission = true, micPermission = true,
    backingTitle = "Вивальди — фортепиано", backingDurationMs = 220_000, underBacking = true, headphonesName = "Pixel Buds",
)

@Composable
private fun Camera(state: CaptureState) {
    ViolinTheme {
        CompositionLocalProvider(LocalReduceMotion provides true) {
            CaptureScreen(state = state, viewfinder = { BusyPicture(it) }, onIntent = {})
        }
    }
}

@Preview(name = "Своя камера · готово: «Pixel Buds · минусовка 3:40» над спуском", locale = "ru", device = "spec:width=412dp,height=892dp")
@Composable
private fun ReadyPreview() = Camera(Ready)

@Preview(name = "Своя камера · съёмка: таймер таблеткой сверху, ход минусовки над «стоп», смена камеры 0,38", locale = "ru", device = "spec:width=412dp,height=892dp")
@Composable
private fun ShootingPreview() = Camera(Ready.copy(recording = true, elapsedSeconds = 72, backingPlayedMs = 72_000))

@Preview(name = "Своя камера · без наушников: плашка на плотном стекле, спуск спит", locale = "ru", device = "spec:width=412dp,height=892dp")
@Composable
private fun NoHeadphonesPreview() = Camera(Ready.copy(noHeadphones = true, headphonesName = null))

@Preview(name = "Своя камера · готовим минусовку: плашка, спуск спит", locale = "ru", device = "spec:width=412dp,height=892dp")
@Composable
private fun PreparingPreview() = Camera(Ready.copy(preparing = true))

@Preview(name = "Своя камера · нет разрешения: строка R1 на стекле 0,82 и «Разрешить доступ», спуск 0,38", locale = "ru", device = "spec:width=412dp,height=892dp")
@Composable
private fun NoPermissionPreview() = Camera(Ready.copy(micPermission = false))

@Preview(name = "Своя камера · «Микрофон недоступен» после оборванной съёмки: спуск не спит", locale = "ru", device = "spec:width=412dp,height=892dp")
@Composable
private fun MicUnavailablePreview() = Camera(Ready.copy(micUnavailable = true))

@Preview(name = "Своя камера · «Мало места: хватит примерно на 7 мин»", locale = "ru", device = "spec:width=412dp,height=892dp")
@Composable
private fun LowSpacePreview() = Camera(Ready.copy(underBacking = false, spaceMinutes = 7))

@Preview(name = "Своя камера · камера не открылась: строка по центру, спуск спит", locale = "ru", device = "spec:width=412dp,height=892dp")
@Composable
private fun CameraFailedPreview() = Camera(Ready.copy(cameraFailed = true))

@Preview(name = "Своя камера · собираем видео: карточка на затемнении, ✕ приглушён", locale = "ru", device = "spec:width=412dp,height=892dp")
@Composable
private fun SavingPreview() = Camera(Ready.copy(saving = true))

@Preview(name = "Своя камера · landscape 892 × 412: колонка 210 справа, верхняя строка на 70 %, наушники над минусовкой двумя строками", locale = "ru", device = "spec:width=892dp,height=412dp")
@Composable
private fun LandscapePreview() = Camera(Ready)

@Preview(name = "Своя камера · landscape 892 × 412, съёмка", locale = "ru", device = "spec:width=892dp,height=412dp")
@Composable
private fun LandscapeShootingPreview() = Camera(Ready.copy(recording = true, elapsedSeconds = 72, backingPlayedMs = 72_000))

@Preview(name = "Своя камера · нет разрешения, fr, 360, шрифт 1,3", locale = "fr", fontScale = 1.3f, device = "spec:width=360dp,height=640dp")
@Composable
private fun FrenchNoPermissionPreview() = Camera(Ready.copy(cameraPermission = false))

@Preview(name = "Своя камера · landscape 892 × 412, нет разрешения: причина целиком, без многоточия", locale = "ru", device = "spec:width=892dp,height=412dp")
@Composable
private fun LandscapeNoPermissionPreview() = Camera(Ready.copy(cameraPermission = false))

@Preview(name = "Своя камера · landscape 640 × 360, нет разрешения, en: четыре строки", locale = "en", device = "spec:width=640dp,height=360dp")
@Composable
private fun SmallLandscapeNoPermissionPreview() = Camera(Ready.copy(cameraPermission = false))

@Preview(name = "Своя камера · es, шрифт 1,3: «acompañamiento 3:40» целиком", locale = "es", fontScale = 1.3f, device = "spec:width=360dp,height=640dp")
@Composable
private fun SpanishLargeReadyPreview() = Camera(Ready)
