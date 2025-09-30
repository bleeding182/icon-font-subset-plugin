package com.davidmedenjak.fontsubsetting.plugin

import org.gradle.api.Action
import org.gradle.api.Named
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import javax.inject.Inject

/**
 * Configuration for a single font that can be subset.
 * This includes the codepoints file and metadata for code generation.
 */
abstract class FontConfiguration @Inject constructor(
    private val name: String,
    private val objectFactory: ObjectFactory
) : Named {
    
    override fun getName(): String = name
    
    /**
     * Path to the actual font file (TTF/OTF) to be subset
     * This is the source font file that will be processed
     */
    abstract val fontFile: RegularFileProperty
    
    /**
     * Path to the codepoints file containing icon name to unicode mappings
     * Format: "icon_name codepoint" per line (e.g., "home e9b2")
     */
    abstract val codepointsFile: RegularFileProperty

    /**
     * Fully qualified class name for the generated Kotlin object
     * (e.g., "com.davidmedenjak.fontsubsetting.MaterialSymbols")
     */
    abstract val className: Property<String>

    /**
     * Resource name used in @Font annotation
     * This should match the font file name without extension in res/font/
     * (e.g., "symbols" for symbols.ttf)
     */
    abstract val resourceName: Property<String>

    /**
     * Whether to strip hinting instructions from the font
     * Hinting can be 15-20% of font size and is unnecessary for icon fonts on high-DPI screens
     * Default: true
     */
    abstract val stripHinting: Property<Boolean>

    /**
     * Whether to strip glyph names from the font
     * Glyph names can be 5-10% of font size and are unnecessary for icon fonts
     * Default: true
     */
    abstract val stripGlyphNames: Property<Boolean>

    /**
     * Container for axis configurations for variable fonts.
     * Allows configuring which axes to keep, remove, or constrain.
     */
    val axes: NamedDomainObjectContainer<AxisConfiguration> =
        objectFactory.domainObjectContainer(AxisConfiguration::class.java)
    
    /**
     * Configure axes using a DSL.
     * Example:
     * ```
     * axes {
     *     axis("wght").range(400f, 700f, 400f)
     *     axis("FILL").remove()
     * }
     * ```
     */
    fun axes(action: Action<NamedDomainObjectContainer<AxisConfiguration>>) {
        action.execute(axes)
    }
    
    /**
     * Convenience method to configure a single axis.
     * @param tag The axis tag (e.g., "wght", "FILL", "GRAD", "opsz")
     * @return The axis configuration for further customization
     */
    fun axis(tag: String): AxisConfiguration {
        return axes.maybeCreate(tag)
    }

}