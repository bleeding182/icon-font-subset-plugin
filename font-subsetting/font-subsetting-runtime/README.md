# Font Subsetting Runtime

Android library for extracting and rendering font glyphs as vector paths in Jetpack Compose with
full variable font axis support.

## Features

- Extract vector paths from font glyphs at runtime using HarfBuzz
- **Variable font axis support** - Extract paths at specific axis values (FILL, wght, opsz, etc.)
- Render glyphs as Compose `Path` objects
- Built-in animation support for path drawing effects
- **Path morphing** - Animate between different axis values
- Minimal overhead - only extracts paths for used glyphs
- Native performance with HarfBuzz 10.0.1
- Optimized rendering with path reuse and efficient recomposition

## Installation

```kotlin
dependencies {
    implementation("com.davidmedenjak.fontsubsetting:font-subsetting-runtime:1.0.0")
}
```

Or for local development:

```kotlin
dependencies {
    implementation(project(":font-subsetting:font-subsetting-runtime"))
}
```

## Usage

### Basic Path Extraction

```kotlin
// Load font from resources
val pathExtractor = FontPathExtractor.fromResource(context, R.font.my_icon_font)

// Extract path for a specific glyph
val starPath = pathExtractor.extractGlyphPath('★')

// Or by codepoint
val heartPath = pathExtractor.extractGlyphPath(0x2665)
```

### Variable Font Axis Extraction

```kotlin
val pathExtractor = FontPathExtractor.fromResource(context, R.font.variable_font)

// Extract at different FILL values
val outlineStar = pathExtractor.extractGlyphPath('★'.code, mapOf("FILL" to 0f))
val filledStar = pathExtractor.extractGlyphPath('★'.code, mapOf("FILL" to 1f))

// Extract with multiple axes
val boldFilledStar = pathExtractor.extractGlyphPath('★'.code, mapOf(
    "FILL" to 1f,
    "wght" to 700f,
    "opsz" to 48f
))
```

### Rendering in Compose

#### Static Icon

```kotlin
@Composable
fun MyIcon() {
    val pathExtractor = remember {
        FontPathExtractor.fromResource(LocalContext.current, R.font.my_icons)
    }
    val glyphPath = remember { pathExtractor.extractGlyphPath('★') }
    
    glyphPath?.let {
        PathIcon(
            glyphPath = it,
            size = 48.dp,
            tint = MaterialTheme.colorScheme.primary
        )
    }
}
```

#### Drawing Animation

```kotlin
@Composable
fun AnimatedIcon() {
    val glyphPath = /* ... */
    var progress by remember { mutableFloatStateOf(0f) }
    
    // Manual control
    AnimatedPathIcon(
        glyphPath = glyphPath,
        progress = progress,
        size = 64.dp,
        strokeWidth = 0.02f
    )
    
    // Or automatic animation
    PathIconAnimated(
        glyphPath = glyphPath,
        animate = true,
        size = 64.dp
    )
}
```

#### Variable Font Axis Morphing

```kotlin
@Composable
fun MorphingIcon() {
    val pathExtractor = remember {
        FontPathExtractor.fromResource(LocalContext.current, R.font.variable_icons)
    }
    
    // Extract paths at different FILL values
    val outlinePath = remember {
        pathExtractor.extractGlyphPath('★'.code, mapOf("FILL" to 0f))
    }
    val filledPath = remember {
        pathExtractor.extractGlyphPath('★'.code, mapOf("FILL" to 1f))
    }
    
    var fillProgress by remember { mutableFloatStateOf(0f) }
    
    // Morph between outline and filled
    MorphingPathIcon(
        fromPath = outlinePath!!,
        toPath = filledPath!!,
        progress = fillProgress,
        size = 64.dp
    )
    
    // Or with automatic animation
    MorphingPathIconAnimated(
        fromPath = outlinePath!!,
        toPath = filledPath!!,
        animate = true,
        size = 64.dp
    )
}
```

#### Infinite Animation Example

```kotlin
@Composable
fun PulsingIcon() {
    val infiniteTransition = rememberInfiniteTransition()
    val fillProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500),
            repeatMode = RepeatMode.Reverse
        )
    )
    
    MorphingPathIcon(
        fromPath = outlinePath,
        toPath = filledPath,
        progress = fillProgress,
        size = 48.dp
    )
}
```

### Path Transformations

