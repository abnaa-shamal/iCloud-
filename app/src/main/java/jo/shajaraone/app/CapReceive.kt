package jo.shajaraone.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.google.firebase.firestore.ListenerRegistration

/**
 * Keeps the captain "online" while he has a published trip: a small permanent notification and a live
 * booking listener, so a new request rings at once even when another app is open or the screen is locked.
 * It stops by itself when the captain has no active trip left.
 */
class CapReceiveService : Service() {
    private var regBookings: ListenerRegistration? = null
    private var regTrips: ListenerRegistration? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val uid = Repo.auth.currentUser?.uid
        if (uid == null) { stopSelf(); return START_NOT_STICKY }
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && nm.getNotificationChannel(CH) == null) {
            nm.createNotificationChannel(NotificationChannel(CH, "استقبال الطلبات", NotificationManager.IMPORTANCE_MIN))
        }
        val ok = try {
            ServiceCompat.startForeground(
                this, NID, note(), if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
            )
            true
        } catch (_: Exception) { false }
        if (!ok) { stopSelf(); return START_NOT_STICKY }

        if (regBookings == null) regBookings = Repo.listenBookings("captainUid", uid) { Alerts.processCaptainBookings(applicationContext, it) }
        if (regTrips == null) regTrips = Repo.listenTrips({ all ->
            val now = System.currentTimeMillis()
            val active = all.any { it.captainUid == uid && it.status != TRIP_CANCELLED && it.departAt > now - 4 * 3_600_000L }
            if (!active) stopSelf()
        }, {})
        return START_STICKY
    }

    override fun onDestroy() {
        regBookings?.remove(); regTrips?.remove()
        regBookings = null; regTrips = null
        super.onDestroy()
    }

    private fun note(): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CH)
            .setSmallIcon(R.drawable.ic_stat_pin)
            .setContentTitle("🟢 أونلاين: متصل لاستقبال الطلبات")
            .setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true).setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_MIN).build()
    }

    companion object {
        private const val CH = "cap_receive"
        private const val NID = 4108
        fun start(ctx: Context) { try { ContextCompat.startForegroundService(ctx.applicationContext, Intent(ctx.applicationContext, CapReceiveService::class.java)) } catch (_: Exception) {} }
        fun stop(ctx: Context) { try { ctx.applicationContext.stopService(Intent(ctx.applicationContext, CapReceiveService::class.java)) } catch (_: Exception) {} }
    }
}
