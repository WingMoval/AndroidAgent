# AndroidAgent — development rules

## Repository roles

```text
AndroidAgent         = development source of truth
AndroidAgent-Public  = release / export downstream only
```

Flow is one-directional:

```text
development directory  ->  explicit Release Step  ->  *-Public
```

Forbidden:

- developing directly in `-Public`
- fixing a bug in `-Public` and copying it back into development
- treating both directories as source of truth

Do not create `-Public` for symmetry. Create it at a real release node.

## Architecture rules

```text
AG Core  -X->  AT Core
AG Core  -X->  Android framework
AG Core  -X->  ADB / JNI
AG Core  -X->  com.androidterminal.*
```

AG Core reaches Android execution only through the Tool contracts.

Concrete Android Tool is the only AndroidAgent execution layer allowed to connect
to an Android-specific execution backend. It may use the Android APIs directly,
connect to the AndroidTerminal execution boundary, or combine both.

```text
AG Core / Agent Runtime / AGA   must not depend on AT implementation
```

`AT Core` lives in the AndroidTerminal project. AndroidAgent must not vendor,
copy, or re-implement it. If AG Core appears to need it, the design is wrong:
introduce a tool instead.

The exact AT integration mechanism is deferred to `AG.SHELL-0.DESIGN`. Do not
assume a direct code dependency on AT Core.

## Package boundary

```text
com.androidagent.core      frozen
```

Forbidden: `com.androidterminal.*`, `com.androidagent.app.*`.
AG Core is product-independent; AGA, AGC, and any future frontend must be able
to reuse it unchanged.

## Development discipline

```text
No opportunistic refactor
No future-feature scaffolding without real need
Logical boundary before unnecessary abstraction
Do not implement empty architecture layers merely for symmetry
```

Adding a module, abstraction, or dependency requires a real consumer today,
not a hypothetical one. One execution backend does not justify a backend
abstraction layer.

## Verification

Any change must keep these green:

```bash
./gradlew :ag-core:test
./gradlew build
```

New logic in AG Core requires a JVM unit test. AG Core is testable precisely
because it is Android-free; do not erode that.

## Git

```text
No commit / push unless the task explicitly allows it.
```

Branch on demand. Do not pre-build a multi-branch Git flow for a single-line
project.

## Current frozen state

```text
AG.0            FROZEN   (tag ag-0)
AG.PROVIDER-0   FROZEN   (tag ag-provider-0)
AGA-0           device chat PASS (strict multi-turn memory PENDING)
AGA-0.ARCH      DONE
```

Implemented and **not** to be described as pending:

```text
:ag-core                       AgentCore, Agent Loop, Session / Run
                               ModelProvider contract, Tool contract
                               Tool Registry, Allow / Deny policy
                               Events, RunOutcome
:ag-runtime                    AgentRuntime — host-neutral session selection
                               and single-active-run lifecycle
:provider-openai-compatible    OpenAiCompatibleProvider, config loader/resolver,
                               OpenAiHttpTransport contract
:provider-openai-compatible-jdk JdkOpenAiHttpTransport (java.net.http)
:app                           AGA host: AndroidOpenAiHttpTransport
                               (HttpsURLConnection), minimal chat UI
config/provider.example.json   tracked, no secret
config/provider.local.json     ignored, real key
```

`AgentCore.run`, `ModelProvider.complete`, and `Tool.execute` are all `suspend`.
AG Core ships no blocking wrapper and has no production dependency beyond the
Kotlin standard library. AG Core does not know about HTTP, JSON, API keys,
config files, OpenAI, EMG, Android, or AT Core.

`ModelTurn.ToolCalls` carries `content`, so a single assistant turn can hold
prose and tool calls together. Verified against a real endpoint.

## Frontend boundary

```text
Frontend must not own reusable Agent runtime semantics.

Reusable Session / Run host lifecycle belongs in :ag-runtime.

AGA / AGC should remain thin frontends: input, rendering, platform lifecycle.

Do not move Android or Provider implementation details into :ag-runtime.
A frontend must not call AgentCore.session/run directly, must not generate
Session ids, and must not implement its own busy guard.
```

## AT integration boundary

```text
AG Core       -X-> AT implementation
Agent Runtime -X-> AT implementation
AGA           -X-> AT implementation
```

`Concrete Android Tool` is the only AndroidAgent execution layer allowed to
integrate an Android-specific execution backend. A concrete tool may use the
Android APIs directly, connect to the AndroidTerminal execution boundary, or
combine both. It must not be written as "all Android Tools must depend on AT
Core" — that is wrong.

AndroidAgent must **not** vendor AT source, copy AT source, reimplement AT Core,
or push an AT implementation into `:ag-core`, `:ag-runtime` or `:app`.

**The AT integration mechanism is not frozen yet.** Do not assume direct AT Core
linking, an Android runtime service, or Binder / AIDL before `AG.SHELL-0.DESIGN`
decides. AT does not have to escape the Android device, but its execution
capability must eventually be usable by Android consumers other than the AT
APK's own UI.

## Still forbidden without an explicitly opened Step

Do not begin any of the following unless a Step explicitly authorizes it:

```text
ShellTool / any Android Tool          AT Runtime / AT Core integration
additional AGA feature development     AGC / further frontend expansion
Skills loader                         MCP
persistence / RAG                     multi-agent
plugin framework                      provider routing
AIController integration
```

Note: AGA itself is **implemented and frozen**. What is forbidden is new AGA
feature work and any additional frontend — not AGA's existence.

## Test hygiene

```text
JUnit/Kotlin tests must use normal block-body Unit methods,
or explicitly return Unit.

Do not place @Test methods as local functions.

Test-count claims must come from actual JUnit execution results,
not source-level method counting.
```

Both failure modes have already occurred here: a non-Unit inferred return type,
and a `@Test` on a local function. In both cases JUnit silently executed nothing
while the source appeared correct.

## Secret handling

```text
Never print, log, or commit an API key.
Never echo a secret-bearing file, including via a parser error path.
Never emit an Authorization header.

Only these are allowed in output:
  API_KEY = PRESENT / ABSENT
  safe endpoint identity (scheme + host + port + path)
  model id
```

The development config is `config/provider.local.json`, which is gitignored and
holds a real key. Validate it without any code path that can echo raw content.

The development discipline above still applies in full: one Step at a time, no
opportunistic refactor, respect dependency boundaries, and no push unless the
task explicitly allows it.