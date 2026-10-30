package jo.shajaraone.app.hasbat

import jo.shajaraone.app.R
import jo.shajaraone.app.MainActivity

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

// ======================================================================
// جنّيات رفيق دربك: تمارا وتالا وتيماء 🧚
// يطرن ويلعبن في التطبيق، ومن يلمسها الكابتن تتوقف وتلوّح وتعرّف بنفسها.
// يختفين أثناء الرحلة. ضغط مطوّل على أي واحدة يخفيهن لبقية اليوم.
// الصور خفيفة جداً (حوالي 140 كيلوبايت للست صور معاً).
// ======================================================================

data class FairySpec(
    val name: String,
    val intro: String,
    val flyRes: Int,
    val waveRes: Int,
    val sizeDp: Dp,       // عرض صورة الطيران
    val phase: Float      // حتى لا يتحركن بنفس التوقيت
)

private val fairySquad = listOf(
    FairySpec("تمارا", "مرحبا كابتن! 👋 أنا تمارا 💗\nكيف بقدر أساعدك اليوم؟", R.drawable.fairy_tamara_fly, R.drawable.fairy_tamara_wave, 84.dp, 0f),
    FairySpec("تالا", "هاي كابتن! 😄 أنا تالا 💖\nكيف بقدر أساعدك اليوم؟", R.drawable.fairy_tala_fly, R.drawable.fairy_tala_wave, 84.dp, 0.37f),
    FairySpec("تيماء", "مرحبا كابتن… 🌸 أنا تيماء الصغيرة\nكيف بقدر أساعدك اليوم؟", R.drawable.fairy_taima_fly, R.drawable.fairy_taima_wave, 72.dp, 0.71f)
)

