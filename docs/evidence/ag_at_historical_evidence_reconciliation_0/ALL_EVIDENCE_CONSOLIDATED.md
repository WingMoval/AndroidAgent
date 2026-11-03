# AG.AT-HISTORICAL-EVIDENCE-RECONCILIATION-0 - CONSOLIDATED EVIDENCE

Single-file merge of docs/evidence/ag_at_historical_evidence_reconciliation_0/ (8 files, 00-07).
Date: 2026-11-04. VERDICT = PASS. RECOMMENDATION: PROCEED_TO_V042_CONSUMER_SYNC.
Note: 07_controller_closeout_corrections.txt records the Controller Review
corrections C1-C4 (wording + gate split) applied to the audit doc. Raw point-in-time
captures (00-06) are unchanged.

==============================================================================
FILE: 00_git_baseline_lineage.txt
==============================================================================

AG.AT-HISTORICAL-EVIDENCE-RECONCILIATION-0 - 00 GIT BASELINE + LINEAGE
Date: 2026-11-04

--- V2 (current) ---
branch = main
HEAD   = c90f6d1bffebf80c16980644073d5057c2a64e70
status = ?? docs/audit/AG.AT-BASELINE-DIFFERENTIAL-AUDIT-0.md | ?? docs/audit/AG.AT-HISTORICAL-EVIDENCE-RECONCILIATION-0.md | ?? docs/evidence/at_baseline_differential_audit_0/ | ?? upload/AG.AT-BASELINE-DIFFERENTIAL-AUDIT-0.evidence.txt | ?? upload/AG.AT-BASELINE-DIFFERENTIAL-AUDIT-0.md

--- V1 (frozen) ---
branch = main
HEAD   = 13df1dbd892c02ff25ce5d8317095bc1deaaaf71
freeze tag target = 13df1dbd892c02ff25ce5d8317095bc1deaaaf71

--- ADB-Regression (forensic) ---
branch = (detached)
HEAD   = 3aeed330169c724c42c4b2e8537b20361468aa49

--- V2 baseline parent check ---
13df1db is ancestor of c84bf5d (V2 baseline parent): exit 0
3aeed33 (LKG) is ancestor of V1 freeze: exit 0

--- V2 key commits ---
  c84bf5d 2026-10-18 16:19:40 +0800 AG.V2-1: establish V2 baseline from frozen V1
  be9e619 2026-10-18 17:56:56 +0800 AG.ADB-LKG-REGRESSION: identify root cause (AT native lib loopback rewrite missing)
  1410d5f 2026-10-18 21:10:45 +0800 AG.V2-2: integrate full AndroidTerminal V3 execution engine
  22e3f28 2026-10-20 01:47:35 +0800 AG.V2-2: integrate AT consumer freeze V1
  6c4d8f1 2026-10-20 15:44:24 +0800 AG.AT-FULL-CONSUMER-INTEGRATION-0: restore full AT V3 consumer capability
  a6eea61 2026-10-20 21:45:37 +0800 AG.AT-FULL-SUBSYSTEM-INTEGRATION-0: close complete AT V3 consumer subsystem
  f640ed0 2026-10-27 00:54:05 +0800 freeze: establish post-I pre-UI AG core baseline
  c90f6d1 2026-10-28 21:52:01 +0800 docs: close normalize-1 evidence and exports

==============================================================================
FILE: 01_v1_v2_equivalence_adb_lkg.txt
==============================================================================

AG.AT-HISTORICAL-EVIDENCE-RECONCILIATION-0 - 01 V1->V2 EQUIVALENCE + ADB-LKG ROOT CAUSE
Date: 2026-11-04

======================================================================
AG.V2-1.BASELINE-MIGRATION.md (at c84bf5d) - key lines
  PRODUCTION_TREE_EQUIVALENCE = EXACT
  ARCHITECTURE_V1 = INHERITED / CONFIRMED / UNCHANGED
  git diff --name-only AG.V1-KNOWN-GOOD-FREEZE-V1   (after V2 doc changes)
  PRODUCTION_DIFF_FROM_V1_FREEZE = 0
  git diff --exit-code AG.V1-KNOWN-GOOD-FREEZE-V1 -- third_party/at-engine-handoff-v0
  DEVICE_EQUIVALENCE_SMOKE = PRIOR_FREEZE_EVIDENCE_INHERITED
  V2_BASELINE_COMMIT    = (recorded in final report; the single V2 metadata commit)
  V2_BASELINE_PARENT    = 13df1dbd892c02ff25ce5d8317095bc1deaaaf71 (= V1 freeze)
  V2_BASELINE_TREE      = (recorded in final report)
  V2_BASELINE_TAG       = AG.V2-BASELINE-V1 (annotated; points to the baseline commit)
  V2_BASELINE     = VERIFIED_EQUIVALENT_TO_V1_FREEZE

