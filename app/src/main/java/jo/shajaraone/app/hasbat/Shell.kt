package jo.shajaraone.app.hasbat

import android.Manifest
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import java.util.Calendar

/** يستدعى من MainActivity مرة واحدة قبل عرض واجهة الكابتن. */
fun initHasbat(activity: ComponentActivity) {
    MeterEngine.init(activity)
    val obs = LifecycleEventObserver { _, e ->
        when (e) {
            Lifecycle.Event.ON_START -> { MeterEngine.onUiStart(); FloatingBubbleService.onAppVisible(activity) }
            Lifecycle.Event.ON_STOP -> {
                MeterEngine.onUiStop()
                if (!activity.isChangingConfigurations) FloatingBubbleService.onAppHidden()
            }
            else -> {}
        }
    }
    activity.lifecycle.addObserver(obs)
    if (activity.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) MeterEngine.onUiStart()
}

/**
 * قشرة تصميم حسبة الكابتن داخل تطبيق الكابتن: الثيمات + الشريط العائم + شاشات الأدوات.
 * التبويبان 0 و1 (نشر الرحلة والطلبات) يرسمهما تطبيق الكابتن نفسه.
 */
@Composable
fun HasbatShell(
    tab: Int,
    onTab: (Int) -> Unit,
    requestsBadge: Int,
    onSeatHistory: () -> Unit,
    captainContent: @Composable (Int) -> Unit
) {
    val context = LocalContext.current
    val view = LocalView.current
    val storage = remember { AppDataManager.get(context) }
    val themes = AppTheme.values()
    val minuteKey = MeterEngine.clockTick / 60_000L
    val isDaytime = remember(minuteKey, storage.autoDayNight, (MeterEngine.latitude * 10).toInt(), (MeterEngine.longitude * 10).toInt()) {
        if (!storage.autoDayNight) false else {
            val m = IslamicCalculationHelper.prayerMinutes(MeterEngine.latitude, MeterEngine.longitude)
            val now = Calendar.getInstance().let { it.get(Calendar.HOUR_OF_DAY) * 60 + it.get(Calendar.MINUTE) }
            now >= m[1] && now < m[4]
        }
    }
    val idx = if (storage.autoDayNight && isDaytime) storage.dayThemeIndex else storage.themeIndex
    val theme = themes[idx.coerceIn(0, themes.size - 1)]
    val isEnglish = storage.isEnglish
    val isTripActive = MeterEngine.isTripActive

    val activity = context as? ComponentActivity
    LaunchedEffect(theme.isDark) {
        val style = if (theme.isDark) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        else SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
        activity?.enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        MeterEngine.refreshPermission()
    }
    val requestPermissions: () -> Unit = {
        val perms = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        if (Build.VERSION.SDK_INT >= 33) perms.add(Manifest.permission.POST_NOTIFICATIONS)
        permissionLauncher.launch(perms.toTypedArray())
    }
    DisposableEffect(isTripActive) {
        view.keepScreenOn = isTripActive
        onDispose { view.keepScreenOn = false }
    }
    BackHandler(enabled = tab != 0) { onTab(0) }

    val colorScheme = if (theme.isDark) {
        darkColorScheme(primary = theme.accentColor, secondary = theme.fareColor, surface = theme.cardBg,
            background = theme.bgGradient.first(), onSurface = Color.White, onBackground = Color.White)
    } else {
        lightColorScheme(primary = theme.accentColor, secondary = theme.fareColor, surface = theme.cardBg,
            background = theme.bgGradient.first(), onSurface = Color(0xFF0F172A), onBackground = Color(0xFF0F172A))
    }

    if (jo.shajaraone.app.CapTheme.t != theme) jo.shajaraone.app.CapTheme.t = theme
    MaterialTheme(colorScheme = colorScheme) {
        CompositionLocalProvider(LocalLayoutDirection provides (if (isEnglish) LayoutDirection.Ltr else LayoutDirection.Rtl)) {
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(theme.bgGradient))) {
                CyberBackdrop(theme)
                if (tab < 2) {
                    captainContent(tab)
                } else {
                    Box(Modifier.fillMaxSize().systemBarsPadding().imePadding()) {
                        Box(Modifier.fillMaxSize().padding(bottom = 92.dp)) {
                            AnimatedContent(
                                targetState = tab,
                                transitionSpec = {
                                    val enter = fadeIn(tween(260, delayMillis = 60)) +
                                        slideInVertically(tween(320, easing = FastOutSlowInEasing)) { it / 24 } +
                                        scaleIn(tween(320, easing = FastOutSlowInEasing), 0.98f)
                                    enter togetherWith fadeOut(tween(120))
                                },
                                modifier = Modifier.fillMaxSize(), label = "tab_transition"
                            ) { t ->
                                when (t) {
                                    2 -> MainDashboardScreen(storage, theme, isEnglish, onRequestPermission = requestPermissions)
                                    3 -> DailyFinanceScreen(storage, theme, isEnglish) { onTab(0) }
                                    4 -> CalculatorAndRatesScreen(storage, theme, isEnglish) { onTab(0) }
                                    else -> CleanSettingsScreen(storage, theme, isEnglish, onSeatHistory = onSeatHistory) { onTab(0) }
                                }
                            }
                        }
                    }
                }
                CaptainDock(tab, theme, isEnglish, isTripActive, requestsBadge, onTab,
                    Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(horizontal = 12.dp, vertical = 10.dp))
                if (tab == 0) {
                    Box(Modifier.fillMaxSize().systemBarsPadding()) {
                        AssistantBubble(storage, theme, isEnglish, visible = !isTripActive)
                    }
                }
            }
        }
    }
}

