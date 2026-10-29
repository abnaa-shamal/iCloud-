package jo.shajaraone.app.hasbat

import jo.shajaraone.app.R
import jo.shajaraone.app.MainActivity

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.util.Calendar
import java.util.Locale

// ======================================================================
// إيصال الراكب: صورة مرتبة تُرسل عبر واتساب أو أي تطبيق
// ======================================================================
object ReceiptHelper {

    /** يرسم الإيصال ويفتح نافذة المشاركة. لا يُسقط التطبيق أبداً إذا فشل. */
    fun share(context: Context, trip: TripRecord, isEnglish: Boolean) {
        try {
            val bmp = draw(trip, isEnglish)
            val dir = File(context.cacheDir, "exports").apply { mkdirs() }
            val file = File(dir, "receipt_${trip.id}.png")
            FileOutputStream(file).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bmp.recycle()
            val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(send, if (isEnglish) "Send receipt" else "إرسال الإيصال")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
        } catch (_: Throwable) {}
    }

    private fun draw(t: TripRecord, en: Boolean): Bitmap {
        val w = 1080
        val h = 1560
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val gold = 0xFFF5C451.toInt()
        val white = 0xFFF5F0E6.toInt()
        val grey = 0xFFA89F8C.toInt()

        c.drawColor(0xFF0B0B0C.toInt())
        val card = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF17140E.toInt() }
        c.drawRoundRect(RectF(50f, 50f, w - 50f, h - 50f), 48f, 48f, card)
        val border = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = gold; style = Paint.Style.STROKE; strokeWidth = 4f }
        c.drawRoundRect(RectF(50f, 50f, w - 50f, h - 50f), 48f, 48f, border)

        val center = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
        center.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        center.color = gold; center.textSize = 64f
        c.drawText(if (en) "Captain's Companion" else "رفيق دربك للكابتن", w / 2f, 170f, center)
        center.typeface = Typeface.DEFAULT
        center.color = grey; center.textSize = 38f
        c.drawText(if (en) "Trip receipt" else "إيصال رحلة", w / 2f, 230f, center)

        // الأجرة
        center.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        center.color = gold; center.textSize = 150f
        c.drawText(Fmt.money(t.totalFare), w / 2f, 420f, center)
        center.typeface = Typeface.DEFAULT
        center.color = white; center.textSize = 42f
        c.drawText(if (en) "Jordanian Dinar" else "دينار أردني", w / 2f, 480f, center)

        val line = Paint().apply { color = 0x33F5C451; strokeWidth = 2f }
        c.drawLine(110f, 540f, w - 110f, 540f, line)

        // التفاصيل: العنوان يميناً والقيمة يساراً (أو العكس بالإنجليزية)
        val label = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = grey; textSize = 40f }
        val value = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = white; textSize = 40f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD) }
        label.textAlign = if (en) Paint.Align.LEFT else Paint.Align.RIGHT
        value.textAlign = if (en) Paint.Align.RIGHT else Paint.Align.LEFT
        val labelX = if (en) 120f else w - 120f
        val valueX = if (en) w - 120f else 120f

        val rows = mutableListOf(
            (if (en) "Date" else "التاريخ") to t.dateStr,
            (if (en) "From" else "من") to shorten(t.startAddress),
            (if (en) "To" else "إلى") to shorten(t.endAddress),
            (if (en) "Start" else "وقت الانطلاق") to t.startTimeStr,
            (if (en) "Arrival" else "وقت الوصول") to t.endTimeStr,
            (if (en) "Distance" else "المسافة") to "${Fmt.km(t.distanceKm)} ${if (en) "km" else "كم"}",
            (if (en) "Duration" else "المدة") to Fmt.clock(t.durationSeconds)
        )
        if (t.isFixedPrice) rows.add((if (en) "Price" else "السعر") to (if (en) "Agreed fixed price" else "سعر متفق عليه"))

        var y = 620f
        rows.forEach { (k, v) ->
            c.drawText(k, labelX, y, label)
            c.drawText(v, valueX, y, value)
            y += 92f
        }

        c.drawLine(110f, y - 30f, w - 110f, y - 30f, line)
        center.color = gold; center.textSize = 44f
        center.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        c.drawText(if (en) "Thank you for riding with us 🌿" else "شكراً لاختياركم، رافقتكم السلامة 🌿", w / 2f, h - 170f, center)
        center.typeface = Typeface.DEFAULT
        center.color = grey; center.textSize = 30f
        c.drawText("Abo Tala", w / 2f, h - 110f, center)
        return bmp
    }

    private fun shorten(s: String): String = if (s.length > 34) s.take(33) + "…" else s
}

// ======================================================================
// تذكير ترخيص السيارة والتأمين
// ======================================================================
object VehicleReminder {
    private const val DAY_MS = 24L * 60L * 60L * 1000L

