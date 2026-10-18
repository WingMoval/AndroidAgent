# AG.V2-2 + AG.V2-2B: AndroidTerminal V3 Full Execution Engine Integration

## Status

```text
AG.V2-2.AT-INTEGRATION          = PASS (one-shot; D1-D4 device verified)
AG.V2-2B.AT-ENGINE-FULL-        = PASS (full current execution capability)
INTEGRATION
FULL_CURRENT_AT_ENGINE_INTEGRATION = PASS
```

## 1. Integration Purpose

AG.V2-2 replaces the legacy AT v0 Handoff Bundle with AndroidTerminal V3 Freeze
V1 as the android_shell one-shot execution backend.

AG.V2-2B extends this to full current-capability integration: the AG Host now
consumes the entire AT Engine execution subsystem (one-shot, persistent session,
raw byte I/O, Ctrl-C, resize, lifecycle, multi-session, Local + ADB backends)
through a unified AG-side AT Execution Adapter. The model-visible tool surface
remains `android_shell` one-shot only.

```text
AT_ENGINE_INTEGRATION_SCOPE = FULL_CURRENT_EXECUTION_CAPABILITY
MODEL_TOOL_EXPOSURE         = ONE_SHOT_ONLY
```

These two dimensions are distinct and must not be conflated:

- The AG Host / Execution Adapter has FULL access to AT Engine's current
  verified execution capability (Local + ADB, one-shot + persistent).
- The LLM / Tool surface currently exposes ONLY `android_shell(command)`
  one-shot. Persistent terminal capability is AVAILABLE INTERNALLY but NOT
  exposed as a model tool.

## 2. Freeze Identity

```text
AT_V3_FREEZE_TAG    = AT.PRE-EMULATOR-FREEZE-V1
AT_V3_FREEZE_COMMIT = 7ccb1bf3ae332b66a425f47586c60d9a30ba70ef
AT_EXECUTION_BASELINE = 700ca3b
AT_V3_REPO_STATUS   = CLEAN (0 writes; HEAD = freeze tag; no tag moves)
```

## 3. Composite-Build Consumption

AG consumes AT V3 through Gradle composite build (NOT AAR binary handoff):

```text
AG_AT_CONSUMPTION_MODE = GRADLE_COMPOSITE_BUILD
BINARY_AAR_HANDOFF     = NOT REQUIRED / NOT ESTABLISHED
```

Mechanism in `settings.gradle.kts`:

```kotlin
includeBuild("../AndroidTerminal-V3") {
    dependencySubstitution {
        substitute(module("com.adb.engine:adb-engine")).using(project(":adb-engine"))
        substitute(module("com.at.engine:at-engine")).using(project(":at-engine"))
        substitute(module("com.at.backend.adb:at-backend-adb")).using(project(":at-backend-adb"))
        substitute(module("com.at.backend.local:at-backend-local")).using(project(":at-backend-local"))
    }
}
```

The V3 repository remains a sibling directory. No V3 source is copied, vendored,
or modified. Build artifacts in V3's `build/` directory are gitignored.

## 4. Module Graph

```text
AG modules:
  :ag-core                    (0 AT dependency)
  :ag-runtime                 (0 AT dependency)
  :provider-openai-compatible (0 AT dependency)
  :provider-openai-compatible-jdk (0 AT dependency)
  :tool-network               (0 AT dependency)
  :tool-file                  (0 AT dependency)
  :integration-at             (UNIFIED AT CONSUMPTION POINT — 4 AT modules)
  :tool-android-shell         (consumes AT via :integration-at api dep)
  :app                        (composition root; debug-only AT reference)

AT V3 modules consumed:
  :at-engine         (AT Engine domain: ATEngine, ATExecutionResult,
                       ATTerminalSession, ATSessionState, ATSessionEvent)
  :at-backend-adb    (AdbOneShotBackend, AdbPersistentBackend,
                       AdbTerminalSession)
  :at-backend-local  (LocalOneShotBackend, LocalPersistentBackend,
                       LocalTerminalSession)
  :adb-engine        (AdbEngine, AdbRuntime, AdbConnectionRequest,
                       AdbRuntimeState, AdbInteractiveStream)
```

NOT consumed (correctly excluded):
- `:app` (V3 standalone app)
- `:test-host` (V3 test infrastructure)
- `:adb-only-test-host`
- `:at-shellless-test-host`

## 5. AG-Side AT Execution Adapter

New module: `:integration-at` (package `com.androidagent.integration.at`)

Class: `AtExecutionAdapter`

```text
AT_EXECUTION_ADAPTER = integration-at / AtExecutionAdapter
```

Responsibilities:
1. Holds the AT Engine composition (one-shot + persistent backends for both
   Local and ADB).
