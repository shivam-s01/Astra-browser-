package com.astra.browser.ui.newtab

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.ContentScale
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/** Ids stored in SettingsStore.wallpaperMode. */
object WallpaperModes {
    const val NIGHT_SKY = "NIGHT_SKY"
    const val AURORA = "AURORA"
    const val SUNSET = "SUNSET"
    const val OCEAN = "OCEAN"
    const val EMBER = "EMBER"
    const val MIDNIGHT = "MIDNIGHT"
    const val CUSTOM = "CUSTOM"

    /** Built-in wallpapers in picker order (label shown under each tile). */
    val builtIns: List<Pair<String, String>> = listOf(
        NIGHT_SKY to "Night sky",
        AURORA to "Aurora",
        SUNSET to "Sunset",
        OCEAN to "Ocean",
        EMBER to "Ember",
        MIDNIGHT to "Midnight"
    )

    fun label(mode: String): String = when (mode) {
        CUSTOM -> "Your photo"
        else -> builtIns.firstOrNull { it.first == mode }?.second ?: "Night sky"
    }
}

/**
 * The single place that paints the home wallpaper. Used by the new-tab page
 * and by the settings preview/thumbnails so they always match exactly.
 */
@Composable
fun HomeWallpaper(
    mode: String,
    custom: ImageBitmap?,
    dim: Float,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        when (mode) {
            WallpaperModes.CUSTOM -> {
                // Crossfade hides the brief decode delay on cold start:
                // plain dark first, then the photo fades in.
                Crossfade(
                    targetState = custom,
                    animationSpec = tween(400),
                    label = "wallpaper",
                    modifier = Modifier.fillMaxSize()
                ) { bmp ->
                    if (bmp != null) {
                        Image(
                            bitmap = bmp,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Box(Modifier.fillMaxSize().background(Color(0xFF0A0F14)))
                    }
                }
                // Gentle top/bottom scrims so the toolbars and shortcuts stay
                // readable on bright photos.
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.verticalGradient(
                            0f to Color.Black.copy(alpha = 0.22f),
                            0.28f to Color.Transparent,
                            0.72f to Color.Transparent,
                            1f to Color.Black.copy(alpha = 0.38f)
                        )
                    )
                )
            }
            WallpaperModes.NIGHT_SKY -> NightSkyWallpaper(Modifier.fillMaxSize())
            else -> PresetBackdrop(mode, Modifier.fillMaxSize())
        }

        if (dim > 0f) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = dim)))
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────
// Built-in gradient wallpapers
// ─────────────────────────────────────────────────────────────────────────

