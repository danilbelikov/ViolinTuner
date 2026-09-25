package com.violinjourney.app.core.backup

import java.io.InputStream
import java.io.OutputStream
import java.util.Properties

/** The passport of a copy as Android writes it: `java.util.Properties`, the format of every copy made so far. */
fun BackupManifest.writeTo(out: OutputStream) {
    val p = Properties()
    p[BackupManifest.MAGIC_KEY] = BackupManifest.MAGIC
    p["format"] = formatVersion.toString()
    p["app"] = appVersion
    p["database"] = databaseVersion.toString()
    p["created"] = createdAtEpochMs.toString()
    p["device"] = device
    p["parts"] = parts.joinToString(",") { it.name }
    with(counts) {
        p["sessions"] = sessions.toString(); p["takes"] = takes.toString(); p["pieces"] = pieces.toString()
        p["pages"] = pages.toString(); p["practiceDays"] = practiceDays.toString(); p["trophies"] = trophies.toString()
        p["level"] = level.toString(); p["withSound"] = withSound.toString(); p["videos"] = videos.toString()
    }
    bytes.forEach { (part, size) -> p["bytes.${part.name}"] = size.toString() }
    p.store(out, null)
}

/** Null when [input] is not the passport of a copy of this app. */
fun BackupManifest.Companion.readFrom(input: InputStream): BackupManifest? {
    val p = Properties()
    try {
        p.load(input)
    } catch (_: IllegalArgumentException) {
        return null // a malformed \u escape: whatever this is, it is not ours
    }
    if (p.getProperty(BackupManifest.MAGIC_KEY) != BackupManifest.MAGIC) return null
    fun int(key: String) = p.getProperty(key)?.toIntOrNull()
    return BackupManifest(
        formatVersion = int("format") ?: return null,
        appVersion = p.getProperty("app").orEmpty(),
        databaseVersion = int("database") ?: return null,
        createdAtEpochMs = p.getProperty("created")?.toLongOrNull() ?: return null,
        device = p.getProperty("device").orEmpty(),
        parts = p.getProperty("parts").orEmpty().split(',').mapNotNull { name -> BackupPart.entries.firstOrNull { it.name == name } }.toSet() + BackupPart.DATA,
        counts = BackupCounts(
            sessions = int("sessions") ?: 0, takes = int("takes") ?: 0, pieces = int("pieces") ?: 0, pages = int("pages") ?: 0,
            practiceDays = int("practiceDays") ?: 0, trophies = int("trophies") ?: 0, level = int("level") ?: 1,
            withSound = int("withSound") ?: 0, videos = int("videos") ?: 0,
        ),
        bytes = BackupPart.entries.mapNotNull { part -> p.getProperty("bytes.${part.name}")?.toLongOrNull()?.let { part to it } }.toMap(),
    )
}
