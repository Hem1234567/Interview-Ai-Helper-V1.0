package com.interviewcopilot.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

data class AppColors(
    val bg: Color,
    val text: Color,
    val text2: Color,
    val card: Color,
    val cardHi: Color,
    val sep: Color,
    val blue: Color,
    val green: Color,
    val red: Color,
    val yellow: Color,
    val chipBlue: Color,
    val chipRed: Color,
    val chipGray: Color,
    val dark: Boolean
)

val LightColors = AppColors(
    bg = Color(0xFFFFFFFF),
    text = Color(0xFF000000),
    text2 = Color(0xFF6E6E73),
    card = Color(0xFFF2F2F7),
    cardHi = Color(0xFFFFFFFF),
    sep = Color(0xFFD1D1D6),
    blue = Color(0xFF007AFF),
    green = Color(0xFF34C759),
    red = Color(0xFFFF3B30),
    yellow = Color(0xFFC98A00),
    chipBlue = Color(0xFFE5F1FF),
    chipRed = Color(0xFFFFE5E3),
    chipGray = Color(0xFFE5E5EA),
    dark = false
)

val DarkColors = AppColors(
    bg = Color(0xFF000000),
    text = Color(0xFFFFFFFF),
    text2 = Color(0xFF8E8E93),
    card = Color(0xFF1C1C1E),
    cardHi = Color(0xFF2C2C2E),
    sep = Color(0xFF38383A),
    blue = Color(0xFF0A84FF),
    green = Color(0xFF30D158),
    red = Color(0xFFFF453A),
    yellow = Color(0xFFFFD60A),
    chipBlue = Color(0xFF0A2540),
    chipRed = Color(0xFF3A1210),
    chipGray = Color(0xFF2C2C2E),
    dark = true
)

val LocalAppColors = staticCompositionLocalOf { LightColors }

@Composable
fun AppTheme(mode: String, content: @Composable () -> Unit) {
    val dark = when (mode) {
        "Light" -> false
        "Dark" -> true
        else -> isSystemInDarkTheme()
    }
    val c = if (dark) DarkColors else LightColors
    val scheme = if (dark) {
        darkColorScheme(
            primary = c.blue, background = c.bg, surface = c.bg,
            onBackground = c.text, onSurface = c.text, onPrimary = Color.White
        )
    } else {
        lightColorScheme(
            primary = c.blue, background = c.bg, surface = c.bg,
            onBackground = c.text, onSurface = c.text, onPrimary = Color.White
        )
    }
    CompositionLocalProvider(LocalAppColors provides c) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
