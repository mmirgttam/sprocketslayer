package com.example.sprocketslayer.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = SprocketTeal,
    onPrimary = PocketNavyDark,
    primaryContainer = PocketNavy,
    onPrimaryContainer = PaperCream,
    secondary = PhotoPurple,
    onSecondary = PaperCream,
    secondaryContainer = ColorCompat.DarkPurpleContainer,
    onSecondaryContainer = PaperCream,
    tertiary = PhotoPink,
    onTertiary = PaperCream,
    background = PocketNavyDark,
    onBackground = PaperCream,
    surface = PocketNavy,
    onSurface = PaperCream,
    surfaceVariant = ColorCompat.DarkSurfaceVariant,
    onSurfaceVariant = SoftBlueGrey,
)

private val LightColorScheme = lightColorScheme(
    primary = SprocketTealDark,
    onPrimary = PaperCream,
    primaryContainer = SprocketTeal,
    onPrimaryContainer = PocketNavy,
    secondary = PhotoPurple,
    onSecondary = PaperCream,
    secondaryContainer = ColorCompat.LightPurpleContainer,
    onSecondaryContainer = PocketNavy,
    tertiary = PhotoPink,
    onTertiary = PaperCream,
    tertiaryContainer = ColorCompat.LightPinkContainer,
    onTertiaryContainer = PocketNavy,
    background = MistBackground,
    onBackground = InkBlue,
    surface = PaperCream,
    onSurface = InkBlue,
    surfaceVariant = SoftBlueGrey,
    onSurfaceVariant = PocketNavy,
)

private object ColorCompat {
    val LightPurpleContainer = androidx.compose.ui.graphics.Color(0xFFE7E2FF)
    val LightPinkContainer = androidx.compose.ui.graphics.Color(0xFFFFD8E8)
    val DarkPurpleContainer = androidx.compose.ui.graphics.Color(0xFF3A3266)
    val DarkSurfaceVariant = androidx.compose.ui.graphics.Color(0xFF2C3654)
}

@Composable
fun SprocketSlayerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        val window = (LocalContext.current as Activity).window
        SideEffect {
            window.statusBarColor = colorScheme.background.toArgb()
            window.navigationBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
