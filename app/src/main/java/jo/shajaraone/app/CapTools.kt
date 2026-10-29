package jo.shajaraone.app

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// =====================================================================================================
// Captain tools: a trip meter (speed, distance, time, fare), private tariffs, finances and history.
// EVERYTHING here lives on the captain's own phone (SharedPreferences). Nothing is sent to Firebase and
// nothing here touches seat prices, trips or bookings.
// =====================================================================================================

// ---------- models ----------

data class Tariff(val name: String, val base: Double, val km: Double, val min: Double)
data class TripRec(val id: Long, val date: String, val start: String, val end: String, val km: Double, val sec: Long, val fare: Double, val tariff: String)
data class ExpRec(val id: Long, val date: String, val category: String, val amount: Double)

const val MIN_FARE = 1.0

val defaultTariffs = listOf(
    Tariff("Saver", 0.400, 0.200, 0.040),
    Tariff("Electric", 0.430, 0.230, 0.055),
    Tariff("Comfort", 0.450, 0.250, 0.070),
    Tariff("تعرفة 1", 0.400, 0.200, 0.040)
)

fun fareOf(t: Tariff, km: Double, sec: Long): Double = maxOf(MIN_FARE, t.base + km * t.km + (sec / 60.0) * t.min)

private fun money(v: Double) = String.format(Locale.US, "%.3f", v)
private fun money2(v: Double) = String.format(Locale.US, "%.2f", v)
private fun kmText(v: Double) = String.format(Locale.US, "%.2f", v)
private fun clockSec(s: Long): String = String.format(Locale.US, "%02d:%02d:%02d", s / 3600, (s % 3600) / 60, s % 60)

/** Accepts Arabic digits and the Arabic decimal comma. */
fun parseNum(text: String): Double? {
    val sb = StringBuilder()
    for (ch in text.trim()) when (ch) {
        in '٠'..'٩' -> sb.append('0' + (ch - '٠'))
        '٫', ',', '،' -> sb.append('.')
        ' ' -> {}
        else -> sb.append(ch)
    }
    val v = sb.toString().toDoubleOrNull() ?: return null
    return if (v.isNaN() || v.isInfinite()) null else v
}

// ---------- local storage ----------

object CapStore {
    private fun sp(c: Context): SharedPreferences = c.applicationContext.getSharedPreferences("cap_tools", Context.MODE_PRIVATE)

    fun tariffs(c: Context): List<Tariff> = try {
        val a = JSONArray(sp(c).getString("tariffs", null) ?: return defaultTariffs)
        List(a.length()) { i -> a.getJSONObject(i).let { Tariff(it.getString("n"), it.getDouble("b"), it.getDouble("k"), it.getDouble("m")) } }
            .takeIf { it.size == defaultTariffs.size } ?: defaultTariffs
    } catch (_: Exception) { defaultTariffs }

    fun saveTariffs(c: Context, l: List<Tariff>) {
        val a = JSONArray()
        l.forEach { a.put(JSONObject().put("n", it.name).put("b", it.base).put("k", it.km).put("m", it.min)) }
        sp(c).edit().putString("tariffs", a.toString()).apply()
    }

    fun selected(c: Context): Int = sp(c).getInt("sel", 0).coerceIn(0, defaultTariffs.size - 1)
    fun select(c: Context, i: Int) { sp(c).edit().putInt("sel", i).apply() }

    fun trips(c: Context): List<TripRec> = try {
        val a = JSONArray(sp(c).getString("trips", "[]"))
        List(a.length()) { i ->
            a.getJSONObject(i).let { TripRec(it.getLong("id"), it.getString("d"), it.getString("s"), it.getString("e"), it.getDouble("km"), it.getLong("sec"), it.getDouble("f"), it.optString("t")) }
        }
    } catch (_: Exception) { emptyList() }

    private fun putTrips(c: Context, l: List<TripRec>) {
        val a = JSONArray()
        l.forEach { a.put(JSONObject().put("id", it.id).put("d", it.date).put("s", it.start).put("e", it.end).put("km", it.km).put("sec", it.sec).put("f", it.fare).put("t", it.tariff)) }
        sp(c).edit().putString("trips", a.toString()).apply()
    }

