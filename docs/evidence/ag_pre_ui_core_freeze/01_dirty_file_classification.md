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
