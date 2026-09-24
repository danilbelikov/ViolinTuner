package com.violinjourney.app.feature.journey.art

import com.violinjourney.app.shared.resources.Res
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.MissingResourceException

/**
 * The text of a picture drawn after the handoff: `journey/<scene>.<mode>.scene`, `home/<house>.<mode>.scene` — the files of
 * the shared compose resources (tools/journey/export.js, tools/home/export.js write them). Null when there is no such
 * picture: a stop drawn only as a silhouette so far.
 */
@OptIn(ExperimentalResourceApi::class)
suspend fun readSceneText(path: String): String? = try {
    Res.readBytes("files/$path").decodeToString()
} catch (_: MissingResourceException) {
    null
}
