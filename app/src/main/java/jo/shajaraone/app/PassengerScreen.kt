package jo.shajaraone.app

import androidx.compose.foundation.background
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
        NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest) {
            NavigationBarItem(
                selected = tab == 0, onClick = { tab = 0 },
                icon = { Icon(Icons.Filled.Search, null) },
                label = { Text("الرحلات") }
            )
            NavigationBarItem(
                selected = tab == 1, onClick = { tab = 1 },
                icon = {
                    BadgedBox(badge = { if (upcoming > 0) Badge { Text("$upcoming") } }) {
                        Icon(Icons.AutoMirrored.Filled.List, null)
                    }
                },
                label = { Text("حجوزاتي") }
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
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            AppCard {
                Text("وين رايح؟", style = MaterialTheme.typography.titleLarge)
                SectionLabel("من")
                ScrollChips(placeOptions, from) { from = it }
                SectionLabel("إلى")
                ScrollChips(placeOptions, to) { to = it }
                SectionLabel("متى؟")
                ScrollChips(dayOptions, day) { day = it }
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
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        when {
                            mine != null -> Box(Modifier.weight(1f)) {
                                Pill(
                                    "حجزك: ${seatsText(mine.seats)} — ${bookingStatusText(mine.status)}",
                                    MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                            t.isBookable -> Button(
                                onClick = { booking = t },
                                shape = MaterialTheme.shapes.medium,
                                modifier = Modifier.weight(1f).height(48.dp)
                            ) { Text("احجز مقعد", style = MaterialTheme.typography.titleMedium) }
                            else -> Spacer(Modifier.weight(1f))
                        }
                        CallButton(t.captainPhone)
                        WhatsAppButton(
                            t.captainPhone,
                            "مرحبا كابتن ${t.captainName}، بخصوص رحلة ${t.from} ← ${t.to} (${timeText(t.departAt)})"
                        )
                    }
                }
            }
        }
    }

    booking?.let { t ->
        BookingDialog(t, me, onClose = { booking = null }, onBooked = { booking = null; onBooked() })
    }
}

/** Top-view car: tap the free seats you want. Taken seats are greyed, the driver is fixed. */
@Composable
internal fun CarSeatMap(total: Int, free: Int, picked: Set<Int>, onToggle: (Int) -> Unit) {
    val taken = (total - free).coerceAtLeast(0)
    fun state(i: Int) = when {
        i < taken -> SeatState.TAKEN
        i in picked -> SeatState.PICKED
        else -> SeatState.FREE
    }
    Column(
        Modifier
            .width(210.dp)
            .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(40.dp))
            .padding(horizontal = 22.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Front row: passenger seat on the right, driver on the left (as seen from above, car facing up).
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            if (total >= 1) SeatShape(state(0), 40.dp) { onToggle(0) } else Spacer(Modifier.size(40.dp))
            Text("الكابتن", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            SeatShape(SeatState.DRIVER, 40.dp)
        }
        (1 until total).toList().chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                row.forEach { i -> SeatShape(state(i), 40.dp) { onToggle(i) } }
                repeat(3 - row.size) { Spacer(Modifier.size(40.dp)) }
            }
        }
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
            Text(
                "اخترت ${seatsText(picked.size.toLong())}",
                style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary
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
                        label = { Text("نقطة النزول") }, placeholder = { Text("مثلاً: مجمع الشمال") },
                        singleLine = true, shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            BigButton(if (sending) "لحظة..." else "أرسل الطلب", enabled = !sending) {
                val dropoff = if (areas != null && point.isNotBlank() && point != OTHER_PLACE) point
                    else if (areas != null && point.isBlank()) ANY_PLACE else other.trim()
                when {
                    dropoff.isBlank() -> toast(ctx, "اكتب نقطة النزول")
                    else -> {
                        sending = true
                        Repo.book(t, me, picked.size.toLong(), dropoff) { ok ->
                            sending = false
                            if (ok) {
                                toast(ctx, "تم إرسال طلبك، الكابتن رح يرد عليك")
                                onBooked()
                            } else toast(ctx, "ما قدرت أرسل، تأكد من الإنترنت")
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
                    if (b.isActive && upcoming) {
                        HorizontalDivider(color = cs.outlineVariant.copy(alpha = 0.6f))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            OutlinedButton(
                                onClick = { toCancel = b },
                                shape = MaterialTheme.shapes.medium,
                                modifier = Modifier.weight(1f).height(48.dp)
                            ) { Text("إلغاء الحجز", color = cs.error) }
                            CallButton(b.captainPhone)
                            WhatsAppButton(b.captainPhone, "مرحبا كابتن ${b.captainName}، بخصوص حجزي برحلة ${b.tripSummary}")
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
