package jo.shajaraone.app

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/** The one place the app's display name lives. */
const val APP_DISPLAY_NAME = "أبناء الشمال"
const val WELCOME_LINE = "هلا بعيال صويت، هلا بالشمال كله"
const val SPLASH_MILLIS = 2000L

/** Two-second welcome: the name with a glowing light sweep, then the welcome line fades in. */
@Composable
fun SplashScreen(onDone: () -> Unit) {
    val current by rememberUpdatedState(onDone)
    LaunchedEffect(Unit) {
        delay(SPLASH_MILLIS)
        current()
    }

    val inf = rememberInfiniteTransition(label = "splash")
    val sweep by inf.animateFloat(
        0f, 1f, infiniteRepeatable(tween(1300, easing = LinearEasing)), label = "sweep"
    )
    val glow by inf.animateFloat(
        0.55f, 1f, infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "glow"
    )
    val appear = remember { Animatable(0f) }
    val line = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        appear.animateTo(1f, tween(600, easing = FastOutSlowInEasing))
    }
    LaunchedEffect(Unit) {
        delay(550)
        line.animateTo(1f, tween(650))
    }

    val accent = Brand.Violet
    val car = remember { Animatable(0f) }
    LaunchedEffect(Unit) { car.animateTo(1f, tween(1800, easing = FastOutSlowInEasing)) }

    Box(
        Modifier.fillMaxSize().background(if (IS_CAPTAIN_APP) CapBg else VantaPageBg),
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier.size(300.dp).alpha(0.30f * glow * appear.value)
                .background(Brush.radialGradient(listOf(accent, accent.copy(alpha = 0f))), androidx.compose.foundation.shape.CircleShape)
        )
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(
                Modifier.alpha(appear.value).scale(0.85f + 0.15f * appear.value).darkGlass(40.dp).padding(22.dp),
                contentAlignment = Alignment.Center
            ) { AppLogo(72.dp) }
            Text(
                APP_DISPLAY_NAME,
                fontSize = 40.sp, fontWeight = FontWeight.Bold, color = Color.White,
                modifier = Modifier.alpha(appear.value)
            )
            Box(
                Modifier.width(120.dp).height(3.dp).alpha(line.value)
                    .background(Brush.horizontalGradient(listOf(accent.copy(alpha = 0f), accent, accent.copy(alpha = 0f))))
            )
            Text(WELCOME_LINE, fontSize = 18.sp, color = Dim, modifier = Modifier.alpha(line.value))
        }
        CarArt(
            Modifier.align(Alignment.BottomStart).padding(bottom = 90.dp).width(150.dp).height(60.dp)
                .offset(x = (-150 + 560 * car.value).dp).alpha(0.9f)
        )
    }
}

/** Big glowing title with a moving light sweep (used on the login screen). */
@Composable
fun GlowTitle(text: String, fontSize: androidx.compose.ui.unit.TextUnit = 44.sp, modifier: Modifier = Modifier) {
    val inf = rememberInfiniteTransition(label = "title")
    val sweep by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(2600, easing = LinearEasing)), label = "sweep")
    val glow by inf.animateFloat(0.5f, 1f, infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "glow")
    val brush = Brush.linearGradient(
        colors = listOf(Brand.Violet, Brand.Teal, Brand.Violet, Color(0xFF5E7C69), Brand.Violet),
        start = Offset(sweep * 700f - 350f, 0f), end = Offset(sweep * 700f + 350f, 120f), tileMode = TileMode.Mirror
    )
    Text(
        text,
        style = MaterialTheme.typography.displayMedium.copy(
            brush = brush, fontSize = fontSize, fontWeight = FontWeight.Black,
            shadow = Shadow(Brand.Ochre.copy(alpha = 0.35f), blurRadius = 12f * glow)
        ),
        textAlign = TextAlign.Center, modifier = modifier
    )
}
