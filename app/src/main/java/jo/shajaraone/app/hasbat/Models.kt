package jo.shajaraone.app.hasbat

import jo.shajaraone.app.R
import jo.shajaraone.app.MainActivity

import androidx.compose.ui.graphics.Color

/** سجل رحلة مكتملة. الحقول الجديدة لها قيم افتراضية حتى تبقى الرحلات القديمة متوافقة. */
data class TripRecord(
    val id: Long,
    val dateStr: String,
    val startTimeStr: String,
    val endTimeStr: String,
    val distanceKm: Double,
    val durationSeconds: Long,
    val totalFare: Double,
    val appliedBaseFare: Double,
    val appliedKmPrice: Double,
    val appliedMinPrice: Double,
    val startAddress: String = "غير محدد",
    val endAddress: String = "غير محدد",
    val tariffName: String = "",
    val multiplier: Double = 1.0,
    val isFixedPrice: Boolean = false,
    val startLat: Double = 0.0,
    val startLng: Double = 0.0,
    val endLat: Double = 0.0,
    val endLng: Double = 0.0,
    /** ثواني الوقوف/السير البطيء المحتسبة في الأجرة (-1 = رحلة قديمة: كل الوقت محتسب) */
    val billedSeconds: Long = -1L
)

data class ExpenseRecord(val id: Long, val dateStr: String, val category: String, val amount: Double, val note: String)

/** تعرفة محفوظة يختارها الكابتن قبل الرحلة (عادي، ذروة، تطبيق معيّن...). */
data class TariffPreset(val id: Long, val name: String, val base: Double, val km: Double, val min: Double)

/** التعرفة المثبتة للرحلة الجارية: لا تتغير حتى لو عدّل الكابتن التعرفات أثناء الرحلة. */
data class TripTariff(
    val name: String,
    val base: Double,
    val km: Double,
    val min: Double,
    val multiplier: Double = 1.0,
    /** إذا لم تكن null فالرحلة بسعر ثابت متفق عليه مع العميل. */
    val fixedPrice: Double? = null
) {
    companion object {
        val DEFAULT = TripTariff("عادي", 0.45, 0.25, 0.05)
    }
}

data class DhikrItem(val text: String, val virtue: String, val targetCount: Int)

enum class MeterState { IDLE, RUNNING, PAUSED }

