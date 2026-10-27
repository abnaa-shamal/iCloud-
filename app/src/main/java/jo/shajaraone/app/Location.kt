package jo.shajaraone.app

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Place
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import java.util.Locale

/** Optional location helpers. Nothing is stored on a server; the location is only sent by the user, through WhatsApp. */

fun hasLocationPermission(ctx: Context) =
    ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

/** One-time location from GPS (or network). Returns null after about 12 seconds if there is no fix. */
@SuppressLint("MissingPermission")
fun fetchLocation(ctx: Context, onResult: (Location?) -> Unit) {
    val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER).filter {
        try { lm.isProviderEnabled(it) } catch (e: Exception) { false }
    }
    if (providers.isEmpty()) {
        onResult(null)
        return
    }
    var done = false
    val finish = { l: Location? ->
        if (!done) {
            done = true
            onResult(l)
        }
    }
    Handler(Looper.getMainLooper()).postDelayed({
        finish(providers.firstNotNullOfOrNull {
            try { lm.getLastKnownLocation(it) } catch (e: SecurityException) { null }
        })
    }, 12_000)
    providers.forEach { p ->
        try {
            if (Build.VERSION.SDK_INT >= 30) {
                lm.getCurrentLocation(p, null, ctx.mainExecutor) { l -> if (l != null) finish(l) }
            } else {
                @Suppress("DEPRECATION")
                lm.requestSingleUpdate(p, object : LocationListener {
                    override fun onLocationChanged(location: Location) = finish(location)
                    override fun onProviderEnabled(provider: String) {}
                    override fun onProviderDisabled(provider: String) {}
                    @Deprecated("Deprecated in Java")
                    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
                }, Looper.getMainLooper())
            }
        } catch (e: SecurityException) {
            finish(null)
        }
    }
}

fun mapsLink(l: Location) =
    "https://maps.google.com/?q=" + String.format(Locale.US, "%.6f,%.6f", l.latitude, l.longitude)

/** Small optional button: gets your GPS location once and sends the map link to the other person on WhatsApp. */
@Composable
fun ShareLocationButton(phone: String) {
    val ctx = LocalContext.current
    var busy by remember { mutableStateOf(false) }
    fun go() {
        if (busy) return
        busy = true
        toast(ctx, "جاري تحديد موقعك...")
        fetchLocation(ctx) { l ->
            busy = false
            if (l == null) toast(ctx, "ما قدرت أحدد موقعك. شغّل الـGPS وجرّب كمان مرة")
            else openWhatsApp(ctx, phone, "موقعي الحالي: ${mapsLink(l)}")
        }
    }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        if (r.values.any { it }) go() else toast(ctx, "لازم توافق على الموقع عشان تشاركه")
    }
    RoundAction(Icons.Filled.LocationOn, "شارك موقعي") {
        if (hasLocationPermission(ctx)) go()
        else ask.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
    }
}

/** Small optional button: opens a place in the maps app already on the phone. */
@Composable
fun OpenMapButton(place: String) {
    val ctx = LocalContext.current
    RoundAction(Icons.Filled.Place, "افتح الخريطة") {
        val q = Uri.encode("$place، الأردن")
        try {
            ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=$q")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            try {
                ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://maps.google.com/?q=$q")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (e2: Exception) {
                toast(ctx, "ما لقيت تطبيق خرائط")
            }
        }
    }
}
