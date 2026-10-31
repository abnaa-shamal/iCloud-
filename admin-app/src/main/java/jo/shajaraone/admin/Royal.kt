package jo.shajaraone.admin

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

fun DrawScope.drawCrown(left: Float, top: Float, w: Float, h: Float) {
    val p = Path().apply {
        moveTo(left + 0.10f * w, top + 0.80f * h)
        lineTo(left + 0.04f * w, top + 0.28f * h)
        lineTo(left + 0.28f * w, top + 0.52f * h)
        lineTo(left + 0.50f * w, top + 0.08f * h)
        lineTo(left + 0.72f * w, top + 0.52f * h)
        lineTo(left + 0.96f * w, top + 0.28f * h)
        lineTo(left + 0.90f * w, top + 0.80f * h)
        close()
    }
    val brush = Brush.verticalGradient(listOf(Royal.GoldLight, Royal.Gold, Royal.GoldDeep), startY = top, endY = top + h)
    drawPath(p, brush)
    drawRoundRect(brush, Offset(left + 0.10f * w, top + 0.85f * h), Size(0.80f * w, 0.11f * h),
        androidx.compose.ui.geometry.CornerRadius(h * 0.05f))
    val r = 0.045f * w
    drawCircle(Royal.Ivory, r, Offset(left + 0.04f * w, top + 0.24f * h))
    drawCircle(Royal.Ivory, r * 1.25f, Offset(left + 0.50f * w, top + 0.04f * h))
    drawCircle(Royal.Ivory, r, Offset(left + 0.96f * w, top + 0.24f * h))
    drawCircle(Royal.Ruby, r * 0.7f, Offset(left + 0.50f * w, top + 0.72f * h))
}

private fun DrawScope.diamond(c: Offset, r: Float, color: Color) {
    val p = Path().apply { moveTo(c.x, c.y - r); lineTo(c.x + r, c.y); lineTo(c.x, c.y + r); lineTo(c.x - r, c.y); close() }
    drawPath(p, color)
}

/** The ornament at the bottom of the screens: a crown between two gold flourishes, then the credit line. */
@Composable
fun RoyalFooter(modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(vertical = 18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(Modifier.fillMaxWidth().height(52.dp)) {
            val cx = size.width / 2
            val cy = size.height / 2
            val cw = 58.dp.toPx()
            val ch = 44.dp.toPx()
            drawCrown(cx - cw / 2, cy - ch / 2, cw, ch)
            val gap = cw / 2 + 12.dp.toPx()
            val len = (size.width / 2 - gap - 20.dp.toPx()).coerceAtLeast(10f)
            for (dir in listOf(-1f, 1f)) {
                val x0 = cx + dir * gap
                val x1 = cx + dir * (gap + len)
                drawLine(Brush.horizontalGradient(if (dir < 0) listOf(Royal.GoldDeep.copy(alpha = 0f), Royal.Gold) else listOf(Royal.Gold, Royal.GoldDeep.copy(alpha = 0f)),
                    startX = minOf(x0, x1), endX = maxOf(x0, x1)), Offset(x0, cy), Offset(x1, cy), strokeWidth = 2f)
                diamond(Offset(x0 + dir * 6.dp.toPx(), cy), 4.dp.toPx(), Royal.Gold)
                diamond(Offset(x0 + dir * 22.dp.toPx(), cy), 2.5.dp.toPx(), Royal.GoldLight)
                drawCircle(Royal.Gold, 2.dp.toPx(), Offset(x1 + dir * 8.dp.toPx(), cy))
                // small curl above and below the line
                drawLine(Royal.GoldDeep, Offset(x0 + dir * 40.dp.toPx(), cy), Offset(x0 + dir * 52.dp.toPx(), cy - 9.dp.toPx()), strokeWidth = 2f)
                drawLine(Royal.GoldDeep, Offset(x0 + dir * 40.dp.toPx(), cy), Offset(x0 + dir * 52.dp.toPx(), cy + 9.dp.toPx()), strokeWidth = 2f)
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "Developed & Managed by Abo-tala",
            color = Royal.Gold, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, letterSpacing = 0.5.sp, textAlign = TextAlign.Center
        )
        Text("أبناء الشمال", color = Royal.Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
    }
}

@Composable
fun RoyalCard(modifier: Modifier = Modifier, accent: Color = Royal.GoldDeep, content: @Composable ColumnScope.() -> Unit) {
    val s = RoundedCornerShape(24.dp)
    Column(
        modifier.fillMaxWidth().clip(s)
            .background(Brush.verticalGradient(listOf(Color(0xFF292214), Color(0xFF12100B))))
            .background(Brush.linearGradient(listOf(Color(0xFFD6A52C).copy(alpha = 0.16f), Color.Transparent, Color.Transparent), Offset(0f, 0f), Offset(700f, 900f)))
            .border(1.dp, Brush.linearGradient(listOf(Color(0x77FFFFFF), accent.copy(alpha = 0.10f), Color(0x40FFFFFF)), Offset(0f, 0f), Offset(600f, 800f)), s)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp), content = content
    )
}

@Composable
fun Badge(text: String, color: Color) {
    Box(Modifier.background(color.copy(alpha = 0.18f), RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 3.dp)) {
        Text(text, color = color, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun GoldButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Button(
        onClick, modifier, enabled = enabled, shape = RoundedCornerShape(26.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Royal.Gold, contentColor = Color.White),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
    ) { Text(text, fontWeight = FontWeight.Bold) }
}

@Composable
fun GhostButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, color: Color = Royal.Gold) {
    OutlinedButton(
        onClick, modifier, shape = RoundedCornerShape(26.dp), border = BorderStroke(1.dp, color.copy(alpha = 0.8f)),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = color),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
    ) { Text(text, fontWeight = FontWeight.Bold) }
}

@Composable
fun ScreenTitle(title: String, subtitle: String? = null) {
    Column(Modifier.padding(top = 6.dp, bottom = 2.dp)) {
        Text(title, color = Royal.GoldLight, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
        if (subtitle != null) Text(subtitle, color = Royal.Muted, fontSize = 13.sp)
        Box(Modifier.padding(top = 6.dp).width(64.dp).height(3.dp).background(Royal.GoldBrush, RoundedCornerShape(2.dp)))
    }
}

@Composable
fun StatTile(value: String, label: String, color: Color, modifier: Modifier = Modifier) {
    val s = RoundedCornerShape(22.dp)
    Column(
        modifier.clip(s)
            .background(Brush.verticalGradient(listOf(Color(0xFF292214), Color(0xFF12100B))))
            .background(Brush.linearGradient(listOf(color.copy(alpha = 0.16f), Color.Transparent, Color.Transparent), Offset(0f, 0f), Offset(500f, 600f)))
            .border(1.dp, Brush.linearGradient(listOf(Color(0x77FFFFFF), Color(0x0AFFFFFF), Color(0x40FFFFFF)), Offset(0f, 0f), Offset(500f, 600f)), s)
            .padding(14.dp)
    ) {
        Text(value, color = color, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold)
        Text(label, color = Royal.Muted, fontSize = 13.sp)
    }
}
