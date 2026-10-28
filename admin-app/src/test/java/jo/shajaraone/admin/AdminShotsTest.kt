package jo.shajaraone.admin

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Renders the REAL admin screens with sample data to PNG files (uploaded by CI). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w390dp-h844dp-xxhdpi")
class AdminShotsTest {
    @get:Rule val rule = createComposeRule()

    private val now = System.currentTimeMillis()
    private val users = listOf(
        AUser("1", "captain", "pending", "سامر العلي", "0791112233", "كيا", "أبيض", "12-34567", now),
        AUser("2", "captain", "approved", "خالد المومني", "0782223344", "هيونداي", "رمادي", "22-98765", now - 1000, maxSeats = 5, verified = true),
        AUser("3", "captain", "suspended", "ياسر الحوراني", "0773334455", "تويوتا", "فضي", "33-11111", now - 2000),
        AUser("4", "passenger", "active", "ليث أحمد", "0794445566", createdAt = now, verifyCode = "482913"),
        AUser("5", "passenger", "active", "نور الخطيب", "0795556677", createdAt = now - 5, verified = true),
        AUser("6", "passenger", "suspended", "رامي سعد", "0796667788", createdAt = now - 9, riderSuspended = true)
    )
    private val trips = listOf(
        ATrip("t1", "خالد المومني", "0782223344", "هيونداي رمادي", "الشجرة", "إربد", now + 3 * 3_600_000, 4, 2, 1.0, "open"),
        ATrip("t2", "سامر العلي", "0791112233", "كيا أبيض", "عمّان", "الرمثا", now + 9 * 3_600_000, 4, 0, null, "full"),
        ATrip("t3", "ياسر الحوراني", "0773334455", "تويوتا فضي", "إربد", "الشجرة", now - 3_600_000, 4, 4, 1.5, "cancelled")
    )
    private val bookings = listOf(
        ABooking("b1", "خالد المومني", "ليث أحمد", "0794445566", 2, "دوار الشجرة", "الشجرة ← إربد • 8/11 18:30", "pending", now),
        ABooking("b2", "خالد المومني", "نور الخطيب", "0795556677", 1, "", "الشجرة ← إربد • 8/11 18:30", "accepted", now - 10)
    )

    private fun shot(name: String, content: @Composable () -> Unit) {
        rule.mainClock.autoAdvance = false
        rule.setContent { RoyalTheme { content() } }
        rule.mainClock.advanceTimeBy(500)
        val bmp = rule.onRoot().captureToImage().asAndroidBitmap()
        val dir = File("build/design-shots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private val noSet: (AUser, Map<String, Any>, String) -> Unit = { _, _, _ -> }
    private val noAsk: (Ask) -> Unit = {}

    @Test fun login() = shot("1-login") { LoginScreen(false, null) { _, _ -> } }

    @Test fun overview() = shot("2-overview") {
        Box { OverviewScreen(users, trips, bookings, true, null, {}, {}, {}, {}, {}) }
    }

    @Test fun captains() = shot("3-captains") { CaptainsScreen(users, noSet, noAsk) }

    @Test fun riders() = shot("4-riders") { RidersScreen(users, noSet, noAsk) }

    @Test fun trips() = shot("5-trips") { TripsScreen(trips, {}, noAsk) }

    @Test fun bookings() = shot("6-bookings") { BookingsScreen(bookings, {}, noAsk) }

    @Test fun places() = shot("7-places") {
        PlacesScreen(mapOf("الشجرة" to listOf("دوار الشجرة", "المدينة الرياضية"), "إربد|الطريق الرئيسي" to listOf("الحصن", "كفرسوم")), {}, {})
    }

    @Test fun footer() = shot("8-footer") { Box(Modifier.padding(top = 300.dp)) { RoyalFooter() } }
}
