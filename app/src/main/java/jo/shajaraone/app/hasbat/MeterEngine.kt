package jo.shajaraone.app.hasbat

import jo.shajaraone.app.R
import jo.shajaraone.app.MainActivity

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * محرك العداد. يعيش خارج الشاشة (على مستوى التطبيق) حتى:
 * - يستمر العداد عند الخروج من التطبيق أو قفل الشاشة (مع MeterService).
 * - لا تضيع الرحلة عند إعادة بناء الشاشة أو إغلاق النظام للتطبيق: تُحفظ حالتها كل بضع ثوانٍ وتُستعاد.
 * كل التعديلات على الحالة تتم على الخيط الرئيسي.
 */
object MeterEngine {
    private const val SNAPSHOT_PREFS = "meter_snapshot"
    private const val GPS_STALE_MS = 12_000L
    private const val MAX_ACCURACY_M = 35f
    private const val MAX_PLAUSIBLE_SPEED_MS = 70f // 252 كم/س: أي قفزة أسرع من ذلك خطأ GPS
    /** تُحتسب الدقائق فقط عند الوقوف أو السير أبطأ من هذه السرعة (مثل عداد التكسي) */
    const val SLOW_SPEED_KMH = 10f

    private lateinit var appContext: Context
    private lateinit var storage: AppDataManager
    private lateinit var snapshotPrefs: SharedPreferences
    private var initialized = false
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    // ---------------- الحالة المعروضة ----------------
    var meterState by mutableStateOf(MeterState.IDLE)
        private set
    var elapsedSeconds by mutableLongStateOf(0L)
        private set
    var distanceKm by mutableDoubleStateOf(0.0)
        private set
    /** الثواني المحتسبة في الأجرة: كل وقت الرحلة من أول دقيقة لآخر دقيقة */
    var billableSeconds by mutableLongStateOf(0L)
        private set
    var speedKmh by mutableFloatStateOf(0f)
        private set
    var latitude by mutableDoubleStateOf(0.0)
        private set
    var longitude by mutableDoubleStateOf(0.0)
        private set
    var hasLocationPermission by mutableStateOf(false)
        private set
    var isGpsEnabled by mutableStateOf(true)
        private set
    var lastGpsFixRealtime by mutableLongStateOf(0L)
        private set
    var gpsAccuracyM by mutableFloatStateOf(-1f)
        private set
    var lockedTariff by mutableStateOf(TripTariff.DEFAULT)
        private set
    /** يتغير كل ثانية تقريباً حتى تعيد الواجهة فحص قوة إشارة GPS. */
    var clockTick by mutableLongStateOf(0L)
        private set

    val isTripActive: Boolean get() = meterState != MeterState.IDLE
    val displayedFare: Double get() = if (isTripActive) FareCalculator.displayed(lockedTariff, distanceKm, billableSeconds) else 0.0
    val rawFare: Double get() = if (isTripActive) FareCalculator.raw(lockedTariff, distanceKm, billableSeconds) else 0.0

    /** هل وصلت قراءة GPS حديثة؟ */
    fun hasFreshGpsFix(): Boolean =
        lastGpsFixRealtime > 0L && SystemClock.elapsedRealtime() - lastGpsFixRealtime < GPS_STALE_MS

    // ---------------- بيانات الرحلة الداخلية ----------------
    private var tripStartWallMillis = 0L
    private var accumulatedMillis = 0L
    private var lastResumeRealtime = 0L
    private var lastResumeWall = 0L
    private var billableMillis = 0L
    private var lastTickRealtime = 0L
    private var startLat = 0.0
    private var startLng = 0.0
    private var lastDistanceLoc: Location? = null
    private var uiVisible = false
    private var listening = false
    private var tickerJob: Job? = null
    private var idleClockJob: Job? = null

    fun init(context: Context) {
        if (initialized) return
        appContext = context.applicationContext
        storage = AppDataManager.get(appContext)
        snapshotPrefs = appContext.getSharedPreferences(SNAPSHOT_PREFS, Context.MODE_PRIVATE)
        initialized = true
        refreshPermission()
        restoreSnapshot()
    }

