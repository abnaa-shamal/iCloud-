package jo.shajaraone.admin

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** One question before a risky action. */
data class Ask(val title: String, val body: String, val confirm: String, val action: () -> Unit)

@Composable
fun AskDialog(ask: Ask?, onDismiss: () -> Unit) {
    if (ask == null) return
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Royal.Surface,
        title = { Text(ask.title, color = Royal.GoldLight, fontWeight = FontWeight.ExtraBold) },
        text = { Text(ask.body, color = Royal.Ivory) },
        confirmButton = { GoldButton(ask.confirm, { ask.action(); onDismiss() }) },
        dismissButton = { GhostButton("تراجع", onDismiss, color = Royal.Muted) }
    )
}

private val pad = PaddingValues(horizontal = 16.dp, vertical = 10.dp)

@Composable
private fun ListScreen(content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = pad, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        content()
        item { RoyalFooter() }
    }
}

@Composable
private fun Search(value: String, onChange: (String) -> Unit, hint: String = "ابحث بالاسم أو الرقم") {
    OutlinedTextField(
        value, onChange, Modifier.fillMaxWidth(), singleLine = true, placeholder = { Text(hint, color = Royal.Muted) },
        shape = RoundedCornerShape(14.dp)
    )
}

@Composable
private fun Chips(options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (key, label) ->
            FilterChip(
                selected == key, { onSelect(key) }, label = { Text(label, fontSize = 12.sp) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = Royal.Gold, selectedLabelColor = Color.White
                )
            )
        }
    }
}

@Composable
private fun Empty(text: String) {
    Box(Modifier.fillMaxWidth().padding(vertical = 40.dp), contentAlignment = Alignment.Center) {
        Text(text, color = Royal.Muted)
    }
}

// ---------------------------------------------------------------- login

@Composable
fun LoginScreen(busy: Boolean, error: String?, onLogin: (String, String) -> Unit) {
    var email by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    Column(
        Modifier.fillMaxSize().padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
    ) {
        androidx.compose.foundation.Canvas(Modifier.size(width = 120.dp, height = 90.dp)) {
            drawCrown(0f, 0f, size.width, size.height)
        }
        Spacer(Modifier.height(10.dp))
        Text("المشرف", color = Royal.GoldLight, fontSize = 34.sp, fontWeight = FontWeight.ExtraBold)
        Text("لوحة إدارة أبناء الشمال", color = Royal.Muted)
        Spacer(Modifier.height(26.dp))
        OutlinedTextField(
            email, { email = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("البريد الإلكتروني") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), shape = RoundedCornerShape(14.dp)
        )
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            pass, { pass = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("كلمة السر") },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), shape = RoundedCornerShape(14.dp)
        )
        if (error != null) Text(error, color = Royal.Ruby, modifier = Modifier.padding(top = 10.dp))
        Spacer(Modifier.height(18.dp))
        GoldButton(if (busy) "لحظة…" else "دخول", { onLogin(email, pass) }, Modifier.fillMaxWidth().height(50.dp), enabled = !busy && email.isNotBlank() && pass.isNotBlank())
        RoyalFooter(Modifier.padding(top = 28.dp))
    }
}

// ---------------------------------------------------------------- overview

