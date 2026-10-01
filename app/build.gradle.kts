import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

/**
 * Release-Signatur aus keystore.properties (nicht eingecheckt, Vorlage: keystore.properties.example)
 * oder – für CI – aus Umgebungsvariablen. Fehlt beides, entsteht ein unsigniertes Release-APK/AAB.
 */
val keystoreProps = Properties().apply {
    rootProject.file("keystore.properties").takeIf { it.exists() }?.inputStream()?.use(::load)
}

fun signingValue(key: String, env: String): String? =
    keystoreProps.getProperty(key) ?: System.getenv(env)?.takeIf { it.isNotBlank() }

val releaseStoreFile = signingValue("storeFile", "DEVNOTCH_KEYSTORE_FILE")?.let { rootProject.file(it) }
val hasReleaseSigning = releaseStoreFile?.exists() == true

android {
    namespace = "com.frezzybuilds.devnotch"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.frezzybuilds.devnotch"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"

        // RevenueCat Public SDK Key (Google Play, beginnt mit "goog_"). Echter Key gehört in
        // local.properties (nicht eingecheckt): revenuecat.apiKey=goog_xxx
        // oder als Gradle-Property -Prevenuecat.apiKey=… (z. B. in CI).
        val localProps = Properties().apply {
            rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use(::load)
        }
        val revenueCatKey = localProps.getProperty("revenuecat.apiKey")
            ?: (project.findProperty("revenuecat.apiKey") as String?)
            ?: "goog_REPLACE_WITH_YOUR_REVENUECAT_KEY"
        buildConfigField("String", "REVENUECAT_API_KEY", "\"$revenueCatKey\"")

        // Paywall-UI: "custom" (eigene Compose-Paywall, Standard) oder "revenuecat"
        // (RevenueCat Paywalls aus purchases-ui, im Dashboard gestaltet).
        val paywallMode = localProps.getProperty("revenuecat.paywall")
            ?: (project.findProperty("revenuecat.paywall") as String?)
            ?: "custom"
        buildConfigField("String", "PAYWALL_MODE", "\"$paywallMode\"")
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = releaseStoreFile
                storePassword = signingValue("storePassword", "DEVNOTCH_KEYSTORE_PASSWORD")
                keyAlias = signingValue("keyAlias", "DEVNOTCH_KEY_ALIAS")
                keyPassword = signingValue("keyPassword", "DEVNOTCH_KEY_PASSWORD")
                enableV1Signing = false // minSdk 26: v2/v3 genügen
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        release {
            // R8: Code verkleinern/optimieren/obfuskieren, ungenutzte Ressourcen entfernen.
            // mapping.txt (build/outputs/mapping/release/) zum Entschlüsseln von Stacktraces
            // in der Play Console hochladen – beim AAB-Upload geschieht das automatisch.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = if (hasReleaseSigning) signingConfigs.getByName("release") else null
            // Native Debug-Symbole fürs Play-Console-Crash-Reporting (aus Bibliotheken).
            ndk { debugSymbolLevel = "SYMBOL_TABLE" }
        }
        debug {
            // Debug bleibt unminifiziert für schnelle Builds und lesbare Stacktraces.
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    testOptions {
        unitTests.isIncludeAndroidResources = true
        // Screenshot-Tests: echtes Hardware-Rendering unter Robolectric (für captureToImage).
        unitTests.all { it.systemProperty("robolectric.pixelCopyRenderMode", "hardware") }
    }
    buildFeatures {
        buildConfig = true
        compose = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.savedstate.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.android)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.coil.compose)
    implementation(libs.androidx.palette.ktx)
    implementation(libs.revenuecat.purchases)
    implementation(libs.revenuecat.purchases.ui)
    implementation(libs.androidx.fragment.ktx)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.ktor.client.mock)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.ui.test.junit4)
}
