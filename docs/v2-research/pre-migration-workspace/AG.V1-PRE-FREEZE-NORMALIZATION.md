# AG.V1-PRE-FREEZE-NORMALIZATION

Pre-freeze normalization / SSOT consolidation / dirty-tree audit for the
AndroidAgent V1 Known-Good freeze candidate.

```text
STEP = AG.V1-PRE-FREEZE-NORMALIZATION
DATE = 2026-10-18
V1_ROOT = D:\codebank\AndCode\AndroidAgent
V2_RESEARCH_ROOT = D:\codebank\AndCode\AndroidAgent-V2
AT_ROOT = D:\codebank\AndCode\AndroidTerminal-V2 (READ ONLY)
PRODUCTION FEATURE IMPLEMENTATION = NO
ARCHITECTURE REDESIGN = NO
NEW STABLE CAPABILITY ID = NO
COMMIT / PUSH / TAG = NO (freeze commit/tag is AG.V1-FREEZE's job)
```

---

## 1. Step identity

Pre-freeze normalization only: SSOT consolidation, dirty-tree audit,
architecture baseline confirmation, AT Handoff V1 intake, freeze-readiness
verification. No production feature work, no architecture redesign, no new
stable capability IDs.

## 2. Input baselines

```text
V1_HEAD = e30d250 (main)
V1_DIRTY = YES
DIRTY_PATH_COUNT = 43 (re-measured this Step; matches previous measurement)
AT_REPO_HEAD = 726a441 (AndroidTerminal-V2, main)
AT freeze refs (700ca3b / 7ccb1bf3... / AT.PRE-EMULATOR-FREEZE-V1) = NOT FOUND LOCALLY
```

## 3. Controller Strategy B decision

```text
STRATEGY_C_TECHNICAL_FINDING = VALID (AG.V2-0: no structural defect)
STRATEGY_B_CONTROLLER_CHOICE = ACTIVE
```

The Controller selects Strategy B (V1 freeze as Known-Good baseline; V2 as
future active tree) for MIGRATION ISOLATION / KNOWN-GOOD FREEZE / ROLLBACK
SAFETY / NEW-AT INTEGRATION ISOLATION. This does not invalidate
`ARCHITECTURE_V1_STRUCTURALLY_VALID = YES` or `PRODUCTION_REFACTOR_REQUIRED = NO`.

Frozen migration sequence:

```text
AG.V1-FREEZE -> AG.V2-1.BASELINE-MIGRATION -> V2 EQUIVALENCE GATE
-> AG.V2-2.AT-INTEGRATION -> NEW AT INTEGRATION GATE
-> AG.OUTPUT-BOUND.PREFLIGHT -> future capabilities on V2
```

TOOL-006 status / priority / identity UNCHANGED; only execution order changes
(TOOL_006_TARGET = V2). Full record:
`D:\codebank\AndCode\AndroidAgent-V2\docs\architecture\AG.V2-CONTROLLER-DECISION.md`.

## 4. Current architecture truth (CONFIRMED, unchanged)

```text
AndroidAgent = AG Engine + Tool Layer + Host/Product
AG Engine    = AG Core + Agent Runtime
Tool Layer   = concrete tool modules (one per capability) — NOT a central
               module/runtime/framework
```

Dependency direction re-verified this Step (see §10):

```text
Concrete Tool -> AG Core contract    ALLOWED (holds)
AG Core -> Concrete Tool             FORBIDDEN (holds: no such edge)
Agent Runtime -> Concrete Tool       FORBIDDEN (holds: no such edge)
AG Core -> Android / HTTP / AT       FORBIDDEN (holds: ag-core imports only
                                      java.util.concurrent.*)
```

No new abstractions introduced (no :execution-adapter, no ExecutionManager,
no ATAdapterManager, no TerminalManager, no central Tool Layer).

## 5. Dirty path provenance matrix

Classification:
A = VERIFIED_COMPLETED_STEP | B = DOC_NORMALIZATION_THIS_STEP |
C = GENERATED_OR_LOCAL_ONLY | D = UNRELATED_KNOWN_CHANGE |
E = UNKNOWN/UNEXPLAINED

