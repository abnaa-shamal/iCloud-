package jo.shajaraone.app

import android.Manifest
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
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
import androidx.compose.ui.draw.rotate
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
    var tab by rememberSaveable { mutableIntStateOf(0) }
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
        if (lastPending >= 0 && pending > lastPending) { AppFeedback.newRequest(feedbackCtx); tab = 1 }
        lastPending = pending
    }
    val online = trips != null && trips!!.any { it.status != TRIP_CANCELLED && it.departAt > System.currentTimeMillis() - 4 * 3_600_000L }
    LaunchedEffect(online, trips != null) {
        if (trips != null) { if (online) CapReceiveService.start(feedbackCtx) else CapReceiveService.stop(feedbackCtx) }
    }
    var showHistory by remember { mutableStateOf(false) }
    var publishNext by rememberSaveable { mutableStateOf(false) }
    androidx.activity.compose.BackHandler(enabled = showHistory) { showHistory = false }
    androidx.activity.compose.BackHandler(enabled = publishNext && tab == 0 && !showHistory) { publishNext = false }
    if (showHistory) {
        CompositionLocalProvider(LocalContentColor provides CapInk) {
            Box(Modifier.fillMaxSize().background(CapBg)) { CapTripHistory(trips, bookings) { showHistory = false } }
        }
    } else {
        jo.shajaraone.app.hasbat.HasbatShell(
            tab = tab, onTab = { tab = it }, requestsBadge = pending,
            onSeatHistory = { showHistory = true }
        ) { t ->
            CompositionLocalProvider(LocalContentColor provides CapInk) {
                Box(Modifier.fillMaxSize()) {
                    if (t == 0) {
                        val nowH = System.currentTimeMillis()
                        LaunchedEffect(Unit) { CapTripState.load(feedbackCtx) }
                        val current = (trips ?: emptyList()).filter { it.status != TRIP_CANCELLED && CapTripState.of(it.id) != "finished" && (CapTripState.of(it.id) == "started" || it.departAt > nowH - 4 * 3_600_000L) }
                        if (current.isNotEmpty() && !publishNext) {
                            CapDashboard(me.name.trim().substringBefore(" "), trips, bookings, home = true, me = me, onPublishNext = { publishNext = true })
                        } else {
                            CapPublish(me, online, trips ?: emptyList(), onTrips = { tab = 1 }, onPublished = { publishNext = false })
                        }
                    } else CapDashboard(me.name.trim().substringBefore(" "), trips, bookings, me = me)
                }
            }
        }
    }
}

// ===== رحلاتي: requests on top, then القادمة / الجارية / المكتملة =====

/** Start / finish marks live on the captain's phone only (the database rules do not allow more). */
object CapTripState {
    private val map = androidx.compose.runtime.mutableStateMapOf<String, String>()
    private var loaded = false
    private fun prefs(c: android.content.Context) = c.getSharedPreferences("cap_trip_state", android.content.Context.MODE_PRIVATE)
    fun load(c: android.content.Context) {
        if (loaded) return
        loaded = true
        prefs(c).all.forEach { (k, v) -> if (v is String) map[k] = v }
    }
    fun of(id: String): String = map[id] ?: ""
    fun set(c: android.content.Context, id: String, st: String) { map[id] = st; prefs(c).edit().putString(id, st).apply() }
}

