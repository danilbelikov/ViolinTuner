package com.violinjourney.app.feature.repertoire.piece

import androidx.compose.runtime.Composable
import com.violinjourney.app.core.domain.VideoQuality

/**
 * What the screen of a piece asks of the system (spec 3.15, 3.19, 3.32): its cameras, its pickers and its sheet of
 * «Поделиться». Each answer comes back through the callbacks given to [rememberPieceSystem]; a picker closed without
 * a choice answers with nothing picked.
 */
class PieceSystem(
    /** The system camera writes a photo of a sheet to [path]. */
    val launchCamera: (path: String) -> Unit,
    /** The system camera writes a video take to [path], in [quality] where it listens — on iOS (spec 3.19). */
    val launchVideoCamera: (path: String, quality: VideoQuality) -> Unit,
    val pickPhotos: () -> Unit,
    val pickVideo: () -> Unit,
    val pickBacking: () -> Unit,
    /** A shot that did not become a take goes to the system sheet as it is — the only way to keep it (spec 3.19). */
    val shareVideo: (path: String) -> Unit,
)

@Composable
expect fun rememberPieceSystem(
    onPhotosPicked: (uris: List<String>) -> Unit,
    onCameraFinished: (saved: Boolean) -> Unit,
    onVideoShot: (saved: Boolean) -> Unit,
    onVideoPicked: (uri: String?) -> Unit,
    onBackingPicked: (uri: String?) -> Unit,
): PieceSystem
