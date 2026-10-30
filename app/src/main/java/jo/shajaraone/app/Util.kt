package jo.shajaraone.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

fun toast(ctx: Context, msg: String) = Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()

// ===== Phone =====

/**
 * Turns every accepted way of writing a Jordanian mobile into the one stored form 07XXXXXXXX:
 * 079xxxxxxx, 79xxxxxxx, 962 79xxxxxxx, 00962 79xxxxxxx (also with a leading +, spaces or dashes).
 * Anything else is returned as plain digits so validation fails on it.
 */
fun normalizePhone(p: String): String {
    var d = p.filter { it.isDigit() }
    if (d.startsWith("00962")) d = d.drop(5) else if (d.startsWith("962")) d = d.drop(3)
    if (d.startsWith("7") && d.length == 9) d = "0$d"
    return d
}

/** Valid Jordanian mobile (077, 078 or 079) in any accepted writing, see [normalizePhone]. */
fun isValidPhone(p: String) = Regex("^07[789]\\d{7}$").matches(normalizePhone(p))

/** Firebase Auth needs an email; the user only ever sees the phone number. */
/** Hidden login email. Different per app, so the rider account and the captain account of one number are separate. */
fun phoneToLoginEmail(phone: String) =
    "${normalizePhone(phone)}@${if (IS_CAPTAIN_APP) "captain.shajaraone.app" else "shajaraone.app"}"

/** 07XXXXXXXX -> 9627XXXXXXXX for WhatsApp links */
fun intlPhone(p: String): String {
    val d = p.filter { it.isDigit() }
    return when {
        d.startsWith("962") -> d
        d.startsWith("0") -> "962" + d.drop(1)
        else -> "962$d"
    }
}

fun callPhone(ctx: Context, phone: String) {
    try {
        ctx.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${phone.trim()}")))
    } catch (e: Exception) {
        toast(ctx, "ما في تطبيق اتصال")
    }
}

fun openWhatsApp(ctx: Context, phone: String, message: String) {
    val url = "https://wa.me/${intlPhone(phone)}?text=${Uri.encode(message)}"
    try {
        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (e: Exception) {
        toast(ctx, "ما قدرت أفتح واتساب")
    }
}

// ===== Text =====

fun seatsText(n: Long) = when (n) {
    1L -> "مقعد واحد"
    2L -> "مقعدين"
    in 3L..10L -> "$n مقاعد"
    else -> "$n مقعد"
}

fun priceText(p: Double?): String = when {
    p == null -> "حسب الاتفاق"
    p % 1.0 == 0.0 -> "${p.toLong()} دينار"
    else -> "$p دينار"
}

// ===== Time =====

private val arLocale = Locale.forLanguageTag("ar-JO-u-nu-latn")

private fun sameDay(a: Calendar, b: Calendar) =
    a.get(Calendar.YEAR) == b.get(Calendar.YEAR) && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)

/** «اليوم»، «بكرا» or the weekday name. */
fun dayName(millis: Long): String {
    val c = Calendar.getInstance().apply { timeInMillis = millis }
    val today = Calendar.getInstance()
    val tomorrow = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 1) }
    return when {
        sameDay(c, today) -> "اليوم"
        sameDay(c, tomorrow) -> "بكرا"
        else -> SimpleDateFormat("EEEE d/M", arLocale).format(Date(millis))
    }
}

fun clockText(millis: Long): String = SimpleDateFormat("h:mm a", arLocale).format(Date(millis))

/** e.g. «اليوم 7:30 ص» */
fun timeText(millis: Long) = "${dayName(millis)} ${clockText(millis)}"

/** Start of the day [offset] days from today. */
fun dayStart(offset: Int): Long = Calendar.getInstance().apply {
    add(Calendar.DAY_OF_YEAR, offset)
    set(Calendar.HOUR_OF_DAY, 0)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
}.timeInMillis

fun departMillis(dayOffset: Int, hour: Int, minute: Int): Long =
    dayStart(dayOffset) + hour * 3_600_000L + minute * 60_000L

