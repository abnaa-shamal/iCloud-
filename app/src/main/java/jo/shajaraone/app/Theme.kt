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
    // Rider app: amber on black. Captain app: electric blue on graphite.
    val Violet = if (IS_CAPTAIN_APP) Color(0xFF3D8BFF) else Color(0xFFF2B300)       // main actions, selected items, logo
    val VioletSoft = if (IS_CAPTAIN_APP) Color(0xFF2F73D9) else Color(0xFFD9A200)   // outlines
    val VioletLight = if (IS_CAPTAIN_APP) Color(0xFF8DB8FF) else Color(0xFFFFD866)  // subtitles, soft highlights
    val Teal = if (IS_CAPTAIN_APP) Color(0xFF9CC4FF) else Color(0xFFFFE08A)         // highlights
    val Navy = if (IS_CAPTAIN_APP) Color(0xFF16171A) else Color(0xFF000000)         // base background
    val Ink = Color(0xFFF4F7FF)          // main text
    val Muted = Color(0xFFA9B4CC)        // secondary text
    val OnTeal = Color(0xFFFFFFFF)       // text on top of teal
    // extra tokens used by the newer components (ticket cards, wells, rules) - glass look on the dark sky
    val Ochre = Color(0xFFFFC857)        // gold highlight
    val Stock = Color(0x1FFFFFFF)        // glass card surface
    val Pressed = Color(0x33000000)      // recessed wells, inactive seats
    val Rule = Color(0x33FFFFFF)         // borders and perforation lines
}

// "Night" palette: deep navy, violet actions, teal highlights. The app is always dark.
private val NightColors = darkColorScheme(
    primary = Brand.Violet,
    onPrimary = if (IS_CAPTAIN_APP) Color.White else Color(0xFF1A0E00),
    primaryContainer = if (IS_CAPTAIN_APP) Color(0xFF173A6B) else Color(0xFF4A3000),
    onPrimaryContainer = if (IS_CAPTAIN_APP) Color(0xFFDCE9FF) else Color(0xFFFFEBC8),
    secondary = Brand.Teal,
    onSecondary = Brand.OnTeal,
    secondaryContainer = Color(0xFF3A3B41),
    onSecondaryContainer = Color(0xFFE6E6EA),
    tertiary = Color(0xFFFF8A7A),
    tertiaryContainer = Color(0xFF5A2018),
    onTertiaryContainer = Color(0xFFFFDAD3),
    background = Brand.Navy,
    onBackground = Brand.Ink,
    surface = Brand.Navy,
    onSurface = Brand.Ink,
    surfaceVariant = Color(0xFF2B2C31),
    onSurfaceVariant = Brand.Muted,
    surfaceContainerLowest = Color(0xFF111214),
    surfaceContainerLow = Color(0xFF18191C),
    surfaceContainer = Color(0xFF1F2024),
    surfaceContainerHigh = Color(0xFF26272B),
    surfaceContainerHighest = Color(0xFF2E2F34),
    outline = Color(0xFF7C7E86),
    outlineVariant = Color(0xFF3A3B41),
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

/** The app background: deep space with twinkling stars and slowly drifting colored glows. */
@Composable
fun AppBackground(content: @Composable () -> Unit) {
    androidx.compose.foundation.layout.Box(
        androidx.compose.ui.Modifier.fillMaxSize().background(if (IS_CAPTAIN_APP) CapBg else VantaPageBg)
    ) {
        androidx.compose.runtime.CompositionLocalProvider(
            androidx.compose.material3.LocalContentColor provides Brand.Ink
        ) { content() }
    }
}
