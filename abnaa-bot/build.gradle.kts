// Same versions as the Abnaa Al-Shamal project (checked there on 2026-10-22).
plugins {
    id("com.android.application") version "9.4.0" apply false
    // AGP 9 has Kotlin built in; this only pins the Kotlin version.
    id("org.jetbrains.kotlin.android") version "2.4.10" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.10" apply false
}
