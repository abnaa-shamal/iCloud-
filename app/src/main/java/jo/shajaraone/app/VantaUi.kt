package jo.shajaraone.app

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val Amber = Color(0xFFF2B300)
val Dim = Color(0xFF9A9A9F)

/** Glass panel: dark gradient body, a diagonal light sheen, and a rim that catches light at the corners. */
fun Modifier.darkGlass(r: Dp = 22.dp): Modifier = glassPanel(r, Color(0xFF2C2C30), Color(0xFF0F0F10), Color(0x2EFFFFFF))

fun Modifier.glassPanel(r: Dp, top: Color, bottom: Color, sheen: Color): Modifier {
    val s = RoundedCornerShape(r)
    return this.clip(s)
        .background(Brush.verticalGradient(listOf(top, bottom)))
        .background(Brush.linearGradient(listOf(sheen, Color.Transparent, Color.Transparent), Offset(0f, 0f), Offset(700f, 900f)))
        .border(1.dp, Brush.linearGradient(listOf(Color(0x77FFFFFF), Color(0x0AFFFFFF), Color(0x40FFFFFF)), Offset(0f, 0f), Offset(600f, 800f)), s)
}

/** Press = a small spring squeeze. */
fun Modifier.squeeze(onClick: () -> Unit = {}): Modifier = composed {
    val src = remember { MutableInteractionSource() }
    val down by src.collectIsPressedAsState()
    val sc by animateFloatAsState(if (down) 0.96f else 1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium), label = "sq")
    this.scale(sc).clickable(src, null) { onClick() }
}

/** Cards rise and fade in one after the other. */
fun Modifier.entrance(index: Int): Modifier = composed {
    var on by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { on = true }
    val a by animateFloatAsState(if (on) 1f else 0f, tween(420, delayMillis = index * 80, easing = FastOutSlowInEasing), label = "ea")
    this.alpha(a).offset(y = (24 * (1f - a)).dp)
}

// ---------- hand-drawn glyphs (no icon library) ----------
@Composable
fun Glyph(kind: Int, color: Color, size: Dp = 22.dp) {
    Canvas(Modifier.size(size)) {
        val w = this.size.width
        val st = Stroke(w * 0.09f, cap = StrokeCap.Round)
        when (kind) {
            0 -> { // home
                val p = Path().apply { moveTo(w * .5f, w * .12f); lineTo(w * .88f, w * .42f); lineTo(w * .88f, w * .88f); lineTo(w * .12f, w * .88f); lineTo(w * .12f, w * .42f); close() }
                drawPath(p, color, style = st)
            }
            1 -> { // car
                drawRoundRect(color, Offset(w * .08f, w * .42f), Size(w * .84f, w * .3f), CornerRadius(w * .1f), style = st)
                drawLine(color, Offset(w * .22f, w * .42f), Offset(w * .32f, w * .22f), st.width, StrokeCap.Round)
                drawLine(color, Offset(w * .32f, w * .22f), Offset(w * .68f, w * .22f), st.width, StrokeCap.Round)
                drawLine(color, Offset(w * .68f, w * .22f), Offset(w * .78f, w * .42f), st.width, StrokeCap.Round)
                drawCircle(color, w * .08f, Offset(w * .3f, w * .76f)); drawCircle(color, w * .08f, Offset(w * .7f, w * .76f))
            }
            2 -> { // ticket
                drawRoundRect(color, Offset(w * .1f, w * .25f), Size(w * .8f, w * .5f), CornerRadius(w * .08f), style = st)
                drawLine(color, Offset(w * .62f, w * .27f), Offset(w * .62f, w * .73f), st.width, StrokeCap.Round, PathEffect.dashPathEffect(floatArrayOf(w * .06f, w * .06f)))
            }
            3 -> { // user
                drawCircle(color, w * .17f, Offset(w * .5f, w * .34f), style = st)
                drawArc(color, 200f, 140f, false, Offset(w * .2f, w * .55f), Size(w * .6f, w * .6f), style = st)
            }
            4 -> { // back arrow (RTL: points right)
                drawLine(color, Offset(w * .25f, w * .5f), Offset(w * .78f, w * .5f), st.width, StrokeCap.Round)
                drawLine(color, Offset(w * .78f, w * .5f), Offset(w * .55f, w * .27f), st.width, StrokeCap.Round)
                drawLine(color, Offset(w * .78f, w * .5f), Offset(w * .55f, w * .73f), st.width, StrokeCap.Round)
            }
            6 -> { // plus
                drawLine(color, Offset(w * .5f, w * .2f), Offset(w * .5f, w * .8f), st.width, StrokeCap.Round)
                drawLine(color, Offset(w * .2f, w * .5f), Offset(w * .8f, w * .5f), st.width, StrokeCap.Round)
            }
            7 -> { // calendar
                drawRoundRect(color, Offset(w * .14f, w * .22f), Size(w * .72f, w * .64f), CornerRadius(w * .1f), style = st)
                drawLine(color, Offset(w * .14f, w * .42f), Offset(w * .86f, w * .42f), st.width, StrokeCap.Round)
                drawLine(color, Offset(w * .34f, w * .12f), Offset(w * .34f, w * .28f), st.width, StrokeCap.Round)
                drawLine(color, Offset(w * .66f, w * .12f), Offset(w * .66f, w * .28f), st.width, StrokeCap.Round)
            }
            5 -> { // sliders
                drawLine(color, Offset(w * .18f, w * .34f), Offset(w * .82f, w * .34f), st.width, StrokeCap.Round)
                drawLine(color, Offset(w * .18f, w * .66f), Offset(w * .82f, w * .66f), st.width, StrokeCap.Round)
                drawCircle(Color.Black, w * .1f, Offset(w * .62f, w * .34f)); drawCircle(color, w * .1f, Offset(w * .62f, w * .34f), style = st)
                drawCircle(Color.Black, w * .1f, Offset(w * .38f, w * .66f)); drawCircle(color, w * .1f, Offset(w * .38f, w * .66f), style = st)
            }
        }
    }
}

