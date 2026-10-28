package jo.shajaraone.app

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ===== Bottom navigation =====

/** Bottom bar styling shared by the rider and captain apps: deep navy, violet-tinted selection, teal badge. */
@Composable
fun navBarColor(): Color = Brand.Navy.copy(alpha = 0.94f)

@Composable
fun navItemColors() = NavigationBarItemDefaults.colors(
    selectedIconColor = Brand.VioletLight,
    selectedTextColor = Brand.VioletLight,
    unselectedIconColor = Color.White.copy(alpha = 0.55f),
    unselectedTextColor = Color.White.copy(alpha = 0.55f),
    indicatorColor = Brand.Violet.copy(alpha = 0.28f)
)

// ===== Selection chips =====

val SignYellow = Brand.Teal
val SignGreen = Brand.Violet
val BoardBlack = Brand.Navy

/** Ink color for borders and hard shadows (near-black in light mode, light in dark mode). */
@Composable
fun inkColor(): Color = MaterialTheme.colorScheme.onBackground

/** One rectangular choice chip: black with a yellow label when selected, white with a black outline when not. */
@Composable
fun PillChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val bg by animateColorAsState(if (selected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.07f), label = "chipBg")
    val fg by animateColorAsState(if (selected) Color.White else MaterialTheme.colorScheme.onSurface, label = "chipFg")
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = bg,
        border = BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.22f)),
        modifier = Modifier.height(42.dp)
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (selected) Icon(Icons.Filled.Check, null, tint = fg, modifier = Modifier.size(16.dp))
            Text(label, color = fg, style = MaterialTheme.typography.labelLarge)
        }
    }
}

/** Chips that wrap onto several lines. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChoiceChips(options: List<String>, selected: String, onSelect: (String) -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        options.forEach { o -> PillChip(o, o == selected) { onSelect(o) } }
    }
}

/** Chips in one horizontally scrolling line. */
@Composable
fun ScrollChips(options: List<Pair<String, String>>, selectedKey: String, onSelect: (String) -> Unit) {
    Row(
        Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        options.forEach { (key, label) -> PillChip(label, key == selectedKey) { onSelect(key) } }
    }
}

/** Next 7 days as (offset, label). */
fun nextDays(count: Int = 7): List<Pair<String, String>> =
    (0 until count).map { it.toString() to dayName(dayStart(it)) }

@Composable
fun Stepper(value: Int, min: Int, max: Int, onChange: (Int) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(4.dp)
    ) {
        FilledIconButton(
            onClick = { if (value > min) onChange(value - 1) }, enabled = value > min,
            modifier = Modifier.size(40.dp)
        ) { Text("−", fontSize = 22.sp) }
        Text(
            "$value", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold,
            textAlign = TextAlign.Center, modifier = Modifier.width(48.dp)
        )
        FilledIconButton(
            onClick = { if (value < max) onChange(value + 1) }, enabled = value < max,
            modifier = Modifier.size(40.dp)
        ) { Text("+", fontSize = 22.sp) }
    }
}

@Composable
fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp)
    )
}

// ===== Small labels =====

/** Small colored label. */
@Composable
fun Pill(text: String, container: Color, content: Color) {
    Text(
        text,
        color = content,
        style = MaterialTheme.typography.labelMedium,
        modifier = Modifier
            .background(container, CircleShape)
            .padding(horizontal = 12.dp, vertical = 5.dp)
    )
}

@Composable
fun PricePill(price: Double?) {
    Text(
        priceText(price),
        color = Brand.OnTeal,
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier
            .background(SignYellow, RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 5.dp)
    )
}

// ===== Seats =====

