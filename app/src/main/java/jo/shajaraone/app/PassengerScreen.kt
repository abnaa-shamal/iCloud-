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

private const val ALL = "all"

@Composable
fun PassengerHome(me: UserProfile) {
    val uid = Repo.auth.currentUser?.uid ?: ""
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var myBookings by remember { mutableStateOf<List<Booking>>(emptyList()) }
    // Search choices shared by the home card and the trips list. Everything is optional.
    var dest by rememberSaveable { mutableIntStateOf(0) }
    var day by rememberSaveable { mutableIntStateOf(-1) }
    var slot by rememberSaveable { mutableIntStateOf(0) }
    var north by rememberSaveable { mutableStateOf("") }

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
                0 -> TripsTab(me, myBookings, true, dest, { dest = it }, day, { day = it }, slot, { slot = it }, north, { north = it }, { tab = 1 }, { tab = 2 })
                1 -> TripsTab(me, myBookings, false, dest, { dest = it }, day, { day = it }, slot, { slot = it }, north, { north = it }, {}, { tab = 2 })
                else -> MyBookings(myBookings)
            }
        }
        Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth(), contentAlignment = Alignment.Center) {
            VantaNav(listOf(0 to "الرئيسية", 1 to "الرحلات", 2 to (if (upcoming > 0) "حجوزاتي ($upcoming)" else "حجوزاتي")), tab, { tab = it })
        }
    }
}

// ===== Browse trips =====

val destChoices = listOf("الكل", "عمّان", "الزرقاء", "إربد", "راجع لعندنا")
val slotChoices = listOf("أي وقت", "صباحاً", "ظهراً", "مساءً")

/** All filters are optional: 0 / -1 / "" means «any». */
fun vantaMatches(t: Trip, dest: Int, day: Int, slot: Int, north: String): Boolean {
    val kind = kindOf(t)
    val zarqa = t.road == "طريق الزرقاء"
    val destOk = when (dest) {
        1 -> kind == KIND_NS && !zarqa && t.road != NORTH_INNER
        2 -> kind == KIND_NS && zarqa
        3 -> kind == KIND_NN || t.to == "إربد"
        4 -> kind == KIND_SN
        else -> true
    }
    val hour = java.util.Calendar.getInstance().apply { timeInMillis = t.departAt }.get(java.util.Calendar.HOUR_OF_DAY)
    val slotOk = when (slot) { 1 -> hour < 12; 2 -> hour in 12..16; 3 -> hour >= 17; else -> true }
    return destOk && slotOk &&
        (day < 0 || dayOffsetOf(t.departAt) == day) &&
        (north.isBlank() || t.from == north || t.to == north)
}

@Composable
private fun WhiteChip(text: String, on: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(14.dp)).background(if (on) Color.Black else Color(0xFFF0F0F0)).clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center
    ) { Text(text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = if (on) Color.White else Color.Black) }
}

/** Home screen: white search card (everything optional) and the nearest rides. */
@Composable
fun RiderHomeView(
    name: String, dest: Int, onDest: (Int) -> Unit, day: Int, onDay: (Int) -> Unit, slot: Int, onSlot: (Int) -> Unit,
    north: String, onNorth: (String) -> Unit, nearest: List<Trip>, onSearch: () -> Unit, onOpen: () -> Unit
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(top = 40.dp, bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("أبناء الشمال", fontSize = 32.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
        Column {
            Text("أهلاً $name 👋", fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
            Text("سافر مع أهل بلدك، أريح وأوفر.", fontSize = 13.sp, color = Dim)
        }
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(Color.White).padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("وين رايح؟", fontSize = 12.sp, color = Color(0xFF777777))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                destChoices.forEachIndexed { i, n -> WhiteChip(n, dest == i) { onDest(i) } }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFFE6E6E6)))
            Text("متى؟ (اختياري)", fontSize = 12.sp, color = Color(0xFF777777))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("اليوم", "غداً", "بعد غد").forEachIndexed { i, n ->
                    WhiteChip(n, day == i) { onDay(if (day == i) -1 else i) }
                }
            }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                slotChoices.forEachIndexed { i, n -> WhiteChip(n, slot == i) { onSlot(i) } }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFFE6E6E6)))
            Text("بلدك (اختياري)", fontSize = 12.sp, color = Color(0xFF777777))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                WhiteChip("أي بلد", north.isBlank()) { onNorth("") }
                northPlaces.forEach { n -> WhiteChip(n, north == n) { onNorth(n) } }
            }
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(30.dp)).background(Color.Black).clickable { onSearch() }.padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
                Text("ابحث عن رحلات", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        if (nearest.isNotEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("أقرب الرحلات", Modifier.weight(1f), fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                Text("عرض الكل", fontSize = 13.sp, color = Amber, modifier = Modifier.clickable { onOpen() })
            }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                nearest.forEachIndexed { i, t -> TripCard(t, Modifier.width(300.dp).clickable { onOpen() }, i, nearest = i == 0) }
            }
        }
    }
}

