// AndroidAgent — settings
//
// Repository roles:
//   AndroidAgent        = development source of truth
//   AndroidAgent-Public = release/export downstream only
//
// Repository policy note:
//   `google()` is deliberately NOT declared. AndroidAgent currently has no
//   Android dependency and must not acquire one implicitly. If a future module
//   genuinely needs AndroidX, that is an explicit, reviewed decision — not a
//   default that is already sitting in the repository list.

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
    }
}

rootProject.name = "AndroidAgent"

// AG.BASELINE-0 scope: :ag-core only.
// Not created here, by decision: :app (AGA), :android-tools, :agc, :at-core.
include(":ag-core")