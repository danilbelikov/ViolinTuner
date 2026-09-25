package com.violinjourney.app.feature.repertoire.piece

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import com.violinjourney.app.core.ui.components.CameraDelegate
import com.violinjourney.app.core.ui.components.DocumentDelegate
import com.violinjourney.app.core.ui.components.MediaPickerDelegate
import com.violinjourney.app.core.ui.components.SystemScreens

/**
 * The system screens of a piece on iOS. The camera asks for its permission by itself; where there is no camera (the
 * simulator) a shot simply does not happen.
 */
@Composable
actual fun rememberPieceSystem(
    onPhotosPicked: (uris: List<String>) -> Unit,
    onCameraFinished: (saved: Boolean) -> Unit,
    onVideoShot: (saved: Boolean) -> Unit,
    onVideoPicked: (uri: String?) -> Unit,
    onBackingPicked: (uri: String?) -> Unit,
): PieceSystem {
    val photosPicked by rememberUpdatedState(onPhotosPicked)
    val cameraFinished by rememberUpdatedState(onCameraFinished)
    val videoShot by rememberUpdatedState(onVideoShot)
    val videoPicked by rememberUpdatedState(onVideoPicked)
    val backingPicked by rememberUpdatedState(onBackingPicked)
    return remember {
        // UIKit keeps its delegates weakly: they live here, as long as the screen
        var photoPath: String? = null
        var videoPath: String? = null
        val photos = MediaPickerDelegate(IMAGE_TYPE) { photosPicked(it) }
        val video = MediaPickerDelegate(MOVIE_TYPE) { videoPicked(it.firstOrNull()) }
        val photoCamera = CameraDelegate(video = false, outPath = { photoPath }) { cameraFinished(it) }
        val videoCamera = CameraDelegate(video = true, outPath = { videoPath }) { videoShot(it) }
        val document = DocumentDelegate { backingPicked(it) }
        PieceSystem(
            launchCamera = { path ->
                photoPath = path
                if (SystemScreens.cameraAvailable()) SystemScreens.present(SystemScreens.camera(video = false, photoCamera)) else cameraFinished(false)
            },
            launchVideoCamera = { path ->
                videoPath = path
                if (SystemScreens.cameraAvailable()) SystemScreens.present(SystemScreens.camera(video = true, videoCamera)) else videoShot(false)
            },
            pickPhotos = { SystemScreens.present(SystemScreens.mediaPicker(videos = false, limit = ANY_NUMBER, delegate = photos)) },
            pickVideo = { SystemScreens.present(SystemScreens.mediaPicker(videos = true, limit = 1, delegate = video)) },
            pickBacking = { SystemScreens.present(SystemScreens.audioPicker(document)) },
            shareVideo = SystemScreens::share,
        )
    }
}

private const val IMAGE_TYPE = "public.image"
private const val MOVIE_TYPE = "public.movie"
private const val ANY_NUMBER = 0L
