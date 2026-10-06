// Top-level build file.
plugins {
    id("com.android.application") version "8.5.0" apply false
    id("org.jetbrains.kotlin.android") version "1.9.24" apply false
    // Only applied by :app when app/google-services.json exists (see app/build.gradle.kts).
    id("com.google.gms.google-services") version "4.4.2" apply false
}
