package jo.shajaraone.app

import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query

/** All shared data lives in Firestore (free Spark plan). */
object Repo {
    val auth: FirebaseAuth get() = FirebaseAuth.getInstance()
    private val db get() = FirebaseFirestore.getInstance()
    private val users get() = db.collection("users")
    private val trips get() = db.collection("trips")
    private val bookings get() = db.collection("bookings")

    private fun now() = System.currentTimeMillis()

    // ===== Accounts (phone + password) =====

    private fun authError(e: Exception?): String = when (e) {
        is FirebaseAuthUserCollisionException -> "هالرقم مسجّل من قبل، جرّب تسجيل الدخول"
        is FirebaseAuthWeakPasswordException -> "كلمة السر لازم تكون 6 أحرف أو أكثر"
        is FirebaseAuthInvalidUserException -> "ما في حساب بهالرقم"
        is FirebaseAuthInvalidCredentialsException -> "الرقم أو كلمة السر غلط"
        is FirebaseNetworkException -> "تأكد من الإنترنت"
        else -> "صار خطأ، جرّب كمان مرة"
    }

    fun signIn(phone: String, password: String, onResult: (String?) -> Unit) {
        auth.signInWithEmailAndPassword(phoneToLoginEmail(phone), password)
            .addOnSuccessListener { onResult(null) }
            .addOnFailureListener { onResult(authError(it)) }
    }

    fun register(phone: String, password: String, onResult: (String?) -> Unit) {
        auth.createUserWithEmailAndPassword(phoneToLoginEmail(phone), password)
            .addOnSuccessListener { onResult(null) }
            .addOnFailureListener { onResult(authError(it)) }
    }

    fun signOut() = auth.signOut()

    /** New profile: passenger is active at once, captain waits for the admin. */
    fun createProfile(p: UserProfile, onResult: (Boolean) -> Unit) {
        val uid = auth.currentUser?.uid ?: return onResult(false)
        val data = hashMapOf<String, Any>(
            "role" to p.role,
            "status" to if (p.role == ROLE_CAPTAIN) USER_PENDING else USER_ACTIVE,
            "name" to p.name,
            "phone" to p.phone,
            "carType" to p.carType,
            "carColor" to p.carColor,
            "carPlate" to p.carPlate,
            "createdAt" to now(),
            // Reverse verification: the user sends this code from his own number to the support WhatsApp.
            "verifyCode" to (100000..999999).random().toString()
        )
        users.document(uid).set(data)
            .addOnSuccessListener { onResult(true) }
            .addOnFailureListener { onResult(false) }
    }

    /** onChange(null) = signed in but no profile yet. */
    fun listenUser(uid: String, onChange: (UserProfile?) -> Unit, onError: () -> Unit): ListenerRegistration =
        users.document(uid).addSnapshotListener { snap, err ->
            when {
                err != null -> onError()
                snap != null -> onChange(if (snap.exists()) snap.toUser() else null)
            }
        }

    // ===== Trips =====

    /** Trips from 3 hours ago onwards, soonest first. */
    fun listenTrips(onChange: (List<Trip>) -> Unit, onError: () -> Unit): ListenerRegistration =
        trips.whereGreaterThan("departAt", now() - 3 * 3_600_000L)
            .orderBy("departAt", Query.Direction.ASCENDING)
            .addSnapshotListener { snap, err ->
                if (err != null) onError()
                if (snap != null) onChange(snap.documents.map { it.toTrip() })
            }

    fun publishTrip(t: Trip, onDone: (Boolean) -> Unit) {
        val uid = auth.currentUser?.uid ?: return onDone(false)
        val data = hashMapOf<String, Any?>(
            "captainUid" to uid,
            "captainName" to t.captainName,
            "captainPhone" to t.captainPhone,
            "carText" to t.carText,
            "from" to t.from,
            "to" to t.to,
            "road" to t.road,
            "departAt" to t.departAt,
            "seatsTotal" to t.seatsTotal,
            "seatsFree" to t.seatsTotal,
            "price" to t.price,
            "acceptsParcels" to t.acceptsParcels,
            "note" to t.note,
            "status" to TRIP_OPEN,
            "cancelReason" to "",
            "createdAt" to now()
        )
        trips.add(data)
            .addOnSuccessListener { onDone(true) }
            .addOnFailureListener { onDone(false) }
    }

    /** Cancel the whole trip and every active booking on it, together. */
    fun cancelTrip(t: Trip, reason: String, active: List<Booking>, onDone: (Boolean) -> Unit) {
        val batch = db.batch()
        batch.update(trips.document(t.id), mapOf("status" to TRIP_CANCELLED, "cancelReason" to reason))
        active.forEach { b ->
            batch.update(
                bookings.document(b.id),
                mapOf("status" to BK_CANCELLED_CAPTAIN, "reason" to reason, "updatedAt" to now())
            )
        }
        batch.commit()
            .addOnSuccessListener { onDone(true) }
            .addOnFailureListener { onDone(false) }
    }