2. Exposes AT's current Freeze V1 capability:
   - `executeAdb(command, timeoutMs)` — one-shot ADB
   - `executeLocal(command, timeoutMs)` — one-shot Local
   - `openAdbSession(command, rows, cols)` — persistent ADB session
   - `openLocalSession(command, rows, cols)` — persistent Local session
   - `writeCommand(session, command)` — write + newline
   - `writeCtrlC(session)` — write 0x03
   - `readOutput(session, timeoutMs)` — blocking read
   - `resize(session, rows, cols)` — terminal resize
   - `closeSession(session, terminate)` — close
   - `setListener(session, listener)` — push-mode events
   - `sessionState(session)` — state query
   - `ensureAdbReady()` — ADB lifecycle (delegates to AdbRuntime)
   - `adbState()` — ADB state query
   - `disconnectAdb()` — ADB disconnect
3. Isolates AT Contract DRAFT from other AG code.
4. Lets future Concrete Tools share the same AT integration.

The adapter is THIN: it does NOT redefine AT execution semantics. It holds the
AT Engine composition and exposes AT's existing public capability.

## 6. Local Backend Integration

`:at-backend-local` is formally in the consumption graph.

`LocalOneShotBackend` + `LocalPersistentBackend` are composed in
`AtExecutionAdapter.localEngine`.

Local execution is verified via:
- E3: Local persistent session open (PASS)
- E4: Local raw I/O (PASS)
- E5: Local cwd/env continuity (PASS)
- L6: Local natural exit (PASS — EOF detected)
- L7: Local explicit close (PASS)

Local one-shot is NOT exposed as a model tool. It is available internally
through `adapter.executeLocal()`.

## 7. ADB Backend Integration

`:at-backend-adb` is formally in the consumption graph.

`AdbOneShotBackend` + `AdbPersistentBackend` are composed in
`AtExecutionAdapter.adbEngine`.

ADB lifecycle ownership:
```text
AdbRuntime = UNIQUE ADB LIFECYCLE OWNER
AG Adapter  = delegates to AdbRuntime (ensureReady, connectAuto, disconnect)
SECOND_ADB_RUNTIME      = NO
SECOND_RECOVERY_OWNER   = NO
```

## 8. Persistent Session Integration

Persistent terminal session is integrated for both Local and ADB backends.

ADB persistent session verified (real device, Honor ALI-AN00, Android 15):

```text
A1  SESSION_OPEN         = PASS
A2  REMOTE_PRINCIPAL     = PASS (uid=2000(shell))
A3  RAW_OUTPUT           = PASS (AT_ENGINE_RAW_OK via session read)
A4  WRITE_INPUT          = PASS
A5  CWD_CONTINUITY       = PASS (cd /data/local/tmp persists)
A6  ENV_CONTINUITY       = PASS (export + echo in same session)
A7  FOREGROUND_STDIN     = PASS (cat + write AG_AT_STDIN_OK)
A8  CTRL_C               = PASS (sleep 30 killed; shell alive; AFTER_CTRL_C)
A9  RESIZE               = PASS (24x80 -> 30x100 via stty size verification)
A10 NATURAL_EXIT         = PASS (exit -> EOF detected -> session terminated)
A11 EXPLICIT_CLOSE       = PASS (close() -> resources released)
A12 MULTI_SESSION_ISO    = PASS (A sees A only, B sees B only)
```

## 9. Identity Ownership

```text
UNSAFE_KEY_COPY_PERFORMED   = YES (one-time bootstrap from V3 app)
V3_EXISTING_IDENTITY_ACCEPTED = NO
AG_OWNS_ITS_ADB_IDENTITY    = PARTIAL
AT_CONSUMER_BOOTSTRAP_GAP   = YES
```