    fun addTrip(c: Context, t: TripRec) = putTrips(c, trips(c) + t)
    fun deleteTrip(c: Context, id: Long) = putTrips(c, trips(c).filter { it.id != id })

    fun expenses(c: Context): List<ExpRec> = try {
        val a = JSONArray(sp(c).getString("exp", "[]"))
        List(a.length()) { i -> a.getJSONObject(i).let { ExpRec(it.getLong("id"), it.getString("d"), it.getString("c"), it.getDouble("a")) } }
    } catch (_: Exception) { emptyList() }

    private fun putExp(c: Context, l: List<ExpRec>) {
        val a = JSONArray()
        l.forEach { a.put(JSONObject().put("id", it.id).put("d", it.date).put("c", it.category).put("a", it.amount)) }
        sp(c).edit().putString("exp", a.toString()).apply()
    }

    fun addExpense(c: Context, e: ExpRec) = putExp(c, expenses(c) + e)
    fun deleteExpense(c: Context, id: Long) = putExp(c, expenses(c).filter { it.id != id })
}

private fun todayStr() = SimpleDateFormat("yyyy/MM/dd", Locale.US).format(Date())
private fun monthStr() = SimpleDateFormat("yyyy/MM", Locale.US).format(Date())

// ---------- meter engine (lives outside the screen so it survives navigation) ----------

enum class MState { IDLE, RUNNING, PAUSED }

object CapMeter {
    private const val MAX_ACCURACY_M = 35f
    private const val MAX_PLAUSIBLE_MS = 70f

    private lateinit var app: Context
    private var ready = false
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    var state by mutableStateOf(MState.IDLE)
        private set
    var seconds by mutableLongStateOf(0L)
        private set
    var km by mutableDoubleStateOf(0.0)
        private set
    var speed by mutableFloatStateOf(0f)
        private set
    var gpsOk by mutableStateOf(false)
        private set
    var tariff by mutableStateOf(defaultTariffs[0])
        private set

    val active get() = state != MState.IDLE
    val fare get() = if (active) fareOf(tariff, km, seconds) else 0.0

    private var startWall = 0L
    private var accMillis = 0L
    private var resumeRt = 0L
    private var lastLoc: Location? = null
    private var lastFixRt = 0L
    private var uiVisible = false
    private var listening = false
    private var ticker: Job? = null

    fun init(c: Context) { if (!ready) { app = c.applicationContext; ready = true } }

    fun setUiVisible(c: Context, v: Boolean) { init(c); uiVisible = v; updateListening() }

    fun start(c: Context, t: Tariff) {
        init(c)
        if (state != MState.IDLE) return
        tariff = t
        startWall = System.currentTimeMillis()
        accMillis = 0L; seconds = 0L; km = 0.0; lastLoc = null
        resumeRt = SystemClock.elapsedRealtime()
        state = MState.RUNNING
        startTicker(); updateListening()
        try { ContextCompat.startForegroundService(app, Intent(app, CapMeterService::class.java)) } catch (_: Exception) {}
    }

    fun togglePause() {
        when (state) {
            MState.RUNNING -> { accMillis = elapsed(); seconds = accMillis / 1000; state = MState.PAUSED; ticker?.cancel() }
            MState.PAUSED -> { resumeRt = SystemClock.elapsedRealtime(); lastLoc = null; state = MState.RUNNING; startTicker() }
            MState.IDLE -> {}
        }
    }

    /** Ends the trip. save = false discards it. Returns the saved record. */
    fun finish(save: Boolean): TripRec? {
        if (!active) return null
        val sec = elapsed() / 1000
        val rec = if (save) {
            val tf = SimpleDateFormat("HH:mm", Locale.US)
            TripRec(System.currentTimeMillis(), todayStr(), tf.format(Date(startWall)), tf.format(Date()), km, sec, fareOf(tariff, km, sec), tariff.name)
        } else null
        ticker?.cancel()
        state = MState.IDLE; seconds = 0L; km = 0.0; accMillis = 0L; lastLoc = null
        updateListening()
        try { app.stopService(Intent(app, CapMeterService::class.java)) } catch (_: Exception) {}
        rec?.let { CapStore.addTrip(app, it) }
        return rec
    }