/** A top-view car seat: glass outline when free, violet when picked, dim with a lock when taken. */
@Composable
fun SeatShape(state: SeatState, size: Dp = 30.dp, label: String? = null, onClick: (() -> Unit)? = null) {
    val violet = Brand.Violet
    val fill = when (state) {
        SeatState.FREE -> Color.White.copy(alpha = 0.06f)
        SeatState.TAKEN -> Color.White.copy(alpha = 0.10f)
        SeatState.PICKED -> violet
        SeatState.DRIVER -> Color.White.copy(alpha = 0.05f)
    }
    val line = when (state) {
        SeatState.FREE -> Brand.VioletSoft.copy(alpha = 0.75f)
        SeatState.PICKED -> Brand.VioletLight
        SeatState.TAKEN -> Color.White.copy(alpha = 0.18f)
        SeatState.DRIVER -> Color.White.copy(alpha = 0.28f)
    }
    val shape = RoundedCornerShape(topStart = size / 3f, topEnd = size / 3f, bottomStart = size / 5, bottomEnd = size / 5)
    val m = Modifier.size(size, size * 1.15f)
    val inner: @Composable () -> Unit = {
        if (label != null || state == SeatState.PICKED || state == SeatState.TAKEN) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                when (state) {
                    SeatState.PICKED -> Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(size * 0.38f))
                    SeatState.TAKEN -> Icon(Icons.Filled.Lock, null, tint = Color.White.copy(alpha = 0.55f), modifier = Modifier.size(size * 0.3f))
                    else -> {}
                }
                if (label != null) {
                    Text(
                        label, fontWeight = FontWeight.Bold, fontSize = (size.value * 0.36f).sp,
                        color = if (state == SeatState.TAKEN) Color.White.copy(alpha = 0.55f) else Color.White
                    )
                }
            }
        }
    }
    if (onClick != null && (state == SeatState.FREE || state == SeatState.PICKED)) {
        Surface(onClick = onClick, shape = shape, color = fill, border = BorderStroke(1.5.dp, line), modifier = m) {
            Box(contentAlignment = Alignment.Center) { inner() }
        }
    } else {
        Surface(shape = shape, color = fill, border = BorderStroke(1.dp, line), modifier = m) {
            Box(contentAlignment = Alignment.Center) { inner() }
        }
    }
}

enum class SeatState { FREE, TAKEN, PICKED, DRIVER }

/** Row of seat shapes: taken ones greyed, free ones green. */
@Composable
fun SeatsRow(total: Long, free: Long) {
    val n = total.toInt().coerceIn(1, 8)
    val f = free.toInt().coerceIn(0, n)
    Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(n) { i -> SeatShape(if (i < n - f) SeatState.TAKEN else SeatState.FREE, 20.dp) }
    }
}

// ===== Route timeline =====

/** Dot — dashed line — pin, with place names. */
@Composable
fun RouteTimeline(from: String, to: String, road: String = "") {
    val dotColor = MaterialTheme.colorScheme.primary
    val pinColor = MaterialTheme.colorScheme.secondary
    val lineColor = MaterialTheme.colorScheme.outline
    Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Canvas(Modifier.width(16.dp).fillMaxHeight()) {
            val r = 6.dp.toPx()
            val cx = size.width / 2
            drawCircle(dotColor, radius = r, center = Offset(cx, r))
            drawCircle(Color.White, radius = r / 2.2f, center = Offset(cx, r))
            drawLine(
                lineColor, Offset(cx, r * 2 + 4.dp.toPx()), Offset(cx, size.height - r * 2 - 4.dp.toPx()),
                strokeWidth = 2.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f))
            )
            drawCircle(pinColor, radius = r, center = Offset(cx, size.height - r))
        }
        Column(verticalArrangement = Arrangement.SpaceBetween, modifier = Modifier.defaultMinSize(minHeight = 64.dp)) {
            Text(from, style = MaterialTheme.typography.titleMedium)
            if (road.isNotBlank()) {
                Text(
                    "عن طريق $road", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }
            Text(to, style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
fun Avatar(name: String, size: Dp = 44.dp) {
    Box(
        Modifier.size(size).clip(CircleShape).background(BoardBlack),
        contentAlignment = Alignment.Center
    ) {
        Text(
            name.trim().firstOrNull()?.toString() ?: "؟",
            color = SignYellow, fontWeight = FontWeight.ExtraBold, fontSize = (size.value / 2.3f).sp
        )
    }
}

/** Yellow square button with a black outline. */
@Composable
private fun YellowButton(onClick: () -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = Color.White.copy(alpha = 0.10f),
        contentColor = SignYellow,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.22f)),
        modifier = modifier.height(46.dp)
    ) { Box(Modifier.padding(horizontal = 12.dp), contentAlignment = Alignment.Center) { content() } }
}

