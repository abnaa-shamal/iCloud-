package jo.shajaraone.app.hasbat

import jo.shajaraone.app.R
import jo.shajaraone.app.MainActivity

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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * خدمة أمامية تُبقي العداد والـ GPS شغالين عند فتح الخرائط أو قفل الشاشة،
 * وتعرض إشعاراً دائماً بالأجرة والمسافة والوقت.
 */
class MeterService : Service() {

    companion object {
        private const val CHANNEL_ID = "meter_trip_channel"
        private const val NOTIFICATION_ID = 4107
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var updateJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        MeterEngine.init(this)
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val started = try {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                buildNotification(),
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0
            )
            true
        } catch (_: Exception) {
            false
        }

        if (!started || !MeterEngine.isTripActive) {
            stopSelf()
            return START_NOT_STICKY
        }

        if (updateJob?.isActive != true) {
            updateJob = scope.launch {
                while (isActive) {
                    delay(2000L)
                    if (!MeterEngine.isTripActive) { stopSelf(); break }
                    try {
                        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                            .notify(NOTIFICATION_ID, buildNotification())
                    } catch (_: Exception) {}
                }
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun createChannel() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            val en = AppDataManager.get(this).isEnglish
            val channel = NotificationChannel(
                CHANNEL_ID,
                if (en) "Active trip meter" else "عداد الرحلة الجارية",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = if (en) "Keeps the meter running in the background" else "يُبقي العداد شغالاً في الخلفية"
                setShowBadge(false)
            }
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): android.app.Notification {
        val en = AppDataManager.get(this).isEnglish
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val paused = MeterEngine.meterState == MeterState.PAUSED
        val fare = Fmt.money(MeterEngine.displayedFare)
        val title = when {
            paused && en -> "⏸ Trip paused • $fare JD"
            paused -> "⏸ الرحلة متوقفة مؤقتاً • $fare د.أ"
            en -> "🚕 Trip running • $fare JD"
            else -> "🚕 رحلة جارية • $fare د.أ"
        }
        val gpsNote = if (MeterEngine.hasFreshGpsFix()) "" else (if (en) " • ⚠️ No GPS" else " • ⚠️ لا توجد إشارة GPS")
        val text = (if (en) "${Fmt.km(MeterEngine.distanceKm)} km • " else "${Fmt.km(MeterEngine.distanceKm)} كم • ") +
            Fmt.clock(MeterEngine.elapsedSeconds) + gpsNote

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_meter)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(pending)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }
}
