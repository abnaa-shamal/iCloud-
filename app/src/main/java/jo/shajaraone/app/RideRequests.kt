package jo.shajaraone.app

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.firestore.ListenerRegistration

// «حجز مسبق»: the rider asks for a ride at a day and time. It goes to every captain, and the first one who accepts takes it.

/** "لـ:0791234567 • note" → the other person's phone (or "") and the free note. */
private fun splitNote(note: String): Pair<String, String> {
    if (!note.startsWith("لـ:")) return "" to note
    val rest = note.removePrefix("لـ:")
    val phone = rest.takeWhile { it.isDigit() }
    return phone to rest.removePrefix(phone).removePrefix(" • ").trim()
}

private fun rideWhen(r: Repo.RideReq) = "${dayName(r.departAt)} • ${clockText(r.departAt)}"

// ===================== Rider =====================

/** A glass card shown under the trips list: "can't find a trip at your time? ask for one". */
@Composable
fun RideRequestEntry(onClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().squeeze(onClick).darkGlass(24.dp).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text("🕓 حجز مسبق بوقت ويوم محدد", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Text("ما لقيت رحلة بوقتك؟ اطلب رحلة، وبتوصل لكل الكباتن، وأول كابتن يقبل بيأخذها.", fontSize = 13.sp, color = Dim)
        Text("اطلب الآن ←", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Amber)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RideRequestDialog(me: UserProfile, fs: RiderFlowState, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    var day by remember { mutableIntStateOf(if (fs.day in 0..4) fs.day else 0) }
    var hour by remember { mutableIntStateOf(8) }
    var minute by remember { mutableIntStateOf(0) }
    var seats by remember { mutableIntStateOf(1) }
    var note by remember { mutableStateOf("") }
    var pickTime by remember { mutableStateOf(false) }
    var sending by remember { mutableStateOf(false) }
    val at = departMillis(day, hour, minute)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("طلب رحلة بحجز مسبق") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(fs.summary(), fontWeight = FontWeight.Bold)
                Text("اليوم", fontSize = 12.sp)
                DayPills(pillDays(5), day) { day = it }
                Text("الوقت", fontSize = 12.sp)
                TextButton(onClick = { pickTime = true }) { Text("🕓 " + clockText(at), fontSize = 18.sp, fontWeight = FontWeight.Bold) }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("عدد المقاعد: $seats", Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                    TextButton(onClick = { if (seats > 1) seats-- }) { Text("−", fontSize = 22.sp) }
                    TextButton(onClick = { if (seats < 4) seats++ }) { Text("+", fontSize = 22.sp) }
                }
                OutlinedTextField(value = note, onValueChange = { note = it.take(100) }, label = { Text("ملاحظة للكابتن (اختياري)") })
                Text("بيوصل طلبك لكل الكباتن، وأول كابتن يقبل بتشوف اسمه ورقمه عندك.", fontSize = 12.sp)
            }
        },
        confirmButton = {
            TextButton(enabled = !sending, onClick = {
                if (at < System.currentTimeMillis() + 10 * 60_000L) { toast(ctx, "اختر وقت بعد 10 دقايق على الأقل"); return@TextButton }
                sending = true
                val full = (if (fs.who == 2 && fs.otherOk) "لـ:" + fs.other.filter { it.isDigit() } + (if (note.isNotBlank()) " • " else "") else "") + note
                Repo.createRideRequest(me, fs.cur, fs.dest, fs.road, at, seats.toLong(), full) { ok ->
                    sending = false
                    toast(ctx, if (ok) "انبعت طلبك للكباتن ✅" else "ما انبعت الطلب، جرّب كمان مرة")
                    if (ok) onDismiss()
                }
            }) { Text("أرسل الطلب") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
    if (pickTime) {
        val st = rememberTimePickerState(initialHour = hour, initialMinute = minute, is24Hour = false)
        AlertDialog(
            onDismissRequest = { pickTime = false },
            confirmButton = { TextButton(onClick = { hour = st.hour; minute = st.minute; pickTime = false }) { Text("تم") } },
            dismissButton = { TextButton(onClick = { pickTime = false }) { Text("إلغاء") } },
            text = { CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) { TimePicker(state = st) } }
        )
    }
}

/** «طلباتي المسبقة» inside the rider's bookings tab. */
@Composable
fun RiderRideRequests() {
    val ctx = LocalContext.current
    var list by remember { mutableStateOf<List<Repo.RideReq>>(emptyList()) }
    var prev by remember { mutableStateOf<Map<String, String>?>(null) }
    DisposableEffect(Unit) {
        val uid = Repo.auth.currentUser?.uid
        val r: ListenerRegistration? = if (uid != null) Repo.listenMyRideRequests(uid) { l ->
            val p = prev
            if (p != null && l.any { it.status == "taken" && p[it.id] == "open" }) {
                AppFeedback.system(ctx)
                toast(ctx, "كابتن قبل طلبك ✅")
            }
            prev = l.associate { it.id to it.status }
            list = l
        } else null
        onDispose { r?.remove() }
    }
    val now = System.currentTimeMillis()
    val shown = list.filter { it.status != "cancelled" && it.departAt > now - 2 * 3_600_000L }
    if (shown.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("طلباتي المسبقة", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
        shown.forEach { r ->
            Column(Modifier.fillMaxWidth().darkGlass(22.dp).padding(16.dp).animateContentSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${r.from} ← ${r.to}", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text("${rideWhen(r)} • ${seatsText(r.seats)}", fontSize = 13.sp, color = Dim)
                if (r.status == "open") {
                    Text("⏳ بانتظار أول كابتن يقبل", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Amber)
                } else {
                    Text("✅ قبل طلبك الكابتن ${r.takenByName}", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF34C759))
                    if (r.takenByCar.isNotBlank()) Text(r.takenByCar, fontSize = 13.sp, color = Dim)
                    Text(r.takenByPhone, fontSize = 13.sp, color = Color.White)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GhostBtn("اتصال", Modifier.weight(1f)) { callPhone(ctx, r.takenByPhone) }
                        GhostBtn("واتساب", Modifier.weight(1f)) { openWhatsApp(ctx, r.takenByPhone, "مرحبا كابتن ${r.takenByName}، بخصوص طلبي ${r.from} ← ${r.to} ${rideWhen(r)}") }
                    }
                }
                GhostBtn("إلغاء الطلب", Modifier.fillMaxWidth(), Color(0xFFFF8A80)) {
                    Repo.cancelRideRequest(r) { ok -> toast(ctx, if (ok) "انلغى الطلب" else "ما قدرت ألغي، جرّب كمان مرة") }
                }
            }
        }
    }
}

