package jo.shajaraone.app.hasbat

import jo.shajaraone.app.R
import jo.shajaraone.app.MainActivity

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

// ======================================================================
// شاشة التحدي اليومي (الهدف 5-100 دينار) مع سجل الإنجاز الشهري
// ======================================================================
private val OkGreen = Color(0xFF22C55E)
private val PartGold = Color(0xFFE8B84A)
private val MissRed = Color(0xFF7A2E2E)

@Composable
fun ChallengeScreen(storage: AppDataManager, theme: AppTheme, isEnglish: Boolean, onBack: () -> Unit) {
    val view = LocalView.current
    val sound = storage.soundEnabled
    val textColor = textColorFor(theme)
    val subTextColor = subTextColorFor(theme)

    var challengeTab by remember { mutableIntStateOf(0) } // 0 = تحدي اليوم، 1 = سجل الإنجاز
    var targetAmount by remember { mutableDoubleStateOf(storage.getChallengeTarget(0)) }
    var inputAmountText by remember {
        mutableStateOf(storage.getChallengeTarget(0).let { if (it == Math.floor(it)) String.format(Locale.US, "%.0f", it) else Fmt.money(it) })
    }
    var feedbackMessage by remember { mutableStateOf<String?>(null) }
    var isErrorMsg by remember { mutableStateOf(false) }

    val dayFormat = remember { SimpleDateFormat("yyyy/MM/dd", Locale.US) }
    val allTrips = remember(storage.version) { storage.getTrips() }
    // مجموع الدخل لكل يوم
    val dayTotals = remember(allTrips) {
        allTrips.groupBy { it.dateStr }.mapValues { e -> e.value.sumOf { it.totalFare } }
    }
    val todayDateStr = dayFormat.format(Date(System.currentTimeMillis()))
    val achievedAmount = dayTotals[todayDateStr] ?: 0.0

    // سلسلة الأيام المتتالية التي تحقق فيها الهدف
    val streak = remember(dayTotals, targetAmount, todayDateStr) {
        var count = 0
        val cal = Calendar.getInstance()
        if ((dayTotals[todayDateStr] ?: 0.0) < targetAmount) cal.add(Calendar.DAY_OF_YEAR, -1)
        while (count < 3650 && (dayTotals[dayFormat.format(cal.time)] ?: 0.0) >= targetAmount && targetAmount > 0.0) {
            count++
            cal.add(Calendar.DAY_OF_YEAR, -1)
        }
        count
    }

    val remainingAmount = (targetAmount - achievedAmount).coerceAtLeast(0.0)
    val rawProgress = if (targetAmount > 0.0) (achievedAmount / targetAmount).toFloat() else 0f
    val animatedProgress by animateFloatAsState(
        targetValue = rawProgress.coerceIn(0f, 1f),
        animationSpec = tween(650, easing = FastOutSlowInEasing),
        label = "challenge_progress"
    )
    val isChallengeCompleted = achievedAmount >= targetAmount && targetAmount > 0.0
    val streakText = if (isEnglish) "🔥 $streak-day streak" else "🔥 سلسلة $streak ${if (streak in 3..10) "أيام" else "يوم"}"

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            GlassIconButton(icon = if (isEnglish) "⬅️" else "➡️", theme = theme, size = 38.dp, onClick = onBack)
            Spacer(modifier = Modifier.width(10.dp))
            Text(if (isEnglish) "🏆 Daily Challenge" else "🏆 التحدي اليومي", fontSize = 21.sp, fontWeight = FontWeight.Black, color = textColor)
        }

        SegmentedTabs(
            items = listOf(if (isEnglish) "🎯 Today" else "🎯 تحدي اليوم", if (isEnglish) "📅 Achievements" else "📅 سجل الإنجاز"),
            selectedIndex = challengeTab,
            theme = theme
        ) { SoundEffectsHelper.playClick(view, sound); challengeTab = it }

        Column(
            modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (challengeTab == 0) {
                // ---------- بطاقة الحلقة ----------
                GlassCard(
                    theme = theme,
                    modifier = Modifier.fillMaxWidth(),
                    corner = 24.dp,
                    glow = if (isChallengeCompleted) OkGreen else theme.fareColor,
                    animatedBorder = isChallengeCompleted,
                    contentPadding = 14.dp
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val ringColor = if (isChallengeCompleted) OkGreen else theme.fareColor
                        val trackColor = if (theme.isDark) Color.White.copy(alpha = 0.10f) else Color.Black.copy(alpha = 0.08f)
                        Box(modifier = Modifier.size(120.dp), contentAlignment = Alignment.Center) {
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                val strokePx = 11.dp.toPx()
                                if (size.minDimension <= strokePx * 2) return@Canvas
                                drawArc(
                                    color = trackColor, startAngle = 0f, sweepAngle = 360f, useCenter = false, style = Stroke(width = strokePx),
                                    topLeft = androidx.compose.ui.geometry.Offset(strokePx / 2, strokePx / 2),
                                    size = androidx.compose.ui.geometry.Size(size.width - strokePx, size.height - strokePx)
                                )
                                drawArc(
                                    color = ringColor, startAngle = -90f, sweepAngle = 360f * animatedProgress, useCenter = false,
                                    style = Stroke(width = strokePx, cap = StrokeCap.Round),
                                    topLeft = androidx.compose.ui.geometry.Offset(strokePx / 2, strokePx / 2),
                                    size = androidx.compose.ui.geometry.Size(size.width - strokePx, size.height - strokePx)
                                )
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(Fmt.money(achievedAmount), fontSize = 22.sp, fontWeight = FontWeight.Black, color = ringColor, fontFamily = FontFamily.Monospace)
                                Text(if (isEnglish) "of ${Fmt.money(targetAmount)} JD" else "من ${Fmt.money(targetAmount)} د.أ", fontSize = 11.sp, color = subTextColor)
                            }
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            val pct = (rawProgress * 100f).toInt().coerceAtMost(999)
                            Text((if (isEnglish) "Achieved: " else "الإنجاز: ") + "$pct%", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = textColor)
                            Text(
                                text = if (isChallengeCompleted) (if (isEnglish) "🎉 Goal reached!" else "🎉 تحقق الهدف!")
                                else (if (isEnglish) "Remaining: ${Fmt.money(remainingAmount)} JD" else "المتبقي: ${Fmt.money(remainingAmount)} د.أ"),
                                fontSize = 15.sp, fontWeight = FontWeight.Bold,
                                color = if (isChallengeCompleted) OkGreen else textColor
                            )
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(theme.accentColor)
                                    .padding(horizontal = 12.dp, vertical = 5.dp)
                            ) { Text(streakText, fontSize = 13.sp, fontWeight = FontWeight.Black, color = readableOn(theme.accentColor)) }
                        }
                    }
                }

                // ---------- بطاقة الهدف ----------
                GlassCard(theme = theme, modifier = Modifier.fillMaxWidth(), corner = 24.dp, contentPadding = 14.dp, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(if (isEnglish) "🎯 My daily goal" else "🎯 هدفي اليومي", fontWeight = FontWeight.Black, fontSize = 15.sp, color = textColor)
                    listOf(15, 25, 30, 50, 75, 100).chunked(3).forEach { rowAmounts ->
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            rowAmounts.forEach { amt ->
                                PillChip(text = "$amt", selected = targetAmount == amt.toDouble(), theme = theme, modifier = Modifier.weight(1f)) {
                                    SoundEffectsHelper.playClick(view, sound)
                                    inputAmountText = amt.toString()
                                    targetAmount = amt.toDouble()
                                    storage.saveChallengeTarget(0, amt.toDouble())
                                    isErrorMsg = false
                                    feedbackMessage = if (isEnglish) "✅ Goal set: $amt JD" else "✅ تم تثبيت الهدف: $amt دينار"
                                }
                            }
                        }
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = inputAmountText,
                            onValueChange = { inputAmountText = it },
                            label = { Text(if (isEnglish) "Other amount (5 - 100)" else "مبلغ آخر (5 - 100)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            colors = neonFieldColors(theme)
                        )
                        NeonActionButton(
                            text = if (isEnglish) "Save" else "حفظ",
                            colors = listOf(theme.accentColor, theme.needleColor),
                            modifier = Modifier.width(90.dp).padding(top = 6.dp),
                            height = 52.dp,
                            textSize = 15.sp
                        ) {
                            SoundEffectsHelper.playClick(view, sound)
                            val parsed = NumberInput.parse(inputAmountText)
                            if (parsed == null || parsed < 5.0 || parsed > 100.0) {
                                isErrorMsg = true
                                feedbackMessage = if (isEnglish) "⚠️ Daily goal must be between 5 and 100 JD." else "⚠️ الهدف اليومي من 5 إلى 100 دينار فقط."
                            } else {
                                isErrorMsg = false
                                targetAmount = AppDataManager.round3(parsed)
                                storage.saveChallengeTarget(0, targetAmount)
                                feedbackMessage = if (isEnglish) "✅ Goal saved!" else "✅ تم حفظ الهدف"
                            }
                        }
                    }
                    if (feedbackMessage != null) {
                        Text(feedbackMessage!!, color = if (isErrorMsg) Color(0xFFEF4444) else OkGreen, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            } else {
                AchievementCalendar(theme, isEnglish, dayTotals, targetAmount, streakText, sound, allTrips, storage.getExpenses())
            }
        }
    }
}

