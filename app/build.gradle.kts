import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    // Kotlin 2.x: Der Compose Compiler ist ein Kotlin-Plugin (Version = Kotlin-Version).
    // composeOptions.kotlinCompilerExtensionVersion gibt es dafür nicht mehr.
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

// ---------------------------------------------------------------------------------------------
// Lokale, nicht eingecheckte Konfiguration
// ---------------------------------------------------------------------------------------------

/** local.properties: SDK-Pfad, RevenueCat-Key, Paywall-Variante. */
val localProps = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use(::load)
}

/** Wert aus local.properties, sonst Gradle-Property (-Pname=…, z. B. in CI). */
fun localOrGradle(name: String): String? =
    localProps.getProperty(name) ?: (project.findProperty(name) as String?)

/**
 * Release-Signatur aus keystore.properties (Vorlage: keystore.properties.example) oder – für
 * CI – aus Umgebungsvariablen. Fehlt beides, entsteht ein unsigniertes Release-APK/AAB.
 */
val keystoreProps = Properties().apply {
    rootProject.file("keystore.properties").takeIf { it.exists() }?.inputStream()?.use(::load)
}

fun signingValue(key: String, env: String): String? =
    keystoreProps.getProperty(key) ?: System.getenv(env)?.takeIf { it.isNotBlank() }

val releaseStoreFile = signingValue("storeFile", "DEVNOTCH_KEYSTORE_FILE")?.let { rootProject.file(it) }
val hasReleaseSigning = releaseStoreFile?.exists() == true

// ---------------------------------------------------------------------------------------------

android {
    namespace = "com.frezzybuilds.devnotch"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.frezzybuilds.devnotch"
        minSdk = 26 // TYPE_APPLICATION_OVERLAY, Notification Channels, adaptive Icons
        targetSdk = 35
        versionCode = 15
        versionName = "0.9.3"

        // RevenueCat Public SDK Key (Google Play, beginnt mit "goog_").
        // local.properties: revenuecat.apiKey=goog_xxx  ·  CI: -Prevenuecat.apiKey=…
        val revenueCatKey = localOrGradle("revenuecat.apiKey") ?: "goog_REPLACE_WITH_YOUR_REVENUECAT_KEY"
        buildConfigField("String", "REVENUECAT_API_KEY", "\"$revenueCatKey\"")

        // Paywall-UI: "custom" (eigene Compose-Paywall, Standard) oder "revenuecat"
        // (RevenueCat Paywalls aus purchases-ui, im Dashboard gestaltet).
        val paywallMode = localOrGradle("revenuecat.paywall") ?: "custom"
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
            // mapping.txt (build/outputs/mapping/release/) entschlüsselt Stacktraces in der
            // Play Console – beim AAB-Upload wird sie automatisch mitgeliefert.
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
            // Debug bleibt unminifiziert: schnelle Builds, lesbare Stacktraces.
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

    buildFeatures {
        buildConfig = true
        compose = true
    }

    packaging {
        resources {
            // Doppelte Lizenzdateien aus Ktor/Coroutines/RevenueCat.
            excludes += setOf("/META-INF/{AL2.0,LGPL2.1}", "/META-INF/INDEX.LIST")
        }
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
        // Screenshot-Tests: echtes Hardware-Rendering unter Robolectric.
        unitTests.all { it.systemProperty("robolectric.pixelCopyRenderMode", "hardware") }
    }
}

// Compose Compiler (Kotlin-Plugin). Strong Skipping ist seit Kotlin 2.0.20 Standard.
composeCompiler {
    // Recomposition-Analyse bei Bedarf: ./gradlew assembleRelease -PcomposeReports=true
    // → app/build/compose_compiler/ (Stabilität von Klassen, überspringbare Composables).
    if (project.findProperty("composeReports") == "true") {
        reportsDestination = layout.buildDirectory.dir("compose_compiler")
        metricsDestination = layout.buildDirectory.dir("compose_compiler")
    }
}

dependencies {
    // --- AndroidX Basis & Lifecycle (ComposeView im WindowManager-Overlay braucht
    //     LifecycleOwner, ViewModelStoreOwner und SavedStateRegistryOwner) -----------------
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.savedstate.ktx)
    implementation(libs.androidx.activity.compose)
    // RevenueCat zieht transitiv fragment 1.1.0; ActivityResult-APIs brauchen >= 1.3.0.
    implementation(libs.androidx.fragment.ktx)

    // --- Jetpack Compose & Material 3 (Versionen über die BOM) ------------------------------
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)

    // --- Coroutines -------------------------------------------------------------------------
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)

    // --- Room (Zwischenablage, Notizen, Projekt-Shortcuts) -----------------------------------
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // --- Networking: Ktor + kotlinx-serialization (GitHub GraphQL, KI-Kosten-APIs) ----------
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.android)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.kotlinx.serialization.json)

    // --- Bilder & Farben (GitHub-Avatar, Cover-Farben im Edge-Player) ------------------------
    implementation(libs.coil.compose)
    implementation(libs.androidx.palette.ktx)
    // QR-Code für NameDrop (Kontaktkarte für iPhone-Kameras), rein lokal erzeugt.
    implementation(libs.zxing.core)

    // --- In-App-Käufe: RevenueCat SDK + Paywalls UI ------------------------------------------
    implementation(libs.revenuecat.purchases)
    implementation(libs.revenuecat.purchases.ui)

    // --- Tests (JVM/Robolectric inkl. Compose-UI- und Screenshot-Tests) ----------------------
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.ktor.client.mock)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.ui.test.junit4)
}