// ===================== Captain =====================

/** Home panel for the captain: open pre-booking requests (first to accept wins) and the ones he took. */
@Composable
fun CaptainRideRequests(me: UserProfile) {
    val ctx = LocalContext.current
    var open by remember { mutableStateOf<List<Repo.RideReq>>(emptyList()) }
    var mine by remember { mutableStateOf<List<Repo.RideReq>>(emptyList()) }
    var expanded by remember { mutableStateOf(false) }
    var lastCount by remember { mutableIntStateOf(-1) }
    DisposableEffect(me.uid) {
        val uid = Repo.auth.currentUser?.uid
        val a: ListenerRegistration? = Repo.listenOpenRideRequests { l ->
            val others = l.filter { it.passengerUid != uid }
            if (lastCount >= 0 && others.size > lastCount) { AppFeedback.newTrip(ctx); toast(ctx, "طلب حجز مسبق جديد") }
            lastCount = others.size
            open = others
        }
        val b: ListenerRegistration? = if (uid != null) Repo.listenTakenRideRequests(uid) { mine = it } else null
        onDispose { a?.remove(); b?.remove() }
    }
    val now = System.currentTimeMillis()
    val upcomingMine = mine.filter { it.departAt > now - 3 * 3_600_000L }
    if (open.isEmpty() && upcomingMine.isEmpty()) return
    val car = listOf(me.carType, me.carColor).filter { it.isNotBlank() }.joinToString(" ") + if (me.carPlate.isNotBlank()) " • ${me.carPlate}" else ""

    Column(Modifier.fillMaxWidth().capCard().animateContentSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            Modifier.fillMaxWidth().clickable(remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, null) { expanded = !expanded },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("🕓 طلبات حجز مسبق", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = CapInk)
                Text(
                    (if (open.isNotEmpty()) "${open.size} بانتظار أول كابتن يقبل" else "ما في طلبات مفتوحة") +
                        (if (upcomingMine.isNotEmpty()) " • قبلت ${upcomingMine.size}" else ""),
                    fontSize = 12.sp, color = CapMute
                )
            }
            Box(Modifier.clip(RoundedCornerShape(12.dp)).background(CapAccent).padding(horizontal = 12.dp, vertical = 6.dp)) {
                Text(if (expanded) "إخفاء" else "عرض", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = CapInk)
            }
        }
        if (expanded) {
            if (open.isNotEmpty()) Text(
                "هذي طلبات عملاء بدهم رحلة بيوم ووقت محدد. أول كابتن يقبل بياخذ الطلب، وبيوصله رقم العميل.",
                fontSize = 12.sp, color = CapMute
            )
            open.forEach { r ->
                val (otherPhone, free) = splitNote(r.note)
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(CapPeach).padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(Modifier.clip(RoundedCornerShape(8.dp)).background(CapDark).padding(horizontal = 10.dp, vertical = 3.dp)) {
                        Text("حجز مسبق", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                    Text("${r.from} ← ${r.to}", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = CapDark)
                    Text("${rideWhen(r)} • ${seatsText(r.seats)}", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = CapDark)
                    if (r.road.isNotBlank() && r.road != NORTH_INNER) Text("الطريق: ${r.road}", fontSize = 12.sp, color = Color(0xFF6B5E4F))
                    Text("العميل: ${r.passengerName.trim().substringBefore(" ")}" + if (otherPhone.isNotBlank()) " (الحجز لشخص آخر)" else "", fontSize = 12.sp, color = Color(0xFF6B5E4F))
                    if (free.isNotBlank()) Text("ملاحظة: $free", fontSize = 12.sp, color = CapDark)
                    CapBtn("أقبل الطلب", Modifier.fillMaxWidth(), color = CapDark, height = 46.dp) {
                        Repo.acceptRideRequest(r, me, car) { ok, msg -> toast(ctx, if (ok) "قبلت الطلب ✅ رقم العميل صار عندك" else msg) }
                    }
                }
            }
            if (upcomingMine.isNotEmpty()) Text("طلبات قبلتها", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = CapInk)
            upcomingMine.forEach { r ->
                val (otherPhone, free) = splitNote(r.note)
                val phone = otherPhone.ifBlank { r.passengerPhone }
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(CapInk.copy(alpha = 0.06f)).padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("${r.from} ← ${r.to}", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = CapInk)
                    Text("${rideWhen(r)} • ${seatsText(r.seats)}", fontSize = 13.sp, color = CapMute)
                    Text((if (otherPhone.isNotBlank()) "حجز ${r.passengerName} لشخص آخر" else r.passengerName) + " • $phone", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = CapInk)
                    if (free.isNotBlank()) Text("ملاحظة: $free", fontSize = 12.sp, color = CapMute)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CapBtn("اتصال", Modifier.weight(1f), filled = false, color = CapInk, height = 40.dp) { callPhone(ctx, phone) }
                        CapBtn("واتساب", Modifier.weight(1f), filled = false, color = CapInk, height = 40.dp) {
                            openWhatsApp(ctx, phone, "مرحبا ${r.passengerName}، أنا الكابتن ${me.name} بخصوص طلبك ${r.from} ← ${r.to} ${rideWhen(r)}")
                        }
                    }
                }
            }
        }
    }
}
