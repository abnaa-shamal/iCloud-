package jo.shajaraone.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

// ===== Route model (v2): north / south roads / inside the north =====
// Every trip has a kind: NORTH→SOUTH, SOUTH→NORTH (Amman & Zarqa) or INSIDE the north (villages <-> Irbid).
// Trip.from / Trip.to hold the real place names; Trip.road holds the south road, or NORTH_INNER for inside-north trips.

const val KIND_NS = "ns"
const val KIND_SN = "sn"
const val KIND_NN = "nn"
const val NORTH_INNER = "داخل الشمال"

/** Northern places (edit here to add / remove / reorder). */
val northPlaces = listOf("إربد", "الرمثا", "المفرق", "البويضة", "الشجرة", "الطرة", "عمراوة", "الذنيبة", "المغيّر")

/** Villages and towns that travel to Irbid inside the north (everything in the north except Irbid itself). */
val northVillages = northPlaces.filter { it != "إربد" }

/** Where the passenger gets off in Irbid on inside-north trips. The last one is settled with the captain. */
const val IRBID_OTHER = "إربد (أخرى)"
val irbidSpots = listOf("مجمع عمّان الجديد", "جامعة اليرموك", IRBID_OTHER)

/** The three southern roads (Amman & Zarqa) and the stops available on each. */
val southRoads: Map<String, List<String>> = linkedMapOf(
    "طريق صويلح" to listOf(
        "صويلح", "دابوق", "خلدا", "محمر", "كمالية", "جندويل", "المدينة الطبية", "جسر ناعور", "عميش",
        "البيادر", "دوار السابع", "دوار الثامن", "صوفية", "أم السماق", "شارع مكة المكرمة"
    ),
    "شارع الأردن" to listOf(
        "أبو نصير", "شفا بدران", "مجمع الشمال", "دوار الداخلية", "دوار الخارجية", "الجامعة الأردنية",
        "الشميساني", "الجاردنز", "الدوار الأول", "الدوار الثاني", "الدوار الثالث", "مطار الملكة علياء"
    ),
    "طريق الزرقاء" to listOf(
        "رصيفة", "ماركا الجنوبية", "ماركا الشمالية", "الموقر", "خو", "سحاب", "طبربور", "دوار دبابة"
    )
)

// Inside the north, two roads lead to the villages (stops in order, starting from Irbid). A trip covers the stops between
// its from and to, so a rider going to a stop on the way (e.g. المغيّر) sees captains who drive past it (e.g. to الشجرة).
const val ROAD_WADI = "طريق وادي الشجرة"
const val ROAD_HILAN = "طريق الحلان"
val innerRoadStops: Map<String, List<String>> = linkedMapOf(
    ROAD_WADI to listOf("إربد", "بشرى", "سال", "المغيّر", "الشجرة", "عمراوة", "الذنيبة"),
    ROAD_HILAN to listOf("إربد", "بشرى", "الطرة", "الشجرة", "عمراوة", "الذنيبة")
)
private val innerChain = setOf("الطرة", "المغيّر", "الشجرة", "عمراوة", "الذنيبة")

/** Does a trip between Irbid and this village need a road choice? (the other inner places have no road) */
fun innerNeedsRoad(a: String, b: String) = (a == "إربد" && b in innerChain) || (b == "إربد" && a in innerChain)
fun innerRoadsFor(a: String, b: String): List<String> {
    val p = if (a == "إربد") b else a
    return innerRoadStops.filter { p in it.value }.keys.toList()
}

/** Does this inside-north trip pass the rider's stops in the rider's direction? */
fun innerTripCovers(t: Trip, cur: String, dest: String): Boolean {
    val s = innerRoadStops[t.road]
    if (s == null) return t.from == cur && t.to == dest
    val a = s.indexOf(t.from); val b = s.indexOf(t.to); val i = s.indexOf(cur); val j = s.indexOf(dest)
    if (a < 0 || b < 0 || i < 0 || j < 0) return t.from == cur && t.to == dest
    return if (a < b) i < j && a <= i && j <= b else i > j && a >= i && j >= b
}

fun kindOf(t: Trip): String = when {
    t.road == NORTH_INNER || t.road in innerRoadStops -> KIND_NN
    t.from in northPlaces -> KIND_NS
    t.to in northPlaces -> KIND_SN
    else -> ""
}

