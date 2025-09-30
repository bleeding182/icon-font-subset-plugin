# Font Subsetting for Android

A Gradle plugin that automatically reduces Android app font sizes by subsetting them at build time to include only the glyphs actually used in the code.

**Result**: Material Symbols font reduced from **10MB → 1.8KB** (just the icons you use)

## How It Works

The plugin analyzes your Kotlin code at build time to detect which icons are used, then automatically subsets the font file to include only those glyphs. This happens through three Gradle tasks that generate icon constants, analyze usage via PSI parsing, and perform HarfBuzz-based font subsetting.

## Installation

### From Gradle Plugin Portal

```kotlin
// settings.gradle.kts
pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}
```

```kotlin
// app/build.gradle.kts
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.davidmedenjak.fontsubsetting") version "1.0.0"
}
```

### From GitHub Packages

```kotlin
// settings.gradle.kts
pluginManagement {
    repositories {
        maven {
            url = uri("https://maven.pkg.github.com/bleeding182/icon-font-subset-plugin")
            credentials {
                username = providers.gradleProperty("gpr.user").orNull ?: System.getenv("GITHUB_ACTOR")
                password = providers.gradleProperty("gpr.key").orNull ?: System.getenv("GITHUB_TOKEN")
            }
        }
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}
```

## Quick Start

### 1. Configure the Plugin

```kotlin
// app/build.gradle.kts
fontSubsetting {
    fonts {
        create("materialSymbols") {
            fontFile.set(file("fonts/MaterialSymbolsOutlined.ttf"))
            codepointsFile.set(file("fonts/MaterialSymbolsOutlined.codepoints"))
            className.set("com.example.icons.MaterialSymbols")
            resourceName.set("symbols")

            stripHinting = true      // Remove hinting (default: true)
            stripGlyphNames = true   // Remove glyph names (default: true)
        }
    }
}
```

### 2. Render Icons with Glyph (Recommended)

The `Glyph` composable renders icons using Android's `Paint` + `Canvas` for optimal performance. It uses `dp` sizing (not `sp`), so icons don't scale with text size preferences.

```kotlin
import com.example.icons.MaterialSymbols

@Composable
fun MyScreen() {
    val font = rememberGlyphFont(R.font.symbols)

    Glyph(
        text = MaterialSymbols.home,  // Type-safe generated constant
        font = font,
        size = 24.dp,
        tint = Color.Black,
    )
}
```

### Alternative: Using Text Composable

You can also use the standard `Text` composable, though this is less optimized for icon rendering:

```kotlin
@Composable
fun MyScreen() {
    Text(
        text = MaterialSymbols.home,
        fontFamily = FontFamily(Font(R.font.symbols)),
        fontSize = 24.sp,
    )
}
```

## Variable Font Support

The plugin preserves variable font axes for runtime styling and animation.

### Static Axes with Glyph

```kotlin
@Composable
fun StyledIcon() {
    val font = rememberGlyphFont(R.font.symbols)

    Glyph(
        text = MaterialSymbols.favorite,
        font = font,
        size = 24.dp,
        axes = mapOf("FILL" to 1f, "wght" to 700f),
    )
}
```

### Animated Axes with Glyph

For animations, use `buildFontVariationSettings` with `remember` for zero-allocation rendering:

```kotlin
@Composable
fun AnimatedIcon(selected: Boolean) {
    val font = rememberGlyphFont(R.font.symbols)
    val fill by animateFloatAsState(if (selected) 1f else 0f)

    Glyph(
        text = MaterialSymbols.favorite,
        font = font,
        size = 24.dp,
        fontVariationSettings = remember(fill) {
            buildFontVariationSettings("FILL" to fill, "wght" to 400f)
        },
    )
}
```

### Available Axes
- **FILL** (0-1): Outlined to Filled
- **wght** (100-700): Thin to Bold
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
                axis("FILL").range(0f, 1f, 0f)    // Keep fill with default 0
                axis("wght").range(400f, 700f, 400f) // Keep only normal-bold
                axis("GRAD").remove()                // Remove grade axis entirely
                axis("opsz").range(24f, 48f, 48f)   // Limit optical sizes
            }
        }
    }
}
```

## Build Commands

```bash
# Build and test the subsetting
./gradlew :demo:assembleDebug

# Run subsetting with verbose output
./gradlew :demo:subsetDebugFonts --info

# For plugin development (Docker required)
cd plugin && ./build-in-docker.sh
```

## Requirements

- Android Gradle Plugin 9.0+
- Kotlin 2.2+
- Minimum SDK 24


## License

MIT License
