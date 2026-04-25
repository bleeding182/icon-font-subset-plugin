package com.davidmedenjak.fontsubsetting

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.davidmedenjak.fontsubsetting.runtime.FontAxisAnimation
import com.davidmedenjak.fontsubsetting.runtime.GlyphVariationPreset
import com.davidmedenjak.fontsubsetting.runtime.animateFontVariationAsState
import com.davidmedenjak.fontsubsetting.runtime.rememberGlyphFont
import com.davidmedenjak.fontsubsetting.runtime.rememberGlyphPainter
import com.davidmedenjak.fontsubsetting.ui.theme.FontSubsettingTheme

private val SelectableIcon = GlyphVariationPreset(
    axes = listOf(
        FontAxisAnimation("FILL", 0f, 1f),
        FontAxisAnimation("wght", 400f, 400f),
        FontAxisAnimation("GRAD", 0f, 200f),
    ),
)

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
private fun MainScreen(modifier: Modifier = Modifier) {
    val font = rememberGlyphFont(R.font.symbols)
    val variation by animateFontVariationAsState(SelectableIcon)

    Column(
        modifier = modifier
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
                    text = "GlyphPainter Demo",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Native HarfBuzz path extraction via Icon(painter = rememberGlyphPainter(...))",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Animating: FILL (0\u21921) and GRAD (0\u2192200)",
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
                IconCard(name = name) {
                    Icon(
                        painter = rememberGlyphPainter(
                            text = icon,
                            font = font,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            variation = variation,
                        ),
                        contentDescription = name,
                        modifier = Modifier,
                    )
                }
            }
        }
    }
}

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

@Preview(showBackground = true, widthDp = 412, heightDp = 900)
@Composable
private fun MainScreenPreview() {
    FontSubsettingTheme {
        MainScreen()
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
