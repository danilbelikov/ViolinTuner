package com.violinjourney.app.core.io

/** A file on the device: `java.io.File` on Android, so the Android code keeps using File as it always has. */
expect class PlatformFile

/** Where the file is: the path the platform opens it by. */
expect val PlatformFile.filePath: String

/** The size of the file in bytes; 0 for a file that is not there. */
expect fun PlatformFile.sizeBytes(): Long

/** The last part of the path: what the database keeps. */
expect val PlatformFile.fileName: String

/** Deletes the file; true when it is gone now. */
expect fun PlatformFile.deleteFile(): Boolean

/** The file at [path]. */
expect fun platformFile(path: String): PlatformFile

/** The file as a `file:` URI, the way pickers hand files over. */
expect val PlatformFile.fileUri: String
