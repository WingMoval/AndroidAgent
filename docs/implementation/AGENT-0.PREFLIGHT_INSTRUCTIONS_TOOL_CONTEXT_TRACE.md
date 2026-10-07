# AGENT-0.PREFLIGHT — Instructions / Tool / Context / Trace Freeze

> Step: **AGENT-0.PREFLIGHT** (PRE-LIVE AGENT READINESS / OBSERVABILITY / CONTRACT AUDIT)
> Baseline: HEAD `c76058f` (AG.SHELL-1 VERIFIED / CLOSED), working tree CLEAN at start
> AT repo: `726a441` READ ONLY, at-core 0 changes
> Verdict: **AGENT_0_PREFLIGHT_PASS**

This Step did **not** run the first real autonomous Agent. It froze what the model
will actually see (Instructions, Tools, Context/History) and built the minimal Agent
Trace, so that every behaviour in AGENT-0.LIVE is observable, replayable and
diagnosable.

After this Step the following questions have deterministic, tested answers:

```text
LLM 收到了什么 Instructions？      -> AGENT_INSTRUCTIONS_V0 (frozen content + hash)
LLM 看到了什么 Tools？            -> exactly android_shell (audited definition)
android_shell 如何描述给模型？     -> WHAT / WHEN / INPUT / OUTPUT (audited)
当前 User Goal 如何进入 ModelRequest？ -> UserMessage -> ModelRequest.history
历史消息如何进入 ModelRequest？     -> Session.history() -> ModelRequest.history
ToolResult 是否进入下一轮模型请求？ -> YES (T5, asserted)
一次 Run 内发生了哪些事件？        -> AgentEvent -> AgentTraceCollector -> AgentTrace
Agent 跑偏时能否定位到具体 Turn？  -> YES (trace spans carry step / tool / args / result)
```

---

## 1. Baseline

```text
branch        main
start HEAD    c76058f (feat: integrate Android shell tool with AT Engine)
tags          ag-0 .. ag-stable-1, ag-engine-0  (all pre-existing, untouched)
remote        none
working tree  CLEAN at start
AT repo       726a441 READ ONLY (0 at-core changes)
```

No unattributable production modification existed at start, so no STOP was raised.

## 2. Current Instructions Audit (before change)

```text
CURRENT_INSTRUCTIONS_SOURCE
    RunOptions.instructions (AG Core) -> ModelRequest.instructions (AG Core)
    ChatCompositionRoot constructs AgentRuntime(agentCore) with NO RunOptions,
    so AgentRuntime used its default RunOptions(instructions = "").

CURRENT_INSTRUCTIONS_VALUE_STATE
    EMPTY  (no instructions were ever supplied to a Run)

CURRENT_INSTRUCTIONS_WIRE_MAPPING
    ChatCompletionWire.projectMessages: instructions.isNotBlank() ->
    leading role="system" message. Empty -> no system message at all.

Per-Run same instructions?  YES by construction (single RunOptions per Run).
instructions separate from tools/history?  YES (first-class ModelRequest field).
```

So before this Step the model received **no** system instructions at all.

## 3. AGENT_INSTRUCTIONS_V0

```text
version = AGENT_INSTRUCTIONS_V0
hash    = d230d2b398e5bf705e85da231e422eead747799a17b5f76bb91481ab8de9cfdf
          (deterministic SHA-256 over the exact content bytes, UTF-8)
```

```text
You are an Android device agent.

When a task depends on the actual state of the connected Android device,
use the available tools to obtain that state instead of guessing.

Treat tool results as observations.
Base each subsequent action on the information newly observed.

Continue only as long as needed to satisfy the user's goal.
Do not repeat an action whose result you already know, unless verification
is necessary.

When the goal is satisfied, provide the final answer.
If the goal cannot be completed, report the actual blocking condition.
```

Ownership, frozen:

```text
Instructions contract (RunOptions/ModelRequest carry the field)  -> AG Core
AGENT_INSTRUCTIONS_V0 content                                     -> Host (:app)
```

The content lives in `app/src/main/.../AgentInstructions.kt` (Host/Composition)
and is injected at the Composition Root. AG Core and the Agent Runtime carry no
prompt. The hash is computed at class load and pinned by test T8, so content and
identity cannot drift. A Trace records only `instructionsVersion` +
`instructionsHash`, never the full prompt.

