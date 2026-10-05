// :ag-runtime — Host-neutral, runnable Agent lifecycle.
//
// This is NOT a second intelligence core. It is AG Core plus the host-neutral
// run/session lifecycle that a frontend (AGA today, AGC later) would otherwise
// have to re-implement.
//
// Hard boundary, enforced by the dependency list below:
//   pure Kotlin/JVM · Android-free · UI-free · provider-implementation-free
//   transport-free · HTTP-free · JSON-free
//
// It knows only AG Core types. It has never heard of OpenAI, EMG, API keys or
// Android.

plugins {
    kotlin("jvm")
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    // The only production dependency. Everything else is the Kotlin stdlib.
    implementation(project(":ag-core"))

    // `suspend` is a Kotlin language feature, so no coroutines library is needed
    // in production. Tests drive coroutines.
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.named<Test>("test") {
    useJUnitPlatform {
        excludeTags("live")
    }
    testLogging {
        events("passed", "skipped", "failed")
    }
}