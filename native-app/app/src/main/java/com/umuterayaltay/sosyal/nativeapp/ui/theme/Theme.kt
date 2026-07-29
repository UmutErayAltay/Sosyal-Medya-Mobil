package com.umuterayaltay.sosyal.nativeapp.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

// style.css'in semantik token eşlemesiyle BİREBİR aynı (--bg/--card/--text/
// --muted/--border/--primary/--danger) — bkz. Color.kt yorumları.
private val LightColors = lightColorScheme(
    primary = Coral700,
    onPrimary = White,
    secondary = Teal700,
    onSecondary = White,
    background = WarmBg,
    onBackground = Charcoal900,
    surface = White,
    onSurface = Charcoal900,
    surfaceVariant = Sand200,
    onSurfaceVariant = Taupe600,
    outline = Sand200,
    error = Red500,
    onError = White,
    errorContainer = Red50,
    onErrorContainer = Red500,
)

private val DarkColors = darkColorScheme(
    primary = Coral400,
    onPrimary = Charcoal950,
    secondary = Teal400,
    onSecondary = Charcoal950,
    background = Charcoal950,
    onBackground = Cream100,
    surface = Charcoal850,
    onSurface = Cream100,
    surfaceVariant = Charcoal700,
    onSurfaceVariant = Taupe400,
    outline = Charcoal700,
    error = Red400,
    onError = Charcoal950,
    errorContainer = Red950,
    onErrorContainer = Red400,
)

@Composable
fun SosyalNativeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColors else LightColors
    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        content = content,
    )
}