// ======================================================================
// تقويم الإنجاز الشهري (يبدأ بالسبت)
// ======================================================================
@Composable
private fun AchievementCalendar(theme: AppTheme, isEnglish: Boolean, dayTotals: Map<String, Double>, target: Double, streakText: String, sound: Boolean,
    allTrips: List<TripRecord>, allExpenses: List<ExpenseRecord>) {
    val view = LocalView.current
    val textColor = textColorFor(theme)
    val subTextColor = subTextColorFor(theme)
    var monthOffset by remember { mutableIntStateOf(0) } // 0 = الشهر الحالي، -1 = السابق ...
    var selectedDay by remember { mutableStateOf<String?>(null) } // اليوم المضغوط لعرض تفاصيله

    val dayFormat = remember { SimpleDateFormat("yyyy/MM/dd", Locale.US) }
    val todayStr = dayFormat.format(Date(System.currentTimeMillis()))
    val monthCal = Calendar.getInstance().apply {
        set(Calendar.DAY_OF_MONTH, 1)
        add(Calendar.MONTH, monthOffset)
    }
    val year = monthCal.get(Calendar.YEAR)
    val month = monthCal.get(Calendar.MONTH)
    val daysInMonth = monthCal.getActualMaximum(Calendar.DAY_OF_MONTH)
    val leadingBlanks = monthCal.get(Calendar.DAY_OF_WEEK) % 7 // السبت = 0
    val arMonths = listOf("كانون الثاني", "شباط", "آذار", "نيسان", "أيار", "حزيران", "تموز", "آب", "أيلول", "تشرين الأول", "تشرين الثاني", "كانون الأول")
    val enMonths = listOf("January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December")
    val title = "${if (isEnglish) enMonths[month] else arMonths[month]} $year"

    var okDays = 0
    var workedDays = 0
    for (d in 1..daysInMonth) {
        val key = String.format(Locale.US, "%04d/%02d/%02d", year, month + 1, d)
        val v = dayTotals[key] ?: 0.0
        if (v > 0.0) workedDays++
        if (target > 0.0 && v >= target) okDays++
    }

    GlassCard(theme = theme, modifier = Modifier.fillMaxWidth(), corner = 24.dp, contentPadding = 12.dp, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            GlassIconButton(icon = if (isEnglish) "⬅️" else "➡️", theme = theme, size = 34.dp) {
                SoundEffectsHelper.playClick(view, sound); monthOffset -= 1
            }
            Text(title, modifier = Modifier.weight(1f), textAlign = TextAlign.Center, fontSize = 16.sp, fontWeight = FontWeight.Black, color = textColor)
            if (monthOffset < 0) {
                GlassIconButton(icon = if (isEnglish) "➡️" else "⬅️", theme = theme, size = 34.dp) {
                    SoundEffectsHelper.playClick(view, sound); monthOffset += 1
                }
            } else {
                Spacer(modifier = Modifier.size(34.dp))
            }
        }

        val weekDays = if (isEnglish) listOf("Sat", "Sun", "Mon", "Tue", "Wed", "Thu", "Fri")
        else listOf("سبت", "أحد", "اثنين", "ثلاثاء", "أربعاء", "خميس", "جمعة")
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            weekDays.forEach { Text(it, modifier = Modifier.weight(1f), textAlign = TextAlign.Center, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = subTextColor) }
        }

        val cells = List(leadingBlanks) { 0 } + (1..daysInMonth).toList()
        cells.chunked(7).forEach { week ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                for (i in 0 until 7) {
                    val day = week.getOrNull(i) ?: 0
                    Box(modifier = Modifier.weight(1f).aspectRatio(1f), contentAlignment = Alignment.Center) {
                        if (day > 0) {
                            val key = String.format(Locale.US, "%04d/%02d/%02d", year, month + 1, day)
                            val value = dayTotals[key] ?: 0.0
                            val isToday = key == todayStr
                            val isFuture = key > todayStr
                            val shape = RoundedCornerShape(9.dp)
                            val met = target > 0.0 && value >= target
                            val (bg, fg) = when {
                                met -> OkGreen to Color(0xFF052E14)
                                isToday || isFuture -> Color.Transparent to (if (isFuture) subTextColor.copy(alpha = 0.55f) else theme.accentColor)
                                value >= target / 2.0 -> PartGold to Color(0xFF2B1D00)
                                else -> MissRed to Color(0xFFFFDADA)
                            }
                            var cellMod = Modifier.fillMaxSize().clip(shape).background(if (isFuture) textColor.copy(alpha = 0.05f) else bg)
                            if (isToday) cellMod = cellMod.border(2.dp, theme.accentColor, shape)
                            if (!isFuture) cellMod = cellMod.clickable { SoundEffectsHelper.playClick(view, sound); selectedDay = key }
                            Box(modifier = cellMod, contentAlignment = Alignment.Center) {
                                Text(if (met) "✓$day" else "$day", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = fg)
                            }
                        }
                    }
                }
            }
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally)) {
            LegendDot(OkGreen, if (isEnglish) "Met" else "تحقق", subTextColor)
            LegendDot(PartGold, if (isEnglish) "Over half" else "أكثر من النصف", subTextColor)
            LegendDot(MissRed, if (isEnglish) "Missed" else "لم يتحقق", subTextColor)
        }
    }

    selectedDay?.let { day ->
        DayDetailsSheet(
            theme, isEnglish, day, target,
            allTrips.filter { it.dateStr == day }.sortedBy { it.startTimeStr },
            allExpenses.filter { it.dateStr == day }
        ) { selectedDay = null }
    }

    GlassCard(theme = theme, modifier = Modifier.fillMaxWidth(), corner = 22.dp, contentPadding = 14.dp, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(streakText, fontSize = 16.sp, fontWeight = FontWeight.Black, color = theme.accentColor)
        Text(
            if (isEnglish) "Goal met on $okDays day(s) • worked $workedDays day(s) this month"
            else "تحقق الهدف في $okDays يوم • عملت $workedDays يوم هذا الشهر",
            fontSize = 13.sp, color = textColor
        )
    }
}

