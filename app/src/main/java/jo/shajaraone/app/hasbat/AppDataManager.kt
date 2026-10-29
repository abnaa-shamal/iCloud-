package jo.shajaraone.app.hasbat

import jo.shajaraone.app.R
import jo.shajaraone.app.MainActivity

import android.content.Context
import android.content.SharedPreferences
import android.util.AtomicFile
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import kotlin.math.round

/**
 * تخزين بيانات التطبيق.
 * - الإعدادات الصغيرة في SharedPreferences.
 * - الرحلات والمصاريف والتعرفات في ملفات JSON تُكتب بشكل ذرّي (AtomicFile)
 *   حتى لا يتلف الملف إذا أُغلق التطبيق أثناء الحفظ.
 * - عند أول تشغيل تُنقل البيانات القديمة (النص المفصول بـ :: و ;;) تلقائياً دون حذفها.
 * - [version] يزيد مع كل تعديل حتى تُحدَّث الشاشات تلقائياً.
 */
class AppDataManager private constructor(context: Context) {

    companion object {
        const val MIN_BASE = 0.400; const val MAX_BASE = 0.450
        const val MIN_KM = 0.200; const val MAX_KM = 0.250
        const val MIN_MIN = 0.040; const val MAX_MIN = 0.070

        // فئات التعرفة الثابتة (2.9.2): أسعارها ثابتة ولا تُعدَّل ولا تُحذف
        const val TIER_SAVER_ID = 101L
        const val TIER_ELECTRIC_ID = 102L
        const val TIER_COMFORT_ID = 103L
        val TIERS: List<TariffPreset> = listOf(
            TariffPreset(TIER_SAVER_ID, "Saver", 0.400, 0.200, 0.040),
            TariffPreset(TIER_ELECTRIC_ID, "Electric", 0.430, 0.230, 0.055),
            TariffPreset(TIER_COMFORT_ID, "Comfort", 0.450, 0.250, 0.070)
        )
        fun isTier(id: Long): Boolean = TIERS.any { it.id == id }

        @Volatile private var instance: AppDataManager? = null

        fun get(context: Context): AppDataManager =
            instance ?: synchronized(this) {
                instance ?: AppDataManager(context.applicationContext).also { instance = it }
            }

        fun round3(v: Double): Double = round(v * 1000.0) / 1000.0
    }

    private val filesDir: File = context.filesDir
    private val prefs: SharedPreferences = context.getSharedPreferences("captain_master_store", Context.MODE_PRIVATE)
    private val tripsFile = AtomicFile(File(filesDir, "trips.json"))
    private val expensesFile = AtomicFile(File(filesDir, "expenses.json"))
    private val presetsFile = AtomicFile(File(filesDir, "tariffs.json"))

    private var tripsCache: MutableList<TripRecord>? = null
    private var expensesCache: MutableList<ExpenseRecord>? = null
    private var presetsCache: MutableList<TariffPreset>? = null

    /** يزيد مع كل تعديل على الرحلات أو المصاريف أو التعرفات. */
    var version by mutableIntStateOf(0)
        private set

    // ------------------------------------------------------------------
    // الإعدادات (حالة Compose حتى تتحدث الواجهة فوراً)
    // ------------------------------------------------------------------
    var language by mutableStateOf(prefs.getString("app_lang", "ar") ?: "ar")
        private set
    var themeIndex by mutableIntStateOf(prefs.getInt("theme_index", 0))
        private set
    var soundEnabled by mutableStateOf(prefs.getBoolean("sound_enabled", true))
        private set
    var selectedPresetId by mutableLongStateOf(prefs.getLong("selected_preset_id", 1L))
        private set
    /** التبديل التلقائي: ثيم النهار بعد الشروق، وثيم الليل (themeIndex) بعد المغرب. */
    var autoDayNight by mutableStateOf(prefs.getBoolean("auto_day_night", false))
        private set
    var dayThemeIndex by mutableIntStateOf(prefs.getInt("day_theme_index", AppTheme.DAYLIGHT_PRO.ordinal))
        private set

    val isEnglish: Boolean get() = language == "en"

