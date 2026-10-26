# AG.POST-I-GIT-NORMALIZATION-CORE-FREEZE-1 - Consolidated Evidence

Merged from docs/evidence/ag_pre_ui_core_freeze/ (00-06 + 01.md). Single-document
evidence bundle for the pre-UI Git normalization + core freeze round.

Round identity: FREEZE_COMMIT = f640ed07f655856888c11f3fb3575e6f84106f7c
FREEZE_TAG = AG.POST-I-CORE-FREEZE-V1 (annotated); TAG_TARGET == HEAD; WORKTREE CLEAN; PUSH = NO

---


<!-- ===== FILE: 00_git_baseline.txt ===== -->

BRANCH: main
START_HEAD: a6eea61dc209898a01cd0e7f2e82304fff7ed9d4
MODIFIED: 40
UNTRACKED: 0
DELETED: 81
TOTAL_DIRTY: 84

---


<!-- ===== FILE: 01_dirty_file_classification.tsv ===== -->

PATH	STATUS	CATEGORY	ORIGIN_PHASE	INTENDED_FOR_FREEZE	SENSITIVE	MACHINE_LOCAL	GENERATED	DECISION	RATIONALE
ag-core/build.gradle.kts	M	A	Phase D	YES	NO	NO	NO	COMMIT	build input (kotlinx-serialization-json)
ag-core/src/main/kotlin/com/androidagent/core/AgentCore.kt	M	A	Phase A/B/C/F	YES	NO	NO	NO	COMMIT	Core loop + durability seam
ag-core/src/main/kotlin/com/androidagent/core/RunOutcome.kt	M	A	Phase A/B	YES	NO	NO	NO	COMMIT	Run outcome domain
ag-core/src/main/kotlin/com/androidagent/core/Session.kt	M	A	Phase C/F	YES	NO	NO	NO	COMMIT	Session domain + restore
ag-core/src/main/kotlin/com/androidagent/core/MessageDurability.kt	??	A	Phase C/F	YES	NO	NO	NO	COMMIT	core durability contract
ag-core/src/test/kotlin/com/androidagent/core/AgentLoopTest.kt	M	A	Phase A/B	YES	NO	NO	NO	COMMIT	test
ag-core/src/test/kotlin/com/androidagent/core/EventSinkContractTest.kt	M	A	Phase A/B	YES	NO	NO	NO	COMMIT	test
ag-core/src/test/kotlin/com/androidagent/core/SessionConcurrencyTest.kt	M	A	Phase C	YES	NO	NO	NO	COMMIT	test
ag-core/src/test/kotlin/com/androidagent/core/ToolRegistryTest.kt	M	A	Phase A	YES	NO	NO	NO	COMMIT	test
ag-core/src/test/kotlin/com/androidagent/core/InterruptCloseoutTest.kt	??	A	RUN-CONTROL	YES	NO	NO	NO	COMMIT	test
ag-runtime/build.gradle.kts	M	A	Phase D/F	YES	NO	NO	NO	COMMIT	build input (serialization)
ag-runtime/src/main/kotlin/com/androidagent/runtime/AgentRuntime.kt	M	A	Phase A-I	YES	NO	NO	NO	COMMIT	Runtime lifecycle/restore/currentHistory
ag-runtime/src/main/kotlin/com/androidagent/runtime/persistence/MessageSerializer.kt	??	A	Phase D	YES	NO	NO	NO	COMMIT	storage-neutral serializer
ag-runtime/src/main/kotlin/com/androidagent/runtime/persistence/Records.kt	??	A	Phase C/D	YES	NO	NO	NO	COMMIT	storage-neutral records
ag-runtime/src/main/kotlin/com/androidagent/runtime/persistence/TopicSessionStore.kt	??	A	Phase C	YES	NO	NO	NO	COMMIT	store contract
ag-runtime/src/test/kotlin/com/androidagent/runtime/AgentRuntimeInterruptTest.kt	M	A	RUN-CONTROL-1	YES	NO	NO	NO	COMMIT	test
ag-runtime/src/test/kotlin/com/androidagent/runtime/AgentRuntimeTest.kt	M	A	Phase A-I	YES	NO	NO	NO	COMMIT	test
ag-runtime/src/test/kotlin/com/androidagent/runtime/TestSupport.kt	M	A	Phase A-I	YES	NO	NO	NO	COMMIT	test support
ag-runtime/src/test/kotlin/com/androidagent/runtime/RuntimeSessionCreationTest.kt	??	A	Phase G	YES	NO	NO	NO	COMMIT	test
ag-runtime/src/test/kotlin/com/androidagent/runtime/StartupRestoreTest.kt	??	A	Phase F/G	YES	NO	NO	NO	COMMIT	test
ag-runtime/src/test/kotlin/com/androidagent/runtime/persistence/InMemoryTopicSessionStore.kt	??	A	Phase C	YES	NO	NO	NO	COMMIT	test store
ag-runtime/src/test/kotlin/com/androidagent/runtime/persistence/MessageSerializerTest.kt	??	A	Phase D	YES	NO	NO	NO	COMMIT	test
ag-runtime/src/test/kotlin/com/androidagent/runtime/persistence/TopicSessionStoreContractTest.kt	??	A	Phase C	YES	NO	NO	NO	COMMIT	test
app/build.gradle.kts	M	A	Phase E/F (Room)	YES	NO	NO	NO	COMMIT	build input
app/src/main/java/com/androidagent/app/AgentInstructions.kt	M	A	AGENT-0.PREFLIGHT	YES	NO	NO	NO	COMMIT	host instructions
app/src/main/java/com/androidagent/app/ChatActivity.kt	M	A	Phase I/observability	YES	NO	NO	NO	COMMIT	host UI
app/src/main/java/com/androidagent/app/ChatCompositionRoot.kt	M	A	AG.V2-2/Phase F	YES	NO	NO	NO	COMMIT	Composition Root
app/src/main/java/com/androidagent/app/SessionHolder.kt	M	A	Phase G/I	YES	NO	NO	NO	COMMIT	process-scoped holder
app/src/main/java/com/androidagent/app/TranscriptModel.kt	M	A	Phase I	YES	NO	NO	NO	COMMIT	presentation projection
app/src/main/java/com/androidagent/app/CompositionAdbSemantic.kt	??	A	AG.V2-2	YES	NO	NO	NO	COMMIT	host ADB semantic (M1 DEFERRED)
app/src/main/java/com/androidagent/app/debug/DebugBridge.kt	M	A	AG.DEBUG-0/HOST-STOP	YES	NO	NO	NO	COMMIT	debug bridge
app/src/main/java/com/androidagent/app/debug/DebugProtocol.kt	M	A	AG.DEBUG-0	YES	NO	NO	NO	COMMIT	debug protocol
app/src/main/java/com/androidagent/app/trace/AgentTraceCollector.kt	M	A	AGENT-0.PREFLIGHT	YES	NO	NO	NO	COMMIT	trace collector
app/src/main/java/com/androidagent/app/persistence/Daos.kt	??	A	Phase E	YES	NO	NO	NO	COMMIT	Room DAOs
app/src/main/java/com/androidagent/app/persistence/Entities.kt	??	A	Phase E	YES	NO	NO	NO	COMMIT	Room entities
app/src/main/java/com/androidagent/app/persistence/RoomTopicSessionStore.kt	??	A	Phase E	YES	NO	NO	NO	COMMIT	Room impl
app/src/main/java/com/androidagent/app/persistence/StoreMessageDurability.kt	??	A	Phase F	YES	NO	NO	NO	COMMIT	durability adapter
app/src/main/java/com/androidagent/app/persistence/TopicSessionDatabase.kt	??	A	Phase E	YES	NO	NO	NO	COMMIT	Room DB (version 1)
app/src/test/java/com/androidagent/app/AgentPreflightTest.kt	M	A	AGENT-0.PREFLIGHT/AG.V2-2	YES	NO	NO	NO	COMMIT	test
app/src/test/java/com/androidagent/app/HostStopStateTest.kt	M	A	HOST-STOP-1	YES	NO	NO	NO	COMMIT	test
app/src/test/java/com/androidagent/app/TranscriptModelTest.kt	M	A	OBSERVABILITY-1/Phase I	YES	NO	NO	NO	COMMIT	test
app/src/test/java/com/androidagent/app/PhaseITranscriptProjectionTest.kt	??	A	Phase I	YES	NO	NO	NO	COMMIT	test
app/src/test/java/com/androidagent/app/persistence/PhaseGRoomIntegrationTest.kt	??	A	Phase G	YES	NO	NO	NO	COMMIT	test
app/src/test/java/com/androidagent/app/persistence/RoomTopicSessionStoreTest.kt	??	A	Phase E	YES	NO	NO	NO	COMMIT	test
app/src/test/java/com/androidagent/app/persistence/StoreMessageDurabilityTest.kt	??	A	Phase F	YES	NO	NO	NO	COMMIT	test
build.gradle.kts	M	A	AG.V2-2/AT V4	YES	NO	NO	NO	COMMIT	root build (AT m2 repo, versions)
docs/integration/AT_TO_AG_HANDOFF_CURRENT.md	M	A/C	AG.V2-2.AT-INTEGRATION	YES	NO	NO	NO	COMMIT	integration canonical pointer
integration-at/build.gradle.kts	M	A	AG.V2-2 (AT V4)	YES	NO	NO	NO	COMMIT	build input
integration-at/src/main/java/com/androidagent/integration/at/AtExecutionAdapter.kt	M	A	AG.V2-2 (AT V4)	YES	NO	NO	NO	COMMIT	AT adapter
integration-at/src/main/java/com/androidagent/integration/at/AdbSemantic.kt	??	A	AG.V2-2	YES	NO	NO	NO	COMMIT	AT adapter type
integration-at/src/main/java/com/androidagent/integration/at/AtAdbBootstrap.kt	??	A	AG.V2-2	YES	NO	NO	NO	COMMIT	AT adapter
integration-at/src/main/java/com/androidagent/integration/at/AtAdbReadiness.kt	??	A	AG.V2-2	YES	NO	NO	NO	COMMIT	AT adapter
integration-at/src/main/java/com/androidagent/integration/at/AtPairingDiscovery.kt	??	A	AG.V2-2	YES	NO	NO	NO	COMMIT	AT adapter
integration-at/src/main/java/com/androidagent/integration/at/AtPairingResult.kt	??	A	AG.V2-2	YES	NO	NO	NO	COMMIT	AT adapter
integration-at/src/test/java/com/androidagent/integration/at/CliBootstrapLifecycleTest.kt	??	A	AG.V2-2	YES	NO	NO	NO	COMMIT	test (literal path strings)
provider-openai-compatible-jdk/src/test/kotlin/com/androidagent/provider/openai/jdk/LiveApiTest.kt	M	A	provider hardening	YES	NO	NO	NO	COMMIT	test (API_KEY=PRESENT/ABSENT)
provider-openai-compatible-jdk/src/test/kotlin/com/androidagent/provider/openai/jdk/ProviderProtocolTest.kt	M	A	provider hardening	YES	NO	NO	NO	COMMIT	test (synthetic sk-* asserts no leak)
provider-openai-compatible-jdk/src/test/kotlin/com/androidagent/provider/openai/jdk/VerticalSliceTest.kt	M	A	provider hardening	YES	NO	NO	NO	COMMIT	test
settings.gradle.kts	M	A	AG.V2-2 (AT V4 m2)	YES	NO	NO	NO	COMMIT	build input
tool-android-shell/src/main/java/com/androidagent/tool/androidshell/AndroidShellToolImpl.kt	M	A	AG.SHELL-1/AT V4	YES	NO	NO	NO	COMMIT	shell tool
tool-android-shell/src/main/java/com/androidagent/tool/androidshell/ShellConnection.kt	M	A	AG.SHELL-1/AT V4	YES	NO	NO	NO	COMMIT	shell connection
tool-android-shell/src/test/java/com/androidagent/tool/androidshell/AndroidShellToolImplTest.kt	M	A	AG.SHELL-1	YES	NO	NO	NO	COMMIT	test
AGENTS.md	M	C	this round (M2)	YES	NO	NO	NO	COMMIT	current-state doc sync
docs/architecture/AG.TOPIC-SESSION-ARCHITECTURE-FREEZE-0.md	??	C	Phase A	YES	NO	NO	NO	COMMIT	architecture SSOT
docs/architecture/AG.TOPIC-SESSION-ARCHITECTURE-FREEZE-0_保存1.md	??	C	Phase A	YES	NO	NO	NO	COMMIT	architecture SSOT variant
docs/architecture/AG.TOPIC-SESSION-DURABILITY-SEAM-PREFLIGHT-0.md	??	C	Phase A	YES	NO	NO	NO	COMMIT	architecture SSOT
docs/architecture/AG.TOPIC-SESSION-DURABILITY-SEAM-PREFLIGHT-0——保存1.md	??	C	Phase A	YES	NO	NO	NO	COMMIT	architecture SSOT variant
docs/audit/AG.AT-V4-Handoff-Audit.md	??	C	AT V4	YES	NO	NO	NO	COMMIT	audit doc
docs/audit/AG.SESSION-PERSISTENCE-RESEARCH-0.md	??	C	Phase A	YES	NO	NO	NO	COMMIT	research doc
docs/freeze/AG.POST-I-ARCHITECTURE-FREEZE-V1.md	??	C	POST-I	YES	NO	NO	NO	COMMIT	freeze doc (identity upgraded)
docs/handoff/AG_POST_I_CLOSURE_HANDOFF.md	??	C	POST-I	YES	NO	NO	NO	COMMIT	handoff doc
docs/implementation/AG.AT-V4-CLI-BOOTSTRAP-LIFECYCLE-FIX.md	??	C	AT V4	YES	NO	NO	NO	COMMIT	implementation report
docs/implementation/AG.AT-V4-Integration-Plan.md	??	C	AT V4	YES	NO	NO	NO	COMMIT	implementation doc
docs/implementation/AG.AT-V4-MODEL-CAPABILITY-UPDATE-0.md	??	C	AT V4	YES	NO	NO	NO	COMMIT	implementation report
docs/implementation/AG.AT-V4-PTY-PERSISTENCE-TEST-0.md	??	C	AT V4	YES	NO	NO	NO	COMMIT	implementation report
docs/implementation/AG.DEBUG-LLM-ADB-DIAGNOSIS-0.md	??	C	debug	YES	NO	NO	NO	COMMIT	implementation report
docs/implementation/AG.FRESH-INSTALL-LLM-PAIRING-E2E-0.md	??	C	fresh-install E2E	YES	NO	NO	NO	COMMIT	implementation report
docs/implementation/AG.TOPIC-SESSION-PERSISTENCE-IMPLEMENTATION-0_FINAL.md	??	C	Phase A-I	YES	NO	NO	NO	COMMIT	implementation SSOT
docs/implementation/AG.TOPIC-SESSION-PERSISTENCE-IMPLEMENTATION-0_FINAL_保存1.md	??	C	Phase A-I	YES	NO	NO	NO	COMMIT	SSOT variant
docs/implementation/AG.TOPIC-SESSION-PERSISTENCE-IMPLEMENTATION-0_保存1.md	??	C	Phase A-I	YES	NO	NO	NO	COMMIT	SSOT variant
docs/evidence/ag_topic_session_device_i/ (10 files)	??	B	DEVICE-I	YES	NO	NO	NO	COMMIT	device verification evidence
docs/evidence/ag_post_i_closure/ (10 files)	??	B	POST-I	YES	NO	NO	NO	COMMIT	closure audit evidence
tools/test-fixtures/db_inspect.py	??	D	DEVICE-I	YES	NO	NO	NO	COMMIT	read-only DB pull helper
tools/test-fixtures/debug_client.py	??	D	DEVICE-I	YES	NO	NO	NO	COMMIT	debug bridge client (127.0.0.1)
tools/test-fixtures/echo_fixture.py	??	D	DEVICE-I	YES	NO	NO	NO	COMMIT	loopback echo fixture (non-secret)
tools/test-fixtures/ui_helper.py	??	D	DEVICE-I	YES	NO	NO	NO	COMMIT	adb UI helper
provider.local.fixture.json	??	D	DEVICE-I	YES	NO	NO	NO	COMMIT	loopback fixture config (portable)
third_party/at-v4-consumer-freeze-v1/ (15 files)	??	D	AG.V2-2 (AT V4)	YES	NO	NO	NO	COMMIT	vendored AT V4 m2 build input
upload/AG.V2-2.AT-INTEGRATION.md	D	C	prior snapshot	YES	NO	NO	NO	COMMIT	old snapshot superseded
upload/AGENTS.md	D	C	prior snapshot	YES	NO	NO	NO	COMMIT	old snapshot superseded
upload/AT_TO_AG_HANDOFF_CURRENT.md	D	C	prior snapshot	YES	NO	NO	NO	COMMIT	old snapshot superseded
upload/AndroidAgent_Master_Roadmap.md	D	C	prior snapshot	YES	NO	NO	NO	COMMIT	old snapshot superseded
upload/AG.POST-I-ARCHITECTURE-FREEZE-V1.md	??	C	POST-I	YES	NO	NO	NO	COMMIT	current snapshot
upload/AG.TOPIC-SESSION-PERSISTENCE-IMPLEMENTATION-0_FINAL.md	??	C	POST-I	YES	NO	NO	NO	COMMIT	current snapshot
upload/AG_POST_I_CLOSURE_EVIDENCE_CONSOLIDATED.md	??	C/B	POST-I	YES	NO	NO	NO	COMMIT	current snapshot (consolidated evidence)
upload/AG_POST_I_CLOSURE_HANDOFF.md	??	C	POST-I	YES	NO	NO	NO	COMMIT	current snapshot

