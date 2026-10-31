package com.spenditos.android.core.i18n

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

/** `values-fr` must carry every key of `values`, with the same format arguments. */
class StringsParityTest {
    private fun load(path: String): Pair<Map<String, String>, Map<String, Set<String>>> {
        val file = listOf(path, "app/$path").map(::File).first { it.exists() }
        val root = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file).documentElement
        val strings = HashMap<String, String>()
        val plurals = HashMap<String, Set<String>>()
        for (i in 0 until root.childNodes.length) {
            val el = root.childNodes.item(i) as? Element ?: continue
            when (el.tagName) {
                "string" -> strings[el.getAttribute("name")] = el.textContent
                "plurals" -> {
                    val items = el.getElementsByTagName("item")
                    plurals[el.getAttribute("name")] = (0 until items.length).map { (items.item(it) as Element).getAttribute("quantity") }.toSet()
                }
            }
        }
        return strings to plurals
    }

    private fun args(text: String): List<String> = Regex("%(\\d+)\\$[sd]").findAll(text).map { it.groupValues[1] }.toSortedSet().toList()

    @Test
    fun `french has every english string and plural with matching arguments`() {
        val (en, enPlurals) = load("src/main/res/values/strings.xml")
        val (fr, frPlurals) = load("src/main/res/values-fr/strings.xml")
        assertTrue("Add some strings", en.size > 1000)
        assertEquals("Missing in values-fr", emptySet<String>(), en.keys - fr.keys)
        assertEquals("Only in values-fr", emptySet<String>(), fr.keys - en.keys)
        assertEquals("Plurals missing in values-fr", emptySet<String>(), enPlurals.keys - frPlurals.keys)
        for ((key, text) in en) assertEquals("Format arguments differ for $key", args(text), args(fr.getValue(key)))
        for ((key, quantities) in frPlurals) assertTrue("fr plural $key needs one and other", quantities.containsAll(setOf("one", "other")))
    }

    @Test
    fun `french copy has no leftover english placeholders`() {
        val (fr, _) = load("src/main/res/values-fr/strings.xml")
        assertTrue(fr.values.none { it.isBlank() })
    }
}