    private fun elapsed(): Long =
        if (state == MState.RUNNING) accMillis + (SystemClock.elapsedRealtime() - resumeRt).coerceAtLeast(0L) else accMillis

    private fun startTicker() {
        ticker?.cancel()
        ticker = scope.launch {
            while (isActive && state == MState.RUNNING) {
                seconds = elapsed() / 1000
                gpsOk = lastFixRt > 0 && SystemClock.elapsedRealtime() - lastFixRt < 12_000L
                delay(250L)
            }
        }
    }

    private fun updateListening() {
        if (!ready) return
        val has = hasLocationPermission(app)
        val want = has && (uiVisible || active)
        if (want && !listening) startListening() else if (!want && listening) stopListening()
    }

    @SuppressLint("MissingPermission")
    private fun startListening() {
        val lm = app.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return
        try {
            if (lm.allProviders.contains(LocationManager.GPS_PROVIDER)) {
                lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, listener, Looper.getMainLooper())
                listening = true
            }
        } catch (_: Exception) { listening = false }
    }

    private fun stopListening() {
        val lm = app.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        try { lm?.removeUpdates(listener) } catch (_: Exception) {}
        listening = false
        speed = 0f
    }

    private val listener = object : LocationListener {
        override fun onLocationChanged(location: Location) = onFix(location)
        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) { speed = 0f }
        @Deprecated("Deprecated in Android API")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
    }

    private fun onFix(loc: Location) {
        lastFixRt = SystemClock.elapsedRealtime()
        gpsOk = true
        if (loc.hasAccuracy() && loc.accuracy > MAX_ACCURACY_M) return
        val prev = lastLoc
        val dt = if (prev != null) (loc.elapsedRealtimeNanos - prev.elapsedRealtimeNanos) / 1_000_000_000.0 else 0.0
        val raw = if (loc.hasSpeed() && loc.speed > 0.2f) loc.speed * 3.6f
        else if (prev != null && dt > 0.3) (prev.distanceTo(loc) / dt).toFloat() * 3.6f else 0f
        speed = if (raw < 2f) 0f else raw
        if (state == MState.RUNNING) {
            if (prev == null) { lastLoc = loc; return }
            val d = prev.distanceTo(loc)
            if (dt > 0.0 && d / dt > MAX_PLAUSIBLE_MS) { lastLoc = loc; return }
            if (d >= 2.5f && speed > 1.8f && dt > 0.4) { km += d / 1000.0; lastLoc = loc }
        } else lastLoc = loc
    }
}

/** Keeps the meter and GPS alive when the screen is locked or another app (maps) is open. */
class CapMeterService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var job: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        CapMeter.init(this)
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CH) == null) nm.createNotificationChannel(NotificationChannel(CH, "عداد الرحلة", NotificationManager.IMPORTANCE_LOW))
        val ok = try {
            ServiceCompat.startForeground(this, NID, note(), if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0)
            true
        } catch (_: Exception) { false }
        if (!ok || !CapMeter.active) { stopSelf(); return START_NOT_STICKY }
        if (job?.isActive != true) job = scope.launch {
            while (isActive) {
                delay(2000L)
                if (!CapMeter.active) { stopSelf(); break }
                try { nm.notify(NID, note()) } catch (_: Exception) {}
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() { scope.cancel(); super.onDestroy() }

    private fun note(): Notification {
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val paused = CapMeter.state == MState.PAUSED
        return NotificationCompat.Builder(this, CH)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle((if (paused) "⏸ الرحلة متوقفة" else "🚕 رحلة جارية") + " • ${money2(CapMeter.fare)} د.أ")
            .setContentText("${kmText(CapMeter.km)} كم • ${clockSec(CapMeter.seconds)}")
            .setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true).setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_LOW).build()
    }

    companion object { private const val CH = "cap_meter"; private const val NID = 4107 }
}

// ---------- screens ----------

