package jo.shajaraone.app

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The rider's two question cards: «أنت وين هسه؟» and «لوين رايح؟».
 * Everything is asked in plain words with example places, so nobody has to know what «شمال/جنوب» or a road name means.
 * The answers are written into the same [RouteState] the old picker used, so trip filtering is unchanged.
 */
@Composable
fun GuidedRoutePicker(route: RouteState) {
    var side1 by rememberSaveable { mutableStateOf("") }     // "n" = north, "s" = Amman/Zarqa
    var northFrom by rememberSaveable { mutableStateOf("") }
    var roadFrom by rememberSaveable { mutableStateOf("") }
    var stopFrom by rememberSaveable { mutableStateOf("") }
    var side2 by rememberSaveable { mutableStateOf("") }     // "s", "irbid", "village" or "n"
    var northTo by rememberSaveable { mutableStateOf("") }
    var roadTo by rememberSaveable { mutableStateOf("") }
    var stopTo by rememberSaveable { mutableStateOf("") }
    var spotTo by rememberSaveable { mutableStateOf("") }
    var open by rememberSaveable { mutableIntStateOf(1) }

    // Write the answers into the route filter.
    LaunchedEffect(side1, northFrom, roadFrom, stopFrom, side2, northTo, roadTo, stopTo, spotTo) {
        when {
            side1 == "n" && side2 == "s" -> { route.kind = KIND_NS; route.north = northFrom; route.road = roadTo; route.south = stopTo; route.spot = ""; route.reverse = false }
            side1 == "s" && side2 == "n" -> { route.kind = KIND_SN; route.north = northTo; route.road = roadFrom; route.south = stopFrom; route.spot = ""; route.reverse = false }
            side1 == "n" && side2 == "irbid" -> { route.kind = KIND_NN; route.north = northFrom; route.spot = spotTo; route.road = ""; route.south = ""; route.reverse = false }
            side1 == "n" && side2 == "village" -> { route.kind = KIND_NN; route.north = northTo; route.spot = ""; route.road = ""; route.south = ""; route.reverse = true }
            else -> { route.kind = ""; route.north = ""; route.road = ""; route.south = ""; route.spot = ""; route.reverse = false }
        }
    }

    fun clearTo() { side2 = ""; northTo = ""; roadTo = ""; stopTo = ""; spotTo = "" }

    val fromAnswer: String? = when (side1) {
        "n" -> northFrom.ifBlank { null }
        "s" -> if (stopFrom.isNotBlank()) "$stopFrom • $roadFrom" else if (roadFrom.isNotBlank()) roadFrom else null
        else -> null
    }
    val toAnswer: String? = when (side2) {
        "s" -> if (stopTo.isNotBlank()) "$stopTo • $roadTo" else if (roadTo.isNotBlank()) roadTo else null
        "irbid" -> spotTo.ifBlank { "إربد" }
        "village" -> northTo.ifBlank { null }
        "n" -> northTo.ifBlank { null }
        else -> null
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        LedgerStepCard("1", "أنت وين هسه؟", fromAnswer, "اضغط واختار مكانك", onToggle = { open = if (open == 1) 0 else 1 }, content = if (open == 1) ({
            LedgerChoice("أنا بالشمال", "إربد، الرمثا، المفرق، الطرة، عمراوة، الذنيبة، المغيّر...", selected = side1 == "n") {
                if (side1 != "n") { side1 = "n"; northFrom = ""; roadFrom = ""; stopFrom = ""; clearTo() }
            }
            LedgerChoice("أنا بعمّان أو الزرقاء", "صويلح، خلدا، الجامعة الأردنية، الشميساني، رصيفة، سحاب...", selected = side1 == "s") {
                if (side1 != "s") { side1 = "s"; northFrom = ""; roadFrom = ""; stopFrom = ""; clearTo(); side2 = "n" }
            }
            if (side1 == "n") {
                Text("اختار بلدك:", fontSize = 13.sp, color = Ledger.Muted)
                ChoiceChips(northPlaces, northFrom) { northFrom = it; clearTo(); open = 2 }
            }
            if (side1 == "s") {
                SouthPicker(roadFrom, stopFrom) { r, st -> roadFrom = r; stopFrom = st; if (st.isNotBlank()) open = 2 }
            }
        }) else null)

        val ready = side1 == "n" && northFrom.isNotBlank() || side1 == "s" && roadFrom.isNotBlank()
        LedgerStepCard("2", "لوين رايح؟", toAnswer, if (ready) "اضغط واختار وجهتك" else "اختار مكانك أول", enabled = ready,
            onToggle = { open = if (open == 2) 0 else 2 }, content = if (open == 2 && ready) ({
                if (side1 == "n") {
                    LedgerChoice("عمّان أو الزرقاء", "صويلح، شارع الأردن، طريق الزرقاء...", selected = side2 == "s") {
                        if (side2 != "s") { clearTo(); side2 = "s" }
                    }
                    if (northFrom != "إربد") {
                        LedgerChoice("إربد", "مجمع عمّان الجديد، جامعة اليرموك...", selected = side2 == "irbid") {
                            if (side2 != "irbid") { clearTo(); side2 = "irbid" }
                        }
                    } else {
                        LedgerChoice("قرية بالشمال", "الطرة، عمراوة، الذنيبة، الرمثا...", selected = side2 == "village") {
                            if (side2 != "village") { clearTo(); side2 = "village" }
                        }
                    }
                    when (side2) {
                        "s" -> SouthPicker(roadTo, stopTo) { r, st -> roadTo = r; stopTo = st; if (st.isNotBlank()) open = 0 }
                        "irbid" -> ChoiceChips(irbidSpots, spotTo) { spotTo = it; open = 0 }
                        "village" -> ChoiceChips(northVillages, northTo) { northTo = it; open = 0 }
                    }
                } else {
                    Text("اختار وجهتك بالشمال:", fontSize = 13.sp, color = Ledger.Muted)
                    ChoiceChips(northPlaces, northTo) { northTo = it; side2 = "n"; open = 0 }
                }
            }) else null)
    }
}

