package jo.shajaraone.app

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Captain side of live location. While the captain taps "share", this foreground service sends his GPS position
 * for ONE trip to Firestore (collection liveLocations), at most every [MIN_SEND_MS] (or when he moved
 * [MIN_MOVE_M] meters), together with the uids of passengers whose booking is accepted.
 * It stops by itself when the captain stops it, when the trip is cancelled, or [MAX_SHARE_MS] after it started.
 */
class LiveShareService : Service() {

    companion object {
        private const val EXTRA_TRIP = "tripId"
        private const val ACTION_STOP = "stop"
        private const val CHANNEL = "live_location_v1"
        private const val NOTIFICATION_ID = 4711
        private const val MIN_SEND_MS = 15_000L
        private const val MIN_MOVE_M = 30f
        private const val MAX_SHARE_MS = 5 * 60 * 60_000L

        /** The trip being shared right now, or null. The UI reads this to show start / stop. */
        val active = MutableStateFlow<String?>(null)

        fun start(ctx: Context, tripId: String) {
            ContextCompat.startForegroundService(ctx, Intent(ctx, LiveShareService::class.java).putExtra(EXTRA_TRIP, tripId))
        }

        fun stop(ctx: Context) {
            ctx.startService(Intent(ctx, LiveShareService::class.java).setAction(ACTION_STOP))
        }
    }

    private var tripId: String? = null
    private var manager: LocationManager? = null
    private var listener: LocationListener? = null
    private var bookingsReg: ListenerRegistration? = null
    private var tripReg: ListenerRegistration? = null
    private var accepted: List<String> = emptyList()
    private var lastSentAt = 0L
    private var lastSent: Location? = null
    private var lastGpsAt = 0L
    private val handler = Handler(Looper.getMainLooper())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            shutDown()
            return START_NOT_STICKY
        }
        val trip = intent?.getStringExtra(EXTRA_TRIP)
        if (trip == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (tripId == trip) return START_NOT_STICKY // already sharing this trip
        if (tripId != null) cleanUp(deleteDoc = true) // switch trips
        tripId = trip
        showForeground()
        begin(trip)
        return START_NOT_STICKY
    }

    private fun showForeground() {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "مشاركة الموقع الحي", NotificationManager.IMPORTANCE_LOW))
        }
        val stop = PendingIntent.getService(
            this, 0, Intent(this, LiveShareService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n: Notification = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle("بتشارك موقعك الحي")
            .setContentText("الركاب المقبولين بيشوفوك على الخريطة")
            .setOngoing(true)
            .addAction(0, "إيقاف", stop)
            .build()
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0
        ServiceCompat.startForeground(this, NOTIFICATION_ID, n, type)
    }

    @SuppressLint("MissingPermission")
    private fun begin(trip: String) {
        val uid = Repo.auth.currentUser?.uid
        if (uid == null || !hasLocationPermission(this)) {
            shutDown()
            return
        }
        active.value = trip
        bookingsReg = Repo.listenBookings("captainUid", uid) { list ->
            accepted = list.filter { it.tripId == trip && it.status == BK_ACCEPTED }.map { it.passengerUid }
        }
        tripReg = Repo.listenTripCancelled(trip) { shutDown() }
        handler.postDelayed({ shutDown() }, MAX_SHARE_MS)

        val lm = getSystemService(LOCATION_SERVICE) as LocationManager
        manager = lm
        val l = object : LocationListener {
            override fun onLocationChanged(location: Location) = onFix(location)
            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) {}
            @Deprecated("Deprecated in Java")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        }
        listener = l
        try {
            if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 5_000L, 0f, l, Looper.getMainLooper())
            }
            if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 10_000L, 0f, l, Looper.getMainLooper())
            }
        } catch (e: SecurityException) {
            shutDown()
        }
    }

    /** A new position. GPS is preferred; a network position is used only when GPS has been silent for a while. */
    private fun onFix(loc: Location) {
        val trip = tripId ?: return
        val now = System.currentTimeMillis()
        if (loc.provider == LocationManager.GPS_PROVIDER) lastGpsAt = now
        else if (now - lastGpsAt < 20_000L || loc.accuracy > 100f) return

        val moved = lastSent?.distanceTo(loc) ?: Float.MAX_VALUE
        val due = now - lastSentAt >= MIN_SEND_MS
        val movedEnough = moved >= MIN_MOVE_M && now - lastSentAt >= 5_000L
        if (!due && !movedEnough) return

        lastSentAt = now
        lastSent = Location(loc)
        Repo.pushLive(trip, accepted, loc.latitude, loc.longitude, loc.accuracy.toDouble(), loc.speed.toDouble())
    }

    private fun shutDown() {
        cleanUp(deleteDoc = true)
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun cleanUp(deleteDoc: Boolean) {
        handler.removeCallbacksAndMessages(null)
        listener?.let { manager?.removeUpdates(it) }
        listener = null
        bookingsReg?.remove()
        bookingsReg = null
        tripReg?.remove()
        tripReg = null
        val t = tripId
        if (deleteDoc && t != null) Repo.stopLive(t)
        tripId = null
        lastSent = null
        lastSentAt = 0L
        active.value = null
    }

    override fun onDestroy() {
        if (tripId != null) cleanUp(deleteDoc = true)
        super.onDestroy()
    }
}