@Composable
fun RoundBtn(kind: Int) {
    Box(Modifier.size(46.dp).darkGlass(23.dp), contentAlignment = Alignment.Center) { Glyph(kind, Color.White, 20.dp) }
}

/** Side-view car drawn in code (no photo): metallic body, dark glass, wheels. */
@Composable
fun CarArt(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width; val h = size.height
        fun DrawScope.p(x: Float, y: Float) = Offset(x * w, y * h)
        val body = Path().apply {
            moveTo(w * .03f, h * .70f); lineTo(w * .06f, h * .55f)
            quadraticTo(w * .10f, h * .47f, w * .20f, h * .45f); lineTo(w * .32f, h * .27f)
            quadraticTo(w * .36f, h * .22f, w * .45f, h * .22f); lineTo(w * .66f, h * .22f)
            quadraticTo(w * .73f, h * .23f, w * .78f, h * .31f); lineTo(w * .86f, h * .45f)
            quadraticTo(w * .96f, h * .48f, w * .98f, h * .58f); lineTo(w * .98f, h * .70f); close()
        }
        drawPath(body, Brush.verticalGradient(listOf(Color(0xFFE6E6E9), Color(0xFF55555B)), h * .2f, h * .75f))
        val glass = Path().apply {
            moveTo(w * .26f, h * .45f); lineTo(w * .35f, h * .30f); lineTo(w * .47f, h * .30f); lineTo(w * .47f, h * .45f); close()
            moveTo(w * .51f, h * .45f); lineTo(w * .51f, h * .30f); lineTo(w * .65f, h * .30f); lineTo(w * .75f, h * .45f); close()
        }
        drawPath(glass, Color(0xFF16161A))
        drawLine(Color(0x66000000), p(.05f, .60f), p(.97f, .60f), 3f)
        drawCircle(Amber, w * .018f, p(.955f, .55f))
        for (cx in listOf(.24f, .78f)) {
            drawCircle(Color(0xFF050506), w * .085f, p(cx, .72f))
            drawCircle(Color(0xFF3A3A3F), w * .055f, p(cx, .72f))
            drawCircle(Color(0xFF8D8D93), w * .02f, p(cx, .72f))
        }
    }
}

// ---------- components ----------
/** [selected] = -1 means no day is chosen (every day is shown). */
@Composable
fun DayPills(days: List<Pair<String, String>>, selected: Int, onPick: (Int) -> Unit = {}) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val gap = 8.dp
        val w = (maxWidth - gap * (days.size - 1)) / days.size
        val x by animateDpAsState((w + gap) * selected.coerceAtLeast(0), spring(0.75f, Spring.StiffnessMediumLow), label = "daysel")
        val vis by animateFloatAsState(if (selected >= 0) 1f else 0f, tween(200), label = "dayvis")
        Box(Modifier.offset(x = x).width(w).height(80.dp).alpha(vis).clip(RoundedCornerShape(30.dp)).background(Color.White))
        Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
            days.forEachIndexed { i, (name, num) ->
                val on = i == selected
                Column(
                    Modifier.width(w).height(80.dp).clip(RoundedCornerShape(30.dp))
                        .then(if (on) Modifier else Modifier.background(Color(0xFF2B2B2E)))
                        .clickable(remember { MutableInteractionSource() }, null) { onPick(i) },
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
                ) {
                    Text(name, fontSize = 11.sp, maxLines = 1, color = if (on) Color.Black.copy(alpha = .65f) else Dim, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(6.dp))
                    Text(num, fontSize = 24.sp, fontWeight = FontWeight.SemiBold, color = if (on) Color.Black else Color.White)
                }
            }
        }
    }
}

