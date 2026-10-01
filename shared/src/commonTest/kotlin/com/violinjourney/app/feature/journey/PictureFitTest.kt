package com.violinjourney.app.feature.journey

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The pictures of the journey and the home give way first (spec 3.36.7, 5.29 R7): what is under the bar, less the bottom zone and the
 * 120 of the words under the picture, within the limits of the picture. 360 × 640 with gestures: 640 − 24 − 24 − 56 = 536 under the
 * bar; with three buttons 512; the zone of the home — 8 + 56 + 10 + 48 + 10 = 132.
 */
class PictureFitTest {
    @Test
    fun `the room of the home on 360 x 640 is 284 with gestures and 260 with three buttons`() {
        assertEquals(284.dp, PictureFit.height(536.dp, 132.dp, PictureFit.RoomMin, PictureFit.RoomMax))
        assertEquals(260.dp, PictureFit.height(512.dp, 132.dp, PictureFit.RoomMin, PictureFit.RoomMax))
    }

    @Test
    fun `a postcard of the journey stays 240 on 360 x 640`() {
        // the zone of the journey: the card of the path and «В путь» — 8 + 68 + 10 + 56 + 10
        assertEquals(240.dp, PictureFit.height(536.dp, 152.dp, PictureFit.PostcardMin, PictureFit.PostcardMax))
    }

    @Test
    fun `a picture never grows past its largest - the base screen`() {
        assertEquals(290.dp, PictureFit.height(812.dp, 132.dp, PictureFit.RoomMin, PictureFit.RoomMax))
        assertEquals(240.dp, PictureFit.height(812.dp, 152.dp, PictureFit.PostcardMin, PictureFit.PostcardMax))
    }

    @Test
    fun `and never shrinks under its least - the rest scrolls`() {
        // lying 640 × 360 on the emulator: 308 − 48 = 260 under the bar
        assertEquals(180.dp, PictureFit.height(260.dp, 132.dp, PictureFit.PostcardMin, PictureFit.PostcardMax))
        assertEquals(200.dp, PictureFit.height(260.dp, 132.dp, PictureFit.RoomMin, PictureFit.RoomMax))
        assertEquals(180.dp, PictureFit.height(0.dp, 0.dp, PictureFit.PostcardMin, PictureFit.PostcardMax))
    }

    @Test
    fun `the limits are those of the spec`() {
        assertEquals(listOf(180.dp, 240.dp, 200.dp, 290.dp, 120.dp), listOf(PictureFit.PostcardMin, PictureFit.PostcardMax, PictureFit.RoomMin, PictureFit.RoomMax, PictureFit.WordsUnder))
    }

    @Test
    fun `lying the postcard of a moment stands 180 beside its words or what is left over the fade of the zone`() {
        // the arrival on the emulator's 640 × 360, lying 603 × 308: 308 − 16 − the zone of 64 − the fade of 28
        assertEquals(180.dp, PictureFit.lying(200.dp))
        // the intro there, under its bar of 48: 308 − 48 − 4 − 64 − 28
        assertEquals(164.dp, PictureFit.lying(164.dp))
        assertEquals(PictureFit.LyingMin, PictureFit.lying((-40).dp))
        assertEquals(listOf(240.dp, 120.dp), listOf(PictureFit.LyingWidth, PictureFit.LyingMin))
    }

    @Test
    fun `the frame of the stamp is 220 where there is room and gives way to it down to 140`() {
        // upright 360 × 640: 640 − 16 − the zone of 126 − 28, less the words over and under the frame (108)
        assertEquals(220.dp, StampFit.frame(362.dp))
        // lying 892 × 412, some 360 under its bars, beside the words: 360 − 16 − 126 − 28
        assertEquals(190.dp, StampFit.frame(190.dp))
        // the emulator's 640 × 360: 308 − 16 − 116 − 28
        assertEquals(148.dp, StampFit.frame(148.dp))
        assertEquals(140.dp, StampFit.frame((-20).dp))
        assertEquals(listOf(220.dp, 140.dp), listOf(StampFit.FrameMax, StampFit.FrameMin))
        assertEquals(168f / 220f, StampFit.STAMP_SHARE, "the stamp is 168 in the frame of 220")
    }
}