@Composable
fun CapToolsScreen(trips: List<Trip>?, bookings: List<Booking>) {
    var section by rememberSaveable { mutableStateOf("") }
    BackHandler(enabled = section.isNotEmpty()) { section = "" }
    when (section) {
        "trips" -> CapTripHistory(trips, bookings) { section = "" }
        "meter" -> CapMeterScreen { section = "" }
        "finance" -> CapFinanceScreen { section = "" }
        "history" -> CapHistoryScreen { section = "" }
        "tariffs" -> CapTariffScreen { section = "" }
        else -> Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp).padding(top = 44.dp, bottom = 170.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("الإعدادات", Modifier.padding(bottom = 6.dp), fontSize = 32.sp, fontWeight = FontWeight.Bold, color = CapInk)
            CapBigChoice("سجل الرحلات", "رحلاتك المنشورة وركابها", 0) { section = "trips" }
            Text("أدواتك (على جوالك بس، ما إلها علاقة بأسعار المقاعد ولا بالحجوزات)", Modifier.padding(top = 6.dp), fontSize = 13.sp, color = CapMute)
            CapBigChoice("العداد", "السرعة والمسافة والزمن والأجرة", 1) { section = "meter" }
            CapBigChoice("المالية", "الدخل والمصاريف وصافي الربح", 2) { section = "finance" }
            CapBigChoice("سجل العداد", "رحلات العداد وتفاصيلها", 3) { section = "history" }
            CapBigChoice("أسعار التعرفة", "Saver • Electric • Comfort • تعرفة 1", 4) { section = "tariffs" }
        }
    }
}

@Composable
private fun SpeedGauge(speed: Float, modifier: Modifier = Modifier) {
    val frac by animateFloatAsState((speed / 160f).coerceIn(0f, 1f), tween(400), label = "gauge")
    Box(modifier.aspectRatio(1.1f), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize().padding(8.dp)) {
            val sw = size.minDimension * 0.07f
            val inset = sw / 2
            val box = Size(size.minDimension - sw, size.minDimension - sw)
            val tl = Offset((size.width - box.width) / 2, inset)
            drawArc(Color(0x26FFFFFF), 150f, 240f, false, tl, box, style = Stroke(sw, cap = StrokeCap.Round))
            if (frac > 0.01f) drawArc(CapAccent, 150f, 240f * frac, false, tl, box, style = Stroke(sw, cap = StrokeCap.Round))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(speed.toInt().toString(), fontSize = 70.sp, fontWeight = FontWeight.Bold, color = CapInk)
            Text("كم/س", fontSize = 14.sp, color = CapMute)
        }
    }
}

@Composable
private fun StatBox(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier.capCard(20.dp).padding(vertical = 14.dp, horizontal = 8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(value, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = CapInk, maxLines = 1)
        Text(label, fontSize = 12.sp, color = CapMute)
    }
}

