plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "it.vincenzo.tvpilot"
    compileSdk = 35

    defaultConfig {
        applicationId = "it.vincenzo.tvpilot"
        minSdk = 26
        targetSdk = 35
        versionCode = 3
        versionName = "1.1.0"
    }

    // Senza chiave (secret non impostati) l'APK di release esce non firmato: serve solo a verificare la build
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

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    packaging {
        resources.excludes += setOf("META-INF/versions/9/OSGI-INF/MANIFEST.MF", "META-INF/DEPENDENCIES")
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-process:2.8.7")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation(platform("androidx.compose:compose-bom:2024.10.01"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    // WebSocket verso la TV (protocollo SSAP di webOS)
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    // SSH verso la TV: modalità sviluppatore LG (porta 9922) e root Homebrew (porta 22)
    implementation("com.github.mwiede:jsch:0.2.21")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
