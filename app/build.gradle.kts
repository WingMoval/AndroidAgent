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

        // AG.SHELL-1: the AT Engine handoff bundle v0 is arm64-v8a only, so the
        // final APK carries exactly that ABI (v0 product constraint).
        ndk {
            abiFilters += "arm64-v8a"
        }

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // AG.SHELL-1: native packaging must match the verified AT consumers so
    // System.loadLibrary("androidterminaladb") resolves the same way.
    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }

    buildFeatures {
        buildConfig = true
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
    implementation(project(":tool-android-shell"))
    // AG.NETWORK-0: second Tool Layer tool (network fetch).
    implementation(project(":tool-network"))
    // AG.FILE-0: third Tool Layer tool (APP_SELF file READ family).
    implementation(project(":tool-file"))

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.activity:activity-ktx:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")

    // AG.DEBUG-0: debug bridge JSONL wire format (same lib the provider uses).
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.0")

    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")

    // AGENT-0.PREFLIGHT: JVM unit tests for the Host-owned Agent Instructions
    // identity and the minimal Agent Trace (collector / formatter / redaction).
    // These exercise the pure trace logic against AG Core's event contract and do
    // not touch Android, the AT Engine, or any live endpoint.
    testImplementation(project(":ag-core"))
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}