======================================================================
AG.ADB-LKG-REGRESSION.md (at be9e619) - key lines
  Post-TLS ADB CNXN                          → FAIL (SSL_read err=1)
  The device adbd accepts the TLS handshake but drops the connection before
  LAST_KNOWN_GOOD_AG_STEP = AGENT-0.LIVE
  LAST_KNOWN_GOOD_AG_COMMIT = 3aeed330169c724c42c4b2e8537b20361468aa49
  LKG_AT_BUNDLE_VERSION = at-engine-handoff-v0 @ 0.1.0
  LKG_NATIVE_SO_EQUALS_CURRENT = YES (SHA256 identical)
  | AndroidTerminal V3 | `com.androidterminal.app` | 0.3.0 | `libadbd_engine.so` (different architecture) | **YES** |
  TCP CONNECT 127.0.0.1:39199 OK (fd=4)        ← loopback works
  READY via loopback 127.0.0.1:39199
  AdbClient::connect: 192.168.137.210:39199    ← direct wlan0 IP (NO loopback)
  RX post-TLS CNXN → SSL_read failed (err=1)   ← adbd drops non-loopback
  The AT app 0.1.0's `libandroidterminaladb.so` (4.3MB) contains a **loopback
  native library rewrites the connection target to `127.0.0.1` (loopback)
  The Honor/MagicOS adbd implementation **only accepts post-TLS ADB
  connections from loopback (127.0.0.1)**, not from the wlan0 IP. This is an
  OEM-specific adbd behavior (similar to the WD readiness anomaly documented
  1. The adbd was accepting connections from the wlan0 IP (different
  loopback rewrite, OR
  3. The device's adbd configuration was different at that time.
  Classification = NOT_AG_SIDE_REGRESSION
  Root cause = AT native lib version (loopback rewrite missing)
  + Honor adbd OEM behavior (loopback-only post-TLS)
  (AG.SHELL-1 S1-S5) was achieved under different device/adbd conditions.
  1. **Update the AT bundle** to include the loopback rewrite mechanism
  adbd listening = wlan0 ports (39199, 38793), no 127.0.0.1 listener
  adbd behavior = accepts TLS from any source, but post-TLS CNXN only
  from loopback (127.0.0.1)
  1. AG's AT bundle (libandroidterminaladb.so 2.1MB) lacks the loopback
  2. Honor/MagicOS adbd only accepts post-TLS ADB connections from
  loopback (127.0.0.1)
  PASS was achieved under different device/adbd conditions.

==============================================================================
FILE: 02_at_v3_integration_gates.txt
==============================================================================

AG.AT-HISTORICAL-EVIDENCE-RECONCILIATION-0 - 02 AT V3 INTEGRATION GATES (at 1410d5f)
Date: 2026-11-04
Source: docs/integration/AG.V2-2.AT-INTEGRATION.md @ 1410d5f (AT V3 full engine)

 One-Shot D1-D4 (Regression)

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
HANDOFF_V1_PROVISIONAL_B

==============================================================================
FILE: 03_host_stop_d1_d4.txt
==============================================================================

AG.AT-HISTORICAL-EVIDENCE-RECONCILIATION-0 - 03 HOST-STOP D1-D4 (excerpts)
Date: 2026-11-04
Source: docs/implementation/AG.HOST-STOP-1.md (V1 pre-freeze; model-stream Stop target)

--- D1-D4 protocol (section 7) ---
  The manual acceptance that still needs a connected phone:
  - **D1** send `测试 Stop 功能`, watch streaming, tap Stop → `Stopping...` →
  `Stopped`, streaming stops, Stop disables, Send re-enables; fixture logs
  `CLIENT_DISCONNECTED`.
  - **D2** send `第二轮测试` → new request reaches the fixture, new stream, UI RUNNING.
  - **D3** let a run complete naturally → normal completion still works.
  - **D4** rapid double Stop → no crash, no duplicate `Stopped`, no stuck `Stopping`.


--- D1-D4 re-attempt results (section 12.4) ---
  D1_RUN_1
  D1_RUN_2
  D1                = PASS (both runs clean; no residue "Stopping..."; terminal "Stopped.")
  D2_RUN
  D2                = PASS (new request reached the fixture, new stream, UI RUNNING,
  D3_RUN
  D3                = INCONCLUSIVE (infrastructure timeout, NOT a Stop defect)
  D4_RUN
  D4                = PASS (rapid double Stop produced exactly one terminal event;
  D1                        = PASS (two clean runs; no residue)
  D2                        = PASS (new instance, new session, clean stop)
  D3                        = INCONCLUSIVE (infrastructure SocketTimeoutException,
  D4                        = PASS (rapid double Stop, exactly one terminal)
  HOST_005                  = VERIFIED (D1/D2/D4 PASS; D3 is an infrastructure issue

--- D3 closeout root cause (V1 commit e30d250) ---
  D3 root cause = fixture SSE framing (keep-alive vs close, Case E)
  fix = fixture Connection: close; D3 re-test PASS (2.2s, 12 tokens)

==============================================================================
FILE: 04_v040_model_capability_pty.txt
==============================================================================

AG.AT-HISTORICAL-EVIDENCE-RECONCILIATION-0 - 04 V0.4.0 MODEL CAPABILITY + PTY PERSISTENCE (excerpts)
Date: 2026-11-04
AT baseline: 7216d0e = v0.4.0. Device: HONOR ALI-AN00. AG UID 10370.

======================================================================
AG.AT-V4-MODEL-CAPABILITY-UPDATE-0 - Phase E direct ground truth
  | U15_spec_describes_local_shell_and_app_identity | PASS |
  | U16_spec_describes_adb_cli_availability | PASS |
  | U17_spec_distinguishes_local_and_adb_shell_principals | PASS |
  | U18_spec_preserves_result_semantics | PASS |
  | U19_tool_name_unchanged | PASS |
  | T8_same_instructions_same_hash (version→V1) | PASS |
  | T9_redaction_never_emits_fake_sentinels (version→V1) | PASS |
  | 全量回归 (324 tests) | PASS |
  | assembleDebug | PASS |
  | DIRECT_LOCAL_ID | PASS | `uid=10370(u0_a370) ... context=u:r:untrusted_app:s0` |
  | DIRECT_ADB_CLI | PASS | exit 0, "AndroidTerminal adb CLI (app_process entrypoint, bridge protocol v1)" |
  | DIRECT_ADB_ID | PASS | `uid=2000(shell) ... context=u:r:shell:s0` |
  | DIRECT_ADB_DEVICE_QUERY | PASS | `ALI-AN00` |
  全部通过 AG 生产 Tool 路径：`tool_invoke → ToolRegistry.dispatch → AndroidShellToolImpl → AtExecutionAdapter.executeLocal → Local PTY`
  **Verdict:** PASS (模型仅 2 步即正确区分两种 principal)
  **Verdict:** PASS (模型自主选择 ADB 路径，无需任何命令提示)
  | MODEL_AUTONOMOUS_LOCAL_SELECTION | PASS |
  | MODEL_AUTONOMOUS_ADB_SELECTION | PASS |
  | LOCAL_PRINCIPAL_OBSERVED | PASS |
  | ADB_PRINCIPAL_OBSERVED | PASS |
  | PRINCIPAL_DISTINCTION_CORRECT | PASS |
  | OBSERVATION_DEPENDENCY | PASS |
  | DEVICE_FACT_MATCH | PASS |
  PASS (uid=10370 u0_a370, untrusted_app)
  PASS (AT V4 CLI facade, app_process entrypoint, bridge protocol v1)
  PASS (uid=2000 shell, shell SELinux context)
  DIRECT_LOCAL_ID_GATE =
  PASS
  DIRECT_ADB_CLI_GATE =
  PASS
  DIRECT_ADB_ID_GATE =
  PASS
  DIRECT_ADB_DEVICE_QUERY_GATE =
  PASS
  PASS
  PASS
  PASS
  PASS
  PASS
  PASS
  PASS
  PASS
  PASS (ALI-AN00)
  PASS (324/324 + 5 new U15-U19)
  PASS
  PASS
  PASS — MODEL_AT_V4_SEMANTICS = VERIFIED

======================================================================
AG.AT-V4-PTY-PERSISTENCE-TEST-0 - Local PTY L1-L5
  ### L1: 创建与初始化 — PASS
  ### L2: 连续读写与状态保持 — PASS
  ### L3: stdin 与原始输出 — PASS
  ### L4: 异步输出与持续读取 — PASS
  ### L5: Ctrl-C — PASS
  ### L6: Resize — PASS
  ### L7: 自然退出 — PASS
  ### L8: 显式关闭 — PASS
  ### A4: Ctrl-C — PASS

==============================================================================
FILE: 05_execution_path_evolution.txt
==============================================================================

AG.AT-HISTORICAL-EVIDENCE-RECONCILIATION-0 - 05 EXECUTION PATH EVOLUTION (git -S milestones)
Date: 2026-11-04

AndroidShellToolImpl first appeared      : c76058f feat: integrate Android shell tool with AT Engine
AtExecutionAdapter first appeared         : 1410d5f AG.V2-2: integrate full AndroidTerminal V3 execution engine
executeAdb first appeared                 : 1410d5f AG.V2-2: integrate full AndroidTerminal V3 execution engine
LocalCliBootstrap first appeared          : f640ed0 freeze: establish post-I pre-UI AG core baseline
adapter.executeLocal (Local default) first: f640ed0 freeze: establish post-I pre-UI AG core baseline

NOTE: executeLocal-as-default lands in the uncommitted V4 migration, frozen at
f640ed0 (POST-I CORE FREEZE). Before that the production tool path was ADB-only.
PTY was NEVER model-visible (AG_MODEL_VISIBLE_CAPABILITY = ONE_SHOT ONLY).

==============================================================================
FILE: 06_final_verdict.txt
==============================================================================

AG.AT-HISTORICAL-EVIDENCE-RECONCILIATION-0 - 06 FINAL VERDICT
Date: 2026-11-04

VERDICT = PASS

Lineage established: V1 freeze 13df1db -> V2 baseline c84bf5d (parent EXACT);
  ADB-Regression = forensic LKG checkout (3aeed33); PreMigration path NOT_FOUND
  (real artifacts = AT-side archive + in-repo research workspace).

AT consumer lineage: Handoff v0 (0.1.0) -> V3 (c9cee21) -> V4/v0.4.0 (7216d0e)
  -> candidate v0.4.2 (925e7ac). V4 freeze == v0.4.0 tag target (EXACT).

Execution evolution: ADB one-shot (v0) -> V3 (ADB one-shot + internal PTY)
  -> V4/v0.4.0 (Local one-shot becomes MODEL DEFAULT; ADB via in-shell CLI).
  PTY never model-visible.

Stop layers: S1/S2/S3/S8 (product) VERIFIED; S4 PARTIAL (model path only);
  S5/S6/S7 (AT-native one-shot termination + cleanup) UNKNOWN in ALL baselines.
  S6 VERIFIED only for PTY-Ctrl-C (different mechanism).

Timeout: T1/T3/T6 VERIFIED/PARTIAL (product); T4/T5 (native stop + orphan) UNKNOWN.

Previous differential UNKNOWNs reclassified:
  pairing/readiness = HISTORICALLY_VERIFIED
  session recovery after interrupt = PRODUCT_LEVEL_VERIFIED_ONLY
  Local Stop/timeout/orphan + ADB cancel/timeout = STILL TRULY UNKNOWN (5)

TRUE remaining gaps (4): G1 Local Stop native+orphan, G2 Local timeout native+orphan,
  G3 ADB CLI cancel native+orphan, G4 ADB timeout native. All testable,
  none a regression, none an architecture blocker.

FINAL v0.4.2 regression gates = 7: G-a Local smoke (REDUCED), G-b Stop native (FULL P0),
  G-c timeout native (FULL P0), G-d local-first Wi-Fi+WD off (FULL),
  G-e local loss->wireless recovery (REDUCED), G-f pairing/trust-loss (REDUCED),
  G-g in-shell adb CLI one-shot (FULL).

RELEASE_V040_FIRST = NO_REASON_TO_RELEASE_ON_V040_FIRST
V042_SYNC_DIRECTION = PROCEED_TO_V042_CONSUMER_SYNC (then narrow 7-gate validation)

Invariants: NO code/dep/AAR/SSOT change; NO commit/tag/push/freeze.

==============================================================================
FILE: 07_controller_closeout_corrections.txt
==============================================================================

AG.AT-HISTORICAL-EVIDENCE-RECONCILIATION-0 - 07 CONTROLLER CLOSEOUT CORRECTIONS
Date: 2026-11-04
Added by: AG.AT-V042-PREFLIGHT-AUDIT-CLOSEOUT-0
Scope: documentation corrections + gate-definition precision only.
       No raw historical capture rewritten. No conclusion changed.

======================================================================
C1 - executeLocal wording corrected (Local one-shot, NOT Local PTY)
======================================================================
The Historical Audit described the v0.4.0 Phase E production path twice as
"executeLocal -> Local PTY". That wording is wrong. The measured path
(AT V4 / v0.4.0, current source) is:

  tool_invoke
  -> AndroidShellToolImpl
  -> AtExecutionAdapter.executeLocal(command, timeoutMs)
     (wraps: cd <appFilesDir> && <cli-bootstrap-source-line> && command)
  -> localEngine.execute(ATExecutionRequest(wrapped, timeoutMs))
  -> LocalOneShotBackend (com.at.backend.local.LocalOneShotBackend,
     ProcessBuilder /system/bin/sh -c command)

This is Local one-shot. The Local PTY surface is a separate implementation
(LocalPtySession / LocalPersistentBackend in at-backend-local) and was NOT
the Phase E path.

Occurrences corrected in the audit doc:
  - Section 13 (AT V4 / v0.4.0 Integration Evidence, Phase E description)
  - Section 19 (Limited Historical Source Comparison, LocalOneShotBackend bullet)

NOT changed:
  - Phase E PASS conclusions (DIRECT_LOCAL_ID / DIRECT_ADB_CLI / DIRECT_ADB_ID /
    DIRECT_ADB_DEVICE_QUERY all remain PASS).
  - Local one-shot historical verification status.
  - PTY evidence: Local PTY L1-L5 (Ctrl-C sleep 30 -> exit 130) and ADB PTY
    E-gates remain independent PTY-level evidence and are NOT mixed into the
    one-shot execution path.

Note on raw evidence: 04_v040_model_capability_pty.txt (and the consolidated
file) contain a verbatim capture line from the historical document
AG.AT-V4-MODEL-CAPABILITY-UPDATE-0 that reads "... executeLocal -> Local PTY".
Per the evidence policy (point-in-time raw captures are not rewritten), that
raw line is left untouched; this correction applies to the audit's
interpretive statements only.

======================================================================
C2 - One-shot is the canonical model execution model (clarification)
======================================================================
  NORMAL_AGENT_EXECUTION_MODEL = ONE_SHOT
  LOCAL_ONE_SHOT               = DEFAULT MODEL EXECUTION PATH
  ADB_ONE_SHOT                 = PRIVILEGED / ANDROID SHELL PATH VIA IN-SHELL ADB CLI
  PTY                          = OPTIONAL / FUTURE INTERACTIVE CAPABILITY
  PTY_MODEL_VISIBLE            = NO

This clarifies what the current code (AGENTS.md AG_MODEL_VISIBLE_CAPABILITY =
ONE_SHOT ONLY; AndroidShellToolImpl default -> executeLocal) and the historical
evidence already prove. It is NOT a new architecture freeze.

======================================================================
C3 - G-b (Stop / Interrupt) split into Local + ADB subcases
======================================================================
G-b = Stop / Interrupt, FULL P0. PASS requires BOTH subcases PASS:

  G-b1 = Local one-shot Stop
         User Stop
         -> AgentRuntime.interruptActiveRun
         -> execution Job cancellation
         -> AndroidShellTool Local one-shot
         -> LocalOneShotBackend ProcessBuilder child terminates
         -> no orphan

  G-b2 = in-shell ADB CLI one-shot Stop
         User Stop
         -> AgentRuntime.interruptActiveRun
         -> execution Job cancellation
         -> AndroidShellTool
         -> Local shell adb CLI
         -> CLI bridge
         -> AT ADB one-shot
         -> underlying ADB shell/native execution terminates
         -> no orphan

PTY Ctrl-C PASS (V3 E11 / v0.4.0 L5 -> exit 130) is NOT substitute evidence
for G-b1 or G-b2 (PTY control byte, different mechanism from AG coroutine
cancellation of a one-shot).

======================================================================
C4 - G-c (Timeout) split into Local + ADB subcases
======================================================================
G-c = Timeout, FULL P0. PASS requires BOTH subcases PASS:

  G-c1 = Local one-shot timeout
         ATExecutionRequest.timeoutMs in effect
         -> underlying LocalOneShotBackend child terminates
         -> Run reaches deterministic terminal state
         -> no orphan process
         -> subsequent Run works

  G-c2 = ADB CLI one-shot timeout
         ADB one-shot timeout reaches underlying ADB execution
         -> command actually terminates
         -> no lingering execution
         -> Run terminal state deterministic
         -> subsequent execution works

Only G-c1 + G-c2 together close G2 + G4.

======================================================================
G-g scope clarification
======================================================================
G-g = in-shell adb CLI one-shot end-to-end. Scope:
  - command routing
  - stdout
  - stderr
  - exitCode
  - shell UID
  - result mapping
  - Local/Wireless transport routing compatibility

G-g validates NORMAL ADB CLI execution ONLY.
G-g does NOT replace G-b2 (cancellation) or G-c2 (timeout):
three different semantics.

======================================================================
Final gate register (7 gates)
======================================================================
G-a  Local one-shot smoke (basic command + stdout + non-zero exit +
     basic ToolResult mapping)                          [REDUCED]
G-b  Stop / Interrupt                                   [FULL, P0]
     G-b1 Local one-shot Stop / native termination / no orphan
     G-b2 ADB CLI one-shot Stop / native termination / no orphan
G-c  Timeout                                            [FULL, P0]
     G-c1 Local one-shot timeout / no orphan
     G-c2 ADB CLI one-shot timeout / native termination
G-d  local-first steady state (Wi-Fi OFF + Wireless
     Debugging OFF; Local TCP available; AG unchanged;
     new execution succeeds; AG reads no transport
     implementation state)                              [FULL]
G-e  local loss -> wireless recovery (AT owns recovery;
     AG does not manually reconnect; no duplicate
     command; result deterministic)                     [REDUCED]
G-f  pairing-required / trust-loss (deterministic
     actionable state; no hang; no UI-string parsing;
     existing AG Host pairing flow compatible)          [REDUCED]
G-g  in-shell adb CLI one-shot end-to-end (NORMAL
     execution only; not a substitute for G-b2/G-c2)    [FULL]

Gap-to-gate mapping (evidence sources that close the genuine gaps):
  G1 (Local Stop)           -> G-b1
  G2 (Local timeout)        -> G-c1
  G3 (ADB CLI cancellation) -> G-b2
  G4 (ADB timeout)          -> G-c2

TRUE_REMAINING_GAP_COUNT = 4 (G1-G4, all TRULY_UNVERIFIED, testable,
no known regression, no architecture blocker).

======================================================================
Baseline status (unchanged by this closeout)
======================================================================
CURRENT_AG_AT_BASELINE = v0.4.0
CANDIDATE_AT_BASELINE  = v0.4.2 (candidate only)
V042_IS_CURRENT_BASELINE = NO
AG_V1_TARGET_AT_BASELINE = v0.4.2 candidate pending sync + validation
RELEASE_V040_FIRST_DECISION = NO_REASON_TO_RELEASE_ON_V040_FIRST
V042_SYNC_DIRECTION = PROCEED_TO_V042_CONSUMER_SYNC
NEXT = AG.AT-V042-CONSUMER-SYNC-0 (then AG.AT-V042-COMPAT-VALIDATION-1)

NO CODE CONCLUSION CHANGED.
NO VERSION RECOMMENDATION CHANGED.
NO HISTORICAL PASS RECLASSIFIED.
