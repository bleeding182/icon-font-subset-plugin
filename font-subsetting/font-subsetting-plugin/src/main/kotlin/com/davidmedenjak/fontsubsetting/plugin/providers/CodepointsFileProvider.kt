package com.davidmedenjak.fontsubsetting.plugin.providers

import java.io.File

/**
 * Provides icon mappings from a codepoints file.
 * The file format is: "icon_name codepoint" per line (e.g., "home e88a")
 */
class CodepointsFileProvider(
    private val codepointsFile: File
) : IconMappingProvider {

    override fun provideMappings(): List<IconMapping> {
        if (!codepointsFile.exists()) {
            return emptyList()
        }

        return codepointsFile.readLines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .mapNotNull { line ->
                val parts = line.split(' ', '\t')
                if (parts.size >= 2) {
                    IconMapping(parts[0], parts[1])
                } else null
            }
            .sortedBy { it.name }
    }
}