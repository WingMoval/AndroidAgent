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
    // The AG Core types (Session/Run semantics, provider/tool contracts).
    implementation(project(":ag-core"))

    // RUN-003 (AG.RUN-CONTROL-1.DETERMINISTIC.FIX): the Runtime owns a REAL
    // structured execution-child Job for each Run and cancels it on a user Stop.
    // This is a real production consumer of kotlinx-coroutines (Job ownership +
    // cancellation + the caller-active conversion guard), NOT future scaffolding:
    // the host-neutral run lifecycle needs a cancellable child and the standard
    // coroutine API to check whether the send caller is still active.
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")

    // TopicSession durable persistence (AG.TOPIC-SESSION-PERSISTENCE-IMPLEMENTATION-0D):
    // the Message persistence serializer uses the SAME kotlinx-serialization-json
    // tree API (JsonElement) the provider modules already use — deliberately NO
    // serialization compiler plugin, NO new JSON stack.
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.0")

    // Tests drive coroutines.
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    // kotlin.test assertions bridge to the JUnit 5 engine (same pattern as the
    // other modules' tests).
    testImplementation(kotlin("test-junit5"))
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