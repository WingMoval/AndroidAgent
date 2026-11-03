# AG.AT-HISTORICAL-EVIDENCE-RECONCILIATION-0

TYPE = READ-ONLY / HISTORICAL EVIDENCE AUDIT / REPOSITORY LINEAGE RECONCILIATION / DEVICE VALIDATION PROVENANCE AUDIT / PRE-v0.4.2-SYNC DECISION INPUT
DATE = 2026-11-04
COMMIT = NO (Controller review input only; task section 39)

Mission: NOT "is v0.4.2 compatible?" (that is AG.AT-BASELINE-DIFFERENTIAL-AUDIT-0).
This Step asks: across the complete AG history, what execution / Stop / Interrupt /
timeout / one-shot / ADB / recovery / Session-reuse behaviors have ALREADY been
proven, under which exact execution path and AT generation -- so that v0.4.2
regression validation can be minimized, not repeated.

PRINCIPLE (task section 3, both directions enforced):
- "V2 lacks evidence for X" does NOT imply X was never tested.
- "X passed historically" does NOT imply it proves the CURRENT AT implementation.
  Every historical PASS is mapped to exact execution path + AT generation.

---

## 1. Repository Inventory (section 5)

```text
PATH = D:\codebank\AndCode\AndroidAgent
TYPE = GIT_REPO
HEAD = 13df1dbd892c02ff25ce5d8317095bc1deaaaf71 (= tag AG.V1-KNOWN-GOOD-FREEZE-V1, EXACT)
BRANCH = main (at the freeze commit)
REMOTE = none
WORKTREE = 2 untracked user-copy .md files (NOT production drift)
LIKELY_ROLE = V1 FROZEN_KNOWN_GOOD (reference/rollback/evidence)
DEEP_AUDIT = YES (done)

PATH = D:\codebank\AndCode\AndroidAgent-ADB-Regression
TYPE = GIT_REPO (DETACHED HEAD)
HEAD = 3aeed330169c724c42c4b2e8537b20361468aa49 ("test: verify AGENT-0 live adaptive tool loop")
BRANCH = (none - detached)
REMOTE = none
WORKTREE = CLEAN
LIKELY_ROLE = forensic checkout of V1 history at the LKG commit (AGENT-0.LIVE),
              created during AG.ADB-LKG-REGRESSION (V2 commit be9e619, 2026-10-18)
ROLE_EVIDENCE = identical tag set to V1 repo; 13df1db (freeze) IS a descendant of
                3aeed33 (verified via merge-base --is-ancestor)
DEEP_AUDIT = YES (done)

PATH = D:\codebank\AndCode\AndroidAgent-V2-PreMigration-Backup
TYPE = NOT_FOUND (directory does not exist on disk)
LIKELY_ROLE = the task's "premigration backup" is actually
              D:\codebank\AndCode\_archives\2026-10-02_AT_V2_premigration (AT-side archive)
              + V2 docs/v2-research/pre-migration-workspace (in-repo research snapshot)
DEEP_AUDIT = YES (both locations audited)

PATH = D:\codebank\AndCode\AndroidAgent-V2
TYPE = GIT_REPO
HEAD = c90f6d1bffebf80c16980644073d5057c2a64e70 (main)
REMOTE = none
WORKTREE = 4 untracked audit-generated files from the previous Step
           (docs/audit/AG.AT-BASELINE-DIFFERENTIAL-AUDIT-0.md,
            docs/evidence/at_baseline_differential_audit_0/,
            upload/AG.AT-BASELINE-DIFFERENTIAL-AUDIT-0.{md,evidence.txt})
           = NOT production drift (task section 40)
LIKELY_ROLE = ACTIVE DEVELOPMENT SOURCE OF TRUTH (current generation)
DEEP_AUDIT = YES (done)

PATH = D:\codebank\AndCode\_archives
TYPE = ARCHIVE_POOL (no .git)
CHILDREN:
  2026-10-02_AT_V2_premigration  = AT-side pre-migration snapshot
      (AndroidTerminal + AndroidTerminal-Public source zips + SHA256 manifests;
       README: pins AT trees before AT.V2-0 work; AT-side, NOT AG evidence)
  2026-10-04_pre_AT_V2_3B        = 2 research master docs (AT/AG research)
ARCHIVES_RELEVANT_SET = (none contain AG device evidence; AT-side only)
ARCHIVES_SKIPPED_SET  = both (no AG V1 / Run-Control / HOST-STOP / ShellTool /
                        ADB-regression / migration AG evidence inside)
REASON = contents are AT source archives + research masters; AG evidence lives
         in the AG repos' own docs/ + git history (verified by inventory)
```

## 2. AG Repository Lineage (section 7, verified by git)

```text
V1_SOURCE_REPO        = D:\codebank\AndCode\AndroidAgent
V1_FREEZE_COMMIT      = 13df1dbd892c02ff25ce5d8317095bc1deaaaf71 (tag AG.V1-KNOWN-GOOD-FREEZE-V1)
V2_BASELINE_COMMIT    = c84bf5d (tag AG.V2-BASELINE-V1) "AG.V2-1: establish V2 baseline from frozen V1"
V2_BASELINE_PARENT    = 13df1db (EXACTLY the V1 freeze commit)
PREMIGRATION_BACKUP_ROLE = _archives\2026-10-02_AT_V2_premigration = AT-side source
                           snapshot (pre AT.V2-0); the AG-side "pre-migration
                           workspace" is in-repo docs/v2-research/pre-migration-workspace
ADB_REGRESSION_REPO_ROLE = forensic LKG checkout (detached at 3aeed33 = AGENT-0.LIVE)
ADB_REGRESSION_REPO_ANCESTRY = 3aeed33 is an ANCESTOR of the V1 freeze 13df1db
CURRENT_V2_LINEAGE    = verified:
  231b319 (ag-baseline-0) -> ... -> 9d43598 (ag-0) -> ... -> 0ad83f2 (AGA-0)
  -> ... -> c76058f (AG.SHELL-1) -> 3aeed33 (AGENT-0.LIVE = LKG)
  -> ... -> e30d250 (HOST-STOP-1 D3 closeout) -> 13df1db (V1 FREEZE)
  -> c84bf5d (V2 BASELINE, parent = 13df1db)
  -> a3e7e61/2e5add1/5679dbf (V2-1 closeout + old-AT device smoke BLOCKED)
  -> be9e619 (AG.ADB-LKG-REGRESSION root cause)
  -> 1410d5f (AT V3 full engine) -> 22e3f28 (AT V3 consumer freeze)
  -> 1ea9654/50211c6 (reconciliation) -> 6c4d8f1 (full consumer)
  -> 4d8aec7 (session continuity fix) -> a6eea61 (full subsystem close)
  -> [V4 migration + 4 bug fixes, uncommitted at the time]
  -> f640ed0 (POST-I CORE FREEZE; V4 consumer 7216d0e + local-first default)
  -> b7dbc04 -> edfc603 (UI) -> a79c062 -> c90f6d1 (CURRENT)
```

Lineage conclusion: V2 is a DIRECT clone of the V1 freeze (parent commit exact);
the ADB-Regression repo is a detached forensic checkout of the V1 LKG commit;
the "PreMigration-Backup" path in the task does not exist -- the real premigration
artifacts are the AT-side archive + in-repo research workspace.
## 3. AG -> AT Consumer Lineage (section 8, verified)

| AG Phase | AG Commit/Tag | AT Generation | Exact AT Identity | Consumption Model | Evidence |
|---|---|---|---|---|---|
| AG.SHELL-1 (first tool) | c76058f | AT Handoff v0 | at-engine-handoff-v0 @ 0.1.0 (dirty HEAD 726a441, sourceTreeSha ff0a6432) | vendored AAR repo-local Maven (com.androidterminal:at-core/at-runtime:0.1.0) | AG.SHELL-1 doc section 2/3 |
| AGENT-0.LIVE (LKG) | 3aeed33 | AT Handoff v0 | same bundle, same SHA256 | same | ADB-LKG-REGRESSION section 3 |
| V1 freeze | 13df1db | AT Handoff v0 | same (byte-identical, PRODUCTION_DIFF=0) | same | V2-1 migration: `git diff --exit-code` = 0 |
| V2 baseline | c84bf5d | AT Handoff v0 | identical to V1 freeze | identical | AG.V2-1.BASELINE-MIGRATION |
| V2-1 device smoke | 5679dbf | AT Handoff v0 (old AT) | same binary | device smoke BLOCKED (post-TLS ADB CNXN fail, wlan0 IP) | 5679dbf |
| ADB-LKG-REGRESSION | be9e619 | AT Handoff v0 | root cause = AT native lib missing loopback rewrite + Honor adbd loopback-only | N/A (diagnosis) | be9e619 doc |
| AG.V2-2 (V3 full engine) | 1410d5f | AT V3 | AndroidTerminal-V3 composite build (source-tree, DRAFT contract) | composite build; AtExecutionAdapter introduced | AG.V2-2.AT-INTEGRATION (at 1410d5f) |
| AG.V2-2 (V3 consumer freeze) | 22e3f28 | AT V3 | AT.AG-CONSUMER-FREEZE-V1, source baseline c9cee21 | vendored AAR (third_party/at-consumer-freeze-v1, @1.0.0) | AG.V2-2.AT-INTEGRATION (current) |
| AT reconciliation + full consumer | 1ea9654/50211c6/6c4d8f1/a6eea61 | AT V3 | c9cee21 bundle (+ at-backend-local, at-cli added) | vendored AAR; E1-E20 device gates | AG.V2-2.AT-INTEGRATION + AG.AT-FULL-CONSUMER-INTEGRATION-0 |
| AT V4 consumer freeze (= v0.4.0) | a6eea61..f640ed0 (V4 migration uncommitted, frozen at f640ed0) | AT V4 / v0.4.0 | AT.V4-CONSUMER-FREEZE-V1 = 7216d0e = v0.4.0 tag target (EXACT, verified) | vendored AAR (third_party/at-v4-consumer-freeze-v1, @4.0.0, debug) | AT_TO_AG_HANDOFF_CURRENT + MANIFEST |
| CURRENT | c90f6d1 | AT V4 / v0.4.0 | 7216d0e | same | AGENTS.md + handoff |
| CANDIDATE | (not yet synced) | AT v0.4.2 | 925e7ac (verified) | would be new vendored bundle | differential audit |

