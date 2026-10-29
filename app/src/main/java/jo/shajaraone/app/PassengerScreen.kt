package jo.shajaraone.app

import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Search
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun PassengerHome(me: UserProfile) {
    val uid = Repo.auth.currentUser?.uid ?: ""
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var myBookings by remember { mutableStateOf<List<Booking>>(emptyList()) }
    var dest by rememberSaveable { mutableIntStateOf(0) }
    var day by rememberSaveable { mutableIntStateOf(-1) }

    DisposableEffect(uid) {
        val r = Repo.listenBookings("passengerUid", uid) { myBookings = it }
        onDispose { r.remove() }
    }

    val upcoming = myBookings.count { it.isActive && it.departAt > System.currentTimeMillis() }
    Box(Modifier.fillMaxSize()) {
        androidx.compose.animation.AnimatedContent(
            targetState = tab,
            transitionSpec = {
                (androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(260)) +
                    androidx.compose.animation.slideInVertically(androidx.compose.animation.core.tween(260)) { it / 12 }) togetherWith
                    androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(140))
            }, label = "page"
        ) { t ->
            when (t) {
                0 -> TripsTab(me, myBookings, dest, { dest = it }, day, { day = it })
                else -> MyBookings(myBookings)
            }
        }
        Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth(), contentAlignment = Alignment.Center) {
            VantaNav(listOf(1 to "الرحلات", 2 to (if (upcoming > 0) "حجوزاتي ($upcoming)" else "حجوزاتي")), tab, { tab = it })
        }
    }
}

// ===== Browse trips =====

val destChoices = listOf("الكل", "عمّان", "الزرقاء", "إربد", "راجع لعندنا")

/** Both choices are optional: dest 0 / day -1 mean «any». */
fun vantaMatches(t: Trip, dest: Int, day: Int): Boolean {
    val kind = kindOf(t)
    val zarqa = t.road == "طريق الزرقاء"
    val destOk = when (dest) {
        1 -> kind == KIND_NS && !zarqa && t.road != NORTH_INNER
        2 -> kind == KIND_NS && zarqa
        3 -> kind == KIND_NN || t.to == "إربد"
        4 -> kind == KIND_SN
        else -> true
    }
    return destOk && (day < 0 || dayOffsetOf(t.departAt) == day)
}

/** The whole rider home: pick a day, pick a place, tap «احجز». Everything is optional. */
@Composable
fun RiderTripsView(
    name: String, shown: List<Trip>?, failed: Boolean, dest: Int, onDest: (Int) -> Unit, day: Int, onDay: (Int) -> Unit,
    actions: @Composable ColumnScope.(Trip) -> Unit
) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 120.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp).padding(top = 44.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Column {
                    Text("أهلاً $name", fontSize = 14.sp, color = Dim)
                    Text("وين رايح؟", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
                DayPills(pillDays(5), day) { onDay(if (day == it) -1 else it) }
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    destChoices.forEachIndexed { i, n ->
                        Box(
                            Modifier.clip(RoundedCornerShape(20.dp)).background(if (dest == i) Amber else Color(0xFF2B2B2E))
                                .clickable { onDest(i) }.padding(horizontal = 16.dp, vertical = 10.dp)
                        ) { Text(n, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = if (dest == i) Color.Black else Color.White) }
                    }
                }
            }
        }
        when {
            shown == null -> item { LoadingBox() }
            failed && shown.isEmpty() -> item { EmptyState("📶", "ما قدرت أجيب الرحلات، تأكد من الإنترنت") }
            shown.isEmpty() -> item { EmptyState("🚌", "ما في رحلات بهالاختيار.\nجرّب يوم ثاني.") }
            else -> items(shown.size) { i ->
                val t = shown[i]
                TripCard(t, Modifier.padding(horizontal = 18.dp), index = i, nearest = i == 0 && day < 0 && dest == 0, actions = { actions(t) })
            }
        }
    }
}

@Composable
private fun MiniContact(phone: String, msg: String) {
    val ctx = LocalContext.current
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        GhostBtn("اتصال", Modifier.weight(1f)) { callPhone(ctx, phone) }
        GhostBtn("واتساب", Modifier.weight(1f)) { openWhatsApp(ctx, phone, msg) }
    }
}

