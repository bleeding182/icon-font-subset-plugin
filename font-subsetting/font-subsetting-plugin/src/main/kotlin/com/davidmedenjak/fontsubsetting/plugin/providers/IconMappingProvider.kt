package com.davidmedenjak.fontsubsetting.plugin.providers

/**
 * Provides icon name to codepoint mappings from various sources.
 * This abstraction allows different fonts to supply their mappings in different ways.
 */
interface IconMappingProvider {
    /**
     * Provides the list of icon mappings.
     */
    fun provideMappings(): List<IconMapping>
}

/**
 * Represents a mapping from an icon name to its Unicode codepoint.
 */
data class IconMapping(
    val name: String,
    val codepoint: String
) {
    /**
     * Returns the codepoint as a Unicode escape sequence.
     */
    fun toUnicodeEscape(): String {
        val hex = codepoint.uppercase()
        return "\\u${hex.padStart(4, '0').takeLast(4)}"
    }
}