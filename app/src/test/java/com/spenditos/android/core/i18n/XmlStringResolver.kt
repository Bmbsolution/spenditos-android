package com.spenditos.android.core.i18n

import java.io.File
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element

/**
 * Unit-test resolver: reads the real `res/values/strings.xml` (and `plurals`) so tests can keep
 * asserting the English copy without Android resources. Loaded through `ServiceLoader`.
 */
class XmlStringResolver : StringResolver {
    private val strings = HashMap<Int, String>()
    private val plurals = HashMap<Int, Map<String, String>>()
    private val pluralsByName = HashMap<String, Map<String, String>>()

    init {
        val file = listOf("src/main/res/values/strings.xml", "app/src/main/res/values/strings.xml").map(::File).first { it.exists() }
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val names = HashMap<String, String>()
        val nodes = doc.documentElement.childNodes
        for (i in 0 until nodes.length) {
            val el = nodes.item(i) as? Element ?: continue
            when (el.tagName) {
                "string" -> names[el.getAttribute("name")] = unescape(el.textContent)
                "plurals" -> {
                    val items = el.getElementsByTagName("item")
                    val map = HashMap<String, String>()
                    for (j in 0 until items.length) {
                        val item = items.item(j) as Element
                        map[item.getAttribute("quantity")] = unescape(item.textContent)
                    }
                    pluralsByName[el.getAttribute("name")] = map
                }
            }
        }
        // Resource ids are resolved by name through the generated R class.
        for (field in com.spenditos.android.R.string::class.java.fields) {
            val text = names[field.name] ?: continue
            strings[field.getInt(null)] = text
        }
        for (field in runCatching { Class.forName("com.spenditos.android.R\$plurals") }.getOrNull()?.fields.orEmpty()) {
            val map = pluralsByName[field.name] ?: continue
            plurals[field.getInt(null)] = map
        }
    }

    override fun string(id: Int, args: Array<out Any?>): String {
        val format = strings[id] ?: error("Missing string resource $id")
        return if (args.isEmpty()) format else String.format(Locale.ENGLISH, format, *args)
    }

    override fun plural(id: Int, quantity: Int, args: Array<out Any?>): String {
        val map = plurals[id] ?: error("Missing plurals resource $id")
        val format = if (quantity == 1) map["one"] ?: map.getValue("other") else map["other"] ?: map.getValue("one")
        return String.format(Locale.ENGLISH, format, *args)
    }

    private fun unescape(raw: String): String {
        val s = raw.trim().let { if (it.length >= 2 && it.startsWith('"') && it.endsWith('"')) it.substring(1, it.length - 1) else it }
        val sb = StringBuilder()
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '\\' && i + 1 < s.length) {
                when (val n = s[i + 1]) {
                    'n' -> sb.append('\n')
                    't' -> sb.append('\t')
                    'u' -> { sb.append(s.substring(i + 2, i + 6).toInt(16).toChar()); i += 4 }
                    else -> sb.append(n)
                }
                i += 2
            } else { sb.append(c); i++ }
        }
        return sb.toString()
    }
}
