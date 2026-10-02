# AndroidAgent

AndroidAgent is the **Agent intelligence / runtime** project for future Android
autonomous interaction.

This repository is the **development source of truth** for AndroidAgent.
It currently holds a single pure-Kotlin/JVM module, `:ag-core`, with a working
JVM unit-test baseline and an implemented Agent Loop v0.

## Current status

| Item | State |
|---|---|
| Stage | `AG.0` + `AG.0.R1` — AG Core v0 runtime and async boundary freeze |
| Modules | `:ag-core` (pure Kotlin/JVM) |
| Tests | JUnit 5, **39 passing**, via `./gradlew :ag-core:test` |
| Agent Loop v0 | **Implemented**: Session/Run, ModelProvider + Tool contracts, Tool Registry, allow/deny policy, events, Run outcomes |
| `AgentCore.run` | **`suspend fun`** — AG Core ships no blocking wrapper |
| Production dependencies | **None** beyond the Kotlin standard library |
| Real model provider | **Not implemented.** A real provider vertical slice is a candidate direction, not a decided step. |
| Next step | **PENDING ARCHITECTURE REVIEW** — not decided, and not to be assumed `AG.0.1` or `AG.1` |
| Android Tools | **Not implemented.** Belongs outside AG Core. |

Nothing beyond the table above is implemented. Any capability table must not
claim features that do not exist.

## Layering

AG Core is the product-independent Agent runtime. It reaches Android execution
only through tools.

Runtime call flow (NOT a code dependency graph):

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

Code dependency runs the other way — implementations depend on AG Core
contracts, and AG Core depends on none of them:

```text
Concrete Tool     --implements-->  Tool contract    (AG Core)
Concrete Provider --implements-->  ModelProvider    (AG Core)
AG Core  -X->  concrete implementations
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
AG.BASELINE-0   project init + baseline freeze        DONE
   |
   v
AG.0            AG Core v0 architecture + minimal Agent Loop    DONE
   |
   v
AG.0.R1         runtime async boundary freeze          DONE
   |
   v
NEXT            PENDING ARCHITECTURE REVIEW
```

The next step is not yet decided and must not be assumed to be AG.0.1 or AG.1.

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

**AG Core has no production dependency beyond the Kotlin standard library.**

```text
runtimeClasspath = kotlin-stdlib
testRuntime      = kotlin-stdlib + kotlinx-coroutines-core + JUnit 5
```

`Tool.execute`, `ModelProvider.complete` and `AgentCore.run` are `suspend`, but
`suspend` is a Kotlin language feature, not a library dependency. AG Core
deliberately ships **no blocking wrapper** — choosing a thread, dispatcher, or
lifecycle belongs to the host, which can bridge with `runBlocking { }` if it
needs to block. Coroutines are therefore needed only to drive suspending calls
from plain JVM tests.

JSON/serialization, DI, HTTP clients, and logging frameworks are **not** added.
They become an explicit decision at the step where a real provider needs them.