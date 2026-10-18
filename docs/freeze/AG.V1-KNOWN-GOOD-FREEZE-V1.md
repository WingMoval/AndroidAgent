# AG.V1-KNOWN-GOOD-FREEZE-V1

V1 Known-Good Freeze — canonical manifest.

```text
STEP = AG.V1-FREEZE
DATE = 2026-10-18
FREEZE_SEMANTICS = KNOWN_GOOD_BASELINE (NOT a final API freeze)
```

This commit is the freeze commit. The annotated tag
`AG.V1-KNOWN-GOOD-FREEZE-V1` points to it (created after the commit; the tag
object is outside the tree by design).

---

## 1. Freeze semantics

```text
AG_V1_FREEZE_SEMANTICS = KNOWN_GOOD_BASELINE
FINAL_AG_API_FREEZE    = NO
V2_EVOLUTION           = ALLOWED
```

Frozen FOR_V1 (baseline, not final API):

```text
ARCHITECTURE_BASELINE        = FROZEN_FOR_V1
AG_CORE_BASELINE             = FROZEN_FOR_V1
AG_RUNTIME_BASELINE          = FROZEN_FOR_V1
PROVIDER_BASELINE            = FROZEN_FOR_V1
TOOL_BASELINE                = FROZEN_FOR_V1
HOST_BASELINE                = FROZEN_FOR_V1
CURRENT_AT_INTEGRATION       = FROZEN_FOR_V1
```

After this freeze:

```text
AndroidAgent V1 = KNOWN-GOOD BASELINE / REFERENCE SOURCE / ROLLBACK SOURCE /
                  EVIDENCE SOURCE
NEW FEATURE DEVELOPMENT ON V1 = FORBIDDEN
Allowed exception = explicitly-authorized critical freeze-line bugfix only
```

## 2. Architecture baseline

```text
AG_FRAMEWORK = AG_ENGINE + TOOL_LAYER + HOST_PRODUCT
AG_ENGINE    = AG_CORE + AG_RUNTIME
Tool Layer   = concrete tool modules (NOT a central framework)
ARCHITECTURE_V1 = CONFIRMED / FROZEN_FOR_V1

AG_CORE_AT_DEPENDENCY    = NONE (verified)
AG_RUNTIME_AT_DEPENDENCY = NONE (verified)
CURRENT_AT_CONSUMERS     = tool-android-shell only (verified)
CLI_USED_AS_AG_API       = NO
ADB_RESPONSIBILITY_DUPLICATED_IN_AG = NO
```

No architecture change in this Step. Forbidden edges re-verified absent:
AG Core -> concrete Tool / AT; Agent Runtime -> concrete Tool / AT.

## 3. Git baseline

```text
PRE_FREEZE_HEAD  = e30d250 (main)
FREEZE_BRANCH    = main
FREEZE_COMMIT    = (recorded in the final report; this commit)
FREEZE_TREE      = (git rev-parse HEAD^{tree}; recorded in the final report)
FREEZE_TAG       = AG.V1-KNOWN-GOOD-FREEZE-V1 (annotated; created after commit)
PUSH             = NO (local commit + tag only)
```

The freeze tree hash is the complete source snapshot identity. The .SHA256
file alongside this manifest records selected evidence/config artifact hashes
only (it does not replace the Git tree identity).

## 4. Module graph (8 modules, unchanged)

```text
:ag-core                        (pure Kotlin leaf; no Android/HTTP/AT)
:ag-runtime                     (-> :ag-core, coroutines)
:provider-openai-compatible     (-> :ag-core)
:provider-openai-compatible-jdk (-> :ag-core; JVM transport)
:tool-android-shell             (-> :ag-core + com.androidterminal:at-runtime:0.1.0)
:tool-network                   (-> :ag-core; direct platform HTTP; NO AT)
:tool-file                      (-> :ag-core; APP_SELF READ; NO AT)
:app                            (AGA composition root; aggregates all)
```

## 5. Canonical test matrix (measured at freeze)

