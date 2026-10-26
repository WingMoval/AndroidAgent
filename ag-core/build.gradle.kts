// AG Core - pure Kotlin/JVM module.
//
// Hard boundary: this module is NOT an Android library and NOT an Android
// application. It must stay compilable and testable on a plain JVM.
//
// AG.0.R1: AG Core has NO production dependency beyond the Kotlin standard
// library. The Tool, ModelProvider, and AgentCore contracts are `suspend`, but
// `suspend` is a Kotlin language feature, not a library dependency. AG Core
// deliberately provides no blocking wrapper: choosing a thread, dispatcher, or
// lifecycle is the host's decision. kotlinx-coroutines is therefore TEST-ONLY,
// used to drive suspending calls from JVM tests.
//
// No AndroidX. No ADB. No JNI. No serialization library. No DI. No HTTP client.
// No logging framework. No reactive framework.

plugins {
    kotlin("jvm")
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        // Target Java 17 bytecode.
        // No `jvmToolchain(17)`: only a JDK 21 runtime is installed locally and
        // toolchain auto-provisioning would add a network dependency to every
        // build. Bytecode target is what actually pins the language level here.
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    // Production: the interrupt closeout (0G.BLOCKER-FIX-1) uses withContext(
    // NonCancellable) to shield the durable synthetic ToolResult commit from the
    // enclosing cancelled context. This is the MINIMAL coroutine API needed; it
    // does not change AG Core's Android-free / pure-JVM nature.
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")

    // TEST ONLY. Needed to call `suspend` functions from plain JVM test methods.
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")

    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
    }
}