Re-measured 2026-10-18 (`git status --porcelain=v1`): 43 paths
(21 tracked-modified + 22 untracked).

### 5.1 Tracked modifications (21)

| # | Path | Type | Origin Step | Verified? | Freeze action |
|---|---|---|---|---|---|
| 1 | ag-core/src/main/.../AgentCore.kt | A | AG.RUN-CONTROL-1.DETERMINISTIC(.FIX) | YES (ag-core 41/41; I1-I15) | FREEZE |
| 2 | ag-core/src/main/.../RunOutcome.kt | A | AG.RUN-CONTROL-1.DETERMINISTIC(.FIX) | YES (RunOutcome.Interrupted; tests green) | FREEZE |
| 3 | ag-runtime/build.gradle.kts | A | AG.RUN-CONTROL-1.DETERMINISTIC.FIX (kotlinx-coroutines added: real consumer = execution Job) | YES (ag-runtime 26/26) | FREEZE |
| 4 | ag-runtime/src/main/.../AgentRuntime.kt | A | AG.RUN-CONTROL-1.DETERMINISTIC(.FIX) | YES (interruptActiveRun; executionJob.cancel) | FREEZE |
| 5 | ag-runtime/src/test/.../AgentRuntimeTest.kt | A | AG.RUN-CONTROL-1.DETERMINISTIC | YES | FREEZE |
| 6 | ag-runtime/src/test/.../TestSupport.kt | A | AG.RUN-CONTROL-1.DETERMINISTIC | YES | FREEZE |
| 7 | app/build.gradle.kts | A | AGA.OBSERVABILITY-0/1 + AG.NETWORK-0/AG.FILE-0 (tool module deps) | YES (app 109/109; assembleDebug PASS) | FREEZE |
| 8 | app/src/main/.../ChatCompositionRoot.kt | A | AG.NETWORK-0 + AG.FILE-0 (register NetworkToolImpl/FileToolImpl) | YES (L2/L3 device PASS in those steps) | FREEZE |
| 9 | app/src/main/.../TranscriptModel.kt | A | AGA.OBSERVABILITY-1 (Thought flip fix) | YES (TranscriptModel L1 T20-T22) | FREEZE |
| 10 | app/src/main/.../debug/DebugProtocol.kt | A | AGA.OBSERVABILITY-0 (reasoning/content delta kinds) | YES (device acceptance PASS) | FREEZE |
| 11 | app/src/main/.../provider/AndroidOpenAiHttpTransport.kt | A | AG.HOST-STOP-1 (withContext(Dispatchers.IO) cancellation-responsive read) | YES (JVM discriminator FAIL->PASS; D1-D4) | FREEZE |
| 12 | app/src/main/.../trace/AgentTraceCollector.kt | A | AGENT-0.PREFLIGHT / AGA.OBSERVABILITY | YES (app tests green) | FREEZE |
| 13 | app/src/main/res/layout/activity_chat.xml | A | AGA.OBSERVABILITY-0/1 + AG.HOST-STOP-1 (status/stop controls) | YES (device acceptance; lint warnings = KNOWN_BASELINE) | FREEZE |
| 14 | app/src/test/.../TranscriptModelTest.kt | A | AGA.OBSERVABILITY-1 | YES (T20-T22) | FREEZE |
| 15 | docs/implementation/AG.HOST-STOP-1.md | B | post-commit note (e30d250 applied after doc finalization, per user instruction) | YES (2-line doc note; no semantic change) | FREEZE |
| 16 | provider-openai-compatible-jdk/build.gradle.kts | D | pre-existing user test-infra adjustment | n/a (test build config) | FREEZE |
| 17 | provider-openai-compatible-jdk/src/test/.../FixtureServer.kt | D | pre-existing user test-infra adjustment | n/a (test-only) | FREEZE |
| 18 | provider-openai-compatible-jdk/src/test/.../LiveApiTest.kt | D | pre-existing user test-infra adjustment | n/a (test-only) | FREEZE |
| 19 | provider-openai-compatible-jdk/src/test/.../ProviderProtocolTest.kt | D | pre-existing user test-infra adjustment | n/a (test-only; provider-jdk 26/26 green) | FREEZE |
| 20 | settings.gradle.kts | A | AG.NETWORK-0 + AG.FILE-0 (include :tool-network, :tool-file) | YES (build green) | FREEZE |
| 21 | tools/ag_debug.py | D | pre-existing user debug tooling (AG.DEBUG-0 era, local helper) | n/a (PC-side tooling, not shipped) | FREEZE (or leave; non-ship) |

