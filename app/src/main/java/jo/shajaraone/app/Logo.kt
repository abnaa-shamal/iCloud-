package jo.shajaraone.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private val LogoFront = Color(0xFFF5EFEB)   // parchment peak
private val LogoBack get() = Brand.Ochre          // ochre peak

/** The app mark: two northern peaks (violet and teal) with a road winding between them and a small star. Drawn in code so it stays sharp at any size. */
@Composable
fun AppLogo(size: Dp = 56.dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height

        // Back peak (teal), right side.
        val teal = Path().apply {
            moveTo(w * 0.42f, h * 0.90f)
            lineTo(w * 0.70f, h * 0.30f)
            lineTo(w * 0.98f, h * 0.90f)
            close()
        }
        drawPath(teal, Brush.linearGradient(listOf(Color(0xFFEBC476), LogoBack), Offset(w * 0.7f, h * 0.3f), Offset(w * 0.7f, h * 0.9f)))

        // Front peak (violet), left side.
        val violet = Path().apply {
            moveTo(w * 0.02f, h * 0.90f)
            lineTo(w * 0.34f, h * 0.20f)
            lineTo(w * 0.66f, h * 0.90f)
            close()
        }
        drawPath(violet, Brush.linearGradient(listOf(Color.White, Color(0xFFE6DACB)), Offset(w * 0.34f, h * 0.2f), Offset(w * 0.34f, h * 0.9f)))

        // The road: an S-curve climbing the front peak, with a dashed centre line.
        val road = Path().apply {
            moveTo(w * 0.30f, h * 0.90f)
            cubicTo(w * 0.62f, h * 0.80f, w * 0.18f, h * 0.60f, w * 0.40f, h * 0.44f)
        }
        drawPath(road, Brand.Violet, style = Stroke(width = w * 0.085f, cap = StrokeCap.Round))
        drawPath(
            road, Brand.Ochre,
            style = Stroke(
                width = w * 0.016f, cap = StrokeCap.Round,
                pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(w * 0.05f, w * 0.05f))
            )
        )

        // Four-point star above the peaks.
        val cx = w * 0.20f
        val cy = h * 0.12f
        val r = w * 0.085f
        val star = Path().apply {
            moveTo(cx, cy - r)
            quadraticTo(cx, cy, cx + r, cy)
            quadraticTo(cx, cy, cx, cy + r)
            quadraticTo(cx, cy, cx - r, cy)
            quadraticTo(cx, cy, cx, cy - r)
            close()
        }
        drawPath(star, Brand.Ochre)
    }
}