/** Which day offset (0 = today) a time falls on. */
fun dayOffsetOf(millis: Long): Int = ((millis - dayStart(0)) / 86_400_000L).toInt()

// ===== Things remembered on this phone only =====

class Prefs(context: Context) {
    private val sp = context.applicationContext.getSharedPreferences("shajaraone", Context.MODE_PRIVATE)

    fun onboarded(role: String) = sp.getBoolean("onboarded_$role", false)
    fun setOnboarded(role: String, v: Boolean) = sp.edit().putBoolean("onboarded_$role", v).apply()

    /** Newest booking change the captain was already alerted about. */
    var lastAlertAt: Long
        get() = sp.getLong("lastAlertAt", 0)
        set(v) = sp.edit().putLong("lastAlertAt", v).apply()

    /** Uid of the signed-in user the background check should watch (set on sign-in). */
    var watchCaptainUid: String
        get() = sp.getString("watchCaptainUid", "") ?: ""
        set(v) = sp.edit().putString("watchCaptainUid", v).apply()
}

/** e.g. «الأحد 1 تشرين الثاني» */
fun dateLongText(millis: Long): String = SimpleDateFormat("EEEE d MMMM", arLocale).format(Date(millis))

/** Day offset (0 = today) of a date chosen in the calendar, which reports UTC midnight of the picked day. */
fun dayOffsetFromPicker(utcMillis: Long): Int {
    val u = Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).apply { timeInMillis = utcMillis }
    val local = Calendar.getInstance().apply {
        clear()
        set(u.get(Calendar.YEAR), u.get(Calendar.MONTH), u.get(Calendar.DAY_OF_MONTH), 0, 0, 0)
    }.timeInMillis
    return Math.round((local - dayStart(0)) / 86_400_000.0).toInt()
}

/** The calendar's UTC midnight for the local date that is [offset] days from today. */
fun pickerMillisOfOffset(offset: Int): Long {
    val l = Calendar.getInstance().apply { timeInMillis = dayStart(offset) }
    return Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).apply {
        clear()
        set(l.get(Calendar.YEAR), l.get(Calendar.MONTH), l.get(Calendar.DAY_OF_MONTH), 0, 0, 0)
    }.timeInMillis
}

// ----- Optional availability window, stored in the trip's existing «note» field as "@T:from,to" (minutes from midnight, -1 = not set).
// No new Firestore fields are needed. departAt then holds the END of the window (or the end of the day), so the trip expires by itself.
fun tripWindow(note: String): Pair<Int, Int>? {
    if (!note.startsWith("@T:")) return null
    val p = note.removePrefix("@T:").substringBefore(" ").split(",")
    return (p.getOrNull(0)?.toIntOrNull() ?: -1) to (p.getOrNull(1)?.toIntOrNull() ?: -1)
}

/** The captain chose to carry Mafraq riders on this trip (flag «@M» stored after the window in the note). */
fun takesMafraq(t: Trip) = t.note.contains("@M")

/** Does the trip pass Mafraq for a Mafraq rider? Opted in by the captain, or any north → south trip on طريق الزرقاء (the captain still accepts or rejects). */
fun passesMafraq(t: Trip) = takesMafraq(t) || (t.road == "طريق الزرقاء" && t.from in northPlaces && t.from != "المفرق")

private fun minText(m: Int) = clockText(dayStart(0) + m * 60_000L)

/** Big time label of a trip: a single clock time, a window, or «وقت مفتوح». */
fun tripTimeLabel(note: String, departAt: Long): String {
    val w = tripWindow(note) ?: return clockText(departAt)
    return when {
        w.first >= 0 && w.second >= 0 -> "${minText(w.first)} – ${minText(w.second)}"
        w.first >= 0 -> "من ${minText(w.first)}"
        w.second >= 0 -> "حتى ${minText(w.second)}"
        else -> "وقت مفتوح"
    }
}