@Composable
private fun TripsTab(
    me: UserProfile, myBookings: List<Booking>,
    dest: Int, onDest: (Int) -> Unit, day: Int, onDay: (Int) -> Unit
) {
    var trips by remember { mutableStateOf<List<Trip>?>(null) }
    var failed by remember { mutableStateOf(false) }
    var booking by remember { mutableStateOf<Trip?>(null) }
    var tracking by remember { mutableStateOf<Trip?>(null) }
    var needVerify by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        val r = Repo.listenTrips(
            onChange = { trips = it; failed = false },
            onError = { failed = true; if (trips == null) trips = emptyList() }
        )
        onDispose { r.remove() }
    }

    val now = System.currentTimeMillis()
    val shown = trips?.filter { t ->
        t.captainUid.isNotBlank() && t.captainUid != me.uid && t.status != TRIP_CANCELLED && t.departAt > now &&
            vantaMatches(t, dest, day)
    }

    RiderTripsView(me.name.trim().substringBefore(" "), shown, failed, dest, onDest, day, onDay) { t ->
        val mine = myBookings.firstOrNull { it.tripId == t.id && it.isActive }
        when {
            mine?.status == BK_ACCEPTED -> {
                AmberBtn("تتبّع الكابتن") { tracking = t }
                MiniContact(t.captainPhone, "مرحبا كابتن ${t.captainName}، بخصوص رحلة ${t.from} ← ${t.to} (${timeText(t.departAt)})")
            }
            mine != null -> Box(Modifier.fillMaxWidth().height(46.dp).clip(RoundedCornerShape(23.dp)).background(Color(0xFF2B2B2E)), contentAlignment = Alignment.Center) {
                Text("${seatsText(mine.seats)} • ${bookingStatusText(mine.status)}", fontSize = 14.sp, color = Amber, fontWeight = FontWeight.SemiBold)
            }
            t.isBookable -> AmberBtn("احجز") { if (me.verified) booking = t else needVerify = true }
        }
    }

    tracking?.let { t -> LiveTrackDialog(t.id, t.captainName, t.captainPhone) { tracking = null } }

    if (needVerify) {
        AlertDialog(
            onDismissRequest = { needVerify = false },
            title = { Text("وثّق رقمك أولاً") },
            text = {
                Text(
                    if (me.verifyCode.isNotBlank()) "ما بتقدر تحجز قبل ما نتأكد من رقمك. ابعت الرمز ${me.verifyCode} من رقمك على واتساب الدعم، وأول ما المشرف يوثّقه بتفتحلك الحجوزات."
                    else "ما بتقدر تحجز قبل ما نتأكد من رقمك. تواصل مع المشرف على واتساب ليوثّقه."
                )
            },
            confirmButton = {
                val ctx = LocalContext.current
                TextButton(onClick = {
                    needVerify = false
                    if (me.verifyCode.isNotBlank()) sendVerifyWhatsApp(ctx, me)
                    else openWhatsApp(ctx, SUPPORT_PHONE, "مرحبا، بدي أوثّق رقمي: ${me.phone}")
                }) { Text("إرسال عبر واتساب") }
            },
            dismissButton = { TextButton(onClick = { needVerify = false }) { Text("لاحقاً") } }
        )
    }

    booking?.let { t ->
        BookingDialog(t, me, onClose = { booking = null }, onBooked = { booking = null })
    }
}