### 5.2 Untracked (22)

| # | Path | Type | Origin Step | Verified? | Freeze action |
|---|---|---|---|---|---|
| 22 | AGENTS_备份1.md | D | user local backup (pre-edit copy of AGENTS.md; differs from current by 334 lines) | n/a (local scratch) | EXCLUDE from freeze (local backup; not part of the tree) |
| 23 | ag-runtime/src/test/.../AgentRuntimeInterruptTest.kt | A | AG.RUN-CONTROL-1.DETERMINISTIC | YES (ag-runtime 26/26 includes it) | FREEZE |
| 24 | app/src/androidTest/.../FileL2FixtureTest.kt | A | AG.FILE-0 (L2 fixture) | YES (L2 16/16 device PASS in AG.FILE-0) | FREEZE |
| 25 | app/src/androidTest/.../FileL3FixtureTest.kt | A | AG.FILE-0 (L3 fixture) | YES (L3 device PASS in AG.FILE-0) | FREEZE |
| 26 | app/src/androidTest/.../ProviderRealDeviceProbeTest.kt | D | pre-existing user real-device probe (test-only androidTest) | n/a (not run in this normalization) | FREEZE (test-only) |
| 27 | app/src/debug/AndroidManifest.xml | A | AG.HOST-STOP-1 (debug-only cleartext override for loopback fixture; release unaffected) | YES (comment documents intent; debug-only merge) | FREEZE |
| 28 | app/src/test/.../HostStopStateTest.kt | A | AG.HOST-STOP-1 (H1-H8 host tests) | YES (app 109/109) | FREEZE |
| 29 | docs/AndroidAgent_Architecture_Capability_Map_v1.0.md | A | AG.ROADMAP-0.REVIEW-1 (Architecture v1.0) | YES (frozen architecture doc) | FREEZE |
| 30 | docs/diagnostics/AGA.PROVIDER-AB-HEADLESS-0.md | D | pre-existing user real-device A/B diagnostic (baseline cd0eb33; device-offline status) | n/a (diagnostic record) | FREEZE (evidence record) |
| 31 | docs/implementation/AG.FILE-0.CLOSEOUT_SSOT_Synchronization.md | A | AG.FILE-0 closeout | YES | FREEZE |
| 32 | docs/implementation/AG.FILE-0_FileTool_READ_v0.md | A | AG.FILE-0 | YES | FREEZE |
| 33 | docs/implementation/AG.NETWORK-0_NetworkTool_Fetch_v0.md | A | AG.NETWORK-0 | YES | FREEZE |
| 34 | docs/implementation/AG.RUN-CONTROL-1.DETERMINISTIC.FIX.md | A | AG.RUN-CONTROL-1.DETERMINISTIC.FIX | YES | FREEZE |
| 35 | docs/implementation/AG.RUN-CONTROL-1.DETERMINISTIC.md | A | AG.RUN-CONTROL-1.DETERMINISTIC | YES (point-in-time; superseded-by-FIX noted inside) | FREEZE |
| 36 | docs/implementation/AGA.OBSERVABILITY-1_...md | A | AGA.OBSERVABILITY-1 | YES | FREEZE |
| 37 | docs/roadmap/AndroidAgent_Master_Roadmap_备份1.md | D | user local backup (pre-edit copy of the Roadmap) | n/a (local scratch) | EXCLUDE from freeze (local backup) |
| 38 | provider-openai-compatible-jdk/src/test/.../OpenRouterPcProbe.kt | D | pre-existing user PC probe (test-only) | n/a | FREEZE (test-only) or EXCLUDE (non-essential) |
| 39 | tool-file/ (entire module) | A | AG.FILE-0 | YES (tool-file 55 tests, 3 skipped Linux-only) | FREEZE |
| 40 | tool-network/ (entire module) | A | AG.NETWORK-0 | YES (tool-network 78/78) | FREEZE |
| 41 | tools/__pycache__/ | C | Python bytecode cache (generated) | n/a | EXCLUDE (add to .gitignore; generated) |
| 42 | tools/ag_file_l2.py | A | AG.FILE-0 (L2 driver) | YES | FREEZE |
| 43 | tools/ag_file_l3.py | A | AG.FILE-0 (L3 driver) | YES | FREEZE |

