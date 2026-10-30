package jo.shajaraone.app.hasbat

import jo.shajaraone.app.R
import jo.shajaraone.app.MainActivity

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ======================================================================
// المساعد: يشرح كل تفاصيل التطبيق للكابتن (بدون إنترنت ومجاني)
// ======================================================================
object AssistantBrain {
    data class Topic(val title: String, val keywords: List<String>, val answer: String)

    val topics: List<Topic> = listOf(
        Topic("▶️ بدء وإنهاء رحلة", listOf("ابدا", "بدء", "ابلش", "رحله", "انهاء", "انهي", "خلص", "اوقف", "ايقاف", "استئناف", "تاكيد"),
            "من الشاشة الرئيسية اضغط «بدء رحلة جديدة»، فيبدأ العداد بحساب المسافة والوقت.\n\n" +
            "• «إيقاف مؤقت»: يوقف العداد مؤقتاً، مثلاً إذا نزل الراكب لدقائق، ثم «استئناف».\n" +
            "• «إنهاء الرحلة»: اضغطه **مرتين** خلال ثانية ونصف حتى لا تنهي الرحلة بالخطأ.\n" +
            "• إذا كانت الرحلة أقل من 100 متر وأقل من دقيقة، يسألك التطبيق هل تحفظها أم تلغيها.\n" +
            "• بعد الإنهاء تُحفظ الرحلة في السجل والمالية تلقائياً."),
        Topic("💵 كيف تُحسب الأجرة؟", listOf("اجره", "سعر", "حساب", "تحسب", "كيلو", "دقيقه", "انتظار", "فتحه", "دينار", "الحد الادنى", "زحمه"),
            "الأجرة = فتحة العداد + (الكيلومترات × سعر الكيلو) + (دقائق الرحلة × سعر الدقيقة).\n\n" +
            "• **الدقائق** تُحسب كاملة من أول دقيقة في الرحلة لآخر دقيقة، وتظهر في «زمن الرحلة».\n" +
            "• أقل أجرة للرحلة **1 دينار**، حتى لو كانت قصيرة جداً.\n" +
            "• التعرفة تثبت عند بدء الرحلة، فلو غيّرت الأسعار أثناءها لا تتأثر الرحلة الجارية."),
        Topic("🤝 السعر المتفق", listOf("متفق", "اتفاق", "ثابت", "مقطوع"),
            "إذا اتفقت مع الراكب على سعر محدد، اضغط زر «🤝 سعر متفق» بجانب «بدء رحلة» قبل البدء واكتب المبلغ.\n" +
            "تُسجَّل الرحلة بهذا المبلغ، مع حفظ المسافة والوقت للتوثيق. ولإلغائه اضغط الزر مرة أخرى قبل البدء."),
        Topic("📡 الـGPS والمسافة", listOf("gps", "جي بي اس", "موقع", "اشاره", "مسافه", "ما بتنحسب", "ما بحسب", "قوي", "ضعيف", "خلفيه", "اشعار"),
            "المسافة تُحسب من إشارة GPS فقط، ولهذا تأكد من:\n" +
            "• تشغيل الموقع في الهاتف، ومنح التطبيق صلاحية الموقع.\n" +
            "• الشارة أعلى الشاشة: «GPS قوي» تعني أن كل شيء تمام، و«ضعيف» تعني أن الإشارة مؤقتاً غير دقيقة.\n" +
            "• العداد يعمل **في الخلفية** حتى لو فتحت الخرائط أو قفلت الشاشة، وستجد إشعاراً فيه الأجرة.\n" +
            "• في الأنفاق والأماكن المغلقة قد تضعف الإشارة، فيتوقف حساب المسافة مؤقتاً، ويستمر حساب الوقت."),
        Topic("🚗 سؤال «بدأت رحلة؟»", listOf("بدات رحله", "سؤال", "تلقائي", "يسالني", "يسأل"),
            "إذا كان العداد متوقفاً والتطبيق مفتوحاً، وتحركت السيارة أسرع من 15 كم/س لمدة 8 ثوانٍ، يسألك التطبيق: «بدأت رحلة؟».\n" +
            "• «نعم، ابدأ العداد»: يبدأ الرحلة فوراً.\n" +
            "• «لا»: لا يسألك مرة أخرى لمدة 15 دقيقة.\n" +
            "• «لا تسألني اليوم»: يسكت حتى نهاية اليوم."),
        Topic("🖥️ وضع القيادة", listOf("وضع القياده", "شاشه كبيره", "كبير", "قياده"),
            "أثناء الرحلة يظهر زر 🖥️ أعلى الشاشة بجانب الخريطة. اضغطه فتظهر شاشة كبيرة وبسيطة فيها الأجرة والمسافة والوقت والسرعة، مع زرّين كبيرين للإيقاف والإنهاء، لتنظر إليها بلمحة وأنت تقود.\n" +
            "للخروج منها اضغط ✕."),
        Topic("🏷️ أسعار التعرفة", listOf("تعرفه", "تعرفات", "اسعار", "اغير السعر", "الارخص", "الاغلى", "عادي", "فئه", "فئات", "saver", "electric", "comfort", "سيفر", "الكتريك", "كمفورت", "كومفورت"),
            "من قسم «الحاسبة» اضغط تبويب «أسعار التعرفة»:\n" +
            "• فوق تجد 3 فئات جاهزة، اضغط أي واحدة فيعتمدها العداد فوراً:\n" +
            "   🔵 Saver: فتحة 0.400، كم 0.200، دقيقة 0.040\n" +
            "   🟣 Electric: فتحة 0.430، كم 0.230، دقيقة 0.055\n" +
            "   🟡 Comfort: فتحة 0.450، كم 0.250، دقيقة 0.070\n" +
            "• تحتها «تعرفة يدوية»: اختر تعرفتك الخاصة أو أضف جديدة، وعدّل أسعارها بزرّي + و−، وتُحفظ تلقائياً.\n" +
            "• أثناء الرحلة يكون تعديل الأسعار مقفلاً حتى تنتهي الرحلة.\n" +
            "• «تعرفة جديدة» تنشئ تعرفة باسمك، مثل «ليلي» أو «مطار».\n" +
            "• الأسعار محصورة بين حد أدنى وأعلى حتى لا يحدث خطأ."),
        Topic("🧮 الحاسبة التقديرية", listOf("حاسبه", "تقدير", "تقديري", "كم بتكلف", "قبل الرحله"),
            "من قسم «الحاسبة» ← «الحاسبة»: اختر التعرفة، واكتب المسافة والدقائق المتوقعة، فتظهر لك الأجرة التقريبية قبل أن تبدأ. مفيدة لتعطي الراكب سعراً قبل الرحلة."),
        Topic("🧾 إيصال الراكب", listOf("ايصال", "فاتوره", "وصل", "ارسل للراكب", "واتس"),
            "بعد إنهاء أي رحلة يظهر زر «🧾 إرسال إيصال للراكب». اضغطه، واختر واتساب أو أي تطبيق، فيصل للراكب إيصال أنيق فيه التاريخ والمسار والمسافة والمدة والأجرة.\n" +
            "وتستطيع إرسال إيصال أي رحلة قديمة من: الإعدادات ← سجل الرحلات ← اضغط على الرحلة."),
        Topic("📜 سجل الرحلات والخريطة", listOf("سجل", "رحلاتي", "الرحلات", "خريطه", "مسار", "حذف رحله", "تفاصيل"),
            "من الإعدادات ← «سجل الرحلات» ترى كل رحلاتك. اضغط على أي رحلة لترى تفاصيلها: نقطة الانطلاق A، ونقطة الوصول B، والمسافة، والمدة، والتعرفة.\n" +
            "• «عرض الرحلة على الخريطة» يفتح المسار في خرائط جوجل.\n" +
            "• تستطيع حذف رحلة، أو مسح السجل كاملاً، وسيُطلب منك التأكيد."),
        Topic("💸 المالية وصافي الربح", listOf("ماليه", "ربح", "صافي", "دخل", "مصاريف", "مصروف", "بنزين", "زيت", "صيانه"),
            "قسم «المالية» فيه 3 تبويبات:\n" +
            "• **صافي الربح**: الدخل ناقص المصاريف لليوم أو 7 أيام أو الشهر، مع عدد الرحلات والمسافة ومتوسط الرحلة.\n" +
            "• **المصاريف**: سجّل البنزين والزيت والغسيل والصيانة والأقساط، ويُخصم كل ذلك من ربحك تلقائياً.\n" +
            "• **أفضل وقت**: يحلل رحلاتك ويقول لك أي يوم وأي فترة تربح فيها أكثر."),
        Topic("⏰ أفضل أوقاتك", listOf("افضل وقت", "افضل يوم", "اوقات", "متى اشتغل"),
            "في المالية ← «أفضل وقت» يحسب التطبيق من رحلات آخر 60 يوماً:\n" +
            "• أفضل يوم في الأسبوع.\n• أفضل فترة في اليوم.\n• دخلك في ساعة القيادة.\n" +
            "مع أشرطة توضح دخل كل يوم وكل فترة. تحتاج 5 رحلات على الأقل لتظهر النتائج."),
        Topic("🤲 صدقة اليوم", listOf("صدقه", "زكاه", "تصدق", "خير"),
            "في المالية ← صافي الربح ستجد بطاقة «🤲 صدقة اليوم». اختر نسبة من 1% إلى 5%، فيحسب التطبيق المبلغ من صافي ربح اليوم. وعندما تتصدق اضغط «تم، تصدّقت»، ويُجمع لك ما تصدّقت به خلال الشهر. تقبّل الله منك 🌿"),
        Topic("🏆 التحدي اليومي", listOf("تحدي", "هدف", "سلسله", "تقويم", "انجاز"),
            "في قسم «التحدي»:\n" +
            "• **تحدي اليوم**: حدد هدفك اليومي من 5 إلى 100 دينار، وتابع الدائرة والمتبقي وعدد أيام السلسلة 🔥.\n" +
            "• **سجل الإنجاز**: تقويم الشهر؛ الأخضر يعني أن الهدف تحقق، والأصفر أكثر من النصف، والأحمر لم يتحقق. تنتقل بين الأشهر بالأسهم."),
        Topic("📿 الأذكار والصلاة والقبلة", listOf("اذكار", "ذكر", "صلاه", "اذان", "قبله", "بوصله", "صباح", "مساء"),
            "في قسم «الأذكار»:\n" +
            "• **الأذكار**: أذكار الصباح والمساء مع عدّاد لكل ذكر، ويُحفظ عدّك لليوم.\n" +
            "• **الصلاة**: أوقات الصلاة حسب موقعك.\n" +
            "• **القبلة**: بوصلة تتجه للقبلة. إذا كانت غير دقيقة، حرّك الهاتف على شكل رقم 8 لمعايرة البوصلة، وابتعد عن المعادن.\n" +
            "وفي الرئيسية يتغير الذكر كل دقيقة، وتستطيع الضغط عليه لتغييره."),
        Topic("📅 تذكير الترخيص والتأمين", listOf("ترخيص", "تامين", "تذكير", "رخصه", "تجديد"),
            "من الإعدادات ← «الترخيص والتأمين» حدد تاريخ انتهاء ترخيص السيارة والتأمين، فيذكّرك التطبيق في الشاشة الرئيسية قبل الموعد بأسبوع."),
        Topic("🎨 الثيمات والشكل", listOf("ثيم", "لون", "الوان", "شكل", "ليلي", "نهاري", "تلقائي"),
            "من الإعدادات ← «الثيمات» اختر من 18 ثيماً. ويمكنك تفعيل **التبديل التلقائي**، فيستخدم التطبيق ثيم النهار من الشروق حتى المغرب، ثم ثيم الليل."),
        Topic("🔊 الأصوات واللغة", listOf("صوت", "اصوات", "نقره", "نغمه", "لغه", "انجليزي", "عربي"),
            "من الإعدادات ← «الأصوات» تشغّل أو تطفئ أصوات النقر ونغمات البدء والإنهاء والافتتاح.\n" +
            "ومن الإعدادات ← «اللغة» تختار العربية أو الإنجليزية."),
        Topic("💾 النسخ الاحتياطي", listOf("نسخ", "احتياطي", "اكسل", "تصدير", "ملف", "بيانات", "استرجاع", "ارجع رحلاتي", "حفظ تلقائي", "ضاعت", "انحذفت", "راحت"),
            "من الإعدادات ← «النسخ الاحتياطي»:\n" +
            "• وتستطيع تصدير رحلاتك ومصاريفك كملف يفتح في Excel.\n" +
            "كل بياناتك محفوظة على هاتفك فقط، ولا تُرسل لأي جهة."),
        Topic("🫧 الفقاعة العائمة", listOf("فقاعه", "فقاعة", "دائره", "ايقونه صغيره", "فوق التطبيقات", "على الشاشه", "عائمه"),
            "لما تطلع من التطبيق (مثلاً لتفتح الخرائط) تظهر فقاعة صغيرة على الشاشة، ووقت الرحلة تعرض الأجرة.\n" +
            "• اضغطها لترجع للتطبيق، واسحبها إلى ✕ في الأسفل لتسكّرها.\n" +
            "• لتشغيلها: الإعدادات ← «الفقاعة العائمة»، وفعّل إذن «الظهور فوق التطبيقات الأخرى»."),
        Topic("👋 تحية", listOf("السلام", "مرحبا", "اهلا", "هلا", "صباح الخير", "مساء الخير", "شكرا", "يعطيك العافيه", "مشكور", "تسلم"),
            "أهلاً وسهلاً يا كابتن 🌿 الله يعطيك العافية. اسألني عن أي شيء في التطبيق: الأجرة، التعرفة، المالية، الفقاعة... وأنا حاضر."),
        Topic("🎧 الدعم الفني", listOf("دعم", "مساعده", "تواصل", "مشكله", "خلل", "عطل", "مطور", "مش شغال", "ما بشتغل", "ما بفتح", "معلق", "علق", "بطيء", "خربان", "غلط", "شكوى", "اقتراح", "واتساب"),
            "للتواصل مع المطوّر (abo-tala): اضغط زر 🎧 «دعم فني» أعلى الشاشة الرئيسية، أو زر «الدعم الفني على واتساب» هنا بالأسفل، واكتب مشكلتك أو اقتراحك.")
    )

