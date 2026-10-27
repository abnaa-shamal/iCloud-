package jo.shajaraone.app

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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

// ===== Selection chips =====

/** One rounded choice pill: filled with the brand color when selected, outlined when not. */
@Composable
fun PillChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val bg by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerLowest,
        label = "chipBg"
    )
    val fg by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
        label = "chipFg"
    )
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = bg,
        border = if (selected) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
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
fun PricePill(price: Double?) = Pill(
    priceText(price),
    MaterialTheme.colorScheme.secondaryContainer,
    MaterialTheme.colorScheme.onSecondaryContainer
)

// ===== Seats =====

/** A small top-view car seat. */
@Composable
fun SeatShape(state: SeatState, size: Dp = 30.dp, onClick: (() -> Unit)? = null) {
    val cs = MaterialTheme.colorScheme
    val fill = when (state) {
        SeatState.FREE -> cs.primaryContainer
        SeatState.TAKEN -> cs.surfaceContainerHighest
        SeatState.PICKED -> cs.primary
        SeatState.DRIVER -> cs.onSurfaceVariant
    }
    val line = when (state) {
        SeatState.FREE -> cs.primary
        SeatState.PICKED -> cs.primary
        else -> Color.Transparent
    }
    val shape = RoundedCornerShape(topStart = size / 2.5f, topEnd = size / 2.5f, bottomStart = size / 6, bottomEnd = size / 6)
    val m = Modifier.size(size, size * 1.1f)
    if (onClick != null && (state == SeatState.FREE || state == SeatState.PICKED)) {
        Surface(onClick = onClick, shape = shape, color = fill, border = BorderStroke(1.5.dp, line), modifier = m) {
            if (state == SeatState.PICKED) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Check, null, tint = cs.onPrimary, modifier = Modifier.size(size / 2))
                }
            }
        }
    } else {
        Surface(shape = shape, color = fill, border = if (line == Color.Transparent) null else BorderStroke(1.5.dp, line), modifier = m) {}
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
        Modifier.size(size).clip(CircleShape).background(
            Brush.linearGradient(listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.tertiary))
        ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            name.trim().firstOrNull()?.toString() ?: "؟",
            color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = (size.value / 2.3f).sp
        )
    }
}

/** Round icon button used for call / message. */
@Composable
fun RoundAction(icon: ImageVector, desc: String, onClick: () -> Unit) {
    FilledTonalIconButton(onClick = onClick, modifier = Modifier.size(44.dp)) {
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
    RoundAction(Icons.AutoMirrored.Filled.Send, "واتساب") { openWhatsApp(ctx, phone, message) }
}

/** Big time with the day under it: «7:30» / «اليوم». */
@Composable
fun TimeBlock(millis: Long) {
    Column(horizontalAlignment = Alignment.Start) {
        Text(clockText(millis), style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
        Text(dayName(millis), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Route, road, time, seats and captain — shared by all trip cards. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TripInfo(t: Trip, showCaptain: Boolean = true) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            TimeBlock(t.departAt)
            Spacer(Modifier.weight(1f))
            PricePill(t.price)
        }
        RouteTimeline(t.from, t.to, t.road)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            when (t.status) {
                TRIP_CANCELLED -> Pill("ملغاة", MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer)
                TRIP_FULL -> Pill("مكتملة", MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer)
                else -> Row(
                    Modifier.background(MaterialTheme.colorScheme.primaryContainer, CircleShape).padding(horizontal = 12.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SeatsRow(t.seatsTotal, t.seatsFree)
                    Text(
                        "باقي ${seatsText(t.seatsFree)}", style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
            if (t.acceptsParcels) {
                Pill("يقبل أمانات", MaterialTheme.colorScheme.surfaceContainerHighest, MaterialTheme.colorScheme.onSurface)
            }
        }
        if (showCaptain) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Avatar(t.captainName, 38.dp)
                Column {
                    Text("الكابتن ${t.captainName}", style = MaterialTheme.typography.titleSmall)
                    if (t.carText.isNotBlank()) {
                        Text(t.carText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        if (t.note.isNotBlank()) {
            Text(t.note, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        }
        if (t.status == TRIP_CANCELLED && t.cancelReason.isNotBlank()) {
            Text("السبب: ${t.cancelReason}", color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
fun AppCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

/** Green gradient header with a title and subtitle; content below it overlaps nicely. */
@Composable
fun HeroHeader(title: String, subtitle: String, trailing: @Composable (() -> Unit)? = null) {
    Box(
        Modifier.fillMaxWidth()
            .background(
                Brush.linearGradient(listOf(Color(0xFF0B3F31), Color(0xFF0E5A45), Color(0xFF1A7A5C))),
                RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp)
            )
            .statusBarsPadding()
            .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 22.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(subtitle, color = Color.White.copy(alpha = 0.78f), style = MaterialTheme.typography.bodyMedium)
                Text(title, color = Color.White, style = MaterialTheme.typography.headlineSmall)
            }
            trailing?.invoke()
        }
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

@Composable
fun BigButton(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    Button(
        onClick = onClick, enabled = enabled,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth().height(58.dp)
    ) { Text(text, style = MaterialTheme.typography.titleMedium) }
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
