package com.violinjourney.app.core.analytics

import kotlin.test.Test
import kotlin.test.assertEquals

/** The consent of the statistics held by the app itself on iOS (spec 3.34, rule 2), and the names of files kept (rule 1). */
class IosAppMetricaAnalyticsTest {
    private val service = RecordingService()
    private val statistics = IosAppMetricaAnalytics(service)

    @Test
    fun `nothing reaches the library before the consent is read and while it is off`() {
        statistics.track(ScreenOpen("live"))
        statistics.error(ErrorGroup.BACKUP, "the copy failed", IllegalStateException("boom"))
        assertEquals(emptyList(), service.told, "the consent has not been read yet")
        statistics.consentChanged(true)
        assertEquals(listOf("sending true", "event screen_open", "error backup"), service.told)

        service.told.clear()
        statistics.consentChanged(false)
        statistics.track(ScreenOpen("live"))
        statistics.error(ErrorGroup.MEDIA, "a codec gave up", null)
        assertEquals(listOf("sending false"), service.told)
    }

    @Test
    fun `an error goes with its class and without the names of files`() {
        statistics.consentChanged(true)
        statistics.error(ErrorGroup.BACKUP, "the copy failed", IllegalStateException("/var/mobile/Containers/Data/Application/1A/Library/Application Support/sessions/a.m4a: gone"))
        assertEquals("IllegalStateException: <path>: gone", service.causes.single())
    }

    private class RecordingService : AnalyticsService {
        val told = mutableListOf<String>()
        val causes = mutableListOf<String?>()

        override fun activate(apiKey: String, logs: Boolean) = Unit

        override fun setDataSendingEnabled(enabled: Boolean) {
            told += "sending $enabled"
        }

        override fun reportEvent(name: String, params: Map<String, Any>) {
            told += "event $name"
        }

        override fun reportError(group: String, message: String, cause: String?) {
            told += "error $group"
            causes += cause
        }

        override fun reportUnhandledException(type: String, message: String?, frames: List<KotlinCrashFrame>, environment: Map<String, String>) {
            told += "crash $type"
        }
    }
}
