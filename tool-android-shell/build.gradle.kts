// AG.V2-2 — Concrete Android Shell Tool (AT V3 Freeze V1).
//
// This is the ONLY AndroidAgent layer allowed to depend on the AT Engine
// (frozen boundary: AG Core / Agent Runtime / AGA Host -X-> AT implementation).
//
//   :tool-android-shell
//     ├── :ag-core                    (Tool contract)
//     ├── :at-engine                  (AT Engine domain contracts, from AT V3)
//     ├── :at-backend-adb             (ADB Backend adapter, from AT V3)
//     └── :adb-engine                 (ADB Engine + libadbd_engine.so, from AT V3)
//
// The AT V3 modules are resolved via includeBuild("../AndroidTerminal-V3")
// in settings.gradle.kts, with dependencySubstitution mapping the module
// coordinates to the V3 project modules. The AARs are built from the exact
// freeze commit (AT.PRE-EMULATOR-FREEZE-V1 = 7ccb1bf3) and cached in
// third_party/at-engine-handoff-v3/artifacts/ for provenance.
//
// Legacy AT bundle v0 (com.androidterminal:at-runtime:0.1.0) is REMOVED.
// The legacy AARs in third_party/at-engine-handoff-v0/ are retained as
// historical artifacts only (no longer resolved by Gradle).
//
// Deliberately NO dependencies on :app, :ag-runtime, or any provider module.
plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.androidagent.tool.androidshell"
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

    // AG.V2-2B: the tool now consumes AT through the unified adapter, not
    // directly. The adapter re-exports AT types (ATExecutionResult) so the
    // tool's Executor seam and result mapping continue to work.
    api(project(":integration-at"))

    // JSON argument schema / result payload.
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.0")

    // withContext(Dispatchers.IO) for the blocking AT execute path.
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")

    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.0")
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
