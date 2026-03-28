package com.davidmedenjak.fontsubsetting.plugin.services

import com.davidmedenjak.fontsubsetting.plugin.providers.IconMapping

internal object KotlinCodeGenerator {

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
            appendLine("internal object $className {")

            sortedMappings.forEach { icon ->
                val propertyName = KotlinNamingService.toPropertyName(icon.name)
                val unicodeValue = icon.toUnicodeEscape()

                if (propertyName != icon.name) {
                    appendLine("    /** Original name: ${icon.name} */")
                }

                appendLine("    const val $propertyName = \"$unicodeValue\"")
            }

            appendLine("}")
        }
    }
}