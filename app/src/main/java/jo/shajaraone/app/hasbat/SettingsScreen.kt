package jo.shajaraone.app.hasbat

import jo.shajaraone.app.R
import jo.shajaraone.app.MainActivity

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun CleanSettingsScreen(storage: AppDataManager, theme: AppTheme, isEnglish: Boolean, onSeatHistory: (() -> Unit)? = null, onBack: () -> Unit) {
    val view = LocalView.current
    // -1 = القائمة، 0 السجل، 2 النسخ الاحتياطي، 3 اللغة، 4 الثيمات، 5 الأصوات، 6 الدعم
    var selectedSection by remember { mutableIntStateOf(-1) }
    val textColor = textColorFor(theme)
    val subTextColor = subTextColorFor(theme)

    val sections = listOf(
        Triple(0, "📜", if (isEnglish) "Trip history" else "سجل الرحلات"),
        Triple(11, "🚌", if (isEnglish) "Seat trips" else "رحلات المقاعد"),
        Triple(2, "💾", if (isEnglish) "Backup" else "النسخ الاحتياطي"),
        Triple(3, "🌐", if (isEnglish) "Language" else "اللغة"),
        Triple(4, "🎨", if (isEnglish) "Themes" else "الثيمات"),
        Triple(5, "🔊", if (isEnglish) "Sounds" else "الأصوات"),
        Triple(8, "📅", if (isEnglish) "License & insurance" else "الترخيص والتأمين"),
        Triple(10, "🫧", if (isEnglish) "Floating bubble" else "الفقاعة العائمة")
        // الدعم الفني انتقل إلى زر 🎧 في الشاشة الرئيسية (2.9.2)
    )

    // زر الرجوع في الهاتف يعيد من القسم إلى القائمة
    BackHandler(enabled = selectedSection != -1) { selectedSection = -1 }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        val current = sections.firstOrNull { it.first == selectedSection }
        Row(verticalAlignment = Alignment.CenterVertically) {
            GlassIconButton(icon = if (isEnglish) "⬅️" else "➡️", theme = theme, size = 38.dp) {
                if (selectedSection != -1) { SoundEffectsHelper.playClick(view, storage.soundEnabled); selectedSection = -1 } else onBack()
            }
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = if (current != null) "${current.second} ${current.third}" else (if (isEnglish) "⚙️ Settings" else "⚙️ الإعدادات"),
                fontSize = 21.sp, fontWeight = FontWeight.Black, color = textColor
            )
        }

        if (current == null) {
            // قائمة عمودية مثل إعدادات الهاتف
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) {
            GlassCard(theme = theme, modifier = Modifier.fillMaxWidth(), corner = 22.dp, contentPadding = 6.dp) {
                sections.forEachIndexed { index, item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .bounceClick { SoundEffectsHelper.playClick(view, storage.soundEnabled); if (item.first == 11) onSeatHistory?.invoke() else selectedSection = item.first }
                            .padding(horizontal = 12.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(theme.accentColor.copy(alpha = 0.16f)),
                            contentAlignment = Alignment.Center
                        ) { Text(item.second, fontSize = 18.sp) }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(item.third, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = textColor)
                        }
                        Text(if (isEnglish) "›" else "‹", fontSize = 22.sp, color = subTextColor)
                    }
                    if (index < sections.lastIndex) ThinDivider(theme)
                }
            }
            }
        } else {
            Column(
                modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                when {
                    selectedSection == 0 -> TripHistorySection(storage, theme, isEnglish)
                    selectedSection == 2 -> BackupSection(storage, theme, isEnglish)
                    selectedSection == 3 -> LanguageSection(storage, theme, isEnglish)
                    selectedSection == 4 -> ThemeSelectorSection(storage, theme, isEnglish)
                    selectedSection == 5 -> SoundSettingsSection(storage, theme, isEnglish)
                    selectedSection == 8 -> VehicleReminderSection(storage, theme, isEnglish)
                    selectedSection == 10 -> FloatingBubbleSection(storage, theme, isEnglish)
                    else -> SupportAboTalaSection(storage, theme, isEnglish)
                }
            }
        }
    }
}

