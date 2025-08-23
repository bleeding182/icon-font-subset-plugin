package com.davidmedenjak.fontsubsetting.plugin

import org.gradle.api.Named
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional

/**
 * Configuration for a single variable font axis.
 * Allows either removing the axis completely or constraining its range.
 */
interface AxisConfiguration : Named {
    
    /**
     * Whether to remove this axis completely from the font.
     * When true, the axis will be removed entirely.
     * When false, the axis range can be constrained using minValue/maxValue/defaultValue.
     */
    @get:Input
    @get:Optional
    val remove: Property<Boolean>
    
    /**
     * Minimum value for this axis range.
     * Only used when remove is false.
     */
    @get:Input
    @get:Optional
    val minValue: Property<Float>
    
    /**
     * Maximum value for this axis range.
     * Only used when remove is false.
     */
    @get:Input
    @get:Optional
    val maxValue: Property<Float>
    
    /**
     * Default value for this axis.
     * Only used when remove is false.
     * If not specified, will be clamped to the min/max range.
     */
    @get:Input
    @get:Optional
    val defaultValue: Property<Float>
    
    /**
     * Convenience method to set the axis range.
     * @param min Minimum value for the axis
     * @param max Maximum value for the axis
     * @param default Default value for the axis (optional)
     */
    fun range(min: Float, max: Float, default: Float? = null) {
        remove.set(false)
        minValue.set(min)
        maxValue.set(max)
        if (default != null) {
            defaultValue.set(default)
        } else {
            // If no default specified, use the midpoint
            defaultValue.set((min + max) / 2f)
        }
    }
    
    /**
     * Convenience method to mark this axis for removal.
     */
    fun remove() {
        remove.set(true)
    }
}