package com.violinjourney.app.core.concurrent

import java.util.concurrent.locks.ReentrantLock

actual class PlatformLock actual constructor() {
    private val lock = ReentrantLock()

    actual fun lock() = lock.lock()

    actual fun unlock() = lock.unlock()
}