Verified: AT V4 consumer freeze commit 7216d0e8131e92e8914ce7c8443e1668b1d53aca
IS the exact AndroidTerminal v0.4.0 tag target (git rev-list + describe, previous Step).

## 4. Model-Visible Execution Path Evolution (section 9)

| Phase | Model-Visible Default | Local/ADB | One-shot/PTY | Backend | AT Generation |
|---|---|---|---|---|---|
| AG.SHELL-1 (c76058f, 2026-10-06..08) | ADB shell one-shot | ADB | one-shot | at-runtime AtRuntime.execute (wireless ADB) | Handoff v0 |
| AGENT-0.LIVE (3aeed33) | ADB shell one-shot | ADB | one-shot | same | Handoff v0 |
| V1 freeze .. V2 baseline (13df1db..c84bf5d) | ADB shell one-shot | ADB | one-shot | same | Handoff v0 |
| AT V3 full engine (1410d5f, 2026-10-18) | ADB shell one-shot (unchanged) | ADB | one-shot (Local PTY = internal, E2 = N/A "not model-visible") | AtExecutionAdapter (composite V3) | V3 |
| AT V3 consumer freeze (22e3f28) | ADB shell one-shot | ADB | one-shot | adapter vendored V3 | V3 |
| Full consumer (6c4d8f1/a6eea61) | ADB shell one-shot | ADB | one-shot | adapter + CLI bridge | V3 |
| AT V4 migration (uncommitted, frozen f640ed0) | **Local one-shot (app UID) becomes DEFAULT**; ADB via in-shell `adb` CLI | Local default + ADB via CLI | one-shot | AtExecutionAdapter.executeLocal (LocalOneShotBackend ProcessBuilder /system/bin/sh); CLI bridge (LocalCliBootstrap + AdbCliBridgeServer) | V4 / v0.4.0 |
| CURRENT (c90f6d1) | Local one-shot default | Local + ADB via CLI | one-shot | same | V4 / v0.4.0 |

Milestones (git -S verified):
- AndroidShellToolImpl first appeared: c76058f (AG.SHELL-1).
- AtExecutionAdapter first appeared: 1410d5f (AG.V2-2, V3 full engine).
- executeAdb appeared: 1410d5f.
- LocalCliBootstrap appeared: 6c4d8f1 (V3 full consumer); rewired in V4 migration.
- AdbCliBridgeServer: V3 full consumer (6c4d8f1) onward.
- executeLocal became the DEFAULT Tool path: the uncommitted V4 migration
  (AG.AT-V4-AGENT-USE-0 semantics), frozen at f640ed0 (POST-I freeze).
  Before that, the tool's production constructor was ADB-only.
- Persistent PTY first available to AG: 1410d5f (adapter session surface;
  internal, NOT model-visible).
- PTY model-visible: NEVER (one-shot only remains model-visible at c90f6d1;
  AGENTS.md AG_MODEL_VISIBLE_CAPABILITY = ONE_SHOT ONLY).

## 5. AG.SHELL-1 Audit (section 11)

Source: docs/implementation/AG.SHELL-1_ANDROID_SHELL_TOOL_AT_ENGINE_INTEGRATION.md
AG commit: c76058f (+ bundle commit). AT baseline: at-engine-handoff-v0 @ 0.1.0.
Device: HONOR ALI-AN00 (APLMUT4312005935), Android 15, arm64-v8a.
Execution path: ADB one-shot (AtRuntime.execute, wireless ADB). NOT local.

| SCENARIO | COMMAND | EXPECTED | OBSERVED | STDOUT | STDERR | EXIT | NONZERO | LONGRUN | STOP | TIMEOUT | CLEANUP |
|---|---|---|---|---|---|---|---|---|---|---|---|
| S1 | ShellConnection.connect | READY | PASS | n/a | n/a | n/a | n/a | NO | NO | NO | n/a |
| S2 | {"command":"id"} | success/COMPLETED/uid=2000(shell) | PASS | YES | n/a | YES (0) | NO | NO | NO | NO | NO |
| S3 | sh -c 'echo OUT; echo ERR >&2; exit 7' | success/exitCode=7/OUT/ERR/hasError | PASS | YES | YES | YES (7) | YES | NO | NO | NO | NO |
| S4 | invalid command | success=false | PASS | n/a | n/a | n/a | n/a | NO | NO | NO | n/a |
| S5 | ToolRegistry dispatch echo AG0_S5_OK | PASS | PASS | YES | n/a | YES (0) | NO | NO | NO | NO | NO |

AG_SHELL_1_EXECUTION_EVIDENCE =
  ADB one-shot basic + stdout + stderr + exit + NON-ZERO exit + result mapping:
  HISTORICALLY_VERIFIED (device, AT Handoff v0).
  Long-running / Stop / timeout / process cleanup: NOT exercised (TRULY_UNVERIFIED
  at this phase -- no such test existed).
  NOTE: S1-S5 ADB connectivity was LATER shown to be device-adbd-state-dependent
  (ADB-LKG-REGRESSION: Honor adbd loopback-only; the PASS was under a different
  adbd/firmware state). The EXECUTION SEMANTICS (result mapping) remain valid;
  the ADB TRANSPORT connectivity is implementation/environment-specific.
## 6. AGENT-0.LIVE Audit (section 12)

Source: docs/implementation/AGENT-0.LIVE_REAL_AGENT_LOOP.md (at 3aeed33) +
ADB-LKG-REGRESSION LKG evidence.
MODEL_PROVIDER = OpenRouter (real model). REAL_MODEL = YES.
TOOL_SELECTION = YES (model autonomously chose android_shell).
ANDROID_SHELL_USED = YES.
EXECUTION_BACKEND = ADB shell one-shot (AtRuntime.execute, wireless ADB).
LOCAL_OR_ADB = ADB. ONESHOT_OR_PTY = one-shot.
OBSERVATION_RETURNED_TO_MODEL = YES (foreground app P observed, used in dependent ToolCall).
MULTI_STEP_AGENT_LOOP = YES. REAL_DEVICE = YES.
STOP_INVOLVED = NO. TIMEOUT_INVOLVED = NO.

AGENT_0_LIVE_EVIDENCE =
  Real adaptive agent E2E on ADB one-shot (AT Handoff v0): model selected the tool,
  observed device state, used it in a dependent call, final answer matched ground truth.
  This is EXECUTION + OBSERVATION evidence. It is NOT cancellation/Stop/timeout
  evidence (no Stop, no timeout occurred).

## 7. Run Control Historical Audit (section 13)

Sources: AG.RUN-CONTROL-0.PREFLIGHT (+CLOSEOUT), AG.RUN-CONTROL-1.DETERMINISTIC,
AG.RUN-CONTROL-1.DETERMINISTIC.FIX, AG.HOST-STOP-1. AG era: V1 pre-freeze
(commits within V1 history, below 13df1db). AT baseline at that time: Handoff v0
-- but CRITICALLY, the Run Control verification used a DETERMINISTIC
ScriptedProvider + a Python OpenAI-compatible HTTP fixture (127.0.0.1:18080),
NOT the AT shell tool. The cancelled unit was the MODEL STREAMING coroutine +
the Android HTTP transport read.

Three concepts, separated:
RUN_CONTROL_SEMANTICS = VERIFIED
  - Runtime owns the active-Run execution child Job; interruptActiveRun() atomically
    claims + cancels the real Job (AG.RUN-CONTROL-1.DETERMINISTIC.FIX).
  - Core finalizes Interrupted + emits exactly one RunFinished(Interrupted) +
    synthetic T; send() converts USER child cancellation to RunOutcome.Interrupted.
  - Deterministic: I1-I15 + M1-M3 + L2 + L2-FULL all PASS (real runtime, real
    provider, real transport). JVM-verified.

