package com.shajara.rides

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

/** Valid Jordanian mobile: 07XXXXXXXX */
fun isValidPhone(p: String) = Regex("^07[789]\\d{7}$").matches(p.trim())

/** Firebase Auth needs an email; the user only ever sees the phone number. */
/** Hidden login email. Different per app, so the rider account and the captain account of one number are separate. */
fun phoneToLoginEmail(phone: String) =
    "${phone.trim()}@${if (IS_CAPTAIN_APP) "captain.mashwarak.app" else "mashwarak.app"}"

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

private val arLocale = Locale.forLanguageTag("ar-JO")

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
    private val sp = context.applicationContext.getSharedPreferences("mashwarak", Context.MODE_PRIVATE)

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
