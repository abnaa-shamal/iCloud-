package jo.shajaraone.app

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * «Neon glass» building blocks: glow ring, sliding tabs, glass nav, ride tile, spring press, shimmer.
 * All colors come from Brand (cyan = Violet token, pink = Teal token), so Theme.kt stays the single place to recolor.
 */
private const val ALL = "all"
private val GlassFill = Brush.verticalGradient(listOf(Color(0x26FFFFFF), Color(0x0AFFFFFF)))

fun Modifier.glass(radius: Dp = 22.dp): Modifier {
    val shape = RoundedCornerShape(radius)
    return this.clip(shape).background(GlassFill)
        .border(1.dp, Brush.linearGradient(listOf(Brand.Violet.copy(alpha = .55f), Color(0x22FFFFFF), Brand.Teal.copy(alpha = .55f))), shape)
}

/** Spring press: the element bounces slightly when touched, plus a short vibration. */
fun Modifier.springPress(onClick: () -> Unit): Modifier = composed {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val sc by animateFloatAsState(
        if (pressed) 0.95f else 1f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium), label = "press"
    )
    val ctx = LocalContext.current
    this.scale(sc).clickable(interactionSource = src, indication = null) { AppFeedback.tap(ctx); onClick() }
}

/** Text with a light sweep passing over it (shimmer). */
@Composable
fun ShimmerText(text: String, fontSize: Int = 14, weight: FontWeight = FontWeight.Bold) {
    val inf = rememberInfiniteTransition(label = "shimmer")
    val x by inf.animateFloat(-200f, 600f, infiniteRepeatable(tween(3200, easing = LinearEasing)), label = "sx")
    Text(
        text, fontSize = fontSize.sp, fontWeight = weight,
        style = TextStyle(brush = Brush.linearGradient(listOf(Brand.Violet, Color.White, Brand.Teal, Brand.Violet), Offset(x, 0f), Offset(x + 260f, 0f)))
    )
}

/** Glowing ring, [fraction] of it lit (cyan to pink). The glow breathes slowly (pulse). */
@Composable
fun GlowRing(fraction: Float, size: Dp, stroke: Dp = 7.dp, content: @Composable BoxScope.() -> Unit) {
    val inf = rememberInfiniteTransition(label = "breath")
    val breath by inf.animateFloat(0.55f, 1f, infiniteRepeatable(tween(2600, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "b")
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize().padding(16.dp)) {
            val sw = stroke.toPx()
            val sweep = Brush.sweepGradient(listOf(Brand.Violet, Brand.Violet, Brand.Teal, Brand.Teal, Brand.Violet), center)
            drawCircle(Color(0x22FFFFFF), radius = this.size.minDimension / 2, style = Stroke(sw * .5f))
            val f = fraction.coerceIn(0.02f, 1f)
            for ((w, a) in listOf(5f to .08f, 3.2f to .14f, 2f to .24f)) {
                drawArc(sweep, -90f, 360f * f, false, Offset.Zero, Size(this.size.width, this.size.height), alpha = a * breath, style = Stroke(sw * w, cap = StrokeCap.Round))
            }
            drawArc(sweep, -90f, 360f * f, false, Offset.Zero, Size(this.size.width, this.size.height), style = Stroke(sw, cap = StrokeCap.Round))
        }
        content()
    }
}

/** Glass segmented tabs whose glowing indicator slides to the chosen option. */
@Composable
fun SlidingTabs(labels: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier.fillMaxWidth().glass(20.dp).padding(5.dp)) {
        val seg = maxWidth / labels.size
        val x by animateDpAsState(seg * selected, spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow), label = "slide")
        Box(
            Modifier.offset(x = x).width(seg).height(40.dp).clip(RoundedCornerShape(15.dp))
                .background(Brush.linearGradient(listOf(Brand.Violet.copy(alpha = .6f), Brand.Teal.copy(alpha = .6f))))
        )
        Row(Modifier.fillMaxWidth()) {
            labels.forEachIndexed { i, l ->
                Box(Modifier.width(seg).height(40.dp).clickable(remember { MutableInteractionSource() }, null) { onSelect(i) }, contentAlignment = Alignment.Center) {
                    Text(l, fontSize = 13.sp, maxLines = 1, color = if (i == selected) Color.White else Color(0x99FFFFFF), fontWeight = if (i == selected) FontWeight.Bold else FontWeight.Normal)
                }
            }
        }
    }
}

/** Bottom bar: glass pill, glowing indicator slides between the items. */
@Composable
fun GlassNav(items: List<Pair<ImageVector, String>>, selected: Int, onSelect: (Int) -> Unit) {
    Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
        BoxWithConstraints(Modifier.fillMaxWidth().glass(26.dp).padding(6.dp)) {
            val seg = maxWidth / items.size
            val x by animateDpAsState(seg * selected, spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow), label = "navslide")
            Box(
                Modifier.offset(x = x).width(seg).height(48.dp).clip(RoundedCornerShape(20.dp))
                    .background(Brush.linearGradient(listOf(Brand.Violet.copy(alpha = .55f), Brand.Teal.copy(alpha = .55f))))
            )
            Row(Modifier.fillMaxWidth()) {
                items.forEachIndexed { i, (icon, label) ->
                    Row(
                        Modifier.width(seg).height(48.dp).clickable(remember { MutableInteractionSource() }, null) { onSelect(i) },
                        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(icon, null, tint = if (i == selected) Color.White else Color(0x99FFFFFF), modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(label, fontSize = 14.sp, color = if (i == selected) Color.White else Color(0x99FFFFFF), fontWeight = if (i == selected) FontWeight.Bold else FontWeight.Normal)
                    }
                }
            }
        }
    }
}

