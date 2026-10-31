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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import org.junit.Before
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

    /** The captain screens read Firebase Auth; a placeholder app lets them render in tests (nothing is ever sent). */
    @Before fun fakeFirebase() {
        val ctx = org.robolectric.RuntimeEnvironment.getApplication()
        if (com.google.firebase.FirebaseApp.getApps(ctx).isEmpty()) {
            com.google.firebase.FirebaseApp.initializeApp(
                ctx,
                com.google.firebase.FirebaseOptions.Builder().setApplicationId("1:1:android:1").setApiKey("x").setProjectId("demo").build()
            )
        }
    }

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

    @Test fun captainSplash1() = shot("C8-captain-splash-1", 900) { CapPhotoSplash(androidx.compose.ui.graphics.painter.BitmapPainter(android.graphics.BitmapFactory.decodeStream(javaClass.getResourceAsStream("/splash_1.jpg")).asImageBitmap())) }
    @Test fun captainSplash2() = shot("C8-captain-splash-2", 900) { CapPhotoSplash(androidx.compose.ui.graphics.painter.BitmapPainter(android.graphics.BitmapFactory.decodeStream(javaClass.getResourceAsStream("/splash_2.jpg")).asImageBitmap())) }
    @Test fun captainSplash3() = shot("C8-captain-splash-3", 900) { CapPhotoSplash(androidx.compose.ui.graphics.painter.BitmapPainter(android.graphics.BitmapFactory.decodeStream(javaClass.getResourceAsStream("/splash_3.jpg")).asImageBitmap())) }
    @Test fun splash() = shot("0-splash", 1300) { SplashScreen(onDone = {}) }
    @Test fun login() = shot("6-login", 900) { AuthScreen() }

    private fun rider(name: String, fs: RiderFlowState, list: List<Trip>) = shot(name) {
        Box(Modifier.fillMaxSize()) {
            RiderFlow("محمد", fs, list, false) { AmberBtn("احجز") {} }
            Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth(), contentAlignment = Alignment.Center) {
                VantaNav(listOf(1 to "الرحلات", 2 to "حجوزاتي", 7 to "اشتراكات"), 0)
            }
        }
    }

    @Test fun riderStart() = rider("R1-where-to", RiderFlowState(), emptyList())
    @Test fun riderWhere() = rider("R2-where-are-you", RiderFlowState(who = 1), emptyList())
    @Test fun riderDest() = rider("R2b-where-to", RiderFlowState(step = 1, cur = "إربد", who = 1), emptyList())
    @Test fun riderSoon() = shot("R6-soon") {
        Box(Modifier.fillMaxSize()) {
            SoonScreen()
            Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth(), contentAlignment = Alignment.Center) {
                VantaNav(listOf(1 to "الرحلات", 2 to "حجوزاتي", 7 to "اشتراكات"), 2)
            }
        }
    }

    private val b1 = Booking(id = "b1", tripId = "1", passengerName = "محمد العلي", seats = 2, tripSummary = "عمراوة ← عمّان", departAt = soon)
    private val b2 = b1.copy(id = "b2", passengerName = "ليلى حسن", seats = 1, departAt = soon + 5 * 3_600_000L, tripSummary = "إربد ← الزرقاء")

    @Test fun captainDashboard() = capShot("C1-captain-home") {
        CapDashboard("أحمد", listOf(t1, t3.copy(captainUid = "c")), listOf(b1, b2))
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) { CapNav(listOf(Triple(0, "الرئيسية", 2), Triple(6, "رحلة جديدة", 0)), 0) {} }
    }

    @Test fun captainEmpty() = capShot("C2-captain-empty") {
        CapDashboard("أحمد", emptyList(), emptyList())
    }

    @Test fun captainPublish() = capShot("C3-captain-publish") {
        CapPublishPreview()
    }

    @Test fun bookingSheet() = shot("R3-booking-seat-map") {
        BookingBody(t1, 4, 3, setOf(1, 2), {}, listOf("الدوار الرابع", "وسط البلد"), "وسط البلد", {}, "", {}, false) {}
    }

    private val d1 = DeliveryOrder("1", "مطعم الشام", "شارع المدينة، قرب الدوار", "الشجرة، حي الجامعة", "٠٫٨ كم", "٢٫٠ د.أ", "قبل ٣ دقائق", "بدون بصل")
    private val d2 = DeliveryOrder("2", "شاورما الأمير", "وسط البلد، مقابل البنك", "الشجرة، الحي الشمالي", "١٫٤ كم", "١٫٥ د.أ", "قبل ٧ دقائق")
    private val d3 = DeliveryOrder("3", "بيتزا النخيل", "الشارع العام", "الشجرة، قرب المسجد", "٢٫١ كم", "٢٫٥ د.أ", "قبل ١٢ دقيقة")
    private val d4 = DeliveryOrder("4", "مطعم الشام", "شارع المدينة، قرب الدوار", "الشجرة، حي الجامعة", "٠٫٨ كم", "٢٫٠ د.أ", "قبل ٢ دقيقة", mine = true)

    @Test fun captainServices() = capShot("C4-captain-services") { CapServicesHome("أحمد", 3, 2, d1, {}, {}) }
    @Test fun captainDelivery() = capShot("C5-captain-delivery") { CapDeliveryScreen(listOf(d1, d2, d3, d4)) }
    @Test fun captainDeliveryMine() = capShot("C6-captain-delivery-mine") { CapDeliveryScreen(listOf(d1, d2, d4), tab = 1) }
    @Test fun captainNewDelivery() = capShot("C7-captain-new-delivery") { CapNewDelivery(DeliveryDraft("مطعم الشام", "شارع المدينة، قرب الدوار", "الشجرة، حي الجامعة", "2.0", "بدون بصل")) }
}
