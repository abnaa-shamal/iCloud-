package jo.abnaashamal.bot

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Same family as the Abnaa Al-Shamal apps: deep forest night, ochre gold accent, parchment text, glass cards.
private val Bg = Color(0xFF0A100D)
private val BgTop = Color(0xFF1A2B22)
private val Card = Color(0x1FFFFFFF)
private val Rule = Color(0x33FFFFFF)
private val Accent = Color(0xFFFFC857)
private val OnAccent = Color(0xFF1B2A22)
private val UserBubble = Color(0xFF2D4436)
private val TextMain = Color(0xFFF4F7FF)
private val TextDim = Color(0xFFA9B4CC)
private val Screen get() = Brush.verticalGradient(listOf(BgTop, Bg))

@Composable
fun BotTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Accent,
            onPrimary = OnAccent,
            background = Bg,
            onBackground = TextMain,
            surface = Color(0xFF16231C),
            secondary = UserBubble,
            onSurface = TextMain,
        ),
        content = content,
    )
}

@Composable
fun BotApp() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var cfg by remember { mutableStateOf(Store.load(ctx)) }
    val msgs = remember { mutableStateListOf<Msg>().apply { addAll(Store.loadChat(ctx)) } }
    var input by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(Store.load(ctx).aiKey.isBlank()) }
    val system = remember { Store.instructions(ctx) }
    val listState = rememberLazyListState()

    fun ask() {
        busy = true
        // Error/warning bubbles are not sent back to the AI.
        val hist = msgs
            .filter { !it.text.startsWith("❌") && !it.text.startsWith("⚠️") }
            .dropWhile { it.role != "user" }
        val current = cfg
        scope.launch {
            val reply = withContext(Dispatchers.IO) { Ai.chat(current, system, hist) }
            msgs.add(Msg("assistant", reply))
            Store.saveChat(ctx, msgs)
            busy = false
        }
    }

    fun send() {
        val t = input.trim()
        if (t.isEmpty() || busy) return
        input = ""
        msgs.add(Msg("user", t))
        Store.saveChat(ctx, msgs)
        ask()
    }

    fun checkBuilds() {
        if (busy) return
        busy = true
        val current = cfg
        scope.launch {
            val (txt, bad) = withContext(Dispatchers.IO) { GitHub.buildReport(current) }
            msgs.add(Msg("assistant", txt))
            Store.saveChat(ctx, msgs)
            busy = false
            if (bad && current.aiKey.isNotBlank()) {
                msgs.add(Msg("user", "لخّص أخطاء آخر فشل بجدول: البند | الحالة | تفصيل قصير. وقل شو ما بتقدر تعمل."))
                ask()
            }
        }
    }

    LaunchedEffect(msgs.size, busy) {
        // One extra item at the end (spinner or spacer), so msgs.size is always a valid index.
        listState.animateScrollToItem(msgs.size)
    }

    if (showSettings) {
        SettingsScreen(
            cfg = cfg,
            onSave = { Store.save(ctx, it); cfg = it; showSettings = false },
            onClear = { msgs.clear(); Store.clearChat(ctx) },
            onClose = { showSettings = false },
            canClose = cfg.aiKey.isNotBlank(),
        )
        return
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Screen)
            .systemBarsPadding()
            .imePadding(),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BotLogo(40.dp)
            Spacer(Modifier.padding(horizontal = 6.dp))
            Column(Modifier.weight(1f)) {
                Text("Abo-tala Assistant", color = Accent, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                Text("مساعدك لمشروع أبناء الشمال", color = TextDim, fontSize = 12.sp)
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Pill("🔍 افحص البناء", !busy) { checkBuilds() }
            Pill("📍 وين وصلنا؟", !busy) {
                msgs.add(Msg("user", "وين وصلنا؟ لخّص الحالة بجدول وقل شو الخطوة الجاية."))
                Store.saveChat(ctx, msgs)
                ask()
            }
            Pill("⚙️ إعدادات", true) { showSettings = true }
        }
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (msgs.isEmpty()) {
                item {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = Card,
                        border = BorderStroke(1.dp, Rule),
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    ) {
                        Column(Modifier.padding(18.dp)) {
                            Text("يا هلا Abo-tala 👋", color = Accent, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "أنا رفيقك لمشروع أبناء الشمال. اسألني أي شي، أو اضغط «افحص البناء» عشان أقرأ آخر نتائج GitHub، أو «وين وصلنا؟» عشان أكمل معك من آخر نقطة.",
                                color = TextMain, fontSize = 15.sp, lineHeight = 22.sp,
                            )
                        }
                    }
                }
            }
            itemsIndexed(msgs) { _, m -> Bubble(m) }
            item {
                if (busy) Text("⏳ لحظة...", color = TextDim, modifier = Modifier.padding(8.dp))
                else Spacer(Modifier.height(1.dp))
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("اكتب هون...", color = TextDim) },
                maxLines = 4,
                shape = RoundedCornerShape(20.dp),
            )
            Spacer(Modifier.padding(horizontal = 4.dp))
            Button(onClick = { send() }, enabled = !busy && input.isNotBlank()) { Text("إرسال") }
        }
    }
}

