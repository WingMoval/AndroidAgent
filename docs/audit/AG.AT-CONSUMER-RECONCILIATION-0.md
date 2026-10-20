# AG.AT-CONSUMER-RECONCILIATION-0 — Current AT V3 Consumer Capability Reconciliation Audit

> 性质：ARCHITECTURE AUDIT / CROSS-VERSION CAPABILITY RECONCILIATION / DEPENDENCY WIRING AUDIT。
> DOCS / EVIDENCE ONLY。PRODUCTION_CODE_CHANGE = NO / TEST_CODE_CHANGE = NO / AT_SOURCE_CHANGE = NO /
> DEVICE_TEST = NO / NEW_FEATURE = NO / AG_SSOT_NORMALIZATION = NO / TAG = NO / PUSH = NO。
>
> **状态**：`AG.AT-CONSUMER-RECONCILIATION-0 = CLOSED`
> **VERDICT**：`PASS`（审计完成，事实已确立，缺口已分类）

---

## 1. Executive Verdict

**当前 AG V2 未满足 Full Current AT V3 Consumer Integration（8 条中满足 4 条）。**

```text
CURRENT_AG_FULL_CURRENT_AT_V3_CONSUMER_INTEGRATION = NO
FULL_INTEGRATION_REQUIREMENTS_SATISFIED = 4/8
```

满足的 4 条：
- **3. ADB one-shot CALLABLE_BY_HOST** — PASS
- **6. Bootstrap CALLABLE_BY_HOST** — PASS
- **7. identity owned correctly** — PASS
- **8. AdbRuntime lifecycle correctly owned** — PASS

缺失的 4 条：

| # | Clause | Gap Classification |
|---|--------|--------------------|
| 1 | Local one-shot CALLABLE_BY_HOST | MISSING_ARTIFACT + MISSING_DEPENDENCY + MISSING_WIRING |
| 2 | Local persistent CALLABLE_BY_HOST | MISSING_ARTIFACT + MISSING_DEPENDENCY + MISSING_WIRING |
| 4 | ADB persistent CALLABLE_BY_HOST | PRESENT_BUT_NOT_CALLABLE（artifact+wiring 存在，adapter 暴露 openAdbSession 但无 model-visible tool） |
| 5 | AT CLI embedded WIRED_IN_COMPOSITION | MISSING_ARTIFACT + MISSING_DEPENDENCY + MISSING_WIRING（完全不存在） |

**PRIMARY_ROOT_CAUSE = INTENTIONAL_PRODUCT_SCOPE_REDUCTION**

直接因果证据存在于 commit `22e3f28` 的 diff 中（见 §16）。

capability surface **确实收缩**（YES）。

---

## 2. Source Identities

```text
AG_START_HEAD = 22e3f2899dc1ab12777cd0b1b55f48732877bf2f
AG_WORKTREE = CLEAN
AT_REQUIRED_HEAD = f55973d
AT_ACTUAL_HEAD = f55973db6d5839b87bd770884b22d4de2f68e65a
AT_HEAD_MATCH = YES
AT_WORKTREE = CLEAN
AT_TREE = 5daafafcfbe1809790d4caa9104c61283681a348
AT_FREEZE_TAG = a6f23d7112ca40c873b63cba4aef5686e0dd4405
AT_SOURCE_BASELINE = c9cee2126b3ebc54a62be27d1f7bdea8ded22a7f
AT_BASE_AUDIT = e92e6fcd1e494fc2e24e4179bfe5a58e456f7ca7
AT_AUDIT_INPUT = AT.CONSUMER-CAPABILITY-AUDIT-0.1
AG_RECONCILIATION_INPUT = AUTHORITATIVE / READY
```

---

## 3. AT Authoritative Input

Accepted from `AT.CONSUMER-CAPABILITY-AUDIT-0.md` (CLOSED) + `0.1` (PASS):

- AT provides capability, not one mandatory artifact/profile.
- Local one-shot / Local persistent PTY / ADB one-shot / ADB persistent / raw I/O /
  stdin / Ctrl-C / resize / multi-session / shell-visible adb / adb shell /
  adb pair / AdbConsumerBootstrap / identity / AdbRuntime = ALL AVAILABLE.
