package jo.shajaraone.app

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
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

/** Renders the REAL screens with sample data to PNG files (uploaded by CI so the design can be seen before installing). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w390dp-h844dp-xxhdpi")
class DesignShotsTest {
    @get:Rule val rule = createComposeRule()

    private val soon = System.currentTimeMillis() + 3 * 3_600_000L
    private val t1 = Trip(
        id = "1", captainUid = "c", captainName = "أحمد", captainPhone = "0790000000", carText = "كيا سيراتو بيضاء",
        from = "عمراوة", to = "عمّان", road = "شارع الأردن", departAt = soon, seatsTotal = 4, seatsFree = 3, price = 4.0
    )
    private val t2 = t1.copy(id = "2", captainName = "خالد", carText = "هيونداي إلنترا رمادية", from = "الرمثا", to = "إربد", road = NORTH_INNER, seatsFree = 1, price = 1.0, departAt = soon + 86_400_000L)
    private val t3 = t1.copy(id = "3", captainName = "سامر", carText = "تويوتا كورولا", from = "إربد", to = "الزرقاء", road = "طريق الزرقاء", price = null, departAt = soon + 5 * 3_600_000L)

    private fun save(name: String) {
        val bmp = rule.onRoot().captureToImage().asAndroidBitmap()
        val dir = File("build/design-shots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun shot(name: String, advanceMs: Long = 600, content: @Composable () -> Unit) {
        rule.mainClock.autoAdvance = false
        rule.setContent { ShajaraTheme { AppBackground { content() } } }
        rule.mainClock.advanceTimeBy(advanceMs)
        save(name)
    }

    private fun capShot(name: String, content: @Composable () -> Unit) {
        rule.mainClock.autoAdvance = false
        rule.setContent {
            ShajaraTheme {
                CompositionLocalProvider(LocalContentColor provides CapInk) { Box(Modifier.fillMaxSize().background(CapBg)) { content() } }
            }
        }
        rule.mainClock.advanceTimeBy(600)
        save(name)
    }

    @Test fun splash() = shot("0-splash", 1300) { SplashScreen(onDone = {}) }
    @Test fun login() = shot("6-login", 900) { AuthScreen() }

    @Test fun riderHome() = shot("R1-rider-home") {
        Box(Modifier.fillMaxSize()) {
            RiderTripsView("محمد", listOf(t1, t2, t3), false, 0, {}, -1, {}) { t ->
                AmberBtn("احجز") {}
            }
            Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth(), contentAlignment = Alignment.Center) {
                VantaNav(listOf(1 to "الرحلات", 2 to "حجوزاتي"), 0)
            }
        }
    }

    @Test fun riderFiltered() = shot("R2-rider-filtered") {
        Box(Modifier.fillMaxSize()) {
            RiderTripsView("محمد", listOf(t1), false, 1, {}, 0, {}) { AmberBtn("احجز") {} }
            Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth(), contentAlignment = Alignment.Center) {
                VantaNav(listOf(1 to "الرحلات", 2 to "حجوزاتي"), 0)
            }
        }
    }

    private val b1 = Booking(id = "b1", tripId = "1", passengerName = "محمد العلي", seats = 2, tripSummary = "عمراوة ← عمّان", departAt = soon)
    private val b2 = b1.copy(id = "b2", passengerName = "ليلى حسن", seats = 1, departAt = soon + 5 * 3_600_000L, tripSummary = "إربد ← الزرقاء")

    @Test fun captainDashboard() = capShot("C1-captain-home") {
        CapDashboard("أحمد", listOf(t1, t3.copy(captainUid = "c")), listOf(b1, b2), onPublish = {})
    }

    @Test fun captainEmpty() = capShot("C2-captain-empty") {
        CapDashboard("أحمد", emptyList(), emptyList(), onPublish = {})
    }

    @Test fun captainPublish() = capShot("C3-captain-publish") {
        CapPublishPreview()
    }

    @Test fun bookingSheet() = shot("R3-booking-seat-map") {
        Column(Modifier.padding(20.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            CarSeatMap(total = 4, free = 3, picked = setOf(1, 2), onToggle = {})
            SeatLegend()
        }
    }
}