@Composable
private fun CapMeterScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    var sel by remember { mutableIntStateOf(CapStore.selected(ctx)) }
    val tariffs = remember { CapStore.tariffs(ctx) }
    var confirmEnd by remember { mutableStateOf(false) }
    DisposableEffect(Unit) { CapMeter.setUiVisible(ctx, true); onDispose { CapMeter.setUiVisible(ctx, false) } }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        if (r.values.any { it }) { CapMeter.setUiVisible(ctx, true); CapMeter.start(ctx, tariffs[sel]) } else toast(ctx, "لازم توافق على الموقع عشان يشتغل العداد")
    }
    val t = if (CapMeter.active) CapMeter.tariff else tariffs[sel]

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp).padding(top = 44.dp, bottom = 170.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        CapBackBar("العداد") { onBack() }
        SpeedGauge(CapMeter.speed, Modifier.fillMaxWidth(0.8f).align(Alignment.CenterHorizontally))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatBox("المسافة (كم)", kmText(CapMeter.km), Modifier.weight(1f))
            StatBox("الزمن", clockSec(CapMeter.seconds), Modifier.weight(1.2f))
            StatBox("الأجرة (د.أ)", money2(if (CapMeter.active) CapMeter.fare else 0.0), Modifier.weight(1f))
        }
        if (CapMeter.active && !CapMeter.gpsOk) Text("⚠️ ما في إشارة GPS. تأكد إن الموقع شغّال", fontSize = 13.sp, color = CapRed)
        if (!CapMeter.active) {
            CapSection("التعرفة") {
                CapGrid(tariffs.map { it.name }, tariffs[sel].name, 2) { n -> sel = tariffs.indexOfFirst { it.name == n }.coerceAtLeast(0); CapStore.select(ctx, sel) }
                Text("فتح ${money(t.base)} • الكم ${money(t.km)} • الدقيقة ${money(t.min)} • أدنى أجرة ${money2(MIN_FARE)}", fontSize = 12.sp, color = CapMute)
            }
            CapBtn("ابدأ الرحلة", Modifier.fillMaxWidth(), height = 58.dp) {
                if (hasLocationPermission(ctx)) CapMeter.start(ctx, tariffs[sel])
                else ask.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CapBtn(if (CapMeter.state == MState.PAUSED) "كمّل" else "إيقاف مؤقت", Modifier.weight(1f), filled = false, height = 56.dp) { CapMeter.togglePause() }
                CapBtn("إنهاء الرحلة", Modifier.weight(1f), height = 56.dp) { confirmEnd = true }
            }
        }
    }

    if (confirmEnd) AlertDialog(
        onDismissRequest = { confirmEnd = false },
        title = { Text("إنهاء الرحلة؟") },
        text = { Text("الأجرة ${money2(CapMeter.fare)} د.أ • ${kmText(CapMeter.km)} كم • ${clockSec(CapMeter.seconds)}") },
        confirmButton = { TextButton(onClick = { CapMeter.finish(true); confirmEnd = false; toast(ctx, "انحفظت الرحلة بالسجل ✅") }) { Text("إنهاء وحفظ") } },
        dismissButton = {
            Row {
                TextButton(onClick = { CapMeter.finish(false); confirmEnd = false }) { Text("إلغاء بدون حفظ") }
                TextButton(onClick = { confirmEnd = false }) { Text("رجوع") }
            }
        }
    )
}

@Composable
private fun CapFinanceScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    var trips by remember { mutableStateOf(CapStore.trips(ctx)) }
    var exps by remember { mutableStateOf(CapStore.expenses(ctx)) }
    var adding by remember { mutableStateOf(false) }
    val today = todayStr(); val month = monthStr()
    val incDay = trips.filter { it.date == today }.sumOf { it.fare }
    val incMonth = trips.filter { it.date.startsWith(month) }.sumOf { it.fare }
    val expDay = exps.filter { it.date == today }.sumOf { it.amount }
    val expMonth = exps.filter { it.date.startsWith(month) }.sumOf { it.amount }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp).padding(top = 44.dp, bottom = 170.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        CapBackBar("المالية") { onBack() }
        listOf("اليوم" to Triple(incDay, expDay, trips.count { it.date == today }), "هالشهر" to Triple(incMonth, expMonth, trips.count { it.date.startsWith(month) })).forEach { (title, v) ->
            CapSection(title) {
                FinRow("الدخل (${v.third} رحلة)", money2(v.first), CapInk)
                FinRow("المصاريف", money2(v.second), CapRed)
                FinRow("صافي الربح", money2(v.first - v.second), CapAccent)
            }
        }
        CapBtn("أضف مصروف", Modifier.fillMaxWidth()) { adding = true }
        val todayExp = exps.filter { it.date == today }.sortedByDescending { it.id }
        if (todayExp.isNotEmpty()) CapSection("مصاريف اليوم") {
            todayExp.forEach { e ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(e.category, Modifier.weight(1f), fontSize = 15.sp, color = CapInk)
                    Text(money2(e.amount), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = CapRed)
                    Text("  ✕", Modifier.clickable { CapStore.deleteExpense(ctx, e.id); exps = CapStore.expenses(ctx) }.padding(8.dp), color = CapMute)
                }
            }
        }
    }

    if (adding) {
        var cat by remember { mutableStateOf("وقود") }
        var amount by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { adding = false },
            title = { Text("مصروف جديد") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    CapGrid(listOf("وقود", "صيانة", "غسيل", "أخرى"), cat, 2) { cat = it }
                    OutlinedTextField(amount, { amount = it }, label = { Text("المبلغ (د.أ)") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val v = parseNum(amount)
                    if (v == null || v <= 0) toast(ctx, "اكتب مبلغ صحيح") else {
                        CapStore.addExpense(ctx, ExpRec(System.currentTimeMillis(), todayStr(), cat, v)); exps = CapStore.expenses(ctx); adding = false
                    }
                }) { Text("حفظ") }
            },
            dismissButton = { TextButton(onClick = { adding = false }) { Text("إلغاء") } }
        )
    }
}