@Composable
private fun PresetBackdrop(mode: String, modifier: Modifier = Modifier) {
    val stars = remember(mode) {
        val rnd = Random(mode.hashCode())
        List(if (mode == WallpaperModes.SUNSET || mode == WallpaperModes.EMBER) 90 else 260) {
            Triple(rnd.nextFloat(), rnd.nextFloat(), rnd.nextFloat())
        }
    }

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        when (mode) {
            WallpaperModes.AURORA -> {
                drawRect(
                    Brush.verticalGradient(
                        0f to Color(0xFF040C14), 0.55f to Color(0xFF082432), 1f to Color(0xFF0C3A3A)
                    )
                )
                glow(Offset(w * 0.30f, h * 0.30f), w * 0.75f, Color(0xFF3FE0C5), 0.30f)
                glow(Offset(w * 0.75f, h * 0.22f), w * 0.60f, Color(0xFF52B788), 0.22f)
                glow(Offset(w * 0.55f, h * 0.45f), w * 0.55f, Color(0xFF9D7BFF), 0.16f)
                curtain(w, h, Color(0xFF3FE0C5), 0.20f, 0.10f)
                curtain(w, h, Color(0xFF7BFFB0), 0.30f, 0.06f)
            }
            WallpaperModes.SUNSET -> {
                drawRect(
                    Brush.verticalGradient(
                        0f to Color(0xFF150C2A), 0.38f to Color(0xFF4A2160),
                        0.68f to Color(0xFFB8407A), 0.86f to Color(0xFFFF7B4A), 1f to Color(0xFFFFB36B)
                    )
                )
                glow(Offset(w * 0.5f, h * 0.92f), w * 0.85f, Color(0xFFFFD08A), 0.55f)
            }
            WallpaperModes.OCEAN -> {
                drawRect(
                    Brush.verticalGradient(
                        0f to Color(0xFF03101C), 0.5f to Color(0xFF07304F), 1f to Color(0xFF0A5C7E)
                    )
                )
                glow(Offset(w * 0.70f, h * 0.18f), w * 0.70f, Color(0xFF00B4D8), 0.18f)
                wave(w, h, 0.66f, 0.030f, Color(0xFF00B4D8), 0.10f, 0f)
                wave(w, h, 0.74f, 0.036f, Color(0xFF0090B8), 0.16f, 1.4f)
                wave(w, h, 0.83f, 0.040f, Color(0xFF04405F), 0.55f, 2.6f)
                wave(w, h, 0.92f, 0.044f, Color(0xFF021C2E), 0.80f, 4.0f)
            }
            WallpaperModes.EMBER -> {
                drawRect(
                    Brush.verticalGradient(
                        0f to Color(0xFF0C0506), 0.55f to Color(0xFF2A0C0F), 1f to Color(0xFF6E1D12)
                    )
                )
                glow(Offset(w * 0.50f, h * 1.02f), w * 0.95f, Color(0xFFFF6B3A), 0.50f)
                glow(Offset(w * 0.20f, h * 0.85f), w * 0.45f, Color(0xFFB5341C), 0.30f)
            }
            else -> { // MIDNIGHT
                drawRect(
                    Brush.verticalGradient(
                        0f to Color(0xFF07070F), 0.5f to Color(0xFF12122A), 1f to Color(0xFF231B4A)
                    )
                )
                glow(Offset(w * 0.50f, h * 0.30f), w * 0.85f, Color(0xFF9D7BFF), 0.26f)
                glow(Offset(w * 0.20f, h * 0.70f), w * 0.55f, Color(0xFF5B3FD0), 0.20f)
            }
        }

        stars.forEach { (x, y, r) ->
            drawCircle(
                Color.White.copy(alpha = 0.25f + r * 0.6f),
                radius = 0.6f + r * 1.2f,
                center = Offset(x * w, y * h * 0.75f)
            )
        }
        // Bottom vignette so the bottom bar blends in
        drawRect(Brush.verticalGradient(0.85f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.35f)))
    }
}

private fun DrawScope.glow(center: Offset, radius: Float, color: Color, alpha: Float) {
    drawCircle(
        brush = Brush.radialGradient(listOf(color.copy(alpha = alpha), Color.Transparent), center = center, radius = radius),
        radius = radius,
        center = center
    )
}

/** Soft vertical aurora "curtain" band. */
private fun DrawScope.curtain(w: Float, h: Float, color: Color, topFrac: Float, alpha: Float) {
    val path = Path().apply {
        moveTo(0f, h * (topFrac + 0.05f))
        cubicTo(w * 0.25f, h * (topFrac - 0.08f), w * 0.55f, h * (topFrac + 0.16f), w, h * (topFrac - 0.02f))
        lineTo(w, h * (topFrac + 0.42f))
        cubicTo(w * 0.60f, h * (topFrac + 0.30f), w * 0.30f, h * (topFrac + 0.52f), 0f, h * (topFrac + 0.40f))
        close()
    }
    drawPath(
        path,
        Brush.verticalGradient(
            listOf(color.copy(alpha = alpha), Color.Transparent),
            startY = h * (topFrac - 0.05f), endY = h * (topFrac + 0.5f)
        )
    )
}

private fun DrawScope.wave(w: Float, h: Float, yFrac: Float, amp: Float, color: Color, alpha: Float, phase: Float) {
    val path = Path().apply {
        moveTo(0f, h)
        lineTo(0f, h * yFrac)
        val steps = 48
        for (i in 0..steps) {
            val t = i / steps.toFloat()
            lineTo(w * t, h * yFrac + sin(t * 2f * PI.toFloat() * 1.5f + phase) * h * amp)
        }
        lineTo(w, h)
        close()
    }
    drawPath(path, color.copy(alpha = alpha))
}
