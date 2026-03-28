package com.davidmedenjak.fontsubsetting.analyzer

internal data class IconUsageResult(
    val usedIcons: Set<String>,
    val analyzedFiles: Int = 0,
    val errors: List<Pair<String, String>> = emptyList()
) {

    fun writeToFile(file: java.io.File) {
        file.parentFile?.mkdirs()
        file.writeText(usedIcons.sorted().joinToString("\n"))
    }

    companion object {
        fun readFromFile(file: java.io.File): IconUsageResult {
            if (!file.exists()) {
                return IconUsageResult(emptySet())
            }

            val icons = file.readLines()
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .toSet()

            return IconUsageResult(icons)
        }
    }
}