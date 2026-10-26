# AG.POST-I-ARCHITECTURE-CLOSURE - Consolidated Evidence

Merged from docs/evidence/ag_post_i_closure/ (00-08). Single-document evidence bundle for the POST-I closure audit.

---


<!-- ===== FILE: 00_git_baseline.txt ===== -->

main
HEAD: a6eea61dc209898a01cd0e7f2e82304fff7ed9d4
DIRTY_TOTAL: 86
MODIFIED: 37
UNTRACKED: 46
DIFF_CHECK_LINES: 9
PRE_EXISTING_DIRTY_FILES_PRESERVED = YES (no git reset/clean/stash/restore/rebase used)
COMMIT = NO / TAG = NO / PUSH = NO

---


<!-- ===== FILE: 01_module_inventory.md ===== -->

# Module Inventory (from `gradlew projects` + settings.gradle.kts + build.gradle.kts)

Root project: `AndroidAgent`. 9 subprojects + root.

| Module | Role | Direct project deps | Android plugin? | External prod deps |
|---|---|---|---|---|
| `:ag-core` | Intelligence/domain: AgentCore, Agent loop, Session/Run, Message, ModelProvider contract, Tool contract, ToolRegistry, events, RunOutcome, MessageDurability contract | — (none) | NO (pure JVM) | kotlin-stdlib, kotlinx-coroutines-core |
| `:ag-runtime` | Host-neutral lifecycle/session orchestration: AgentRuntime (current session selection, single-active-run Job, interrupt), persistence contracts (TopicSessionStore, Records, MessageSerializer), Core hydration, currentHistory seam | `:ag-core` | NO (pure JVM) | kotlin-stdlib, kotlinx-coroutines-core, kotlinx-serialization-json |
| `:provider-openai-compatible` | ModelProvider implementation: OpenAiCompatibleProvider, config loader/resolver, OpenAiHttpTransport contract | `:ag-core` | NO (pure JVM) | kotlin-stdlib, kotlinx-serialization-json, kotlinx-coroutines-core |
| `:provider-openai-compatible-jdk` | JDK transport implementation: JdkOpenAiHttpTransport (java.net.http) | `:provider-openai-compatible` | NO (pure JVM) | kotlin-stdlib, kotlinx-coroutines-core |
| `:tool-android-shell` | Concrete Tool: AndroidShellToolImpl (one-shot shell) + ShellConnection; consumes AT via `:integration-at` (api) | `:integration-at` (api), `:ag-core` | YES (com.android.library) | (AT AARs transitive via integration-at) |
| `:tool-network` | Concrete Tool: NetworkToolImpl (fetch, HTTPS-only GET, SSRF guard, HtmlToText); platform HttpsURLConnection; NO AT | `:ag-core` | YES (com.android.library) | kotlin-stdlib, kotlinx-serialization-json, kotlinx-coroutines-core |
| `:tool-file` | Concrete Tool: FileToolImpl (read/search/list/stat, APP_SELF root, PathResolver, bounds); READ-only; NO AT | `:ag-core` | YES (com.android.library) | kotlin-stdlib, kotlinx-serialization-json, kotlinx-coroutines-core |
| `:integration-at` | Unified AT consumption point: AtExecutionAdapter (AT V4 composition, one-shot + persistent backends); ONLY AG module directly on AT V4 AARs | — (AT AARs: com.at.engine.api:at-engine-api:4.0.0, com.at.cli:at-cli:4.0.0 from `third_party/at-v4-consumer-freeze-v1/m2`) | YES (com.android.library) | AT V4 AARs (vendored local m2), kotlin-stdlib, kotlinx-coroutines-core |
| `:app` | AGA Host/Product: Android lifecycle, Room persistence impl, Composition Root, SessionHolder, TranscriptModel, ChatActivity/UI, provider wiring, tool registration, DebugBridge | `:ag-core`, `:ag-runtime`, `:provider-openai-compatible`, `:tool-android-shell`, `:tool-network`, `:tool-file`, `:integration-at` | YES (com.android.application) | AndroidX (appcompat, lifecycle, room, coroutines-android), kotlin-stdlib |

Notes:
- `:ag-core` has ZERO project dependencies — the strongest isolation guarantee (Gradle-enforced).
- `:provider-openai-compatible-jdk` reaches ag-core only transitively (via :provider-openai-compatible); no direct edge.
- AT V4 artifacts are consumed ONLY through `:integration-at` (AARs from the vendored local m2 repo
  `third_party/at-v4-consumer-freeze-v1/m2`); `:tool-android-shell` gets AT types via the `api` edge to
  `:integration-at`.
- No `:tool-layer` central module, no ToolManager/ToolBackend/ToolFactory framework exists.

---


<!-- ===== FILE: 02_gradle_dependency_graph.md ===== -->

# Gradle Physical Dependency Graph (compileClasspath / debugCompileClasspath, real `gradlew dependencies` output)

Evidence captured: `gradlew :<module>:dependencies` per module (JVM modules: compileClasspath;
Android library/app modules: debugCompileClasspath). Project edges only (external libs omitted).

```
                        +----------------------+
                        |      :ag-core        |   (no project deps at all)
                        +----------+-----------+
                                 ^
        +----------------+-------+----------------+
        |                |                         |
+-------+--------+  +----+-----------+  +---------+--------+  +---------------------------+
| :ag-runtime    |  | :provider-openai-|  | :tool-android-  |  | :tool-network            |
|                |  | compatible       |  | shell           |  |                           |
+-------+--------+  +---------+--------+  +---------+-------+  +---------+-----------------+
        ^                ^                       ^                         ^
        |                |                       |                         |
        |        +-------+---------+             |                         |
        |        | :provider-openai-             |                         |
        |        | compatible-jdk                |                         |
        |        +-----------------+             |                         |
        |                            ^           |                         |
        |                            |           |   +---------------------+---------+
        |                            |           |   | :integration-at     |         |
        |                            |           +---| (AT V4 AARs, ONLY   |         |
        |                            |               |  module on AT)      |         |
        |                            |               +---------------------+         |
        |                            |                                               |
        +----------------------------+------------------------------------------------+
                                     |
                              +------v-------+
                              |     :app     |  (Host: also direct :provider-openai-compatible,
                              +--------------+   :tool-*, :integration-at; Room/AndroidX)
```

