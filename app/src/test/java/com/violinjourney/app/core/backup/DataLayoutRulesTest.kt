package com.violinjourney.app.core.backup

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

/**
 * The names of [DataLayout] where the code cannot reach them: the rules of the system's own backup, and the folders the
 * storages of both platforms open. A folder renamed on one side only, or a new one opened with a name of its own, would
 * leave data out of a copy or put gigabytes into the system backup (spec 5.14).
 */
class DataLayoutRulesTest {
    private val rules = File("src/main/res/xml")

    /** What stays on this phone alone: a restore unpacked here and its marks (see the comments of the rules). */
    private val ownMarks = setOf("${RestoreSwap.STAGING}/", RestoreSwap.READY_MARK, RestoreSwap.WIPE_MARK)

    /** The photo of the profile is small and goes with the database; every other folder of media is for the copy. */
    private val heavy = (DataLayout.MEDIA_DIRS - DataLayout.PROFILE).map { "$it/" }.toSet()

    private fun excludedFiles(file: String, section: String? = null): Set<String> {
        val root = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File(rules, file)).documentElement
        val scope = if (section == null) root else root.getElementsByTagName(section).item(0) as Element
        val excludes = scope.getElementsByTagName("exclude")
        return (0 until excludes.length).map { excludes.item(it) as Element }
            .onEach { assertEquals("$file: only files are left out", "file", it.getAttribute("domain")) }
            .map { it.getAttribute("path") }
            .toSet()
    }

    @Test
    fun `the backup of the system leaves out the media of the copy, the waveforms and the marks of a restore`() {
        val expected = heavy + "${DataLayout.WAVEFORMS}/" + ownMarks
        assertEquals(expected, excludedFiles("backup_rules.xml"))
        assertEquals(expected, excludedFiles("data_extraction_rules.xml", "cloud-backup"))
    }

    @Test
    fun `a transfer to a new phone takes the media and leaves only what is reckoned or this phone's own`() {
        assertEquals(setOf("${DataLayout.WAVEFORMS}/") + ownMarks, excludedFiles("data_extraction_rules.xml", "device-transfer"))
    }

    @Test
    fun `every folder of data the storages open is named in DataLayout`() {
        val sources = listOf(File("src/main/java"), File("../shared/src/commonMain/kotlin"), File("../shared/src/androidMain/kotlin"), File("../shared/src/iosMain/kotlin"))
        val opened = Regex("""File\(context\.filesDir,\s*([^)]+)\)|IosFolders\.(?:folder|deviceOnlyFolder)\(([^)]+)\)""")
        val named = Regex("""DataLayout\.[A-Z_]+""")
        val sites = sources.flatMap { root -> root.walkTopDown().filter { it.extension == "kt" }.toList() }
            .flatMap { file -> opened.findAll(file.readText()).map { file.name to (it.groupValues[1].ifEmpty { it.groupValues[2] }).trim() } }
        // more than a dozen places open a folder today: a scan that finds few has lost its way, not proven anything
        assertTrue("found ${sites.size} folders", sites.size >= 12)
        val strays = sites.filterNot { (_, name) -> named.matches(name) }
        assertTrue("folders of data named outside DataLayout: $strays", strays.isEmpty())
    }

    /**
     * On an iPhone one backup is both the cloud and the move to a new phone, and it has no limit per app: it takes the data,
     * as a cable transfer does on Android, and leaves out what is reckoned again or only passes through — the folders iOS
     * opens with `deviceOnlyFolder`. A restore's staging folder and its marks are marked by `IosBackupStore` (`IosStorageTest`).
     */
    @Test
    fun `the backup of an iPhone leaves out the waveforms and the shots on their way in, and nothing of the data`() {
        val sources = File("../shared/src/iosMain/kotlin").walkTopDown().filter { it.extension == "kt" }.map { it.readText() }.toList()
        fun opened(how: String) = sources.flatMap { text -> Regex("""IosFolders\.$how\(DataLayout\.([A-Z_]+)\)""").findAll(text).map { it.groupValues[1] } }.toSet()
        val helpers = setOf("WAVEFORMS", "CAMERA")
        assertEquals(helpers, opened("deviceOnlyFolder"))
        val backedUp = opened("folder")
        assertTrue("found ${backedUp.size} folders of data", backedUp.isNotEmpty())
        assertTrue("helpers opened into the backup: ${backedUp intersect helpers}", (backedUp intersect helpers).isEmpty())
    }
}
