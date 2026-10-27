plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.gms.google-services")
}

// Signing values come ONLY from environment variables set by GitHub Actions
// (which reads them from GitHub Secrets). Nothing secret lives in this file.
val keystoreFile: String? = System.getenv("KEYSTORE_FILE")

android {
    namespace = "com.shajara.rides"
    compileSdk = 37

    defaultConfig {
        // The rider app id; the captain flavor below replaces it. Both are registered in the Firebase project.
        applicationId = "jo.shajaraone.rider"
        minSdk = 26
        targetSdk = 36
        versionCode = (System.getenv("VERSION_CODE") ?: "1").toInt()
        versionName = "1.5"
    }

    // Two apps from the same code, one flavor each (the admin dashboard is a separate web app, not part of these):
    //   rider   = jo.shajaraone.rider     (riders' app)
    //   captain = jo.shajaraone.captain   (captains' app)
    // Both use the same Firebase project and are registered in it with exactly these package names.
    // The Kotlin namespace stays com.shajara.rides on purpose: it is only the source package, not the app id.
    flavorDimensions += "app"
    productFlavors {
        create("rider") {
            dimension = "app"
            buildConfigField("boolean", "CAPTAIN_APP", "false")
        }
        create("captain") {
            dimension = "app"
            applicationId = "jo.shajaraone.captain"
            buildConfigField("boolean", "CAPTAIN_APP", "true")
        }
    }

    signingConfigs {
        create("release") {
            if (keystoreFile != null) {
                storeFile = file(keystoreFile)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (keystoreFile != null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation("androidx.core:core:1.19.1")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.work:work-runtime:2.12.0")

    implementation(platform("androidx.compose:compose-bom:2026.09.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.material3:material3")

    implementation(platform("com.google.firebase:firebase-bom:35.0.2"))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")
}
