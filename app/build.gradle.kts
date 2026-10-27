plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.gms.google-services")
}

// Signing values come ONLY from environment variables set by GitHub Actions
// (which reads them from GitHub Secrets). Nothing secret lives in this file.
val keystoreFile: String? = System.getenv("KEYSTORE_FILE")

// DEV build only: the admin panel talks to the REAL Firebase project.
// Its settings are read from the REAL google-services.json that GitHub Actions
// writes from a Secret. If the file is absent (e.g. local), the values stay empty.
fun realFirebaseValue(key: String): String {
    val f = file("src/prod/google-services.json")
    if (!f.exists()) return ""
    val root = groovy.json.JsonSlurper().parse(f) as Map<*, *>
    val info = root["project_info"] as Map<*, *>
    val clients = root["client"] as List<*>
    val client = clients.map { it as Map<*, *> }.first { c ->
        val ci = c["client_info"] as Map<*, *>
        val aci = ci["android_client_info"] as Map<*, *>
        aci["package_name"] == "jo.shajaraone.rider"
    }
    val clientInfo = client["client_info"] as Map<*, *>
    val apiKeys = client["api_key"] as List<*>
    val firstKey = apiKeys.first() as Map<*, *>
    return when (key) {
        "project" -> info["project_id"].toString()
        "bucket" -> info["storage_bucket"]?.toString() ?: ""
        "app" -> clientInfo["mobilesdk_app_id"].toString()
        else -> firstKey["current_key"].toString()
    }
}

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

    // Two apps from the same code (dimension "app") x two environments (dimension "env"):
    //   riderProd   = jo.shajaraone.rider             (riders' app, REAL project)
    //   captainProd = jo.shajaraone.captain           (captains' app, REAL project)
    //   riderDev    = jo.shajaraone.rider.dev         (old developer build, optional)
    //   captainDev  = jo.shajaraone.captain.dev       (old developer build, optional)
    // The Kotlin namespace stays com.shajara.rides on purpose: it is only the source package, not the app id.
    flavorDimensions += listOf("app", "env")
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
        // Users' app: no admin code at all
        create("prod") {
            dimension = "env"
        }
        // Developer app: installs next to the users' app, has the admin panel
        create("dev") {
            dimension = "env"
            applicationIdSuffix = ".dev"
            versionNameSuffix = "-dev"
            buildConfigField("String", "REAL_PROJECT_ID", "\"${realFirebaseValue("project")}\"")
            buildConfigField("String", "REAL_APP_ID", "\"${realFirebaseValue("app")}\"")
            buildConfigField("String", "REAL_API_KEY", "\"${realFirebaseValue("key")}\"")
            buildConfigField("String", "REAL_STORAGE_BUCKET", "\"${realFirebaseValue("bucket")}\"")
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

    implementation(platform("com.google.firebase:firebase-bom:35.1.0"))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")
}
