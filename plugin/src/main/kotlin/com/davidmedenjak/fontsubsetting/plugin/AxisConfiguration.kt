package com.davidmedenjak.fontsubsetting.plugin

import org.gradle.api.Named
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional

interface AxisConfiguration : Named {

    @get:Input
    @get:Optional
    val remove: Property<Boolean>

    @get:Input
    @get:Optional
    val minValue: Property<Float>

    @get:Input
    @get:Optional
    val maxValue: Property<Float>

    @get:Input
    @get:Optional
    val defaultValue: Property<Float>

    fun range(min: Float, max: Float, default: Float? = null) {
        remove.set(false)
        minValue.set(min)
        maxValue.set(max)
        if (default != null) {
            defaultValue.set(default)
        }
    }
    
    fun remove() {
        remove.set(true)
    }
}