### 5.3 Classification summary

```text
A = 28 paths (verified completed steps)
B = 1  path  (this Step's doc note on AG.HOST-STOP-1.md)
C = 1  path  (tools/__pycache__ — generated)
D = 12 paths (pre-existing unrelated known changes: user backups, probes,
              test-infra tweaks, debug tooling, diagnostics)
E = 0  paths
UNKNOWN_OR_UNEXPLAINED_DIRTY_PATHS = 0
```

Note: this Step's own doc changes (AGENTS.md, Roadmap, this file,
docs/integration/AT_TO_AG_HANDOFF_CURRENT.md) are type B by definition and
are applied after this inventory; they do not create unknown paths.

## 6. SSOT residue resolution (from AG.V2-0 evidence)

Source: `AndroidAgent-V2/evidence/SSOT_RESIDUE.txt` + `STALE_ASSUMPTIONS.txt`
(re-read, not from memory).

| ID | Still present at audit? | Resolution this Step |
|---|---|---|
| R1 (coverage matrix HOST-005 = RESEARCH_REQUIRED) | YES (line 899) | FIXED: matrix row now VERIFIED (matches registry) |
| R2 (duplicate AG.PROVIDER-RECOVERY.PREFLIGHT in NEXT) | YES (items 2 and 3 identical) | FIXED: duplicate removed; items renumbered; queue re-sequenced for Strategy B |
| R3 (AG.HOST-STOP-1 §13.11 hash placeholder) | YES | HISTORICAL-ONLY: point-in-time doc; hash line documents the closeout-time state; not a current SSOT defect. Left as-is (append-only history). |
| R4 (AGENTS app=109 vs measured) | CONSISTENT | NO ACTION (matches measured 109) |
| R5 (AGENTS frozen-state order) | CONSISTENT | NO ACTION |
| R6 (queue CURRENT=NONE) | CONSISTENT | Updated to Strategy B sequence (NEXT = AG.V1-FREEZE) |
| R7 (AT-001/AT-002 notes thin) | YES | FIXED: notes enriched (AT ownership explicit; Handoff V1 truth; AG consumer = one-shot; Status stays EXTERNAL) |
| R8 (handoff-v0 naming) | YES (by design) | RESOLVED BY DOCUMENTATION: capability(V1) vs binary(v0) distinction frozen in docs/integration/AT_TO_AG_HANDOFF_CURRENT.md; folder NOT renamed |
| S1-S12 (stale assumptions) | see STALE_ASSUMPTIONS.txt | All either historical-only (S2), confirmed-compliant (S11/S12), or frozen boundaries (S7-S10). No current SSOT defects remain after R1/R2/R7 fixes. |

## 7. AT Handoff V1 intake

```text
AT_HANDOFF_V1 = ACCEPTED (controller notification; no standalone local artifact)
AT_PHASE = PRE_EMULATOR
AT_CONTRACT_STATUS = DRAFT
AT_CONTRACT_FREEZE_AUTHORIZED = NO
PERSISTENT_EXECUTION_CAPABILITY = MATURE_ENOUGH_FOR_EMULATOR
TERMINAL_EMULATOR / SCREEN_STATE / STRUCTURED_OBSERVATION = NOT_STARTED
```

Missing local freeze refs (700ca3b, 7ccb1bf3..., tag AT.PRE-EMULATOR-FREEZE-V1)
are recorded as NOT AVAILABLE IN CURRENT AT TREE — NOT rewritten as verified.
The consumed binary's identity comes from the bundle manifest provenance
(AT@726a441, dirty tree, sourceTreeSha256=ff0a6432..., 162 files) + hashes.
Provenance proves BINARY IDENTITY only, never a final AT API.

