package com.xiangqin.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = BrandPrimary,
    secondary = BrandSecondary,
    tertiary = Purple80,
    background = BrandBackground,
    surface = BrandSurface,
    surfaceVariant = BrandCard,
    onPrimary = BrandBackground,
    onSecondary = BrandTextPrimary,
    onBackground = BrandTextPrimary,
    onSurface = BrandTextPrimary,
    outline = BrandDivider,
    error = BrandError
)

@Composable
fun XiangQinTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}