    fun saveLanguage(lang: String) { language = lang; prefs.edit().putString("app_lang", lang).apply() }
    fun saveThemeIndex(index: Int) { themeIndex = index; prefs.edit().putInt("theme_index", index).apply() }
    fun setSound(enabled: Boolean) { soundEnabled = enabled; prefs.edit().putBoolean("sound_enabled", enabled).apply() }
    fun selectPreset(id: Long) { selectedPresetId = id; prefs.edit().putLong("selected_preset_id", id).apply() }
    fun saveAutoDayNight(enabled: Boolean) { autoDayNight = enabled; prefs.edit().putBoolean("auto_day_night", enabled).apply() }
    fun saveDayThemeIndex(index: Int) { dayThemeIndex = index; prefs.edit().putInt("day_theme_index", index).apply() }

    // التحديات (يومي: 0، أسبوعي: 1، شهري: 2)
    fun getChallengeType(): Int = prefs.getInt("challenge_active_type", 0)
    fun saveChallengeType(type: Int) = prefs.edit().putInt("challenge_active_type", type).apply()
    fun getChallengeTarget(type: Int): Double {
        val defaultTarget = when (type) { 0 -> 30.0; 1 -> 200.0; else -> 800.0 }
        return prefs.getFloat("challenge_target_$type", defaultTarget.toFloat()).toDouble()
    }
    fun saveChallengeTarget(type: Int, target: Double) = prefs.edit().putFloat("challenge_target_$type", target.toFloat()).apply()

    // صدقة اليوم: نسبة من صافي الربح يختارها الكابتن (1% إلى 5%)
    fun getCharityPercent(): Int = prefs.getInt("charity_percent", 1).coerceIn(1, 5)
    fun saveCharityPercent(p: Int) { prefs.edit().putInt("charity_percent", p.coerceIn(1, 5)).apply(); version++ }
    fun getCharityPaid(day: String): Double = prefs.getFloat("charity_paid_$day", 0f).toDouble()
    fun saveCharityPaid(day: String, amount: Double) { prefs.edit().putFloat("charity_paid_$day", amount.toFloat()).apply(); version++ }
    /** مجموع الصدقات المسجلة في شهر (yyyy/MM) */
    fun getCharityMonthTotal(monthPrefix: String): Double =
        prefs.all.entries.filter { it.key.startsWith("charity_paid_$monthPrefix") }.sumOf { (it.value as? Float)?.toDouble() ?: 0.0 }

    // فقاعة المساعد: إخفاؤها حتى وقت معيّن (ضغط مطوّل)
    fun getBubbleHiddenUntil(): Long = prefs.getLong("bubble_hidden_until", 0L)
    fun saveBubbleHiddenUntil(t: Long) = prefs.edit().putLong("bubble_hidden_until", t).apply()

    // سؤال «بدأت رحلة؟» التلقائي: إيقافه حتى وقت معيّن
    fun getAutoStartMutedUntil(): Long = prefs.getLong("auto_start_muted_until", 0L)
    fun saveAutoStartMutedUntil(t: Long) = prefs.edit().putLong("auto_start_muted_until", t).apply()

    // تذكير ترخيص السيارة والتأمين (0 = غير محدد)
    fun getLicenseDate(): Long = prefs.getLong("vehicle_license_date", 0L)
    fun saveLicenseDate(t: Long) { prefs.edit().putLong("vehicle_license_date", t).apply(); version++ }
    fun getInsuranceDate(): Long = prefs.getLong("vehicle_insurance_date", 0L)
    fun saveInsuranceDate(t: Long) { prefs.edit().putLong("vehicle_insurance_date", t).apply(); version++ }

    // عبارات شاشة البداية: نتجنب تكرار آخر عبارة
    fun getLastQuoteIndex(): Int = prefs.getInt("last_quote_index", -1)
    fun saveLastQuoteIndex(i: Int) = prefs.edit().putInt("last_quote_index", i).apply()

    // عدادات الأذكار لليوم الحالي فقط
    fun getDhikrCount(key: String, day: String): Int = if (prefs.getString("dhikr_day", "") == day) prefs.getInt("dhikr_$key", 0) else 0
    fun saveDhikrCount(key: String, day: String, count: Int) {
        val e = prefs.edit()
        if (prefs.getString("dhikr_day", "") != day) {
            prefs.all.keys.filter { it.startsWith("dhikr_") && it != "dhikr_day" }.forEach { e.remove(it) }
            e.putString("dhikr_day", day)
        }
        e.putInt("dhikr_$key", count).apply()
    }

    // ------------------------------------------------------------------
    // الرحلات
    // ------------------------------------------------------------------
    @Synchronized
    fun getTrips(): List<TripRecord> = ensureTrips().toList()

