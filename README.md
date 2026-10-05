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
| Stage | `AG.0` / `AG.PROVIDER-0` / `AGA-0` **all DONE / FROZEN** |
| Modules | `:ag-core`, `:ag-runtime`, `:provider-openai-compatible`, `:provider-openai-compatible-jdk`, `:app` |
| Tests | AG Core **41**, Agent Runtime **8**, Provider offline **46**, Provider live **6 verified** |
| AG Core v0 | **FROZEN** (tag `ag-0`) — Agent Loop, Session/Run, ModelProvider + Tool contracts, Tool Registry, allow/deny policy, events, Run outcomes |
| Agent Runtime | **IMPLEMENTED / FROZEN** (in tag `ag-aga-0`) — host-neutral session selection and single-active-run lifecycle |
| OpenAI-compatible Provider | **IMPLEMENTED / VERIFIED / FROZEN** (tag `ag-provider-0`) against a real endpoint |
| Real Model → Tool → Model loop | **VERIFIED** end to end (not fixture-only) |
| AGA Android host | **IMPLEMENTED / FROZEN** (tag `ag-aga-0`) — builds, installs, launches |
| Real Android chat | **PASS** on a real device against a real endpoint |
| Strict real multi-turn memory | **PENDING** — see note below |
| AGA UI owns Session / Run lifecycle | **No** — that lives in `:ag-runtime` |
| Android Tools | **NOT STARTED** |
| AT integration | **NOT STARTED** — integration mechanism not frozen |
| Next step | **Android Tool / AT integration architecture design** (`AG.SHELL-0.DESIGN`) — not started |

**On "strict real multi-turn memory"**: real Android chat already **PASS** — a real
request reached a real endpoint and the model answered. What is still pending is
narrower: proving that a Round-2 answer *semantically depends* on a fact
introduced only in Round 1.

## Layering

AG Core is the product-independent **Agent semantic core**. It reaches Android
execution only through tools.

Terminology, used consistently below:

```text
AG Core        = Agent semantics  (loop, session/run, history, contracts, events)
Agent Runtime  = Host-neutral runnable lifecycle  (which session, one run at a time)
```

Runtime call flow (NOT a code dependency graph):

```text
              AGA
               |
               v
        Agent Runtime
               |
               v
            AG Core
               |
               v
         Tool Registry
               |
               v
  Concrete Android Tool
        /            \
       v              v
 Android APIs   AndroidTerminal
                execution boundary
                       |
                       v
                    Android
```

A future CLI/TUI frontend shares the same runtime path:

```text
              AGC
               |
               v
        Agent Runtime
               |
               v
            AG Core
```

Code dependency runs the other way — implementations depend on AG Core
contracts, and AG Core depends on none of them:

```text
Concrete Tool     --implements-->  Tool contract    (AG Core)
Concrete Provider --implements-->  ModelProvider    (AG Core)
AG Core  -X->  concrete implementations
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
deliberately absent from the pure-JVM modules' repository list.

Concrete Android Tools are the only AndroidAgent execution layer allowed to
connect to an Android-specific execution backend. They may use the Android APIs
directly, or connect to the AndroidTerminal execution boundary, or combine both.

The exact AT integration mechanism is **not frozen**; it is deferred to
`AG.SHELL-0.DESIGN`. Do not assume a direct code dependency on AT Core.

## Roadmap

```text
AG.BASELINE-0   project init + baseline freeze              DONE / FROZEN
   |
   v
AG.0            AG Core v0 architecture + Agent Loop         DONE / FROZEN  (ag-0)
   |
   v
AG.PROVIDER-0   verified OpenAI-compatible provider         DONE / FROZEN  (ag-provider-0)
   |
   v
AGA-0           DONE / FROZEN  (ag-aga-0)
   ├─ Android Host (:app)
   ├─ Android-compatible Provider Transport (HttpsURLConnection)
   └─ Agent Runtime / UI physical separation (:ag-runtime)
   |
   v
NEXT            Android Tool / AT integration architecture design
                AG.SHELL-0.DESIGN — design only, not started
```

Not planned here, and deliberately not scaffolded: `:agc`, `:android-tools`,
`:at-core`, `:skills`, `:provider-openai-compatible-android`.

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
# live verification through the JDK transport
# (requires a filled config/provider.local.json)
./gradlew :provider-openai-compatible-jdk:liveTest
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

Architecture-level boundaries, per module. Transitive AndroidX artifacts are
not listed.

```text
:ag-core
  pure JVM · stdlib only in production
  test: + kotlinx-coroutines-core + JUnit 5

:ag-runtime
  project(:ag-core) + kotlin-stdlib
  Android-free · no provider implementation · no HTTP · no JSON

:provider-openai-compatible
  project(:ag-core) + kotlinx-serialization-json + kotlinx-coroutines-core
  holds the OpenAiHttpTransport **contract** only
  java.net.http = NO · Android = NO

:provider-openai-compatible-jdk
  project(:provider-openai-compatible)
  JDK java.net.http — the only JDK HTTP implementation

:app
  Android frontend
  project(:ag-core) + project(:ag-runtime) + project(:provider-openai-compatible)
  AndroidOpenAiHttpTransport (HttpsURLConnection)
  must NOT depend on :provider-openai-compatible-jdk
```

`Tool.execute`, `ModelProvider.complete` and `AgentCore.run` are `suspend`, but
`suspend` is a Kotlin language feature, not a library dependency. AG Core
deliberately ships **no blocking wrapper** — choosing a thread, dispatcher, or
lifecycle belongs to the host, which can bridge with `runBlocking { }` if it
needs to block. Coroutines are therefore needed only to drive suspending calls
from plain JVM tests.

No OkHttp / Retrofit, no DI framework, and no logging framework has been
introduced anywhere.