    // ======================================================================
    // دورة حياة الواجهة
    // ======================================================================
    fun onUiStart() {
        uiVisible = true
        refreshPermission()
        refreshProviderState()
        updateListening()
        ensureServiceIfActive()
        if (idleClockJob?.isActive != true) {
            idleClockJob = scope.launch {
                while (isActive) { clockTick = SystemClock.elapsedRealtime(); delay(1000L) }
            }
        }
    }

    fun onUiStop() {
        uiVisible = false
        idleClockJob?.cancel(); idleClockJob = null
        updateListening()
    }

    fun refreshPermission() {
        if (!initialized) return
        val granted = ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val changed = granted != hasLocationPermission
        hasLocationPermission = granted
        if (changed) {
            if (!granted && listening) stopListening()
            updateListening()
            ensureServiceIfActive()
        }
    }

    private fun refreshProviderState() {
        val lm = appContext.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return
        isGpsEnabled = try { lm.isProviderEnabled(LocationManager.GPS_PROVIDER) } catch (_: Exception) { false }
    }

    // ======================================================================
    // التحكم بالرحلة
    // ======================================================================
    fun startTrip(tariff: TripTariff) {
        if (!initialized || meterState != MeterState.IDLE) return
        lockedTariff = tariff
        val nowWall = System.currentTimeMillis()
        tripStartWallMillis = nowWall
        accumulatedMillis = 0L
        elapsedSeconds = 0L
        distanceKm = 0.0
        billableMillis = 0L
        billableSeconds = 0L
        lastResumeRealtime = SystemClock.elapsedRealtime()
        lastResumeWall = nowWall
        lastDistanceLoc = null
        if (hasFreshGpsFix() && latitude != 0.0) { startLat = latitude; startLng = longitude } else { startLat = 0.0; startLng = 0.0 }
        meterState = MeterState.RUNNING
        startTicker()
        saveSnapshot()
        updateListening()
        ensureServiceIfActive()
    }

    fun togglePause() {
        when (meterState) {
            MeterState.RUNNING -> {
                accumulatedMillis = currentElapsedMillis()
                elapsedSeconds = accumulatedMillis / 1000L
                meterState = MeterState.PAUSED
                tickerJob?.cancel()
            }
            MeterState.PAUSED -> {
                lastResumeRealtime = SystemClock.elapsedRealtime()
                lastResumeWall = System.currentTimeMillis()
                lastDistanceLoc = null // لا نحسب أي حركة حصلت أثناء الإيقاف
                meterState = MeterState.RUNNING
                startTicker()
            }
            MeterState.IDLE -> return
        }
        saveSnapshot()
    }

    /** ملخص الرحلة الجارية لعرضه قبل التأكيد. */
    fun currentTripIsTiny(): Boolean = isTripActive && distanceKm < 0.1 && currentElapsedMillis() < 60_000L

