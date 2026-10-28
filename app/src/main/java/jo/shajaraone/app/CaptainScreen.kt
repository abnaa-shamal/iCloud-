package jo.shajaraone.app

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.ui.graphics.Color
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

@Composable
fun CaptainHome(me: UserProfile) {
    val uid = Repo.auth.currentUser?.uid ?: ""
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var bookings by remember { mutableStateOf<List<Booking>>(emptyList()) }
    DisposableEffect(uid) {
        val r = Repo.listenBookings("captainUid", uid) { bookings = it }
        onDispose { r.remove() }
    }
    val pending = bookings.count { it.status == BK_PENDING && it.departAt > System.currentTimeMillis() }
    // Feedback when a NEW request arrives (not on the first load).
    val feedbackCtx = LocalContext.current
    var lastPending by remember { mutableIntStateOf(-1) }
    LaunchedEffect(pending) {
        if (lastPending >= 0 && pending > lastPending) AppFeedback.newRequest(feedbackCtx)
        lastPending = pending
    }
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (tab == 0) MyTrips(me, onPublish = { tab = 1 }) else PublishTrip(me, onPublished = { tab = 0 })
        }
        NavigationBar(containerColor = navBarColor()) {
            NavigationBarItem(
                selected = tab == 0, onClick = { tab = 0 },
                icon = { BadgedBox(badge = { if (pending > 0) Badge { Text("$pending") } }) { Icon(Icons.AutoMirrored.Filled.List, null) } },
                label = { Text("رحلاتي والطلبات") },
                colors = navItemColors()
            )
            NavigationBarItem(
                selected = tab == 1, onClick = { tab = 1 },
                icon = { Icon(Icons.Filled.Add, null) },
                label = { Text("نشر رحلة") },
                colors = navItemColors()
            )
        }
    }
}

