package com.davidmedenjak.fontsubsetting.analyzer

/**
 * Result of icon usage analysis.
 *
 * @property usedIcons Set of icon names that were found to be used
 * @property analyzedFiles Number of files that were successfully analyzed
 * @property errors List of errors encountered during analysis (file path to error message)
 */
data class IconUsageResult(
    val usedIcons: Set<String>,
    val analyzedFiles: Int = 0,
    val errors: List<Pair<String, String>> = emptyList()
) {

    /**
     * Writes the used icons to a file in a simple text format (one icon per line).
     */
    fun writeToFile(file: java.io.File) {
        file.parentFile?.mkdirs()
        file.writeText(usedIcons.sorted().joinToString("\n"))
    }

    companion object {
        /**
         * Reads icon usage from a file in simple text format (one icon per line).
         */
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