@Composable
fun CapDashboard(name: String, trips: List<Trip>?, bookings: List<Booking>, home: Boolean = false, me: UserProfile? = null, onPublishNext: () -> Unit = {}) {
    val ctx = LocalContext.current
    LaunchedEffect(Unit) { CapTripState.load(ctx) }
    var cancelTrip by remember { mutableStateOf<Trip?>(null) }
    var reqDialog by remember { mutableStateOf<Pair<Trip, String>?>(null) }
    var myReqs by remember { mutableStateOf<List<Repo.TripReq>>(emptyList()) }
    DisposableEffect(Unit) {
        val uid = Repo.auth.currentUser?.uid
        val r = if (uid != null) Repo.listenMyTripRequests(uid) { myReqs = it } else null
        onDispose { r?.remove() }
    }
    var prevPending by remember { mutableIntStateOf(-1) }
    LaunchedEffect(myReqs) {
        val p = myReqs.count { it.status == "pending" }
        if (prevPending >= 0 && p < prevPending) AppFeedback.system(ctx)
        prevPending = p
    }
    var cancelBooking by remember { mutableStateOf<Booking?>(null) }
    var finishTrip by remember { mutableStateOf<Trip?>(null) }
    var open by rememberSaveable { mutableStateOf("") }
    var seg by rememberSaveable { mutableIntStateOf(0) }
    val now = System.currentTimeMillis()
    val pend = bookings.filter { it.status == BK_PENDING && it.departAt > now }.sortedBy { it.departAt }
    val all = trips ?: emptyList()
    fun st(t: Trip) = CapTripState.of(t.id)
    val running = all.filter { it.status != TRIP_CANCELLED && st(it) == "started" }.sortedBy { it.departAt }
    // About 30 minutes before arrival (≈ 25 km from the destination by GPS) the captain is asked whether to publish his next trip.
    var askNext by remember { mutableStateOf<Trip?>(null) }
    val watched = if (home) running.lastOrNull() else null
    LaunchedEffect(watched?.id) {
        val w = watched ?: return@LaunchedEffect
        val c = placeCentres[w.to] ?: return@LaunchedEffect
        while (CapTripState.of("near_" + w.id) != "1") {
            if (hasLocationPermission(ctx)) fetchLocation(ctx) { l ->
                if (l != null && CapTripState.of("near_" + w.id) != "1") {
                    val out = FloatArray(1)
                    android.location.Location.distanceBetween(l.latitude, l.longitude, c.first, c.second, out)
                    if (out[0] <= 25_000f) { CapTripState.set(ctx, "near_" + w.id, "1"); askNext = w }
                }
            }
            kotlinx.coroutines.delay(30_000L)
        }
    }
    askNext?.let { w ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { askNext = null },
            title = { Text("قربت من ${w.to}") },
            text = { Text("بتحب تنشر رحلتك الجاية من ${w.to}؟") },
            confirmButton = { androidx.compose.material3.TextButton(onClick = { askNext = null; onPublishNext() }) { Text("نعم، انشر") } },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { askNext = null }) { Text("لا") } }
        )
    }
    val upcoming = all.filter { it.status != TRIP_CANCELLED && st(it) == "" && it.departAt > now - 4 * 3_600_000L }.sortedBy { it.departAt }
    val done = all.filter { it.status == TRIP_CANCELLED || st(it) == "finished" || (st(it) == "" && it.departAt <= now - 4 * 3_600_000L) }.sortedByDescending { it.departAt }
    val list = if (home) (if (running.isNotEmpty()) running else upcoming) else when (seg) { 0 -> upcoming; 1 -> running; else -> done }

    Box(Modifier.fillMaxSize()) {
        androidx.compose.foundation.lazy.LazyColumn(
            Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 44.dp, bottom = 170.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Column {
                    Text("رحلاتي • أهلاً كابتن $name", fontSize = 14.sp, color = CapMute)
                    Text(if (pend.isEmpty()) (if (home) "رحلتي الحالية" else "متابعة رحلتي") else if (pend.size == 1) "طلب واحد بانتظارك" else "${pend.size} طلبات بانتظارك",
                        fontSize = 28.sp, fontWeight = FontWeight.Bold, color = CapInk)
                }
            }
            if (home && me != null) item { CaptainRideRequests(me) }
            items(pend.size, key = { pend[it].id }) { i ->
                val b = pend[i]
                Column(Modifier.entrance(i).fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(CapPeach).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(Modifier.size(44.dp).clip(androidx.compose.foundation.shape.CircleShape).background(CapDark), contentAlignment = Alignment.Center) {
                            Text(b.passengerName.trim().take(1), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                        Column(Modifier.weight(1f)) {
                            Text(b.passengerName, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = CapDark)
                            Text("${seatsText(b.seats)} • ${b.tripSummary}", fontSize = 12.sp, color = Color(0xFF6B5E4F))
                            if (b.dropoff.isNotBlank()) Text("وجهته: ${b.dropoff}", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = CapDark)
                        }
                        Text(trips?.firstOrNull { it.id == b.tripId }?.let { tripTimeLabel(it.note, it.departAt) } ?: clockText(b.departAt), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = CapDark)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        CapBtn("قبول", Modifier.weight(1.4f), color = CapDark) {
                            Repo.accept(b) { ok -> toast(ctx, if (ok) "تم القبول ✅" else "ما في مقاعد كافية أو الطلب تغيّر") }
                        }
                        CapBtn("رفض", Modifier.weight(1f), filled = false, color = CapDark) {
                            Repo.reject(b) { ok -> if (!ok) toast(ctx, "ما قدرت أرفض، جرّب كمان مرة") }
                        }
                    }
                }
            }
            if (!home) item {
                Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("القادمة" to upcoming.size, "الجارية" to running.size, "المكتملة" to done.size).forEachIndexed { i, (n, c) ->
                        CapChip(if (i < 2 && c > 0) "$n ($c)" else n, seg == i, Modifier.weight(1f)) { seg = i }
                    }
                }
            }
            if (home && running.isNotEmpty() && upcoming.isEmpty() && CapTripState.of("near_" + running.last().id) == "1") item {
                CapBtn("انشر رحلة من ${running.last().to}", Modifier.fillMaxWidth(), height = 54.dp) { onPublishNext() }
            }
            if (trips == null) item { LoadingBox() }
            else if (list.isEmpty()) item {
                Box(Modifier.fillMaxWidth().capCard().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text(when (seg) { 0 -> "ما عندك رحلات قادمة"; 1 -> "ما في رحلة جارية"; else -> "ما في رحلات مكتملة" }, color = CapMute)
                }
            }
            items(list.size, key = { "s${if (home) 9 else seg}" + list[it].id }) { i ->
                val t = list[i]
                val sg = if (home) (if (CapTripState.of(t.id) == "started") 1 else 0) else seg
                val reqs = bookings.filter { it.tripId == t.id && it.isActive && it.status == BK_ACCEPTED }
                val booked = (t.seatsTotal - t.seatsFree).coerceAtLeast(0)
                val isOpen = home || open == t.id
                val cancelled = t.status == TRIP_CANCELLED
                val label = when {
                    cancelled -> "ملغية"
                    sg == 2 -> "اكتملت"
                    sg == 1 -> "الرحلة قيد التنفيذ"
                    t.status == "full" && t.seatsFree > 0L -> "الحجز مغلق"
                    t.seatsFree <= 0L -> "مكتملة المقاعد"
                    booked > 0 -> "$booked حجوزات مؤكدة"
                    else -> "بانتظار الركاب"
                }
                Column(Modifier.fillMaxWidth().capCard().animateContentSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (home && !cancelled && sg < 2) CapLiveStatus(
                        if (sg == 1) "الرحلة جارية الآن" else if (t.status == "full" && t.seatsFree > 0L) "الحجز مغلق" else if (t.seatsFree <= 0L) "المقاعد مكتملة، جاهز للانطلاق" else if (booked > 0) "بانتظار باقي الركاب" else "بانتظار الركاب",
                        booked.toFloat() / t.seatsTotal.coerceAtLeast(1L).toFloat(), sg == 1
                    )
                    Row(
                        Modifier.fillMaxWidth().clickable(remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, null) { open = if (isOpen) "" else t.id },
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text("${t.from}  ←  ${t.to}", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = CapInk)
                            Text("${dayName(t.departAt)} • ${tripTimeLabel(t.note, t.departAt)} • ${priceText(t.price)}", fontSize = 12.sp, color = CapMute)
                            Text("المقاعد: $booked من ${t.seatsTotal} محجوزة", fontSize = 12.sp, color = CapMute)
                        }
                        Box(Modifier.clip(RoundedCornerShape(12.dp)).background(CapSurface).padding(horizontal = 12.dp, vertical = 7.dp)) {
                            Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (cancelled) CapRed else CapAccent)
                        }
                    }
                    if (sg == 0 && reqs.isNotEmpty()) CapBtn("بدء الرحلة", Modifier.fillMaxWidth(), color = Color(0xFF22A559), height = 48.dp) {
                        CapTripState.set(ctx, t.id, "started"); seg = 1
                    }
                    if (sg == 1) CapBtn("إنهاء الرحلة", Modifier.fillMaxWidth(), color = CapRed, height = 48.dp) { finishTrip = t }
                    if (isOpen) {
                        Text("إدارة الركاب", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = CapMute)
                        if (reqs.isEmpty()) Text("ما في ركاب لسا", fontSize = 13.sp, color = CapMute)
                        reqs.forEach { b -> RiderCard(b, canCancel = sg == 0,
                            onApproveReq = { Repo.cancelBooking(b, BK_CANCELLED_CAPTAIN, "بطلب العميل") { ok -> toast(ctx, if (ok) "انلغى الحجز" else "ما قدرت ألغي، جرّب كمان مرة") } },
                            onRejectReq = { Repo.setCancelReq(b, false) { ok -> toast(ctx, if (ok) "انرفض الطلب" else "ما انرفض، جرّب كمان مرة") } }) { cancelBooking = b } }
                        if (sg < 2) {
                            if (sg == 1) LiveShareControl(t)
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                CapBtn("مشاركة الرحلة", Modifier.weight(1f), filled = false, color = CapAccent, height = 42.dp) {
                                    try {
                                        val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(android.content.Intent.EXTRA_TEXT, "رحلة ${t.summary} • ${priceText(t.price)} • ${t.seatsFree} مقاعد فاضية — أبناء الشمال")
                                        }
                                        ctx.startActivity(android.content.Intent.createChooser(send, "مشاركة الرحلة"))
                                    } catch (_: Throwable) {}
                                }
                                if (sg == 0) CapBtn("إلغاء الرحلة", Modifier.weight(1f), filled = false, color = CapRed, height = 42.dp) { cancelTrip = t }
                            }
                        }
                    }
                }
            }
        }
    }

    finishTrip?.let { t ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { finishTrip = null },
            title = { Text("إنهاء رحلة ${t.from} ← ${t.to}؟") },
            text = { Text("بعد الإنهاء تنتقل الرحلة إلى «المكتملة».") },
            confirmButton = { androidx.compose.material3.TextButton(onClick = { CapTripState.set(ctx, t.id, "finished"); finishTrip = null; seg = 2 }) { Text("نعم، أنهِ الرحلة") } },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { finishTrip = null }) { Text("إلغاء") } }
        )
    }
    reqDialog?.let { (t, type) ->
        val ctxR = LocalContext.current
        var price by remember { mutableStateOf("") }
        var text by remember { mutableStateOf("") }
        var day by remember { mutableIntStateOf(0) }
        fun send(detail: String, newDepartAt: Long, newPrice: Double?, newNote: String) {
            val prof = me ?: return
            Repo.sendTripRequest(t, prof, type, detail, newDepartAt, newPrice, newNote) { ok ->
                toast(ctxR, if (ok) "انبعت الطلب للمشرف" else "ما قدرت أبعت الطلب، جرّب كمان مرة")
            }
            reqDialog = null
        }
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { reqDialog = null },
            title = { Text(when (type) { "edit" -> "طلب تعديل الرحلة"; "postpone" -> "طلب تأجيل الرحلة"; else -> "طلب إغلاق الحجز" }) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("${t.from} ← ${t.to}", fontWeight = FontWeight.Bold)
                    when (type) {
                        "edit" -> {
                            androidx.compose.material3.OutlinedTextField(value = price, onValueChange = { price = it.filter { c -> c.isDigit() || c == '.' }.take(5) }, singleLine = true, label = { Text("السعر الجديد (اختياري)") })
                            androidx.compose.material3.OutlinedTextField(value = text, onValueChange = { text = it.take(120) }, label = { Text("شو بدك تعدّل؟") })
                        }
                        "postpone" -> CapDays(pillDays(5), day) { day = it }
                        else -> Text("بيوقف الحجز على هالرحلة. بعد موافقة المشرف ما بتظهر للعملاء.")
                    }
                    Text("الطلب بيروح للمشرف، وما بيتطبق إلا بعد موافقته.", fontSize = 12.sp)
                }
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    when (type) {
                        "edit" -> if (price.isBlank() && text.isBlank()) toast(ctxR, "اكتب السعر أو شو بدك تعدّل")
                            else send(listOf(if (price.isNotBlank()) "السعر الجديد: $price" else "", text).filter { it.isNotBlank() }.joinToString(" • "), 0L, price.toDoubleOrNull(), "")
                        "postpone" -> send("تأجيل إلى ${dayName(dayStart(day) + 1439 * 60_000L)}", dayStart(day) + 1439 * 60_000L, null, "@T:-1,-1" + if (takesMafraq(t)) " @M" else "")
                        else -> send("إغلاق الحجز على الرحلة", 0L, null, "")
                    }
                }) { Text("أرسل للمشرف") }
            },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { reqDialog = null }) { Text("رجوع") } }
        )
    }
    cancelTrip?.let { t ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { cancelTrip = null },
            title = { Text("طلب إلغاء الرحلة؟") },
            text = { Text("${t.from} ← ${t.to}\nبينرسل للمشرف، وما بتنلغي إلا بعد موافقته.") },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    val prof = me
                    if (prof != null) Repo.sendTripRequest(t, prof, "cancelTrip", "", 0L, null, "") { ok -> toast(ctx, if (ok) "انبعت الطلب للمشرف" else "ما انبعت الطلب، جرّب كمان مرة") }
                    cancelTrip = null
                }) { Text("إرسال الطلب", color = CapRed) }
            },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { cancelTrip = null }) { Text("رجوع") } }
        )
    }
    cancelBooking?.let { b ->
        ReasonDialog(
            title = "طلب إلغاء حجز ${b.passengerName}؟ (بعد موافقة المشرف)", confirmText = "أرسل الطلب", onDismiss = { cancelBooking = null },
            onConfirm = { reason ->
                val prof = me
                val tr = trips?.firstOrNull { it.id == b.tripId }
                if (prof != null && tr != null) Repo.sendTripRequest(tr, prof, "cancelBooking", "${b.id}|${b.passengerName.take(30)}|${reason.take(90)}", 0L, null, "") { ok -> toast(ctx, if (ok) "انبعت الطلب للمشرف" else "ما انبعت الطلب، جرّب كمان مرة") }
                cancelBooking = null
            }
        )
    }
}