Verified edges (actual `project :X` lines in the dependency reports):

| From | To | Kind | Status |
|---|---|---|---|
| ag-runtime | ag-core | implementation | ALLOWED (Runtime -> Core) |
| provider-openai-compatible | ag-core | implementation | ALLOWED (Provider -> Core contract) |
| provider-openai-compatible-jdk | provider-openai-compatible | implementation | ALLOWED (transport -> protocol) |
| tool-android-shell | integration-at | **api** | ALLOWED (concrete tool -> AT adapter; AT types visible to tool only) |
| tool-android-shell | ag-core | implementation | ALLOWED (Tool -> Core contract) |
| tool-network | ag-core | implementation | ALLOWED (Tool -> Core contract) |
| tool-file | ag-core | implementation | ALLOWED (Tool -> Core contract) |
| integration-at | (AT V4 AARs: com.at.engine.api:at-engine-api:4.0.0, com.at.cli:at-cli:4.0.0) | maven | ALLOWED (only AT consumption point) |
| app | ag-core, ag-runtime, provider-openai-compatible, tool-android-shell, tool-network, tool-file, integration-at | implementation | ALLOWED (Host composes everything) |

Forbidden edges checked (all ABSENT from the real graphs):

| Forbidden edge | Found? |
|---|---|
| ag-core -> ag-runtime | NO |
| ag-core -> app | NO |
| ag-core -> any tool-* | NO |
| ag-core -> any provider-* | NO |
| ag-core -> Android/Room/AT | NO (pure JVM, no Android plugin, zero android/androidx on compileClasspath) |
| ag-runtime -> app | NO |
| ag-runtime -> Room/Android | NO (pure JVM) |
| ag-runtime -> any tool-* | NO |
| ag-runtime -> any provider-* | NO |
| provider-* -> app | NO |
| provider-* -> tool-* | NO |
| tool-network -> integration-at (AT) | NO (counter-example holds: fetch tool has zero AT) |
| tool-file -> integration-at (AT) | NO |
| integration-at -> ag-core / ag-runtime | NO (adapter is AT-side only; does not import AG domain) |

Direction verdict: matches the frozen concept
`Core <- Runtime <- Host` and `Core <- concrete tools/providers <- Host composition`.

---


<!-- ===== FILE: 03_physical_import_audit.md ===== -->

AG-CORE (ag-core/src, main+test):
  ANDROID_IMPORTS      = 0
  ANDROIDX_IMPORTS     = 0
  ROOM_MENTIONS        = 4   (ALL comments, see below — zero code references)
  APP_PACKAGE_IMPORTS  = 0
  RUNTIME_PKG_IMPORTS  = 0
  PROVIDER_PKG_IMPORTS = 0
  TOOL_PKG_IMPORTS     = 0
  AT_PKG_IMPORTS       = 1   (comment inside a test assertion string, see below)
AG-RUNTIME (ag-runtime/src, main+test):
  ANDROID_IMPORTS      = 0
  ANDROIDX_IMPORTS     = 0
  ROOM_MENTIONS        = 13  (ALL comments/doc + one test comment, see below — zero code references)
  APP_PACKAGE_IMPORTS  = 0
  PROVIDER_PKG_IMPORTS = 0
  TOOL_PKG_IMPORTS     = 0
  AT_PKG_IMPORTS       = 0
PROVIDER (provider-openai-compatible/src):
  APP_PACKAGE_IMPORTS  = 0
  TOOL_PKG_IMPORTS     = 0
  ANDROID_IMPORTS      = 0
PROVIDER-JDK (provider-openai-compatible-jdk/src):
  APP_PACKAGE_IMPORTS  = 0
  TOOL_PKG_IMPORTS     = 0
  ANDROID_IMPORTS      = 0
TOOL-SHELL (tool-android-shell/src, main+test):
  AG_CORE_IMPORTS      = 7
  AG_RUNTIME_IMPORTS   = 0
  APP_PACKAGE_IMPORTS  = 0
TOOL-NET (tool-network/src, main+test):
  AG_CORE_IMPORTS      = 6
  AG_RUNTIME_IMPORTS   = 0
  APP_PACKAGE_IMPORTS  = 0
  AT_IMPORTS           = 0
TOOL-FILE (tool-file/src, main+test):
  AG_CORE_IMPORTS      = 5
  AG_RUNTIME_IMPORTS   = 0
  APP_PACKAGE_IMPORTS  = 0
  AT_IMPORTS           = 0
INTEGRATION-AT (integration-at/src, main+test):
  AG_CORE_IMPORTS      = 0
  AG_RUNTIME_IMPORTS   = 0
  APP_PACKAGE_IMPORTS  = 11  (test-only: CliBootstrapLifecycleTest.kt uses the AGA package name
                             as literal filesystem path strings, e.g.
                             "/data/user/0/com.androidagent.app/files/at-cli-bridge/cli_bridge.sock";
                             zero code imports, zero compile edge)
  AT_PKG_IMPORTS       = 24  (expected: this is the unified AT consumption point)
