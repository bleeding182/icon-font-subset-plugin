package com.davidmedenjak.fontsubsetting.plugin

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.io.File

/**
 * Gradle task that generates Kotlin constants from a codepoints file.
 * Parses icon name to unicode mappings and creates a Kotlin object with const val declarations.
 */
abstract class GenerateIconConstantsTask : DefaultTask() {

    /**
     * Input codepoints file containing icon mappings
     */
    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val codepointsFile: RegularFileProperty

    /**
     * Package name for the generated Kotlin file
     */
    @get:Input
    abstract val packageName: Property<String>

    /**
     * Class name for the generated Kotlin object
     */
    @get:Input
    abstract val className: Property<String>

    @get:Input
    abstract val resourceName: Property<String>

    @get:Input
    abstract val fontFileName: Property<String>

    /**
     * Whether to generate internal constants
     */
    @get:Input
    abstract val generateInternal: Property<Boolean>

    /**
     * Output directory for generated Kotlin files
     */
    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    init {
        group = "fontSubsetting"
        description = "Generates Kotlin constants from font codepoints file"
    }

    @TaskAction
    fun generate() {
        val codepointsFile = codepointsFile.get().asFile
        val packageName = packageName.get()
        val className = className.get()
        val isInternal = generateInternal.get()

        if (!codepointsFile.exists()) {
            throw IllegalArgumentException("Codepoints file does not exist: ${codepointsFile.absolutePath}")
        }

        // Parse codepoints file
        val icons = parseCodepointsFile(codepointsFile)
        
        // Generate Kotlin code
        val kotlinCode = generateKotlinCode(
            packageName = packageName,
            className = className,
            icons = icons,
            isInternal = isInternal
        )

        // Write to output file
        val outputDir = outputDirectory.get().asFile
        val packageDir = File(outputDir, packageName.replace('.', '/'))
        packageDir.mkdirs()
        
        val outputFile = File(packageDir, "$className.kt")
        outputFile.writeText(kotlinCode)
        
        logger.info("Generated ${icons.size} icon constants")
    }

    private fun parseCodepointsFile(file: File): List<IconMapping> {
        val icons = mutableListOf<IconMapping>()
        
        file.forEachLine { line ->
            val trimmed = line.trim()
            if (trimmed.isNotEmpty() && !trimmed.startsWith("#")) {
                val parts = trimmed.split(' ', '\t')
                if (parts.size >= 2) {
                    val name = parts[0]
                    val codepoint = parts[1]
                    icons.add(IconMapping(name, codepoint))
                }
            }
        }
        
        return icons.sortedBy { it.name }
    }

    private fun generateKotlinCode(
        packageName: String,
        className: String,
        icons: List<IconMapping>,
        isInternal: Boolean
    ): String {
        val builder = StringBuilder()
        
        // Package declaration
        builder.appendLine("package $packageName")
        builder.appendLine()
        
        builder.appendLine("/**")
        builder.appendLine(" * Generated icon constants from font codepoints file.")
        builder.appendLine(" * This file is auto-generated. Do not modify manually.")
        builder.appendLine(" */")
        builder.appendLine("object $className {")
        
        // Generate constants
        val visibility = if (isInternal) "internal " else ""
        icons.forEach { icon ->
            val propertyName = convertToKotlinPropertyName(icon.name)
            val unicodeValue = convertToUnicodeEscape(icon.codepoint)
            
            // Add comment with original name if it differs
            if (propertyName != icon.name) {
                builder.appendLine("    /** Original name: ${icon.name} */")
            }
            
            builder.appendLine("    ${visibility}const val $propertyName = \"$unicodeValue\"")
        }
        
        builder.appendLine("}")
        
        return builder.toString()
    }

    private fun convertToKotlinPropertyName(name: String): String {
        // Handle special cases
        var result = name
        
        // Convert snake_case to camelCase
        result = result.split('_').mapIndexed { index, part ->
            if (index == 0) part else part.capitalize()
        }.joinToString("")
        
        // Handle names starting with numbers
        if (result.isNotEmpty() && result[0].isDigit()) {
            result = convertNumberPrefix(result)
        }
        
        // Ensure the result is a valid Kotlin identifier
        result = result.replace(Regex("[^a-zA-Z0-9_]"), "_")
        
        // Handle Kotlin keywords
        if (isKotlinKeyword(result)) {
            result = "`$result`"
        }
        
        return result
    }

    private fun isKotlinKeyword(word: String): Boolean {
        val keywords = setOf(
            "as", "break", "class", "continue", "do", "else", "false", "for", "fun",
            "if", "in", "interface", "is", "null", "object", "package", "return",
            "super", "this", "throw", "true", "try", "typealias", "typeof", "val",
            "var", "when", "while"
        )
        return word in keywords
    }

    private fun convertNumberPrefix(name: String): String {
        val numberPrefixes = mapOf(
            "10k" to "tenK", "10mp" to "tenMp", "11mp" to "elevenMp", "12mp" to "twelveMp",
            "123" to "oneTwoThree", "13mp" to "thirteenMp", "14mp" to "fourteenMp",
            "15mp" to "fifteenMp", "16mp" to "sixteenMp", "17mp" to "seventeenMp",
            "18mp" to "eighteenMp", "18_up" to "eighteenUp", "19mp" to "nineteenMp",
            "1k" to "oneK", "1x" to "oneX", "20mp" to "twentyMp", "21mp" to "twentyOneMp",
            "22mp" to "twentyTwoMp", "23mp" to "twentyThreeMp", "24mp" to "twentyFourMp",
            "24fps" to "twentyFourFps", "2d" to "twoD", "2k" to "twoK", "2mp" to "twoMp",
            "30fps" to "thirtyFps", "360" to "threeSixty", "3d" to "threeD", "3g" to "threeG",
            "3k" to "threeK", "3mp" to "threeMp", "3p" to "threeP", "4g" to "fourG",
            "4k" to "fourK", "4mp" to "fourMp", "50mp" to "fiftyMp", "5g" to "fiveG",
            "5k" to "fiveK", "5mp" to "fiveMp", "60fps" to "sixtyFps", "6k" to "sixK",
            "6mp" to "sixMp", "7k" to "sevenK", "7mp" to "sevenMp", "8k" to "eightK",
            "8mp" to "eightMp", "9k" to "nineK", "9mp" to "nineMp"
        )
        
        for ((prefix, replacement) in numberPrefixes) {
            if (name.startsWith(prefix)) {
                return replacement + name.substring(prefix.length)
            }
        }
        return "_$name" // Fallback for any other number-starting names
    }

    private fun convertToUnicodeEscape(codepoint: String): String {
        // Convert hex codepoint to Unicode escape sequence
        val hex = codepoint.uppercase()
        return "\\u${hex.padStart(4, '0').takeLast(4).uppercase()}"
    }

    data class IconMapping(
        val name: String,
        val codepoint: String
    )
}