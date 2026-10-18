// AG.NETWORK-0 — Concrete Network Tool (Tool Layer).
//
// Tool Layer module, sibling of :tool-android-shell. It depends on the AG Core
// Tool contract and on the Android App network stack (java.net.HttpsURLConnection)
// only. It does NOT depend on :app, :ag-runtime, any provider module, the AT
// Engine, or the shell. AG Core / Runtime / Provider never depend on it.
//
//   :tool-network
//     → :ag-core                    (Tool contract)
//     → java.net.HttpsURLConnection (Android App Realm network stack, platform)
//
// ZERO new third-party networking dependency. kotlinx-serialization / coroutines
// are already project dependencies (same versions as :tool-android-shell).
plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.androidagent.tool.network"
    compileSdk = 34

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        buildConfig = false
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    implementation(project(":ag-core"))

    // JSON argument schema / result payload (same lib + version as the shell tool).
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.0")

    // withContext(Dispatchers.IO) for the blocking HttpsURLConnection path.
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")

    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.0")
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
