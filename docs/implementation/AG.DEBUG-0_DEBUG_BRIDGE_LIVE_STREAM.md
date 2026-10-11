# AG.DEBUG-0 — Debug Bridge & Live Execution Stream

> STEP: AG.DEBUG-0 (DEBUG BRIDGE & LIVE EXECUTION STREAM)
> BASELINE HEAD: `3aeed33` (test: verify AGENT-0 live adaptive tool loop)
> Verdict: **AG_DEBUG_0_PASS**

This Step adds a PC-side debug channel to the existing AGA Host: a TCP JSONL
bridge on 127.0.0.1 (debug builds only) that (D1) submits user input into the
SAME `AgentRuntime` the UI uses, and (D2) lets a passive PC client observe the
live `AgentEvent` execution stream — including runs that originated in the UI
(D3). It is host/tooling layer only: no second Agent, no second loop, no change
to AG Core / Agent Runtime / Tool / Provider.

---

## 1. Baseline

```text
start HEAD  : 3aeed33 (test: verify AGENT-0 live adaptive tool loop)
working tree: 3 untracked diagnostic fixtures retained from earlier Steps
              (ProviderRealDeviceProbeTest.kt, OpenRouterPcProbe.kt,
               docs/diagnostics/) - untouched by this Step
production diff (this Step): :app only
  app/build.gradle.kts                 +buildConfig, +kotlinx-serialization-json
  app/.../ChatActivity.kt              debug bridge wiring (process-scoped)
  app/.../ChatCompositionRoot.kt       CompositeSink: trace + host + debug
  app/.../debug/DebugBridge.kt         new (bridge)
  app/.../debug/DebugProtocol.kt       new (protocol, pure, JVM-testable)
  app/src/test/.../debug/DebugProtocolTest.kt   new (14 tests)
  app/src/androidTest/.../UiRunSideChannelTest.kt  new (T3 fixture)
  tools/ag_debug.py                    new (PC client, stdlib only)
```

## 2. Design

```text
PC ag_debug.py  -- adb forward tcp:45678 tcp:45678 -->  DebugBridge (device)
      |                                                  127.0.0.1:45678
      | JSONL hello/ping/pong/send/accepted/busy/error/event
      | send  -> AgentRuntime.send(text)   (the SAME runtime the UI uses)
      | event <- CompositeSink(trace, host, debug) <- AgentRuntime event stream
```

- One Agent Core / Runtime / ToolRegistry / `android_shell` / `AgentEvent`.
- Debug input is routed through `AgentRuntime.send` (existing single-active-run
  busy guard reused). A `send` while busy is answered `{"type":"busy"}` and
  never queued (T4). The bridge never calls AgentCore directly.
- The bridge is a side channel: it only forwards input and re-emits events.
- Multi-session, CLI, event-replay: DEFERRED (not in this Step).

## 3. Wire protocol (TCP + UTF-8 JSON Lines)

```text
client -> device : {"type":"ping"}           | {"type":"send","text":"..."}
device -> client : {"type":"hello","protocol":"ag-debug-0"}
                  {"type":"accepted"} | {"type":"busy"} | {"type":"pong"}
                  {"type":"error","message":"..."}
                  {"type":"event"} projections:
                  run_start | model_turn_start | tool_call | tool_result
                  model_turn_end | final_answer | run_failed | run_finished
```

All event content passes through `TraceFormatter.redact` before emission; no
secret or Authorization material leaves the device.

## 4. Release boundary (T7)

- `DebugBridge` binds `127.0.0.1` only (never the network address).
- The Host only starts it from `ChatActivity.ensureDebugBridge()`, gated by
  `if (!BuildConfig.DEBUG) return`.
- Verified on the built release: `BuildConfig.DEBUG == false`; a signed release
  APK installed and launched shows **no** listener on 45678 (`ss -ltn`), and a
  PC connect to the forwarded port returns EOF (no bridge to talk to).

## 5. Key bug found and fixed

The UI-originated Run emits AgentEvents on the host/main thread, so the first
implementation broadcast the stream from that thread. Android then throws
`NetworkOnMainThreadException` for every blocking socket write, and the old
`send()` swallowed it silently: hello/ping/pong (sent from the bridge's own IO
threads) worked while run events never arrived. Fix:

- `broadcast()` now enqueues on a dedicated single daemon `Executors
  .newSingleThreadExecutor` ("ag-debug-send"): off the emitting thread and
  strictly ordered.
- `ClientWriter.send` returns success/failure and logs the failure class; dead
  writers are dropped from the client list instead of lingering.

## 6. Acceptance (real device, debug build)

```text
DEVICE      = APLMUT4312005935 (HONOR ALI-AN00, Android 15, arm64-v8a, USB)
PROVIDER    = OpenRouter (deepseek/deepseek-v4-flash-0731)
```

- **T1 PASS** PC debug input produces a complete Agent run via the same
  runtime (send -> accepted -> run events -> outcome).
- **T2 PASS** Live execution stream: run_start / model_turn_start /
  model_turn_end / final_answer / run_finished observed in real time.
- **T3 PASS** UI-originated run observable by a passive PC client. Fixture
  `UiRunSideChannelTest` drives the REAL ChatActivity production submit path
  (input field + Send button), goal "Reply exactly with DEBUG_UI_OK." The
  passive client (no sends) captured in real time:
  `run_start, model_turn_start, model_turn_end(final_answer),
   final_answer(DEBUG_UI_OK), run_finished(Answer(DEBUG_UI_OK))`;
  `SEQ=['run_start','model_turn_start','model_turn_end','final_answer',
   'run_finished']` (314 bytes). `PC_DEBUG_SEND_USED = NO`.
- **T4 PASS** Busy semantics: a debug send during an active Run is answered
  `busy`, never queued.
- **T5 PASS** Client disconnect does not affect an in-flight Run.
- **T6 PASS** A reconnecting client resumes receiving events.
- **T7 PASS** Release boundary (see section 4): `assembleRelease` builds;
  no listener / no external ingress in the release build.

## 7. Unit tests

`DebugProtocolTest` (JVM, :app): 14 tests, 0 failures — parse (ping/send),
malformed/blank lines, encode (hello/accepted/busy/pong/error), busy mapping,
disconnect isolation, event -> JSON projection for every AgentEvent variant,
redaction.

## 8. Regression (this Step)

```text
:ag-core                        41 tests  0 fail
:ag-runtime                      8 tests  0 fail
:provider-openai-compatible     39 tests  0 fail
:provider-openai-compatible-jdk 24 tests  0 fail  (+1 OpenRouterPcProbe retained
                                                   diagnostic, not committed)
:tool-android-shell             16 tests  0 fail
:app (testDebugUnitTest)        62 tests  0 fail  (includes DebugProtocolTest 14)
:app:assembleDebug              OK
:app:assembleRelease            OK
```

## 9. Dependency audit

`AG Core -X- DebugBridge`, `Agent Runtime -X- DebugBridge`, `Tool -X-
DebugBridge`, `Provider -X- DebugBridge`. The bridge lives in :app (host/tooling
layer), depends only on `AgentEventSink` / `AgentRuntime` / `TraceFormatter`.
No change to `com.androidagent.core`, `com.androidagent.runtime`,
`com.androidterminal.*`, or the AT Handoff bundle.

## 10. Commit

```text
feat: add AG debug bridge and live execution stream
```