/** أقسام الإعدادات التي تُقفل بعد انتهاء التجربة: السجل، النسخ الاحتياطي، الثيمات */
private val LOCKED_SETTINGS = setOf(0, 2, 4)

// ======================================================================
// سجل الرحلات
// ======================================================================
@Composable
fun TripHistorySection(storage: AppDataManager, theme: AppTheme, isEnglish: Boolean) {
    val view = LocalView.current
    val sound = storage.soundEnabled
    val trips = remember(storage.version) { storage.getTrips() }
    var visibleCount by remember { mutableIntStateOf(30) }
    var selectedTripId by remember { mutableLongStateOf(-1L) }
    var confirmClearAll by remember { mutableStateOf(false) }
    val textColor = textColorFor(theme)
    val subTextColor = subTextColorFor(theme)

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = if (isEnglish) "📜 Recorded trips (${trips.size})" else "📜 سجل الرحلات (${trips.size})",
                fontSize = 16.sp, fontWeight = FontWeight.Black, color = textColor
            )
            if (trips.isNotEmpty()) {
                val clearShape = RoundedCornerShape(14.dp)
                Box(
                    modifier = Modifier
                        .bounceClick { SoundEffectsHelper.playClick(view, sound); confirmClearAll = true }
                        .clip(clearShape)
                        .background(Color(0xFFEF4444).copy(alpha = 0.14f))
                        .border(1.dp, Color(0xFFEF4444).copy(alpha = 0.60f), clearShape)
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(if (isEnglish) "Clear all" else "مسح السجل", color = Color(0xFFEF4444), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        if (trips.isEmpty()) {
            GlassCard(theme = theme, modifier = Modifier.fillMaxWidth(), corner = 22.dp, contentPadding = 32.dp, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("🧾", fontSize = 42.sp)
                Text(if (isEnglish) "No recorded trips yet." else "لا توجد رحلات مسجلة بعد.", color = subTextColor, fontSize = 14.sp)
            }
        } else {
            trips.take(visibleCount).forEach { trip ->
                GlassCard(
                    theme = theme,
                    modifier = Modifier.fillMaxWidth().bounceClick { SoundEffectsHelper.playClick(view, sound); selectedTripId = trip.id },
                    corner = 20.dp,
                    glow = theme.fareColor,
                    contentPadding = 14.dp,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("${trip.dateStr} • ${trip.startTimeStr}", fontSize = 12.sp, color = subTextColor, fontWeight = FontWeight.Bold)
                        Text(
                            "${Fmt.money(trip.totalFare)} JD",
                            fontWeight = FontWeight.Black, fontSize = 19.sp, color = theme.fareColor,
                            style = if (theme.isDark) neonTextStyle(theme.fareColor, 12f, 0.4f) else TextStyle.Default
                        )
                    }
                    ThinDivider(theme)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "🛣️ ${Fmt.km(trip.distanceKm)} ${if (isEnglish) "km" else "كم"}  •  ⏱️ ${Fmt.clock(trip.durationSeconds)}" +
                                (if (trip.isFixedPrice) "  •  🤝" else if (trip.multiplier != 1.0) "  •  ${Fmt.multiplier(trip.multiplier)}" else ""),
                            fontSize = 12.sp, color = textColor, fontWeight = FontWeight.Medium
                        )
                        Text(if (isEnglish) "Details 🔍" else "التفاصيل 🔍", fontSize = 11.sp, color = theme.accentColor, fontWeight = FontWeight.Bold)
                    }
                }
            }
            if (trips.size > visibleCount) {
                NeonActionButton(
                    text = if (isEnglish) "Show more (${trips.size - visibleCount})" else "عرض المزيد (${trips.size - visibleCount})",
                    colors = listOf(theme.accentColor.copy(alpha = 0.85f), theme.needleColor.copy(alpha = 0.85f)),
                    modifier = Modifier.fillMaxWidth(),
                    height = 46.dp,
                    textSize = 14.sp
                ) { SoundEffectsHelper.playClick(view, sound); visibleCount += 30 }
            }
        }
    }

    if (confirmClearAll) {
        ConfirmDialog(
            theme = theme,
            title = if (isEnglish) "Delete all trips?" else "حذف كل الرحلات؟",
            message = if (isEnglish) "All ${trips.size} trips will be permanently deleted, and your income reports will be reset. Consider exporting a backup first."
            else "سيتم حذف ${trips.size} رحلة نهائياً، وستُصفَّر تقارير الدخل. ننصح بتصدير نسخة احتياطية أولاً من قسم «نسخ احتياطي».",
            confirmText = if (isEnglish) "Delete all" else "حذف الكل",
            dismissText = if (isEnglish) "Cancel" else "إلغاء",
            onConfirm = { storage.clearTrips(); confirmClearAll = false; visibleCount = 30 },
            onDismiss = { confirmClearAll = false }
        )
    }

    trips.firstOrNull { it.id == selectedTripId }?.let { tr ->
        TripDetailsDialog(storage, theme, isEnglish, tr, onClose = { selectedTripId = -1L })
    }
}

