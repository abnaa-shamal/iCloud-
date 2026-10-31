package jo.abnaashamal.bot

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Same mark as the Abnaa Al-Shamal apps: two northern peaks (ochre + parchment) with a road between them. */
@Composable
fun BotLogo(size: Dp = 40.dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val back = Path().apply {
            moveTo(w * 0.42f, h * 0.90f)
            lineTo(w * 0.70f, h * 0.30f)
            lineTo(w * 0.98f, h * 0.90f)
            close()
        }
        drawPath(back, Color(0xFFFFC857))
        val front = Path().apply {
            moveTo(w * 0.02f, h * 0.90f)
            lineTo(w * 0.34f, h * 0.20f)
            lineTo(w * 0.66f, h * 0.90f)
            close()
        }
        drawPath(front, Color(0xFFF5EFEB))
        val road = Path().apply {
            moveTo(w * 0.30f, h * 0.90f)
            cubicTo(w * 0.62f, h * 0.80f, w * 0.18f, h * 0.60f, w * 0.40f, h * 0.44f)
        }
        drawPath(road, Color(0xFF2D4436), style = Stroke(width = w * 0.085f, cap = StrokeCap.Round))
    }
}