- Terminal Emulator / ScreenState / Screen Snapshot = NOT AVAILABLE.
- 7-layer integration taxonomy (L1-L7) accepted.
- 8-clause Full Current Integration normative definition accepted.
- ATEngine instance count is NOT a lifecycle invariant.
- Direct ADB = VALID CURRENT CAPABILITY (not superseded by CLI).
- AdbConsumerBootstrap = first-use pairing facade; AdbRuntime = normal readiness owner.

---

## 4. Historical AG Integration Timeline

```text
c76058f  AG.SHELL-1: first AT integration (v0 handoff bundle, composite/project dep)
         → :tool-android-shell directly consumed AT v0 (at-core + at-runtime AARs)
         → Device verified S1-S5 PASS

1410d5f  AG.V2-2 (commit message: "integrate full AndroidTerminal V3 execution engine")
         → :integration-at created with FULL capability:
           - Local one-shot (LocalOneShotBackend)
           - Local persistent (LocalPersistentBackend)
           - ADB one-shot (AdbOneShotBackend)
           - ADB persistent (AdbPersistentBackend)
           - executeLocal / executeAdb / openLocalSession / openAdbSession
         → Physical consumption: includeBuild("../AndroidTerminal-V3") + dependencySubstitution
           (resolves :at-engine, :at-backend-adb, :adb-engine, :at-backend-local from source)
         → AT snapshot: AndroidTerminal-V3 at whatever HEAD existed then (pre-freeze)
         → :at-cli NOT consumed (no ATCliExecution, no bridge, no LocalCliBootstrap)

22e3f28  AG.V2-2 (commit message: "integrate AT consumer freeze V1")
         → MIGRATION to vendored AAR (repo-local Maven, self-contained)
         → :at-backend-local REMOVED from dependency + adapter
         → localEngine / executeLocal / openLocalSession REMOVED from adapter
         → AdbConsumerBootstrap ADDED (was not in 1410d5f adapter)
         → Identity changed: "at-identity" → "at-consumer/identity" (self-generated)
         → includeBuild REMOVED; maven repo switched to third_party/at-consumer-freeze-v1/m2
         → Commit message: "No :at-backend-local (not needed for one-shot)"
         → Device verified: Gates A-I PASS (ADB one-shot + pairing + identity + restart)
```

**HISTORICAL_COMPOSITE_INTEGRATION_COMMIT = 1410d5f**
**VENDORED_AAR_MIGRATION_COMMIT = 22e3f28**

---

## 5. Current Physical Artifact Inventory

| Artifact | File | Size | SHA256 | Maven Coordinate | Native | In Current Dep Graph? | In Current APK? |
|----------|------|------|--------|-----------------|--------|----------------------|-----------------|
| at-engine.aar | third_party/at-consumer-freeze-v1/at-engine.aar | 23,778 B | BD2E0A9A...E28521 | com.at.engine:at-engine:1.0.0 | none | YES (api dep in integration-at) | YES (dex: com/at/engine) |
| at-backend-adb.aar | third_party/at-consumer-freeze-v1/at-backend-adb.aar | 8,627 B | 795CFE85...500DC52A4 | com.at.backend.adb:at-backend-adb:1.0.0 | none | YES (api dep in integration-at) | YES (dex: com/at/backend/adb) |
| adb-engine.aar | third_party/at-consumer-freeze-v1/adb-engine.aar | 1,161,599 B | EC7234AE...3A895CD66912 | com.adb.engine:adb-engine:1.0.0 | libadbd_engine.so | YES (api dep in integration-at) | YES (lib/arm64-v8a/libadbd_engine.so) |
| at-backend-local.aar | **ABSENT** | — | — | — | libat_local_backend.so (would be) | **NO** | **NO** (dex: com/at/backend/local = ABSENT) |
| at-cli.aar | **ABSENT** | — | — | — | libatcli_env.so (would be) | **NO** | **NO** (dex: com/at/cli = ABSENT) |

**APK provenance**: `app/build/outputs/apk/debug/app-debug.apk` (4,929,772 B, built 10/20/2026 01:43, from AG HEAD 22e3f28). BUILD_SOURCE_HEAD = 22e3f28. BUILD_WORKTREE = CLEAN (before/after).

---

## 6. Current Resolved Dependency Graph

```text
:app
  └── :integration-at
        ├── com.at.engine:at-engine:1.0.0          (api)
        ├── com.at.backend.adb:at-backend-adb:1.0.0 (api)
        │     ├── com.at.engine:at-engine:1.0.0    (transitive via POM)
        │     └── com.adb.engine:adb-engine:1.0.0  (transitive via POM)
        └── com.adb.engine:adb-engine:1.0.0        (api)

:tool-android-shell
  └── :integration-at (api)
```

