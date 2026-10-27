package com.shajara.rides

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private const val ALL = "all"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PassengerHome(me: UserProfile) {
    val uid = Repo.auth.currentUser?.uid ?: ""
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var myBookings by remember { mutableStateOf<List<Booking>>(emptyList()) }

    DisposableEffect(uid) {
        val r = Repo.listenBookings("passengerUid", uid) { myBookings = it }
        onDispose { r.remove() }
    }

    Column(Modifier.fillMaxSize()) {
        PrimaryTabRow(selectedTabIndex = tab) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("🔎 الرحلات") })
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = {
                val n = myBookings.count { it.isActive && it.departAt > System.currentTimeMillis() }
                Text(if (n > 0) "🗂️ حجوزاتي ($n)" else "🗂️ حجوزاتي")
            })
        }
        if (tab == 0) TripsList(me, myBookings, onBooked = { tab = 1 }) else MyBookings(myBookings)
    }
}

// ===== Browse trips =====

@Composable
private fun TripsList(me: UserProfile, myBookings: List<Booking>, onBooked: () -> Unit) {
    val ctx = LocalContext.current
    var trips by remember { mutableStateOf<List<Trip>?>(null) }
    var failed by remember { mutableStateOf(false) }
    var from by rememberSaveable { mutableStateOf(ALL) }
    var to by rememberSaveable { mutableStateOf(ALL) }
    var day by rememberSaveable { mutableStateOf(ALL) }
    var booking by remember { mutableStateOf<Trip?>(null) }

    DisposableEffect(Unit) {
        val r = Repo.listenTrips(
            onChange = { trips = it; failed = false },
            onError = { failed = true; if (trips == null) trips = emptyList() }
        )
        onDispose { r.remove() }
    }

    val placeOptions = listOf(ALL to "الكل") + allPlaces.map { it to it }
    val dayOptions = listOf(ALL to "كل الأيام") + nextDays()
    val now = System.currentTimeMillis()
    val shown = trips.orEmpty().filter { t ->
        t.captainUid.isNotBlank() && t.captainUid != me.uid && t.status != TRIP_CANCELLED && t.departAt > now &&
            (from == ALL || t.from == from) && (to == ALL || t.to == to) &&
            (day == ALL || dayOffsetOf(t.departAt) == day.toInt())
    }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SectionLabel("📍 من")
                ScrollChips(placeOptions, from) { from = it }
                SectionLabel("🏁 إلى")
                ScrollChips(placeOptions, to) { to = it }
                SectionLabel("📅 اليوم")
                ScrollChips(dayOptions, day) { day = it }
            }
        }
        when {
            trips == null -> item { LoadingBox() }
            failed && shown.isEmpty() -> item { EmptyState("📶", "ما قدرت أجيب الرحلات، تأكد من الإنترنت") }
            shown.isEmpty() -> item { EmptyState("🚌", "ما في رحلات بهالاختيار هلأ.\nجرّب يوم أو خط ثاني.") }
            else -> items(shown, key = { it.id }) { t ->
                val mine = myBookings.firstOrNull { it.tripId == t.id && it.isActive }
                AppCard {
                    TripInfo(t)
                    when {
                        mine != null -> Pill(
                            "حجزك: ${seatsText(mine.seats)} — ${bookingStatusText(mine.status)}",
                            MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        t.isBookable -> BigButton("📩 احجز مقعد") { booking = t }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { callPhone(ctx, t.captainPhone) },
                            shape = MaterialTheme.shapes.medium, modifier = Modifier.weight(1f)
                        ) { Text("📞 اتصال") }
                        OutlinedButton(
                            onClick = {
                                openWhatsApp(
                                    ctx, t.captainPhone,
                                    "مرحبا كابتن ${t.captainName}، بخصوص رحلة ${t.from} ← ${t.to} (${timeText(t.departAt)})"
                                )
                            },
                            shape = MaterialTheme.shapes.medium, modifier = Modifier.weight(1f)
                        ) { Text("💬 واتساب") }
                    }
                }
            }
        }
    }

    booking?.let { t ->
        BookingDialog(t, me, onClose = { booking = null }, onBooked = { booking = null; onBooked() })
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BookingDialog(t: Trip, me: UserProfile, onClose: () -> Unit, onBooked: () -> Unit) {
    val ctx = LocalContext.current
    var seats by remember { mutableIntStateOf(1) }
    var point by remember { mutableStateOf("") }
    var other by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    val areas = subAreasFor(t.to, t.road)

    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("حجز برحلة ${t.from} ← ${t.to}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("🕒 ${timeText(t.departAt)} • ${priceText(t.price)}")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("كم مقعد؟", modifier = Modifier.weight(1f))
                    Stepper(seats, 1, t.seatsFree.toInt().coerceAtLeast(1)) { seats = it }
                }
                SectionLabel("📍 وين بدك تنزل؟")
                if (areas != null) {
                    ChoiceChips(areas + ANY_PLACE + OTHER_PLACE, point) { point = it }
                    if (point == OTHER_PLACE) {
                        OutlinedTextField(
                            value = other, onValueChange = { other = it.take(60) },
                            label = { Text("اكتب المكان") }, singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                } else {
                    OutlinedTextField(
                        value = other, onValueChange = { other = it.take(60) },
                        label = { Text("نقطة النزول") }, placeholder = { Text("مثلاً: مجمع الشمال") },
                        singleLine = true, modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            Button(enabled = !sending, onClick = {
                val dropoff = if (areas != null && point.isNotBlank() && point != OTHER_PLACE) point
                    else if (areas != null && point.isBlank()) ANY_PLACE else other.trim()
                when {
                    dropoff.isBlank() -> toast(ctx, "اكتب نقطة النزول")
                    else -> {
                        sending = true
                        Repo.book(t, me, seats.toLong(), dropoff) { ok ->
                            sending = false
                            if (ok) {
                                toast(ctx, "تم إرسال طلبك، الكابتن رح يرد عليك")
                                onBooked()
                            } else toast(ctx, "ما قدرت أرسل، تأكد من الإنترنت")
                        }
                    }
                }
            }) { Text(if (sending) "لحظة..." else "أرسل الطلب") }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("إلغاء") } }
    )
}

// ===== My bookings =====

@Composable
private fun MyBookings(list: List<Booking>) {
    val ctx = LocalContext.current
    var toCancel by remember { mutableStateOf<Booking?>(null) }
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
                AppCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            b.tripSummary, style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f)
                        )
                        Text(bookingStatusText(b.status), style = MaterialTheme.typography.labelLarge)
                    }
                    Text("🕒 ${timeText(b.departAt)} • ${seatsText(b.seats)}")
                    if (b.dropoff.isNotBlank()) Text("📍 النزول: ${b.dropoff}")
                    Text("الكابتن ${b.captainName}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (b.reason.isNotBlank()) Text("السبب: ${b.reason}", color = MaterialTheme.colorScheme.error)
                    if (b.isActive && upcoming) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            OutlinedButton(onClick = { toCancel = b }) {
                                Text("إلغاء الحجز", color = MaterialTheme.colorScheme.error)
                            }
                            Spacer(Modifier.weight(1f))
                            FilledTonalIconButton(onClick = { callPhone(ctx, b.captainPhone) }) { Text("📞") }
                            FilledTonalIconButton(onClick = {
                                openWhatsApp(ctx, b.captainPhone, "مرحبا كابتن ${b.captainName}، بخصوص حجزي برحلة ${b.tripSummary}")
                            }) { Text("💬") }
                        }
                    }
                }
            }
        }
    }

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