@Composable
private fun TripDetailsDialog(storage: AppDataManager, theme: AppTheme, isEnglish: Boolean, tr: TripRecord, onClose: () -> Unit) {
    val view = LocalView.current
    val context = LocalContext.current
    val sound = storage.soundEnabled
    val textColor = textColorFor(theme)
    val subTextColor = subTextColorFor(theme)
    var confirmDelete by remember { mutableStateOf(false) }
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
                        GlassIconButton(icon = if (isEnglish) "⬅️" else "➡️", theme = theme, size = 40.dp) { SoundEffectsHelper.playClick(view, sound); onClose() }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(if (isEnglish) "Trip report" else "تفاصيل الرحلة", fontSize = 20.sp, fontWeight = FontWeight.Black, color = textColor)
                            Text("${tr.dateStr} • ${tr.startTimeStr}", fontSize = 12.sp, color = subTextColor)
                        }
                    }

                    GlassCard(theme = theme, modifier = Modifier.fillMaxWidth(), corner = 26.dp, glow = theme.fareColor, secondGlow = theme.accentColor, contentPadding = 20.dp, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(if (isEnglish) "TOTAL FARE" else "إجمالي أجرة الرحلة", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = subTextColor)
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(Fmt.money(tr.totalFare), fontSize = 50.sp, fontWeight = FontWeight.Black, color = theme.fareColor, fontFamily = FontFamily.Monospace, style = if (theme.isDark) neonTextStyle(theme.fareColor) else TextStyle.Default)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("JD", fontSize = 18.sp, fontWeight = FontWeight.Black, color = theme.fareColor, modifier = Modifier.padding(bottom = 8.dp))
                        }
                        if (tr.isFixedPrice) Text(if (isEnglish) "🤝 Agreed fixed price" else "🤝 سعر متفق عليه", fontSize = 12.sp, color = subTextColor)
                    }

                    GlassCard(theme = theme, modifier = Modifier.fillMaxWidth(), corner = 24.dp, contentPadding = 16.dp, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(if (isEnglish) "📍 Route (A ➔ B)" else "📍 مسار الرحلة (من A إلى B)", fontSize = 15.sp, fontWeight = FontWeight.Black, color = textColor)
                        RoutePointRow("A", Color(0xFF22C55E), if (isEnglish) "Start" else "نقطة الانطلاق", tr.startAddress, textColor, subTextColor, tr.startLat, tr.startLng, isEnglish) { openMap(context, tr.startLat, tr.startLng) }
                        ThinDivider(theme)
                        RoutePointRow("B", Color(0xFFEF4444), if (isEnglish) "Arrival" else "نقطة الوصول", tr.endAddress, textColor, subTextColor, tr.endLat, tr.endLng, isEnglish) { openMap(context, tr.endLat, tr.endLng) }
                        if (tr.startLat != 0.0 && tr.endLat != 0.0) {
                            PillChip(
                                text = if (isEnglish) "🗺️ Show trip on map (A ➔ B)" else "🗺️ عرض الرحلة على الخريطة (A ➔ B)",
                                selected = false,
                                theme = theme,
                                modifier = Modifier.fillMaxWidth()
                            ) { SoundEffectsHelper.playClick(view, sound); openRoute(context, tr) }
                        }
                    }

                    GlassCard(theme = theme, modifier = Modifier.fillMaxWidth(), corner = 22.dp, contentPadding = 16.dp, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SummaryRow(if (isEnglish) "🛣️ Distance:" else "🛣️ المسافة:", "${Fmt.km(tr.distanceKm)} ${if (isEnglish) "km" else "كم"}", textColor, subTextColor)
                        ThinDivider(theme)
                        SummaryRow(if (isEnglish) "⏱️ Duration:" else "⏱️ المدة:", Fmt.clock(tr.durationSeconds), textColor, subTextColor)
                        ThinDivider(theme)
                        SummaryRow(if (isEnglish) "⏰ Start time:" else "⏰ وقت الانطلاق:", tr.startTimeStr, textColor, subTextColor)
                        ThinDivider(theme)
                        SummaryRow(if (isEnglish) "🏁 End time:" else "🏁 وقت الوصول:", tr.endTimeStr, textColor, subTextColor)
                        ThinDivider(theme)
                        if (tr.tariffName.isNotBlank()) {
                            SummaryRow(if (isEnglish) "🏷️ Tariff:" else "🏷️ التعرفة:", tr.tariffName + if (tr.multiplier != 1.0) " ${Fmt.multiplier(tr.multiplier)}" else "", theme.accentColor, subTextColor)
                            ThinDivider(theme)
                        }
                        SummaryRow(
                            if (isEnglish) "⚙️ Base / Km / Min:" else "⚙️ فتحة / كم / دقيقة:",
                            "${Fmt.rate(tr.appliedBaseFare)} / ${Fmt.rate(tr.appliedKmPrice)} / ${Fmt.rate(tr.appliedMinPrice)}",
                            theme.accentColor, subTextColor
                        )
                    }

                    NeonActionButton(
                        text = if (isEnglish) "🧾 Send receipt to passenger" else "🧾 إرسال إيصال للراكب",
                        colors = listOf(Color(0xFF25D366), Color(0xFF128C7E)),
                        modifier = Modifier.fillMaxWidth(), height = 52.dp, textSize = 14.sp
                    ) { SoundEffectsHelper.playClick(view, sound); ReceiptHelper.share(context, tr, isEnglish) }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        NeonActionButton(
                            text = if (isEnglish) "🗑️ Delete trip" else "🗑️ حذف الرحلة",
                            colors = listOf(Color(0xFFEF4444), Color(0xFFFF6B8A)),
                            modifier = Modifier.weight(1f), height = 52.dp, textSize = 14.sp
                        ) { SoundEffectsHelper.playClick(view, sound); confirmDelete = true }
                        NeonActionButton(
                            text = if (isEnglish) "Close" else "إغلاق",
                            colors = listOf(theme.accentColor, theme.needleColor),
                            modifier = Modifier.weight(1f), height = 52.dp, textSize = 14.sp
                        ) { SoundEffectsHelper.playClick(view, sound); onClose() }
                    }
                }

                if (confirmDelete) {
                    ConfirmDialog(
                        theme = theme,
                        title = if (isEnglish) "Delete this trip?" else "حذف هذه الرحلة؟",
                        message = if (isEnglish) "${Fmt.money(tr.totalFare)} JD on ${tr.dateStr} will be removed from history and income."
                        else "سيتم حذف رحلة بقيمة ${Fmt.money(tr.totalFare)} د.أ بتاريخ ${tr.dateStr} من السجل والدخل.",
                        confirmText = if (isEnglish) "Delete" else "حذف",
                        dismissText = if (isEnglish) "Cancel" else "إلغاء",
                        onConfirm = { confirmDelete = false; storage.deleteTrip(tr.id); onClose() },
                        onDismiss = { confirmDelete = false }
                    )
                }
            }
        }
    }
}

