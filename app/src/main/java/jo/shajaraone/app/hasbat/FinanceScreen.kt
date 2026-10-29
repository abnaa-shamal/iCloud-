package jo.shajaraone.app.hasbat

import jo.shajaraone.app.R
import jo.shajaraone.app.MainActivity

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DailyFinanceScreen(storage: AppDataManager, theme: AppTheme, isEnglish: Boolean, onBack: () -> Unit) {
    val view = LocalView.current
    val sound = storage.soundEnabled
    val textColor = textColorFor(theme)
    val subTextColor = subTextColorFor(theme)

    val nowMillis = System.currentTimeMillis()
    val todayDateStr = SimpleDateFormat("yyyy/MM/dd", Locale.US).format(Date(nowMillis))
    val monthPrefix = todayDateStr.substring(0, 7)
    val weekAgo = nowMillis - 7L * 24L * 3600L * 1000L

    val allTrips = remember(storage.version) { storage.getTrips() }
    val allExpenses = remember(storage.version) { storage.getExpenses() }

    // 0 = اليوم، 1 = آخر 7 أيام، 2 = هذا الشهر
    var period by remember { mutableIntStateOf(0) }
    val periodTrips = when (period) {
        0 -> allTrips.filter { it.dateStr == todayDateStr }
        1 -> allTrips.filter { it.id >= weekAgo }
        else -> allTrips.filter { it.dateStr.startsWith(monthPrefix) }
    }
    val periodExpenses = when (period) {
        0 -> allExpenses.filter { it.dateStr == todayDateStr }
        1 -> allExpenses.filter { it.id >= weekAgo }
        else -> allExpenses.filter { it.dateStr.startsWith(monthPrefix) }
    }
    val revenue = periodTrips.sumOf { it.totalFare }
    val expensesSum = periodExpenses.sumOf { it.amount }
    val net = revenue - expensesSum
    val distance = periodTrips.sumOf { it.distanceKm }

    var expenseCategory by remember { mutableStateOf("بنزين") }
    var expenseAmountText by remember { mutableStateOf("") }
    var expenseNoteText by remember { mutableStateOf("") }
    var expenseError by remember { mutableStateOf<String?>(null) }
    var pendingDelete by remember { mutableStateOf<ExpenseRecord?>(null) }
    var expenseSaved by remember { mutableStateOf(false) }
    LaunchedEffect(expenseSaved) { if (expenseSaved) { kotlinx.coroutines.delay(2500L); expenseSaved = false } }
    // 0 = صافي الربح، 1 = المصاريف
    var financeTab by remember { mutableIntStateOf(0) }
    val categories = listOf("بنزين", "زيت", "غسيل", "صيانة", "أقساط", "أخرى")

    val periodLabel = when (period) {
        0 -> if (isEnglish) "Today" else "اليوم"
        1 -> if (isEnglish) "Last 7 days" else "آخر 7 أيام"
        else -> if (isEnglish) "This month" else "هذا الشهر"
    }

    // العنوان والتبويبات ثابتة، والتمرير (إن احتاج) داخل منطقة المحتوى فقط
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                GlassIconButton(icon = if (isEnglish) "⬅️" else "➡️", theme = theme, size = 38.dp, onClick = onBack)
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(if (isEnglish) "💸 Finance" else "💸 المالية", fontSize = 19.sp, fontWeight = FontWeight.Black, color = textColor)
                    Text(if (isEnglish) "Income, expenses and net profit" else "الدخل والمصاريف وصافي الربح", fontSize = 10.sp, color = subTextColor)
                }
            }
            Text(todayDateStr, fontSize = 12.sp, color = theme.accentColor, fontWeight = FontWeight.Bold)
        }

        SegmentedTabs(
            items = listOf(
                if (isEnglish) "📊 Net profit" else "📊 صافي الربح",
                if (isEnglish) "🧾 Expenses" else "🧾 المصاريف",
                if (isEnglish) "⏰ Best times" else "⏰ أفضل وقت"
            ),
            selectedIndex = financeTab,
            theme = theme
        ) { SoundEffectsHelper.playClick(view, sound); financeTab = it }

        // أزرار الفترة الصغيرة (تظهر في التبويبين)
        val periodRow: @Composable () -> Unit = {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(if (isEnglish) "Period:" else "الفترة:", fontSize = 12.sp, color = subTextColor, fontWeight = FontWeight.Bold)
                listOf(
                    if (isEnglish) "Today" else "اليوم",
                    if (isEnglish) "7 days" else "7 أيام",
                    if (isEnglish) "Month" else "الشهر"
                ).forEachIndexed { i, label ->
                    PillChip(text = label, selected = period == i, theme = theme) { SoundEffectsHelper.playClick(view, sound); period = i }
                }
            }
        }

        Column(
            modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
        if (financeTab == 0) {
        periodRow()
        GlassCard(
            theme = theme,
            modifier = Modifier.fillMaxWidth(),
            corner = 26.dp,
            glow = theme.fareColor,
            secondGlow = theme.accentColor,
            contentPadding = 18.dp,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(if (isEnglish) "Your net profit • $periodLabel" else "صافي ربحك • $periodLabel", color = subTextColor, fontSize = 13.sp)
            Text(
                "${Fmt.money(net)} JD",
                fontSize = 36.sp,
                fontWeight = FontWeight.Black,
                color = if (net >= 0) theme.fareColor else Color(0xFFF87171),
                style = if (theme.isDark) neonTextStyle(if (net >= 0) theme.fareColor else Color(0xFFF87171)) else TextStyle.Default
            )
        }

        GlassCard(theme = theme, modifier = Modifier.fillMaxWidth(), corner = 22.dp, contentPadding = 16.dp, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SummaryRow(if (isEnglish) "🚕 Trips" else "🚕 عدد الرحلات", "${periodTrips.size}", textColor, subTextColor)
            ThinDivider(theme)
            SummaryRow(if (isEnglish) "🛣️ Distance" else "🛣️ المسافة", "${Fmt.km(distance)} ${if (isEnglish) "km" else "كم"}", textColor, subTextColor)
            ThinDivider(theme)
            SummaryRow(if (isEnglish) "💰 Income" else "💰 الدخل", "${Fmt.money(revenue)} JD", textColor, subTextColor)
            ThinDivider(theme)
            SummaryRow(if (isEnglish) "🧾 Expenses" else "🧾 المصاريف", "- ${Fmt.money(expensesSum)} JD", Color(0xFFEF4444), subTextColor)
            if (periodTrips.isNotEmpty()) {
                ThinDivider(theme)
                SummaryRow(if (isEnglish) "📈 Average trip" else "📈 متوسط الرحلة", "${Fmt.money(revenue / periodTrips.size)} JD", textColor, subTextColor)
            }
        }

        CharityCard(storage, theme, isEnglish, allTrips, allExpenses, todayDateStr, monthPrefix)
        }

        if (financeTab == 2) {
            BestTimesContent(allTrips, theme, isEnglish)
        }

        if (financeTab == 1) {

        GlassCard(theme = theme, modifier = Modifier.fillMaxWidth(), corner = 22.dp, contentPadding = 16.dp, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(if (isEnglish) "➕ Add expense" else "➕ تسجيل مصروف", fontWeight = FontWeight.Black, fontSize = 14.sp, color = textColor)
            categories.chunked(3).forEach { rowCats ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    rowCats.forEach { cat ->
                        PillChip(
                            text = Fmt.expenseCategory(cat, isEnglish),
                            selected = (expenseCategory == cat),
                            theme = theme,
                            modifier = Modifier.weight(1f)
                        ) { SoundEffectsHelper.playClick(view, sound); expenseCategory = cat }
                    }
                }
            }
            OutlinedTextField(
                value = expenseAmountText,
                onValueChange = { expenseAmountText = it; expenseError = null },
                label = { Text(if (isEnglish) "Amount (JD)" else "المبلغ (د.أ)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                colors = neonFieldColors(theme)
            )
            OutlinedTextField(
                value = expenseNoteText,
                onValueChange = { expenseNoteText = it.take(80) },
                label = { Text(if (isEnglish) "Note (optional)" else "ملاحظة (اختياري)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                colors = neonFieldColors(theme)
            )
            expenseError?.let { Text(it, color = Color(0xFFEF4444), fontSize = 12.sp, fontWeight = FontWeight.Bold) }
            if (expenseSaved) Text(if (isEnglish) "✅ Expense saved" else "✅ تم حفظ المصروف", color = Color(0xFF22C55E), fontSize = 12.sp, fontWeight = FontWeight.Bold)
            NeonActionButton(
                text = if (isEnglish) "Save Expense" else "حفظ المصروف",
                colors = listOf(theme.accentColor, theme.needleColor),
                modifier = Modifier.fillMaxWidth(),
                height = 50.dp,
                textSize = 15.sp
            ) {
                SoundEffectsHelper.playClick(view, sound)
                val amt = NumberInput.parse(expenseAmountText)
                if (amt != null && amt > 0.0 && amt < 100_000.0) {
                    storage.addExpense(ExpenseRecord(System.currentTimeMillis(), todayDateStr, expenseCategory, AppDataManager.round3(amt), expenseNoteText.trim()))
                    expenseAmountText = ""
                    expenseNoteText = ""
                    expenseError = null
                    expenseSaved = true
                } else {
                    expenseError = if (isEnglish) "⚠️ Enter a valid amount." else "⚠️ أدخل مبلغاً صحيحاً."
                }
            }
        }

        periodRow()
        if (periodExpenses.isEmpty()) {
            Text(
                if (isEnglish) "No expenses in this period. Add your first expense above." else "لا توجد مصاريف في هذه الفترة. سجّل أول مصروف من الأعلى.",
                fontSize = 13.sp, color = subTextColor
            )
        }
        periodExpenses.forEach { exp ->
            GlassCard(theme = theme, modifier = Modifier.fillMaxWidth(), corner = 18.dp, glow = Color(0xFFEF4444), secondGlow = theme.accentColor, contentPadding = 10.dp) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(Fmt.expenseCategory(exp.category, isEnglish), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = textColor)
                        Text(if (exp.note.isNotBlank()) "${exp.note} • ${exp.dateStr}" else exp.dateStr, fontSize = 11.sp, color = subTextColor)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("- ${Fmt.money(exp.amount)} JD", color = Color(0xFFEF4444), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Spacer(modifier = Modifier.width(8.dp))
                        GlassIconButton(icon = "🗑️", theme = theme, size = 34.dp) {
                            SoundEffectsHelper.playClick(view, sound)
                            pendingDelete = exp
                        }
                    }
                }
            }
        }
        }
        }
    }

    pendingDelete?.let { exp ->
        ConfirmDialog(
            theme = theme,
            title = if (isEnglish) "Delete expense?" else "حذف المصروف؟",
            message = if (isEnglish) "${Fmt.expenseCategory(exp.category, true)} • ${Fmt.money(exp.amount)} JD will be deleted."
            else "سيتم حذف ${exp.category} بقيمة ${Fmt.money(exp.amount)} د.أ.",
            confirmText = if (isEnglish) "Delete" else "حذف",
            dismissText = if (isEnglish) "Cancel" else "إلغاء",
            onConfirm = { storage.deleteExpense(exp.id); pendingDelete = null },
            onDismiss = { pendingDelete = null }
        )
    }
}

