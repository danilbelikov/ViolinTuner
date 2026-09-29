package com.violinjourney.app.feature.camera

import com.violinjourney.app.feature.camera.CaptureAccess.ASKABLE
import com.violinjourney.app.feature.camera.CaptureAccess.BLOCKED
import com.violinjourney.app.feature.camera.CaptureAccess.GRANTED
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** When «Разрешить доступ» of the own camera opens the settings instead of asking (spec 3.36.4). */
class CapturePermissionRulesTest {
    @Test
    fun `an allowed one clears its refusal whatever was known`() {
        assertFalse(CapturePermissionRules.blocked(wasBlocked = true, GRANTED, answered = false))
        assertFalse(CapturePermissionRules.blocked(wasBlocked = true, GRANTED, answered = true))
    }

    @Test
    fun `one refused for good is refused for good in an answer and in a report`() {
        assertTrue(CapturePermissionRules.blocked(wasBlocked = false, BLOCKED, answered = true))
        // iOS says «denied» on every return to the screen
        assertTrue(CapturePermissionRules.blocked(wasBlocked = false, BLOCKED, answered = false))
    }

    @Test
    fun `an answer refused in a shown dialog may be asked again`() {
        assertFalse(CapturePermissionRules.blocked(wasBlocked = true, ASKABLE, answered = true))
        assertFalse(CapturePermissionRules.blocked(wasBlocked = false, ASKABLE, answered = true))
    }

    @Test
    fun `a report on a return keeps what the answers knew — Android does not tell «for good» before a request`() {
        assertTrue(CapturePermissionRules.blocked(wasBlocked = true, ASKABLE, answered = false))
        assertFalse(CapturePermissionRules.blocked(wasBlocked = false, ASKABLE, answered = false))
    }

    @Test
    fun `the settings open only for one refused for good and not allowed`() {
        assertTrue(CapturePermissionRules.settingsOnly(cameraBlocked = true, camera = false, micBlocked = false, mic = true))
        assertTrue(CapturePermissionRules.settingsOnly(cameraBlocked = false, camera = false, micBlocked = true, mic = false))
        assertFalse(CapturePermissionRules.settingsOnly(cameraBlocked = false, camera = false, micBlocked = false, mic = false))
        // the microphone was refused for good and is allowed now: the camera, refused only in its dialog, is asked again
        assertFalse(CapturePermissionRules.settingsOnly(cameraBlocked = false, camera = false, micBlocked = true, mic = true))
    }

    // the scenario of the review of stage 109: the microphone refused for good on Live, the camera refused once in its dialog;
    // the settings give back the microphone — the camera is asked by the system again, not in the settings
    @Test
    fun `the settings giving back the one refused for good leave the other to its dialog`() {
        var camera = CapturePermissionRules.blocked(wasBlocked = false, ASKABLE, answered = true)
        var mic = CapturePermissionRules.blocked(wasBlocked = false, BLOCKED, answered = true)
        assertTrue(CapturePermissionRules.settingsOnly(camera, false, mic, false), "the microphone is refused for good")

        camera = CapturePermissionRules.blocked(camera, ASKABLE, answered = false)
        mic = CapturePermissionRules.blocked(mic, GRANTED, answered = false)
        assertFalse(CapturePermissionRules.settingsOnly(camera, false, mic, true), "the camera may be asked by its dialog")
    }
}
