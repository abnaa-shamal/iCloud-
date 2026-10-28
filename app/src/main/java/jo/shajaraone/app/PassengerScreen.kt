package jo.shajaraone.app

import androidx.compose.foundation.background
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

private const val ALL = "all"

@Composable
fun PassengerHome(me: UserProfile) {
    val uid = Repo.auth.currentUser?.uid ?: ""
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var myBookings by remember { mutableStateOf<List<Booking>>(emptyList()) }

    DisposableEffect(uid) {
        val r = Repo.listenBookings("passengerUid", uid) { myBookings = it }
        onDispose { r.remove() }
    }

    val upcoming = myBookings.count { it.isActive && it.departAt > System.currentTimeMillis() }
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (tab == 0) TripsList(me, myBookings, onBooked = { tab = 1 }) else MyBookings(myBookings)
        }
        NavigationBar(containerColor = navBarColor()) {
            NavigationBarItem(
                selected = tab == 0, onClick = { tab = 0 },
                icon = { Icon(Icons.Filled.Search, null) },
                label = { Text("الرحلات") },
                colors = navItemColors()
            )
            NavigationBarItem(
                selected = tab == 1, onClick = { tab = 1 },
                icon = {
                    BadgedBox(badge = { if (upcoming > 0) Badge { Text("$upcoming") } }) {
                        Icon(Icons.AutoMirrored.Filled.List, null)
                    }
                },
                label = { Text("حجوزاتي") },
                colors = navItemColors()
            )
        }
    }
}

// ===== Browse trips =====

