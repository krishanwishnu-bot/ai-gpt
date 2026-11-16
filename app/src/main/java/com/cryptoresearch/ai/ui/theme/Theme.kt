package com.cryptoresearch.ai.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

object MarketColors {
    val Up = Color(0xFF16C784)
    val Down = Color(0xFFEA3943)
    val Warn = Color(0xFFF5A524)
    val Accent = Color(0xFF4FC3F7)
    val Neutral = Color(0xFF8A96AB)
}

private val NavyScheme = darkColorScheme(
    primary = MarketColors.Accent,
    onPrimary = Color(0xFF00212E),
    secondary = Color(0xFF7C8CF8),
    background = Color(0xFF0B1220),
    onBackground = Color(0xFFE6EDF7),
    surface = Color(0xFF111A2E),
    onSurface = Color(0xFFE6EDF7),
    surfaceVariant = Color(0xFF1A2540),
    onSurfaceVariant = Color(0xFF9FB0C8),
    outline = Color(0xFF2B3A5A),
    error = MarketColors.Down
)

private val BlackScheme = darkColorScheme(
    primary = MarketColors.Accent,
    onPrimary = Color(0xFF00212E),
    secondary = Color(0xFF7C8CF8),
    background = Color(0xFF000000),
    onBackground = Color(0xFFEDEDED),
    surface = Color(0xFF0E0E10),
    onSurface = Color(0xFFEDEDED),
    surfaceVariant = Color(0xFF1A1A1E),
    onSurfaceVariant = Color(0xFFA5A5AD),
    outline = Color(0xFF303036),
    error = MarketColors.Down
)

private val LightScheme = lightColorScheme(
    primary = Color(0xFF0B6FA4),
    background = Color(0xFFF4F6FA),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFE6EBF3),
    onSurfaceVariant = Color(0xFF4A566B),
    outline = Color(0xFFC3CCDA),
    error = Color(0xFFC62828)
)

@Composable
fun CryptoTheme(mode: String, content: @Composable () -> Unit) {
    val scheme = when (mode) {
        "black" -> BlackScheme
        "light" -> LightScheme
        "system" -> if (isSystemInDarkTheme()) NavyScheme else LightScheme
        else -> NavyScheme
    }
    val dark = scheme === NavyScheme || scheme === BlackScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                window.statusBarColor = scheme.background.toArgb()
                window.navigationBarColor = scheme.background.toArgb()
                val ctrl = WindowCompat.getInsetsController(window, view)
                ctrl.isAppearanceLightStatusBars = !dark
                ctrl.isAppearanceLightNavigationBars = !dark
            }
        }
    }
    MaterialTheme(colorScheme = scheme, content = content)
}