## 4. android_shell Tool Definition (audited)

```text
name      android_shell
arguments opaque JSON string {"command":"..."} (unchanged, permissive object schema)
```

The `ToolSpec.description` was rewritten to answer the four required questions and
to remove the success-semantics ambiguity:

```text
WHAT    Execute one non-interactive shell command on the connected Android device
        and return its result.
WHEN    Use when the task depends on actual Android device state or a
        shell-accessible Android operation, and the answer is not already known.
INPUT   A single JSON object with one string field "command", e.g. {"command":"id"}.
        No other fields are supported.
OUTPUT  A JSON object with outcome / exitCode / stdout / stderr / hasError /
        error (only on mechanism failure). A completed command reports success
        even when exitCode is non-zero; only a tool mechanism failure
        (transport / protocol / unsupported / timeout / cancelled) reports
        success = false with an "error" field.
```

Frozen semantics preserved: `COMPLETED -> success=true` even when `exitCode != 0`;
`success` is a **mechanism** flag, never "exit code == 0"; device stderr stays in
the payload and is never promoted to `ToolResult.error`.

```text
typed schema change : NO   (no ToolSpec redesign, no Core change, per §8.3)
```

## 5. Model Request Shape

```text
ModelRequest
├─ sessionId      PASS (session id from AG Core)
├─ instructions   PASS (AGENT_INSTRUCTIONS_V0, T1 + E1)
├─ history        PASS (UserMessage, T4)
└─ tools          PASS (contains exactly android_shell, T2)

after Tool execution:
history contains ModelMessage(tool call) + ToolResultMessage   PASS (T5)
next ModelRequest.history contains the previous ToolResultMessage  PASS (T5)
```

## 6. Provider Wire Audit

```text
instructions      -> role="system" (leading) when non-blank   (E1, E1b)
UserMessage       -> role="user"                              (E2)
ModelMessage      -> role="assistant" (+ tool_calls when present) (E3)
tool calls        -> structured tool_calls (id/name/arguments) (E3)
ToolResultMessage -> role="tool" + tool_call_id + content      (E4)
  mechanism error -> appended as "[error] ..." for the model   (E5)
ToolSpec          -> tools/function (name/description/permissive object) (T2,T3)
```

Proven by inspecting the exact JSON body `ChatCompletionWire.buildRequestBody`
produces — no network, no live endpoint, no real key.

## 7. Trace Architecture

```text
AgentCore
   ↓ AgentEvent (ordered, synchronous)
AgentEventSink (contract owner: AG Core)
   ↓
AgentTraceCollector (owner: Host / :app observability)
   ↓
AgentTrace (structured) -> TraceFormatter (redacted text)
```

```text
AgentEvent owner          AG Core
AgentEventSink owner      AG Core
AgentTraceCollector owner Host (:app)
physical location         app/src/main/.../trace/
Core production change    0
Runtime production change 0
```

The collector is a plain `AgentEventSink` consumer in `:app` (`app/src/main/.../
trace/AgentTraceCollector.kt`). It captures the Host-known metadata AG Core cannot
supply (model id, instructions identity, available tools) and reconstructs the Run
from the ordered events. No new AG Core event, no telemetry, no metrics, no
persistence, no logging framework.

One collector serves the process: AGA admits at most one Run at a time (single
Session + Host busy guard), and its `AgentTrace` is rebuilt per admitted Run. It is
installed as `RunOptions.eventSink` in the Composition Root; `ChatActivity` logs
the redacted trace after each Run.

## 8. Trace v0 Evidence (per Run)

```text
Run
├─ localTraceId / sessionId / modelId
├─ instructionsVersion / instructionsHash
├─ availableToolNames
├─ start / end
├─ spans: run_start, model_turn_start(step), model_turn_end(step,type,tools),
│         tool_call(name,args), tool_result(name,success,output,error),
│         final_answer(content), run_failure(failure)
└─ finalOutcome
```

Reconstructability (T7): which tool, its arguments, its result, the order, and how
the Run ended are all recoverable from one `AgentTrace`.

## 9. Secret / Privacy

