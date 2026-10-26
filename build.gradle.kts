// AndroidAgent — root build file
//
// Plugins are declared here with `apply false` and applied per module.
//
// `:ag-core` and `:provider-openai-compatible` are pure Kotlin/JVM and apply
// only `kotlin("jvm")`. Only `:app` (the AGA Android host) applies the Android
// plugins, which keeps Android artifacts out of the pure-JVM modules.

plugins {
    kotlin("jvm") version "2.1.0" apply false
    kotlin("android") version "2.1.0" apply false
    id("com.android.application") version "8.5.2" apply false
    // AG.TOPIC-SESSION-PERSISTENCE-IMPLEMENTATION-0E: Room compiler. KSP
    // 2.1.0-1.0.29 is the release pinned to the project's Kotlin 2.1.0
    // (KSP is versioned per Kotlin release; no Kotlin/AGP/Gradle upgrade).
    id("com.google.devtools.ksp") version "2.1.0-1.0.29" apply false
}