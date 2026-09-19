package com.astra.browser.ui.motion

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.navigation.NavBackStackEntry

/** Ids stored in SettingsStore.animStyle. */
object AnimStyles {
    const val NONE = "NONE"
    const val FADE = "FADE"
    const val SLIDE = "SLIDE"
    const val SCALE = "SCALE"

    val all: List<Pair<String, String>> = listOf(
        NONE to "Off (fastest)",
        FADE to "Fade",
        SLIDE to "Slide",
        SCALE to "Zoom"
    )

    fun label(id: String) = all.firstOrNull { it.first == id }?.second ?: "Fade"
}

/**
 * Effective motion config after combining the user's chosen style with the
 * two "go easy on the device" switches. Reduced-motion or Lite mode force a
 * plain short fade (or nothing) so low-end phones never drop frames or heat up
 * from animating full-screen layers.
 */
data class MotionSpec(
    val style: String,
    val durationMs: Int
) {
    companion object {
        val Default = MotionSpec(AnimStyles.FADE, 180)

        fun resolve(style: String, lite: Boolean, reduced: Boolean): MotionSpec = when {
            reduced || style == AnimStyles.NONE -> MotionSpec(AnimStyles.NONE, 0)
            // Lite: only a very short fade, never slide/scale (those animate a
            // whole screen-sized layer = the expensive kind on weak GPUs).
            lite -> MotionSpec(AnimStyles.FADE, 120)
            else -> MotionSpec(style, 240)
        }
    }
}

// Smooth, slightly snappy curve (Material "emphasized decelerate"-like).
private val Emphasized = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)

typealias NavScope = AnimatedContentTransitionScope<NavBackStackEntry>

fun NavScope.astraEnter(m: MotionSpec, forward: Boolean = true): EnterTransition = when (m.style) {
    AnimStyles.NONE -> EnterTransition.None
    AnimStyles.SLIDE -> slideInHorizontally(
        animationSpec = tween(m.durationMs, easing = Emphasized),
        initialOffsetX = { full -> if (forward) full / 4 else -full / 4 }
    ) + fadeIn(tween(m.durationMs, easing = FastOutSlowInEasing))
    AnimStyles.SCALE -> scaleIn(
        animationSpec = tween(m.durationMs, easing = Emphasized),
        initialScale = 0.94f
    ) + fadeIn(tween(m.durationMs, easing = FastOutSlowInEasing))
    else -> fadeIn(tween(m.durationMs, easing = FastOutSlowInEasing))
}

fun NavScope.astraExit(m: MotionSpec, forward: Boolean = true): ExitTransition = when (m.style) {
    AnimStyles.NONE -> ExitTransition.None
    AnimStyles.SLIDE -> slideOutHorizontally(
        animationSpec = tween(m.durationMs, easing = Emphasized),
        targetOffsetX = { full -> if (forward) -full / 6 else full / 4 }
    ) + fadeOut(tween(m.durationMs / 2))
    AnimStyles.SCALE -> scaleOut(
        animationSpec = tween(m.durationMs, easing = Emphasized),
        targetScale = 1.04f
    ) + fadeOut(tween(m.durationMs / 2))
    else -> fadeOut(tween(m.durationMs / 2))
}
