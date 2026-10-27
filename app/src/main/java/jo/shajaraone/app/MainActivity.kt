package jo.shajaraone.app

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        Alerts.createChannels(this)
        setContent {
            ShajaraTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Root()
                }
            }
        }
    }
}

/** Signed out → login. Signed in → profile → onboarding → home. */
@Composable
fun Root() {
    var uid by remember { mutableStateOf(Repo.auth.currentUser?.uid) }
    DisposableEffect(Unit) {
        val l = FirebaseAuth.AuthStateListener { uid = it.currentUser?.uid }
        Repo.auth.addAuthStateListener(l)
        onDispose { Repo.auth.removeAuthStateListener(l) }
    }
    val current = uid
    if (current == null) AuthScreen() else SignedIn(current)
}

@Composable
private fun SignedIn(uid: String) {
    var me by remember(uid) { mutableStateOf<UserProfile?>(null) }
    var loaded by remember(uid) { mutableStateOf(false) }
    DisposableEffect(uid) {
        val r = Repo.listenUser(uid, { me = it; loaded = true }, { loaded = true })
        onDispose { r.remove() }
    }
    val profile = me
    when {
        !loaded -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        profile == null -> ProfileSetupScreen()
        else -> Ready(uid, profile)
    }
}

@Composable
private fun Ready(uid: String, me: UserProfile) {
    val ctx = LocalContext.current
    val prefs = remember { Prefs(ctx) }
    var onboarded by remember(APP_ROLE) { mutableStateOf(prefs.onboarded(APP_ROLE)) }
    // Captain alerts exist only in the captain app.
    val approvedCaptain = IS_CAPTAIN_APP && me.isCaptain && me.status == USER_APPROVED

    // Place lists edited by the admin (falls back to the built-in lists until/unless they exist).
    DisposableEffect(Unit) {
        val reg = Repo.listenPlaces { PlacesConfig.remote = it }
        onDispose { reg.remove() }
    }

    // Ask once for notification permission (Android 13+), needed for the booking chime.
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33) permission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    // Captain alerts: live while the app runs, plus a background check every 15 minutes.
    DisposableEffect(uid, approvedCaptain) {
        if (approvedCaptain) {
            prefs.watchCaptainUid = uid
            Alerts.scheduleBackgroundCheck(ctx)
        } else {
            prefs.watchCaptainUid = ""
            Alerts.cancelBackgroundCheck(ctx)
        }
        val r = if (approvedCaptain) {
            Repo.listenBookings("captainUid", uid) { Alerts.processCaptainBookings(ctx, it) }
        } else null
        onDispose { r?.remove() }
    }

    when {
        IS_CAPTAIN_APP && !me.isCaptain -> WrongAppScreen()
        // Rider app: a captain (any captain status) enters like a passenger unless suspended as a rider.
        !IS_CAPTAIN_APP && me.riderBlocked -> SuspendedScreen(me)
        !onboarded -> OnboardingScreen(APP_ROLE) {
            prefs.setOnboarded(APP_ROLE, true)
            onboarded = true
        }
        IS_CAPTAIN_APP && me.isCaptain && me.status != USER_APPROVED -> CaptainStatusScreen(me)
        else -> HomeScaffold(me, onShowGuide = { onboarded = false })
    }
}

@Composable
private fun HomeScaffold(me: UserProfile, onShowGuide: () -> Unit) {
    val ctx = LocalContext.current
    var menu by remember { mutableStateOf(false) }
    var confirmLogout by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        HeroHeader(
            title = "أهلاً ${me.name}",
            subtitle = if (IS_CAPTAIN_APP) "شجرة 1 • تطبيق الكابتن" else "شجرة 1 • وين رايح اليوم؟",
            trailing = {
                Box {
                    IconButton(onClick = { menu = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "القائمة", tint = Color.White)
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("شرح التطبيق") }, onClick = { menu = false; onShowGuide() })
                        DropdownMenuItem(
                            text = { Text("تواصل مع المشرف") },
                            onClick = { menu = false; openWhatsApp(ctx, SUPPORT_PHONE, "مرحبا، بخصوص تطبيق شجرة 1") }
                        )
                        DropdownMenuItem(text = { Text("تسجيل الخروج") }, onClick = { menu = false; confirmLogout = true })
                    }
                }
            }
        )
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                IS_CAPTAIN_APP && me.isCaptain && me.status == USER_APPROVED -> CaptainHome(me)
                IS_CAPTAIN_APP && me.isCaptain -> CaptainStatusScreen(me) // admin whose own captain account is not approved yet
                else -> PassengerHome(me) // rider app: approved captains also see the passenger screens
            }
        }
        if (me.needsVerification) VerifyBar(me)
    }

    if (confirmLogout) {
        AlertDialog(
            onDismissRequest = { confirmLogout = false },
            title = { Text("تسجيل الخروج؟") },
            confirmButton = {
                TextButton(onClick = {
                    confirmLogout = false
                    Alerts.cancelBackgroundCheck(ctx)
                    Prefs(ctx).watchCaptainUid = ""
                    Repo.signOut()
                }) { Text("خروج") }
            },
            dismissButton = { TextButton(onClick = { confirmLogout = false }) { Text("رجوع") } }
        )
    }
}

/** A passenger account signed in to the captain app. */
@Composable
private fun WrongAppScreen() {
    Column(
        Modifier.fillMaxSize().systemBarsPadding().padding(28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("🧍", fontSize = 64.sp)
        Spacer(Modifier.height(12.dp))
        Text(
            "هذا حساب راكب، استعمل تطبيق الراكب",
            style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = { Repo.signOut() }) { Text("تسجيل الخروج") }
    }
}

/** Rider app: passenger suspended, or any account suspended as a rider, by the admin. */
@Composable
private fun SuspendedScreen(me: UserProfile) {
    val ctx = LocalContext.current
    Column(
        Modifier.fillMaxSize().systemBarsPadding().padding(28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("🚫", fontSize = 64.sp)
        Spacer(Modifier.height(12.dp))
        Text(
            "تم إيقاف حسابك",
            style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "للتواصل مع المشرف: $SUPPORT_PHONE",
            color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(24.dp))
        OutlinedButton(onClick = {
            openWhatsApp(ctx, SUPPORT_PHONE, "مرحبا، حسابي (${me.phone}) موقوف بتطبيق شجرة 1.")
        }) { Text("💬 تواصل مع المشرف") }
        TextButton(onClick = { Repo.signOut() }) { Text("تسجيل الخروج") }
    }
}
