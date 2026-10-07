// AndroidAgent — settings
//
// Repository roles:
//   AndroidAgent        = development source of truth
//   AndroidAgent-Public = release/export downstream only
//
// Repository policy note:
//   `google()` was deliberately absent while the project was pure JVM. It is now
//   declared because `:app` (the AGA Android host) genuinely needs the Android
//   Gradle Plugin and AndroidX. The pure-JVM modules `:ag-core` and
//   `:provider-openai-compatible` still declare no Android dependency at all,
//   so Android artifacts cannot leak into AG Core by accident.

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        google()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
        google()
        // AG.SHELL-1: repo-local Maven metadata wrapper for the verified AT Engine
        // Handoff Bundle v0. The AARs in this repo are byte-identical copies of
        // third_party/at-engine-handoff-v0/artifacts/*.aar (hashes match SHA256SUMS);
        // the POMs only describe dependency resolution and never repackage the AARs.
        maven {
            url = uri("third_party/at-engine-handoff-v0/m2")
        }
    }
}

rootProject.name = "AndroidAgent"

// AG.PROVIDER-0: the first concrete ModelProvider integration. It depends on
// :ag-core, never the reverse. No provider framework: there is exactly one
// real provider, so exactly one module.
include(":ag-core")
include(":ag-runtime")
include(":provider-openai-compatible")
include(":provider-openai-compatible-jdk")
include(":tool-android-shell")
include(":app")