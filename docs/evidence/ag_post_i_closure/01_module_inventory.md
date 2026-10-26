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
