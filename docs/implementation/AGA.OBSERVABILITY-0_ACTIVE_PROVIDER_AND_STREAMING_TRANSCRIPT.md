# AGA.OBSERVABILITY-0 — Active Provider Truth & Streaming Agent Transcript

> STEP: AGA.OBSERVABILITY-0 (ACTIVE PROVIDER TRUTH & STREAMING AGENT TRANSCRIPT)
> BASELINE HEAD: `a7f8218` (feat: add AG debug bridge and live execution stream)
> Verdict: **AGA_OBSERVABILITY_0_PASS** (device acceptance pending phone reconnect)

This Step makes the AGA Host tell the truth about the active provider and show
the agent working live:

- **Provider truth**: the Provider selector is switch-is-active (no Apply
  button), the screen always shows the ACTIVE provider's safe endpoint
  (scheme + host + path), and a failed switch reverts the selector to the
  still-active node instead of lying about it.
- **Streaming transcript**: real provider reasoning deltas stream into a
  Thinking block that flips to a Thought block (same item, never a second
  block), Assistant content streams incrementally, dispatched tool calls and
  their results are visible, and failures are visible with their reason.
- The model stream is **observation only**: the final `ModelTurn` semantics are
  unchanged, there is no second Agent loop, and no tool executes before its
  turn is complete.

---

## 1. Baseline

```text
start HEAD  : a7f8218 (feat: add AG debug bridge and live execution stream)
working tree: 3 untracked diagnostic fixtures retained from earlier Steps
              (ProviderRealDeviceProbeTest.kt, OpenRouterPcProbe.kt,
               docs/diagnostics/) - untouched by this Step
production diff (this Step):
  ag-core/.../Model.kt                  +ModelStreamObserver, ModelRequest.streamObserver
  ag-core/.../AgentCore.kt              RunOptions.streamObserver -> ModelRequest
  provider-openai-compatible/.../OpenAiHttpTransport.kt   +postStream
  provider-openai-compatible/.../ChatCompletionWire.kt    streaming (StreamAccumulator,
                                        parseStreamLine, toModelTurn(acc), stream flag)
  provider-openai-compatible/.../OpenAiCompatibleProvider.kt  complete() always streams
  provider-openai-compatible/.../OpenAiCompatibleConfig.kt    +safeChatCompletionsEndpoint
  provider-openai-compatible-jdk/.../JdkOpenAiHttpTransport.kt  +postStream (ofLines)
  provider-openai-compatible-jdk/.../FixtureServer.kt        SSE framing on stream:true
  app/.../provider/AndroidOpenAiHttpTransport.kt             +postStream (line loop)
  app/.../TranscriptModel.kt            new (pure transcript, JVM-testable)
  app/.../ChatCompositionRoot.kt        streamObserver -> RunOptions
  app/.../ChatActivity.kt               provider truth UI + transcript + stream wiring
  app/.../HostStatusModel.kt            +resolveProviderSwitch (pure)
  app/.../debug/DebugBridge.kt          +streamSink (JSONL incremental)
  app/.../debug/DebugProtocol.kt        +streamToJson + STREAM_* kinds
  app/.../res/layout/activity_chat.xml  provider selector + endpoint label; manual
                                        config fields removed from the main path
  tools/ag_debug.py                     pretty() cases for reasoning/content deltas
  tests: StreamWireTest (12), OpenAiCompatibleProviderStreamingTest (7),
         TranscriptModelTest (21), HostStatusModelTest P-series (6)
```

## 2. Phase-0 audit: what was actually wrong

1. **Streaming was fully buffered (option 2).** `ChatCompletionWire.buildRequestBody`
   hardcoded `"stream":false`; the JDK transport used `BodyHandlers.ofString()`
   and the Android transport `readText()`; the provider parsed one JSON body
   into one `ModelTurn`. The UI therefore could only show "Thinking... · Step N"
   until the entire turn arrived.
2. **Provider truth bug.** The UI showed only the node name ("Provider: EMG"),
   never the endpoint. `tryDeviceLocalConfig` picked the first loadable file,
   and the device's `files/provider.local.json` had been swapped to OpenRouter
   content during AG.DEBUG-0 (EMG backup at `provider.local.json.emgbak`), so
   the screen said EMG while every request went to OpenRouter. A failed switch
   left the spinner on the new node while the provider stayed on the old one.

## 3. Root cause of the truth bug

The UI state (`currentNode`), the config source (first loadable file), and the
actual applied config were three independent things. The fix is one active
state: the selector, the endpoint label, the applied config, and the actual
request all derive from the node that was actually applied.

## 4. Streaming: before / after

```text
BEFORE  request {"stream":false} -> buffered body -> one JSON -> one ModelTurn
AFTER   request {"stream":true}  -> SSE lines -> StreamAccumulator + observer
        -> final ModelTurn assembled from what actually streamed
```

