# AndroidAgent

AndroidAgent is the **Agent intelligence / runtime** project for future Android
autonomous interaction.

This repository is the **development source of truth** for AndroidAgent.
It is currently at baseline stage: a buildable, testable, version-controlled
skeleton with a single pure-Kotlin/JVM module. **No Agent behaviour exists yet.**

## Current status

| Item | State |
|---|---|
| Stage | `AG.BASELINE-0` — project initialization and baseline freeze |
| Modules | `:ag-core` (pure Kotlin/JVM) |
| Tests | JUnit 5, executed via `./gradlew :ag-core:test` |
| Agent runtime | **Not implemented.** Belongs to `AG.0`. |

Nothing in this repository is implemented beyond the build baseline. Any
capability table must not claim features that do not exist.

## Layering

AG Core is the product-independent Agent runtime. It reaches Android execution
only through tools.

```text
            AGA
             |
             v
          AG Core
             |
             v
        Tool Registry
             |
             v
       Concrete Tools
             |
      Android Tools
             |
             v
       Stable AT Core
             |
             v
          Android
```

A future CLI/TUI frontend reuses the same Core:

```text
AGC  --->  AG Core
```

## Hard rules

```text
AG Core does not depend on AT Core.
AG Core does not contain Android execution protocol code.
```

Concretely, `ag-core` must never import or reference:

```text
android.*            androidx.*        com.androidterminal.*
JNI                  ADB               AT Core
Activity             Context           View / Compose
AdbManager           AdbClient         shell_v2 / TLS
```

This is not a convention only. `:ag-core` is built with the `kotlin("jvm")`
plugin, so an `android.*` import is a **compile error**, and `google()` is
deliberately absent from `settings.gradle.kts`.

`Android Tools` is the only layer permitted to depend on both AG Core and
AT Core. It is not AG Core.

## Roadmap

```text
AG.BASELINE-0   project init + baseline freeze        DONE (this commit)
   |
   v
AG.0            AG Core v0 architecture + minimal Agent Loop    NEXT
   |
   v
AG.1            AGA v0
```

Not planned here, and deliberately not scaffolded: `:app`, `:android-tools`,
`:agc`, `:at-core`.

## Build

```bash
./gradlew projects
./gradlew :ag-core:test
./gradlew build
```

Requires a JDK 17 or newer toolchain (Java 17 bytecode target). Tested on
JDK 21 with Gradle 8.7 and Kotlin 2.1.0.

## Repository roles

```text
AndroidAgent         = development source of truth
AndroidAgent-Public  = release / export downstream only
```

Changes flow one way: development directory, then an explicit release step,
then `-Public`. Never develop directly in `-Public`, never back-port fixes from
`-Public` into development, never treat both as source of truth.

The first `AndroidAgent-Public` sync should happen at a real release-ready
node, such as AG Core v0.1.

## Dependencies

At baseline the module depends on nothing but the Kotlin standard library.

```text
runtimeClasspath = kotlin-stdlib
testRuntimeClasspath = kotlin-stdlib + JUnit 5
```

Coroutines and JSON/serialization are **not** added yet. They are an explicit
`AG.0` dependency decision, not a baseline prerequisite.