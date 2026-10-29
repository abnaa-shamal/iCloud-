package jo.shajaraone.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Captain app: graphite surfaces, electric-blue accent, warm peach request tiles, docked bottom bar.
val CapBg = androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color(0xFF26272B), Color(0xFF141517)))
val CapInk = Color(0xFFF4F4F6)
val CapAccent = Color(0xFF3D8BFF)
val CapMute = Color(0xFF9A9CA3)
val CapLine = Color(0x26FFFFFF)
val CapRed = Color(0xFFFF6B6B)
val CapSurface = Color(0xFF2B2C31)
val CapPeach = Color(0xFFF8E8D2)
val CapDark = Color(0xFF16171A)

fun Modifier.capCard(r: Dp = 22.dp): Modifier {
    return this.glassPanel(r, Color(0xFF303136), Color(0xFF1C1D20), Color(0x2E5C9DFF))
}

@Composable
fun CapBtn(text: String, modifier: Modifier = Modifier, filled: Boolean = true, color: Color = CapAccent, height: Dp = 54.dp, onClick: () -> Unit) {
    val s = RoundedCornerShape(16.dp)
    Box(
        modifier.height(height).squeeze(onClick).clip(s)
            .then(if (filled) Modifier.background(color) else Modifier.border(1.5.dp, color.copy(alpha = .6f), s)),
        contentAlignment = Alignment.Center
    ) { Text(text, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = if (filled) Color.White else color) }
}

@Composable
fun CapChip(text: String, on: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val s = RoundedCornerShape(14.dp)
    Box(
        modifier.clip(s).background(if (on) CapAccent else CapSurface).border(1.dp, if (on) CapAccent else CapLine, s)
            .clickable(remember { MutableInteractionSource() }, null) { onClick() }.padding(horizontal = 8.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) { Text(text, fontSize = 14.sp, maxLines = 1, fontWeight = FontWeight.SemiBold, color = Color.White) }
}

/** Day strip in the captain look: light tiles, green when picked. */
@Composable
fun CapDays(days: List<Pair<String, String>>, selected: Int, onPick: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        days.forEachIndexed { i, (name, num) ->
            val on = i == selected
            Column(
                Modifier.weight(1f).height(68.dp).clip(RoundedCornerShape(18.dp))
                    .background(if (on) CapAccent else CapSurface)
                    .border(1.dp, if (on) CapAccent else CapLine, RoundedCornerShape(18.dp))
                    .clickable(remember { MutableInteractionSource() }, null) { onPick(i) },
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
            ) {
                Text(name, fontSize = 11.sp, maxLines = 1, color = if (on) Color.White.copy(alpha = .85f) else CapMute)
                Text(num, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = if (on) Color.White else CapInk)
            }
        }
    }
}

@Composable
fun CapAvatar(name: String, size: Dp = 42.dp) {
    Box(Modifier.size(size).clip(CircleShape).background(Color(0xFF3A3B41)), contentAlignment = Alignment.Center) {
        Text(name.trim().take(1), fontSize = (size.value * .42f).sp, fontWeight = FontWeight.Bold, color = CapAccent)
    }
}

/** Docked bottom bar: the selected item gets a glowing outlined tile. */
@Composable
fun CapNav(items: List<Triple<Int, String, Int>>, selected: Int, onPick: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)).glassPanel(28.dp, Color(0xFF2A2B30), Color(0xFF141517), Color(0x2E5C9DFF))
            .border(1.dp, CapLine, RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)).navigationBarsPadding().padding(horizontal = 24.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically
    ) {
        items.forEachIndexed { i, (g, name, badge) ->
            val on = i == selected
            Box {
                Column(
                    Modifier.width(96.dp).clip(RoundedCornerShape(18.dp))
                        .then(if (on) Modifier.background(Color(0xFF2D2F35)).border(1.5.dp, CapAccent.copy(alpha = .7f), RoundedCornerShape(18.dp)) else Modifier)
                        .clickable(remember { MutableInteractionSource() }, null) { onPick(i) }.padding(vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Glyph(g, if (on) Color.White else CapMute, 24.dp)
                    Text(name, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = if (on) Color.White else CapMute)
                }
                if (badge > 0) Box(Modifier.align(Alignment.TopEnd).padding(top = 2.dp, end = 10.dp).size(20.dp).clip(CircleShape).background(CapAccent), contentAlignment = Alignment.Center) {
                    Text("$badge", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }
    }
}

/** One glass card per topic: a small title, then its content, neatly from the right. */
@Composable
fun CapSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().capCard(24.dp).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = CapMute)
        content()
    }
}

/** Equal-width grid (default 3 per row) so choices line up in tidy columns instead of wrapping loosely. */
@Composable
fun CapGrid(items: List<String>, selected: String, cols: Int = 3, onPick: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.chunked(cols).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { n -> CapChip(n, n == selected, Modifier.weight(1f)) { onPick(n) } }
                repeat(cols - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}
