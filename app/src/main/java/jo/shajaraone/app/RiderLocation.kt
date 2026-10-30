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
    val todo = bookings.filter { it.status == BK_ACCEPTED && it.departAt > System.currentTimeMillis() && it.id !in sent }
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

/** Captain side: opens the map app with directions to the rider once he has shared his place. */
@Composable
fun RiderDirectionsButton(b: Booking) {
    val ctx = LocalContext.current
    var loc by remember(b.id) { mutableStateOf<Pair<Double, Double>?>(null) }
    DisposableEffect(b.id) {
        val r: ListenerRegistration = Repo.listenRiderLoc(b.id) { loc = it }
        onDispose { r.remove() }
    }
    val l = loc
    Text("📞 ${b.passengerPhone}", fontSize = 12.sp, color = CapMute, modifier = Modifier.padding(start = 46.dp))
    if (l == null) {
        Text("موقع العميل لسا ما وصل", fontSize = 11.sp, color = CapMute, modifier = Modifier.padding(start = 46.dp))
    } else {
        CapBtn("اتجاهات للعميل", Modifier.fillMaxWidth(), height = 40.dp) {
            val nav = Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=${l.first},${l.second}")).setPackage("com.google.android.apps.maps")
            val web = Intent(Intent.ACTION_VIEW, Uri.parse("geo:${l.first},${l.second}?q=${l.first},${l.second}"))
            runCatching { ctx.startActivity(nav.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                .onFailure { runCatching { ctx.startActivity(web.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.onFailure { toast(ctx, "ما في تطبيق خرائط") } }
        }
    }
}