    /** توحيد الكتابة العربية حتى يفهم السؤال بأي طريقة كُتب */
    fun normalize(s: String): String {
        val sb = StringBuilder()
        for (ch in s.lowercase()) {
            when (ch) {
                'أ', 'إ', 'آ' -> sb.append('ا')
                'ة' -> sb.append('ه')
                'ى' -> sb.append('ي')
                'ؤ' -> sb.append('و')
                'ئ' -> sb.append('ي')
                'ڤ' -> sb.append('ف')
                'گ' -> sb.append('ك')
                in 'ً'..'ْ', 'ـ' -> {} // التشكيل والتطويل
                else -> sb.append(ch)
            }
        }
        // حذف الحروف المكررة مثل «شكراااا» وعلامات الترقيم
        return sb.toString().replace(Regex("(\\p{L})\\1{2,}"), "$1").replace(Regex("[^\\p{L}\\p{N}\\s]"), " ")
    }

    /** يحذف «ال» وحروف الربط من أول الكلمة: «بالتعرفه» ← «تعرفه» */
    private fun stem(w: String): String {
        var x = w
        for (p in listOf("وال", "بال", "فال", "كال", "لل", "ال")) if (x.length > p.length + 2 && x.startsWith(p)) { x = x.removePrefix(p); break }
        if (x.length > 4 && (x.startsWith("و") || x.startsWith("ب") || x.startsWith("ف"))) x = x.substring(1)
        return x
    }

