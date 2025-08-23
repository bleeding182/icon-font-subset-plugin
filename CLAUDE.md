# CLAUDE.md

Guidance for Claude Code (claude.ai/code) when working with this repository.

## Build Commands

```bash
# Clean and rebuild
./gradlew clean build

# Publish to Maven local (required for development)
./gradlew :font-subsetting:publishToMavenLocal

# Build native library (Docker required)
./font-subsetting/font-subsetting-plugin/build-in-docker.sh

# Test subsetting
./gradlew :app:subsetDebugFonts --info

# Build demo app
./gradlew :app:assembleDebug
```

## Architecture

Three tasks per Android variant:
1. **GenerateIconConstantsTask** - Parse .codepoints → Generate Kotlin constants
2. **AnalyzeIconUsageTask** - PSI analysis → Output usage JSON  
3. **FontSubsettingTask** - HarfBuzz subsetting → Output subsetted font

Key components:
- `font-subsetting-plugin/` - Main Gradle plugin with integrated HarfBuzz JNI wrapper
- `app/` - Demo app

## Configuration

```kotlin
fontSubsetting {
    fonts {
        create("materialSymbols") {
            fontFile.set(file("path/to/font.ttf"))
            codepointsFile.set(file("path/to/font.codepoints"))
            packageName.set("com.example")
            className.set("Icons")
            
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

## Technical Notes

- **PSI Analysis**: Uses `kotlin-compiler-embeddable` for Kotlin AST parsing
- **HarfBuzz**: Version 10.0.1+ for subsetting, 8.0.0+ for axis configuration
- **Caching**: Subsetted fonts cached in `build/fontSubsetting/cache/`
- **Native Build**: Cross-compiled via Docker for Linux/Windows/macOS
- **Generated Code**: Icon constants marked as `internal`

## Development Workflow

1. Modify plugin code
2. Run `./gradlew :font-subsetting:publishToMavenLocal`
3. Test: `./gradlew :app:subsetDebugFonts --info`

## Troubleshooting

- **Native Library**: Ensure built for your platform
- **Icons Not Found**: Check name matching (camelCase vs snake_case)
- **Stale Results**: Run `./gradlew clean`