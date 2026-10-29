package jo.shajaraone.app

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
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

@Composable
fun CaptainHome(me: UserProfile) {
    val uid = Repo.auth.currentUser?.uid ?: ""
    var publishing by rememberSaveable { mutableStateOf(false) }
    var trips by remember { mutableStateOf<List<Trip>?>(null) }
    var bookings by remember { mutableStateOf<List<Booking>>(emptyList()) }
    DisposableEffect(uid) {
        val a = Repo.listenTrips({ all -> trips = all.filter { it.captainUid == uid } }, { if (trips == null) trips = emptyList() })
        val b = Repo.listenBookings("captainUid", uid) { bookings = it }
        onDispose { a.remove(); b.remove() }
    }
    val pending = bookings.count { it.status == BK_PENDING && it.departAt > System.currentTimeMillis() }
    val feedbackCtx = LocalContext.current
    var lastPending by remember { mutableIntStateOf(-1) }
    LaunchedEffect(pending) {
        if (lastPending >= 0 && pending > lastPending) AppFeedback.newRequest(feedbackCtx)
        lastPending = pending
    }
    CompositionLocalProvider(LocalContentColor provides CapInk) {
        Box(Modifier.fillMaxSize().background(CapBg)) {
            if (publishing) CapPublish(me, onBack = { publishing = false })
            else CapDashboard(me.name.trim().substringBefore(" "), trips, bookings, onPublish = { publishing = true })
        }
    }
}

// ===== Dashboard: requests first, then my rides =====

@Composable
fun CapDashboard(name: String, trips: List<Trip>?, bookings: List<Booking>, onPublish: () -> Unit) {
    val ctx = LocalContext.current
    var cancelTrip by remember { mutableStateOf<Trip?>(null) }
    var cancelBooking by remember { mutableStateOf<Booking?>(null) }
    var open by rememberSaveable { mutableStateOf("") }
    val now = System.currentTimeMillis()
    val pend = bookings.filter { it.status == BK_PENDING && it.departAt > now }.sortedBy { it.departAt }
    val active = (trips ?: emptyList()).filter { it.status != TRIP_CANCELLED && it.departAt > now - 4 * 3_600_000L }.sortedBy { it.departAt }

    Box(Modifier.fillMaxSize()) {
        androidx.compose.foundation.lazy.LazyColumn(
            Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 44.dp, bottom = 110.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Column {
                    Text("أهلاً كابتن $name", fontSize = 14.sp, color = CapMute)
                    Text(if (pend.isEmpty()) "ما في طلبات جديدة" else if (pend.size == 1) "طلب واحد بانتظارك" else "${pend.size} طلبات بانتظارك",
                        fontSize = 28.sp, fontWeight = FontWeight.Bold, color = CapInk)
                }
            }
            items(pend.size, key = { pend[it].id }) { i ->
                val b = pend[i]
                Column(Modifier.entrance(i).fillMaxWidth().capCard().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        CapAvatar(b.passengerName)
                        Column(Modifier.weight(1f)) {
                            Text(b.passengerName, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = CapInk)
                            Text("${seatsText(b.seats)} • ${b.tripSummary}", fontSize = 12.sp, color = CapMute)
                        }
                        Text(clockText(b.departAt), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = CapGreen)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        CapBtn("قبول", Modifier.weight(1.4f)) {
                            Repo.accept(b) { ok -> toast(ctx, if (ok) "تم القبول ✅" else "ما في مقاعد كافية أو الطلب تغيّر") }
                        }
                        CapBtn("رفض", Modifier.weight(1f), filled = false, color = CapRed) {
                            Repo.reject(b) { ok -> if (!ok) toast(ctx, "ما قدرت أرفض، جرّب كمان مرة") }
                        }
                    }
                }
            }
            item {
                Text("رحلاتي", Modifier.padding(top = 10.dp), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = CapInk)
            }
            if (trips == null) item { LoadingBox() }
            else if (active.isEmpty()) item {
                Box(Modifier.fillMaxWidth().capCard().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text("ما عندك رحلات جاية", color = CapMute)
                }
            }
            items(active.size, key = { active[it].id }) { i ->
                val t = active[i]
                val reqs = bookings.filter { it.tripId == t.id && it.isActive && it.status == BK_ACCEPTED }
                val isOpen = open == t.id
                Column(Modifier.fillMaxWidth().capCard().animateContentSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        Modifier.fillMaxWidth().clickable(remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, null) { open = if (isOpen) "" else t.id },
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("${t.from}  ←  ${t.to}", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = CapInk)
                            Text("${dayName(t.departAt)} • ${clockText(t.departAt)} • ${priceText(t.price)}", fontSize = 12.sp, color = CapMute)
                        }
                        Box(Modifier.clip(RoundedCornerShape(12.dp)).background(Color(0xFFE3F4EC)).padding(horizontal = 12.dp, vertical = 7.dp)) {
                            Text(if (t.seatsFree <= 0L) "مكتملة" else "${t.seatsFree} فاضية", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = CapGreen)
                        }
                    }
                    if (isOpen) {
                        if (reqs.isEmpty()) Text("ما في ركاب لسا", fontSize = 13.sp, color = CapMute)
                        reqs.forEach { b ->
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                CapAvatar(b.passengerName, 36.dp)
                                Column(Modifier.weight(1f)) {
                                    Text(b.passengerName, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = CapInk)
                                    Text(seatsText(b.seats) + if (b.dropoff.isNotBlank()) " • ${b.dropoff}" else "", fontSize = 11.sp, color = CapMute)
                                }
                                CapBtn("اتصال", filled = false, color = CapInk, height = 38.dp) { callPhone(ctx, b.passengerPhone) }
                                CapBtn("إلغاء", filled = false, color = CapRed, height = 38.dp) { cancelBooking = b }
                            }
                        }
                        LiveShareControl(t)
                        Text("إلغاء الرحلة", Modifier.clickable { cancelTrip = t }.padding(vertical = 6.dp), fontSize = 13.sp, color = CapRed)
                    }
                }
            }
        }
        Box(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color.Transparent, CapBg, CapBg)))
                .padding(horizontal = 18.dp).padding(top = 28.dp, bottom = 16.dp)
        ) { CapBtn("+  رحلة جديدة", Modifier.fillMaxWidth(), height = 58.dp, onClick = onPublish) }
    }

    cancelTrip?.let { t ->
        ReasonDialog(
            title = "إلغاء رحلة ${t.from} ← ${t.to}؟", confirmText = "ألغِ الرحلة", onDismiss = { cancelTrip = null },
            onConfirm = { reason ->
                val act = bookings.filter { it.tripId == t.id && it.isActive }
                Repo.cancelTrip(t, reason, act) { ok -> toast(ctx, if (ok) "انلغت الرحلة" else "ما قدرت ألغي، جرّب كمان مرة") }
                cancelTrip = null
            }
        )
    }
    cancelBooking?.let { b ->
        ReasonDialog(
            title = "إلغاء حجز ${b.passengerName}؟", confirmText = "ألغِ الحجز", onDismiss = { cancelBooking = null },
            onConfirm = { reason ->
                Repo.cancelBooking(b, BK_CANCELLED_CAPTAIN, reason) { ok -> toast(ctx, if (ok) "انلغى الحجز" else "ما قدرت ألغي، جرّب كمان مرة") }
                cancelBooking = null
            }
        )
    }
}