    /** هل الكلمتان متقاربتان (خطأ حرف واحد)؟ */
    private fun close(a: String, b: String): Boolean {
        if (a == b) return true
        if (minOf(a.length, b.length) < 4 || kotlin.math.abs(a.length - b.length) > 1) return false
        val prev = IntArray(b.length + 1) { it }
        val cur = IntArray(b.length + 1)
        for (i in 1..a.length) {
            cur[0] = i
            for (j in 1..b.length) {
                cur[j] = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1)
            }
            System.arraycopy(cur, 0, prev, 0, cur.size)
        }
        return prev[b.length] <= 1
    }

    /** يختار أقرب موضوع للسؤال، أو null إذا لم يفهم */
    fun answer(question: String): Topic? {
        val q = normalize(question)
        if (q.isBlank()) return null
        val words = q.split(Regex("\\s+")).filter { it.isNotBlank() }
        var best: Topic? = null
        var bestScore = 0
        // التحية لا تُختار إلا إذا لم يوجد سؤال حقيقي في الرسالة
        val greeting = topics.firstOrNull { it.title.startsWith("👋") }
        topics.filter { it !== greeting }.forEach { t ->
            var score = 0
            t.keywords.forEach { k ->
                val nk = normalize(k).trim()
                if (q.contains(nk)) score += if (nk.length >= 5) 3 else 2
                else if (!nk.contains(' ')) {
                    // تقارب الكلمات: يفهم الأخطاء الإملائية البسيطة و«ال» في أول الكلمة
                    val sk = stem(nk)
                    if (words.any { w -> close(stem(w), sk) }) score += 1
                }
            }
            if (q.contains(normalize(t.title).replace(Regex("[^\\p{L}\\s]"), "").trim())) score += 3
            if (score > bestScore) { bestScore = score; best = t }
        }
        if (best == null && greeting != null && greeting.keywords.any { k -> q.contains(normalize(k).trim()) }) return greeting
        return best
    }
}

