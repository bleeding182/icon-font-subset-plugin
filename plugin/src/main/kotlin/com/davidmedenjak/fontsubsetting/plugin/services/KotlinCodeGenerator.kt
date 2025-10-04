package com.davidmedenjak.fontsubsetting.plugin.services

import com.davidmedenjak.fontsubsetting.plugin.providers.IconMapping

/**
 * Generates Kotlin code for icon constants.
 * Always generates an internal object with alphabetically sorted constants.
 */
object KotlinCodeGenerator {

    /**
     * Generates Kotlin code for the given icon mappings.
     */
    fun generate(
        packageName: String,
        className: String,
        mappings: List<IconMapping>
    ): String {
        val sortedMappings = mappings.sortedBy { it.name }

        return buildString {
            appendLine("package $packageName")
            appendLine()
            appendLine("/**")
            appendLine(" * Generated icon constants from font codepoints file.")
            appendLine(" * This file is auto-generated. Do not modify manually.")
            appendLine(" */")
            appendLine("object $className {")

            sortedMappings.forEach { icon ->
                val propertyName = KotlinNamingService.toPropertyName(icon.name)
                val unicodeValue = icon.toUnicodeEscape()

                // Add comment with original name if it differs
                if (propertyName != icon.name) {
                    appendLine("    /** Original name: ${icon.name} */")
                }

                appendLine("    internal const val $propertyName = \"$unicodeValue\"")
            }

            appendLine("}")
        }
    }
}