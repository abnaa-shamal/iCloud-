package jo.shajaraone.app

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Chips that wrap onto several lines. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChoiceChips(options: List<String>, selected: String, onSelect: (String) -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        options.forEach { o ->
            FilterChip(
                selected = o == selected,
                onClick = { onSelect(o) },
                label = { Text(o) },
                shape = MaterialTheme.shapes.small
            )
        }
    }
}

/** Chips in one horizontally scrolling line. */
@Composable
fun ScrollChips(options: List<Pair<String, String>>, selectedKey: String, onSelect: (String) -> Unit) {
    Row(
        Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        options.forEach { (key, label) ->
            FilterChip(
                selected = key == selectedKey,
                onClick = { onSelect(key) },
                label = { Text(label) },
                shape = MaterialTheme.shapes.small
            )
        }
    }
}

/** Next 7 days as (offset, label). */
fun nextDays(count: Int = 7): List<Pair<String, String>> =
    (0 until count).map { it.toString() to dayName(dayStart(it)) }

@Composable
fun Stepper(value: Int, min: Int, max: Int, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        FilledTonalIconButton(onClick = { if (value > min) onChange(value - 1) }, enabled = value > min) {
            Text("−", fontSize = 20.sp)
        }
        Text(
            "$value", fontSize = 22.sp, fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center, modifier = Modifier.width(44.dp)
        )
        FilledTonalIconButton(onClick = { if (value < max) onChange(value + 1) }, enabled = value < max) {
            Text("+", fontSize = 20.sp)
        }
    }
}

@Composable
fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 4.dp)
    )
}

/** Small colored label. */
@Composable
fun Pill(text: String, container: Color, content: Color) {
    Text(
        text,
        color = content,
        style = MaterialTheme.typography.labelMedium,
        modifier = Modifier
            .background(container, CircleShape)
            .padding(horizontal = 10.dp, vertical = 4.dp)
    )
}

@Composable
fun PricePill(price: Double?) = Pill(
    priceText(price),
    MaterialTheme.colorScheme.primaryContainer,
    MaterialTheme.colorScheme.onPrimaryContainer
)

/** Route, road, time, seats and captain — shared by all trip cards. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TripInfo(t: Trip, showCaptain: Boolean = true) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${t.from}  ←  ${t.to}",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            PricePill(t.price)
        }
        Text("🕒 ${timeText(t.departAt)}", style = MaterialTheme.typography.bodyLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            when (t.status) {
                TRIP_CANCELLED -> Pill("ملغاة", MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer)
                TRIP_FULL -> Pill("مكتملة", MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer)
                else -> Pill(
                    "💺 باقي ${seatsText(t.seatsFree)}",
                    MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
            if (t.road.isNotBlank()) {
                Pill("🛣️ ${t.road}", MaterialTheme.colorScheme.surfaceContainerHighest, MaterialTheme.colorScheme.onSurface)
            }
            if (t.acceptsParcels) {
                Pill("📦 يقبل أمانات", MaterialTheme.colorScheme.surfaceContainerHighest, MaterialTheme.colorScheme.onSurface)
            }
        }
        if (showCaptain) {
            Text(
                "الكابتن ${t.captainName}" + if (t.carText.isNotBlank()) " • ${t.carText}" else "",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (t.note.isNotBlank()) {
            Text("📝 ${t.note}", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (t.status == TRIP_CANCELLED && t.cancelReason.isNotBlank()) {
            Text("السبب: ${t.cancelReason}", color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
fun AppCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
    }
}

@Composable
fun EmptyState(emoji: String, text: String) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 48.dp, horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(emoji, fontSize = 44.sp)
        Spacer(Modifier.height(8.dp))
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

@Composable
fun LoadingBox() {
    Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
fun BigButton(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    Button(
        onClick = onClick, enabled = enabled,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth().height(56.dp)
    ) { Text(text, style = MaterialTheme.typography.titleMedium) }
}

/** Ask for a reason before cancelling. */
@Composable
fun ReasonDialog(title: String, confirmText: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var reason by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = reason, onValueChange = { reason = it.take(120) },
                label = { Text("السبب") }, placeholder = { Text("مثلاً: ظرف طارئ") },
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(reason.trim()) }, enabled = reason.isNotBlank()) {
                Text(confirmText, color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("رجوع") } }
    )
}


/** Bottom bar: reverse number verification. One tap opens WhatsApp to the support number with the code ready. */
@Composable
fun VerifyBar(me: UserProfile) {
    val ctx = LocalContext.current
    Surface(color = MaterialTheme.colorScheme.primaryContainer, tonalElevation = 3.dp) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(Modifier.weight(1f)) {
                Text("وثّق رقمك", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                Text(
                    "ابعت رمزك ${me.verifyCode} من رقمك على واتساب الدعم",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            Button(onClick = {
                openWhatsApp(
                    ctx, SUPPORT_PHONE,
                    "رمز التحقق: ${me.verifyCode}\nالاسم: ${me.name}\nالرقم: ${me.phone}\n" +
                        if (IS_CAPTAIN_APP) "تطبيق الكابتن" else "تطبيق الراكب"
                )
            }) { Text("إرسال") }
        }
    }
}