@Composable
fun OverviewScreen(
    users: List<AUser>, trips: List<ATrip>, bookings: List<ABooking>, loaded: Boolean, error: String?,
    onApprove: (AUser) -> Unit, onVerify: (AUser) -> Unit, onOpenCaptains: () -> Unit, onOpenPlaces: () -> Unit, onSignOut: () -> Unit
) {
    val pending = users.filter { it.isCaptain && it.status == "pending" }
    val unverified = users.filter { !it.verified && it.verifyCode.isNotBlank() }
    ListScreen {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                ScreenTitle("نظرة عامة", "أهلاً بك يا مشرف")
                GhostButton("خروج", onSignOut, color = Royal.Muted)
            }
        }
        if (error != null) item { RoyalCard(accent = Royal.Ruby) { Text("تعذّر التحميل: $error", color = Royal.Ruby) } }
        if (!loaded && error == null) item { Empty("جاري التحميل…") }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatTile("${pending.size}", "كباتن بانتظار الموافقة", Royal.Amber, Modifier.weight(1f))
                    StatTile("${users.count { it.isCaptain && it.status == "approved" }}", "كباتن معتمدون", Royal.Emerald, Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatTile("${users.count { !it.isCaptain }}", "ركاب", Royal.GoldLight, Modifier.weight(1f))
                    StatTile("${unverified.size}", "أرقام بانتظار التوثيق", Royal.Amber, Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatTile("${trips.count { it.status == "open" && it.departAt > System.currentTimeMillis() }}", "رحلات قادمة", Royal.Gold, Modifier.weight(1f))
                    StatTile("${bookings.count { it.status == "pending" || it.status == "accepted" }}", "حجوزات فعّالة", Royal.Emerald, Modifier.weight(1f))
                }
            }
        }
        if (pending.isNotEmpty()) {
            item { Text("طلبات الكباتن", color = Royal.GoldLight, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp) }
            items(pending.take(3), key = { "p" + it.uid }) { u ->
                RoyalCard(accent = Royal.Amber) {
                    Text(u.name, color = Royal.Ivory, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
                    Text("${u.phone}  •  ${u.car}", color = Royal.Muted, fontSize = 13.sp)
                    GoldButton("قبول الكابتن", { onApprove(u) })
                }
            }
            if (pending.size > 3) item { GhostButton("كل الطلبات (${pending.size})", onOpenCaptains, Modifier.fillMaxWidth()) }
        }
        if (unverified.isNotEmpty()) {
            item { Text("أرقام تنتظر التوثيق", color = Royal.GoldLight, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp) }
            items(unverified.take(5), key = { "v" + it.uid }) { u ->
                RoyalCard {
                    Text("${u.name}  •  ${u.phone}", color = Royal.Ivory, fontWeight = FontWeight.Bold)
                    Text("الكود المنتظر على واتساب: ${u.verifyCode}", color = Royal.Amber, fontWeight = FontWeight.ExtraBold)
                    GoldButton("وصل الكود، وثّق", { onVerify(u) })
                }
            }
        }
        item { GhostButton("إدارة الأماكن", onOpenPlaces, Modifier.fillMaxWidth()) }
    }
}

// ---------------------------------------------------------------- captains

