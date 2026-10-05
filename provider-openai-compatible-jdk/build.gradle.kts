// :provider-openai-compatible-jdk — JDK implementation of OpenAiHttpTransport.
//
// This module exists because the OpenAI-compatible Provider now has two real
// consumers on two runtimes, and `java.net.http` only exists on the JVM.
// AG Core and :provider-openai-compatible stay platform-neutral; only this module
// and the Android host's transport touch a concrete HTTP stack.
//
// Only a JVM host, the Provider's live tests, or a future CLI should depend on
// this module. An Android consumer must NOT.

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
    implementation(project(":provider-openai-compatible"))

    // Bridges java.net.http's sendAsync into the frozen `suspend` transport
    // contract. `implementation` on the provider module is not transitive.
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")

    // Test fixtures build response bodies with the JSON tree API.
    testImplementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.0")

    // The integration tests drive the real AG Core loop through this transport,
    // so they need the AG Core types directly. The provider module keeps
    // :ag-core as `implementation`, which is intentionally not transitive.
    testImplementation(project(":ag-core"))

    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

// Live verification drives the real JDK transport against a real endpoint.
tasks.named<Test>("test") {
    useJUnitPlatform {
        excludeTags("live")
    }
    testLogging {
        events("passed", "skipped", "failed")
    }
}

tasks.register<Test>("liveTest") {
    description = "Runs @Tag(\"live\") tests through the JDK transport against a real endpoint."
    group = "verification"
    useJUnitPlatform {
        includeTags("live")
    }
    val localConfig = rootProject.projectDir.resolve("config/provider.local.json")
    val hasEnv = !System.getenv("AG_OPENAI_BASE_URL").isNullOrBlank() &&
        !System.getenv("AG_OPENAI_MODEL").isNullOrBlank()
    inputs.file(localConfig).optional().withPropertyName("agProviderLocalConfig")
    doFirst {
        if (!localConfig.isFile && !hasEnv) {
            throw GradleException(
                "liveTest needs a provider configuration.\n" +
                    "  Preferred (AG-owned): $localConfig\n" +
                    "  Fallback: AG_OPENAI_BASE_URL + AG_OPENAI_MODEL\n" +
                    "           (+ AG_OPENAI_API_KEY when the endpoint needs auth)",
            )
        }
    }
    systemProperty("ag.provider.config", localConfig.absolutePath)
    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = true
    }
}