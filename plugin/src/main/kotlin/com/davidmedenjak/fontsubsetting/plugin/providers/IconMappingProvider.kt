package com.davidmedenjak.fontsubsetting.plugin.providers

internal data class IconMapping(
    val name: String,
    val codepoint: String
) {
    fun toUnicodeEscape(): String {
        val hex = codepoint.uppercase()
        return "\\u${hex.padStart(4, '0').takeLast(4)}"
    }
}