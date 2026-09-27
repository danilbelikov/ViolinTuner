package com.violinjourney.app.core.text

// Text as a person counts it, not as UTF-16 does: an emoji is one character, and a cut never halves it. Names, titles
// and file names that have a limit in characters cut here — a limit in UTF-16 units leaves a lone surrogate at its
// edge, which is drawn as a box.

/** At most [max] code points from the start; a surrogate pair is never torn. */
fun String.takeCodePoints(max: Int): String {
    var end = 0
    var taken = 0
    while (end < length && taken < max) {
        end = nextCodePointIndex(end)
        taken++
    }
    return substring(0, end)
}

/** How many code points the text has: what a counter «N / 24» shows. */
fun String.codePointLength(): Int {
    var index = 0
    var count = 0
    while (index < length) {
        index = nextCodePointIndex(index)
        count++
    }
    return count
}

/**
 * The first symbol as it is seen — the letter of an avatar: the first code point with everything that sticks to it —
 * combining marks («е» + ◌̈ is «ё»), variation selectors, skin tones, the tags of a subdivision flag, a code point
 * joined by a zero width joiner (a family is one symbol), and the second half of a flag. Empty for an empty text.
 */
fun String.firstSymbol(): String {
    if (isEmpty()) return ""
    val first = codePointAt(0)
    var end = nextCodePointIndex(0)
    if (first in REGIONAL_INDICATORS) {
        if (end < length && codePointAt(end) in REGIONAL_INDICATORS) end = nextCodePointIndex(end)
        return substring(0, end)
    }
    while (end < length) {
        val next = codePointAt(end)
        end = when {
            sticks(next) -> nextCodePointIndex(end)
            next == ZERO_WIDTH_JOINER && nextCodePointIndex(end) < length -> nextCodePointIndex(nextCodePointIndex(end))
            else -> return substring(0, end)
        }
    }
    return substring(0, end)
}

private const val ZERO_WIDTH_JOINER = 0x200D
private val REGIONAL_INDICATORS = 0x1F1E6..0x1F1FF
private val VARIATION_SELECTORS = 0xFE00..0xFE0F
private val SKIN_TONES = 0x1F3FB..0x1F3FF
private val TAGS = 0xE0020..0xE007F
private val MARKS = setOf(CharCategory.NON_SPACING_MARK, CharCategory.ENCLOSING_MARK, CharCategory.COMBINING_SPACING_MARK)
private const val BMP_END = 0x10000

/** A code point that belongs to the one before it rather than standing alone. */
private fun sticks(codePoint: Int): Boolean =
    codePoint in VARIATION_SELECTORS || codePoint in SKIN_TONES || codePoint in TAGS ||
        (codePoint < BMP_END && codePoint.toChar().category in MARKS)

private fun String.isPairAt(index: Int): Boolean = this[index].isHighSurrogate() && index + 1 < length && this[index + 1].isLowSurrogate()

private fun String.nextCodePointIndex(index: Int): Int = if (isPairAt(index)) index + 2 else index + 1

private fun String.codePointAt(index: Int): Int =
    if (isPairAt(index)) {
        ((this[index].code - Char.MIN_HIGH_SURROGATE.code) shl SURROGATE_BITS) + (this[index + 1].code - Char.MIN_LOW_SURROGATE.code) + BMP_END
    } else {
        this[index].code
    }

private const val SURROGATE_BITS = 10
