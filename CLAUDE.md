# CLAUDE.md

Guidance for Claude Code (claude.ai/code) when working with this repository.

## Project Structure

- `font-subsetting/` - Gradle plugin with HarfBuzz JNI wrapper
  - `font-subsetting-plugin/` - Main plugin implementation
  - `font-subsetting-runtime/` - Android library for runtime path extraction and animation
  - `src/main/cpp/` - Native C++ code with HarfBuzz integration
  - `src/main/resources/native/` - Pre-built native libraries for all platforms
- `app/` - Demo Android application using the plugin and runtime

## Build Commands

```bash
# Clean and rebuild entire project
./gradlew clean build

# Build native libraries (Docker required, cross-compiles for all platforms)
cd font-subsetting/font-subsetting-plugin
./build-in-docker.sh

# Test font subsetting in demo app
./gradlew :app:subsetDebugFonts --info

# Build demo app
./gradlew :app:assembleDebug

# Build runtime library
./gradlew :font-subsetting:font-subsetting-runtime:assembleDebug
```

## Plugin Architecture

Three Gradle tasks are registered per Android variant:

1. **GenerateIconConstantsTask** - Parses `.codepoints` files → Generates Kotlin icon constants
2. **AnalyzeIconUsageTask** - PSI analysis of Kotlin code → Outputs JSON with used icons
3. **FontSubsettingTask** - HarfBuzz subsetting → Creates optimized font with only used glyphs

## Runtime Library Architecture

The `font-subsetting-runtime` module provides:
- **FontPathExtractor** - JNI wrapper for extracting vector paths from font glyphs using HarfBuzz
- **Variable Font Axis Support** - Extract paths at specific axis values (FILL, wght, opsz, etc.)
- **PathIcon** - Compose component for rendering glyphs as vector paths
- **AnimatedPathIcon** - Compose component with progressive drawing animation
- **MorphingPathIcon** - Compose component for morphing between different axis values
- Native C++ code using HarfBuzz 10.0.1 for path extraction
- Optimized rendering with path reuse and efficient Canvas updates

## Configuration

```kotlin
// In app/build.gradle.kts
plugins {
    id("com.davidmedenjak.fontsubsetting") version "local" // Uses included build
}

dependencies {
    // Add runtime library for path extraction and animation
    implementation(project(":font-subsetting:font-subsetting-runtime"))
}

fontSubsetting {
    fonts {
        create("materialSymbols") {
            fontFile.set(file("path/to/font.ttf"))
            codepointsFile.set(file("path/to/font.codepoints"))
            packageName.set("com.example")
            className.set("Icons")

            // Font optimization flags (optional, default: true)
            stripHinting.set(true)      // Remove hinting instructions (15-20% size reduction)
            stripGlyphNames.set(true)   // Remove glyph names (5-10% size reduction)

            // Variable font axes (optional)
            axes {
                axis("FILL").range(0f, 1f, 0f)
                axis("wght").range(400f, 700f, 400f)
                axis("GRAD").remove()
            }
        }
    }
}
```

## Runtime Library Usage

```kotlin
// Extract font paths at runtime
val pathExtractor = FontPathExtractor.fromResource(context, R.font.my_font)
val glyphPath = pathExtractor.extractGlyphPath('★'.code)

// Extract at specific variable font axis values
val outlinePath = pathExtractor.extractGlyphPath('★'.code, mapOf("FILL" to 0f))
val filledPath = pathExtractor.extractGlyphPath('★'.code, mapOf("FILL" to 1f))

// Render as static Compose icon
PathIcon(
    glyphPath = glyphPath,
    size = 48.dp,
    tint = Color.Blue
)

// Render with drawing animation
AnimatedPathIcon(
    glyphPath = glyphPath,
    progress = animationProgress, // 0f to 1f
    size = 48.dp
)

// Morph between axis values
MorphingPathIcon(
    fromPath = outlinePath,
    toPath = filledPath,
    progress = fillProgress, // 0f to 1f
    size = 48.dp
)
```

## Technical Details

### Plugin
- **Kotlin Analysis**: Uses `kotlin-compiler-embeddable` for PSI/AST parsing
- **HarfBuzz**: Version 10.0.1 for subsetting with full flag support
- **Subsetting Flags**:
  - `HB_SUBSET_FLAGS_NO_HINTING` - Strips TrueType hinting
  - `HB_SUBSET_FLAGS_DESUBROUTINIZE` - Removes CFF subroutines
  - `HB_SUBSET_FLAGS_GLYPH_NAMES` - Controls glyph name retention
- **Caching**: Subsetted fonts cached in `build/fontSubsetting/cache/` with SHA-256 keys
- **Native Libraries**: Pre-built for Linux x86_64, Windows x86_64, macOS x86_64, macOS ARM64
- **Generated Code**: Icon constants are `internal` to prevent API leakage

### Runtime Library

- **HarfBuzz**: Version 10.0.1 for path extraction using draw funcs API
- **Variable Font Axes**: Uses `hb_font_set_variations()` to apply axis values before extraction
- **JNI**: Native bridge between Kotlin and C++ for path extraction
- **Path Format**: Normalized coordinates (0-1 range) with advance metrics
- **Supported Commands**: MOVE_TO, LINE_TO, QUADRATIC_TO, CUBIC_TO, CLOSE
- **Supported Axes**: Any variable font axis (FILL, wght, wdth, opsz, slnt, GRAD, custom)
- **Performance**: Path reuse with `rewind()`, zero allocations during animation
- **Android ABIs**: armeabi-v7a, arm64-v8a, x86, x86_64

## Development Workflow

The plugin uses Gradle's composite build feature (configured in `settings.gradle.kts`):
- Version `"local"` uses the included build directly - no publishing needed
- Other versions resolve from Maven repositories

To develop:
1. Modify plugin code in `font-subsetting/`
2. If native code changed:
  - Plugin: `./font-subsetting/font-subsetting-plugin/build-in-docker.sh`
  - Runtime: Native builds happen automatically via CMake during Android build
3. Test directly: `./gradlew :app:subsetDebugFonts --info`

## Troubleshooting

- **Native Library Not Found**: Run `build-in-docker.sh` to build for all platforms
- **Icons Not Found**: Plugin handles both camelCase and snake_case naming
- **Stale Results**: Run `./gradlew clean` to clear caches
- **Daemon Crashes**: Check for JNI method signature mismatches between Kotlin and C++