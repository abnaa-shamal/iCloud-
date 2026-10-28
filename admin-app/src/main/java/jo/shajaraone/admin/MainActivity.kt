package jo.shajaraone.admin

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initFirebase(applicationContext)
        setContent {
            RoyalTheme { AdminApp { Toast.makeText(this, it, Toast.LENGTH_SHORT).show() } }
        }
    }
}

@Composable
fun AdminApp(report: (String) -> Unit) {
    var signedIn by remember { mutableStateOf(false) }
    var checking by remember { mutableStateOf(FirebaseAuth.getInstance().currentUser != null) }
    var busy by remember { mutableStateOf(false) }
    var loginError by remember { mutableStateOf<String?>(null) }
    val data = remember { AdminData() }
    val actions = remember { AdminActions(report) }

    LaunchedEffect(Unit) {
        if (checking) checkAdmin { err -> checking = false; if (err == null) signedIn = true else loginError = err }
    }
    DisposableEffect(signedIn) {
        if (signedIn) data.start()
        onDispose { data.stop() }
    }

    when {
        checking -> Box(Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) { CircularProgressIndicator(color = Royal.Gold) }
        !signedIn -> LoginScreen(busy, loginError) { email, pass ->
            busy = true; loginError = null
            adminSignIn(email, pass) { err -> busy = false; if (err == null) signedIn = true else loginError = err }
        }
        else -> AdminHome(data, actions) { FirebaseAuth.getInstance().signOut(); signedIn = false }
    }
}

@Composable
fun AdminHome(data: AdminData, actions: AdminActions, onSignOut: () -> Unit) {
    var tab by remember { mutableStateOf(0) }
    var places by remember { mutableStateOf(false) }
    var ask by remember { mutableStateOf<Ask?>(null) }
    BackHandler(places) { places = false }
    AskDialog(ask) { ask = null }
    val setUser: (AUser, Map<String, Any>, String) -> Unit = { u, f, m -> actions.setUser(u.uid, f, m) }
    val askFn: (Ask) -> Unit = { ask = it }

    Scaffold(
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        bottomBar = {
            NavBar(tab) { tab = it; places = false }
        }
    ) { inner ->
        Box(Modifier.padding(inner)) {
            if (places) PlacesScreen(data.places, { actions.savePlaces(it) }, { places = false })
            else when (tab) {
                0 -> OverviewScreen(
                    data.users, data.trips, data.bookings, data.loaded, data.error,
                    onApprove = { setUser(it, mapOf("status" to "approved"), "تم قبول ${it.name}") },
                    onVerify = { setUser(it, mapOf("verified" to true), "تم توثيق الرقم") },
                    onOpenCaptains = { tab = 1 }, onOpenPlaces = { places = true }, onSignOut = onSignOut
                )
                1 -> CaptainsScreen(data.users, setUser, askFn)
                2 -> RidersScreen(data.users, setUser, askFn)
                3 -> TripsScreen(data.trips, { actions.cancelTrip(it.id) }, askFn)
                else -> BookingsScreen(data.bookings, { actions.cancelPendingBooking(it.id) }, askFn)
            }
        }
    }
}

@Composable
fun NavBar(selected: Int, onSelect: (Int) -> Unit) {
    val items = listOf("👑" to "الرئيسية", "🚗" to "الكباتن", "👥" to "الركاب", "🛣" to "الرحلات", "🎫" to "الحجوزات")
    NavigationBar(containerColor = Royal.Surface) {
        items.forEachIndexed { i, (icon, label) ->
            NavigationBarItem(
                selected == i, { onSelect(i) },
                icon = { Text(icon, fontSize = 20.sp) },
                label = { Text(label, fontSize = 11.sp) },
                colors = NavigationBarItemDefaults.colors(
                    selectedTextColor = Royal.Gold, unselectedTextColor = Royal.Muted, indicatorColor = Royal.Container
                )
            )
        }
    }
}
