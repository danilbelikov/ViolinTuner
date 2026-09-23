package com.violinjourney.app.core.domain.repertoire.scale

import com.violinjourney.app.core.domain.repertoire.Tonic
import kotlin.math.abs

/**
 * Metrics of the notation (handoff 24h7), all in staff spaces — the distance between two lines
 * of the staff is 1. The drawing multiplies by its own size of a space; nothing here is pixels.
 */
object NotationMetrics {
    const val LINE = 0.10f
    const val STEM = 0.12f
    const val LEDGER = 0.14f
    const val LEDGER_EXTENT = 0.32f
    const val BAR_THIN = 0.12f
    const val BAR_THICK = 0.45f
    const val BAR_GAP = 0.35f
    const val STEM_LENGTH = 3.5f
    const val STEM_INSET = 0.15f
    const val STEM_OFFSET = 0.56f
    const val PAD = 0.8f
    const val CLEF_X = 1.6f
    const val CLEF_WIDTH = 3.4f
    const val CLEF_GAP = 0.7f
    const val SIGN_FIRST = 0.45f
    const val SIGN_STEP = 0.95f
    const val SIGNS_TO_NOTES = 1.6f
    const val NOTE_STEP = 2.4f
    const val ACCIDENTAL_EXTRA = 1.1f
    const val ACCIDENTAL_BEFORE = 1.35f
    const val HEAD_HALF_WIDTH = 0.62f
    const val RIGHT_RESERVE = 1.4f
    const val SYSTEM_TOP = 6f
    const val SYSTEM_BOTTOM = 3f
    const val STAFF_HEIGHT = 4f
    const val SYSTEM_HEIGHT = SYSTEM_TOP + STAFF_HEIGHT + SYSTEM_BOTTOM
    const val OTTAVA_TEXT = 1.1f
    const val OTTAVA_DASH = 0.5f
    const val OTTAVA_DASH_GAP = 0.4f
    const val OTTAVA_HOOK = 0.8f

    /** A system with an «8va» is taller by this: five ledger lines fill the usual room, the bracket needs its own (not in the handoff — found on the device). */
    const val OTTAVA_ROOM = 2.5f

    /** From this position on a note would need a sixth ledger line: it is written an octave lower under «8va». */
    const val OTTAVA_FROM = 20
    const val OCTAVE_POSITIONS = 7
    const val MIDDLE_LINE = 4
    const val TOP_LINE = 8
    const val FIRST_LEDGER_ABOVE = 10
    const val FIRST_LEDGER_BELOW = -2
    const val CLEF_LINE = 2

    /** Where each sign of a key signature sits in the treble clef: sharps F C G D A E B, flats B E A D G C F. */
    val SHARP_POSITIONS = mapOf(Tonic.F to 8, Tonic.C to 5, Tonic.G to 9, Tonic.D to 6, Tonic.A to 3, Tonic.E to 7, Tonic.B to 4)
    val FLAT_POSITIONS = mapOf(Tonic.B to 4, Tonic.E to 7, Tonic.A to 3, Tonic.D to 6, Tonic.G to 2, Tonic.C to 5, Tonic.F to 1)
}

/** One sign of the key signature of a system. */
data class PlacedSign(val x: Float, val position: Int, val sharp: Boolean)

/** A note where it stands in its system. [x] is the centre of the head; [position] is where it is drawn — an octave lower under «8va». */
data class PlacedNote(
    val note: ScaleNote,
    val x: Float,
    val position: Int,
    /** The sign to draw before the head: −1 flat, 0 natural, 1 sharp, 2 double sharp; null when the state of the step already says it. */
    val accidental: Int?,
    val stemUp: Boolean,
    /** Positions of the ledger lines this note needs, nearest to the staff first. */
    val ledgers: List<Int>,
    val ottava: Boolean,
)

/** A run of notes under one «8va» bracket: from the left edge of the first head to the right edge of the last. */
data class OttavaSpan(val fromX: Float, val toX: Float)

data class EngravedSystem(val notes: List<PlacedNote>, val ottavas: List<OttavaSpan>, val last: Boolean) {
    /** Room above the usual top of the system. */
    val extraTop: Float get() = if (ottavas.isEmpty()) 0f else NotationMetrics.OTTAVA_ROOM

    val height: Float get() = NotationMetrics.SYSTEM_HEIGHT + extraTop
}

/** A scale laid out for a given width: every system carries the clef and [signs] again. */
data class Engraving(
    val widthSp: Float,
    val signs: List<PlacedSign>,
    val systems: List<EngravedSystem>,
) {
    val heightSp: Float get() = heightOf(systems.size)

    /** Height of the first [count] systems: what a folded card shows. */
    fun heightOf(count: Int): Float = systems.take(count).sumOf { it.height.toDouble() }.toFloat()

    /** Where the system with this index begins. */
    fun top(systemIndex: Int): Float = heightOf(systemIndex)

    /** y of a staff position inside the system with this index; grows downwards like the canvas. */
    fun y(systemIndex: Int, position: Int): Float =
        top(systemIndex) + systems[systemIndex].extraTop + NotationMetrics.SYSTEM_TOP + NotationMetrics.STAFF_HEIGHT - position / 2f
}

