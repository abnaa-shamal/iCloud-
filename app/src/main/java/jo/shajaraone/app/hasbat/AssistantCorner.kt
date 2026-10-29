package jo.shajaraone.app.hasbat

import jo.shajaraone.app.R
import jo.shajaraone.app.MainActivity

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

// ======================================================================
// نافذة الكابتن المساعد: تُفتح من زر 🤖 في زاوية الشاشة الرئيسية
// ======================================================================
@Composable
fun AssistantCornerDialog(storage: AppDataManager, theme: AppTheme, isEnglish: Boolean, onClose: () -> Unit) {
    val view = LocalView.current
    val direction = LocalLayoutDirection.current
    val scroll = rememberScrollState()
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        CompositionLocalProvider(LocalLayoutDirection provides direction) {
            Box(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(theme.bgGradient))) {
                CyberBackdrop(theme)
                Column(
                    modifier = Modifier.fillMaxSize().systemBarsPadding().padding(16.dp).verticalScroll(scroll),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        GlassIconButton(icon = if (isEnglish) "⬅️" else "➡️", theme = theme, size = 40.dp) {
                            SoundEffectsHelper.playClick(view, storage.soundEnabled); onClose()
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("🤖 " + (if (isEnglish) "Captain's assistant" else "الكابتن المساعد"), fontSize = 20.sp, fontWeight = FontWeight.Black, color = textColorFor(theme))
                    }
                    AssistantSection(storage, theme, isEnglish, scroll)
                }
            }
        }
    }
}

// ======================================================================
// نافذة الدعم الفني: تُفتح من زر 🎧 في الشاشة الرئيسية (2.9.2)
// ======================================================================
@Composable
fun SupportCornerDialog(storage: AppDataManager, theme: AppTheme, isEnglish: Boolean, onClose: () -> Unit) {
    val view = LocalView.current
    val direction = LocalLayoutDirection.current
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        CompositionLocalProvider(LocalLayoutDirection provides direction) {
            Box(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(theme.bgGradient))) {
                CyberBackdrop(theme)
                Column(
                    modifier = Modifier.fillMaxSize().systemBarsPadding().padding(16.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        GlassIconButton(icon = if (isEnglish) "⬅️" else "➡️", theme = theme, size = 40.dp) {
                            SoundEffectsHelper.playClick(view, storage.soundEnabled); onClose()
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("🎧 " + (if (isEnglish) "Technical support" else "الدعم الفني"), fontSize = 20.sp, fontWeight = FontWeight.Black, color = textColorFor(theme))
                    }
                    SupportAboTalaSection(storage, theme, isEnglish)
                }
            }
        }
    }
}