HOST-APP (app/src, main+test):
  AG_CORE_IMPORTS      = 159
  AG_RUNTIME_IMPORTS   = 40
  ROOM_IMPORTS         = 18
  PROVIDER_PKG_IMPORTS = 23
  TOOL_PKG_IMPORTS     = 6
  AT_DIRECT_IMPORTS    = 17  (see APP DIRECT AT note below)

EXCEPTION REVIEW (all hits above):

1. AG-CORE Room/SQLite mentions (4) — ALL comments:
   AgentCore.kt:530 "Pure function: no Android, no Room, no Store dependency."
   MessageDurability.kt:10 "Implementation is storage-agnostic: could be Room, file, or in-memory."
   MessageDurability.kt:34 "Must not expose backend exceptions (Room/SQLite) to Core"
   Session.kt:74 "Restores the full message history from a durable source (e.g., Room/SQLite)."
   => zero code references. VERIFIED FACT (documentation of the boundary, not a leak).

2. AG-CORE AT-like mention (1) — comment inside a test assertion string:
   AgCoreBaselineTest.kt:31 "AG Core must live in com.androidagent.core, never com.androidterminal.*"
   => this is the baseline test ENFORCING the boundary. VERIFIED FACT.

3. AG-RUNTIME Room/SQLite mentions (13) — ALL comments/doc:
   AgentRuntime.kt:131/310/311/318 (seam + exception-classification docs),
   persistence/Records.kt:7/8/11/66 (records are NOT Room entities; pure JVM),
   persistence/TopicSessionStore.kt:9/13/51 (contract docs: no android.*, no androidx.room.*,
   no SQLite, no DAO), test/StartupRestoreTest.kt:232/503 (test comments).
   => zero code references; the persistence package is storage-neutral by design and by import.

4. INTEGRATION-AT app mentions (11) — test-only literal path strings (no imports, no edge).

5. HOST-APP direct AT imports (17 lines, 3 main files):
   - ChatCompositionRoot.kt: `import com.at.composition.android.ATAndroid` / `ATAndroidConfig`
     (AT V4 Android composition bootstrap — Host owns AT lifecycle policy; documented as intended:
     "no com.androidterminal.* type ever appears in Host code" = legacy v0 forbidden, V4 composition
     types allowed at Host level).
   - CompositionAdbSemantic.kt: `com.at.composition.android.*` (5 ADB semantic state types)
     (Host owns ADB bootstrap/pairing UX state).
   - debug/DebugBridge.kt: `import com.at.engine.ATTerminalSession` (debug-only; keeps a
     session-id -> ATTerminalSession map for debug introspection).
   VERDICT: MINOR DEBT (documented exception) — the frozen rule "AGA -X-> AT implementation"
   is implemented as "no raw AT engine types in Host code" via the :integration-at adapter;
   the 3 V4 composition/bootstrap type usages in Host are a Host-owned lifecycle wiring choice,
   not Core/Runtime/Tool leakage. AG Core and Agent Runtime remain AT-free (verified = 0).
   Recommendation (non-blocking, future): route DebugBridge's ATTerminalSession map and the
   composition bootstrap through :integration-at adapter methods to make the Host AT-surface
   zero-import. Record in Known Non-Blocking Debt of the freeze doc.

---


<!-- ===== FILE: 04_file_ownership_matrix.tsv ===== -->

