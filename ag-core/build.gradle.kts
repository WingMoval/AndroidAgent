// AG Core - pure Kotlin/JVM module.
//
// Hard boundary: this module is NOT an Android library and NOT an Android
// application. It must stay compilable and testable on a plain JVM.
//
// Dependencies here are deliberately minimal at AG.BASELINE-0.
// No AndroidX. No ADB. No JNI. No coroutines. No serialization library yet.

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