package com.shajara.rides

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

private val LightColors = lightColorScheme(
    primary = Color(0xFF0B6E4F),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB9F1D7),
    onPrimaryContainer = Color(0xFF002115),
    secondary = Color(0xFF4A6458),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCDE9DA),
    onSecondaryContainer = Color(0xFF072017),
    tertiary = Color(0xFFB45A1E),
    tertiaryContainer = Color(0xFFFFDBC9),
    onTertiaryContainer = Color(0xFF341000),
    background = Color(0xFFF4FAF6),
    onBackground = Color(0xFF161D1A),
    surface = Color(0xFFF4FAF6),
    onSurface = Color(0xFF161D1A),
    surfaceVariant = Color(0xFFDBE5DE),
    onSurfaceVariant = Color(0xFF404943),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFEEF5F0),
    surfaceContainer = Color(0xFFE8EFEA),
    surfaceContainerHigh = Color(0xFFE3EAE5),
    surfaceContainerHighest = Color(0xFFDDE4DF),
    outline = Color(0xFF707973),
    outlineVariant = Color(0xFFBFC9C2),
    error = Color(0xFFBA1A1A)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8DD7B4),
    onPrimary = Color(0xFF003826),
    primaryContainer = Color(0xFF005139),
    onPrimaryContainer = Color(0xFFB9F1D7),
    secondary = Color(0xFFB1CCBE),
    onSecondary = Color(0xFF1D352A),
    secondaryContainer = Color(0xFF334C40),
    onSecondaryContainer = Color(0xFFCDE9DA),
    tertiary = Color(0xFFFFB68D),
    tertiaryContainer = Color(0xFF7A3A00),
    onTertiaryContainer = Color(0xFFFFDBC9),
    background = Color(0xFF0E1512),
    onBackground = Color(0xFFDDE4DF),
    surface = Color(0xFF0E1512),
    onSurface = Color(0xFFDDE4DF),
    surfaceVariant = Color(0xFF404943),
    onSurfaceVariant = Color(0xFFBFC9C2),
    surfaceContainerLowest = Color(0xFF09100D),
    surfaceContainerLow = Color(0xFF161D1A),
    surfaceContainer = Color(0xFF1A211E),
    surfaceContainerHigh = Color(0xFF252C28),
    surfaceContainerHighest = Color(0xFF2F3633),
    outline = Color(0xFF8A938D),
    outlineVariant = Color(0xFF404943),
    error = Color(0xFFFFB4AB)
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

@Composable
fun MashwarakTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        shapes = AppShapes
    ) {
        // Arabic app: always right-to-left
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl, content = content)
    }
}
