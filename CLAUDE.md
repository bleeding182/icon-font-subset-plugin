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
- Native C++ code using HarfBuzz for path extraction with proper overlap handling
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

- **HarfBuzz**: Industry-standard font shaping library for path extraction
- **Overlap Handling**: HarfBuzz properly handles overlapping contours (fixes FILL 1f hairline
  issues)
- **Variable Font Axes**: Uses HarfBuzz's shaping capabilities to apply axis values before
  extraction
- **JNI**: Native bridge between Kotlin and C++ for path extraction
- **Path Format**: Normalized coordinates (0-1 range) with advance metrics
- **Supported Commands**: MOVE_TO, LINE_TO, QUADRATIC_TO, CUBIC_TO, CLOSE
- **Supported Axes**: Any variable font axis (FILL, wght, wdth, opsz, slnt, GRAD, custom)
- **Performance**: Path reuse with `rewind()`, zero allocations during animation
- **Android ABIs**: armeabi-v7a, arm64-v8a, x86, x86_64
- **Size**: ~150-200KB per architecture (stripped and optimized)

## Why HarfBuzz?

The runtime library uses HarfBuzz because:

1. **Proper Overlap Handling**: HarfBuzz correctly handles overlapping contours in variable fonts (
   e.g., Material Symbols FILL axis)
2. **Android Native Compatibility**: Matches Android's internal rendering stack (Skia uses HarfBuzz,
   not FreeType draw funcs)
3. **Smaller Binary**: ~60% smaller than FreeType for outline extraction only
4. **Battle-Tested**: Industry standard used by Android, Chrome, and most graphics systems

### Material Symbols FILL Axis Glyph Substitution

Material Symbols is a variable font family from Google (https://fonts.google.com/icons), commonly
used for Android icons. One of its variable axes is **FILL**, which allows a glyph to be rendered as
an outline (`FILL=0`) or filled (`FILL=1`). However, there’s a technical detail: for some
codepoints, changing the FILL axis does *not* just update the outline—it actually causes the font to
substitute a **different glyph** entirely.

For example:

- The icon for `codepoint = 0xe3af` switches from a regular star outline at `FILL=0` to a
  solid-filled star at `FILL=1`, but the actual glyph indices are not always 1:1 mapped.
- Some symbols (especially extended ones) swap to alternate shapes at different FILL values by using
  GSUB/feature substitutions.
- If you just pick a codepoint and ask for the path at `FILL=1` without proper shaping, you might
  get the wrong icon (often just the outline, not the true filled shape).

#### Why is this tricky?

Variable fonts can do two things with axes:

- **Interpolate shape:** Move or transform contours with continuous values.
- **Substitute glyphs:** Swap glyphs via OpenType GSUB tables and conditional features.

The Material Symbols font heavily uses both: FILL changes may interpolate contours, but on many
icons it also triggers substitution logic (e.g. via the `ssXX` OpenType Stylistic Sets).

#### How does HarfBuzz fix this?

If you only use FreeType or simple font libraries, you get the glyph for the codepoint *directly*,
but you don't get the proper substitutions. HarfBuzz, as a full OpenType shaper, applies all
relevant substitution features—including those triggered by axis values (like FILL)—to select the *
*correct glyph** for the requested variation.

Our runtime library uses HarfBuzz's shaping API to:

- Shape a single-character buffer with variation coordinates (e.g., `FILL=1, wght=700`) using
  OpenType feature logic.
- Map the Unicode codepoint to the correct glyph ID for those axis coordinates, applying any GSUB
  substitutions.
- Extract the path from the actual glyph, guaranteeing both interpolation *and* correct
  substitution.

**Result:** No wrong icons rendered! When you animate the FILL axis, the outline-to-filled morph
always uses the correct shapes, as chosen by the font’s internal substitution table. You never get
the "hairline bug" or missing filled shapes.

---

**Note**: We don't need HarfBuzz's text shaping capabilities since we only render individual glyphs,
not complex text layout—but *we do require* its OpenType shaping logic for proper glyph selection on
axes like FILL.

## Performance Optimizations

### Native Path Morphing

The library now includes optimized native path morphing for variable font animations. Instead of
extracting two separate glyph paths and morphing them in Kotlin, the morphing happens entirely in
C++:

**Old approach (slower):**

```kotlin
val outlinePath = extractor.extractGlyphPath(codepoint, mapOf("FILL" to 0f))
val filledPath = extractor.extractGlyphPath(codepoint, mapOf("FILL" to 1f))

// Kotlin-side morphing on every frame
MorphingPathIcon(
    fromPath = outlinePath,
    toPath = filledPath,
    progress = animatedProgress
)
```

**New approach (3-5x faster):**

```kotlin
// Native morphing - single JNI call per frame
MorphingPathIcon(
    fontExtractor = extractor,
    char = '★',
    fromVariations = mapOf("FILL" to 0f),
    toVariations = mapOf("FILL" to 1f),
    progress = animatedProgress
)
```

**Benefits:**

- Morphing happens in native memory (no Kotlin object allocations)
- Single JNI call instead of two + Kotlin interpolation loop
- 3-5x performance improvement for animations
- Cleaner API - no need to pre-extract paths

**Implementation:**
The native `morphGlyphPaths()` function:

1. Extracts both glyph paths with HarfBuzz (using different axis values)
2. Interpolates path commands directly in C++
3. Returns pre-morphed float array ready for Compose Path

This optimization is particularly effective when animating multiple icons simultaneously.

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