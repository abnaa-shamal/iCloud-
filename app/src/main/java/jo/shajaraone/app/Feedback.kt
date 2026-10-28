package jo.shajaraone.app

import android.content.Context
import android.media.MediaPlayer
import android.os.Build
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

    /** Sounds are off until you add files to res/raw (see above). */
    val SOUNDS_ENABLED = false

    // Sound file names (without extension) expected in res/raw.
    private const val SND_TAP = "fb_tap"
    private const val SND_SUCCESS = "fb_success"
    private const val SND_ERROR = "fb_error"
    private const val SND_REQUEST = "fb_request"

    /** A main button was pressed. */
    fun tap(ctx: Context) = fire(ctx, SND_TAP, millis = 12, amplitude = 80)

    /** Something worked, e.g. a booking request was sent. */
    fun success(ctx: Context) = fire(ctx, SND_SUCCESS, millis = 40, amplitude = 160)

    /** Something failed. */
    fun error(ctx: Context) = fire(ctx, SND_ERROR, millis = 90, amplitude = 200)

    /** Captain only: a new booking request arrived. */
    fun newRequest(ctx: Context) = fire(ctx, SND_REQUEST, millis = 70, amplitude = 180)

    private fun fire(ctx: Context, sound: String, millis: Long, amplitude: Int) {
        try {
            if (HAPTICS_ENABLED) vibrate(ctx, millis, amplitude)
            if (SOUNDS_ENABLED) play(ctx, sound)
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
