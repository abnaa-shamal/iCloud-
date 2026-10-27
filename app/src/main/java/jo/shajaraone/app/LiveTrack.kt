package jo.shajaraone.app

import android.content.Intent
import android.location.Location
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
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
fun LiveTrackDialog(tripId: String, captainName: String, captainPhone: String = "", onClose: () -> Unit) {
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
                    Column(Modifier.weight(1f)) {
                        Text("موقع الكابتن", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Text("لحجزك المؤكد فقط", style = MaterialTheme.typography.bodyMedium, color = Color(0xFFB9B2FF))
                    }
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
                LiveInfo(live, me, nowMs, captainName, captainPhone)
            }
        }
    }
}

@Composable
private fun LiveInfo(live: LiveLoc?, me: Location?, nowMs: Long, captainName: String, captainPhone: String) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxWidth().padding(14.dp)
            .background(Color.White.copy(alpha = 0.07f), RoundedCornerShape(24.dp))
            .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(24.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Avatar(captainName, 46.dp)
            Column(Modifier.weight(1f)) {
                Text("الكابتن $captainName", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            if (live != null) {
                val age = ((nowMs - live.updatedAt) / 1000).coerceAtLeast(0)
                val stale = age > 60
                val tone = if (stale) cs.error else Color(0xFF2DE2C0)
                Column(
                    Modifier.background(tone.copy(alpha = 0.12f), RoundedCornerShape(14.dp)).padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalAlignment = Alignment.Start
                ) {
                    Text(if (stale) "موقع قديم" else "موقع حديث", color = tone, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    Text(
                        if (stale) "آخر تحديث قبل ${age / 60} دقيقة" else "آخر تحديث قبل $age ثانية",
                        style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant
                    )
                }
            }
        }
        if (live != null) {
            HorizontalDivider(color = Color.White.copy(alpha = 0.10f))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                if (me != null) {
                    val d = Location("x").apply { latitude = live.lat; longitude = live.lng }.distanceTo(me)
                    LiveStat("مسافة مباشرة", if (d >= 1000) "%.1f كم".format(java.util.Locale.US, d / 1000) else "${d.toInt()} م")
                }
                LiveStat("السرعة", if (live.speed > 1.0) "${(live.speed * 3.6).toInt()} كم/س" else "واقف")
                LiveStat("الدقة", "± ${live.accuracy.toInt()} م")
            }
            Text("قراءات تقديرية تعتمد على الهاتف", style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant, modifier = Modifier.align(Alignment.CenterHorizontally))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = {
                        val uri = Uri.parse("geo:${live.lat},${live.lng}?q=${live.lat},${live.lng}(${Uri.encode("الكابتن")})")
                        try {
                            ctx.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        } catch (e: Exception) {
                            toast(ctx, "ما لقيت تطبيق خرائط")
                        }
                    },
                    shape = MaterialTheme.shapes.medium, modifier = Modifier.weight(1f).height(48.dp)
                ) { Text("افتح بتطبيق الخرائط") }
                if (captainPhone.isNotBlank()) {
                    CallButton(captainPhone)
                    WhatsAppButton(captainPhone, "مرحبا كابتن $captainName، وينك هسه؟")
                }
            }
        }
    }
}

@Composable
private fun LiveStat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
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