    @Synchronized
    fun addTrip(trip: TripRecord) {
        val list = ensureTrips()
        if (list.any { it.id == trip.id }) return
        list.add(0, trip)
        writeTrips(list)
    }

    @Synchronized
    fun updateTripAddresses(id: Long, startAddress: String, endAddress: String) {
        val list = ensureTrips()
        val i = list.indexOfFirst { it.id == id }
        if (i < 0) return
        list[i] = list[i].copy(startAddress = startAddress, endAddress = endAddress)
        writeTrips(list)
    }

    @Synchronized
    fun deleteTrip(id: Long) {
        val list = ensureTrips()
        if (list.removeAll { it.id == id }) writeTrips(list)
    }

    @Synchronized
    fun clearTrips() {
        val list = ensureTrips()
        list.clear()
        writeTrips(list)
    }

    private fun ensureTrips(): MutableList<TripRecord> {
        tripsCache?.let { return it }
        val loaded: MutableList<TripRecord> = if (prefs.getBoolean("migrated_trips_json", false)) {
            readJsonArray(tripsFile)?.let { arr -> (0 until arr.length()).mapNotNull { tripFromJson(arr.optJSONObject(it)) }.toMutableList() }
                ?: mutableListOf()
        } else {
            // أول تشغيل بعد التحديث: نقل الرحلات القديمة
            val legacy = parseLegacyTrips()
            if (writeJsonArray(tripsFile, JSONArray().apply { legacy.forEach { put(tripToJson(it)) } })) {
                prefs.edit().putBoolean("migrated_trips_json", true).apply()
            }
            legacy.toMutableList()
        }
        tripsCache = loaded
        return loaded
    }

    private fun writeTrips(list: List<TripRecord>) {
        writeJsonArray(tripsFile, JSONArray().apply { list.forEach { put(tripToJson(it)) } })
        version++
    }

    private fun tripToJson(t: TripRecord) = JSONObject().apply {
        put("id", t.id); put("date", t.dateStr); put("start", t.startTimeStr); put("end", t.endTimeStr)
        put("km", t.distanceKm); put("sec", t.durationSeconds); put("fare", t.totalFare)
        put("base", t.appliedBaseFare); put("kmPrice", t.appliedKmPrice); put("minPrice", t.appliedMinPrice)
        put("startAddr", t.startAddress); put("endAddr", t.endAddress)
        put("tariff", t.tariffName); put("mult", t.multiplier); put("fixed", t.isFixedPrice)
        put("sLat", t.startLat); put("sLng", t.startLng); put("eLat", t.endLat); put("eLng", t.endLng)
        put("billSec", t.billedSeconds)
    }

    private fun tripFromJson(o: JSONObject?): TripRecord? {
        if (o == null || !o.has("id")) return null
        return TripRecord(
            id = o.optLong("id"),
            dateStr = o.optString("date"),
            startTimeStr = o.optString("start"),
            endTimeStr = o.optString("end"),
            distanceKm = o.optDouble("km", 0.0).finiteOr(0.0),
            durationSeconds = o.optLong("sec", 0L),
            totalFare = o.optDouble("fare", 0.0).finiteOr(0.0),
            appliedBaseFare = o.optDouble("base", 0.45).finiteOr(0.45),
            appliedKmPrice = o.optDouble("kmPrice", 0.25).finiteOr(0.25),
            appliedMinPrice = o.optDouble("minPrice", 0.05).finiteOr(0.05),
            startAddress = o.optString("startAddr", "غير محدد"),
            endAddress = o.optString("endAddr", "غير محدد"),
            tariffName = o.optString("tariff", ""),
            multiplier = o.optDouble("mult", 1.0).finiteOr(1.0),
            isFixedPrice = o.optBoolean("fixed", false),
            startLat = o.optDouble("sLat", 0.0).finiteOr(0.0),
            startLng = o.optDouble("sLng", 0.0).finiteOr(0.0),
            endLat = o.optDouble("eLat", 0.0).finiteOr(0.0),
            endLng = o.optDouble("eLng", 0.0).finiteOr(0.0),
            billedSeconds = o.optLong("billSec", -1L)
        )
    }

