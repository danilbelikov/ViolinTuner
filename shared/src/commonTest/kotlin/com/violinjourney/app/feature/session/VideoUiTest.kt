package com.violinjourney.app.feature.session

import com.violinjourney.app.feature.share.ShareInfo
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The size of a video under «Удалить…» of the recording's «⋯» is a warning — bold in the colour of danger — from 100 MB on (spec 3.19,
 * 3.36.5, 5.29 R5): the one border of «Поделиться» too ([ShareInfo.LARGE_BYTES]).
 */
class VideoUiTest {
    @Test
    fun `a video of 100 MB and more is large and one a byte smaller is not`() {
        assertTrue(VideoUi(sizeBytes = ShareInfo.LARGE_BYTES).large)
        assertTrue(VideoUi(sizeBytes = 612L * 1024 * 1024).large)
        assertFalse(VideoUi(sizeBytes = ShareInfo.LARGE_BYTES - 1).large)
    }

    @Test
    fun `a video that is gone is not large whatever it weighed`() {
        assertFalse(VideoUi(lost = true, sizeBytes = 612L * 1024 * 1024).large)
    }
}
