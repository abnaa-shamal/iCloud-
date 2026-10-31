package jo.shajaraone.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Captain app, food delivery (phase 1, design only: no database yet). Careem-style service tiles on the home screen,
// then a list of nearby orders, then a form where the captain enters an order agreed with a restaurant.
private val FoodOrange = Color(0xFFFF8A3D)
private val FoodRed = Color(0xFFFF5A5F)
private val OnlineGreen = Color(0xFF3DDC97)
private val FoodBrush get() = Brush.linearGradient(listOf(FoodOrange, FoodRed))

data class DeliveryOrder(
    val id: String,
    val restaurant: String,
    val pickup: String,
    val area: String,
    val distKm: String,
    val fare: String,
    val ago: String,
    val note: String = "",
    val mine: Boolean = false,
)

data class DeliveryDraft(
    val restaurant: String = "",
    val pickup: String = "",
    val area: String = "",
    val fare: String = "",
    val note: String = "",
)

/** Home of the captain app: two big service tiles (rides / food), today's numbers, and the closest delivery order. */
@Composable
fun CapServicesHome(name: String, ridePending: Int, foodNearby: Int, nearest: DeliveryOrder?, onRides: () -> Unit, onFood: () -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CapAvatar(name, 50.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("أهلاً كابتن $name", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = CapInk)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(OnlineGreen))
                    Spacer(Modifier.width(6.dp))
                    Text("متصل وجاهز للطلبات", fontSize = 13.sp, color = CapMute)
                }
            }
        }
        Text("شو بدك تشتغل اليوم؟", fontSize = 15.sp, color = CapMute)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            ServiceTile("رحلات الركاب", "انشر رحلتك واستقبل الحجوزات", "🚗", ridePending, "طلب حجز", Brush.linearGradient(listOf(CapAccent, CapAccent.copy(alpha = 0.55f))), onRides)
            ServiceTile("توصيل أكل", "طلبات المطاعم القريبة منك", "🍔", foodNearby, "طلب قريب", FoodBrush, onFood)
        }
        CapSection("اليوم") {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Stat("٣", "رحلات")
                Stat("٥", "توصيلات")
                Stat("٢٧ د.أ", "الأرباح")
            }
        }
        if (nearest != null) {
            CapSection("أقرب طلب توصيل إلك") { OrderCard(nearest, {}) }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun RowScope.ServiceTile(title: String, sub: String, emoji: String, count: Int, countLabel: String, brush: Brush, onClick: () -> Unit) {
    Box(
        Modifier.weight(1f).height(196.dp).squeeze(onClick).clip(RoundedCornerShape(26.dp)).background(brush).padding(16.dp)
    ) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
            Box(Modifier.size(52.dp).clip(CircleShape).background(Color(0x33FFFFFF)), contentAlignment = Alignment.Center) { Text(emoji, fontSize = 26.sp) }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text(sub, fontSize = 12.sp, color = Color.White.copy(alpha = 0.88f), lineHeight = 16.sp)
                if (count > 0) {
                    Box(Modifier.clip(RoundedCornerShape(50)).background(Color(0x40000000)).padding(horizontal = 10.dp, vertical = 4.dp)) {
                        Text("$count $countLabel", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
private fun RowScope.Stat(value: String, label: String) {
    Column(
        Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).background(Color(0x14FFFFFF)).padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(value, fontSize = 19.sp, fontWeight = FontWeight.Bold, color = CapInk)
        Text(label, fontSize = 12.sp, color = CapMute)
    }
}

/** Delivery orders: nearby (by GPS) and mine. Sample data for now. */
@Composable
fun CapDeliveryScreen(
    orders: List<DeliveryOrder>,
    tab: Int = 0,
    onTab: (Int) -> Unit = {},
    onBack: () -> Unit = {},
    onNew: () -> Unit = {},
    onAccept: (DeliveryOrder) -> Unit = {},
) {
    val near = orders.filter { !it.mine }
    val mine = orders.filter { it.mine }
    val shown = if (tab == 0) near else mine
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 20.dp).padding(top = 12.dp)) {
            CapBackBar("توصيل أكل", onBack)
            Spacer(Modifier.height(14.dp))
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color(0x14FFFFFF)).padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Seg("قريبة منك (${near.size})", tab == 0, Modifier.weight(1f)) { onTab(0) }
                Seg("طلباتي (${mine.size})", tab == 1, Modifier.weight(1f)) { onTab(1) }
            }
            Spacer(Modifier.height(14.dp))
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                shown.forEach { OrderCard(it, onAccept) }
                Spacer(Modifier.height(96.dp))
            }
        }
        CapBtn("＋  طلب جديد من مطعم", Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(20.dp), color = FoodOrange, onClick = onNew)
    }
}