---


<!-- ===== FILE: 02_staged_manifest.txt ===== -->

STAGED MANIFEST (AG.POST-I-GIT-NORMALIZATION-CORE-FREEZE-1)
STAGED_FILE_COUNT = 134
ADDED = 93
MODIFIED = 37
DELETED = 4
RENAMED = 0

=== ADDED (93) ===

=== MODIFIED (37) ===

=== DELETED (4) ===

STAGED_BUILD_ARTIFACTS = 0
STAGED_SECRET_OR_MACHINE_LOCAL = 0
STAGED_PATHS (full, sorted) below

---


<!-- ===== FILE: 03_secret_privacy_audit.md ===== -->

# 03 — Secret / Privacy Audit (AG.POST-I-GIT-NORMALIZATION-CORE-FREEZE-1)

Scope: all freeze-eligible candidate text files (untracked + modified tracked, excluding binary AAR/POM).
SCANNED_FILE_COUNT = 114

## Method
Regex scan for credential patterns (api_key/apikey/token/secret/password/authorization/bearer/sk-/
OPENAI_API_KEY/OPENROUTER_API_KEY/x-api-key/private-key markers) + privacy patterns (device serial,
model, private IPs 10./192.168./172.16-31.), then manual adjudication of every hit.

## SECRET pattern hits — ALL adjudicated (0 real credentials)

