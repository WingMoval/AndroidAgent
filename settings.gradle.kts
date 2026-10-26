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
        // AG.AT-V4-INTEGRATION-1: repo-local Maven for the AT V4 Consumer Freeze bundle.
        // AARs/JARs from AT V4 freeze commit 7216d0e (AT.V4-CONSUMER-FREEZE-V1).
        // See third_party/at-v4-consumer-freeze-v1/MANIFEST.md.
        maven {
            url = uri("third_party/at-v4-consumer-freeze-v1/m2")
        }
        // LEGACY (V3, superseded): retained for rollback reference only.
        // maven { url = uri("third_party/at-consumer-freeze-v1/m2") }
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
// AG.NETWORK-0: second Tool Layer module (network fetch), sibling of the shell tool.
include(":tool-network")
// AG.FILE-0: third Tool Layer module (APP_SELF file READ family), sibling of the others.
include(":tool-file")
// AG.V2-2B: AG AT Execution Adapter — unified AT consumption point.
include(":integration-at")
include(":app")