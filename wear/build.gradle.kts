plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// App per Pixel Watch / Wear OS: stesso applicationId e stessa firma dell'app telefono (obbligatorio per parlarsi)
android {
    namespace = "it.vincenzo.tvpilot.wear"
    compileSdk = 35

    defaultConfig {
        applicationId = "it.vincenzo.tvpilot"
        minSdk = 30
        targetSdk = 34
        // Sempre UNO IN MENO dell'app telefono, letto da app/build.gradle.kts: se l'APK dell'orologio
        // finisce per sbaglio sul telefono, Android lo rifiuta (sarebbe un "downgrade") invece di sostituire TVPilot.
        val phone = rootProject.file("app/build.gradle.kts").readText()
        versionCode = Regex("versionCode = (\\d+)").find(phone)!!.groupValues[1].toInt() - 1
        versionName = Regex("versionName = \"([^\"]+)\"").find(phone)!!.groupValues[1]
    }

    val ks = System.getenv("KEYSTORE_PATH")?.let { file(it) }?.takeIf { it.exists() }
    signingConfigs {
        if (ks != null) create("release") {
            storeFile = ks
            storePassword = System.getenv("KEYSTORE_PASS")?.trim()
            keyAlias = "tvpilot"
            keyPassword = System.getenv("KEYSTORE_PASS")?.trim()
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release")
        }
    }

    buildFeatures { compose = true }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation(platform("androidx.compose:compose-bom:2024.10.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.wear.compose:compose-material:1.4.0")
    implementation("androidx.wear.compose:compose-foundation:1.4.0")
    implementation("com.google.android.gms:play-services-wearable:18.2.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
}
