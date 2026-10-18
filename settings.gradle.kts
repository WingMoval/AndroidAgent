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
// AG.NETWORK-0: second Tool Layer module (network fetch), sibling of the shell tool.
include(":tool-network")
// AG.FILE-0: third Tool Layer module (APP_SELF file READ family), sibling of the others.
include(":tool-file")
// AG.V2-2B: AG AT Execution Adapter — unified AT consumption point.
include(":integration-at")
include(":app")

// AG.V2-2: AndroidTerminal V3 Freeze V1 consumer modules.
// The V3 source tree is read-only (D:\codebank\AndCode\AndroidTerminal-V3);
// the AARs are built from the exact freeze commit and stored in
// third_party/at-engine-handoff-v3/artifacts/. The Gradle includeBuild below
// resolves :at-engine / :at-backend-adb / :adb-engine from those AARs via a
// dependency substitution, so AG V2 never copies AT source.
includeBuild("../AndroidTerminal-V3") {
    dependencySubstitution {
        substitute(module("com.adb.engine:adb-engine")).using(project(":adb-engine"))
        substitute(module("com.at.engine:at-engine")).using(project(":at-engine"))
        substitute(module("com.at.backend.adb:at-backend-adb")).using(project(":at-backend-adb"))
        substitute(module("com.at.backend.local:at-backend-local")).using(project(":at-backend-local"))
    }
}