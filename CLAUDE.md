# CLAUDE.md

Guidance for Claude Code when working with this repository.

## Project Structure

```
plugin/                          - Gradle plugin (included build via pluginManagement)
  src/main/cpp/                  - Native C++ for HarfBuzz font subsetting (JVM)
  src/main/kotlin/.../plugin/    - Plugin tasks, DSL, services
  build-in-docker.sh             - Cross-compile native libs (Docker required)
demo/                            - Demo Android app using plugin + glyph rendering
font-subsetting/                 - Maven publishing modules (scaffolding)
  font-subsetting-native/        - Native lib resource packaging (only active module)
res/                             - Font resources
```

Key: `plugin/` is a separate included build (see `settings.gradle.kts`). The main project only includes `:demo`.

## Build Commands

```bash
./gradlew clean build                              # Clean and rebuild
./gradlew :demo:assembleDebug                      # Build demo app
./gradlew :demo:subsetDebugFonts --info            # Test font subsetting

# Build native libs for plugin's JVM subsetting (Docker required, cross-compiles for all platforms)
cd plugin && ./build-in-docker.sh
```

## Plugin Architecture

Plugin ID: `com.davidmedenjak.fontsubsetting`

Three Gradle tasks registered per Android variant:

1. **GenerateIconConstantsTask** - Parses `.codepoints` files -> Generates Kotlin icon constants
2. **AnalyzeIconUsageTask** - PSI analysis of Kotlin source -> Outputs JSON with used icons
3. **FontSubsettingTask** - HarfBuzz subsetting -> Creates optimized font with only used glyphs

Task source: `plugin/src/main/kotlin/com/davidmedenjak/fontsubsetting/plugin/tasks/`

## Configuration

```kotlin
// In demo/build.gradle.kts
plugins {
    id("com.davidmedenjak.fontsubsetting") version "local" // Uses included build
}

fontSubsetting {
    fonts {
        create("materialSymbols") {
            fontFile.set(file("path/to/font.ttf"))
            codepointsFile.set(file("path/to/font.codepoints"))
            className.set("com.example.pkg.Icons")  // Fully-qualified class name (package derived from this)
            resourceName.set("symbols")              // Matches font filename in res/font/

            stripHinting.set(true)      // Remove hinting (15-20% size reduction, default: true)
            stripGlyphNames.set(true)   // Remove glyph names (5-10% size reduction, default: true)

            axes {
                axis("FILL").range(0f, 1f, 0f)
                axis("wght").range(400f, 700f, 400f)
                axis("GRAD").remove()
            }
        }
    }
}
```

## Glyph Rendering (in demo)

The demo includes `Glyph.kt` with Paint + Canvas rendering for subsetted icon fonts:

- **GlyphFont** — `@Immutable` wrapper around Android `Typeface`
- **Glyph()** — Composable that renders via `Paint` + `Canvas.drawText()` (Skia GPU glyph caching)
- **rememberGlyphFont()** — Loads a `Typeface` from a font resource
- Variable font axes via `Paint.fontVariationSettings` (API 26+; on API 24-25 uses font defaults)
- Icon sizes in `dp` (not `sp`) — icons don't scale with text size preferences

## Troubleshooting

- **Icons Not Found**: Plugin handles both camelCase and snake_case naming
- **Stale Results**: Run `./gradlew clean` to clear caches
- **Axes Not Working**: `fontVariationSettings` requires API 26+; on API 24-25, axes are ignored
