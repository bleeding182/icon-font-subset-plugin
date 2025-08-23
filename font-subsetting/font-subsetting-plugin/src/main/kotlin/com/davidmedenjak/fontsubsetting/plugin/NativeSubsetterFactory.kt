package com.davidmedenjak.fontsubsetting.plugin

import com.davidmedenjak.fontsubsetting.native.HarfBuzzSubsetter
import com.davidmedenjak.fontsubsetting.native.NativeLogger
import org.gradle.api.GradleException
import org.gradle.api.logging.Logger

/**
 * Factory for creating and managing HarfBuzzSubsetter instances.
 */
class NativeSubsetterFactory(private val logger: Logger) {
    
    private var subsetter: HarfBuzzSubsetter? = null
    
    /**
     * Gets or creates a HarfBuzzSubsetter instance with proper logging.
     */
    fun getSubsetter(): HarfBuzzSubsetter {
        if (subsetter == null) {
            subsetter = createSubsetter()
        }
        return subsetter!!
    }
    
    private fun createSubsetter(): HarfBuzzSubsetter {
        return try {
            val nativeLogger = createNativeLogger()
            HarfBuzzSubsetter(nativeLogger)
        } catch (e: UnsatisfiedLinkError) {
            logger.error(Constants.LogMessages.NATIVE_LIBRARY_ERROR, e)
            throw GradleException(Constants.LogMessages.NATIVE_LIBRARY_ERROR, e)
        }
    }
    
    private fun createNativeLogger(): NativeLogger {
        return object : NativeLogger {
            override fun log(level: Int, message: String) {
                val prefixedMessage = "${Constants.LogMessages.NATIVE_LOG_PREFIX} $message"
                when (level) {
                    HarfBuzzSubsetter.LOG_DEBUG -> logger.debug(prefixedMessage)
                    HarfBuzzSubsetter.LOG_INFO -> logger.info(prefixedMessage)
                    HarfBuzzSubsetter.LOG_WARN -> logger.warn(prefixedMessage)
                    HarfBuzzSubsetter.LOG_ERROR -> logger.error(prefixedMessage)
                }
            }
        }
    }
}