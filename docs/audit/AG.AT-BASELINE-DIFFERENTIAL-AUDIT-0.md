# AG.AT-BASELINE-DIFFERENTIAL-AUDIT-0 — AG Current AT v0.4.0 vs Candidate AT v0.4.2

TYPE = READ_ONLY / CURRENT_STATE_AUDIT / DIFFERENTIAL_COMPATIBILITY_AUDIT / AG_BASELINE_DECISION_PREFLIGHT
DATE = 2026-11-03
COMMIT = NO (Controller review input only; task §62)

```text
PRINCIPLE = EVIDENCE > VERSION RECENCY
            Do not upgrade because v0.4.2 is newer.
            Do not retain v0.4.0 merely because it is current.
            Choose on: actual AG dependency + compatibility evidence
            + regression evidence + material benefit + remaining validation cost.
```

---

## 1. AG Current Baseline (verified on-site)

```text
AG_REPO        = D:\codebank\AndCode\AndroidAgent-V2
AG_BRANCH      = main
AG_HEAD        = c90f6d1bffebf80c16980644073d5057c2a64e70  (matches expected)
AG_WORKTREE    = CLEAN (git status --short empty)
```

## 2. §4 — Prove What AT Version AG Actually Uses

```text
CURRENT_AG_AT_BASELINE = v0.4.0 (Controller assumption CONFIRMED)

EVIDENCE CHAIN:
1. docs/integration/AT_TO_AG_HANDOFF_CURRENT.md (canonical pointer, read-only):
   SOURCE_BASELINE    = 7216d0e8131e92e8914ce7c8443e1668b1d53aca (freeze commit)
   FREEZE_TAG         = AT.V4-CONSUMER-FREEZE-V1
   AT_CONTRACT_STATUS = FROZEN (V4 consumer boundary)
   AT_PUBLIC_CONTRACT = com.at.engine.api:at-engine-api:4.0.0
2. AT repo (D:\codebank\AndCode\AndroidTerminal-V4-LOCAL-TCP-042, verified):
   git rev-list -n 1 v0.4.0 = 7216d0e8131e92e8914ce7c8443e1668b1d53aca
   v0.4.0 tag target commit = "AT.V4-6: close device parity and freeze V4
   consumer boundary" (2026-10-22) => v0.4.0 tag target == 7216d0e EXACT.
   (7216d0e is ALSO tagged AT.V4-CONSUMER-FREEZE-V1 — same commit, two tags.)
3. AG repo physical:
   settings.gradle.kts -> repo-local Maven third_party/at-v4-consumer-freeze-v1/m2
   (V3 m2 entry commented out; V0 handoff not in dependency graph)
   third_party/at-v4-consumer-freeze-v1/MANIFEST.md:
     Freeze commit 7216d0e, variant = debug, 8 artifacts @4.0.0
   at-engine-api-4.0.0.jar sha256 =
     b6b28fb1a88eb3139d809e2732534d0f2e4254e59abb4ef9e059380e2ddff1f0
     (23079 bytes; class list extracted and used for the API surface below)

BASELINE_ASSUMPTION_MISMATCH = NO

CURRENT_AG_AT_SOURCE            = vendored AAR/JAR via repo-local Maven
CURRENT_AG_AT_VERSION_EVIDENCE  = MANIFEST.md + m2 coordinates 4.0.0 + tag target
CURRENT_AG_AT_ARTIFACT          = com.at.engine.api:at-engine-api:4.0.0 (jar,
                                  public contract) + com.at.composition.android:
                                  at-composition-android:4.0.0 (aar, entry) +
                                  com.at.cli:at-cli:4.0.0 (aar, optional)
                                  + 5 internal transitive (at-engine, at-engine-spi,
                                  at-backend-local, at-backend-adb, adb-engine)
CURRENT_AG_AT_CONSUMPTION_MODULE = :integration-at (AtExecutionAdapter; the ONLY
                                  AT consumption point) + :app (composition root:
                                  ATAndroid.create, CompositionAdbSemantic)
                                  + :tool-android-shell (thin one-shot consumer)
```

## 3. §5/§6 — Exact AT Release Identities

