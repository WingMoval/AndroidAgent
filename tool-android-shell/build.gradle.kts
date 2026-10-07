// AG.SHELL-1 — Concrete Android Shell Tool Integration.
//
// This is the ONLY AndroidAgent layer allowed to depend on the AT Engine
// (frozen boundary: AG Core / Agent Runtime / AGA Host -X-> AT implementation).
//
//   :tool-android-shell
//     → :ag-core                    (Tool contract)
//     → com.androidterminal:at-runtime (stable public contract)
//     → com.androidterminal:at-core    (transitive impl + JNI + native, via at-runtime POM)
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

    // AT Engine handoff bundle v0, resolved from the repo-local metadata wrapper.
    // at-core arrives transitively via at-runtime's POM (compile scope).
    implementation("com.androidterminal:at-runtime:0.1.0")

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