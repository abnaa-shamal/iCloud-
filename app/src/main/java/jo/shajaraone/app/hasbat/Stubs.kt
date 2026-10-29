package jo.shajaraone.app.hasbat

import jo.shajaraone.app.R
import jo.shajaraone.app.MainActivity

import android.content.Context

/** لا تفعيل ولا ترخيص في تطبيق الكابتن: كل الميزات مفتوحة. */
object License {
    const val locked = false
    const val activated = true
    fun refresh(context: Context) {}
}

object AutoBackup {
    fun init(context: Context) {}
    fun saveAsync(context: Context) {}
}

object BuildMode {
    const val WHATSAPP_NUMBER = "962790905298"
    const val CLIQ_ALIAS = ""
    const val CLIQ_WALLET_AR = ""
}
