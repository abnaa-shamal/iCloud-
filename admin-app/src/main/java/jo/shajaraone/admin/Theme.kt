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

/** Admin look: deep navy glass with an indigo-violet accent. Different from the rider (black + amber) and captain (graphite + blue) apps. */
object Royal {
    val Bg = Color(0xFF060A14)
    val BgTop = Color(0xFF16213D)
    val Surface = Color(0xFF101829)
    val Container = Color(0xFF1B2640)
    val Gold = Color(0xFF8B7CFF)
    val GoldLight = Color(0xFFB9AFFF)
    val GoldDeep = Color(0xFF5B4FD6)
    val Ivory = Color(0xFFF1F3FA)
    val Muted = Color(0xFF9AA3BC)
    val Emerald = Color(0xFF3DBB84)
    val Ruby = Color(0xFFE5677B)
    val Amber = Color(0xFFF0A93B)
    val GoldBrush = Brush.horizontalGradient(listOf(GoldDeep, GoldLight, Gold, GoldDeep))
}

private val RoyalScheme = darkColorScheme(
    primary = Royal.Gold, onPrimary = Color(0xFF101829),
    primaryContainer = Royal.Container, onPrimaryContainer = Royal.GoldLight,
    secondary = Royal.GoldLight, onSecondary = Color(0xFF101829),
    background = Royal.Bg, onBackground = Royal.Ivory,
    surface = Royal.Surface, onSurface = Royal.Ivory,
    surfaceVariant = Royal.Container, onSurfaceVariant = Royal.Muted,
    surfaceContainer = Royal.Surface, surfaceContainerHigh = Royal.Container,
    outline = Royal.GoldDeep, outlineVariant = Color(0xFF2E3A5C),
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
