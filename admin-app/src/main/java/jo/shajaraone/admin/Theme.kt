package jo.shajaraone.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** «Royal» look of the admin app: deep burgundy, antique gold, ivory. Different from the Aurora look of the rider/captain apps. */
object Royal {
    val Bg = Color(0xFF1A0610)
    val BgTop = Color(0xFF3A0F26)
    val Surface = Color(0xFF2A0C1B)
    val Container = Color(0xFF3A1428)
    val Gold = Color(0xFFD4AF37)
    val GoldLight = Color(0xFFF2D675)
    val GoldDeep = Color(0xFF9C7A1C)
    val Ivory = Color(0xFFF8F0DC)
    val Muted = Color(0xFFC9B79A)
    val Emerald = Color(0xFF3DBB84)
    val Ruby = Color(0xFFE5677B)
    val Amber = Color(0xFFF0A93B)
    val GoldBrush = Brush.horizontalGradient(listOf(GoldDeep, GoldLight, Gold, GoldDeep))
}

private val RoyalScheme = darkColorScheme(
    primary = Royal.Gold, onPrimary = Color(0xFF2A0C1B),
    primaryContainer = Royal.Container, onPrimaryContainer = Royal.GoldLight,
    secondary = Royal.GoldLight, onSecondary = Color(0xFF2A0C1B),
    background = Royal.Bg, onBackground = Royal.Ivory,
    surface = Royal.Surface, onSurface = Royal.Ivory,
    surfaceVariant = Royal.Container, onSurfaceVariant = Royal.Muted,
    surfaceContainer = Royal.Surface, surfaceContainerHigh = Royal.Container,
    outline = Royal.GoldDeep, outlineVariant = Color(0xFF5A2A40),
    error = Royal.Ruby
)

private val RoyalShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp), small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp), large = RoundedCornerShape(22.dp), extraLarge = RoundedCornerShape(28.dp)
)

private val RoyalType = Typography().let { d ->
    d.copy(
        headlineSmall = d.headlineSmall.copy(fontWeight = FontWeight.ExtraBold),
        titleLarge = d.titleLarge.copy(fontWeight = FontWeight.ExtraBold, fontSize = 20.sp),
        titleMedium = d.titleMedium.copy(fontWeight = FontWeight.Bold),
        titleSmall = d.titleSmall.copy(fontWeight = FontWeight.Bold),
        labelLarge = d.labelLarge.copy(fontWeight = FontWeight.Bold)
    )
}

@Composable
fun RoyalTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = RoyalScheme, shapes = RoyalShapes, typography = RoyalType) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(listOf(Royal.BgTop, Royal.Bg, Royal.Bg))
                )
            ) { content() }
        }
    }
}