**NOT in graph**: `:at-backend-local`, `:at-cli`, `com.at.backend.local:*`, `com.at.cli:*`.

---

## 7. Stale/Inactive Artifact Inventory

| Artifact | File Present? | Currently Resolved? | Classification |
|----------|--------------|--------------------:|----------------|
| third_party/at-engine-handoff-v0/ (entire tree) | YES | NO | STALE / INACTIVE ARTIFACT (legacy v0; not in any build.gradle; not in settings maven repo) |
| third_party/at-engine-handoff-v0/artifacts/*.aar | YES | NO | STALE / INACTIVE |
| third_party/at-engine-handoff-v0/m2/ (v0 Maven metadata) | YES | NO | STALE / INACTIVE |
| third_party/at-engine-handoff-v0/native/*.so | YES | NO | STALE / INACTIVE (libandroidterminaladb.so NOT in current APK) |

---

## 8. Current Composition Graph

```text
ChatActivity / DebugBridge (host)
    │
    ▼
ShellConnection (tool-android-shell)
    │  adapter: AtExecutionAdapter
    ▼
AtExecutionAdapter (integration-at) — THE UNIFIED AT CONSUMPTION POINT
    │
    ├── adbEngine: ATEngine (lazy)
    │     ├── oneShot = AdbOneShotBackend(placeholder)
    │     └── persistent = AdbPersistentBackend(placeholder)
    │
    ├── bootstrap: AdbConsumerBootstrap (lazy)
    │     └── identityDir = filesDir/at-consumer/identity
    │
    └── AdbRuntime (Kotlin object, process singleton)
          ├── initialize(appContext)
          ├── ensureReady(identityDir)
          ├── readinessState()
          └── disconnect()
```

**NOT constructed**: LocalOneShotBackend, LocalPersistentBackend, localEngine, ATCliExecution, AdbCliBridgeServer, LocalCliBootstrap.

---

## 9. Current Adapter Callable Surface

From `AtExecutionAdapter.kt` (current HEAD 22e3f28), exact public methods:

| Method | Capability | Evidence (line) |
|--------|-----------|-----------------|
| `initializeAdb()` | AdbRuntime.initialize | L90 |
| `ensureAdbReady(): Boolean` | AdbRuntime.ensureReady | L100 |
| `adbState(): AdbRuntimeState` | AdbRuntime.readinessState | L108 |
| `disconnectAdb()` | AdbRuntime.disconnect | L111 |
| `bootstrap: AdbConsumerBootstrap` | Consumer bootstrap facade | L122 |
| `bootstrapState(): AdbBootstrapState` | Readiness projection | L134 |
| `discoverPairing(timeoutMs): AdbPairingDiscovery` | Pairing discovery | L143 |
| `pairDiscovered(code, timeoutMs): AdbPairingResultV2` | Pair via discovery | L153 |
| `pair(host, port, code): AdbPairingResultV2` | Pair explicit | L161 |
| `executeAdb(command, timeoutMs): ATExecutionResult` | **ADB one-shot** | L174 |
| `openAdbSession(command, rows, cols): ATTerminalSession?` | **ADB persistent** | L187 |
| `writeCommand(session, cmd): Int` | Session write | L205 |
| `writeCtrlC(session): Int` | Ctrl-C | L210 |
| `readOutput(session, timeoutMs): ByteArray?` | Session read | L214 |
| `resize(session, rows, cols): Boolean` | Resize | L218 |
| `closeSession(session, terminate): Unit` | Close | L222 |
| `setListener(session, listener): Unit` | Event push | L226 |
| `sessionState(session): String` | State query | L230 |

**ABSENT** (compared to 1410d5f): `executeLocal()`, `openLocalSession()`, `localEngine` property.

---

## 10. CLI Embedding Audit

| Item | Status |
|------|--------|
| AT_CLI_ARTIFACT_FILE_PRESENT | NO (at-cli.aar does not exist in AG repo) |
| AT_CLI_CURRENTLY_RESOLVED | NO |
| AT_CLI_CURRENTLY_PACKAGED | NO |
| AT_CLI_CLASSES_IN_BASE_APK | NO (dex search: com/at/cli = ABSENT) |
| ATCLIEXECUTION_IMPL | NO (no class implements ATCliExecution in AG) |
| CLI_BRIDGE_STARTED | NO (no AdbCliBridgeServer reference) |
| LOCAL_CLI_BOOTSTRAP_PROVISIONED | NO (no LocalCliBootstrap reference) |
| SHELL_VISIBLE_ADB_CURRENTLY_AVAILABLE_IN_AG_LOCAL_ENV | NO |
| CLI_CURRENT_INTEGRATION_LEVEL | NONE |

---

## 11. Bootstrap / Runtime Ownership Audit

| Item | Value | Evidence |
|------|-------|----------|
| BOOTSTRAP_PACKAGED | YES | AdbConsumerBootstrap in adb-engine.aar (com.adb.engine) |
| BOOTSTRAP_WIRED | YES | AtExecutionAdapter.bootstrap (lazy AdbConsumerBootstrap) |
| BOOTSTRAP_CALLABLE | YES | bootstrapState/discoverPairing/pairDiscovered/pair all exposed |
| IDENTITY_DIR | filesDir/at-consumer/identity | DEFAULT_IDENTITY_DIR constant |
| IDENTITY_SELF_GENERATED | YES | Created by AdbEngine.pair on first pairing |
| KEY_COPY_ACTIVE | NO | No reference to AndroidTerminal app key |
| ADBRUNTIME_OWNER | AdbRuntime (Kotlin object) | Single process-scoped singleton |
| SECOND_ADBRUNTIME | NO | Only AdbRuntime.initialize/ensureReady called |
| SECOND_RECOVERY_OWNER | NO | No second recovery mechanism |
| TRANSPORT_OBSERVER_OWNER_COUNT | 1 | AdbRuntime.startTransportObserver (internal) |
| BOOTSTRAP_IS_LIFECYCLE_OWNER | NO | Bootstrap is read-only projection + pairing entry |

---

## 12. Packaging Differential

| Dimension | Historical (1410d5f) | Current (22e3f28) | AT REQUIRED_RAW_AAR_SET |
|-----------|---------------------|-------------------|------------------------|
| at-engine | YES (project via includeBuild) | YES (vendored AAR) | YES |
| at-backend-local | YES (project via includeBuild) | **NO** | YES |
| at-backend-adb | YES (project via includeBuild) | YES (vendored AAR) | YES |
| adb-engine | YES (project via includeBuild) | YES (vendored AAR) | YES |
| at-cli | NO (never consumed) | NO | YES |
| Consumption mode | Gradle composite (includeBuild) | Repo-local Maven (vendored AAR) | — |
| Transitivity metadata | Gradle project deps (automatic) | POM files (hand-written, partial) | ABSENT in raw AARs |
| Self-contained? | NO (requires sibling AT tree) | YES | — |
| Formal AT bundle? | N/A | NO (AG-side wrapper, not AT-published) | NOT_ESTABLISHED |

**Key finding**: The migration from composite build to vendored AAR was a packaging mode change
(self-containment gain) that simultaneously removed `:at-backend-local` from the consumed set.
These two effects occurred in the SAME commit (22e3f28) but are separable decisions:
- Self-containment: remove includeBuild → vendor AARs (packaging mechanism)
- Scope reduction: remove :at-backend-local (capability choice)

---

## 13. Historical vs Current Capability Matrix

| Capability | AT Available | Reference Host Wired | Hist AG Pkg | Hist AG Wired | Hist AG Callable | Current AG Pkg | Current AG Wired | Current AG Callable | Product Exposed | Model Visible | Gap Classification |
|---|---|---|---|---|---|---|---|---|---|---|---|
| Local one-shot | YES | NO | YES | YES | YES | **NO** | **NO** | **NO** | NO | NO | MISSING_ARTIFACT + REMOVED_DURING_MIGRATION |
| Local persistent PTY | YES | YES | YES | YES | YES | **NO** | **NO** | **NO** | NO | NO | MISSING_ARTIFACT + REMOVED_DURING_MIGRATION |
| Local raw I/O | YES | YES | YES | YES | YES | **NO** | **NO** | **NO** | NO | NO | MISSING_ARTIFACT (follows persistent) |
| Local Ctrl-C | YES | YES | YES | YES | YES | **NO** | **NO** | **NO** | NO | NO | MISSING_ARTIFACT (follows persistent) |
| Local resize | YES | YES | YES | YES | YES | **NO** | **NO** | **NO** | NO | NO | MISSING_ARTIFACT (follows persistent) |
| ADB one-shot | YES | YES | YES | YES | YES | YES | YES | YES | YES | **YES** | NONE |
| ADB persistent PTY | YES | YES | YES | YES | YES | YES | YES | YES* | NO | NO | PRESENT_BUT_NOT_PRODUCT_EXPOSED |
| ADB stdin | YES | YES | YES | YES | YES | YES | YES | YES* | NO | NO | PRESENT_BUT_NOT_PRODUCT_EXPOSED |
| ADB Ctrl-C | YES | YES | YES | YES | YES | YES | YES | YES* | NO | NO | PRESENT_BUT_NOT_PRODUCT_EXPOSED |
| ADB resize | YES | YES | YES | YES | YES | YES | YES | YES* | NO | NO | PRESENT_BUT_NOT_PRODUCT_EXPOSED |
| ADB multi-session | YES | YES | YES | YES | YES | YES | YES | YES* | NO | NO | PRESENT_BUT_NOT_PRODUCT_EXPOSED |
| AT CLI (shell-visible adb) | YES | YES | NO | NO | NO | **NO** | **NO** | **NO** | NO | NO | MISSING_ARTIFACT + MISSING_WIRING (never was) |
| adb shell one-shot (via CLI) | YES | YES | NO | NO | NO | NO | NO | NO | NO | NO | MISSING_ARTIFACT (follows CLI) |
| adb shell interactive (via CLI) | YES | YES | NO | NO | NO | NO | NO | NO | NO | NO | MISSING_ARTIFACT (follows CLI) |
| adb pair host:port code | YES | YES | NO | NO | NO | YES** | YES** | YES** | YES (Pair button) | NO | NONE (via bootstrap) |
| Bootstrap (bootstrapState) | YES | YES | NO*** | NO | NO | YES | YES | YES | YES (debug) | NO | NONE |
| Pairing discovery | YES | YES | NO*** | NO | NO | YES | YES | YES | YES (debug) | NO | NONE |
| Identity (per-consumer) | YES | YES | YES*** | YES | YES | YES | YES | YES | — | — | NONE |
| AdbRuntime readiness | YES | YES | YES | YES | YES | YES | YES | YES | — | — | NONE |
| Recovery | YES | YES | YES | YES | YES | YES | YES | YES | — | — | NONE |
| Connection reuse | YES | YES | YES | YES | YES | YES | YES | YES | — | — | NONE |
| Transport-loss semantics | YES | YES | YES | YES | YES | YES | YES | YES | — | — | NONE |

\* "Callable" at L5 (adapter exposes openAdbSession + write/read/resize/Ctrl-C/close).
  NOT callable at L6/L7 (no product tool exposes persistent terminal to user or model).

\** Via AdbConsumerBootstrap (added in 22e3f28; not present in 1410d5f adapter).

\*** In 1410d5f, identity was "at-identity" (V3 app key provisioned, not self-generated).
  Bootstrap API (AdbConsumerBootstrap) was NOT in the adapter at that commit — it was added in 22e3f28.

**Historical evidence validity**:
- 1410d5f Local one-shot/persistent: HISTORICAL_PASS (composite build + device verified D1-D4, E1-E20 per commit message). CURRENT_CALLABILITY = NO. CURRENT_REVALIDATION = NOT_APPLICABLE_UNTIL_RESTORED.
- 22e3f28 ADB one-shot: STILL_APPLICABLE (current, device verified Gates A-I).

---

## 14. Full Current Integration Verdict

```text
CURRENT_AG_FULL_CURRENT_AT_V3_CONSUMER_INTEGRATION = NO
FULL_INTEGRATION_REQUIREMENTS_SATISFIED = 4/8
```

| # | Clause | Verdict |
|---|--------|---------|
| 1 | Local one-shot CALLABLE_BY_HOST | **FAIL** (no artifact, no wiring) |
| 2 | Local persistent CALLABLE_BY_HOST | **FAIL** (no artifact, no wiring) |
| 3 | ADB one-shot CALLABLE_BY_HOST | **PASS** |
| 4 | ADB persistent CALLABLE_BY_HOST | **PASS** (adapter exposes openAdbSession + full session API) |
| 5 | AT CLI embedded WIRED_IN_COMPOSITION | **FAIL** (no artifact, no implementation) |
| 6 | Bootstrap CALLABLE_BY_HOST | **PASS** |
| 7 | identity owned correctly | **PASS** (app-private, self-generated, no copy) |
| 8 | AdbRuntime lifecycle correctly owned | **PASS** (unique owner, no second recovery) |

**Correction**: Clause 4 (ADB persistent) IS callable at L5 — the adapter exposes
`openAdbSession()` returning `ATTerminalSession` with full write/read/resize/Ctrl-C/close
surface. The gap is at L6/L7 (no product tool / model tool exposes it), which is
consumer product policy, NOT an AT integration failure. Per AT audit §18, L6/L7 are
explicitly NOT part of the 8-clause definition. **Clause 4 = PASS.**

**MISSING_FULL_INTEGRATION_CLAUSES = 1, 2, 5**

---

## 15. Capability-Delta Evidence

```text
CAPABILITY_SURFACE_NARROWED_DURING_MIGRATION = YES
```

| Dimension | Before (1410d5f) | After (22e3f28) | Delta |
|-----------|-----------------|-----------------|-------|
| Local one-shot | CALLABLE | ABSENT | LOST |
| Local persistent | CALLABLE | ABSENT | LOST |
| ADB one-shot | CALLABLE | CALLABLE | UNCHANGED |
| ADB persistent | CALLABLE | CALLABLE | UNCHANGED |
| Bootstrap | ABSENT (no API) | CALLABLE | GAINED |
| Identity model | V3 key provisioned | Self-generated | IMPROVED |
| Self-contained build | NO | YES | GAINED |
| AT CLI | ABSENT | ABSENT | UNCHANGED (never was) |

---

## 16. Root-Cause Evidence

```text
CAUSE_COMMIT = 22e3f28
CAUSE_DIFF = git diff 1410d5f..22e3f28 -- integration-at/
```

**BEFORE (1410d5f)**:
```kotlin
// build.gradle.kts
api("com.at.engine:at-engine")
api("com.at.backend.adb:at-backend-adb")
api("com.at.backend.local:at-backend-local")   // ← present
api("com.adb.engine:adb-engine")

// AtExecutionAdapter.kt
private val localEngine: ATEngine by lazy {
    ATEngine(oneShot = LocalOneShotBackend(), persistent = LocalPersistentBackend())
}
suspend fun executeLocal(command: String, ...): ATExecutionResult
suspend fun openLocalSession(command: String, ...): ATTerminalSession?
```

**AFTER (22e3f28)**:
```kotlin
// build.gradle.kts
api("com.at.engine:at-engine:1.0.0")
api("com.at.backend.adb:at-backend-adb:1.0.0")
api("com.adb.engine:adb-engine:1.0.0")
// com.at.backend.local REMOVED

// AtExecutionAdapter.kt
// localEngine REMOVED
// executeLocal REMOVED
// openLocalSession REMOVED
// AdbConsumerBootstrap ADDED
```

**Commit message explicit statement**:
> "No :at-cli, no :app (AT), no :at-backend-local (not needed for one-shot)"

**DIRECT_CAPABILITY_EFFECT**: Local one-shot + Local persistent removed from AG consumption.

```text
ROOT_CAUSE_CAUSAL_EVIDENCE = PRESENT
PRIMARY_ROOT_CAUSE = INTENTIONAL_PRODUCT_SCOPE_REDUCTION
```

The migration simultaneously:
1. Changed packaging mode (composite → vendored AAR) for self-containment
2. Reduced scope (removed Local) to match the "one-shot only" product decision
3. Added bootstrap (new AT capability at c9cee21 not present in pre-freeze)

These are three separable decisions in one commit. The Local removal is explicitly
justified in the commit message as a product scope choice ("not needed for one-shot"),
not a packaging limitation.

**Secondary observation**: The vendored AAR set (3 AARs) is a SUBSET of the AT
REQUIRED_RAW_AAR_SET (5 AARs). If AG wanted to restore Local, it would need to vendor
`at-backend-local.aar` (with `libat_local_backend.so`). This is technically possible
without modifying AT (TECHNICALLY_POSSIBLE_FROM_AT_AUDIT, BUT_NOT_FORMALLY_VERIFIED
per AT §16 RAW_AAR_CONSUMPTION_VERIFIED = NO).

---

## 17. SSOT Contradictions Found

| # | Location | Stale Content | Correct Current |
|---|----------|--------------|-----------------|
| S1 | AGENTS.md L85-89 | "AT integration mechanism is frozen and implemented (`AG.SHELL-1`): ... through the vendored `third_party/at-engine-handoff-v0` Handoff Bundle v0" | Current = AT.AG-CONSUMER-FREEZE-V1 via vendored AAR (at-consumer-freeze-v1); v0 SUPERSEDED |
| S2 | AGENTS.md L639 | "third_party/at-engine-handoff-v0/ vendored AT Engine Handoff Bundle v0" (no HISTORICAL/INACTIVE marker) | Must be marked HISTORICAL / INACTIVE / NOT IN ACTIVE DEPENDENCY GRAPH |
| S3 | AGENTS.md L639 area | Missing `third_party/at-consumer-freeze-v1/` in repo layout | Must add current bundle with role |
| S4 | Roadmap L597 | "Recomputed after AG.V2-2B.AT-ENGINE-FULL-INTEGRATION" | Should reference AG.V2-2.AT-INTEGRATION (or confirm 2B identity) |
| S5 | Roadmap L611-614 | "AT CONSUMER BOOTSTRAP GAP ... AG identity = UNSAFE_KEY_COPY ... Resolved when AT V3 provides consumer-facing pairing" | RESOLVED by AG.V2-2 (AdbConsumerBootstrap + self-generated identity) |
| S6 | Roadmap L716 | "Old AT app uses different architecture (local adbd engine vs remote ADB client)" | SUPERSEDED / INVALIDATED BY LATER EVIDENCE |
| S7 | Roadmap L717 | "Root cause: AG's AT bundle libandroidterminaladb.so lacks loopback rewrite mechanism" | LEGACY_ROOT_CAUSE = UNRESOLVED / NON-BLOCKING; EARLIER_LOOPBACK_CAUSAL_CLAIM = SUPERSEDED_BY_LATER_EVIDENCE |

```text
SSOT_CONTRADICTIONS_FOUND = YES
SSOT_NORMALIZATION_REQUIRED = YES
SSOT_NORMALIZATION_PERFORMED = NO (deferred per §64-65 of task spec)
```

---

## 18. Recommended Next Task

Based on evidence:

```text
RECOMMENDED_NEXT = D (NEEDS_CONTROLLER_DECISION)
```

Reasoning:
- The capability surface reduction was INTENTIONAL (product scope: one-shot only).
- Restoring Local requires: vendor at-backend-local.aar + add dep + restore adapter surface.
  Technically straightforward but is a PRODUCT POLICY decision (does AG want Local execution?).
- AT CLI requires: vendor at-cli.aar + implement ATCliExecution + start bridge + provision
  LocalCliBootstrap. Substantially more host obligation. Also product policy.
- The SSOT contradictions (S1-S7) are factual errors that should be corrected, but
  the task spec (§64) defers SSOT normalization to a separate step.

**Candidate options** (for Controller decision):
- A. `AG.AT-FULL-CONSUMER-INTEGRATION-0` — restore Local + (optionally) CLI
- B. `AT.ARTIFACT-BUNDLE-0` — only if formal AT bundle is the blocker (it is not; AG can vendor)
- C. `AG.AT-CONSUMER-SSOT-NORMALIZATION` — fix S1-S7 (docs only)
- D. `NEEDS_CONTROLLER_DECISION` — whether current 4/8 is the intended target or a gap to close

The audit does NOT recommend any of these automatically.

---

## 19. Controller Decision Points

1. **Is 4/8 Full Current Integration the intended target for AG V2's current phase?**
   If YES → the 4 missing clauses are PRODUCT POLICY, not defects.
   If NO → which clauses to restore? (Local? CLI? Both?)

2. **Local execution for AG**: Does the AG product need Local one-shot/persistent?
   (Current model tool is ADB one-shot only. Local would be an ALTERNATIVE execution path.)

3. **AT CLI for AG**: Does the AG product need shell-visible adb?
   (Requires Local persistent as prerequisite for the shell environment.)

4. **SSOT normalization**: Approve separate `AG.AT-CONSUMER-SSOT-NORMALIZATION` step
   to fix S1-S7?

5. **Historical evidence**: Accept that 1410d5f Local PASS evidence is VALID_FOR_HISTORY
   only (not inheritable to current)?

6. **AAR packaging**: If Local is to be restored, accept that AG must self-vendor
   at-backend-local.aar (AT has no formal Maven publication)?
