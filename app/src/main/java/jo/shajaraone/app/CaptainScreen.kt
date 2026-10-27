package jo.shajaraone.app

import androidx.compose.foundation.layout.*
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CaptainHome(me: UserProfile) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize()) {
        PrimaryTabRow(selectedTabIndex = tab) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("📝 نشر رحلة") })
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("🚗 رحلاتي والطلبات") })
        }
        if (tab == 0) PublishTrip(me, onPublished = { tab = 1 }) else MyTrips(me)
    }
}

// ===== Publish =====

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PublishTrip(me: UserProfile, onPublished: () -> Unit) {
    val ctx = LocalContext.current
    var from by rememberSaveable { mutableStateOf("الشجرة") }
    var to by rememberSaveable { mutableStateOf("إربد") }
    var road by rememberSaveable { mutableStateOf(tripRoads[0]) }
    var day by rememberSaveable { mutableStateOf("0") }
    var hour by rememberSaveable { mutableIntStateOf(7) }
    var minute by rememberSaveable { mutableIntStateOf(0) }
    var timeChosen by rememberSaveable { mutableStateOf(false) }
    var showTime by remember { mutableStateOf(false) }
    var seats by rememberSaveable { mutableIntStateOf(4) }
    var fixedPrice by rememberSaveable { mutableStateOf(true) }
    var priceStr by rememberSaveable { mutableStateOf("1") }
    var parcels by rememberSaveable { mutableStateOf(false) }
    var note by rememberSaveable { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }

    fun setRoute(f: String, t: String) {
        from = f
        to = t
        val d = defaultFixedPrice(f, t)
        fixedPrice = d != null
        priceStr = if (d != null) "1" else ""
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        AppCard {
            SectionLabel("📍 من وين؟")
            ChoiceChips(allPlaces, from) { setRoute(it, to) }
            SectionLabel("🏁 لوين؟")
            ChoiceChips(allPlaces, to) { setRoute(from, it) }
            if (!isValidRoute(from, to)) {
                Text(
                    "الرحلة لازم تكون بين منطقة (الشجرة، الذنيبة، عمراوة، الطرة، الرمثا) ومدينة (إربد، عمّان، الزرقاء).",
                    color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall
                )
            }
            if (needsRoadChoice(from, to)) {
                SectionLabel("🛣️ الطريق")
                ChoiceChips(tripRoads, road) { road = it }
            }
        }

        AppCard {
            SectionLabel("📅 اليوم")
            ScrollChips(nextDays(), day) { day = it }
            OutlinedButton(
                onClick = { showTime = true },
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                Text(
                    if (timeChosen) "🕒 الساعة ${clockText(departMillis(day.toInt(), hour, minute))}"
                    else "🕒 اختار ساعة الانطلاق"
                )
            }
        }

        AppCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("💺 المقاعد الفاضية", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Stepper(seats, 1, me.maxSeats) { seats = it }
            }
            SectionLabel("💰 السعر للراكب")
            ChoiceChips(listOf("سعر محدد", "حسب الاتفاق"), if (fixedPrice) "سعر محدد" else "حسب الاتفاق") {
                fixedPrice = it == "سعر محدد"
                if (fixedPrice && priceStr.isBlank()) priceStr = "1"
            }
            if (fixedPrice) {
                OutlinedTextField(
                    value = priceStr,
                    onValueChange = { v -> priceStr = v.filter { it.isDigit() || it == '.' }.take(5) },
                    label = { Text("السعر") }, suffix = { Text("دينار") }, singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("📦 بقبل أمانات بهالرحلة", modifier = Modifier.weight(1f))
                Switch(checked = parcels, onCheckedChange = { parcels = it })
            }
            OutlinedTextField(
                value = note, onValueChange = { note = it.take(120) },
                label = { Text("ملاحظة (اختياري)") }, placeholder = { Text("مثلاً: بطلع من عند الجامع") },
                shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()
            )
        }

        BigButton(if (sending) "جاري النشر..." else "🚀 انشر الرحلة", enabled = !sending) {
            val price = if (fixedPrice) priceStr.toDoubleOrNull() else null
            val departAt = departMillis(day.toInt(), hour, minute)
            when {
                !isValidRoute(from, to) -> toast(ctx, "اختار خط صحيح")
                !timeChosen -> toast(ctx, "اختار ساعة الانطلاق")
                departAt < System.currentTimeMillis() -> toast(ctx, "هالوقت فات، اختار وقت جاي")
                fixedPrice && (price == null || price <= 0.0) -> toast(ctx, "اكتب السعر صح")
                else -> {
                    sending = true
                    Repo.publishTrip(
                        Trip(
                            captainName = me.name, captainPhone = me.phone, carText = me.carText,
                            from = from, to = to, road = if (needsRoadChoice(from, to)) road else "",
                            departAt = departAt, seatsTotal = seats.toLong(), price = price,
                            acceptsParcels = parcels, note = note.trim()
                        )
                    ) { ok ->
                        sending = false
                        if (ok) {
                            toast(ctx, "تم نشر الرحلة ✅")
                            timeChosen = false
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

    if (showTime) {
        val st = rememberTimePickerState(initialHour = hour, initialMinute = minute, is24Hour = false)
        AlertDialog(
            onDismissRequest = { showTime = false },
            confirmButton = {
                TextButton(onClick = {
                    hour = st.hour
                    minute = st.minute
                    timeChosen = true
                    showTime = false
                }) { Text("تم") }
            },
            dismissButton = { TextButton(onClick = { showTime = false }) { Text("إلغاء") } },
            text = {
                // The clock face reads better left-to-right
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    TimePicker(state = st)
                }
            }
        )
    }
}

// ===== My trips and their bookings =====

@Composable
private fun MyTrips(me: UserProfile) {
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
        list.isEmpty() -> EmptyState("🚗", "ما عندك رحلات جاية.\nانشر رحلة من تبويب «نشر رحلة».")
        else -> LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(list, key = { it.id }) { t ->
                val reqs = bookings.filter { it.tripId == t.id }
                    .sortedBy { if (it.status == BK_PENDING) 0 else if (it.status == BK_ACCEPTED) 1 else 2 }
                AppCard {
                    TripInfo(t, showCaptain = false)
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
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
private fun BookingRow(b: Booking, onCancel: () -> Unit) {
    val ctx = LocalContext.current
    val active = b.isActive
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = if (active) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${b.passengerName} • ${seatsText(b.seats)}",
                    fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f)
                )
                Text(bookingStatusText(b.status), style = MaterialTheme.typography.labelLarge)
            }
            if (b.dropoff.isNotBlank()) Text("📍 النزول: ${b.dropoff}")
            if (b.reason.isNotBlank() && !active) Text("السبب: ${b.reason}", color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (active) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (b.status == BK_PENDING) {
                        Button(onClick = {
                            Repo.accept(b) { ok -> toast(ctx, if (ok) "تم القبول ✅" else "ما في مقاعد كافية أو الطلب تغيّر") }
                        }) { Text("✅ قبول") }
                        OutlinedButton(onClick = {
                            Repo.reject(b) { ok -> if (!ok) toast(ctx, "ما قدرت أرفض، جرّب كمان مرة") }
                        }) { Text("رفض") }
                    } else {
                        OutlinedButton(onClick = onCancel) { Text("إلغاء الحجز") }
                    }
                    Spacer(Modifier.weight(1f))
                    FilledTonalIconButton(onClick = { callPhone(ctx, b.passengerPhone) }) { Text("📞") }
                    FilledTonalIconButton(onClick = {
                        openWhatsApp(ctx, b.passengerPhone, "مرحبا ${b.passengerName}، بخصوص حجزك برحلة ${b.tripSummary}")
                    }) { Text("💬") }
                }
            }
        }
    }
}
