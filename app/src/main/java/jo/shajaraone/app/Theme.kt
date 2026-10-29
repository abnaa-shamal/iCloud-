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
import androidx.compose.animation.core.animateFloat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

/**
 * THE place to change the app's colors. Every screen and component reads these names instead of hex values,
 * so a new look is mostly a matter of editing this block (plus NightColors below for the Material roles).
 */
object Brand {
    // Regional Boarding Ledger palette (olive / terracotta / ochre on parchment). The old Aurora names are kept so
    // every screen keeps working; they now point at the new colors.
    val Violet = Color(0xFF2D4436)       // main actions, selected items (olive)
    val VioletSoft = Color(0xFF5E7C69)   // outlines
    val VioletLight = Color(0xFF4C6454)  // subtitles, selected navigation, links
    val Teal = Color(0xFFBA5A31)         // highlights: prices, free seats, live status (terracotta)
    val Navy = Color(0xFFF5EFEB)         // base background (parchment)
    val Ink = Color(0xFF2B2621)          // main text (basalt)
    val Muted = Color(0xFF6E655B)        // secondary text
    val OnTeal = Color(0xFFFFFFFF)       // text on top of terracotta
    val Ochre = Color(0xFFD9A84E)
    val Stock = Color(0xFFFFFFFF)        // ticket / card surface
    val Pressed = Color(0xFFEAE0D5)      // recessed wells, inactive seats
    val Rule = Color(0xFFD6C7B7)         // borders and perforation lines
}

// Light "ledger" palette: parchment canvas, white ticket stock, olive actions.
private val NightColors = lightColorScheme(
    primary = Brand.Violet,
    onPrimary = Color(0xFFF5EFEB),
    primaryContainer = Color(0xFFCEE9D5),
    onPrimaryContainer = Color(0xFF092014),
    secondary = Brand.Teal,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFDBCE),
    onSecondaryContainer = Color(0xFF370E00),
    tertiary = Brand.Ochre,
    tertiaryContainer = Color(0xFFFFDEA9),
    onTertiaryContainer = Color(0xFF271900),
    background = Brand.Navy,
    onBackground = Brand.Ink,
    surface = Brand.Stock,
    onSurface = Brand.Ink,
    surfaceVariant = Brand.Pressed,
    onSurfaceVariant = Brand.Muted,
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFCF2EA),
    surfaceContainer = Color(0xFFF7ECE4),
    surfaceContainerHigh = Color(0xFFF1E6DE),
    surfaceContainerHighest = Color(0xFFEBE1D9),
    outline = Color(0xFF737973),
    outlineVariant = Brand.Rule,
    error = Color(0xFFBA1A1A),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF93000A)
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(6.dp),
    medium = RoundedCornerShape(8.dp),
    large = RoundedCornerShape(10.dp),
    extraLarge = RoundedCornerShape(12.dp)
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

/** The app background: warm parchment paper. */
@Composable
fun AppBackground(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().background(Brand.Navy)) {
        androidx.compose.runtime.CompositionLocalProvider(
            androidx.compose.material3.LocalContentColor provides Brand.Ink
        ) { content() }
    }
}
