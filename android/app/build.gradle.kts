plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.autocrop.scanner"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.autocrop.scanner"
        minSdk = 23
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
    }

    // Keep Java and Kotlin bytecode targets aligned. This prevents
    // Gradle's "Inconsistent JVM-target compatibility" compile failure.
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.activity:activity-ktx:1.9.3")
    implementation("com.google.android.gms:play-services-mlkit-document-scanner:16.0.0")
}
