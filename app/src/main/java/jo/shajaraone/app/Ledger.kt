package jo.shajaraone.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * «Regional Boarding Ledger» look (from the Stitch design system): olive, terracotta and ochre on warm parchment,
 * trips drawn as boarding-pass tickets with perforation notches and a captain stamp.
 * Preview-only for now: the live app still uses the Aurora theme until this look is approved.
 */
object Ledger {
    // Mapped onto the Aurora (deep space) brand so the ticket components follow the app theme.
    val Olive = Brand.Violet
    val Terracotta = Brand.Teal
    val Ochre = Brand.Ochre
    val Basalt = Brand.Ink
    val Parchment = Brand.Navy
    val Stock = Brand.Stock
    val Pressed = Brand.Pressed
    val Rule = Brand.Rule
    val Muted = Brand.Muted
}

private val LedgerScheme = lightColorScheme(
    primary = Ledger.Olive, onPrimary = Ledger.Parchment,
    secondary = Ledger.Terracotta, onSecondary = Color.White,
    tertiary = Ledger.Ochre, background = Ledger.Parchment, onBackground = Ledger.Basalt,
    surface = Ledger.Stock, onSurface = Ledger.Basalt, surfaceVariant = Ledger.Pressed, onSurfaceVariant = Ledger.Muted,
    outline = Ledger.Rule, outlineVariant = Ledger.Rule
)

@Composable
fun LedgerTheme(content: @Composable () -> Unit) {
    ShajaraTheme { AppBackground { content() } }
}

private val numberFont = FontFamily.Monospace

@Composable
fun LedgerTopBar(title: String, subtitle: String) {
    Column(Modifier.fillMaxWidth().background(Ledger.Olive).statusBarsPadding().padding(horizontal = 16.dp, vertical = 14.dp)) {
        Text(subtitle, color = Ledger.Ochre, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
        Text(title, color = Ledger.Parchment, style = MaterialTheme.typography.headlineSmall)
    }
}

/** Search summary: one printed line with an edit button. */
@Composable
fun LedgerSearchBar(summary: String, onEdit: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(Ledger.Stock, RoundedCornerShape(6.dp))
            .border(1.dp, Ledger.Rule, RoundedCornerShape(6.dp)).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Column(Modifier.weight(1f)) {
            Text("وين مشوارك؟", fontSize = 12.sp, color = Ledger.Muted)
            Text(summary, style = MaterialTheme.typography.titleMedium)
        }
        OutlinedButton(
            onEdit, shape = RoundedCornerShape(6.dp), border = BorderStroke(1.5.dp, Ledger.Olive),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Ledger.Olive)
        ) { Text("تعديل", fontWeight = FontWeight.Bold) }
    }
}

/** The tear line between the two halves of a ticket: dashed rule with a half-circle bite on each edge. */
@Composable
private fun Perforation() {
    Canvas(Modifier.fillMaxWidth().height(24.dp).offset(0.dp, 0.dp)) {
        val r = 12.dp.toPx()
        val cy = size.height / 2
        drawLine(
            Ledger.Rule, Offset(r + 6.dp.toPx(), cy), Offset(size.width - r - 6.dp.toPx(), cy), strokeWidth = 1.5f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f))
        )
        // bites: parchment circles centred on the card edges, with a thin rule-coloured rim
        for (x in listOf(0f, size.width)) {
            drawCircle(Ledger.Parchment, r, Offset(x, cy))
            drawCircle(Ledger.Rule, r, Offset(x, cy), style = Stroke(1.dp.toPx()))
        }
    }
}

/** Round ink stamp «ختم الكابتن المعتمد», slightly tilted. */
@Composable
fun CaptainStamp(modifier: Modifier = Modifier) {
    val ink = Ledger.Olive.copy(alpha = 0.88f)
    Box(modifier.size(64.dp).rotate(-6f), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(ink, size.minDimension / 2 - 1.dp.toPx(), style = Stroke(2.dp.toPx()))
            drawCircle(
                ink, size.minDimension / 2 - 6.dp.toPx(),
                style = Stroke(1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 4f)))
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("✓", color = ink, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
            Text("كابتن\nمعتمد", color = ink, fontSize = 9.sp, lineHeight = 10.sp, textAlign = TextAlign.Center, fontWeight = FontWeight.ExtraBold)
        }
    }
}