/** Top-view car drawn as a glass outline: tap the free seats you want. Taken seats are locked, the driver is fixed. */
@Composable
internal fun CarSeatMap(total: Int, free: Int, picked: Set<Int>, onToggle: (Int) -> Unit) {
    val taken = (total - free).coerceAtLeast(0)
    fun state(i: Int) = when {
        i < taken -> SeatState.TAKEN
        i in picked -> SeatState.PICKED
        else -> SeatState.FREE
    }
    val seat = 50.dp
    val body = Brand.VioletSoft
    Box(
        Modifier
            .width(236.dp)
            .drawBehind {
                val w = size.width
                val h = size.height
                val side = 14.dp.toPx()
                // Wheels poke out of the sides.
                val wheelH = 46.dp.toPx()
                listOf(h * 0.17f, h * 0.68f).forEach { y ->
                    drawRoundRect(body.copy(alpha = 0.30f), Offset(0f, y), Size(side + 4.dp.toPx(), wheelH), CornerRadius(6.dp.toPx()))
                    drawRoundRect(body.copy(alpha = 0.30f), Offset(w - side - 4.dp.toPx(), y), Size(side + 4.dp.toPx(), wheelH), CornerRadius(6.dp.toPx()))
                }
                // Side mirrors.
                drawRoundRect(body.copy(alpha = 0.45f), Offset(2.dp.toPx(), h * 0.20f), Size(12.dp.toPx(), 8.dp.toPx()), CornerRadius(4.dp.toPx()))
                drawRoundRect(body.copy(alpha = 0.45f), Offset(w - 14.dp.toPx(), h * 0.20f), Size(12.dp.toPx(), 8.dp.toPx()), CornerRadius(4.dp.toPx()))
                // Body.
                val bodyTl = Offset(side, 0f)
                val bodySize = Size(w - side * 2, h)
                drawRoundRect(Brand.Pressed.copy(alpha = 0.6f), bodyTl, bodySize, CornerRadius(80.dp.toPx(), 80.dp.toPx()))
                drawRoundRect(body.copy(alpha = 0.6f), bodyTl, bodySize, CornerRadius(80.dp.toPx(), 80.dp.toPx()), style = androidx.compose.ui.graphics.drawscope.Stroke(1.5.dp.toPx()))
                // Windshield.
                drawRoundRect(
                    body.copy(alpha = 0.40f), Offset(side + 28.dp.toPx(), 26.dp.toPx()), Size(w - side * 2 - 56.dp.toPx(), 30.dp.toPx()),
                    CornerRadius(10.dp.toPx()), style = androidx.compose.ui.graphics.drawscope.Stroke(1.2.dp.toPx())
                )
                // Rear window.
                drawRoundRect(
                    body.copy(alpha = 0.30f), Offset(side + 30.dp.toPx(), h - 40.dp.toPx()), Size(w - side * 2 - 60.dp.toPx(), 16.dp.toPx()),
                    CornerRadius(8.dp.toPx()), style = androidx.compose.ui.graphics.drawscope.Stroke(1.2.dp.toPx())
                )
            }
            .padding(start = 40.dp, end = 40.dp, top = 84.dp, bottom = 80.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(34.dp)) {
            // Front row: passenger seat on the right, driver on the left (car seen from above, facing up).
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                if (total >= 1) SeatShape(state(0), seat, "1") { onToggle(0) } else Spacer(Modifier.size(seat))
                Box(contentAlignment = Alignment.Center) {
                    SeatShape(SeatState.DRIVER, seat)
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Filled.Person, null, tint = Brand.Muted, modifier = Modifier.size(18.dp))
                        Text("الكابتن", fontSize = 10.sp, color = Brand.Muted, fontWeight = FontWeight.Bold)
                    }
                }
            }
            (1 until total).toList().chunked(3).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    row.forEach { i -> SeatShape(state(i), seat, "${i + 1}") { onToggle(i) } }
                    repeat(3 - row.size) { Spacer(Modifier.size(seat)) }
                }
            }
        }
    }
}

