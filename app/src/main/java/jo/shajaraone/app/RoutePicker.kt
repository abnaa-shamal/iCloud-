package jo.shajaraone.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

/** What the user picked for the route. Blank fields mean "any" when browsing, and "not chosen yet" when publishing. */
@Stable
class RouteState(kind: String = "", road: String = "", north: String = "", south: String = "", spot: String = "", reverse: Boolean = false) {
    var kind by mutableStateOf(kind)
    var road by mutableStateOf(road)
    var north by mutableStateOf(north)
    var south by mutableStateOf(south)
    var spot by mutableStateOf(spot)
    /** Inside-north only: false = village → Irbid, true = Irbid → village. */
    var reverse by mutableStateOf(reverse)

    fun from() = when (kind) { KIND_NS -> north; KIND_SN -> south; KIND_NN -> if (reverse) spot else north; else -> "" }
    fun to() = when (kind) { KIND_NS -> south; KIND_SN -> north; KIND_NN -> if (reverse) north else spot; else -> "" }
    fun roadField() = when (kind) { KIND_NN -> NORTH_INNER; KIND_NS, KIND_SN -> road; else -> "" }

    fun complete() = when (kind) {
        KIND_NS, KIND_SN -> north.isNotBlank() && road.isNotBlank() && south.isNotBlank()
        KIND_NN -> north.isNotBlank() && spot.isNotBlank()
        else -> false
    }

    fun chooseKind(k: String) {
        kind = k
        if (k == KIND_NN && north == "إربد") north = ""
        if (k != KIND_NN) { spot = ""; reverse = false }
        if (k == KIND_NN) { road = ""; south = "" }
    }

    fun chooseRoad(r: String) {
        road = r
        if (south !in (southRoads[r] ?: emptyList())) south = ""
    }

    fun swap() {
        when (kind) {
            KIND_NS -> kind = KIND_SN
            KIND_SN -> kind = KIND_NS
            KIND_NN -> reverse = !reverse
        }
    }

    /** True when the trip fits what is picked so far. */
    fun matches(t: Trip): Boolean {
        if (kind.isBlank()) return true
        if (kindOf(t) != kind) return false
        return when (kind) {
            KIND_NS -> (north.isBlank() || t.from == north) && (road.isBlank() || t.road == road) && (south.isBlank() || t.to == south)
            KIND_SN -> (north.isBlank() || t.to == north) && (road.isBlank() || t.road == road) && (south.isBlank() || t.from == south)
            else -> (t.from in irbidSpots) == reverse &&
                (north.isBlank() || (if (reverse) t.to else t.from) == north) &&
                (spot.isBlank() || (if (reverse) t.from else t.to) == spot)
        }
    }

    companion object {
        val Saver: Saver<RouteState, Any> = listSaver(
            save = { listOf(it.kind, it.road, it.north, it.south, it.spot, it.reverse.toString()) },
            restore = { RouteState(it[0], it[1], it[2], it[3], it[4], it[5].toBoolean()) }
        )
    }
}

@Composable
fun rememberRouteState(kind: String = ""): RouteState =
    androidx.compose.runtime.saveable.rememberSaveable(saver = RouteState.Saver) { RouteState(kind) }

/** Big glass card used for the route type and the road. */
@Composable
private fun ChoiceCard(title: String, subtitle: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    val bg = if (selected) Brush.linearGradient(listOf(Brand.Violet, Brand.Violet))
    else Brush.linearGradient(listOf(Brand.Stock, Brand.Stock))
    Row(
        Modifier.fillMaxWidth().background(bg, shape)
            .border(1.dp, if (selected) Brand.Violet else Brand.Rule, shape)
            .clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = if (selected) Brand.Navy else Brand.Ink)
            if (subtitle.isNotBlank()) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = if (selected) Brand.Navy.copy(alpha = 0.85f) else Brand.Muted)
        }
        if (selected) Text("✓", color = Brand.Ochre, style = MaterialTheme.typography.titleLarge)
    }
}

/**
 * The route steps as cards: route type → (road) → places. [allowAll] adds «الكل» chips and lets the user
 * un-pick the type (used for browsing); when false every step must be chosen (used by the captain to publish).
 */
