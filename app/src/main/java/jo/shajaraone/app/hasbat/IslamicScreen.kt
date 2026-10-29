package jo.shajaraone.app.hasbat

import jo.shajaraone.app.R
import jo.shajaraone.app.MainActivity

import android.content.Context
import android.hardware.GeomagneticField
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun AdhkarAndIslamicScreen(storage: AppDataManager, theme: AppTheme, isEnglish: Boolean, onBack: () -> Unit) {
    val view = LocalView.current
    var selectedIslamicTab by remember { mutableIntStateOf(0) }
    val textColor = textColorFor(theme)

    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            GlassIconButton(icon = if (isEnglish) "⬅️" else "➡️", theme = theme, size = 38.dp, onClick = onBack)
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = when (selectedIslamicTab) {
                    0 -> if (isEnglish) "📿 Daily Adhkar" else "📿 الأذكار اليومية"
                    1 -> if (isEnglish) "🕌 Prayer Times" else "🕌 أوقات الصلاة"
                    else -> if (isEnglish) "🕋 Qibla Compass" else "🕋 اتجاه القبلة الشريفة"
                },
                fontSize = 19.sp, fontWeight = FontWeight.Black, color = textColor
            )
        }

        SegmentedTabs(
            items = listOf(
                if (isEnglish) "📿 Adhkar" else "📿 الأذكار",
                if (isEnglish) "🕌 Prayers" else "🕌 الصلاة",
                if (isEnglish) "🕋 Qibla" else "🕋 القبلة"
            ),
            selectedIndex = selectedIslamicTab,
            theme = theme
        ) { SoundEffectsHelper.playClick(view, storage.soundEnabled); selectedIslamicTab = it }

        when (selectedIslamicTab) {
            0 -> AdhkarContent(theme, storage, isEnglish)
            1 -> PrayerTimesContent(theme, isEnglish)
            // البوصلة تعمل فقط في هذا التبويب لتوفير البطارية
            else -> QiblaCompassContent(theme, isEnglish)
        }
    }
}

