package jo.shajaraone.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

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

fun isValidRoute(from: String, to: String) =
    from != to && ((from in localAreas && to in cities) || (from in cities && to in localAreas))

fun involvesAmman(from: String, to: String) = from == AMMAN || to == AMMAN

/** The captain picks a road only on trips to/from Amman or Zarqa (Irbid has a single road). */
fun needsRoadChoice(from: String, to: String) = from == AMMAN || to == AMMAN || from == ZARQA || to == ZARQA

/** Default price: from a local area to Irbid = 1 JD; all other routes «حسب الاتفاق». */
fun defaultFixedPrice(from: String, to: String): Double? =
    if (from in localAreas && to == "إربد") 1.0 else null

/** Public support contact shown to users (WhatsApp). It is NOT an identity and grants no permission. */
const val SUPPORT_PHONE = "0790905298"