@Composable
fun RoutePicker(st: RouteState, allowAll: Boolean) {
    fun opts(list: List<String>) = (if (allowAll) listOf("" to "الكل") else emptyList()) + list.map { it to it }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionLabel("نوع المشوار")
        ChoiceCard("الشمال  ←  عمّان والزرقاء", "من إربد والرمثا وقراها لعمّان والزرقاء", st.kind == KIND_NS) {
            if (allowAll && st.kind == KIND_NS) st.kind = "" else st.chooseKind(KIND_NS)
        }
        ChoiceCard("عمّان والزرقاء  ←  الشمال", "رجعة من عمّان والزرقاء للشمال", st.kind == KIND_SN) {
            if (allowAll && st.kind == KIND_SN) st.kind = "" else st.chooseKind(KIND_SN)
        }
        ChoiceCard("داخل الشمال", "الرمثا وقراها ⇄ إربد", st.kind == KIND_NN) {
            if (allowAll && st.kind == KIND_NN) st.kind = "" else st.chooseKind(KIND_NN)
        }

        if (st.kind.isNotBlank()) {
            OutlinedButton(onClick = { st.swap() }, shape = MaterialTheme.shapes.medium) { Text("⇄  عكس الاتجاه") }
        }

        when (st.kind) {
            KIND_NS -> {
                SectionLabel("من وين بالشمال؟")
                ScrollChips(opts(northPlaces), st.north) { st.north = it }
                RoadAndStops(st, allowAll, "لأي منطقة؟")
            }
            KIND_SN -> {
                RoadAndStops(st, allowAll, "من أي منطقة؟")
                SectionLabel("لوين بالشمال؟")
                ScrollChips(opts(northPlaces), st.north) { st.north = it }
            }
            KIND_NN -> {
                SectionLabel(if (st.reverse) "لوين؟ (البلد)" else "من وين؟ (البلد)")
                ScrollChips(opts(northVillages), st.north) { st.north = it }
                SectionLabel(if (st.reverse) "من وين بإربد؟" else "لوين بإربد؟")
                ScrollChips(opts(irbidSpots), st.spot) { st.spot = it }
                if (st.spot == IRBID_OTHER) {
                    Text("الوجهة بتتفق عليها مع الكابتن.", style = MaterialTheme.typography.bodySmall, color = Brand.VioletLight)
                }
            }
        }
    }
}

@Composable
private fun RoadAndStops(st: RouteState, allowAll: Boolean, stopsLabel: String) {
    SectionLabel("أي طريق؟")
    southRoads.keys.forEach { r ->
        ChoiceCard(r, "", st.road == r) { if (allowAll && st.road == r) st.road = "" else st.chooseRoad(r) }
    }
    val stops = southRoads[st.road]
    if (stops != null) {
        SectionLabel(stopsLabel)
        ScrollChips((if (allowAll) listOf("" to "الكل") else emptyList()) + stops.map { it to it }, st.south) { st.south = it }
    }
}

/**
 * Day + hour. The calendar opens first; once a day is picked the clock opens so the hour is chosen by hand.
 * [dayOffset] null = no day chosen. [minutes] null = no hour chosen (minutes since midnight).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WhenPicker(dayOffset: Int?, minutes: Int?, onDay: (Int?) -> Unit, onMinutes: (Int?) -> Unit, allowClear: Boolean) {
    var showDate by remember { mutableStateOf(false) }
    var showTime by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(
            onClick = { showDate = true }, shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth().height(48.dp)
        ) { Text(if (dayOffset != null) dateLongText(dayStart(dayOffset)) else "اختار اليوم من التقويم") }
        if (dayOffset != null) {
            OutlinedButton(
                onClick = { showTime = true }, shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                Text(
                    if (minutes != null) "الساعة ${clockText(dayStart(dayOffset) + minutes * 60_000L)}"
                    else if (allowClear) "الساعة (اختياري)" else "اختار الساعة"
                )
            }
        }
        if (allowClear && dayOffset != null) {
            TextButton(onClick = { onDay(null); onMinutes(null) }) { Text("كل الأيام") }
        }
    }

    if (showDate) {
        val todayUtc = pickerMillisOfOffset(0)
        val dp = rememberDatePickerState(
            initialSelectedDateMillis = pickerMillisOfOffset(dayOffset ?: 0),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis >= todayUtc
            }
        )
        DatePickerDialog(
            onDismissRequest = { showDate = false },
            confirmButton = {
                TextButton(onClick = {
                    dp.selectedDateMillis?.let { onDay(dayOffsetFromPicker(it)); showTime = true }
                    showDate = false
                }) { Text("تم") }
            },
            dismissButton = { TextButton(onClick = { showDate = false }) { Text("إلغاء") } }
        ) { DatePicker(state = dp) }
    }

    if (showTime) {
        val st = rememberTimePickerState(initialHour = (minutes ?: 420) / 60, initialMinute = (minutes ?: 420) % 60, is24Hour = false)
        AlertDialog(
            onDismissRequest = { showTime = false },
            confirmButton = { TextButton(onClick = { onMinutes(st.hour * 60 + st.minute); showTime = false }) { Text("تم") } },
            dismissButton = { TextButton(onClick = { showTime = false }) { Text("إلغاء") } },
            text = { CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) { TimePicker(state = st) } }
        )
    }
}