/** A live status strip: breathing dot, travelling dots and a seat-fill bar, so the home screen always looks alive. */
@Composable
private fun CapLiveStatus(text: String, fill: Float, running: Boolean) {
    val tr = androidx.compose.animation.core.rememberInfiniteTransition(label = "live")
    val pulse by tr.animateFloat(0.35f, 1f, androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(900), androidx.compose.animation.core.RepeatMode.Reverse), label = "p")
    val dots by tr.animateFloat(0f, 4f, androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(1600, easing = androidx.compose.animation.core.LinearEasing)), label = "d")
    val shown by androidx.compose.animation.core.animateFloatAsState(fill.coerceIn(0f, 1f), androidx.compose.animation.core.tween(700), label = "f")
    val col = if (running) Color(0xFF22A559) else CapAccent
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(10.dp).clip(RoundedCornerShape(5.dp)).background(col.copy(alpha = pulse)))
            Text(text + ".".repeat(dots.toInt().coerceIn(0, 3)), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = col)
        }
        Box(Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp)).background(CapInk.copy(alpha = 0.10f))) {
            Box(Modifier.fillMaxWidth(if (running) 1f else shown.coerceAtLeast(0.04f)).fillMaxHeight().clip(RoundedCornerShape(3.dp)).background(col.copy(alpha = if (running) 0.35f + 0.4f * pulse else 1f)))
        }
    }
}

