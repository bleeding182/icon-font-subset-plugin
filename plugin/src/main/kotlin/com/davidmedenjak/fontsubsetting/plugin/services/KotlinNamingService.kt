package com.davidmedenjak.fontsubsetting.plugin.services

/**
 * Converts icon names to valid Kotlin property names.
 */
object KotlinNamingService {

    private val KOTLIN_KEYWORDS = setOf(
        "as", "break", "class", "continue", "do", "else", "false", "for", "fun",
        "if", "in", "interface", "is", "null", "object", "package", "return",
        "super", "this", "throw", "true", "try", "typealias", "typeof", "val",
        "var", "when", "while"
    )

    private val NUMBER_PREFIXES = mapOf(
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

    /**
     * Converts an icon name to a valid Kotlin property name.
     * Always succeeds - falls back to underscore prefix if needed.
     */
    fun toPropertyName(name: String): String {
        if (name.isEmpty()) return "_empty"

        var result = name

        // Handle known number prefixes
        if (result[0].isDigit()) {
            for ((prefix, replacement) in NUMBER_PREFIXES) {
                if (result.startsWith(prefix)) {
                    result = replacement + result.substring(prefix.length)
                        .replaceFirstChar { if (it == '_') "" else it.toString() }
                    break
                }
            }
        }

        // Convert snake_case to camelCase
        result = result.split('_').mapIndexed { index, part ->
            if (index == 0) part else part.replaceFirstChar { it.uppercase() }
        }.joinToString("")

        // Replace invalid characters with underscore
        result = result.replace(Regex("[^a-zA-Z0-9_]"), "_")

        // If still starts with digit after conversion, prefix with underscore
        if (result.isNotEmpty() && result[0].isDigit()) {
            result = "_$result"
        }

        // Handle Kotlin keywords
        if (result in KOTLIN_KEYWORDS) {
            result = "`$result`"
        }

        // Final fallback - if result is empty or invalid, use underscore
        if (result.isEmpty() || result == "_") {
            result = "_icon"
        }

        return result
    }
}