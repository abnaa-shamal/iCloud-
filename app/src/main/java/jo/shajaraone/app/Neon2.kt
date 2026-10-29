package jo.shajaraone.app

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Option 1: ONE ride fills the screen; swipe sideways for the next one. The ring IS the card. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RideSwipe(trips: List<Trip>, modifier: Modifier = Modifier, actions: @Composable ColumnScope.(Trip) -> Unit) {
    val pager = rememberPagerState { trips.size }
    Column(modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        HorizontalPager(pager, Modifier.weight(1f), pageSpacing = 16.dp) { i ->
            val t = trips[i]
            Column(Modifier.fillMaxSize().padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Text(dayName(t.departAt), fontSize = 13.sp, color = Color(0x99FFFFFF))
                Text(clockText(t.departAt), fontSize = 54.sp, fontWeight = FontWeight.ExtraLight, color = Color.White)
                Spacer(Modifier.height(8.dp))
                GlowRing(t.seatsFree.toFloat() / t.seatsTotal.coerceAtLeast(1), 250.dp, 8.dp) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("${t.seatsFree}", fontSize = 88.sp, fontWeight = FontWeight.ExtraLight, color = Color.White, lineHeight = 90.sp)
                        Text("مقعد فاضي من ${t.seatsTotal}", fontSize = 13.sp, color = Brand.Violet)
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text("${t.from} ← ${t.to}", fontSize = 22.sp, fontWeight = FontWeight.Light, color = Color.White)
                if (t.road.isNotBlank()) Text("عن طريق ${t.road}", fontSize = 12.sp, color = Color(0x99FFFFFF))
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.size(32.dp).clip(CircleShape).background(Brush.linearGradient(listOf(Brand.Violet, Brand.Teal))), contentAlignment = Alignment.Center) {
                        Text(t.captainName.take(1), color = Color.White, fontWeight = FontWeight.Bold)
                    }
                    Text("الكابتن ${t.captainName} • ${t.carText}", fontSize = 13.sp, color = Color(0xCCFFFFFF))
                }
                Spacer(Modifier.height(16.dp))
                actions(t)
            }
        }
        Row(Modifier.padding(vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(trips.size) { i ->
                val on = pager.currentPage == i
                Box(Modifier.height(4.dp).width(if (on) 26.dp else 8.dp).clip(RoundedCornerShape(2.dp))
                    .background(if (on) Brush.linearGradient(listOf(Brand.Violet, Brand.Teal)) else Brush.linearGradient(listOf(Color(0x44FFFFFF), Color(0x44FFFFFF)))))
            }
        }
    }
}

/** Option 2: the road itself is the screen. A glowing line runs north (top) to south (bottom); rides sit on it as stops. */
@Composable
fun RoadTimeline(trips: List<Trip>, top: String, bottom: String, modifier: Modifier = Modifier, onPick: (Trip) -> Unit = {}) {
    Column(modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(14.dp).clip(CircleShape).background(Brand.Violet))
            Spacer(Modifier.width(14.dp))
            Text(top, fontSize = 20.sp, fontWeight = FontWeight.Light, color = Color.White)
        }
        trips.forEach { t ->
            Row(Modifier.height(IntrinsicSize.Min).fillMaxWidth().springPress { onPick(t) }) {
                Box(Modifier.width(14.dp).fillMaxHeight(), contentAlignment = Alignment.TopCenter) {
                    Box(Modifier.width(3.dp).fillMaxHeight().background(Brush.verticalGradient(listOf(Brand.Violet, Brand.Teal))))
                    Box(Modifier.padding(top = 34.dp).size(14.dp).clip(CircleShape).background(Color.White))
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f).padding(vertical = 12.dp).glass(20.dp).padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(clockText(t.departAt), fontSize = 30.sp, fontWeight = FontWeight.ExtraLight, color = Color.White, modifier = Modifier.weight(1f))
                        SeatDots(t.seatsFree.toInt(), t.seatsTotal.toInt())
                    }
                    Text("${t.from} ← ${t.to}", fontSize = 14.sp, color = Brand.Violet)
                    Text("الكابتن ${t.captainName} • ${t.carText}", fontSize = 12.sp, color = Color(0x99FFFFFF))
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(14.dp).clip(CircleShape).background(Brand.Teal))
            Spacer(Modifier.width(14.dp))
            Text(bottom, fontSize = 20.sp, fontWeight = FontWeight.Light, color = Color.White)
        }
    }
}