// ===== Trip history (opened from الإعدادات) =====

@Composable
fun CapTripHistory(trips: List<Trip>?, bookings: List<Booking>, onBack: () -> Unit) {
    var open by rememberSaveable { mutableStateOf("") }
    val now = System.currentTimeMillis()
    val past = (trips ?: emptyList()).filter { t -> !(t.status != TRIP_CANCELLED && t.departAt > now - 4 * 3_600_000L) }.sortedByDescending { it.departAt }
    androidx.compose.foundation.lazy.LazyColumn(
        Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 44.dp, bottom = 170.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { CapBackBar("سجل الرحلات") { onBack() } }
        if (past.isEmpty()) item {
            Box(Modifier.fillMaxWidth().capCard().padding(24.dp), contentAlignment = Alignment.Center) { Text("ما في رحلات سابقة", color = CapMute) }
        }
            run {
                items(past.size, key = { "h" + past[it].id }) { i ->
                    val t = past[i]
                    val riders = bookings.filter { it.tripId == t.id && it.status == BK_ACCEPTED }
                    val isOpen = open == "h" + t.id
                    Column(Modifier.fillMaxWidth().capCard().animateContentSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            Modifier.fillMaxWidth().clickable(remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, null) { open = if (isOpen) "" else "h" + t.id },
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text("${t.from}  ←  ${t.to}", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = CapInk)
                                Text("${dayName(t.departAt)} • ${tripTimeLabel(t.note, t.departAt)} • ${priceText(t.price)}", fontSize = 12.sp, color = CapMute)
                            }
                            Text(if (t.status == TRIP_CANCELLED) "ملغية" else if (t.seatsFree <= 0L) "اكتملت" else "${riders.sumOf { it.seats }} راكب",
                                fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (t.status == TRIP_CANCELLED) CapRed else CapAccent)
                        }
                        if (isOpen) {
                            if (riders.isEmpty()) Text("ما في ركاب", fontSize = 13.sp, color = CapMute)
                            riders.forEach { b ->
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    CapAvatar(b.passengerName, 34.dp)
                                    Column(Modifier.weight(1f)) {
                                        Text(b.passengerName, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = CapInk)
                                        Text(seatsText(b.seats) + if (b.dropoff.isNotBlank()) " • ${b.dropoff}" else "", fontSize = 11.sp, color = CapMute)
                                    }
                                }
                            }
                        }
                    }
                }
            }
    }
}

