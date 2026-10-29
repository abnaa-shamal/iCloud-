package jo.shajaraone.app.hasbat

import jo.shajaraone.app.R
import jo.shajaraone.app.MainActivity

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.round

@Composable
fun CalculatorAndRatesScreen(storage: AppDataManager, theme: AppTheme, isEnglish: Boolean, onBack: () -> Unit) {
    val view = LocalView.current
    val sound = storage.soundEnabled
    val textColor = textColorFor(theme)
    val subTextColor = subTextColorFor(theme)

    val presets = remember(storage.version) { storage.getPresets() }
    val selected = presets.firstOrNull { it.id == storage.selectedPresetId } ?: presets.first()
    var calcTab by remember { mutableIntStateOf(0) } // 0 = الحاسبة، 1 = أسعار التعرفة

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            GlassIconButton(icon = if (isEnglish) "⬅️" else "➡️", theme = theme, size = 38.dp, onClick = onBack)
            Spacer(modifier = Modifier.width(10.dp))
            Text(if (isEnglish) "🧮 Calculator" else "🧮 الحاسبة", fontSize = 20.sp, fontWeight = FontWeight.Black, color = textColor)
        }

        SegmentedTabs(
            items = listOf(if (isEnglish) "🧮 Calculator" else "🧮 الحاسبة", if (isEnglish) "🏷️ Tariff prices" else "🏷️ أسعار التعرفة"),
            selectedIndex = calcTab,
            theme = theme
        ) { SoundEffectsHelper.playClick(view, sound); calcTab = it }

        if (calcTab == 0) {
            EstimateCalculator(storage, theme, isEnglish, presets, selected, textColor, subTextColor)
        } else {
            TariffPresetsEditor(storage, theme, isEnglish, presets, selected, textColor, subTextColor)
        }
    }
}

