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
    val Violet = Color(0xFF29D9FF)       // main actions, selected items, logo
    val VioletSoft = Color(0xFF6FD3EA)   // outlines and the car drawing
    val VioletLight = Color(0xFF9BE7FF)  // subtitles, selected navigation, soft highlights
    val Teal = Color(0xFFFF4D7E)         // highlights: prices, free seats, links, live status
    val Navy = Color(0xFF05070D)         // base background
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
    onPrimary = Color(0xFF03121A),
    primaryContainer = Color(0xFF0B3F4E),
    onPrimaryContainer = Color(0xFFCFF6FF),
    secondary = Brand.Teal,
    onSecondary = Brand.OnTeal,
    secondaryContainer = Color(0xFF5A1230),
    onSecondaryContainer = Color(0xFFFFD6E2),
    tertiary = Color(0xFFFF8A7A),
    tertiaryContainer = Color(0xFF5A2018),
    onTertiaryContainer = Color(0xFFFFDAD3),
    background = Brand.Navy,
    onBackground = Brand.Ink,
    surface = Brand.Navy,
    onSurface = Brand.Ink,
    surfaceVariant = Color(0xFF2A2F5C),
    onSurfaceVariant = Brand.Muted,
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

/** The app background: deep space with twinkling stars and slowly drifting colored glows. */
@Composable
fun AppBackground(content: @Composable () -> Unit) {
    val inf = androidx.compose.animation.core.rememberInfiniteTransition(label = "space")
    val tw by inf.animateFloat(
        0f, 1f,
        androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(5000, easing = androidx.compose.animation.core.LinearEasing)),
        label = "twinkle"
    )
    val drift by inf.animateFloat(
        0f, 1f,
        androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(18000, easing = androidx.compose.animation.core.LinearEasing)),
        label = "drift"
    )
    val stars = androidx.compose.runtime.remember {
        val r = kotlin.random.Random(7)
        List(90) { floatArrayOf(r.nextFloat(), r.nextFloat(), 0.6f + r.nextFloat() * 1.6f, r.nextFloat()) }
    }
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF05070D), Color(0xFF070B14), Color(0xFF0A0710))))) {
        androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val two = (2 * Math.PI).toFloat()
            fun orb(cx: Float, cy: Float, rad: Float, c: Color, phase: Float) {
                val dx = kotlin.math.sin((drift + phase) * two) * 0.06f * w
                val dy = kotlin.math.cos((drift + phase) * two) * 0.04f * h
                drawCircle(
                    Brush.radialGradient(listOf(c.copy(alpha = 0.55f), c.copy(alpha = 0f)), center = androidx.compose.ui.geometry.Offset(cx * w + dx, cy * h + dy), radius = rad),
                    radius = rad, center = androidx.compose.ui.geometry.Offset(cx * w + dx, cy * h + dy)
                )
            }
            orb(0.15f, 0.18f, w * 0.75f, Brand.Violet, 0f)
            orb(0.92f, 0.52f, w * 0.7f, Brand.Teal, 0.33f)
            orb(0.25f, 0.92f, w * 0.8f, Brand.Teal.copy(alpha = 0.5f), 0.66f)
            stars.forEach { s ->
                val a = 0.25f + 0.75f * ((kotlin.math.sin((tw + s[3]) * two) + 1f) / 2f)
                drawCircle(Color.White.copy(alpha = a), radius = s[2], center = androidx.compose.ui.geometry.Offset(s[0] * w, s[1] * h))
            }
        }
        androidx.compose.runtime.CompositionLocalProvider(
            androidx.compose.material3.LocalContentColor provides Brand.Ink
        ) { content() }
    }
}
