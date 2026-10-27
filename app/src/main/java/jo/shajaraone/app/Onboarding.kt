package jo.shajaraone.app

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

private data class Page(val emoji: String, val title: String, val text: String)

private val captainPages = listOf(
    Page("👋", "أهلاً كابتن", "شجرة 1 بيوصلك بركاب منطقتك بدل قروبات الواتساب، وكل شي بمكان واحد."),
    Page("📝", "انشر رحلتك", "اختار من وين لوين، والطريق لعمّان، واليوم والساعة، وعدد المقاعد والسعر."),
    Page("🔔", "استقبل الحجوزات", "بتوصلك نغمة مميزة لما راكب يحجز، وبتشوف اسمه ونقطة نزوله وعدد مقاعده."),
    Page("✅", "قبول أو رفض", "اقبل أو ارفض كل طلب. لما تمتلئ المقاعد الرحلة بتتقفل لحالها."),
    Page("❌", "الإلغاء", "بتقدر تلغي الرحلة كاملة أو حجز راكب معيّن، مع كتابة السبب."),
    Page("⏳", "موافقة المشرف", "حسابك بيتفعّل بعد موافقة المشرف، وبعدها بتقدر تنشر رحلاتك.")
)

private val passengerPages = listOf(
    Page("👋", "أهلاً فيك", "لاقِ رحلتك من منطقتك لإربد وعمّان والزرقاء، وبالعكس."),
    Page("🔎", "دوّر على رحلة", "فلتر حسب منطقتك ووجهتك واليوم، وشوف المقاعد الباقية والسعر والطريق."),
    Page("📩", "احجز مقعدك", "اختار عدد المقاعد ونقطة نزولك، والكابتن بيرد عليك بالقبول أو الرفض."),
    Page("📞", "تواصل مباشر", "زر اتصال وزر واتساب مع الكابتن بضغطة وحدة."),
    Page("🗂️", "حجوزاتي", "تابع حالة حجزك من «حجوزاتي»، وإذا بدك تلغي المقعد بيرجع متاح لغيرك.")
)

@Composable
fun OnboardingScreen(role: String, onDone: () -> Unit) {
    val pages = if (role == ROLE_CAPTAIN) captainPages else passengerPages
    val pager = rememberPagerState { pages.size }
    val scope = rememberCoroutineScope()
    val last = pager.currentPage == pages.lastIndex

    Column(Modifier.fillMaxSize().systemBarsPadding().padding(24.dp)) {
        Row(Modifier.fillMaxWidth()) {
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onDone) { Text("تخطي") }
        }
        HorizontalPager(state = pager, modifier = Modifier.weight(1f)) { i ->
            val p = pages[i]
            Column(
                Modifier.fillMaxSize().padding(horizontal = 8.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    Modifier.size(132.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                    contentAlignment = Alignment.Center
                ) { Text(p.emoji, fontSize = 60.sp) }
                Spacer(Modifier.height(28.dp))
                Text(
                    p.title, style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold, textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    p.text, style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center
                )
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(vertical = 20.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            pages.indices.forEach { i ->
                val active = i == pager.currentPage
                val w by animateDpAsState(if (active) 24.dp else 8.dp, label = "dot")
                val c by animateColorAsState(
                    if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                    label = "dotColor"
                )
                Box(Modifier.padding(horizontal = 3.dp).height(8.dp).width(w).background(c, CircleShape))
            }
        }
        BigButton(if (last) "يلّا نبلش 🚀" else "التالي") {
            if (last) onDone() else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) }
        }
    }
}
