package com.trandz123.hotronguoikhiemthi.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

// High-contrast theme cho nguoi khiem thi nhe
private val HighContrastDarkColors = darkColorScheme(
    primary = HighContrastYellow,
    onPrimary = Black,
    secondary = HighContrastOrange,
    onSecondary = Black,
    background = Black,
    onBackground = White,
    surface = NearBlack,
    onSurface = White,
    onSurfaceVariant = LightGray,
    error = ErrorRed,
    onError = Black,
)

private val HighContrastLightColors = lightColorScheme(
    primary = Black,
    onPrimary = HighContrastYellow,
    secondary = DarkGray,
    onSecondary = White,
    background = White,
    onBackground = Black,
    surface = LightGray,
    onSurface = Black,
    onSurfaceVariant = DarkGray,
    error = ErrorRed,
    onError = White,
)

@Composable
fun HoTroTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) HighContrastDarkColors else HighContrastLightColors
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content,
    )
}
