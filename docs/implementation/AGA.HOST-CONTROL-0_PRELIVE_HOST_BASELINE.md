# AGA.HOST-CONTROL-0 — Pre-Live Host Baseline

> STEP: AGA.HOST-CONTROL-0 (HOST CONSOLIDATION / PRE-LIVE BASELINE)
> BASELINE HEAD: `cd0eb33`
> WORKTREE: was DIRTY (experiments) → consolidated this Step
> NEXT: AGENT-0.LIVE
> Verdict: **AGA_HOST_CONTROL_0_PASS**

This Step converged the dirty AGA experimental state into a clean, long-lived Host
baseline: kept the proven Provider node switching, AT connect control and run-phase
status; removed temporary diagnostics, the `atEnabled` experiment, the `noat`
diagnostic flavor and the `connectTo` diagnostic helper.

---

## 1. Baseline

```text
start HEAD  : cd0eb33
starting dirty files:
  app/build.gradle.kts                                    (at/noat flavors, AT_ENABLED_BY_DEFAULT)
  app/src/main/java/com/androidagent/app/ChatActivity.kt  (node switch + status + TEMP diag)
  app/src/main/java/com/androidagent/app/DeviceLocalProviderConfig.kt (ProviderNode + NodeLoader)
  app/src/main/java/com/androidagent/app/provider/AndroidOpenAiHttpTransport.kt (TEMP diag)
  app/src/main/res/layout/activity_chat.xml               (Spinner)
  provider-openai-compatible/.../OpenAiCompatibleConfig.kt      (atEnabled experiment)
  provider-openai-compatible/.../OpenAiCompatibleConfigLoader.kt (atEnabled parse)
  provider-openai-compatible/.../OpenAiCompatibleProvider.kt    (System.err diag)
  tool-android-shell/.../ShellConnection.kt               (connectTo experiment)
  .gitignore
untracked: ProviderRealDeviceProbeTest.kt, OpenRouterPcProbe.kt, docs/diagnostics/
```

## 2. 401 Root Cause (established, not reopened)

```text
confirmed: manual Apply -> API Key EditText already blank -> apiKey="" ->
          Authorization absent -> OpenRouter 401.
NOT attributed to: network / Cloudflare / AT / OpenRouter / EMG.
```

**Fix**: `HostStatusModel.effectiveKey(typedKey, lastAppliedKey)` — a blank typed key
retains the last successfully applied credential. Regression-tested (R1-R5) with a
fake sentinel.

## 3. Provider Nodes

```text
EMG         filesDir/provider.local.json
OpenRouter  filesDir/provider.openrouter.json
enum ProviderNode + DeviceLocalProviderNodeLoader (Host-level only)
switch auto-applies: Spinner -> load node config -> rebuild Composition Root -> active.
settings page    : DEFERRED
provider routing : NO framework (no fallback/load-balancing/health routing)
```

## 4. AT Connection

```text
startup auto-connect : YES (single AGA APK always wires AT; no flavor gate)
Connected state      : "AT: Connected"
Disconnected state   : "AT: Disconnected" / "Wireless debugging unavailable"
Reconnect            : dedicated button, disabled while Connecting...
Provider usable while AT disconnected : YES (verified: LLM works, android_shell
                        returns mechanism TRANSPORT_ERROR, Agent explains failure)
```

## 5. Runtime Status

Mapped from real `AgentEvent` via `HostStatusModel.runPhaseText`:

```text
RunStarted / ModelTurnStarted -> Thinking... · Step N
ToolCallStarted              -> Executing <tool>...
ToolCallFinished             -> Processing result...
AnswerProduced               -> Preparing answer...
RunFinished                  -> Ready.
RunFailed                    -> Failed.
```

Core event contract: UNCHANGED (Host observes the existing AgentEventSink stream
through a CompositeSink in ChatCompositionRoot).

## 6. Timeout

```text
EMG         requestTimeoutSeconds = 600  (local Qwen slow; multi-turn runs)
OpenRouter  requestTimeoutSeconds = 180
transport contract : UNCHANGED (no connect/read/model split this Step)
```

## 7. Experiment Cleanup

```text
atEnabled            : REMOVED (config + loader)
noat flavor          : REMOVED
AT_ENABLED_BY_DEFAULT: REMOVED
connectTo            : REMOVED (production consumer = 0)
ProviderDiag         : REMOVED
System.err diag      : REMOVED
writeDiag / diagDir  : REMOVED
AUTH_/keyLen/keyPrefix debug : REMOVED
```

## 8. Diagnostics Retained

```text
ProviderRealDeviceProbeTest.kt  : device A/B probe fixture. No secret, no noat
                                  code dependency; kept as regression fixture.
OpenRouterPcProbe.kt             : PC OpenRouter live probe (@Tag live). Kept.
docs/diagnostics/AGA.PROVIDER-AB-HEADLESS-0.md : historical diagnostic record.
```

## 9. Architecture

