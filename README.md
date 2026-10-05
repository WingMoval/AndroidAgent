# AndroidAgent

AndroidAgent is the **Agent intelligence / runtime** project for future Android
autonomous interaction.

This repository is the **development source of truth** for AndroidAgent.
It holds five modules: the Agent runtime (`:ag-core`, `:ag-runtime`), the
verified OpenAI-compatible provider (`:provider-openai-compatible` and its JDK
transport), and `:app` (the AGA Android host).

## Current status

| Item | State |
|---|---|
| Stage | `AG.0` / `AG.PROVIDER-0` **FROZEN** · `AGA-0` done · `AGA-0.ARCH` done |
| Modules | `:ag-core`, `:ag-runtime`, `:provider-openai-compatible`, `:provider-openai-compatible-jdk`, `:app` |
| Tests | AG Core **41**, Agent Runtime **8**, Provider offline **46**, Provider live **6 verified** |
| AG Core v0 | **FROZEN** — Agent Loop, Session/Run, ModelProvider + Tool contracts, Tool Registry, allow/deny policy, events, Run outcomes |
| Agent Runtime | **Implemented** — host-neutral session selection and single-active-run lifecycle |
| OpenAI-compatible Provider | **IMPLEMENTED / VERIFIED** against a real endpoint |
| Real Model → Tool → Model loop | **VERIFIED** end to end (not fixture-only) |
| AGA Android host | **IMPLEMENTED** — builds, installs, launches, real chat **PASS** on device |
| Strict real multi-turn memory | **PENDING** — blocked while EMG is busy |
| AGA UI does not own Session / Run lifecycle | **true** — that lives in `:ag-runtime` |
| Android Tools / AT integration / AGA beyond the minimal host | **NOT STARTED** |
| Next step | **PENDING ARCHITECTURE STEP DESIGN** |

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
AG.BASELINE-0   project init + baseline freeze              DONE
   |
   v
AG.0            AG Core v0 architecture + Agent Loop         DONE / FROZEN
   |
   v
AG.0.R1         runtime async boundary freeze               DONE
   |
   v
AG.PROVIDER-0   verified OpenAI-compatible provider         DONE / FROZEN
   |
   v
AGA-0           minimal Android chat host                   DONE
   |
   v
AGA-0.ARCH      agent runtime / UI physical separation      DONE
   |
   v
NEXT            PENDING ARCHITECTURE STEP DESIGN
```

Not planned here, and deliberately not scaffolded: `:agc`, `:android-tools`,
`:at-core`, `:skills`.

## Provider configuration

The first concrete provider is configured by AndroidAgent itself, never by
reading another tool's configuration.

```text
config/provider.example.json    tracked, no secret
config/provider.local.json      ignored, holds the real key
        |
        v
OpenAiCompatibleConfigLoader -> ProviderConfigResolver -> OpenAiCompatibleConfig
```

Resolution uses **one** complete source per run: the local JSON if usable,
otherwise `AG_OPENAI_*` environment variables. Fields are never mixed across
sources.

```bash
# live verification (requires a filled config/provider.local.json)
./gradlew :provider-openai-compatible:liveTest
```

`config/provider.local.json` is development/bootstrap configuration only. It is
not a production credential store.

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
:ag-core  runtimeClasspath = kotlin-stdlib
          testRuntime      = kotlin-stdlib + kotlinx-coroutines-core + JUnit 5

:provider-openai-compatible
          = kotlin-stdlib + project(:ag-core)
          + kotlinx-serialization-json
          + kotlinx-coroutines-core
          + JDK java.net.http (no OkHttp / Retrofit)
```

`Tool.execute`, `ModelProvider.complete` and `AgentCore.run` are `suspend`, but
`suspend` is a Kotlin language feature, not a library dependency. AG Core
deliberately ships **no blocking wrapper** — choosing a thread, dispatcher, or
lifecycle belongs to the host, which can bridge with `runBlocking { }` if it
needs to block. Coroutines are therefore needed only to drive suspending calls
from plain JVM tests.

AG Core does not depend on JSON/serialization, HTTP clients, DI, or logging
frameworks.

The concrete OpenAI-compatible Provider, which lives **outside** AG Core, does
use `kotlinx-serialization-json` and the JDK `java.net.http.HttpClient`.

No DI framework and no logging framework has been introduced anywhere.