| File:line | Match | Adjudication |
|---|---|---|
| docs/evidence/ag_topic_session_device_i/07_logcat.txt:194,212,217 | `token=android.os.BinderProxy@...` | FALSE POSITIVE — Android window/binder token, not a credential |
| docs/implementation/AG.FRESH-INSTALL-LLM-PAIRING-E2E-0.md:52,216 | `PROVIDER_SECRET_LEAKED = NO` | DOCUMENTATION TEXT — asserts no leak |
| docs/implementation/AG.TOPIC-SESSION-...-0_FINAL.md:1789,1851,1886,1889 | "non-secret apiKey", "task-allowed", "non-secret diagnostic" | DOCUMENTATION TEXT |
| provider.local.fixture.json:5 | `"apiKey": "fixture-key"` | SAFE TEST FIXTURE — non-secret loopback value |
| provider-openai-compatible-jdk/.../LiveApiTest.kt:67 | `API_KEY = PRESENT/ABSENT` | SAFE DIAGNOSTIC — never prints the key |
| provider-openai-compatible-jdk/.../ProviderProtocolTest.kt:429,433,440-451 | `sk-super-secret-value-9999`, `sk-leak-me` | SYNTHETIC TEST VALUE — asserts the key is NEVER echoed (assertFalse on leak) |
| tools/test-fixtures/echo_fixture.py:16,18,19,103 | "Never logs the Authorization header / API key", "NON-SECRET" | DOCUMENTATION TEXT — fixture explicitly avoids secrets |
| upload/AG.TOPIC-SESSION-...-0_FINAL.md:1789,1851,1886,1889 | same as SSOT | DOCUMENTATION TEXT (snapshot copy) |