// ===== Publish: direction, place, village, day, time, seats =====

@Composable
internal fun CapPublishPreview() = CapPublish(UserProfile(name = "أحمد", phone = "0790000000"), false, emptyList(), onTrips = {}, onPublished = {})

@Composable
internal fun CapBigChoice(title: String, sub: String, index: Int, onClick: () -> Unit) {
    Row(
        Modifier.entrance(index).fillMaxWidth().squeeze(onClick).capCard(28.dp).padding(horizontal = 22.dp, vertical = 26.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = CapInk)
            Text(sub, fontSize = 13.sp, color = CapMute, maxLines = 2, lineHeight = 20.sp)
        }
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(22.dp)).background(CapAccent), contentAlignment = Alignment.Center) {
            Box(Modifier.rotate(180f)) { Glyph(4, CapInk, 20.dp) }
        }
    }
}

private const val G_NORTH_C = "إلى عمّان / الزرقاء"
private const val G_SOUTH_C = "من عمّان / الزرقاء"
private const val G_INNER_C = "قرى الشمال"

/** Destinations for «من الشمال» and «من الجنوب» (kept here, in one place, to edit later). */
private val northToDests = listOf("عمّان", "الزرقاء", "المفرق")
private val southToDests = listOf("إربد", "الرمثا", "المغيّر", "الشجرة", "الطرة", "عمراوة", "الذنيبة")

/** «داخل الشمال»: places in three groups (Mafraq is not part of it). */
private val innerGroups = listOf(
    "المدن الرئيسية" to listOf("إربد", "الرمثا"),
    "قرى إربد" to listOf("المغيّر", "البويضة"),
    "قرى الرمثا" to listOf("الشجرة", "الطرة", "عمراوة", "الذنيبة")
)

@Composable
private fun InnerPicker(title: String, except: String?, onPick: (String) -> Unit) {
    Text(title, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = CapInk)
    innerGroups.forEach { (g, list) ->
        CapSection(g) { CapGrid(list.filter { it != except }, "", 2, big = true) { onPick(it) } }
    }
}

private fun roadLabel(r: String) = r

/** A road card: tap to unfold a small, tidy map of the places it passes, then «اختر هالطريق». Places are information only for the captain. */
@Composable
private fun CapRoadCard(title: String, places: List<String>, open: Boolean, index: Int, onToggle: () -> Unit, onPick: () -> Unit) {
    Column(Modifier.entrance(index).fillMaxWidth().capCard(28.dp).animateContentSize().padding(horizontal = 20.dp, vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth().squeeze(onToggle), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = CapInk)
                Text(if (title in innerRoadStops) "داخل الشمال" else "عمّان", fontSize = 13.sp, color = CapMute)
            }
            Box(Modifier.size(40.dp).clip(RoundedCornerShape(20.dp)).background(CapInk.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                Text(if (open) "−" else "+", fontSize = 22.sp, color = CapAccent, fontWeight = FontWeight.Bold)
            }
        }
        if (open) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                places.chunked(4).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        row.forEach { p ->
                            Box(Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(CapInk.copy(alpha = 0.08f)).padding(horizontal = 4.dp, vertical = 7.dp), contentAlignment = Alignment.Center) {
                                Text(p, fontSize = 10.5.sp, maxLines = 1, color = CapMute)
                            }
                        }
                        repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
            CapBtn("اختر هالطريق", Modifier.fillMaxWidth(), height = 48.dp) { onPick() }
        }
    }
}

@Composable
internal fun CapBackBar(title: String, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(46.dp).squeeze(onBack).capCard(23.dp), contentAlignment = Alignment.Center) { Glyph(4, CapInk, 20.dp) }
        Text(title, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = CapInk, maxLines = 1)
    }
}

