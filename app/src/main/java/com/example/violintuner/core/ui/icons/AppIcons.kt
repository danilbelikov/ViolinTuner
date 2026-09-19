package com.example.violintuner.core.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * The one icon set of the app (spec 3.16). There is no icon library in the project: the vectors
 * are built from the path strings of the handoff, each on first use. Single-coloured — the
 * colour here is a placeholder that the tint of [AppIcon] replaces.
 */
object AppIcons {
    val Back: ImageVector by lazy { icon("Back", IconPaths.BACK) }
    val Close: ImageVector by lazy { icon("Close", IconPaths.CLOSE) }
    val ChevronDown: ImageVector by lazy { icon("ChevronDown", IconPaths.CHEVRON_DOWN) }
    val ChevronLeft: ImageVector by lazy { icon("ChevronLeft", IconPaths.CHEVRON_LEFT) }
    val ChevronRight: ImageVector by lazy { icon("ChevronRight", IconPaths.CHEVRON_RIGHT) }
    val More: ImageVector by lazy { icon("More", IconPaths.MORE) }
    val Pencil: ImageVector by lazy { icon("Pencil", IconPaths.PENCIL) }
    val Trash: ImageVector by lazy { icon("Trash", IconPaths.TRASH) }
    val Plus: ImageVector by lazy { icon("Plus", IconPaths.PLUS) }
    val Check: ImageVector by lazy { icon("Check", IconPaths.CHECK) }
    val Camera: ImageVector by lazy { icon("Camera", IconPaths.CAMERA) }
    val Gallery: ImageVector by lazy { icon("Gallery", IconPaths.GALLERY) }
    val Sheet: ImageVector by lazy { icon("Sheet", IconPaths.SHEET) }
    val Mic: ImageVector by lazy { icon("Mic", IconPaths.MIC) }
    val Play: ImageVector by lazy { icon("Play", IconPaths.PLAY) }
    val Pause: ImageVector by lazy { icon("Pause", IconPaths.PAUSE) }
    val Lock: ImageVector by lazy { icon("Lock", IconPaths.LOCK) }
    val Timer: ImageVector by lazy { icon("Timer", IconPaths.TIMER) }
    val Flag: ImageVector by lazy { icon("Flag", IconPaths.FLAG) }
    val Clock: ImageVector by lazy { icon("Clock", IconPaths.CLOCK) }
    val Trophy: ImageVector by lazy { icon("Trophy", IconPaths.TROPHY) }
    val NoteOne: ImageVector by lazy { icon("NoteOne", IconPaths.NOTE_ONE) }
    val NotePair: ImageVector by lazy { icon("NotePair", IconPaths.NOTE_PAIR) }
    val Select: ImageVector by lazy { icon("Select", IconPaths.SELECT) }
    val Star: ImageVector by lazy { icon("Star", IconPaths.STAR) }
    val Metronome: ImageVector by lazy { icon("Metronome", IconPaths.METRONOME) }
    val Stand: ImageVector by lazy { icon("Stand", IconPaths.STAND) }
    val Fork: ImageVector by lazy { icon("Fork", IconPaths.FORK) }
    val Target: ImageVector by lazy { icon("Target", IconPaths.TARGET) }
    val Repeat: ImageVector by lazy { icon("Repeat", IconPaths.REPEAT) }
    val Share: ImageVector by lazy { icon("Share", IconPaths.SHARE) }
    val Sound: ImageVector by lazy { icon("Sound", IconPaths.SOUND) }
    val Ab: ImageVector by lazy { icon("Ab", IconPaths.AB) }
    val Eq: ImageVector by lazy { icon("Eq", IconPaths.EQ) }
    val Compressor: ImageVector by lazy { icon("Compressor", IconPaths.COMPRESSOR) }
    val Hall: ImageVector by lazy { icon("Hall", IconPaths.HALL) }
    val Volume: ImageVector by lazy { icon("Volume", IconPaths.VOLUME) }
    val VolumeOff: ImageVector by lazy { icon("VolumeOff", IconPaths.VOLUME_OFF) }
    val Reset: ImageVector by lazy { icon("Reset", IconPaths.RESET) }
    val Preset: ImageVector by lazy { icon("Preset", IconPaths.PRESET) }
    val Minus: ImageVector by lazy { icon("Minus", IconPaths.MINUS) }
    val FileAudio: ImageVector by lazy { icon("FileAudio", IconPaths.FILE_AUDIO) }
    val Limiter: ImageVector by lazy { icon("Limiter", IconPaths.LIMITER) }

