package jo.abnaashamal.bot

import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * READ-ONLY access to GitHub Actions. This file contains no write calls on purpose:
 * the bot can look at builds, but it cannot push, merge, delete or touch any branch (including main).
 */
object GitHub {
    private fun headers(token: String): Map<String, String> {
        val h = mutableMapOf(
            "Accept" to "application/vnd.github+json",
            "X-GitHub-Api-Version" to "2022-11-28",
            "User-Agent" to "abnaa-bot",
        )
        if (token.isNotBlank()) h["Authorization"] = "Bearer $token"
        return h
    }

    private fun get(cfg: Config, path: String): Pair<Int, String> =
        Net.request("https://api.github.com/repos/${cfg.ghRepo}$path", "GET", headers(cfg.ghToken), null)

    private val fmt = DateTimeFormatter.ofPattern("MM-dd HH:mm").withZone(ZoneId.of("Asia/Amman"))

    private fun time(iso: String): String = try {
        fmt.format(Instant.parse(iso))
    } catch (_: Exception) {
        iso
    }

    private fun state(status: String, conclusion: String): String = when {
        conclusion == "success" -> "✅ نجح"
        conclusion == "failure" -> "❌ فشل"
        conclusion == "cancelled" -> "⏹ أُلغي"
        conclusion == "skipped" -> "⏭ تخطّى"
        status == "in_progress" || status == "queued" || status == "waiting" || status == "pending" -> "⏳ شغّال"
        conclusion.isNotBlank() -> conclusion
        else -> status
    }

    /** Returns (report text, true when the newest finished run failed). */
    fun buildReport(cfg: Config): Pair<String, Boolean> {
        if (cfg.ghRepo.isBlank() || !cfg.ghRepo.contains("/")) return "⚠️ اكتب اسم المستودع بالشكل owner/name من الإعدادات." to false
        return try {
            val (code, text) = get(cfg, "/actions/runs?per_page=8")
            if (code == 401) return "❌ مفتاح GitHub غلط أو منتهي." to false
            if (code == 404) return "❌ ما لقيت المستودع (أو المفتاح ما عنده صلاحية قراءة عليه)." to false
            if (code !in 200..299) return "❌ خطأ من GitHub: $code" to false
            val runs: JSONArray = JSONObject(text).optJSONArray("workflow_runs") ?: JSONArray()
            if (runs.length() == 0) return "ما في أي تشغيل بناء لهالمستودع لسا." to false

            val sb = StringBuilder()
            sb.append("| الرقم | الفرع | الحالة | الوقت |\n|---|---|---|---|\n")
            var failedId = 0L
            var failedNo = 0
            for (i in 0 until runs.length()) {
                val r = runs.getJSONObject(i)
                val concl = r.optString("conclusion")
                if (failedId == 0L && concl == "failure") {
                    failedId = r.optLong("id")
                    failedNo = r.optInt("run_number")
                }
                sb.append("| #${r.optInt("run_number")} | ${r.optString("head_branch")} | ")
                    .append(state(r.optString("status"), concl)).append(" | ")
                    .append(time(r.optString("created_at"))).append(" |\n")
            }
            // Only explain a failure if it is the newest finished run.
            var newestFinished = ""
            for (i in 0 until runs.length()) {
                val c = runs.getJSONObject(i).optString("conclusion")
                if (c.isNotBlank()) { newestFinished = c; break }
            }
            val bad = newestFinished == "failure" && failedId != 0L
            if (bad) sb.append("\n").append(failureDetails(cfg, failedId, failedNo))
            sb.toString().trimEnd() to bad
        } catch (e: Exception) {
            "❌ ما اتصلت بـ GitHub: ${e.javaClass.simpleName}" to false
        }
    }

    private fun failureDetails(cfg: Config, runId: Long, runNo: Int): String {
        val sb = StringBuilder("تفاصيل آخر فشل (#$runNo):\n| المهمة | الحالة |\n|---|---|\n")
        try {
            val (code, text) = get(cfg, "/actions/runs/$runId/jobs")
            if (code !in 200..299) return "تفاصيل آخر فشل (#$runNo): ما قدرت أقرأها ($code)."
            val jobs = JSONObject(text).optJSONArray("jobs") ?: JSONArray()
            val notes = StringBuilder()
            for (i in 0 until jobs.length()) {
                val j = jobs.getJSONObject(i)
                val concl = j.optString("conclusion")
                sb.append("| ${j.optString("name")} | ${state(j.optString("status"), concl)} |\n")
                if (concl == "failure") {
                    val (c2, t2) = get(cfg, "/check-runs/${j.optLong("id")}/annotations")
                    if (c2 in 200..299) {
                        val a = JSONArray(t2)
                        var shown = 0
                        for (k in 0 until a.length()) {
                            val o = a.getJSONObject(k)
                            if (o.optString("annotation_level") != "failure") continue
                            val msg = (o.optString("title") + " " + o.optString("message")).trim().replace("\n", " ")
                            if (msg.isBlank()) continue
                            notes.append("- ${j.optString("name")}: ${msg.take(300)}\n")
                            if (++shown >= 3) break
                        }
                    }
                }
            }
            if (notes.isNotEmpty()) sb.append("\nرسائل الخطأ:\n").append(notes)
        } catch (_: Exception) {
            sb.append("(ما قدرت أقرأ التفاصيل)")
        }
        return sb.toString().trimEnd()
    }
}