EXECUTION_CHILD_JOB_CANCELLATION = VERIFIED (at the AG coroutine boundary)
  - The structured execution child Job cancellation is real and deterministic
    (Job.cancel, not a cooperative flag). Propagation Core -> Provider ->
    Android HTTP transport proven (L2 transport cancel-safety; D1 fixture saw
    ConnectionAbortedError).

UNDERLYING_OS_OR_NATIVE_EXECUTION_TERMINATION = UNKNOWN / NOT_EXERCISED
  - No historical Run Control test cancelled an AT shell command. The Stop tests
    cancelled a MODEL STREAM, not a shell process. There is NO evidence that a
    coroutine cancellation actually terminates an in-flight AT Local
    ProcessBuilder child or an ADB shell stream. This is the crux of the
    previous differential audit's GAP-1.

## 8. HOST-STOP D1-D4 Audit (section 14, P0)

Source: docs/implementation/AG.HOST-STOP-1.md. AG era: V1 pre-freeze.
AT baseline: Handoff v0 in the build, but the Stop target was the MODEL STREAM
via a Python OpenAI-compatible fixture over ADB reverse (127.0.0.1:18080),
NOT the AT shell tool. Device: HONOR ALI-AN00.

| GATE | AG COMMIT | AT BASELINE | COMMAND_OR_FIXTURE | EXECUTION_PATH | STOP_ACTION | EXPECTED | OBSERVED | RUN_TERMINAL | SESSION_REUSABLE | SUBSEQUENT_RUN | PROCESS_CHECKED | PID | ORPHAN |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| D1 | V1 pre-freeze | Handoff v0 (not the stop target) | fixture 600-chunk stream, msg "d1" | MODEL STREAM (HTTP) | human Stop tap | Stopping->Stopped, stream stops, CLIENT_DISCONNECTED | PASS (2 clean runs; fixture saw ConnectionAbortedError) | Interrupted x1 | YES (same-session reuse in D1 double run) | YES | NO | NO | NO |
| D2 | same | same | fixture, fresh app instance + fresh session | MODEL STREAM | human Stop | new request new stream clean stop | PASS | Interrupted x1 | YES (fresh) | YES | NO | NO | NO |
| D3 | same | same | fixture 12-token natural completion | MODEL STREAM | none (natural) | normal completion | INCONCLUSIVE -> later PASS (root cause = fixture SSE keep-alive framing, NOT a Stop defect; D3 closeout e30d250) | Failure(SocketTimeout) then clean | n/a | n/a | NO | NO | NO |
| D4 | same | same | fixture, rapid double Stop | MODEL STREAM | double Stop | one terminal, no dup Stopped, no stuck | PASS | Interrupted x1 | YES | n/a | NO | NO | NO |

D3_INITIAL_FAILURE = SocketTimeoutException after 12/12 tokens (App did not see stream end).
D3_ROOT_CAUSE = fixture SSE framing (keep-alive vs close, Case E); App kept the
  connection open after [DONE] with no EOF. Fix = fixture Connection: close.
D3_RETEST_RESULT = PASS (2.2 s natural completion, 12 tokens, no timeout).

HOST_STOP_D1..D4 = ALL PASS on the FINAL build.
CRITICAL SCOPE NOTE: D1-D4 prove the AG product Stop semantic chain (UI ->
Runtime Job cancel -> Core Interrupted x1 -> transport disconnect -> UI reset ->
session reusable). They do NOT prove termination of an AT shell process, because
the stop target was the model HTTP stream, not a shell command. PROCESS_EXISTENCE /
PID / ORPHAN were NEVER checked (NO across all gates).

## 9. ADB-Regression Repo Audit (section 15)

ADB_REGRESSION_REPO_ROLE = forensic LKG checkout (detached at 3aeed33 = AGENT-0.LIVE).
HEAD = 3aeed33. ANCESTRY = ancestor of V1 freeze. SOURCE_BASELINE = V1 history.
TARGET_PROBLEM = post-TLS ADB CNXN failure (SSL_read err=1) via wlan0 IP on
  Honor/MagicOS, shared by V1 freeze AND V2 baseline.
ROOT_CAUSE = AT native lib (libandroidterminaladb.so 2.1MB) MISSING the loopback
  rewrite that AT app 0.1.0's 4.3MB lib had; Honor adbd accepts post-TLS ADB
  connections from loopback (127.0.0.1) only, not the wlan0 IP.
KNOWN_LKG = 3aeed33 (AGENT-0.LIVE), same native SHA256 as current.
AG_CODE_REGRESSION = NO (V1==V2 failure identical; production diff 0; native SHA256 identical).
AT_BINARY_REGRESSION = NO (not a regression; an OEM/adbd environment limitation of that lib).
EXECUTION_BEHAVIORS_VERIFIED = ADB handshake/TLS/loopback behavior (diagnostic only).
STOP_EVIDENCE = NONE. TIMEOUT_EVIDENCE = NONE. PROCESS_CLEANUP_EVIDENCE = NONE.
This repo tested ADB CONNECTIVITY, not Stop/timeout/cleanup.

## 10. PreMigration Backup Audit (section 16)

PREMIGRATION_BACKUP_ROLE = the named path does not exist. The real premigration
  artifacts are: (a) _archives\2026-10-02_AT_V2_premigration = AT-side source zips
  + SHA256 (pins AT before AT.V2-0; NOT AG evidence); (b) in-repo
  docs/v2-research/pre-migration-workspace = V1 pre-freeze research/decision docs.
PREMIGRATION_UNIQUE_EVIDENCE = none beyond what is already in V1 repo + V2 git history.
PREMIGRATION_UNIQUE_TESTS = none.
PREMIGRATION_EVIDENCE_LOST_IN_CURRENT_V2 = NO (all V1 evidence is reachable in the
  V1 frozen repo 13df1db and in V2 git history, since V2 parent = V1 freeze).
## 11. V1 -> V2 Evidence Inheritance (section 17)

Source: docs/migration/AG.V2-1.BASELINE-MIGRATION.md (at c84bf5d).
PRODUCTION_EQUIVALENCE = EXACT (PRODUCTION_DIFF_FROM_V1_FREEZE = 0; every diff was docs-only).
TEST_EQUIVALENCE = EXACT (no test diff).
AT_ARTIFACT_EQUIVALENCE = EXACT (third_party/at-engine-handoff-v0 `git diff --exit-code` = 0).

Therefore the V1 freeze real-device evidence IS inherited by the initial V2
baseline (DEVICE_EQUIVALENCE_SMOKE = PRIOR_FREEZE_EVIDENCE_INHERITED).

| Historical Evidence | V1 Valid | V2 Baseline Inherited | Later AT Change Invalidates It | Current Relevance |
|---|---|---|---|---|
| AG.SHELL-1 S1-S5 (ADB one-shot) | YES (device, Handoff v0) | YES | YES - ADB transport is env/lib-specific; execution semantics survive | SEMANTIC_ONLY (result mapping) |
| AGENT-0.LIVE (ADB agent E2E) | YES | YES | YES - same ADB transport caveat | SEMANTIC_ONLY (observation loop) |
| RUN-CONTROL I1-I15/M/L2 (deterministic) | YES (JVM+fixture) | YES | NO - AG-internal, AT-independent | FULLY_INHERITABLE |
| HOST-STOP D1-D4 (model stream Stop) | YES (device+fixture) | YES | NO for the model-stream path; but NOT shell-process evidence | SEMANTIC_ONLY (product Stop) |
| NETWORK-0 / FILE-0 shell regression | YES | YES | YES - ADB transport caveat | SEMANTIC_ONLY |

Key: inherited evidence that touches the AT ADB TRANSPORT is
implementation/environment-specific (the loopback/adbd finding). Inherited
evidence that is AG-internal (Run Control, Core semantics) is fully portable.

## 12. AT V3 Integration Evidence (section 18)

Sources: AG.V2-2.AT-INTEGRATION (at 1410d5f) + AG.AT-FULL-CONSUMER-INTEGRATION-0 (6c4d8f1).
Commits: 1410d5f (V3 full engine, composite), 22e3f28 (V3 consumer freeze c9cee21),
1ea9654/50211c6 (reconciliation), 6c4d8f1 (full consumer), a6eea61 (subsystem close).
Device: HONOR ALI-AN00. Canonical tests: 726 total / 0 fail / 6 skipped.

One-shot D1-D4 (V3, ADB): D1 id=uid2000 PASS, D2 echo PASS, D3 OUT/ERR/7 PASS,
  D4 nonexistent exit127 COMPLETED PASS. -> ADB one-shot basic/stdout/stderr/
  exit/nonzero = HISTORICALLY_VERIFIED on V3.
Persistent E1-E20 (V3): E1 ADB one-shot PASS, E2 Local one-shot N/A (internal,
  not model-visible), E3-E5 Local PTY PASS, E6-E14 ADB PTY (open/raw/stdin/cwd/
  Ctrl-C/resize/exit/close) PASS, E15 multi-session isolation PASS, E16
  Local+ADB same adapter PASS, E17 one-shot regression PASS, E18 identity
  PARTIAL (UNSAFE_KEY_COPY), E19 app-restart identity PASS, E20 no hybrid PASS.

