package com.davidmedenjak.fontsubsetting

import android.content.Context
import androidx.annotation.FontRes
import kotlin.math.ln
import kotlin.math.pow

object FontSizeUtil {
    fun getFontResourceSize(context: Context, @FontRes fontResId: Int): Long {
        return try {
            context.resources.openRawResource(fontResId).use { inputStream ->
                inputStream.available().toLong()
            }
        } catch (e: Exception) {
            -1L
        }
    }
    
    fun formatFileSize(sizeInBytes: Long): String {
        if (sizeInBytes <= 0) return "N/A"
        
        val units = arrayOf("B", "KB", "MB", "GB")
        val digitGroups = (ln(sizeInBytes.toDouble()) / ln(1024.0)).toInt()
        
        return String.format(
            "%.1f %s",
            sizeInBytes / 1024.0.pow(digitGroups.toDouble()),
            units[digitGroups]
        )
    }
    
    fun calculateReductionPercentage(originalSize: Long, reducedSize: Long): Float {
        if (originalSize <= 0 || reducedSize < 0) return 0f
        return ((originalSize - reducedSize).toFloat() / originalSize) * 100
    }
}