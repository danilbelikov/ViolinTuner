package com.violinjourney.app.feature.onboarding

import com.violinjourney.app.feature.onboarding.OnboardingProgressMath.Segment
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** The strip of the way (spec 3.36.8, 5.29 R8): one for the seven screens, its segments, its break and how fast it fills. */
class OnboardingProgressTest {

    @Test
    fun `every screen has its number on the way of seven`() {
        assertEquals(7, OnboardingProgressMath.count)
        assertEquals((1..7).toList(), OnboardingStep.entries.map { it.number })
        assertEquals(1, OnboardingStep.WELCOME.number)
        assertEquals(4, OnboardingStep.DATA.number)
        assertEquals(5, OnboardingStep.MICROPHONE.number)
        assertEquals(7, OnboardingStep.TOLERANCE.number)
    }

    @Test
    fun `screens 1 to 4 are the introduction and 5 to 7 the setup`() {
        val parts = OnboardingStep.entries.groupBy({ it.part }, { it.number })
        assertEquals(listOf(1, 2, 3, 4), parts.getValue(OnboardingPart.INTRO))
        assertEquals(listOf(5, 6, 7), parts.getValue(OnboardingPart.SETUP))
    }

    @Test
    fun `the segments before the screen are passed and after it are ahead`() {
        val onMicrophone = (0 until OnboardingProgressMath.count).map { OnboardingProgressMath.segmentState(it, OnboardingStep.MICROPHONE) }
        assertEquals(List(4) { Segment.DONE } + Segment.CURRENT + List(2) { Segment.AHEAD }, onMicrophone)
        val onWelcome = (0 until OnboardingProgressMath.count).map { OnboardingProgressMath.segmentState(it, OnboardingStep.WELCOME) }
        assertEquals(listOf(Segment.CURRENT) + List(6) { Segment.AHEAD }, onWelcome)
        val onLast = (0 until OnboardingProgressMath.count).map { OnboardingProgressMath.segmentState(it, OnboardingStep.TOLERANCE) }
        assertEquals(List(6) { Segment.DONE } + Segment.CURRENT, onLast)
    }

    @Test
    fun `the break of the parts stands after the fourth segment only`() {
        val breaks = (0 until OnboardingProgressMath.count).filter { OnboardingProgressMath.partGapAfter(it) }
        assertEquals(listOf(3), breaks, "after the page about the data and nowhere else")
    }

    @Test
    fun `a segment fills in 200 ms and a jump of skip under its dissolve of 450`() {
        assertEquals(200, OnboardingProgressMath.fillMs(from = 1, to = 2, still = false))
        assertEquals(200, OnboardingProgressMath.fillMs(from = 5, to = 4, still = false), "back one screen")
        assertEquals(450, OnboardingProgressMath.fillMs(from = 1, to = 4, still = false), "«Пропустить» from the first page")
        assertEquals(450, OnboardingProgressMath.fillMs(from = 2, to = 4, still = false), "«Пропустить» from the second page")
        assertEquals(450, OnboardingProgressMath.fillMs(from = 1, to = 3, still = false))
        assertEquals(200, OnboardingProgressMath.fillMs(from = 4, to = 4, still = false))
    }

    @Test
    fun `with the animations removed the strip fills at once`() {
        assertEquals(0, OnboardingProgressMath.fillMs(from = 1, to = 2, still = true))
        assertEquals(0, OnboardingProgressMath.fillMs(from = 1, to = 4, still = true))
    }

    // ---- the label (the review of stage 120). The widths are dp at a density of 1 at 12 sp, measured by CoreText on Manrope (700,
    // 0.06 em, tabular figures for the count); the font scale linear, as in the window of the tests; the strip lying on the 603 of the
    // emulator's 640 × 360 is its row of 285.5 less «Пропустить».

    @Test
    fun `the count goes under the part only where the wider label does not stand on one line`() {
        assertEquals(ProgressLabelFit(12f, oneLine = true), label(width = 200f, intro = 100f, setup = 90f, count = 40f))
        assertEquals(ProgressLabelFit(12f, oneLine = true), label(width = 200f, intro = 148f, setup = 90f, count = 40f), "on the edge — one line")
        assertEquals(ProgressLabelFit(12f, oneLine = false), label(width = 200f, intro = 149f, setup = 90f, count = 40f))
    }