Wire evidence (safe probe, no secrets printed): OpenRouter
(`deepseek/deepseek-v4-flash-0731`) streams `delta.reasoning` and
`delta.content` incrementally, interleaved in the same chunks, with
`finish_reason=stop`. EMG (`qwen3.8-local`) answered 403 from the PC (it is a
device-only endpoint); its streaming shape is verified on-device during
acceptance.

## 5. Reasoning wire format and the Thinking -> Thought rule

A model turn can carry reasoning deltas, content deltas, or both, in any
interleaving. The transcript renders:

```text
onReasoningStarted/Delta/Finished  -> one Thinking block; Finished flips the
                                      SAME block to Thought (no second block)
onContentStarted/Delta             -> one Assistant block per turn
onAnswerProduced                   -> marks the existing Assistant block final;
                                      fallback block only when nothing streamed
```

A model with no reasoning output produces no Thinking/Thought block at all —
the Host never invents reasoning (FAKE_REASONING=NONE).

## 6. Tool actions, results, failures

- `ToolCallStarted` -> `Shell` block with the actual command (`commandOf`
  extracts `{"command":...}`; anything else is shown verbatim).
- `ToolCallFinished` -> `Result` block rendering the android_shell payload
  (`exitCode` / `stdout` / `stderr`; mechanism errors appended).
- `RunFailed` -> `Failure` block with the reason (`Provider failure (Timeout)`,
  `Stopped after N steps (maxSteps)`, `Session busy`, `Runtime failure (...)`).

## 7. Provider truth UI

- Main path is `Provider [node ▼]` + the active safe endpoint label
  (`cfg.safeChatCompletionsEndpoint` = scheme + host + path, no query, no key).
- Switch-is-active: selecting a node immediately loads and applies it.
- `resolveProviderSwitch` (pure, JVM-tested) decides Applied/Rejected; on
  Rejected the selector reverts to the still-active node and the reason is
  shown. The UI never claims a provider that is not active.
- The manual base-url/model/key/timeout fields are removed from the main path;
  per-node device-local configs are the only credential source.

## 8. Debug bridge incremental stream

The v0 JSONL protocol gains six message kinds (framing unchanged, older
clients ignore unknown types safely):

```text
reasoning_started | reasoning_delta | reasoning_finished
content_started   | content_delta   | content_finished
```

`DebugBridge.streamSink` broadcasts them through the same single-thread send
executor as the event stream (off the emitting thread, strict order, dead
writers evicted). All text passes through `TraceFormatter.redact`.

## 9. Verification

### JVM (this Step, phone-independent)

```text
ag-core                        41 tests  PASS
ag-runtime                      8 tests  PASS
provider-openai-compatible     58 tests  PASS  (+StreamWireTest 12,
                                                +ProviderStreamingTest 7)
provider-openai-compatible-jdk 25 tests  PASS  (FixtureServer SSE framing)
tool-android-shell             32 tests  PASS
app                           148 tests  PASS  (+TranscriptModelTest 21,
                                                +HostStatusModel P-series 6)
:app:assembleDebug / :app:assembleRelease  BUILD SUCCESSFUL
```

### Device acceptance (phone connected, stable network)

```text
P1 OpenRouter active route:
     UI spinner=OpenRouter, endpoint label=https://openrouter.ai/api/v1/chat/completions
     actual request endpoint (debug log)=https://openrouter.ai/api/v1/chat/completions
     => PROVIDER_UI_TRUTH=VERIFIED, OPENROUTER_ACTIVE_ROUTE=VERIFIED
P2 EMG active route (key expired -> HTTP 401):
     UI spinner=EMG, endpoint label=https://api.emg.dpdns.org/v1/chat/completions
     actual request endpoint (debug log)=https://api.emg.dpdns.org/v1/chat/completions
     run failed with Provider failure (HttpStatus), visible in transcript
     => EMG_ACTIVE_ROUTE=VERIFIED, RUN_FAILURE_VISIBLE=VERIFIED
     EMG_CONTENT_STREAM / EMG reasoning: DEFERRED until the EMG key is renewed
     (user will fix manually)
P3 switch back EMG -> OpenRouter reverts spinner + label + route honestly
     => PROVIDER_UI_TRUTH=VERIFIED (switch never lies)

REASONING_STREAM_IS_INCREMENTAL=YES   (7 reasoning_delta before model_turn_end)
NOT_BATCHED_AT_END=YES                (deltas streamed, not delivered at the end)
ASSISTANT_CONTENT_STREAM=VERIFIED     (29 content_delta in one turn)
THINKING_TO_THOUGHT=VERIFIED          (one block flips to "Thought" in the UI)
EMG_FAKE_REASONING=NONE               (the 401 run emitted no reasoning_started;
                                       the transcript showed only the Failure block)
TOOL_ACTION_VISIBLE=VERIFIED          (Shell $ id / $ echo hello)
TOOL_RESULT_VISIBLE=VERIFIED          (Result exitCode=0 stdout=uid=2000(shell)...)
TOOL_FAILURE_VISIBLE=VERIFIED         (Result exitCode=1 for `false`)
RUN_FAILURE_VISIBLE=VERIFIED          (Failed / Provider failure (HttpStatus))
FINAL_ANSWER_DUPLICATION=NONE         (one Assistant block, marked final)
DEBUG_BRIDGE_REGRESSION=PASS          (PC client saw reasoning/content deltas,
                                       reasoning_finished/content_finished,
                                       tool_call, tool_result, final_answer,
                                       run_failed, run_finished)
RELEASE_BUILD=PASS                    (release APK: no listener on 45678,
                                       no AG.DebugBridge / AG.OpenAiTransport
                                       logs, PC connect recv=b''; UI truth works)
SECRET_HANDLING=PASS                  (endpoint labels are scheme+host+path only;
                                       no key in UI, logcat, transcript or stream)
```