@Composable
fun SummaryRow(label: String, value: String, valueColor: Color, labelColor: Color) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontSize = 13.sp, color = labelColor)
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = valueColor)
    }
}

// ======================================================================
// أفضل أوقاتك: متى تشتغل لتربح أكثر (من سجل آخر 60 يوماً)
// ======================================================================
@Composable
fun BestTimesContent(allTrips: List<TripRecord>, theme: AppTheme, isEnglish: Boolean) {
    val textColor = textColorFor(theme)
    val subTextColor = subTextColorFor(theme)
    val since = System.currentTimeMillis() - 60L * 24L * 3600L * 1000L
    val trips = remember(allTrips) { allTrips.filter { it.id >= since } }
    val days = remember(trips, isEnglish) { BestTimes.byDay(trips, isEnglish) }
    val periods = remember(trips, isEnglish) { BestTimes.byPeriod(trips, isEnglish) }
    val perHour = remember(trips) { BestTimes.incomePerDrivingHour(trips) }

    if (trips.size < 5) {
        GlassCard(theme = theme, modifier = Modifier.fillMaxWidth(), corner = 22.dp, contentPadding = 20.dp, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("⏰", fontSize = 38.sp)
            Text(
                if (isEnglish) "Record a few more trips (at least 5) and you'll see your best days and hours here."
                else "سجّل بعض الرحلات (5 على الأقل) وستظهر هنا أفضل أيامك وساعاتك.",
                fontSize = 14.sp, color = subTextColor, textAlign = TextAlign.Center, lineHeight = 22.sp
            )
        }
        return
    }

    val bestDay = days.maxByOrNull { it.income }
    val bestPeriod = periods.maxByOrNull { it.income }

    GlassCard(theme = theme, modifier = Modifier.fillMaxWidth(), corner = 24.dp, glow = theme.fareColor, contentPadding = 16.dp, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(if (isEnglish) "🏆 Your best" else "🏆 الأفضل لك", fontSize = 16.sp, fontWeight = FontWeight.Black, color = textColor)
        if (bestDay != null && bestDay.income > 0.0) SummaryRow(if (isEnglish) "📅 Best day:" else "📅 أفضل يوم:", bestDay.label, theme.fareColor, subTextColor)
        if (bestPeriod != null && bestPeriod.income > 0.0) SummaryRow(if (isEnglish) "🕒 Best time:" else "🕒 أفضل وقت:", bestPeriod.label, theme.fareColor, subTextColor)
        if (perHour > 0.0) SummaryRow(if (isEnglish) "💵 Income per driving hour:" else "💵 دخل ساعة القيادة:", "${Fmt.money(perHour)} ${if (isEnglish) "JD" else "د.أ"}", textColor, subTextColor)
        Text(if (isEnglish) "Based on the last 60 days." else "حسب آخر 60 يوماً.", fontSize = 11.sp, color = subTextColor)
    }

    BestTimesBars(if (isEnglish) "📅 By day" else "📅 حسب اليوم", days, theme, isEnglish)
    BestTimesBars(if (isEnglish) "🕒 By time of day" else "🕒 حسب الفترة", periods, theme, isEnglish)
}

