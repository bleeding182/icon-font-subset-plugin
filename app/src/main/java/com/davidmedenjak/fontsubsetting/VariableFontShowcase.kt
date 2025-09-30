package com.davidmedenjak.fontsubsetting

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VariableFontShowcase() {
    var fontConfig by remember { mutableStateOf(VariableFontConfig.DEFAULT) }
    var enableAnimation by remember { mutableStateOf(false) }
    val fontFamily = if (enableAnimation) {
        rememberAnimatedVariableFontFamily(fontConfig)
    } else {
        rememberVariableFontFamily(fontConfig)
    }
    
    val context = LocalContext.current
    val subsettedFontSize = remember {
        FontSizeUtil.getFontResourceSize(context, R.font.symbols)
    }
    val originalFontSize = 10174464L // 9.7 MB in bytes
    val reductionPercentage = FontSizeUtil.calculateReductionPercentage(
        originalFontSize,
        subsettedFontSize
    )
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        TopAppBar(
            title = { Text("Material Symbols Variable Font") },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
            )
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Original Size",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    )
                    Text(
                        text = FontSizeUtil.formatFileSize(originalFontSize),
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Subsetted Size",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    )
                    Text(
                        text = FontSizeUtil.formatFileSize(subsettedFontSize),
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Reduction",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    )
                    Text(
                        text = "%.1f%%".format(reductionPercentage),
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Font Axis Controls",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Enable Animation",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Switch(
                            checked = enableAnimation,
                            onCheckedChange = { enableAnimation = it }
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    FontAxisSlider(
                        label = "Fill",
                        value = fontConfig.fill,
                        onValueChange = { fontConfig = fontConfig.copy(fill = it) },
                        valueRange = VariableFontConfig.FILL_RANGE,
                        steps = 10
                    )
                    
                    FontAxisSlider(
                        label = "Weight",
                        value = fontConfig.weight,
                        onValueChange = { fontConfig = fontConfig.copy(weight = it) },
                        valueRange = VariableFontConfig.WEIGHT_RANGE,
                        steps = 6
                    )
                    
                    FontAxisSlider(
                        label = "Grade",
                        value = fontConfig.grade,
                        onValueChange = { fontConfig = fontConfig.copy(grade = it) },
                        valueRange = VariableFontConfig.GRADE_RANGE,
                        steps = 0
                    )
                    
                    FontAxisSlider(
                        label = "Optical Size",
                        value = fontConfig.opticalSize,
                        onValueChange = { fontConfig = fontConfig.copy(opticalSize = it) },
                        valueRange = VariableFontConfig.OPTICAL_SIZE_RANGE,
                        steps = 0
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Icon Preview",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    IconGrid(fontFamily = fontFamily)
                }
            }
        }
    }
}

@Composable
fun FontAxisSlider(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int = 0
) {
    Column(
        modifier = Modifier.padding(vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = "%.1f".format(value),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
        
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun IconGrid(fontFamily: androidx.compose.ui.text.font.FontFamily) {
    val icons = listOf(
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
//        MaterialSymbols.shoppingCart to "shopping_cart",
//        MaterialSymbols.cloud to "cloud",
        MaterialSymbols.folder to "folder",
        MaterialSymbols.email to "email",
        MaterialSymbols.call to "call",
        MaterialSymbols.schedule to "schedule",
        MaterialSymbols.lock to "lock",
        MaterialSymbols.person to "person"
    )
    
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 100.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(500.dp),
        contentPadding = PaddingValues(4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(icons) { iconPair ->
            IconCard(icon = iconPair.first, name = iconPair.second, fontFamily = fontFamily)
        }
    }
}

@Composable
fun IconCard(
    icon: String,
    name: String,
    fontFamily: androidx.compose.ui.text.font.FontFamily
) {
    Card(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(8.dp)),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
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
            Text(
                text = icon,
                fontFamily = fontFamily,
                fontSize = 40.sp,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = name,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}