Canonical snapshot: `docs/integration/AT_TO_AG_HANDOFF_CURRENT.md` (created
this Step as the single pointer; other docs reference it).

## 8. Capability vs binary handoff distinction

```text
CAPABILITY_HANDOFF    = AT_TO_AG_HANDOFF_V1   (capability snapshot)
CURRENT_BINARY_HANDOFF = at-engine-handoff-v0 @ 0.1.0 (physical paired AAR)
BINARY_HANDOFF_UPGRADE_THIS_STEP = NO
```

No folder rename (handoff-v0 stays). New AT binary integration is deferred to
AG.V2-2.AT-INTEGRATION on V2.

## 9. AG framework normalization

AGENTS.md updated with a "Current integration snapshot (canonical pointer)"
section that states the frozen facts (architecture CONFIRMED, AT dependency
NONE/NONE, single AT consumer, capability vs binary handoff, one-shot
consumption, raw-terminal planning status, screen-blocked, shared-session
deferred, CLI/ADB non-duplication, next sync point) and points to
`docs/integration/AT_TO_AG_HANDOFF_CURRENT.md`. Framework wording kept:
"Tool Layer = concrete tool modules" (no central module).

AT API wording check: no document claims a "frozen final AT API". The bundle
manifest's `stableSurface` is referenced only as the "current exposed runtime
surface" of the DRAFT contract.

## 10. Current AT consumer audit (re-run this Step)

```text
CURRENT_AT_CONSUMERS = tool-android-shell (exactly one production module)
AG_CORE_AT_DEPENDENCY = NONE
AG_RUNTIME_AT_DEPENDENCY = NONE
CLI_USED_AS_AG_API = NO
ADB_RESPONSIBILITY_DUPLICATED_IN_AG = NO
```

Method: gradle dependency scan (only `:tool-android-shell` declares
`com.androidterminal:at-runtime:0.1.0`) + source import scan (only
`ShellConnection.kt` imports `com.androidterminal.runtime.*`) +
subprocess/CLI keyword scan (none) + ADB keyword scan (Host = connect trigger
+ status presentation + manual reconnect affordance only, which is allowed).

## 11. Canonical freeze test matrix (measured this Step)

Modules enumerated via `./gradlew projects` (8 production modules). All
modules with a test task were executed.

| Module | Test task | Tests | Failures | Skipped |
|---|---|---:|---:|---:|
| ag-core | :test | 41 | 0 | 0 |
| ag-runtime | :test | 26 | 0 | 0 |
| provider-openai-compatible | :test | 59 | 0 | 0 |
| provider-openai-compatible-jdk | :test | 26 | 0 | 0 |
| tool-android-shell | :testDebugUnitTest | 16 | 0 | 0 |
| tool-network | :testDebugUnitTest | 78 | 0 | 0 |
| tool-file | :testDebugUnitTest | 55 | 0 | 3 (Linux-only symlink tests, skipped on Windows) |
| app | :testDebugUnitTest | 109 | 0 | 0 |
| **TOTAL** | | **410** | **0** | **3** |

```text
FREEZE_TEST_TOTAL = 410
FREEZE_TEST_FAILURES = 0
```

(The AG.V2-0 "384" figure omitted provider-openai-compatible-jdk (26);
410 = 384 + 26. No count was force-matched to the old number.)

## 12. Build baseline

```text
ASSEMBLE_DEBUG = PASS (BUILD SUCCESSFUL)
FULL_BUILD     = FAIL_KNOWN_BASELINE
```

Full-build failure detail:

```text
1 lint ERROR (pre-existing, from the AT AAR, NOT from AG code):
  at-runtime-0.1.0 AndroidManifest.xml:30
  [CoarseFineLocation] ACCESS_FINE_LOCATION without ACCESS_COARSE_LOCATION
14 lint warnings (pre-existing): androidx newer-version hints, x86_64 ABI
  note, manifest allowBackup, button-bar / autofill / hardcoded-string layout
  warnings.
```

