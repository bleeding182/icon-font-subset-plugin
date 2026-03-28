package com.davidmedenjak.fontsubsetting.benchmark

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Picture
import android.graphics.Typeface
import android.os.Build
import androidx.benchmark.junit4.BenchmarkRule
import androidx.benchmark.junit4.measureRepeated
import androidx.core.content.res.ResourcesCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.davidmedenjak.fontsubsetting.runtime.HarfBuzzGlyphExtractor
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.ConcurrentHashMap

/**
 * Microbenchmark comparing icon rendering approaches:
 *
 * 1. Paint+Canvas — Extract glyph path via Paint.getTextPath(), draw via Canvas.drawPath()
 * 2. HarfBuzz — Extract glyph path via native HarfBuzz JNI, draw via Canvas.drawPath()
 * 3. drawText — Direct Canvas.drawText() (baseline comparison, not used by the library)
 *
 * The library uses approach 1 (demo GlyphPainter) or 2 (runtime GlyphPainter).
 * drawText is included as a baseline — it's faster due to Skia's internal glyph cache,
 * but can't support variable font axes on pre-API-26 or explicit cache control for animations.
 *
 * VectorDrawable benchmarks are in a separate class to avoid AppCompat resource merge issues.
 */
@RunWith(AndroidJUnit4::class)
class IconDrawingBenchmark {

    @get:Rule
    val benchmarkRule = BenchmarkRule()

    private lateinit var bitmap: Bitmap
    private lateinit var canvas: Canvas

    // Font resources
    private lateinit var typeface: Typeface
    private lateinit var fontBytes: ByteArray

    // Pre-extracted paths for cache-hit benchmarks
    private lateinit var paintCanvasPathHome: Path
    private lateinit var harfBuzzPathHome: Path
    private lateinit var harfBuzzPathDoneAll: Path

    // HarfBuzz extractor
    private lateinit var extractor: HarfBuzzGlyphExtractor

    // Paint for path extraction (reference size 100px)
    private lateinit var extractPaint: Paint