```text
AT_V040_TAG          = v0.4.0
AT_V040_TAG_TARGET   = 7216d0e8131e92e8914ce7c8443e1668b1d53aca
AT_V040_RELEASE_IDENTITY = VERIFIED (tag target == AG consumed freeze commit, EXACT)

AT_V042_TAG          = v0.4.2
AT_V042_TAG_TARGET   = 925e7ac223d19eea7ddb89bf05e55df25ca7a7f2
MATCH_EXPECTED       = YES (matches Controller-expected SHA exactly)
AT_V042_IDENTITY     = annotated tag, versionName 0.4.2 / versionCode 4,
                       tag commit 925e7ac "docs: finalize AndroidTerminal
                       v0.4.2 release evidence" (2026-11-03)
AT_V041_TAG_TARGET   = cbc20985b2154d91e11b29f53823b182322bca72
                       (v0.4.1 = V2 signing lineage + Wireless ADB identity
                        lifecycle; 46 commits total between v0.4.0 and v0.4.2)
```

Comparison identities are FROZEN for this audit:

```text
CURRENT   = AndroidAgent-V2 @ c90f6d1 + AT v0.4.0 tag target 7216d0e
CANDIDATE = same AndroidAgent-V2 source + AT v0.4.2 tag target 925e7ac
```

## 4. §8 — Current AG Execution Architecture (from current source)

```text
AG_CURRENT_EXECUTION_CHAIN =
  Model ToolCall {"command": "..."}
    -> AgentCore tool loop (ag-core, AT-free)
    -> AndroidShellToolImpl.execute (tool-android-shell; Executor seam)
    -> AtExecutionAdapter.executeLocal(command)          [integration-at]
         = "cd '<appFilesDir>' && <cli-bootstrap-source-line> && <command>"
         -> localEngine.execute(ATExecutionRequest(wrapped, 30_000))
            (localEngine = ATEngine injected by Host from ATAndroid.create;
             V4 default = LocalOneShotBackend: ProcessBuilder /system/bin/sh)
    -> ATExecutionResult(outcome, stdout, stderr, exitCode, hasError, error)
    -> ShellResultMapper.toToolResult
         COMPLETED      -> ToolResult(success=true,  payload JSON)
         else (TRANSPORT_ERROR / UNSUPPORTED)
                         -> ToolResult(success=false, error=...)
    -> ToolResult into Run history

  ADB path (NOT the model default, but integrated):
    AtExecutionAdapter.executeAdb(command) -> adbEngine.execute(...)
    In-shell `adb ...` CLI (app_process AtCliMain -> AdbCliBridgeServer UDS
    -> ATCliExecution -> adbEngine) — CLI bridge started at composition init.

AG_CURRENT_INTERRUPT_CHAIN =
  UI Stop (HostActions) -> AgentRuntime.interruptActiveRun()
    -> execution child Job.cancel() (real structured coroutine cancellation)
    -> cancellation propagates Core -> Tool -> adapter withContext(Dispatchers.IO)
       -> native AT execute call (CANCELLATION BEHAVIOR AT NATIVE BOUNDARY
          NOT VERIFIED — PRE_EXISTING_UNKNOWN)
    -> Core finalizes Interrupted + exactly one RunFinished(Interrupted)
    -> send() converts to RunOutcome.Interrupted (USER claim)

AG_CURRENT_SESSION_EXECUTION_MODEL =
  One durable TopicSession per conversation (Room SSOT, DB v1).
  AgentRuntime owns single-active-run per session (structured execution child
  Job). Max ONE active Run per session; Host (AGA) has one SessionHolder +
  busy guard. Session reusable when Run coroutine reaches terminal.
  Transcript = presentation projection only.
```

## 5. §9 — Architecture Boundary Verification (measured)

```text
AG_CORE_AT_DIRECT_DEPENDENCY    = NO (0 AT imports in ag-core/src, measured)
AG_RUNTIME_AT_DIRECT_DEPENDENCY = NO (0 AT imports in ag-runtime/src, measured)

AT-type import locations (measured, all 50 occurrences classified):
  :integration-at   com.at.engine.* (ATEngine, ATExecutionRequest/Result/Outcome,
                    ATOpenSessionResult, ATSessionEvent, ATSessionLaunch,
                    ATTerminalSession) + com.at.cli.* (AdbCliBridgeServer,
                    ATCliExecution, ATCliPairingResult/State, LocalCliBootstrap)
  :tool-android-shell  com.at.engine.ATExecutionResult/ATExecutionOutcome ONLY
  :app (composition root)  com.at.composition.android.* (ATAndroid, ATAndroidConfig,
                    ATAndroidAdbControl, ATAdbBootstrapState, ATAdbReadinessState,
                    ATAdbPairingDiscovery, ATAdbPairingResult)
                    + com.at.engine.ATTerminalSession in DebugBridge (debug only)
  NO com.adb.engine.* import anywhere in AG (verified: 0)
  NO com.at.backend.* import anywhere in AG (verified: 0)

ARCHITECTURE_BLOCKER = NO
Chain holds: AG Core -> Tool contract -> concrete shell tool -> :integration-at
-> AT public execution boundary (at-engine-api) / composition facade (:app only).
```

