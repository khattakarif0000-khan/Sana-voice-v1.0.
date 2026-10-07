package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.model.ConversationState
import com.example.ui.theme.SanaNeonBlue
import com.example.ui.theme.SanaNeonCyan
import com.example.ui.theme.SanaNeonPink
import com.example.ui.theme.SanaNeonPurple
import com.example.ui.theme.SanaNeonRed
import com.example.ui.theme.SanaWarningAmber
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun SanaOrb(
    state: ConversationState,
    audioLevel: Float,
    modifier: Modifier = Modifier,
    orbSize: Dp = 220.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "orb_transition")

    // Rotation angle
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 6000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    // Breathing pulse
    val breathing by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathing"
    )

    // Primary & Secondary color transitions based on state
    val primaryColor by animateColorAsState(
        targetValue = when (state) {
            ConversationState.LISTENING -> SanaNeonCyan
            ConversationState.PROCESSING -> SanaNeonRed
            ConversationState.SPEAKING -> SanaNeonPink
            ConversationState.INTERRUPTED -> SanaNeonPurple
            ConversationState.RECOVERING -> SanaWarningAmber
            ConversationState.ERROR -> SanaNeonRed
            ConversationState.STOPPED, ConversationState.IDLE -> SanaNeonCyan
        },
        animationSpec = tween(durationMillis = 400),
        label = "primary_color"
    )

    val secondaryColor by animateColorAsState(
        targetValue = when (state) {
            ConversationState.LISTENING -> SanaNeonBlue
            ConversationState.PROCESSING -> SanaNeonPurple
            ConversationState.SPEAKING -> SanaNeonPink.copy(alpha = 0.8f)
            ConversationState.INTERRUPTED -> SanaNeonCyan
            ConversationState.RECOVERING -> SanaNeonRed
            ConversationState.ERROR -> Color(0xFFB71C1C)
            ConversationState.STOPPED, ConversationState.IDLE -> SanaNeonPink
        },
        animationSpec = tween(durationMillis = 400),
        label = "secondary_color"
    )

    Box(
        modifier = modifier.size(orbSize),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val baseRadius = (size.width / 2f) * 0.55f

            // Audio reaction factor
            val dynamicScale = if (state == ConversationState.LISTENING || state == ConversationState.SPEAKING) {
                1.0f + (audioLevel * 0.45f)
            } else if (state == ConversationState.PROCESSING) {
                1.0f + (sin(rotation * PI.toFloat() / 180f) * 0.1f)
            } else {
                breathing
            }

            val currentRadius = baseRadius * dynamicScale

            // 1. Outer Glow Aura
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        primaryColor.copy(alpha = 0.35f),
                        secondaryColor.copy(alpha = 0.15f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = currentRadius * 1.55f
                ),
                radius = currentRadius * 1.55f,
                center = center
            )

            // 2. Outer Rotating Cyber Ring
            val ringRadius = currentRadius * 1.25f
            drawCircle(
                color = primaryColor.copy(alpha = 0.6f),
                radius = ringRadius,
                center = center,
                style = Stroke(
                    width = 2.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(24f, 16f, 8f, 16f), rotation * 2f)
                )
            )

            // 3. Counter-rotating Secondary Ring
            drawCircle(
                color = secondaryColor.copy(alpha = 0.45f),
                radius = ringRadius * 0.92f,
                center = center,
                style = Stroke(
                    width = 1.5.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 20f), -rotation * 3f)
                )
            )

            // 4. Inner Core Sphere with Rich Radial Gradient
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.9f),
                        primaryColor,
                        secondaryColor,
                        Color(0xFF07080B)
                    ),
                    center = Offset(center.x - (currentRadius * 0.2f), center.y - (currentRadius * 0.2f)),
                    radius = currentRadius
                ),
                radius = currentRadius,
                center = center
            )

            // 5. Sound wave ripples during Listening / Speaking
            if (state == ConversationState.LISTENING || state == ConversationState.SPEAKING) {
                val rippleCount = 3
                for (i in 1..rippleCount) {
                    val rippleRadius = currentRadius + (i * 18.dp.toPx() * (audioLevel.coerceAtLeast(0.15f)))
                    drawCircle(
                        color = primaryColor.copy(alpha = (0.4f / i)),
                        radius = rippleRadius,
                        center = center,
                        style = Stroke(width = 1.5.dp.toPx())
                    )
                }
            }
        }
    }
}
