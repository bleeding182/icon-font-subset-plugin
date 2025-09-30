package com.davidmedenjak.fontsubsetting.native

import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Integration tests for HarfBuzzSubsetter using the low-level subsetFontWithAxesAndFlags API.
 */
class HarfBuzzSubsetterIntegrationTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var subsetter: HarfBuzzSubsetter
    private lateinit var fontFile: File

    // Codepoints for common Material Symbols icons
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

    private val TEN_ICON_CODEPOINTS = intArrayOf(
        HOME, SEARCH, SETTINGS, CLOSE, MENU,
        ADD, STAR, DELETE, CHECK, FAVORITE
    )

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

    @After
    fun tearDown() {
        // Reset the native logger to prevent singleton state leaking between tests.
        val noOpLogger = object : NativeLogger {
            override fun log(level: Int, message: String) {}
        }
        HarfBuzzSubsetter(noOpLogger)
    }

    private fun outputFile(name: String = "output.ttf"): File =
        File(tempFolder.root, name)

    // --- Basic subsetFontWithAxesAndFlags API ---

    @Test
    fun `subset with single codepoint succeeds`() {
        val output = outputFile()
        val result = subsetter.subsetFontWithAxesAndFlags(
            fontFile.absolutePath, output.absolutePath,
            intArrayOf(HOME), emptyList()
        )
        assertThat(result).isTrue()
    }

    @Test
    fun `subset output file exists and is valid`() {
        val output = outputFile()
        subsetter.subsetFontWithAxesAndFlags(
            fontFile.absolutePath, output.absolutePath,
            intArrayOf(HOME), emptyList()
        )
        assertThat(output).exists()
        assertThat(subsetter.validateFont(output.absolutePath)).isTrue()
    }

    @Test
    fun `subset reduces file size`() {
        val output = outputFile()
        subsetter.subsetFontWithAxesAndFlags(
            fontFile.absolutePath, output.absolutePath,
            TEN_ICON_CODEPOINTS, emptyList()
        )
        assertThat(output.length()).isLessThan(fontFile.length())
    }

    // --- Full workflow ---

    @Test
    fun `full workflow with axis removal and stripping`() {
        val output = outputFile()
        val axes = listOf(
            HarfBuzzSubsetter.AxisConfig(tag = "GRAD", remove = true),
            HarfBuzzSubsetter.AxisConfig(tag = "wght", minValue = 400f, maxValue = 700f, defaultValue = 400f)
        )
        val result = subsetter.subsetFontWithAxesAndFlags(
            fontFile.absolutePath, output.absolutePath,
            TEN_ICON_CODEPOINTS, axes,
            stripHinting = true,
            stripGlyphNames = true
        )
        assertThat(result).isTrue()

        val info = subsetter.getFontInfoDetailed(output.absolutePath)!!
        assertThat(info.axes).isNotNull()
        // GRAD removed, so remaining axes should not contain it
        val remainingTags = info.axes!!.map { it.tag }
        assertThat(remainingTags).doesNotContain("GRAD")

        assertThat(output.length()).isLessThan(200_000)
    }

    @Test
    fun `full workflow with all axes removed`() {
        val output = outputFile()
        val axes = listOf(
            HarfBuzzSubsetter.AxisConfig(tag = "FILL", remove = true),
            HarfBuzzSubsetter.AxisConfig(tag = "wght", remove = true),
            HarfBuzzSubsetter.AxisConfig(tag = "GRAD", remove = true),
            HarfBuzzSubsetter.AxisConfig(tag = "opsz", remove = true)
        )
        val result = subsetter.subsetFontWithAxesAndFlags(
            fontFile.absolutePath, output.absolutePath,
            TEN_ICON_CODEPOINTS, axes,
            stripHinting = true,
            stripGlyphNames = true
        )
        assertThat(result).isTrue()

        val info = subsetter.getFontInfoDetailed(output.absolutePath)!!
        assertThat(info.axes ?: emptyList()).isEmpty()
        assertThat(output.length()).isLessThan(50_000)
    }

    // --- Logger integration ---

    @Test
    fun `logger can be set without crashing`() {
        val messages = mutableListOf<Pair<Int, String>>()
        val logger = object : NativeLogger {
            override fun log(level: Int, message: String) {
                messages.add(level to message)
            }
        }
        val loggedSubsetter = HarfBuzzSubsetter(logger)
        val output = outputFile()
        val result = loggedSubsetter.subsetFontWithAxesAndFlags(
            fontFile.absolutePath, output.absolutePath,
            TEN_ICON_CODEPOINTS, emptyList()
        )
        assertThat(result).isTrue()
    }

    @Test
    fun `multiple subset calls succeed sequentially`() {
        for (i in 1..5) {
            val output = outputFile("sequential_$i.ttf")
            val result = subsetter.subsetFontWithAxesAndFlags(
                fontFile.absolutePath, output.absolutePath,
                intArrayOf(HOME), emptyList()
            )
            assertThat(result).withFailMessage("Iteration $i failed").isTrue()
        }
    }

    // --- parseFontInfo coverage (tested indirectly via getFontInfoDetailed) ---

    @Test
    fun `getFontInfoDetailed parses all fields from subset output`() {
        val output = outputFile()
        subsetter.subsetFontWithAxesAndFlags(
            fontFile.absolutePath, output.absolutePath,
            TEN_ICON_CODEPOINTS, emptyList()
        )

        val info = subsetter.getFontInfoDetailed(output.absolutePath)!!
        assertThat(info.glyphCount).isGreaterThan(0)
        assertThat(info.unitsPerEm).isGreaterThan(0)
        assertThat(info.fileSize).isEqualTo(output.length())
        // Subset of variable font retains axes
        assertThat(info.axes).isNotNull()
        assertThat(info.axes!!).isNotEmpty()
        // Verify each axis has valid structure
        for (axis in info.axes!!) {
            assertThat(axis.tag).hasSize(4)
            assertThat(axis.maxValue).isGreaterThanOrEqualTo(axis.minValue)
        }
    }

    @Test
    fun `getFontInfoDetailed on static subset has no axes`() {
        val output = outputFile()
        subsetter.subsetFontWithAxesAndFlags(
            fontFile.absolutePath, output.absolutePath,
            intArrayOf(HOME),
            listOf(
                HarfBuzzSubsetter.AxisConfig(tag = "FILL", remove = true),
                HarfBuzzSubsetter.AxisConfig(tag = "wght", remove = true),
                HarfBuzzSubsetter.AxisConfig(tag = "GRAD", remove = true),
                HarfBuzzSubsetter.AxisConfig(tag = "opsz", remove = true)
            )
        )

        val info = subsetter.getFontInfoDetailed(output.absolutePath)!!
        assertThat(info.axes ?: emptyList()).isEmpty()
    }
}