@Composable
private fun TripsList(me: UserProfile, myBookings: List<Booking>, onBooked: () -> Unit) {
    val ctx = LocalContext.current
    var trips by remember { mutableStateOf<List<Trip>?>(null) }
    var failed by remember { mutableStateOf(false) }
    val route = rememberRouteState()
    var day by rememberSaveable { mutableIntStateOf(-1) }
    var mins by rememberSaveable { mutableIntStateOf(-1) }
    var booking by remember { mutableStateOf<Trip?>(null) }
    var tracking by remember { mutableStateOf<Trip?>(null) }
    var needVerify by remember { mutableStateOf(false) }
    var filterOpen by rememberSaveable { mutableStateOf(false) }

    DisposableEffect(Unit) {
        val r = Repo.listenTrips(
            onChange = { trips = it; failed = false },
            onError = { failed = true; if (trips == null) trips = emptyList() }
        )
        onDispose { r.remove() }
    }

    val now = System.currentTimeMillis()
    val shown = trips.orEmpty().filter { t ->
        t.captainUid.isNotBlank() && t.captainUid != me.uid && t.status != TRIP_CANCELLED && t.departAt > now &&
            route.matches(t) &&
            (day < 0 || dayOffsetOf(t.departAt) == day) &&
            (mins < 0 || (t.departAt - dayStart(dayOffsetOf(t.departAt))) / 60_000L >= mins)
    }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            val routeText = if (route.kind.isBlank()) "كل الخطوط"
                else listOf(route.from().ifBlank { "أي مكان" }, route.to().ifBlank { "أي مكان" }).joinToString("  ←  ")
            val dayText = if (day < 0) "كل الأيام" else dateLongText(dayStart(day))
            FilterSummaryBar("$routeText  •  $dayText", filterOpen) { filterOpen = !filterOpen }
        }
        if (filterOpen) item {
            AppCard {
                RoutePicker(route, allowAll = true)
                SectionLabel("متى؟")
                WhenPicker(
                    dayOffset = day.takeIf { it >= 0 }, minutes = mins.takeIf { it >= 0 },
                    onDay = { day = it ?: -1 }, onMinutes = { mins = it ?: -1 }, allowClear = true
                )
            }
        }
        item {
            Text(
                if (trips == null) "" else "${shown.size} رحلة متاحة",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }
        when {
            trips == null -> item { LoadingBox() }
            failed && shown.isEmpty() -> item { EmptyState("📶", "ما قدرت أجيب الرحلات، تأكد من الإنترنت") }
            shown.isEmpty() -> item { EmptyState("🚌", "ما في رحلات بهالاختيار هلأ.\nجرّب يوم أو خط ثاني.") }
            else -> items(shown, key = { it.id }) { t ->
                val mine = myBookings.firstOrNull { it.tripId == t.id && it.isActive }
                AppCard {
                    TripInfo(t)
                    if (mine?.status == BK_ACCEPTED) {
                        Button(
                            onClick = { tracking = t },
                            shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth().height(48.dp)
                        ) { Text("تتبّع الكابتن على الخريطة") }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                    when {
                        mine != null -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Box(Modifier.weight(1f)) {
                                Pill(
                                    "حجزك: ${seatsText(mine.seats)} — ${bookingStatusText(mine.status)}",
                                    MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                            ShareLocationButton(t.captainPhone)
                        }
                        t.isBookable -> Button(
                            onClick = { if (me.verified) booking = t else needVerify = true },
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier.fillMaxWidth().height(52.dp)
                        ) { Text("احجز مقعد", style = MaterialTheme.typography.titleMedium) }
                    }
                    ContactRow(
                        t.to, t.captainPhone,
                        "مرحبا كابتن ${t.captainName}، بخصوص رحلة ${t.from} ← ${t.to} (${timeText(t.departAt)})"
                    )
                }
            }
        }
    }

    tracking?.let { t -> LiveTrackDialog(t.id, t.captainName, t.captainPhone) { tracking = null } }

    if (needVerify) {
        val ctx = LocalContext.current
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
        BookingDialog(t, me, onClose = { booking = null }, onBooked = { booking = null; onBooked() })
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
                drawRoundRect(Color.White.copy(alpha = 0.05f), bodyTl, bodySize, CornerRadius(80.dp.toPx(), 80.dp.toPx()))
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
                        Icon(Icons.Filled.Person, null, tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.size(18.dp))
                        Text("الكابتن", fontSize = 10.sp, color = Color.White.copy(alpha = 0.8f), fontWeight = FontWeight.Bold)
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
        Modifier.fillMaxWidth().background(Color.White.copy(alpha = 0.06f), RoundedCornerShape(16.dp))
            .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(16.dp)).padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically
    ) {
        LegendItem("شاغر") { Box(Modifier.size(18.dp).border(1.5.dp, Brand.VioletSoft, CircleShape)) }
        LegendItem("محجوز") { Icon(Icons.Filled.Lock, null, tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(18.dp)) }
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
                style = MaterialTheme.typography.titleMedium, color = Brand.Teal
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
    // Upcoming first, then past/finished ones.
    val sorted = list.sortedWith(compareByDescending<Booking> { it.isActive && it.departAt > now }.thenBy { it.departAt })

    if (sorted.isEmpty()) {
        EmptyState("🗂️", "ما عندك حجوزات لسا.\nاحجز من تبويب «الرحلات».")
    } else {
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(sorted, key = { it.id }) { b ->
                val upcoming = b.departAt > now
                val cs = MaterialTheme.colorScheme
                val (stBg, stFg) = when (b.status) {
                    BK_ACCEPTED -> cs.primaryContainer to cs.onPrimaryContainer
                    BK_PENDING -> cs.secondaryContainer to cs.onSecondaryContainer
                    else -> cs.errorContainer to cs.onErrorContainer
                }
                AppCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            b.tripSummary, style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f)
                        )
                        Pill(bookingStatusText(b.status), stBg, stFg)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        TimeBlock(b.departAt)
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(seatsText(b.seats), style = MaterialTheme.typography.titleSmall)
                            if (b.dropoff.isNotBlank()) {
                                Text("النزول: ${b.dropoff}", style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                            }
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Avatar(b.captainName, 34.dp)
                        Text("الكابتن ${b.captainName}", color = cs.onSurfaceVariant)
                    }
                    if (b.reason.isNotBlank()) Text("السبب: ${b.reason}", color = cs.error)
                    if (b.status == BK_ACCEPTED && upcoming) {
                        Button(
                            onClick = { tracking = b },
                            shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth().height(48.dp)
                        ) { Text("تتبّع الكابتن على الخريطة") }
                    }
                    if (b.isActive && upcoming) {
                        HorizontalDivider(color = cs.outlineVariant.copy(alpha = 0.6f))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            OutlinedButton(
                                onClick = { toCancel = b },
                                shape = MaterialTheme.shapes.medium,
                                modifier = Modifier.weight(1f).height(48.dp)
                            ) { Text("إلغاء الحجز", color = cs.error) }
                            ShareLocationButton(b.captainPhone)
                            CallButton(b.captainPhone)
                            WhatsAppButton(b.captainPhone, "مرحبا كابتن ${b.captainName}، بخصوص حجزي برحلة ${b.tripSummary}")
                        }
                    }
                }
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
