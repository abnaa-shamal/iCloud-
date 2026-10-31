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
import androidx.compose.runtime.remember
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
    // Colors follow the theme picked in Settings (the same 18 themes in both apps); CapTheme.t is set in ShajaraTheme.
    private val t get() = CapTheme.t
    private val dark get() = t?.isDark ?: true
    val Violet: Color get() = t?.accentColor ?: Color(0xFFF5C451)               // main actions, selected items, logo
    val VioletSoft: Color get() = t?.accentColor?.let { androidx.compose.ui.graphics.lerp(it, Color.Black, 0.15f) } ?: Color(0xFFC9932A)
    val VioletLight: Color get() = t?.accentColor?.let { androidx.compose.ui.graphics.lerp(it, Color.White, 0.4f) } ?: Color(0xFFFFD873)
    val Teal: Color get() = t?.needleColor ?: Color(0xFFFFD873)                 // highlights
    val Navy: Color get() = t?.bgGradient?.first() ?: Color(0xFF000000)         // base background
    val Ink: Color get() = if (dark) Color(0xFFF4F7FF) else Color(0xFF1E293B)   // main text
    val Muted: Color get() = if (dark) Color(0xFFA9B4CC) else Color(0xFF64748B) // secondary text
    val OnTeal = Color(0xFFFFFFFF)       // text on top of teal
    val Ochre = Color(0xFFFFD873)        // gold highlight
    val Stock: Color get() = if (dark) Color(0x1FFFFFFF) else Color(0x14000000) // glass card surface
    val Pressed: Color get() = if (dark) Color(0x33000000) else Color(0x14000000) // recessed wells, inactive seats
    val Rule: Color get() = if (dark) Color(0x33FFFFFF) else Color(0x33000000)  // borders and perforation lines
}

private fun nightColors(): androidx.compose.material3.ColorScheme {
    val t = CapTheme.t
    if (t != null && !t.isDark) return lightColorScheme(
        primary = t.accentColor, onPrimary = Color.White, secondary = t.needleColor, onSecondary = Color.White,
        background = t.bgGradient.first(), onBackground = Color(0xFF1E293B),
        surface = t.cardBg, onSurface = Color(0xFF1E293B), surfaceVariant = Color(0xFFE8ECF1), onSurfaceVariant = Color(0xFF64748B),
        surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFF6F8FA), surfaceContainer = Color(0xFFF0F3F6),
        surfaceContainerHigh = Color(0xFFEAEEF2), surfaceContainerHighest = Color(0xFFE3E8ED), outline = Color(0xFF94A3B8)
    )
    val card = t?.cardBg ?: Color(0xFF0E0E12)
    fun up(f: Float) = androidx.compose.ui.graphics.lerp(card, Color.White, f)
    return darkColorScheme(
        primary = Brand.Violet, onPrimary = Color.White,
        primaryContainer = androidx.compose.ui.graphics.lerp(Brand.Violet, Color.Black, 0.65f), onPrimaryContainer = Color.White,
        secondary = Brand.Teal, onSecondary = Brand.OnTeal,
        secondaryContainer = up(0.12f), onSecondaryContainer = Color(0xFFE6E6EA),
        tertiary = Color(0xFFFF8A7A), tertiaryContainer = Color(0xFF5A2018), onTertiaryContainer = Color(0xFFFFDAD3),
        background = Brand.Navy, onBackground = Brand.Ink, surface = Brand.Navy, onSurface = Brand.Ink,
        surfaceVariant = up(0.1f), onSurfaceVariant = Brand.Muted,
        surfaceContainerLowest = up(0.02f), surfaceContainerLow = up(0.04f), surfaceContainer = up(0.06f),
        surfaceContainerHigh = up(0.09f), surfaceContainerHighest = up(0.12f),
        outline = Color(0xFF7C7E86), outlineVariant = up(0.15f),
        error = Color(0xFFFF8A80), errorContainer = Color(0xFF5C1A1A), onErrorContainer = Color(0xFFFFDAD6)
    )
}

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
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val storage = remember { jo.shajaraone.app.hasbat.AppDataManager.get(ctx) }
    val hasbatTheme = jo.shajaraone.app.hasbat.rememberHasbatTheme(storage)
    if (CapTheme.t != hasbatTheme) CapTheme.t = hasbatTheme
    MaterialTheme(
        colorScheme = nightColors(),
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
        androidx.compose.ui.Modifier.fillMaxSize().background(CapBg)
    ) {
        androidx.compose.runtime.CompositionLocalProvider(
            androidx.compose.material3.LocalContentColor provides Brand.Ink
        ) { content() }
    }
}
