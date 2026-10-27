package com.shajara.rides

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore

private const val ENV_REAL = "real"
private const val ENV_TEST = "test"

/** DEV BUILD ONLY: admin panel. Manages the REAL project by default, or the TEST one. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminScreen() {
    var env by rememberSaveable { mutableStateOf(ENV_REAL) }
    Column(Modifier.fillMaxSize()) {
        if (env == ENV_REAL) EnvStrip("⚠️ لوحة المشرف • بيانات حقيقية", Color(0xFFB3261E))
        else EnvStrip("🧪 لوحة المشرف • بيانات تجريبية", Color(0xFF1B7A3E))

        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(16.dp)) {
            SegmentedButton(
                selected = env == ENV_REAL, onClick = { env = ENV_REAL },
                shape = SegmentedButtonDefaults.itemShape(0, 2)
            ) { Text("📕 الحقيقي") }
            SegmentedButton(
                selected = env == ENV_TEST, onClick = { env = ENV_TEST },
                shape = SegmentedButtonDefaults.itemShape(1, 2)
            ) { Text("📗 التجريبي") }
        }

        if (env == ENV_REAL) RealAdmin()
        else AdminLists(FirebaseFirestore.getInstance())
    }
}

/** Real project: the admin must sign in there with the admin phone. */
@Composable
private fun RealAdmin() {
    val ctx = LocalContext.current
    val auth = remember { RealFirebase.auth(ctx) }
    val db = remember { RealFirebase.db(ctx) }
    if (auth == null || db == null) {
        EmptyState("⚙️", "إعدادات المشروع الحقيقي ناقصة بهالنسخة.")
        return
    }
    // The admin is decided ONLY by the custom claim `admin: true` inside the signed-in user's token
    // (set from a trusted environment, see admin-tools/). null = still checking.
    var signedInEmail by remember { mutableStateOf<String?>(null) }
    var isAdmin by remember { mutableStateOf<Boolean?>(null) }
    DisposableEffect(auth) {
        val l = FirebaseAuth.AuthStateListener { a ->
            val u = a.currentUser
            signedInEmail = u?.email
            if (u == null) {
                isAdmin = false
            } else {
                isAdmin = null
                u.getIdToken(true)
                    .addOnSuccessListener { r -> isAdmin = r.claims["admin"] == true }
                    .addOnFailureListener { isAdmin = false }
            }
        }
        auth.addAuthStateListener(l)
        onDispose { auth.removeAuthStateListener(l) }
    }

    when {
        isAdmin == null -> Text("جارٍ التحقق من الصلاحية…", modifier = Modifier.padding(16.dp))
        isAdmin == true -> Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("✅ داخل كمشرف (${signedInEmail ?: ""})", modifier = Modifier.weight(1f))
                TextButton(onClick = { auth.signOut() }) { Text("خروج") }
            }
            AdminLists(db)
        }
        signedInEmail != null -> Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("هذا الحساب ما عنده صلاحية مشرف.", fontWeight = FontWeight.Bold)
            TextButton(onClick = { auth.signOut() }) { Text("تسجيل الخروج") }
        }
        else -> AdminLogin(auth)
    }
}

@Composable
private fun AdminLogin(auth: FirebaseAuth) {
    val ctx = LocalContext.current
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("سجّل دخول بحساب المشرف بالتطبيق الحقيقي", fontWeight = FontWeight.Bold)
        OutlinedTextField(
            value = email, onValueChange = { email = it },
            label = { Text("البريد الإلكتروني") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = password, onValueChange = { password = it },
            label = { Text("كلمة السر") }, singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth()
        )
        BigButton(if (busy) "لحظة..." else "دخول", enabled = !busy && email.isNotBlank() && password.isNotBlank()) {
            busy = true
            auth.signInWithEmailAndPassword(email.trim(), password)
                .addOnSuccessListener { busy = false }
                .addOnFailureListener {
                    busy = false
                    toast(ctx, "ما زبط الدخول: ${it.localizedMessage ?: "خطأ"}")
                }
        }
    }
}

/** Two tabs: captains and passengers. */
@Composable
private fun AdminLists(db: FirebaseFirestore) {
    var role by rememberSaveable { mutableStateOf(ROLE_CAPTAIN) }
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.padding(horizontal = 16.dp)) {
            ScrollChips(listOf(ROLE_CAPTAIN to "🚗 الكباتن", ROLE_PASSENGER to "🧍 الركاب"), role) { role = it }
        }
        UsersList(db, role)
    }
}