@Composable
fun AssistantBubble(storage: AppDataManager, theme: AppTheme, isEnglish: Boolean, visible: Boolean) {
    val view = LocalView.current
    var openAssistant by remember { mutableStateOf(false) }
    var hiddenUntil by remember { mutableStateOf(storage.getBubbleHiddenUntil()) }
    var talkingIndex by remember { mutableIntStateOf(-1) }
    val hidden = System.currentTimeMillis() < hiddenUntil

    if (visible && !hidden) {
        // ساعة الحركة (تعمل فقط وهن ظاهرات، حفاظاً على البطارية)
        val time by produceState(0f) {
            val start = withFrameNanos { it }
            while (true) withFrameNanos { value = (it - start) / 1_000_000_000f }
        }
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val areaW = maxWidth
            val areaH = maxHeight

            fairySquad.forEachIndexed { index, spec ->
                val w = spec.sizeDp
                val h = spec.sizeDp
                val maxX = (areaW - w - 4.dp).coerceAtLeast(4.dp)
                val minY = areaH * 0.10f
                val maxY = (areaH * 0.66f - h).coerceAtLeast(minY)
                val ax = remember { Animatable((areaW.value - w.value) * (0.12f + index * 0.34f)) }
                val ay = remember { Animatable(areaH.value * (0.18f + index * 0.13f)) }
                val spin = remember { Animatable(0f) }
                var facingRight by remember { mutableStateOf(false) }
                var moving by remember { mutableStateOf(false) }
                val talking = talkingIndex == index

                // الطيران واللعب: مكان جديد كل بضع ثوانٍ، وأحياناً دورة مرحة
                LaunchedEffect(index, areaW, areaH) {
                    delay(500L * index)
                    while (true) {
                        if (talkingIndex == index) { moving = false; delay(250L); continue }
                        val tx = 4f + Random.nextFloat() * (maxX.value - 4f)
                        val ty = minY.value + Random.nextFloat() * (maxY.value - minY.value)
                        facingRight = tx > ax.value
                        val dur = 2000 + Random.nextInt(1800)
                        moving = true
                        if (Random.nextInt(5) == 0) launch { spin.snapTo(0f); spin.animateTo(360f, tween(900, easing = FastOutSlowInEasing)); spin.snapTo(0f) }
                        launch { ax.animateTo(tx, tween(dur, easing = FastOutSlowInEasing)) }
                        ay.animateTo(ty, tween(dur, easing = FastOutSlowInEasing))
                        moving = false
                        delay(700L + Random.nextInt(2300))
                    }
                }

                val tt = time + spec.phase * 3f
                val bob = sin(tt * 2f * PI.toFloat() * 0.8f) * 6f
                val flutter = 1f + sin(tt * 2f * PI.toFloat() * 4f) * 0.025f   // رجفة خفيفة تشبه رفرفة الأجنحة
                val lean = if (moving && !talking) (if (facingRight) 8f else -8f) else sin(tt * 2f * PI.toFloat() * 0.5f) * 3f
                val hop = if (talking) -kotlin.math.abs(sin(tt * 2f * PI.toFloat() * 1.6f)) * 6f else 0f

                // ✨ غبار النجوم خلف الجنّية
                Canvas(
                    modifier = Modifier
                        .offset(x = ax.value.dp, y = (ay.value + bob).dp)
                        .size(w, h)
                ) {
                    for (i in 0 until 6) {
                        val k = (tt * 0.8f + i * 0.17f) % 1f
                        val alpha = ((1f - k) * 0.8f).coerceIn(0f, 1f)
                        val sx = size.width * (if (facingRight) 0.25f - k * 0.35f else 0.75f + k * 0.35f)
                        val sy = size.height * (0.55f + sin(i * 1.9f + tt * 2f) * 0.18f + k * 0.2f)
                        drawCircle(Color(0xFFFFE27A).copy(alpha = alpha), radius = (1.5f + (1f - k) * 2.5f) * density, center = Offset(sx, sy))
                    }
                }

                Box(
                    modifier = Modifier
                        .offset(x = ax.value.dp, y = (ay.value + bob + hop).dp)
                        .size(w, h)
                        .graphicsLayer {
                            // الصور تطير نحو اليسار أصلاً، فنعكسها عند الطيران يميناً
                            scaleX = (if (facingRight && !talking) -1f else 1f) * flutter
                            scaleY = 2f - flutter
                            rotationZ = lean + spin.value
                        }
                        .pointerInput(index) {
                            detectTapGestures(
                                onTap = {
                                    SoundEffectsHelper.playClick(view, storage.soundEnabled)
                                    talkingIndex = if (talkingIndex == index) -1 else index
                                },
                                onLongPress = {
                                    view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                    val until = endOfDay()
                                    storage.saveBubbleHiddenUntil(until)
                                    hiddenUntil = until
                                    talkingIndex = -1
                                }
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = if (talking) spec.waveRes else spec.flyRes),
                        contentDescription = spec.name,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // فقاعة الكلام
                if (talking) {
                    val bubbleW = 240.dp
                    val bx = (ax.value.dp + w / 2 - bubbleW / 2).coerceIn(8.dp, (areaW - bubbleW - 8.dp).coerceAtLeast(8.dp))
                    val by = ay.value.dp + h + 6.dp
                    Column(
                        modifier = Modifier
                            .offset(x = bx, y = by)
                            .width(bubbleW)
                            .clip(RoundedCornerShape(20.dp))
                            .background(theme.cardBg)
                            .border(1.5.dp, theme.accentColor, RoundedCornerShape(20.dp))
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(if (AssistantBrain.riderMode) spec.intro.replace("كابتن", "غالي") else spec.intro, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = textColorFor(theme), lineHeight = 21.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            PillChip(text = "💬 اسألني", selected = true, theme = theme) {
                                SoundEffectsHelper.playClick(view, storage.soundEnabled)
                                talkingIndex = -1
                                openAssistant = true
                            }
                            PillChip(text = "روحي العبي 😄", selected = false, theme = theme) {
                                SoundEffectsHelper.playClick(view, storage.soundEnabled)
                                talkingIndex = -1
                            }
                        }
                    }
                }
            }
        }
    }

    if (openAssistant) AssistantDialog(storage, theme, isEnglish) { openAssistant = false }
}

private fun endOfDay(): Long = java.util.Calendar.getInstance().apply {
    set(java.util.Calendar.HOUR_OF_DAY, 23); set(java.util.Calendar.MINUTE, 59); set(java.util.Calendar.SECOND, 59)
}.timeInMillis

@Composable
private fun AssistantDialog(storage: AppDataManager, theme: AppTheme, isEnglish: Boolean, onClose: () -> Unit) {
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
                        Text("🧚 " + (if (isEnglish) "Fairy assistant" else if (AssistantBrain.riderMode) "المساعدة" else "مساعدة الكابتن"), fontSize = 20.sp, fontWeight = FontWeight.Black, color = textColorFor(theme))
                    }
                    AssistantSection(storage, theme, isEnglish, scroll)
                }
            }
        }
    }
}
