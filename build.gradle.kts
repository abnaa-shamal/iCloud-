// Versions checked on 2026-10-22 against the official release pages.
plugins {
    id("com.android.application") version "9.5.0" apply false
    // AGP 9 has Kotlin built in; this only pins the Kotlin version (official AGP 9.4 setup).
    id("org.jetbrains.kotlin.android") version "2.4.10" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.10" apply false
    id("com.google.gms.google-services") version "4.4.4" apply false
}