// ===== Publish =====

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PublishTrip(me: UserProfile, onPublished: () -> Unit) {
    val ctx = LocalContext.current
    val route = rememberRouteState()
    var day by rememberSaveable { mutableIntStateOf(-1) }
    var minutes by rememberSaveable { mutableIntStateOf(-1) }
    var seats by rememberSaveable { mutableIntStateOf(4) }
    var fixedPrice by rememberSaveable { mutableStateOf(true) }
    var priceStr by rememberSaveable { mutableStateOf("1") }
    var parcels by rememberSaveable { mutableStateOf(false) }
    var note by rememberSaveable { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }

    // Inside-north trips default to a fixed 1 JD; everything else to «حسب الاتفاق», until the captain touches the price.
    LaunchedEffect(route.kind) {
        val d = defaultFixedPrice(route.kind)
        fixedPrice = d != null
        priceStr = if (d != null) "1" else ""
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("نشر رحلة جديدة", style = MaterialTheme.typography.headlineSmall)
        AppCard {
            Text("١. الخط", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            RoutePicker(route, allowAll = false)
        }

        AppCard {
            Text("٢. الوقت", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            WhenPicker(
                dayOffset = day.takeIf { it >= 0 }, minutes = minutes.takeIf { it >= 0 },
                onDay = { day = it ?: -1 }, onMinutes = { minutes = it ?: -1 }, allowClear = false
            )
        }

        AppCard {
            Text("٣. المقاعد والسعر", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            SectionLabel("كم مقعد فاضي؟ (اضغط على المقعد)")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                for (i in 1..me.maxSeats.coerceIn(1, 8)) {
                    SeatShape(if (i <= seats) SeatState.PICKED else SeatState.FREE, 38.dp) { seats = i }
                }
                Spacer(Modifier.weight(1f))
                Text("$seats", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
            }
            SectionLabel("السعر للراكب")
            ChoiceChips(listOf("سعر محدد", "حسب الاتفاق"), if (fixedPrice) "سعر محدد" else "حسب الاتفاق") {
                fixedPrice = it == "سعر محدد"
                if (fixedPrice && priceStr.isBlank()) priceStr = "1"
            }
            if (fixedPrice) {
                ScrollChips(listOf("1", "1.5", "2", "3", "5").map { it to "$it د.أ" }, priceStr) { priceStr = it }
                OutlinedTextField(
                    value = priceStr,
                    onValueChange = { v -> priceStr = v.filter { it.isDigit() || it == '.' }.take(5) },
                    label = { Text("السعر") }, suffix = { Text("دينار") }, singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        AppCard {
            Text("٤. تفاصيل إضافية", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("بقبل أمانات بهالرحلة", modifier = Modifier.weight(1f))
                Switch(checked = parcels, onCheckedChange = { parcels = it })
            }
            OutlinedTextField(
                value = note, onValueChange = { note = it.take(120) },
                label = { Text("ملاحظة (اختياري)") }, placeholder = { Text("مثلاً: بطلع من عند الجامع") },
                shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()
            )
        }

        BigButton(if (sending) "جاري النشر..." else "انشر الرحلة", enabled = !sending) {
            val price = if (fixedPrice) priceStr.toDoubleOrNull() else null
            val departAt = if (day >= 0 && minutes >= 0) dayStart(day) + minutes * 60_000L else 0L
            when {
                !route.complete() -> toast(ctx, "كمّل اختيار الخط")
                day < 0 -> toast(ctx, "اختار اليوم")
                minutes < 0 -> toast(ctx, "اختار ساعة الانطلاق")
                departAt < System.currentTimeMillis() -> toast(ctx, "هالوقت فات، اختار وقت جاي")
                fixedPrice && (price == null || price <= 0.0) -> toast(ctx, "اكتب السعر صح")
                else -> {
                    sending = true
                    Repo.publishTrip(
                        Trip(
                            captainName = me.name, captainPhone = me.phone, carText = me.carText,
                            from = route.from(), to = route.to(), road = route.roadField(),
                            departAt = departAt, seatsTotal = seats.toLong(), price = price,
                            acceptsParcels = parcels, note = note.trim()
                        )
                    ) { ok ->
                        sending = false
                        if (ok) {
                            toast(ctx, "تم نشر الرحلة ✅")
                            minutes = -1
                            note = ""
                            onPublished()
                        } else {
                            toast(ctx, "ما قدرت أنشر. تأكد من الإنترنت ومن موافقة المشرف على حسابك")
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

// ===== My trips and their bookings =====

@Composable
private fun MyTrips(me: UserProfile, onPublish: () -> Unit) {
    val ctx = LocalContext.current
    val uid = Repo.auth.currentUser?.uid ?: ""
    var trips by remember { mutableStateOf<List<Trip>?>(null) }
    var bookings by remember { mutableStateOf<List<Booking>>(emptyList()) }
    var cancelTrip by remember { mutableStateOf<Trip?>(null) }
    var cancelBooking by remember { mutableStateOf<Booking?>(null) }

    DisposableEffect(uid) {
        val a = Repo.listenTrips({ all -> trips = all.filter { it.captainUid == uid } }, { if (trips == null) trips = emptyList() })
        val b = Repo.listenBookings("captainUid", uid) { bookings = it }
        onDispose {
            a.remove()
            b.remove()
        }
    }

    val list = trips
    when {
        list == null -> LoadingBox()
        else -> LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                val pend = bookings.count { it.status == BK_PENDING && it.departAt > System.currentTimeMillis() }
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        StatTile("رحلاتك", "${list.count { it.status != TRIP_CANCELLED }}", Modifier.weight(1f))
                        StatTile("طلبات جديدة", "$pend", Modifier.weight(1f), highlight = pend > 0)
                    }
                    BigButton("انشر رحلة جديدة", onClick = onPublish)
                }
            }
            if (list.isEmpty()) item { EmptyState("🚗", "ما عندك رحلات جاية.\nانشر أول رحلة بالزر فوق.") }
            items(list, key = { it.id }) { t ->
                val reqs = bookings.filter { it.tripId == t.id }
                    .sortedBy { if (it.status == BK_PENDING) 0 else if (it.status == BK_ACCEPTED) 1 else 2 }
                AppCard {
                    TripInfo(t, showCaptain = false)
                    LiveShareControl(t)
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                    if (reqs.isEmpty()) {
                        Text("ما في حجوزات لسا", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        Text(
                            "الحجوزات (${reqs.count { it.isActive }})",
                            style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold
                        )
                        reqs.forEach { b -> BookingRow(b, onCancel = { cancelBooking = b }) }
                    }
                    if (t.status != TRIP_CANCELLED) {
                        TextButton(onClick = { cancelTrip = t }) {
                            Text("إلغاء الرحلة كاملة", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }

    cancelTrip?.let { t ->
        ReasonDialog(
            title = "إلغاء رحلة ${t.from} ← ${t.to}؟",
            confirmText = "ألغِ الرحلة",
            onDismiss = { cancelTrip = null },
            onConfirm = { reason ->
                val active = bookings.filter { it.tripId == t.id && it.isActive }
                Repo.cancelTrip(t, reason, active) { ok -> toast(ctx, if (ok) "انلغت الرحلة" else "ما قدرت ألغي، جرّب كمان مرة") }
                cancelTrip = null
            }
        )
    }

    cancelBooking?.let { b ->
        ReasonDialog(
            title = "إلغاء حجز ${b.passengerName}؟",
            confirmText = "ألغِ الحجز",
            onDismiss = { cancelBooking = null },
            onConfirm = { reason ->
                Repo.cancelBooking(b, BK_CANCELLED_CAPTAIN, reason) { ok ->
                    toast(ctx, if (ok) "انلغى الحجز" else "ما قدرت ألغي، جرّب كمان مرة")
                }
                cancelBooking = null
            }
        )
    }
}

@Composable
internal fun StatTile(label: String, value: String, modifier: Modifier = Modifier, highlight: Boolean = false) {
    val cs = MaterialTheme.colorScheme
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = if (highlight) Brand.Violet.copy(alpha = 0.28f) else Color.White.copy(alpha = 0.07f),
        border = BorderStroke(1.dp, if (highlight) Brand.VioletSoft.copy(alpha = 0.7f) else Color.White.copy(alpha = 0.14f))
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(value, style = MaterialTheme.typography.headlineSmall, color = if (highlight) Brand.Teal else Color.White)
            Text(label, style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
        }
    }
}

@Composable
internal fun BookingRow(b: Booking, onCancel: () -> Unit) {
    val ctx = LocalContext.current
    val active = b.isActive
    val cs = MaterialTheme.colorScheme
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = when (b.status) {
            BK_PENDING -> cs.secondaryContainer.copy(alpha = 0.55f)
            BK_ACCEPTED -> cs.primaryContainer.copy(alpha = 0.7f)
            else -> cs.surfaceContainer
        },
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Avatar(b.passengerName, 38.dp)
                Column(Modifier.weight(1f)) {
                    Text(b.passengerName, style = MaterialTheme.typography.titleSmall)
                    Text(
                        seatsText(b.seats) + if (b.dropoff.isNotBlank()) " • ${b.dropoff}" else "",
                        style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant
                    )
                }
                Text(bookingStatusText(b.status), style = MaterialTheme.typography.labelMedium)
            }
            if (b.reason.isNotBlank() && !active) Text("السبب: ${b.reason}", color = cs.onSurfaceVariant)
            if (active) {
                // Row 1: the decision (accept / reject, or cancel). Row 2: contact buttons.
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (b.status == BK_PENDING) {
                        Button(
                            onClick = { Repo.accept(b) { ok -> toast(ctx, if (ok) "تم القبول ✅" else "ما في مقاعد كافية أو الطلب تغيّر") } },
                            shape = MaterialTheme.shapes.medium, modifier = Modifier.weight(1f).height(48.dp)
                        ) { Text("قبول") }
                        OutlinedButton(
                            onClick = { Repo.reject(b) { ok -> if (!ok) toast(ctx, "ما قدرت أرفض، جرّب كمان مرة") } },
                            shape = MaterialTheme.shapes.medium, modifier = Modifier.weight(1f).height(48.dp)
                        ) { Text("رفض") }
                    } else {
                        OutlinedButton(
                            onClick = onCancel,
                            shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth().height(48.dp)
                        ) { Text("إلغاء الحجز") }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    ShareLocationButton(b.passengerPhone)
                    CallButton(b.passengerPhone)
                    WhatsAppButton(b.passengerPhone, "مرحبا ${b.passengerName}، بخصوص حجزك برحلة ${b.tripSummary}")
                }
            }
        }
    }
}

/** Optional live location sharing for one trip: available from an hour before departure until a few hours after. */
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
    val sharing = active == t.id
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            Modifier.fillMaxWidth().background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(16.dp)).padding(14.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                Modifier.size(40.dp).background((if (sharing) Brand.Teal else Color.White).copy(alpha = 0.14f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.LocationOn, null, tint = if (sharing) Brand.Teal else Color.White.copy(alpha = 0.7f))
            }
            Column(Modifier.weight(1f)) {
                Text(if (sharing) "المشاركة شغّالة" else "المشاركة متوقفة", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(
                    if (sharing) "الركاب المقبولون بيشوفوا موقعك" else "شغّلها عند بدء الرحلة",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        when {
            sharing -> OutlinedButton(
                onClick = { LiveShareService.stop(ctx) },
                shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth().height(50.dp)
            ) { Text("إيقاف مشاركة موقعي", color = MaterialTheme.colorScheme.error) }
            active != null -> Text("بتشارك موقعك لرحلة ثانية. وقّفها أول.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            else -> BigButton("بدء الرحلة ومشاركة الموقع") {
                if (hasLocationPermission(ctx)) LiveShareService.start(ctx, t.id)
                else ask.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
            }
        }
        if (!sharing && active == null) {
            Text("جهّز مشوارك قبل القيادة", style = MaterialTheme.typography.bodySmall, color = Brand.VioletLight, modifier = Modifier.align(Alignment.CenterHorizontally))
        }
    }
}