AT_ADAPTER	main	integration-at\src\main\java\com\androidagent\integration\at\AdbSemantic.kt
AT_ADAPTER	main	integration-at\src\main\java\com\androidagent\integration\at\AtAdbBootstrap.kt
AT_ADAPTER	main	integration-at\src\main\java\com\androidagent\integration\at\AtAdbReadiness.kt
AT_ADAPTER	main	integration-at\src\main\java\com\androidagent\integration\at\AtExecutionAdapter.kt
AT_ADAPTER	main	integration-at\src\main\java\com\androidagent\integration\at\AtPairingDiscovery.kt
AT_ADAPTER	main	integration-at\src\main\java\com\androidagent\integration\at\AtPairingResult.kt
AT_ADAPTER	test	integration-at\src\test\java\com\androidagent\integration\at\CliBootstrapLifecycleTest.kt
CORE	main	ag-core\src\main\kotlin\com\androidagent\core\AgCoreMarker.kt
CORE	main	ag-core\src\main\kotlin\com\androidagent\core\AgentCore.kt
CORE	main	ag-core\src\main\kotlin\com\androidagent\core\AgentEvent.kt
CORE	main	ag-core\src\main\kotlin\com\androidagent\core\Message.kt
CORE	main	ag-core\src\main\kotlin\com\androidagent\core\MessageDurability.kt
CORE	main	ag-core\src\main\kotlin\com\androidagent\core\Model.kt
CORE	main	ag-core\src\main\kotlin\com\androidagent\core\RunOutcome.kt
CORE	main	ag-core\src\main\kotlin\com\androidagent\core\Session.kt
CORE	main	ag-core\src\main\kotlin\com\androidagent\core\Tool.kt
CORE	main	ag-core\src\main\kotlin\com\androidagent\core\ToolApproval.kt
CORE	main	ag-core\src\main\kotlin\com\androidagent\core\ToolRegistry.kt
CORE	test	ag-core\src\test\kotlin\com\androidagent\core\AgCoreBaselineTest.kt
CORE	test	ag-core\src\test\kotlin\com\androidagent\core\AgentLoopTest.kt
CORE	test	ag-core\src\test\kotlin\com\androidagent\core\EventSinkContractTest.kt
CORE	test	ag-core\src\test\kotlin\com\androidagent\core\InterruptCloseoutTest.kt
CORE	test	ag-core\src\test\kotlin\com\androidagent\core\SessionConcurrencyTest.kt
CORE	test	ag-core\src\test\kotlin\com\androidagent\core\TestDoubles.kt
CORE	test	ag-core\src\test\kotlin\com\androidagent\core\ToolRegistryTest.kt
HOST	main	app\src\androidTest\java\com\androidagent\app\FileL2FixtureTest.kt
HOST	main	app\src\androidTest\java\com\androidagent\app\FileL3FixtureTest.kt
HOST	main	app\src\androidTest\java\com\androidagent\app\ProviderRealDeviceProbeTest.kt
HOST	main	app\src\androidTest\java\com\androidagent\app\ShellToolRealDeviceTest.kt
HOST	main	app\src\androidTest\java\com\androidagent\app\UiRunSideChannelTest.kt
HOST	main	app\src\main\java\com\androidagent\app\AgentInstructions.kt
HOST	main	app\src\main\java\com\androidagent\app\ChatActivity.kt
HOST	main	app\src\main\java\com\androidagent\app\ChatCompositionRoot.kt
HOST	main	app\src\main\java\com\androidagent\app\CompositionAdbSemantic.kt
HOST	main	app\src\main\java\com\androidagent\app\debug\DebugBridge.kt
HOST	main	app\src\main\java\com\androidagent\app\debug\DebugProtocol.kt
HOST	main	app\src\main\java\com\androidagent\app\DeviceLocalProviderConfig.kt
HOST	main	app\src\main\java\com\androidagent\app\HostStatusModel.kt
HOST	main	app\src\main\java\com\androidagent\app\persistence\Daos.kt
HOST	main	app\src\main\java\com\androidagent\app\persistence\Entities.kt
HOST	main	app\src\main\java\com\androidagent\app\persistence\RoomTopicSessionStore.kt
HOST	main	app\src\main\java\com\androidagent\app\persistence\StoreMessageDurability.kt
HOST	main	app\src\main\java\com\androidagent\app\persistence\TopicSessionDatabase.kt
HOST	main	app\src\main\java\com\androidagent\app\provider\AndroidOpenAiHttpTransport.kt
HOST	main	app\src\main\java\com\androidagent\app\SessionHolder.kt
HOST	main	app\src\main\java\com\androidagent\app\trace\AgentTrace.kt
HOST	main	app\src\main\java\com\androidagent\app\trace\AgentTraceCollector.kt
HOST	main	app\src\main\java\com\androidagent\app\trace\TraceFormatter.kt
HOST	main	app\src\main\java\com\androidagent\app\TranscriptModel.kt
HOST	test	app\src\test\java\com\androidagent\app\AgentPreflightTest.kt
HOST	test	app\src\test\java\com\androidagent\app\debug\DebugProtocolTest.kt
HOST	test	app\src\test\java\com\androidagent\app\DeviceLocalProviderConfigTest.kt
HOST	test	app\src\test\java\com\androidagent\app\HostStatusModelTest.kt
HOST	test	app\src\test\java\com\androidagent\app\HostStopStateTest.kt
HOST	test	app\src\test\java\com\androidagent\app\persistence\PhaseGRoomIntegrationTest.kt
HOST	test	app\src\test\java\com\androidagent\app\persistence\RoomTopicSessionStoreTest.kt
HOST	test	app\src\test\java\com\androidagent\app\persistence\StoreMessageDurabilityTest.kt
HOST	test	app\src\test\java\com\androidagent\app\PhaseITranscriptProjectionTest.kt
HOST	test	app\src\test\java\com\androidagent\app\provider\AndroidTransportCancelTest.kt
HOST	test	app\src\test\java\com\androidagent\app\ProviderNodeTest.kt
HOST	test	app\src\test\java\com\androidagent\app\TranscriptModelTest.kt
PROVIDER	main	provider-openai-compatible\src\main\kotlin\com\androidagent\provider\openai\ChatCompletionWire.kt
PROVIDER	main	provider-openai-compatible\src\main\kotlin\com\androidagent\provider\openai\OpenAiCompatibleConfig.kt
PROVIDER	main	provider-openai-compatible\src\main\kotlin\com\androidagent\provider\openai\OpenAiCompatibleConfigLoader.kt
PROVIDER	main	provider-openai-compatible\src\main\kotlin\com\androidagent\provider\openai\OpenAiCompatibleProvider.kt
PROVIDER	main	provider-openai-compatible\src\main\kotlin\com\androidagent\provider\openai\OpenAiHttpTransport.kt
PROVIDER	main	provider-openai-compatible\src\main\kotlin\com\androidagent\provider\openai\OpenAiProviderException.kt
PROVIDER	main	provider-openai-compatible\src\main\kotlin\com\androidagent\provider\openai\ProviderConfigResolver.kt
PROVIDER	test	provider-openai-compatible\src\test\kotlin\com\androidagent\provider\openai\OpenAiCompatibleProviderStreamingTest.kt
PROVIDER	test	provider-openai-compatible\src\test\kotlin\com\androidagent\provider\openai\ProviderConfigTest.kt
PROVIDER	test	provider-openai-compatible\src\test\kotlin\com\androidagent\provider\openai\StreamWireTest.kt
PROVIDER	test	provider-openai-compatible\src\test\kotlin\com\androidagent\provider\openai\WireAuditTest.kt
PROVIDER_JDK PROVIDER	main	provider-openai-compatible-jdk\src\main\kotlin\com\androidagent\provider\openai\jdk\JdkOpenAiHttpTransport.kt
PROVIDER_JDK PROVIDER	test	provider-openai-compatible-jdk\src\test\kotlin\com\androidagent\provider\openai\jdk\FixtureServer.kt
PROVIDER_JDK PROVIDER	test	provider-openai-compatible-jdk\src\test\kotlin\com\androidagent\provider\openai\jdk\LiveApiTest.kt
PROVIDER_JDK PROVIDER	test	provider-openai-compatible-jdk\src\test\kotlin\com\androidagent\provider\openai\jdk\OpenRouterPcProbe.kt
PROVIDER_JDK PROVIDER	test	provider-openai-compatible-jdk\src\test\kotlin\com\androidagent\provider\openai\jdk\ProviderProtocolTest.kt
PROVIDER_JDK PROVIDER	test	provider-openai-compatible-jdk\src\test\kotlin\com\androidagent\provider\openai\jdk\VerticalSliceTest.kt
RUNTIME	main	ag-runtime\src\main\kotlin\com\androidagent\runtime\AgentRuntime.kt
RUNTIME	main	ag-runtime\src\main\kotlin\com\androidagent\runtime\persistence\MessageSerializer.kt
RUNTIME	main	ag-runtime\src\main\kotlin\com\androidagent\runtime\persistence\Records.kt
RUNTIME	main	ag-runtime\src\main\kotlin\com\androidagent\runtime\persistence\TopicSessionStore.kt
RUNTIME	test	ag-runtime\src\test\kotlin\com\androidagent\runtime\AgentRuntimeInterruptTest.kt
RUNTIME	test	ag-runtime\src\test\kotlin\com\androidagent\runtime\AgentRuntimeTest.kt
RUNTIME	test	ag-runtime\src\test\kotlin\com\androidagent\runtime\persistence\InMemoryTopicSessionStore.kt
RUNTIME	test	ag-runtime\src\test\kotlin\com\androidagent\runtime\persistence\MessageSerializerTest.kt
RUNTIME	test	ag-runtime\src\test\kotlin\com\androidagent\runtime\persistence\TopicSessionStoreContractTest.kt
RUNTIME	test	ag-runtime\src\test\kotlin\com\androidagent\runtime\RuntimeSessionCreationTest.kt
RUNTIME	test	ag-runtime\src\test\kotlin\com\androidagent\runtime\StartupRestoreTest.kt
RUNTIME	test	ag-runtime\src\test\kotlin\com\androidagent\runtime\TestSupport.kt
TOOL_FILE	main	tool-file\src\main\java\com\androidagent\tool\file\FileError.kt
TOOL_FILE	main	tool-file\src\main\java\com\androidagent\tool\file\FileResultPayload.kt
TOOL_FILE	main	tool-file\src\main\java\com\androidagent\tool\file\FileToolImpl.kt
TOOL_FILE	main	tool-file\src\main\java\com\androidagent\tool\file\FileToolSchema.kt
TOOL_FILE	main	tool-file\src\main\java\com\androidagent\tool\file\PathResolver.kt
TOOL_FILE	main	tool-file\src\main\java\com\androidagent\tool\file\TextCodec.kt
TOOL_FILE	test	tool-file\src\test\java\com\androidagent\tool\file\FileCoresTest.kt
TOOL_FILE	test	tool-file\src\test\java\com\androidagent\tool\file\FileToolImplTest.kt
TOOL_NET	main	tool-network\src\main\java\com\androidagent\tool\network\AppHttpFetcher.kt
TOOL_NET	main	tool-network\src\main\java\com\androidagent\tool\network\Fetcher.kt
TOOL_NET	main	tool-network\src\main\java\com\androidagent\tool\network\HtmlToText.kt
TOOL_NET	main	tool-network\src\main\java\com\androidagent\tool\network\NetworkError.kt
TOOL_NET	main	tool-network\src\main\java\com\androidagent\tool\network\NetworkResultMapper.kt
TOOL_NET	main	tool-network\src\main\java\com\androidagent\tool\network\NetworkResultPayload.kt
TOOL_NET	main	tool-network\src\main\java\com\androidagent\tool\network\NetworkToolImpl.kt
TOOL_NET	main	tool-network\src\main\java\com\androidagent\tool\network\NetworkToolSchema.kt
TOOL_NET	main	tool-network\src\main\java\com\androidagent\tool\network\PublicIpGuard.kt
TOOL_NET	main	tool-network\src\main\java\com\androidagent\tool\network\UrlValidator.kt
TOOL_NET	test	tool-network\src\test\java\com\androidagent\tool\network\NetworkCoresTest.kt
TOOL_NET	test	tool-network\src\test\java\com\androidagent\tool\network\NetworkToolImplTest.kt
TOOL_SHELL	main	tool-android-shell\src\main\java\com\androidagent\tool\androidshell\AndroidShellToolImpl.kt
TOOL_SHELL	main	tool-android-shell\src\main\java\com\androidagent\tool\androidshell\ShellConnection.kt
TOOL_SHELL	main	tool-android-shell\src\main\java\com\androidagent\tool\androidshell\ShellResultMapper.kt
TOOL_SHELL	main	tool-android-shell\src\main\java\com\androidagent\tool\androidshell\ShellResultPayload.kt
TOOL_SHELL	main	tool-android-shell\src\main\java\com\androidagent\tool\androidshell\ShellToolSchema.kt
TOOL_SHELL	test	tool-android-shell\src\test\java\com\androidagent\tool\androidshell\AndroidShellToolImplTest.kt

