package jo.shajaraone.app.hasbat

import jo.shajaraone.app.R
import jo.shajaraone.app.MainActivity

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import kotlin.math.abs

// ======================================================================
// الفقاعة العائمة: تظهر فوق التطبيقات الأخرى عند الخروج من التطبيق.
// بدون رحلة: أيقونة التطبيق. أثناء الرحلة: الأجرة الحالية.
// الضغط عليها يفتح التطبيق، وسحبها إلى ✕ في الأسفل يغلقها.
// تختفي عند العودة للتطبيق أو إغلاقه من قائمة التطبيقات المفتوحة.
// ======================================================================
class FloatingBubbleService : Service() {

    companion object {
        private const val CHANNEL_ID = "floating_bubble_channel"
        private const val NOTIFICATION_ID = 4108
        private const val PREFS = "floating_bubble_prefs"
        private const val KEY_ON = "enabled"
        private const val KEY_X = "x"
        private const val KEY_Y = "y"

        fun isEnabled(context: Context): Boolean =
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ON, true)

        fun setEnabled(context: Context, on: Boolean) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_ON, on).apply()
            if (!on) hide(context)
        }

        fun hasPermission(context: Context): Boolean =
            Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(context)

        /** يفتح شاشة «الظهور فوق التطبيقات الأخرى» لهذا التطبيق. */
        fun openPermissionScreen(context: Context) {
            try {
                val i = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + context.packageName))
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(i)
            } catch (_: Throwable) { }
        }

        @Volatile private var instance: FloatingBubbleService? = null
        @Volatile private var wantVisible = false

        /**
         * التطبيق ظاهر: نشغّل الخدمة (مسموح لأن التطبيق في المقدمة) والفقاعة مخفية.
         * أندرويد 15+ لا يسمح ببدء الخدمة بعد الخروج، لذلك تبدأ هنا مسبقاً.
         */
        fun onAppVisible(context: Context) {
            wantVisible = false
            val running = instance
            if (running != null) { running.applyVisibility(); return }
            if (!isEnabled(context) || !hasPermission(context)) return
            try {
                val i = Intent(context, FloatingBubbleService::class.java)
                if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(i) else context.startService(i)
            } catch (_: Throwable) { }
        }

        /** الخروج من التطبيق: تظهر الفقاعة. */
        fun onAppHidden() {
            wantVisible = true
            instance?.applyVisibility()
        }

        /** إيقاف الفقاعة تماماً. */
        fun hide(context: Context) {
            try { context.stopService(Intent(context, FloatingBubbleService::class.java)) } catch (_: Throwable) { }
        }
    }

    private var windowManager: WindowManager? = null
    private var bubble: FrameLayout? = null
    private var closeTarget: TextView? = null
    private var icon: ImageView? = null
    private var fareText: TextView? = null
    private var params: WindowManager.LayoutParams? = null
    private val handler = Handler(Looper.getMainLooper())

    private val ticker = object : Runnable {
        override fun run() {
            refresh()
            handler.postDelayed(this, 1000L)
        }
    }

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
                if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
            )
            true
        } catch (_: Throwable) {
            false
        }
        if (!started || !hasPermission(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        instance = this
        if (bubble == null) createBubble()
        applyVisibility()
        handler.removeCallbacks(ticker)
        handler.post(ticker)
        return START_NOT_STICKY
    }

    /** إغلاق التطبيق من قائمة التطبيقات المفتوحة يُخفي الفقاعة. */
    override fun onTaskRemoved(rootIntent: Intent?) {
        stopSelf()
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        handler.removeCallbacks(ticker)
        removeViews()
        super.onDestroy()
    }

    /** نضيف الفقاعة للشاشة عند الخروج من التطبيق، ونزيلها عند الرجوع إليه. */
    private fun applyVisibility() {
        handler.post {
            val wm = windowManager ?: return@post
            val root = bubble ?: return@post
            val lp = params ?: return@post
            val shown = root.parent != null
            try {
                if (wantVisible && !shown) {
                    refresh()
                    wm.addView(root, lp)
                } else if (!wantVisible && shown) {
                    wm.removeView(root)
                    closeTarget?.visibility = View.GONE
                }
            } catch (_: Throwable) { }
        }
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun overlayType(): Int =
        if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

    @SuppressLint("ClickableViewAccessibility")
    private fun createBubble() {
        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        windowManager = wm
        val size = dp(58)
        val gold = Color.parseColor("#F5C451")

        val root = FrameLayout(this)
        root.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.parseColor("#EE0B0B0C"))
            setStroke(dp(2), gold)
        }
        root.elevation = dp(6).toFloat()

        val img = ImageView(this)
        img.setImageResource(R.mipmap.ic_launcher)
        val imgPad = dp(6)
        img.setPadding(imgPad, imgPad, imgPad, imgPad)
        root.addView(img, FrameLayout.LayoutParams(size, size))

        val txt = TextView(this)
        txt.setTextColor(gold)
        txt.textSize = 13f
        txt.typeface = Typeface.DEFAULT_BOLD
        txt.gravity = Gravity.CENTER
        txt.visibility = View.GONE
        root.addView(txt, FrameLayout.LayoutParams(size, size))

        val prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val lp = WindowManager.LayoutParams(
            size, size, overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        lp.gravity = Gravity.TOP or Gravity.START
        lp.x = prefs.getInt(KEY_X, resources.displayMetrics.widthPixels - size - dp(12))
        lp.y = prefs.getInt(KEY_Y, dp(160))

        // علامة الإغلاق ✕ تظهر في الأسفل أثناء السحب
        val close = TextView(this)
        close.text = "✕"
        close.textSize = 22f
        close.setTextColor(Color.WHITE)
        close.gravity = Gravity.CENTER
        close.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.parseColor("#CCEF4444"))
        }
        close.visibility = View.GONE
        val closeLp = WindowManager.LayoutParams(
            dp(56), dp(56), overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT
        )
        closeLp.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        closeLp.y = dp(48)

        root.setOnTouchListener(object : View.OnTouchListener {
            private var startX = 0
            private var startY = 0
            private var touchX = 0f
            private var touchY = 0f
            private var moved = false

            override fun onTouch(v: View, e: MotionEvent): Boolean {
                when (e.action) {
                    MotionEvent.ACTION_DOWN -> {
                        startX = lp.x; startY = lp.y
                        touchX = e.rawX; touchY = e.rawY
                        moved = false
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = e.rawX - touchX
                        val dy = e.rawY - touchY
                        if (abs(dx) > dp(6) || abs(dy) > dp(6)) {
                            if (!moved) close.visibility = View.VISIBLE
                            moved = true
                        }
                        if (moved) {
                            lp.x = startX + dx.toInt()
                            lp.y = startY + dy.toInt()
                            try { wm.updateViewLayout(root, lp) } catch (_: Throwable) { }
                        }
                        return true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        close.visibility = View.GONE
                        if (!moved) {
                            openApp()
                        } else if (isOverClose(e.rawX, e.rawY)) {
                            stopSelf()
                        } else {
                            prefs.edit().putInt(KEY_X, lp.x).putInt(KEY_Y, lp.y).apply()
                        }
                        return true
                    }
                }
                return false
            }
        })

        try {
            wm.addView(close, closeLp)
        } catch (_: Throwable) {
            stopSelf()
            return
        }
        bubble = root
        closeTarget = close
        icon = img
        fareText = txt
        params = lp
        refresh()
    }

    private fun isOverClose(rawX: Float, rawY: Float): Boolean {
        val m = resources.displayMetrics
        return rawY > m.heightPixels - dp(150) && abs(rawX - m.widthPixels / 2f) < dp(90)
    }

    private fun refresh() {
        val txt = fareText ?: return
        val img = icon ?: return
        if (MeterEngine.isTripActive) {
            txt.text = Fmt.money(MeterEngine.displayedFare)
            txt.visibility = View.VISIBLE
            img.visibility = View.GONE
        } else {
            txt.visibility = View.GONE
            img.visibility = View.VISIBLE
        }
    }

    private fun openApp() {
        try {
            val i = Intent(this, MainActivity::class.java)
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            startActivity(i)
        } catch (_: Throwable) { }
    }

    private fun removeViews() {
        val wm = windowManager ?: return
        try { bubble?.let { if (it.parent != null) wm.removeView(it) } } catch (_: Throwable) { }
        try { closeTarget?.let { wm.removeView(it) } } catch (_: Throwable) { }
        bubble = null
        closeTarget = null
    }

    private fun createChannel() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            val en = AppDataManager.get(this).isEnglish
            val channel = NotificationChannel(
                CHANNEL_ID,
                if (en) "Floating bubble" else "الفقاعة العائمة",
                NotificationManager.IMPORTANCE_MIN
            ).apply {
                description = if (en) "Keeps the floating bubble visible" else "يُبقي الفقاعة العائمة ظاهرة"
                setShowBadge(false)
            }
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): android.app.Notification {
        val en = AppDataManager.get(this).isEnglish
        val pending = PendingIntent.getActivity(
            this, 1,
            Intent(this, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle(if (en) "Captain's Companion is running" else "رفيق دربك يعمل")
            .setContentText(if (en) "Tap the bubble to return to the app" else "اضغط الفقاعة للرجوع للتطبيق")
            .setContentIntent(pending)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
    }
}
