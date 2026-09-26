package com.violinjourney.app

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

/**
 * The configurations of the iOS app keep their promises (CLAUDE.md, «Общий модуль и iOS»). Profile is the owner's .debug
 * app with a release Kotlin framework: the Kotlin plugin takes the type of the framework from the name of the
 * configuration and reads `KOTLIN_FRAMEWORK_BUILD_TYPE` only when it is neither Debug nor Release, so the setting
 * anywhere else is silently ignored. A release framework inside a store id would send statistics from the owner's
 * phone (`IosBuild.isDevApp` goes by the `.debug` tail of the bundle id), and a key of Info.plist forgotten in one
 * configuration ends that build at the first request of the system.
 */
class XcodeProjectTest {
    private val project = File("../iosApp/iosApp.xcodeproj/project.pbxproj").readText()
    private val scheme = File("../iosApp/iosApp.xcodeproj/xcshareddata/xcschemes/iosApp.xcscheme")

    private class Configuration(val id: String, val name: String, val settings: Map<String, String>)

    private val configurations: Map<String, Configuration> =
        Regex("""(\w{24}) /\* [^*]+ \*/ = \{\s*isa = XCBuildConfiguration;\s*buildSettings = \{(.*?)\};\s*name = (\w+);""", RegexOption.DOT_MATCHES_ALL)
            .findAll(project)
            .map { match -> Configuration(match.groupValues[1], match.groupValues[3], settingsOf(match.groupValues[2])) }
            .associateBy { it.id }

    private fun settingsOf(block: String): Map<String, String> =
        Regex("""^\s*([A-Za-z_][A-Za-z0-9_]*) = (.*?);?$""", RegexOption.MULTILINE).findAll(block)
            .associate { it.groupValues[1] to it.groupValues[2].trim('"') }

    /** The configurations of the list of the one object of kind [isa] (the project or the app's target). */
    private fun configurationsOf(isa: String): List<Configuration> {
        val start = Regex("""isa = $isa;""").findAll(project).single().range.last
        val listId = Regex("""buildConfigurationList = (\w{24})""").find(project, start)?.groupValues?.get(1) ?: error("no configuration list of $isa")
        val ids = Regex("""$listId /\* [^*]+ \*/ = \{\s*isa = XCConfigurationList;\s*buildConfigurations = \((.*?)\);""", RegexOption.DOT_MATCHES_ALL)
            .find(project)?.groupValues?.get(1) ?: error("no list $listId")
        return Regex("""(\w{24})""").findAll(ids).map { configurations.getValue(it.value) }.toList()
    }

    private val projectConfigurations get() = configurationsOf("PBXProject")
    private val targetConfigurations get() = configurationsOf("PBXNativeTarget")

    @Test
    fun `the project is read`() {
        assertEquals(setOf("Debug", "Release", "Profile"), targetConfigurations.map { it.name }.toSet())
        assertTrue(targetConfigurations.all { "PRODUCT_BUNDLE_IDENTIFIER" in it.settings })
    }

    @Test
    fun `a release Kotlin framework is asked for only where the plugin reads it - never in Debug or Release`() {
        configurations.values.filter { "KOTLIN_FRAMEWORK_BUILD_TYPE" in it.settings }.forEach {
            assertTrue("${it.name} names its own framework type", it.name != "Debug" && it.name != "Release")
        }
        assertEquals("release", targetConfigurations.single { it.name == "Profile" }.settings["KOTLIN_FRAMEWORK_BUILD_TYPE"])
    }

    @Test
    fun `the owner's team and a chosen framework type go only with the debug bundle id`() {
        configurations.values.filter { "DEVELOPMENT_TEAM" in it.settings || "KOTLIN_FRAMEWORK_BUILD_TYPE" in it.settings }.forEach {
            // IosBuild.DEV_BUNDLE_SUFFIX
            assertTrue("${it.name}: ${it.settings["PRODUCT_BUNDLE_IDENTIFIER"]}", it.settings["PRODUCT_BUNDLE_IDENTIFIER"].orEmpty().endsWith(".debug"))
        }
        val debug = targetConfigurations.single { it.name == "Debug" }.settings
        val profile = targetConfigurations.single { it.name == "Profile" }.settings
        assertEquals("Profile is the same app, with the same data", debug["PRODUCT_BUNDLE_IDENTIFIER"], profile["PRODUCT_BUNDLE_IDENTIFIER"])
        assertEquals(debug["DEVELOPMENT_TEAM"], profile["DEVELOPMENT_TEAM"])
    }

    @Test
    fun `every configuration of the app writes the same keys into Info plist`() {
        val keys = targetConfigurations.associate { configuration -> configuration.name to configuration.settings.keys.filter { it.startsWith("INFOPLIST_KEY_") }.toSet() }
        val debug = keys.getValue("Debug")
        assertTrue(debug.isNotEmpty())
        keys.forEach { (name, set) -> assertEquals("$name against Debug", debug, set) }
    }

    @Test
    fun `Profile is in the project and in the target`() {
        assertTrue(projectConfigurations.any { it.name == "Profile" })
        assertTrue(targetConfigurations.any { it.name == "Profile" })
    }

    @Test
    fun `the shared scheme runs Debug profiles Profile and archives Release`() {
        val root = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(scheme).documentElement
        fun configurationOf(action: String) = (root.getElementsByTagName(action).item(0) as Element).getAttribute("buildConfiguration")
        assertEquals("Debug", configurationOf("LaunchAction"))
        assertEquals("Debug", configurationOf("TestAction"))
        assertEquals("Debug", configurationOf("AnalyzeAction"))
        assertEquals("Profile", configurationOf("ProfileAction"))
        assertEquals("Release", configurationOf("ArchiveAction"))
    }
}