/** Road + stop picker for Amman and Zarqa: type the place, or pick a road and then the stop. */
@Composable
private fun SouthPicker(road: String, stop: String, onPick: (String, String) -> Unit) {
    var q by rememberSaveable { mutableStateOf("") }
    OutlinedTextField(
        value = q, onValueChange = { q = it }, singleLine = true, modifier = Modifier.fillMaxWidth(),
        placeholder = { Text("اكتب اسم منطقتك (مثلاً: خلدا)") },
        shape = MaterialTheme.shapes.medium
    )
    val matches = if (q.isBlank()) emptyList() else southRoads.flatMap { (r, list) -> list.filter { it.contains(q.trim()) }.map { r to it } }.take(8)
    if (matches.isNotEmpty()) {
        ChoiceChips(matches.map { "${it.second} • ${it.first}" }, "") { label ->
            val m = matches.first { "${it.second} • ${it.first}" == label }
            onPick(m.first, m.second); q = ""
        }
    } else if (q.isNotBlank()) {
        Text("ما لقيت هالاسم، اختار الطريق من تحت", fontSize = 13.sp, color = Ledger.Muted)
    }
    Text("أو اختار الطريق اللي بتركب منه:", fontSize = 13.sp, color = Ledger.Muted)
    val hints = mapOf(
        "طريق صويلح" to "صويلح، دابوق، خلدا، المدينة الطبية، جسر ناعور، دوار السابع...",
        "شارع الأردن" to "الجامعة الأردنية، الشميساني، الجاردنز، الدوار الأول، مطار الملكة علياء...",
        "طريق الزرقاء" to "رصيفة، ماركا، الموقر، سحاب، طبربور..."
    )
    southRoads.keys.forEach { r ->
        LedgerChoice(r, hints[r] ?: "", selected = road == r) { onPick(r, "") }
    }
    if (road.isNotBlank()) {
        Text("وين بالضبط؟", fontSize = 13.sp, color = Ledger.Muted, fontWeight = FontWeight.Bold)
        ChoiceChips(southRoads[road].orEmpty(), stop) { onPick(road, it) }
    }
}
