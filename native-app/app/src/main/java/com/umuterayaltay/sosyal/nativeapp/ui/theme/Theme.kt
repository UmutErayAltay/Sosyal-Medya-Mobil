package com.umuterayaltay.sosyal.nativeapp.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColors = lightColorScheme(
    primary = BrandDark,
    onPrimary = BrandBackgroundLight,
    secondary = BrandAccent,
    background = BrandBackgroundLight,
    surface = BrandSurfaceLight,
    error = BrandError,
)

private val DarkColors = darkColorScheme(
    primary = BrandAccent,
    onPrimary = BrandDarkVariant,
    secondary = BrandAccent,
    background = BrandBackgroundDark,
    surface = BrandSurfaceDark,
    error = BrandError,
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