/**
 * Suspend a rider (a plain passenger, or a captain suspended AS a rider): cancel every pending/accepted
 * booking he made, give the seats of accepted ones back to the trip (one transaction per booking, so the
 * counter stays right even if the captain acts at the same time), and only in the LAST step apply [finalWrite]
 * to his profile. If anything fails before that he stays active and the admin can press the button again.
 */
private const val RIDER_CANCEL_REASON = "يعطيك العافية كابتن، نعتذر منك. تم إلغاء الحجز من قبل الراكب بدون ذكر سبب."

private fun suspendRider(db: FirebaseFirestore, u: UserProfile, finalWrite: Map<String, Any>, onDone: (Int, Boolean) -> Unit) {
    db.collection("bookings").whereEqualTo("passengerUid", u.uid).get().addOnSuccessListener { snap ->
        val active = snap.documents.filter { it.getString("status") == BK_PENDING || it.getString("status") == BK_ACCEPTED }
        var cancelled = 0
        fun finish() {
            db.collection("users").document(u.uid).update(finalWrite)
                .addOnSuccessListener { onDone(cancelled, true) }
                .addOnFailureListener { onDone(cancelled, false) }
        }
        fun next(i: Int) {
            if (i >= active.size) { finish(); return }
            val bRef = active[i].reference
            db.runTransaction { tx ->
                val b = tx.get(bRef)
                val st = b.getString("status")
                if (st != BK_PENDING && st != BK_ACCEPTED) return@runTransaction 0
                val tRef = db.collection("trips").document(b.getString("tripId") ?: "-")
                val t = tx.get(tRef)
                if (st == BK_ACCEPTED && t.exists() && t.getString("status") != TRIP_CANCELLED) {
                    val total = t.getLong("seatsTotal") ?: 0
                    val before = t.getLong("seatsFree") ?: 0
                    val free = minOf(total, before + (b.getLong("seats") ?: 0))
                    if (free > before) tx.update(tRef, mapOf("seatsFree" to free, "status" to TRIP_OPEN, "lastBookingId" to b.id))
                }
                tx.update(bRef, mapOf("status" to BK_CANCELLED_PASSENGER, "reason" to RIDER_CANCEL_REASON, "updatedAt" to System.currentTimeMillis()))
                1
            }
                .addOnSuccessListener { cancelled += it ?: 0; next(i + 1) }
                .addOnFailureListener { onDone(cancelled, false) }
        }
        next(0)
    }.addOnFailureListener { onDone(0, false) }
}

/**
 * Suspend a captain: cancel all his open/full trips and his pending/accepted bookings,
 * then (in the LAST batch) set his status to rejected. If anything fails before the last
 * batch the captain stays approved, so the admin can simply press the button again.
 */
private fun suspendCaptain(db: FirebaseFirestore, c: UserProfile, onDone: (Int, Boolean) -> Unit) {
    val reason = "تم إيقاف حساب الكابتن"
    db.collection("trips").whereEqualTo("captainUid", c.uid).get().addOnSuccessListener { tSnap ->
        db.collection("bookings").whereEqualTo("captainUid", c.uid).get().addOnSuccessListener { bSnap ->
            val now = System.currentTimeMillis()
            val ops = mutableListOf<Pair<DocumentReference, Map<String, Any>>>()
            tSnap.documents
                .filter { it.getString("status") == TRIP_OPEN || it.getString("status") == TRIP_FULL }
                .forEach { ops += it.reference to mapOf("status" to TRIP_CANCELLED, "cancelReason" to reason) }
            bSnap.documents
                .filter { it.getString("status") == BK_PENDING || it.getString("status") == BK_ACCEPTED }
                .forEach {
                    ops += it.reference to mapOf(
                        "status" to BK_CANCELLED_CAPTAIN, "reason" to reason, "updatedAt" to now
                    )
                }
            val cancelled = ops.size
            ops += db.collection("users").document(c.uid) to mapOf("status" to USER_REJECTED)
            val chunks = ops.chunked(400)
            fun commit(i: Int) {
                if (i >= chunks.size) { onDone(cancelled, true); return }
                val batch = db.batch()
                chunks[i].forEach { (ref, data) -> batch.update(ref, data) }
                batch.commit()
                    .addOnSuccessListener { commit(i + 1) }
                    .addOnFailureListener { onDone(cancelled, false) }
            }
            commit(0)
        }.addOnFailureListener { onDone(0, false) }
    }.addOnFailureListener { onDone(0, false) }
}