/** Seat legend: free, taken, yours. */
@Composable
internal fun SeatLegend() {
    Row(
        Modifier.fillMaxWidth().background(Brand.Pressed.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
            .border(1.dp, Brand.Rule, RoundedCornerShape(10.dp)).padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically
    ) {
        LegendItem("شاغر") { Box(Modifier.size(18.dp).border(1.5.dp, Brand.Rule, CircleShape)) }
        LegendItem("محجوز") { Icon(Icons.Filled.Lock, null, tint = Brand.Muted, modifier = Modifier.size(18.dp)) }
        LegendItem("اختيارك") { Box(Modifier.size(18.dp).background(Brand.Violet, CircleShape)) }
    }
}

@Composable
private fun LegendItem(text: String, icon: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        icon()
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BookingDialog(t: Trip, me: UserProfile, onClose: () -> Unit, onBooked: () -> Unit) {
    val ctx = LocalContext.current
    val total = t.seatsTotal.toInt().coerceIn(1, 8)
    val free = t.seatsFree.toInt().coerceIn(1, total)
    var picked by remember { mutableStateOf(setOf(total - free)) }
    var point by remember { mutableStateOf("") }
    var other by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    val areas = subAreasFor(t.to, t.road)
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onClose,
        sheetState = sheet,
        containerColor = MaterialTheme.colorScheme.background
    ) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding().imePadding()
                .padding(horizontal = 20.dp).padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("${t.from}  ←  ${t.to}", style = MaterialTheme.typography.titleLarge)
            Text(
                "${timeText(t.departAt)}  •  ${priceText(t.price)}",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text("اضغط على المقاعد اللي بدك ياها", style = MaterialTheme.typography.titleSmall)
            CarSeatMap(total, free, picked) { i ->
                val taken = total - free
                if (i >= taken) {
                    picked = if (i in picked) {
                        if (picked.size > 1) picked - i else picked
                    } else picked + i
                }
            }
            SeatLegend()
            Text(
                "اخترت ${seatsText(picked.size.toLong())}",
                style = MaterialTheme.typography.titleMedium, color = Brand.Violet
            )

            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionLabel("وين بدك تنزل؟")
                if (areas != null) {
                    ChoiceChips(areas + ANY_PLACE + OTHER_PLACE, point) { point = it }
                    if (point == OTHER_PLACE) {
                        OutlinedTextField(
                            value = other, onValueChange = { other = it.take(60) },
                            label = { Text("اكتب المكان") }, singleLine = true,
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                } else {
                    OutlinedTextField(
                        value = other, onValueChange = { other = it.take(60) },
                        label = { Text(if (t.to == IRBID_OTHER) "وين بالضبط بإربد؟" else "نقطة النزول (اختياري)") }, placeholder = { Text("مثلاً: مجمع الشمال") },
                        singleLine = true, shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            BigButton(if (sending) "لحظة..." else "أرسل الطلب", enabled = !sending) {
                val dropoff = if (areas != null && point.isNotBlank() && point != OTHER_PLACE) point
                    else if (areas != null && point.isBlank()) ANY_PLACE else other.trim()
                when {
                    dropoff.isBlank() && t.to == IRBID_OTHER -> toast(ctx, "اكتب نقطة النزول")
                    else -> {
                        sending = true
                        Repo.book(t, me, picked.size.toLong(), dropoff.ifBlank { ANY_PLACE }) { ok ->
                            sending = false
                            if (ok) {
                                AppFeedback.success(ctx)
                                toast(ctx, "تم إرسال طلبك، الكابتن رح يرد عليك")
                                onBooked()
                            } else {
                                AppFeedback.error(ctx)
                                toast(ctx, "ما قدرت أرسل، تأكد من الإنترنت")
                            }
                        }
                    }
                }
            }
        }
    }
}

// ===== My bookings =====

@Composable
private fun MyBookings(list: List<Booking>) {
    val ctx = LocalContext.current
    var toCancel by remember { mutableStateOf<Booking?>(null) }
    var tracking by remember { mutableStateOf<Booking?>(null) }
    val now = System.currentTimeMillis()
    val sorted = list.sortedWith(compareByDescending<Booking> { it.isActive && it.departAt > now }.thenBy { it.departAt })

    LazyColumn(
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 44.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.fillMaxSize()
    ) {
        item { Text("حجوزاتي", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Color.White) }
        if (sorted.isEmpty()) item { EmptyState("🎫", "ما عندك حجوزات لسا.") }
        items(sorted.size) { i ->
            val b = sorted[i]
            val upcoming = b.departAt > now
            Column(Modifier.entrance(i).fillMaxWidth().darkGlass(24.dp).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(b.tripSummary, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Text("${timeText(b.departAt)} • ${seatsText(b.seats)}", fontSize = 12.sp, color = Dim)
                    }
                    Box(Modifier.clip(RoundedCornerShape(12.dp)).background(Color(0xFF3A3A3E)).padding(horizontal = 12.dp, vertical = 7.dp)) {
                        Text(bookingStatusText(b.status), fontSize = 12.sp, color = if (b.status == BK_ACCEPTED) Amber else Color(0xFFD7D7DB))
                    }
                }
                Text("الكابتن ${b.captainName}", fontSize = 13.sp, color = Dim)
                if (b.reason.isNotBlank()) Text("السبب: ${b.reason}", fontSize = 12.sp, color = Color(0xFFFF8A80))
                if (b.status == BK_ACCEPTED && upcoming) {
                    AmberBtn("تتبّع الكابتن") { tracking = b }
                    MiniContact(b.captainPhone, "مرحبا كابتن ${b.captainName}، بخصوص حجزي برحلة ${b.tripSummary}")
                }
                if (b.isActive && upcoming) GhostBtn("إلغاء الحجز", Modifier.fillMaxWidth(), Color(0xFFFF8A80)) { toCancel = b }
            }
        }
    }

    tracking?.let { b -> LiveTrackDialog(b.tripId, b.captainName, b.captainPhone) { tracking = null } }

    toCancel?.let { b ->
        AlertDialog(
            onDismissRequest = { toCancel = null },
            title = { Text("إلغاء الحجز؟") },
            text = { Text("${b.tripSummary} • ${timeText(b.departAt)}\nالمقعد بيرجع متاح لغيرك.") },
            confirmButton = {
                TextButton(onClick = {
                    Repo.cancelBooking(b, BK_CANCELLED_PASSENGER, "") { ok ->
                        toast(ctx, if (ok) "انلغى الحجز" else "ما قدرت ألغي، جرّب كمان مرة")
                    }
                    toCancel = null
                }) { Text("ألغِ الحجز", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { toCancel = null }) { Text("رجوع") } }
        )
    }
}
