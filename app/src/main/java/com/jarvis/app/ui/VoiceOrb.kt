package com.jarvis.app.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import com.jarvis.app.core.JarvisState
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Liquid orb. IDLE breathes slowly, LISTENING ripples with your voice,
 * THINKING swirls, SPEAKING pulses.
 */
@Composable
fun VoiceOrb(state: JarvisState, level: Float, active: Boolean, modifier: Modifier = Modifier) {
    val t by rememberInfiniteTransition(label = "orb").animateFloat(
        initialValue = 0f, targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(6000, easing = LinearEasing)), label = "t"
    )
    val smoothLevel by animateFloatAsState(level, tween(120), label = "level")
    val core by animateColorAsState(
        when (state) {
            JarvisState.IDLE -> if (active) JarvisColors.Saffron else JarvisColors.Muted
            JarvisState.LISTENING -> JarvisColors.SoftGold
            JarvisState.THINKING -> Color(0xFFFF7A1A)
            JarvisState.SPEAKING -> JarvisColors.Saffron
        }, tween(400), label = "core"
    )
    val wobble = when (state) {
        JarvisState.IDLE -> 0.03f
        JarvisState.LISTENING -> 0.05f + 0.18f * smoothLevel
        JarvisState.THINKING -> 0.08f
        JarvisState.SPEAKING -> 0.07f + 0.06f * sin(t * 9f).let { it * it }
    }
    val speed = when (state) {
        JarvisState.IDLE -> 1f
        JarvisState.LISTENING -> 3f
        JarvisState.THINKING -> 6f
        JarvisState.SPEAKING -> 4f
    }
    Canvas(modifier) {
        val c = Offset(size.width / 2, size.height / 2)
        val base = size.minDimension * 0.33f * (1f + 0.03f * sin(t * 2f))
        // Soft glow behind the orb
        drawCircle(
            Brush.radialGradient(listOf(core.copy(alpha = 0.35f), Color.Transparent), c, base * 1.7f),
            radius = base * 1.7f, center = c
        )
        // Two blob layers with different wave patterns give the liquid look.
        for (layer in 0..1) {
            val path = Path()
            val steps = 72
            for (i in 0..steps) {
                val a = (i.toFloat() / steps) * 2f * PI.toFloat()
                val w = sin(a * 3f + t * speed + layer) * 0.6f + sin(a * 5f - t * speed * 0.7f + layer * 2) * 0.4f
                val r = base * (1f - layer * 0.12f) * (1f + wobble * w)
                val x = c.x + r * cos(a)
                val y = c.y + r * sin(a)
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            path.close()
            drawPath(
                path,
                Brush.radialGradient(
                    listOf(Color.White.copy(alpha = 0.55f - layer * 0.25f), core, core.copy(alpha = 0.5f)),
                    center = Offset(c.x - base * 0.3f, c.y - base * 0.35f),
                    radius = base * 1.6f
                ),
                alpha = if (layer == 0) 0.95f else 0.6f
            )
        }
        if (state == JarvisState.THINKING) {
            val a = t * 3f
            drawArc(
                core, startAngle = Math.toDegrees(a.toDouble()).toFloat(), sweepAngle = 80f, useCenter = false,
                topLeft = Offset(c.x - base * 1.25f, c.y - base * 1.25f),
                size = androidx.compose.ui.geometry.Size(base * 2.5f, base * 2.5f),
                style = Stroke(width = 6f)
            )
        }
    }
}