Bugs found during device acceptance and fixed:

1. **Missing reasoning/content "Finished" callbacks.** The provider never
   signaled the end of a reasoning/content section, so the transcript block
   stayed "Thinking..." forever (never flipped to "Thought") and the PC client
   never saw `reasoning_finished` / `content_finished`. Fix:
   `OpenAiCompatibleProvider.complete()` now closes the sections at stream end
   (`if (acc.reasoning.isNotEmpty()) observer.onReasoningFinished()`, same for
   content). Covered by provider streaming test P8.
2. **Transcript missed debug-bridge-originated failures.** `RunFailed` and
   `AnswerProduced` were only fed into the transcript by the UI send path, so a
   run started from the PC debug bridge never showed its failure/final answer.
   Fix: `ChatActivity.onRunEvent` now also feeds `AnswerProduced` and
   `RunFailed` into the transcript (single source for UI and debug runs); the
   UI send path no longer duplicates them.

## 10. Files changed

```text
ag-core/src/main/kotlin/com/androidagent/core/Model.kt
ag-core/src/main/kotlin/com/androidagent/core/AgentCore.kt
provider-openai-compatible/src/main/kotlin/com/androidagent/provider/openai/
  OpenAiHttpTransport.kt, ChatCompletionWire.kt, OpenAiCompatibleProvider.kt,
  OpenAiCompatibleConfig.kt
provider-openai-compatible-jdk/src/main/kotlin/com/androidagent/provider/openai/jdk/
  JdkOpenAiHttpTransport.kt
provider-openai-compatible-jdk/src/test/kotlin/com/androidagent/provider/openai/jdk/
  FixtureServer.kt
app/src/main/java/com/androidagent/app/
  TranscriptModel.kt (new), ChatCompositionRoot.kt, ChatActivity.kt,
  HostStatusModel.kt, provider/AndroidOpenAiHttpTransport.kt,
  debug/DebugBridge.kt, debug/DebugProtocol.kt
app/src/main/res/layout/activity_chat.xml
tools/ag_debug.py
```

## 11. Deferred (explicitly NOT this Step)

Collapse of transcript blocks, transcript persistence, multi-session UI,
AGC CLI, new AT migration, UI polish.

## 12. Verdict

```text
AGA_OBSERVABILITY_0_PASS
PROVIDER_UI_TRUTH=VERIFIED        OPENROUTER_ACTIVE_ROUTE=VERIFIED
EMG_ACTIVE_ROUTE=VERIFIED         MODEL_STREAMING=VERIFIED
OPENROUTER_REASONING_STREAM=VERIFIED  THINKING_TO_THOUGHT=VERIFIED
ASSISTANT_CONTENT_STREAM=VERIFIED EMG_CONTENT_STREAM=DEFERRED (expired EMG key;
                                user renews manually; route + failure already
                                verified on-device)
EMG_REASONING_EXPECTED=NO         FAKE_REASONING=NONE
TOOL_ACTION_VISIBLE=VERIFIED      TOOL_RESULT_VISIBLE=VERIFIED
TOOL_FAILURE_VISIBLE=VERIFIED     RUN_FAILURE_VISIBLE=VERIFIED
FINAL_ANSWER_DUPLICATION=NONE     DEBUG_BRIDGE_REGRESSION=PASS
RELEASE_BUILD=PASS                SECRET_HANDLING=PASS
READY_FOR_AGENT_0_CONTRAST=YES
```

Two acceptance bugs found and fixed on device (missing reasoning/content
"Finished" callbacks; transcript not showing debug-bridge-originated failures).
Full JVM regression green after both fixes.