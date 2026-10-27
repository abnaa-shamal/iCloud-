package jo.shajaraone.app

import com.google.firebase.firestore.DocumentSnapshot

// ===== Users =====

const val ROLE_PASSENGER = "passenger"
const val ROLE_CAPTAIN = "captain"

const val USER_ACTIVE = "active"       // passengers
const val USER_PENDING = "pending"     // captain waiting for admin
const val USER_APPROVED = "approved"   // captain approved
const val USER_REJECTED = "rejected"   // captain rejected
const val USER_SUSPENDED = "suspended" // passenger suspended by the admin

data class UserProfile(
    val uid: String = "",
    val role: String = ROLE_PASSENGER,
    val status: String = USER_ACTIVE,
    val name: String = "",
    val phone: String = "",
    val carType: String = "",
    val carColor: String = "",
    val carPlate: String = "",
    val createdAt: Long = 0,
    /** Set by the admin only; a missing field (old accounts) means not suspended. */
    val riderSuspended: Boolean = false,
    /** Most seats this captain may announce per trip. 4 for everyone; only the admin raises it (max 7). */
    val maxSeats: Int = 4,
    /** 6-digit code the user sends from his own number to the support WhatsApp; blank on old accounts. */
    val verifyCode: String = "",
    /** Set by the admin only, after the code arrived from the same number. */
    val verified: Boolean = false
) {
    /** True while the "verify your number" bar should show. */
    val needsVerification get() = verifyCode.isNotBlank() && !verified
    val isCaptain get() = role == ROLE_CAPTAIN
    /** Blocked in the RIDER app: suspended as rider (any account) or a suspended plain passenger. */
    val riderBlocked get() = riderSuspended || (!isCaptain && status == USER_SUSPENDED)
    val carText get() = listOf(carType, carColor).filter { it.isNotBlank() }.joinToString(" ") +
        if (carPlate.isNotBlank()) " • $carPlate" else ""
}

fun DocumentSnapshot.toUser() = UserProfile(
    uid = id,
    role = getString("role") ?: ROLE_PASSENGER,
    status = getString("status") ?: USER_ACTIVE,
    name = getString("name") ?: "",
    phone = getString("phone") ?: "",
    carType = getString("carType") ?: "",
    carColor = getString("carColor") ?: "",
    carPlate = getString("carPlate") ?: "",
    createdAt = getLong("createdAt") ?: 0,
    riderSuspended = getBoolean("riderSuspended") ?: false,
    maxSeats = (getLong("maxSeats") ?: 4L).toInt().coerceIn(4, 7),
    verifyCode = getString("verifyCode") ?: "",
    verified = getBoolean("verified") ?: false
)

// ===== Trips =====

const val TRIP_OPEN = "open"
const val TRIP_FULL = "full"
const val TRIP_CANCELLED = "cancelled"

data class Trip(
    val id: String = "",
    val captainUid: String = "",
    val captainName: String = "",
    val captainPhone: String = "",
    val carText: String = "",
    val from: String = "",
    val to: String = "",
    val road: String = "",
    val departAt: Long = 0,
    val seatsTotal: Long = 0,
    val seatsFree: Long = 0,
    /** null = «حسب الاتفاق» */
    val price: Double? = null,
    val acceptsParcels: Boolean = false,
    val note: String = "",
    val status: String = TRIP_OPEN,
    val cancelReason: String = ""
) {
    val isBookable get() = status == TRIP_OPEN && seatsFree > 0 && departAt > System.currentTimeMillis()
    val summary get() = "$from ← $to • ${timeText(departAt)}"
}

fun DocumentSnapshot.toTrip() = Trip(
    id = id,
    captainUid = getString("captainUid") ?: "",
    captainName = getString("captainName") ?: "",
    captainPhone = getString("captainPhone") ?: "",
    carText = getString("carText") ?: "",
    from = getString("from") ?: "",
    to = getString("to") ?: "",
    road = getString("road") ?: "",
    departAt = getLong("departAt") ?: 0,
    seatsTotal = getLong("seatsTotal") ?: 0,
    seatsFree = getLong("seatsFree") ?: 0,
    price = getDouble("price"),
    acceptsParcels = getBoolean("acceptsParcels") ?: false,
    note = getString("note") ?: "",
    status = getString("status") ?: "",
    cancelReason = getString("cancelReason") ?: ""
)

// ===== Bookings =====

const val BK_PENDING = "pending"
const val BK_ACCEPTED = "accepted"
const val BK_REJECTED = "rejected"
const val BK_CANCELLED_PASSENGER = "cancelled_by_passenger"
const val BK_CANCELLED_CAPTAIN = "cancelled_by_captain"

data class Booking(
    val id: String = "",
    val tripId: String = "",
    val captainUid: String = "",
    val captainName: String = "",
    val captainPhone: String = "",
    val passengerUid: String = "",
    val passengerName: String = "",
    val passengerPhone: String = "",
    val seats: Long = 1,
    val dropoff: String = "",
    val tripSummary: String = "",
    val departAt: Long = 0,
    val status: String = BK_PENDING,
    val reason: String = "",
    val createdAt: Long = 0,
    val updatedAt: Long = 0
) {
    val isActive get() = status == BK_PENDING || status == BK_ACCEPTED
}

fun DocumentSnapshot.toBooking() = Booking(
    id = id,
    tripId = getString("tripId") ?: "",
    captainUid = getString("captainUid") ?: "",
    captainName = getString("captainName") ?: "",
    captainPhone = getString("captainPhone") ?: "",
    passengerUid = getString("passengerUid") ?: "",
    passengerName = getString("passengerName") ?: "",
    passengerPhone = getString("passengerPhone") ?: "",
    seats = getLong("seats") ?: 1,
    dropoff = getString("dropoff") ?: "",
    tripSummary = getString("tripSummary") ?: "",
    departAt = getLong("departAt") ?: 0,
    status = getString("status") ?: BK_PENDING,
    reason = getString("reason") ?: "",
    createdAt = getLong("createdAt") ?: 0,
    updatedAt = getLong("updatedAt") ?: 0
)

fun bookingStatusText(s: String) = when (s) {
    BK_ACCEPTED -> "مقبول ✅"
    BK_REJECTED -> "مرفوض"
    BK_CANCELLED_PASSENGER -> "ألغاه الراكب"
    BK_CANCELLED_CAPTAIN -> "ألغاه الكابتن"
    else -> "بانتظار رد الكابتن ⏳"
}