This is the SAME pre-existing known issue recorded by AG.V2-0 ("global build
FAIL_UNRELATED (pre-existing AT AAR lint)"). Classification:

```text
NEW_BUILD_REGRESSION = NO
```

All AG-owned code compiles and all unit tests pass; the failure originates in
the vendored AT AAR manifest (external artifact), which V1 does not modify.

## 13. Real-device verification references

No new real-device runs this Step (no production code change). Recent valid
evidence references:

```text
D1-D4 (HOST-005 Stop)          = docs/implementation/AG.HOST-STOP-1.md §12/§13
                                  (D1/D2/D4 PASS dispatch-fixed build; D3 PASS post fixture-fix)
AGENT-0.LIVE (adaptive tool loop) = AGENTS.md frozen state (OpenRouter E2E)
AG.NETWORK-0 L2/L3             = docs/implementation/AG.NETWORK-0_NetworkTool_Fetch_v0.md
AG.FILE-0 L2/L3                = docs/implementation/AG.FILE-0_FileTool_READ_v0.md
AT.ENGINE-AG0 (H1-H4)          = third_party/at-engine-handoff-v0/at-engine-handoff.json (validationStatus)
```

AG.V1-FREEZE may optionally run a final smoke; not required by this Step.

## 14. Freeze candidate snapshot

```text
AG_V1_FREEZE_CANDIDATE
GIT_HEAD               = e30d250 (main)
WORKTREE_DIRTY         = YES
DIRTY_PATH_COUNT       = 43
UNEXPLAINED_DIRTY_PATHS = 0
ARCHITECTURE_V1        = CONFIRMED
RUN_003                = VERIFIED
RUN_004                = VERIFIED
HOST_005               = VERIFIED (D1-D4 ALL PASS)
AT_CAPABILITY_HANDOFF  = V1
AT_BINARY_HANDOFF      = v0 @ 0.1.0
AG_AT_CONSUMPTION      = ONE-SHOT
RAW_TERMINAL           = PLANNABLE / NOT IMPLEMENTED
SCREEN_AWARE           = BLOCKED
TEST_MATRIX            = 410 tests / 0 failures / 3 skipped (see §11)
ASSEMBLE_DEBUG         = PASS
FULL_BUILD             = FAIL_KNOWN_BASELINE (AT AAR lint; NEW_BUILD_REGRESSION = NO)
PRODUCTION_CODE_CHANGED_THIS_STEP = NO
```

## 15. Freeze readiness gates (F1-F10)

| Gate | Requirement | Result |
|---|---|---|
| F1 | every dirty path explained | PASS (43/43 classified; E=0) |
| F2 | no unknown production diff | PASS (all production diffs traced to verified steps or known pre-existing) |
| F3 | SSOT current truth consistent | PASS (R1/R2/R7 fixed; R3 historical-only; AGENTS/Roadmap/integration aligned) |
| F4 | Architecture v1 confirmed | PASS (re-audited: no forbidden edge) |
| F5 | AT Handoff / current bundle truth explicit | PASS (docs/integration/AT_TO_AG_HANDOFF_CURRENT.md created) |
| F6 | all required unit tests green | PASS (410/410, 0 failures) |
| F7 | assembleDebug green | PASS |
| F8 | no new build regression | PASS (full-build fail = pre-existing AT AAR lint, unchanged) |
| F9 | HOST-005 current status consistent | PASS (registry + coverage matrix both VERIFIED) |
| F10 | execution queue normalized | PASS (dedup done; Strategy B sequence recorded) |

All gates PASS.

## 16. Final verdict

```text
VERDICT = PASS
V1_FREEZE_READY = YES
NEXT = AG.V1-FREEZE
```

After AG.V1-FREEZE: V1 = KNOWN-GOOD REFERENCE / ROLLBACK SOURCE / EVIDENCE
SOURCE, no new feature development (only explicitly-authorized critical
freeze-line bugfixes). V2 baseline migration target = EQUIVALENCE (not
improvement). New AT integration isolated to AG.V2-2.AT-INTEGRATION.

Suggested freeze tag name (final name is the Controller's choice in
AG.V1-FREEZE): `AG.V1-KNOWN-GOOD-FREEZE`.