@Composable
private fun CaptainDock(
    currentTab: Int, theme: AppTheme, isEnglish: Boolean, tripActive: Boolean, badge: Int,
    onTabSelect: (Int) -> Unit, modifier: Modifier = Modifier
) {
    val items = listOf(
        Triple(0, if (isEnglish) "Home" else "الرئيسية", "🏠"),
        Triple(1, if (isEnglish) "Requests" else "طلبات", "🔔"),
        Triple(2, if (isEnglish) "Meter" else "العداد", "🚕"),
        Triple(3, if (isEnglish) "Finance" else "المالية", "💸"),
        Triple(4, if (isEnglish) "Calculator" else "الحاسبة", "🧮"),
        Triple(5, if (isEnglish) "Settings" else "الإعدادات", "⚙️")
    )
    val dockShape = RoundedCornerShape(30.dp)
    Box(
        modifier = modifier.fillMaxWidth().height(70.dp)
            .shadow(18.dp, dockShape, ambientColor = theme.accentColor.copy(alpha = 0.30f), spotColor = theme.accentColor.copy(alpha = 0.45f))
            .clip(dockShape)
            .background(
                if (theme.isDark) Brush.verticalGradient(listOf(Color(0xFF141A2C).copy(alpha = 0.92f), Color(0xFF080B14).copy(alpha = 0.96f)))
                else Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.96f), Color.White.copy(alpha = 0.86f)))
            )
            .border(1.2.dp, Brush.horizontalGradient(listOf(theme.accentColor.copy(alpha = 0.15f), theme.accentColor, theme.needleColor, theme.accentColor.copy(alpha = 0.15f))), dockShape)
    ) {
        Row(Modifier.fillMaxSize().padding(horizontal = 4.dp), horizontalArrangement = Arrangement.SpaceAround, verticalAlignment = Alignment.CenterVertically) {
            items.forEach { (i, title, icon) ->
                DockItem(
                    icon = icon, title = title, isSelected = currentTab == i, theme = theme,
                    showBadge = (i == 1 && badge > 0) || (tripActive && i == 2 && currentTab != 2),
                    modifier = Modifier.weight(1f), onClick = { onTabSelect(i) }
                )
            }
        }
    }
}

@Composable
private fun DockItem(icon: String, title: String, isSelected: Boolean, theme: AppTheme, showBadge: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val itemShape = RoundedCornerShape(22.dp)
    val iconScale by animateFloatAsState(if (isSelected) 1.18f else 1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium), label = "dock_icon_scale")
    val pillAlpha by animateFloatAsState(if (isSelected) 1f else 0f, tween(260), label = "dock_pill")
    val barWidth by animateDpAsState(if (isSelected) 20.dp else 0.dp, spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMedium), label = "dock_bar")
    val labelColor by animateColorAsState(if (isSelected) theme.accentColor else (if (theme.isDark) Color(0xFFA3B1C6) else Color(0xFF52627A)), tween(240), label = "dock_label")
    Box(
        modifier = modifier.fillMaxHeight().padding(vertical = 5.dp, horizontal = 1.dp).bounceClick(onClick = onClick).clip(itemShape)
            .background(Brush.verticalGradient(listOf(theme.accentColor.copy(alpha = 0.26f * pillAlpha), theme.needleColor.copy(alpha = 0.08f * pillAlpha)))),
        contentAlignment = Alignment.Center
    ) {
        Box(Modifier.align(Alignment.TopCenter).width(barWidth).height(3.dp).clip(RoundedCornerShape(bottomStart = 4.dp, bottomEnd = 4.dp))
            .background(Brush.horizontalGradient(listOf(theme.accentColor, theme.needleColor))))
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Box {
                Text(icon, fontSize = 19.sp, modifier = Modifier.graphicsLayer { scaleX = iconScale; scaleY = iconScale })
                if (showBadge) {
                    Box(Modifier.align(Alignment.TopEnd).padding(top = 1.dp).size(8.dp).clip(RoundedCornerShape(4.dp))
                        .background(Color(0xFF22C55E)).border(1.dp, Color.White.copy(alpha = 0.8f), RoundedCornerShape(4.dp)))
                }
            }
            Text(title, fontSize = 10.sp, fontWeight = if (isSelected) FontWeight.Black else FontWeight.SemiBold, color = labelColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
