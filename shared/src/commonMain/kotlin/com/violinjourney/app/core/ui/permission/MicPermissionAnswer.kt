package com.violinjourney.app.core.ui.permission

/** What the system answered, once, to a request that was actually made (spec 3.34). */
enum class MicPermissionAnswer(val key: String) {
    GRANTED("granted"),
    DENIED("denied"),

    /** «Denied for good»: the system will not show its dialog again, and only its settings can help. */
    BLOCKED("blocked"),
}
