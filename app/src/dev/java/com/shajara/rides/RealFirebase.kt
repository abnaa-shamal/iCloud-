package com.shajara.rides

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

/**
 * DEV BUILD ONLY.
 * The developer app runs on the TEST Firebase project, except the admin panel,
 * which must manage the REAL captains and passengers. This opens a second,
 * separate connection to the real project. Its settings are read at build time
 * from the real google-services.json (kept in GitHub Secrets), never from the repo.
 */
object RealFirebase {
    private const val NAME = "real"

    val configured get() = BuildConfig.REAL_PROJECT_ID.isNotBlank()

    private fun app(ctx: Context): FirebaseApp? {
        if (!configured) return null
        FirebaseApp.getApps(ctx).firstOrNull { it.name == NAME }?.let { return it }
        val options = FirebaseOptions.Builder()
            .setProjectId(BuildConfig.REAL_PROJECT_ID)
            .setApplicationId(BuildConfig.REAL_APP_ID)
            .setApiKey(BuildConfig.REAL_API_KEY)
            .setStorageBucket(BuildConfig.REAL_STORAGE_BUCKET)
            .build()
        return FirebaseApp.initializeApp(ctx.applicationContext, options, NAME)
    }

    fun auth(ctx: Context): FirebaseAuth? = app(ctx)?.let { FirebaseAuth.getInstance(it) }
    fun db(ctx: Context): FirebaseFirestore? = app(ctx)?.let { FirebaseFirestore.getInstance(it) }
}
