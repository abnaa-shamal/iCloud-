package jo.shajaraone.app

import androidx.compose.foundation.isSystemInDarkTheme
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
private val LightColors = lightColorScheme(
    primary = Color(0xFF0E5A45),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD3F0E1),
    onPrimaryContainer = Color(0xFF00281C),
    secondary = Color(0xFF8A5A00),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFE3A8),
    onSecondaryContainer = Color(0xFF2B1A00),
    tertiary = Color(0xFFB4532A),
    tertiaryContainer = Color(0xFFFFDBCB),
    onTertiaryContainer = Color(0xFF3A0F00),
    background = Color(0xFFF6F4EC),
    onBackground = Color(0xFF1A1C19),
    surface = Color(0xFFF6F4EC),
    onSurface = Color(0xFF1A1C19),
    surfaceVariant = Color(0xFFE4E3D6),
    onSurfaceVariant = Color(0xFF4A4D45),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFBFAF4),
    surfaceContainer = Color(0xFFF0EEE3),
    surfaceContainerHigh = Color(0xFFEAE8DC),
    surfaceContainerHighest = Color(0xFFE4E2D6),
    outline = Color(0xFF7A7D72),
    outlineVariant = Color(0xFFD3D2C4),
    error = Color(0xFFBA1A1A),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF7FD9B3),
    onPrimary = Color(0xFF00382A),
    primaryContainer = Color(0xFF0E5A45),
    onPrimaryContainer = Color(0xFFD3F0E1),
    secondary = Color(0xFFF2B84B),
    onSecondary = Color(0xFF3F2800),
    secondaryContainer = Color(0xFF5D3E00),
    onSecondaryContainer = Color(0xFFFFE3A8),
    tertiary = Color(0xFFFFB59B),
    tertiaryContainer = Color(0xFF7A2C0C),
    onTertiaryContainer = Color(0xFFFFDBCB),
    background = Color(0xFF0F1512),
    onBackground = Color(0xFFE3E4DD),
    surface = Color(0xFF0F1512),
    onSurface = Color(0xFFE3E4DD),
    surfaceVariant = Color(0xFF42463E),
    onSurfaceVariant = Color(0xFFC4C7BA),
    surfaceContainerLowest = Color(0xFF0A0F0D),
    surfaceContainerLow = Color(0xFF151C18),
    surfaceContainer = Color(0xFF1A221E),
    surfaceContainerHigh = Color(0xFF242C28),
    surfaceContainerHighest = Color(0xFF2E3733),
    outline = Color(0xFF8E9186),
    outlineVariant = Color(0xFF42463E),
    error = Color(0xFFFFB4AB),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6)
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp)
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
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        shapes = AppShapes,
        typography = AppTypography
    ) {
        // Arabic app: always right-to-left
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl, content = content)
    }
}
