package com.shajara.rides

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** DEV BUILD: the admin panel exists. (The prod build has an empty version of this file.) */
object AdminFeature {
    const val enabled = true

    @Composable
    fun Screen() = AdminScreen()

    /** Green strip: everything outside the admin panel uses the TEST project. */
    @Composable
    fun EnvBanner() = EnvStrip("🧪 نسخة المطوّر • بيانات تجريبية", Color(0xFF1B7A3E))
}

@Composable
fun EnvStrip(text: String, color: Color) {
    Text(
        text,
        color = Color.White,
        style = MaterialTheme.typography.labelLarge,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().background(color).padding(vertical = 6.dp)
    )
}
