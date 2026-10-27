package jo.shajaraone.app

import android.content.Intent
import android.location.Location
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import java.io.File

/** Passenger side: the captain's live position on an OpenStreetMap map (free, no key), plus distance and accuracy. */
@Composable
fun LiveTrackDialog(tripId: String, captainName: String, onClose: () -> Unit) {
    val ctx = LocalContext.current
    var live by remember { mutableStateOf<LiveLoc?>(null) }
    var failed by remember { mutableStateOf(false) }
    var me by remember { mutableStateOf<Location?>(null) }
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }

    DisposableEffect(tripId) {
        val r = Repo.listenLive(tripId, onChange = { live = it; failed = false }, onError = { failed = true })
        onDispose { r.remove() }
    }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            nowMs = System.currentTimeMillis()
        }
    }
    LaunchedEffect(Unit) {
        if (hasLocationPermission(ctx)) fetchLocation(ctx) { me = it }
    }

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().systemBarsPadding()) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("موقع الكابتن $captainName", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    TextButton(onClick = onClose) { Text("إغلاق") }
                }
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    LiveMap(live, me)
                    if (live == null) {
                        Surface(
                            modifier = Modifier.align(Alignment.Center).padding(24.dp),
                            shape = MaterialTheme.shapes.large,
                            color = MaterialTheme.colorScheme.surfaceContainerHigh
                        ) {
                            Text(
                                if (failed) "ما قدرت أجيب موقع الكابتن. تأكد من الإنترنت ومن إنو حجزك مقبول."
                                else "الكابتن لسا ما بدأ مشاركة موقعه.\nأول ما يبدأ بيظهر هون لحاله.",
                                modifier = Modifier.padding(20.dp)
                            )
                        }
                    }
                }
                LiveInfo(live, me, nowMs)
            }
        }
    }
}

@Composable
private fun LiveInfo(live: LiveLoc?, me: Location?, nowMs: Long) {
    val ctx = LocalContext.current
    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (live != null) {
            val age = ((nowMs - live.updatedAt) / 1000).coerceAtLeast(0)
            val stale = age > 60
            Text(
                if (stale) "آخر تحديث قبل ${age / 60} دقيقة، ممكن الاتصال ضعيف" else "آخر تحديث قبل $age ثانية",
                color = if (stale) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleSmall
            )
            val parts = mutableListOf<String>()
            if (me != null) {
                val d = Location("x").apply { latitude = live.lat; longitude = live.lng }.distanceTo(me)
                parts += if (d >= 1000) "يبعد عنك %.1f كم".format(java.util.Locale.US, d / 1000) else "يبعد عنك ${d.toInt()} متر"
            }
            if (live.speed > 1.0) parts += "السرعة ${(live.speed * 3.6).toInt()} كم/س"
            parts += "دقة ±${live.accuracy.toInt()} متر"
            Text(parts.joinToString("  •  "), color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedButton(
                onClick = {
                    val uri = Uri.parse("geo:${live.lat},${live.lng}?q=${live.lat},${live.lng}(${Uri.encode("الكابتن")})")
                    try {
                        ctx.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    } catch (e: Exception) {
                        toast(ctx, "ما لقيت تطبيق خرائط")
                    }
                },
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) { Text("افتح بتطبيق الخرائط") }
        }
    }
}

/** OpenStreetMap view: the captain's marker (the map follows it) and, when known, yours. */
@Composable
private fun LiveMap(cap: LiveLoc?, me: Location?) {
    var centered by remember { mutableStateOf(false) }
    var mapRef by remember { mutableStateOf<MapView?>(null) }
    DisposableEffect(Unit) { onDispose { mapRef?.onDetach() } }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { c ->
            Configuration.getInstance().apply {
                userAgentValue = c.packageName
                osmdroidBasePath = File(c.cacheDir, "osmdroid")
                osmdroidTileCache = File(c.cacheDir, "osmdroid/tiles")
            }
            MapView(c).apply {
                setTileSource(TileSourceFactory.MAPNIK)
                setMultiTouchControls(true)
                zoomController.setVisibility(CustomZoomButtonsController.Visibility.SHOW_AND_FADEOUT)
                controller.setZoom(16.0)
                controller.setCenter(GeoPoint(32.55, 35.85)) // northern Jordan until the first fix arrives
                onResume()
                mapRef = this
            }
        },
        update = { map ->
            map.overlays.clear()
            if (cap != null) {
                val p = GeoPoint(cap.lat, cap.lng)
                map.overlays.add(Marker(map).apply {
                    position = p
                    title = "الكابتن"
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                })
                if (!centered) {
                    map.controller.setCenter(p)
                    centered = true
                } else {
                    map.controller.animateTo(p)
                }
            }
            if (me != null) {
                map.overlays.add(Marker(map).apply {
                    position = GeoPoint(me.latitude, me.longitude)
                    title = "أنت"
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                })
            }
            map.invalidate()
        }
    )
}
