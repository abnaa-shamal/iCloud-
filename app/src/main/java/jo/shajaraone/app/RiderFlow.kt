package jo.shajaraone.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// The rider's journey: where are you now? -> where to? -> (which road / where in Mafraq) -> trips.

private const val MAFRAQ = "المفرق"
private val currentChoices = northPlaces + listOf(AMMAN, ZARQA)

fun roadTitle(r: String) = if (r == "طريق الزرقاء") "الطريق الصحراوي" else r

@Stable
class RiderFlowState(step: Int = 0, cur: String = "", dest: String = "", road: String = "", day: Int = -1) {
    var step by mutableIntStateOf(step)   // 0 = where are you, 1 = where to, 2 = road, 3 = trips
    var cur by mutableStateOf(cur)
    var dest by mutableStateOf(dest)
    var road by mutableStateOf(road)
    var day by mutableIntStateOf(day)     // -1 = any day

    val curNorth get() = cur in northPlaces
    val needsRoad get() = curNorth && (dest == AMMAN || dest == ZARQA || dest == MAFRAQ)

    fun back() {
        when (step) {
            3 -> { day = -1; if (needsRoad) { road = ""; step = 2 } else { dest = ""; step = 1 } }
            2 -> { dest = ""; road = ""; step = 1 }
            1 -> { cur = ""; step = 0 }
        }
    }

    fun summary(): String = listOf(cur, dest).filter { it.isNotBlank() }.joinToString(" ← ") + if (road.isNotBlank() && road != NORTH_INNER) " • " + roadTitle(road) else ""

    companion object {
        val Saver: Saver<RiderFlowState, Any> = listSaver(
            save = { listOf(it.step.toString(), it.cur, it.dest, it.road, it.day.toString()) },
            restore = { RiderFlowState(it[0].toInt(), it[1], it[2], it[3], it[4].toInt()) }
        )
    }
}

@Composable
fun rememberRiderFlow(): RiderFlowState = rememberSaveable(saver = RiderFlowState.Saver) { RiderFlowState() }

/** Trips that go from where the rider is to where he wants to go (and on the road he picked). */
fun flowMatches(t: Trip, fs: RiderFlowState): Boolean {
    val ok = when {
        fs.cur.isBlank() || fs.dest.isBlank() -> true
        fs.cur in northPlaces && fs.dest in northPlaces -> kindOf(t) == KIND_NN && innerTripCovers(t, fs.cur, fs.dest)
        else -> t.from == fs.cur && t.to == fs.dest && (fs.road.isBlank() || fs.road == NORTH_INNER || t.road == fs.road)
    }
    return ok && (fs.day < 0 || dayOffsetOf(t.departAt) == fs.day)
}

// ---------- small glass building blocks (rider look) ----------

@Composable
internal fun GlassSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().darkGlass(24.dp).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Dim)
        content()
    }
}

@Composable
internal fun GlassChip(text: String, on: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val s = RoundedCornerShape(16.dp)
    Box(
        modifier.clip(s).background(if (on) Amber else Color(0x1AFFFFFF)).border(1.dp, if (on) Amber else Color(0x26FFFFFF), s)
            .clickable(remember { MutableInteractionSource() }, null) { onClick() }.padding(horizontal = 8.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) { Text(text, fontSize = 14.sp, maxLines = 1, fontWeight = FontWeight.SemiBold, color = if (on) Color.Black else Color.White) }
}

@Composable
internal fun GlassGrid(items: List<String>, selected: String, cols: Int = 3, onPick: (String) -> Unit) = CapGrid(items, selected, 2, big = true, onPick = onPick)

@Composable
private fun BigChoice(title: String, sub: String, index: Int, onClick: () -> Unit) {
    Row(
        Modifier.entrance(index).fillMaxWidth().squeeze(onClick).darkGlass(28.dp).padding(horizontal = 22.dp, vertical = 26.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Text(sub, fontSize = 13.sp, color = Dim, maxLines = 2, lineHeight = 20.sp)
        }
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(22.dp)).background(Amber), contentAlignment = Alignment.Center) {
            Box(Modifier.rotate(180f)) { Glyph(4, Color.Black, 20.dp) }
        }
    }
}

@Composable
private fun BackBar(title: String, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 44.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(46.dp).squeeze(onBack), contentAlignment = Alignment.Center) { RoundBtn(4) }
        Text(title, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1)
    }
}