REAL_SECRET_FOUND = 0
REAL_CREDENTIAL_FOUND = NO

## DEVICE / PRIVACY pattern hits — adjudicated

- Device serial `APLMUT4312005935`, model `ALI-AN00`, build fingerprint, private IPs `192.168.137.x`
  appear in: pre-existing historical implementation docs (AT-V4-MODEL-CAPABILITY-UPDATE-0,
  AT-V4-PTY-PERSISTENCE-TEST-0, DEBUG-LLM-ADB-DIAGNOSIS-0, FRESH-INSTALL-LLM-PAIRING-E2E-0),
  the DEVICE-I evidence dir, and the SSOT §20 / freeze doc.
- These are REAL internal-device verification identifiers, NOT credentials.
- `127.0.0.1` (loopback) in fixtures/tests = local-only, non-identifying.
- "serial/serialization/serially" hits (MessageSerializer, serial tool calls) = DOMAIN NOISE, false positive.

DEVICE_IDENTIFIER_POLICY = PRIVATE_REPO_RETAINED
Rationale: repo has NO remote (local-only); identifiers are internal real-device evidence, not
credentials; redacting would rewrite point-in-time historical docs/evidence (unnecessary churn).
If a public repo / export is later created, run a separate public-repo privacy normalization
(redact exact serial to a stable label).

## provider.local.fixture.json — decision

