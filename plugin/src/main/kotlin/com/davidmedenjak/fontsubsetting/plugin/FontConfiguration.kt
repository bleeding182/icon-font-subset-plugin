package com.davidmedenjak.fontsubsetting.plugin

import org.gradle.api.Action
import org.gradle.api.Named
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import javax.inject.Inject

abstract class FontConfiguration @Inject constructor(
    private val name: String,
    private val objectFactory: ObjectFactory
) : Named {
    
    override fun getName(): String = name
    
    abstract val fontFile: RegularFileProperty

    /** Format: "icon_name codepoint" per line (e.g., "home e9b2") */
    abstract val codepointsFile: RegularFileProperty

    /** Fully qualified class name for the generated Kotlin object. */
    abstract val className: Property<String>

    /** Should match the font file name without extension in res/font/. */
    abstract val resourceName: Property<String>

    abstract val stripHinting: Property<Boolean>

    abstract val stripGlyphNames: Property<Boolean>

    val axes: NamedDomainObjectContainer<AxisConfiguration> =
        objectFactory.domainObjectContainer(AxisConfiguration::class.java)
    
    fun axes(action: Action<NamedDomainObjectContainer<AxisConfiguration>>) {
        action.execute(axes)
    }
    
    fun axis(tag: String): AxisConfiguration {
        return axes.maybeCreate(tag)
    }

}