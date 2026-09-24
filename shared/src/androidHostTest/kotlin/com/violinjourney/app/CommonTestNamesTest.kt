package com.violinjourney.app

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Kotlin/Native refuses test names with , . : ; / \ < > [ ] — the iOS tests would not even compile, while the JVM
 * takes them happily. Caught here, on the JVM, with the rest of the tests.
 */
class CommonTestNamesTest {
    @Test
    fun `names of the common tests are ones Kotlin Native accepts`() {
        val name = Regex("fun `([^`]*)`")
        val illegal = Regex("""[,.:;/\\<>\[\]]""")
        val bad = File("src/commonTest").walkTopDown().filter { it.extension == "kt" }.flatMap { file ->
            name.findAll(file.readText()).map { it.groupValues[1] }.filter { illegal.containsMatchIn(it) }.map { "${file.name}: $it" }
        }.toList()
        assertTrue(bad.isEmpty(), "rename (« — » instead of commas, 0_38 for decimals): $bad")
    }
}
