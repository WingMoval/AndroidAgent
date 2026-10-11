# AGENT-0.LIVE — Real Agent Adaptive Tool Loop Verification

> STEP: AGENT-0.LIVE (REAL AGENT ADAPTIVE TOOL LOOP VERIFICATION)
> BASELINE HEAD: `2359f9b`
> Verdict: **AGENT_0_LIVE_PASS**

This Step verifies the full autonomous Agent loop on a real device with a real
LLM: User Goal → Model → Tool → Real Observation → Model → dependent Next Tool →
Observation → Final Answer. No production code was changed.

---

## 1. Baseline

```text
start HEAD : 2359f9b (feat: consolidate AGA host controls before agent live)
working tree : 3 untracked diagnostic fixtures retained (ProviderRealDeviceProbeTest.kt,
               OpenRouterPcProbe.kt, docs/diagnostics/) — untouched this Step
production diff : NONE
```

## 2. Device / Provider

```text
DEVICE         = APLMUT4312005935 (HONOR ALI-AN00, Android 15, arm64-v8a)
PROVIDER       = OpenRouter (primary; no fallback used)
MODEL          = deepseek/deepseek-v4-flash-0731
AT_CONNECTED   = YES (auto-connect; reconnect after enabling Wireless Debugging)
execution path : AG Core → Tool contract → android_shell → AndroidShellToolImpl
                 → third_party/at-engine-handoff-v0 → adbd → real Android
```

## 3. Exact User Goal

```text
Find the app currently in the foreground on this phone, and tell me its package
name, version information, and APK path.
```

(ASCII form used because the Chinese IME interfered with automated text entry;
semantically identical to the Chinese goal. No package name, command, or workflow
was given to the model.)

## 4. Trace Summary (from AgentTraceCollector / AgentEvent)

```text
RunStarted
step 1  ModelTurn → ToolCall(android_shell)
        args: dumpsys window ... | grep mCurrentFocus ...
step 2  ToolCall(android_shell) args: dumpsys activity activities ... grep ResumedActivity...
        ToolResult: topResumedActivity=ActivityRecord{... com.androidagent.app/.ChatActivity}
step 3  (model uses more dumpsys probes; foreground stays com.androidagent.app)
step 4  ModelTurn → ToolCall(android_shell)
        args: pm path com.androidagent.app; dumpsys package com.androidagent.app | grep -iE "versionName|versionCode|..."
        ← arguments carry P = com.androidagent.app (learned in steps 1-3)
        ToolResult: package:/data/app/~~QFN-hBCgRnNpkx-1hTZqTg==/.../base.apk
                   versionName=0.1.0 versionCode=1 minSdk=26 targetSdk=34
step 5  ModelTurn → FinalAnswer (full report: package, version, APK path)
RunFinished → outcome=answer
```

## 5. ToolCall A

```text
name      android_shell
arguments {"command": "dumpsys activity activities 2>/dev/null | grep -iE \"ResumedActivity|topResumedActivity|mResumedActivity|state=RESUMED|mFocused\" | head -20"}
```

## 6. Observation A

```text
success=true  outcome=COMPLETED  exitCode=0
stdout:
  topResumedActivity=ActivityRecord{fc9c236 u0 com.androidagent.app/.ChatActivity t17627}
  state=RESUMED delayedResume=false finishing=false
  ResumedActivity: ActivityRecord{fc9c236 u0 com.androidagent.app/.ChatActivity t17627}
  mFocusedApp=ActivityRecord{fc9c236 u0 com.androidagent.app/.ChatActivity t17627}
→ dynamic fact P = com.androidagent.app
```

## 7. ToolCall B (dependent)

```text
name      android_shell
arguments {"command": "pm path com.androidagent.app; echo \"===\"; dumpsys package com.androidagent.app 2>/dev/null | grep -iE \"versionName|versionCode|firstInstallTime|lastUpdateTime|minSdk|targetSdk\" | head -20"}
```

The arguments contain `com.androidagent.app` — the fact P learned from
Observation A.

## 8. Observation Dependency Proof