    /** نفس منطق القراءة في النسخة السابقة من التطبيق. */
    private fun parseLegacyTrips(): List<TripRecord> {
        val raw = prefs.getString("trips_data_v4", null) ?: prefs.getString("trips_data_v3", "") ?: ""
        if (raw.isBlank()) return emptyList()
        return raw.split(";;").mapNotNull { row ->
            val p = row.split("::")
            if (p.size >= 10) TripRecord(
                id = p[0].toLongOrNull() ?: 0L,
                dateStr = p[1],
                startTimeStr = p[2],
                endTimeStr = p[3],
                distanceKm = p[4].toDoubleOrNull() ?: 0.0,
                durationSeconds = p[5].toLongOrNull() ?: 0L,
                totalFare = p[6].toDoubleOrNull() ?: 0.0,
                appliedBaseFare = p[7].toDoubleOrNull() ?: 0.45,
                appliedKmPrice = p[8].toDoubleOrNull() ?: 0.25,
                appliedMinPrice = p[9].toDoubleOrNull() ?: 0.05,
                startAddress = p.getOrNull(10)?.takeIf { it.isNotBlank() } ?: "غير محدد",
                endAddress = p.getOrNull(11)?.takeIf { it.isNotBlank() } ?: "غير محدد"
            ) else null
        }.distinctBy { it.id }
    }

    // ------------------------------------------------------------------
    // المصاريف
    // ------------------------------------------------------------------
    @Synchronized
    fun getExpenses(): List<ExpenseRecord> = ensureExpenses().toList()

    @Synchronized
    fun addExpense(exp: ExpenseRecord) {
        val list = ensureExpenses()
        list.add(0, exp)
        writeExpenses(list)
    }

    @Synchronized
    fun deleteExpense(id: Long) {
        val list = ensureExpenses()
        if (list.removeAll { it.id == id }) writeExpenses(list)
    }

    private fun ensureExpenses(): MutableList<ExpenseRecord> {
        expensesCache?.let { return it }
        val loaded: MutableList<ExpenseRecord> = if (prefs.getBoolean("migrated_expenses_json", false)) {
            readJsonArray(expensesFile)?.let { arr ->
                (0 until arr.length()).mapNotNull { i ->
                    arr.optJSONObject(i)?.let { o ->
                        ExpenseRecord(o.optLong("id"), o.optString("date"), o.optString("cat"), o.optDouble("amount", 0.0).finiteOr(0.0), o.optString("note"))
                    }
                }.toMutableList()
            } ?: mutableListOf()
        } else {
            val raw = prefs.getString("expenses_data", "") ?: ""
            val legacy = if (raw.isBlank()) emptyList() else raw.split(";;").mapNotNull { row ->
                val p = row.split("::")
                if (p.size >= 5) ExpenseRecord(p[0].toLongOrNull() ?: 0L, p[1], p[2], p[3].toDoubleOrNull() ?: 0.0, p[4]) else null
            }
            if (writeJsonArray(expensesFile, expensesToJson(legacy))) {
                prefs.edit().putBoolean("migrated_expenses_json", true).apply()
            }
            legacy.toMutableList()
        }
        expensesCache = loaded
        return loaded
    }

    private fun expensesToJson(list: List<ExpenseRecord>) = JSONArray().apply {
        list.forEach { put(JSONObject().apply { put("id", it.id); put("date", it.dateStr); put("cat", it.category); put("amount", it.amount); put("note", it.note) }) }
    }

    private fun writeExpenses(list: List<ExpenseRecord>) {
        writeJsonArray(expensesFile, expensesToJson(list))
        version++
    }

    // ------------------------------------------------------------------
    // التعرفات المحفوظة
    // ------------------------------------------------------------------
    @Synchronized
    fun getPresets(): List<TariffPreset> = ensurePresets().toList()

    @Synchronized
    fun getSelectedPreset(): TariffPreset {
        val list = ensurePresets()
        return list.firstOrNull { it.id == selectedPresetId } ?: list.first()
    }

    @Synchronized
    fun savePreset(preset: TariffPreset) {
        if (isTier(preset.id)) return // الفئات الثابتة لا تُعدَّل
        val list = ensurePresets()
        val clean = preset.copy(
            name = preset.name.trim().ifBlank { "تعرفة" }.take(24),
            base = round3(preset.base.coerceIn(MIN_BASE, MAX_BASE)),
            km = round3(preset.km.coerceIn(MIN_KM, MAX_KM)),
            min = round3(preset.min.coerceIn(MIN_MIN, MAX_MIN))
        )
        val i = list.indexOfFirst { it.id == clean.id }
        if (i >= 0) list[i] = clean else list.add(clean)
        writePresets(list)
    }