@Composable
private fun RoutePointRow(
    letter: String, color: Color, title: String, address: String, textColor: Color, subTextColor: Color,
    lat: Double, lng: Double, isEnglish: Boolean, onMap: () -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(
            modifier = Modifier.size(38.dp).clip(CircleShape).background(color.copy(alpha = 0.2f)).border(1.5.dp, color, CircleShape),
            contentAlignment = Alignment.Center
        ) { Text(letter, fontWeight = FontWeight.Black, fontSize = 16.sp, color = color) }
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 11.sp, color = subTextColor, fontWeight = FontWeight.Bold)
            Text(address, fontSize = 14.sp, color = textColor, fontWeight = FontWeight.Bold)
        }
        if (lat != 0.0 || lng != 0.0) {
            Box(
                modifier = Modifier
                    .bounceClick(onClick = onMap)
                    .clip(RoundedCornerShape(12.dp))
                    .border(1.dp, color.copy(alpha = 0.7f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) { Text(if (isEnglish) "🗺️ Map" else "🗺️ الخريطة", fontSize = 11.sp, color = color, fontWeight = FontWeight.Bold) }
        }
    }
}

/** يفتح مسار الرحلة من A إلى B في خرائط جوجل */
private fun openRoute(context: Context, tr: TripRecord) {
    val url = "https://www.google.com/maps/dir/?api=1&origin=${tr.startLat},${tr.startLng}&destination=${tr.endLat},${tr.endLng}&travelmode=driving"
    try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } catch (_: Exception) {}
}

