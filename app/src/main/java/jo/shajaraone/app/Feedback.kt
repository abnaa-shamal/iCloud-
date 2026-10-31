package jo.shajaraone.app

import android.content.Context
import android.media.MediaPlayer
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * Sound and vibration for the whole app, in ONE place (like Theme.kt is for colors).
 *
 * HOW TO CHANGE:
 *  - Turn vibration or sound on/off with the two switches below.
 *  - To add a sound: put an .mp3 or .ogg file in  app/src/main/res/raw/  with ONE of the names below
 *    (lowercase, no spaces), then set SOUNDS_ENABLED = true. A missing file is simply skipped, never a crash.
 *  - To change how strong a vibration feels, edit the numbers in [vibrate] calls.
 *
 * The rest of the app only calls tap / success / error / newRequest and never knows how they are done.
 */
object AppFeedback {
    /** Short vibrations on button presses and results. */
    val HAPTICS_ENABLED = true

    /** Sounds on/off. The files live in res/raw (fb_*.wav). */
    val SOUNDS_ENABLED = true

    // Sound file names (without extension) expected in res/raw.
    private const val SND_OPEN = "snd_splash"

    /** The app just opened: one soft chime, no vibration. */
    fun open(ctx: Context) = fire(ctx, SND_OPEN, millis = 0, amplitude = 0)

    /** A main button was pressed. */
    fun tap(ctx: Context) = fire(ctx, TAPS[(tapIdx++) % TAPS.size], millis = 12, amplitude = 80)
    private val TAPS = listOf("fb_tap", "fb_tap2", "fb_tap3", "fb_tap4")
    private var tapIdx = 0

    /** A message from the admin / system, or a new app update. */
    fun system(ctx: Context) = fire(ctx, "snd_notify", millis = 40, amplitude = 120)

    /** A new trip / request. */
    fun newTrip(ctx: Context) = fire(ctx, "snd_new_trip", millis = 70, amplitude = 180)

    /** A trip was cancelled. */
    fun tripCancel(ctx: Context) = fire(ctx, "snd_trip_cancel", millis = 90, amplitude = 200)

    /** A message from a rider. */
    fun riderMsg(ctx: Context) = fire(ctx, "snd_rider_msg", millis = 40, amplitude = 140)

    /** Something worked, e.g. a booking request was sent. */
    fun success(ctx: Context) = fire(ctx, "snd_booking", millis = 40, amplitude = 160)

    /** Trip / captain arrived. */
    fun arrival(ctx: Context) = fire(ctx, "snd_arrival", millis = 60, amplitude = 160)

    /** Something failed. */
    fun error(ctx: Context) = fire(ctx, "snd_trip_cancel", millis = 90, amplitude = 200)

    /** Captain only: a new booking request arrived (one short chime; the repeating ring is [ringRequest]). */
    fun newRequest(ctx: Context) = fire(ctx, "snd_new_trip", millis = 70, amplitude = 180)

    /** A new app version is ready: the full update tune (not trimmed). */
    fun updateSound(ctx: Context) = fire(ctx, "snd_update", millis = 40, amplitude = 120)

    // ---- Repeating ring for a new booking request: loops sound + vibration until the captain decides or time is up ----
    private val ringHandler = Handler(Looper.getMainLooper())
    private val ringStopper = Runnable { ringStop() }
    private var ringPlayer: MediaPlayer? = null
    private var ringVib: Vibrator? = null
    private var ringActive = false
    private var ringStartTask: Runnable? = null

    /**
     * Rings for [maxMs] (15 s by default) or until [ringStop] is called (the captain accepted / declined).
     * [startDelayMs]: the notification chime plays first, then the loop continues right after it.
     * Calling it again while ringing only extends the time, it never doubles the sound.
     */
    fun ringRequest(ctx: Context, maxMs: Long = 15_000, startDelayMs: Long = 0) {
        try {
            val app = ctx.applicationContext
            ringHandler.post {
                try {
                    ringHandler.removeCallbacks(ringStopper)
                    ringHandler.postDelayed(ringStopper, maxMs + startDelayMs)
                    if (ringActive) return@post
                    ringActive = true
                    val task = Runnable { startRingLoop(app) }
                    ringStartTask = task
                    ringHandler.postDelayed(task, startDelayMs)
                } catch (_: Exception) { }
            }
        } catch (_: Exception) { }
    }

    private fun startRingLoop(ctx: Context) {
        try {
            if (!ringActive) return
            if (HAPTICS_ENABLED) {
                val vib = vibrator(ctx)
                if (vib != null && vib.hasVibrator()) {
                    vib.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 500, 700), 0))
                    ringVib = vib
                }
            }
            if (SOUNDS_ENABLED && jo.shajaraone.app.hasbat.AppDataManager.get(ctx).soundEnabled) {
                val id = ctx.resources.getIdentifier("snd_new_trip", "raw", ctx.packageName)
                if (id != 0) {
                    val mp = MediaPlayer.create(ctx, id)
                    if (mp != null) {
                        mp.isLooping = true
                        mp.start()
                        ringPlayer = mp
                    }
                }
            }
        } catch (_: Exception) { }
    }

    /** Stops the ring (the captain accepted / declined, or opened the request). Safe to call any time. */
    fun ringStop() {
        try {
            ringHandler.post {
                try {
                    ringActive = false
                    ringHandler.removeCallbacks(ringStopper)
                    ringStartTask?.let { ringHandler.removeCallbacks(it) }
                    ringStartTask = null
                    ringPlayer?.let { try { it.stop() } catch (_: Exception) { }; it.release() }
                    ringPlayer = null
                    ringVib?.cancel()
                    ringVib = null
                } catch (_: Exception) { }
            }
        } catch (_: Exception) { }
    }

    private fun vibrator(ctx: Context): Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (ctx.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            ctx.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }

    private fun fire(ctx: Context, sound: String, millis: Long, amplitude: Int) {
        try {
            if (HAPTICS_ENABLED && millis > 0) vibrate(ctx, millis, amplitude)
            if (SOUNDS_ENABLED && jo.shajaraone.app.hasbat.AppDataManager.get(ctx).soundEnabled) play(ctx, sound)
        } catch (_: Exception) {
            // Feedback must never break the app.
        }
    }

    private fun vibrate(ctx: Context, millis: Long, amplitude: Int) {
        val vib: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (ctx.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            ctx.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
        if (vib == null || !vib.hasVibrator()) return
        val amp = if (vib.hasAmplitudeControl()) amplitude.coerceIn(1, 255) else VibrationEffect.DEFAULT_AMPLITUDE
        vib.vibrate(VibrationEffect.createOneShot(millis, amp))
    }

    private fun play(ctx: Context, name: String) {
        val id = ctx.resources.getIdentifier(name, "raw", ctx.packageName)
        if (id == 0) return
        val mp = MediaPlayer.create(ctx, id) ?: return
        mp.setOnCompletionListener { it.release() }
        mp.start()
    }
}
