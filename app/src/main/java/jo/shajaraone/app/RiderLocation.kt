package jo.shajaraone.app

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.Looper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Phone
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.firestore.ListenerRegistration

/** One GPS fix (best effort: last known first, then a single fresh update). */
@SuppressLint("MissingPermission")
internal fun oneFix(ctx: Context, onFix: (Location?) -> Unit) {
    val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER).filter { runCatching { lm.isProviderEnabled(it) }.getOrDefault(false) }
    if (providers.isEmpty()) return onFix(null)
    var done = false
    val listeners = mutableListOf<LocationListener>()
    fun finish(l: Location?) {
        if (done) return
        done = true
        listeners.forEach { runCatching { lm.removeUpdates(it) } }
        onFix(l)
    }
    providers.forEach { p ->
        val l = LocationListener { finish(it) }
        listeners += l
        runCatching { lm.requestLocationUpdates(p, 0L, 0f, l, Looper.getMainLooper()) }
    }
    android.os.Handler(Looper.getMainLooper()).postDelayed({
        if (!done) finish(providers.mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }.maxByOrNull { it.time })
    }, 12_000L)
}

/** Rider side: as soon as a captain confirms a booking, the rider's place is sent to that captain automatically (permission asked once). */
@Composable
fun AutoShareLocation(bookings: List<Booking>) {
    val ctx = LocalContext.current
    val sent = remember { mutableStateListOf<String>() }
    val todo = bookings.filter { it.status == BK_ACCEPTED && it.departAt > System.currentTimeMillis() && it.id !in sent && (!it.dropoff.startsWith("لـ:") || it.dropoff.contains("📍")) }
    fun send(list: List<Booking>) {
        if (list.isEmpty()) return
        sent += list.map { it.id }
        oneFix(ctx) { l ->
            if (l == null) { sent.removeAll(list.map { it.id }.toSet()); return@oneFix }
            list.forEach { Repo.pushRiderLoc(it, l.latitude, l.longitude, l.accuracy.toDouble()) { } }
        }
    }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        if (r.values.any { it }) send(todo)
    }
    var asked by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(todo.map { it.id }) {
        if (todo.isEmpty()) return@LaunchedEffect
        if (hasLocationPermission(ctx)) send(todo)
        else if (!asked) { asked = true; ask.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)) }
    }
}

/** Rider side: the full captain card of a confirmed booking: name, phone, car. */
@Composable
fun CaptainInfoCard(b: Booking) {
    var car by remember(b.tripId) { mutableStateOf("") }
    LaunchedEffect(b.tripId) { Repo.tripCar(b.tripId) { car = it } }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color(0xFF2B2B2E)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("الكابتن ${b.captainName}", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Text("📞 ${b.captainPhone}", fontSize = 13.sp, color = Amber)
        if (car.isNotBlank()) Text("🚗 $car", fontSize = 13.sp, color = Dim)
    }
}

private fun appIcon(ctx: Context, vararg pkgs: String): androidx.compose.ui.graphics.ImageBitmap? {
    for (p in pkgs) {
        val d = runCatching { ctx.packageManager.getApplicationIcon(p) }.getOrNull() ?: continue
        return runCatching {
            val bmp = android.graphics.Bitmap.createBitmap(96, 96, android.graphics.Bitmap.Config.ARGB_8888)
            val c = android.graphics.Canvas(bmp); d.setBounds(0, 0, 96, 96); d.draw(c)
            bmp.asImageBitmap()
        }.getOrNull()
    }
    return null
}

@Composable
private fun RoundAction(label: String, onClick: () -> Unit, enabled: Boolean = true, content: @Composable () -> Unit) {
    Column(Modifier.alpha(if (enabled) 1f else 0.4f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(
            Modifier.size(48.dp).clip(CircleShape).background(CapInk.copy(alpha = 0.08f)).clickable(enabled = enabled, onClick = onClick),
            contentAlignment = Alignment.Center
        ) { content() }
        Text(label, fontSize = 11.sp, color = CapMute)
    }
}

/** Captain side: one clear card per confirmed rider: name, phone, call / WhatsApp / directions (with the real app icons). */
@Composable
fun RiderCard(b: Booking, canCancel: Boolean, onCancel: () -> Unit) {
    val ctx = LocalContext.current
    var loc by remember(b.id) { mutableStateOf<Pair<Double, Double>?>(null) }
    DisposableEffect(b.id) {
        val r: ListenerRegistration = Repo.listenRiderLoc(b.id) { loc = it }
        onDispose { r.remove() }
    }
    val wa = remember { appIcon(ctx, "com.whatsapp", "com.whatsapp.w4b") }
    val maps = remember { appIcon(ctx, "com.google.android.apps.maps") }
    val l = loc
    val forOther = b.dropoff.startsWith("لـ:")
    val otherPhone = if (forOther) b.dropoff.removePrefix("لـ:").takeWhile { it.isDigit() } else ""
    val phone = if (forOther && otherPhone.isNotBlank()) otherPhone else b.passengerPhone
    val dropShown = if (forOther) b.dropoff.substringAfter(" • ", "") else b.dropoff
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(CapInk.copy(alpha = 0.05f)).padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CapAvatar(b.passengerName, 44.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(if (forOther) "حجز لشخص آخر" else b.passengerName, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = CapInk)
                Text(phone, fontSize = 13.sp, color = CapMute)
                if (forOther) Text("حجزه: ${b.passengerName}", fontSize = 11.sp, color = CapMute)
                Text(seatsText(b.seats) + if (dropShown.isNotBlank()) " • $dropShown" else "", fontSize = 12.sp, color = CapMute)
            }
            if (canCancel) CapBtn("إلغاء", filled = false, color = CapRed, height = 34.dp) { onCancel() }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            RoundAction("اتصال", { callPhone(ctx, phone) }) {
                Icon(Icons.Filled.Phone, null, tint = CapAccent, modifier = Modifier.size(24.dp))
            }
            RoundAction("واتساب", { openWhatsApp(ctx, phone, "مرحبا، أنا الكابتن بخصوص حجزك") }) {
                if (wa != null) Image(wa, null, Modifier.size(30.dp)) else Text("💬", fontSize = 22.sp)
            }
            RoundAction(if (l != null) "اتجاهات" else "الموقع لم يصل", {
                val ll = l ?: return@RoundAction
                val nav = Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=${ll.first},${ll.second}")).setPackage("com.google.android.apps.maps")
                val web = Intent(Intent.ACTION_VIEW, Uri.parse("geo:${ll.first},${ll.second}?q=${ll.first},${ll.second}"))
                runCatching { ctx.startActivity(nav.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    .onFailure { runCatching { ctx.startActivity(web.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.onFailure { toast(ctx, "ما في تطبيق خرائط") } }
            }, enabled = l != null) {
                if (maps != null) Image(maps, null, Modifier.size(30.dp)) else Text("📍", fontSize = 22.sp)
            }
        }
    }
}
