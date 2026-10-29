plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Admin app «المشرف»: a separate app (jo.abnaashamal.admin). It shares the existing Firebase project and
// uses the same web/API settings as the rider app, passed in as environment variables by GitHub Actions.
// No google-services.json and no extra secret are needed. Nothing secret lives in this file.
val keystoreFile: String? = System.getenv("KEYSTORE_FILE")
fun env(name: String, fallback: String) = "\"" + (System.getenv(name)?.takeIf { it.isNotBlank() } ?: fallback) + "\""

android {
    namespace = "jo.shajaraone.admin"
    compileSdk = 37

    defaultConfig {
        applicationId = "jo.abnaashamal.admin"
        minSdk = 26
        targetSdk = 36
        versionCode = (System.getenv("VERSION_CODE") ?: "1").toInt()
        versionName = "1.0.${System.getenv("VERSION_CODE") ?: "0"}"
        buildConfigField("String", "FB_API_KEY", env("ABNAA_FIREBASE_API_KEY", "placeholder"))
        buildConfigField("String", "FB_APP_ID", env("ABNAA_RIDER_APP_ID", "1:1:android:1"))
        buildConfigField("String", "FB_PROJECT_ID", env("FIREBASE_PROJECT_ID", "shajara-rides-security-t-2632f"))
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

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation("androidx.core:core:1.19.1")
    implementation("androidx.activity:activity-compose:1.13.0")

    implementation(platform("androidx.compose:compose-bom:2026.09.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.material3:material3")

    implementation(platform("com.google.firebase:firebase-bom:35.0.2"))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.16")
    testImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