    // Paint for drawing cached paths
    private val drawPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK }

    // Paint for drawText
    private lateinit var textPaint: Paint

    // Transforms for drawing cached paths
    private val paintCanvasMatrix = Matrix().apply {
        setScale(SIZE / REFERENCE_SIZE, SIZE / REFERENCE_SIZE)
        postTranslate(SIZE / 2f, 0f)
    }
    private val harfBuzzMatrix = Matrix().apply {
        setTranslate(-0.5f, 0.5f)
        postScale(SIZE, SIZE)
        postTranslate(SIZE / 2f, SIZE / 2f)
    }

    @Before
    fun setup() {
        bitmap = Bitmap.createBitmap(SIZE.toInt(), SIZE.toInt(), Bitmap.Config.ARGB_8888)
        canvas = Canvas(bitmap)

        val context = ApplicationProvider.getApplicationContext<Context>()

        // Load font
        typeface = ResourcesCompat.getFont(context, R.font.material_symbols)!!
        @Suppress("ResourceType")
        fontBytes = context.resources.openRawResource(R.font.material_symbols).use { it.readBytes() }

        // Paint for extraction
        extractPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.typeface = this@IconDrawingBenchmark.typeface
            textSize = REFERENCE_SIZE
            textAlign = Paint.Align.CENTER
        }
        val metrics = Paint.FontMetrics()
        extractPaint.getFontMetrics(metrics)
        val baselineY = (REFERENCE_SIZE - (metrics.ascent + metrics.descent)) / 2f

        // Paint for drawText
        textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.typeface = this@IconDrawingBenchmark.typeface
            textSize = SIZE
            textAlign = Paint.Align.CENTER
            color = Color.BLACK
        }

        // Pre-extract Paint+Canvas path
        paintCanvasPathHome = Path().also {
            extractPaint.getTextPath(HOME_TEXT, 0, HOME_TEXT.length, 0f, baselineY, it)
        }

        // HarfBuzz extractor + pre-extract paths
        extractor = HarfBuzzGlyphExtractor.create(fontBytes)
        harfBuzzPathHome = extractor.extractPath(HOME_CODEPOINT, emptyArray(), FloatArray(0))!!
        harfBuzzPathDoneAll = extractor.extractPath(DONE_ALL_CODEPOINT, emptyArray(), FloatArray(0))!!
    }

    @After
    fun teardown() {
        extractor.close()
        bitmap.recycle()
    }

    // ── Paint+Canvas: Path Extraction (cache miss) ──────────────────────

    @Test
    fun paintCanvas_extractPath_home() {
        val paint = extractPaint
        val metrics = Paint.FontMetrics()
        paint.getFontMetrics(metrics)
        val baselineY = (REFERENCE_SIZE - (metrics.ascent + metrics.descent)) / 2f

        benchmarkRule.measureRepeated {
            val path = Path()
            paint.getTextPath(HOME_TEXT, 0, HOME_TEXT.length, 0f, baselineY, path)
        }
    }

    @Test
    fun paintCanvas_extractPath_doneAll() {
        val paint = extractPaint
        val metrics = Paint.FontMetrics()
        paint.getFontMetrics(metrics)
        val baselineY = (REFERENCE_SIZE - (metrics.ascent + metrics.descent)) / 2f

        benchmarkRule.measureRepeated {
            val path = Path()
            paint.getTextPath(DONE_ALL_TEXT, 0, DONE_ALL_TEXT.length, 0f, baselineY, path)
        }
    }

    // ── Paint+Canvas: Drawing Cached Path (cache hit) ───────────────────

    @Test
    fun paintCanvas_drawCachedPath() {
        benchmarkRule.measureRepeated {
            canvas.save()
            canvas.concat(paintCanvasMatrix)
            canvas.drawPath(paintCanvasPathHome, drawPaint)
            canvas.restore()
        }
    }

    // ── HarfBuzz: Path Extraction (cache miss) ──────────────────────────

    @Test
    fun harfBuzz_extractPath_home() {
        benchmarkRule.measureRepeated {
            extractor.extractPath(HOME_CODEPOINT, emptyArray(), FloatArray(0))
        }
    }

    @Test
    fun harfBuzz_extractPath_doneAll() {
        benchmarkRule.measureRepeated {
            extractor.extractPath(DONE_ALL_CODEPOINT, emptyArray(), FloatArray(0))
        }
    }

    @Test
    fun harfBuzz_extractPathWithVariation() {
        val tags = arrayOf("FILL", "wght", "GRAD", "opsz")
        val values = floatArrayOf(1f, 400f, 0f, 24f)

        benchmarkRule.measureRepeated {
            extractor.extractPath(HOME_CODEPOINT, tags, values)
        }
    }

    // ── HarfBuzz: Batch Extraction (animation pre-computation) ──────────

    @Test
    fun harfBuzz_extractPathBatch_10frames() {
        val tags = arrayOf("FILL", "wght")
        val numFrames = 10
        // 10 frames interpolating FILL 0->1, wght 400->700
        val flatValues = FloatArray(tags.size * numFrames) { i ->
            val frame = i / tags.size
            val axis = i % tags.size
            val t = frame.toFloat() / (numFrames - 1)
            when (axis) {
                0 -> t              // FILL: 0 -> 1
                1 -> 400f + 300f * t // wght: 400 -> 700
                else -> 0f
            }
        }

        benchmarkRule.measureRepeated {
            extractor.extractPathBatch(HOME_CODEPOINT, tags, flatValues, numFrames)
        }
    }

    // ── HarfBuzz: Drawing Cached Path (cache hit) ───────────────────────

    @Test
    fun harfBuzz_drawCachedPath_home() {
        benchmarkRule.measureRepeated {
            canvas.save()
            canvas.concat(harfBuzzMatrix)
            canvas.drawPath(harfBuzzPathHome, drawPaint)
            canvas.restore()
        }
    }

    @Test
    fun harfBuzz_drawCachedPath_doneAll() {
        benchmarkRule.measureRepeated {
            canvas.save()
            canvas.concat(harfBuzzMatrix)
            canvas.drawPath(harfBuzzPathDoneAll, drawPaint)
            canvas.restore()
        }
    }

    // ── HarfBuzz: End-to-end GlyphPainter hot path (cache hit) ──────────

    /** Replicates runtime GlyphPainter.onDraw(): ConcurrentHashMap lookup + matrix + drawPath. */
    @Test
    fun harfBuzz_glyphPainterHotPath() {
        // Pre-populate cache like a real GlyphPainter after first render
        data class Variation(val axes: Array<String>, val values: FloatArray)
        val variation = Variation(emptyArray(), FloatArray(0))
        val pathCache = ConcurrentHashMap<Variation, Path>()
        pathCache[variation] = harfBuzzPathHome

        benchmarkRule.measureRepeated {
            val path = pathCache.getOrPut(variation) {
                extractor.extractPath(HOME_CODEPOINT, variation.axes, variation.values)!!
            }
            canvas.save()
            canvas.concat(harfBuzzMatrix)
            canvas.drawPath(path, drawPaint)
            canvas.restore()
        }
    }

    // ── HarfBuzz: Picture-cached hot path (cache hit) ────────────────────

    /** Measures drawPicture replay — the optimized GlyphPainter approach. */
    @Test
    fun harfBuzz_glyphPainterHotPath_picture() {
        // Pre-record a Picture with the glyph draw commands
        val picture = Picture().also { pic ->
            val c = pic.beginRecording(SIZE.toInt(), SIZE.toInt())
            c.concat(harfBuzzMatrix)
            c.drawPath(harfBuzzPathHome, drawPaint)
            pic.endRecording()
        }

        // Simulate cache-hit: just replay the Picture
        data class PictureCacheKey(val variation: Int, val color: Int, val w: Int, val h: Int)
        val key = PictureCacheKey(0, Color.BLACK, SIZE.toInt(), SIZE.toInt())
        val pictureCache = ConcurrentHashMap<PictureCacheKey, Picture>()
        pictureCache[key] = picture

        benchmarkRule.measureRepeated {
            val cached = pictureCache[key]!!
            canvas.drawPicture(cached)
        }
    }

    // ── drawText (baseline comparison) ──────────────────────────────────

    @Test
    fun drawText_home() {
        val metrics = Paint.FontMetrics()
        textPaint.getFontMetrics(metrics)
        val baselineY = (SIZE - (metrics.ascent + metrics.descent)) / 2f

        benchmarkRule.measureRepeated {
            canvas.drawText(HOME_TEXT, SIZE / 2f, baselineY, textPaint)
        }
    }

    @Test
    fun drawText_doneAll() {
        val metrics = Paint.FontMetrics()
        textPaint.getFontMetrics(metrics)
        val baselineY = (SIZE - (metrics.ascent + metrics.descent)) / 2f

        benchmarkRule.measureRepeated {
            canvas.drawText(DONE_ALL_TEXT, SIZE / 2f, baselineY, textPaint)
        }
    }

    companion object {
        private const val SIZE = 48f
        private const val REFERENCE_SIZE = 100f

        // Codepoints from MaterialSymbolsOutlined.codepoints
        private const val HOME_CODEPOINT = 0xE9B2
        private const val DONE_ALL_CODEPOINT = 0xE877
        private val HOME_TEXT = String(intArrayOf(HOME_CODEPOINT), 0, 1)
        private val DONE_ALL_TEXT = String(intArrayOf(DONE_ALL_CODEPOINT), 0, 1)
    }
}
