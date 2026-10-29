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

// The captain app is deliberately the opposite of the rider app: light paper, deep green, big decisions.
val CapBg = Color(0xFFF3F0E8)
val CapInk = Color(0xFF14201B)
val CapGreen = Color(0xFF12A36A)
val CapMute = Color(0xFF7E8782)
val CapLine = Color(0xFFE4DFD2)
val CapRed = Color(0xFFD64545)

fun Modifier.capCard(r: Dp = 22.dp): Modifier {
    val s = RoundedCornerShape(r)
    return this.clip(s).background(Color.White).border(1.dp, CapLine, s)
}

@Composable
fun CapBtn(text: String, modifier: Modifier = Modifier, filled: Boolean = true, color: Color = CapGreen, height: Dp = 54.dp, onClick: () -> Unit) {
    val s = RoundedCornerShape(16.dp)
    Box(
        modifier.height(height).squeeze(onClick).clip(s)
            .then(if (filled) Modifier.background(color) else Modifier.border(1.5.dp, color.copy(alpha = .55f), s)),
        contentAlignment = Alignment.Center
    ) { Text(text, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = if (filled) Color.White else color) }
}

@Composable
fun CapChip(text: String, on: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val s = RoundedCornerShape(14.dp)
    Box(
        modifier.clip(s).background(if (on) CapInk else Color.White).border(1.dp, if (on) CapInk else CapLine, s)
            .clickable(remember { MutableInteractionSource() }, null) { onClick() }.padding(horizontal = 16.dp, vertical = 11.dp),
        contentAlignment = Alignment.Center
    ) { Text(text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = if (on) Color.White else CapInk) }
}

/** Day strip in the captain look: light tiles, green when picked. */
@Composable
fun CapDays(days: List<Pair<String, String>>, selected: Int, onPick: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        days.forEachIndexed { i, (name, num) ->
            val on = i == selected
            Column(
                Modifier.weight(1f).height(68.dp).clip(RoundedCornerShape(18.dp))
                    .background(if (on) CapGreen else Color.White)
                    .border(1.dp, if (on) CapGreen else CapLine, RoundedCornerShape(18.dp))
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
    Box(Modifier.size(size).clip(CircleShape).background(Color(0xFFE3F4EC)), contentAlignment = Alignment.Center) {
        Text(name.trim().take(1), fontSize = (size.value * .42f).sp, fontWeight = FontWeight.Bold, color = CapGreen)
    }
}
