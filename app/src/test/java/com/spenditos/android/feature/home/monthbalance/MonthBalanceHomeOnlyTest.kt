package com.spenditos.android.feature.home.monthbalance

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The card and the carried-in line live on Home only (owner decision, 2026-10-25): nothing about the month
 * balance appears on the Plan tab (Income & bills, Month plan). Mirrors iOS `testTheCardIsNotOnThePlanTab`.
 */
class MonthBalanceHomeOnlyTest {
    private val root = File("src/main/java/com/spenditos/android")

    private fun sources(dir: String): List<File> =
        File(root, dir).walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()

    @Test
    fun `the plan screens never mention the month balance`() {
        val files = sources("feature/plan") + sources("feature/planning") + sources("feature/recurring")
        assertTrue("expected the plan sources to be found from ${File(".").absolutePath}", files.isNotEmpty())
        for (file in files) {
            val text = file.readText()
            assertFalse("${file.name} must not mention the month-start card", text.contains("MonthBalance"))
            assertFalse("${file.name} must not show a carried-in line", text.contains("carriedIn"))
        }
    }

    @Test
    fun `only Home uses the card`() {
        val users = root.walkTopDown().filter { it.isFile && it.extension == "kt" && Regex("""MonthBalanceCard\(\s*card\s*=""").containsMatchIn(it.readText()) }
            .map { it.name }.toSet()
        assertTrue(users.toString(), users == setOf("HomeScreen.kt"))
    }
}
