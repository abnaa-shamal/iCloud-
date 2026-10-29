package jo.shajaraone.app.hasbat

import jo.shajaraone.app.R
import jo.shajaraone.app.MainActivity

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ======================================================================
// الإعدادات ← الفقاعة العائمة
// ======================================================================
@Composable
fun FloatingBubbleSection(storage: AppDataManager, theme: AppTheme, isEnglish: Boolean) {
    val context = LocalContext.current
    val view = LocalView.current
    val textColor = textColorFor(theme)
    val subTextColor = subTextColorFor(theme)
    var on by remember { mutableStateOf(FloatingBubbleService.isEnabled(context)) }
    // يُعاد الفحص كل ثانية حتى يظهر الإذن فور تفعيله من إعدادات الهاتف
    val tick = MeterEngine.clockTick / 1000L
    val permitted = remember(tick) { FloatingBubbleService.hasPermission(context) }

    GlassCard(theme = theme, modifier = Modifier.fillMaxWidth(), corner = 24.dp, contentPadding = 16.dp, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(if (isEnglish) "🫧 Floating bubble" else "🫧 الفقاعة العائمة", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = textColor)
        Text(
            if (isEnglish) "When you leave the app (e.g. to open Maps), a small bubble stays on the screen. During a trip it shows the fare. Tap it to return; drag it to ✕ at the bottom to close it."
            else "لما تطلع من التطبيق (مثلاً لتفتح الخرائط)، بتضل فقاعة صغيرة على الشاشة. وقت الرحلة بتعرض الأجرة. اضغطها لترجع للتطبيق، واسحبها لـ ✕ تحت لتسكّرها.",
            fontSize = 12.sp, color = subTextColor, lineHeight = 19.sp
        )
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(if (isEnglish) "Show the bubble" else "إظهار الفقاعة", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = textColor)
            }
            Switch(
                checked = on,
                onCheckedChange = {
                    SoundEffectsHelper.playClick(view, storage.soundEnabled)
                    on = it
                    FloatingBubbleService.setEnabled(context, it)
                },
                colors = SwitchDefaults.colors(checkedTrackColor = theme.accentColor)
            )
        }
        if (on) {
            if (permitted) {
                Text(if (isEnglish) "✅ Permission granted" else "✅ الإذن مفعّل", color = Color(0xFF22C55E), fontSize = 12.sp, fontWeight = FontWeight.Bold)
            } else {
                Text(
                    if (isEnglish) "Android needs one permission: «Display over other apps». Tap the button, then turn it on for the app."
                    else "أندرويد بيحتاج إذن واحد: «الظهور فوق التطبيقات الأخرى». اضغط الزر، وفعّله للتطبيق.",
                    fontSize = 12.sp, color = subTextColor, lineHeight = 19.sp
                )
                NeonActionButton(
                    text = if (isEnglish) "🔓 Allow the bubble" else "🔓 تفعيل إذن الفقاعة",
                    colors = listOf(theme.accentColor, theme.needleColor),
                    modifier = Modifier.fillMaxWidth(),
                    height = 50.dp,
                    textSize = 14.sp
                ) {
                    SoundEffectsHelper.playClick(view, storage.soundEnabled)
                    FloatingBubbleService.openPermissionScreen(context)
                }
            }
        }
    }
}
