package jo.shajaraone.admin

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL

/** Checks the public GitHub releases for a newer build of THIS app; shows a dialog with a download link. Reads the last 30 releases (preview-* ones are skipped by the build- tag filter) so previews cannot push the real build out of the list. */
private const val RELEASES_URL = "https://api.github.com/repos/abnaa-shamal/iCloud-/releases?per_page=30"

private class UpdateInfo(val build: Long, val url: String)

private fun installedBuild(ctx: Context): Long =
    try { ctx.packageManager.getPackageInfo(ctx.packageName, 0).longVersionCode } catch (_: Exception) { Long.MAX_VALUE }

private fun fetchLatest(key: String): UpdateInfo? {
    val c = URL(RELEASES_URL).openConnection() as HttpURLConnection
    c.connectTimeout = 8000; c.readTimeout = 8000
    c.setRequestProperty("Accept", "application/vnd.github+json")
    if (c.responseCode != 200) return null
    val arr = JSONArray(c.inputStream.bufferedReader().readText())
    var best: UpdateInfo? = null
    for (i in 0 until arr.length()) {
        val r = arr.getJSONObject(i)
        val n = r.optString("tag_name").removePrefix("build-").toLongOrNull() ?: continue
        val assets = r.optJSONArray("assets") ?: continue
        for (j in 0 until assets.length()) {
            val a = assets.getJSONObject(j)
            if (a.optString("name").contains("-$key-")) {
                if (best == null || n > best.build) best = UpdateInfo(n, a.optString("browser_download_url"))
            }
        }
    }
    return best
}

@Composable
fun UpdateCheck() {
    val ctx = LocalContext.current
    var info by remember { mutableStateOf<UpdateInfo?>(null) }
    var dismissed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        try {
            val key = "admin"
            val latest = withContext(Dispatchers.IO) { fetchLatest(key) }
            if (latest != null && latest.build > installedBuild(ctx)) info = latest
        } catch (_: Exception) { }
    }
    val u = info
    if (u != null && !dismissed) {
        AlertDialog(
            onDismissRequest = { dismissed = true },
            title = { Text("في تحديث جديد") },
            text = { Text("نزل تحديث جديد لتطبيق أبناء الشمال (المشرف). اضغط «حدّث»، نزّل الملف وثبّته فوق القديم.") },
            confirmButton = {
                TextButton(onClick = {
                    dismissed = true
                    try { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(u.url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (_: Exception) { }
                }) { Text("حدّث") }
            },
            dismissButton = { TextButton(onClick = { dismissed = true }) { Text("لاحقاً") } }
        )
    }
}