private fun openMap(context: Context, lat: Double, lng: Double) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("geo:$lat,$lng?q=$lat,$lng")))
    } catch (_: Exception) {
        try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://maps.google.com/?q=$lat,$lng"))) } catch (_: Exception) {}
    }
}

// ======================================================================
// النسخ الاحتياطي: تصدير الرحلات والمصاريف كملف CSV يفتح في Excel
// ======================================================================
@Composable
fun BackupSection(storage: AppDataManager, theme: AppTheme, isEnglish: Boolean) {
    val context = LocalContext.current
    val view = LocalView.current
    val textColor = textColorFor(theme)
    var message by remember { mutableStateOf<String?>(null) }


    GlassCard(theme = theme, modifier = Modifier.fillMaxWidth(), corner = 24.dp, contentPadding = 16.dp, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(if (isEnglish) "Export your data" else "تصدير بياناتك", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = textColor)
        Text(
            if (isEnglish) "Creates a CSV file (opens in Excel / Google Sheets) with all trips and expenses. Send it to yourself on WhatsApp or save it to Drive."
            else "ينشئ ملف CSV (يفتح في Excel أو Google Sheets) فيه كل الرحلات والمصاريف. أرسله لنفسك على واتساب أو احفظه في Drive.",
            fontSize = 12.sp, color = subTextColorFor(theme), lineHeight = 19.sp
        )
        NeonActionButton(
            text = if (isEnglish) "📤 Export trips & expenses" else "📤 تصدير الرحلات والمصاريف",
            colors = listOf(theme.accentColor, theme.needleColor),
            modifier = Modifier.fillMaxWidth(),
            height = 52.dp,
            textSize = 14.sp
        ) {
            SoundEffectsHelper.playClick(view, storage.soundEnabled)
            message = if (exportCsv(context, storage, isEnglish)) null
            else (if (isEnglish) "⚠️ Export failed. Try again." else "⚠️ تعذّر التصدير، حاول مرة أخرى.")
        }
        message?.let { Text(it, color = Color(0xFFEF4444), fontSize = 12.sp, fontWeight = FontWeight.Bold) }
    }
}

private fun csvCell(s: String): String = "\"" + s.replace("\"", "\"\"") + "\""

