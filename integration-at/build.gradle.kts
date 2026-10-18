// AG.V2-2B — AG AT Execution Adapter.
//
// This is the AG-side unified AT consumption point. It sits between AG Concrete
// Tools and the AT Engine, providing a single, thin, backend-neutral execution
// abstraction.
//
//   :tool-android-shell  →  AtExecutionAdapter  →  ATEngine  →  ADB Backend  →  AdbEngine  →  adbd
//   (future tools)        →  AtExecutionAdapter  →  ATEngine  →  Local Backend  →  Local PTY
//
// The adapter:
//   1. Holds the AT Engine composition (one-shot + persistent backends).
//   2. Exposes AT's current Freeze V1 capability (one-shot, persistent, raw I/O,
//      write, resize, Ctrl-C, lifecycle, multi-session, Local + ADB).
//   3. Isolates AT Contract DRAFT from other AG code.
//   4. Lets future Concrete Tools share the same AT integration without re-
//      integrating AT.
//
// AG Core / AG Runtime do NOT depend on this module. Only :app (composition
// root) and :tool-android-shell (concrete AT consumer) do.
plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.androidagent.integration.at"
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
}

dependencies {
    // AT V3 Freeze V1 consumer modules (resolved via includeBuild in
    // settings.gradle.kts). The adapter re-exports AT types so consumers
    // (tool-android-shell, :app) can access them transitively.
    api("com.at.engine:at-engine")
    api("com.at.backend.adb:at-backend-adb")
    api("com.at.backend.local:at-backend-local")
    api("com.adb.engine:adb-engine")

    // forContext / Dispatchers.IO.
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")

    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