    @Test
    fun `the label of the setup takes the lines of the wider label of the introduction so the strip does not jump`() {
        // en at the font 1.5 lying: «INTRODUCTION» 149.5, «SETUP» 64.2, «7 OF 7» 60 in a strip of 209.9 — «SETUP · 5 OF 7» alone fits
        val fit = label(width = 209.9f, intro = 149.5f, setup = 64.2f, count = 60f)
        assertEquals(ProgressLabelFit(12f, oneLine = false), fit, "both on two lines: the strip as high on screens 4 and 5")
        assertEquals(fit, label(width = 209.9f, intro = 64.2f, setup = 149.5f, count = 60f), "whichever part is the wider")
    }

    @Test
    fun `a part word that does not stand whole steps the label down and no further than it needs`() {
        // ru at the font 1.5 lying: «ЗНАКОМСТВО» 137.5 in a strip of 129.7 — 131.8 at 11.5 sp, 126 at 11
        assertEquals(ProgressLabelFit(11f, oneLine = false), label(width = 129.7f, intro = 137.5f, setup = 121.3f, count = 61.7f))
        // de at the font 1.5: «EINRICHTUNG» 134.6 in 115.1 — 10 sp, the least
        assertEquals(ProgressLabelFit(10f, oneLine = false), label(width = 115.1f, intro = 126.7f, setup = 134.6f, count = 75.7f))
        assertEquals(ProgressLabelFit(12f, oneLine = false), label(width = 134.6f, intro = 126.7f, setup = 134.6f, count = 75.7f), "on the edge — whole at 12")
    }

    @Test
    fun `a part word that does not stand whole even at the least size asks for more room`() {
        // de at the font 2.0 beside «Überspringen»: «EINRICHTUNG» 179.5 in 68.9 — under the button, in the whole row of 285.5, it is 12 sp
        assertNull(label(width = 68.9f, intro = 168.9f, setup = 179.5f, count = 101f))
        assertEquals(ProgressLabelFit(12f, oneLine = false), label(width = 285.5f, intro = 168.9f, setup = 179.5f, count = 101f))
    }

    @Test
    fun `lying the title stands 12 under the label and the row is no lower than the button`() {
        // the font 1: the strip of 30 at the bottom of the row of 8 + 48
        val one = OnboardingProgressMath.topRow(beside = true, stripHeight = 30, skipHeight = 48, inset = 8, toTitle = 12)
        assertEquals(TopRowPlaces(stripTop = 14, skipTop = 8, height = 56), one)
        assertEquals(12, one.height - (one.stripTop + 30), "the title 12 under the label")
        // a label on two lines at the font 1.3 (6 + 8 + 2 × 20.8): the row grows, the title still 12 under it
        val two = OnboardingProgressMath.topRow(beside = true, stripHeight = 56, skipHeight = 48, inset = 8, toTitle = 12)
        assertEquals(TopRowPlaces(stripTop = 8, skipTop = 8, height = 76), two)
    }

    @Test
    fun `where the label does not stand beside the button the strip stands under it`() {
        val under = OnboardingProgressMath.topRow(beside = false, stripHeight = 60, skipHeight = 48, inset = 8, toTitle = 12)
        assertEquals(TopRowPlaces(stripTop = 56, skipTop = 8, height = 128), under)
    }

    private fun label(width: Float, intro: Float, setup: Float, count: Float): ProgressLabelFit? = OnboardingProgressMath.labelFit(
        width = width,
        maxSp = LABEL_SP,
        partAt = { sizeSp -> maxOf(intro, setup) * sizeSp / LABEL_SP },
        lineAt = { sizeSp -> (maxOf(intro, setup) + count) * sizeSp / LABEL_SP + LABEL_GAP },
    )

    private companion object {
        /** The label: 12 sp, the least gap between the part and the count 12 (spec 5.29 R8). */
        const val LABEL_SP = 12f
        const val LABEL_GAP = 12f
    }
}
