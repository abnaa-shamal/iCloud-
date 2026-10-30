package jo.shajaraone.app.hasbat

import jo.shajaraone.app.R
import jo.shajaraone.app.MainActivity

import android.content.Intent
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import android.provider.Settings
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

@Composable
fun MainDashboardScreen(storage: AppDataManager, theme: AppTheme, isEnglish: Boolean, onRequestPermission: () -> Unit) {
    val context = LocalContext.current
    val view = LocalView.current
    val textColor = textColorFor(theme)
    val subTextColor = subTextColorFor(theme)
    val sound = storage.soundEnabled

    val meterState = MeterEngine.meterState
    val isTripActive = meterState != MeterState.IDLE
    val gpsFresh = remember(MeterEngine.clockTick, MeterEngine.lastGpsFixRealtime) { MeterEngine.hasFreshGpsFix() }

    val adhkarCycle = remember {
        listOf(
            "لَا إِلٰهَ إِلَّا ٱللَّٰهُ",
            "سُبْحَانَ اللَّهِ وَبِحَمْدِهِ ، سُبْحَانَ اللَّهِ الْعَظِيمِ",
            "الْحَمْدُ لِلَّهِ رَبِّ الْعَالَمِينَ",
            "اللَّهُ أَكْبَرُ كَبِيرًا ، وَالْحَمْدُ لِلَّهِ كَثِيرًا",
            "لَا حَوْلَ وَلَا قُوَّةَ إِلَّا بِاللَّهِ الْعَلِيِّ الْعَظِيمِ",
            "اللَّهُمَّ صَلِّ وَسَلِّمْ عَلَى سَيِّدِنَا مُحَمَّدٍ",
            "أَسْتَغْفِرُ اللَّهَ الْعَظِيمَ وَأَتُوبُ إِلَيْهِ"
        )
    }
    var currentDhikrIndex by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60000L)
            currentDhikrIndex = (currentDhikrIndex + 1) % adhkarCycle.size
        }
    }

    // تأكيد الإنهاء بضغطتين
    var lastTapTime by remember { mutableLongStateOf(0L) }
    var waitingForSecondTap by remember { mutableStateOf(false) }
    LaunchedEffect(waitingForSecondTap) {
        if (waitingForSecondTap) {
            delay(1500L)
            waitingForSecondTap = false
            lastTapTime = 0L
        }
    }

    var showTinyTripDialog by remember { mutableStateOf(false) }
    var savedMessage by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(savedMessage) {
        if (savedMessage != null) {
            delay(4500L)
            savedMessage = null
        }
    }

    // آخر رحلة مسجلة (لإرسال الإيصال) ووضع القيادة
    var lastRecord by remember { mutableStateOf<TripRecord?>(null) }
    var drivingMode by remember { mutableStateOf(false) }

    fun finishAndReport(save: Boolean) {
        val rec = MeterEngine.finishTrip(save)
        if (rec != null) SoundEffectsHelper.playTripFinish(view, sound)
        lastRecord = rec
        drivingMode = false
        savedMessage = if (rec != null) {
            if (isEnglish) "✅ Trip saved: ${Fmt.money(rec.totalFare)} JD" else "✅ تم حفظ الرحلة: ${Fmt.money(rec.totalFare)} د.أ"
        } else {
            if (isEnglish) "Trip discarded (not saved)." else "تم إلغاء الرحلة دون حفظ."
        }
    }

    // التعرفة المعتمدة تُختار من الإعدادات ← أسعار التعرفة
    val presets = remember(storage.version) { storage.getPresets() }
    // بعد انتهاء الفترة التجريبية تُثبَّت التعرفة الأساسية (أقل الأسعار)
    val selectedPreset = (presets.firstOrNull { it.id == storage.selectedPresetId } ?: presets.first())
    // السعر المتفق عليه للرحلة القادمة (اختياري)
    var fixedPrice by remember { mutableStateOf<Double?>(null) }
    var showFixedDialog by remember { mutableStateOf(false) }
    var fixedText by remember { mutableStateOf("") }
    var fixedError by remember { mutableStateOf<String?>(null) }

    // بدء الرحلة (من الزر أو من سؤال «بدأت رحلة؟»)
    fun startNewTrip() {
        SoundEffectsHelper.playTripStart(view, sound)
        savedMessage = null
        lastRecord = null
        MeterEngine.startTrip(
            TripTariff(
                name = selectedPreset.name,
                base = selectedPreset.base,
                km = selectedPreset.km,
                min = selectedPreset.min,
                fixedPrice = (if (License.locked) null else fixedPrice)?.let { AppDataManager.round3(it) }
            )
        )
        fixedPrice = null
    }

    fun onPauseTap() {
        SoundEffectsHelper.playClick(view, sound)
        MeterEngine.togglePause()
    }

    fun onFinishTap() {
        val now = System.currentTimeMillis()
        SoundEffectsHelper.playClick(view, sound)
        if (waitingForSecondTap && now - lastTapTime <= 1500L) {
            waitingForSecondTap = false
            lastTapTime = 0L
            if (MeterEngine.currentTripIsTiny()) showTinyTripDialog = true else finishAndReport(save = true)
        } else {
            lastTapTime = now
            waitingForSecondTap = true
        }
    }

    // سؤال «بدأت رحلة؟»: إذا تحركت السيارة أسرع من 15 كم/س لمدة 8 ثوانٍ والعداد متوقف
    var showAutoStart by remember { mutableStateOf(false) }
    var movingSinceMs by remember { mutableLongStateOf(0L) }
    var autoSnoozeUntil by remember { mutableLongStateOf(0L) }
    LaunchedEffect(MeterEngine.clockTick) {
        val nowMs = System.currentTimeMillis()
        val moving = !isTripActive && gpsFresh && MeterEngine.speedKmh >= 15f
        if (!moving) {
            movingSinceMs = 0L
        } else {
            if (movingSinceMs == 0L) movingSinceMs = nowMs
            if (!showAutoStart && nowMs - movingSinceMs >= 8000L && nowMs > autoSnoozeUntil && nowMs > storage.getAutoStartMutedUntil()) {
                showAutoStart = true
            }
        }
    }
    LaunchedEffect(isTripActive) { if (!isTripActive) drivingMode = false else showAutoStart = false }

    var showAssistant by remember { mutableStateOf(false) }
    if (showAssistant) AssistantCornerDialog(storage, theme, isEnglish) { showAssistant = false }
    var showSupport by remember { mutableStateOf(false) }
    if (showSupport) SupportCornerDialog(storage, theme, isEnglish) { showSupport = false }

    // تذكير الترخيص والتأمين (يُفحص كل ساعة)
    val vehicleReminder = remember(storage.version, MeterEngine.clockTick / 3_600_000L) { VehicleReminder.dueText(storage, isEnglish) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (drivingMode && isTripActive) {
            DrivingModeView(
                theme = theme,
                isEnglish = isEnglish,
                waitingForSecondTap = waitingForSecondTap,
                onPause = { onPauseTap() },
                onFinish = { onFinishTap() },
                onExit = { SoundEffectsHelper.playClick(view, sound); drivingMode = false }
            )
        } else {
        // ---------------- الترويسة ----------------
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(Brush.linearGradient(listOf(theme.accentColor.copy(alpha = 0.35f), theme.needleColor.copy(alpha = 0.12f))))
                        .border(1.5.dp, Brush.linearGradient(listOf(theme.accentColor, theme.needleColor)), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text("👑", fontSize = 20.sp)
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    // بدون رحلة: تحية حسب وقت اليوم، وأثناء الرحلة: اسم التطبيق
                    val greeting = remember(MeterEngine.clockTick / 60_000L, isEnglish) { greetingText(isEnglish) }
                    Text(
                        if (isTripActive) (if (isEnglish) "Captain's Companion" else "رفيق دربك للكابتن") else greeting,
                        color = textColor, fontSize = 18.sp, fontWeight = FontWeight.Black, maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                    val statusText = when (meterState) {
                        MeterState.RUNNING -> if (isEnglish) "Trip running" else "رحلة جارية"
                        MeterState.PAUSED -> if (isEnglish) "⏸ Trip paused" else "⏸ الرحلة متوقفة مؤقتاً"
                        MeterState.IDLE -> if (isEnglish) "Ready to start" else "جاهز لبدء الرحلة"
                    }
                    val statusColor = when (meterState) {
                        MeterState.RUNNING -> Color(0xFF22C55E)
                        MeterState.PAUSED -> Color(0xFFF59E0B)
                        MeterState.IDLE -> subTextColor
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (meterState == MeterState.RUNNING) {
                            PulsingDot(statusColor, true)
                            Spacer(modifier = Modifier.width(6.dp))
                        }
                        Text(statusText, color = statusColor, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.width(8.dp))
                        GpsBadge(theme, isEnglish, gpsFresh)
                    }
                }
            }
            if (isTripActive) {
                HeaderLabeledButton("🖥️", if (isEnglish) "Drive" else "القيادة", theme, subTextColor) {
                    SoundEffectsHelper.playClick(view, sound); drivingMode = true
                }
                Spacer(modifier = Modifier.width(6.dp))
            }
            if (!isTripActive) {
                // زر الدعم الفني 🎧
                HeaderLabeledButton("🎧", if (isEnglish) "Support" else "دعم فني", theme, subTextColor) {
                    SoundEffectsHelper.playClick(view, sound); showSupport = true
                }
                Spacer(modifier = Modifier.width(6.dp))
            }
            HeaderLabeledButton("🗺️", if (isEnglish) "Map" else "الخريطة", theme, subTextColor) {
                SoundEffectsHelper.playClick(view, sound)
                val lat = MeterEngine.latitude
                val lon = MeterEngine.longitude
                try {
                    val uri = if (lat != 0.0 && lon != 0.0) Uri.parse("geo:$lat,$lon?q=$lat,$lon") else Uri.parse("geo:0,0?q=my+location")
                    context.startActivity(Intent(Intent.ACTION_VIEW, uri).apply { setPackage("com.google.android.apps.maps") })
                } catch (_: Exception) {
                    try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://maps.google.com"))) } catch (_: Exception) {}
                }
            }
        }

        // ---------------- تنبيهات الموقع ----------------
        if (!MeterEngine.hasLocationPermission) {
            WarningBanner(
                theme = theme,
                color = Color(0xFFEF4444),
                text = if (isEnglish) "Location permission is off. The meter will count time only, not distance." else "صلاحية الموقع غير مفعّلة. العداد سيحسب الوقت فقط بدون المسافة.",
                primaryText = if (isEnglish) "Allow" else "منح الصلاحية",
                onPrimary = { onRequestPermission() },
                secondaryText = if (isEnglish) "Settings" else "الإعدادات",
                onSecondary = {
                    try {
                        context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)))
                    } catch (_: Exception) {}
                }
            )
        } else if (!MeterEngine.isGpsEnabled) {
            WarningBanner(
                theme = theme,
                color = Color(0xFFEF4444),
                text = if (isEnglish) "GPS is turned off. Distance will not be counted." else "الموقع (GPS) مطفأ. المسافة لن تُحسب.",
                primaryText = if (isEnglish) "Turn on" else "تشغيل الموقع",
                onPrimary = {
                    try { context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) } catch (_: Exception) {}
                }
            )
        } else if (isTripActive && !gpsFresh) {
            WarningBanner(
                theme = theme,
                color = Color(0xFFF59E0B),
                text = if (isEnglish) "Searching for GPS signal… distance is paused until the signal returns." else "جارٍ البحث عن إشارة GPS… المسافة لا تُحسب حتى تعود الإشارة."
            )
        }

        if (vehicleReminder != null && !isTripActive) {
            WarningBanner(theme = theme, color = Color(0xFFF59E0B), text = vehicleReminder)
        }

        // ---------------- الذكر ----------------
        GlassCard(
            theme = theme,
            modifier = Modifier
                .fillMaxWidth()
                .bounceClick {
                    SoundEffectsHelper.playClick(view, sound)
                    currentDhikrIndex = (currentDhikrIndex + 1) % adhkarCycle.size
                },
            corner = 20.dp,
            glow = theme.fareColor,
            secondGlow = theme.accentColor,
            contentPadding = 8.dp,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AnimatedContent(
                targetState = adhkarCycle[currentDhikrIndex],
                transitionSpec = { fadeIn(tween(400)) togetherWith fadeOut(tween(400)) },
                label = "dhikr_anim"
            ) { dhikr ->
                Text(dhikr, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = theme.fareColor, textAlign = TextAlign.Center, maxLines = 2, modifier = Modifier.fillMaxWidth())
            }
        }

        // عداد السرعة يأخذ المساحة المتبقية فقط، فيكبر ويصغر حسب حجم الهاتف
        ExactSpeedometerDial240(MeterEngine.speedKmh, theme, isEnglish, Modifier.weight(1f).fillMaxWidth())

        // ---------------- الأجرة ----------------
        GlassCard(
            theme = theme,
            modifier = Modifier.fillMaxWidth(),
            corner = 28.dp,
            glow = theme.fareColor,
            secondGlow = theme.accentColor,
            animatedBorder = meterState == MeterState.RUNNING,
            contentPadding = 10.dp,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(if (isEnglish) "CURRENT FARE" else "الأجرة الحالية", color = subTextColor, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = Fmt.money(MeterEngine.displayedFare),
                    fontSize = 40.sp,
                    fontWeight = FontWeight.Black,
                    color = theme.fareColor,
                    fontFamily = FontFamily.Monospace,
                    style = if (theme.isDark) neonTextStyle(theme.fareColor) else TextStyle.Default
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("JD", fontSize = 16.sp, fontWeight = FontWeight.Black, color = theme.fareColor, modifier = Modifier.padding(bottom = 8.dp))
            }
            AnimatedVisibility(visible = isTripActive) {
                val t = MeterEngine.lockedTariff
                val summary = if (t.fixedPrice != null) {
                    if (isEnglish) "🤝 Agreed fixed price" else "🤝 سعر متفق عليه مع العميل"
                } else {
                    val real = (if (isEnglish) " • Actual: " else " • الفعلي: ") + Fmt.rate(MeterEngine.rawFare)
                    "${t.name}$real"
                }
                Text(summary, color = subTextColor, fontSize = 11.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center)
            }
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            GlassCard(theme = theme, modifier = Modifier.weight(1f), corner = 22.dp, contentPadding = 8.dp, horizontalAlignment = Alignment.CenterHorizontally) {
                Text(if (isEnglish) "🛣️ Distance" else "🛣️ المسافة", color = subTextColor, fontSize = 11.sp)
                Text(Fmt.km(MeterEngine.distanceKm), color = textColor, fontSize = 21.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace)
                Text(if (isEnglish) "km" else "كيلومتر", color = subTextColor, fontSize = 10.sp)
            }
            GlassCard(theme = theme, modifier = Modifier.weight(1f), corner = 22.dp, contentPadding = 8.dp, horizontalAlignment = Alignment.CenterHorizontally) {
                Text(if (isEnglish) "⏱️ Duration" else "⏱️ زمن الرحلة", color = subTextColor, fontSize = 11.sp)
                Text(Fmt.clock(MeterEngine.elapsedSeconds), color = textColor, fontSize = 19.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace)
                Text(if (isEnglish) "h:m:s" else "ساعة : دقيقة : ثانية", color = subTextColor, fontSize = 9.sp)
            }
        }

        savedMessage?.let { msg ->
            Text(msg, color = Color(0xFF22C55E), fontSize = 13.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        }

        // إرسال إيصال آخر رحلة للراكب
        val receiptTrip = lastRecord
        if (!isTripActive && receiptTrip != null && !License.locked) {
            PillChip(
                text = if (isEnglish) "🧾 Send receipt to passenger" else "🧾 إرسال إيصال للراكب",
                selected = false,
                theme = theme,
                modifier = Modifier.fillMaxWidth()
            ) {
                SoundEffectsHelper.playClick(view, sound)
                val fresh = storage.getTrips().firstOrNull { it.id == receiptTrip.id } ?: receiptTrip
                ReceiptHelper.share(context, fresh, isEnglish)
            }
        }

        if (!isTripActive) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                // زر صغير: سعر متفق عليه مع العميل (يُخفى بعد انتهاء التجربة)
                val fp = fixedPrice
                if (!License.locked) {
                Box(
                    modifier = Modifier
                        .height(56.dp)
                        .bounceClick {
                            SoundEffectsHelper.playClick(view, sound)
                            if (fp == null) { fixedText = ""; fixedError = null; showFixedDialog = true } else fixedPrice = null
                        }
                        .clip(RoundedCornerShape(18.dp))
                        .background(if (fp != null) theme.accentColor.copy(alpha = 0.22f) else (if (theme.isDark) Color.White.copy(alpha = 0.06f) else Color.Black.copy(alpha = 0.05f)))
                        .border(1.dp, if (fp != null) theme.accentColor else theme.accentColor.copy(alpha = 0.45f), RoundedCornerShape(18.dp))
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (fp == null) (if (isEnglish) "🤝 Agreed\nprice" else "🤝 سعر\nمتفق")
                        else "🤝 ${Fmt.money(fp)}\n${if (isEnglish) "✕ cancel" else "✕ إلغاء"}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = textColor,
                        textAlign = TextAlign.Center,
                        lineHeight = 16.sp
                    )
                }
                }
                NeonActionButton(
                    text = if (isEnglish) "▶️ START TRIP" else "▶️ بدء رحلة جديدة",
                    colors = listOf(Color(0xFF16A34A), Color(0xFF22D3A0)),
                    modifier = Modifier.weight(1f),
                    height = 56.dp,
                    pulse = true,
                    textSize = 17.sp
                ) { startNewTrip() }
            }
        } else {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                NeonActionButton(
                    text = if (meterState == MeterState.PAUSED) (if (isEnglish) "▶️ RESUME" else "▶️ استئناف") else (if (isEnglish) "⏸ PAUSE" else "⏸ إيقاف مؤقت"),
                    colors = if (meterState == MeterState.PAUSED) listOf(Color(0xFF16A34A), Color(0xFF22D3A0)) else listOf(Color(0xFFD97706), Color(0xFFFBBF24)),
                    modifier = Modifier.weight(1f),
                    textSize = 14.sp
                ) { onPauseTap() }

                NeonActionButton(
                    text = if (waitingForSecondTap) (if (isEnglish) "⚠️ TAP AGAIN!" else "⚠️ اضغط للتأكيد!") else (if (isEnglish) "⏹ FINISH TRIP" else "⏹ إنهاء الرحلة"),
                    colors = if (waitingForSecondTap) listOf(Color(0xFF991B1B), Color(0xFFFF3B30)) else listOf(Color(0xFFEF4444), Color(0xFFFF6B8A)),
                    modifier = Modifier.weight(1.3f),
                    pulse = waitingForSecondTap,
                    textSize = 14.sp
                ) { onFinishTap() }
            }
        }
        } // نهاية الشاشة العادية (غير وضع القيادة)
    }

    // سؤال «بدأت رحلة؟»
    if (showAutoStart && !isTripActive) {
        val tc = textColorFor(theme)
        AlertDialog(
            onDismissRequest = { showAutoStart = false; autoSnoozeUntil = System.currentTimeMillis() + 15L * 60L * 1000L },
            containerColor = theme.cardBg,
            titleContentColor = tc,
            textContentColor = tc.copy(alpha = 0.85f),
            title = { Text(if (isEnglish) "🚗 Did a trip start?" else "🚗 بدأت رحلة؟", fontWeight = FontWeight.Black) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        if (isEnglish) "The car is moving and the meter is off." else "السيارة تتحرك والعداد متوقف.",
                        lineHeight = 20.sp
                    )
                    TextButton(onClick = {
                        showAutoStart = false
                        storage.saveAutoStartMutedUntil(endOfTodayMillis())
                    }) { Text(if (isEnglish) "Don't ask today" else "لا تسألني اليوم", color = tc.copy(alpha = 0.7f)) }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAutoStart = false; startNewTrip() }) {
                    Text(if (isEnglish) "Yes, start" else "نعم، ابدأ العداد", color = theme.accentColor, fontWeight = FontWeight.Black)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAutoStart = false; autoSnoozeUntil = System.currentTimeMillis() + 15L * 60L * 1000L }) {
                    Text(if (isEnglish) "No" else "لا", color = tc.copy(alpha = 0.8f), fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    // نافذة إدخال السعر المتفق عليه
    if (showFixedDialog) {
        val tc = textColorFor(theme)
        AlertDialog(
            onDismissRequest = { showFixedDialog = false },
            containerColor = theme.cardBg,
            titleContentColor = tc,
            textContentColor = tc.copy(alpha = 0.85f),
            title = { Text(if (isEnglish) "🤝 Agreed price" else "🤝 سعر متفق عليه مع العميل", fontWeight = FontWeight.Black) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        if (isEnglish) "The next trip will be recorded at this amount. Distance and time are still tracked."
                        else "الرحلة القادمة تُسجَّل بهذا المبلغ، مع حفظ المسافة والوقت للتوثيق.",
                        fontSize = 13.sp, lineHeight = 20.sp
                    )
                    OutlinedTextField(
                        value = fixedText,
                        onValueChange = { fixedText = it; fixedError = null },
                        label = { Text(if (isEnglish) "Amount (JD)" else "المبلغ (د.أ)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        colors = neonFieldColors(theme)
                    )
                    fixedError?.let { Text(it, color = Color(0xFFEF4444), fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val v = NumberInput.parse(fixedText)
                    if (v == null || v <= 0.0 || v > 500.0) {
                        fixedError = if (isEnglish) "⚠️ Enter a valid amount." else "⚠️ أدخل مبلغاً صحيحاً."
                    } else {
                        fixedPrice = v
                        showFixedDialog = false
                    }
                }) { Text(if (isEnglish) "Confirm" else "تأكيد", color = theme.accentColor, fontWeight = FontWeight.Black) }
            },
            dismissButton = {
                TextButton(onClick = { showFixedDialog = false }) {
                    Text(if (isEnglish) "Cancel" else "إلغاء", color = tc.copy(alpha = 0.8f), fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    // رحلة قصيرة جداً: غالباً بدأت بالخطأ
    if (showTinyTripDialog) {
        val tc = textColorFor(theme)
        AlertDialog(
            onDismissRequest = { showTinyTripDialog = false },
            containerColor = theme.cardBg,
            titleContentColor = tc,
            textContentColor = tc.copy(alpha = 0.85f),
            title = { Text(if (isEnglish) "Very short trip" else "رحلة قصيرة جداً", fontWeight = FontWeight.Black) },
            text = {
                Text(
                    if (isEnglish) "Less than 100 m and under a minute. Was it started by mistake? You can discard it so it doesn't count in your income."
                    else "المسافة أقل من 100 متر والمدة أقل من دقيقة. هل بدأتها بالخطأ؟ يمكنك إلغاؤها حتى لا تُحسب في دخلك.",
                    lineHeight = 22.sp
                )
            },
            confirmButton = {
                TextButton(onClick = { showTinyTripDialog = false; finishAndReport(save = true) }) {
                    Text(if (isEnglish) "Save trip" else "حفظ الرحلة", color = theme.accentColor, fontWeight = FontWeight.Black)
                }
            },
            dismissButton = {
                TextButton(onClick = { showTinyTripDialog = false; finishAndReport(save = false) }) {
                    Text(if (isEnglish) "Discard" else "إلغاء دون حفظ", color = Color(0xFFEF4444), fontWeight = FontWeight.Bold)
                }
            }
        )
    }
}

@Composable
fun GpsBadge(theme: AppTheme, isEnglish: Boolean, fresh: Boolean) {
    val acc = MeterEngine.gpsAccuracyM
    val (label, color) = when {
        !MeterEngine.hasLocationPermission || !MeterEngine.isGpsEnabled -> (if (isEnglish) "GPS off" else "GPS مطفأ") to Color(0xFFEF4444)
        !fresh -> (if (isEnglish) "No signal" else "لا إشارة") to Color(0xFFF59E0B)
        acc in 0f..15f -> (if (isEnglish) "GPS strong" else "GPS قوي") to Color(0xFF22C55E)
        else -> (if (isEnglish) "GPS weak" else "GPS ضعيف") to Color(0xFFF59E0B)
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = if (theme.isDark) 0.16f else 0.12f))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text("📡 $label", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = color)
    }
}

@Composable
fun WarningBanner(
    theme: AppTheme,
    color: Color,
    text: String,
    primaryText: String? = null,
    onPrimary: () -> Unit = {},
    secondaryText: String? = null,
    onSecondary: () -> Unit = {}
) {
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(color.copy(alpha = if (theme.isDark) 0.14f else 0.10f))
            .border(1.dp, color.copy(alpha = 0.6f), shape)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("⚠️ $text", color = textColorFor(theme), fontSize = 13.sp, fontWeight = FontWeight.Bold, lineHeight = 20.sp)
        if (primaryText != null || secondaryText != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (primaryText != null) {
                    Box(
                        modifier = Modifier
                            .bounceClick(onClick = onPrimary)
                            .clip(RoundedCornerShape(12.dp))
                            .background(color)
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    ) { Text(primaryText, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Black) }
                }
                if (secondaryText != null) {
                    Box(
                        modifier = Modifier
                            .bounceClick(onClick = onSecondary)
                            .clip(RoundedCornerShape(12.dp))
                            .border(1.dp, color, RoundedCornerShape(12.dp))
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    ) { Text(secondaryText, color = color, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}

@Composable
fun ExactSpeedometerDial240(speedKmh: Float, theme: AppTheme, isEnglish: Boolean, modifier: Modifier = Modifier) {
    val animatedSpeed by animateFloatAsState(
        targetValue = speedKmh.coerceIn(0f, 240f),
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "speed_dial"
    )
    // نُنشئ أداة رسم الأرقام مرة واحدة بدل كل إطار
    val textPaint = remember(theme.isDark) {
        Paint().apply {
            color = if (theme.isDark) android.graphics.Color.WHITE else android.graphics.Color.rgb(15, 23, 42)
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
    }

    BoxWithConstraints(modifier = modifier, contentAlignment = Alignment.Center) {
        val dialSize = minOf(maxWidth, maxHeight)
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeW = 10.dp.toPx()
            val diameter = size.minDimension - strokeW * 2
            // حماية: إذا لم تتوفر مساحة كافية للعداد لا نرسمه (التدرج اللوني يرفض نصف قطر سالب أو صفر)
            if (diameter <= 24.dp.toPx()) return@Canvas
            val radius = diameter / 2
            val centerOffset = center
            val topLeftOffset = Offset((size.width - diameter) / 2, (size.height - diameter) / 2)
            val tickColor = if (theme.isDark) Color.White else Color(0xFF0F172A)
            textPaint.textSize = size.minDimension * 0.05f

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(theme.gaugeColor.copy(alpha = if (theme.isDark) 0.16f else 0.10f), theme.cardBg.copy(alpha = 0.55f), Color.Transparent),
                    center = centerOffset,
                    radius = radius + 8.dp.toPx()
                ),
                radius = radius + 8.dp.toPx(),
                center = centerOffset
            )
            drawArc(
                color = if (theme.isDark) Color(0xFF1E293B).copy(alpha = 0.65f) else Color(0xFFCBD5E1),
                startAngle = 135f, sweepAngle = 270f, useCenter = false,
                topLeft = topLeftOffset, size = Size(diameter, diameter),
                style = Stroke(width = 7.dp.toPx(), cap = StrokeCap.Round)
            )
            val activeSweep = (animatedSpeed / 240f) * 270f
            if (activeSweep > 0.5f) {
                drawArc(
                    color = theme.gaugeColor.copy(alpha = 0.22f),
                    startAngle = 135f, sweepAngle = activeSweep, useCenter = false,
                    topLeft = topLeftOffset, size = Size(diameter, diameter),
                    style = Stroke(width = 16.dp.toPx(), cap = StrokeCap.Round)
                )
                drawArc(
                    brush = Brush.sweepGradient(
                        0f to theme.gaugeColor.copy(alpha = 0.45f),
                        0.375f to theme.gaugeColor.copy(alpha = 0.45f),
                        0.875f to theme.needleColor,
                        1f to theme.needleColor,
                        center = centerOffset
                    ),
                    startAngle = 135f, sweepAngle = activeSweep, useCenter = false,
                    topLeft = topLeftOffset, size = Size(diameter, diameter),
                    style = Stroke(width = 7.dp.toPx(), cap = StrokeCap.Round)
                )
            }
            for (i in 0..48) {
                val speedAtTick = i * 5
                val rad = Math.toRadians((135f + (speedAtTick / 240f) * 270f).toDouble())
                val isMajor = (speedAtTick % 30 == 0)
                val isLit = speedAtTick <= animatedSpeed
                val tickLen = if (isMajor) 14.dp.toPx() else 6.dp.toPx()
                val outerR = radius - 6.dp.toPx()
                drawLine(
                    color = if (isLit) theme.gaugeColor.copy(alpha = if (isMajor) 1f else 0.75f) else if (isMajor) tickColor else tickColor.copy(alpha = 0.45f),
                    start = Offset(centerOffset.x + (outerR * cos(rad)).toFloat(), centerOffset.y + (outerR * sin(rad)).toFloat()),
                    end = Offset(centerOffset.x + ((outerR - tickLen) * cos(rad)).toFloat(), centerOffset.y + ((outerR - tickLen) * sin(rad)).toFloat()),
                    strokeWidth = if (isMajor) 2.8.dp.toPx() else 1.4.dp.toPx(),
                    cap = StrokeCap.Round
                )
                if (isMajor) {
                    val numR = outerR - tickLen - 14.dp.toPx()
                    drawContext.canvas.nativeCanvas.drawText(
                        "$speedAtTick",
                        centerOffset.x + (numR * cos(rad)).toFloat(),
                        centerOffset.y + (numR * sin(rad)).toFloat() + 4.dp.toPx(),
                        textPaint
                    )
                }
            }
            val needleRad = Math.toRadians((135f + (animatedSpeed / 240f) * 270f).toDouble())
            val needleLen = radius * 0.74f
            val needleEnd = Offset(centerOffset.x + (needleLen * cos(needleRad)).toFloat(), centerOffset.y + (needleLen * sin(needleRad)).toFloat())
            drawLine(color = theme.needleColor.copy(alpha = 0.28f), start = centerOffset, end = needleEnd, strokeWidth = 11.dp.toPx(), cap = StrokeCap.Round)
            drawLine(color = theme.needleColor, start = centerOffset, end = needleEnd, strokeWidth = 4.5.dp.toPx(), cap = StrokeCap.Round)
            drawCircle(theme.needleColor.copy(alpha = 0.30f), 13.dp.toPx(), centerOffset)
            drawCircle(theme.needleColor, 8.dp.toPx(), centerOffset)
            drawCircle(theme.cardBg, 3.5.dp.toPx(), centerOffset)
        }

        if (dialSize >= 90.dp) Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(top = dialSize * 0.14f)) {
            Text(
                text = "${animatedSpeed.roundToInt()}",
                fontSize = (dialSize.value * 0.16f).sp,
                fontWeight = FontWeight.Black,
                color = if (theme.isDark) Color.White else Color(0xFF0F172A),
                fontFamily = FontFamily.Monospace,
                style = if (theme.isDark) neonTextStyle(theme.gaugeColor, 18f, 0.55f) else TextStyle.Default
            )
            Text(
                text = if (isEnglish) "KM/H" else "كم/س",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = if (theme.isDark) Color(0xFF94A3B8) else Color(0xFF64748B)
            )
        }
    }
}

/** نهاية اليوم الحالي (لخيار «لا تسألني اليوم») */
private fun endOfTodayMillis(): Long = java.util.Calendar.getInstance().apply {
    set(java.util.Calendar.HOUR_OF_DAY, 23); set(java.util.Calendar.MINUTE, 59); set(java.util.Calendar.SECOND, 59)
}.timeInMillis

// ======================================================================
// وضع القيادة: شاشة كبيرة وبسيطة أثناء الرحلة
// ======================================================================
@Composable
private fun DrivingModeView(
    theme: AppTheme,
    isEnglish: Boolean,
    waitingForSecondTap: Boolean,
    onPause: () -> Unit,
    onFinish: () -> Unit,
    onExit: () -> Unit
) {
    val textColor = textColorFor(theme)
    val subTextColor = subTextColorFor(theme)
    val paused = MeterEngine.meterState == MeterState.PAUSED

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (isEnglish) "🖥️ Driving mode" else "🖥️ وضع القيادة",
                modifier = Modifier.weight(1f),
                fontSize = 18.sp, fontWeight = FontWeight.Black, color = textColor
            )
            GlassIconButton(icon = "✕", theme = theme, size = 44.dp, onClick = onExit)
        }

        Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = if (paused) (if (isEnglish) "⏸ Paused" else "⏸ متوقفة مؤقتاً") else (if (isEnglish) "● Trip running" else "● رحلة جارية"),
                    color = if (paused) Color(0xFFF59E0B) else Color(0xFF22C55E),
                    fontSize = 16.sp, fontWeight = FontWeight.Bold
                )
                Text(
                    text = Fmt.money(MeterEngine.displayedFare),
                    fontSize = 88.sp,
                    fontWeight = FontWeight.Black,
                    color = theme.fareColor,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    style = if (theme.isDark) neonTextStyle(theme.fareColor) else TextStyle.Default
                )
                Text(if (isEnglish) "Jordanian Dinar" else "دينار أردني", fontSize = 16.sp, color = subTextColor, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(18.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(22.dp), verticalAlignment = Alignment.CenterVertically) {
                    DrivingStat("🛣️", Fmt.km(MeterEngine.distanceKm), if (isEnglish) "km" else "كم", textColor, subTextColor)
                    DrivingStat("⏱️", Fmt.clock(MeterEngine.elapsedSeconds), if (isEnglish) "time" else "الوقت", textColor, subTextColor)
                    DrivingStat("🚗", MeterEngine.speedKmh.toInt().toString(), if (isEnglish) "km/h" else "كم/س", textColor, subTextColor)
                }
            }
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            NeonActionButton(
                text = if (paused) (if (isEnglish) "▶️ RESUME" else "▶️ استئناف") else (if (isEnglish) "⏸ PAUSE" else "⏸ إيقاف مؤقت"),
                colors = if (paused) listOf(Color(0xFF16A34A), Color(0xFF22D3A0)) else listOf(Color(0xFFD97706), Color(0xFFFBBF24)),
                modifier = Modifier.weight(1f),
                height = 84.dp,
                textSize = 20.sp,
                onClick = onPause
            )
            NeonActionButton(
                text = if (waitingForSecondTap) (if (isEnglish) "⚠️ TAP AGAIN!" else "⚠️ اضغط للتأكيد!") else (if (isEnglish) "⏹ FINISH" else "⏹ إنهاء"),
                colors = if (waitingForSecondTap) listOf(Color(0xFF991B1B), Color(0xFFFF3B30)) else listOf(Color(0xFFEF4444), Color(0xFFFF6B8A)),
                modifier = Modifier.weight(1.2f),
                height = 84.dp,
                pulse = waitingForSecondTap,
                textSize = 20.sp,
                onClick = onFinish
            )
        }
    }
}

@Composable
private fun DrivingStat(icon: String, value: String, unit: String, textColor: Color, subTextColor: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(icon, fontSize = 22.sp)
        Text(value, fontSize = 24.sp, fontWeight = FontWeight.Black, color = textColor, fontFamily = FontFamily.Monospace)
        Text(unit, fontSize = 12.sp, color = subTextColor)
    }
}

// زر دائري في الترويسة مع اسم صغير تحته
@Composable
private fun HeaderLabeledButton(icon: String, label: String, theme: AppTheme, labelColor: Color, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        GlassIconButton(icon = icon, theme = theme, size = 40.dp, onClick = onClick)
        Spacer(modifier = Modifier.height(2.dp))
        Text(label, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = labelColor, maxLines = 1)
    }
}

/** تحية حسب وقت اليوم */
private fun greetingText(isEnglish: Boolean): String {
    val h = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
    return if (isEnglish) when (h) {
        in 4..10 -> "Good morning, captain ☀️"
        in 11..14 -> "Have a great day, captain 🌤️"
        in 15..18 -> "Good afternoon, captain 🌇"
        in 19..23 -> "Good evening, captain 🌙"
        else -> "Stay safe tonight, captain 🌌"
    } else when (h) {
        in 4..10 -> "صباح الخير يا كابتن ☀️"
        in 11..14 -> "نهارك سعيد يا كابتن 🌤️"
        in 15..18 -> "مساء الخير يا كابتن 🌇"
        in 19..23 -> "مساء النور يا كابتن 🌙"
        else -> "الله يعطيك العافية 🌌"
    }
}
