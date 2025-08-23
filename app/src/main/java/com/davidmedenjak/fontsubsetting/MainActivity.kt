package com.davidmedenjak.fontsubsetting

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.davidmedenjak.fontsubsetting.ui.theme.FontSubsettingTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FontSubsettingTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    VariableFontShowcase()
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun VariableFontShowcasePreview() {
    FontSubsettingTheme {
        VariableFontShowcase()
    }
}