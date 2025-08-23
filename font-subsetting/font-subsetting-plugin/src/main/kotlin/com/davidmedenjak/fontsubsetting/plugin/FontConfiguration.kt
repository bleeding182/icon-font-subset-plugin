package com.davidmedenjak.fontsubsetting.plugin

import org.gradle.api.Action
import org.gradle.api.Named
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import javax.inject.Inject
import java.io.File

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
     * Package name for the generated Kotlin file
     * (e.g., "com.davidmedenjak.fontsubsetting")
     */
    abstract val packageName: Property<String>

    /**
     * Class name for the generated Kotlin object
     * (e.g., "MaterialSymbols")
     */
    abstract val className: Property<String>

    /**
     * Resource name used in @Font annotation
     * This should match the font file name without extension in res/font/
     * (e.g., "symbols" for symbols.ttf)
     */
    abstract val resourceName: Property<String>

    /**
     * Font file name used in @Font annotation
     * This is the actual file name in res/font/ directory
     * (e.g., "symbols.ttf")
     */
    abstract val fontFileName: Property<String>

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
    
    /**
     * Get the default resource name based on the font file name.
     * Sanitizes the font file name to be a valid Android resource name.
     */
    fun getDefaultResourceName(): Provider<String> {
        return fontFile.map { file ->
            sanitizeForResourceName(file.asFile.nameWithoutExtension)
        }
    }
    
    /**
     * Get the default font file name based on the original font file.
     */
    fun getDefaultFontFileName(): Provider<String> {
        return fontFile.map { file ->
            file.asFile.name
        }
    }
    
    /**
     * Validates that all required properties are set.
     * @throws IllegalStateException if validation fails
     */
    fun validate() {
        if (!fontFile.isPresent) {
            throw IllegalStateException("Font file not configured for font '$name'")
        }
        if (!fontFile.get().asFile.exists()) {
            throw IllegalStateException("Font file does not exist: ${fontFile.get().asFile.absolutePath}")
        }
        if (!codepointsFile.isPresent) {
            throw IllegalStateException("Codepoints file not configured for font '$name'")
        }
        if (!codepointsFile.get().asFile.exists()) {
            throw IllegalStateException("Codepoints file does not exist: ${codepointsFile.get().asFile.absolutePath}")
        }
        if (!packageName.isPresent) {
            throw IllegalStateException("Package name not configured for font '$name'")
        }
        if (!className.isPresent) {
            throw IllegalStateException("Class name not configured for font '$name'")
        }
    }
    
    companion object {
        /**
         * Sanitizes a string to be a valid Android resource name.
         */
        fun sanitizeForResourceName(input: String): String {
            return input
                .replace(Regex(Constants.Defaults.INVALID_CHAR_REGEX), "_")
                .lowercase()
                .replace(Regex(Constants.Defaults.MULTIPLE_UNDERSCORE_REGEX), "_")
                .trim('_')
        }
    }
}