@Composable
fun AssistantSection(storage: AppDataManager, theme: AppTheme, isEnglish: Boolean, scrollState: ScrollState? = null) {
    val context = LocalContext.current
    val view = LocalView.current
    val sound = storage.soundEnabled
    val textColor = textColorFor(theme)
    val subTextColor = subTextColorFor(theme)
    val messages = remember {
        mutableStateListOf(
            false to "أهلاً يا كابتن 👋 أنا الكابتن المساعد في «رفيق دربك». اختر موضوعاً من الأزرار، أو اكتب سؤالك بكلماتك، وأشرح لك أي شيء في التطبيق.\n\n(إذا أزعجتك فقاعتي المتحركة، اضغط عليها مطولاً فأختفي لبقية اليوم 😄)"
        )
    }
    var input by remember { mutableStateOf("") }
    var unanswered by remember { mutableStateOf(false) }
    // النزول تلقائياً لآخر رسالة
    LaunchedEffect(messages.size) {
        if (scrollState != null && messages.size > 1) {
            kotlinx.coroutines.delay(60L)
            scrollState.animateScrollTo(scrollState.maxValue)
        }
    }

    fun ask(question: String, topic: AssistantBrain.Topic?) {
        messages.add(true to question)
        if (topic != null) {
            messages.add(false to topic.answer)
            unanswered = false
        } else {
            messages.add(false to "لم أفهم سؤالك تماماً 🙏 جرّب كلمات أخرى، مثل «أجرة» أو «تعرفة» أو «تفعيل» أو «GPS»، أو اختر من الأزرار.\nوإذا عندك مشكلة، اضغط زر الدعم الفني بالأسفل ويرد عليك المطوّر مباشرة.")
            unanswered = true
        }
    }

    Text("اضغط موضوعاً:", fontSize = 13.sp, color = subTextColor, fontWeight = FontWeight.Bold)
    ScrollableChipRow(items = AssistantBrain.topics.map { it.title }, selectedIndex = -1, theme = theme) { i ->
        SoundEffectsHelper.playClick(view, sound)
        val t = AssistantBrain.topics[i]
        ask(t.title, t)
    }

    // المحادثة
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        messages.forEach { (fromUser, text) ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = if (fromUser) Arrangement.Start else Arrangement.End) {
                val shape = RoundedCornerShape(18.dp)
                Box(
                    modifier = Modifier
                        .widthIn(max = 320.dp)
                        .clip(shape)
                        .background(if (fromUser) theme.accentColor else (if (theme.isDark) Color.White.copy(alpha = 0.07f) else Color.Black.copy(alpha = 0.05f)))
                        .border(1.dp, theme.accentColor.copy(alpha = if (fromUser) 0f else 0.35f), shape)
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = (if (fromUser) "" else "🤖 ") + text.replace("**", ""),
                        fontSize = 14.sp,
                        lineHeight = 22.sp,
                        color = if (fromUser) readableOn(theme.accentColor) else textColor
                    )
                }
            }
        }
    }

    if (unanswered) {
        NeonActionButton(
            text = "🎧 الدعم الفني على واتساب",
            colors = listOf(Color(0xFF25D366), Color(0xFF128C7E)),
            modifier = Modifier.fillMaxWidth(),
            height = 48.dp,
            textSize = 14.sp
        ) {
            SoundEffectsHelper.playClick(view, sound)
            try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/${BuildMode.WHATSAPP_NUMBER}"))) } catch (_: Exception) {}
        }
    }

    // كتابة سؤال
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = input,
            onValueChange = { input = it },
            label = { Text("اكتب سؤالك...") },
            modifier = Modifier.weight(1f),
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            colors = neonFieldColors(theme)
        )
        NeonActionButton(
            text = "إرسال",
            colors = listOf(theme.accentColor, theme.needleColor),
            // عرض ثابت: الزر كان يتمدد ويأخذ مكان خانة الكتابة
            modifier = Modifier.width(96.dp).padding(top = 6.dp),
            height = 52.dp,
            textSize = 14.sp
        ) {
            val q = input.trim()
            if (q.isNotEmpty()) {
                SoundEffectsHelper.playClick(view, sound)
                ask(q, AssistantBrain.answer(q))
                input = ""
            }
        }
    }
}