    /** عدد الأيام المتبقية (سالب = انتهى)، أو null إذا لم يُحدد التاريخ. */
    fun daysLeft(dateMillis: Long, now: Long = System.currentTimeMillis()): Long? {
        if (dateMillis <= 0L) return null
        val today = startOfDay(now)
        val due = startOfDay(dateMillis)
        return (due - today) / DAY_MS
    }

    private fun startOfDay(t: Long): Long = Calendar.getInstance().apply {
        timeInMillis = t
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    fun describe(days: Long, en: Boolean): String = when {
        days < 0L -> if (en) "expired ${-days} day(s) ago" else "انتهى منذ ${-days} يوم"
        days == 0L -> if (en) "ends today" else "ينتهي اليوم"
        days == 1L -> if (en) "ends tomorrow" else "ينتهي غداً"
        else -> if (en) "ends in $days days" else "ينتهي بعد $days أيام"
    }

    /** نص التنبيه في الرئيسية إذا بقي أسبوع أو أقل، وإلا null. */
    fun dueText(storage: AppDataManager, en: Boolean): String? {
        val parts = mutableListOf<String>()
        daysLeft(storage.getLicenseDate())?.let { if (it <= 7L) parts.add((if (en) "Car license " else "ترخيص السيارة ") + describe(it, en)) }
        daysLeft(storage.getInsuranceDate())?.let { if (it <= 7L) parts.add((if (en) "Insurance " else "التأمين ") + describe(it, en)) }
        return if (parts.isEmpty()) null else parts.joinToString(" • ")
    }

    fun formatDate(t: Long): String = if (t <= 0L) "—" else {
        val cal = Calendar.getInstance().apply { timeInMillis = t }
        String.format(Locale.US, "%04d/%02d/%02d", cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH))
    }
}

// ======================================================================
// أفضل أوقاتك: تحليل الدخل حسب اليوم والفترة
// ======================================================================
object BestTimes {
    data class Bucket(val label: String, val income: Double, val trips: Int)

    val dayNamesAr = listOf("الأحد", "الاثنين", "الثلاثاء", "الأربعاء", "الخميس", "الجمعة", "السبت")
    val dayNamesEn = listOf("Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday")

    /** الفترات: 6-10 صباحاً، 10-2 ظهراً، 2-6 عصراً، 6-10 مساءً، 10-2 ليلاً، 2-6 فجراً */
    fun periodLabel(i: Int, en: Boolean): String = if (en) listOf("6–10 AM", "10 AM–2 PM", "2–6 PM", "6–10 PM", "10 PM–2 AM", "2–6 AM")[i]
        else listOf("٦–١٠ صباحاً", "١٠ص–٢ ظهراً", "٢–٦ عصراً", "٦–١٠ مساءً", "١٠م–٢ ليلاً", "٢–٦ فجراً")[i]

    fun periodIndex(hour: Int): Int = when (hour) {
        in 6..9 -> 0
        in 10..13 -> 1
        in 14..17 -> 2
        in 18..21 -> 3
        22, 23, 0, 1 -> 4
        else -> 5
    }

    /** يوم الأسبوع (0 = الأحد) من تاريخ بالشكل yyyy/MM/dd */
    fun weekday(dateStr: String): Int? {
        val p = dateStr.split("/")
        if (p.size != 3) return null
        val y = p[0].toIntOrNull() ?: return null
        val m = p[1].toIntOrNull() ?: return null
        val d = p[2].toIntOrNull() ?: return null
        val cal = Calendar.getInstance().apply { clear(); set(y, m - 1, d) }
        return cal.get(Calendar.DAY_OF_WEEK) - 1
    }

    fun byDay(trips: List<TripRecord>, en: Boolean): List<Bucket> {
        val income = DoubleArray(7)
        val count = IntArray(7)
        trips.forEach { t -> weekday(t.dateStr)?.let { income[it] += t.totalFare; count[it]++ } }
        val names = if (en) dayNamesEn else dayNamesAr
        // نبدأ بالسبت
        return listOf(6, 0, 1, 2, 3, 4, 5).map { Bucket(names[it], income[it], count[it]) }
    }

    fun byPeriod(trips: List<TripRecord>, en: Boolean): List<Bucket> {
        val income = DoubleArray(6)
        val count = IntArray(6)
        trips.forEach { t ->
            val hour = t.startTimeStr.take(2).toIntOrNull()
            if (hour != null) { val i = periodIndex(hour); income[i] += t.totalFare; count[i]++ }
        }
        return (0 until 6).map { Bucket(periodLabel(it, en), income[it], count[it]) }
    }

    /** متوسط الدخل لكل ساعة قيادة فعلية (من مدة الرحلات). */
    fun incomePerDrivingHour(trips: List<TripRecord>): Double {
        val hours = trips.sumOf { it.durationSeconds } / 3600.0
        return if (hours > 0.05) trips.sumOf { it.totalFare } / hours else 0.0
    }
}