@Composable
fun CaptainsScreen(users: List<AUser>, onSet: (AUser, Map<String, Any>, String) -> Unit, ask: (Ask) -> Unit) {
    var tab by remember { mutableStateOf("pending") }
    var q by remember { mutableStateOf("") }
    val captains = users.filter { it.isCaptain }
    val list = captains.filter {
        when (tab) {
            "pending" -> it.status == "pending"
            "approved" -> it.status == "approved"
            "blocked" -> it.status == "suspended" || it.status == "rejected"
            else -> true
        } && (q.isBlank() || it.name.contains(q, true) || it.phone.contains(q))
    }.sortedByDescending { it.createdAt }
    ListScreen {
        item { ScreenTitle("الكباتن", "${captains.size} كابتن") }
        item { Search(q, { q = it }) }
        item {
            Chips(listOf("pending" to "بانتظار الموافقة", "approved" to "معتمد", "blocked" to "موقوف/مرفوض", "all" to "الكل"), tab) { tab = it }
        }
        if (list.isEmpty()) item { Empty("لا يوجد كباتن هنا") }
        items(list, key = { it.uid }) { u ->
            RoyalCard(accent = if (u.status == "pending") Royal.Amber else Royal.GoldDeep) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(u.name, color = Royal.Ivory, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                    Badge(userStatusText(u), when (u.status) { "approved" -> Royal.Emerald; "pending" -> Royal.Amber; else -> Royal.Ruby })
                }
                Text(u.phone, color = Royal.GoldLight, fontWeight = FontWeight.Bold)
                if (u.car.isNotBlank()) Text(u.car, color = Royal.Muted, fontSize = 13.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Badge(if (u.verified) "رقم موثّق" else "غير موثّق", if (u.verified) Royal.Emerald else Royal.Muted)
                    Badge("${u.maxSeats} مقاعد", Royal.Gold)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (u.status == "pending") {
                        GoldButton("قبول", { onSet(u, mapOf("status" to "approved"), "تم قبول ${u.name}") })
                        GhostButton("رفض", {
                            ask(Ask("رفض الكابتن", "رفض ${u.name}؟", "رفض") { onSet(u, mapOf("status" to "rejected"), "تم الرفض") })
                        }, color = Royal.Ruby)
                    }
                    if (u.status == "approved") {
                        GhostButton("إيقاف", {
                            ask(Ask("إيقاف الكابتن", "إيقاف ${u.name} عن العمل؟", "إيقاف") { onSet(u, mapOf("status" to "suspended"), "تم الإيقاف") })
                        }, color = Royal.Ruby)
                    }
                    if (u.status == "suspended" || u.status == "rejected") {
                        GoldButton("إعادة تفعيل", { onSet(u, mapOf("status" to "approved"), "تمت إعادة التفعيل") })
                    }
                    GhostButton(if (u.verified) "إلغاء التوثيق" else "توثيق الرقم", {
                        onSet(u, mapOf("verified" to !u.verified), if (u.verified) "أُلغي التوثيق" else "تم توثيق الرقم")
                    })
                }
                if (u.status == "approved") {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("أقصى عدد مقاعد:", color = Royal.Muted, fontSize = 13.sp)
                        GhostButton("−", { if (u.maxSeats > 4) onSet(u, mapOf("maxSeats" to u.maxSeats - 1), "تم") })
                        Text("${u.maxSeats}", color = Royal.GoldLight, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                        GhostButton("+", { if (u.maxSeats < 7) onSet(u, mapOf("maxSeats" to u.maxSeats + 1), "تم") })
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- riders

@Composable
fun RidersScreen(users: List<AUser>, onSet: (AUser, Map<String, Any>, String) -> Unit, ask: (Ask) -> Unit) {
    var tab by remember { mutableStateOf("all") }
    var q by remember { mutableStateOf("") }
    val riders = users.filter { !it.isCaptain }
    val list = riders.filter {
        when (tab) {
            "unverified" -> !it.verified
            "blocked" -> it.riderBlocked
            else -> true
        } && (q.isBlank() || it.name.contains(q, true) || it.phone.contains(q))
    }.sortedByDescending { it.createdAt }
    ListScreen {
        item { ScreenTitle("الركاب", "${riders.size} راكب") }
        item { Search(q, { q = it }) }
        item { Chips(listOf("all" to "الكل", "unverified" to "غير موثّق", "blocked" to "موقوف"), tab) { tab = it } }
        if (list.isEmpty()) item { Empty("لا يوجد ركاب هنا") }
        items(list, key = { it.uid }) { u ->
            RoyalCard {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(u.name, color = Royal.Ivory, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Badge(if (u.verified) "موثّق" else "غير موثّق", if (u.verified) Royal.Emerald else Royal.Muted)
                        if (u.riderBlocked) Badge("موقوف", Royal.Ruby)
                    }
                }
                Text(u.phone, color = Royal.GoldLight, fontWeight = FontWeight.Bold)
                if (!u.verified && u.verifyCode.isNotBlank()) Text("الكود المنتظر: ${u.verifyCode}", color = Royal.Amber, fontSize = 13.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GhostButton(if (u.verified) "إلغاء التوثيق" else "توثيق الرقم", {
                        onSet(u, mapOf("verified" to !u.verified), if (u.verified) "أُلغي التوثيق" else "تم توثيق الرقم")
                    })
                    if (u.riderBlocked) {
                        GoldButton("إعادة تفعيل", {
                            val m = mutableMapOf<String, Any>("riderSuspended" to false)
                            if (u.status == "suspended") m["status"] = "active"
                            onSet(u, m, "تمت إعادة التفعيل")
                        })
                    } else {
                        GhostButton("إيقاف", {
                            ask(Ask("إيقاف الراكب", "منع ${u.name} من الحجز؟", "إيقاف") { onSet(u, mapOf("riderSuspended" to true), "تم الإيقاف") })
                        }, color = Royal.Ruby)
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- trips

@Composable
fun TripsScreen(trips: List<ATrip>, onCancel: (ATrip) -> Unit, ask: (Ask) -> Unit) {
    var tab by remember { mutableStateOf("open") }
    val list = trips.filter { tab == "all" || it.status == tab || (tab == "open" && it.status == "full") }
    ListScreen {
        item { ScreenTitle("الرحلات", "آخر ${trips.size} رحلة") }
        item { Chips(listOf("open" to "قادمة", "cancelled" to "ملغاة", "all" to "الكل"), tab) { tab = it } }
        if (list.isEmpty()) item { Empty("لا توجد رحلات هنا") }
        items(list, key = { it.id }) { t ->
            RoyalCard {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("${t.from} ← ${t.to}", color = Royal.Ivory, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
                    Badge(tripStatusText(t.status), when (t.status) { "open" -> Royal.Emerald; "full" -> Royal.Amber; else -> Royal.Ruby })
                }
                Text("${fmtTime(t.departAt)}  •  ${t.captainName}", color = Royal.GoldLight)
                Text(
                    "${t.carText}  •  مقاعد ${t.seatsFree}/${t.seatsTotal}  •  " + (t.price?.let { "$it دينار" } ?: "حسب الاتفاق"),
                    color = Royal.Muted, fontSize = 13.sp
                )
                if (t.status == "open" || t.status == "full") {
                    GhostButton("إلغاء الرحلة", {
                        ask(Ask("إلغاء الرحلة", "إلغاء رحلة ${t.from} ← ${t.to}؟", "إلغاء") { onCancel(t) })
                    }, color = Royal.Ruby)
                }
            }
        }
    }
}

// ---------------------------------------------------------------- bookings

@Composable
fun BookingsScreen(bookings: List<ABooking>, onCancel: (ABooking) -> Unit, ask: (Ask) -> Unit) {
    var tab by remember { mutableStateOf("active") }
    val list = bookings.filter {
        when (tab) {
            "active" -> it.status == "pending" || it.status == "accepted"
            else -> true
        }
    }
    ListScreen {
        item { ScreenTitle("الحجوزات", "آخر ${bookings.size} حجز") }
        item { Chips(listOf("active" to "فعّالة", "all" to "الكل"), tab) { tab = it } }
        if (list.isEmpty()) item { Empty("لا توجد حجوزات هنا") }
        items(list, key = { it.id }) { b ->
            RoyalCard {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(b.passengerName, color = Royal.Ivory, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
                    Badge(bookingStatusText(b.status), when (b.status) { "accepted" -> Royal.Emerald; "pending" -> Royal.Amber; else -> Royal.Ruby })
                }
                Text("${b.passengerPhone}  •  ${b.seats} مقعد", color = Royal.GoldLight)
                Text("مع الكابتن ${b.captainName}", color = Royal.Muted, fontSize = 13.sp)
                Text(b.tripSummary, color = Royal.Muted, fontSize = 13.sp)
                if (b.dropoff.isNotBlank()) Text("النزول: ${b.dropoff}", color = Royal.Muted, fontSize = 13.sp)
                if (b.status == "pending") {
                    GhostButton("إلغاء الحجز", {
                        ask(Ask("إلغاء الحجز", "إلغاء حجز ${b.passengerName}؟", "إلغاء") { onCancel(b) })
                    }, color = Royal.Ruby)
                }
            }
        }
    }
}

// ---------------------------------------------------------------- places

@Composable
fun PlacesScreen(places: Map<String, List<String>>, onSave: (Map<String, List<String>>) -> Unit, onBack: () -> Unit) {
    // key -> names joined by «،» ; editing happens on plain text
    val texts = remember(places) { mutableStateMapOf<String, String>().apply { places.forEach { (k, v) -> put(k, v.joinToString("، ")) } } }
    ListScreen {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                ScreenTitle("الأماكن", "الأسماء بتظهر بالتطبيقات مباشرة")
                GhostButton("رجوع", onBack, color = Royal.Muted)
            }
        }
        if (places.isEmpty()) item { Empty("لا توجد قوائم أماكن محفوظة بعد") }
        items(texts.keys.sorted(), key = { it }) { k ->
            RoyalCard {
                Text(k.replace("|", "  ←  "), color = Royal.GoldLight, fontWeight = FontWeight.ExtraBold)
                OutlinedTextField(
                    texts[k] ?: "", { texts[k] = it }, Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                    supportingText = { Text("افصل بين الأسماء بفاصلة «،»", color = Royal.Muted) }
                )
            }
        }
        if (places.isNotEmpty()) item {
            GoldButton("حفظ الأماكن", {
                onSave(texts.mapValues { (_, v) -> v.split("،", ",").map { it.trim() }.filter { it.isNotEmpty() } })
            }, Modifier.fillMaxWidth().height(50.dp))
        }
    }
}
