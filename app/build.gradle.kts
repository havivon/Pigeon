import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
    id("app.cash.paparazzi")
}

android {
    namespace = "com.havivon.pigeon"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.havivon.pigeon"
        minSdk = 26
        targetSdk = 34
        versionCode = 2
        versionName = "0.2.0"
    }

    // The release key stays out of the (public) repo: keystore.properties next to settings.gradle.kts,
    // with storeFile, storePassword, keyAlias and keyPassword. Without it the release build is unsigned.
    val keystore = rootProject.file("keystore.properties").takeIf { it.exists() }?.let { file ->
        Properties().apply { file.inputStream().use { load(it) } }
    }
    signingConfigs {
        if (keystore != null) {
            create("release") {
                storeFile = rootProject.file(keystore.getProperty("storeFile"))
                storePassword = keystore.getProperty("storePassword")
                keyAlias = keystore.getProperty("keyAlias")
                keyPassword = keystore.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            // R8: shrinks and obfuscates the code, drops unused resources; never debuggable
            isMinifyEnabled = true
            isShrinkResources = true
            isDebuggable = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release")
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
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }
}

ksp {
    // The schema of every database version, kept in the repo for future migrations
    arg("room.schemaLocation", "$projectDir/schemas")
}

// Biometric needs AppCompat in the app (its fingerprint dialog on Android 8 and 9), but with
// AppCompat on the test classpath Paparazzi inflates AppCompat views, which widget RemoteViews
// refuse. The JVM tests never show a biometric prompt, so they run without it.
configurations.matching { it.name.endsWith("UnitTestRuntimeClasspath") }.configureEach {
    exclude(group = "androidx.appcompat")
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.1")
    // Home-screen widgets, in Compose-like code. Same version as the user's other app.
    implementation("androidx.glance:glance-appwidget:1.1.1")

    // SMS platform and the app's own data. Same versions as the user's other app; no network libraries.
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    // Glance already brings WorkManager; the -ktx artifact adds CoroutineWorker
    implementation("androidx.work:work-runtime-ktx:2.9.0")
    // Locked chats and app lock
    implementation("androidx.biometric:biometric:1.1.0")
    // MainActivity is an AppCompatActivity: BiometricPrompt needs a FragmentActivity, and the per-app
    // language (AppCompatDelegate.setApplicationLocales) works back to Android 8 with it
    implementation("androidx.appcompat:appcompat:1.7.0")

    val composeBom = platform("androidx.compose:compose-bom:2024.06.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
}
