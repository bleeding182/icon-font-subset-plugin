package com.davidmedenjak.fontsubsetting.benchmark

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import androidx.core.content.ContextCompat
import androidx.benchmark.junit4.BenchmarkRule
import androidx.benchmark.junit4.measureRepeated
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Microbenchmark for VectorDrawable rendering — the traditional approach that
 * font subsetting replaces.
 *
 * Separated from [IconDrawingBenchmark] for clarity — these measure the baseline
 * that font subsetting replaces.
 *
 * Key finding: inflation is ~100-150x slower than drawing a pre-inflated drawable.
 * With 20 icons on screen, inflation alone costs ~4ms of the 16ms frame budget.
 */
@RunWith(AndroidJUnit4::class)
class VectorDrawableBenchmark {

    @get:Rule
    val benchmarkRule = BenchmarkRule()

    private lateinit var bitmap: Bitmap
    private lateinit var canvas: Canvas

    // Pre-inflated drawables
    private lateinit var homeDrawable: Drawable
    private lateinit var doneAllDrawable: Drawable

    @Before
    fun setup() {
        bitmap = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        canvas = Canvas(bitmap)

        val context = ApplicationProvider.getApplicationContext<Context>()
        homeDrawable = ContextCompat.getDrawable(context, R.drawable.ic_home)!!.apply {
            setBounds(0, 0, SIZE, SIZE)
        }
        doneAllDrawable = ContextCompat.getDrawable(context, R.drawable.ic_done_all)!!.apply {
            setBounds(0, 0, SIZE, SIZE)
        }
    }

    @After
    fun teardown() {
        bitmap.recycle()
    }

    // ── Drawing pre-inflated (cache hit) ────────────────────────────────

    @Test
    fun vectorDrawable_draw_home() {
        benchmarkRule.measureRepeated {
            homeDrawable.draw(canvas)
        }
    }

    @Test
    fun vectorDrawable_draw_doneAll() {
        benchmarkRule.measureRepeated {
            doneAllDrawable.draw(canvas)
        }
    }

    // ── Inflate + draw (cache miss / first render) ──────────────────────

    @Test
    fun vectorDrawable_inflateAndDraw_home() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        benchmarkRule.measureRepeated {
            val d = ContextCompat.getDrawable(context, R.drawable.ic_home)!!
            d.setBounds(0, 0, SIZE, SIZE)
            d.draw(canvas)
        }
    }

    @Test
    fun vectorDrawable_inflateAndDraw_doneAll() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        benchmarkRule.measureRepeated {
            val d = ContextCompat.getDrawable(context, R.drawable.ic_done_all)!!
            d.setBounds(0, 0, SIZE, SIZE)
            d.draw(canvas)
        }
    }

    companion object {
        private const val SIZE = 48
    }
}