## 6. §10 — AG v1 Execution Requirements (derived from AG source, not AT feature list)

| Capability | AG v1 Requirement | Current Usage | Evidence |
|---|---|---|---|
| Local one-shot (app UID) | REQUIRED_NOW | PRIMARY model-visible path (AndroidShellToolImpl default) | tool-android-shell prod ctor -> executeLocal |
| ADB one-shot (shell uid) | REQUIRED_NOW | adapter.executeAdb + in-shell `adb` CLI (model uses for privileged ops) | AtExecutionAdapter.executeAdb; CLI bridge |
| stdout | REQUIRED_NOW | ToolResult payload | ShellResultPayload |
| stderr | REQUIRED_NOW | ToolResult payload | ShellResultPayload |
| exitCode | REQUIRED_NOW | ToolResult payload; non-zero != tool failure | ShellResultMapper |
| timeout | REQUIRED_NOW | ATExecutionRequest.timeoutMs (default 30s); AG has NO independent coroutine timeout | AtExecutionAdapter.executeLocal |
| cancellation | REQUIRED_NOW | AG Stop -> execution Job.cancel(); Tool = suspend on Dispatchers.IO | AG.RUN-CONTROL-1.DETERMINISTIC.FIX |
| structured failure | REQUIRED_NOW | outcome enum + error string -> ToolResult.error | ShellResultMapper |
| ADB readiness / pairing state | REQUIRED_NOW | ShellConnection.connect/readiness -> Host ADB indicator + pair UI | ChatActivity ADB dot/pair row |
| cwd continuity (app files) | REQUIRED_NOW | executeLocal wraps `cd <appFilesDir>` | AtExecutionAdapter |
| env continuity (Local PTY) | FUTURE | not used by model path | ATSessionLaunch.env unused by AG |
| persistent ADB PTY | OPTIONAL_NOW | adapter exposes openAdbSession; NOT used by model-visible tool (debug/CLI only) | AtExecutionAdapter.openAdbSession |
| persistent Local PTY | FUTURE | adapter exposes openLocalSession/openBootstrappedLocalSession; no model consumer | — |
| stdin / interactive | FUTURE | adapter writeCommand/writeCtrlC exist; no model consumer | — |
| resize | FUTURE | adapter.resize exists; no model consumer | — |
| multi-session concurrency | FUTURE | AG enforces max-1 Run per session; no parallel-Run consumer | AgentRuntime |
| transport loss / recovery | REQUIRED_NOW (AT-owned) | AdbRuntime owns recovery; AG must not re-own it | AdbSemantic.ensureReady only |
| process-death / Activity recreation | REQUIRED_NOW | Host SessionHolder + composition root; AT engine process-scoped ownership | ChatCompositionRoot |

Key rules honored: PTY/interactive are NOT AG v1 requirements (adapter surface
exists for future; model path is one-shot). AG requirements come from AG source.

## 7. §11 — AG v0.4.0 Consumed API Surface (measured from vendored JAR + source)

