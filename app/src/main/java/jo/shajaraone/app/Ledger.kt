package jo.shajaraone.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
    val Olive = Color(0xFF2D4436)
    val Terracotta = Color(0xFFBA5A31)
    val Ochre = Color(0xFFD9A84E)
    val Basalt = Color(0xFF2B2621)
    val Parchment = Color(0xFFF5EFEB)
    val Stock = Color(0xFFFFFFFF)
    val Pressed = Color(0xFFEAE0D5)
    val Rule = Color(0xFFD6C7B7)
    val Muted = Color(0xFF6E655B)
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
    MaterialTheme(colorScheme = LedgerScheme, typography = Typography().let {
        it.copy(
            headlineSmall = it.headlineSmall.copy(fontWeight = FontWeight.ExtraBold),
            titleLarge = it.titleLarge.copy(fontWeight = FontWeight.Bold),
            titleMedium = it.titleMedium.copy(fontWeight = FontWeight.SemiBold)
        )
    }) {
        CompositionLocalProvider(
            LocalLayoutDirection provides LayoutDirection.Rtl,
            LocalContentColor provides Ledger.Basalt
        ) { Box(Modifier.fillMaxSize().background(Ledger.Parchment)) { content() } }
    }
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
    val shape = RoundedCornerShape(8.dp)
    Column(
        modifier.fillMaxWidth().background(Ledger.Stock, shape).border(1.dp, Ledger.Rule, shape)
    ) {
        // top stub: time and route
        Column(Modifier.padding(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(dayName(t.departAt), fontSize = 12.sp, color = Ledger.Muted, fontWeight = FontWeight.Bold)
                    Text(clockText(t.departAt), fontFamily = numberFont, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, color = Ledger.Olive)
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
                    Text("${t.seatsFree} / ${t.seatsTotal}", fontFamily = numberFont, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = Ledger.Olive)
                    Text("مقاعد شاغرة", fontSize = 11.sp, color = Ledger.Muted)
                }
            }
            actions()
        }
    }
}

@Composable
fun LedgerPrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(
        onClick, modifier.fillMaxWidth().heightIn(min = 52.dp), shape = RoundedCornerShape(6.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Ledger.Olive, contentColor = Ledger.Parchment)
    ) { Text(text, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold) }
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
