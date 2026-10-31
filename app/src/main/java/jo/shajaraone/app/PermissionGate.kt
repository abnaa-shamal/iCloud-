package jo.shajaraone.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat

private fun granted(ctx: Context, p: String) = ContextCompat.checkSelfPermission(ctx, p) == PackageManager.PERMISSION_GRANTED
private fun gpsOn(ctx: Context) = runCatching {
    val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    lm.isProviderEnabled(LocationManager.GPS_PROVIDER) || lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
}.getOrDefault(false)

private fun openAppSettings(ctx: Context) {
    ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + ctx.packageName)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

/** The app does not work without location, notifications and background access: nothing opens before all of them are on. */
@Composable
fun PermissionGate(content: @Composable () -> Unit) {
    val ctx = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { while (true) { kotlinx.coroutines.delay(1000); tick++ } }
    val loc = tick >= 0 && (granted(ctx, Manifest.permission.ACCESS_FINE_LOCATION) || granted(ctx, Manifest.permission.ACCESS_COARSE_LOCATION))
    val gps = tick >= 0 && gpsOn(ctx)
    val notif = tick >= 0 && (Build.VERSION.SDK_INT < 33 || granted(ctx, Manifest.permission.POST_NOTIFICATIONS))
    val bg = tick >= 0 && (Build.VERSION.SDK_INT < 29 || granted(ctx, Manifest.permission.ACCESS_BACKGROUND_LOCATION))
    // Captain only: "display over other apps", so a new booking request pops up on top of whatever app is open.
    val ovl = tick >= 0 && (!BuildConfig.CAPTAIN_APP || Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(ctx))
    val askBg = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { tick++ }
    fun askBg0() = askBg.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
    val gp = remember { ctx.getSharedPreferences("perm_gate", Context.MODE_PRIVATE) }
    var skipped by remember { mutableStateOf(gp.getBoolean("bg_skipped", false)) }
    if (loc && gps && notif && ovl && (bg || skipped)) { content(); return }
    if (loc && gps && notif && ovl) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(top = 80.dp, bottom = 40.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("خطوة أخيرة (يُفضّل)", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = CapInk)
            Text("عشان الموقع يضل شغال حتى لو طلعت من التطبيق، فعّل «السماح طول الوقت»:", fontSize = 15.sp, color = CapMute, lineHeight = 24.sp)
            Column(Modifier.fillMaxWidth().capCard(20.dp).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("1. اضغط «فتح الإعدادات» تحت", "2. اختر «الأذونات» ثم «الموقع»", "3. اختر «السماح طول الوقت»", "4. ارجع للتطبيق").forEach { Text(it, fontSize = 15.sp, color = CapInk) }
            }
            CapBtn("فتح الإعدادات", Modifier.fillMaxWidth(), height = 56.dp) { if (Build.VERSION.SDK_INT == 29) askBg0() else openAppSettings(ctx) }
            CapBtn("تخطي", Modifier.fillMaxWidth(), filled = false, height = 50.dp) { gp.edit().putBoolean("bg_skipped", true).apply(); skipped = true }
        }
        return
    }

    val askLoc = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { tick++ }
    val askNotif = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { tick++ }
    var triedLoc by remember { mutableStateOf(false) }
    var triedNotif by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(top = 80.dp, bottom = 40.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("قبل ما نبدأ", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = CapInk)
        Text("التطبيق ما بيشتغل بدون هالأذونات، فعّلهم واحد واحد:", fontSize = 15.sp, color = CapMute, lineHeight = 24.sp)
        listOf(
            Triple("الموقع", "عشان نعرف مكانك ونوصّل الكابتن والعميل لبعض", loc),
            Triple("تشغيل الـGPS", "لازم يكون الموقع شغّال من إعدادات الجوال", gps),
            Triple("الإشعارات", "عشان توصلك الطلبات والردود فوراً", notif)
        ).let { base ->
            if (BuildConfig.CAPTAIN_APP) base + Triple("الظهور فوق التطبيقات", "عشان يطلع لك طلب الحجز فوق أي تطبيق مفتوح وتقبله بلمسة، بدون ما تفتح التطبيق", ovl) else base
        }.forEach { (t, d, ok) ->
            Row(Modifier.fillMaxWidth().capCard(20.dp).padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(if (ok) "✅" else "⭕", fontSize = 22.sp)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(t, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = CapInk)
                    Text(d, fontSize = 12.sp, color = CapMute, lineHeight = 18.sp)
                }
            }
        }
        val label = when {
            !loc -> "السماح بالموقع"
            !gps -> "فتح إعدادات الموقع"
            !notif -> "السماح بالإشعارات"
            !ovl -> "السماح بالظهور فوق التطبيقات"
            else -> "تابع"
        }
        CapBtn(label, Modifier.fillMaxWidth(), height = 56.dp) {
            when {
                !loc -> if (!triedLoc) { triedLoc = true; askLoc.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)) } else openAppSettings(ctx)
                !gps -> ctx.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                !notif -> if (!triedNotif && Build.VERSION.SDK_INT >= 33) { triedNotif = true; askNotif.launch(Manifest.permission.POST_NOTIFICATIONS) } else openAppSettings(ctx)
                !ovl -> ctx.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + ctx.packageName)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                else -> {}
            }
        }
        Text("لو الزر ما ظهر له نافذة، بيفتحلك إعدادات التطبيق عشان تفعّلها من هناك.", fontSize = 12.sp, color = CapMute, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    }
}
