plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.pockettaco.salsa"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.pockettaco.salsa"
        minSdk = 29
        targetSdk = 35
        // GitHub passes in its build number so every build gets a new version (1.0.<number>).
        val build = (project.findProperty("buildNumber") as String?)?.toIntOrNull() ?: 1
        versionCode = build
        versionName = "1.0.$build"
    }

    // A fixed signing key kept in the repo. Without it, GitHub makes a new
    // random key on every build, Android refuses to install the update over
    // the old app, and you'd have to uninstall (losing all settings) each time.
    //
    // For public releases, a private key is used instead when GitHub provides it (the
    // RELEASE_KEYSTORE_* secrets). Without those secrets, builds keep using the debug key.
    val releaseStore = System.getenv("RELEASE_KEYSTORE_FILE")?.let { file(it) }?.takeIf { it.exists() }
    signingConfigs {
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
        if (releaseStore != null) create("private") {
            storeFile = releaseStore
            storePassword = System.getenv("RELEASE_KEYSTORE_PASSWORD")
            keyAlias = "taco"
            keyPassword = System.getenv("RELEASE_KEYSTORE_PASSWORD")
        }
    }

    buildTypes {
        debug {
            signingConfig = if (releaseStore != null) signingConfigs.getByName("private") else signingConfigs.getByName("debug")
        }
        release { isMinifyEnabled = false }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.documentfile:documentfile:1.0.1")
    implementation("androidx.core:core-ktx:1.15.0")
}
