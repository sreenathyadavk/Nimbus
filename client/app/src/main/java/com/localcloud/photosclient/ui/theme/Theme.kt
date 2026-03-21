package com.localcloud.photosclient.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = SamsungBlue,
    secondary = SamsungBlue,
    tertiary = SamsungBlue,
    background = PureBlack,
    surface = DarkSurface,
    surfaceVariant = DarkSurfaceVariant,
    onPrimary = PureBlack,
    onSecondary = PureBlack,
    onTertiary = PureBlack,
    onBackground = White,
    onSurface = White,
    onSurfaceVariant = White
)

@Composable
fun PhotosClientTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}
