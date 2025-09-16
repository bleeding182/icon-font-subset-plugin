package com.davidmedenjak.fontsubsetting.plugin

import org.gradle.api.Action
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.model.ObjectFactory
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
     * Output directory for all subsetted fonts.
     * This directory will contain res/font/ structure with all processed fonts.
     */
    abstract val outputDirectory: DirectoryProperty

    /**
     * Configure fonts using a DSL.
     */
    fun fonts(action: Action<NamedDomainObjectContainer<FontConfiguration>>) {
        action.execute(fonts)
    }
}