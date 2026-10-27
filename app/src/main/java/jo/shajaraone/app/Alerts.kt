package jo.shajaraone.app

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.FirebaseFirestore
import java.util.concurrent.TimeUnit

/** Captain alerts: distinct chime + vibration when a booking arrives or is cancelled. */
object Alerts {
    // Channel sound can't change after creation, so the id carries a version.
    private const val CAPTAIN_CHANNEL = "captain_bookings_v1"
    private const val WORK_NAME = "captain_booking_check"

    fun createChannels(ctx: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val sound = Uri.parse("android.resource://${ctx.packageName}/${R.raw.booking_chime}")
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val channel = NotificationChannel(CAPTAIN_CHANNEL, "طلبات الحجز", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "نغمة وإشعار لما يوصلك حجز أو إلغاء"
            setSound(sound, attrs)
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 300, 150, 300, 150, 500)
        }
        ctx.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun canNotify(ctx: Context) =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun show(ctx: Context, id: Int, title: String, text: String) {
        if (!canNotify(ctx)) return
        val open = PendingIntent.getActivity(
            ctx, 0,
            Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n = NotificationCompat.Builder(ctx, CAPTAIN_CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_pin)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(ctx).notify(id, n)
        } catch (e: SecurityException) {
            // permission revoked meanwhile: nothing to do
        }
    }

    /**
     * Alert the captain about bookings changed since the last alert:
     * new requests and passenger cancellations. Used by the open app and the background check.
     */
    fun processCaptainBookings(ctx: Context, list: List<Booking>) {
        val prefs = Prefs(ctx)
        val since = prefs.lastAlertAt
        if (since == 0L) {
            // First run: start from now, don't replay old history.
            prefs.lastAlertAt = System.currentTimeMillis()
            return
        }
        val fresh = list.filter { it.updatedAt > since }
        fresh.forEach { b ->
            when (b.status) {
                BK_PENDING -> show(
                    ctx, b.id.hashCode(), "📥 طلب حجز جديد",
                    "${b.passengerName} بدو ${seatsText(b.seats)} • ${b.tripSummary}" +
                        if (b.dropoff.isNotBlank()) " • النزول: ${b.dropoff}" else ""
                )
                BK_CANCELLED_PASSENGER -> show(
                    ctx, b.id.hashCode() + 1, "❌ راكب ألغى حجزه",
                    "${b.passengerName} ألغى ${seatsText(b.seats)} • ${b.tripSummary}"
                )
            }
        }
        val newest = fresh.maxOfOrNull { it.updatedAt } ?: return
        prefs.lastAlertAt = newest
    }

    /** Free background check every 15 minutes (Android's minimum) for when the app is closed. */
    fun scheduleBackgroundCheck(ctx: Context) {
        val req = PeriodicWorkRequestBuilder<BookingCheckWorker>(15, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, req)
    }

    fun cancelBackgroundCheck(ctx: Context) {
        WorkManager.getInstance(ctx).cancelUniqueWork(WORK_NAME)
    }
}

class BookingCheckWorker(ctx: Context, params: WorkerParameters) : Worker(ctx, params) {
    override fun doWork(): Result {
        val uid = Prefs(applicationContext).watchCaptainUid
        if (uid.isBlank() || Repo.auth.currentUser?.uid != uid) return Result.success()
        return try {
            val snap = Tasks.await(
                FirebaseFirestore.getInstance().collection("bookings").whereEqualTo("captainUid", uid).get(),
                30, TimeUnit.SECONDS
            )
            Alerts.processCaptainBookings(applicationContext, snap.documents.map { it.toBooking() })
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
