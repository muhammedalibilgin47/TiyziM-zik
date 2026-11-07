package com.tiyzi.muzik.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp

/** Liquid Glass görünümü açık mı? */
val LocalGlass = staticCompositionLocalOf { false }

private val SolidColors = darkColorScheme(
    primary = Color(0xFFB794FF),
    onPrimary = Color(0xFF22005D),
    primaryContainer = Color(0xFF4A2A99),
    onPrimaryContainer = Color(0xFFEADDFF),
    secondaryContainer = Color(0xFF3A3150),
    background = Color(0xFF101014),
    surface = Color(0xFF101014),
    surfaceContainer = Color(0xFF1A1A21),
    surfaceContainerHigh = Color(0xFF22222B),
    surfaceContainerHighest = Color(0xFF2C2C36),
    surfaceContainerLow = Color(0xFF16161C),
)

private val GlassColors = darkColorScheme(
    primary = Color(0xFFD4C2FF),
    onPrimary = Color(0xFF22005D),
    primaryContainer = Color(0x664A2A99),
    onPrimaryContainer = Color(0xFFF1E8FF),
    secondaryContainer = Color(0x55FFFFFF),
    onSecondaryContainer = Color.White,
    background = Color.Transparent,
    surface = Color.Transparent,
    surfaceVariant = Color(0x1FFFFFFF),
    onSurfaceVariant = Color(0xFFD0CCDA),
    surfaceContainerLowest = Color(0x14FFFFFF),
    surfaceContainerLow = Color(0xF0151220),   // alt pencereler
    surfaceContainer = Color(0xEB1A1628),      // menüler
    surfaceContainerHigh = Color(0xE61E192E),  // diyaloglar
    surfaceContainerHighest = Color(0x24FFFFFF), // kartlar, kapak arka planı
    outline = Color(0x55FFFFFF),
    outlineVariant = Color(0x26FFFFFF),
)

@Composable
fun TiyziTheme(glass: Boolean = false, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalGlass provides glass) {
        MaterialTheme(colorScheme = if (glass) GlassColors else SolidColors, content = content)
    }
}

/** Cam efekti: yarı saydam dolgu + ışık yansıyan ince kenar */
fun Modifier.glass(shape: Shape = RoundedCornerShape(20.dp), strong: Boolean = false): Modifier = this
    .clip(shape)
    .background(
        Brush.verticalGradient(
            listOf(
                Color.White.copy(alpha = if (strong) 0.22f else 0.15f),
                Color.White.copy(alpha = if (strong) 0.10f else 0.05f),
            )
        )
    )
    .border(
        1.dp,
        Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.50f), Color.White.copy(alpha = 0.06f))),
        shape,
    )

/** Yavaşça hareket eden renkli "sıvı" arka plan */
@Composable
fun LiquidBackground(modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "liquid")
    val a by t.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(16_000, easing = LinearEasing), RepeatMode.Reverse),
        label = "a",
    )
    val b by t.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(23_000, easing = LinearEasing), RepeatMode.Reverse),
        label = "b",
    )
    Box(
        modifier
            .fillMaxSize()
            .background(Color(0xFF0A0812))
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val r = maxOf(w, h)
            blob(Color(0xB07C4DFF), Offset(w * (0.10f + 0.55f * a), h * (0.15f + 0.20f * b)), r * 0.60f)
            blob(Color(0x99FF4D9D), Offset(w * (0.95f - 0.55f * b), h * (0.45f + 0.15f * a)), r * 0.50f)
            blob(Color(0x8C00C2FF), Offset(w * (0.25f + 0.45f * b), h * (0.88f - 0.20f * a)), r * 0.55f)
            blob(Color(0x66FFB84D), Offset(w * (0.80f - 0.35f * a), h * (0.08f + 0.15f * b)), r * 0.35f)
        }
        // Yazıların okunması için hafif karartma
        Box(
            Modifier
                .fillMaxSize()
                .background(Color(0x66000000))
        )
    }
}

private fun DrawScope.blob(color: Color, center: Offset, radius: Float) {
    drawCircle(
        brush = Brush.radialGradient(listOf(color, color.copy(alpha = 0f)), center = center, radius = radius),
        radius = radius,
        center = center,
    )
}