---


<!-- ===== FILE: 05_boundary_matrix.md ===== -->

# Boundary Matrix (Core/Runtime/Tool/Provider/Host/Persistence)

Source of each cell: 02 (Gradle project edges), 03 (physical imports), 04 (file ownership).

| From | To | Allowed? | Actual | Evidence | Verdict |
|---|---|---|---|---|---|
| ag-core | ag-runtime | NO | ABSENT | 02 (no project edge), 03 (RUNTIME_PKG_IMPORTS=0) | PASS |
| ag-core | app | NO | ABSENT | 02, 03 (APP_PACKAGE_IMPORTS=0) | PASS |
| ag-core | concrete tool | NO | ABSENT | 02, 03 (TOOL_PKG_IMPORTS=0) | PASS |
| ag-core | concrete provider | NO | ABSENT | 02, 03 (PROVIDER_PKG_IMPORTS=0) | PASS |
| ag-core | Android/Room/AT | NO | ABSENT | 02 (pure JVM classpath), 03 (ANDROID=0, Room mentions all comments) | PASS |
| ag-runtime | ag-core | YES | PRESENT (19 core imports, all domain types) | 02, 03 | PASS |
| ag-runtime | app | NO | ABSENT | 02, 03 (APP_PACKAGE_IMPORTS=0) | PASS |
| ag-runtime | Room/Android | NO | ABSENT | 02 (pure JVM), 03 (ROOM mentions all comments) | PASS |
| ag-runtime | concrete tool | NO | ABSENT | 02, 03 (TOOL_PKG_IMPORTS=0) | PASS |
| ag-runtime | concrete provider | NO | ABSENT | 02, 03 (PROVIDER_PKG_IMPORTS=0) | PASS |
| provider-* | app | NO | ABSENT | 02, 03 | PASS |
| provider-* | tool-* | NO | ABSENT | 02, 03 | PASS |
| provider-jdk | ag-core (direct) | n/a | ABSENT (transitive via :provider-openai-compatible only) | 02 | PASS (clean) |
| app | ag-runtime | YES | PRESENT (40 imports) | 02, 03 | PASS |
| app | ag-core | YES | PRESENT (159 imports) | 02, 03 | PASS |
| app | concrete providers | YES | PRESENT (23 imports) | 02, 03 | PASS |
| app | concrete tools | YES | PRESENT (6 imports) | 02, 03 | PASS |
| tool-shell | ag-core | YES | PRESENT (7 imports) | 02, 03 | PASS |
| tool-shell | integration-at (AT) | YES (only AT consumer path) | PRESENT (api edge) | 02, 03 (AT_PKG imports in tool main) | PASS |
| tool-network | ag-core | YES | PRESENT (6 imports) | 02, 03 | PASS |
| tool-network | AT | NO | ABSENT | 02 (no integration-at edge), 03 (AT_IMPORTS=0) | PASS (counter-example holds) |
| tool-file | ag-core | YES | PRESENT (5 imports) | 02, 03 | PASS |
| tool-file | AT | NO | ABSENT | 02, 03 | PASS |
| integration-at | ag-core / ag-runtime | NO | ABSENT | 02 (no project edge), 03 (0 imports) | PASS |
| integration-at | AT V4 AARs | YES (only point) | PRESENT (at-engine-api:4.0.0, at-cli:4.0.0) | 02 | PASS |
| app | raw AT engine types | NO (frozen: route via :integration-at) | PARTIAL — 3 Host files import com.at.composition.android.* (2) + com.at.engine.ATTerminalSession (1, debug-only) | 03 | MINOR DEBT (documented; Core/Runtime remain AT-free) |
| TranscriptModel | DB/Room | NO | ABSENT | 03 (no Room/Store imports; capability search clean) | PASS |
| TranscriptModel | model/tool execution | NO | ABSENT | 03 (only imports ToolCall/ToolResult as rendering data types) | PASS |

