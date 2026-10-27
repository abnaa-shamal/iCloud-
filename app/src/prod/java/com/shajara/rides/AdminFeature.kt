package com.shajara.rides

import androidx.compose.runtime.Composable

/**
 * PROD BUILD: no admin panel. This empty version replaces the developer one,
 * so the users' app contains no admin code at all.
 */
object AdminFeature {
    const val enabled = false

    @Composable
    fun Screen() {}

    @Composable
    fun EnvBanner() {}
}
