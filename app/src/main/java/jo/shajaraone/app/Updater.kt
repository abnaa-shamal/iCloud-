package jo.shajaraone.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL

/** Checks the download page's version file for a newer build of THIS app. */
private const val SITE = "https://abnaa-shamal.github.io/iCloud-/"
private const val VERSION_URL = SITE + "apk/version.json"

private class UpdateInfo(val build: Long, val url: String)

private fun installedBuild(ctx: Context): Long =
    try { ctx.packageManager.getPackageInfo(ctx.packageName, 0).longVersionCode } catch (_: Exception) { Long.MAX_VALUE }

private fun fetchLatest(key: String): UpdateInfo? {
    val c = URL(VERSION_URL + "?t=" + System.currentTimeMillis()).openConnection() as HttpURLConnection
    c.connectTimeout = 8000; c.readTimeout = 8000
    c.useCaches = false
    if (c.responseCode != 200) return null
    val j = org.json.JSONObject(c.inputStream.bufferedReader().readText())
    val n = j.optLong("n", 0L)
    if (n <= 0L) return null
    return UpdateInfo(n, SITE + "?v=" + n)
}

// Channel sound cannot change after creation, so the sound change needs a new channel id (v2).
private const val UPD_CHANNEL = "app_update_v2"

/** Background check (every ~15 min, free): when a newer build is out, show ONE notification per build. */
class UpdateCheckWorker(ctx: Context, params: androidx.work.WorkerParameters) : androidx.work.Worker(ctx, params) {
    override fun doWork(): Result {
        val ctx = applicationContext
        return try {
            val latest = fetchLatest(if (BuildConfig.CAPTAIN_APP) "captain" else "rider") ?: return Result.success()
            val sp = ctx.getSharedPreferences("upd", Context.MODE_PRIVATE)
            if (!BuildConfig.CAPTAIN_APP) checkRideTaken(ctx, sp)
            if (latest.build > installedBuild(ctx) && latest.build > sp.getLong("notified", 0L)) {
                notifyUpdate(ctx, latest)
                sp.edit().putLong("notified", latest.build).apply()
            }
            Result.success()
        } catch (_: Exception) { Result.success() }
    }

    /** Rider: a captain accepted his pre-booking request while the app was closed. */
    private fun checkRideTaken(ctx: Context, sp: android.content.SharedPreferences) {
        try {
            val uid = Repo.auth.currentUser?.uid ?: return
            val snap = com.google.android.gms.tasks.Tasks.await(
                com.google.firebase.firestore.FirebaseFirestore.getInstance().collection("rideRequests").whereEqualTo("passengerUid", uid).get(),
                30, java.util.concurrent.TimeUnit.SECONDS
            )
            val since = sp.getLong("takenAt", 0L)
            val now = System.currentTimeMillis()
            if (since == 0L) { sp.edit().putLong("takenAt", now).apply(); return }
            val fresh = snap.documents.filter { it.getString("status") == "taken" && (it.getLong("updatedAt") ?: 0L) > since }
            if (fresh.isEmpty()) return
            if (android.os.Build.VERSION.SDK_INT >= 33 &&
                androidx.core.content.ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) return
            val nm = ctx.getSystemService(android.app.NotificationManager::class.java)
            if (android.os.Build.VERSION.SDK_INT >= 26) {
                val ch = android.app.NotificationChannel("ride_taken_v2", "قبول طلب الحجز المسبق", android.app.NotificationManager.IMPORTANCE_HIGH)
                ch.setSound(
                    Uri.parse("android.resource://${ctx.packageName}/${R.raw.snd_booking}"),
                    android.media.AudioAttributes.Builder().setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION).build()
                )
                nm.createNotificationChannel(ch)
            }
            val open = android.app.PendingIntent.getActivity(
                ctx, 8, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
            )
            fresh.take(3).forEach { d ->
                val n = androidx.core.app.NotificationCompat.Builder(ctx, "ride_taken_v2")
                    .setSmallIcon(R.drawable.ic_stat_pin)
                    .setContentTitle("✅ كابتن قبل طلبك المسبق")
                    .setContentText("${d.getString("takenByName") ?: ""} • ${d.getString("from") ?: ""} ← ${d.getString("to") ?: ""}")
                    .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
                    .setContentIntent(open).setAutoCancel(true).build()
                try { nm.notify(d.id.hashCode(), n) } catch (_: SecurityException) { }
            }
            sp.edit().putLong("takenAt", fresh.maxOf { it.getLong("updatedAt") ?: 0L }).apply()
        } catch (_: Exception) { }
    }

    private fun notifyUpdate(ctx: Context, u: UpdateInfo) {
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            androidx.core.content.ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) return
        val nm = ctx.getSystemService(android.app.NotificationManager::class.java)
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            val ch = android.app.NotificationChannel(UPD_CHANNEL, "تحديث التطبيق", android.app.NotificationManager.IMPORTANCE_HIGH)
            ch.setSound(
                Uri.parse("android.resource://${ctx.packageName}/${R.raw.snd_update}"),
                android.media.AudioAttributes.Builder().setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION).build()
            )
            nm.createNotificationChannel(ch)
        }
        val open = android.app.PendingIntent.getActivity(
            ctx, 7, Intent(Intent.ACTION_VIEW, Uri.parse(u.url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n = androidx.core.app.NotificationCompat.Builder(ctx, UPD_CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_pin)
            .setContentTitle("🔄 في تحديث جديد")
            .setContentText("نزل تحديث لتطبيق أبناء الشمال. اضغط وحدّث الآن.")
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(open).setAutoCancel(true).build()
        try { nm.notify(7001, n) } catch (_: SecurityException) { }
    }

    companion object {
        fun schedule(ctx: Context) {
            val req = androidx.work.PeriodicWorkRequestBuilder<UpdateCheckWorker>(15, java.util.concurrent.TimeUnit.MINUTES)
                .setConstraints(androidx.work.Constraints.Builder().setRequiredNetworkType(androidx.work.NetworkType.CONNECTED).build())
                .build()
            androidx.work.WorkManager.getInstance(ctx).enqueueUniquePeriodicWork("app_update_check", androidx.work.ExistingPeriodicWorkPolicy.KEEP, req)
        }
    }
}

@Composable
fun UpdateCheck() {
    val ctx = LocalContext.current
    var info by remember { mutableStateOf<UpdateInfo?>(null) }
    var dismissed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        try {
            val key = if (BuildConfig.CAPTAIN_APP) "captain" else "rider"
            val latest = withContext(Dispatchers.IO) { fetchLatest(key) }
            if (latest != null && latest.build > installedBuild(ctx)) info = latest
        } catch (_: Exception) { }
    }
    val u = info
    LaunchedEffect(u != null) { if (u != null) AppFeedback.updateSound(ctx) }
    if (u != null && !dismissed) {
        AlertDialog(
            onDismissRequest = { dismissed = true },
            title = { Text("في تحديث جديد") },
            text = { Text("نزل تحديث جديد لتطبيق أبناء الشمال. اضغط «حدّث»، نزّل الملف وثبّته فوق القديم.") },
            confirmButton = {
                TextButton(onClick = {
                    dismissed = true
                    try { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(u.url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (_: Exception) { }
                }) { Text("حدّث") }
            },
            dismissButton = { TextButton(onClick = { dismissed = true }) { Text("لاحقاً") } }
        )
    }
}
