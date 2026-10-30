package jo.shajaraone.app.hasbat

import jo.shajaraone.app.R
import jo.shajaraone.app.MainActivity

import android.content.Context
import android.location.Geocoder
import android.view.HapticFeedbackConstants
import android.view.SoundEffectConstants
import android.view.View
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.tan

// ======================================================================
// إدخال الأرقام: يقبل الأرقام العربية (١٢٣) والفاصلة العربية والإنجليزية
// ======================================================================
object NumberInput {
    fun normalize(text: String): String {
        val sb = StringBuilder()
        for (ch in text.trim()) {
            when (ch) {
                in '٠'..'٩' -> sb.append('0' + (ch - '٠'))
                in '۰'..'۹' -> sb.append('0' + (ch - '۰'))
                '٫', ',', '،' -> sb.append('.')
                ' ', '‏', '‎' -> {}
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }

    fun parse(text: String): Double? {
        val v = normalize(text).toDoubleOrNull() ?: return null
        return if (v.isNaN() || v.isInfinite()) null else v
    }
}

// ======================================================================
// تحويل الإحداثيات لعنوان. دالة حاجبة: تُستدعى فقط من خيط خلفي (Dispatchers.IO).
// ======================================================================
object LocationAddressHelper {
    fun coordinates(lat: Double, lng: Double): String = String.format(Locale.US, "%.5f, %.5f", lat, lng)

    @Suppress("DEPRECATION")
    fun resolveAddressBlocking(context: Context, lat: Double, lng: Double, isEnglish: Boolean): String {
        if (lat == 0.0 && lng == 0.0) {
            return if (isEnglish) "Location not detected" else "لم يتم رصد الموقع"
        }
        if (!Geocoder.isPresent()) return coordinates(lat, lng)
        return try {
            val geocoder = Geocoder(context, if (isEnglish) Locale.US else Locale.forLanguageTag("ar"))
            val list = geocoder.getFromLocation(lat, lng, 1)
            if (!list.isNullOrEmpty()) {
                val a = list[0]
                val parts = listOfNotNull(a.subLocality, a.thoroughfare, a.locality, a.adminArea)
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .distinct()
                if (parts.isNotEmpty()) parts.joinToString(" - ") else a.getAddressLine(0) ?: coordinates(lat, lng)
            } else coordinates(lat, lng)
        } catch (_: Exception) {
            coordinates(lat, lng)
        }
    }
}

// ======================================================================
// صوت واهتزاز النقر
// ======================================================================
object SoundEffectsHelper {
    /** كل الأصوات صارت من AppFeedback (النغمات الجديدة فقط). صوت اللمس يُشغَّل مرة وحدة لكل لمسة من MainActivity. */
    fun playClick(view: View, soundEnabled: Boolean) { /* اللمس مغطّى عالمياً */ }

    /** صوت زر «بدء رحلة». */
    fun playTripStart(view: View, soundEnabled: Boolean) {
        if (soundEnabled) jo.shajaraone.app.AppFeedback.newTrip(view.context)
    }

    /** صوت إنهاء الرحلة. */
    fun playTripFinish(view: View, soundEnabled: Boolean) {
        if (soundEnabled) jo.shajaraone.app.AppFeedback.system(view.context)
    }

    /** نغمة افتتاح التطبيق. */
    fun playStartup(soundEnabled: Boolean) { /* الترحيب من AppFeedback.open */ }
}

// ======================================================================
// القبلة وأوقات الصلاة
// ======================================================================
object IslamicCalculationHelper {
    private const val KAABA_LAT = 21.422487
    private const val KAABA_LNG = 39.826206
    const val DEFAULT_LAT = 31.95   // عمّان عند عدم توفر GPS
    const val DEFAULT_LNG = 35.93

    fun getQiblaBearing(lat: Double, lng: Double): Float {
        val uLat = if (lat == 0.0 && lng == 0.0) DEFAULT_LAT else lat
        val uLng = if (lat == 0.0 && lng == 0.0) DEFAULT_LNG else lng
        val phiK = Math.toRadians(KAABA_LAT); val lambdaK = Math.toRadians(KAABA_LNG)
        val phi = Math.toRadians(uLat)
        val lambda = Math.toRadians(uLng)
        val y = sin(lambdaK - lambda)
        val x = cos(phi) * tan(phiK) - sin(phi) * cos(lambdaK - lambda)
        return ((Math.toDegrees(atan2(y, x)) + 360.0) % 360.0).toFloat()
    }

    fun directionName(bearing: Float, isEnglish: Boolean): String {
        val ar = listOf("شمال", "شمال شرق", "شرق", "جنوب شرق", "جنوب", "جنوب غرب", "غرب", "شمال غرب")
        val en = listOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
        val idx = (((bearing % 360f) + 360f) % 360f / 45f).roundToInt() % 8
        return if (isEnglish) en[idx] else ar[idx]
    }

    // ---- خوارزمية حساب المواقيت (PrayTimes): الفجر والعشاء 18°، العصر شافعي ----
    private fun dsin(d: Double) = sin(Math.toRadians(d))
    private fun dcos(d: Double) = cos(Math.toRadians(d))
    private fun dtan(d: Double) = tan(Math.toRadians(d))
    private fun darcsin(x: Double) = Math.toDegrees(asin(x.coerceIn(-1.0, 1.0)))
    private fun darccos(x: Double) = Math.toDegrees(acos(x.coerceIn(-1.0, 1.0)))
    private fun darctan2(y: Double, x: Double) = Math.toDegrees(atan2(y, x))
    private fun darccot(x: Double) = Math.toDegrees(atan(1.0 / x))
    private fun fixAngle(a: Double) = a - 360.0 * floor(a / 360.0)
    private fun fixHour(a: Double) = a - 24.0 * floor(a / 24.0)

    private fun julian(year: Int, month: Int, day: Int): Double {
        var y = year; var m = month
        if (m <= 2) { y -= 1; m += 12 }
        val a = floor(y / 100.0)
        val b = 2 - a + floor(a / 4.0)
        return floor(365.25 * (y + 4716)) + floor(30.6001 * (m + 1)) + day + b - 1524.5
    }

    /** يعيد (الميل الشمسي بالدرجات، معادلة الزمن بالساعات). */
    private fun sunPosition(jd: Double): Pair<Double, Double> {
        val d = jd - 2451545.0
        val g = fixAngle(357.529 + 0.98560028 * d)
        val q = fixAngle(280.459 + 0.98564736 * d)
        val l = fixAngle(q + 1.915 * dsin(g) + 0.020 * dsin(2 * g))
        val e = 23.439 - 0.00000036 * d
        val ra = darctan2(dcos(e) * dsin(l), dcos(l)) / 15.0
        val eqt = q / 15.0 - fixHour(ra)
        val decl = darcsin(dsin(e) * dsin(l))
        return decl to eqt
    }

    /** أوقات اليوم بالترتيب: الفجر، الشروق، الظهر، العصر، المغرب، العشاء (بالدقائق منذ منتصف الليل). */
    fun prayerMinutes(lat: Double, lng: Double, cal: Calendar = Calendar.getInstance()): List<Int> {
        val uLat = if (lat == 0.0 && lng == 0.0) DEFAULT_LAT else lat
        val uLng = if (lat == 0.0 && lng == 0.0) DEFAULT_LNG else lng
        val tz = TimeZone.getDefault().getOffset(cal.timeInMillis) / 3_600_000.0
        val jDate = julian(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH)) - uLng / (15.0 * 24.0)

        fun midDay(t: Double) = fixHour(12.0 - sunPosition(jDate + t).second)
        fun sunAngleTime(angle: Double, t: Double, beforeNoon: Boolean): Double {
            val decl = sunPosition(jDate + t).first
            val noon = midDay(t)
            val dt = darccos((-dsin(angle) - dsin(decl) * dsin(uLat)) / (dcos(decl) * dcos(uLat))) / 15.0
            return if (beforeNoon) noon - dt else noon + dt
        }
        fun asrTime(t: Double): Double {
            val decl = sunPosition(jDate + t).first
            val angle = -darccot(1.0 + dtan(abs(uLat - decl)))
            return sunAngleTime(angle, t, false)
        }

        val raw = listOf(
            sunAngleTime(18.0, 5.0 / 24.0, true),
            sunAngleTime(0.833, 6.0 / 24.0, true),
            midDay(12.0 / 24.0) + 2.0 / 60.0, // احتياط دقيقتين للظهر كما في النسخة السابقة
            asrTime(13.0 / 24.0),
            sunAngleTime(0.833, 18.0 / 24.0, false),
            sunAngleTime(18.0, 18.0 / 24.0, false)
        )
        val adjust = tz - uLng / 15.0
        return raw.map { h -> (((h + adjust) * 60.0).roundToInt() % 1440 + 1440) % 1440 }
    }

    fun formatMinutes(totalMinutes: Int, isEnglish: Boolean): String {
        val hr = totalMinutes / 60
        val min = totalMinutes % 60
        val suffix = if (hr < 12) (if (isEnglish) "AM" else "ص") else (if (isEnglish) "PM" else "م")
        val dh = if (hr == 0) 12 else if (hr > 12) hr - 12 else hr
        return String.format(Locale.US, "%02d:%02d %s", dh, min, suffix)
    }

    val prayerNamesAr = listOf("الفجر", "الشروق", "الظهر", "العصر", "المغرب", "العشاء")
    val prayerNamesEn = listOf("Fajr", "Sunrise", "Dhuhr", "Asr", "Maghrib", "Isha")
    val prayerIcons = listOf("🌅", "☀️", "🏙️", "🌤️", "🌇", "🌙")
}

// ======================================================================
// عبارات شاشة البداية
// ======================================================================
object MotivationalQuotes {
    val all = listOf(
        "اسعَ بقلبٍ مطمئن، فما كُتب لك سيأتيك، وما عليك إلا أن تُحسن السعي.",
        "قد لا ترى ثمرة تعبك اليوم، لكن لا تستهِن ببذرة تزرعها بإخلاص كل يوم.",
        "الرزق ليس سباقًا مع الآخرين؛ امضِ في طريقك، فلك من الحياة نصيبك.",
        "ليس كل يوم سهلًا، لكن كل يوم تمنح فيه عملك حقه هو خطوة تستحق أن تفخر بها.",
        "توكّل على الله، وخذ بالأسباب، وامضِ نحو هدفك بقلبٍ لا يعرف اليأس.",
        "خلف كل إنسان يواصل الطريق رغم تعبه، حكاية صبر لا يعرف تفاصيلها أحد.",
        "لا تجعل قسوة يومٍ واحد تُنسيك كم مرة تجاوزت الصعاب ووصلت إلى هنا.",
        "القوة ليست ألّا تتعب؛ القوة أن تعرف متى تستريح، ثم تعود وأنت أكثر ثباتًا.",
        "قد لا يصفّق لك أحد وأنت تحاول، لكن يكفيك أنك لم تتخلَّ عن حلمك.",
        "حين يثقل الطريق، تذكّر السبب الذي جعلك تبدأ، وامنح نفسك فرصة أخرى.",
        "لا تحتاج إلى أن تسبق الجميع؛ يكفي أن تتقدم كل يوم خطوة عن الأمس.",
        "الأحلام الكبيرة لا تتحقق بقفزة واحدة، بل بخطوات صغيرة لا تتوقف.",
        "اجعل عملك شاهدًا على اجتهادك، وأخلاقك شاهدًا على أصلك، وصبرك شاهدًا على قوتك.",
        "لا تقِس نجاحك بما تملكه اليوم، بل بما تتعلمه وتبنيه وتصبر عليه.",
        "كل بداية متواضعة تحمل احتمالًا عظيمًا، فلا تحتقر أول الطريق.",
        "هناك من ينتظر عودتك سالمًا، فلا تجعل استعجال الوصول أغلى من سلامتك.",
        "قد تكون رحلتك عادية في نظر الناس، لكنها جزء من قصة كبيرة تبنيها لأجل من تحب.",
        "كرامتك في أمانتك، وقيمتك في أخلاقك، ونجاحك الحقيقي أن تنام مرتاح الضمير.",
        "أجمل ما تقدمه لأهلك ليس المال وحده، بل قلب حاضر، ووجه مطمئن، وعودة سالمة.",
        "عامل كل إنسان بلطف؛ فقد تكون ابتسامتك الشيء الجميل الوحيد في يومه.",
        "لا تحكم على مستقبلك من صعوبة حاضرك؛ فالأيام تتبدل، والأبواب تُفتح، والسعي لا يضيع.",
        "كل صباح فرصة جديدة لتصلح ما استطعت، وتبدأ من جديد، وتقترب من الحياة التي تريدها.",
        "ليس مطلوبًا منك أن تحمل همّ العمر كله اليوم؛ يكفي أن تؤدي خطوة اليوم بإتقان.",
        "إذا تأخر ما تتمناه، فلا تجعل الانتظار يسرق منك جمال ما تملكه الآن.",
        "اختم يومك وأنت تعلم أنك سعيت بما استطعت، وتوكّلت على الله فيما لم تستطع."
    )

