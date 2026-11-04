import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

/**
 * Backend base URL for the optional cloud features (threat intelligence + AI explanation).
 * Resolution order: Gradle property `cybershield.backendUrl`, then env `CYBERSHIELD_BACKEND_URL`.
 * Debug builds default to the Android emulator's alias for the host machine.
 * Release builds default to empty, which disables cloud features until a URL is configured.
 * This is a public endpoint URL, not a secret. API keys live only on the backend.
 */
fun backendUrl(default: String): String =
    (findProperty("cybershield.backendUrl") as String?)
        ?: System.getenv("CYBERSHIELD_BACKEND_URL")
        ?: default

android {
    namespace = "io.cybershield.app"
    compileSdk = 35
    defaultConfig {
        applicationId = "io.cybershield.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 3
        versionName = "0.3.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // Ship only the languages the app is translated into; drops unused library translations.
        resourceConfigurations += listOf("en", "hi", "mr")
    }

    // Release signing is configured only when all four values are supplied through the environment
    // (see docs/RELEASE.md). Without them `assembleRelease` produces app-release-unsigned.apk.
    val keystorePath = System.getenv("CYBERSHIELD_KEYSTORE_PATH")
    val keystorePassword = System.getenv("CYBERSHIELD_KEYSTORE_PASSWORD")
    val keyAlias = System.getenv("CYBERSHIELD_KEY_ALIAS")
    val keyPassword = System.getenv("CYBERSHIELD_KEY_PASSWORD")
    val canSignRelease = listOf(keystorePath, keystorePassword, keyAlias, keyPassword).all { !it.isNullOrBlank() }
    if (canSignRelease) {
        signingConfigs {
            create("release") {
                storeFile = file(keystorePath!!)
                storePassword = keystorePassword
                this.keyAlias = keyAlias
                this.keyPassword = keyPassword
            }
        }
    }

    buildTypes {
        debug {
            buildConfigField("String", "BACKEND_URL", "\"${backendUrl("http://10.0.2.2:8787")}\"")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            buildConfigField("String", "BACKEND_URL", "\"${backendUrl("")}\"")
            if (canSignRelease) signingConfig = signingConfigs.getByName("release")
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
    androidResources {
        // Generates the per-app language list (Android 13+ system settings) from values-hi / values-mr.
        generateLocaleConfig = true
    }
    testOptions {
        unitTests.isReturnDefaultValues = false
    }
    lint {
        abortOnError = true
        warningsAsErrors = false
        checkDependencies = false
        // Dependency upgrade nags are tracked manually; they must not break CI on unrelated changes.
        disable += listOf("GradleDependency", "NewerVersionAvailable", "AndroidGradlePluginVersion", "OldTargetApi")
    }
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

dependencies {
    val bom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(bom)
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.camera:camera-camera2:1.4.1")
    implementation("androidx.camera:camera-lifecycle:1.4.1")
    implementation("androidx.camera:camera-view:1.4.1")
    implementation("com.google.mlkit:barcode-scanning:17.3.0")
    // On-device OCR for user-selected screenshots. The Devanagari model also recognises Latin script.
    implementation("com.google.mlkit:text-recognition-devanagari:16.0.1")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    testImplementation("junit:junit:4.13.2")
    // android.jar ships org.json as stubs; unit tests need the real implementation.
    testImplementation("org.json:json:20240303")

    androidTestImplementation(bom)
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
}