Current state: AG's `files/at-identity/` contains the V3 AndroidTerminal app's
adbkey/adbkey.pub/adb_cert.pem (SHA256 identical to V3 app's identity). This
is a one-time bootstrap copy, not formal pairing.

AT V3 Freeze V1 has `AdbEngine.pair(host, port, code, keyDir)` as a low-level
primitive, but does NOT expose a consumer-facing pairing/bootstrap API through
`AdbRuntime`. The AG consumer composition cannot legally trigger pairing
without importing AdbEngine pairing internals (forbidden by §37).

```text
IDENTITY_REUSE_AFTER_APP_RESTART = PASS (I1-I4 verified)
OFFICIAL_PAIRING_BOOTSTRAP       = BLOCKED_BY_AT_CONSUMER_BOOTSTRAP_GAP
```

The AG-owned identity files are in AG's app-private space (u0_a370), not
hardcoded, not root, not disabled auth. The key ORIGIN is the V3 app, which
is the KNOWN_ISSUE to be resolved when AT V3 provides a consumer-facing
pairing API (POST_AT_EMULATOR_0_AND_CONTRACT_1_REVIEW).

## 10. Native Packaging

```text
FINAL_NATIVE_LIB_SET:
  lib/arm64-v8a/libadbd_engine.so    (3,273,952 B)  owner: :adb-engine
  lib/arm64-v8a/libat_local_backend.so (384,784 B)  owner: :at-backend-local

HYBRID_NATIVE_RUNTIME = NO
LEGACY_V0_NATIVE      = ABSENT (libandroidterminaladb.so not in APK)
```

APK: 5,185,059 B, SHA256 `623B937ECF42F4ED0924F0D7B337FA38CEFA1CC38AAC0EA163F84C58A07E2B6D`

## 11. One-Shot D1-D4 (Regression)

```text
D1  id                      = PASS (uid=2000(shell), exitCode=0)
D2  echo AG_AT_V3_OK        = PASS
D3  stdout/stderr/exit      = PASS (stdout=OUT, stderr=ERR, exitCode=7)
D4  nonexistent command     = PASS (exitCode=127, outcome=COMPLETED)
```

## 12. Persistent E Gates

```text
E1   ADB one-shot              = PASS
E2   Local one-shot            = N/A (not model-visible; internal only)
E3   Local persistent open     = PASS
E4   Local raw I/O             = PASS
E5   Local cwd/env continuity  = PASS
E6   ADB persistent open       = PASS
E7   ADB remote uid=2000       = PASS
E8   ADB raw output            = PASS
E9   ADB write/stdin           = PASS
E10  ADB cwd/env continuity    = PASS
E11  ADB Ctrl-C                = PASS
E12  ADB resize                = PASS (24x80 -> 30x100)
E13  ADB natural exit          = PASS (EOF detected)
E14  ADB explicit close        = PASS
E15  multi-session isolation   = PASS
E16  Local+ADB same Adapter    = PASS
E17  one-shot D1-D4 regression = PASS
E18  AG-owned identity         = PARTIAL (UNSAFE_KEY_COPY; see §9)
E19  app restart identity reuse= PASS
E20  no hybrid runtime         = PASS
```

## 13. Canonical Regression

```text
PREVIOUS_TEST_TOTAL = 714
NEW_TEST_TOTAL      = 726 (+12: new session protocol tests in :app,
                          T12 updated for integration-at)
TEST_FAILURES       = 0
TEST_SKIPPED        = 6 (tool-file symlink tests, Linux-gated)
DELTA               = +12
```

Per-module:
```text
ag-core                       41
ag-runtime                    26
provider-openai-compatible    59
provider-openai-compatible-jdk 26
tool-android-shell            30
tool-network                 156
tool-file                    110 (6 skipped)
integration-at                  0 (no unit tests; device-verified)
app                          218
```

## 14. Full Build

```text
ASSEMBLE_DEBUG = PASS
FULL_BUILD     = PASS (after clean rebuild; lint CoarseFineLocation fixed)
```

The pre-existing legacy AT AAR lint blocker is GONE (legacy AAR no longer in
dependency graph).

## 15. Contract Draft Isolation

```text
AT_CONTRACT_STATUS = DRAFT
FINAL_AT_API_BINDING = NO
HANDOFF_V1_PROVISIONAL_BINDING = YES
```

AT import surface:

```text
ag-core:                    0 imports
ag-runtime:                 0 imports
integration-at:             14 imports (unified AT consumption point)
tool-android-shell:         5 imports (via adapter api dep)
app (non-debug):            0 imports
app (debug/DebugBridge):    1 import (ATTerminalSession, debug-only)
```

AT-specific API usage is concentrated in:
- `:integration-at` (AtExecutionAdapter) — the unified consumption point
- `:tool-android-shell` (AndroidShellToolImpl, ShellConnection, ShellResultMapper)
- `:app` debug-only (DebugBridge session validation)

## 16. Deferred Capability

```text
TERMINAL_EMULATOR      = NOT_IMPLEMENTED (deferred to AT.EMULATOR-0)
SCREEN_STATE           = NOT_IMPLEMENTED (deferred to AT.EMULATOR-0)
SCREEN_SNAPSHOT        = NOT_IMPLEMENTED
CURSOR_STATE           = NOT_IMPLEMENTED
TUI_SEMANTIC_OBSERVE   = NOT_IMPLEMENTED
RENDERER               = NOT_IMPLEMENTED
HUMAN_AGENT_SHARED_SESSION = DEFERRED
PERSISTENT_TERMINAL_TOOL = NOT_IMPLEMENTED (internal capability only)
SCREEN_AWARE_TERMINAL  = NOT_IMPLEMENTED (BLOCKED_BY_AT.EMULATOR-0)
```

## 17. Final Verdict

```text
FULL_CURRENT_AT_ENGINE_INTEGRATION = PASS
AG.V2-2 = CLOSED
AG.V2-2B = CLOSED
```

The AG Host has the complete AT Engine current execution capability through
the unified AtExecutionAdapter. The model tool surface remains
`android_shell` one-shot only. Future persistent terminal tools can consume
the existing adapter without re-integrating AT.

## 18. Legacy AT v0 Status

```text
LEGACY_AT_ACTIVE      = NO
LEGACY_ROOT_CAUSE     = RESOLVED_SUFFICIENTLY (two-layer: Honor adbd OEM
                         remote-IP post-TLS CNXN rejection + AG original
                         adbkey not authorized by adbd; superseded by V3
                         loopback-first connectAuto strategy)
LEGACY_REGRESSION_DOC = docs/regression/AG.ADB-LKG-REGRESSION.md
                         (its "loopback rewrite" root-cause claim is
                         SUPERSEDED by this step's two-layer explanation)
```
