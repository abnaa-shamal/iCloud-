package jo.abnaashamal.bot

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object Net {
    /** Returns (http status code, body text). Never prints headers, so keys are not leaked. */
    fun request(url: String, method: String, headers: Map<String, String>, body: String?): Pair<Int, String> {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.requestMethod = method
            c.connectTimeout = 15000
            c.readTimeout = 90000
            headers.forEach { (k, v) -> c.setRequestProperty(k, v) }
            if (body != null) {
                c.doOutput = true
                c.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val code = c.responseCode
            val stream = if (code in 200..299) c.inputStream else c.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
            return code to text
        } finally {
            c.disconnect()
        }
    }
}

object Ai {
    fun chat(cfg: Config, system: String, history: List<Msg>): String {
        if (cfg.aiKey.isBlank()) return "⚠️ حط مفتاح الذكاء الاصطناعي من الإعدادات."
        if (cfg.model.isBlank()) return "⚠️ اكتب اسم النموذج من الإعدادات."
        if (history.isEmpty()) return "⚠️ ما في رسالة."
        return try {
            if (cfg.format == "openai") openai(cfg, system, history) else anthropic(cfg, system, history)
        } catch (e: Exception) {
            "❌ ما اتصلت: ${e.javaClass.simpleName} ${(e.message ?: "").take(100)}"
        }
    }

    private fun anthropic(cfg: Config, system: String, history: List<Msg>): String {
        val msgs = JSONArray()
        history.forEach { msgs.put(JSONObject().put("role", it.role).put("content", it.text)) }
        val body = JSONObject()
            .put("model", cfg.model)
            .put("max_tokens", 2000)
            .put("system", system)
            .put("messages", msgs)
            .toString()
        val headers = mapOf(
            "x-api-key" to cfg.aiKey,
            "anthropic-version" to "2023-06-01",
            "content-type" to "application/json",
        )
        val (code, text) = Net.request(cfg.url, "POST", headers, body)
        if (code !in 200..299) return "❌ خطأ $code: ${errorText(text)}"
        val arr = JSONObject(text).optJSONArray("content") ?: return "❌ رد غير مفهوم من الخدمة."
        val sb = StringBuilder()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            if (o.optString("type") == "text") sb.append(o.optString("text"))
        }
        return sb.toString().ifBlank { "..." }
    }

    private fun openai(cfg: Config, system: String, history: List<Msg>): String {
        val msgs = JSONArray()
        msgs.put(JSONObject().put("role", "system").put("content", system))
        history.forEach { msgs.put(JSONObject().put("role", it.role).put("content", it.text)) }
        val body = JSONObject().put("model", cfg.model).put("messages", msgs).toString()
        val headers = mapOf(
            "Authorization" to "Bearer ${cfg.aiKey}",
            "content-type" to "application/json",
        )
        val (code, text) = Net.request(cfg.url, "POST", headers, body)
        if (code !in 200..299) return "❌ خطأ $code: ${errorText(text)}"
        val choices = JSONObject(text).optJSONArray("choices") ?: return "❌ رد غير مفهوم من الخدمة."
        if (choices.length() == 0) return "❌ رد فاضي من الخدمة."
        return choices.getJSONObject(0).optJSONObject("message")?.optString("content").orEmpty().ifBlank { "..." }
    }

    private fun errorText(text: String): String = try {
        val j = JSONObject(text)
        val e = j.optJSONObject("error")
        (e?.optString("message") ?: j.optString("error")).ifBlank { text }.take(200)
    } catch (_: Exception) {
        text.take(200)
    }
}
