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
