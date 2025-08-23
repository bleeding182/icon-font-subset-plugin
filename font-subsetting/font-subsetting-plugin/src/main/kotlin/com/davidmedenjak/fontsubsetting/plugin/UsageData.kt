package com.davidmedenjak.fontsubsetting.plugin

/**
 * Data class representing the usage data from the icon analysis task.
 */
data class UsageData(
    val usedIcons: List<String> = emptyList()
)