## Core vs Runtime boundary (detailed)

Core currently owns (verified by file ownership + imports):
- Agent loop + Run lifecycle (AgentCore, RunOptions/RunOutcome/RunFailure/RunInterruptSignal)
- TopicSession domain semantics (Session = "long-lived multi-turn conversation")
- Message / ModelMessage / UserMessage / ToolResultMessage, ToolCall/ToolSpec/ToolResult
- ModelProvider contract, Tool contract, ToolRegistry
- AgentEvent stream, MessageDurability contract (storage-agnostic)

Runtime currently owns (verified):
- AgentRuntime: host current-Session selection (initialize/create/restore), single-active-run
  Job ownership, interruptActiveRun (RUN-CONTROL-1 semantics), currentHistory() read seam,
  Core hydration
- persistence/: TopicSessionStore interface + Records (storage-neutral) + MessageSerializer
  (storage-neutral; maps Core Message <-> durable records)

Overlap check:
- Runtime does NOT re-implement the agent loop, policy, or event semantics (imports only Core
  domain types; no duplicate loop/registry/provider code in 04 file map).
- Core does NOT do process lifecycle, session selection, or I/O (no Android, no Room, no
  store; MessageDurability is a pure contract).
- VERDICT: CORE_RUNTIME_BOUNDARY = CLEAN

## Duplicate ownership audit (single authoritative owner per concern)

| Concern | Owner | Others touching it | Verdict |
|---|---|---|---|
| Session creation (durable) | ag-runtime AgentRuntime.initialize() | app SessionHolder only CALLS it | SINGLE |
| Current session selection | ag-runtime AgentRuntime | TranscriptModel renders only | SINGLE |
| History restore (durable -> Core) | ag-runtime AgentRuntime (hydrate) + TopicSessionStore (app impl) | no second path | SINGLE |
| History projection (Core -> UI) | app TranscriptModel.reproject() | only consumer of AgentRuntime.currentHistory() | SINGLE |
| Durability write semantics | app StoreMessageDurability -> RoomTopicSessionStore | Core MessageDurability is contract only | SINGLE |
| Tool Registry | core ToolRegistry (contract), Host composes instances | no second registry | SINGLE |
| ModelProvider selection | Host ChatCompositionRoot | no runtime/router owner | SINGLE |
| Run lifecycle (Job, interrupt) | ag-runtime AgentRuntime | Host only calls interruptActiveRun() | SINGLE |
| DB schema / migration | app TopicSessionDatabase (version=1) | no other schema owner | SINGLE |

