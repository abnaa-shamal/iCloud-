package jo.shajaraone.app

import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Renders the REAL components with sample data to PNG files (uploaded by CI so the design can be seen before installing). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w390dp-h844dp-xxhdpi")
class DesignShotsTest {
    @get:Rule val rule = createComposeRule()

    private val soon = System.currentTimeMillis() + 3 * 3_600_000L
    private val t1 = Trip(
        id = "1", captainUid = "c", captainName = "أحمد", captainPhone = "0790000000", carText = "كيا سيراتو بيضاء",
        from = "الشجرة", to = "إربد", road = "الطريق الرئيسي", departAt = soon, seatsTotal = 4, seatsFree = 3,
        price = 1.0, acceptsParcels = true
    )
    private val t2 = t1.copy(id = "2", captainName = "خالد", carText = "هيونداي إلنترا رمادية", seatsFree = 1, price = null, road = "")

    private fun shot(name: String, content: @Composable () -> Unit) {
        rule.setContent {
            ShajaraTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { content() }
            }
        }
        val bmp = rule.onRoot().captureToImage().asAndroidBitmap()
        val dir = File("build/design-shots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun riderHome() = shot("1-rider-home") {
        Column(Modifier.fillMaxSize()) {
            HeroHeader("أهلاً محمد", "شجرة 1 • وين رايح اليوم؟")
            Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                AppCard {
                    Text("وين رايح؟", style = MaterialTheme.typography.titleLarge)
                    SectionLabel("من")
                    ScrollChips(listOf("all" to "الكل", "a" to "الشجرة", "b" to "الذنيبة"), "a") {}
                    SectionLabel("إلى")
                    ScrollChips(listOf("all" to "الكل", "a" to "إربد", "b" to "عمّان"), "a") {}
                    SectionLabel("متى؟")
                    ScrollChips(listOf("all" to "كل الأيام", "0" to "اليوم", "1" to "غداً"), "0") {}
                }
                AppCard {
                    TripInfo(t1)
                    HorizontalDivider()
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(onClick = {}, modifier = Modifier.weight(1f).height(52.dp)) { Text("احجز مقعد") }
                        CallButton("0790000000")
                        WhatsAppButton("0790000000", "")
                    }
                }
            }
        }
    }

    @Test fun riderTrips() = shot("2-rider-trips") {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            AppCard { TripInfo(t1) }
            AppCard { TripInfo(t2) }
        }
    }

    @Test fun bookingSheet() = shot("3-booking-seat-map") {
        Column(Modifier.padding(20.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("الشجرة  ←  إربد", style = MaterialTheme.typography.titleLarge)
            Text("اليوم 7:30 ص  •  1 دينار", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("اضغط على المقاعد اللي بدك ياها", style = MaterialTheme.typography.titleSmall)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                CarSeatMap(total = 4, free = 3, picked = setOf(1, 2), onToggle = {})
            }
            Text("اخترت مقعدين", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            SectionLabel("وين بدك تنزل؟")
            ChoiceChips(listOf("الدوار الرابع", "وسط البلد", "حسب اتفاق الكابتن"), "وسط البلد") {}
            BigButton("أرسل الطلب") {}
        }
    }

    @Test fun captainHome() = shot("4-captain-home") {
        Column(Modifier.fillMaxSize()) {
            HeroHeader("أهلاً أحمد", "شجرة 1 • تطبيق الكابتن")
            Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatTile("رحلاتك", "2", Modifier.weight(1f))
                    StatTile("طلبات جديدة", "2", Modifier.weight(1f), highlight = true)
                }
                BigButton("انشر رحلة جديدة") {}
                AppCard {
                    TripInfo(t1, showCaptain = false)
                    HorizontalDivider()
                    Text("الحجوزات (2)", style = MaterialTheme.typography.titleSmall)
                    BookingRow(Booking(passengerName = "محمد", seats = 2, dropoff = "الدوار الرابع", status = BK_PENDING, passengerPhone = "0780000000"), {})
                    BookingRow(Booking(passengerName = "سارة", seats = 1, dropoff = "وسط البلد", status = BK_ACCEPTED, passengerPhone = "0770000000"), {})
                }
            }
        }
    }

    @Test fun captainPublish() = shot("5-captain-publish") {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("نشر رحلة جديدة", style = MaterialTheme.typography.headlineSmall)
            AppCard {
                Text("١. الخط", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                SectionLabel("من وين؟")
                ChoiceChips(listOf("الشجرة", "الذنيبة", "عمراوة"), "الشجرة") {}
                SectionLabel("لوين؟")
                ChoiceChips(listOf("إربد", "عمّان", "الزرقاء"), "إربد") {}
            }
            AppCard {
                Text("٣. المقاعد والسعر", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    for (i in 1..4) SeatShape(if (i <= 3) SeatState.PICKED else SeatState.FREE, 38.dp) {}
                }
                ScrollChips(listOf("1", "1.5", "2", "3").map { it to "$it د.أ" }, "1") {}
            }
        }
    }
}
