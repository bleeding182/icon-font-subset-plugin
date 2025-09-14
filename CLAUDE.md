# CLAUDE.md

Guidance for Claude Code (claude.ai/code) when working with this repository.

## Project Structure

- `font-subsetting/` - Gradle plugin with HarfBuzz JNI wrapper
  - `font-subsetting-plugin/` - Main plugin implementation
  - `src/main/cpp/` - Native C++ code with HarfBuzz integration
  - `src/main/resources/native/` - Pre-built native libraries for all platforms
- `app/` - Demo Android application using the plugin

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
```

## Plugin Architecture

Three Gradle tasks are registered per Android variant:

1. **GenerateIconConstantsTask** - Parses `.codepoints` files → Generates Kotlin icon constants
2. **AnalyzeIconUsageTask** - PSI analysis of Kotlin code → Outputs JSON with used icons
3. **FontSubsettingTask** - HarfBuzz subsetting → Creates optimized font with only used glyphs

## Configuration

```kotlin
// In app/build.gradle.kts
plugins {
    id("com.davidmedenjak.fontsubsetting") version "local" // Uses included build
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

## Technical Details

- **Kotlin Analysis**: Uses `kotlin-compiler-embeddable` for PSI/AST parsing
- **HarfBuzz**: Version 10.0.1 for subsetting with full flag support
- **Subsetting Flags**:
  - `HB_SUBSET_FLAGS_NO_HINTING` - Strips TrueType hinting
  - `HB_SUBSET_FLAGS_DESUBROUTINIZE` - Removes CFF subroutines
  - `HB_SUBSET_FLAGS_GLYPH_NAMES` - Controls glyph name retention
- **Caching**: Subsetted fonts cached in `build/fontSubsetting/cache/` with SHA-256 keys
- **Native Libraries**: Pre-built for Linux x86_64, Windows x86_64, macOS x86_64, macOS ARM64
- **Generated Code**: Icon constants are `internal` to prevent API leakage

## Development Workflow

The plugin uses Gradle's composite build feature (configured in `settings.gradle.kts`):
- Version `"local"` uses the included build directly - no publishing needed
- Other versions resolve from Maven repositories

To develop:
1. Modify plugin code in `font-subsetting/`
2. If native code changed: `./font-subsetting/font-subsetting-plugin/build-in-docker.sh`
3. Test directly: `./gradlew :app:subsetDebugFonts --info`

## Troubleshooting

- **Native Library Not Found**: Run `build-in-docker.sh` to build for all platforms
- **Icons Not Found**: Plugin handles both camelCase and snake_case naming
- **Stale Results**: Run `./gradlew clean` to clear caches
- **Daemon Crashes**: Check for JNI method signature mismatches between Kotlin and C++