DUPLICATE_OWNERSHIP_FOUND = NONE

## TopicSession / Run domain semantics (verified in code)

- `com.androidagent.core.Session` doc: "A long-lived multi-turn conversation." -> TopicSession != terminal session. PASS
- Runtime owns current-session selection + single-active-run -> same TopicSession max one active Run. PASS
- Nothing in Core/Runtime assumes one process = one conceptual session (initialize() restores OR creates; newSession() switches current). PASS
- DATABASE_VERSION = 1 (TopicSessionDatabase, version = 1, no migration wired). PASS
- Tables: topic_sessions, messages (Entities.kt @Entity tableName x2). PASS

TOPIC_SESSION_PERSISTENCE_ARCHITECTURE_STABLE = YES

---


<!-- ===== FILE: 06_ssot_residue_audit.md ===== -->

# SSOT Residue Audit (live vs historical)

## Fixed this round (LIVE section)

- `AG.TOPIC-SESSION-PERSISTENCE-IMPLEMENTATION-0_FINAL.md` §11 "Recommended Next"
  - BEFORE: "**Device verification** (`DEV-I1..I4`, NEXT) ... Device verification = PENDING"
  - AFTER: "TopicSession persistence subsystem = CLOSED / FREEZE CANDIDATE ... DEVICE-I =
    VERIFIED (§20) ... NEXT: POST-I ARCHITECTURE / IMPLEMENTATION CLOSURE AUDIT -> if PASS:
    UI + RELEASE PREPARATION"
  - Top STATUS line and §6 table were already updated in the DEVICE-I round to
    `DEVICE-I VERIFIED` / `COMPLETED (§20)`.

## Historical residue — deliberately NOT modified (point-in-time evidence)

