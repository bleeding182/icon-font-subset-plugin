# Font Subsetting for Android

Adding icons individually is a pain. We already have icon fonts, but using them directly bloats the app with font sizes being multiple MB. Instead, we could look at which icons we use and then subset the font (also known as tree shaking if you have some JS background).

There are two parts to this library:

* Gradle plugin to offer you easy-to-use icons and subset the font for your build
* Runtime lib that tries to optimize the rendering via JNI calls because the Typeface API that Android offers is a little clunky

## How It Works

**1. Generate constants**

The plugin parses `.codepoints` files and generates type-safe Kotlin constants for each icon.

```kotlin
internal object MaterialSymbols {
    // ...
    const val home = "\uE9B2"
    const val homeAndGarden = "\uEF9F"
    const val homeFilled = "\uE9B2"
    // ...
}
```

_The `internal` is intentional so that we can determine which icons get used in the next step. If you use multiple modules, introduce a thin wrapper and you're good to go._

```kotlin
object AppIcon {
    val home = MaterialSymbols.home
    // ...
}
```

**2. Analyze usage**

Kotlin PSI analysis scans your source code to determine which icon constants are actually referenced. This is the basis for the next step.

**3. Subset font**

HarfBuzz is used to create a new font file containing only the used glyphs, with optional axes and hinting optimizations.

## Plugin

Add the plugin to register fonts and trigger the code generation showcased above.

```kotlin
// settings.gradle.kts
pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}

// build.gradle.kts
plugins {
    // ...
    id("com.davidmedenjak.fontsubsetting") version "x.y.z"
}
```

Register the icon font that you want to use. Don't add the font as a resource to your app, but put it in a separate directory. You can adjust the axis that you need, e.g. if you only use regular to bold you can adjust the weight to 400-700 which reduces the size further.

Having a range for an axis is needed for animations (e.g. animating between regular and bold requires 400, 700 to be available)

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

### Using the output

The plugin emits the subsetted font at `R.font.<resourceName>` and a Kotlin object at `<className>`.

You _can_ render them with stock Compose:

```kotlin
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import com.example.icons.MaterialSymbols

@Composable
fun MyScreen() {
    val symbols = remember { FontFamily(Font(R.font.symbols)) }

    Text(
        text = MaterialSymbols.home,
        fontFamily = symbols,
        fontSize = 24.sp,
    )
}
```

But ideally you'd use a Composable sized in `dp` that focuses on drawing — `Text` is sized in `sp` and routes through the full text layout pipeline, which is overkill for a single glyph.

## Runtime (optional)

To alleviate the problems of using `Text`, this library offers a runtime. Since animations need to adjust the axis and the Android API only offers to do so in a roundabout way, the runtime bundles HarfBuzz to extract the paths for various axis and animate between them — packaged as a small JNI artifact.

### Dependency

```kotlin
// build.gradle.kts
dependencies {
    implementation("com.davidmedenjak.fontsubsetting:font-subsetting-runtime:x.y.z")
}
```

### Basic usage

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

### Variable axes

Pass a static `FontVariation` for fixed axis values:

```kotlin
Icon(
    painter = rememberGlyphPainter(
        text = MaterialSymbols.favorite,
        font = font,
        variation = FontVariation.of("FILL" to 1f, "wght" to 700f),
    ),
    contentDescription = "Favorite",
)
```

For animated transitions, define a `GlyphVariationPreset` and read it via `animateFontVariationAsState`. Frames are pre-computed at the display refresh rate and HarfBuzz path extraction is batched off the main thread:

```kotlin
private val Selectable = GlyphVariationPreset(
    axes = listOf(
        FontAxisAnimation("FILL", 0f, 1f),
        FontAxisAnimation("wght", 400f, 400f),
    ),
)

@Composable
fun FavoriteIcon(font: GlyphFont) {
    val variation by animateFontVariationAsState(Selectable)

    Icon(
        painter = rememberGlyphPainter(
            text = MaterialSymbols.favorite,
            font = font,
            variation = variation,
        ),
        contentDescription = "Favorite",
    )
}
```

Variation works on all supported API levels via HarfBuzz. In Compose previews and JVM unit tests where the native library isn't loaded, the painter falls back to `Paint.fontVariationSettings`, which is silently ignored on API 24-25.

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
