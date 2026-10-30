package jo.shajaraone.admin

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.android.gms.tasks.Task

fun initFirebase(ctx: Context) {
    if (FirebaseApp.getApps(ctx).isEmpty()) {
        FirebaseApp.initializeApp(
            ctx,
            FirebaseOptions.Builder()
                .setApiKey(BuildConfig.FB_API_KEY)
                .setApplicationId(BuildConfig.FB_APP_ID)
                .setProjectId(BuildConfig.FB_PROJECT_ID)
                .build()
        )
    }
}

/** Everything the screens show, kept live from Firestore. Only used after an admin signed in. */
class AdminData {
    var users by mutableStateOf(listOf<AUser>())
    var trips by mutableStateOf(listOf<ATrip>())
    var bookings by mutableStateOf(listOf<ABooking>())
    var requests by mutableStateOf(listOf<ARequest>())
    var places by mutableStateOf(mapOf<String, List<String>>())
    var loaded by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    private val regs = mutableListOf<ListenerRegistration>()

    fun start() {
        stop()
        val db = FirebaseFirestore.getInstance()
        fun fail(e: Exception) { error = e.message ?: "خطأ في التحميل" }
        regs += db.collection("users").addSnapshotListener { s, e ->
            if (e != null) fail(e) else if (s != null) { users = s.documents.map { it.toAUser() }; loaded = true; error = null }
        }
        regs += db.collection("trips").orderBy("departAt", Query.Direction.DESCENDING).limit(100)
            .addSnapshotListener { s, e -> if (e != null) fail(e) else if (s != null) trips = s.documents.map { it.toATrip() } }
        regs += db.collection("bookings").orderBy("createdAt", Query.Direction.DESCENDING).limit(150)
            .addSnapshotListener { s, e -> if (e != null) fail(e) else if (s != null) bookings = s.documents.map { it.toABooking() } }
        regs += db.collection("tripRequests").orderBy("createdAt", Query.Direction.DESCENDING).limit(100)
            .addSnapshotListener { s, e -> if (e != null) fail(e) else if (s != null) requests = s.documents.map { it.toARequest() } }
        regs += db.collection("config").document("places").addSnapshotListener { s, e ->
            if (e == null && s != null) {
                val raw = s.get("subAreas") as? Map<*, *> ?: emptyMap<Any, Any>()
                places = raw.entries.associate { (k, v) -> k.toString() to ((v as? List<*>)?.map { it.toString() } ?: emptyList()) }
            }
        }
    }

    fun stop() { regs.forEach { it.remove() }; regs.clear(); loaded = false }
}

/** What the admin can do. Each write is exactly one the Firestore rules allow for an admin. */
class AdminActions(private val report: (String) -> Unit) {
    private val db get() = FirebaseFirestore.getInstance()

    private fun run(okMsg: String, task: com.google.android.gms.tasks.Task<Void>) {
        task.addOnSuccessListener { report(okMsg) }
            .addOnFailureListener { report("ما تم: ${it.message?.take(120) ?: "خطأ"}") }
    }

    fun setUser(uid: String, fields: Map<String, Any>, okMsg: String = "تم") =
        run(okMsg, db.collection("users").document(uid).update(fields))

    fun cancelTrip(id: String) = run(
        "تم إلغاء الرحلة",
        db.collection("trips").document(id).update(mapOf("status" to "cancelled", "cancelReason" to "ألغتها الإدارة"))
    )

    fun cancelPendingBooking(id: String) = run(
        "تم إلغاء الحجز",
        db.collection("bookings").document(id)
            .update(mapOf("status" to "cancelled_by_captain", "reason" to "ألغته الإدارة", "updatedAt" to System.currentTimeMillis()))
    )

    /** Approve a captain's request: apply it to the trip first, then mark the request approved. */
    fun approve(r: ARequest) {
        val trip = db.collection("trips").document(r.tripId)
        val change: Task<Void> = when (r.type) {
            "close" -> trip.update(mapOf("status" to "full"))
            "postpone" -> trip.update(mapOf("departAt" to r.newDepartAt, "note" to r.newNote))
            else -> {
                val m = buildMap<String, Any> {
                    if (r.newPrice != null) put("price", r.newPrice)
                    if (r.newDepartAt > 0) { put("departAt", r.newDepartAt); put("note", r.newNote) }
                }
                if (m.isEmpty()) com.google.android.gms.tasks.Tasks.forResult<Void>(null) else trip.update(m)
            }
        }
        change.addOnSuccessListener {
            db.collection("tripRequests").document(r.id).update(mapOf("status" to "approved", "updatedAt" to System.currentTimeMillis()))
                .addOnSuccessListener { report("تمت الموافقة وتم تطبيقها على الرحلة") }
                .addOnFailureListener { report("طُبّقت على الرحلة لكن تعذّر تحديث الطلب") }
        }.addOnFailureListener { report("ما تم: ${it.message?.take(120) ?: "خطأ"}") }
    }

    fun reject(r: ARequest) = run(
        "تم رفض الطلب",
        db.collection("tripRequests").document(r.id).update(mapOf("status" to "rejected", "updatedAt" to System.currentTimeMillis()))
    )

    fun savePlaces(map: Map<String, List<String>>) = run(
        "تم حفظ الأماكن، بتظهر بالتطبيقات مباشرة",
        db.collection("config").document("places").set(mapOf("subAreas" to map, "updatedAt" to System.currentTimeMillis()))
    )
}

/** Signs in and proves the account carries the admin claim. Calls back with null on success or an Arabic error. */
fun adminSignIn(email: String, password: String, done: (String?) -> Unit) {
    val auth = FirebaseAuth.getInstance()
    auth.signInWithEmailAndPassword(email.trim(), password)
        .addOnSuccessListener { checkAdmin(done) }
        .addOnFailureListener { done("البريد أو كلمة السر غير صحيحة") }
}

fun checkAdmin(done: (String?) -> Unit) {
    val auth = FirebaseAuth.getInstance()
    val u = auth.currentUser ?: return done("سجّل الدخول")
    u.getIdToken(true).addOnSuccessListener { r ->
        if (r.claims["admin"] == true) done(null)
        else { auth.signOut(); done("هذا الحساب ليس حساب مشرف") }
    }.addOnFailureListener { done("تعذّر التحقق، تأكد من الإنترنت") }
}