@Composable
private fun BestTimesBars(title: String, buckets: List<BestTimes.Bucket>, theme: AppTheme, isEnglish: Boolean) {
    val textColor = textColorFor(theme)
    val subTextColor = subTextColorFor(theme)
    val max = (buckets.maxOfOrNull { it.income } ?: 0.0).coerceAtLeast(0.01)
    GlassCard(theme = theme, modifier = Modifier.fillMaxWidth(), corner = 22.dp, contentPadding = 16.dp, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, fontSize = 15.sp, fontWeight = FontWeight.Black, color = textColor)
        buckets.forEach { b ->
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(b.label, modifier = Modifier.width(96.dp), fontSize = 12.sp, color = textColor, maxLines = 1)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(14.dp)
                        .clip(RoundedCornerShape(7.dp))
                        .background(if (theme.isDark) Color.White.copy(alpha = 0.07f) else Color.Black.copy(alpha = 0.06f))
                ) {
                    val fraction = (b.income / max).toFloat().coerceIn(0f, 1f)
                    if (fraction > 0f) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(fraction)
                                .height(14.dp)
                                .clip(RoundedCornerShape(7.dp))
                                .background(theme.accentColor)
                        )
                    }
                }
                Text(
                    Fmt.money(b.income),
                    modifier = Modifier.width(58.dp),
                    fontSize = 12.sp, fontWeight = FontWeight.Bold, color = subTextColor, textAlign = TextAlign.End
                )
            }
        }
        Text(if (isEnglish) "Income (JD) • trips counted: ${buckets.sumOf { it.trips }}" else "الدخل بالدينار • عدد الرحلات: ${buckets.sumOf { it.trips }}", fontSize = 11.sp, color = subTextColor)
    }
}

