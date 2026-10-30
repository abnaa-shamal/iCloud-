package jo.shajaraone.admin

import com.google.firebase.firestore.DocumentSnapshot
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class AUser(
    val uid: String = "", val role: String = "passenger", val status: String = "active",
    val name: String = "", val phone: String = "", val carType: String = "", val carColor: String = "",
    val carPlate: String = "", val createdAt: Long = 0, val riderSuspended: Boolean = false,
    val maxSeats: Int = 4, val verified: Boolean = false, val verifyCode: String = ""
) {
    val isCaptain get() = role == "captain"
    val riderBlocked get() = riderSuspended || (!isCaptain && status == "suspended")
    val car get() = listOf(carType, carColor, carPlate).filter { it.isNotBlank() }.joinToString(" • ")
}

data class ATrip(
    val id: String = "", val captainName: String = "", val captainPhone: String = "", val carText: String = "",
    val from: String = "", val to: String = "", val departAt: Long = 0, val seatsTotal: Long = 0,
    val seatsFree: Long = 0, val price: Double? = null, val status: String = ""
)

data class ABooking(
    val id: String = "", val captainName: String = "", val passengerName: String = "", val passengerPhone: String = "",
    val seats: Long = 1, val dropoff: String = "", val tripSummary: String = "", val status: String = "pending",
    val createdAt: Long = 0
)

data class ARequest(
    val id: String = "", val tripId: String = "", val captainName: String = "", val tripSummary: String = "",
    val type: String = "", val detail: String = "", val newDepartAt: Long = 0, val newPrice: Double? = null,
    val newNote: String = "", val status: String = "pending", val createdAt: Long = 0
)

fun DocumentSnapshot.toARequest() = ARequest(
    id = id, tripId = getString("tripId") ?: "", captainName = getString("captainName") ?: "",
    tripSummary = getString("tripSummary") ?: "", type = getString("type") ?: "", detail = getString("detail") ?: "",
    newDepartAt = getLong("newDepartAt") ?: 0, newPrice = getDouble("newPrice"), newNote = getString("newNote") ?: "",
    status = getString("status") ?: "pending", createdAt = getLong("createdAt") ?: 0
)

fun requestTypeText(t: String) = when (t) { "edit" -> "تعديل الرحلة"; "postpone" -> "تأجيل الرحلة"; "close" -> "إغلاق الحجز"; "cancelTrip" -> "إلغاء الرحلة"; "cancelBooking" -> "إلغاء حجز عميل"; else -> t }

fun DocumentSnapshot.toAUser() = AUser(
    uid = id, role = getString("role") ?: "passenger", status = getString("status") ?: "active",
    name = getString("name") ?: "", phone = getString("phone") ?: "", carType = getString("carType") ?: "",
    carColor = getString("carColor") ?: "", carPlate = getString("carPlate") ?: "", createdAt = getLong("createdAt") ?: 0,
    riderSuspended = getBoolean("riderSuspended") ?: false, maxSeats = (getLong("maxSeats") ?: 4L).toInt().coerceIn(4, 7),
    verified = getBoolean("verified") ?: false, verifyCode = getString("verifyCode") ?: ""
)

fun DocumentSnapshot.toATrip() = ATrip(
    id = id, captainName = getString("captainName") ?: "", captainPhone = getString("captainPhone") ?: "",
    carText = getString("carText") ?: "", from = getString("from") ?: "", to = getString("to") ?: "",
    departAt = getLong("departAt") ?: 0, seatsTotal = getLong("seatsTotal") ?: 0, seatsFree = getLong("seatsFree") ?: 0,
    price = getDouble("price"), status = getString("status") ?: ""
)

fun DocumentSnapshot.toABooking() = ABooking(
    id = id, captainName = getString("captainName") ?: "", passengerName = getString("passengerName") ?: "",
    passengerPhone = getString("passengerPhone") ?: "", seats = getLong("seats") ?: 1, dropoff = getString("dropoff") ?: "",
    tripSummary = getString("tripSummary") ?: "", status = getString("status") ?: "pending", createdAt = getLong("createdAt") ?: 0
)

fun fmtTime(ms: Long): String =
    if (ms <= 0) "—" else SimpleDateFormat("d/M  HH:mm", Locale.ENGLISH).format(Date(ms))

fun userStatusText(u: AUser) = when (u.status) {
    "pending" -> "بانتظار الموافقة"
    "approved" -> "معتمد"
    "rejected" -> "مرفوض"
    "suspended" -> "موقوف"
    else -> "فعّال"
}

fun tripStatusText(s: String) = when (s) {
    "open" -> "مفتوحة"
    "full" -> "مكتملة المقاعد"
    "cancelled" -> "ملغاة"
    else -> s
}

fun bookingStatusText(s: String) = when (s) {
    "accepted" -> "مقبول"
    "rejected" -> "مرفوض"
    "cancelled_by_passenger" -> "ألغاه الراكب"
    "cancelled_by_captain" -> "ألغاه الكابتن/الإدارة"
    else -> "بانتظار الكابتن"
}
