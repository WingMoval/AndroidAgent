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
}