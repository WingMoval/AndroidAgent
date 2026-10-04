// :provider-openai-compatible — the first concrete ModelProvider integration.
//
// Dependency direction is one-way:
//     :provider-openai-compatible  ->  :ag-core
// :ag-core gains NO knowledge of this module and must never depend on it.
//
// HTTP is the JDK's java.net.http. JSON is kotlinx-serialization-json used as a
// plain tree API (JsonElement) - deliberately NO compiler serialization plugin,
// because this module maps a wire protocol by hand rather than owning a @Serializable
// DTO hierarchy.
//
// Coroutines are a production dependency HERE (not in :ag-core) solely to bridge
// HttpClient.sendAsync into the frozen `suspend ModelProvider.complete` contract
// with real cancellation propagation.
//
// No Android. No AT Core. No ADB. No JNI. No logging framework.

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
    // The frozen AG Core contract this module implements.
    implementation(project(":ag-core"))

    // JSON tree API only. No serialization compiler plugin.
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.0")

    // Minimal suspend/cancellation bridge for java.net.http.
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")

    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

// Deterministic protocol tests. Offline. No credentials. No network.
tasks.named<Test>("test") {
    useJUnitPlatform {
        // Live API tests are opt-in via the liveTest task, never the normal build.
        excludeTags("live")
    }
    testLogging {
        events("passed", "skipped", "failed")
    }
}

// Live API verification.
// Config comes from AndroidAgent's own config/provider.local.json (AG-owned),
// falling back to AG_OPENAI_* environment variables. No other tool's config is read.
//
// The root project directory is passed in explicitly so nothing depends on the
// shell's working directory and no absolute path is hardcoded in the loader.
tasks.register<Test>("liveTest") {
    description = "Runs @Tag(\"live\") tests against a real OpenAI-compatible endpoint."
    group = "verification"
    useJUnitPlatform {
        includeTags("live")
    }

    val rootProjectDir = rootProject.projectDir
    val localConfig = rootProjectDir.resolve("config/provider.local.json")

    // Friendly early failure. The authoritative resolution and validation happen
    // inside the test via ProviderConfigResolver; this only avoids a confusing
    // "no tests found" when nothing is configured at all.
    inputs.file(localConfig).optional().withPropertyName("agProviderLocalConfig")
    doFirst {
        val hasEnv = !System.getenv("AG_OPENAI_BASE_URL").isNullOrBlank() &&
            !System.getenv("AG_OPENAI_MODEL").isNullOrBlank()
        if (!localConfig.isFile && !hasEnv) {
            throw GradleException(
                "liveTest needs a provider configuration.\n" +
                    "  Preferred (AG-owned): $localConfig\n" +
                    "  Must be valid JSON declaring type=openai-compatible, with a\n" +
                    "  non-blank baseUrl and model, and must NOT still say REPLACE_ME.\n" +
                    "  Fallback: AG_OPENAI_BASE_URL + AG_OPENAI_MODEL\n" +
                    "           (+ AG_OPENAI_API_KEY when the endpoint needs auth)",
            )
        }
    }

    // The root project dir is passed explicitly so neither the loader nor the
    // tests depend on the shell's working directory or a hardcoded path.
    systemProperty("ag.provider.config", localConfig.absolutePath)

    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = true
    }
}