Classification:
- E1/E17 (ADB one-shot) = AT_IMPLEMENTATION_SPECIFIC (V3 AdbEngine native).
- E3-E5 (Local PTY) = AT_IMPLEMENTATION_SPECIFIC (V3 Local backend).
- E6-E14 (ADB PTY) = AT_IMPLEMENTATION_SPECIFIC (V3 AdbEngine).
- E15 (multi-session) = AT_IMPLEMENTATION_SPECIFIC.
- E19 (identity reuse) = SEMANTIC_ONLY (AG-identity ownership survived to V4).
- Stop/timeout/cleanup = NOT in V3 gate set (no such gate). TRULY_UNVERIFIED.

## 13. AT V4 / v0.4.0 Integration Evidence (section 19)

Sources: AG.V2-2.AT-INTEGRATION (current, V4 c9cee21-era replaced by V4 freeze),
AT_TO_AG_HANDOFF_CURRENT (7216d0e), AG.AT-V4-MODEL-CAPABILITY-UPDATE-0,
AG.AT-V4-PTY-PERSISTENCE-TEST-0, AG.AT-V4-CLI-BOOTSTRAP-LIFECYCLE-FIX,
AG.POST-I freeze (f640ed0). AT baseline: 7216d0e = v0.4.0. Device: HONOR ALI-AN00.

V040 device gates re-validated at the V4/v0.4.0 migration:
- AG.AT-V4-MODEL-CAPABILITY-UPDATE-0 Phase E (direct, production tool path
  tool_invoke -> AndroidShellToolImpl -> AtExecutionAdapter.executeLocal
  -> localEngine.execute -> LocalOneShotBackend
  (ProcessBuilder /system/bin/sh) — Local one-shot, NOT Local PTY):
    DIRECT_LOCAL_ID PASS (uid=10370 app UID), DIRECT_ADB_CLI PASS,
    DIRECT_ADB_ID PASS (uid=2000), DIRECT_ADB_DEVICE_QUERY PASS.
  -> LOCAL one-shot basic (app UID) + ADB CLI one-shot + ADB shell id =
     HISTORICALLY_VERIFIED on v0.4.0, via the CURRENT production path.
- AG.AT-V4-PTY-PERSISTENCE-TEST-0 (DebugBridge, no LLM): Local PTY L1 create
  PASS, L2 cwd/env continuity PASS, L3 stdin/raw PASS, L4 async output PASS,
  L5 Ctrl-C (sleep 30 -> ^C -> 130) PASS. ADB PTY likewise. -> PTY (internal,
  not model-visible) semantics verified on v0.4.0, including Ctrl-C exit 130.
- AG.AT-V4-CLI-BOOTSTRAP-LIFECYCLE-FIX: canonical adb() bootstrap lifecycle.

V040_AG_DEVICE_GATES = MODEL-CAPABILITY Phase E (4) + PTY-PERSISTENCE (L1-L5 + ADB).
V040_LOCAL_ONESHOT_VERIFIED = YES (basic + identity, via executeLocal).
V040_ADB_ONESHOT_VERIFIED = YES (via in-shell adb CLI; direct executeAdb = adapter surface).
V040_ADB_CLI_VERIFIED = YES (DIRECT_ADB_CLI/ID/DEVICE_QUERY).
V040_STOP_REVALIDATED = NO (no v0.4.0 Stop test; Run Control Stop evidence is the
  V1 model-stream D1-D4, AT-independent).
V040_TIMEOUT_REVALIDATED = NO.
V040_SESSION_REUSABILITY_REVALIDATED = PARTIAL (TopicSession persistence resume
  verified at f640ed0 = durable session resume; but that is DB resume, not
  post-interrupt execution reuse on the shell path).
V040_PROCESS_CLEANUP_VERIFIED = NO (never checked in any baseline).

## 14. Local One-Shot Evidence Reconciliation (section 20)

LOCAL_ONESHOT_IMPLEMENTED_WHEN = V3 full engine (1410d5f); became MODEL DEFAULT
  at the V4 migration (frozen f640ed0).
LOCAL_ONESHOT_MODEL_VISIBLE_WHEN = f640ed0 (V4 local-first default).
LOCAL_ONESHOT_BASIC_EXECUTION = HISTORICALLY_VERIFIED (v0.4.0 Phase E DIRECT_LOCAL_ID,
  production path; also V3 E3-E5 internal).
LOCAL_ONESHOT_STDOUT = HISTORICALLY_VERIFIED (Phase E + result payload).
LOCAL_ONESHOT_STDERR = PARTIALLY_VERIFIED (result mapping proven on ADB S3/V3 D3;
  Local-specific stderr capture not separately device-asserted).
LOCAL_ONESHOT_EXIT_CODE = HISTORICALLY_VERIFIED (result payload; nonzero mapping proven on ADB).
LOCAL_ONESHOT_NON_ZERO_EXIT = PARTIALLY_VERIFIED (mapping proven on ADB path S3/D3;
  Local nonzero not separately device-asserted).
LOCAL_ONESHOT_LONG_RUNNING = PARTIALLY_VERIFIED (PTY L5 Ctrl-C on a sleep 30 proves
  a long-running Local process can be interrupted at PTY level; one-shot long-running
  not separately tested).
LOCAL_ONESHOT_STOP_PRODUCT_SEMANTIC = PRODUCT_LEVEL_VERIFIED_BUT_CLEANUP_UNVERIFIED
  (AG Stop -> Job.cancel -> Core Interrupted proven on the model-stream path;
  never exercised on a Local shell command).
LOCAL_ONESHOT_STOP_PROCESS_TERMINATION = TRULY_UNVERIFIED (no evidence a coroutine
  cancel terminates the LocalOneShotBackend ProcessBuilder child).
LOCAL_ONESHOT_TIMEOUT_PRODUCT_SEMANTIC = PARTIALLY_VERIFIED (AT timeoutMs exists and
  returns a terminal result; AG-side Run terminal state deterministic in JVM;
  not device-asserted on Local).
LOCAL_ONESHOT_TIMEOUT_PROCESS_TERMINATION = TRULY_UNVERIFIED.
LOCAL_ONESHOT_ORPHAN_ABSENCE = TRULY_UNVERIFIED (no pid/orphan check in any baseline).

## 15. ADB One-Shot Evidence Reconciliation (section 21)

Three DISTINCT paths (not merged):
(a) Direct executeAdb (adapter surface; model uses in-shell `adb` CLI instead).
(b) In-shell adb CLI bridge (LocalCliBootstrap + AdbCliBridgeServer + ATCliExecution).
(c) Persistent ADB PTY (internal, not model-visible).

ADB_ONESHOT_DIRECT_BASIC = HISTORICALLY_VERIFIED (V3 E1/D1-D4; v0.4.0 via CLI which
  routes to adbEngine.execute).
ADB_ONESHOT_CLI_BASIC = HISTORICALLY_VERIFIED (v0.4.0 Phase E DIRECT_ADB_CLI/ID/
  DEVICE_QUERY, production path).
ADB_ONESHOT_STOP_PRODUCT_SEMANTIC = PRODUCT_LEVEL_VERIFIED_BUT_CLEANUP_UNVERIFIED
  (same model-stream Stop evidence; never on an ADB command).
ADB_ONESHOT_NATIVE_TERMINATION = TRULY_UNVERIFIED (V3 E11 ADB PTY Ctrl-C=130 proves
  PTY-level Ctrl-C, NOT coroutine-cancel of a one-shot ADB command).
ADB_ONESHOT_TIMEOUT_PRODUCT_SEMANTIC = PARTIALLY_VERIFIED (AT timeoutMs; not device-asserted).
ADB_ONESHOT_TIMEOUT_NATIVE_TERMINATION = TRULY_UNVERIFIED.
ADB_ONESHOT_TRANSPORT_RECOVERY = HISTORICALLY_VERIFIED (AT-owned; ADB-LKG-REGRESSION
  characterized the failure; V3 E-gates + V4 ready/pairing gates; v0.4.2 adds local
  recovery -- but AG-side never re-owned it).
ADB_ONESHOT_ORPHAN_ABSENCE = TRULY_UNVERIFIED.
## 16. Stop / Interrupt Evidence Layers (section 22)

Evidence sources: HOST-STOP D1-D4 (V1, model-stream), RUN-CONTROL FIX (V1,
deterministic JVM+fixture), V3 E11 (ADB PTY Ctrl-C), v0.4.0 PTY L5 (Local PTY
Ctrl-C=130), v0.4.2 AT-side (AT-internal only, NOT AG path).

