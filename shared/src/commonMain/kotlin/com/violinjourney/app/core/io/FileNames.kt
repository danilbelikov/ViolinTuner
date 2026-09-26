package com.violinjourney.app.core.io

/**
 * A bare name of a file in its folder — what the database keeps for a recording, a video, a page of notes, a photo of the
 * profile, a backing. The app makes these names itself (a UUID, «avatar-…»), but a database may come from a copy picked by
 * hand (spec 3.20): a name that is empty, «.», «..», or holds a separator or a NUL would reach outside its folder — and a
 * delete by such a name would take the database or a whole folder with it. Such a name is not one of ours.
 */
fun isOwnFileName(name: String): Boolean =
    name.isNotEmpty() && name != "." && name != ".." && '/' !in name && '\\' !in name && '\u0000' !in name