| API | Caller | Purpose | Input | Output | Error | Lifecycle | Cancellation |
|---|---|---|---|---|---|---|---|
| ATEngine.execute(request) | AtExecutionAdapter.executeAdb/executeLocal | one-shot command | ATExecutionRequest(command, timeoutMs) | ATExecutionResult(outcome, stdout, stderr, exitCode, hasError, error) | outcome=TRANSPORT_ERROR/UNSUPPORTED + error string | stateless per call | NOT VERIFIED (blocking native call inside withContext(IO)) |
| ATEngine.openSession(launch) | adapter.openAdbSession/openLocalSession (NOT model path) | persistent PTY | ATSessionLaunch(command, rows, cols[, env]) | ATOpenSessionResult.Success(session)/Failure(reason) | Failure.reason string | session object owned by caller | close(terminateProcess) |
| ATTerminalSession.write/read/resize/close/setListener/state | adapter session helpers (not model path) | raw PTY I/O | bytes / timeoutMs | Int / ByteArray? / Boolean | — | caller-owned | — |
| ATAndroid.create(context, config) | ChatCompositionRoot (Host) | composition entry | Context, ATAndroidConfig(identityProfile) | ATAndroidComposition(localEngine, adbEngine, adbControl) | — | process-scoped, once | n/a |
| ATAndroidAdbControl.bootstrapState() | CompositionAdbSemantic | consumer bootstrap projection | — | ATAdbBootstrapState (PAIRING_REQUIRED/CONNECT_FAILED/READY/USER_ACTION_REQUIRED_WD/ENDPOINT_NOT_FOUND/NOT_READY) | — | stateless read | n/a |
| ATAndroidAdbControl.readinessState() | CompositionAdbSemantic | ADB readiness | — | ATAdbReadinessState (READY/DISCONNECTED/CONNECTING/else->UNKNOWN) | — | stateless read | n/a |
| ATAndroidAdbControl.ensureReady(onStatusLine) | ShellConnection.connect (Host action) | readiness gate + auto-WD + discovery + connect | callback | Boolean | status lines via callback | blocking (withContext IO) | NOT VERIFIED |
| ATAndroidAdbControl.hasIdentity() | CompositionAdbSemantic / CLI execution | identity material present | — | Boolean | — | stateless | n/a |
| ATAndroidAdbControl.discoverPairing(timeoutMs) | CompositionAdbSemantic | scan pairing service | Long | ATAdbPairingDiscovery(endpoints, serviceNotFound, multiple) | — | blocking | NOT VERIFIED |
| ATAndroidAdbControl.pair / pairDiscovered | CompositionAdbSemantic (Host pair UI) | pair with code | host, port, code | ATAdbPairingResult(success, outcome enum, deviceGuid, error) | outcome enum | blocking | NOT VERIFIED |
| ATAndroidAdbControl.addLifecycleListener / removeLifecycleListener | NOT consumed by AG (available) | state transitions | Function2 | handle | — | — | — |
| ATCliExecution (interface impl by AG adapter) | AtExecutionAdapter -> AdbCliBridgeServer | in-shell `adb` CLI backend | command | OneShotResult(status, stdout, stderr, error) | status string | bridge thread | NOT VERIFIED |
| AdbCliBridgeServer(dir, exec).start/stop/socketPath | AtExecutionAdapter (composition init) | UDS CLI bridge | File | Boolean | log | started at init | stop() |
| LocalCliBootstrap(mainClass).writeScript/sourceLine | AtExecutionAdapter | generate `adb` shell function | paths | String script/source | null on failure | canonical file owner | n/a |

NOT consumed (AG does not use): ATAndroidAdbControl.completeFirstUseBootstrap,
ATAndroidAdbControl lifecycle listeners, ATSessionEvent stream (session path only),
ATSessionLaunch.env, ATOpenSessionResult for model path.

---

## 8. Controller Closeout Note (added by AG.AT-V042-PREFLIGHT-AUDIT-CLOSEOUT-0, 2026-11-04)

Historical reconciliation completed
(AG.AT-HISTORICAL-EVIDENCE-RECONCILIATION-0).

No v0.4.2 regression discovered.

The previous unknowns (GAP-1 interrupt last-hop, GAP-2 orphan-execution) were
narrowed by the historical reconciliation to four one-shot native
termination / orphan-absence gaps:
G1 Local one-shot Stop, G2 Local one-shot timeout,
G3 ADB CLI one-shot cancellation, G4 ADB one-shot timeout.
All remain TRULY_UNVERIFIED and are the P0 core of the v0.4.2 regression
gates (G-b1/G-b2/G-c1/G-c2; G-g is normal ADB CLI execution only).

Next direction remains v0.4.2 consumer sync
(V042_SYNC_DIRECTION = PROCEED_TO_V042_CONSUMER_SYNC), followed by the
narrow 7-gate validation.

This note does not retroactively attribute the recovered historical evidence
to this point-in-time audit.