    val TabLive: TabIcon by lazy { tab("TabLive", IconPaths.TAB_LIVE) }
    val TabPractice: TabIcon by lazy { tab("TabPractice", IconPaths.TAB_PRACTICE) }
    val TabRecords: TabIcon by lazy { tab("TabRecords", IconPaths.TAB_RECORDS) }
    val TabSettings: TabIcon by lazy { tab("TabSettings", IconPaths.TAB_SETTINGS) }

    /** Every plain icon by name: for the test that builds them all, and for a gallery preview. */
    val all: List<Pair<String, () -> ImageVector>> = listOf(
        "Back" to { Back },
        "Close" to { Close },
        "ChevronDown" to { ChevronDown },
        "ChevronLeft" to { ChevronLeft },
        "ChevronRight" to { ChevronRight },
        "More" to { More },
        "Pencil" to { Pencil },
        "Trash" to { Trash },
        "Plus" to { Plus },
        "Check" to { Check },
        "Camera" to { Camera },
        "Gallery" to { Gallery },
        "Sheet" to { Sheet },
        "Mic" to { Mic },
        "Play" to { Play },
        "Pause" to { Pause },
        "Lock" to { Lock },
        "Timer" to { Timer },
        "Flag" to { Flag },
        "Clock" to { Clock },
        "Trophy" to { Trophy },
        "NoteOne" to { NoteOne },
        "NotePair" to { NotePair },
        "Select" to { Select },
        "Star" to { Star },
        "Metronome" to { Metronome },
        "Stand" to { Stand },
        "Fork" to { Fork },
        "Target" to { Target },
        "Repeat" to { Repeat },
        "Share" to { Share },
        "Sound" to { Sound },
        "Ab" to { Ab },
        "Eq" to { Eq },
        "Compressor" to { Compressor },
        "Hall" to { Hall },
        "Volume" to { Volume },
        "VolumeOff" to { VolumeOff },
        "Reset" to { Reset },
        "Preset" to { Preset },
        "Minus" to { Minus },
        "FileAudio" to { FileAudio },
        "Limiter" to { Limiter },
    )

    val tabs: List<() -> TabIcon> = listOf({ TabLive }, { TabPractice }, { TabRecords }, { TabSettings })

    const val GRID = 24f
    const val STROKE = 1.8f

    private fun icon(name: String, paths: List<String>): ImageVector =
        builder(name).apply { paths.forEach { path(it, fillBody = false) } }.build()

    /**
     * A selected tab is the same outline with its body filled; a detail inside the body (the hand
     * of the stopwatch) would drown in it, so it goes to a layer of its own that the bar paints in
     * the colour of the pill — cut out, as far as the eye can tell.
     */
    private fun tab(name: String, paths: List<IconPaths.TabPath>): TabIcon {
        val cut = paths.filter { it.selected == IconPaths.Selected.CUT }
        return TabIcon(
            normal = builder(name).apply { paths.forEach { path(it.d, fillBody = false) } }.build(),
            selected = builder("${name}Selected").apply {
                paths.filter { it.selected != IconPaths.Selected.CUT }.forEach { path(it.d, fillBody = it.selected == IconPaths.Selected.FILL) }
            }.build(),
            selectedCut = if (cut.isEmpty()) null else builder("${name}Cut").apply { cut.forEach { path(it.d, fillBody = false) } }.build(),
        )
    }

    private fun builder(name: String) = ImageVector.Builder(name, GRID.dp, GRID.dp, GRID, GRID)

    private fun ImageVector.Builder.path(data: String, fillBody: Boolean) {
        val filledOnly = data.startsWith(IconPaths.FILLED)
        val nodes = PathParser().parsePathString(data.removePrefix(IconPaths.FILLED)).toNodes()
        val ink = SolidColor(Color.Black)
        addPath(
            pathData = nodes,
            fill = if (filledOnly || fillBody) ink else null,
            stroke = if (filledOnly) null else ink,
            strokeLineWidth = if (filledOnly) 0f else STROKE,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        )
    }
}

/** A tab icon in its two states; [selectedCut] is drawn over [selected] in the colour of the pill. */
class TabIcon(val normal: ImageVector, val selected: ImageVector, val selectedCut: ImageVector?)
