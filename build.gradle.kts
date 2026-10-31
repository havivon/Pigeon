plugins {
    id("com.android.application") version "8.5.2" apply false
    id("org.jetbrains.kotlin.android") version "1.9.24" apply false
    // Room's annotation processor
    id("com.google.devtools.ksp") version "1.9.24-1.0.20" apply false
    // Renders the real Compose screens to PNG on the PC (same engine as Android Studio previews)
    id("app.cash.paparazzi") version "1.3.4" apply false
}
