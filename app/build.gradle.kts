// :app — AGA, the Android Host / UI / Composition Root.
//
// AGA depends on AG Core and on the concrete Provider. Neither of those ever
// depends on :app.
//
// This module exists to answer one question: can the frozen pure-JVM AndroidAgent
// runtime be consumed by a real Android APK? Until that is proven, the UI stays
// deliberately minimal — no Compose, no DI, no Navigation, no Room, no
// third-party HTTP client.

plugins {
    id("com.android.application")
    kotlin("android")
}

android {
    namespace = "com.androidagent.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.androidagent.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }
}

dependencies {
    implementation(project(":ag-core"))
    implementation(project(":ag-runtime"))
    implementation(project(":provider-openai-compatible"))

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.activity:activity-ktx:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
}