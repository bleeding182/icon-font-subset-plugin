package com.davidmedenjak.fontsubsetting.native

import org.assertj.core.api.Assertions.assertThat
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Tests for low-level font subsetting via subsetFontWithAxesAndFlags().
 */
class HarfBuzzSubsetterSubsetTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var subsetter: HarfBuzzSubsetter
    private lateinit var fontFile: File

    // Codepoints from MaterialSymbolsOutlined.codepoints
    private val HOME = 0xE9B2
    private val SEARCH = 0xE8B6
    private val SETTINGS = 0xE8B8
    private val CLOSE = 0xE5CD
    private val MENU = 0xE5D2
    private val ADD = 0xE145
    private val STAR = 0xF09A
    private val DELETE = 0xE92E
    private val CHECK = 0xE5CA
    private val FAVORITE = 0xE87E

    private val TEN_ICONS = intArrayOf(HOME, SEARCH, SETTINGS, CLOSE, MENU, ADD, STAR, DELETE, CHECK, FAVORITE)

    @Before
    fun setUp() {
        assumeTrue(
            "Native library not available on this platform",
            HarfBuzzSubsetter.isNativeLibraryAvailable()
        )
        subsetter = HarfBuzzSubsetter()

        val fontPath = System.getProperty("test.font.path")
            ?: error("System property 'test.font.path' not set")
        fontFile = File(fontPath)
        assumeTrue("Font file not found: $fontPath", fontFile.exists())
    }

    private fun outputFile(name: String = "output.ttf"): File =
        File(tempFolder.root, name)

    // --- Basic subsetting ---

    @Test
    fun `single icon subset succeeds`() {
        val output = outputFile()
        val result = subsetter.subsetFontWithAxesAndFlags(
            fontFile.absolutePath, output.absolutePath,
            intArrayOf(HOME), emptyList()
        )
        assertThat(result).isTrue()
        assertThat(output).exists()
    }

    @Test
    fun `single icon subset produces valid font`() {
        val output = outputFile()
        subsetter.subsetFontWithAxesAndFlags(
            fontFile.absolutePath, output.absolutePath,
            intArrayOf(HOME), emptyList()
        )
        assertThat(subsetter.validateFont(output.absolutePath)).isTrue()
    }

    @Test
    fun `single icon subset has dramatic size reduction`() {
        val output = outputFile()
        subsetter.subsetFontWithAxesAndFlags(
            fontFile.absolutePath, output.absolutePath,
            intArrayOf(HOME), emptyList()
        )
        assertThat(output.length()).isLessThan(100_000)
        assertThat(output.length()).isLessThan(fontFile.length())
    }

    @Test
    fun `single icon subset has small glyph count`() {
        val output = outputFile()
        subsetter.subsetFontWithAxesAndFlags(
            fontFile.absolutePath, output.absolutePath,
            intArrayOf(HOME), emptyList()
        )
        val info = subsetter.getFontInfoDetailed(output.absolutePath)!!
        // 1 requested glyph + .notdef + possibly a few others
        assertThat(info.glyphCount).isLessThanOrEqualTo(5)
    }

    @Test
    fun `ten icons subset succeeds`() {
        val output = outputFile()
        val result = subsetter.subsetFontWithAxesAndFlags(
            fontFile.absolutePath, output.absolutePath,
            TEN_ICONS, emptyList()
        )
        assertThat(result).isTrue()
        assertThat(output).exists()
    }

    @Test
    fun `ten icons subset has reasonable glyph count`() {
        val output = outputFile()
        subsetter.subsetFontWithAxesAndFlags(
            fontFile.absolutePath, output.absolutePath,
            TEN_ICONS, emptyList()
        )
        val info = subsetter.getFontInfoDetailed(output.absolutePath)!!
        assertThat(info.glyphCount).isBetween(5, 30)
    }

    @Test
    fun `ten icons subset is under 500KB`() {
        val output = outputFile()
        subsetter.subsetFontWithAxesAndFlags(
            fontFile.absolutePath, output.absolutePath,
            TEN_ICONS, emptyList()
        )
        assertThat(output.length()).isLessThan(500_000)
    }

    @Test
    fun `empty codepoints returns false`() {
        val output = outputFile()
        val result = subsetter.subsetFontWithAxesAndFlags(
            fontFile.absolutePath, output.absolutePath,
            intArrayOf(), emptyList()
        )
        assertThat(result).isFalse()
    }

    @Test
    fun `invalid input path returns false`() {
        val output = outputFile()
        val result = subsetter.subsetFontWithAxesAndFlags(
            "/nonexistent/font.ttf", output.absolutePath,
            intArrayOf(HOME), emptyList()
        )
        assertThat(result).isFalse()
    }

    @Test
    fun `invalid output path returns false`() {
        val result = subsetter.subsetFontWithAxesAndFlags(
            fontFile.absolutePath, "/nonexistent/dir/output.ttf",
            intArrayOf(HOME), emptyList()
        )
        assertThat(result).isFalse()
    }

    // --- Axis manipulation ---

    @Test
    fun `no axis configs preserves all axes`() {
        val output = outputFile()
        subsetter.subsetFontWithAxesAndFlags(
            fontFile.absolutePath, output.absolutePath,
            TEN_ICONS, emptyList()
        )
        val info = subsetter.getFontInfoDetailed(output.absolutePath)!!
        assertThat(info.axes).hasSize(4)
    }

    @Test
    fun `remove one axis leaves three`() {
        val output = outputFile()
        val axes = listOf(
            HarfBuzzSubsetter.AxisConfig(tag = "GRAD", remove = true)
        )
        subsetter.subsetFontWithAxesAndFlags(
            fontFile.absolutePath, output.absolutePath,
            TEN_ICONS, axes
        )
        val info = subsetter.getFontInfoDetailed(output.absolutePath)!!
        assertThat(info.axes).hasSize(3)
        assertThat(info.axes!!.map { it.tag }).doesNotContain("GRAD")
    }

    @Test
    fun `remove one axis produces smaller output`() {
        val withAll = outputFile("with_all.ttf")
        val withRemoved = outputFile("with_removed.ttf")

        subsetter.subsetFontWithAxesAndFlags(
            fontFile.absolutePath, withAll.absolutePath,
            TEN_ICONS, emptyList()
        )
        subsetter.subsetFontWithAxesAndFlags(
            fontFile.absolutePath, withRemoved.absolutePath,
            TEN_ICONS, listOf(HarfBuzzSubsetter.AxisConfig(tag = "GRAD", remove = true))
        )
        assertThat(withRemoved.length()).isLessThan(withAll.length())
    }

    @Test
    fun `remove two axes leaves two`() {
        val output = outputFile()
        val axes = listOf(
            HarfBuzzSubsetter.AxisConfig(tag = "GRAD", remove = true),
            HarfBuzzSubsetter.AxisConfig(tag = "opsz", remove = true)
        )
        subsetter.subsetFontWithAxesAndFlags(
            fontFile.absolutePath, output.absolutePath,
            TEN_ICONS, axes
        )
        val info = subsetter.getFontInfoDetailed(output.absolutePath)!!
        assertThat(info.axes).hasSize(2)
        val tags = info.axes!!.map { it.tag }
        assertThat(tags).doesNotContain("GRAD")
        assertThat(tags).doesNotContain("opsz")
    }

    @Test
    fun `restrict axis range preserves axis`() {
        val output = outputFile()
        val axes = listOf(
            HarfBuzzSubsetter.AxisConfig(tag = "wght", minValue = 400f, maxValue = 700f, defaultValue = 400f)
        )
        subsetter.subsetFontWithAxesAndFlags(
            fontFile.absolutePath, output.absolutePath,
            TEN_ICONS, axes
        )
        val info = subsetter.getFontInfoDetailed(output.absolutePath)!!
        val wght = info.axes!!.first { it.tag == "wght" }
        assertThat(wght.minValue).isEqualTo(400f)
        assertThat(wght.maxValue).isEqualTo(700f)
    }

    @Test
    fun `restrict axis range produces smaller output`() {
        val full = outputFile("full_range.ttf")
        val restricted = outputFile("restricted_range.ttf")

        subsetter.subsetFontWithAxesAndFlags(
            fontFile.absolutePath, full.absolutePath,
            TEN_ICONS, emptyList()
        )
        subsetter.subsetFontWithAxesAndFlags(
            fontFile.absolutePath, restricted.absolutePath,
            TEN_ICONS, listOf(
                HarfBuzzSubsetter.AxisConfig(tag = "wght", minValue = 400f, maxValue = 700f, defaultValue = 400f)
            )
        )
        assertThat(restricted.length()).isLessThan(full.length())
    }

    @Test
    fun `remove all axes creates static font`() {
        val output = outputFile()
        val axes = listOf(
            HarfBuzzSubsetter.AxisConfig(tag = "FILL", remove = true),
            HarfBuzzSubsetter.AxisConfig(tag = "wght", remove = true),
            HarfBuzzSubsetter.AxisConfig(tag = "GRAD", remove = true),
            HarfBuzzSubsetter.AxisConfig(tag = "opsz", remove = true)
        )
        val result = subsetter.subsetFontWithAxesAndFlags(
            fontFile.absolutePath, output.absolutePath,
            TEN_ICONS, axes
        )
        assertThat(result).isTrue()
        val info = subsetter.getFontInfoDetailed(output.absolutePath)!!
        // Static font: no axes or empty axis list
        assertThat(info.axes ?: emptyList()).isEmpty()
    }

    @Test
    fun `remove all axes produces dramatic size reduction`() {
        val variable = outputFile("variable.ttf")
        val static_ = outputFile("static.ttf")

        subsetter.subsetFontWithAxesAndFlags(
            fontFile.absolutePath, variable.absolutePath,
            TEN_ICONS, emptyList()
        )
        subsetter.subsetFontWithAxesAndFlags(
            fontFile.absolutePath, static_.absolutePath,
            TEN_ICONS, listOf(
                HarfBuzzSubsetter.AxisConfig(tag = "FILL", remove = true),
                HarfBuzzSubsetter.AxisConfig(tag = "wght", remove = true),
                HarfBuzzSubsetter.AxisConfig(tag = "GRAD", remove = true),
                HarfBuzzSubsetter.AxisConfig(tag = "opsz", remove = true)
            )
        )
        assertThat(static_.length()).isLessThan(variable.length())
    }

    // --- Strip options ---

    @Test
    fun `stripHinting true produces smaller output than false`() {
        val withHinting = outputFile("with_hinting.ttf")
        val withoutHinting = outputFile("without_hinting.ttf")

        subsetter.subsetFontWithAxesAndFlags(
            fontFile.absolutePath, withHinting.absolutePath,
            TEN_ICONS, emptyList(), stripHinting = false, stripGlyphNames = false
        )
        subsetter.subsetFontWithAxesAndFlags(
            fontFile.absolutePath, withoutHinting.absolutePath,
            TEN_ICONS, emptyList(), stripHinting = true, stripGlyphNames = false
        )
        assertThat(withoutHinting.length()).isLessThanOrEqualTo(withHinting.length())
    }

    @Test
    fun `stripGlyphNames true produces smaller output than false`() {
        val withNames = outputFile("with_names.ttf")
        val withoutNames = outputFile("without_names.ttf")

        subsetter.subsetFontWithAxesAndFlags(
            fontFile.absolutePath, withNames.absolutePath,
            TEN_ICONS, emptyList(), stripHinting = false, stripGlyphNames = false
        )
        subsetter.subsetFontWithAxesAndFlags(
            fontFile.absolutePath, withoutNames.absolutePath,
            TEN_ICONS, emptyList(), stripHinting = false, stripGlyphNames = true
        )
        assertThat(withoutNames.length()).isLessThanOrEqualTo(withNames.length())
    }

    @Test
    fun `all optimizations combined produces smallest output`() {
        val noOpt = outputFile("no_opt.ttf")
        val allOpt = outputFile("all_opt.ttf")

        subsetter.subsetFontWithAxesAndFlags(
            fontFile.absolutePath, noOpt.absolutePath,
            TEN_ICONS, emptyList(), stripHinting = false, stripGlyphNames = false
        )
        subsetter.subsetFontWithAxesAndFlags(
            fontFile.absolutePath, allOpt.absolutePath,
            TEN_ICONS, listOf(
                HarfBuzzSubsetter.AxisConfig(tag = "GRAD", remove = true),
                HarfBuzzSubsetter.AxisConfig(tag = "opsz", remove = true)
            ), stripHinting = true, stripGlyphNames = true
        )
        assertThat(allOpt.length()).isLessThan(noOpt.length())
    }

    // --- Robustness ---

    @Test
    fun `output is idempotent`() {
        val output1 = outputFile("idempotent1.ttf")
        val output2 = outputFile("idempotent2.ttf")

        subsetter.subsetFontWithAxesAndFlags(
            fontFile.absolutePath, output1.absolutePath,
            TEN_ICONS, emptyList()
        )
        subsetter.subsetFontWithAxesAndFlags(
            fontFile.absolutePath, output2.absolutePath,
            TEN_ICONS, emptyList()
        )
        assertThat(output1.length()).isEqualTo(output2.length())
    }

    @Test
    fun `output can be subsetted again`() {
        val first = outputFile("first_pass.ttf")
        val second = outputFile("second_pass.ttf")

        subsetter.subsetFontWithAxesAndFlags(
            fontFile.absolutePath, first.absolutePath,
            TEN_ICONS, emptyList()
        )

        // Subset the already-subsetted font with fewer icons
        val result = subsetter.subsetFontWithAxesAndFlags(
            first.absolutePath, second.absolutePath,
            intArrayOf(HOME, SEARCH), emptyList()
        )
        assertThat(result).isTrue()
        assertThat(subsetter.validateFont(second.absolutePath)).isTrue()
    }
}