@Composable
private fun LegendDot(color: Color, label: String, textColor: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(color))
        Spacer(modifier = Modifier.width(4.dp))
        Text(label, fontSize = 11.sp, color = textColor)
    }
}

// ======================================================================
// ورقة تفاصيل اليوم: تظهر من الأسفل عند الضغط على يوم في التقويم (2.9.4)
// ======================================================================
@Composable
private fun DayDetailsSheet(
    theme: AppTheme, isEnglish: Boolean, day: String, target: Double,
    trips: List<TripRecord>, expenses: List<ExpenseRecord>, onClose: () -> Unit
) {
    val textColor = textColorFor(theme)
    val subTextColor = subTextColorFor(theme)
    val income = trips.sumOf { it.totalFare }
    val spent = expenses.sumOf { it.amount }
    val km = trips.sumOf { it.distanceKm }
    val secs = trips.sumOf { it.durationSeconds }
    val pct = if (target > 0.0) (income / target * 100.0).toInt() else 0
    val direction = if (isEnglish) LayoutDirection.Ltr else LayoutDirection.Rtl
    val jd = if (isEnglish) "JD" else "د.أ"

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        CompositionLocalProvider(LocalLayoutDirection provides direction) {
            Box(
                modifier = Modifier.fillMaxSize().clickable(
                    indication = null,
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                ) { onClose() },
                contentAlignment = Alignment.BottomCenter
            ) {
                GlassCard(
                    theme = theme,
                    modifier = Modifier.fillMaxWidth().padding(10.dp).clickable(
                        indication = null,
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                    ) { },
                    corner = 26.dp, contentPadding = 14.dp, verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("📅 $day", modifier = Modifier.weight(1f), fontSize = 16.sp, fontWeight = FontWeight.Black, color = textColor)
                        GlassIconButton(icon = "✖️", theme = theme, size = 32.dp, onClick = onClose)
                    }
                    if (trips.isEmpty() && expenses.isEmpty()) {
                        Text(if (isEnglish) "No work recorded on this day." else "ما اشتغلت هذا اليوم.", fontSize = 14.sp, color = subTextColor)
                    } else {
                        Text(
                            "${Fmt.money(income)} $jd" + if (target > 0.0) "  •  $pct%" else "",
                            fontSize = 22.sp, fontWeight = FontWeight.Black,
                            color = if (target > 0.0 && income >= target) OkGreen else theme.fareColor
                        )
                        if (trips.isNotEmpty()) {
                            val span = "${trips.first().startTimeStr.take(5)} → ${trips.maxByOrNull { it.endTimeStr }!!.endTimeStr.take(5)}"
                            Text(
                                (if (isEnglish) "${trips.size} trip(s)  •  ${Fmt.km(km)} km  •  " else "${trips.size} رحلة  •  ${Fmt.km(km)} كم  •  ") + Fmt.clock(secs),
                                fontSize = 13.sp, color = textColor
                            )
                            Text(
                                (if (isEnglish) "Worked: $span  •  Avg/trip: " else "فترة الشغل: $span  •  معدل الرحلة: ") + "${Fmt.money(income / trips.size)} $jd",
                                fontSize = 12.sp, color = subTextColor
                            )
                        }
                        if (spent > 0.0) {
                            Text(
                                (if (isEnglish) "Expenses: " else "المصاريف: ") + "${Fmt.money(spent)} $jd  •  " +
                                    (if (isEnglish) "Net: " else "الصافي: ") + "${Fmt.money(income - spent)} $jd",
                                fontSize = 13.sp, fontWeight = FontWeight.Bold, color = textColor
                            )
                        }
                        if (trips.isNotEmpty()) {
                            Column(
                                modifier = Modifier.fillMaxWidth().heightIn(max = 210.dp).verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                trips.forEach { t ->
                                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                        Text(t.startTimeStr.take(5), fontSize = 12.sp, color = subTextColor, modifier = Modifier.width(48.dp), fontFamily = FontFamily.Monospace)
                                        Text("${Fmt.km(t.distanceKm)} ${if (isEnglish) "km" else "كم"}", fontSize = 12.sp, color = textColor, modifier = Modifier.weight(1f))
                                        Text("${Fmt.money(t.totalFare)} $jd", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = theme.fareColor)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