/** Trips list: day pills (tap again to clear), destination chips, then one glass card per ride. */
@Composable
fun RiderTripsView(
    shown: List<Trip>?, failed: Boolean, dest: Int, onDest: (Int) -> Unit, day: Int, onDay: (Int) -> Unit,
    slot: Int, onSlot: (Int) -> Unit, actions: @Composable ColumnScope.(Trip) -> Unit
) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 110.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Column(
                Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Color(0xFF2A2A2D), Color(0xFF141415)))).padding(horizontal = 18.dp).padding(top = 38.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("الرحلات المتاحة", fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                Text(if (day < 0) "كل الأيام" else dayName(dayStart(day)), fontSize = 15.sp, color = Dim)
                DayPills(pillDays(5), day) { onDay(if (day == it) -1 else it) }
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    destChoices.forEachIndexed { i, n ->
                        Box(
                            Modifier.clip(RoundedCornerShape(18.dp)).background(if (dest == i) Amber else Color(0xFF2B2B2E)).clickable { onDest(i) }.padding(horizontal = 14.dp, vertical = 8.dp)
                        ) { Text(n, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = if (dest == i) Color.Black else Color.White) }
                    }
                }
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    slotChoices.forEachIndexed { i, n ->
                        Box(
                            Modifier.clip(RoundedCornerShape(18.dp)).border(1.dp, if (slot == i) Amber else Color(0x33FFFFFF), RoundedCornerShape(18.dp)).clickable { onSlot(i) }.padding(horizontal = 14.dp, vertical = 6.dp)
                        ) { Text(n, fontSize = 12.sp, color = if (slot == i) Amber else Dim) }
                    }
                }
            }
        }
        item {
            Text(
                if (shown == null) "" else "${shown.size} رحلة متاحة",
                Modifier.padding(horizontal = 18.dp), fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Color.White
            )
        }
        when {
            shown == null -> item { LoadingBox() }
            failed && shown.isEmpty() -> item { EmptyState("📶", "ما قدرت أجيب الرحلات، تأكد من الإنترنت") }
            shown.isEmpty() -> item { EmptyState("🚌", "ما في رحلات بهالاختيار هلأ.\nجرّب يوم ثاني أو اختر «الكل».") }
            else -> items(shown.size) { i ->
                val t = shown[i]
                TripCard(t, Modifier.padding(horizontal = 18.dp), index = i, nearest = i == 0, actions = { actions(t) })
            }
        }
    }
}

@Composable
private fun TripsTab(
    me: UserProfile, myBookings: List<Booking>, home: Boolean,
    dest: Int, onDest: (Int) -> Unit, day: Int, onDay: (Int) -> Unit, slot: Int, onSlot: (Int) -> Unit,
    north: String, onNorth: (String) -> Unit, onSearch: () -> Unit, onBooked: () -> Unit
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
    val base = trips?.filter { t -> t.captainUid.isNotBlank() && t.captainUid != me.uid && t.status != TRIP_CANCELLED && t.departAt > now }
    val shown = base?.filter { vantaMatches(it, dest, day, slot, north) }

    if (home) {
        RiderHomeView(
            me.name.trim().substringBefore(" "), dest, onDest, day, onDay, slot, onSlot, north, onNorth,
            nearest = (shown ?: emptyList()).take(3), onSearch = onSearch, onOpen = onSearch
        )
    } else {
        RiderTripsView(shown, failed, dest, onDest, day, onDay, slot, onSlot) { t ->
        val mine = myBookings.firstOrNull { it.tripId == t.id && it.isActive }
        if (mine?.status == BK_ACCEPTED) {
            Button(
                onClick = { tracking = t },
                shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth().height(48.dp)
            ) { Text("تتبّع الكابتن على الخريطة") }
        }
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