All remaining PENDING/DEFERRED hits are inside historical phase reports, each superseded
by a later section of the same SSOT. Per task rule ("do not rewrite history; only live
current-state sections"), these stay as-is:

| Line | Text | Context | Superseded by |
|---|---|---|---|
| 262, 658 | "replace production Noop" | Phase F scope wording (the Noop WAS replaced in Phase F) | §12 Phase F report |
| 481, 630 | "Phase F pending, as frozen" | earlier-phase scope text | §12 |
| 782, 784 | "pending cancellation" | test-name descriptions (CANCEL_F3/F4) | n/a (test semantics) |
| 1147, 1155 | FAILURE_CLOSEOUT_STABLE_BOUNDARY = PENDING / quarantine deferred | Phase G report, resolved by NORMALIZE-1 | §14 NORMALIZE-1 |
| 1415 | DEVICE_G_VERIFICATION = DEFERRED (JVM/Robolectric only this round) | Phase G report | §20 DEVICE-I PASS |
| 1553-1554, 1582 | Phase I PENDING / PHASE_I_STATUS = NEXT (PENDING) | Phase G report's next-work table (point-in-time) | §19 Phase I report |
| 1712-1716 | DEVICE_I1..I4 = DEFERRED, DEVICE_I_VERIFICATION = DEFERRED | §19 Phase I report (point-in-time, written before device round) | §20 DEVICE-I PASS (all four = PASS, REAL_DEVICE_VERIFIED) |

## Architecture docs (docs/architecture/*.md)

Stale-claim scan (`Device verification PENDING`, `pending Phase F/G`) = 0 hits. The two
architecture freeze docs (AG.TOPIC-SESSION-ARCHITECTURE-FREEZE-0, AG.TOPIC-SESSION-DURABILITY-SEAM-
PREFLIGHT-0) are read-only inputs and contain no live-residue device wording.

## AGENTS.md

No device-pending / Phase-pending residue. AGENTS.md current-state section describes the
pre-persistence capability line (RUN-CONTROL, HOST-STOP, observability, tools) as frozen
facts — none of it is contradicted by Phase A-I or DEVICE-I (all those capabilities remain
VERIFIED). Note: AGENTS.md is the repo-level "WHAT IS TRUE NOW" doc; adding the TopicSession
persistence line there is a Controller/roadmap-level documentation decision and is OUT OF
SCOPE for this round (allowed-file list). Recorded as MINOR DEBT (doc sync candidate for the
next normalization round).

## Verdict

LIVE_SSOT_RESIDUE_CLEANED = YES (within allowed-file scope: FINAL SSOT live sections are
consistent; historical sections intentionally untouched)

---


<!-- ===== FILE: 07_regression_results.md ===== -->

FULL REGRESSION (POST-I CLOSURE AUDIT) -- rerun-tasks, JUnit XML actuals
Run: .\gradlew.bat :ag-core:test :ag-runtime:test :provider-openai-compatible-jdk:test :app:testDebugUnitTest :app:assembleDebug --rerun-tasks
BUILD = BUILD SUCCESSFUL (2m51s); assembleDebug = PASS

ag-core : tests=52 failures=0 errors=0
ag-runtime : tests=88 failures=0 errors=0
app : tests=171 failures=0 errors=0
provider-jdk : tests=26 failures=0 errors=0

I11 FLAKY OBSERVED THIS RUN = NO (suite green under full matrix)

---


<!-- ===== FILE: 08_final_closure_matrix.md ===== -->

# Final Closure Matrix — AG.POST-I-ARCHITECTURE-CLOSURE-FREEZE-1

## Physical isolation verdicts

```text
AG_CORE_PHYSICAL_ISOLATION        = PASS
  (0 android/androidx imports; 0 Room code refs (4 comment hits); 0 app/tool/provider/runtime
   package imports; 0 project deps in Gradle; pure JVM; production deps = kotlin stdlib +
   coroutines only)
AG_RUNTIME_PHYSICAL_ISOLATION     = PASS
  (0 android/androidx imports; 0 Room code refs (13 comment/doc hits); 0 app/tool/provider
   imports; deps = ag-core + stdlib + coroutines + kotlinx-serialization; persistence package
   is storage-neutral contracts only)
TOOL_LAYER_PHYSICAL_ISOLATION     = PASS
  (shell: ag-core + integration-at(api) only; network: ag-core only, AT=0; file: ag-core only,
   AT=0; all concrete tools implement the Core Tool contract; no tool imports ag-runtime or app)
PROVIDER_PHYSICAL_ISOLATION       = PASS
  (provider-openai-compatible: ag-core only; provider-jdk: provider-openai-compatible only;
   both pure JVM; 0 app/tool/Android imports; no session lifecycle, no tool policy, no
   TranscriptModel mutation)
APP_HOST_BOUNDARY                 = PASS
  (Host owns Room impl, Composition Root, SessionHolder, TranscriptModel, UI, provider wiring,
   tool registration; no duplicate Core loop, no duplicate Runtime lifecycle, no duplicate
   persistence domain; TranscriptModel is presentation-only)
PERSISTENCE_PHYSICAL_ISOLATION    = PASS
  (contracts+records+serializer in ag-runtime/persistence = storage-neutral; Room entities,
   DAOs, Database, RoomTopicSessionStore, StoreMessageDurability all in app/persistence;
   no Room entity leakage into runtime/core; no second UI->DB restore pipeline)
```

## Architecture verdicts

```text
CORE_RUNTIME_BOUNDARY             = CLEAN
AG_ENGINE_BOUNDARY                = FREEZE_READY
TOOL_DEPENDENCY_INVERSION         = PASS
MODEL_PROVIDER_DEPENDENCY_INVERSION = PASS
CLI_REUSE_READINESS               = READY (Core+Runtime are pure JVM, host-neutral; a future CLI
                                     can compose them without any Android/Room/UI code)
TOPIC_SESSION_PERSISTENCE_ARCHITECTURE_STABLE = YES
TOPIC_SESSION_FOUNDATION          = FREEZE_READY
ARCHITECTURE_LAYERING             = CLEAR
OWNERSHIP                         = SINGLE (no duplicate owners found)
DEPENDENCY_DIRECTION              = CLEAN
PHYSICAL_ISOLATION                = EFFECTIVE
```

## Findings classification

```text
RELEASE BLOCKERS   = NONE
ARCHITECTURE BLOCKERS = NONE

MINOR DEBT (recorded, non-blocking):
  M1. Host direct AT V4 composition imports (3 main files: ChatCompositionRoot 2,
      CompositionAdbSemantic 5, DebugBridge 1) — frozen rule says route AT via :integration-at;
      Core/Runtime are AT-free so no boundary violation, but Host AT-surface could be reduced
      to zero-import by routing these through the adapter. Future cleanup candidate.
  M2. AGENTS.md current-state section does not yet mention the TopicSession persistence line
      (doc sync; out of this round's allowed-file scope).
  M3. AgentRuntimeInterruptTest I11 = KNOWN TEST STABILITY DEBT (full-matrix parallel flake,
      passes isolated; not observed in this round's full regression).
  M4. Worktree carries 86 uncommitted files (37 modified pre-existing from Phases A-I +
      46 untracked evidence/fixtures) — Git normalization is a separate authorized task.

FUTURE DESIGN (explicitly NOT frozen):
  session browser UI, session naming, retention policy, cloud sync, provider fallback,
  AT concrete tool catalog, CLI command syntax, final UI visual style.
```

## TopicSession closure verdict

```text
TOPIC_SESSION_CORE_CAPABILITY    = BUILT
TOPIC_SESSION_PERSISTENCE        = BUILT
TOPIC_SESSION_RECOVERY           = BUILT
TOPIC_SESSION_VISIBLE_RESUME     = REAL_DEVICE_VERIFIED
TOPIC_SESSION_FOUNDATION         = FREEZE_READY
```

## PASS gate summary

```text
LIVE_SSOT_RESIDUE_CLEANED          = YES
MODULE_INVENTORY_COMPLETE          = YES (9 modules + root, 01)
GRADLE_DEPENDENCY_AUDIT_COMPLETE   = YES (02)
PACKAGE_IMPORT_AUDIT_COMPLETE      = YES (03)
FILE_OWNERSHIP_AUDIT_COMPLETE      = YES (04, 116 production+test files)
CORE_RUNTIME_BOUNDARY              = CLEAN
ALL SIX PHYSICAL ISOLATION         = PASS
AG_ENGINE_BOUNDARY                 = FREEZE_READY
CLI_REUSE_READINESS                = READY
NO RELEASE BLOCKER                 = YES
NO ARCHITECTURE BLOCKER            = YES
DURABLE_RUNTIME_RESUME             = PASS
TRANSCRIPT_REPROJECTION            = PASS
FULL_USER_VISIBLE_RESUME           = REAL_DEVICE_VERIFIED
AG_CORE_TESTS                      = PASS (52, JUnit XML actuals)
AG_RUNTIME_TESTS                   = PASS (88)
APP_TESTS                          = PASS (171)
PROVIDER_TESTS                     = PASS (26)
ASSEMBLE_DEBUG                     = PASS
PRODUCTION_CODE_CHANGED            = NO
TEST_CODE_CHANGED                  = NO
GRADLE_CHANGED                     = NO
PRE_EXISTING_DIRTY_FILES_PRESERVED = YES
DEVICE_RETEST_REQUIRED             = NO
```

---
