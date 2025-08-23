package com.davidmedenjak.fontsubsetting

import androidx.compose.animation.core.*
import androidx.compose.runtime.*
import androidx.compose.ui.text.font.FontFamily

@Composable
fun rememberAnimatedVariableFontFamily(
    targetConfig: VariableFontConfig,
    animationSpec: AnimationSpec<Float> = tween(300)
): FontFamily {
    val animatedFill by animateFloatAsState(
        targetValue = targetConfig.fill,
        animationSpec = animationSpec,
        label = "fill"
    )
    
    val animatedWeight by animateFloatAsState(
        targetValue = targetConfig.weight,
        animationSpec = animationSpec,
        label = "weight"
    )
    
    val animatedGrade by animateFloatAsState(
        targetValue = targetConfig.grade,
        animationSpec = animationSpec,
        label = "grade"
    )
    
    val animatedOpticalSize by animateFloatAsState(
        targetValue = targetConfig.opticalSize,
        animationSpec = animationSpec,
        label = "opticalSize"
    )
    
    val animatedConfig = remember(animatedFill, animatedWeight, animatedGrade, animatedOpticalSize) {
        VariableFontConfig(
            fill = animatedFill,
            weight = animatedWeight,
            grade = animatedGrade,
            opticalSize = animatedOpticalSize
        )
    }
    
    return rememberVariableFontFamily(animatedConfig)
}

@Composable
fun AnimatedFontShowcase(): VariableFontConfig {
    var isAnimating by remember { mutableStateOf(false) }
    
    val infiniteTransition = rememberInfiniteTransition(label = "fontAnimation")
    
    val animatedFill = if (isAnimating) {
        infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(2000, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "fill"
        ).value
    } else {
        0.5f
    }
    
    val animatedWeight = if (isAnimating) {
        infiniteTransition.animateFloat(
            initialValue = 100f,
            targetValue = 700f,
            animationSpec = infiniteRepeatable(
                animation = tween(3000, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "weight"
        ).value
    } else {
        400f
    }
    
    val config = VariableFontConfig(
        fill = animatedFill,
        weight = animatedWeight,
        grade = 0f,
        opticalSize = 48f
    )
    
    return config
}