    /** يختار عبارة عشوائية مختلفة عن آخر عبارة ظهرت. */
    fun pickNext(storage: AppDataManager): String {
        val last = storage.getLastQuoteIndex()
        val candidates = all.indices.filter { it != last }.ifEmpty { all.indices.toList() }
        val idx = candidates.random()
        storage.saveLastQuoteIndex(idx)
        return all[idx]
    }
}

// ======================================================================
// تنسيق ونصوص مشتركة
// ======================================================================
object Fmt {
    fun money(v: Double): String = String.format(Locale.US, "%.2f", v)
    fun km(v: Double): String = String.format(Locale.US, "%.2f", v)
    fun rate(v: Double): String = String.format(Locale.US, "%.3f", v)
    fun clock(totalSeconds: Long): String {
        val s = totalSeconds.coerceAtLeast(0L)
        return String.format(Locale.US, "%02d:%02d:%02d", s / 3600, (s % 3600) / 60, s % 60)
    }
    fun multiplier(m: Double): String =
        if (m == floor(m)) "×${m.toInt()}" else "×" + String.format(Locale.US, "%.2f", m).trimEnd('0').trimEnd('.')

    /** ترجمة فئات المصاريف (تُخزَّن بالعربية). */
    fun expenseCategory(cat: String, isEnglish: Boolean): String {
        if (!isEnglish) return cat
        return when (cat) {
            "بنزين" -> "Fuel"; "زيت" -> "Oil"; "غسيل" -> "Car wash"; "صيانة" -> "Maintenance"
            "أقساط" -> "Installments"; "أخرى" -> "Other"; else -> cat
        }
    }
}