/** One ride as a glass tile: big light time, route in cyan, seat lamps, captain row, then the actions. */
@Composable
fun RideTile(t: Trip, modifier: Modifier = Modifier, actions: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth().glass().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(dayName(t.departAt), fontSize = 12.sp, color = Color(0x99FFFFFF))
                Text(clockText(t.departAt), fontSize = 40.sp, fontWeight = FontWeight.ExtraLight, color = Color.White, lineHeight = 42.sp)
                Text("${t.from} ← ${t.to}", fontSize = 16.sp, color = Brand.Violet, fontWeight = FontWeight.Medium)
                if (t.road.isNotBlank()) Text("عن طريق ${t.road}", fontSize = 12.sp, color = Color(0x99FFFFFF))
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (t.seatsFree <= 0L) Text("مكتملة", color = Brand.Teal, fontWeight = FontWeight.Bold)
                else {
                    SeatDots(t.seatsFree.toInt(), t.seatsTotal.toInt())
                    Text("${t.seatsFree} من ${t.seatsTotal} فاضي", fontSize = 12.sp, color = Color(0xCCFFFFFF), modifier = Modifier.padding(top = 6.dp))
                }
                if (t.acceptsParcels) Text("يقبل أمانات", fontSize = 11.sp, color = Brand.Teal, modifier = Modifier.padding(top = 4.dp))
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(34.dp).clip(CircleShape).background(Brush.linearGradient(listOf(Brand.Violet, Brand.Teal))), contentAlignment = Alignment.Center) {
                Text(t.captainName.take(1), color = Color.White, fontWeight = FontWeight.Bold)
            }
            Column {
                Text("الكابتن ${t.captainName}", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                if (t.carText.isNotBlank()) Text(t.carText, fontSize = 12.sp, color = Color(0x99FFFFFF))
            }
        }
        actions()
    }
}

/**
 * The rider's top area: glow ring for the next ride, sliding direction tabs, and OPTIONAL filters (place / road / day / time).
 * Only the direction tabs matter; leave everything else untouched and every ride is shown.
 */
fun LazyListScope.neonHeader(
    shown: List<Trip>, route: RouteState, day: Int, mins: Int,
    onDay: (Int) -> Unit, onMins: (Int) -> Unit, countText: String
) {
    item {
        val next = shown.firstOrNull()
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            ShimmerText("أبناء الشمال", 15)
            GlowRing(if (next == null || next.seatsTotal == 0L) 0.02f else next.seatsFree.toFloat() / next.seatsTotal, 230.dp) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (next == null) Text("ما في رحلات", fontSize = 18.sp, color = Color(0xAAFFFFFF))
                    else {
                        Text(clockText(next.departAt), fontSize = 13.sp, color = Color(0xAAFFFFFF))
                        Text("${next.seatsFree} من ${next.seatsTotal}", fontSize = 40.sp, fontWeight = FontWeight.ExtraLight, color = Color.White)
                        Text("مقاعد فاضية", fontSize = 12.sp, color = Brand.Violet)
                    }
                }
            }
            if (next != null) Text("${next.from} ← ${next.to}", fontSize = 17.sp, fontWeight = FontWeight.Light, color = Color.White)
        }
    }
    item {
        val kinds = listOf("", KIND_NS, KIND_SN, KIND_NN)
        SlidingTabs(listOf("الكل", "شمال ← جنوب", "جنوب ← شمال", "داخل الشمال"), kinds.indexOf(route.kind).coerceAtLeast(0), { i ->
            if (kinds[i].isBlank()) { route.kind = ""; route.north = ""; route.road = ""; route.south = ""; route.spot = "" } else route.chooseKind(kinds[i])
        })
    }
    item {
        var open by remember { mutableStateOf(false) }
        Column(Modifier.fillMaxWidth().glass(18.dp).padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth().clickable(remember { MutableInteractionSource() }, null) { open = !open }, verticalAlignment = Alignment.CenterVertically) {
                Text("تخصيص (اختياري): المكان والطريق والوقت", Modifier.weight(1f), fontSize = 13.sp, color = Color(0xCCFFFFFF))
                Text(if (open) "▴" else "▾", color = Brand.Violet)
            }
            if (open) {
                if (route.kind.isNotBlank()) {
                    Text("البلد بالشمال", fontSize = 12.sp, color = Color(0x99FFFFFF))
                    ScrollChips(listOf(ALL to "أي بلد") + northPlaces.map { it to it }, route.north.ifBlank { ALL }) { route.north = if (it == ALL) "" else it }
                    if (route.kind != KIND_NN) {
                        Text("الطريق", fontSize = 12.sp, color = Color(0x99FFFFFF))
                        ScrollChips(listOf(ALL to "أي طريق") + southRoads.keys.map { it to it }, route.road.ifBlank { ALL }) { if (it == ALL) route.road = "" else route.chooseRoad(it) }
                    }
                }
                Text("اليوم", fontSize = 12.sp, color = Color(0x99FFFFFF))
                ScrollChips(listOf(ALL to "أي يوم") + nextDays(), if (day < 0) ALL else day.toString()) { onDay(if (it == ALL) -1 else it.toInt()) }
            }
        }
    }
    item { Text(countText, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 4.dp)) }
}