/** A trip as a boarding pass. [actions] is the area under the stub (book button, contact buttons...). */
@Composable
fun TicketCard(t: Trip, modifier: Modifier = Modifier, actions: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(22.dp)
    Column(
        modifier.fillMaxWidth().clip(shape)
            .background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color(0x26FFFFFF), Color(0x0AFFFFFF))))
            .border(1.dp, androidx.compose.ui.graphics.Brush.linearGradient(listOf(Brand.Violet.copy(alpha = .55f), Color(0x22FFFFFF), Brand.Teal.copy(alpha = .55f))), shape)
    ) {
        // top stub: time and route
        Column(Modifier.padding(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(dayName(t.departAt), fontSize = 12.sp, color = Ledger.Muted, fontWeight = FontWeight.Bold)
                    Text(clockText(t.departAt), fontFamily = numberFont, fontSize = 34.sp, fontWeight = FontWeight.Light, color = Color.White)
                }
                if (t.acceptsParcels) {
                    Box(
                        Modifier.background(Ledger.Terracotta.copy(alpha = 0.12f), RoundedCornerShape(4.dp))
                            .border(1.dp, Ledger.Terracotta, RoundedCornerShape(4.dp)).padding(horizontal = 8.dp, vertical = 3.dp)
                    ) { Text("يقبل أمانات", color = Ledger.Terracotta, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold) }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(t.from, style = MaterialTheme.typography.titleLarge)
                Text("←", fontSize = 20.sp, color = Ledger.Terracotta, fontWeight = FontWeight.ExtraBold)
                Text(t.to, style = MaterialTheme.typography.titleLarge)
            }
            if (t.road.isNotBlank()) Text("عن طريق ${t.road}", fontSize = 13.sp, color = Ledger.Muted)
        }
        Perforation()
        // bottom stub: captain, seats, stamp
        Column(Modifier.padding(start = 18.dp, end = 18.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CaptainStamp()
                Column(Modifier.weight(1f)) {
                    Text("الكابتن ${t.captainName}", style = MaterialTheme.typography.titleMedium)
                    if (t.carText.isNotBlank()) Text(t.carText, fontSize = 13.sp, color = Ledger.Muted)
                }
                Column(horizontalAlignment = Alignment.End) {
                    if (t.status == "full") {
                        Text("مكتملة", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = Ledger.Terracotta)
                    } else {
                        SeatDots(t.seatsFree.toInt(), t.seatsTotal.toInt())
                        Text("${t.seatsFree} من ${t.seatsTotal}", fontSize = 18.sp, fontWeight = FontWeight.Light, color = Color.White)
                        Text("مقاعد شاغرة", fontSize = 11.sp, color = Ledger.Muted)
                    }
                }
            }
            if (t.note.isNotBlank()) Text(t.note, fontSize = 13.sp, color = Ledger.Muted)
            actions()
        }
    }
}

@Composable
fun LedgerPrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val shape = RoundedCornerShape(18.dp)
    Box(
        modifier.fillMaxWidth().heightIn(min = 54.dp).clip(shape)
            .background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(Brand.Violet, Brand.Teal)))
            .clickable { AppFeedback.tap(ctx); onClick() },
        contentAlignment = Alignment.Center
    ) { Text(text, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Color.White) }
}

/** Seat lamps: lit = free seat (cyan to pink glow), dim = taken. */
@Composable
fun SeatDots(free: Int, total: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(total.coerceIn(0, 8)) { i ->
            Box(
                Modifier.size(12.dp).clip(androidx.compose.foundation.shape.CircleShape).background(
                    if (i < free) androidx.compose.ui.graphics.Brush.linearGradient(listOf(Brand.Violet, Brand.Teal))
                    else androidx.compose.ui.graphics.Brush.linearGradient(listOf(Color(0x33FFFFFF), Color(0x33FFFFFF)))
                )
            )
        }
    }
}

@Composable
fun LedgerSecondaryRow(labels: List<String>) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        labels.forEach {
            OutlinedButton(
                {}, Modifier.weight(1f).height(44.dp), shape = RoundedCornerShape(6.dp),
                border = BorderStroke(1.5.dp, Ledger.Olive), contentPadding = PaddingValues(horizontal = 4.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Ledger.Basalt)
            ) { Text(it, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1) }
        }
    }
}

enum class LedgerSeat { FREE, TAKEN, PICKED, DRIVER }

@Composable
private fun SeatCell(n: Int, s: LedgerSeat) {
    val (fill, line, ink) = when (s) {
        LedgerSeat.PICKED -> Triple(Ledger.Olive, Ledger.Olive, Color.White)
        LedgerSeat.TAKEN -> Triple(Ledger.Pressed, Ledger.Rule, Ledger.Muted)
        LedgerSeat.DRIVER -> Triple(Color.Transparent, Ledger.Rule, Ledger.Muted)
        LedgerSeat.FREE -> Triple(Ledger.Stock, Ledger.Rule, Ledger.Basalt)
    }
    Box(
        Modifier.size(width = 62.dp, height = 56.dp).background(fill, RoundedCornerShape(6.dp))
            .border(1.5.dp, line, RoundedCornerShape(6.dp)),
        contentAlignment = Alignment.Center
    ) {
        Text(if (s == LedgerSeat.DRIVER) "سائق" else "%02d".format(n), color = ink, fontFamily = numberFont, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
        if (s == LedgerSeat.PICKED) {
            Box(Modifier.align(Alignment.TopStart).size(14.dp).background(Ledger.Ochre, RoundedCornerShape(topStart = 6.dp, bottomEnd = 8.dp)))
        }
    }
}

/** Van seat chart: the driver first, then rows of three. */
@Composable
fun LedgerSeatChart(total: Int, taken: Set<Int>, picked: Set<Int>) {
    val cells = buildList<Pair<Int, LedgerSeat>> {
        add(0 to LedgerSeat.DRIVER)
        for (i in 1..total) add(i to when (i) { in picked -> LedgerSeat.PICKED; in taken -> LedgerSeat.TAKEN; else -> LedgerSeat.FREE })
    }
    Column(
        Modifier.fillMaxWidth().background(Ledger.Pressed.copy(alpha = 0.55f), RoundedCornerShape(8.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.CenterHorizontally
    ) {
        cells.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { row.forEach { (n, s) -> SeatCell(n, s) } }
        }
    }
}

@Composable
fun LedgerLegend() {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        listOf("شاغر" to Ledger.Stock, "مختار" to Ledger.Olive, "محجوز" to Ledger.Pressed).forEach { (l, c) ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.size(16.dp).background(c, RoundedCornerShape(3.dp)).border(1.dp, Ledger.Rule, RoundedCornerShape(3.dp)))
                Text(l, fontSize = 13.sp, color = Ledger.Muted)
            }
        }
    }
}