```text
P = com.androidagent.app
  appears in: Observation A (ToolResult stdout) — NOT in user prompt,
  NOT in AGENT_INSTRUCTIONS_V0, NOT in host workflow, NOT hardcoded.
P later appears in ToolCall B.arguments (pm path <P>; dumpsys package <P> ...).

⇒ P originated from Observation A → entered model history/context → affected Action B
OBSERVATION_DEPENDENCY = VERIFIED
```

## 9. Final Answer

```text
Package name : com.androidagent.app
Version      : versionName 0.1.0, versionCode 1, targetSdk 34, minSdk 26
               firstInstall 2026-10-05, lastUpdate 2026-10-09
APK path     : /data/app/~~QFN-hBCgRnNpkx-1hTZqTg==/com.androidagent.app-hMd62vDYlr80qiXn0UbSKw==/base.apk
```

## 10. Independent Ground Truth (after Agent run, via PC USB adb)

```text
AGENT_REPORTED_PACKAGE = com.androidagent.app
GROUND_TRUTH_PACKAGE    = com.androidagent.app        MATCH

AGENT_REPORTED_VERSION  = 0.1.0 / versionCode 1
GROUND_TRUTH_VERSION    = 0.1.0 / versionCode 1       MATCH

AGENT_REPORTED_APK      = /data/app/~~QFN-hBCgRnNpkx-1hTZqTg==/.../base.apk
GROUND_TRUTH_APK        = /data/app/~~QFN-hBCgRnNpkx-1hTZqTg==/.../base.apk  MATCH

FINAL_ANSWER_CORRECT = YES
```

Note: the foreground app at run time was AGA itself (com.androidagent.app), which
the Agent correctly identified from real device state.

## 11. Regression (real JUnit execution)

```text
:ag-core:test                        41 PASS
:ag-runtime:test                      8 PASS
:provider-openai-compatible:test     39 PASS
:provider-openai-compatible-jdk:test 24 PASS
:tool-android-shell:test             16 PASS
:app:testDebugUnitTest               48 PASS
:app:assembleDebug                  PASS
```

## 12. Secret Audit

```text
API key in git diff      = NONE
Authorization in logs    = NONE
raw provider JSON in logs = NONE
key prefix/length debug  = NONE
provider configs tracked = NO
```

## 13. Architecture Audit

```text
AG Core       : UNCHANGED
Agent Runtime : UNCHANGED
Tool contract : UNCHANGED
Handoff       : UNCHANGED
Legacy AT     : UNCHANGED
New AT migration : NOT_STARTED
ADB/AT concepts in AG Core or Runtime : NONE
Legacy backend remains replaceable via android_shell only
```

## 14. Known Limitations

```text
- The foreground app during the run was AGA itself; a two-environment contrast
  (AGENT-0.CONTRAST) is needed to show P varies with a different foreground app.
- Input used the ASCII goal form (IME interference); semantic equivalent.
- Single run; no repetition count beyond one observed multi-step pass.
- Full ToolCall/Result content is captured in AgentTrace (redacted-safe).
```

## 15. Verdict

```text
AGENT_0_LIVE_PASS

REAL_MODEL                    = VERIFIED
AUTONOMOUS_TOOL_SELECTION     = VERIFIED
REAL_DEVICE_EXECUTION         = VERIFIED
TOOL_RESULT_FEEDBACK          = VERIFIED
OBSERVATION_DEPENDENCY        = VERIFIED
FINAL_ANSWER_CORRECT          = YES
HOST_FIXED_WORKFLOW           = NO

AG_CORE                       = UNCHANGED
AG_RUNTIME                    = UNCHANGED
TOOL_CONTRACT                 = UNCHANGED
HANDOFF                       = UNCHANGED
LEGACY_AT_BACKEND             = UNCHANGED
NEW_AT_ENGINE_MIGRATION       = NOT_STARTED
LEGACY_BACKEND_ISOLATION      = PASS

SECRET_LEAKAGE                = NONE

READY_FOR_AGENT_0_CONTRAST    = YES
```

STOP HERE. AGENT-0.CONTRAST / STABILITY / FREEZE and AT migration not started;
await Controller Review.