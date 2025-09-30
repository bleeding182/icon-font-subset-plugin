package com.davidmedenjak.fontsubsetting.plugin.providers

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