@Composable
private fun FinRow(label: String, value: String, color: Color) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), fontSize = 15.sp, color = CapMute)
        Text("$value د.أ", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = color)
    }
}

@Composable
private fun CapHistoryScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    var trips by remember { mutableStateOf(CapStore.trips(ctx).sortedByDescending { it.id }) }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp).padding(top = 44.dp, bottom = 170.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        CapBackBar("سجل العداد") { onBack() }
        if (trips.isEmpty()) Box(Modifier.fillMaxWidth().capCard().padding(24.dp), contentAlignment = Alignment.Center) { Text("ما في رحلات محفوظة", color = CapMute) }
        trips.forEach { r ->
            Column(Modifier.fillMaxWidth().capCard().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("${r.date} • ${r.start} ← ${r.end}", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = CapInk)
                        Text("${kmText(r.km)} كم • ${clockSec(r.sec)} • ${r.tariff}", fontSize = 12.sp, color = CapMute)
                    }
                    Text("${money2(r.fare)} د.أ", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = CapAccent)
                }
                Text("حذف", Modifier.clickable { CapStore.deleteTrip(ctx, r.id); trips = CapStore.trips(ctx).sortedByDescending { it.id } }.padding(vertical = 4.dp), fontSize = 12.sp, color = CapRed)
            }
        }
    }
}

@Composable
private fun CapTariffScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    var list by remember { mutableStateOf(CapStore.tariffs(ctx)) }
    var editIdx by remember { mutableIntStateOf(-1) }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp).padding(top = 44.dp, bottom = 170.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        CapBackBar("أسعار التعرفة") { onBack() }
        Text("أسعارك الخاصة. بتأثر على العداد بس، وما إلها علاقة بأسعار المقاعد.", fontSize = 13.sp, color = CapMute)
        list.forEachIndexed { i, t ->
            Column(Modifier.fillMaxWidth().capCard().clickable { editIdx = i }.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(t.name, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = CapInk)
                Text("فتح ${money(t.base)} • الكم ${money(t.km)} • الدقيقة ${money(t.min)}", fontSize = 13.sp, color = CapMute)
                Text("اضغط للتعديل", fontSize = 11.sp, color = CapAccent)
            }
        }
        CapBtn("رجّع الأسعار الأصلية", Modifier.fillMaxWidth(), filled = false) { list = defaultTariffs; CapStore.saveTariffs(ctx, list) }
    }

    if (editIdx >= 0) {
        val cur = list[editIdx]
        var b by remember { mutableStateOf(money(cur.base)) }
        var k by remember { mutableStateOf(money(cur.km)) }
        var m by remember { mutableStateOf(money(cur.min)) }
        AlertDialog(
            onDismissRequest = { editIdx = -1 },
            title = { Text(cur.name) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(Triple("سعر الفتح", b, 0), Triple("سعر الكم", k, 1), Triple("سعر الدقيقة", m, 2)).forEach { (label, v, which) ->
                        OutlinedTextField(v, { x -> when (which) { 0 -> b = x; 1 -> k = x; else -> m = x } }, label = { Text(label) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val nb = parseNum(b); val nk = parseNum(k); val nm = parseNum(m)
                    if (nb == null || nk == null || nm == null || nb < 0 || nk < 0 || nm < 0) toast(ctx, "اكتب أرقام صحيحة")
                    else {
                        list = list.toMutableList().also { it[editIdx] = cur.copy(base = nb, km = nk, min = nm) }
                        CapStore.saveTariffs(ctx, list); editIdx = -1
                    }
                }) { Text("حفظ") }
            },
            dismissButton = { TextButton(onClick = { editIdx = -1 }) { Text("إلغاء") } }
        )
    }
}