/** One car = one open trip at a time. After «بدء الرحلة» the next trip can only start from where the running one ends. */
@Composable
private fun CapPublish(me: UserProfile, online: Boolean, trips: List<Trip>, onTrips: () -> Unit, onPublished: () -> Unit) {
    val ctx = LocalContext.current
    LaunchedEffect(Unit) { CapTripState.load(ctx) }
    val now = System.currentTimeMillis()
    val live = trips.filter { it.status != TRIP_CANCELLED && it.departAt > now - 4 * 3_600_000L }
    val waiting = live.filter { CapTripState.of(it.id) == "" }.minByOrNull { it.departAt }
    val running = trips.filter { it.status != TRIP_CANCELLED && CapTripState.of(it.id) == "started" }.maxByOrNull { it.departAt }
    if (waiting != null) {
        Column(Modifier.fillMaxSize().padding(horizontal = 18.dp).padding(top = 44.dp, bottom = 170.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("عندك رحلة منشورة", Modifier.padding(top = 6.dp), fontSize = 28.sp, fontWeight = FontWeight.Bold, color = CapInk)
            Column(Modifier.fillMaxWidth().capCard().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("${waiting.from}  ←  ${waiting.to}", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = CapInk)
                Text("${dayName(waiting.departAt)} • ${tripTimeLabel(waiting.note, waiting.departAt)}", fontSize = 13.sp, color = CapMute)
            }
            Text("ابدأ رحلتك أو ألغها من «رحلاتي»، وبعدها بتقدر تنشر الرحلة اللي بعدها.", fontSize = 14.sp, color = CapMute, lineHeight = 22.sp)
            CapBtn("افتح رحلاتي", Modifier.fillMaxWidth()) { onTrips() }
        }
    } else CapPublishFlow(me, online, running?.to, onPublished)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CapPublishFlow(me: UserProfile, online: Boolean, lockFrom: String?, onPublished: () -> Unit) {
    val ctx = LocalContext.current
    var step by rememberSaveable { mutableIntStateOf(0) }      // 0 = direction, 1 = places, 2 = time and publish
    var group by rememberSaveable { mutableStateOf("") }       // من الشمال / من الجنوب / داخل الشمال
    var origin by rememberSaveable { mutableStateOf("") }
    var dest by rememberSaveable { mutableStateOf("") }
    var road by rememberSaveable { mutableStateOf("") }
    var day by rememberSaveable { mutableIntStateOf(0) }
    var fromMin by rememberSaveable { mutableIntStateOf(-1) }
    var toMin by rememberSaveable { mutableIntStateOf(-1) }
    var seats by rememberSaveable { mutableIntStateOf(me.maxSeats.coerceIn(1, 8)) }
    var priceStr by rememberSaveable { mutableStateOf("") }
    var pick by remember { mutableIntStateOf(0) }   // 0 = closed, 1 = from, 2 = to
    var sending by remember { mutableStateOf(false) }
    var openRoad by remember { mutableStateOf("") }
    var manualStart by rememberSaveable { mutableStateOf(false) }   // true = the captain chose to pick his starting point by hand
    var gpsHere by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(lockFrom) {
        if (lockFrom == null && hasLocationPermission(ctx)) fetchLocation(ctx) { l -> if (l != null) gpsHere = nearestPlace(l.latitude, l.longitude) }
    }
    // In Amman / Zarqa by GPS: go straight to «وين رايح بالشمال؟»
    LaunchedEffect(gpsHere, manualStart) {
        val h = gpsHere
        if (lockFrom == null && !manualStart && step == 0 && group.isBlank() && (h == AMMAN || h == ZARQA)) { group = G_SOUTH_C; origin = h; priceStr = ""; step = 1 }
    }
    var carryMafraq by rememberSaveable { mutableStateOf(false) }

    val inner = group == G_INNER_C
    val fromNorth = group == G_NORTH_C
    val priceChips = if (inner) listOf("1", "1.5") else listOf("3", "3.5", "4", "4.5", "5")
    val maxSeats = me.maxSeats.coerceIn(1, 8)

    fun clearLast(): Boolean = when {
        road.isNotBlank() -> { road = ""; true }
        dest.isNotBlank() -> { dest = ""; true }
        origin.isNotBlank() && lockFrom == null && (manualStart || gpsHere == null || gpsHere !in northPlaces) -> { origin = ""; true }
        else -> false
    }
    LaunchedEffect(step, group) {
        if (lockFrom != null && (lockFrom == AMMAN || lockFrom == ZARQA) && group.isBlank()) { group = G_SOUTH_C; priceStr = ""; step = 1 }
    }
    fun goBack() {
        when (step) {
            2 -> { step = 1; clearLast() }
            1 -> if (!clearLast()) { step = 0; group = ""; openRoad = ""; if (gpsHere == AMMAN || gpsHere == ZARQA) manualStart = true }
        }
    }
    BackHandler(enabled = step > 0) { goBack() }

    val colMod = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp).padding(top = 44.dp, bottom = 170.dp)
    when (step) {
        0 -> Column(colMod, verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.size(10.dp).clip(androidx.compose.foundation.shape.CircleShape).background(if (online) Color(0xFF34C759) else CapMute))
                Text(if (online) "أونلاين: عندك رحلة منشورة" else "أوفلاين: ما عندك رحلة منشورة", fontSize = 13.sp, color = if (online) Color(0xFF34C759) else CapMute)
            }
            val lf = lockFrom ?: gpsHere?.takeIf { !manualStart && it in northPlaces }
            if (lf != null) {
                if (lockFrom == null) Box(Modifier.clip(RoundedCornerShape(14.dp)).background(CapAccent.copy(alpha = 0.14f)).border(1.dp, CapAccent, RoundedCornerShape(14.dp)).clickable { manualStart = true }.padding(horizontal = 16.dp, vertical = 11.dp)) {
                    Text("📍 تغيير نقطة الانطلاق يدوياً", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = CapInk)
                }
                Text("انطلاقك من $lf", Modifier.padding(top = 6.dp, bottom = 4.dp), fontSize = 32.sp, fontWeight = FontWeight.Bold, color = CapInk)
                Text(if (lockFrom != null) "وصلت إلى $lf، وين رايح بعدها؟" else "حسب موقعك الحالي، وين رايح؟", fontSize = 14.sp, color = CapMute)
                CapBigChoice(G_NORTH_C, "من الشمال", 0) { group = G_NORTH_C; origin = lf; priceStr = ""; step = 1 }
                if (lf != "المفرق") CapBigChoice(G_INNER_C, "الرمثا • إربد • باقي القرى", 1) { group = G_INNER_C; origin = lf; priceStr = ""; step = 1 }
            } else {
                Text("وجهة انطلاقك؟", Modifier.padding(top = 6.dp, bottom = 4.dp), fontSize = 32.sp, fontWeight = FontWeight.Bold, color = CapInk)
                CapBigChoice(G_NORTH_C, "من الشمال", 0) { group = G_NORTH_C; priceStr = ""; step = 1 }
                CapBigChoice(G_SOUTH_C, "إلى قرى الشمال: الرمثا • إربد • باقي القرى", 1) { group = G_SOUTH_C; priceStr = ""; step = 1 }
                CapBigChoice(G_INNER_C, "الرمثا • إربد • باقي القرى", 2) { group = G_INNER_C; priceStr = ""; step = 1 }
            }
        }
        1 -> Column(colMod, verticalArrangement = Arrangement.spacedBy(14.dp)) {
            CapBackBar(if (origin.isNotBlank() && dest.isBlank()) origin else group) { goBack() }
            when {
                // من الشمال: from which place, to Amman / Zarqa / Mafraq, then the road
                fromNorth && origin.isBlank() -> CapSection("وين بالشمال؟") { CapGrid(northPlaces.filter { it != "المفرق" }, origin, 2, big = true) { origin = it } }
                fromNorth && dest.isBlank() -> CapSection("إلى أين؟") { CapGrid(northToDests.filter { it != origin }, dest, 2, big = true) { dest = it } }
                // من الجنوب: to which place in the north, then the road
                group == G_SOUTH_C && lockFrom == null && origin.isBlank() -> CapSection("من وين؟") { CapGrid(listOf(AMMAN, ZARQA), origin, 2, big = true) { origin = it } }
                group == G_SOUTH_C && dest.isBlank() -> CapSection("وين رايح بالشمال؟") { CapGrid(southToDests + "المفرق", dest, 2, big = true) { dest = it; if (it != "المفرق") step = 2 } }
                // داخل الشمال: from, then to, both grouped
                inner && origin.isBlank() -> InnerPicker("من أين؟", null) { origin = it }
                inner && dest.isBlank() -> InnerPicker("إلى أين؟", origin) { dest = it; if (!innerNeedsRoad(origin, it)) step = 2 }
                !inner && dest == "المفرق" -> CapSection("وين بالمفرق؟") { CapGrid(listOf("دوار الدبابة", "داخل المفرق"), road, 2, big = true) { road = it; step = 2 } }
                inner -> {
                    Text("من أي طريق؟", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = CapInk)
                    innerRoadsFor(origin, dest).forEachIndexed { i, r ->
                        CapRoadCard(r, innerRoadStops[r] ?: emptyList(), openRoad == r, i, onToggle = { openRoad = if (openRoad == r) "" else r }) { road = r; step = 2 }
                    }
                }
                else -> {
                    Text("اختر الطريق", fontSize = 15.sp, color = CapMute)
                    southRoads.entries.forEachIndexed { i, (r, places) ->
                        CapRoadCard(roadLabel(r), places, openRoad == r, i, onToggle = { openRoad = if (openRoad == r) "" else r }) { road = r; step = 2 }
                    }
                }
            }
        }
        else -> Column(colMod, verticalArrangement = Arrangement.spacedBy(14.dp)) {
            val sPlace = lockFrom?.takeIf { it == AMMAN || it == ZARQA } ?: origin.ifBlank { AMMAN }
            val tFrom = if (group == G_SOUTH_C) sPlace else origin
            CapBackBar(tFrom + " ← " + dest + if (road.isBlank()) "" else " • " + roadLabel(road)) { goBack() }
            CapSection("اليوم والوقت (اختياري)") {
                CapDays(pillDays(5), day) { day = it }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    listOf(1 to fromMin, 2 to toMin).forEach { (which, v) ->
                        Box(Modifier.weight(1f).height(52.dp).clip(RoundedCornerShape(16.dp)).background(CapInk.copy(alpha = 0.08f)).clickable { pick = which }, contentAlignment = Alignment.Center) {
                            Text(
                                (if (which == 1) "من " else "إلى ") + if (v >= 0) clockText(dayStart(0) + v * 60_000L) else "—",
                                fontSize = 15.sp, fontWeight = FontWeight.Bold, color = if (v >= 0) CapInk else CapAccent
                            )
                        }
                    }
                    if (fromMin >= 0 || toMin >= 0) Box(Modifier.size(44.dp).clickable { fromMin = -1; toMin = -1 }, contentAlignment = Alignment.Center) { Text("✕", fontSize = 18.sp, color = CapRed) }
                }
                Text(if (fromMin >= 0 || toMin >= 0) "متواجد بهالفترة، أو لحد ما يكتمل العدد" else "بدون وقت: متاح لحد ما يكتمل العدد", fontSize = 12.sp, color = CapMute)
            }
            if (dest != "المفرق" && group == G_SOUTH_C) CapSection("تحمل ركاب المفرق؟") {
                CapGrid(listOf("لا", "نعم"), if (carryMafraq) "نعم" else "لا", 2) { carryMafraq = it == "نعم" }
                Text("إذا نعم، رحلتك بتظهر كمان لعملاء المفرق، وطلباتهم بتوصلك بعلامة «راكب المفرق»", fontSize = 12.sp, color = CapMute)
            }
            CapSection("المقاعد") {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    Row(Modifier.clip(RoundedCornerShape(16.dp)).background(CapInk.copy(alpha = 0.08f)).height(52.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(44.dp).clickable { if (seats < maxSeats) seats++ }, contentAlignment = Alignment.Center) { Text("+", fontSize = 24.sp, color = CapInk) }
                        Text("$seats مقاعد", Modifier.padding(horizontal = 14.dp), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = CapInk)
                        Box(Modifier.size(44.dp).clickable { if (seats > 1) seats-- }, contentAlignment = Alignment.Center) { Text("−", fontSize = 24.sp, color = CapInk) }
                    }
                }
            }
            CapSection("السعر (اختياري)") {
                CapGrid(listOf("حسب الاتفاق") + priceChips.map { "$it د.أ" }, if (priceStr.isBlank()) "حسب الاتفاق" else "$priceStr د.أ", 3) {
                    priceStr = if (it == "حسب الاتفاق") "" else it.removeSuffix(" د.أ")
                }
            }

            CapBtn(if (sending) "جاري النشر..." else "انشر الرحلة", Modifier.fillMaxWidth(), height = 58.dp) {
                val departAt = dayStart(day) + (if (toMin >= 0) toMin else 1439) * 60_000L
                when {
                    group.isBlank() -> toast(ctx, "اختر وجهة انطلاقك")
                    group != G_SOUTH_C && origin.isBlank() -> toast(ctx, "اختر مكان انطلاقك")
                    dest.isBlank() -> toast(ctx, "اختر وجهة الوصول")
                    !inner && road.isBlank() && (group != G_SOUTH_C || dest == "المفرق") -> toast(ctx, "اختر الطريق")
                    fromMin >= 0 && toMin >= 0 && toMin <= fromMin -> toast(ctx, "وقت النهاية لازم يكون بعد البداية")
                    departAt < System.currentTimeMillis() -> toast(ctx, "هالوقت فات، اختر وقت جاي")
                    sending -> {}
                    else -> {
                        val (from, to, roadField) = when (group) {
                            G_SOUTH_C -> Triple(sPlace, dest, road)
                            G_NORTH_C -> Triple(origin, dest, road)
                            else -> Triple(origin, dest, road.ifBlank { NORTH_INNER })
                        }
                        sending = true
                        Repo.publishTrip(
                            Trip(
                                captainName = me.name, captainPhone = me.phone, carText = me.carText,
                                from = from, to = to, road = roadField,
                                departAt = departAt, seatsTotal = seats.toLong(), price = priceStr.toDoubleOrNull(),
                                acceptsParcels = false, note = "@T:$fromMin,$toMin" + if (carryMafraq && dest != "المفرق" && group == G_SOUTH_C) " @M" else ""
                            )
                        ) { ok ->
                            sending = false
                            if (ok) { toast(ctx, "تم نشر الرحلة ✅"); onPublished() }
                            else toast(ctx, "ما قدرت أنشر. تأكد من الإنترنت ومن موافقة المشرف على حسابك")
                        }
                    }
                }
            }
        }
    }

    if (pick != 0) {
        val cur = if (pick == 1) fromMin else toMin
        val st = rememberTimePickerState(initialHour = if (cur >= 0) cur / 60 else 7, initialMinute = if (cur >= 0) cur % 60 else 0, is24Hour = false)
        AlertDialog(
            onDismissRequest = { pick = 0 },
            confirmButton = { TextButton(onClick = { val v = st.hour * 60 + st.minute; if (pick == 1) fromMin = v else toMin = v; pick = 0 }) { Text("تم") } },
            dismissButton = { TextButton(onClick = { pick = 0 }) { Text("إلغاء") } },
            text = { CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) { TimePicker(state = st) } }
        )
    }
}

/** One button to start sharing the live location, one to stop. */
@Composable
internal fun LiveShareControl(t: Trip) {
    val ctx = LocalContext.current
    val active by LiveShareService.active.collectAsState()
    val now = System.currentTimeMillis()
    val inWindow = t.status != TRIP_CANCELLED && (tripWindow(t.note) != null || now >= t.departAt - 60 * 60_000L) && now <= t.departAt + 4 * 3_600_000L
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
