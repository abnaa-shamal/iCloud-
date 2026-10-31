package jo.abnaashamal.bot

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** One chat message. role is "user" or "assistant". */
data class Msg(val role: String, val text: String)

/** Everything the user types in the settings screen. Stored only on this phone. */
data class Config(
    val format: String,   // "anthropic" or "openai"
    val url: String,
    val model: String,
    val aiKey: String,
    val ghToken: String,
    val ghRepo: String,   // owner/name
)

const val URL_ANTHROPIC = "https://api.anthropic.com/v1/messages"
const val URL_OPENAI = "https://api.openai.com/v1/chat/completions"

object Store {
    private fun p(c: Context) = c.getSharedPreferences("bot", Context.MODE_PRIVATE)

    fun load(c: Context): Config {
        val s = p(c)
        return Config(
            format = s.getString("format", "anthropic") ?: "anthropic",
            url = s.getString("url", URL_ANTHROPIC) ?: URL_ANTHROPIC,
            model = s.getString("model", "claude-sonnet-5-5") ?: "",
            aiKey = s.getString("aiKey", "") ?: "",
            ghToken = s.getString("ghToken", "") ?: "",
            ghRepo = s.getString("ghRepo", "abnaa-shamal/iCloud-") ?: "",
        )
    }

    fun save(c: Context, cfg: Config) {
        p(c).edit()
            .putString("format", cfg.format)
            .putString("url", cfg.url.trim())
            .putString("model", cfg.model.trim())
            .putString("aiKey", cfg.aiKey.trim())
            .putString("ghToken", cfg.ghToken.trim())
            .putString("ghRepo", cfg.ghRepo.trim())
            .apply()
    }

    fun loadChat(c: Context): List<Msg> = try {
        val arr = JSONArray(p(c).getString("chat", "[]") ?: "[]")
        (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            Msg(o.optString("r"), o.optString("t"))
        }
    } catch (_: Exception) {
        emptyList()
    }

    fun saveChat(c: Context, list: List<Msg>) {
        val arr = JSONArray()
        list.takeLast(60).forEach { arr.put(JSONObject().put("r", it.role).put("t", it.text)) }
        p(c).edit().putString("chat", arr.toString()).apply()
    }

    fun clearChat(c: Context) {
        p(c).edit().remove("chat").apply()
    }

    /** The rules file + the full project state; both are sent to the AI as its fixed instructions. */
    fun instructions(c: Context): String =
        listOf("BOT_INSTRUCTIONS.md", "PROJECT_STATE.md").joinToString("\n\n---\n\n") { name ->
            try {
                c.assets.open(name).bufferedReader(Charsets.UTF_8).use { it.readText() }
            } catch (_: Exception) {
                ""
            }
        }
}
