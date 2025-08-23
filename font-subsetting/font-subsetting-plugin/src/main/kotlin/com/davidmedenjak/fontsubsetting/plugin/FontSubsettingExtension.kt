package com.davidmedenjak.fontsubsetting.plugin

import org.gradle.api.Action
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import javax.inject.Inject

/**
 * Extension for configuring the Font Subsetting plugin.
 */
abstract class FontSubsettingExtension @Inject constructor(
    private val objectFactory: ObjectFactory
) {
    
    /**
     * Container for font configurations.
     * Allows defining multiple fonts with their codepoints files for code generation.
     */
    val fonts: NamedDomainObjectContainer<FontConfiguration> = objectFactory.domainObjectContainer(
        FontConfiguration::class.java
    )
    
    /**
     * Configure fonts using a DSL.
     */
    fun fonts(action: Action<NamedDomainObjectContainer<FontConfiguration>>) {
        action.execute(fonts)
    }
    
    fun setDefaults() {
        // No defaults needed currently
    }
}