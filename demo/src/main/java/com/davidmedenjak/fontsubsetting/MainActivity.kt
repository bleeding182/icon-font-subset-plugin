package com.davidmedenjak.fontsubsetting

/**
 * Font Subsetting Demo - Variable Font Animation
 *
 * Demonstrates two rendering approaches side-by-side:
 * - **Native Font**: Android's Text composable with variable font axes
 * - **Glyph Library**: Paint + Canvas rendering with variable font axes
 *
 * Both tabs animate FILL (0-1) and GRAD (0-200) across 40 icons at 60fps.
 */

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.davidmedenjak.fontsubsetting.ui.theme.FontSubsettingTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FontSubsettingTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    MainScreen(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}

@Composable
fun MainScreen(modifier: Modifier = Modifier) {
    var selectedTab by remember { mutableIntStateOf(0) }

    Column(modifier = modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = selectedTab) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text("Native Font") }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text("Glyph Library") }
            )
        }

        when (selectedTab) {
            0 -> NativeFontDemo()
            1 -> GlyphLibraryDemo()
        }
    }
}

@Preview(showBackground = true)
@Composable
fun MainScreenPreview() {
    FontSubsettingTheme {
        MainScreen()
    }
}

// Icon list for the demo - 40 icons (8x5 grid)
private val demoIcons: List<Pair<String, String>> = listOf(
    MaterialSymbols.home to "home",
    MaterialSymbols.favorite to "favorite",
    MaterialSymbols.star to "star",
    MaterialSymbols.search to "search",
    MaterialSymbols.accountCircle to "account_circle",
    MaterialSymbols.settings to "settings",
    MaterialSymbols.notifications to "notifications",
    MaterialSymbols.edit to "edit",
    MaterialSymbols.delete to "delete",
    MaterialSymbols.send to "send",
    MaterialSymbols.share to "share",
    MaterialSymbols.menu to "menu",
    MaterialSymbols.folder to "folder",
    MaterialSymbols.email to "email",
    MaterialSymbols.call to "call",
    MaterialSymbols.schedule to "schedule",
    MaterialSymbols.lock to "lock",
    MaterialSymbols.person to "person",
    MaterialSymbols.cloud to "cloud",
    MaterialSymbols.info to "info",
    MaterialSymbols.add to "add",
    MaterialSymbols.remove to "remove",
    MaterialSymbols.update to "update",
    MaterialSymbols.download to "download",
    MaterialSymbols.upload to "upload",
    MaterialSymbols.contentCopy to "content_copy",
    MaterialSymbols.contentPaste to "content_paste",
    MaterialSymbols.cut to "cut",
    MaterialSymbols.undo to "undo",
    MaterialSymbols.redo to "redo",
    MaterialSymbols.save to "save",
    MaterialSymbols.print to "print",
    MaterialSymbols.linkOff to "link_off",
    MaterialSymbols.help to "help",
    MaterialSymbols.flag to "flag",
    MaterialSymbols.warning to "warning",
    MaterialSymbols.error to "error",
    MaterialSymbols.check to "check",
    MaterialSymbols.close to "close",
    MaterialSymbols.cleanHands to "clean_hands",
)

/**
 * Animates fill (0-1) and grade (0-200) axes with an infinite reverse transition.
 * Returns a Pair of (fill, grade) animated values.
 */
@Composable
private fun rememberAnimatedFillAndGrade(label: String): Pair<Float, Float> {
    val infiniteTransition = rememberInfiniteTransition(label = label)
    val fill by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "fill"
    )
    val grade by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 200f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "grade"
    )
    return fill to grade
}

@Composable
fun NativeFontDemo() {
    val (fill, grade) = rememberAnimatedFillAndGrade("native_font_animation")

    val animatedFontFamily = rememberVariableFontFamily(
        VariableFontConfig(
            fill = fill,
            weight = 400f,
            grade = grade,
            opticalSize = 48f
        )
    )

    IconGridDemo(
        title = "Native Android Font Rendering",
        description = "Using Android's Text composable with variable font axes"
    ) { icon, name ->
        NativeIconCard(
            icon = icon,
            name = name,
            fontFamily = animatedFontFamily
        )
    }
}

@Composable
fun GlyphLibraryDemo() {
    val font = rememberGlyphFont(R.font.symbols)
    val (fill, grade) = rememberAnimatedFillAndGrade("glyph_animation")

    IconGridDemo(
        title = "Glyph Library Canvas Rendering",
        description = "Using Paint + Canvas with variable font axes"
    ) { icon, name ->
        GlyphIconCard(
            icon = icon,
            name = name,
            font = font,
            fill = fill,
            grade = grade
        )
    }
}

@Composable
fun IconGridDemo(
    title: String,
    description: String,
    iconCard: @Composable (icon: String, name: String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Animating: FILL (0→1) and GRAD (0→200)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        LazyVerticalGrid(
            columns = GridCells.Fixed(8),
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(4.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(demoIcons) { (icon, name) ->
                iconCard(icon, name)
            }
        }
    }
}

@Composable
fun NativeIconCard(
    icon: String,
    name: String,
    fontFamily: androidx.compose.ui.text.font.FontFamily
) {
    IconCard(name = name) {
        Text(
            text = icon,
            fontFamily = fontFamily,
            fontSize = 20.sp,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun GlyphIconCard(
    icon: String,
    name: String,
    font: GlyphFont,
    fill: Float,
    grade: Float
) {
    IconCard(name = name) {
        Glyph(
            text = icon,
            font = font,
            size = 20.dp,
            tint = MaterialTheme.colorScheme.onSecondaryContainer,
            fontVariationSettings = remember(fill, grade) {
                buildFontVariationSettings("FILL" to fill, "wght" to 400f, "GRAD" to grade)
            },
        )
    }
}

@Composable
private fun IconCard(
    name: String,
    icon: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.aspectRatio(1f),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            icon()
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = name,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
                maxLines = 1
            )
        }
    }
}