/** Round icon button used for call / message. */
@Composable
fun RoundAction(icon: ImageVector, desc: String, onClick: () -> Unit) {
    YellowButton(onClick, Modifier.width(48.dp)) {
        Icon(icon, contentDescription = desc, modifier = Modifier.size(20.dp))
    }
}

@Composable
fun CallButton(phone: String) {
    val ctx = LocalContext.current
    RoundAction(Icons.Filled.Call, "اتصال") { callPhone(ctx, phone) }
}

@Composable
fun WhatsAppButton(phone: String, message: String) {
    val ctx = LocalContext.current
    YellowButton({ openWhatsApp(ctx, phone, message) }) {
        Text("واتساب", style = MaterialTheme.typography.labelLarge)
    }
}

/** Departure-board time: yellow digits on black, the day under it. */
@Composable
fun TimeBlock(millis: Long) {
    Column(
        Modifier.background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(14.dp)).border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(14.dp)).padding(horizontal = 14.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(clockText(millis), style = MaterialTheme.typography.titleLarge, color = SignYellow)
        Text(dayName(millis), style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.8f))
    }
}

/** A highway-style sign: green panel, white inner frame, destination big, origin small. */
@Composable
fun RoadSign(from: String, to: String, road: String = "", modifier: Modifier = Modifier) {
    Box(modifier.background(Brush.linearGradient(listOf(Brand.Violet, Color(0xFF3D2BD6))), RoundedCornerShape(18.dp)).padding(5.dp)) {
        Column(
            Modifier.fillMaxWidth()
                .border(1.dp, Color.White.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text("من $from", color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.labelLarge)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("←", color = SignYellow, fontSize = 26.sp, fontWeight = FontWeight.Black)
                Text(to, color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Black)
            }
            if (road.isNotBlank()) {
                Text("عن طريق $road", color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

/** Route, road, time, seats and captain — shared by all trip cards. Time and route on one side, the price on the other, captain and seats underneath. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TripInfo(t: Trip, showCaptain: Boolean = true) {
    val cs = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(clockText(t.departAt), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(dayName(t.departAt), style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant, modifier = Modifier.padding(bottom = 3.dp))
                }
                Text("${t.from}  ←  ${t.to}", style = MaterialTheme.typography.titleMedium)
                if (t.road.isNotBlank()) {
                    Text("عن طريق ${t.road}", style = MaterialTheme.typography.bodyMedium, color = Brand.VioletLight)
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    priceText(t.price), style = MaterialTheme.typography.titleLarge,
                    color = Brand.Teal, fontWeight = FontWeight.Bold
                )
                if (t.price != null) Text("للمقعد", style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
            }
        }
        HorizontalDivider(color = Color.White.copy(alpha = 0.10f))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (showCaptain) {
                Avatar(t.captainName, 38.dp)
                Column(Modifier.weight(1f)) {
                    Text("الكابتن ${t.captainName}", style = MaterialTheme.typography.titleSmall)
                    if (t.carText.isNotBlank()) {
                        Text(t.carText, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                    }
                }
            } else {
                Spacer(Modifier.weight(1f))
            }
            when (t.status) {
                TRIP_CANCELLED -> Pill("ملغاة", cs.errorContainer, cs.onErrorContainer)
                TRIP_FULL -> Pill("مكتملة", cs.tertiaryContainer, cs.onTertiaryContainer)
                else -> Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    SeatsRow(t.seatsTotal, t.seatsFree)
                    Text(
                        "${seatsText(t.seatsFree)} شاغرة", style = MaterialTheme.typography.labelLarge,
                        color = Brand.Teal, fontWeight = FontWeight.Bold
                    )
                }
            }
        }
        if (t.acceptsParcels) {
            Pill("يقبل أمانات", Color.White.copy(alpha = 0.10f), cs.onSurface)
        }
        if (t.note.isNotBlank()) {
            Text(t.note, color = cs.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        }
        if (t.status == TRIP_CANCELLED && t.cancelReason.isNotBlank()) {
            Text("السبب: ${t.cancelReason}", color = cs.error)
        }
    }
}

/** Glass card: translucent fill, hairline border. */
@Composable
fun AppCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(24.dp)
    Box(
        modifier
            .fillMaxWidth()
            .background(Color.White.copy(alpha = 0.08f), shape)
            .border(1.dp, Color.White.copy(alpha = 0.16f), shape)
    ) {
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
        }
    }
}

/** Header: app title and a greeting over a soft violet glow, with a thin teal-violet line underneath. */
@Composable
fun HeroHeader(title: String, subtitle: String, trailing: @Composable (() -> Unit)? = null) {
    Column(Modifier.fillMaxWidth()) {
        Box(
            Modifier.fillMaxWidth().statusBarsPadding()
                .padding(start = 20.dp, end = 12.dp, top = 12.dp, bottom = 14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AppLogo(46.dp)
                Column(Modifier.weight(1f)) {
                    Text(subtitle, color = SignYellow, style = MaterialTheme.typography.labelLarge)
                    Text(title, color = Color.White, style = MaterialTheme.typography.headlineSmall)
                }
                trailing?.invoke()
            }
        }
        Box(Modifier.fillMaxWidth().height(2.dp).background(Brush.horizontalGradient(listOf(Brand.Violet, Brand.Teal))))
    }
}

@Composable
fun EmptyState(emoji: String, text: String) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 48.dp, horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier.size(96.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) { Text(emoji, fontSize = 44.sp) }
        Spacer(Modifier.height(14.dp))
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

@Composable
fun LoadingBox() {
    Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

/** Primary action: solid violet, white label. */
@Composable
fun BigButton(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    Surface(
        onClick = onClick, enabled = enabled,
        shape = RoundedCornerShape(16.dp),
        color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = Color.White,
        modifier = Modifier.fillMaxWidth().height(56.dp)
    ) {
        Box(contentAlignment = Alignment.Center) { Text(text, style = MaterialTheme.typography.titleMedium) }
    }
}

/** Ask for a reason before cancelling. */
@Composable
fun ReasonDialog(title: String, confirmText: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var reason by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = reason, onValueChange = { reason = it.take(120) },
                label = { Text("السبب") }, placeholder = { Text("مثلاً: ظرف طارئ") },
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(reason.trim()) }, enabled = reason.isNotBlank()) {
                Text(confirmText, color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("رجوع") } }
    )
}


/** Bottom bar: reverse number verification. One tap opens WhatsApp to the support number with the code ready. */
@Composable
fun VerifyBar(me: UserProfile) {
    val ctx = LocalContext.current
    Surface(color = MaterialTheme.colorScheme.primaryContainer, tonalElevation = 3.dp) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(Modifier.weight(1f)) {
                Text("وثّق رقمك", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                Text(
                    "ابعت رمزك ${me.verifyCode} من رقمك على واتساب الدعم",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            Button(onClick = {
                openWhatsApp(
                    ctx, SUPPORT_PHONE,
                    "رمز التحقق: ${me.verifyCode}\nالاسم: ${me.name}\nالرقم: ${me.phone}\n" +
                        if (IS_CAPTAIN_APP) "تطبيق الكابتن" else "تطبيق الراكب"
                )
            }) { Text("إرسال") }
        }
    }
}