```text
API key leakage        NONE (G1: not in body/endpoint; G2: config toString redacted)
Authorization leakage  NONE (T9: "Authorization: Bearer SECRET_SENTINEL" -> redacted)
fake sentinel leakage  NONE (T9: 9 sentinel forms + full-trace render all clean)
adb private key        NONE (real PEM material scan = 0)
```

Redaction (`TraceFormatter.redact`) masks `Authorization:` / `Bearer ` /
`api_key|key|password|secret|token` values. It is applied to every rendered span
and to the data-class `toString()` of content-bearing spans, so a secret can reach
no log, no assertion message, and no printed trace. A fake sentinel is used for the
test; no real credential is exercised.

## 10. Static Boundary Audit

```text
:ag-core    trace/telemetry/android/logging  0  (T10)
:ag-runtime trace/metrics/telemetry/logging  0  (T11)
AG Core AT imports          0  (T12)
Agent Runtime AT imports    0  (T12)
:app main AT imports        0  (T12)
:tool-android-shell AT      retained (the only allowed consumer)  (T12)
absolute AT path in tracked 0
real private-key material   0
```

## 11. Tests (from real JUnit execution)

```text
:ag-core:test                        41  PASS
:ag-runtime:test                      8  PASS
:provider-openai-compatible:test     32  PASS  (incl. new WireAuditTest 11)
:provider-openai-compatible-jdk:test 24  PASS
:tool-android-shell:test             16  PASS
:app:testDebugUnitTest (new)         11  PASS  (AgentPreflightTest T1-T12)
```

New preflight tests: `AgentPreflightTest` (T1, T4, T5, T6, T7, T7b, T8, T9, T10,
T11, T12) and `WireAuditTest` (T2, T3, E1, E1b, E2, E3, E4, E5, G1, G2).

## 12. Build

```text
:app:assembleDebug   PASS   (APK 5,016,870 B; arm64-v8a; 3 native libs present:
                             libandroidterminaladb.so, libc++_shared.so,
                             libnetwork_probe.so)
```

No AT native re-verification, no exit7 real-device, no identity provisioning was
done — this Step did not touch those production paths.

## 13. Changed Files

```text
modified
  AGENTS.md                                          (SSOT normalization, §A)
  app/build.gradle.kts                               (:app JVM unit-test setup)
  app/src/main/.../ChatCompositionRoot.kt            (instructions + trace wiring)
  app/src/main/.../ChatActivity.kt                   (root field + logTrace)
  tool-android-shell/.../AndroidShellToolImpl.kt     (ToolSpec description, §D)

added
  app/src/main/.../AgentInstructions.kt              (AGENT_INSTRUCTIONS_V0)
  app/src/main/.../trace/AgentTrace.kt
  app/src/main/.../trace/AgentTraceCollector.kt
  app/src/main/.../trace/TraceFormatter.kt
  app/src/test/.../AgentPreflightTest.kt             (T1-T12)
  provider-openai-compatible/src/test/.../WireAuditTest.kt (T2,T3,E,G)
  docs/implementation/AGENT-0.PREFLIGHT_INSTRUCTIONS_TOOL_CONTEXT_TRACE.md (this)
```

## 14. Known Limitations

```text
- Trace is in-memory + log-only; no durable store, no viewer UI (out of scope).
- Trace records instructions identity, not the full prompt (by design).
- No live autonomous run was executed (that is AGENT-0.LIVE).
- ToolSpec remains untyped (opaque arguments); revisit only if AGENT-0.LIVE trace
  shows the model repeatedly mis-calling the argument structure.
- A single process-wide collector relies on AGA's single-active-Run guarantee.
```

## 15. Final Verdict

```text
AGENT_0_PREFLIGHT_PASS

INSTRUCTIONS_V0                = FROZEN
ANDROID_SHELL_TOOL_DEFINITION  = VERIFIED
MODEL_REQUEST_SHAPE            = VERIFIED
TOOL_RESULT_FEEDBACK           = VERIFIED
TRACE_V0                       = READY
TRACE_BOUNDARY                 = PRESERVED
SECRET_LEAKAGE                 = NONE
AG_CORE                        = UNCHANGED
AG_RUNTIME                     = UNCHANGED
AT_ENGINE                      = UNCHANGED
READY_FOR_AGENT_0_LIVE         = YES
```

STOP HERE. AGENT-0.LIVE / AGENT-0.CONTRAST / AGENT-0.STABILITY are not started;
await Controller Review.