private fun exportCsv(context: Context, storage: AppDataManager, isEnglish: Boolean): Boolean {
    return try {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val stamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
        val file = File(dir, "captain_backup_$stamp.csv")
        val sb = StringBuilder("﻿") // حتى يقرأ Excel العربية بشكل صحيح
        sb.append(listOf("التاريخ", "البداية", "النهاية", "المسافة كم", "المدة ثانية", "الأجرة", "التعرفة", "المعامل", "سعر ثابت", "من", "إلى").joinToString(",") { csvCell(it) }).append("\n")
        storage.getTrips().forEach { t ->
            sb.append(
                listOf(
                    t.dateStr, t.startTimeStr, t.endTimeStr, Fmt.km(t.distanceKm), t.durationSeconds.toString(),
                    Fmt.rate(t.totalFare), t.tariffName, t.multiplier.toString(), if (t.isFixedPrice) "نعم" else "لا",
                    t.startAddress, t.endAddress
                ).joinToString(",") { csvCell(it) }
            ).append("\n")
        }
        sb.append("\n")
        sb.append(listOf("المصاريف: التاريخ", "الفئة", "المبلغ", "ملاحظة").joinToString(",") { csvCell(it) }).append("\n")
        storage.getExpenses().forEach { e ->
            sb.append(listOf(e.dateStr, e.category, Fmt.rate(e.amount), e.note).joinToString(",") { csvCell(it) }).append("\n")
        }
        file.writeText(sb.toString(), Charsets.UTF_8)
        val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, file.name)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, if (isEnglish) "Save backup" else "حفظ النسخة الاحتياطية"))
        true
    } catch (_: Exception) {
        false
    }
}

