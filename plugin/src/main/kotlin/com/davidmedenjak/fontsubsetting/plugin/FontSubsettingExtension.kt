package com.davidmedenjak.fontsubsetting.plugin

import org.gradle.api.Action
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.model.ObjectFactory
import javax.inject.Inject

abstract class FontSubsettingExtension @Inject constructor(
    private val objectFactory: ObjectFactory
) {

    val fonts: NamedDomainObjectContainer<FontConfiguration> = objectFactory.domainObjectContainer(
        FontConfiguration::class.java
    )

    abstract val outputDirectory: DirectoryProperty

    fun fonts(action: Action<NamedDomainObjectContainer<FontConfiguration>>) {
        action.execute(fonts)
    }
}