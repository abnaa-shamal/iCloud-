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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.firestore.ListenerRegistration

/** One GPS fix (best effort: last known first, then a single fresh update). */
@SuppressLint("MissingPermission")
private fun oneFix(ctx: Context, onFix: (Location?) -> Unit) {
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

/** Rider side: one tap sends the rider's exact place to the captain who accepted his booking. */
@Composable
fun ShareMyLocationButton(b: Booking) {
    val ctx = LocalContext.current
    var state by remember { mutableIntStateOf(0) }   // 0 idle, 1 sending, 2 sent
    fun send() {
        state = 1
        oneFix(ctx) { l ->
            if (l == null) { state = 0; toast(ctx, "ما قدرت أحدد موقعك، فعّل الـ GPS وجرّب كمان مرة"); return@oneFix }
            Repo.pushRiderLoc(b, l.latitude, l.longitude, l.accuracy.toDouble()) { ok ->
                state = if (ok) 2 else 0
                toast(ctx, if (ok) "وصل موقعك للكابتن ✅" else "ما قدرت أرسل، جرّب كمان مرة")
            }
        }
    }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        if (r.values.any { it }) send() else toast(ctx, "لازم توافق على الموقع عشان يوصلك الكابتن")
    }
    GhostBtn(
        when (state) { 1 -> "جاري تحديد موقعك..."; 2 -> "تم إرسال موقعك • حدّثه"; else -> "شارك موقعي مع الكابتن" },
        Modifier.fillMaxWidth(), Amber
    ) {
        if (state == 1) return@GhostBtn
        if (hasLocationPermission(ctx)) send() else ask.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
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
    if (l == null) {
        Text("العميل لسا ما شارك موقعه", fontSize = 11.sp, color = CapMute, modifier = Modifier.padding(start = 46.dp))
    } else {
        CapBtn("اتجاهات للعميل", Modifier.fillMaxWidth(), height = 40.dp) {
            val nav = Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=${l.first},${l.second}")).setPackage("com.google.android.apps.maps")
            val web = Intent(Intent.ACTION_VIEW, Uri.parse("geo:${l.first},${l.second}?q=${l.first},${l.second}"))
            runCatching { ctx.startActivity(nav.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                .onFailure { runCatching { ctx.startActivity(web.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.onFailure { toast(ctx, "ما في تطبيق خرائط") } }
        }
    }
}
