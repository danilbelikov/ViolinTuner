package com.violinjourney.app.core.recording.video

/**
 * A video picked in the system's library (spec 3.19, 5.13): the picker hands it over at once, its file not always. The picker of
 * Android gives the file itself ([Ready]); the library of an iPhone makes it only when asked ([Coming]) — downloads it from iCloud,
 * renders a trim, a slow motion or a cinematic video — which may take a minute or two, and «Добавляем видео…» stands meanwhile (0.94).
 */
sealed interface VideoPick {
    /** The file is there: the `content:` URI the picker gave. */
    data class Ready(val uri: String) : VideoPick

    /**
     * The file is still to be made: [make] makes it and answers with its `file:` URI — the app's own copy, as every pick of iOS is
     * ([VideoFiles.release] lets it go) — or throws when the system could not (no network for iCloud, a failure of its own). Nothing is
     * made before it is called; cancelling the call stops the system's work, and nothing of it is left.
     */
    class Coming(val make: suspend () -> String) : VideoPick
}