/** Approve / suspend captains, suspend / reactivate passengers, on the given project. */
@Composable
private fun UsersList(db: FirebaseFirestore, role: String) {
    val ctx = LocalContext.current
    val captainTab = role == ROLE_CAPTAIN
    var users by remember(db, role) { mutableStateOf<List<UserProfile>?>(null) }
    var filter by rememberSaveable(role) { mutableStateOf(if (captainTab) USER_PENDING else USER_ACTIVE) }
    var confirm by remember { mutableStateOf<UserProfile?>(null) }
    var confirmRider by remember { mutableStateOf<UserProfile?>(null) }
    var seatsFor by remember { mutableStateOf<UserProfile?>(null) }
    var seatsValue by remember { mutableStateOf(4) }
    var editing by remember { mutableStateOf<UserProfile?>(null) }
    var busy by remember { mutableStateOf(false) }

    DisposableEffect(db, role) {
        val r = db.collection("users").whereEqualTo("role", role).addSnapshotListener { snap, err ->
            if (err != null && users == null) users = emptyList()
            if (snap != null) users = snap.documents.map { it.toUser() }.sortedByDescending { it.createdAt }
        }
        onDispose { r.remove() }
    }

    fun setStatus(u: UserProfile, status: String, okText: String) {
        db.collection("users").document(u.uid).update("status", status)
            .addOnSuccessListener { toast(ctx, okText) }
            .addOnFailureListener { toast(ctx, "ما زبط: تأكد إنك داخل كمشرف ومن قواعد الحماية") }
    }

    fun reactivateRider(u: UserProfile) {
        db.collection("users").document(u.uid).update("riderSuspended", false)
            .addOnSuccessListener { toast(ctx, "تم تفعيل ${u.name} كراكب ✅") }
            .addOnFailureListener { toast(ctx, "ما زبط: تأكد إنك داخل كمشرف ومن قواعد الحماية") }
    }

    val all = users.orEmpty()
    val options = if (captainTab) listOf(
        USER_PENDING to "⏳ بانتظار (${all.count { it.status == USER_PENDING }})",
        USER_APPROVED to "✅ مقبولين (${all.count { it.status == USER_APPROVED }})",
        USER_REJECTED to "🚫 موقوفين/مرفوضين (${all.count { it.status == USER_REJECTED }})"
    ) else listOf(
        USER_ACTIVE to "✅ فعّالين (${all.count { it.status == USER_ACTIVE }})",
        USER_SUSPENDED to "🚫 موقوفين (${all.count { it.status == USER_SUSPENDED }})"
    )
    val shown = all.filter { it.status == filter }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item { ScrollChips(options, filter) { filter = it } }
        when {
            users == null -> item { LoadingBox() }
            shown.isEmpty() -> item { EmptyState("👤", "ما في أحد هون") }
            else -> items(shown, key = { it.uid }) { u ->
                AppCard {
                    Text(u.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("📞 ${u.phone}")
                    if (captainTab) Text("🚗 ${u.carText}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (captainTab) Text("💺 حدّ المقاعد: ${u.maxSeats}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (captainTab && u.riderSuspended) Text("🚫 موقوف كراكب", color = MaterialTheme.colorScheme.error)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (captainTab) {
                            if (u.status != USER_APPROVED) {
                                Button(onClick = { setStatus(u, USER_APPROVED, "تمت الموافقة على ${u.name} ✅") }) { Text("✅ قبول") }
                            }
                            if (u.status == USER_APPROVED) {
                                OutlinedButton(onClick = { confirm = u }, enabled = !busy) { Text("إيقاف") }
                            } else if (u.status == USER_PENDING) {
                                OutlinedButton(onClick = { setStatus(u, USER_REJECTED, "تم رفض ${u.name}") }) { Text("رفض") }
                            }
                        } else {
                            if (u.status == USER_ACTIVE) {
                                OutlinedButton(onClick = { confirm = u }, enabled = !busy) { Text("إيقاف") }
                            } else {
                                Button(onClick = { setStatus(u, USER_ACTIVE, "تم تفعيل ${u.name} ✅") }) { Text("✅ إعادة تفعيل") }
                            }
                        }
                        if (captainTab) {
                            TextButton(onClick = { editing = u }) { Text("✏️ تعديل") }
                        }
                        Spacer(Modifier.weight(1f))
                        FilledTonalIconButton(onClick = { callPhone(ctx, u.phone) }) { Text("📞") }
                    }
                    if (captainTab) {
                        // Independent from the captain suspension above: only blocks him in the RIDER app.
                        if (u.riderSuspended) {
                            Button(onClick = { reactivateRider(u) }) { Text("✅ إعادة تفعيل كراكب") }
                        } else {
                            OutlinedButton(onClick = { confirmRider = u }, enabled = !busy) { Text("إيقاف كراكب") }
                        }
                        TextButton(onClick = { seatsValue = u.maxSeats; seatsFor = u }) { Text("💺 حدّ المقاعد") }
                    }
                }
            }
        }
    }

    confirm?.let { u ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text("إيقاف ${u.name}؟") },
            text = {
                Text(
                    if (u.isCaptain) "راح تنلغي كل رحلاته المفتوحة وحجوزاته المعلّقة والمقبولة، وما بيقدر يستعمل التطبيق."
                    else "راح تنلغي كل حجوزاته (المعلّقة والمقبولة) وترجع المقاعد للرحلات، وما بيقدر يحجز لين تعيد تفعيله."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirm = null
                    if (u.isCaptain) {
                        busy = true
                        suspendCaptain(db, u) { n, ok ->
                            busy = false
                            toast(ctx, if (ok) "تم إيقاف ${u.name} وإلغاء $n عنصر" else "ما كمل الإيقاف، جرّب كمان مرة")
                        }
                    } else {
                        busy = true
                        suspendRider(db, u, mapOf("status" to USER_SUSPENDED)) { n, ok ->
                            busy = false
                            toast(ctx, if (ok) "تم إيقاف ${u.name} وإلغاء $n حجز" else "ما كمل الإيقاف، جرّب كمان مرة")
                        }
                    }
                }) { Text("نعم، أوقف") }
            },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("تراجع") } }
        )
    }

    confirmRider?.let { u ->
        AlertDialog(
            onDismissRequest = { confirmRider = null },
            title = { Text("إيقاف ${u.name} كراكب؟") },
            text = { Text("راح تنلغي كل حجوزاته كراكب (المعلّقة والمقبولة) وترجع المقاعد للرحلات. حسابه ككابتن ما بيتأثر.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmRider = null
                    busy = true
                    suspendRider(db, u, mapOf("riderSuspended" to true)) { n, ok ->
                        busy = false
                        toast(ctx, if (ok) "تم إيقاف ${u.name} كراكب وإلغاء $n حجز" else "ما كمل الإيقاف، جرّب كمان مرة")
                    }
                }) { Text("نعم، أوقف") }
            },
            dismissButton = { TextButton(onClick = { confirmRider = null }) { Text("تراجع") } }
        )
    }

    seatsFor?.let { u ->
        AlertDialog(
            onDismissRequest = { seatsFor = null },
            title = { Text("حدّ مقاعد ${u.name}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("الأصل 4 مقاعد لكل كابتن. ارفعه فقط لسيارة أكبر بعد التأكد منها.", style = MaterialTheme.typography.bodySmall)
                    Stepper(seatsValue, 4, 7) { seatsValue = it }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    seatsFor = null
                    db.collection("users").document(u.uid).update("maxSeats", seatsValue)
                        .addOnSuccessListener { toast(ctx, "حدّ مقاعد ${u.name}: $seatsValue ✅") }
                        .addOnFailureListener { toast(ctx, "ما زبط الحفظ، تأكد إنك داخل كمشرف") }
                }) { Text("حفظ") }
            },
            dismissButton = { TextButton(onClick = { seatsFor = null }) { Text("تراجع") } }
        )
    }

    editing?.let { u ->
        var name by remember(u.uid) { mutableStateOf(u.name) }
        var carType by remember(u.uid) { mutableStateOf(u.carType) }
        var carColor by remember(u.uid) { mutableStateOf(u.carColor) }
        var carPlate by remember(u.uid) { mutableStateOf(u.carPlate) }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text("تعديل بيانات الكابتن") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("الكابتن بعد الموافقة ما بيقدر يعدّل هالبيانات، المشرف بس.", style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(name, { name = it }, label = { Text("الاسم") }, singleLine = true)
                    OutlinedTextField(carType, { carType = it }, label = { Text("نوع السيارة") }, singleLine = true)
                    OutlinedTextField(carColor, { carColor = it }, label = { Text("اللون") }, singleLine = true)
                    OutlinedTextField(carPlate, { carPlate = it }, label = { Text("رقم اللوحة") }, singleLine = true)
                }
            },
            confirmButton = {
                TextButton(
                    enabled = name.trim().length >= 2,
                    onClick = {
                        db.collection("users").document(u.uid).update(
                            mapOf(
                                "name" to name.trim(), "carType" to carType.trim(),
                                "carColor" to carColor.trim(), "carPlate" to carPlate.trim()
                            )
                        )
                            .addOnSuccessListener { toast(ctx, "تم حفظ التعديل ✅") }
                            .addOnFailureListener { toast(ctx, "ما زبط الحفظ، تأكد إنك داخل كمشرف") }
                        editing = null
                    }
                ) { Text("حفظ") }
            },
            dismissButton = { TextButton(onClick = { editing = null }) { Text("تراجع") } }
        )
    }
}