    @Synchronized
    fun addPreset(name: String): TariffPreset {
        val base = getSelectedPreset()
        // رقم جديد لا يتعارض مع أرقام الفئات الثابتة
        val nextId = ((ensurePresets().filter { !isTier(it.id) }.maxOfOrNull { it.id } ?: 0L) + 1L).let { if (isTier(it)) TIER_COMFORT_ID + 1L else it }
        val created = TariffPreset(nextId, name, base.base, base.km, base.min)
        savePreset(created)
        return created
    }

    /** يحذف التعرفة ما دام هناك تعرفة أخرى على الأقل. */
    @Synchronized
    fun deletePreset(id: Long): Boolean {
        if (isTier(id)) return false // الفئات الثابتة لا تُحذف
        val list = ensurePresets()
        if (list.size <= 1) return false
        if (!list.removeAll { it.id == id }) return false
        writePresets(list)
        if (selectedPresetId == id) selectPreset(list.first().id)
        return true
    }

    private fun ensurePresets(): MutableList<TariffPreset> {
        presetsCache?.let { return it }
        var loaded: MutableList<TariffPreset> = mutableListOf()
        run {
            readJsonArray(presetsFile)?.let { arr ->
                loaded = (0 until arr.length()).mapNotNull { i ->
                    arr.optJSONObject(i)?.let { o ->
                        TariffPreset(o.optLong("id"), o.optString("name"), o.optDouble("base", 0.45).finiteOr(0.45), o.optDouble("km", 0.25).finiteOr(0.25), o.optDouble("min", 0.05).finiteOr(0.05))
                    }
                }.toMutableList()
            }
        }
        if (loaded.isEmpty()) {
            // التعرفة القديمة التي ضبطها الكابتن تصبح أول تعرفة محفوظة
            val oldBase = round3(prefs.getFloat("base_fare", 0.450f).toDouble())
            val oldKm = round3(prefs.getFloat("km_price", 0.250f).toDouble())
            val oldMin = round3(prefs.getFloat("min_price", 0.050f).toDouble())
            loaded = mutableListOf(
                TariffPreset(1L, "عادي", oldBase, oldKm, oldMin)
            )
            writeJsonArray(presetsFile, presetsToJson(loaded))
        }
        // الفئات الثابتة الثلاث تُضاف دائماً بأسعارها الصحيحة (لا تمس تعرفات الكابتن الخاصة)
        val tiersOk = TIERS.all { t -> loaded.any { it == t } }
        if (!tiersOk) {
            loaded.removeAll { isTier(it.id) }
            loaded.addAll(TIERS)
            writeJsonArray(presetsFile, presetsToJson(loaded))
        }
        presetsCache = loaded
        return loaded
    }

    private fun presetsToJson(list: List<TariffPreset>) = JSONArray().apply {
        list.forEach { put(JSONObject().apply { put("id", it.id); put("name", it.name); put("base", it.base); put("km", it.km); put("min", it.min) }) }
    }

    private fun writePresets(list: List<TariffPreset>) {
        writeJsonArray(presetsFile, presetsToJson(list))
        version++
    }

    // ------------------------------------------------------------------
    // الحفظ التلقائي: نسخة كاملة من بيانات الكابتن في ملف واحد
    // ------------------------------------------------------------------
    fun isAutoBackupOn(): Boolean = prefs.getBoolean("auto_backup_enabled", false)
    fun setAutoBackupOn(on: Boolean) {
        prefs.edit().putBoolean("auto_backup_enabled", on).putBoolean("auto_backup_asked", true).apply()
        version++
    }
    fun wasAutoBackupAsked(): Boolean = prefs.getBoolean("auto_backup_asked", false)
    fun markAutoBackupAsked() = prefs.edit().putBoolean("auto_backup_asked", true).apply()

    /** مفاتيح مؤقتة لا داعي لحفظها في النسخة. */
    private fun isSkippedKey(k: String): Boolean =
        k == "bubble_hidden_until" || k == "auto_start_muted_until" || k == "trips_data_v3" ||
            k == "trips_data_v4" || k == "expenses_data" || k.startsWith("migrated_")