@Composable
private fun Bubble(m: Msg) {
    val user = m.role == "user"
    Box(
        Modifier.fillMaxWidth(),
        contentAlignment = if (user) Alignment.CenterStart else Alignment.CenterEnd,
    ) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = if (user) UserBubble else Card,
            border = BorderStroke(1.dp, Rule),
            modifier = if (user) Modifier.widthIn(max = 300.dp) else Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(12.dp)) { RichText(m.text) }
        }
    }
}

/** Plain text, plus a simple renderer for markdown tables (| a | b |) that the AI and the build report use. */
@Composable
private fun RichText(text: String) {
    val lines = text.lines()
    Column {
        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            if (line.trim().startsWith("|")) {
                val rows = mutableListOf<List<String>>()
                while (i < lines.size && lines[i].trim().startsWith("|")) {
                    val cells = lines[i].trim().trim('|').split("|").map { it.trim() }
                    val isSeparator = cells.all { c -> c.isNotEmpty() && c.all { ch -> ch == '-' || ch == ':' } }
                    if (!isSeparator) rows.add(cells)
                    i++
                }
                TableView(rows)
            } else {
                val clean = line.replace("**", "").replace("`", "")
                if (clean.isBlank()) Spacer(Modifier.height(6.dp))
                else Text(clean, color = TextMain, fontSize = 15.sp, lineHeight = 22.sp)
                i++
            }
        }
    }
}

@Composable
private fun TableView(rows: List<List<String>>) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .border(BorderStroke(1.dp, TextDim.copy(alpha = 0.4f)), RoundedCornerShape(8.dp)),
    ) {
        rows.forEachIndexed { idx, cells ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(if (idx == 0) Accent.copy(alpha = 0.18f) else Color.Transparent),
            ) {
                cells.forEach { cell ->
                    Text(
                        cell,
                        color = TextMain,
                        fontSize = 12.sp,
                        fontWeight = if (idx == 0) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 6.dp, vertical = 6.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsScreen(
    cfg: Config,
    onSave: (Config) -> Unit,
    onClear: () -> Unit,
    onClose: () -> Unit,
    canClose: Boolean,
) {
    var format by remember { mutableStateOf(cfg.format) }
    var url by remember { mutableStateOf(cfg.url) }
    var model by remember { mutableStateOf(cfg.model) }
    var aiKey by remember { mutableStateOf(cfg.aiKey) }
    var ghToken by remember { mutableStateOf(cfg.ghToken) }
    var ghRepo by remember { mutableStateOf(cfg.ghRepo) }

    Column(
        Modifier
            .fillMaxSize()
            .background(Screen)
            .systemBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) { BotLogo(32.dp); Spacer(Modifier.padding(horizontal = 6.dp)); Text("الإعدادات", color = Accent, fontWeight = FontWeight.Bold, fontSize = 20.sp) }
        Text("كل شي بينحفظ على جوالك بس. ما بينرفع لأي مكان غير الخدمة اللي بتحددها.", color = TextDim, fontSize = 13.sp)

        Text("نوع خدمة الذكاء الاصطناعي", color = TextMain, fontWeight = FontWeight.Bold)
        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = format == "anthropic", onClick = { format = "anthropic"; url = URL_ANTHROPIC })
            Text("Anthropic (Claude)", color = TextMain)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = format == "openai", onClick = { format = "openai"; url = URL_OPENAI })
            Text("متوافقة مع OpenAI", color = TextMain)
        }
        OutlinedTextField(url, { url = it }, label = { Text("رابط الخدمة") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(model, { model = it }, label = { Text("اسم النموذج") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(
            aiKey, { aiKey = it },
            label = { Text("مفتاح الذكاء الاصطناعي") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
        )

        Text("GitHub (قراءة فقط)", color = TextMain, fontWeight = FontWeight.Bold)
        OutlinedTextField(ghRepo, { ghRepo = it }, label = { Text("المستودع (owner/name)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(
            ghToken, { ghToken = it },
            label = { Text("مفتاح GitHub (اختياري للمستودع العام)") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
        )

        Button(
            onClick = { onSave(Config(format, url, model, aiKey, ghToken, ghRepo)) },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("حفظ") }
        if (canClose) TextButton(onClick = onClose, modifier = Modifier.fillMaxWidth()) { Text("رجوع بدون حفظ") }
        TextButton(onClick = onClear, modifier = Modifier.fillMaxWidth()) { Text("مسح المحادثة") }
    }
}

@Composable
private fun Pill(text: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .border(BorderStroke(1.dp, if (enabled) Accent.copy(alpha = 0.6f) else Rule), RoundedCornerShape(50))
            .background(Card)
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(text, color = if (enabled) Accent else TextDim, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}