@Composable
fun AdhkarContent(theme: AppTheme, storage: AppDataManager, isEnglish: Boolean) {
    val view = LocalView.current
    var selectedType by remember { mutableIntStateOf(if (Calendar.getInstance().get(Calendar.HOUR_OF_DAY) >= 15) 1 else 0) }
    val textColor = textColorFor(theme)
    val today = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())

    val morningAdhkar = remember {
        listOf(
            DhikrItem("أَصْبَحْنَا وَأَصْبَحَ المُلْكُ لِلَّهِ، وَالْحَمْدُ لِلَّهِ لاَ إِلَهَ إِلاَّ اللَّهُ وَحْدَهُ لاَ شَرِيكَ لَهُ، لَهُ المُلْكُ وَلَهُ الْحَمْدُ وَهُوَ عَلَى كُلِّ شَيْءٍ قَدِيرٌ.", "حفظ وبركة اليوم", 1),
            DhikrItem("اللَّهُمَّ بِكَ أَصْبَحْنَا، وَبِكَ أَمْسَيْنَا، وَبِكَ نَحْيَا، وَبِكَ نَمُوتُ، وَإِلَيْكَ النُّشُورُ.", "توكل وتسليم", 1),
            DhikrItem("سَيِّدُ الاِسْتِغْفَارِ: اللَّهُمَّ أَنْتَ رَبِّي لاَ إِلَهَ إِلاَّ أَنْتَ، خَلَقْتَنِي وَأَنَا عَبْدُكَ، وَأَنَا عَلَى عهدِكَ وَوَعْدِكَ مَا اسْتَطَعْتُ، أَعُوذُ بِكَ مِنْ شَرِّ مَا صَنَعْتُ، أَبُوءُ لَكَ بِنِعْمَتِكَ عَلَيَّ، وَأَبُوءُ لَكَ بِذَنْبِي فَاغْفِرْ لِي فَإِنَّهُ لاَ يَغْفِرُ الذُّنُوبَ إِلاَّ أَنْتَ.", "موجبة للجنة", 1),
            DhikrItem("بِسْمِ اللَّهِ الَّذِي لاَ يَضُرُّ مَعَ اسْمِهِ شَيْءٌ فِي الأَرْضِ وَلاَ فِي السَّمَاءِ وَهُوَ السَّمِيعُ العَلِيمُ.", "لم يضره شيء", 3),
            DhikrItem("حَسْبِيَ اللَّهُ لاَ إِلَهَ إِلاَّ هُوَ عَلَيْهِ تَوَكَّلْتُ وَهُوَ رَبُّ العَرْشِ العَظِيمِ.", "كفاه الله ما أهمه", 7)
        )
    }

    val eveningAdhkar = remember {
        listOf(
            DhikrItem("أَمْسَيْنَا وَأَمْسَى المُلْكُ لِلَّهِ، وَالْحَمْدُ لِلَّهِ لاَ إِلَهَ إِلاَّ اللَّهُ وَحْدَهُ لاَ شَرِيكَ لَهُ، لَهُ المُلْكُ وَلَهُ الْحَمْدُ وَهُوَ عَلَى كُلِّ شَيْءٍ قَدِيرٌ.", "حفظ الليلة", 1),
            DhikrItem("اللَّهُمَّ بِكَ أَمْسَيْنَا، وَبِكَ أَصْبَحْنَا، وَبِكَ نَحْيَا، وَبِكَ نَمُوتُ، وَإِلَيْكَ الْمَصِيرُ.", "تسليم وتفويض", 1),
            DhikrItem("أَعُوذُ بِكَلِمَاتِ اللَّهِ التَّامَّاتِ مِنْ شَرِّ مَا خَلَقَ.", "حفظ من الهوام والشرور", 3),
            DhikrItem("بِسْمِ اللَّهِ الَّذِي لاَ يَضُرُّ مَعَ اسْمِهِ شَيْءٌ فِي الأَرْضِ وَلاَ فِي السَّمَاءِ وَهُوَ السَّمِيعُ العَلِيمُ.", "لم يضره شيء", 3),
            DhikrItem("يَا حَيُّ يَا قَيُّومُ بِرَحْمَتِكَ أَسْتَغِيثُ، أَصْلِحْ لِي شَأْنِي كُلَّهُ، وَلاَ تَكِلْنِي إِلَى نَفْسِي طَرْفَةَ عَيْنٍ.", "صلاح الشأن والرزق", 1)
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(if (isEnglish) "☀️ Morning" else "☀️ أذكار الصباح", if (isEnglish) "🌙 Evening" else "🌙 أذكار المساء").forEachIndexed { index, title ->
                PillChip(text = title, selected = (selectedType == index), theme = theme, modifier = Modifier.weight(1f)) {
                    SoundEffectsHelper.playClick(view, storage.soundEnabled); selectedType = index
                }
            }
        }

        val activeList = if (selectedType == 0) morningAdhkar else eveningAdhkar
        LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 8.dp)) {
            itemsIndexed(activeList) { index, item ->
                // العداد محفوظ لليوم الحالي ولا يرجع للصفر عند التنقل بين الشاشات
                val countKey = "${selectedType}_$index"
                var currentCount by remember(countKey, today) { mutableIntStateOf(storage.getDhikrCount(countKey, today)) }
                val isFinished = currentCount >= item.targetCount
                val progress by animateFloatAsState(
                    targetValue = if (item.targetCount > 0) currentCount.toFloat() / item.targetCount.toFloat() else 0f,
                    animationSpec = tween(320, easing = FastOutSlowInEasing),
                    label = "dhikr_progress"
                )
                val doneGreen = Color(0xFF22C55E)
                GlassCard(
                    theme = theme,
                    modifier = Modifier.fillMaxWidth(),
                    corner = 20.dp,
                    glow = if (isFinished) doneGreen else theme.accentColor,
                    secondGlow = if (isFinished) doneGreen else theme.needleColor,
                    contentPadding = 14.dp,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(item.text, fontSize = 15.sp, color = textColor, lineHeight = 25.sp, fontWeight = FontWeight.Medium)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(if (theme.isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.07f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(progress.coerceIn(0f, 1f))
                                .fillMaxHeight()
                                .background(Brush.horizontalGradient(listOf(if (isFinished) doneGreen else theme.accentColor, if (isFinished) doneGreen else theme.needleColor)))
                        )
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("💡 ${item.virtue}", fontSize = 11.sp, color = subTextColorFor(theme), modifier = Modifier.weight(1f))
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .bounceClick(enabled = !isFinished) {
                                    SoundEffectsHelper.playClick(view, storage.soundEnabled)
                                    if (currentCount < item.targetCount) {
                                        currentCount++
                                        storage.saveDhikrCount(countKey, today, currentCount)
                                    }
                                }
                                .clip(RoundedCornerShape(14.dp))
                                .background(
                                    if (isFinished) Brush.horizontalGradient(listOf(doneGreen, Color(0xFF16A34A)))
                                    else Brush.horizontalGradient(listOf(theme.accentColor, theme.needleColor))
                                )
                                .padding(horizontal = 18.dp, vertical = 9.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                if (isFinished) "✓" else "$currentCount / ${item.targetCount}",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Black,
                                color = if (isFinished) Color.White else readableOn(theme.accentColor)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PrayerTimesContent(theme: AppTheme, isEnglish: Boolean) {
    val lat = MeterEngine.latitude
    val lng = MeterEngine.longitude
    // نقرّب الموقع حتى لا يُعاد الحساب مع كل قراءة GPS
    val latKey = (lat * 20).toInt()
    val lngKey = (lng * 20).toInt()
    val today = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
    val minutes = remember(latKey, lngKey, today) { IslamicCalculationHelper.prayerMinutes(lat, lng) }
    val textColor = textColorFor(theme)
    val todayFmt = remember(today, isEnglish) {
        SimpleDateFormat("EEEE, d MMMM yyyy", if (isEnglish) Locale.US else Locale.forLanguageTag("ar")).format(Date())
    }
    val nowMinutes = Calendar.getInstance().let { it.get(Calendar.HOUR_OF_DAY) * 60 + it.get(Calendar.MINUTE) }
    // الصلاة القادمة (نتجاهل الشروق)
    val nextIndex = listOf(0, 2, 3, 4, 5).firstOrNull { minutes[it] > nowMinutes } ?: 0

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(7.dp)) {
        GlassCard(theme = theme, modifier = Modifier.fillMaxWidth(), corner = 22.dp, contentPadding = 10.dp, horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                if (lat == 0.0 && lng == 0.0) (if (isEnglish) "🕌 Prayer times (Amman — no GPS yet)" else "🕌 أوقات الصلاة (عمّان — بانتظار الموقع)")
                else (if (isEnglish) "🕌 Prayer times for your location" else "🕌 أوقات الصلاة حسب موقعك"),
                fontWeight = FontWeight.Bold, fontSize = 14.sp, color = theme.accentColor
            )
            Text(todayFmt, fontSize = 11.sp, color = subTextColorFor(theme))
        }
        minutes.forEachIndexed { i, m ->
            val isNext = i == nextIndex
            GlassCard(
                theme = theme,
                modifier = Modifier.fillMaxWidth(),
                corner = 18.dp,
                glow = if (isNext) theme.fareColor else theme.accentColor,
                contentPadding = 10.dp
            ) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(IslamicCalculationHelper.prayerIcons[i], fontSize = 20.sp)
                        Text(
                            if (isEnglish) IslamicCalculationHelper.prayerNamesEn[i] else IslamicCalculationHelper.prayerNamesAr[i],
                            fontWeight = FontWeight.Bold, fontSize = 15.sp, color = textColor
                        )
                        if (isNext) Text(if (isEnglish) "• Next" else "• القادمة", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = theme.fareColor)
                    }
                    Text(
                        IslamicCalculationHelper.formatMinutes(m, isEnglish),
                        fontWeight = FontWeight.Black,
                        fontSize = 16.sp,
                        color = theme.fareColor,
                        style = if (theme.isDark) neonTextStyle(theme.fareColor, 12f, 0.4f) else TextStyle.Default
                    )
                }
            }
        }
        Text(
            if (isEnglish) "Calculated: Fajr & Isha 18°, Asr standard. Times may differ by a minute or two from the official calendar."
            else "طريقة الحساب: الفجر والعشاء 18°، والعصر على المذهب الشافعي. قد تختلف دقيقة أو دقيقتين عن التقويم الرسمي.",
            fontSize = 10.sp, color = subTextColorFor(theme), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
fun QiblaCompassContent(theme: AppTheme, isEnglish: Boolean) {
    val context = LocalContext.current
    val textColor = textColorFor(theme)
    val lat = MeterEngine.latitude
    val lng = MeterEngine.longitude
    val qiblaBearing = remember((lat * 100).toInt(), (lng * 100).toInt()) { IslamicCalculationHelper.getQiblaBearing(lat, lng) }

    // الفرق بين الشمال المغناطيسي والجغرافي (في الأردن حوالي 4–5 درجات)
    val declination = remember((lat * 10).toInt(), (lng * 10).toInt()) {
        val uLat = if (lat == 0.0 && lng == 0.0) IslamicCalculationHelper.DEFAULT_LAT else lat
        val uLng = if (lat == 0.0 && lng == 0.0) IslamicCalculationHelper.DEFAULT_LNG else lng
        GeomagneticField(uLat.toFloat(), uLng.toFloat(), 0f, System.currentTimeMillis()).declination
    }

    var deviceAzimuth by remember { mutableFloatStateOf(0f) }
    var hasSensor by remember { mutableStateOf(true) }
    var lowAccuracy by remember { mutableStateOf(false) }

    DisposableEffect(declination) {
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val rotSensor = sm?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        hasSensor = rotSensor != null
        var sinAvg = 0.0
        var cosAvg = 1.0
        var first = true
        val rotMatrix = FloatArray(9)
        val orientation = FloatArray(3)
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                if (event.sensor.type != Sensor.TYPE_ROTATION_VECTOR) return
                SensorManager.getRotationMatrixFromVector(rotMatrix, event.values)
                SensorManager.getOrientation(rotMatrix, orientation)
                val trueAz = Math.toDegrees(orientation[0].toDouble()) + declination
                val rad = Math.toRadians(trueAz)
                // تنعيم دائري حتى لا يرتجف السهم
                if (first) { sinAvg = sin(rad); cosAvg = cos(rad); first = false }
                else { sinAvg = 0.85 * sinAvg + 0.15 * sin(rad); cosAvg = 0.85 * cosAvg + 0.15 * cos(rad) }
                deviceAzimuth = ((Math.toDegrees(atan2(sinAvg, cosAvg)) + 360.0) % 360.0).toFloat()
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
                lowAccuracy = accuracy <= SensorManager.SENSOR_STATUS_ACCURACY_LOW
            }
        }
        if (rotSensor != null) sm?.registerListener(listener, rotSensor, SensorManager.SENSOR_DELAY_UI)
        onDispose { sm?.unregisterListener(listener) }
    }

    val angleDiff = ((qiblaBearing - deviceAzimuth + 540f) % 360f) - 180f
    val isAligned = hasSensor && abs(angleDiff) <= 3.0f
    val alignedGreen = Color(0xFF22C55E)
    val ringColor by animateColorAsState(targetValue = if (isAligned) alignedGreen else theme.accentColor, animationSpec = tween(300), label = "qibla_ring")

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        GlassCard(theme = theme, modifier = Modifier.fillMaxWidth(), corner = 22.dp, glow = ringColor, contentPadding = 14.dp, horizontalAlignment = Alignment.CenterHorizontally) {
            Text(if (isEnglish) "🕋 Qibla Direction" else "🕋 اتجاه القبلة الشريفة (مكة المكرمة)", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = textColor)
            Text(
                text = when {
                    !hasSensor -> if (isEnglish) "This phone has no compass sensor." else "هذا الهاتف لا يحتوي على حساس بوصلة."
                    isAligned -> if (isEnglish) "🟢 You are facing the Qibla" else "🟢 أنت باتجاه القبلة"
                    else -> if (isEnglish) "Qibla: ${qiblaBearing.toInt()}° ${IslamicCalculationHelper.directionName(qiblaBearing, true)}"
                    else "زاوية القبلة: ${qiblaBearing.toInt()}° ${IslamicCalculationHelper.directionName(qiblaBearing, false)}"
                },
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                color = if (isAligned) alignedGreen else theme.accentColor
            )
        }

        Box(
            modifier = Modifier
                .size(230.dp)
                .clip(CircleShape)
                .background(Brush.radialGradient(listOf(ringColor.copy(alpha = 0.18f), theme.cardBg.copy(alpha = 0.88f))))
                .border(3.dp, Brush.sweepGradient(listOf(ringColor, theme.needleColor, ringColor)), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Box(modifier = Modifier.fillMaxSize().rotate(-deviceAzimuth), contentAlignment = Alignment.Center) {
                Canvas(modifier = Modifier.fillMaxSize().padding(8.dp)) {
                    val r = size.minDimension / 2f
                    for (i in 0 until 36) {
                        val rad = Math.toRadians((i * 10.0) - 90.0)
                        val major = (i % 9 == 0)
                        val len = if (major) 12.dp.toPx() else 6.dp.toPx()
                        drawLine(
                            color = if (major) ringColor else textColor.copy(alpha = 0.35f),
                            start = Offset(center.x + (r * cos(rad)).toFloat(), center.y + (r * sin(rad)).toFloat()),
                            end = Offset(center.x + ((r - len) * cos(rad)).toFloat(), center.y + ((r - len) * sin(rad)).toFloat()),
                            strokeWidth = if (major) 2.5.dp.toPx() else 1.2.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                    }
                }
                Box(modifier = Modifier.fillMaxSize().rotate(qiblaBearing), contentAlignment = Alignment.TopCenter) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(top = 22.dp)) {
                        Text("🕋", fontSize = 26.sp)
                        Text(if (isEnglish) "Qibla" else "القبلة", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = if (isAligned) alignedGreen else theme.accentColor)
                    }
                }
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                    Text("N", fontSize = 12.sp, fontWeight = FontWeight.Black, color = Color.Red, modifier = Modifier.padding(top = 4.dp))
                }
            }
            Text(
                "▲",
                fontSize = 40.sp,
                fontWeight = FontWeight.Black,
                color = if (isAligned) alignedGreen else theme.needleColor,
                modifier = Modifier.rotate(angleDiff),
                style = if (theme.isDark) neonTextStyle(if (isAligned) alignedGreen else theme.needleColor, 20f, 0.6f) else TextStyle.Default
            )
        }

        if (lowAccuracy) {
            Text(
                if (isEnglish) "⚠️ Compass needs calibration: move the phone in a figure-8 a few times."
                else "⚠️ البوصلة تحتاج معايرة: حرّك الهاتف على شكل رقم 8 عدة مرات.",
                fontSize = 12.sp, color = Color(0xFFF59E0B), fontWeight = FontWeight.Bold, textAlign = TextAlign.Center
            )
        }
        Text(
            text = if (isEnglish) "Hold the phone flat, away from metal and magnets, until the arrow points to the Kaaba 🕋"
            else "امسك الهاتف مستوياً وبعيداً عن المعادن والمغناطيس حتى يشير السهم نحو الكعبة 🕋 ويتحول للأخضر.",
            fontSize = 11.sp,
            color = subTextColorFor(theme),
            textAlign = TextAlign.Center
        )
    }
}