PORTABLE = YES (baseUrl http://127.0.0.1:18080/v1, apiKey "fixture-key", model "fixture-stream").
No personal endpoint, no token, no absolute path, no secret.
PROVIDER_LOCAL_FIXTURE_DECISION = COMMIT (repo-portable test fixture)

## Build input

third_party/at-v4-consumer-freeze-v1/ = vendored AT V4 m2 (AAR/POM/MANIFEST), 1.67 MB.
Verified: NO cache/temp/lock/resolver-status/junk inside (clean m2 layout only).
AT_V4_VENDORED_BUILD_INPUT = COMMIT
CLEAN_CLONE_BUILD_INPUT_COMPLETE = YES

## Verdict

SECRET_SCAN = PASS
PRIVACY_AUDIT = PASS
REAL_CREDENTIAL_FOUND = NO
MACHINE_LOCAL_FILES_STAGED = 0
BUILD_ARTIFACTS_STAGED = 0

---


<!-- ===== FILE: 04_physical_isolation_guard.md ===== -->

PHYSICAL ISOLATION GUARD (pre-freeze re-check)
ag-core:
  android/androidx imports = 0 + 0
  Room CODE refs (non-comment) = 0
  app/tool/provider/runtime imports = 0
  AT imports = 1
ag-runtime:
  android/androidx imports = 0 + 0
  Room CODE refs (non-comment) = 2
  app/tool/provider imports = 0
  AT imports = 0
Gradle forbidden edges (from 02, re-confirmed via settings/gradle unchanged): ag-core->(runtime/app/tool/provider/AT)=NONE; ag-runtime->(app/Room/tool/provider/AT)=NONE
PHYSICAL_ISOLATION_GUARD = PASS

---


<!-- ===== FILE: 05_regression_results.md ===== -->

FULL MODULE REGRESSION (pre-freeze, --rerun-tasks fresh run)
Tasks: :ag-core:test :ag-runtime:test :provider-openai-compatible:test :provider-openai-compatible-jdk:test
  :tool-android-shell:testDebugUnitTest :tool-network:testDebugUnitTest :tool-file:testDebugUnitTest
  :integration-at:testDebugUnitTest :app:testDebugUnitTest :app:assembleDebug
Pre-staging regression  = BUILD SUCCESSFUL (2m58s); assembleDebug = PASS
FINAL pre-commit regression (staged candidate) = BUILD SUCCESSFUL (2m35s); assembleDebug = PASS

ag-core                          tests=52   failures=0 errors=0
ag-runtime                       tests=88   failures=0 errors=0
app                              tests=171  failures=0 errors=0
integration-at                   tests=8    failures=0 errors=0
provider-openai-compatible       tests=59   failures=0 errors=0
provider-openai-compatible-jdk   tests=26   failures=0 errors=0
tool-android-shell               tests=20   failures=0 errors=0
tool-file                        tests=55   failures=0 errors=0
tool-network                     tests=78   failures=0 errors=0

GRAND TOTAL tests=557 failures=0

I11 FLAKY OBSERVED THIS RUN = NO (full matrix green)
M3_STATUS = KNOWN_NON_BLOCKING_DEBT (not reproduced this run)

---


<!-- ===== FILE: 06_freeze_candidate_matrix.md ===== -->

# 06 — Freeze Candidate Matrix (AG.POST-I-GIT-NORMALIZATION-CORE-FREEZE-1)

```text
ALL_DIRTY_FILES_CLASSIFIED = YES            (85 porcelain lines / 99 TSV records, all accounted)
ALL_TRACKED_MODIFICATIONS_ACCOUNTED_FOR = YES (37 modified + 4 deleted, each mapped to Phase A-I / AG.V2-2 / this round)
UNKNOWN_FILES = 0
SECRETS = 0                                  (REAL_CREDENTIAL_FOUND = NO)
MACHINE_LOCAL_FILES_STAGED = 0
BUILD_ARTIFACTS_STAGED = 0

PRODUCTION_SEMANTICS_CHANGED_THIS_ROUND = NO
ARCHITECTURE_CHANGED_THIS_ROUND = NO
DATABASE_CHANGED_THIS_ROUND = NO
UI_CHANGED_THIS_ROUND = NO
  (this round: docs-only normalization + explicit staging + commit/tag; no production/test/Gradle
   semantic change; M1/M3 not touched)

M1_HOST_AT_IMPORT = DEFERRED NON-BLOCKING     (3 Host files, preserved, not modified)
M2_AGENTS_DOC_SYNC = CLOSED                   (AGENTS.md current-state TopicSession FROZEN line added)
M3_I11_FLAKE = KNOWN_NON_BLOCKING_DEBT        (not reproduced in either full regression)
M4_DIRTY_WORKTREE = CLOSED                    (this freeze commit; worktree clean after commit)

PHYSICAL_ISOLATION_GUARD = PASS
  (ag-core: android/androidx=0, Room code=0, app/tool/provider/runtime=0, AT code=0 [1 test-assertion string]
   ag-runtime: android/androidx=0, Room code=0 [comments only], app/tool/provider=0, AT=0;
   Gradle forbidden edges = NONE)
CORE_RUNTIME_BOUNDARY = CLEAN
TOOL_DEPENDENCY_INVERSION = PASS
MODEL_PROVIDER_DEPENDENCY_INVERSION = PASS
PERSISTENCE_PHYSICAL_ISOLATION = PASS

TOPIC_SESSION_FOUNDATION = FROZEN

FULL_MODULE_REGRESSION = PASS
  ag-core 52 / ag-runtime 88 / provider 59 / provider-jdk 26 / tool-shell 20 / tool-network 78 /
  tool-file 55 / integration-at 8 / app 171 = 557 tests, 0 failures (JUnit XML actuals, 2 fresh runs)
ASSEMBLE_DEBUG = PASS

STAGED_DIFF_CHECK = PASS
  (git diff --cached --check: only pre-existing doc whitespace warnings; no credentials, no build
   outputs, no accidental format churn, no unrelated work, no accidental deletion)
UNSTAGED_PROJECT_CHANGES = 0                  (worktree matches freeze candidate)

STAGED_FILE_COUNT = 135                        (93 ADDED + 37 MODIFIED + 4 DELETED + 1 manifest re-stage
                                               net = 135 entries; 0 RENAMED)
```

PRE_UI_FREEZE_READY = YES

---


<!-- ===== FILE: 01_dirty_file_classification.md ===== -->

# 01 — Dirty File Classification (AG.POST-I-GIT-NORMALIZATION-CORE-FREEZE-1)

Columns: PATH | STATUS | CATEGORY | ORIGIN_PHASE | INTENDED_FOR_FREEZE | SENSITIVE | MACHINE_LOCAL | GENERATED | DECISION | RATIONALE

Categories: A=Phase A-I production/test/Gradle intended work; B=DEVICE-I/Closure evidence;
C=architecture/freeze/handoff/SSOT docs; D=reproducible test infra/fixture;
E=machine-local/secret/private config; F=generated/build/cache; G=unrelated/unknown.
Freeze-eligible: A/B/C/D only. E/F/G excluded. UNKNOWN(G) count must be 0.

## A — Phase A-I production/test/Gradle (37 modified tracked + 19 untracked source)

MODIFIED TRACKED (37):
ag-core/build.gradle.kts | M | A | Phase D (kotlinx-serialization-json) | YES | NO | NO | NO | COMMIT | build input, Phase D
ag-core/src/main/kotlin/com/androidagent/core/AgentCore.kt | M | A | Phase A/B/C/F | YES | NO | NO | NO | COMMIT | Core loop + durability seam
ag-core/src/main/kotlin/com/androidagent/core/RunOutcome.kt | M | A | Phase A/B | YES | NO | NO | NO | COMMIT | Run outcome domain
ag-core/src/main/kotlin/com/androidagent/core/Session.kt | M | A | Phase C/F | YES | NO | NO | NO | COMMIT | Session domain + restore
ag-core/src/test/kotlin/com/androidagent/core/AgentLoopTest.kt | M | A | Phase A/B | YES | NO | NO | NO | COMMIT | test
ag-core/src/test/kotlin/com/androidagent/core/EventSinkContractTest.kt | M | A | Phase A/B | YES | NO | NO | NO | COMMIT | test
ag-core/src/test/kotlin/com/androidagent/core/SessionConcurrencyTest.kt | M | A | Phase C | YES | NO | NO | NO | COMMIT | test
ag-core/src/test/kotlin/com/androidagent/core/ToolRegistryTest.kt | M | A | Phase A | YES | NO | NO | NO | COMMIT | test
ag-runtime/build.gradle.kts | M | A | Phase D/F (serialization) | YES | NO | NO | NO | COMMIT | build input
ag-runtime/src/main/kotlin/com/androidagent/runtime/AgentRuntime.kt | M | A | Phase A-I (lifecycle/restore/currentHistory) | YES | NO | NO | NO | COMMIT | Runtime orchestration
ag-runtime/src/test/kotlin/com/androidagent/runtime/AgentRuntimeInterruptTest.kt | M | A | RUN-CONTROL-1 | YES | NO | NO | NO | COMMIT | test
ag-runtime/src/test/kotlin/com/androidagent/runtime/AgentRuntimeTest.kt | M | A | Phase A-I | YES | NO | NO | NO | COMMIT | test
ag-runtime/src/test/kotlin/com/androidagent/runtime/TestSupport.kt | M | A | Phase A-I | YES | NO | NO | NO | COMMIT | test support
app/build.gradle.kts | M | A | Phase E/F (Room) | YES | NO | NO | NO | COMMIT | build input
app/src/main/java/com/androidagent/app/AgentInstructions.kt | M | A | AGENT-0.PREFLIGHT | YES | NO | NO | NO | COMMIT | host instructions
app/src/main/java/com/androidagent/app/ChatActivity.kt | M | A | Phase I / observability | YES | NO | NO | NO | COMMIT | host UI
app/src/main/java/com/androidagent/app/ChatCompositionRoot.kt | M | A | AG.V2-2 / Phase F | YES | NO | NO | NO | COMMIT | Composition Root
app/src/main/java/com/androidagent/app/SessionHolder.kt | M | A | Phase G/I | YES | NO | NO | NO | COMMIT | process-scoped holder
app/src/main/java/com/androidagent/app/TranscriptModel.kt | M | A | Phase I (reproject) | YES | NO | NO | NO | COMMIT | presentation projection
app/src/main/java/com/androidagent/app/debug/DebugBridge.kt | M | A | AG.DEBUG-0 / HOST-STOP | YES | NO | NO | NO | COMMIT | debug bridge
app/src/main/java/com/androidagent/app/debug/DebugProtocol.kt | M | A | AG.DEBUG-0 | YES | NO | NO | NO | COMMIT | debug protocol
app/src/main/java/com/androidagent/app/trace/AgentTraceCollector.kt | M | A | AGENT-0.PREFLIGHT | YES | NO | NO | NO | COMMIT | trace collector
app/src/test/java/com/androidagent/app/AgentPreflightTest.kt | M | A | AGENT-0.PREFLIGHT / AG.V2-2 | YES | NO | NO | NO | COMMIT | test
app/src/test/java/com/androidagent/app/HostStopStateTest.kt | M | A | HOST-STOP-1 | YES | NO | NO | NO | COMMIT | test
app/src/test/java/com/androidagent/app/TranscriptModelTest.kt | M | A | OBSERVABILITY-1 / Phase I | YES | NO | NO | NO | COMMIT | test
build.gradle.kts | M | A | AG.V2-2 / AT V4 | YES | NO | NO | NO | COMMIT | root build (AT m2 repo, versions)
docs/integration/AT_TO_AG_HANDOFF_CURRENT.md | M | A/C | AG.V2-2.AT-INTEGRATION | YES | NO | NO | NO | COMMIT | integration canonical pointer
integration-at/build.gradle.kts | M | A | AG.V2-2 (AT V4) | YES | NO | NO | NO | COMMIT | build input
integration-at/src/main/java/com/androidagent/integration/at/AtExecutionAdapter.kt | M | A | AG.V2-2 (AT V4) | YES | NO | NO | NO | COMMIT | AT adapter
provider-openai-compatible-jdk/src/test/kotlin/com/androidagent/provider/openai/jdk/LiveApiTest.kt | M | A | provider hardening | YES | NO | NO | NO | COMMIT | test (API_KEY=PRESENT/ABSENT safe)
provider-openai-compatible-jdk/src/test/kotlin/com/androidagent/provider/openai/jdk/ProviderProtocolTest.kt | M | A | provider hardening | YES | NO | NO | NO | COMMIT | test (synthetic sk-* values, asserts no leak)
provider-openai-compatible-jdk/src/test/kotlin/com/androidagent/provider/openai/jdk/VerticalSliceTest.kt | M | A | provider hardening | YES | NO | NO | NO | COMMIT | test
settings.gradle.kts | M | A | AG.V2-2 (AT V4 m2 repo) | YES | NO | NO | NO | COMMIT | build input
tool-android-shell/src/main/java/com/androidagent/tool/androidshell/AndroidShellToolImpl.kt | M | A | AG.SHELL-1 / AT V4 | YES | NO | NO | NO | COMMIT | shell tool
tool-android-shell/src/main/java/com/androidagent/tool/androidshell/ShellConnection.kt | M | A | AG.SHELL-1 / AT V4 | YES | NO | NO | NO | COMMIT | shell connection
tool-android-shell/src/test/java/com/androidagent/tool/androidshell/AndroidShellToolImplTest.kt | M | A | AG.SHELL-1 | YES | NO | NO | NO | COMMIT | test

UNTRACKED SOURCE (19):
ag-core/src/main/kotlin/com/androidagent/core/MessageDurability.kt | ?? | A | Phase C/F (durability contract) | YES | NO | NO | NO | COMMIT | core contract
ag-core/src/test/kotlin/com/androidagent/core/InterruptCloseoutTest.kt | ?? | A | RUN-CONTROL | YES | NO | NO | NO | COMMIT | test
ag-runtime/src/main/kotlin/com/androidagent/runtime/persistence/MessageSerializer.kt | ?? | A | Phase D | YES | NO | NO | NO | COMMIT | storage-neutral serializer
ag-runtime/src/main/kotlin/com/androidagent/runtime/persistence/Records.kt | ?? | A | Phase C/D | YES | NO | NO | NO | COMMIT | storage-neutral records
ag-runtime/src/main/kotlin/com/androidagent/runtime/persistence/TopicSessionStore.kt | ?? | A | Phase C | YES | NO | NO | NO | COMMIT | store contract
ag-runtime/src/test/kotlin/com/androidagent/runtime/RuntimeSessionCreationTest.kt | ?? | A | Phase G | YES | NO | NO | NO | COMMIT | test
ag-runtime/src/test/kotlin/com/androidagent/runtime/StartupRestoreTest.kt | ?? | A | Phase F/G (restore) | YES | NO | NO | NO | COMMIT | test
ag-runtime/src/test/kotlin/com/androidagent/runtime/persistence/InMemoryTopicSessionStore.kt | ?? | A | Phase C | YES | NO | NO | NO | COMMIT | test store
ag-runtime/src/test/kotlin/com/androidagent/runtime/persistence/MessageSerializerTest.kt | ?? | A | Phase D | YES | NO | NO | NO | COMMIT | test
ag-runtime/src/test/kotlin/com/androidagent/runtime/persistence/TopicSessionStoreContractTest.kt | ?? | A | Phase C | YES | NO | NO | NO | COMMIT | test
app/src/main/java/com/androidagent/app/CompositionAdbSemantic.kt | ?? | A | AG.V2-2 (AT ADB UX) | YES | NO | NO | NO | COMMIT | host ADB semantic (M1: com.at.composition import, DEFERRED)
app/src/main/java/com/androidagent/app/persistence/Daos.kt | ?? | A | Phase E | YES | NO | NO | NO | COMMIT | Room DAOs
app/src/main/java/com/androidagent/app/persistence/Entities.kt | ?? | A | Phase E | YES | NO | NO | NO | COMMIT | Room entities
app/src/main/java/com/androidagent/app/persistence/RoomTopicSessionStore.kt | ?? | A | Phase E | YES | NO | NO | NO | COMMIT | Room impl
app/src/main/java/com/androidagent/app/persistence/StoreMessageDurability.kt | ?? | A | Phase F | YES | NO | NO | NO | COMMIT | durability adapter
app/src/main/java/com/androidagent/app/persistence/TopicSessionDatabase.kt | ?? | A | Phase E | YES | NO | NO | NO | COMMIT | Room DB (version 1)
app/src/test/java/com/androidagent/app/PhaseITranscriptProjectionTest.kt | ?? | A | Phase I | YES | NO | NO | NO | COMMIT | test
app/src/test/java/com/androidagent/app/persistence/PhaseGRoomIntegrationTest.kt | ?? | A | Phase G | YES | NO | NO | NO | COMMIT | test
app/src/test/java/com/androidagent/app/persistence/RoomTopicSessionStoreTest.kt | ?? | A | Phase E | YES | NO | NO | NO | COMMIT | test
app/src/test/java/com/androidagent/app/persistence/StoreMessageDurabilityTest.kt | ?? | A | Phase F | YES | NO | NO | NO | COMMIT | test
integration-at/src/main/java/com/androidagent/integration/at/AdbSemantic.kt | ?? | A | AG.V2-2 | YES | NO | NO | NO | COMMIT | AT adapter type
integration-at/src/main/java/com/androidagent/integration/at/AtAdbBootstrap.kt | ?? | A | AG.V2-2 | YES | NO | NO | NO | COMMIT | AT adapter
integration-at/src/main/java/com/androidagent/integration/at/AtAdbReadiness.kt | ?? | A | AG.V2-2 | YES | NO | NO | NO | COMMIT | AT adapter
integration-at/src/main/java/com/androidagent/integration/at/AtPairingDiscovery.kt | ?? | A | AG.V2-2 | YES | NO | NO | NO | COMMIT | AT adapter
integration-at/src/main/java/com/androidagent/integration/at/AtPairingResult.kt | ?? | A | AG.V2-2 | YES | NO | NO | NO | COMMIT | AT adapter
integration-at/src/test/java/com/androidagent/integration/at/CliBootstrapLifecycleTest.kt | ?? | A | AG.V2-2 | YES | NO | NO | NO | COMMIT | test (uses AGA pkg name as literal path strings)

## B — DEVICE-I / Closure evidence

docs/evidence/ag_topic_session_device_i/* (10 files) | ?? | B | DEVICE-I | YES | NO (no real credential; device serial retained per PRIVATE_REPO_RETAINED) | NO | NO | COMMIT | device verification evidence
docs/evidence/ag_post_i_closure/* (10 files incl. ALL_EVIDENCE_CONSOLIDATED.md) | ?? | B | POST-I Closure | YES | NO | NO | NO | COMMIT | closure audit evidence

## C — architecture / freeze / handoff / SSOT docs

docs/architecture/AG.TOPIC-SESSION-ARCHITECTURE-FREEZE-0.md | ?? | C | Phase A (arch SSOT) | YES | NO | NO | NO | COMMIT | architecture SSOT
docs/architecture/AG.TOPIC-SESSION-ARCHITECTURE-FREEZE-0_保存1.md | ?? | C | Phase A (arch SSOT, 保存1 variant) | YES | NO | NO | NO | COMMIT | architecture SSOT variant
docs/architecture/AG.TOPIC-SESSION-DURABILITY-SEAM-PREFLIGHT-0.md | ?? | C | Phase A (durable seam SSOT) | YES | NO | NO | NO | COMMIT | architecture SSOT
docs/architecture/AG.TOPIC-SESSION-DURABILITY-SEAM-PREFLIGHT-0——保存1.md | ?? | C | Phase A (durable seam SSOT, 保存1 variant) | YES | NO | NO | NO | COMMIT | architecture SSOT variant
docs/audit/AG.AT-V4-Handoff-Audit.md | ?? | C | AT V4 | YES | NO | NO | NO | COMMIT | audit doc
docs/audit/AG.SESSION-PERSISTENCE-RESEARCH-0.md | ?? | C | Phase A (research) | YES | NO | NO | NO | COMMIT | research doc
docs/freeze/AG.POST-I-ARCHITECTURE-FREEZE-V1.md | ?? | C | POST-I (this round, identity upgraded) | YES | NO | NO | NO | COMMIT | freeze doc
docs/handoff/AG_POST_I_CLOSURE_HANDOFF.md | ?? | C | POST-I | YES | NO | NO | NO | COMMIT | handoff doc
docs/implementation/AG.AT-V4-CLI-BOOTSTRAP-LIFECYCLE-FIX.md | ?? | C | AT V4 | YES | NO | NO | NO | COMMIT | implementation report
docs/implementation/AG.AT-V4-Integration-Plan.md | ?? | C | AT V4 | YES | NO | NO | NO | COMMIT | implementation doc
docs/implementation/AG.AT-V4-MODEL-CAPABILITY-UPDATE-0.md | ?? | C | AT V4 | YES | NO | NO | NO | COMMIT | implementation report
docs/implementation/AG.AT-V4-PTY-PERSISTENCE-TEST-0.md | ?? | C | AT V4 | YES | NO | NO | NO | COMMIT | implementation report
docs/implementation/AG.DEBUG-LLM-ADB-DIAGNOSIS-0.md | ?? | C | debug | YES | NO | NO | NO | COMMIT | implementation report
docs/implementation/AG.FRESH-INSTALL-LLM-PAIRING-E2E-0.md | ?? | C | fresh-install E2E | YES | NO | NO | NO | COMMIT | implementation report
docs/implementation/AG.TOPIC-SESSION-PERSISTENCE-IMPLEMENTATION-0_FINAL.md | ?? | C | Phase A-I (SSOT) | YES | NO | NO | NO | COMMIT | implementation SSOT
docs/implementation/AG.TOPIC-SESSION-PERSISTENCE-IMPLEMENTATION-0_FINAL_保存1.md | ?? | C | Phase A-I (SSOT, 保存1 variant) | YES | NO | NO | NO | COMMIT | SSOT variant
docs/implementation/AG.TOPIC-SESSION-PERSISTENCE-IMPLEMENTATION-0_保存1.md | ?? | C | Phase A-I (SSOT, 保存1 variant) | YES | NO | NO | NO | COMMIT | SSOT variant

## D — reproducible test infrastructure / fixture / build input

tools/test-fixtures/db_inspect.py | ?? | D | DEVICE-I | YES | NO | NO | NO | COMMIT | read-only DB pull helper
tools/test-fixtures/debug_client.py | ?? | D | DEVICE-I | YES | NO | NO | NO | COMMIT | debug bridge client (127.0.0.1)
tools/test-fixtures/echo_fixture.py | ?? | D | DEVICE-I | YES | NO | NO | NO | COMMIT | loopback echo fixture (non-secret)
tools/test-fixtures/ui_helper.py | ?? | D | DEVICE-I | YES | NO | NO | NO | COMMIT | adb UI helper
provider.local.fixture.json | ?? | D | DEVICE-I | YES | NO (fixture-key, non-secret; 127.0.0.1) | NO | NO | COMMIT | loopback fixture provider config (repo-portable)
third_party/at-v4-consumer-freeze-v1/ (15 files: MANIFEST + 14 AAR/POM) | ?? | D | AG.V2-2 (AT V4 build input) | YES | NO | NO | NO | COMMIT | vendored AT V4 m2 build input (required for clean-clone reproducible build; AT_V4_VENDORED_BUILD_INPUT=COMMITTED)

## upload/ — rolling current-doc snapshot area (tracked, not gitignored)

DELETED (4, accept deletion):
upload/AG.V2-2.AT-INTEGRATION.md | D | C | prior snapshot | YES | NO | NO | NO | COMMIT | old snapshot superseded by current POST-I snapshot
upload/AGENTS.md | D | C | prior snapshot | YES | NO | NO | NO | COMMIT | old snapshot superseded
upload/AT_TO_AG_HANDOFF_CURRENT.md | D | C | prior snapshot | YES | NO | NO | NO | COMMIT | old snapshot superseded
upload/AndroidAgent_Master_Roadmap.md | D | C | prior snapshot | YES | NO | NO | NO | COMMIT | old snapshot superseded
UNTRACKED (4, current snapshot):
upload/AG.POST-I-ARCHITECTURE-FREEZE-V1.md | ?? | C | POST-I | YES | NO | NO | NO | COMMIT | current snapshot
upload/AG.TOPIC-SESSION-PERSISTENCE-IMPLEMENTATION-0_FINAL.md | ?? | C | POST-I | YES | NO | NO | NO | COMMIT | current snapshot
upload/AG_POST_I_CLOSURE_EVIDENCE_CONSOLIDATED.md | ?? | C/B | POST-I | YES | NO | NO | NO | COMMIT | current snapshot (consolidated evidence)
upload/AG_POST_I_CLOSURE_HANDOFF.md | ?? | C | POST-I | YES | NO | NO | NO | COMMIT | current snapshot

## AGENTS.md — M2 doc sync (this round)

AGENTS.md | M | C | this round (M2 close) | YES | NO | NO | NO | COMMIT | current-state doc sync (TopicSession FROZEN line)

## E / F / G — EXCLUDED

E (machine-local/secret) staged = 0: none found (config/provider.local.json is gitignored and NOT in the dirty set).
F (build/cache) staged = 0: build/ .gradle/ *.apk local.properties all gitignored; not in dirty set.
G (unknown/unrelated) = 0: every dirty file is accounted for above.

## Summary

TOTAL_DIRTY (porcelain lines) = 85 (37 M + 4 D + 44 ?? collapsed; 88 untracked files when directories expanded)
ALL_DIRTY_FILES_CLASSIFIED = YES
UNKNOWN_FILES = 0
STAGEABLE (A/B/C/D/upload) = ALL
EXCLUDED (E/F/G) = 0

---