| Module | Tests | Failures | Skipped |
|---|---:|---:|---:|
| ag-core | 41 | 0 | 0 |
| ag-runtime | 26 | 0 | 0 |
| provider-openai-compatible | 59 | 0 | 0 |
| provider-openai-compatible-jdk | 26 | 0 | 0 |
| tool-android-shell | 16 | 0 | 0 |
| tool-network | 78 | 0 | 0 |
| tool-file | 55 | 0 | 3 (Linux-only symlink tests) |
| app | 109 | 0 | 0 |
| **TOTAL** | **410** | **0** | **3** |

```text
ASSEMBLE_DEBUG = PASS
FULL_BUILD     = FAIL_KNOWN_BASELINE
```

Full-build failure = 1 pre-existing lint error from the VENDORED AT AAR
manifest (`at-runtime-0.1.0` AndroidManifest.xml:30, CoarseFineLocation) +
14 pre-existing warnings. `NEW_BUILD_REGRESSION = NO`. This is a known
external-artifact limitation, NOT a V1 feature failure.

## 6. Stable capability statuses (current Roadmap SSOT)

```text
RUN_003 (cancel mechanism)        = VERIFIED
RUN_004 (interrupt semantics)     = VERIFIED
HOST_005 (Stop / Cancel UI)       = VERIFIED (D1-D4 ALL PASS)
TOOL_NETWORK (AG.NETWORK-0)       = VERIFIED
TOOL_FILE (AG.FILE-0)             = VERIFIED
CORE-007 (history rollback)       = RESEARCH_REQUIRED (stays)
```

No new Stable IDs introduced by this freeze.

## 7. AT integration snapshot (frozen into V1)

Canonical pointer: `docs/integration/AT_TO_AG_HANDOFF_CURRENT.md`
(single source of truth; not duplicated here).

```text
CURRENT_CAPABILITY_HANDOFF   = AT_TO_AG_HANDOFF_V1
CURRENT_BINARY_HANDOFF       = third_party/at-engine-handoff-v0 @ 0.1.0
                               (paired AAR bundle, hash-verified via
                                SHA256SUMS + at-engine-handoff.json)
CURRENT_AT_BINARY_PROVENANCE = from bundle manifest (AT@726a441,
                               sourceTreeSha256=ff0a6432..., 162 files)
AT_CONTRACT_STATUS           = DRAFT (NO final API freeze)
AG_CONSUMPTION               = ONE-SHOT ONLY (AtRuntime.execute)
RAW_TERMINAL                 = EXTERNALLY AVAILABLE / NOT CONSUMED BY AG
SCREEN_AWARE_TERMINAL        = BLOCKED_BY_AT.EMULATOR-0
HUMAN_AGENT_SHARED_SESSION   = DEFERRED
NEXT_FORMAL_AT_SYNC          = POST_AT_EMULATOR_0_AND_CONTRACT_1_REVIEW
```

This Step does NOT update the AT binary, regenerate the bundle, rename
handoff-v0, or consume new AT APIs. New AT integration is deferred to
AG.V2-2.AT-INTEGRATION on V2.

## 8. Real-device evidence (references)

No new device runs at freeze (device not attached at freeze time; production
code unchanged since the last verified runs on this exact build).

```text
FINAL_DEVICE_SMOKE = PRIOR_EVIDENCE_USED
  S1-S5 (launch / provider / Answer / shell one-shot / next run):
    most recent valid evidence on build e30d250:
    - D3 re-test (bridge run -> natural Answer, 2.2 s, 12 tokens):
      docs/implementation/AG.HOST-STOP-1.md §13.4
    - AGENT-0.LIVE adaptive tool-loop E2E (AGENTS.md frozen state)
  S4 (android_shell one-shot through current AT bundle):
    AG.SHELL-1 S1-S5 (AGENTS.md) + AT.ENGINE-AG0 H1-H4 (bundle manifest
    validationStatus)
STOP_SMOKE = PRIOR_EVIDENCE_USED
  S6 (streaming run -> user Stop -> one Interrupted -> usable UI):
    docs/implementation/AG.HOST-STOP-1.md §13.6 (stop smoke PASS:
    Interrupted + ConnectionAbortedError@16 + "Stopped.")
```

