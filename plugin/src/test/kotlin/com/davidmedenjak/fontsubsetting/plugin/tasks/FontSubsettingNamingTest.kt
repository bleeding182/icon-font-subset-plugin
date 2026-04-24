package com.davidmedenjak.fontsubsetting.plugin.tasks

import com.davidmedenjak.fontsubsetting.plugin.services.KotlinNamingService
import org.assertj.core.api.Assertions.assertThat
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

class FontSubsettingNamingTest {

    private fun loadCodepointEntries(): List<Pair<String, String>> {
        val path = System.getProperty("test.codepoints.path")
            ?: "../demo/symbolfonts/MaterialSymbolsOutlined.codepoints"
        val file = File(path)
        assumeTrue("Codepoints fixture not available at $path", file.exists())

        return file.readLines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .mapNotNull { line ->
                val parts = line.split(' ', '\t', limit = 2)
                if (parts.size == 2) parts[0] to parts[1] else null
            }
    }

    @Test
    fun `letter-to-digit boundary names survive the name round-trip`() {
        val entries = loadCodepointEntries()

        val counter0 = entries.firstOrNull { it.first == "counter_0" }
        assumeTrue("counter_0 not present in fixture", counter0 != null)

        assertThat(KotlinNamingService.toPropertyName("counter_0")).isEqualTo("counter0")
    }

    @Test
    fun `every codepoint name produces a unique property name`() {
        val entries = loadCodepointEntries()
        assumeTrue("No codepoint entries loaded", entries.isNotEmpty())

        val collisions = entries
            .groupBy({ KotlinNamingService.toPropertyName(it.first) }, { it.first })
            .filterValues { it.size > 1 }

        assertThat(collisions)
            .withFailMessage { "Property-name collisions (same Kotlin constant produced for multiple icons): $collisions" }
            .isEmpty()
    }
}