// ---------- screens ----------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RiderFlow(
    name: String, fs: RiderFlowState, shown: List<Trip>?, failed: Boolean,
    actions: @Composable ColumnScope.(Trip) -> Unit
) {
    BackHandler(enabled = fs.step > 0) { fs.back() }
    when (fs.step) {
        0 -> Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp).padding(top = 44.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Column(Modifier.padding(bottom = 6.dp)) {
                Text("أهلاً $name", fontSize = 14.sp, color = Dim)
                Text("وين أنت حالياً؟", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = Brand.Ink)
            }
            GlassSection("اختر مكانك") { GlassGrid(currentChoices, fs.cur) { fs.cur = it; fs.step = 1 } }
        }
        1 -> Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp).padding(bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            BackBar("أنت في ${fs.cur}") { fs.back() }
            Text("لوين بدك تروح؟", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Brand.Ink)
            if (fs.curNorth) {
                GlassSection("داخل الشمال") {
                    GlassGrid((northPlaces - MAFRAQ).filter { it != fs.cur }, "") { fs.dest = it; fs.road = NORTH_INNER; fs.step = 3 }
                }
                GlassSection("الجنوب") {
                    GlassGrid(listOf("إلى $AMMAN", "إلى $ZARQA", "إلى $MAFRAQ"), "") { fs.dest = it.removePrefix("إلى "); fs.road = ""; fs.step = 2 }
                }
            } else {
                GlassSection("إلى الشمال") {
                    GlassGrid(northPlaces - MAFRAQ, "") { fs.dest = it; fs.road = ""; fs.step = 3 }
                }
            }
        }
        2 -> Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp).padding(bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            BackBar("${fs.cur} ← ${fs.dest}") { fs.back() }
            if (fs.dest == MAFRAQ) {
                GlassSection("وين بالمفرق؟") { GlassGrid(listOf("دوار الدبابة", "داخل المفرق"), "") { fs.road = it; fs.step = 3 } }
            } else {
                Text("من أي طريق بدك؟", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Brand.Ink)
                southRoads.entries.forEachIndexed { i, (road, stops) ->
                    BigChoice(roadTitle(road), (if (road == "طريق الزرقاء") listOf("الصحراوي") + stops.take(3) else stops.take(4)).joinToString(" • "), i) { fs.road = road; fs.step = 3 }
                }
            }
        }
        else -> {
            var pickDate by remember { mutableStateOf(false) }
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 18.dp, end = 18.dp, bottom = 120.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                item { BackBar(fs.summary()) { fs.back() } }
                item {
                    GlassSection("بتحب تحجز موعد؟") {
                        DayPills(pillDays(5), if (fs.day in 0..4) fs.day else -1) { fs.day = if (fs.day == it) -1 else it }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            GlassChip("تاريخ آخر", fs.day > 4, Modifier.weight(1f)) { pickDate = true }
                            GlassChip(if (fs.day >= 0) "كل الأيام" else "كل الأيام ✓", fs.day < 0, Modifier.weight(1f)) { fs.day = -1 }
                        }
                        if (fs.day > 4) Text(dateLongText(dayStart(fs.day)), fontSize = 13.sp, color = Amber)
                    }
                }
                when {
                    shown == null -> item { LoadingBox() }
                    failed && shown.isEmpty() -> item { EmptyState("📶", "ما قدرت أجيب الرحلات، تأكد من الإنترنت") }
                    shown.isEmpty() -> item { EmptyState("🚌", "ما في رحلات بهالاختيار.\nجرّب يوم ثاني.") }
                    else -> {
                        item { Text("${shown.size} رحلة متاحة", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color.White) }
                        items(shown.size) { i -> TripCard(shown[i], Modifier, index = i, nearest = i == 0, actions = { actions(shown[i]) }) }
                    }
                }
            }
            if (pickDate) {
                val todayUtc = pickerMillisOfOffset(0)
                val dp = rememberDatePickerState(
                    initialSelectedDateMillis = pickerMillisOfOffset(fs.day.coerceAtLeast(0)),
                    selectableDates = object : SelectableDates {
                        override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis >= todayUtc
                    }
                )
                DatePickerDialog(
                    onDismissRequest = { pickDate = false },
                    confirmButton = { TextButton(onClick = { dp.selectedDateMillis?.let { fs.day = dayOffsetFromPicker(it) }; pickDate = false }) { Text("تم") } },
                    dismissButton = { TextButton(onClick = { pickDate = false }) { Text("إلغاء") } }
                ) { DatePicker(state = dp) }
            }
        }
    }
}

/** Third section: monthly rides for teachers and students. Announcement only for now. */
@Composable
fun SoonScreen() {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp).padding(top = 44.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("اشتراكات شهرية", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Column(Modifier.entrance(0).fillMaxWidth().darkGlass(28.dp).padding(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(Modifier.clip(RoundedCornerShape(12.dp)).background(Amber).padding(horizontal = 14.dp, vertical = 6.dp)) {
                Text("قريباً", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.Black)
            }
            CarArt(Modifier.fillMaxWidth().height(110.dp))
            Text("سيارات مشاهرة بأفضل الأسعار", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Text("خدمة جديدة جاية لك، بتوصلك كل يوم لمدرستك أو جامعتك أو عملك بسعر شهري ثابت.", fontSize = 14.sp, color = Dim)
        }
        listOf("للمعلمات", "لطلاب المدارس", "لطلاب الجامعات").forEachIndexed { i, t ->
            Row(Modifier.entrance(i + 1).fillMaxWidth().darkGlass(22.dp).padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(t, Modifier.weight(1f), fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                Glyph(7, Amber, 24.dp)
            }
        }
    }
}