    // ===== Places edited by the admin (config/places) =====

    fun listenPlaces(onChange: (Map<String, List<String>>) -> Unit): ListenerRegistration =
        db.collection("config").document("places").addSnapshotListener { snap, _ ->
            val raw = snap?.get("subAreas") as? Map<*, *> ?: return@addSnapshotListener
            onChange(raw.entries.mapNotNull { (k, v) ->
                val key = k as? String ?: return@mapNotNull null
                val items = (v as? List<*>)?.filterIsInstance<String>() ?: return@mapNotNull null
                key to items
            }.toMap())
        }

    // ===== Bookings =====

    fun listenBookings(field: String, uid: String, onChange: (List<Booking>) -> Unit): ListenerRegistration =
        bookings.whereEqualTo(field, uid).addSnapshotListener { snap, _ ->
            if (snap != null) onChange(snap.documents.map { it.toBooking() }.sortedByDescending { it.createdAt })
        }

    fun book(t: Trip, me: UserProfile, seats: Long, dropoff: String, onDone: (Boolean) -> Unit) {
        val uid = auth.currentUser?.uid ?: return onDone(false)
        val data = hashMapOf<String, Any>(
            "tripId" to t.id,
            "captainUid" to t.captainUid,
            "captainName" to t.captainName,
            "captainPhone" to t.captainPhone,
            "passengerUid" to uid,
            "passengerName" to me.name,
            "passengerPhone" to me.phone,
            "seats" to seats,
            "dropoff" to dropoff,
            "tripSummary" to "${t.from} ← ${t.to}",
            "departAt" to t.departAt,
            "status" to BK_PENDING,
            "reason" to "",
            "createdAt" to now(),
            "updatedAt" to now()
        )
        bookings.add(data)
            .addOnSuccessListener { onDone(true) }
            .addOnFailureListener { onDone(false) }
    }

    /** Accept: take the seats and close the trip automatically when it is full. */
    fun accept(b: Booking, onDone: (Boolean) -> Unit) {
        val tripRef = trips.document(b.tripId)
        val bookingRef = bookings.document(b.id)
        db.runTransaction { tx ->
            val trip = tx.get(tripRef)
            val booking = tx.get(bookingRef)
            val free = trip.getLong("seatsFree") ?: 0
            if (!trip.exists() || trip.getString("status") == TRIP_CANCELLED ||
                booking.getString("status") != BK_PENDING || free < b.seats
            ) {
                throw FirebaseFirestoreException("cannot accept", FirebaseFirestoreException.Code.ABORTED)
            }
            val left = free - b.seats
            tx.update(tripRef, mapOf("seatsFree" to left, "status" to if (left == 0L) TRIP_FULL else TRIP_OPEN, "lastBookingId" to b.id))
            tx.update(bookingRef, mapOf("status" to BK_ACCEPTED, "updatedAt" to now()))
            true
        }
            .addOnSuccessListener { onDone(true) }
            .addOnFailureListener { onDone(false) }
    }

    fun reject(b: Booking, onDone: (Boolean) -> Unit) {
        bookings.document(b.id).update(mapOf("status" to BK_REJECTED, "updatedAt" to now()))
            .addOnSuccessListener { onDone(true) }
            .addOnFailureListener { onDone(false) }
    }

    /**
     * Cancel a booking (by the passenger or the captain).
     * If it was accepted, its seats go back to the trip and the trip reopens.
     */
    fun cancelBooking(b: Booking, newStatus: String, reason: String, onDone: (Boolean) -> Unit) {
        val tripRef = trips.document(b.tripId)
        val bookingRef = bookings.document(b.id)
        db.runTransaction { tx ->
            val booking = tx.get(bookingRef)
            val trip = tx.get(tripRef)
            val wasAccepted = booking.getString("status") == BK_ACCEPTED
            if (wasAccepted && trip.exists() && trip.getString("status") != TRIP_CANCELLED) {
                val total = trip.getLong("seatsTotal") ?: 0
                val free = minOf(total, (trip.getLong("seatsFree") ?: 0) + b.seats)
                tx.update(tripRef, mapOf("seatsFree" to free, "status" to TRIP_OPEN, "lastBookingId" to b.id))
            }
            tx.update(bookingRef, mapOf("status" to newStatus, "reason" to reason, "updatedAt" to now()))
            true
        }
            .addOnSuccessListener { onDone(true) }
            .addOnFailureListener { onDone(false) }
    }
}