enum class AppTheme(
    val titleAr: String, val titleEn: String, val isDark: Boolean,
    val bgGradient: List<Color>, val cardBg: Color, val gaugeColor: Color,
    val needleColor: Color, val accentColor: Color, val fareColor: Color
) {
    BLACK_RED("الأسود الفاحم والأحمر", "Black & Red", true, listOf(Color(0xFF000000), Color(0xFF07070A)), Color(0xFF0E0E12), Color(0xFFFF1744), Color(0xFFFF1744), Color(0xFFFF1744), Color(0xFFFFD700)),
    CLASSIC_BLUE("الكلاسيكي الملكي", "Classic Blue", true, listOf(Color(0xFF040A17), Color(0xFF0A152B)), Color(0xFF0F1E38), Color(0xFF00D2FF), Color(0xFF00F0FF), Color(0xFF00D2FF), Color(0xFFFFD200)),
    DARK_RED("الداكن الفاخر", "Dark Red", true, listOf(Color(0xFF0A0A0E), Color(0xFF14141A)), Color(0xFF181822), Color(0xFFFF2A55), Color(0xFFFF5252), Color(0xFFFF2A55), Color(0xFFFFC107)),
    LIGHT_GREEN("الفاتح العصري", "Light Green", false, listOf(Color(0xFFF0F5F2), Color(0xFFE2EBE6)), Color(0xFFFFFFFF), Color(0xFF00C853), Color(0xFF00E676), Color(0xFF00C853), Color(0xFFD97706)),
    PURPLE_NEON("البنفسجي النيوني", "Purple Neon", true, listOf(Color(0xFF0F021F), Color(0xFF1A0536)), Color(0xFF220844), Color(0xFFD500F9), Color(0xFFE040FB), Color(0xFFD500F9), Color(0xFFFFD600)),
    GOLDEN_LUXURY("الذهبي الملكي", "Golden Luxury", true, listOf(Color(0xFF121110), Color(0xFF090807)), Color(0xFF1E1C1A), Color(0xFFFFB300), Color(0xFFFFC107), Color(0xFFFFB300), Color(0xFFFFD700)),
    NATURE_GREEN("الطبيعي الزمردي", "Nature Green", true, listOf(Color(0xFF05170E), Color(0xFF020B07)), Color(0xFF0D291B), Color(0xFF00E676), Color(0xFF69F0AE), Color(0xFF00E676), Color(0xFFFFD700)),
    CHERRY_PINK("الوردي الأنيق", "Cherry Pink", false, listOf(Color(0xFFFFF0F4), Color(0xFFFCE4EC)), Color(0xFFFFFFFF), Color(0xFFFF4081), Color(0xFFFF80AB), Color(0xFFFF4081), Color(0xFFE91E63)),
    CYBER_GRADIENT("السايبر المتدرج", "Cyber Gradient", true, listOf(Color(0xFF091226), Color(0xFF030712)), Color(0xFF111E3B), Color(0xFF00F0FF), Color(0xFFFF007F), Color(0xFF00F0FF), Color(0xFFFFD700)),
    SUNSET_ORANGE("البرتقالي الناري", "Sunset Orange", true, listOf(Color(0xFF170902), Color(0xFF0B0401)), Color(0xFF261205), Color(0xFFFF6D00), Color(0xFFFF9100), Color(0xFFFF6D00), Color(0xFFFFCA28)),

    // ---- ثيمات 2026 الجديدة (مضافة في النهاية حتى لا يتغير اختيار المستخدم الحالي) ----
    GOLD_NOIR("الذهبي الأسود", "Gold Noir", true, listOf(Color(0xFF0B0B0C), Color(0xFF17130A)), Color(0xFF1A1710), Color(0xFFC9932A), Color(0xFFF5C451), Color(0xFFF5C451), Color(0xFFFFD873)),
    AURORA_GLASS("الشفق الزجاجي", "Aurora Glass", true, listOf(Color(0xFF060B1E), Color(0xFF14093A)), Color(0xFF141A3A), Color(0xFFA78BFA), Color(0xFF5EEAD4), Color(0xFF5EEAD4), Color(0xFF99F6E4)),
    CARBON_SPORT("الكربون الرياضي", "Carbon Sport", true, listOf(Color(0xFF111214), Color(0xFF1C1D21)), Color(0xFF202126), Color(0xFFFF8A00), Color(0xFFFF3B30), Color(0xFFFF3B30), Color(0xFFFFFFFF)),
    OLED_PURE("أسود أوليد (توفير بطارية)", "OLED Pure", true, listOf(Color(0xFF000000), Color(0xFF000000)), Color(0xFF0A0A0A), Color(0xFF7CFF6B), Color(0xFFC6FF3D), Color(0xFFC6FF3D), Color(0xFFC6FF3D)),
    DAYLIGHT_PRO("نهاري عالي الوضوح", "Daylight Pro", false, listOf(Color(0xFFF6F8FB), Color(0xFFE9EEF5)), Color(0xFFFFFFFF), Color(0xFF00B3A4), Color(0xFF0A66FF), Color(0xFF0A66FF), Color(0xFF0B1F44)),
    PETRA_SAND("رمال البتراء", "Petra Sand", false, listOf(Color(0xFFF7EDE2), Color(0xFFEAD6C3)), Color(0xFFFFF8F1), Color(0xFFE0915C), Color(0xFFC2552D), Color(0xFFC2552D), Color(0xFF7A2E14)),
    EMERALD_LUX("الزمرد الفاخر", "Emerald Lux", true, listOf(Color(0xFF03140E), Color(0xFF082A1E)), Color(0xFF0B2A1E), Color(0xFFD4AF37), Color(0xFF34D399), Color(0xFF34D399), Color(0xFFF2D27A)),
    MIDNIGHT_NEON("منتصف الليل النيوني", "Midnight Neon", true, listOf(Color(0xFF070A1A), Color(0xFF0E1440)), Color(0xFF151B45), Color(0xFFEC4899), Color(0xFF3B82F6), Color(0xFF3B82F6), Color(0xFFF0ABFC))
}

/** حساب الأجرة في مكان واحد حتى تتطابق الشاشة والإشعار والسجل والحاسبة. */
object FareCalculator {
    const val MIN_FARE = 1.0

    fun metered(tariff: TripTariff, distanceKm: Double, elapsedSeconds: Long): Double =
        (tariff.base + distanceKm * tariff.km + (elapsedSeconds / 60.0) * tariff.min) * tariff.multiplier

    /** الأجرة الفعلية قبل تطبيق الحد الأدنى. */
    fun raw(tariff: TripTariff, distanceKm: Double, elapsedSeconds: Long): Double =
        tariff.fixedPrice ?: metered(tariff, distanceKm, elapsedSeconds)

    /** الأجرة المعروضة للعميل: السعر الثابت كما هو، أو العداد مع حد أدنى دينار. */
    fun displayed(tariff: TripTariff, distanceKm: Double, elapsedSeconds: Long): Double =
        tariff.fixedPrice ?: maxOf(MIN_FARE, metered(tariff, distanceKm, elapsedSeconds))
}