@Composable
private fun EstimateCalculator(
    storage: AppDataManager, theme: AppTheme, isEnglish: Boolean,
    presets: List<TariffPreset>, selected: TariffPreset, textColor: Color, subTextColor: Color
) {
    val view = LocalView.current
    val sound = storage.soundEnabled
    var calcDist by remember { mutableStateOf("") }
    var calcTimeMinutes by remember { mutableStateOf("") }

    val dist = NumberInput.parse(calcDist)?.coerceAtLeast(0.0) ?: 0.0
    val mins = NumberInput.parse(calcTimeMinutes)?.coerceAtLeast(0.0) ?: 0.0
    val tariff = TripTariff(selected.name, selected.base, selected.km, selected.min)
    val calcTotal = if (dist > 0.0 || mins > 0.0) FareCalculator.displayed(tariff, dist, (mins * 60.0).toLong()) else 0.0

    GlassCard(
        theme = theme,
        modifier = Modifier.fillMaxWidth(),
        corner = 26.dp,
        glow = Color(0xFF16A34A),
        secondGlow = theme.accentColor,
        contentPadding = 18.dp,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(if (isEnglish) "Estimated trip calculator" else "حاسبة الرحلة التقديرية", fontWeight = FontWeight.Black, fontSize = 16.sp, color = textColor)

        ScrollableChipRow(items = presets.map { it.name }, selectedIndex = presets.indexOf(selected), theme = theme) {
            SoundEffectsHelper.playClick(view, sound); storage.selectPreset(presets[it].id)
        }

        OutlinedTextField(
            value = calcDist,
            onValueChange = { calcDist = it },
            label = { Text(if (isEnglish) "🛣️ Distance (km)" else "🛣️ المسافة (كيلومتر)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            colors = neonFieldColors(theme)
        )
        OutlinedTextField(
            value = calcTimeMinutes,
            onValueChange = { calcTimeMinutes = it },
            label = { Text(if (isEnglish) "⏱️ Time (minutes)" else "⏱️ الوقت (بالدقائق)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            colors = neonFieldColors(theme)
        )

        ThinDivider(theme)

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(if (theme.isDark) Color.White.copy(alpha = 0.05f) else Color.Black.copy(alpha = 0.04f))
                .border(1.5.dp, Brush.horizontalGradient(listOf(Color(0xFF22C55E), theme.fareColor)), RoundedCornerShape(20.dp))
                .padding(vertical = 18.dp, horizontal = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(if (isEnglish) "ESTIMATED FARE" else "الأجرة التقديرية للرحلة", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = subTextColor)
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = Fmt.money(calcTotal),
                    fontSize = 54.sp,
                    fontWeight = FontWeight.Black,
                    color = theme.fareColor,
                    fontFamily = FontFamily.Monospace,
                    style = if (theme.isDark) neonTextStyle(theme.fareColor, 24f, 0.7f) else TextStyle.Default
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("JD", fontSize = 20.sp, fontWeight = FontWeight.Black, color = Color(0xFF22C55E), modifier = Modifier.padding(bottom = 10.dp))
            }
            Text(
                text = if (isEnglish) "${selected.name}: Base ${Fmt.rate(selected.base)} | Km ${Fmt.rate(selected.km)} | Min ${Fmt.rate(selected.min)}"
                else "${selected.name}: فتحة ${Fmt.rate(selected.base)} | كم ${Fmt.rate(selected.km)} | دقيقة ${Fmt.rate(selected.min)}",
                fontSize = 11.sp,
                color = subTextColor
            )
            Text(
                if (isEnglish) "Minimum fare: ${Fmt.money(FareCalculator.MIN_FARE)} JD" else "الحد الأدنى للرحلة: ${Fmt.money(FareCalculator.MIN_FARE)} د.أ",
                fontSize = 10.sp, color = subTextColor
            )
        }
    }
}

/** قسم «أسعار التعرفة»: يُعرض داخل الإعدادات. */
@Composable
fun TariffPresetsEditor(
    storage: AppDataManager, theme: AppTheme, isEnglish: Boolean,
    presets: List<TariffPreset>, selected: TariffPreset, textColor: Color, subTextColor: Color
) {
    val view = LocalView.current
    val sound = storage.soundEnabled
    // التعرفة الخاصة التي تُعدَّل يدوياً (الفئات الثابتة لا تُعدَّل)
    val customPresets = presets.filter { !AppDataManager.isTier(it.id) }
    val editing: TariffPreset? = if (AppDataManager.isTier(selected.id)) null else selected
    var nameText by remember(selected.id) { mutableStateOf(selected.name) }
    var info by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    LaunchedEffect(info) {
        if (info != null) { kotlinx.coroutines.delay(3000L); info = null }
    }

    // أثناء الرحلة: تعديل الأسعار مقفل حتى تنتهي الرحلة
    if (MeterEngine.isTripActive) {
        GlassCard(theme = theme, modifier = Modifier.fillMaxWidth(), corner = 24.dp, contentPadding = 16.dp, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(if (isEnglish) "🔒 Tariff prices are locked" else "🔒 تعديل أسعار التعرفة مقفل", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = textColor)
            Text(
                if (isEnglish) "You can't change prices during a trip. Finish the trip first, then edit."
                else "لا يمكن تعديل الأسعار أثناء رحلة جارية. أنهِ الرحلة أولاً، ثم عدّل.",
                fontSize = 12.sp, color = subTextColor, lineHeight = 19.sp
            )
        }
        return
    }

    GlassCard(theme = theme, modifier = Modifier.fillMaxWidth(), corner = 24.dp, contentPadding = 16.dp, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(if (isEnglish) "🏷️ Tariff prices — the selected one is used by the meter" else "🏷️ أسعار التعرفة — المختارة هي المعتمدة في العداد", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = textColor)
        // الفئات الثلاث الثابتة
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AppDataManager.TIERS.forEach { tier ->
                TariffTierCard(
                    tier = tier,
                    selected = selected.id == tier.id,
                    theme = theme,
                    isEnglish = isEnglish,
                    textColor = textColor,
                    subTextColor = subTextColor,
                    modifier = Modifier.weight(1f)
                ) {
                    SoundEffectsHelper.playClick(view, sound); storage.selectPreset(tier.id)
                }
            }
        }
        Text(
            if (isEnglish) "Changes are saved automatically and never affect a running trip."
            else "التعديلات تُحفظ تلقائياً، ولا تؤثر على الرحلة الجارية.",
            fontSize = 11.sp, color = subTextColor
        )
        ThinDivider(theme)
        Text(if (isEnglish) "✏️ Manual tariff (optional)" else "✏️ تعرفة يدوية (اختياري)", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = textColor)
        if (customPresets.isNotEmpty()) {
            ScrollableChipRow(items = customPresets.map { it.name }, selectedIndex = customPresets.indexOfFirst { it.id == selected.id }, theme = theme) {
                SoundEffectsHelper.playClick(view, sound); storage.selectPreset(customPresets[it].id)
            }
        }
        if (editing == null) {
            Text(
                if (isEnglish) "To use your own prices, pick a manual tariff above or add a new one."
                else "لتستخدم أسعاراً خاصة بك، اختر تعرفة يدوية من الأعلى أو أضف تعرفة جديدة.",
                fontSize = 12.sp, color = subTextColor, lineHeight = 19.sp
            )
        }

        if (editing != null) {
        OutlinedTextField(
            value = nameText,
            onValueChange = { v ->
                nameText = v.take(24)
                if (nameText.isNotBlank()) storage.savePreset(editing.copy(name = nameText))
            },
            label = { Text(if (isEnglish) "Tariff name (e.g. Normal, Highest)" else "اسم التعرفة (مثلاً: عادي، الأعلى)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            colors = neonFieldColors(theme)
        )

        PennyStepperRow(if (isEnglish) "Base fare" else "فتحة العداد", selected.base, 0.005, AppDataManager.MIN_BASE, AppDataManager.MAX_BASE, theme, sound) {
            storage.savePreset(editing.copy(base = it))
        }
        ThinDivider(theme)
        PennyStepperRow(if (isEnglish) "Per km" else "سعر الكيلومتر", selected.km, 0.005, AppDataManager.MIN_KM, AppDataManager.MAX_KM, theme, sound) {
            storage.savePreset(editing.copy(km = it))
        }
        ThinDivider(theme)
        PennyStepperRow(if (isEnglish) "Per minute" else "سعر الدقيقة", selected.min, 0.005, AppDataManager.MIN_MIN, AppDataManager.MAX_MIN, theme, sound) {
            storage.savePreset(editing.copy(min = it))
        }
        } // نهاية التعديل اليدوي

        info?.let { Text(it, color = Color(0xFF16A34A), fontSize = 12.sp, fontWeight = FontWeight.Bold) }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            NeonActionButton(
                text = if (isEnglish) "➕ New tariff" else "➕ تعرفة جديدة",
                colors = listOf(theme.accentColor, theme.needleColor),
                modifier = Modifier.weight(1f),
                height = 50.dp,
                textSize = 14.sp
            ) {
                SoundEffectsHelper.playClick(view, sound)
                val created = storage.addPreset(if (isEnglish) "Tariff ${customPresets.size + 1}" else "تعرفة ${customPresets.size + 1}")
                storage.selectPreset(created.id)
                info = if (isEnglish) "New tariff added — rename it above." else "تمت إضافة تعرفة جديدة، غيّر اسمها من الأعلى."
            }
            if (editing != null) {
                NeonActionButton(
                    text = if (isEnglish) "🗑️ Delete" else "🗑️ حذف",
                    colors = listOf(Color(0xFFEF4444), Color(0xFFFF6B8A)),
                    modifier = Modifier.weight(0.7f),
                    height = 50.dp,
                    textSize = 14.sp
                ) {
                    SoundEffectsHelper.playClick(view, sound)
                    confirmDelete = true
                }
            }
        }
    }

    if (confirmDelete) {
        ConfirmDialog(
            theme = theme,
            title = if (isEnglish) "Delete tariff?" else "حذف التعرفة؟",
            message = if (isEnglish) "\"${selected.name}\" will be deleted. Saved trips are not affected." else "سيتم حذف \"${selected.name}\". الرحلات المسجلة لا تتأثر.",
            confirmText = if (isEnglish) "Delete" else "حذف",
            dismissText = if (isEnglish) "Cancel" else "إلغاء",
            onConfirm = { storage.deletePreset(selected.id); confirmDelete = false },
            onDismiss = { confirmDelete = false }
        )
    }
}

/** بطاقة فئة تعرفة ثابتة (Saver / Electric / Comfort) */
@Composable
fun TariffTierCard(
    tier: TariffPreset, selected: Boolean, theme: AppTheme, isEnglish: Boolean,
    textColor: Color, subTextColor: Color, modifier: Modifier = Modifier, onClick: () -> Unit
) {
    val (color, icon) = when (tier.id) {
        AppDataManager.TIER_SAVER_ID -> Color(0xFF2563EB) to "💧"
        AppDataManager.TIER_ELECTRIC_ID -> Color(0xFF7C3AED) to "⚡"
        else -> Color(0xFFEAB308) to "🚕"
    }
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier = modifier
            .bounceClick(onClick = onClick)
            .clip(shape)
            .background(
                if (selected) Brush.verticalGradient(listOf(color.copy(alpha = 0.30f), color.copy(alpha = 0.06f)))
                else Brush.verticalGradient(listOf(
                    if (theme.isDark) Color.White.copy(alpha = 0.05f) else Color.Black.copy(alpha = 0.04f),
                    if (theme.isDark) Color.White.copy(alpha = 0.03f) else Color.Black.copy(alpha = 0.02f)
                ))
            )
            .border(if (selected) 2.dp else 1.dp, if (selected) color else theme.accentColor.copy(alpha = 0.25f), shape)
            .padding(horizontal = 6.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(modifier = Modifier.size(34.dp).clip(CircleShape).background(color), contentAlignment = Alignment.Center) {
            Text(icon, fontSize = 16.sp)
        }
        Text(tier.name, fontSize = 15.sp, fontWeight = FontWeight.Black, color = textColor, maxLines = 1)
        val f = { v: Double -> String.format(java.util.Locale.US, "%.3f", v) }
        Text(
            if (isEnglish) "Base ${f(tier.base)}\nKm ${f(tier.km)}\nMin ${f(tier.min)}"
            else "فتحة ${f(tier.base)}\nكم ${f(tier.km)}\nدقيقة ${f(tier.min)}",
            fontSize = 11.sp, color = subTextColor, lineHeight = 17.sp, textAlign = TextAlign.Center
        )
        Text(
            if (selected) (if (isEnglish) "✓ In use" else "✓ المعتمدة") else " ",
            fontSize = 10.sp, fontWeight = FontWeight.Bold, color = color
        )
    }
}

@Composable
fun PennyCircleButton(label: String, enabled: Boolean, filled: Boolean, theme: AppTheme, onClick: () -> Unit) {
    val textColor = textColorFor(theme)
    Box(
        modifier = Modifier
            .size(42.dp)
            .graphicsLayer { alpha = if (enabled) 1f else 0.35f }
            .bounceClick(enabled = enabled, onClick = onClick)
            .clip(CircleShape)
            .background(
                if (filled) Brush.linearGradient(listOf(theme.accentColor, theme.needleColor))
                else Brush.linearGradient(
                    listOf(
                        if (theme.isDark) Color.White.copy(alpha = 0.10f) else Color.Black.copy(alpha = 0.07f),
                        if (theme.isDark) Color.White.copy(alpha = 0.04f) else Color.Black.copy(alpha = 0.03f)
                    )
                )
            )
            .border(1.dp, if (filled) Color.White.copy(alpha = 0.35f) else theme.accentColor.copy(alpha = 0.45f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(label, fontSize = 22.sp, fontWeight = FontWeight.Black, color = if (filled) readableOn(theme.accentColor) else textColor)
    }
}

/** ضبط السعر بخطوات صغيرة (5 فلوس) ضمن الحدود المسموحة. */
@Composable
fun PennyStepperRow(title: String, value: Double, step: Double, min: Double, max: Double, theme: AppTheme, soundEnabled: Boolean, onValueChange: (Double) -> Unit) {
    val view = LocalView.current
    val textColor = textColorFor(theme)
    fun r3(v: Double) = round(v * 1000.0) / 1000.0
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Column {
            Text(title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = textColor)
            Text(
                "${Fmt.rate(value)} JD",
                fontSize = 18.sp,
                fontWeight = FontWeight.Black,
                color = theme.fareColor,
                style = if (theme.isDark) neonTextStyle(theme.fareColor, 14f, 0.45f) else TextStyle.Default
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            PennyCircleButton(label = "-", enabled = value > min + 1e-9, filled = false, theme = theme) {
                SoundEffectsHelper.playClick(view, soundEnabled); onValueChange(r3((value - step).coerceAtLeast(min)))
            }
            PennyCircleButton(label = "+", enabled = value < max - 1e-9, filled = true, theme = theme) {
                SoundEffectsHelper.playClick(view, soundEnabled); onValueChange(r3((value + step).coerceAtMost(max)))
            }
        }
    }
}
