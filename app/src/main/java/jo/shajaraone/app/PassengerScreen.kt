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
    val flow = rememberRiderFlow()

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
                0 -> TripsTab(me, myBookings, flow)
                1 -> MyBookings(myBookings)
                else -> SoonScreen()
            }
        }
        Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth(), contentAlignment = Alignment.Center) {
            VantaNav(listOf(1 to "الرحلات", 2 to (if (upcoming > 0) "حجوزاتي ($upcoming)" else "حجوزاتي"), 7 to "اشتراكات"), tab, { tab = it })
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
private fun TripsTab(me: UserProfile, myBookings: List<Booking>, flow: RiderFlowState) {
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
        t.captainUid.isNotBlank() && t.captainUid != me.uid && t.status != TRIP_CANCELLED && t.departAt > now && flowMatches(t, flow)
    }

    RiderFlow(me.name.trim().substringBefore(" "), flow, shown, failed) { t ->
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

    ModalBottomSheet(onDismissRequest = onClose, sheetState = sheet, containerColor = Color(0xFF0B0B0D)) {
        BookingBody(
            t, total, free, picked,
            onToggle = { i ->
                val taken = total - free
                if (i >= taken) picked = if (i in picked) { if (picked.size > 1) picked - i else picked } else picked + i
            },
            areas = areas, point = point, onPoint = { point = it }, other = other, onOther = { other = it.take(60) },
            sending = sending
        ) {
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
