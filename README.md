# Font Subsetting for Android

A Gradle plugin that automatically reduces Android app font sizes by subsetting them at build time to include only the glyphs actually used in the code.

**Result**: Material Symbols font reduced from **10MB → 1.8KB** (just the icons you use)

## How It Works

The plugin analyzes your Kotlin code at build time to detect which icons are used, then automatically subsets the font file to include only those glyphs. This happens through three Gradle tasks that generate icon constants, analyze usage via PSI parsing, and perform HarfBuzz-based font subsetting.

## Quick Start

### 1. Apply the Plugin

```kotlin
// app/build.gradle.kts
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.davidmedenjak.fontsubsetting") version "1.0.0"
}

fontSubsetting {
    fonts {
        create("materialSymbols") {
            fontFile.set(file("fonts/MaterialSymbolsOutlined.ttf"))
            codepointsFile.set(file("fonts/MaterialSymbolsOutlined.codepoints"))
            packageName.set("com.example.icons")
            className.set("MaterialIcons")
        }
    }
}
```

### 2. Use Generated Icons

```kotlin
import com.example.icons.MaterialIcons

@Composable
fun MyScreen() {
    Text(
        text = MaterialIcons.home,  // Type-safe icon constant
        fontFamily = FontFamily(Font(R.font.material_symbols)),
        fontSize = 24.sp
    )
}
```

## Variable Font Support

The plugin preserves variable font axes for animations:

```kotlin
@Composable
fun AnimatedIcon(selected: Boolean) {
    val fill by animateFloatAsState(if (selected) 1f else 0f)
    
    Text(
        text = MaterialIcons.favorite,
        fontVariationSettings = FontVariation.Settings(
            FontVariation.Setting("FILL", fill),  // 0=outlined, 1=filled
            FontVariation.Setting("wght", 400f),  // weight: 100-700
        )
    )
}
```

### Available Axes
- **FILL** (0-1): Outlined → Filled
- **wght** (100-700): Thin → Bold  
- **GRAD** (-25-200): Grade adjustment
- **opsz** (20-48): Optical size

## Advanced: Axis Optimization

Further reduce size by constraining or removing axes:

```kotlin
fontSubsetting {
    fonts {
        create("materialSymbols") {
            // ... font configuration ...
            
            axes {
                axis("wght").range(400f, 700f)  // Keep only normal-bold
                axis("GRAD").remove()            // Remove grade axis
                axis("opsz").range(24f, 48f)    // Limit optical sizes
            }
        }
    }
}
```

## Build Commands

```bash
# Build and test the subsetting
./gradlew :app:assembleDebug

# For plugin development
./gradlew :font-subsetting:publishToMavenLocal
```

## Requirements

- Android Gradle Plugin 8.0+
- Kotlin 2.0+
- Minimum SDK 21


## License

Apache 2.0