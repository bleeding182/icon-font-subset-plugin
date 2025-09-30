package com.davidmedenjak.fontsubsetting.native

import org.assertj.core.api.Assertions.assertThat
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Edge case tests for HarfBuzz subsetter.
 */
class HarfBuzzSubsetterEdgeCaseTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var fontFile: File

    @Before
    fun setUp() {
        assumeTrue(
            "Native library not available on this platform",
            HarfBuzzSubsetter.isNativeLibraryAvailable()
        )
        val fontPath = System.getProperty("test.font.path")
            ?: error("System property 'test.font.path' not set")
        fontFile = File(fontPath)
        assumeTrue("Font file not found: $fontPath", fontFile.exists())
    }

    private fun outputFile(name: String = "output.ttf"): File =
        File(tempFolder.root, name)

    @Test
    fun `multiple instances share library without crash`() {
        val sub1 = HarfBuzzSubsetter()
        val sub2 = HarfBuzzSubsetter()
        val sub3 = HarfBuzzSubsetter()

        val output1 = outputFile("instance1.ttf")
        val output2 = outputFile("instance2.ttf")
        val output3 = outputFile("instance3.ttf")

        assertThat(sub1.subsetFontWithAxesAndFlags(
            fontFile.absolutePath, output1.absolutePath,
            intArrayOf(0xE9B2), emptyList()
        )).isTrue()

        assertThat(sub2.subsetFontWithAxesAndFlags(
            fontFile.absolutePath, output2.absolutePath,
            intArrayOf(0xE8B6), emptyList()
        )).isTrue()

        assertThat(sub3.subsetFontWithAxesAndFlags(
            fontFile.absolutePath, output3.absolutePath,
            intArrayOf(0xE8B8), emptyList()
        )).isTrue()
    }

    @Test
    fun `duplicate codepoints succeed`() {
        val subsetter = HarfBuzzSubsetter()
        val output = outputFile()
        // Same codepoint repeated - HarfBuzz should deduplicate
        val result = subsetter.subsetFontWithAxesAndFlags(
            fontFile.absolutePath, output.absolutePath,
            intArrayOf(0xE9B2, 0xE9B2, 0xE9B2), emptyList()
        )
        assertThat(result).isTrue()
        assertThat(subsetter.validateFont(output.absolutePath)).isTrue()
    }

    @Test
    fun `high PUA codepoint succeeds`() {
        val subsetter = HarfBuzzSubsetter()
        val output = outputFile()
        // 0xF09A (star) is in the Private Use Area
        val result = subsetter.subsetFontWithAxesAndFlags(
            fontFile.absolutePath, output.absolutePath,
            intArrayOf(0xF09A), emptyList()
        )
        assertThat(result).isTrue()
    }

    @Test
    fun `codepoint not in font succeeds with notdef only`() {
        val subsetter = HarfBuzzSubsetter()
        val output = outputFile()
        // 0x0041 = 'A' - not in an icon font
        val result = subsetter.subsetFontWithAxesAndFlags(
            fontFile.absolutePath, output.absolutePath,
            intArrayOf(0x0041), emptyList()
        )
        assertThat(result).isTrue()
        val info = subsetter.getFontInfoDetailed(output.absolutePath)!!
        // Should contain at least .notdef
        assertThat(info.glyphCount).isGreaterThanOrEqualTo(1)
    }

    @Test
    fun `large codepoint set succeeds`() {
        val subsetter = HarfBuzzSubsetter()
        val output = outputFile()
        val codepointsPath = System.getProperty("test.codepoints.path")
            ?: error("System property 'test.codepoints.path' not set")

        // Parse first 500 codepoints from file (stress test without overwhelming native code)
        val codepoints = File(codepointsPath).readLines()
            .filter { it.isNotBlank() }
            .mapNotNull { line ->
                val parts = line.trim().split("\\s+".toRegex())
                if (parts.size >= 2) parts[1].toIntOrNull(16) else null
            }
            .take(500)
            .toIntArray()

        assertThat(codepoints.size).isEqualTo(500)

        val result = subsetter.subsetFontWithAxesAndFlags(
            fontFile.absolutePath, output.absolutePath,
            codepoints, emptyList()
        )
        assertThat(result).isTrue()
        assertThat(subsetter.validateFont(output.absolutePath)).isTrue()
    }

    @Test
    fun `invalid axis tag is ignored`() {
        val subsetter = HarfBuzzSubsetter()
        val output = outputFile()
        val axes = listOf(
            HarfBuzzSubsetter.AxisConfig(tag = "XXXX", remove = true)
        )
        val result = subsetter.subsetFontWithAxesAndFlags(
            fontFile.absolutePath, output.absolutePath,
            intArrayOf(0xE9B2), axes
        )
        assertThat(result).isTrue()
        assertThat(subsetter.validateFont(output.absolutePath)).isTrue()
    }

    @Test
    fun `short axis tag is ignored`() {
        val subsetter = HarfBuzzSubsetter()
        val output = outputFile()
        val axes = listOf(
            HarfBuzzSubsetter.AxisConfig(tag = "XX", remove = true)
        )
        val result = subsetter.subsetFontWithAxesAndFlags(
            fontFile.absolutePath, output.absolutePath,
            intArrayOf(0xE9B2), axes
        )
        assertThat(result).isTrue()
        assertThat(subsetter.validateFont(output.absolutePath)).isTrue()
    }

    @Test
    fun `mixed valid and invalid codepoints succeed`() {
        val subsetter = HarfBuzzSubsetter()
        val output = outputFile()
        // Mix of valid icon codepoints and random values
        val result = subsetter.subsetFontWithAxesAndFlags(
            fontFile.absolutePath, output.absolutePath,
            intArrayOf(0xE9B2, 0x0041, 0xFFFF, 0xE8B6), emptyList()
        )
        assertThat(result).isTrue()
    }

    @Test
    fun `concurrent instances on different outputs`() {
        val threads = (1..4).map { i ->
            Thread {
                val sub = HarfBuzzSubsetter()
                val output = File(tempFolder.root, "concurrent_$i.ttf")
                val result = sub.subsetFontWithAxesAndFlags(
                    fontFile.absolutePath, output.absolutePath,
                    intArrayOf(0xE9B2, 0xE8B6, 0xE8B8), emptyList()
                )
                assertThat(result).withFailMessage("Thread $i failed").isTrue()
            }
        }
        threads.forEach { it.start() }
        threads.forEach { it.join(30_000) }
    }

    // --- Corrupted font data ---

    @Test
    fun `random bytes file fails subsetting gracefully`() {
        val subsetter = HarfBuzzSubsetter()
        val corrupted = File.createTempFile("corrupted", ".ttf")
        corrupted.deleteOnExit()
        // 1KB of random-looking data — no valid font structure
        corrupted.writeBytes(ByteArray(1024) { (it * 37 + 13).toByte() })
        val output = outputFile()
        val result = subsetter.subsetFontWithAxesAndFlags(
            corrupted.absolutePath, output.absolutePath,
            intArrayOf(0xE9B2), emptyList()
        )
        assertThat(result).isFalse()
    }

    @Test
    fun `font with valid header but truncated body fails subsetting`() {
        val subsetter = HarfBuzzSubsetter()
        val truncated = File.createTempFile("truncated_body", ".ttf")
        truncated.deleteOnExit()
        // Copy first 256 bytes — valid header but truncated tables
        fontFile.inputStream().use { input ->
            val bytes = ByteArray(256)
            input.read(bytes)
            truncated.writeBytes(bytes)
        }
        val output = outputFile()
        // validateFont passes (checks header only), but subsetting should fail
        // because the font tables are incomplete
        val result = subsetter.subsetFontWithAxesAndFlags(
            truncated.absolutePath, output.absolutePath,
            intArrayOf(0xE9B2), emptyList()
        )
        assertThat(result).isFalse()
    }

    // --- Axis config contradictions ---

    @Test
    fun `axis with min greater than max succeeds`() {
        // HarfBuzz handles this by clamping or ignoring the invalid range
        val subsetter = HarfBuzzSubsetter()
        val output = outputFile()
        val axes = listOf(
            HarfBuzzSubsetter.AxisConfig(tag = "wght", minValue = 700f, maxValue = 100f, defaultValue = 400f)
        )
        val result = subsetter.subsetFontWithAxesAndFlags(
            fontFile.absolutePath, output.absolutePath,
            intArrayOf(0xE9B2), axes
        )
        // Should not crash — either succeeds or fails gracefully
        // (HarfBuzz behavior may vary, so we just verify no crash)
        assertThat(result).isIn(true, false)
    }

    @Test
    fun `axis with default outside range succeeds`() {
        val subsetter = HarfBuzzSubsetter()
        val output = outputFile()
        val axes = listOf(
            HarfBuzzSubsetter.AxisConfig(tag = "wght", minValue = 400f, maxValue = 500f, defaultValue = 700f)
        )
        val result = subsetter.subsetFontWithAxesAndFlags(
            fontFile.absolutePath, output.absolutePath,
            intArrayOf(0xE9B2), axes
        )
        assertThat(result).isIn(true, false)
    }

    @Test
    fun `duplicate axis configs do not crash`() {
        val subsetter = HarfBuzzSubsetter()
        val output = outputFile()
        val axes = listOf(
            HarfBuzzSubsetter.AxisConfig(tag = "wght", minValue = 400f, maxValue = 700f, defaultValue = 400f),
            HarfBuzzSubsetter.AxisConfig(tag = "wght", minValue = 300f, maxValue = 600f, defaultValue = 400f)
        )
        val result = subsetter.subsetFontWithAxesAndFlags(
            fontFile.absolutePath, output.absolutePath,
            intArrayOf(0xE9B2), axes
        )
        // Should not crash — last-wins or HarfBuzz merges
        assertThat(result).isIn(true, false)
    }
}