// ======================================================================
// صدقة اليوم: نسبة يختارها الكابتن من صافي ربح اليوم
// ======================================================================
@Composable
fun CharityCard(
    storage: AppDataManager,
    theme: AppTheme,
    isEnglish: Boolean,
    allTrips: List<TripRecord>,
    allExpenses: List<ExpenseRecord>,
    todayDateStr: String,
    monthPrefix: String
) {
    val view = LocalView.current
    val sound = storage.soundEnabled
    val textColor = textColorFor(theme)
    val subTextColor = subTextColorFor(theme)
    @Suppress("UNUSED_VARIABLE") val v = storage.version // لإعادة الرسم بعد الحفظ
    val percent = storage.getCharityPercent()
    val todayNet = allTrips.filter { it.dateStr == todayDateStr }.sumOf { it.totalFare } -
        allExpenses.filter { it.dateStr == todayDateStr }.sumOf { it.amount }
    val charity = AppDataManager.round3((todayNet * percent / 100.0).coerceAtLeast(0.0))
    val paidToday = storage.getCharityPaid(todayDateStr)
    val monthTotal = storage.getCharityMonthTotal(monthPrefix)

    GlassCard(theme = theme, modifier = Modifier.fillMaxWidth(), corner = 22.dp, glow = Color(0xFF22C55E), contentPadding = 16.dp, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(if (isEnglish) "🤲 Today's charity" else "🤲 صدقة اليوم", fontSize = 16.sp, fontWeight = FontWeight.Black, color = textColor)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(if (isEnglish) "Share:" else "النسبة:", fontSize = 12.sp, color = subTextColor, fontWeight = FontWeight.Bold)
            (1..5).forEach { p ->
                PillChip(text = "$p%", selected = p == percent, theme = theme, modifier = Modifier.weight(1f)) {
                    SoundEffectsHelper.playClick(view, sound); storage.saveCharityPercent(p)
                }
            }
        }
        SummaryRow(
            if (isEnglish) "Amount ($percent% of today's net):" else "المبلغ ($percent% من صافي اليوم):",
            "${Fmt.money(charity)} ${if (isEnglish) "JD" else "د.أ"}",
            Color(0xFF22C55E), subTextColor
        )
        if (paidToday > 0.0) {
            Text(
                if (isEnglish) "✅ Done today: ${Fmt.money(paidToday)} JD — may God accept it 🌿" else "✅ تصدّقت اليوم بـ ${Fmt.money(paidToday)} د.أ — تقبّل الله منك 🌿",
                fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF22C55E)
            )
        } else if (charity > 0.0) {
            NeonActionButton(
                text = if (isEnglish) "✅ I gave it" else "✅ تم، تصدّقت",
                colors = listOf(Color(0xFF16A34A), Color(0xFF22C55E)),
                modifier = Modifier.fillMaxWidth(),
                height = 48.dp,
                textSize = 14.sp
            ) { SoundEffectsHelper.playClick(view, sound); storage.saveCharityPaid(todayDateStr, charity) }
        } else {
            Text(
                if (isEnglish) "It will be calculated after today's first trip." else "يُحسب المبلغ بعد أول رحلة اليوم.",
                fontSize = 12.sp, color = subTextColor
            )
        }
        if (monthTotal > 0.0) {
            Text(
                if (isEnglish) "This month: ${Fmt.money(monthTotal)} JD" else "مجموع صدقاتك هذا الشهر: ${Fmt.money(monthTotal)} د.أ",
                fontSize = 12.sp, color = subTextColor
            )
        }
    }
}
