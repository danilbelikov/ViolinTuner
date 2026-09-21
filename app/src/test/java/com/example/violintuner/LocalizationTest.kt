package com.example.violintuner

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

/**
 * Every language of the interface says the same things (spec 3.26): the same keys, the same
 * placeholders in each of them, arrays of the same length — and the languages offered to the
 * system are exactly those that have words. The Russian files are the source.
 */
class LocalizationTest {
    private val res = File("src/main/res")
    private val files = listOf("strings.xml", "strings_home.xml", "home_catalog.xml")
    private val placeholder = Regex("%(\\d+\\$)?[sdf]|%%")
    private val cyrillic = Regex("[А-Яа-яЁё]")

    private class Texts(val strings: Map<String, String>, val arrays: Map<String, List<String>>)

    private fun read(dir: File): Texts {
        val strings = LinkedHashMap<String, String>()
        val arrays = LinkedHashMap<String, List<String>>()
        for (name in files) {
            val file = File(dir, name)
            assertTrue("${dir.name} has no $name", file.exists())
            val root = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file).documentElement
            val nodes = root.childNodes
            for (i in 0 until nodes.length) {
                val node = nodes.item(i) as? Element ?: continue
                when (node.tagName) {
                    "string" -> strings[node.getAttribute("name")] = node.textContent
                    "string-array" -> {
                        val items = node.getElementsByTagName("item")
                        arrays[node.getAttribute("name")] = (0 until items.length).map { items.item(it).textContent }
                    }
                }
            }
        }
        return Texts(strings, arrays)
    }

    private fun languages(): Map<String, File> = res.listFiles { f -> f.isDirectory && (f.name == "values" || f.name.startsWith("values-")) && File(f, "strings.xml").exists() }!!
        .associateBy { if (it.name == "values") "en" else it.name.removePrefix("values-") }

    @Test
    fun `every language has the same keys, placeholders and arrays as the Russian source`() {
        val source = read(File(res, "values-ru"))
        val problems = ArrayList<String>()
        for ((tag, dir) in languages()) {
            if (tag == "ru") continue
            val texts = read(dir)
            (source.strings.keys - texts.strings.keys).forEach { problems += "$tag: no string «$it»" }
            (texts.strings.keys - source.strings.keys).forEach { problems += "$tag: a string the source does not have «$it»" }
            (source.arrays.keys - texts.arrays.keys).forEach { problems += "$tag: no array «$it»" }
            for ((key, text) in texts.strings) {
                val expected = source.strings[key] ?: continue
                val want = placeholder.findAll(expected).map { it.value }.sorted().toList()
                val have = placeholder.findAll(text).map { it.value }.sorted().toList()
                if (want != have) problems += "$tag: «$key» has placeholders $have, the source has $want"
                if (cyrillic.containsMatchIn(text)) problems += "$tag: «$key» is still in Russian"
                if (text.isBlank() && expected.isNotBlank()) problems += "$tag: «$key» is empty"
            }
            for ((key, items) in texts.arrays) {
                val expected = source.arrays[key] ?: continue
                if (items.size != expected.size) problems += "$tag: array «$key» has ${items.size} items, the source has ${expected.size}"
                items.forEachIndexed { i, item -> if (cyrillic.containsMatchIn(item)) problems += "$tag: «$key»[$i] is still in Russian" }
            }
        }
        assertTrue(problems.take(60).joinToString("\n", prefix = "${problems.size} problems:\n"), problems.isEmpty())
    }

    @Test
    fun `the languages offered to the system are those that have words, and the formats speak each of them`() {
        val config = File(res, "xml/locales_config.xml").readText()
        val offered = Regex("android:name=\"([a-zA-Z-]+)\"").findAll(config).map { it.groupValues[1] }.toSet()
        assertEquals(languages().keys, offered)
        assertEquals(offered, com.example.violintuner.core.ui.format.FormatLanguage.ALL.map { it.tag }.toSet())
    }
}