@Composable
private fun Seg(text: String, on: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier.height(42.dp).squeeze(onClick).clip(RoundedCornerShape(12.dp)).then(if (on) Modifier.background(FoodBrush) else Modifier),
        contentAlignment = Alignment.Center
    ) { Text(text, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = if (on) Color.White else CapMute) }
}

@Composable
private fun OrderCard(o: DeliveryOrder, onAccept: (DeliveryOrder) -> Unit) {
    Column(Modifier.fillMaxWidth().capCard(22.dp).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(46.dp).clip(RoundedCornerShape(14.dp)).background(FoodBrush), contentAlignment = Alignment.Center) { Text("🍔", fontSize = 22.sp) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(o.restaurant, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = CapInk)
                Text(o.ago + "  •  " + o.distKm + " منك", fontSize = 12.sp, color = CapMute)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(o.fare, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = FoodOrange)
                Text("بالاتفاق", fontSize = 11.sp, color = CapMute)
            }
        }
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(FoodOrange))
                Spacer(Modifier.width(10.dp))
                Text("من: " + o.pickup, fontSize = 14.sp, color = CapInk)
            }
            Box(Modifier.padding(start = 4.dp).width(2.dp).height(14.dp).background(CapLine))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(CapAccent))
                Spacer(Modifier.width(10.dp))
                Text("إلى: " + o.area, fontSize = 14.sp, color = CapInk)
            }
        }
        if (o.note.isNotBlank()) Text("📝 " + o.note, fontSize = 13.sp, color = CapMute)
        if (o.mine) {
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(OnlineGreen.copy(alpha = 0.16f)).padding(12.dp)) {
                Text("✅ قبلته، بالطريق للمطعم", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = OnlineGreen)
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CapBtn("قبول الطلب", Modifier.weight(1f), color = FoodOrange, height = 48.dp) { onAccept(o) }
                CapBtn("اتصال", Modifier.weight(0.6f), filled = false, color = CapAccent, height = 48.dp) {}
            }
        }
    }
}

/** The captain agrees with a restaurant (phone / WhatsApp), then enters the order here so nearby captains can take it. */
@Composable
fun CapNewDelivery(initial: DeliveryDraft = DeliveryDraft(), onBack: () -> Unit = {}, onPublish: (DeliveryDraft) -> Unit = {}) {
    var d by remember { mutableStateOf(initial) }
    Column(
        Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 20.dp).padding(top = 12.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        CapBackBar("طلب توصيل جديد", onBack)
        Text("المطعم اتفق معك؟ سجّل الطلب وبيوصل للكباتن القريبين منك.", fontSize = 13.sp, color = CapMute)
        CapSection("المطعم") {
            DField("اسم المطعم", d.restaurant) { d = d.copy(restaurant = it) }
            DField("عنوان الاستلام أو علامة دالة", d.pickup) { d = d.copy(pickup = it) }
        }
        CapSection("الزبون") {
            DField("منطقة التسليم", d.area) { d = d.copy(area = it) }
            DField("ملاحظات (اختياري)", d.note) { d = d.copy(note = it) }
        }
        CapSection("الأجرة (بالاتفاق)") {
            CapGrid(listOf("1.0", "1.5", "2.0", "2.5", "3.0", "غيرها"), d.fare, cols = 3) { d = d.copy(fare = it) }
            Text("الأجرة بينك وبين المطعم. التطبيق ما بيحسب عمولة.", fontSize = 12.sp, color = CapMute)
        }
        CapBtn("نشر الطلب للكباتن القريبين", Modifier.fillMaxWidth(), color = FoodOrange) { onPublish(d) }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun DField(label: String, value: String, onChange: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, fontSize = 12.sp, color = CapMute)
        Box(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color(0x14FFFFFF))
                .border(1.dp, CapLine, RoundedCornerShape(14.dp)).padding(horizontal = 14.dp, vertical = 14.dp)
        ) {
            BasicTextField(
                value = value, onValueChange = onChange,
                textStyle = TextStyle(color = CapInk, fontSize = 16.sp),
                cursorBrush = SolidColor(CapAccent), singleLine = true, modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