// ======================================================================
// اللغة والثيمات والأصوات والدعم
// ======================================================================
@Composable
fun LanguageSection(storage: AppDataManager, theme: AppTheme, isEnglish: Boolean) {
    val view = LocalView.current
    val textColor = textColorFor(theme)
    val unselectedBg = if (theme.isDark) Color.White.copy(alpha = 0.06f) else Color.Black.copy(alpha = 0.04f)
    val rowShape = RoundedCornerShape(16.dp)

    GlassCard(theme = theme, modifier = Modifier.fillMaxWidth(), corner = 24.dp, contentPadding = 16.dp, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(if (isEnglish) "Select app language" else "اختر لغة التطبيق", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = textColor)
        listOf("ar" to "العربية (الأردن) 🇯🇴", "en" to "English (US) 🇺🇸").forEach { (code, label) ->
            val active = storage.language == code
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .bounceClick { SoundEffectsHelper.playClick(view, storage.soundEnabled); storage.saveLanguage(code) }
                    .clip(rowShape)
                    .background(if (active) theme.accentColor.copy(alpha = 0.20f) else unselectedBg)
                    .border(1.dp, if (active) theme.accentColor.copy(alpha = 0.8f) else Color.Transparent, rowShape)
                    .padding(14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(label, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = textColor)
                if (active) Text(if (isEnglish) "Active ✓" else "مفعّل ✓", color = theme.accentColor, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }
    }
}

@Composable
fun ThemeSelectorSection(storage: AppDataManager, theme: AppTheme, isEnglish: Boolean) {
    val view = LocalView.current
    val textColor = textColorFor(theme)
    val subTextColor = subTextColorFor(theme)
    var editingDay by remember { mutableStateOf(false) }
    val auto = storage.autoDayNight

    GlassCard(theme = theme, modifier = Modifier.fillMaxWidth(), corner = 24.dp, contentPadding = 14.dp, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // التبديل التلقائي نهار/ليل
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(if (isEnglish) "🌗 Auto day / night" else "🌗 تبديل تلقائي نهار/ليل", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = textColor)
                Text(
                    if (isEnglish) "Day theme after sunrise, night theme after sunset" else "ثيم النهار بعد الشروق، وثيم الليل بعد المغرب",
                    fontSize = 11.sp, color = subTextColor
                )
            }
            Switch(
                checked = auto,
                onCheckedChange = { enabled ->
                    SoundEffectsHelper.playClick(view, storage.soundEnabled)
                    storage.saveAutoDayNight(enabled)
                    if (!enabled) editingDay = false
                },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = readableOn(theme.accentColor),
                    checkedTrackColor = theme.accentColor,
                    uncheckedThumbColor = if (theme.isDark) Color(0xFF94A3B8) else Color.White,
                    uncheckedTrackColor = if (theme.isDark) Color.White.copy(alpha = 0.10f) else Color(0xFFCBD5E1)
                )
            )
        }
        if (auto) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PillChip(text = if (isEnglish) "🌙 Night theme" else "🌙 ثيم الليل", selected = !editingDay, theme = theme, modifier = Modifier.weight(1f)) {
                    SoundEffectsHelper.playClick(view, storage.soundEnabled); editingDay = false
                }
                PillChip(text = if (isEnglish) "☀️ Day theme" else "☀️ ثيم النهار", selected = editingDay, theme = theme, modifier = Modifier.weight(1f)) {
                    SoundEffectsHelper.playClick(view, storage.soundEnabled); editingDay = true
                }
            }
        }
        ThinDivider(theme)
        Text(
            text = when {
                auto && editingDay -> if (isEnglish) "Choose the day theme" else "اختر ثيم النهار"
                auto -> if (isEnglish) "Choose the night theme" else "اختر ثيم الليل"
                else -> if (isEnglish) "Choose app theme" else "اختر ثيم التطبيق"
            },
            fontWeight = FontWeight.Bold, fontSize = 14.sp, color = textColor
        )
        val all = AppTheme.values().toList()
        val selectedIdx = if (auto && editingDay) storage.dayThemeIndex else storage.themeIndex
        all.chunked(3).forEachIndexed { rowIndex, rowThemes ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                rowThemes.forEachIndexed { col, th ->
                    val index = rowIndex * 3 + col
                    ThemeTile(th, index == selectedIdx, isEnglish, textColor, Modifier.weight(1f)) {
                        SoundEffectsHelper.playClick(view, storage.soundEnabled)
                        if (auto && editingDay) storage.saveDayThemeIndex(index) else storage.saveThemeIndex(index)
                    }
                }
                repeat(3 - rowThemes.size) { Spacer(modifier = Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun ThemeTile(th: AppTheme, selected: Boolean, isEnglish: Boolean, textColor: Color, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier = modifier
            .bounceClick(onClick = onClick)
            .clip(shape)
            .background(if (selected) th.accentColor.copy(alpha = 0.18f) else textColor.copy(alpha = 0.04f))
            .border(if (selected) 1.5.dp else 1.dp, if (selected) th.accentColor else textColor.copy(alpha = 0.10f), shape)
            .padding(vertical = 8.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(th.bgGradient.first(), th.accentColor, th.fareColor)))
                .border(1.dp, Color.White.copy(alpha = 0.4f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (selected) Text("✓", fontSize = 14.sp, fontWeight = FontWeight.Black, color = Color.White)
        }
        Text(
            if (isEnglish) th.titleEn else th.titleAr,
            fontSize = 10.sp, fontWeight = FontWeight.Bold, color = textColor,
            maxLines = 2, textAlign = TextAlign.Center, lineHeight = 13.sp
        )
    }
}

@Composable
fun SoundSettingsSection(storage: AppDataManager, theme: AppTheme, isEnglish: Boolean) {
    val view = LocalView.current
    val textColor = textColorFor(theme)
    GlassCard(theme = theme, modifier = Modifier.fillMaxWidth(), corner = 24.dp, contentPadding = 16.dp, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(if (isEnglish) "Click feedback" else "أصوات النقر واللمس", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = textColor)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(if (isEnglish) "Button sounds & vibration" else "أصوات واهتزاز النقر على الأزرار", fontSize = 13.sp, color = textColor, modifier = Modifier.weight(1f))
            Switch(
                checked = storage.soundEnabled,
                onCheckedChange = { enabled ->
                    storage.setSound(enabled)
                    SoundEffectsHelper.playClick(view, enabled)
                },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = readableOn(theme.accentColor),
                    checkedTrackColor = theme.accentColor,
                    uncheckedThumbColor = if (theme.isDark) Color(0xFF94A3B8) else Color.White,
                    uncheckedTrackColor = if (theme.isDark) Color.White.copy(alpha = 0.10f) else Color(0xFFCBD5E1)
                )
            )
        }
    }
}