/**
 * Lays a scale out as notation (spec 3.22, 5.16; handoff 24h): how many notes go into a system,
 * which of them need an accidental, a ledger line, a stem up or down, the «8va». Pure — the
 * canvas only draws what this says, and the tests hold it against the reference frames.
 */
object ScaleEngraver {
    fun engrave(scale: Scale, widthSp: Float): Engraving {
        val m = NotationMetrics
        val signCount = abs(scale.fifths)
        val sharp = scale.fifths > 0
        val order = if (sharp) Scales.SHARP_ORDER else Scales.FLAT_ORDER
        val positions = if (sharp) m.SHARP_POSITIONS else m.FLAT_POSITIONS
        val signsStart = m.PAD + m.CLEF_WIDTH + m.CLEF_GAP
        val signs = order.take(signCount).mapIndexed { index, letter ->
            PlacedSign(signsStart + m.SIGN_FIRST + index * m.SIGN_STEP, positions.getValue(letter), sharp)
        }
        val notesStart = signsStart + signCount * m.SIGN_STEP + m.SIGNS_TO_NOTES
        val available = widthSp - notesStart - m.PAD - m.RIGHT_RESERVE

        // What each step of each octave currently carries. Not reset between systems: there are no
        // bars, a scale is one phrase — hence the naturals on the way down the melodic minor.
        data class Marked(val note: ScaleNote, val accidental: Int?)
        val state = HashMap<Pair<Tonic, Int>, Int>()
        val marked = scale.notes.map { note ->
            val key = note.letter to note.octave
            val carried = state[key] ?: scale.signature.getValue(note.letter)
            state[key] = note.alter
            Marked(note, note.alter.takeIf { it != carried })
        }
        fun widthOf(item: Marked) = m.NOTE_STEP + if (item.accidental != null) m.ACCIDENTAL_EXTRA else 0f

        /** [sizes] — how many notes each system would like to hold; the width still has the last word. */
        fun split(sizes: List<Int>): List<List<Marked>> {
            val systems = mutableListOf<MutableList<Marked>>(mutableListOf())
            var used = 0f
            marked.forEach { item ->
                val current = systems.last()
                val wanted = sizes.getOrNull(systems.lastIndex)
                if (current.isNotEmpty() && (used + widthOf(item) > available || (wanted != null && current.size >= wanted))) {
                    systems += mutableListOf<Marked>()
                    used = 0f
                }
                systems.last() += item
                used += widthOf(item)
            }
            return systems
        }
        // First as many as fit, to learn how many systems there are; then evenly by count — 43 notes
        // read better as 15 · 14 · 14 than as 17 · 17 · 9.
        val greedy = split(emptyList())
        fun even(count: Int) = List(count) { index -> marked.size / count + if (index < marked.size % count) 1 else 0 }
        // An even share may not fit where accidentals crowd: then one more system, shared evenly again —
        // never a last system of a single note (found on the device with gis-moll in three octaves).
        var count = greedy.size
        var lines = if (count > 1) split(even(count)) else greedy
        while (lines.size > count && count < marked.size) {
            count = lines.size
            lines = split(even(count))
        }

        val systems = lines.mapIndexed { index, line ->
            var x = notesStart
            val placed = line.map { item ->
                val centre = x + (if (item.accidental != null) m.ACCIDENTAL_EXTRA else 0f) + m.HEAD_HALF_WIDTH
                x += widthOf(item)
                val ottava = item.note.position >= m.OTTAVA_FROM
                val position = if (ottava) item.note.position - m.OCTAVE_POSITIONS else item.note.position
                PlacedNote(
                    note = item.note,
                    x = centre,
                    position = position,
                    accidental = item.accidental,
                    stemUp = position < m.MIDDLE_LINE,
                    ledgers = ledgersOf(position),
                    ottava = ottava,
                )
            }
            EngravedSystem(placed, ottavasOf(placed), last = index == lines.lastIndex)
        }
        return Engraving(widthSp, signs, systems)
    }

    private fun ledgersOf(position: Int): List<Int> = when {
        position >= NotationMetrics.FIRST_LEDGER_ABOVE -> (NotationMetrics.FIRST_LEDGER_ABOVE..position step 2).toList()
        position <= NotationMetrics.FIRST_LEDGER_BELOW -> (NotationMetrics.FIRST_LEDGER_BELOW downTo position step 2).toList()
        else -> emptyList()
    }

    private fun ottavasOf(notes: List<PlacedNote>): List<OttavaSpan> {
        val spans = mutableListOf<OttavaSpan>()
        var from: Float? = null
        var to = 0f
        notes.forEach { note ->
            if (note.ottava) {
                if (from == null) from = note.x - SPAN_HALF
                to = note.x + SPAN_HALF
            } else {
                from?.let { spans += OttavaSpan(it, to) }
                from = null
            }
        }
        from?.let { spans += OttavaSpan(it, to) }
        return spans
    }

    private const val SPAN_HALF = 0.7f
}
