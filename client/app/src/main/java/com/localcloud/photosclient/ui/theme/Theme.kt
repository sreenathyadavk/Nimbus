package com.localcloud.photosclient.ui.theme

import android.app.Activity
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = AccentOrange,
    onPrimary = Color.Black,
    secondary = Secondary,
    onSecondary = Color.White,
    tertiary = Tertiary,
    background = PureBlack,
    surface = SurfaceDark,
    onSurface = TextPrimary,
    onSurfaceVariant = TextSecondary,
    surfaceVariant = CardDark,
    outline = Color(0xFF333333)
)

@Composable
fun NimbusTheme(
    content: @Composable () -> Unit
) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = Color.Transparent.toArgb()
            window.navigationBarColor = NavBg.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }

    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}
