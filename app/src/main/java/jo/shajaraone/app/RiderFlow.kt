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

// The rider's journey is a small tree: where to (south / north / inside the north) -> which place -> when (optional) -> trips.

const val G_SOUTH = "S"
const val G_NORTH = "N"
const val G_INNER = "I"

@Stable
class RiderFlowState(step: Int = 0, group: String = "", road: String = "", place: String = "", spot: String = "", day: Int = -1, origin: String = "") {
    var step by mutableIntStateOf(step)   // 0 = pick group, 1 = pick place, 2 = trips
    var group by mutableStateOf(group)
    var road by mutableStateOf(road)
    var place by mutableStateOf(place)
    var spot by mutableStateOf(spot)
    var day by mutableIntStateOf(day)     // -1 = any day
    var origin by mutableStateOf(origin)  // optional northern town the rider leaves from / returns to

    fun back() {
        when (step) {
            2 -> { step = 1; day = -1 }
            1 -> { step = 0; group = ""; road = ""; place = ""; spot = ""; origin = "" }
        }
    }

    fun summary(): String = when (group) {
        G_SOUTH -> listOf(origin, "الجنوب", road, place).filter { it.isNotBlank() }.joinToString(" • ")
        G_NORTH -> listOf("الشمال", place).filter { it.isNotBlank() }.joinToString(" • ")
        else -> listOf("داخل الشمال", place, spot).filter { it.isNotBlank() }.joinToString(" • ")
    }

    companion object {
        val Saver: Saver<RiderFlowState, Any> = listSaver(
            save = { listOf(it.step.toString(), it.group, it.road, it.place, it.spot, it.day.toString(), it.origin) },
            restore = { RiderFlowState(it[0].toInt(), it[1], it[2], it[3], it[4], it[5].toInt(), it[6]) }
        )
    }
}

@Composable
fun rememberRiderFlow(): RiderFlowState = rememberSaveable(saver = RiderFlowState.Saver) { RiderFlowState() }

private val allSouthStops = southRoads.values.flatten().toSet()

/** Every choice is optional: blank = any. A trip that names no specific stop (e.g. just «عمّان») matches any stop. */
fun flowMatches(t: Trip, fs: RiderFlowState): Boolean {
    val kind = kindOf(t)
    val ok = when (fs.group) {
        G_SOUTH -> kind == KIND_NS && (fs.origin.isBlank() || t.from == fs.origin) && (fs.road.isBlank() || t.road == fs.road) &&
            (fs.place.isBlank() || t.to == fs.place || t.to !in allSouthStops)
        G_NORTH -> kind == KIND_SN && (fs.place.isBlank() || t.to == fs.place)
        G_INNER -> kind == KIND_NN && (fs.place.isBlank() || t.from == fs.place || t.to == fs.place) &&
            (fs.spot.isBlank() || fs.spot == IRBID_OTHER || t.to == fs.spot || t.from == fs.spot || t.to == "إربد" || t.from == "إربد")
        else -> true
    }
    return ok && (fs.day < 0 || dayOffsetOf(t.departAt) == fs.day)
}

// ---------- small glass building blocks (rider look) ----------

@Composable
private fun GlassSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().darkGlass(24.dp).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Dim)
        content()
    }
}

@Composable
private fun GlassChip(text: String, on: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val s = RoundedCornerShape(16.dp)
    Box(
        modifier.clip(s).background(if (on) Amber else Color(0x1AFFFFFF)).border(1.dp, if (on) Amber else Color(0x26FFFFFF), s)
            .clickable(remember { MutableInteractionSource() }, null) { onClick() }.padding(horizontal = 8.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) { Text(text, fontSize = 14.sp, maxLines = 1, fontWeight = FontWeight.SemiBold, color = if (on) Color.Black else Color.White) }
}

@Composable
private fun GlassGrid(items: List<String>, selected: String, cols: Int = 3, onPick: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.chunked(cols).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { n -> GlassChip(n, n == selected, Modifier.weight(1f)) { onPick(n) } }
                repeat(cols - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun BigChoice(title: String, sub: String, index: Int, onClick: () -> Unit) {
    Row(
        Modifier.entrance(index).fillMaxWidth().squeeze(onClick).darkGlass(28.dp).padding(horizontal = 22.dp, vertical = 26.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Text(sub, fontSize = 13.sp, color = Dim)
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
            Column(Modifier.padding(bottom = 10.dp)) {
                Text("أهلاً $name", fontSize = 14.sp, color = Dim)
                Text("لوين رايح؟", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
            BigChoice("الجنوب", "عمّان والزرقاء", 0) { fs.group = G_SOUTH; fs.step = 1 }
            BigChoice("الشمال", "راجع لعندنا", 1) { fs.group = G_NORTH; fs.step = 1 }
            BigChoice("داخل الشمال", "من البلد لإربد", 2) { fs.group = G_INNER; fs.step = 1 }
        }
        1 -> Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp).padding(bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            BackBar(when (fs.group) { G_SOUTH -> "الجنوب"; G_NORTH -> "الشمال"; else -> "داخل الشمال" }) { fs.back() }
            when (fs.group) {
                G_SOUTH -> southRoads.forEach { (road, stops) ->
                    GlassSection(road) {
                        GlassGrid(listOf("كل المناطق") + stops, "") { p ->
                            fs.road = road; fs.place = if (p == "كل المناطق") "" else p; fs.step = 2
                        }
                    }
                }
                G_NORTH -> GlassSection("وين بالشمال؟") {
                    GlassGrid(listOf("كل المناطق") + northPlaces, "") { p -> fs.place = if (p == "كل المناطق") "" else p; fs.step = 2 }
                }
                else -> {
                    GlassSection("من أي بلد؟") { GlassGrid(northVillages, fs.place) { fs.place = it } }
                    if (fs.place.isNotBlank()) GlassSection("وين بإربد؟") {
                        GlassGrid(irbidSpots, fs.spot, 1) { fs.spot = it; fs.step = 2 }
                    }
                }
            }
        }
        else -> {
            var pickDate by remember { mutableStateOf(false) }
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 18.dp, end = 18.dp, bottom = 120.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                item { BackBar(fs.summary()) { fs.back() } }
                if (fs.group == G_SOUTH) item {
                    GlassSection("من أي بلد بالشمال؟ (اختياري)") {
                        GlassGrid(listOf("أي بلد") + northPlaces, if (fs.origin.isBlank()) "أي بلد" else fs.origin, 3) { fs.origin = if (it == "أي بلد") "" else it }
                    }
                }
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
