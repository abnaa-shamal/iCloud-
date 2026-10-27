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
const val WELCOME_LINE = "ياهلا بإبن البلد"
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

    val shimmer = Brush.linearGradient(
        colors = listOf(Color(0xFFB9B2FF), Color.White, Color(0xFF2DE2C0), Color.White, Color(0xFFB9B2FF)),
        start = Offset(sweep * 700f - 350f, 0f),
        end = Offset(sweep * 700f + 350f, 120f),
        tileMode = TileMode.Mirror
    )

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        // Soft pulsing light behind the name
        Box(
            Modifier.size(320.dp).alpha(0.55f * glow * appear.value)
                .background(
                    Brush.radialGradient(listOf(Color(0xFF6B4BFF), Color(0x006B4BFF))),
                    androidx.compose.foundation.shape.CircleShape
                )
        )
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp)) {
            AppLogo(96.dp, Modifier.alpha(appear.value).scale(0.85f + 0.15f * appear.value))
            Text(
                APP_DISPLAY_NAME,
                style = MaterialTheme.typography.displayMedium.copy(
                    brush = shimmer,
                    fontSize = 52.sp,
                    fontWeight = FontWeight.Black,
                    shadow = Shadow(Color(0xFF6B4BFF), blurRadius = 48f * glow)
                ),
                textAlign = TextAlign.Center,
                modifier = Modifier.alpha(appear.value).scale(0.9f + 0.1f * appear.value)
            )
            Box(
                Modifier.width(120.dp).height(3.dp).alpha(line.value)
                    .background(Brush.horizontalGradient(listOf(Color(0x006B4BFF), Color(0xFF2DE2C0), Color(0x006B4BFF))))
            )
            Text(
                WELCOME_LINE,
                style = MaterialTheme.typography.headlineSmall.copy(
                    color = Color(0xFFB7BBE0), fontWeight = FontWeight.SemiBold,
                    shadow = Shadow(Color(0xFF2DE2C0), blurRadius = 24f * glow)
                ),
                modifier = Modifier.alpha(line.value)
            )
        }
    }
}

/** Big glowing title with a moving light sweep (used on the login screen). */
@Composable
fun GlowTitle(text: String, fontSize: androidx.compose.ui.unit.TextUnit = 44.sp, modifier: Modifier = Modifier) {
    val inf = rememberInfiniteTransition(label = "title")
    val sweep by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(2600, easing = LinearEasing)), label = "sweep")
    val glow by inf.animateFloat(0.5f, 1f, infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "glow")
    val brush = Brush.linearGradient(
        colors = listOf(Color(0xFFB9B2FF), Color.White, Color(0xFF2DE2C0), Color.White, Color(0xFFB9B2FF)),
        start = Offset(sweep * 700f - 350f, 0f), end = Offset(sweep * 700f + 350f, 120f), tileMode = TileMode.Mirror
    )
    Text(
        text,
        style = MaterialTheme.typography.displayMedium.copy(
            brush = brush, fontSize = fontSize, fontWeight = FontWeight.Black,
            shadow = Shadow(Color(0xFF6B4BFF), blurRadius = 40f * glow)
        ),
        textAlign = TextAlign.Center, modifier = modifier
    )
}
