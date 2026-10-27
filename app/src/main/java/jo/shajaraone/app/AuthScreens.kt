package jo.shajaraone.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
private fun Brand() {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Text("📍", fontSize = 52.sp)
        Text(
            APP_DISPLAY_NAME, fontSize = 36.sp, fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            "مشاويرك من منطقتك لإربد وعمّان والزرقاء",
            color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun PhoneField(value: String, onChange: (String) -> Unit) = OutlinedTextField(
    value = value,
    onValueChange = { onChange(it.filter { c -> c.isDigit() }.take(14)) },
    label = { Text("رقم الهاتف") }, placeholder = { Text("079xxxxxxx") },
    singleLine = true, shape = MaterialTheme.shapes.medium,
    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
    modifier = Modifier.fillMaxWidth()
)

@Composable
private fun PasswordField(value: String, label: String, onChange: (String) -> Unit) = OutlinedTextField(
    value = value, onValueChange = onChange,
    label = { Text(label) }, singleLine = true, shape = MaterialTheme.shapes.medium,
    visualTransformation = PasswordVisualTransformation(),
    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
    modifier = Modifier.fillMaxWidth()
)

@Composable
private fun TextInput(value: String, label: String, onChange: (String) -> Unit) = OutlinedTextField(
    value = value, onValueChange = { onChange(it.take(40)) },
    label = { Text(label) }, singleLine = true, shape = MaterialTheme.shapes.medium,
    modifier = Modifier.fillMaxWidth()
)

/** Sign in, or create a new account (passenger or captain). */
@Composable
fun AuthScreen() {
    var registering by rememberSaveable { mutableStateOf(false) }
    Column(
        Modifier.fillMaxSize().systemBarsPadding().imePadding()
            .verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Spacer(Modifier.height(16.dp))
        Brand()
        Spacer(Modifier.height(8.dp))
        if (registering) {
            ProfileForm(needAccount = true)
            TextButton(onClick = { registering = false }, modifier = Modifier.fillMaxWidth()) {
                Text("عندي حساب، بدي أسجّل دخول")
            }
        } else {
            LoginForm()
            TextButton(onClick = { registering = true }, modifier = Modifier.fillMaxWidth()) {
                Text("ما عندي حساب، بدي أسجّل جديد")
            }
        }
    }
}

@Composable
private fun LoginForm() {
    val ctx = LocalContext.current
    var phone by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }

    Text("تسجيل الدخول", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
    PhoneField(phone) { phone = it }
    PasswordField(password, "كلمة السر") { password = it }
    BigButton(if (busy) "لحظة..." else "دخول", enabled = !busy) {
        when {
            !isValidPhone(phone) -> toast(ctx, "اكتب رقمك صح: 077 أو 078 أو 079 (أو 00962)")
            password.isBlank() -> toast(ctx, "اكتب كلمة السر")
            else -> {
                busy = true
                Repo.signIn(phone, password) { err ->
                    busy = false
                    if (err != null) toast(ctx, err)
                }
            }
        }
    }
}

/**
 * New account form. With [needAccount] = false the user is already signed in
 * but has no profile yet (e.g. the first save failed), so only the profile is saved.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileForm(needAccount: Boolean) {
    val ctx = LocalContext.current
    // The role is fixed by the app: the captain app only creates captains, the rider app only passengers.
    val role = APP_ROLE
    var name by rememberSaveable { mutableStateOf("") }
    var phone by rememberSaveable {
        mutableStateOf(Repo.auth.currentUser?.email?.substringBefore("@") ?: "")
    }
    var password by remember { mutableStateOf("") }
    var password2 by remember { mutableStateOf("") }
    var carType by rememberSaveable { mutableStateOf("") }
    var carColor by rememberSaveable { mutableStateOf("") }
    var carPlate by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }

    Text(
        (if (needAccount) "حساب جديد" else "كمّل معلوماتك") + (if (role == ROLE_CAPTAIN) " • كابتن" else ""),
        style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold
    )

    TextInput(name, "الاسم") { name = it }
    if (needAccount) {
        PhoneField(phone) { phone = it }
        PasswordField(password, "كلمة السر (6 أحرف أو أكثر)") { password = it }
        PasswordField(password2, "أعد كلمة السر") { password2 = it }
    }

    if (role == ROLE_CAPTAIN) {
        SectionLabel("معلومات السيارة")
        TextInput(carType, "نوع السيارة (مثلاً: هيونداي النترا)") { carType = it }
        TextInput(carColor, "لون السيارة") { carColor = it }
        TextInput(carPlate, "رقم اللوحة") { carPlate = it }
        Text(
            "⏳ حساب الكابتن بيتفعّل بعد موافقة المشرف.",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    BigButton(if (busy) "لحظة..." else "إنشاء الحساب", enabled = !busy) {
        val captain = role == ROLE_CAPTAIN
        when {
            name.trim().length < 3 -> toast(ctx, "اكتب اسمك")
            !isValidPhone(phone) -> toast(ctx, "اكتب رقمك صح: 077 أو 078 أو 079 (أو 00962)")
            needAccount && password.length < 6 -> toast(ctx, "كلمة السر لازم تكون 6 أحرف أو أكثر")
            needAccount && password != password2 -> toast(ctx, "كلمتين السر مش متطابقات")
            captain && (carType.isBlank() || carColor.isBlank() || carPlate.isBlank()) ->
                toast(ctx, "عبّي معلومات السيارة")
            else -> {
                busy = true
                val profile = UserProfile(
                    role = role, name = name.trim(), phone = normalizePhone(phone),
                    carType = if (captain) carType.trim() else "",
                    carColor = if (captain) carColor.trim() else "",
                    carPlate = if (captain) carPlate.trim() else ""
                )
                val save = {
                    Repo.createProfile(profile) { ok ->
                        busy = false
                        if (!ok) toast(ctx, "ما قدرت أحفظ معلوماتك، تأكد من الإنترنت وجرّب كمان مرة")
                    }
                }
                if (needAccount) {
                    Repo.register(phone, password) { err ->
                        if (err != null) {
                            busy = false
                            toast(ctx, err)
                        } else save()
                    }
                } else save()
            }
        }
    }
}

/** Shown when the user is signed in but has no profile document yet. */
@Composable
fun ProfileSetupScreen() {
    Column(
        Modifier.fillMaxSize().systemBarsPadding().imePadding()
            .verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        ProfileForm(needAccount = false)
        TextButton(onClick = { Repo.signOut() }, modifier = Modifier.fillMaxWidth()) { Text("تسجيل الخروج") }
    }
}

/** Captain waiting for the admin, or rejected. */
@Composable
fun CaptainStatusScreen(me: UserProfile) {
    val ctx = LocalContext.current
    val rejected = me.status == USER_REJECTED
    Column(
        Modifier.fillMaxSize().systemBarsPadding().padding(28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(if (rejected) "🚫" else "⏳", fontSize = 64.sp)
        Spacer(Modifier.height(12.dp))
        Text(
            if (rejected) "ما تمت الموافقة على حسابك" else "حسابك بانتظار موافقة المشرف",
            style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            if (rejected) "للاستفسار تواصل مع المشرف."
            else "أهلاً كابتن ${me.name} 👋\nأول ما يوافق المشرف بيفتحلك التطبيق لحاله.",
            color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(24.dp))
        if (me.needsVerification) {
            // Reverse verification: the code must come to the support WhatsApp from the captain's own number.
            Text("رمز التحقق: ${me.verifyCode}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Button(onClick = {
                openWhatsApp(ctx, SUPPORT_PHONE,
                    "رمز التحقق: ${me.verifyCode}\nالاسم: ${me.name}\nالرقم: ${me.phone}\nتطبيق الكابتن")
            }) { Text("✅ وثّق رقمك على واتساب") }
            Spacer(Modifier.height(8.dp))
        }
        OutlinedButton(onClick = {
            openWhatsApp(ctx, SUPPORT_PHONE, "مرحبا، أنا الكابتن ${me.name} (${me.phone})، سجّلت بتطبيق $APP_DISPLAY_NAME وبانتظار الموافقة.")
        }) { Text("💬 تواصل مع المشرف") }
        TextButton(onClick = { Repo.signOut() }) { Text("تسجيل الخروج") }
    }
}