    /**
     * ينهي الرحلة. إذا كان [save] = false تُلغى الرحلة دون تسجيل.
     * يُحفظ السجل فوراً بالإحداثيات، ثم يُحدَّث العنوان بالخلفية دون تجميد الشاشة.
     */
    fun finishTrip(save: Boolean): TripRecord? {
        if (!isTripActive) return null
        val totalMillis = currentElapsedMillis()
        val seconds = totalMillis / 1000L
        val now = System.currentTimeMillis()
        val endLat = latitude
        val endLng = longitude
        val tariff = lockedTariff
        val billedSec = seconds // كل وقت الرحلة
        val fare = FareCalculator.displayed(tariff, distanceKm, billedSec)
        val dist = distanceKm
        val sLat = startLat
        val sLng = startLng
        val startWall = tripStartWallMillis

        var record: TripRecord? = null
        if (save) {
            val pending = if (storage.isEnglish) "Resolving address..." else "جارٍ تحديد العنوان..."
            val timeFmt = SimpleDateFormat("HH:mm:ss", Locale.US)
            record = TripRecord(
                id = now,
                dateStr = SimpleDateFormat("yyyy/MM/dd", Locale.US).format(Date(now)),
                startTimeStr = timeFmt.format(Date(startWall)),
                endTimeStr = timeFmt.format(Date(now)),
                distanceKm = dist,
                durationSeconds = seconds,
                totalFare = fare,
                appliedBaseFare = tariff.base,
                appliedKmPrice = tariff.km,
                appliedMinPrice = tariff.min,
                startAddress = if (sLat != 0.0) pending else (if (storage.isEnglish) "Not detected" else "غير محدد"),
                endAddress = if (endLat != 0.0) pending else (if (storage.isEnglish) "Not detected" else "غير محدد"),
                tariffName = tariff.name,
                multiplier = tariff.multiplier,
                isFixedPrice = tariff.fixedPrice != null,
                startLat = sLat, startLng = sLng, endLat = endLat, endLng = endLng,
                billedSeconds = billedSec
            )
        }

        // تصفير الحالة
        tickerJob?.cancel()
        meterState = MeterState.IDLE
        accumulatedMillis = 0L
        elapsedSeconds = 0L
        distanceKm = 0.0
        billableMillis = 0L
        billableSeconds = 0L
        tripStartWallMillis = 0L
        startLat = 0.0; startLng = 0.0
        lastDistanceLoc = null
        clearSnapshot()
        stopService()
        updateListening()

        record?.let {
            storage.addTrip(it)
            AutoBackup.saveAsync(appContext) // الحفظ التلقائي بعد كل رحلة
            resolveAddressesAsync(it)
        }
        return record
    }

    private fun resolveAddressesAsync(trip: TripRecord) {
        if (trip.startLat == 0.0 && trip.endLat == 0.0) return
        val english = storage.isEnglish
        scope.launch {
            val (s, e) = withContext(Dispatchers.IO) {
                val s = if (trip.startLat != 0.0) LocationAddressHelper.resolveAddressBlocking(appContext, trip.startLat, trip.startLng, english) else trip.startAddress
                val e = if (trip.endLat != 0.0) LocationAddressHelper.resolveAddressBlocking(appContext, trip.endLat, trip.endLng, english) else trip.endAddress
                s to e
            }
            storage.updateTripAddresses(trip.id, s, e)
        }
    }

    // ======================================================================
    // الوقت
    // ======================================================================
    private fun currentElapsedMillis(): Long =
        if (meterState == MeterState.RUNNING) accumulatedMillis + (SystemClock.elapsedRealtime() - lastResumeRealtime).coerceAtLeast(0L)
        else accumulatedMillis

    private fun startTicker() {
        tickerJob?.cancel()
        lastTickRealtime = SystemClock.elapsedRealtime()
        tickerJob = scope.launch {
            var n = 0
            while (isActive && meterState == MeterState.RUNNING) {
                elapsedSeconds = currentElapsedMillis() / 1000L
                // الدقائق تُحتسب كاملة من أول دقيقة لآخر دقيقة (حسب التعرفة)
                val nowRt = SystemClock.elapsedRealtime()
                lastTickRealtime = nowRt
                billableMillis = currentElapsedMillis()
                billableSeconds = billableMillis / 1000L
                if (n % 20 == 0) saveSnapshot() // كل 5 ثوانٍ
                n++
                delay(250L)
            }
        }
    }

    // ======================================================================
    // الموقع
    // ======================================================================
    private fun updateListening() {
        if (!initialized) return
        val shouldListen = hasLocationPermission && (uiVisible || meterState != MeterState.IDLE)
        if (shouldListen && !listening) startListening()
        else if (!shouldListen && listening) stopListening()
    }

