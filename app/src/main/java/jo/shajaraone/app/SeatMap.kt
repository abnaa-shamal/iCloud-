package jo.shajaraone.app

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private enum class SeatKind { FREE, TAKEN, PICKED, DRIVER }

/** One seat seen from above: glass when free, amber when picked, dim when taken. The driver seat shows a steering wheel. */
@Composable
private fun GlassSeat(kind: SeatKind, label: String, onClick: (() -> Unit)?) {
    val fill by animateColorAsState(
        when (kind) { SeatKind.PICKED -> Amber; SeatKind.TAKEN -> Color(0x33000000); SeatKind.DRIVER -> Color(0x10FFFFFF); SeatKind.FREE -> Color(0x22FFFFFF) }, label = "seatfill"
    )
    val sc by animateFloatAsState(if (kind == SeatKind.PICKED) 1.06f else 1f, spring(Spring.DampingRatioMediumBouncy), label = "seatsc")
    val rim = if (kind == SeatKind.PICKED) Color(0xFFFFE08A) else Color(0x40FFFFFF)
    val shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 12.dp, bottomEnd = 12.dp)
    Box(
        Modifier.size(56.dp, 64.dp).scale(sc).clip(shape).background(fill).border(1.2.dp, rim, shape)
            .then(if (onClick != null) Modifier.clickable(remember { MutableInteractionSource() }, null) { onClick() } else Modifier),
        contentAlignment = Alignment.Center
    ) {
        // headrest
        Box(Modifier.align(Alignment.TopCenter).padding(top = 5.dp).size(26.dp, 6.dp).clip(RoundedCornerShape(3.dp)).background(Color(0x33FFFFFF)))
        when (kind) {
            SeatKind.DRIVER -> Canvas(Modifier.size(30.dp)) {
                val c = Offset(size.width / 2, size.height / 2)
                drawCircle(Color(0x99FFFFFF), size.width * .42f, c, style = Stroke(3f))
                drawLine(Color(0x99FFFFFF), c, Offset(c.x, c.y - size.width * .42f), 3f)
                drawLine(Color(0x99FFFFFF), c, Offset(c.x - size.width * .36f, c.y + size.width * .2f), 3f)
                drawLine(Color(0x99FFFFFF), c, Offset(c.x + size.width * .36f, c.y + size.width * .2f), 3f)
            }
            SeatKind.TAKEN -> Text("محجوز", fontSize = 10.sp, color = Color(0x88FFFFFF))
            SeatKind.PICKED -> Text("✓", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.Black)
            SeatKind.FREE -> Text(label, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
        }
    }
}