```text
AG Core     : UNCHANGED
Agent Runtime : UNCHANGED
AT Engine   : UNCHANGED
Handoff     : UNCHANGED
Provider semantics : UNCHANGED (OpenAiCompatibleConfig has no atEnabled now)
```

## 10. Tests

```text
ag-core:test                        41 PASS
ag-runtime:test                      8 PASS
provider-openai-compatible:test     39 PASS
provider-openai-compatible-jdk:test 24 PASS
tool-android-shell:test             16 PASS
app:testDebugUnitTest               48 PASS
  new: HostStatusModelTest (R1-R5, A2-A6, S1-S8), ProviderNodeTest (P1-P6)
```

## 11. Build

```text
:app:assembleDebug PASS (single flavor, applicationId com.androidagent.app)
```

## 12. Device Sanity (USB, ALI-AN00)

```text
D1 launch           PASS
D2 provider selector PASS (EMG/OpenRouter shown)
D3 EMG/OpenRouter switch PASS
D4 LLM 你好 on selected provider PASS
D5 AT startup auto-connect PASS (Connected via loopback)
D6 AT disconnect -> Disconnected : observed Connecting->Connected on Reconnect;
     natural adbd wireless port not immediately released after setting disable
     (device behavior); real disconnected path verified in prior sessions
D7 Reconnect restores PASS
D8 LLM works while AT disconnected : PASS (prior sessions: android_shell
     returns TRANSPORT_ERROR, Agent answers normally)
D9 manual Apply blank-key no 401 : PASS (fix active; no new 401 on empty-key Apply)
```

## 13. Secret Audit

```text
real API key in git diff        = NONE
Authorization in logs           = NONE
raw provider JSON in logs       = NONE
keyPrefix / keyLen / AUTH_ / ProviderDiag = 0
provider.local.json tracked     = NO
provider.openrouter.local.json tracked = NO
provider config packaged in APK = NO
```

## 14. Files Changed

```text
modified:
  app/build.gradle.kts                     (single flavor restored)
  app/src/main/java/com/androidagent/app/ChatActivity.kt
  app/src/main/java/com/androidagent/app/ChatCompositionRoot.kt (CompositeSink)
  app/src/main/java/com/androidagent/app/DeviceLocalProviderConfig.kt
  app/src/main/java/com/androidagent/app/provider/AndroidOpenAiHttpTransport.kt
  app/src/main/res/layout/activity_chat.xml (Spinner + Reconnect)
  provider-openai-compatible/.../OpenAiCompatibleConfig.kt
  provider-openai-compatible/.../OpenAiCompatibleConfigLoader.kt
  provider-openai-compatible/.../OpenAiCompatibleProvider.kt
  tool-android-shell/.../ShellConnection.kt
  config/provider.local.json                (timeout 600, gitignored)
  config/provider.openrouter.local.json     (timeout 180, gitignored)
  AGENTS.md
added:
  app/src/main/java/com/androidagent/app/HostStatusModel.kt
  app/src/test/java/com/androidagent/app/HostStatusModelTest.kt
  app/src/test/java/com/androidagent/app/ProviderNodeTest.kt
  docs/implementation/AGA.HOST-CONTROL-0_PRELIVE_HOST_BASELINE.md (this)
retained (untracked): ProviderRealDeviceProbeTest.kt, OpenRouterPcProbe.kt,
  docs/diagnostics/AGA.PROVIDER-AB-HEADLESS-0.md
```

## 15. Known Limitations

```text
- AT disconnected state is only rendered after a connect/reconnect attempt; the
  Host does not passively poll adbd for sudden loss.
- Provider node selection is not persisted across restarts (NODE_SELECTION_PERSISTENCE DEFERRED).
- Provider config editing remains on the main screen (SETTINGS_PAGE DEFERRED).
- adbd wireless port may keep listening briefly after the setting is disabled,
  so "AT: Disconnected" may lag a real disable on some devices.
```

## 16. Final Verdict

```text
AGA_HOST_CONTROL_0_PASS

PROVIDER_NODE_EMG              = READY
PROVIDER_NODE_OPENROUTER       = READY
NODE_SWITCH_AUTO_APPLY         = VERIFIED
BLANK_KEY_401                  = FIXED

AT_AUTO_CONNECT                = VERIFIED
AT_MANUAL_RECONNECT            = VERIFIED
AT_FAILURE_BLOCKS_LLM          = NO

RUN_PROGRESS_STATUS            = READY
TEMP_DIAGNOSTICS               = REMOVED

AT_ENABLED_EXPERIMENT          = REMOVED
NOAT_FLAVOR                    = REMOVED
DIAGNOSTIC_CONNECT_TO          = REMOVED

EMG_TIMEOUT                    = ADEQUATELY_EXTENDED (600s)

AG_CORE                        = UNCHANGED
AG_RUNTIME                     = UNCHANGED
AT_ENGINE                      = UNCHANGED
HANDOFF                        = UNCHANGED

SECRET_LEAKAGE                 = NONE
READY_FOR_AGENT_0_LIVE         = YES
```

STOP HERE. AGENT-0.LIVE / CONTRAST / STABILITY not started; await Controller Review.