/** Inside-north trips default to 1 JD (as the old local-area→Irbid trips did); the rest are «حسب الاتفاق». */
fun defaultFixedPrice(kind: String): Double? = if (kind == KIND_NN) 1.0 else null

/** Local departure areas (phase 4: editable from the admin panel). */
val localAreas = listOf("الشجرة", "الذنيبة", "عمراوة", "الطرة", "الرمثا")

/** City destinations. Every trip goes between a local area and a city (either direction). */
val cities = listOf("إربد", "عمّان", "الزرقاء")

val allPlaces = localAreas + cities

const val AMMAN = "عمّان"

const val ZARQA = "الزرقاء"

/** Two roads to Amman and to Zarqa (both directions), chosen by the captain. */
val tripRoads = listOf("الطريق الصحراوي", "طريق الزرقاء")

/**
 * Optional sub-areas, only for the three cities (villages have none). Shown as a drop-down under the city.
 * Starter list of well-known landmarks; to be edited from the admin dashboard later.
 */
val subAreas: Map<String, List<String>> = mapOf(
    AMMAN to listOf(
        "الدوار الأول", "الدوار الثاني", "الدوار الثالث", "الدوار الرابع", "الدوار الخامس",
        "الدوار السادس", "الدوار السابع", "الدوار الثامن",
        "صويلح", "الجبيهة", "طبربور", "العبدلي", "دوار الداخلية"
    ),
    "إربد" to listOf("مجمع عمّان الجديد", "جامعة اليرموك", "بوابة الاقتصاد الجديد"),
    ZARQA to listOf("وسط البلد", "الجامعة الهاشمية", "مجمع الزرقاء الجديد"),
    // The science & technology university is counted with Ramtha by the locals, not with Irbid.
    "الرمثا" to listOf("جامعة العلوم والتكنولوجيا")
)

/**
 * Sub-area lists edited by the admin (Firestore doc config/places, field subAreas). Keys are a place name
 * («عمّان») or place + road («عمّان|الطريق الصحراوي»). Observable, so screens refresh when it changes.
 * An empty map means "not loaded / never edited": the built-in [subAreas] above are used.
 */
object PlacesConfig {
    var remote: Map<String, List<String>> by mutableStateOf(emptyMap())
}

/** Sub-areas to offer under [place] on [road]: the admin's road-specific list, else the admin's list, else the built-in one. */
fun subAreasFor(place: String, road: String = ""): List<String>? {
    val r = PlacesConfig.remote
    val list = (if (road.isNotBlank()) r["$place|$road"] else null) ?: r[place] ?: subAreas[place]
    return list?.takeIf { it.isNotEmpty() }
}

/** The point is settled with the captain; also stored when the passenger leaves the sub-area empty. */
const val ANY_PLACE = "حسب اتفاق الكابتن"

const val OTHER_PLACE = "مكان آخر"

/** Public support contact shown to users (WhatsApp). It is NOT an identity and grants no permission. */
const val SUPPORT_PHONE = "0790905298"


// Approximate centres of the places, used only to suggest «وين أنت حالياً» from the phone's GPS (the rider confirms or changes it).
val placeCentres: Map<String, Pair<Double, Double>> = linkedMapOf(
    "إربد" to (32.5556 to 35.8500), "الرمثا" to (32.5686 to 36.0100), "المفرق" to (32.3400 to 36.2080),
    "البويضة" to (32.5300 to 35.8200), "الشجرة" to (32.4600 to 35.7500), "الطرة" to (32.6400 to 35.9900),
    "عمراوة" to (32.4400 to 35.7200), "الذنيبة" to (32.4200 to 35.7000), "المغيّر" to (32.4800 to 35.7700),
    "عمّان" to (31.9539 to 35.9106), "الزرقاء" to (32.0728 to 36.0876)
)

/** The nearest known place to a GPS point, or null when it is farther than ~15 km from all of them. */
fun nearestPlace(lat: Double, lng: Double): String? {
    var best: String? = null; var bestD = 15_000f
    val out = FloatArray(1)
    placeCentres.forEach { (n, c) ->
        android.location.Location.distanceBetween(lat, lng, c.first, c.second, out)
        if (out[0] < bestD) { bestD = out[0]; best = n }
    }
    return best
}