/** Day pills for the next [count] days: (label, day number). */
fun pillDays(count: Int = 5): List<Pair<String, String>> {
    val names = listOf("الأحد", "الإثنين", "الثلاثاء", "الأربعاء", "الخميس", "الجمعة", "السبت")
    return (0 until count).map { i ->
        val cal = java.util.Calendar.getInstance().apply { timeInMillis = dayStart(i) }
        val label = when (i) { 0 -> "اليوم"; 1 -> "غداً"; else -> names[cal.get(java.util.Calendar.DAY_OF_WEEK) - 1] }
        label to "${cal.get(java.util.Calendar.DAY_OF_MONTH)}"
    }
}

/** One ride as a dark glass card; [actions] go under the price row. */
@Composable
fun TripCard(t: Trip, modifier: Modifier = Modifier, index: Int = 0, nearest: Boolean = false, actions: @Composable ColumnScope.() -> Unit = {}) {
    Column(modifier.entrance(index).darkGlass(24.dp).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(34.dp).clip(CircleShape).background(Color(0xFF333337)), contentAlignment = Alignment.Center) { Glyph(1, Amber, 20.dp) }
            Column(Modifier.weight(1f)) {
                Text("الكابتن ${t.captainName}", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                if (t.carText.isNotBlank()) Text(t.carText, fontSize = 11.sp, color = Dim)
            }
            if (nearest) Box(Modifier.clip(RoundedCornerShape(10.dp)).background(Color(0xFF3A3A3E)).padding(horizontal = 10.dp, vertical = 4.dp)) {
                Text("الأقرب", fontSize = 11.sp, color = Color(0xFFD7D7DB))
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text(clockText(t.departAt), fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text("${dayName(t.departAt)} • ${t.from}", fontSize = 12.sp, color = Dim)
            }
            Box(Modifier.weight(1f).padding(horizontal = 10.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxWidth().height(2.dp)) {
                    drawLine(Color(0x55FFFFFF), Offset(0f, 1f), Offset(size.width, 1f), 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f)))
                }
                Box(Modifier.background(Color(0xFF1B1B1D)).padding(horizontal = 6.dp)) { Glyph(1, Color.White, 20.dp) }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(t.to, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text(if (t.road.isNotBlank() && t.road != NORTH_INNER) t.road else "الوجهة", fontSize = 12.sp, color = Dim)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(priceText(t.price), Modifier.weight(1f), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Box(Modifier.clip(RoundedCornerShape(12.dp)).background(Color(0xFF3A3A3E)).padding(horizontal = 12.dp, vertical = 8.dp)) {
                Text(if (t.seatsFree <= 0L) "مكتملة" else "${t.seatsFree} من ${t.seatsTotal} فاضية", fontSize = 12.sp, color = Color(0xFFD7D7DB))
            }
        }
        actions()
    }
}

/** Floating pill; the selected item expands into an amber capsule with its name. */
@Composable
fun VantaNav(items: List<Pair<Int, String>>, selected: Int, onPick: (Int) -> Unit = {}) {
    Row(
        Modifier.padding(bottom = 14.dp).darkGlass(40.dp).padding(6.dp).animateContentSize(),
        horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically
    ) {
        items.forEachIndexed { i, (g, name) ->
            val on = i == selected
            Row(
                Modifier.height(52.dp).clip(RoundedCornerShape(26.dp))
                    .background(if (on) Amber else Color(0xFF2B2B2E))
                    .clickable(remember { MutableInteractionSource() }, null) { onPick(i) }
                    .padding(horizontal = if (on) 18.dp else 15.dp).animateContentSize(),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Glyph(g, if (on) Color.Black else Color.White, 22.dp)
                if (on) Text(name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.Black)
            }
        }
    }
}

val VantaPageBg = Brush.verticalGradient(listOf(Color(0xFF212123), Color(0xFF050505), Color.Black), 0f, 1400f)


/** Main amber action of the rider app. */
@Composable
fun AmberBtn(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier.fillMaxWidth().height(50.dp).squeeze(onClick).clip(RoundedCornerShape(25.dp)).background(Amber),
        contentAlignment = Alignment.Center
    ) { Text(text, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.Black) }
}

/** Quiet outlined action on dark glass. */
@Composable
fun GhostBtn(text: String, modifier: Modifier = Modifier, color: Color = Color.White, onClick: () -> Unit) {
    Box(
        modifier.height(42.dp).squeeze(onClick).clip(RoundedCornerShape(21.dp)).border(1.dp, color.copy(alpha = .35f), RoundedCornerShape(21.dp)).padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center
    ) { Text(text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = color) }
}