| Layer | Meaning | VERIFIED? | Evidence + AT generation |
|---|---|---|---|
| S1 UI | user Stop reaches Host | VERIFIED | HOST-STOP D1 (human tap -> Stopping) |
| S2 Runtime | interruptActiveRun / execution Job cancel | VERIFIED | RUN-CONTROL FIX M1 (real Job.cancel) |
| S3 Core | Run reaches Interrupted exactly once | VERIFIED | RUN-CONTROL I1-I15 + D1 fixture (Interrupted x1) |
| S4 Tool coroutine | Tool execution ceases normal completion | PARTIAL | proven for the MODEL/HTTP tool; NOT proven for the AT shell tool (a shell ToolCall was never the cancelled unit) |
| S5 AT API invocation | AT execute stops/returns/terminates deterministically | UNKNOWN | never exercised (Stop target was never an AT execute) |
| S6 OS/native execution | actual Process/native ADB command terminates | UNKNOWN for one-shot; VERIFIED for PTY-Ctrl-C only (V3 E11, v0.4.0 L5 = ^C -> 130, but that is a PTY control byte, not AG coroutine cancel) |
| S7 Cleanup | absence of orphan execution proven | UNKNOWN | no pid/orphan check in ANY baseline |
| S8 Recovery | subsequent Run succeeds, session reusable | VERIFIED (product) | D1 same-session double run; D2 fresh; TopicSession durable resume (f640ed0). NOT on the shell path specifically |

The layered matrix replaces "Stop is verified": S1-S3 + S8 (product) are VERIFIED;
S4 is PARTIAL (model path only); S5/S6/S7 (AT-native one-shot termination + cleanup)
are UNKNOWN. This is the precise form of the previous audit's "cancellation unknown".

## 17. Timeout Evidence Layers (section 23)

LOCAL_ONESHOT_TIMEOUT_LAYERS:
| T1 request exists | T2 terminal result | T3 AG Run terminal | T4 command actually stops | T5 no orphan | T6 subsequent works |
| VERIFIED (ATExecutionRequest.timeoutMs, default 30s) | PARTIAL (AT returns a result; not device-asserted on Local) | VERIFIED (deterministic JVM) | UNKNOWN | UNKNOWN | VERIFIED (product session reuse) |

ADB_ONESHOT_TIMEOUT_LAYERS:
| T1 | T2 | T3 | T4 | T5 | T6 |
| VERIFIED | PARTIAL | VERIFIED (JVM) | UNKNOWN | UNKNOWN | VERIFIED (product) |

PTY timeout: v0.4.0 L5 / V3 E11 show a long-running PTY command (sleep 30/100)
CAN be interrupted by Ctrl-C -> exit 130 (PTY-level, not AG timeout). This is
the only historical "underlying command actually stops" evidence, and it is
PTY-Ctrl-C specific, not one-shot-timeout specific.

## 18. Historical Evidence Validity (section 24)

| Old PASS | Classification |
|---|---|
| Core RunOutcome.Interrupted / RUN-CONTROL semantics | FULLY_INHERITABLE (AG-internal, AT-independent) |
| HOST-STOP D1-D4 (model-stream Stop) | SEMANTIC_ONLY (product Stop; not shell-process) |
| AG.SHELL-1 S1-S5 (ADB one-shot) | SEMANTIC_ONLY for result mapping; ADB transport = IMPLEMENTATION_SPECIFIC (loopback/adbd) |
| AGENT-0.LIVE (ADB agent E2E) | SEMANTIC_ONLY (observation loop) |
| V3 E1-E20 (ADB/Local PTY + one-shot) | IMPLEMENTATION_SPECIFIC (V3 native AdbEngine/Local backends) |
| v0.4.0 Phase E + PTY L1-L5 | SEMANTIC_ONLY / still-current (v0.4.0 = current AT; Local one-shot + CLI + PTY semantics valid TODAY) |
| ADB-LKG-REGRESSION loopback finding | IMPLEMENTATION_SPECIFIC (that lib + Honor adbd) |

## 19. Limited Historical Source Comparison (section 25)

- LocalOneShotBackend: V3 (ProcessBuilder /system/bin/sh) vs v0.4.0 (same class,
  com.at.backend.local.LocalOneShotBackend, injected as ATAndroidComposition.localEngine).
  The v0.4.0 Phase E direct test exercised the CURRENT LocalOneShotBackend via
  executeLocal -> localEngine.execute -> LocalOneShotBackend (one-shot; the
  separate Local PTY surface is LocalPtySession/LocalPersistentBackend and was
  NOT the Phase E path). So Local one-shot basic execution evidence is CURRENT
  (v0.4.0), not merely inherited from V3.
- ATEngine.execute: stable public contract across V3->v0.4.0 (at-engine-api).
  Cancellation semantics at the native boundary NOT changed and NOT verified.
- AdbOneShotBackend / AdbRuntime.executeShell: V3 vs v0.4.0 differ in native lib
  (V3 libadbd_engine vs v0.4.0 adb-engine) -- ADB one-shot native termination is
  implementation-specific and must be re-verified per generation (hence v0.4.2).
- AtExecutionAdapter.executeLocal/executeAdb: AG-owned, stable across V3->v0.4.0.

## 20. Reclassify Previous Differential Audit UNKNOWNs (section 26)

Previous audit (AG.AT-BASELINE-DIFFERENTIAL-AUDIT-0) marked these UNKNOWN:
- Local one-shot Stop (native) -> STILL_UNKNOWN (product Stop verified, process
  termination never tested). NOT upgraded by history.
- Local one-shot timeout (native) -> STILL_UNKNOWN.
- Local one-shot orphan cleanup -> STILL_UNKNOWN.
- ADB one-shot cancellation (native) -> STILL_UNKNOWN (V3 E11 / v0.4.0 L5 are
  PTY-Ctrl-C, not coroutine-cancel of a one-shot).
- ADB timeout (native) -> STILL_UNKNOWN.
- Session recovery after interrupt -> RECLASSIFIED: PRODUCT_LEVEL_VERIFIED_ONLY
  (D1/D2 + TopicSession durable resume prove session reusability at the product
  level; not on the shell-interrupt path specifically).
- pairing/readiness behavior -> RECLASSIFIED: HISTORICALLY_VERIFIED (V3 E19
  identity reuse; V4/V040 ready+pairing gates; v0.4.2 strengthens same-key
  re-pair -- AG-side Host flow already proven).

DIFFERENTIAL_AUDIT_RECLASSIFICATION:
  RECLASSIFIED_HISTORICALLY_VERIFIED = 1 (pairing/readiness)
  RECLASSIFIED_PRODUCT_LEVEL_ONLY = 1 (session recovery after interrupt)
  STILL_TRULY_UNKNOWN = 5 (Local Stop/timeout/orphan, ADB cancellation/timeout)

## 21. Historical Document Integrity (section 27)

No historical point-in-time document was edited.
HISTORICAL_REPORT_CONFLICT = one minor: AG.SHELL-1 S1-S5 recorded ADB connectivity
  PASS; the later ADB-LKG-REGRESSION shows that connectivity was
  adbd-state/environment-dependent (loopback-only on Honor). The two are
  reconciled (not a contradiction): S1-S5 semantics remain valid; the transport
  connectivity was environment-specific. RESOLUTION = keep both; annotate
  transport as environment-specific in THIS audit (done in section 5).
  No original document rewritten.
## 22. Deliverable A — AG Historical Timeline (section 31)

| ORDER/DATE | STEP | AG IDENTITY | AT IDENTITY | CAPABILITY | DEVICE EVIDENCE | STATUS |
|---|---|---|---|---|---|---|
| 1 | ag-baseline-0 (231b319) | project init | none | Core scaffold | none | FROZEN |
| 2 | ag-0 (9d43598) | AG Core v0 | none | Core runtime | none | FROZEN |
| 3 | ag-provider-0 (2b30212) | +provider | none | OpenAI provider | none | FROZEN |
| 4 | ag-aga-0 (2c8306c) | +AGA host | none | host runtime | chat PASS | FROZEN |
| 5 | ag-stable-0/1 (2fa5f2d/401a365) | baseline | none | stability | re-verify | FROZEN |
| 6 | ag-engine-0 (d9d9bf5) | Engine boundary | none | Core/Runtime boundary | audit | FROZEN |
| 7 | AG.SHELL-1 (c76058f) | +shell tool | Handoff v0 @0.1.0 | android_shell ADB one-shot | S1-S5 device | VERIFIED |
| 8 | AGENT-0.PREFLIGHT (74a653f) | instructions/trace | v0 | preflight | static | READY |
| 9 | AGENT-0.LIVE (3aeed33) | real agent | v0 | adaptive tool loop | device E2E (LKG) | VERIFIED |
| 10 | AG.NETWORK-0 | +network tool | v0 | fetch | L2/L3 device | PASS |
| 11 | AG.FILE-0 | +file tool | v0 | file READ | L2/L3 device | PASS |
| 12 | Conversation Control research | research | v0 | control semantics | none | VERIFIED |
| 13 | RUN-CONTROL-0/1 + FIX | Run ownership | v0 | Stop/Interrupt | deterministic + fixture | VERIFIED |
| 14 | HOST-STOP-1 | Stop UI | v0 | user Stop | D1-D4 device | VERIFIED (D3 re-test) |
| 15 | V1 freeze (13df1db) | V1 known-good | v0 | baseline | inherited | FROZEN |
| 16 | V2 baseline (c84bf5d) | V2 clone | v0 | equivalence | diff=0 | VERIFIED |
| 17 | ADB-LKG-REGRESSION (be9e619) | diagnosis | v0 | ADB loopback root cause | A/B/A device | ROOT_CAUSE |
| 18 | AT V3 integration (1410d5f..a6eea61) | +integration-at | V3 (c9cee21) | full V3 engine + consumer | D1-D4 + E1-E20 device | PASS |
| 19 | Host Session Continuity (c4a58fe/4d8aec7) | session UI | V3 | config-change session | device | VERIFIED |
| 20 | TopicSession research + freeze | persistence | V3 | durable session | research | FROZEN |
| 21 | TopicSession persistence (..f640ed0) | Room DB v1 | V4/v0.4.0 (7216d0e) | durable SSOT + resume | DEVICE-I device | FROZEN |
| 22 | POST-I architecture freeze (f640ed0) | pre-UI freeze | V4/v0.4.0 | local-first default | Phase E + PTY device | FROZEN |
| 23 | VNext UI (edfc603) | UI shell + cards | v0.4.0 | transcript/browser | C1-C9/S1-S10 device | CLOSED |
| 24 | CURRENT (c90f6d1) | current | v0.4.0 (7216d0e) | full current | inherited | CURRENT |
| 25 | CANDIDATE | (pending) | v0.4.2 (925e7ac) | local TCP / recovery | AT-side 7/7 + 74/74 | CANDIDATE |

