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
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

    private fun shot(name: String, advanceMs: Long = 0, content: @Composable () -> Unit) {
        rule.mainClock.autoAdvance = false
        rule.setContent {
            ShajaraTheme {
                AppBackground { Surface(Modifier.fillMaxSize(), color = androidx.compose.ui.graphics.Color.Transparent) { content() } }
            }
        }
        rule.mainClock.advanceTimeBy(if (advanceMs > 0) advanceMs else 400)
        val bmp = rule.onRoot().captureToImage().asAndroidBitmap()
        val dir = File("build/design-shots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun splash() = shot("0-splash", advanceMs = 1300) { SplashScreen(onDone = {}) }

    @Test fun login() = shot("6-login", advanceMs = 900) { AuthScreen() }

    @Test fun captainWaiting() = shot("7-captain-waiting", advanceMs = 600) {
        CaptainStatusScreen(UserProfile(role = ROLE_CAPTAIN, name = "أحمد", phone = "0790000000", status = USER_PENDING))
    }

    @Test fun riderHome() = shot("1-rider-home") {
        Column(Modifier.fillMaxSize()) {
            HeroHeader("أهلاً محمد", "أبناء الشمال • وين رايح اليوم؟")
            Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                FilterSummaryBar("الشجرة  ←  إربد  •  اليوم", false) {}
                Text("2 رحلة متاحة", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 4.dp))
                listOf(t1, t2).forEach { t ->
                    TicketCard(t) {
                        Button(onClick = {}, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("احجز مقعد", style = MaterialTheme.typography.titleMedium) }
                        ContactRow(t.to, "0790000000", "")
                    }
                }
            }
        }
    }

    @Test fun riderHomeFilterOpen() = shot("1b-rider-home-filter-open") {
        Column(Modifier.fillMaxSize()) {
            HeroHeader("أهلاً محمد", "أبناء الشمال • وين رايح اليوم؟")
            Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                FilterSummaryBar("الشجرة  ←  إربد  •  اليوم", true) {}
                AppCard {
                    SectionLabel("من")
                    ScrollChips(listOf("all" to "الكل", "a" to "الشجرة", "b" to "الذنيبة"), "a") {}
                    SectionLabel("إلى")
                    ScrollChips(listOf("all" to "الكل", "a" to "إربد", "b" to "عمّان"), "a") {}
                    SectionLabel("متى؟")
                    ScrollChips(listOf("all" to "كل الأيام", "0" to "اليوم", "1" to "غداً"), "0") {}
                }
            }
        }
    }

    private fun ledgerShot(name: String, content: @Composable () -> Unit) {
        rule.mainClock.autoAdvance = false
        rule.setContent { LedgerTheme { content() } }
        rule.mainClock.advanceTimeBy(400)
        val bmp = rule.onRoot().captureToImage().asAndroidBitmap()
        val dir = File("build/design-shots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun ledgerHome() = ledgerShot("L1-ledger-home") {
        Column(Modifier.fillMaxSize()) {
            LedgerTopBar("أهلاً محمد", "أبناء الشمال")
            Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                LedgerSearchBar("الشجرة  ←  إربد  •  اليوم") {}
                Text("2 رحلة متاحة", style = MaterialTheme.typography.titleMedium)
                listOf(t1, t2).forEach { t ->
                    TicketCard(t) {
                        LedgerPrimaryButton("احجز مقعد", {})
                        LedgerSecondaryRow(listOf("الموقع", "اتصال", "واتساب"))
                    }
                }
            }
        }
    }

    @Test fun ledgerSeats() = ledgerShot("L2-ledger-seats") {
        Column(Modifier.fillMaxSize()) {
            LedgerTopBar("اختر مقعدك", "الشجرة ← إربد")
            Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                LedgerSeatChart(total = 7, taken = setOf(1, 4), picked = setOf(2, 3))
                LedgerLegend()
                Text("اخترت مقعدين: 02 ، 03", style = MaterialTheme.typography.titleMedium)
                LedgerPrimaryButton("أرسل الطلب", {})
            }
        }
    }

    @Test fun ledgerWelcome() = ledgerShot("L3-welcome") {
        Column(Modifier.fillMaxSize()) {
            LedgerWelcomeHeader("أهلاً محمد", "وين رايح اليوم؟")
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                LedgerStepCard("1", "أنت وين هسه؟", null, "اضغط واختار مكانك")
                LedgerStepCard("2", "لوين رايح؟", null, "اختار مكانك أول", enabled = false)
            }
        }
    }

    @Test fun ledgerWhereOpen() = ledgerShot("L4-where-open") {
        Column(Modifier.fillMaxSize()) {
            LedgerWelcomeHeader("أهلاً محمد", "وين رايح اليوم؟")
            Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                LedgerStepCard("1", "أنت وين هسه؟", null, "") {
                    LedgerChoice("أنا بالشمال", "إربد، الرمثا، المفرق، الشجرة، الطرة، عمراوة، الذنيبة، المغيّر...")
                    LedgerChoice("أنا بعمّان أو الزرقاء", "صويلح، خلدا، الجامعة الأردنية، الشميساني، رصيفة، سحاب...")
                }
                LedgerStepCard("2", "لوين رايح؟", null, "اختار مكانك أول", enabled = false)
            }
        }
    }

    @Test fun ledgerRoads() = ledgerShot("L5-roads") {
        Column(Modifier.fillMaxSize()) {
            LedgerWelcomeHeader("أهلاً محمد", "وين رايح اليوم؟")
            Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                LedgerStepCard("1", "أنت وين هسه؟", "عمّان أو الزرقاء", "") {
                    LedgerFindBox("اكتب اسم منطقتك (مثلاً: خلدا)")
                    Text("أو اختار الطريق اللي بتركب منه:", fontSize = 13.sp, color = Ledger.Muted)
                    LedgerChoice("طريق صويلح", "صويلح، دابوق، خلدا، المدينة الطبية، جسر ناعور، دوار السابع...")
                    LedgerChoice("شارع الأردن", "الجامعة الأردنية، الشميساني، الجاردنز، الدوار الأول، مطار الملكة علياء...")
                    LedgerChoice("طريق الزرقاء", "رصيفة، ماركا، الموقر، سحاب، طبربور...")
                }
            }
        }
    }

    /** The REAL guided picker, driven by clicks like a user would. */
    private fun guidedShot(name: String) {
        rule.waitForIdle()
        val bmp = rule.onRoot().captureToImage().asAndroidBitmap()
        val dir = File("build/design-shots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun guidedFlow() {
        rule.setContent {
            ShajaraTheme {
                AppBackground {
                    Column(Modifier.fillMaxSize()) {
                        HeroHeader("أهلاً محمد", "أبناء الشمال • وين رايح اليوم؟")
                        Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) { GuidedRoutePicker(rememberRouteState()) }
                    }
                }
            }
        }
        guidedShot("G1-start")
        rule.onNodeWithText("أنا بالشمال").performClick(); guidedShot("G2-north")
        rule.onNodeWithText("الشجرة").performClick(); guidedShot("G3-card2")
        rule.onNodeWithText("عمّان أو الزرقاء").performClick(); guidedShot("G4-south-roads")
        rule.onNodeWithText("طريق صويلح").performClick(); guidedShot("G5-stops")
        rule.onNodeWithText("خلدا").performClick(); guidedShot("G6-done")
    }

    @Test fun riderUnverified() = shot("9-rider-unverified") {
        val places = listOf("all" to "الكل", "1" to "الشجرة", "2" to "الذنيبة", "3" to "عمراوة", "4" to "الطرة", "5" to "الرمثا", "6" to "إربد", "7" to "عمّان", "8" to "الزرقاء")
        Column(Modifier.fillMaxSize()) {
            HeroHeader("أهلاً محمد", "أبناء الشمال • وين رايح اليوم؟")
            VerifyBar(UserProfile(name = "محمد", phone = "0790000000", verifyCode = "432244"))
            Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                AppCard {
                    Text("وين مشوارك؟", style = MaterialTheme.typography.titleLarge)
                    SectionLabel("من")
                    ScrollChips(places, "1") {}
                    SectionLabel("إلى")
                    ScrollChips(places, "6") {}
                    SectionLabel("متى؟")
                    ScrollChips(listOf("all" to "كل الأيام", "0" to "اليوم", "1" to "غداً", "2" to "بعد غد", "3" to "الجمعة"), "0") {}
                }
                AppCard {
                    TripInfo(t1)
                    HorizontalDivider()
                    Button(onClick = {}, modifier = Modifier.fillMaxWidth().height(48.dp)) { Text("احجز مقعد") }
                }
            }
        }
    }

    @Test fun routeSouthRoad() = shot("10-route-north-to-south") {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            AppCard {
                RoutePicker(RouteState(kind = KIND_NS, north = "الشجرة", road = "طريق صويلح", south = "دابوق"), allowAll = false)
            }
        }
    }

    @Test fun routeInsideNorth() = shot("11-route-inside-north") {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            AppCard {
                RoutePicker(RouteState(kind = KIND_NN, north = "الرمثا", spot = IRBID_OTHER), allowAll = true)
            }
        }
    }

    @OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
    @Test fun calendarAndClock() = shot("12-calendar") {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            AppCard { WhenPicker(dayOffset = 2, minutes = 7 * 60 + 30, onDay = {}, onMinutes = {}, allowClear = false) }
            androidx.compose.material3.DatePicker(state = androidx.compose.material3.rememberDatePickerState(initialSelectedDateMillis = pickerMillisOfOffset(2)))
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
            SeatLegend()
            Text("اخترت مقعدين", style = MaterialTheme.typography.titleMedium, color = androidx.compose.ui.graphics.Color(0xFF2DE2C0))
            SectionLabel("وين بدك تنزل؟")
            ChoiceChips(listOf("الدوار الرابع", "وسط البلد", "حسب اتفاق الكابتن"), "وسط البلد") {}
            BigButton("أرسل الطلب") {}
        }
    }

    @Test fun captainHome() = shot("4-captain-home") {
        Column(Modifier.fillMaxSize()) {
            HeroHeader("أهلاً أحمد", "أبناء الشمال • تطبيق الكابتن")
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

    @Test fun liveTrackPanel() = shot("8-live-track-panel") {
        val me = android.location.Location("x").apply { latitude = 32.5500; longitude = 35.8500 }
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f).fillMaxWidth())
            LiveInfo(
                LiveLoc(32.5560, 35.8540, 12.0, 10.0, System.currentTimeMillis() - 18_000), me,
                System.currentTimeMillis(), "أحمد", "0791234567"
            )
        }
    }
}
