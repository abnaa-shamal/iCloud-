package jo.shajaraone.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

// Palette: deep olive-green (the village tree), warm amber (the road sun), warm paper background.
// "Night" palette: deep navy, violet actions, teal highlights. The app is always dark.
private val NightColors = darkColorScheme(
    primary = Color(0xFF6B4BFF),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF2A2370),
    onPrimaryContainer = Color(0xFFE4DEFF),
    secondary = Color(0xFF2DE2C0),
    onSecondary = Color(0xFF06251F),
    secondaryContainer = Color(0xFF0F4D47),
    onSecondaryContainer = Color(0xFFB9F6EA),
    tertiary = Color(0xFFFF8A7A),
    tertiaryContainer = Color(0xFF5A2018),
    onTertiaryContainer = Color(0xFFFFDAD3),
    background = Color(0xFF0B1030),
    onBackground = Color(0xFFF5F6FF),
    surface = Color(0xFF0B1030),
    onSurface = Color(0xFFF5F6FF),
    surfaceVariant = Color(0xFF2A2F5C),
    onSurfaceVariant = Color(0xFFB7BBE0),
    surfaceContainerLowest = Color(0xFF11163A),
    surfaceContainerLow = Color(0xFF151A42),
    surfaceContainer = Color(0xFF1B2050),
    surfaceContainerHigh = Color(0xFF232958),
    surfaceContainerHighest = Color(0xFF2C3262),
    outline = Color(0xFF7F84B8),
    outlineVariant = Color(0xFF3A4078),
    error = Color(0xFFFF8A80),
    errorContainer = Color(0xFF5C1A1A),
    onErrorContainer = Color(0xFFFFDAD6)
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(20.dp)
)

private val AppTypography = Typography().let { d ->
    d.copy(
        headlineSmall = d.headlineSmall.copy(fontWeight = FontWeight.ExtraBold),
        titleLarge = d.titleLarge.copy(fontWeight = FontWeight.ExtraBold, fontSize = 20.sp),
        titleMedium = d.titleMedium.copy(fontWeight = FontWeight.Bold),
        titleSmall = d.titleSmall.copy(fontWeight = FontWeight.Bold),
        labelLarge = d.labelLarge.copy(fontWeight = FontWeight.Bold),
        labelMedium = d.labelMedium.copy(fontWeight = FontWeight.SemiBold),
        bodyLarge = d.bodyLarge.copy(fontSize = 16.sp, lineHeight = 24.sp)
    )
}

@Composable
fun ShajaraTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = NightColors,
        shapes = AppShapes,
        typography = AppTypography
    ) {
        // Arabic app: always right-to-left
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl, content = content)
    }
}

/** The app background: deep navy fading into violet. */
@Composable
fun AppBackground(content: @Composable () -> Unit) {
    Box(
        Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF0B1030), Color(0xFF1B0F3F), Color(0xFF0E2B3A)))),
        content = {
            androidx.compose.runtime.CompositionLocalProvider(
                androidx.compose.material3.LocalContentColor provides Color(0xFFF5F6FF)
            ) { content() }
        }
    )
}