## 23. Deliverable B — AG -> AT Historical Lineage Matrix (section 32)

See section 3 table (full phase/commit/AT/execution/validation matrix).

## 24. Deliverable C — Execution Evidence Matrix (section 33)

Legend: HV=HISTORICALLY_VERIFIED, PLV=PRODUCT_LEVEL_VERIFIED_BUT_CLEANUP_UNVERIFIED,
PV=PARTIALLY_VERIFIED, TU=TRULY_UNVERIFIED, NA=NOT_APPLICABLE, SI=SUPERSEDED_IMPLEMENTATION_ONLY.

| Capability | V1 (v0) | V2 Baseline (v0) | AT V3 | AT v0.4.0 | Current Confidence |
|---|---|---|---|---|---|
| Local one-shot basic | NA | NA | PV (internal E3-E5) | HV (Phase E) | HV |
| Local one-shot stdout | NA | NA | PV | HV | HV |
| Local one-shot stderr | NA | NA | PV | PV | PV |
| Local one-shot non-zero exit | NA | NA | PV | PV | PV |
| Local one-shot Stop | NA | NA | NA | NA | PLV (product only) |
| Local one-shot timeout | NA | NA | NA | NA | PV (product) |
| Local one-shot process cleanup | NA | NA | NA | NA | TU |
| Local one-shot orphan absence | NA | NA | NA | NA | TU |
| ADB one-shot basic | HV (S2/S5) | HV (inherited) | HV (D1-D4/E1) | HV (CLI Phase E) | HV |
| ADB CLI one-shot | NA | NA | NA (no CLI) | HV (Phase E) | HV |
| ADB one-shot Stop | NA | NA | NA | NA | PLV (product only) |
| ADB one-shot timeout | NA | NA | NA | NA | PV (product) |
| ADB transport loss | NA (env) | NA (env) | HV (E-gates) | HV (ready/pairing) | HV (AT-owned) |
| ADB recovery | NA | NA | HV | HV | HV (AT-owned) |
| readiness | HV (S1) | HV | HV (E19) | HV (Phase E) | HV |
| pairing | NA (deferred) | NA | HV (Gate A-E) | HV (Phase E pair) | HV |
| Run Interrupted semantics | HV | HV | HV (inherited) | HV (inherited) | HV (FULLY_INHERITABLE) |
| subsequent Run after Interrupt | HV (D1/D2) | HV | HV | HV (TopicSession) | PLV (product) |
| Session reuse | HV | HV | HV | HV | PLV (product) |
| Activity recreation | PV | PV | PV | PV (SessionHolder) | PV |

## 25. Deliverable D — Stop Layer Matrix (section 34)

S1-S8 matrix in section 16. AG product semantics (S1,S2,S3,S8) = VERIFIED across
V1->current (AT-independent). Physical process/native cleanup (S5,S6,S7) = UNKNOWN
for one-shot in every baseline; S6 = VERIFIED only for PTY-Ctrl-C (V3 E11, v0.4.0 L5),
which is a different mechanism than AG coroutine cancellation of a one-shot.

## 26. Deliverable E — Genuine Remaining Evidence Gaps (section 35)

GAP_ID = G1
CAPABILITY = Local one-shot Stop -> underlying ProcessBuilder child termination + orphan absence
CURRENT_CLASSIFICATION = TRULY_UNVERIFIED
WHY_HISTORICAL_EVIDENCE_DOES_NOT_CLOSE_IT = every historical Stop test targeted the model HTTP stream, never a Local shell command; no pid/orphan check exists in any baseline
AFFECTED_BASELINE = v0.4.0 (and v0.4.2)
NEEDS_V042_RETEST = YES
RELEASE_BLOCKING = CONDITIONAL (only if AG v1 must guarantee shell-process cleanup on Stop)

GAP_ID = G2
CAPABILITY = Local one-shot timeout -> underlying command actually stops + no orphan
CURRENT_CLASSIFICATION = TRULY_UNVERIFIED
WHY = AT timeoutMs exists and returns a result, but native stop + orphan absence never device-asserted on Local
AFFECTED_BASELINE = v0.4.0 (and v0.4.2)
NEEDS_V042_RETEST = YES
RELEASE_BLOCKING = CONDITIONAL

GAP_ID = G3
CAPABILITY = ADB one-shot (in-shell CLI) cancellation -> native ADB command termination + orphan
CURRENT_CLASSIFICATION = TRULY_UNVERIFIED
WHY = V3 E11 / v0.4.0 L5 are PTY-Ctrl-C, not coroutine-cancel of a one-shot ADB command; v0.4.2 changed the ADB native lib + added local TCP routing
AFFECTED_BASELINE = v0.4.2 specifically (native changed)
NEEDS_V042_RETEST = YES
RELEASE_BLOCKING = CONDITIONAL

GAP_ID = G4
CAPABILITY = ADB one-shot timeout -> native termination
CURRENT_CLASSIFICATION = TRULY_UNVERIFIED
WHY = same as G3; timeout native stop never asserted
AFFECTED_BASELINE = v0.4.2
NEEDS_V042_RETEST = YES
RELEASE_BLOCKING = CONDITIONAL

NOT carried forward (already closed by history):
- pairing/readiness (HISTORICALLY_VERIFIED), session recovery (PRODUCT_LEVEL),
  ADB transport loss/recovery (AT-owned, HV), Run Interrupted semantics (HV),
  Local/ADB one-shot basic + stdout/exit (HV/PV), ADB CLI one-shot (HV).

TRUE_REMAINING_GAP_COUNT = 4 (G1-G4), all centered on NATIVE one-shot
termination + orphan absence under Stop/timeout. All are testable, none is a
known regression, none is an architecture blocker.

## 27. Deliverable F — Recomputed v0.4.2 Regression Scope (section 36)

Previous plan (7 gates). Re-evaluated against recovered history:

1. Basic one-shot + non-zero exit -> KEEP_REDUCED.
   Local one-shot basic + stdout is already HISTORICALLY_VERIFIED on v0.4.0
   (current AT). The v0.4.2 delta for Local one-shot is small (Local TCP is the
   ADB transport, Local ProcessBuilder unchanged). Keep a SMOKE (basic + nonzero)
   to confirm the v0.4.2 artifact end-to-end, not a full re-prove.
2. Stop / Interrupt -> KEEP_FULL. This is GAP G1/G3. The AG Stop->AT-native
   termination chain was NEVER verified on any shell path. P0. Must run on v0.4.2.
3. timeout / no orphan -> KEEP_FULL. GAP G2/G4. Native stop + orphan absence
   never verified. P0.
4. local-first with Wi-Fi + Wireless Debugging off -> KEEP_FULL. NEW v0.4.2
   capability (Local TCP 127.0.0.1:38800). No historical equivalent. Must verify
   AG works unchanged when only local TCP is up.
5. Local loss to Wireless recovery -> KEEP_REDUCED. v0.4.2 AT-internal recovery
   (AT-owned; AG never re-owns). Verify AG does not break + no duplicate command;
   lighter than a full re-prove since recovery is AT-owned and AG surface unchanged.
6. pairing-required / trust-loss -> KEEP_REDUCED. pairing/readiness already
   HISTORICALLY_VERIFIED (V3 E19, v0.4.0 gates). v0.4.2 adds same-key re-pair;
   verify the AG Host pair flow still reaches a deterministic actionable state
   (no hang, no string parse) -- reduced, not full.