/** The inside of the car from above: glass body, windshield and dashboard, seats you can tap. */
@Composable
internal fun CarSeatMap(total: Int, free: Int, picked: Set<Int>, onToggle: (Int) -> Unit) {
    val taken = (total - free).coerceAtLeast(0)
    fun kind(i: Int) = when { i < taken -> SeatKind.TAKEN; i in picked -> SeatKind.PICKED; else -> SeatKind.FREE }
    Box(
        Modifier.width(284.dp).drawBehind {
            val w = size.width; val h = size.height
            val side = 14.dp.toPx()
            // wheels
            for (y in listOf(h * .16f, h * .72f)) {
                drawRoundRect(Color(0xFF1B1B1E), Offset(0f, y), Size(side + 6.dp.toPx(), 52.dp.toPx()), CornerRadius(8.dp.toPx()))
                drawRoundRect(Color(0xFF1B1B1E), Offset(w - side - 6.dp.toPx(), y), Size(side + 6.dp.toPx(), 52.dp.toPx()), CornerRadius(8.dp.toPx()))
            }
            // mirrors
            drawRoundRect(Color(0xFF2A2A2E), Offset(2.dp.toPx(), h * .24f), Size(14.dp.toPx(), 9.dp.toPx()), CornerRadius(4.dp.toPx()))
            drawRoundRect(Color(0xFF2A2A2E), Offset(w - 16.dp.toPx(), h * .24f), Size(14.dp.toPx(), 9.dp.toPx()), CornerRadius(4.dp.toPx()))
            // body
            val bodyTl = Offset(side, 0f); val bodySz = Size(w - side * 2, h)
            drawRoundRect(Brush.verticalGradient(listOf(Color(0x40FFFFFF), Color(0x10FFFFFF))), bodyTl, bodySz, CornerRadius(96.dp.toPx()))
            drawRoundRect(Brush.linearGradient(listOf(Color(0x99FFFFFF), Color(0x10FFFFFF), Color(0x55FFFFFF)), Offset(0f, 0f), Offset(w, h)), bodyTl, bodySz, CornerRadius(96.dp.toPx()), style = Stroke(1.5.dp.toPx()))
            // windshield
            val ws = Path().apply {
                moveTo(side + 38.dp.toPx(), 70.dp.toPx()); lineTo(w - side - 38.dp.toPx(), 70.dp.toPx())
                lineTo(w - side - 26.dp.toPx(), 26.dp.toPx()); quadraticTo(w / 2, 6.dp.toPx(), side + 26.dp.toPx(), 26.dp.toPx()); close()
            }
            drawPath(ws, Brush.verticalGradient(listOf(Color(0x66000000), Color(0x22FFFFFF)), 0f, 70.dp.toPx()))
            drawPath(ws, Color(0x33FFFFFF), style = Stroke(1.dp.toPx()))
            // dashboard
            drawRoundRect(Color(0x22FFFFFF), Offset(side + 24.dp.toPx(), 78.dp.toPx()), Size(w - side * 2 - 48.dp.toPx(), 8.dp.toPx()), CornerRadius(4.dp.toPx()))
            // rear window
            drawRoundRect(Color(0x22FFFFFF), Offset(side + 40.dp.toPx(), h - 44.dp.toPx()), Size(w - side * 2 - 80.dp.toPx(), 18.dp.toPx()), CornerRadius(9.dp.toPx()), style = Stroke(1.dp.toPx()))
        }.padding(start = 42.dp, end = 42.dp, top = 100.dp, bottom = 64.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(26.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                if (total >= 1) GlassSeat(kind(0), "1") { onToggle(0) } else Spacer(Modifier.size(56.dp, 64.dp))
                GlassSeat(SeatKind.DRIVER, "", null)
            }
            (1 until total).toList().chunked(3).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    row.forEach { i -> GlassSeat(kind(i), "${i + 1}") { onToggle(i) } }
                    repeat(3 - row.size) { Spacer(Modifier.size(56.dp, 64.dp)) }
                }
            }
        }
    }
}

@Composable
private fun LegendDot(text: String, fill: Color, rim: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(14.dp).clip(RoundedCornerShape(5.dp)).background(fill).border(1.dp, rim, RoundedCornerShape(5.dp)))
        Text(text, fontSize = 12.sp, color = Dim)
    }
}

@Composable
internal fun SeatLegend() {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        LegendDot("شاغر", Color(0x22FFFFFF), Color(0x55FFFFFF))
        LegendDot("محجوز", Color(0x33000000), Color(0x55FFFFFF))
        LegendDot("اختيارك", Amber, Color(0xFFFFE08A))
    }
}

/** The booking sheet content: trip header, the car, drop-off point, send. */
@Composable
internal fun BookingBody(
    t: Trip, total: Int, free: Int, picked: Set<Int>, onToggle: (Int) -> Unit,
    areas: List<String>?, point: String, onPoint: (String) -> Unit, other: String, onOther: (String) -> Unit,
    sending: Boolean, onSend: () -> Unit
) {
    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding().imePadding().padding(horizontal = 18.dp).padding(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp), horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(Modifier.fillMaxWidth().darkGlass(24.dp).padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("${t.from}  ←  ${t.to}", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Text("${timeText(t.departAt)}  •  ${priceText(t.price)}", fontSize = 13.sp, color = Dim)
        }
        Text("اضغط على المقعد اللي بدك ياه", fontSize = 14.sp, color = Dim)
        CarSeatMap(total, free, picked, onToggle)
        SeatLegend()
        Text("اخترت ${seatsText(picked.size.toLong())}", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Amber)
        GlassSection("وين بدك تنزل؟") {
            if (areas != null) {
                GlassGrid(areas + ANY_PLACE + OTHER_PLACE, point, 2, onPoint)
                if (point == OTHER_PLACE) OutlinedTextField(value = other, onValueChange = onOther, label = { Text("اكتب المكان") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            } else {
                OutlinedTextField(
                    value = other, onValueChange = onOther,
                    label = { Text(if (t.to == IRBID_OTHER) "وين بالضبط بإربد؟" else "نقطة النزول (اختياري)") },
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
            }
        }
        AmberBtn(if (sending) "لحظة..." else "أرسل الطلب") { if (!sending) onSend() }
    }
}
