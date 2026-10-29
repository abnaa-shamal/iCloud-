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
val northPlaces = listOf("إربد", "الرمثا", "المفرق", "البويضة", "الطرة", "عمراوة", "الذنيبة", "المغيّر")

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

fun kindOf(t: Trip): String = when {
    t.road == NORTH_INNER -> KIND_NN
    t.from in northPlaces -> KIND_NS
    t.to in northPlaces -> KIND_SN
    else -> ""
}

/** Inside-north trips default to 1 JD (as the old local-area→Irbid trips did); the rest are «حسب الاتفاق». */
fun defaultFixedPrice(kind: String): Double? = if (kind == KIND_NN) 1.0 else null

/** Local departure areas (phase 4: editable from the admin panel). */
val localAreas = listOf("الذنيبة", "عمراوة", "الطرة", "الرمثا")

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