```kotlin
val originalPath = pathExtractor.extractGlyphPath('→')
val mirroredPath = originalPath?.mirror()  // ←
val flippedPath = originalPath?.flipVertical()
```

## Composable Functions

### PathIcon

Static icon rendering with no animation.

**Parameters:**

- `glyphPath: GlyphPath` - The extracted glyph path
- `modifier: Modifier` - Compose modifier
- `size: Dp` - Icon size (default: 24.dp)
- `tint: Color` - Icon color (default: Color.Unspecified)
- `style: DrawStyle` - Fill or Stroke (default: Fill)

### AnimatedPathIcon

Icon with progress-based drawing animation.

**Parameters:**

- `glyphPath: GlyphPath` - The extracted glyph path
- `progress: Float` - Animation progress 0f to 1f
- `modifier: Modifier` - Compose modifier
- `size: Dp` - Icon size (default: 24.dp)
- `tint: Color` - Icon color
- `strokeWidth: Float` - Stroke width for animation (default: 0.01f)

### PathIconAnimated

Auto-animated drawing effect with spring animation.

**Parameters:**

- `glyphPath: GlyphPath` - The extracted glyph path
- `animate: Boolean` - Whether to animate (default: true)
- `animationSpec: AnimationSpec<Float>` - Animation specification
- Other parameters same as AnimatedPathIcon

### MorphingPathIcon

Morph between two glyph paths (e.g., different axis values).

**Parameters:**

- `fromPath: GlyphPath` - Starting glyph path
- `toPath: GlyphPath` - Target glyph path
- `progress: Float` - Morphing progress 0f to 1f
- `modifier: Modifier` - Compose modifier
- `size: Dp` - Icon size (default: 24.dp)
- `tint: Color` - Icon color

### MorphingPathIconAnimated

Auto-animated morphing between two paths.

**Parameters:**

- `fromPath: GlyphPath` - Starting glyph path
- `toPath: GlyphPath` - Target glyph path
- `animate: Boolean` - Whether to animate
- `animationSpec: AnimationSpec<Float>` - Animation specification
- Other parameters same as MorphingPathIcon

## How It Works

1. Font data is loaded from resources or files
2. HarfBuzz extracts the glyph outline using the draw funcs API
3. Variable font variations are applied via `hb_font_set_variations()` before extraction
4. Path commands are normalized to 0-1 coordinate space
5. Commands are passed through JNI to Kotlin
6. Kotlin converts them to Compose `Path` objects
7. Canvas reuses Path objects with `rewind()` for efficient animation

## Variable Font Axes

The library supports any variable font axis. Common axes include:

- **FILL** (0-1): Outline to filled
- **wght** (100-900): Weight (thin to black)
- **wdth** (50-200): Width (condensed to extended)
- **opsz** (6-72): Optical size
- **slnt** (-15 to 0): Slant
- **GRAD** (-200 to 150): Grade
- **Custom axes**: Any 4-character tag defined in the font

## Path Data Format

Extracted paths include:
- **Commands**: MOVE_TO, LINE_TO, QUADRATIC_TO, CUBIC_TO, CLOSE
- **Coordinates**: Normalized (0-1 range based on font's units per EM)
- **Metrics**: Advance width/height for proper spacing
- **Variable data**: Extracted at specified axis values

## Performance Optimizations

- **Path Reuse**: Single `Path` object reused with `rewind()` during animation
- **No Allocations**: Zero new Path objects created during animation frames
- **Efficient Morphing**: Direct coordinate interpolation between paths
- **Lazy Extraction**: Paths extracted only when needed
- **Compose `remember`**: Prevents re-extraction on recomposition

## Native Dependencies

The library includes prebuilt native libraries for:
- `armeabi-v7a` (32-bit ARM)
- `arm64-v8a` (64-bit ARM)
- `x86` (32-bit Intel)
- `x86_64` (64-bit Intel)

Native libraries are built automatically via CMake when compiling the Android library.

## Requirements

- **Minimum SDK**: 24 (Android 7.0)
- **Compile SDK**: 36
- **HarfBuzz**: 10.0.1 (bundled)
- **Jetpack Compose**: Required for rendering

## Demo Application

See the demo app for complete examples of:

- Static path rendering
- Progressive drawing animation
- Variable font FILL axis morphing (outline ↔ filled)
- Multiple icons with different rendering modes

## License

MIT License - see [LICENSE](../../LICENSE) file.
