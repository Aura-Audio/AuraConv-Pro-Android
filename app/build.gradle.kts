plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.auraconv.pro"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.auraconv.pro"
        minSdk = 24          // FIX: raised from 21 → 24 for reliable Chromium WebView
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    // WebViewAssetLoader for secure local-file serving (AudioWorklet requirement)
    implementation("androidx.webkit:webkit:1.12.0")

    // Core AndroidX
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")

    // FIX: ConstraintLayout was missing — required by activity_main.xml
    implementation("androidx.constraintlayout:constraintlayout:2.2.0")

    // Activity Result API for modern permission handling
    implementation("androidx.activity:activity-ktx:1.9.3")
}