A fresh final smoke is optional for the Controller and can be run after
device re-attach without changing the freeze identity.

## 9. Known limitations

1. `./gradlew build` fails on the vendored AT AAR lint error
   (CoarseFineLocation) — pre-existing, external artifact, NEW_BUILD_REGRESSION = NO.
2. tool-file: 3 Linux-only symlink tests skipped on Windows (expected).
3. AT contract is DRAFT — the consumed surface is the "current exposed runtime
   surface", not a frozen final API.
4. V1 local working tree retains excluded scratch paths after the freeze
   (user backups, __pycache__) — see §10; they are untracked and do not
   affect the freeze identity.

## 10. Excluded local paths (NOT in the freeze commit)

| Path | Reason |
|---|---|
| AGENTS_备份1.md | user local backup (pre-edit AGENTS.md copy); scratch |
| docs/roadmap/AndroidAgent_Master_Roadmap_备份1.md | user local backup (pre-edit Roadmap copy); scratch |
| tools/__pycache__/ | generated Python bytecode; excluded via .gitignore added in this commit |

Probe/diagnostic policy (explicit decisions, per AG.V1-FREEZE §12):

| Path | Decision | Reason |
|---|---|---|
| app/src/androidTest/.../ProviderRealDeviceProbeTest.kt | INCLUDE | reproducible verification infra (instrumented probe of production wiring + connectivity); androidTest-only, not run by unit tasks |
| provider-openai-compatible-jdk/.../OpenRouterPcProbe.kt | INCLUDE | part of the canonical 26-test baseline; properly isolated behind @Tag("live") + excludeTags("live") so it never runs in the default test task |
| docs/diagnostics/AGA.PROVIDER-AB-HEADLESS-0.md | INCLUDE | point-in-time diagnostic record (evidence source role) |
| tools/ag_debug.py | INCLUDE | PC-side debug-bridge client used by the verification workflow (AG.DEBUG-0 tooling family) |

provider-jdk test-infra changes (build.gradle.kts live-test wiring +
FixtureServer/LiveApiTest/ProviderProtocolTest): INCLUDE — they ARE the
formal test infrastructure of the current 26-test canonical baseline
(live/local split via @Tag("live")).

## 11. SSOT consistency at freeze

```text
AGENTS.md                          = current integration snapshot section present
Roadmap                            = CURRENT = NONE; NEXT = AG.V2-1.BASELINE-MIGRATION;
                                     HOST-005 VERIFIED (registry + coverage matrix)
AT_TO_AG_HANDOFF_CURRENT.md        = canonical AT snapshot (referenced, not duplicated)
This manifest                      = current truth summary (historical docs untouched)
```

Historical point-in-time records (initial D1 FAIL, initial D3 INCONCLUSIVE,
old test counts, old NEXT values) are preserved in their Step docs; this
manifest states the current truth without rewriting history.

## 12. Next migration step

```text
CURRENT = NONE
NEXT    = AG.V2-1.BASELINE-MIGRATION
NEXT_OPEN_AUTHORIZED = NO (awaiting Controller Review of this freeze evidence)
```

V2 baseline migration target = EQUIVALENCE (same module graph, same
architecture, same current AT binary, same behavior, same 410-test baseline).
New AT integration is isolated to AG.V2-2.AT-INTEGRATION. TOOL-006 runs after
the AT integration gate (semantics unchanged).

## 13. Post-freeze policy

```text
AG_V1_STATUS           = FROZEN_KNOWN_GOOD
ACTIVE_V1_FEATURE_DEV  = NO
V2_MIGRATION_ELIGIBLE  = YES
```

Tag `AG.V1-KNOWN-GOOD-FREEZE-V1` is IMMUTABLE once created: no force-move,
no delete/recreate. V1 receives only explicitly-authorized critical
freeze-line bugfixes (each requiring its own Step + Controller approval).
