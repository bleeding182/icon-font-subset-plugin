# Font Subsetting for Android

A Gradle plugin that automatically subsets icon fonts based on actual usage in your code, paired with a Compose runtime library for rendering. Material Symbols ships ~10MB of glyphs; most apps use a handful. This plugin analyzes your source, finds which icons you reference, and produces a subset font containing only those glyphs -- reducing the font from megabytes to kilobytes.

## How It Works

1. **Generate constants** -- The plugin parses `.codepoints` files and generates type-safe Kotlin constants for each icon.
2. **Analyze usage** -- Kotlin PSI analysis scans your source code to determine which icon constants are actually referenced.
3. **Subset font** -- HarfBuzz creates a new font file containing only the used glyphs, with optional axis and hinting optimizations.
4. **Render** -- The runtime library provides a Compose `Glyph` painter that renders icons via native HarfBuzz path extraction.

## Setup

### Plugin

```kotlin
// settings.gradle.kts
pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}

// app/build.gradle.kts
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.davidmedenjak.fontsubsetting") version "1.0.0"
}
```

### Configuration

```kotlin
fontSubsetting {
    fonts {
        create("materialSymbols") {
            fontFile.set(file("fonts/MaterialSymbolsOutlined.ttf"))
            codepointsFile.set(file("fonts/MaterialSymbolsOutlined.codepoints"))
            className.set("com.example.icons.MaterialSymbols")
            resourceName.set("symbols")

            stripHinting = true
            stripGlyphNames = true

            axes {
                axis("FILL").range(0f, 1f, 0f)
                axis("wght").range(400f, 700f, 400f)
                axis("GRAD").remove()
            }
        }
    }
}
```

### Runtime

Add the runtime library dependency:

```kotlin
// app/build.gradle.kts
dependencies {
    implementation("com.davidmedenjak.fontsubsetting:font-subsetting-runtime:1.0.0")
}
```

Use the generated constants with `rememberGlyphPainter`:

```kotlin
import com.example.icons.MaterialSymbols

@Composable
fun MyScreen() {
    val font = rememberGlyphFont(R.font.symbols)

    Icon(
        painter = rememberGlyphPainter(
            text = MaterialSymbols.home,
            font = font,
            tint = Color.Black,
        ),
        contentDescription = "Home",
    )
}
```

## Variable Font Axes

The plugin supports subsetting variable font axes -- you can constrain ranges, set defaults, or remove axes entirely to further reduce file size. At runtime, variation settings can be applied per icon, including animated transitions:

```kotlin
val fill by animateFloatAsState(if (selected) 1f else 0f)

Icon(
    painter = rememberGlyphPainter(
        text = MaterialSymbols.favorite,
        font = font,
        variation = animateFontVariationAsState(
            FontVariation.of("FILL" to fill, "wght" to 400f),
        ),
    ),
    contentDescription = "Favorite",
)
```

Font variation settings require API 26+. On API 24-25, axis values are ignored and defaults are used.

## Build

```bash
./gradlew :demo:assembleDebug              # Build demo app
./gradlew :demo:subsetDebugFonts --info    # Run subsetting with verbose output
cd plugin && ./build-in-docker.sh          # Cross-compile native libs (Docker required)
```

## Requirements

- Android Gradle Plugin 9.0+
- Kotlin 2.2+
- Minimum SDK 24

## License

MIT License