@Composable
fun SupportAboTalaSection(storage: AppDataManager, theme: AppTheme, isEnglish: Boolean) {
    val context = LocalContext.current
    val view = LocalView.current
    GlassCard(
        theme = theme,
        modifier = Modifier.fillMaxWidth(),
        corner = 24.dp,
        glow = Color(0xFF25D366),
        secondGlow = theme.accentColor,
        contentPadding = 18.dp,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("💬", fontSize = 36.sp)
        Text(if (isEnglish) "Direct support: abo-tala" else "الدعم الفني المباشر: abo-tala", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = textColorFor(theme))
        Text(if (isEnglish) "Developer: abo-tala | WhatsApp: 0790905298" else "المطور: abo-tala | واتساب: 0790905298", fontSize = 12.sp, color = subTextColorFor(theme))
        NeonActionButton(
            text = if (isEnglish) "WhatsApp: abo-tala" else "مراسلة الدعم (abo-tala) عبر واتساب",
            colors = listOf(Color(0xFF25D366), Color(0xFF128C7E)),
            modifier = Modifier.fillMaxWidth(),
            height = 52.dp,
            textSize = 14.sp
        ) {
            SoundEffectsHelper.playClick(view, storage.soundEnabled)
            try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/${BuildMode.WHATSAPP_NUMBER}"))) } catch (_: Exception) {}
        }
        Text("v2.9.5", fontSize = 10.sp, color = subTextColorFor(theme))
    }
}

// ======================================================================
// تذكير ترخيص السيارة والتأمين
// ======================================================================
@Composable
fun VehicleReminderSection(storage: AppDataManager, theme: AppTheme, isEnglish: Boolean) {
    val context = LocalContext.current
    val view = LocalView.current
    val sound = storage.soundEnabled
    val textColor = textColorFor(theme)
    val subTextColor = subTextColorFor(theme)
    @Suppress("UNUSED_VARIABLE") val v = storage.version // لإعادة الرسم بعد الحفظ
    val license = storage.getLicenseDate()
    val insurance = storage.getInsuranceDate()

    fun pickDate(current: Long, onPicked: (Long) -> Unit) {
        val cal = java.util.Calendar.getInstance().apply { if (current > 0L) timeInMillis = current }
        try {
            android.app.DatePickerDialog(
                context,
                { _, y, m, d ->
                    val picked = java.util.Calendar.getInstance().apply { clear(); set(y, m, d, 12, 0, 0) }.timeInMillis
                    onPicked(picked)
                },
                cal.get(java.util.Calendar.YEAR), cal.get(java.util.Calendar.MONTH), cal.get(java.util.Calendar.DAY_OF_MONTH)
            ).show()
        } catch (_: Throwable) {}
    }

    GlassCard(theme = theme, modifier = Modifier.fillMaxWidth(), corner = 22.dp, contentPadding = 16.dp, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            if (isEnglish) "We'll remind you on the home screen a week before the date." else "سنذكّرك في الشاشة الرئيسية قبل الموعد بأسبوع.",
            fontSize = 13.sp, color = subTextColor, lineHeight = 20.sp
        )
    }

    listOf(
        Triple(if (isEnglish) "🚗 Car license" else "🚗 ترخيص السيارة", license) { t: Long -> storage.saveLicenseDate(t) },
        Triple(if (isEnglish) "🛡️ Insurance" else "🛡️ التأمين", insurance) { t: Long -> storage.saveInsuranceDate(t) }
    ).forEach { (title, date, save) ->
        GlassCard(theme = theme, modifier = Modifier.fillMaxWidth(), corner = 22.dp, contentPadding = 16.dp, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, fontSize = 16.sp, fontWeight = FontWeight.Black, color = textColor)
                    val days = VehicleReminder.daysLeft(date)
                    Text(
                        text = if (days == null) (if (isEnglish) "Not set" else "غير محدد")
                        else VehicleReminder.formatDate(date) + " • " + VehicleReminder.describe(days, isEnglish),
                        fontSize = 12.sp,
                        color = if (days != null && days <= 7L) Color(0xFFF59E0B) else subTextColor
                    )
                }
                PillChip(text = if (isEnglish) "Set date" else "تحديد التاريخ", selected = false, theme = theme) {
                    SoundEffectsHelper.playClick(view, sound)
                    pickDate(date) { save(it) }
                }
            }
            if (date > 0L) {
                Text(
                    if (isEnglish) "Remove date" else "إزالة التاريخ",
                    color = Color(0xFFEF4444), fontSize = 12.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.bounceClick { SoundEffectsHelper.playClick(view, sound); save(0L) }
                )
            }
        }
    }
}
