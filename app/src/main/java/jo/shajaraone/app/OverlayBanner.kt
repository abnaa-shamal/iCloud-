package jo.shajaraone.app

import android.app.ActivityManager
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Captain app only: a neat notification-style card that drops from the top of the screen over any other app
 * when a new booking request arrives. Needs the "display over other apps" permission; does nothing without it
 * or while the app itself is on screen. Tap opens the app, it also disappears by itself after 12 seconds.
 */
object OverlayBanner {
    private val main = Handler(Looper.getMainLooper())
    private var current: View? = null
    private var hide: Runnable? = null

    fun show(ctx: Context, title: String, body: String) {
        if (!BuildConfig.CAPTAIN_APP || Build.VERSION.SDK_INT < 26) return
        val app = ctx.applicationContext
        main.post {
            try {
                if (!Settings.canDrawOverlays(app) || appOnScreen(app)) return@post
                remove(app)
                val wm = app.getSystemService(Context.WINDOW_SERVICE) as WindowManager
                val d = app.resources.displayMetrics.density
                fun dp(v: Int) = (v * d).toInt()

                val card = LinearLayout(app).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutDirection = View.LAYOUT_DIRECTION_RTL
                    setPadding(dp(18), dp(14), dp(18), dp(14))
                    background = GradientDrawable().apply {
                        cornerRadius = dp(22).toFloat()
                        setColor(Color.parseColor("#F2141418"))
                        setStroke(dp(1), Color.parseColor("#D4AF37"))
                    }
                    elevation = dp(12).toFloat()
                    addView(TextView(app).apply {
                        text = title; textSize = 15f; setTypeface(typeface, Typeface.BOLD)
                        setTextColor(Color.parseColor("#F3C94B")); gravity = Gravity.START
                    })
                    addView(TextView(app).apply {
                        text = body; textSize = 13f; setTextColor(Color.WHITE); maxLines = 2
                        gravity = Gravity.START; setPadding(0, dp(4), 0, 0)
                    })
                    setOnClickListener {
                        remove(app)
                        app.packageManager.getLaunchIntentForPackage(app.packageName)
                            ?.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)?.let { app.startActivity(it) }
                    }
                }
                val lp = WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                    PixelFormat.TRANSLUCENT
                ).apply { gravity = Gravity.TOP; x = 0; y = dp(36); horizontalMargin = 0.03f }
                wm.addView(card, lp)
                card.translationY = -dp(120).toFloat(); card.alpha = 0f
                card.animate().translationY(0f).alpha(1f).setDuration(280).start()
                current = card
                val r = Runnable { remove(app) }
                hide = r
                main.postDelayed(r, 12_000)
            } catch (_: Throwable) { }
        }
    }

    private fun remove(app: Context) {
        try {
            hide?.let { main.removeCallbacks(it) }; hide = null
            current?.let { (app.getSystemService(Context.WINDOW_SERVICE) as WindowManager).removeView(it) }
        } catch (_: Throwable) { }
        current = null
    }

    private fun appOnScreen(app: Context): Boolean = try {
        val am = app.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        am.runningAppProcesses?.any { it.processName == app.packageName && it.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND } == true
    } catch (_: Throwable) { false }
}