// ===== Welcome screen: two question cards =====

/** Header with a soft ochre glow so the olive is not flat. */
@Composable
fun LedgerWelcomeHeader(greeting: String, line: String) {
    Box(
        Modifier.fillMaxWidth().background(
            androidx.compose.ui.graphics.Brush.linearGradient(listOf(Color(0xFF1C3226), Ledger.Olive, Color(0xFF3C5B49)))
        )
    ) {
        Canvas(Modifier.matchParentSize()) {
            drawCircle(
                androidx.compose.ui.graphics.Brush.radialGradient(
                    listOf(Ledger.Ochre.copy(alpha = 0.40f), Color.Transparent),
                    center = Offset(size.width * 0.15f, size.height * 0.1f), radius = size.width * 0.7f
                ),
                radius = size.width * 0.7f, center = Offset(size.width * 0.15f, size.height * 0.1f)
            )
        }
        Column(Modifier.statusBarsPadding().padding(horizontal = 20.dp, vertical = 22.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("أبناء الشمال", color = Ledger.Ochre, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
            Text(greeting, color = Ledger.Parchment, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold)
            Text(line, color = Ledger.Parchment.copy(alpha = 0.8f), fontSize = 15.sp)
        }
        Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(3.dp).background(Ledger.Ochre))
    }
}

/** One question as a card: number, question, the answer (or a hint), a chevron. */
@Composable
fun LedgerStepCard(step: String, question: String, answer: String?, hint: String, enabled: Boolean = true, onToggle: (() -> Unit)? = null, content: (@Composable ColumnScope.() -> Unit)? = null) {
    val shape = RoundedCornerShape(10.dp)
    Column(
        Modifier.fillMaxWidth().background(if (enabled) Ledger.Stock else Ledger.Pressed.copy(alpha = 0.5f), shape)
            .border(if (answer == null && enabled) 1.5.dp else 1.dp, if (answer == null && enabled) Ledger.Olive else Ledger.Rule, shape)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().then(if (onToggle != null && enabled) Modifier.clickable { onToggle() } else Modifier),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                Modifier.size(38.dp).background(if (enabled) Ledger.Olive else Ledger.Rule, androidx.compose.foundation.shape.CircleShape),
                contentAlignment = Alignment.Center
            ) { Text(step, color = Color.White, fontFamily = numberFont, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp) }
            Column(Modifier.weight(1f)) {
                Text(question, style = MaterialTheme.typography.titleLarge, color = if (enabled) Ledger.Basalt else Ledger.Muted)
                Text(answer ?: hint, fontSize = 14.sp, color = if (answer != null) Ledger.Terracotta else Ledger.Muted,
                    fontWeight = if (answer != null) FontWeight.ExtraBold else FontWeight.Normal)
            }
            Text(if (content != null) "⌃" else "⌄", fontSize = 22.sp, color = Ledger.Olive, fontWeight = FontWeight.ExtraBold)
        }
        if (content != null) content()
    }
}

/** Big answer button with a plain-words explanation, for people who do not know «شمال/جنوب» or road names. */
@Composable
fun LedgerChoice(title: String, explain: String, selected: Boolean = false, onClick: (() -> Unit)? = null) {
    val shape = RoundedCornerShape(8.dp)
    Column(
        Modifier.fillMaxWidth().background(if (selected) Ledger.Olive else Ledger.Parchment, shape)
            .border(1.5.dp, if (selected) Ledger.Olive else Ledger.Rule, shape)
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Text(title, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = if (selected) Ledger.Parchment else Ledger.Basalt)
        Text(explain, fontSize = 13.sp, lineHeight = 19.sp, color = if (selected) Ledger.Parchment.copy(alpha = 0.85f) else Ledger.Muted)
    }
}

/** «Not sure of the road? type the place»: the road is found from the stop name. */
@Composable
fun LedgerFindBox(hint: String) {
    Row(
        Modifier.fillMaxWidth().background(Ledger.Stock, RoundedCornerShape(8.dp)).border(1.5.dp, Ledger.Ochre, RoundedCornerShape(8.dp))
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("🔍", fontSize = 18.sp)
        Text(hint, color = Ledger.Muted, fontSize = 15.sp)
    }
}