// ===== Publish: direction, place, village, day, time, seats =====

private val capDests = listOf("عمّان", "الزرقاء", "إربد")

@Composable
internal fun CapPublishPreview() = CapPublish(UserProfile(name = "أحمد", phone = "0790000000", carText = "كيا سيراتو"), onBack = {})

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun CapPublish(me: UserProfile, onBack: () -> Unit) {
    val ctx = LocalContext.current
    var back by rememberSaveable { mutableStateOf(false) }       // false = going out (رايح), true = coming home (راجع)
    var dest by rememberSaveable { mutableStateOf("") }
    var village by rememberSaveable { mutableStateOf("") }
    var road by rememberSaveable { mutableStateOf("شارع الأردن") }
    var day by rememberSaveable { mutableIntStateOf(0) }
    var minutes by rememberSaveable { mutableIntStateOf(-1) }
    var seats by rememberSaveable { mutableIntStateOf(3) }
    var priceStr by rememberSaveable { mutableStateOf("") }
    var showTime by remember { mutableStateOf(false) }
    var sending by remember { mutableStateOf(false) }

    val inner = dest == "إربد"
    val priceChips = if (inner) listOf("1", "1.5") else listOf("3", "3.5", "4", "4.5", "5")
    val villages = if (inner) northVillages else northPlaces
    val maxSeats = me.maxSeats.coerceIn(1, 8)

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp).padding(top = 40.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(44.dp).capCard(22.dp).clickable { onBack() }, contentAlignment = Alignment.Center) { Glyph(4, CapInk, 20.dp) }
            Text("رحلة جديدة", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = CapInk)
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            CapChip("رايح", !back, Modifier.weight(1f)) { back = false }
            CapChip("راجع", back, Modifier.weight(1f)) { back = true }
        }
        CapLabel(if (back) "راجع من وين؟" else "رايح لوين؟")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            capDests.forEach { d ->
                CapChip(d, dest == d, Modifier.weight(1f)) {
                    dest = d; priceStr = ""
                    if (d == "إربد" && village == "إربد") village = ""
                }
            }
        }
        if (dest == "عمّان") {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf("شارع الأردن", "طريق صويلح").forEach { r -> CapChip(r, road == r) { road = r } }
            }
        }
        CapLabel(if (back) "لأي بلد؟" else "من أي بلد؟")
        androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            villages.forEach { v -> CapChip(v, village == v) { village = v } }
        }

        CapLabel("اليوم")
        CapDays(pillDays(5), day) { day = it }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f).height(56.dp).capCard(16.dp).clickable { showTime = true }, contentAlignment = Alignment.Center) {
                Text(if (minutes >= 0) "الساعة ${clockText(dayStart(day) + minutes * 60_000L)}" else "اختر الساعة", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = if (minutes >= 0) CapInk else CapGreen)
            }
            Row(Modifier.capCard(16.dp).height(56.dp).padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(44.dp).clickable { if (seats > 1) seats-- }, contentAlignment = Alignment.Center) { Text("−", fontSize = 24.sp, color = CapInk) }
                Text("$seats مقاعد", Modifier.padding(horizontal = 6.dp), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = CapInk)
                Box(Modifier.size(44.dp).clickable { if (seats < maxSeats) seats++ }, contentAlignment = Alignment.Center) { Text("+", fontSize = 24.sp, color = CapInk) }
            }
        }

        CapLabel("السعر (اختياري)")
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CapChip("حسب الاتفاق", priceStr.isBlank()) { priceStr = "" }
            priceChips.forEach { p -> CapChip("$p د.أ", priceStr == p) { priceStr = p } }
        }

        CapBtn(if (sending) "جاري النشر..." else "انشر الرحلة", Modifier.fillMaxWidth(), height = 58.dp) {
            val departAt = if (minutes >= 0) dayStart(day) + minutes * 60_000L else 0L
            when {
                dest.isBlank() -> toast(ctx, "اختر الوجهة")
                village.isBlank() -> toast(ctx, "اختر البلد")
                minutes < 0 -> toast(ctx, "اختر الساعة")
                departAt < System.currentTimeMillis() -> toast(ctx, "هالوقت فات، اختر وقت جاي")
                sending -> {}
                else -> {
                    val roadField = when (dest) { "إربد" -> NORTH_INNER; "الزرقاء" -> "طريق الزرقاء"; else -> road }
                    val from = if (back) dest else village
                    val to = if (back) village else dest
                    sending = true
                    Repo.publishTrip(
                        Trip(
                            captainName = me.name, captainPhone = me.phone, carText = me.carText,
                            from = from, to = to, road = roadField,
                            departAt = departAt, seatsTotal = seats.toLong(), price = priceStr.toDoubleOrNull(),
                            acceptsParcels = false, note = ""
                        )
                    ) { ok ->
                        sending = false
                        if (ok) { toast(ctx, "تم نشر الرحلة ✅"); onBack() }
                        else toast(ctx, "ما قدرت أنشر. تأكد من الإنترنت ومن موافقة المشرف على حسابك")
                    }
                }
            }
        }
    }

    if (showTime) {
        val st = rememberTimePickerState(initialHour = if (minutes >= 0) minutes / 60 else 7, initialMinute = if (minutes >= 0) minutes % 60 else 0, is24Hour = false)
        AlertDialog(
            onDismissRequest = { showTime = false },
            confirmButton = { TextButton(onClick = { minutes = st.hour * 60 + st.minute; showTime = false }) { Text("تم") } },
            dismissButton = { TextButton(onClick = { showTime = false }) { Text("إلغاء") } },
            text = { CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) { TimePicker(state = st) } }
        )
    }
}

@Composable
private fun CapLabel(text: String) = Text(text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = CapMute)

/** One button to start sharing the live location, one to stop. */
@Composable
internal fun LiveShareControl(t: Trip) {
    val ctx = LocalContext.current
    val active by LiveShareService.active.collectAsState()
    val now = System.currentTimeMillis()
    val inWindow = t.status != TRIP_CANCELLED && now >= t.departAt - 60 * 60_000L && now <= t.departAt + 4 * 3_600_000L
    if (!inWindow) return
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        if (r.values.any { it }) LiveShareService.start(ctx, t.id) else toast(ctx, "لازم توافق على الموقع عشان تشاركه")
    }
    when {
        active == t.id -> CapBtn("أوقف مشاركة موقعي", Modifier.fillMaxWidth(), filled = false, color = CapRed) { LiveShareService.stop(ctx) }
        active != null -> Text("بتشارك موقعك لرحلة ثانية. وقّفها أول.", fontSize = 12.sp, color = CapMute)
        else -> CapBtn("ابدأ الرحلة وشارك موقعي", Modifier.fillMaxWidth()) {
            if (hasLocationPermission(ctx)) LiveShareService.start(ctx, t.id)
            else ask.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        }
    }
}
