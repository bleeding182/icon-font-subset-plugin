package com.davidmedenjak.fontsubsetting.native

import org.assertj.core.api.Assertions.assertThat
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * Tests for font validation and font info retrieval via HarfBuzz native library.
 */
class HarfBuzzSubsetterValidationTest {

    private lateinit var subsetter: HarfBuzzSubsetter
    private lateinit var fontFile: File
    private lateinit var codepointsFile: File

    @Before
    fun setUp() {
        assumeTrue(
            "Native library not available on this platform",
            HarfBuzzSubsetter.isNativeLibraryAvailable()
        )
        subsetter = HarfBuzzSubsetter()

        val fontPath = System.getProperty("test.font.path")
            ?: error("System property 'test.font.path' not set")
        val codepointsPath = System.getProperty("test.codepoints.path")
            ?: error("System property 'test.codepoints.path' not set")

        fontFile = File(fontPath)
        codepointsFile = File(codepointsPath)

        assumeTrue("Font file not found: $fontPath", fontFile.exists())
        assumeTrue("Codepoints file not found: $codepointsPath", codepointsFile.exists())
    }

    // --- validateFont ---

    @Test
    fun `validateFont returns true for valid font`() {
        assertThat(subsetter.validateFont(fontFile.absolutePath)).isTrue()
    }

    @Test
    fun `validateFont returns false for nonexistent file`() {
        assertThat(subsetter.validateFont("/nonexistent/path/font.ttf")).isFalse()
    }

    @Test
    fun `validateFont returns false for empty file`() {
        val emptyFile = File.createTempFile("empty", ".ttf")
        emptyFile.deleteOnExit()
        assertThat(subsetter.validateFont(emptyFile.absolutePath)).isFalse()
    }

    @Test
    fun `validateFont returns false for non-font file`() {
        val textFile = File.createTempFile("notafont", ".ttf")
        textFile.deleteOnExit()
        textFile.writeText("This is not a font file")
        assertThat(subsetter.validateFont(textFile.absolutePath)).isFalse()
    }

    @Test
    fun `validateFont returns true for truncated file with valid header`() {
        val truncated = File.createTempFile("truncated", ".ttf")
        truncated.deleteOnExit()
        // Copy first 100 bytes of the real font - has valid header signature
        fontFile.inputStream().use { input ->
            val bytes = ByteArray(100)
            input.read(bytes)
            truncated.writeBytes(bytes)
        }
        // validateFont only checks the 4-byte header signature, so truncated files
        // with valid headers pass validation
        assertThat(subsetter.validateFont(truncated.absolutePath)).isTrue()
    }

    // --- getFontInfo ---

    @Test
    fun `getFontInfo returns non-null for valid font`() {
        val info = subsetter.getFontInfo(fontFile.absolutePath)
        assertThat(info).isNotNull()
        assertThat(info).isNotEmpty()
    }

    @Test
    fun `getFontInfo returns null for invalid file`() {
        val info = subsetter.getFontInfo("/nonexistent/path/font.ttf")
        assertThat(info).isNull()
    }

    @Test
    fun `getFontInfo contains expected properties`() {
        val info = subsetter.getFontInfo(fontFile.absolutePath)!!
        assertThat(info).contains("glyphCount")
        assertThat(info).contains("unitsPerEm")
        assertThat(info).contains("fileSize")
    }

    // --- getFontInfoDetailed ---

    @Test
    fun `getFontInfoDetailed returns non-null for valid font`() {
        val info = subsetter.getFontInfoDetailed(fontFile.absolutePath)
        assertThat(info).isNotNull()
    }

    @Test
    fun `getFontInfoDetailed returns null for invalid file`() {
        val info = subsetter.getFontInfoDetailed("/nonexistent/path/font.ttf")
        assertThat(info).isNull()
    }

    @Test
    fun `getFontInfoDetailed has correct glyph count`() {
        val info = subsetter.getFontInfoDetailed(fontFile.absolutePath)!!
        // MaterialSymbolsOutlined has 4094 codepoints + .notdef + others
        assertThat(info.glyphCount).isGreaterThan(4000)
    }

    @Test
    fun `getFontInfoDetailed has valid unitsPerEm`() {
        val info = subsetter.getFontInfoDetailed(fontFile.absolutePath)!!
        assertThat(info.unitsPerEm).isGreaterThan(0)
    }

    @Test
    fun `getFontInfoDetailed fileSize matches actual file size`() {
        val info = subsetter.getFontInfoDetailed(fontFile.absolutePath)!!
        assertThat(info.fileSize).isEqualTo(fontFile.length())
    }

    @Test
    fun `getFontInfoDetailed has all 4 axes`() {
        val info = subsetter.getFontInfoDetailed(fontFile.absolutePath)!!
        assertThat(info.axes).isNotNull()
        assertThat(info.axes).hasSize(4)

        val axisTags = info.axes!!.map { it.tag }
        assertThat(axisTags).containsExactlyInAnyOrder("FILL", "wght", "GRAD", "opsz")
    }

    @Test
    fun `getFontInfoDetailed FILL axis has correct range`() {
        val info = subsetter.getFontInfoDetailed(fontFile.absolutePath)!!
        val fill = info.axes!!.first { it.tag == "FILL" }
        assertThat(fill.minValue).isEqualTo(0f)
        assertThat(fill.maxValue).isEqualTo(1f)
        assertThat(fill.defaultValue).isEqualTo(0f)
    }

    @Test
    fun `getFontInfoDetailed wght axis has correct range`() {
        val info = subsetter.getFontInfoDetailed(fontFile.absolutePath)!!
        val wght = info.axes!!.first { it.tag == "wght" }
        assertThat(wght.minValue).isEqualTo(100f)
        assertThat(wght.maxValue).isEqualTo(700f)
        assertThat(wght.defaultValue).isEqualTo(400f)
    }

    @Test
    fun `getFontInfoDetailed GRAD axis has correct range`() {
        val info = subsetter.getFontInfoDetailed(fontFile.absolutePath)!!
        val grad = info.axes!!.first { it.tag == "GRAD" }
        assertThat(grad.minValue).isEqualTo(-50f)
        assertThat(grad.maxValue).isEqualTo(200f)
        assertThat(grad.defaultValue).isEqualTo(0f)
    }

    @Test
    fun `getFontInfoDetailed opsz axis has correct range`() {
        val info = subsetter.getFontInfoDetailed(fontFile.absolutePath)!!
        val opsz = info.axes!!.first { it.tag == "opsz" }
        assertThat(opsz.minValue).isEqualTo(20f)
        assertThat(opsz.maxValue).isEqualTo(48f)
        assertThat(opsz.defaultValue).isEqualTo(24f)
    }
}