7. in-shell adb CLI one-shot -> KEEP_FULL. GAP G3. The ADB native lib + local
   TCP routing changed in v0.4.2; the CLI one-shot path (model's ADB access)
   must be re-verified end-to-end on v0.4.2.

AG-discovered additions: none new beyond G1-G4 (they map to gates 2/3/7).

FINAL_V042_MINIMUM_REGRESSION_GATE_COUNT = 7
FINAL_V042_MINIMUM_REGRESSION_GATES =
  G-a  Local one-shot smoke (basic command + stdout + non-zero exit + basic
       ToolResult mapping)                                    [KEEP_REDUCED]
  G-b  Stop / Interrupt, FULL P0, split into TWO subcases:
       G-b1  Local one-shot Stop:
             User Stop -> AgentRuntime.interruptActiveRun -> execution Job
             cancellation -> AndroidShellTool Local one-shot ->
             LocalOneShotBackend ProcessBuilder child terminates -> no orphan
       G-b2  in-shell ADB CLI one-shot Stop:
             User Stop -> AgentRuntime.interruptActiveRun -> execution Job
             cancellation -> AndroidShellTool -> Local shell adb CLI ->
             CLI bridge -> AT ADB one-shot -> underlying ADB shell/native
             execution terminates -> no orphan
       G-b PASS requires BOTH G-b1 and G-b2 PASS.
       PTY Ctrl-C PASS (V3 E11 / v0.4.0 L5) is NOT substitute evidence for
       either subcase (different mechanism).
  G-c  Timeout, FULL P0, split into TWO subcases:
       G-c1  Local one-shot timeout:
             ATExecutionRequest.timeoutMs in effect -> underlying
             LocalOneShotBackend child terminates -> Run reaches deterministic
             terminal state -> no orphan process -> subsequent Run works
       G-c2  ADB CLI one-shot timeout:
             ADB one-shot timeout reaches underlying ADB execution -> command
             actually terminates -> no lingering execution -> Run terminal
             state deterministic -> subsequent execution works
       G-c PASS requires BOTH G-c1 and G-c2 PASS.
  G-d  local-first steady state (Wi-Fi OFF + Wireless Debugging OFF; Local
       TCP available; AG unchanged; new AG execution succeeds; AG reads no
       transport implementation state)                        [KEEP_FULL]
  G-e  local loss -> wireless recovery (AT owns recovery; AG does not
       manually reconnect transport; no duplicate command; result
       deterministic)                                         [KEEP_REDUCED]
  G-f  pairing-required / trust-loss (deterministic actionable state;
       no hang; no UI-string parsing; existing AG Host pairing flow
       remains compatible)                                    [KEEP_REDUCED]
  G-g  in-shell adb CLI one-shot end-to-end: command routing,
       stdout, stderr, exitCode, shell UID, result mapping,
       Local/Wireless transport routing compatibility.
       G-g validates NORMAL ADB CLI execution ONLY.
       G-g does NOT replace G-b2 (cancellation) or G-c2 (timeout):
       three different semantics.                              [KEEP_FULL]

The count stays 7 but two are reduced in scope. The P0 core is G-b + G-c + G-g.
G-b contains the Local + ADB cancellation subcases; G-c contains the Local +
ADB timeout subcases; G-g proves normal ADB CLI execution only.
Therefore the evidence sources that actually close G1-G4 are:

  G1 -> G-b1   G2 -> G-c1   G3 -> G-b2   G4 -> G-c2

G-g is the ADB CLI normal-execution regression gate and does NOT by itself
close any cancellation/timeout gap.
## 28. Release Decision Question (section 37)

Is there an evidence-based reason to publish AndroidAgent first while still on
AT v0.4.0?

Reasons examined:
- v0.4.0 integration IS known-good for the model-visible surface (Local one-shot
  basic/identity, ADB CLI one-shot, readiness/pairing) -- all HISTORICALLY_VERIFIED
  on the current AT at the production path.
- BUT the same v0.4.0 baseline carries the SAME four genuine gaps (G1-G4): native
  one-shot Stop/timeout termination + orphan absence were never verified on v0.4.0
  either. Publishing on v0.4.0 would ship those exact unknowns.
- v0.4.2 is source-contract compatible (previous differential audit: public API
  unchanged), adds no known regression, and its AT-side evidence is strong (7/7
  human gates, 74/74 tests). Moving to v0.4.2 does not add risk to the model
  surface and is the vehicle to close G1-G4 with a narrow regression set.

Decision: there is NO evidence-based advantage to releasing on v0.4.0 first.
v0.4.0 does not close any gap that v0.4.2 leaves open; v0.4.2 is compatible and
is the path to close the remaining native-termination gaps. Releasing on v0.4.0
first would only add a later forced re-validation.

RELEASE_V040_FIRST_DECISION = NO_REASON_TO_RELEASE_ON_V040_FIRST

V042_SYNC_DIRECTION = PROCEED_TO_V042_CONSUMER_SYNC
  followed by the narrow 7-gate (G-a..G-g) v0.4.2 regression validation,
  with G-b (G-b1+G-b2) / G-c (G-c1+G-c2) / G-g as the P0 core.
  No discovered regression blocks the sync.

## 29. Invariants (read-only compliance)

AG_CODE_CHANGED = NO
AT_CODE_CHANGED = NO
TEST_CODE_CHANGED = NO
DEPENDENCY_CHANGED = NO
AAR_CHANGED = NO
SSOT_CHANGED = NO (AGENTS.md / Roadmap / AT_TO_AG_HANDOFF_CURRENT.md / freeze docs /
  previous audit docs / historical reports all UNTOUCHED)
COMMIT = NO
TAG = NO
PUSH = NO
FREEZE = NO

This Step created only: docs/audit/AG.AT-HISTORICAL-EVIDENCE-RECONCILIATION-0.md
(untracked; Controller review input). No SSOT or code modified.

---

## 30. Required Final Report (section 41)

AG.AT-HISTORICAL-EVIDENCE-RECONCILIATION-0 FINAL REPORT

VERDICT =
PASS

MODE =
READ_ONLY

CURRENT_AG_REPO =
D:\codebank\AndCode\AndroidAgent-V2

CURRENT_AG_BRANCH =
main

CURRENT_AG_HEAD =
c90f6d1bffebf80c16980644073d5057c2a64e70

CURRENT_AG_WORKTREE_BEFORE =
4 untracked audit files from prior Step (NOT production drift)

CURRENT_AG_WORKTREE_AFTER =
+ docs/audit/AG.AT-HISTORICAL-EVIDENCE-RECONCILIATION-0.md (untracked, this Step)

V1_REPO =
D:\codebank\AndCode\AndroidAgent

V1_FREEZE_COMMIT =
13df1dbd892c02ff25ce5d8317095bc1deaaaf71 (tag AG.V1-KNOWN-GOOD-FREEZE-V1, EXACT)

V2_BASELINE_COMMIT =
c84bf5d (tag AG.V2-BASELINE-V1; parent = 13df1db EXACT)

V2_INHERITED_V1_EVIDENCE =
YES (PRODUCTION/TEST/AT-ARTIFACT equivalence = EXACT, diff=0)

PREMIGRATION_BACKUP_ROLE =
named path NOT_FOUND; real artifacts = _archives/2026-10-02_AT_V2_premigration
(AT-side) + in-repo docs/v2-research/pre-migration-workspace

ADB_REGRESSION_REPO_ROLE =
forensic detached checkout of V1 LKG (3aeed33 = AGENT-0.LIVE)

ADB_REGRESSION_ROOT_CAUSE =
AT native lib missing loopback rewrite + Honor adbd loopback-only post-TLS
(NOT an AG code regression; V1==V2 failure identical; native SHA256 identical)

AG_AT_LINEAGE =
Handoff v0 (0.1.0) -> AT V3 (c9cee21) -> AT V4 / v0.4.0 (7216d0e) -> [candidate v0.4.2 (925e7ac)]

V1_AT_BASELINE =
at-engine-handoff-v0 @ 0.1.0

V2_INITIAL_AT_BASELINE =
at-engine-handoff-v0 @ 0.1.0 (identical to V1)

AT_V3_BASELINE =
AT.AG-CONSUMER-FREEZE-V1, source baseline c9cee21

AT_V4_BASELINE =
AT.V4-CONSUMER-FREEZE-V1, 7216d0e

AT_V4_EQUALS_V040 =
YES (7216d0e == v0.4.0 tag target, verified)

CURRENT_AT_BASELINE =
v0.4.0

CANDIDATE_AT_BASELINE =
v0.4.2

V042_IS_CURRENT_BASELINE =
NO (candidate only; consumer sync + validation not yet executed)

AG_V1_TARGET_AT_BASELINE =
v0.4.2 candidate pending sync + validation

AG_SHELL_1_EVIDENCE =
ADB one-shot basic/stdout/stderr/exit/nonzero = HISTORICALLY_VERIFIED (device, Handoff v0);
transport connectivity = environment-specific (loopback/adbd); Stop/timeout/cleanup = not exercised

AGENT_0_LIVE_EVIDENCE =
real adaptive agent E2E on ADB one-shot (observation loop); NOT Stop/timeout/cancellation evidence

RUN_CONTROL_EVIDENCE =
RUN_CONTROL_SEMANTICS=VERIFIED; EXECUTION_CHILD_JOB_CANCELLATION=VERIFIED (AG boundary);
UNDERLYING_OS_PROCESS_TERMINATION=UNKNOWN (never exercised on a shell command)

HOST_STOP_D1 =
PASS (model-stream Stop; 2 clean runs; fixture ConnectionAbortedError)
HOST_STOP_D2 =
PASS (fresh instance/session, clean stop)
HOST_STOP_D3 =
INCONCLUSIVE -> PASS (root cause = fixture SSE keep-alive framing; fix = Connection: close)
HOST_STOP_D4 =
PASS (rapid double Stop, exactly one terminal, no dup)
SCOPE NOTE = D1-D4 target the MODEL HTTP stream, NOT an AT shell command; PROCESS/PID/ORPHAN never checked

LOCAL_ONESHOT_BASIC =
HISTORICALLY_VERIFIED (v0.4.0 Phase E, production path)
LOCAL_ONESHOT_STOP_PRODUCT_SEMANTIC =
PRODUCT_LEVEL_VERIFIED_BUT_CLEANUP_UNVERIFIED
LOCAL_ONESHOT_STOP_PROCESS_TERMINATION =
TRULY_UNVERIFIED
LOCAL_ONESHOT_TIMEOUT_PRODUCT_SEMANTIC =
PARTIALLY_VERIFIED
LOCAL_ONESHOT_TIMEOUT_PROCESS_TERMINATION =
TRULY_UNVERIFIED
LOCAL_ONESHOT_ORPHAN_ABSENCE =
TRULY_UNVERIFIED

ADB_ONESHOT_BASIC =
HISTORICALLY_VERIFIED (V3 D1-D4/E1 + v0.4.0 CLI Phase E)
ADB_ONESHOT_STOP_PRODUCT_SEMANTIC =
PRODUCT_LEVEL_VERIFIED_BUT_CLEANUP_UNVERIFIED
ADB_ONESHOT_NATIVE_TERMINATION =
TRULY_UNVERIFIED (V3 E11 / v0.4.0 L5 = PTY-Ctrl-C, not coroutine-cancel of one-shot)
ADB_ONESHOT_TIMEOUT =
PARTIALLY_VERIFIED (product) / native TRULY_UNVERIFIED
ADB_CLI_ONESHOT =
HISTORICALLY_VERIFIED (v0.4.0 Phase E, production path)

STOP_LAYER_S1_UI =
VERIFIED
STOP_LAYER_S2_RUNTIME =
VERIFIED
STOP_LAYER_S3_CORE =
VERIFIED
STOP_LAYER_S4_TOOL =
PARTIAL (model/HTTP tool only; AT shell tool never the cancelled unit)
STOP_LAYER_S5_AT_API =
UNKNOWN
STOP_LAYER_S6_OS_NATIVE =
UNKNOWN (one-shot); VERIFIED only for PTY-Ctrl-C (different mechanism)
STOP_LAYER_S7_CLEANUP =
UNKNOWN (no pid/orphan check in any baseline)
STOP_LAYER_S8_RECOVERY =
VERIFIED (product: D1/D2 + TopicSession durable resume)

PREVIOUS_UNKNOWN_COUNT =
7 (from differential audit: Local Stop/timeout/orphan, ADB cancel/timeout, session recovery, pairing/readiness)

RECLASSIFIED_HISTORICALLY_VERIFIED =
1 (pairing/readiness)

RECLASSIFIED_PRODUCT_LEVEL_ONLY =
1 (session recovery after interrupt)

STILL_TRULY_UNKNOWN =
5 (Local Stop/timeout/orphan, ADB cancellation/timeout)

TRUE_REMAINING_GAP_COUNT =
4 (G1 Local Stop native+orphan, G2 Local timeout native+orphan, G3 ADB CLI cancel native+orphan, G4 ADB timeout native)

TRUE_REMAINING_GAPS =
G1,G2,G3,G4 = native one-shot termination + orphan absence under Stop/timeout (all testable, none a regression, none an architecture blocker)

PREVIOUS_V042_GATE_COUNT =
7

FINAL_V042_MINIMUM_REGRESSION_GATE_COUNT =
7

FINAL_V042_MINIMUM_REGRESSION_GATES =
G-a Local one-shot smoke (REDUCED) |
G-b Stop/Interrupt native+orphan (FULL, P0) = G-b1 Local one-shot Stop + G-b2 ADB CLI one-shot Stop |
G-c timeout native+orphan (FULL, P0) = G-c1 Local one-shot timeout + G-c2 ADB CLI one-shot timeout |
G-d local-first Wi-Fi+WD off (FULL) |
G-e local loss->wireless recovery (REDUCED) | G-f pairing/trust-loss deterministic (REDUCED) |
G-g in-shell adb CLI one-shot end-to-end (FULL; NORMAL execution only,
  does not replace G-b2/G-c2)
GAP_TO_GATE_MAPPING = G1->G-b1 | G2->G-c1 | G3->G-b2 | G4->G-c2

RELEASE_V040_FIRST_DECISION =
NO_REASON_TO_RELEASE_ON_V040_FIRST

V042_SYNC_DIRECTION =
PROCEED_TO_V042_CONSUMER_SYNC

AG_CODE_CHANGED =
NO
AT_CODE_CHANGED =
NO
TEST_CODE_CHANGED =
NO
DEPENDENCY_CHANGED =
NO
AAR_CHANGED =
NO
SSOT_CHANGED =
NO
COMMIT =
NO
TAG =
NO
PUSH =
NO
FREEZE =
NO
NEXT =
CONTROLLER_REVIEW
STOP =
YES

---

## 31. Controller Review Corrections / Closeout Addendum (AG.AT-V042-PREFLIGHT-AUDIT-CLOSEOUT-0)

Date: 2026-11-04. This addendum records the Controller Review corrections
applied to THIS audit document. No code conclusion, version recommendation, or
historical PASS classification is changed.

C1 = executeLocal -> Local PTY wording corrected to the Local one-shot path.
     Correct path:
       tool_invoke
       -> AndroidShellToolImpl
       -> AtExecutionAdapter.executeLocal
       -> localEngine.execute
       -> LocalOneShotBackend (com.at.backend.local.LocalOneShotBackend,
          ProcessBuilder /system/bin/sh)
     This is Local one-shot, NOT Local PTY. Two occurrences corrected
     (section 13 Phase E description; section 19 source comparison).
     Local PTY evidence remains separate (Local PTY L1-L5; ADB PTY E-gates;
     Ctrl-C -> exit 130) and is NOT mixed into the one-shot execution path.
     Phase E PASS conclusions and Local one-shot historical verification are
     unchanged.

C2 = One-shot confirmed as the canonical model execution model:
     NORMAL_AGENT_EXECUTION_MODEL = ONE_SHOT
     LOCAL_ONE_SHOT = DEFAULT MODEL EXECUTION PATH
     ADB_ONE_SHOT = PRIVILEGED / ANDROID SHELL PATH VIA IN-SHELL ADB CLI
     PTY = OPTIONAL / FUTURE INTERACTIVE CAPABILITY
     PTY_MODEL_VISIBLE = NO
     This is a clarification of what the current code and historical evidence
     already prove; it is NOT a new architecture freeze.

C3 = G-b (Stop / Interrupt, FULL P0) is split into two subcases:
     G-b1 = Local one-shot Stop (User Stop -> interruptActiveRun -> execution
            Job cancellation -> AndroidShellTool Local one-shot ->
            LocalOneShotBackend ProcessBuilder child terminates -> no orphan)
     G-b2 = in-shell ADB CLI one-shot Stop (User Stop -> interruptActiveRun ->
            execution Job cancellation -> AndroidShellTool -> Local shell
            adb CLI -> CLI bridge -> AT ADB one-shot -> underlying ADB
            shell/native execution terminates -> no orphan)
     Both subcases must PASS for G-b to PASS.
     PTY Ctrl-C PASS is NOT substitute evidence for G-b1 or G-b2.

C4 = G-c (Timeout, FULL P0) is split into two subcases:
     G-c1 = Local one-shot timeout (timeoutMs in effect -> underlying
            LocalOneShotBackend child terminates -> Run reaches deterministic
            terminal state -> no orphan -> subsequent Run works)
     G-c2 = ADB CLI one-shot timeout (ADB one-shot timeout reaches underlying
            ADB execution -> command actually terminates -> no lingering
            execution -> Run terminal state deterministic -> subsequent
            execution works)
     Both subcases must PASS for G-c to PASS; only G-c1 + G-c2 together
     close G2 + G4.

G-g scope clarified: in-shell adb CLI one-shot end-to-end (command routing,
stdout, stderr, exitCode, shell UID, result mapping, transport routing
compatibility). G-g validates NORMAL ADB CLI execution only and does NOT
replace G-b2 (cancellation) or G-c2 (timeout).

Gap-to-gate mapping (evidence sources that close the genuine gaps):
  G1 -> G-b1    G2 -> G-c1    G3 -> G-b2    G4 -> G-c2

Baseline status (unchanged):
  CURRENT_AG_AT_BASELINE = v0.4.0
  CANDIDATE_AT_BASELINE  = v0.4.2 (candidate only; V042_IS_CURRENT_BASELINE = NO)
  RELEASE_V040_FIRST_DECISION = NO_REASON_TO_RELEASE_ON_V040_FIRST (unchanged)
  V042_SYNC_DIRECTION = PROCEED_TO_V042_CONSUMER_SYNC (unchanged)

NO CODE CONCLUSION CHANGED.
NO VERSION RECOMMENDATION CHANGED.
NO HISTORICAL PASS RECLASSIFIED.