    /** كل البيانات: الرحلات والمصاريف والتعرفات والإعدادات والتحدي. */
    @Synchronized
    fun exportAllJson(): JSONObject {
        val root = JSONObject()
        root.put("app", "rafeeq-darb")
        root.put("format", 1)
        root.put("created", System.currentTimeMillis())
        root.put("trips", JSONArray().apply { ensureTrips().forEach { put(tripToJson(it)) } })
        root.put("expenses", expensesToJson(ensureExpenses()))
        root.put("presets", presetsToJson(ensurePresets()))
        val p = JSONObject()
        for ((k, v) in prefs.all) {
            if (v == null || isSkippedKey(k)) continue
            val o = JSONObject()
            when (v) {
                is Boolean -> { o.put("t", "b"); o.put("v", v) }
                is Int -> { o.put("t", "i"); o.put("v", v) }
                is Long -> { o.put("t", "l"); o.put("v", v) }
                is Float -> { o.put("t", "f"); o.put("v", v.toDouble()) }
                is String -> { o.put("t", "s"); o.put("v", v) }
                else -> continue
            }
            p.put(k, o)
        }
        root.put("prefs", p)
        return root
    }

    /** يسترجع نسخة كاملة. يرجع عدد الرحلات المسترجعة، أو -1 إذا لم يكن الملف نسخة من التطبيق. */
    @Synchronized
    fun importAllJson(root: JSONObject): Int {
        if (root.optString("app") != "rafeeq-darb") return -1
        val trips = root.optJSONArray("trips") ?: JSONArray()
        val expenses = root.optJSONArray("expenses") ?: JSONArray()
        val presets = root.optJSONArray("presets")
        val e = prefs.edit()
        val p = root.optJSONObject("prefs")
        if (p != null) {
            val keys = p.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                if (isSkippedKey(k)) continue
                val o = p.optJSONObject(k) ?: continue
                when (o.optString("t")) {
                    "b" -> e.putBoolean(k, o.optBoolean("v"))
                    "i" -> e.putInt(k, o.optInt("v"))
                    "l" -> e.putLong(k, o.optLong("v"))
                    "f" -> e.putFloat(k, o.optDouble("v", 0.0).toFloat())
                    "s" -> e.putString(k, o.optString("v"))
                }
            }
        }
        e.putBoolean("migrated_trips_json", true).putBoolean("migrated_expenses_json", true)
        e.putBoolean("auto_backup_asked", true)
        e.commit()
        writeJsonArray(tripsFile, trips)
        writeJsonArray(expensesFile, expenses)
        if (presets != null && presets.length() > 0) writeJsonArray(presetsFile, presets)
        tripsCache = null
        expensesCache = null
        presetsCache = null
        language = prefs.getString("app_lang", "ar") ?: "ar"
        themeIndex = prefs.getInt("theme_index", 0)
        soundEnabled = prefs.getBoolean("sound_enabled", true)
        selectedPresetId = prefs.getLong("selected_preset_id", 1L)
        autoDayNight = prefs.getBoolean("auto_day_night", false)
        dayThemeIndex = prefs.getInt("day_theme_index", AppTheme.DAYLIGHT_PRO.ordinal)
        version++
        return trips.length()
    }

    // ------------------------------------------------------------------
    // قراءة وكتابة الملفات
    // ------------------------------------------------------------------
    private fun readJsonArray(file: AtomicFile): JSONArray? {
        return try {
            JSONArray(String(file.readFully(), Charsets.UTF_8))
        } catch (_: java.io.FileNotFoundException) {
            JSONArray()
        } catch (_: Exception) {
            // الملف تالف: نحتفظ بنسخة منه بدل الكتابة فوقه، ونكمل بقائمة فارغة
            try {
                val bad = file.baseFile
                bad.copyTo(File(bad.parentFile, bad.name + ".corrupt-" + System.currentTimeMillis()), overwrite = true)
            } catch (_: Exception) { }
            null
        }
    }

    private fun writeJsonArray(file: AtomicFile, arr: JSONArray): Boolean {
        val out = try { file.startWrite() } catch (_: Exception) { return false }
        return try {
            out.write(arr.toString().toByteArray(Charsets.UTF_8))
            file.finishWrite(out)
            true
        } catch (_: Exception) {
            file.failWrite(out)
            false
        }
    }
}

private fun Double.finiteOr(fallback: Double): Double = if (this.isNaN() || this.isInfinite()) fallback else this