    @SuppressLint("MissingPermission")
    private fun startListening() {
        val lm = appContext.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return
        try {
            val providers = lm.allProviders
            // نسجّل حتى لو كان GPS مطفأً: تبدأ القراءات تلقائياً عند تشغيله
            if (providers.contains(LocationManager.GPS_PROVIDER)) {
                lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, gpsListener, Looper.getMainLooper())
            }
            if (providers.contains(LocationManager.NETWORK_PROVIDER)) {
                lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 5000L, 0f, networkListener, Looper.getMainLooper())
            }
            listening = true
            if (latitude == 0.0) {
                val last = try { lm.getLastKnownLocation(LocationManager.GPS_PROVIDER) ?: lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER) } catch (_: Exception) { null }
                if (last != null) { latitude = last.latitude; longitude = last.longitude }
            }
        } catch (_: SecurityException) {
            listening = false
        } catch (_: Exception) {
            listening = false
        }
        refreshProviderState()
    }

    private fun stopListening() {
        val lm = appContext.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        try { lm?.removeUpdates(gpsListener) } catch (_: Exception) {}
        try { lm?.removeUpdates(networkListener) } catch (_: Exception) {}
        listening = false
        speedKmh = 0f
    }

    private val gpsListener = object : LocationListener {
        override fun onLocationChanged(location: Location) = handleGpsFix(location)
        override fun onProviderEnabled(provider: String) { if (provider == LocationManager.GPS_PROVIDER) isGpsEnabled = true }
        override fun onProviderDisabled(provider: String) {
            if (provider == LocationManager.GPS_PROVIDER) { isGpsEnabled = false; speedKmh = 0f }
        }
        // مطلوبة على أندرويد 8–10 حيث كانت الدالة إلزامية في الواجهة
        @Deprecated("Deprecated in Android API")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
    }

    /** موقع الشبكة يُستخدم لعرض الموقع فقط عند غياب GPS، ولا يدخل أبداً في حساب المسافة. */
    private val networkListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            if (!hasFreshGpsFix()) { latitude = location.latitude; longitude = location.longitude }
        }
        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}
        @Deprecated("Deprecated in Android API")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
    }

    private fun handleGpsFix(loc: Location) {
        latitude = loc.latitude
        longitude = loc.longitude
        lastGpsFixRealtime = SystemClock.elapsedRealtime()
        gpsAccuracyM = if (loc.hasAccuracy()) loc.accuracy else -1f
        isGpsEnabled = true

        if (meterState != MeterState.IDLE && startLat == 0.0 && loc.latitude != 0.0) {
            startLat = loc.latitude; startLng = loc.longitude
        }

        if (loc.hasAccuracy() && loc.accuracy > MAX_ACCURACY_M) return

        val prev = lastDistanceLoc
        val dtSec = if (prev != null) (loc.elapsedRealtimeNanos - prev.elapsedRealtimeNanos) / 1_000_000_000.0 else 0.0
        val rawSpeed = if (loc.hasSpeed() && loc.speed > 0.2f) {
            loc.speed * 3.6f
        } else if (prev != null && dtSec > 0.3) {
            (prev.distanceTo(loc) / dtSec).toFloat() * 3.6f
        } else 0f
        speedKmh = if (rawSpeed < 2.0f) 0f else rawSpeed

        if (meterState == MeterState.RUNNING) {
            if (prev == null) {
                lastDistanceLoc = loc
            } else {
                val deltaM = prev.distanceTo(loc)
                if (dtSec > 0.0 && deltaM / dtSec > MAX_PLAUSIBLE_SPEED_MS) {
                    // قفزة غير منطقية: لا نضيف مسافة، ونبدأ القياس من النقطة الجديدة
                    lastDistanceLoc = loc
                    return
                }
                if (deltaM >= 2.5f && speedKmh > 1.8f && dtSec > 0.4) {
                    distanceKm += deltaM / 1000.0
                    lastDistanceLoc = loc
                }
            }
        } else {
            lastDistanceLoc = loc
        }
    }

    // ======================================================================
    // الخدمة الأمامية
    // ======================================================================
    private fun ensureServiceIfActive() {
        if (!initialized || meterState == MeterState.IDLE || !hasLocationPermission) return
        try {
            ContextCompat.startForegroundService(appContext, Intent(appContext, MeterService::class.java))
        } catch (_: Exception) {
            // قد يرفض النظام تشغيل الخدمة من الخلفية؛ ستُشغَّل عند فتح التطبيق
        }
    }

    private fun stopService() {
        try { appContext.stopService(Intent(appContext, MeterService::class.java)) } catch (_: Exception) {}
    }

    // ======================================================================
    // حفظ واستعادة الرحلة الجارية
    // ======================================================================
    private fun saveSnapshot() {
        if (!initialized) return
        if (meterState == MeterState.IDLE) { clearSnapshot(); return }
        val t = lockedTariff
        val tariffJson = JSONObject().apply {
            put("name", t.name); put("base", t.base); put("km", t.km); put("min", t.min); put("mult", t.multiplier)
            if (t.fixedPrice != null) put("fixed", t.fixedPrice)
        }.toString()
        snapshotPrefs.edit()
            .putString("state", meterState.name)
            .putString("tariff", tariffJson)
            .putLong("startWall", tripStartWallMillis)
            .putLong("accumulated", accumulatedMillis)
            .putLong("lastResumeRealtime", lastResumeRealtime)
            .putLong("lastResumeWall", lastResumeWall)
            .putString("distance", distanceKm.toString())
            .putLong("billable", billableMillis)
            .putString("startLat", startLat.toString())
            .putString("startLng", startLng.toString())
            .apply()
    }

    private fun clearSnapshot() {
        snapshotPrefs.edit().clear().apply()
    }

    private fun restoreSnapshot() {
        val stateName = snapshotPrefs.getString("state", null) ?: return
        val state = try { MeterState.valueOf(stateName) } catch (_: Exception) { null }
        if (state == null || state == MeterState.IDLE) { clearSnapshot(); return }
        try {
            val o = JSONObject(snapshotPrefs.getString("tariff", "{}") ?: "{}")
            lockedTariff = TripTariff(
                name = o.optString("name", "عادي"),
                base = o.optDouble("base", 0.45),
                km = o.optDouble("km", 0.25),
                min = o.optDouble("min", 0.05),
                multiplier = o.optDouble("mult", 1.0),
                fixedPrice = if (o.has("fixed")) o.optDouble("fixed") else null
            )
        } catch (_: Exception) {
            lockedTariff = TripTariff.DEFAULT
        }
        tripStartWallMillis = snapshotPrefs.getLong("startWall", System.currentTimeMillis())
        accumulatedMillis = snapshotPrefs.getLong("accumulated", 0L)
        lastResumeRealtime = snapshotPrefs.getLong("lastResumeRealtime", SystemClock.elapsedRealtime())
        lastResumeWall = snapshotPrefs.getLong("lastResumeWall", System.currentTimeMillis())
        distanceKm = snapshotPrefs.getString("distance", "0")?.toDoubleOrNull() ?: 0.0
        // كل وقت الرحلة يُحتسب
        billableMillis = accumulatedMillis
        billableSeconds = billableMillis / 1000L
        startLat = snapshotPrefs.getString("startLat", "0")?.toDoubleOrNull() ?: 0.0
        startLng = snapshotPrefs.getString("startLng", "0")?.toDoubleOrNull() ?: 0.0

        if (state == MeterState.RUNNING) {
            val nowRt = SystemClock.elapsedRealtime()
            if (nowRt < lastResumeRealtime) {
                // أُعيد تشغيل الجهاز: نعتمد على ساعة الجهاز لحساب الوقت المنقضي
                val wallGap = (System.currentTimeMillis() - lastResumeWall).coerceAtLeast(0L)
                lastResumeRealtime = nowRt - wallGap
            }
        }
        meterState = state
        elapsedSeconds = currentElapsedMillis() / 1000L
        lastDistanceLoc = null
        if (state == MeterState.RUNNING) startTicker()
        saveSnapshot()
        updateListening()
    }
}
