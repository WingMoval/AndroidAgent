// AG.FILE-0 — Concrete File Tool (Tool Layer), READ family v0.
//
// Tool Layer module, sibling of :tool-android-shell and :tool-network. It depends
// on the AG Core Tool contract only. It uses the platform java.nio.file (NIO.2,
// available from API 26 = minSdk) for bounded reads, canonical paths, and symlink
// resolution. It does NOT depend on :app, :ag-runtime, any provider module, the
// AT Engine, the shell, SAF/ContentResolver, or any file backend framework.
// AG Core / Runtime / Provider never depend on it.
//
//   :tool-file
//     → :ag-core        (Tool contract)
//     → java.nio.file   (platform NIO.2, API 26+)
//
// ZERO new third-party dependency. kotlinx-serialization / coroutines are already
// project dependencies (same versions as :tool-network).
plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.androidagent.tool.file"
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

    // JSON argument schema / result payload (same lib + version as :tool-network).
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.0")

    // withContext(Dispatchers.IO) for the blocking NIO.2 file path.
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")

    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.0")
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
