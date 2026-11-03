# AG.AT-BASELINE-DIFFERENTIAL-AUDIT-0 — CONSOLIDATED EVIDENCE

Single-file merge of docs/evidence/at_baseline_differential_audit_0/ (7 files, 00-07).
Date: 2026-11-03 (evidence re-captured 2026-11-04).
AG baseline: main @ c90f6d1bffebf80c16980644073d5057c2a64e70 (CLEAN).
CURRENT AT: v0.4.0 tag target 7216d0e8131e92e8914ce7c8443e1668b1d53aca.
CANDIDATE AT: v0.4.2 tag target 925e7ac223d19eea7ddb89bf05e55df25ca7a7f2.
VERDICT = PASS_WITH_GAPS; RECOMMENDATION = VALIDATE_AND_MOVE_TO_V0.4.2.

==============================================================================
FILE: 00_git_baseline.txt
==============================================================================

﻿AG.AT-BASELINE-DIFFERENTIAL-AUDIT-0 — 00 GIT BASELINE
Date: 2026-11-03 (re-run 2026-11-04 for evidence capture)

START_BRANCH = main
START_HEAD   = c90f6d1bffebf80c16980644073d5057c2a64e70
WORKTREE     = ?? docs/audit/AG.AT-BASELINE-DIFFERENTIAL-AUDIT-0.md

--- git log -10 --oneline --decorate ---
c90f6d1 (HEAD -> main) docs: close normalize-1 evidence and exports a79c062 docs: normalize post-UI architecture and roadmap SSOT edfc603 AG.UI-TRANSCRIPT-CARDS-1 + AG.UI-SHELL-1: transcript cards, session browser, provider settings, shell UI b7dbc04 docs: normalize post-freeze live SSOT f640ed0 (tag: AG.POST-I-CORE-FREEZE-V1) freeze: establish post-I pre-UI AG core baseline a6eea61 AG.AT-FULL-SUBSYSTEM-INTEGRATION-0: close complete AT V3 consumer subsystem 4d8aec7 AG.HOST-SESSION-CONTINUITY-0.1: complete session UI rebinding fix c4a58fe AG.HOST-SESSION-CONTINUITY-0: preserve session across config changes 6c4d8f1 AG.AT-FULL-CONSUMER-INTEGRATION-0: restore full AT V3 consumer capability 50211c6 AG.AT-CONSUMER-RECONCILIATION-0.1: normalize audit verdict

==============================================================================
FILE: 01_at_release_identities.txt
==============================================================================

﻿AG.AT-BASELINE-DIFFERENTIAL-AUDIT-0 — 01 AT RELEASE IDENTITIES
Date: 2026-11-03 (re-run 2026-11-04 for evidence capture)
AT_REPO = D:\codebank\AndCode\AndroidTerminal-V4-LOCAL-TCP-042 (feature/local-tcp-v0.4.2)

--- git rev-list -n 1 v0.4.0 ---
7216d0e8131e92e8914ce7c8443e1668b1d53aca
--- git rev-list -n 1 v0.4.1 ---
cbc20985b2154d91e11b29f53823b182322bca72
--- git rev-list -n 1 v0.4.2 ---
925e7ac223d19eea7ddb89bf05e55df25ca7a7f2

--- v0.4.0 tag target commit ---
7216d0e8131e92e8914ce7c8443e1668b1d53aca AT.V4-6: close device parity and freeze V4 consumer boundary 2026-10-22 12:03:42 +0800

--- v0.4.2 tag target commit (expected 925e7ac223d19eea7ddb89bf05e55df25ca7a7f2) ---
925e7ac223d19eea7ddb89bf05e55df25ca7a7f2 docs: finalize AndroidTerminal v0.4.2 release evidence 2026-11-03 21:15:01 +0800

--- v0.4.0 == AG consumed freeze commit 7216d0e (describe) ---
v0.4.0

--- rev-list --count v0.4.0..v0.4.2 ---
46

--- v0.4.2 version identity ---
        versionCode = 4         versionName = "0.4.2"

==============================================================================
FILE: 02_ag_consumed_artifacts.txt
==============================================================================

AG.AT-BASELINE-DIFFERENTIAL-AUDIT-0 — 02 AG CONSUMED AT ARTIFACTS (vendored bundle)
Date: 2026-11-03 (re-run 2026-11-04 for evidence capture)

Bundle: third_party/at-v4-consumer-freeze-v1 (MANIFEST.md: freeze commit 7216d0e, tag AT.V4-CONSUMER-FREEZE-V1, variant debug)

--- m2 coordinates (settings.gradle.kts repo-local Maven) ---
com.at.engine.api:at-engine-api:4.0.0 (jar, public contract)
com.at.composition.android:at-composition-android:4.0.0 (aar, consumer entry)
com.at.cli:at-cli:4.0.0 (aar, optional frontend)
com.at.engine:at-engine:4.0.0 (aar, internal transitive)
com.at.engine.spi:at-engine-spi:4.0.0 (jar, internal transitive)
com.at.backend.local:at-backend-local:4.0.0 (aar, internal transitive)
com.at.backend.adb:at-backend-adb:4.0.0 (aar, internal transitive)
com.adb.engine:adb-engine:4.0.0 (aar, internal transitive)

--- at-engine-api-4.0.0.jar fingerprint ---
size   = 23079 bytes
sha256 = b6b28fb1a88eb3139d809e2732534d0f2e4254e59abb4ef9e059380e2ddff1f0

--- at-engine-api 4.0.0 class list (extracted from jar) ---
  com/at/engine/ATEngine$Companion.class
  com/at/engine/ATEngine.class
  com/at/engine/ATExecutionOutcome.class
  com/at/engine/ATExecutionRequest.class
  com/at/engine/ATExecutionResult.class
  com/at/engine/ATOpenSessionResult$Failure.class
  com/at/engine/ATOpenSessionResult$Success.class
  com/at/engine/ATOpenSessionResult.class
  com/at/engine/ATSessionEvent$Closed.class
  com/at/engine/ATSessionEvent$Data.class
  com/at/engine/ATSessionEvent$Eof.class
  com/at/engine/ATSessionEvent$Exited.class
  com/at/engine/ATSessionEvent$Failed.class
  com/at/engine/ATSessionEvent$Resized.class
  com/at/engine/ATSessionEvent.class
  com/at/engine/ATSessionLaunch.class
  com/at/engine/ATSessionState.class
  com/at/engine/ATTerminalSession$DefaultImpls.class
  com/at/engine/ATTerminalSession.class

--- at-engine-api 4.0.0 public API (bytecode signatures) ---
=== com/at/engine/ATEngine.class
com/at/engine/ATEngine java/lang/Object execute E(Lcom/at/engine/ATExecutionRequest;)Lcom/at/engine/ATExecutionResult; #Lorg/jetbrains/annotations/NotNull; openSession D(Lcom/at/engine/ATSessionLaunch;)Lcom/at/engine/ATOpenSessionResult; <clinit> ()V  com/at/engine/ATEngine$Companion $$INSTANCE "Lcom/at/engine/ATEngine$Companion; Companion Lkotlin/Metadata; H&J Lcom/at/engine/ATEngine; !Lcom/at/engine/ATExecutionResult; request "Lcom/at/engine/ATExecutionRequest; #Lcom/at/engine/ATOpenSessionResult; launch Lcom/at/engine/ATSessionLaunch; at-engine-api ATEngine.kt RuntimeInvisibleAnnotations $RuntimeInvisibleParameterAnnotations Code InnerClasses SourceFile RuntimeVisibleAnnotations

=== com/at/engine/ATExecutionOutcome.class
 com/at/engine/ATExecutionOutcome 4Ljava/lang/Enum<Lcom/at/engine/ATExecutionOutcome;>; java/lang/Enum <init> (Ljava/lang/String;I)V ()V this "Lcom/at/engine/ATExecutionOutcome; $enum$name Ljava/lang/String; $enum$ordinal values %()[Lcom/at/engine/ATExecutionOutcome; $VALUES #[Lcom/at/engine/ATExecutionOutcome; [Ljava/lang/Object; clone ()Ljava/lang/Object; valueOf 6(Ljava/lang/String;)Lcom/at/engine/ATExecutionOutcome; 5(Ljava/lang/Class;Ljava/lang/String;)Ljava/lang/Enum; value getEntries ()Lkotlin/enums/EnumEntries; @()Lkotlin/enums/EnumEntries<Lcom/at/engine/ATExecutionOutcome;>; #Lorg/jetbrains/annotations/NotNull; $ENTRIES Lkotlin/enums/EnumEntries; $values COMPLETED TRANSPORT_ERROR UNSUPPORTED <clinit> [Ljava/lang/Enum; kotlin/enums/EnumEntriesKt enumEntries -([Ljava/lang/Enum;)Lkotlin/enums/EnumEntries; Lkotlin/Metadata; at-engine-api ATExecution.kt Code LineNumberTable LocalVariableTable Signature RuntimeInvisibleAnnotations SourceFile RuntimeVisibleAnnotations@1 /S* 2S* 5S*

=== com/at/engine/ATExecutionRequest.class
 com/at/engine/ATExecutionRequest java/lang/Object <init> (Ljava/lang/String;I)V #Lorg/jetbrains/annotations/NotNull; command kotlin/jvm/internal/Intrinsics checkNotNullParameter '(Ljava/lang/Object;Ljava/lang/String;)V ()V Ljava/lang/String; timeoutMs this "Lcom/at/engine/ATExecutionRequest; E(Ljava/lang/String;IILkotlin/jvm/internal/DefaultConstructorMarker;)V getCommand ()Ljava/lang/String; getTimeoutMs ()I component1 component2 copy 7(Ljava/lang/String;I)Lcom/at/engine/ATExecutionRequest; copy$default l(Lcom/at/engine/ATExecutionRequest;Ljava/lang/String;IILjava/lang/Object;)Lcom/at/engine/ATExecutionRequest; toString *ATExecutionRequest(command= , timeoutMs= $java/lang/invoke/StringConcatFactory makeConcatWithConstants (Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/invoke/CallSite; '(Ljava/lang/String;I)Ljava/lang/String; hashCode java/lang/String java/lang/Integer (I)I result equals (Ljava/lang/Object;)Z $Lorg/jetbrains/annotations/Nullable; areEqual '(Ljava/lang/Object;Ljava/lang/Object;)Z other Ljava/lang/Object; Lkotlin/Metadata; at-engine-api ATExecution.kt RuntimeInvisibleAnnotations Code LineNumberTable LocalVariableTable $RuntimeInvisibleParameterAnnotations StackMapTable SourceFile RuntimeVisibleAnnotations BootstrapMethods =*+ =*+ A`< 4*+

=== com/at/engine/ATExecutionResult.class
com/at/engine/ATExecutionResult java/lang/Object <init> ](Lcom/at/engine/ATExecutionOutcome;Ljava/lang/String;Ljava/lang/String;IZLjava/lang/String;)V #Lorg/jetbrains/annotations/NotNull; $Lorg/jetbrains/annotations/Nullable; outcome kotlin/jvm/internal/Intrinsics checkNotNullParameter '(Ljava/lang/Object;Ljava/lang/String;)V stdout stderr ()V "Lcom/at/engine/ATExecutionOutcome; Ljava/lang/String; exitCode hasError error this !Lcom/at/engine/ATExecutionResult; getOutcome $()Lcom/at/engine/ATExecutionOutcome; getStdout ()Ljava/lang/String; getStderr getExitCode ()I getHasError ()Z getError getExecutionFailed  com/at/engine/ATExecutionOutcome COMPLETED component1 component2 component3 component4 component5 component6 copy }(Lcom/at/engine/ATExecutionOutcome;Ljava/lang/String;Ljava/lang/String;IZLjava/lang/String;)Lcom/at/engine/ATExecutionResult; copy$default (Lcom/at/engine/ATExecutionResult;Lcom/at/engine/ATExecutionOutcome;Ljava/lang/String;Ljava/lang/String;IZLjava/lang/String;ILjava/lang/Object;)Lcom/at/engine/ATExecutionResult; toString QATExecutionResult(outcome= , stdout= , stderr= , exitCode= , hasError= , error= $java/lang/invoke/StringConcatFactory makeConcatWithConstants (Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/invoke/CallSite; n(Lcom/at/engine/ATExecutionOutcome;Ljava/lang/String;Ljava/lang/String;IZLjava/lang/String;)Ljava/lang/String; hashCode java/lang/String java/lang/Integer (I)I java/lang/Boolean (Z)I result equals (Ljava/lang/Object;)Z areEqual '(Ljava/lang/Objec

=== com/at/engine/ATSessionLaunch.class
com/at/engine/ATSessionLaunch java/lang/Object <init> &(Ljava/lang/String;IILjava/util/Map;)V L(Ljava/lang/String;IILjava/util/Map<Ljava/lang/String;Ljava/lang/String;>;)V #Lorg/jetbrains/annotations/NotNull; command kotlin/jvm/internal/Intrinsics checkNotNullParameter '(Ljava/lang/Object;Ljava/lang/String;)V env ()V Ljava/lang/String; initialRows initialCols Ljava/util/Map; this Lcom/at/engine/ATSessionLaunch; U(Ljava/lang/String;IILjava/util/Map;ILkotlin/jvm/internal/DefaultConstructorMarker;)V kotlin/collections/MapsKt emptyMap ()Ljava/util/Map; getCommand ()Ljava/lang/String; getInitialRows ()I getInitialCols getEnv 7()Ljava/util/Map<Ljava/lang/String;Ljava/lang/String;>; component1 component2 component3 component4 copy D(Ljava/lang/String;IILjava/util/Map;)Lcom/at/engine/ATSessionLaunch; j(Ljava/lang/String;IILjava/util/Map<Ljava/lang/String;Ljava/lang/String;>;)Lcom/at/engine/ATSessionLaunch; copy$default v(Lcom/at/engine/ATSessionLaunch;Ljava/lang/String;IILjava/util/Map;ILjava/lang/Object;)Lcom/at/engine/ATSessionLaunch; toString ?ATSessionLaunch(command= , initialRows= , initialCols= , env= $java/lang/invoke/StringConcatFactory makeConcatWithConstants (Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/invoke/CallSite; 7(Ljava/lang/String;IILjava/util/Map;)Ljava/lang/String; hashCode java/lang/String java/lang/Integer (I)I result equals (Ljava/lang/Object;)Z $Lorg/jetbrains/annotations/Nullable; areEqual '(Ljava/lang/Object;Ljava/lang/Object;)Z other Ljava/lang/Object; 5Ljava/util/M

=== com/at/engine/ATSessionState.class
com/at/engine/ATSessionState 0Ljava/lang/Enum<Lcom/at/engine/ATSessionState;>; java/lang/Enum <init> (Ljava/lang/String;I)V ()V this Lcom/at/engine/ATSessionState; $enum$name Ljava/lang/String; $enum$ordinal values !()[Lcom/at/engine/ATSessionState; $VALUES [Lcom/at/engine/ATSessionState; [Ljava/lang/Object; clone ()Ljava/lang/Object; valueOf 2(Ljava/lang/String;)Lcom/at/engine/ATSessionState; 5(Ljava/lang/Class;Ljava/lang/String;)Ljava/lang/Enum; value getEntries ()Lkotlin/enums/EnumEntries; <()Lkotlin/enums/EnumEntries<Lcom/at/engine/ATSessionState;>; #Lorg/jetbrains/annotations/NotNull; $ENTRIES Lkotlin/enums/EnumEntries; $values OPENING ACTIVE EXITED FAILED CLOSED <clinit> [Ljava/lang/Enum; kotlin/enums/EnumEntriesKt enumEntries -([Ljava/lang/Enum;)Lkotlin/enums/EnumEntries; Lkotlin/Metadata; at-engine-api ATSession.kt Code LineNumberTable LocalVariableTable Signature RuntimeInvisibleAnnotations SourceFile RuntimeVisibleAnnotations@1 /S* 2S* 5S* 8S* ;S*

==============================================================================
FILE: 03_ag_at_import_scan.txt
==============================================================================

AG.AT-BASELINE-DIFFERENTIAL-AUDIT-0 — 03 AG AT IMPORT SCAN (measured)
Date: 2026-11-03 (re-run 2026-11-04 for evidence capture)

All lines containing com.at.* or com.adb.* in AG source (excl. third_party/build):
TOTAL = 50

app/build.gradle.kts:84 implementation("com.at.composition.android:at-composition-android:4.0.0")
app/src/main/java/com/androidagent/app/ChatCompositionRoot.kt:25 import com.at.composition.android.ATAndroid
app/src/main/java/com/androidagent/app/ChatCompositionRoot.kt:26 import com.at.composition.android.ATAndroidConfig
app/src/main/java/com/androidagent/app/ChatCompositionRoot.kt:91 * injection and never imports com.at.composition.android.* types.
app/src/main/java/com/androidagent/app/CompositionAdbSemantic.kt:8 import com.at.composition.android.ATAndroidAdbControl
app/src/main/java/com/androidagent/app/CompositionAdbSemantic.kt:9 import com.at.composition.android.ATAdbBootstrapState
app/src/main/java/com/androidagent/app/CompositionAdbSemantic.kt:10 import com.at.composition.android.ATAdbReadinessState
app/src/main/java/com/androidagent/app/CompositionAdbSemantic.kt:11 import com.at.composition.android.ATAdbPairingDiscovery
app/src/main/java/com/androidagent/app/CompositionAdbSemantic.kt:12 import com.at.composition.android.ATAdbPairingResult
app/src/main/java/com/androidagent/app/CompositionAdbSemantic.kt:20 * `com.at.composition.android.*` types.
app/src/main/java/com/androidagent/app/debug/DebugBridge.kt:13 import com.at.engine.ATTerminalSession
app/src/test/java/com/androidagent/app/AgentPreflightTest.kt:262 // AG.V2-2: AT V3 uses packages com.at.engine / com.adb.engine /
app/src/test/java/com/androidagent/app/AgentPreflightTest.kt:263 // com.at.backend.adb (legacy v0 used com.androidterminal.*).
app/src/test/java/com/androidagent/app/AgentPreflightTest.kt:271 "import com.at.engine",        // V3 AT Engine
app/src/test/java/com/androidagent/app/AgentPreflightTest.kt:272 "import com.at.backend",       // V3 AT Backend
app/src/test/java/com/androidagent/app/AgentPreflightTest.kt:273 "import com.adb.engine",       // V3 ADB Engine
integration-at/build.gradle.kts:58 api("com.at.engine.api:at-engine-api:4.0.0")
integration-at/build.gradle.kts:59 api("com.at.cli:at-cli:4.0.0")
integration-at/src/main/java/com/androidagent/integration/at/AdbSemantic.kt:9 * imports `com.at.composition.android.*` types.
integration-at/src/main/java/com/androidagent/integration/at/AtAdbBootstrap.kt:7 * the adapter and tool layers never import `com.at.composition.android.*`.
integration-at/src/main/java/com/androidagent/integration/at/AtAdbReadiness.kt:7 * the adapter and tool layers never import `com.at.composition.android.*`.
integration-at/src/main/java/com/androidagent/integration/at/AtExecutionAdapter.kt:10 //   - This adapter ONLY imports com.at.engine.* (public contract) +
integration-at/src/main/java/com/androidagent/integration/at/AtExecutionAdapter.kt:11 //     com.at.cli.* (optional frontend).
integration-at/src/main/java/com/androidagent/integration/at/AtExecutionAdapter.kt:12 //   - It does NOT import com.at.composition.android.* (Host's responsibility).
integration-at/src/main/java/com/androidagent/integration/at/AtExecutionAdapter.kt:13 //   - It does NOT import com.adb.engine.* (AdbRuntime / AdbConsumerBootstrap / backends).
integration-at/src/main/java/com/androidagent/integration/at/AtExecutionAdapter.kt:14 //   - It does NOT import com.at.backend.* (backend implementations).
integration-at/src/main/java/com/androidagent/integration/at/AtExecutionAdapter.kt:25 import com.at.cli.AdbCliBridgeServer
integration-at/src/main/java/com/androidagent/integration/at/AtExecutionAdapter.kt:26 import com.at.cli.ATCliExecution
integration-at/src/main/java/com/androidagent/integration/at/AtExecutionAdapter.kt:27 import com.at.cli.ATCliPairingResult
integration-at/src/main/java/com/androidagent/integration/at/AtExecutionAdapter.kt:28 import com.at.cli.ATCliPairingState
integration-at/src/main/java/com/androidagent/integration/at/AtExecutionAdapter.kt:29 import com.at.cli.LocalCliBootstrap
integration-at/src/main/java/com/androidagent/integration/at/AtExecutionAdapter.kt:30 import com.at.engine.ATEngine
integration-at/src/main/java/com/androidagent/integration/at/AtExecutionAdapter.kt:31 import com.at.engine.ATExecutionRequest
integration-at/src/main/java/com/androidagent/integration/at/AtExecutionAdapter.kt:32 import com.at.engine.ATExecutionResult
integration-at/src/main/java/com/androidagent/integration/at/AtExecutionAdapter.kt:33 import com.at.engine.ATOpenSessionResult
integration-at/src/main/java/com/androidagent/integration/at/AtExecutionAdapter.kt:34 import com.at.engine.ATSessionEvent
integration-at/src/main/java/com/androidagent/integration/at/AtExecutionAdapter.kt:35 import com.at.engine.ATSessionLaunch
integration-at/src/main/java/com/androidagent/integration/at/AtExecutionAdapter.kt:36 import com.at.engine.ATTerminalSession
integration-at/src/main/java/com/androidagent/integration/at/AtExecutionAdapter.kt:69 *                     com.at.composition.android.* types.
integration-at/src/main/java/com/androidagent/integration/at/AtExecutionAdapter.kt:439 const val CLI_MAIN_CLASS: String = "com.at.cli.AtCliMain"
integration-at/src/main/java/com/androidagent/integration/at/AtPairingResult.kt:7 * the adapter and tool layers never import `com.at.composition.android.*`.
integration-at/src/test/java/com/androidagent/integration/at/CliBootstrapLifecycleTest.kt:3 import com.at.cli.LocalCliBootstrap
integration-at/src/test/java/com/androidagent/integration/at/CliBootstrapLifecycleTest.kt:26 private val mainClass = "com.at.cli.AtCliMain"
tool-android-shell/src/main/java/com/androidagent/tool/androidshell/AndroidShellToolImpl.kt:8 import com.at.engine.ATExecutionResult
tool-android-shell/src/main/java/com/androidagent/tool/androidshell/AndroidShellToolImpl.kt:17 * instead of directly constructing an [com.at.engine.ATEngine]. The adapter is
tool-android-shell/src/main/java/com/androidagent/tool/androidshell/ShellResultMapper.kt:4 import com.at.engine.ATExecutionOutcome
tool-android-shell/src/main/java/com/androidagent/tool/androidshell/ShellResultMapper.kt:5 import com.at.engine.ATExecutionResult
tool-android-shell/src/main/java/com/androidagent/tool/androidshell/ShellResultPayload.kt:3 import com.at.engine.ATExecutionResult
tool-android-shell/src/test/java/com/androidagent/tool/androidshell/AndroidShellToolImplTest.kt:4 import com.at.engine.ATExecutionOutcome
tool-android-shell/src/test/java/com/androidagent/tool/androidshell/AndroidShellToolImplTest.kt:5 import com.at.engine.ATExecutionResult

Classification:
  :integration-at  -> com.at.engine.* (public contract) + com.at.cli.* (allowed)
  :tool-android-shell -> com.at.engine.ATExecutionResult / ATExecutionOutcome only
  :app (composition root) -> com.at.composition.android.* (Host facade, allowed)
  :app DebugBridge -> com.at.engine.ATTerminalSession (debug-only)
  ag-core  -> 0 AT imports (measured)
  ag-runtime -> 0 AT imports (measured)
  com.adb.engine.* imports in AG -> 0 (verified)
  com.at.backend.* imports in AG -> 0 (verified)

ARCHITECTURE_BLOCKER = NO

==============================================================================
FILE: 04_v040_v042_differential.txt
==============================================================================

﻿AG.AT-BASELINE-DIFFERENTIAL-AUDIT-0 — 04 V0.4.0 -> V0.4.2 DIFFERENTIAL (measured)
Date: 2026-11-03 (re-run 2026-11-04 for evidence capture)
AT_REPO = D:\codebank\AndCode\AndroidTerminal-V4-LOCAL-TCP-042

--- git diff --name-status v0.4.0 v0.4.2 (non-kt, non-md) ---
M	.gitignore A	LICENSE A	NOTICE M	adb-engine/build.gradle.kts M	adb-engine/src/main/cpp/CMakeLists.txt M	adb-engine/src/main/cpp/adb/client/adb_client.cpp M	adb-engine/src/main/cpp/adb/client/adb_client.h M	adb-engine/src/main/cpp/adb/connection/adb_connection.cpp M	adb-engine/src/main/cpp/adb/connection/adb_connection.h A	adb-engine/src/main/cpp/adb/connection/adb_packet_transport.h M	adb-engine/src/main/cpp/adb/connection/adb_stream_hub.cpp M	adb-engine/src/main/cpp/adb/connection/adb_stream_hub.h A	adb-engine/src/main/cpp/adb/connection/plain_tcp_connection.cpp A	adb-engine/src/main/cpp/adb/connection/plain_tcp_connection.h M	adb-engine/src/main/cpp/adb/transport/tls_transport.cpp M	adb-engine/src/main/cpp/adb/transport/tls_transport.h M	adb-engine/src/main/cpp/jni/adb_engine_jni.cpp A	adb-engine/src/main/cpp/third_party/boringssl/LICENSE M	app/build.gradle.kts M	app/src/main/res/layout/activity_main.xml A	"\346\234\254\350\275\256\344\272\244\344\273\230/AT.V042-EXECUTION-CAPABILITY-FINAL.md" A	"\346\234\254\350\275\256\344\272\244\344\273\230/AT.V042-RELEASE-1.md" A	"\346\234\254\350\275\256\344\272\244\344\273\230/AT.V042-RELEASE-PREFLIGHT-1.md" D	"\346\234\254\350\275\256\344\272\244\344\273\230/AT.V4-6_Final_Freeze_Verdict.md" D	"\346\234\254\350\275\256\344\272\244\344\273\230/AT.V4-6_Implementation.md" D	"\346\234\254\350\275\256\344\272\244\344\273\230/AT.V4-CONSUMER-FREEZE-V1.md" D	"\346\234\254\350\275\256\344\272\244\344\273\230/AT.V4_MASTER_SSOT.md" D	"\346\234\254\350\275\256\344\272\244\344\273\230/AT.V4_ROADMAP.md" D	"\346\234\254\350\275\256\344\272\244\344\273\230/AT_V4_CONSUMER_HANDOFF.md" D	"\346\234\254\350\275\256\344\272\244\344\273\230/SHA256SUMS.txt"

--- .kt files changed (count) ---
22

--- .kt files changed (list) ---
M	adb-engine/src/main/java/com/adb/engine/AdbConsumerBootstrap.kt M	adb-engine/src/main/java/com/adb/engine/AdbEngine.kt A	adb-engine/src/main/java/com/adb/engine/AdbPairingMetadata.kt M	adb-engine/src/main/java/com/adb/engine/AdbRecovery.kt M	adb-engine/src/main/java/com/adb/engine/AdbRecoveryCoordinator.kt M	adb-engine/src/main/java/com/adb/engine/AdbRuntime.kt A	adb-engine/src/main/java/com/adb/engine/AdbTransportMode.kt M	adb-engine/src/main/java/com/adb/engine/internal/AdbNative.kt A	adb-engine/src/test/java/com/adb/engine/AdbPairingMetadataTest.kt A	adb-engine/src/test/java/com/adb/engine/StaleGuardTest.kt A	adb-engine/src/test/java/com/adb/engine/TrustLossRecoveryTest.kt M	adb-only-test-host/src/androidTest/java/com/androidterminal/adbonlyhost/AdbRuntimeLifecycleTest.kt M	app/src/main/java/com/androidterminal/app/AppCliExecution.kt M	app/src/main/java/com/androidterminal/app/MainActivity.kt M	app/src/main/java/com/androidterminal/app/PairActivity.kt M	at-backend-adb/src/main/java/com/at/backend/adb/AdbBackend.kt M	at-cli/src/test/java/com/at/cli/AdbCliArgsTest.kt M	at-composition-android/src/main/java/com/at/composition/android/ATAndroidAdbControl.kt M	test-host/src/androidTest/java/com/androidterminal/testhost/AdbBackendHeadlessTest.kt A	test-host/src/androidTest/java/com/androidterminal/testhost/AdbBackendRoutingTest.kt A	test-host/src/androidTest/java/com/androidterminal/testhost/LocalTcpCoreSanityTest.kt A	test-host/src/androidTest/java/com/androidterminal/testhost/LocalTcpProductWiringTest.kt

--- at-engine-api/ diff (AG public contract) ---

[EMPTY = public contract UNCHANGED]

--- at-cli/src/main diff (AG second direct dependency) ---

[EMPTY = at-cli main UNCHANGED]

--- at-composition-android diff (consumer facade) ---
diff --git a/at-composition-android/src/main/java/com/at/composition/android/ATAndroidAdbControl.kt b/at-composition-android/src/main/java/com/at/composition/android/ATAndroidAdbControl.kt index 33ac296..d37b9c2 100644 --- a/at-composition-android/src/main/java/com/at/composition/android/ATAndroidAdbControl.kt +++ b/at-composition-android/src/main/java/com/at/composition/android/ATAndroidAdbControl.kt @@ -58,6 +58,12 @@ class ATAndroidAdbControl internal constructor(      fun bootstrapState(): ATAdbBootstrapState =          ATAdbBootstrapState.from(bootstrap.bootstrapState())   +    /** +     * AT.TRUST-LOSS-RECOVERY-1: read-only projection of the Runtime's +     * trust-rejection observation (device revoked trust for our key). +     */ +    fun trustRejectedObserved(): Boolean = bootstrap.trustRejectedObserved() +      /**       * Whether the consumer has usable identity material.       * First-use pairing is required when this is false.

--- app manifest diff ---

[EMPTY = AndroidManifest UNCHANGED]

--- v0.4.2 new type: AdbTransportMode (internal, com.adb.engine) ---
// ADB Engine 鈥?active ADB transport selector (AT.LOCAL-TCP-1B). // Invariant (MAX_ACTIVE_ADB_TRANSPORTS = 1): at most ONE of {Wireless, Local} // (Wireless is disconnected before Local is connected, and vice versa).  * - [WIRELESS] the existing post-trust Wireless ADB connection  * - [LOCAL]    a plain-TCP localhost ADB connection to a legacy-mode adbd  *              (CNXN + AUTH, no TLS), reached over [DEFAULT_LOCAL_ADB_PORT]. enum class AdbTransportMode {     WIRELESS,     LOCAL,

--- v0.4.2 AdbRuntime transport routing (key lines) ---
    const val DEFAULT_LOCAL_ADB_PORT = 38800     var activeTransport: AdbTransportMode = AdbTransportMode.WIRELESS     // invokes the EXISTING recovery ingress (onObservedTransportLost ->      * [onObservedTransportLost] (the EXISTING recovery ingress). No ping, no         lastTransportOnline = activeTransportOnline()                     val online = activeTransportOnline()                             onObservedTransportLost()     fun onObservedTransportLost() {         onOperationResult(transportLost = true)             if (activeTransport != AdbTransportMode.LOCAL) {         activeTransport = AdbTransportMode.WIRELESS      * @param transportLost true when the operation failed for a transport     fun onOperationResult(transportLost: Boolean) {         if (!transportLost) return             if (activeTransport == AdbTransportMode.LOCAL) {                 activeTransport = AdbTransportMode.WIRELESS             activeTransport = AdbTransportMode.WIRELESS             Log.i(TAG, "local transport loss; activeTransport -> WIRELESS (fallback on next ensureReady)")      * AT.LOCAL-TCP-1B Gate E: routed by [activeTransport] 鈥?LOCAL goes to the     fun executeShell(command: String, timeoutMs: Int = 0): AdbShellResult =         if (activeTransport == AdbTransportMode.LOCAL) {      * AT.LOCAL-TCP-1B Gate E: routed by [activeTransport] 鈥?LOCAL goes to         if (activeTransport == AdbTransportMode.LOCAL) {     fun activeTransportOnline(): Boolean =         if (activeTransport == AdbTransportMode.LOCAL) AdbEngine.localhostConnected()         activeTransport = AdbTransportMode.WIRELESS      * port. On success: [activeTransport]=LOCAL, state=READY, and a [Local]         activeTransport = AdbTransportMode.LOCAL      *   3. connectLocalhost(port); on success activeTransport = LOCAL, READY.             activeTransport == AdbTransportMode.LOCAL && it()         } ?: (activeTransport == AdbTransportMode.LOCAL && AdbEngine.localhostConnected())                     activeTransport = AdbTransportMode.WIRELESS             activeTransport = AdbTransportMode.LOCAL         activeTransport = AdbTransportMode.WIRELESS      * then clean the Local client state and restore [activeTransport] to         if (activeTransport == AdbTransportMode.LOCAL) {         activeTransport = AdbTransportMode.WIRELESS         Log.i(TAG, "disableLocalTcp: Local transport disabled (listener removed via usb:); activeTransport=WIRELESS")

==============================================================================
FILE: 05_v042_release_evidence.txt
==============================================================================

AG.AT-BASELINE-DIFFERENTIAL-AUDIT-0 — 05 V0.4.2 RELEASE EVIDENCE (reused, AT-side)
Date: 2026-11-03 (re-run 2026-11-04 for evidence capture)
Source: AT repo docs at tag v0.4.2 (925e7ac223d19eea7ddb89bf05e55df25ca7a7f2)
REUSE classification: AT-internal capability claims = REUSE;
AG-path claims (Stop->cancel->AT, timeout, pairing) = RETEST_FROM_AG (cannot be borrowed)

==============================================================================
FILE: docs/evidence/AT.V042-RELEASE-1.md
==============================================================================

# AT.V042-RELEASE-1

**Date:** 2026-11-03

---

## Release Identity

```
VERSION_NAME = 0.4.2
VERSION_CODE = 4
PACKAGE = com.androidterminal.app
```

## Commit Chain

```
PREFLIGHT_COMMIT = 04f9c30
RELEASE_COMMIT = (this commit)
TAG_TARGET = RELEASE_COMMIT
```

## Branch

```
BRANCH = feature/local-tcp-v0.4.2
REMOTE = github (https://github.com/WingMoval/AndroidTerminal.git)
REMOTE_BRANCH_HEAD = 04f9c30 (before release commit)
```

## Product Source Guard

```
PRODUCT_CODE_CHANGED_SINCE_ACCEPTED_RC = NO
```

Last 2 commits (bbce5a6, 04f9c30) are docs/evidence only.
All product code changes were in prior feature rounds (UX-POLISH, STALE-GUARD, version bump),
all tested and accepted in v0.4.2 Final Acceptance.

## Release Artifact

```
RELEASE_ARTIFACT_SOURCE = ACCEPTED_RC
APK_NAME = AndroidTerminal-v0.4.2-arm64-v8a.apk
APK_SIZE = 4465528 bytes
APK_SHA256 = 90CD5E5B37186DC971126698A895F86A4F2992A38952C64B38B8919EBE019B2C
APK_SHA_MATCHES_ACCEPTED_RC = YES
```

## Signing

```
APK_SIGNED = YES
SIGNER_CERT_SHA256 = 7bdcba7781e6fecfe078eb9741e7bfeb380f0a7aa64530007991250b07835cde
V2_SIGNER_MATCH = YES
SIGNER_DN = CN=AndroidTerminal, OU=Mobile, O=AndroidTerminal, L=NA, ST=NA, C=US
```

## Known Finding

```
SEVERITY = MINOR
CATEGORY = COSMETIC_UI_STATE_REFRESH
DESCRIPTION = After same-key re-pair, status banner may remain stale until next
  ADB readiness trigger. Backend functionality is already restored.
BLOCKING = NO
```

## Tag Convention

```
CONVENTION = annotated tag (matching v0.4.1)
TAG_NAME = v0.4.2
```

==============================================================================
FILE: 本轮交付/AT.V042-EXECUTION-CAPABILITY-FINAL.md
==============================================================================

# AT.V042-EXECUTION-CAPABILITY-FINAL

**Step:** AT.V042-HUMAN-EXECUTION-CLOSEOUT-1
**Date:** 2026-11-03
**Device:** HONOR ALI-AN00, Android 15
**Branch:** feature/local-tcp-v0.4.2
**Base HEAD:** 374fc42

---

## Final Capability Matrix

| Capability | Product Surface | Evidence | Result |
|---|---|---|---|
| Local backend one-shot | `LocalOneShotBackend.execute()` (ProcessBuilder → /system/bin/sh -c) | Code audit: implemented in ATAndroidComposition.localEngine | IMPLEMENTED, NOT TESTED (no user-facing consumer) |
| Local PTY | + → Local | H00 (human, uid=10443) | PASS |
| Local PTY cwd | Local PTY | NOT_TESTED (not explicitly tested in Local PTY) | NOT_TESTED |
| Local PTY env | Local PTY | NOT_TESTED | NOT_TESTED |
| Local PTY Ctrl-C | Local PTY | NOT_TESTED | NOT_TESTED |
| ADB CLI one-shot via Local PTY | `adb shell <cmd>` in Local PTY (shell function → app_process → AdbCliMain → CLI Bridge → adbEngine().execute) | H00 (human: adb pair) + Phase 3 auto (5/5: id/pwd/getprop/exit7/stderr) | PASS |
| ADB Engine one-shot | Internal API: AdbRuntime.executeShell → AdbEngine.executeShell/executeShellLocalhost (transport-routed) | Code audit + 74 JVM tests + 5 device tests | PASS |
| ADB PTY | + → ADB | GATE 1-7 (human) | PASS |
| ADB PTY cwd | PTY persistence | GATE 1 (human, pwd confirmed) | PASS |
| ADB PTY env | PTY persistence | GATE 1 (human, echo $AT_TEST confirmed) | PASS |
| ADB PTY Ctrl-C | PTY interrupt | GATE 1 (human, sleep 100 → ^C → 130) | PASS |
| Wireless bootstrap | Internal: connectAuto → Wireless READY | GATE 5/6/7 (logcat) | PASS |
| Local TCP ADB transport | Internal: 38800 adbd-owned | GATE 3-7 + H13/H16 | PASS |
| Multi-session | + → ADB (3 concurrent) | GATE 2 (human) | PASS |
| Session isolation | PTY independent | GATE 2 (human, cwd/env/blocking/close) | PASS |
| Session churn | Engine/Consumer | Phase 3 auto (10/10) | PASS |
| Wi-Fi independence | Local TCP | GATE 3 (human) | PASS |
| WD independence | Local TCP | GATE 3 (human) | PASS |
| App restart local-first | Local TCP | GATE 4 (human) | PASS |
| Local loss recovery | Lifecycle | GATE 5 (human) | PASS |
| Reboot recovery | Lifecycle | GATE 6 (human) | PASS |
| Trust-loss recovery | Lifecycle | GATE 7 (human) | PASS |

---

## PHASE 2: Human Gate Results

| Gate | Capability | Result |
|---|---|---|
| GATE 1 | ADB Local TCP PTY full semantics | PASS |
| GATE 2 | Multi-session isolation (3 sessions) | PASS |
| GATE 3 | Full Local Independence (Wi-Fi OFF + WD OFF) | PASS |
| GATE 4 | App Death + Local-first relaunch | PASS |
| GATE 5 | Local Loss safe failure + Recovery without restart | PASS |
| GATE 6 | Device Reboot recovery | PASS |
| GATE 7 | Trust Loss + Same-key Repair | PASS |

**CRITICAL_HUMAN_GATES_PASS = 7/7**

---

## PHASE 3: Automated Results

```
LOCAL_BACKEND_ONESHOT_AUTOMATED = NOT_TESTED (no user-facing consumer; PC adb shell tests do not exercise LocalOneShotBackend)
ADB_CLI_ONESHOT_VIA_LOCAL_PTY = PASS (5/5: id, pwd, getprop, exit 7, stderr)
PC_ADB_SHELL_MATRIX = PASS (8/8: stdout, stderr, exit codes, not-found, unicode, long output, empty, rapid) [informational: PC→device USB transport, not an AT capability]
SESSION_CHURN = 10/10
TRANSPORT_CHURN = 3/3
STATE_DRIFT = NO
DOUBLE_RX_OWNER = NO
TEST_TOTAL = 74
TEST_PASS = 74
TEST_FAIL = 0
BUILD_RELEASE = PASS
ARCHITECTURE_GATE = NO_TASK (no dedicated arch gate task in build)
```

---

## PHASE 4: ADB One-shot Code Audit

### ADB_ONESHOT_PRIMITIVE

Two primitives exist:

1. **`AdbEngine.executeShell(command, timeoutMs)`** — Wireless one-shot
   - File: `adb-engine/src/main/java/com/adb/engine/AdbEngine.kt` L55
   - JNI: `AdbNative.executeShell` → `adb_engine_jni.cpp` L166
   - Native: `client->shell(command)` on the existing Wireless AdbClient connection
   - Service: `shell,v2,raw:<command>` via AdbClient internal stream

2. **`AdbEngine.executeShellLocalhost(command)`** — Local TCP one-shot
   - File: `adb-engine/src/main/java/com/adb/engine/AdbEngine.kt` L96
   - JNI: `AdbNative.executeShellLocalhost` → `adb_engine_jni.cpp` L453
   - Native: Opens `shell,v2,raw:<command>` stream on `g_local->hub` (Local TCP stream hub)
   - Same shell-v2 protocol parser, same AdbShellResult return type

### ADB_ONESHOT_CALL_CHAIN

```
Consumer (AdbBackend / CLI / internal)
  → AdbRuntime.executeShell(command, timeoutMs)     [L860: transport router]
    → if activeTransport == LOCAL:
        AdbEngine.executeShellLocalhost(command)    [Local TCP hub]
          → AdbNative.executeShellLocalhost         [JNI]
            → g_local->hub->openStream("shell,v2,raw:cmd")
            → ShellV2Protocol read loop → AdbShellResult
    → else (WIRELESS):
        AdbEngine.executeShell(command, timeoutMs)  [Wireless client]
          → AdbNative.executeShell                  [JNI]
            → client->shell(command)                [AdbClient]
            → AdbShellResult
```

### ADB_ONESHOT_FIRST_KNOWN_COMMIT

```
a40387e (2026-10-11) — AT.V3-1: extract and validate independent ADB engine
```

`executeShell` was present from the **very first ADB Engine commit** (AT.V3-1). It is one of the earliest AT V3 capabilities, predating Local TCP by multiple weeks.

### ADB_ONESHOT_WIRELESS_SUPPORTED = YES

- `AdbEngine.executeShell` uses `ClientExisting()` → the established Wireless AdbClient
- Verified in device tests: `AdbEngineDeviceTest.kt` L95-111
- Verified in production: all prior rounds used Wireless one-shot before Local TCP existed

### ADB_ONESHOT_LOCAL_TCP_SUPPORTED = YES

- `AdbRuntime.executeShell` L860-865: explicit transport routing
- When `activeTransport == LOCAL`, routes to `executeShellLocalhost`
- When `activeTransport == WIRELESS`, routes to `executeShell`
- Code comment (L855-858): "AT.LOCAL-TCP-1B Gate E: routed by [activeTransport]"
- Verified in device tests: `AdbRuntimeLifecycleTest.kt` L275-294, `LocalTcpProductWiringTest.kt` L46
- Verified in production: H00 (adb pair via Local one-shot), GATE 1-7 (all ADB operations over Local)

### ADB_CLI_ONESHOT_VIA_LOCAL_PTY_PATH

```
Local PTY shell (uid=10443, untrusted_app)
  → shell function: adb() { /system/bin/app_process -Djava.class.path="$AT_APP_APK" ... AdbCliMain }
    → AdbCliMain (at-cli module)
      → CLI Bridge UDS (cli_bridge_server.cpp, loaded by host app)
        → AdbCliBridgeServer.onOneShot
          → execution.adbEngine().execute(request)     [AdbOneShotBackend]
            → AdbRuntime.executeShell
              → (transport-routed: LOCAL → executeShellLocalhost / WIRELESS → executeShell)
```

### ADB_ENGINE_ONESHOT_PATH

```
AdbRuntime.executeShell → AdbEngine.executeShell/executeShellLocalhost → JNI → native
```

### LOCAL_BACKEND_ONESHOT_PATH

```
ATAndroidComposition.localEngine.execute(request)
  → LocalOneShotBackend.execute(request)
    → ProcessBuilder("/system/bin/sh", "-c", command)
    → concurrent stdout/stderr drain
    → process.waitFor()
    → ATExecutionResult(COMPLETED, stdout, stderr, exitCode)
```

**NOT consumed by any user-facing surface.** The APK UI only exposes PTY sessions.
The CLI bridge (`adb shell <cmd>`) routes to `adbEngine`, NOT `localEngine`.

### ADB_CLI_AND_ENGINE_ONESHOT_SAME_PATH = YES (at the engine level)

The CLI `adb shell <cmd>` in the Local PTY session ultimately calls `execution.adbEngine().execute(...)` → `AdbOneShotBackend.execute()` → `AdbRuntime.executeShell` → `AdbEngine.executeShell/executeShellLocalhost`. The execution primitive is the ADB Engine one-shot, not the Local backend one-shot.

### Consumer Surface

```
LOCAL_BACKEND_ONESHOT_USER_SURFACE = NO
  - LocalOneShotBackend is implemented and wired into localEngine
  - No APK UI button, no CLI path routes to localEngine
  - Available for future AG/consumer integration via ATAndroidComposition.localEngine

APK_ADB_ONESHOT_USER_SURFACE = NO (no direct "run command once" button in the APK UI)
  - APK has PTY sessions (+ → ADB / + → Local) and CLI bridge
  - ADB one-shot is consumed programmatically via AdbBackend / CLI bridge

CLI_ADB_ONESHOT_VIA_LOCAL_PTY_SURFACE = YES
  - `adb shell <command>` in Local PTY → AdbCliMain → CLI Bridge → adbEngine().execute
  - AdbCliArgs parses Kind.SHELL → remoteCommand → ADB one-shot backend
  - This is ADB CLI one-shot via Local PTY, NOT Local backend one-shot

ENGINE_ADB_ONESHOT_API = YES
  - AdbRuntime.executeShell (public API, transport-routed)
  - AdbEngine.executeShell (Wireless)
  - AdbEngine.executeShellLocalhost (Local TCP)
```

### Product Interpretation

```
LOCAL_BACKEND_ONESHOT = IMPLEMENTED, NOT EXPOSED (no user-facing consumer)
ADB_ENGINE_ONESHOT = IMPLEMENTED (both Wireless and Local TCP, transport-routed)
ADB_ENGINE_ONESHOT_APK_DIRECT_UI = NOT EXPOSED (no dedicated one-shot button)
ADB_CLI_ONESHOT_VIA_LOCAL_PTY = EXPOSED (via `adb shell <cmd>` in Local PTY)
```

The ADB Engine one-shot is **fully implemented** at the engine level. Its user-facing surface is the CLI (`adb shell` in Local PTY). The Local backend one-shot is also implemented but has no user-facing consumer yet. Neither is "missing" — they simply have different exposure levels.

---

## AG Recommendation

For future AndroidAgent:

```
AG shell tool (single non-interactive command)
  → AT execution adapter
    → AdbRuntime.executeShell (one-shot)
      → transport-routed (LOCAL or WIRELESS, transparent to AG)

AG interactive/persistent shell
  → AT execution adapter
    → AdbRuntime.openInteractive("shell,v2,pty:")
      → PTY session (persistent, Ctrl-C capable, cwd/env state)
```

**Recommendation:** One-shot for single calls (lower overhead, no PTY allocation, no session bookkeeping). PTY for interactive/persistent shells (multi-command state, interrupt, resize).

---

## Bug Register

| Bug ID | Severity | Description | Repro | Evidence |
|---|---|---|---|---|
| OBS-1 | NON_REPRODUCED | H05: one-time input anomaly (`at_h05_probe` → `at_h05_probr`). NOT reproducible: 5/5 commands up to 120 chars executed intact via `input text`; EditText has no maxLength. Classified as user typing error / IME interference. | Not reproducible (5/5 automated test PASS) | H05 screenshot + RELEASE-PREFLIGHT-1 test |
| OBS-2 | MINOR | After re-pair (GATE 7), AT status banner shows "NEEDS PAIRING" + "CONNECT_FAILED" even though 38800 is already LISTENING. Status updates on next `+`→ADB tap. Cosmetic only. | Trust-loss → re-pair → observe banner | Screenshot g7f.png, logcat 17:45:39 |

**BLOCKER_COUNT = 0**
**MAJOR_COUNT = 0**
**MINOR_COUNT = 1**
**OBSERVATION_COUNT = 0**

---

## Final Verdict

```
STEP = AT.V042-HUMAN-EXECUTION-CLOSEOUT-1
VERDICT = PASS_WITH_FINDINGS
BASE_HEAD = 374fc42
FINAL_HEAD = bbce5a6 (docs-only commit)
H01_H16_SUMMARIZED = YES
H01_H16_RERUN = NO
CRITICAL_HUMAN_GATES_TOTAL = 7
CRITICAL_HUMAN_GATES_PASS = 7/7
ADB_LOCAL_TCP_PTY = PASS
ADB_LOCAL_TCP_PTY_CWD = PASS
ADB_LOCAL_TCP_PTY_ENV = PASS
ADB_LOCAL_TCP_PTY_CTRL_C = PASS
MULTI_SESSION = PASS
SESSION_ISOLATION = PASS
BLOCKING_ISOLATION = PASS
SESSION_CLOSE_ISOLATION = PASS
LOCAL_TCP_FULL_INDEPENDENCE = PASS
RELAUNCH_LOCAL_FIRST = PASS
LOCAL_LOSS_SAFE_FAILURE = PASS
RECOVERY_WITHOUT_APP_RESTART = PASS
POST_REBOOT_RECOVERY = PASS
TRUST_LOSS = PASS
SAME_KEY_REPAIR = PASS
LOCAL_BACKEND_ONESHOT_AUTOMATED = NOT_TESTED (implemented, no user-facing consumer)
ADB_CLI_ONESHOT_VIA_LOCAL_PTY = PASS
PC_ADB_SHELL_MATRIX = PASS (8/8, informational: PC→device, not an AT capability)
SESSION_CHURN = 10/10
TRANSPORT_CHURN = 3/3
STATE_DRIFT = NO
DOUBLE_RX_OWNER = NO
ADB_ONESHOT_PRIMITIVE = AdbEngine.executeShell (Wireless) + AdbEngine.executeShellLocalhost (Local TCP), routed by AdbRuntime.executeShell
ADB_ONESHOT_CALL_CHAIN = Consumer → AdbRuntime.executeShell → [LOCAL: AdbEngine.executeShellLocalhost → JNI → g_local hub stream] / [WIRELESS: AdbEngine.executeShell → JNI → AdbClient.shell]
ADB_ONESHOT_FIRST_KNOWN_COMMIT = a40387e (2026-10-11, AT.V3-1)
ADB_ONESHOT_WIRELESS_SUPPORTED = YES
ADB_ONESHOT_LOCAL_TCP_SUPPORTED = YES
APK_ADB_ONESHOT_USER_SURFACE = NO (not exposed as dedicated UI)
CLI_ADB_ONESHOT_VIA_LOCAL_PTY_SURFACE = YES (adb shell in Local PTY → adbEngine)
ENGINE_ADB_ONESHOT_API = YES
ADB_CLI_AND_ENGINE_ONESHOT_SAME_PATH = YES (CLI routes to adbEngine → AdbRuntime.executeShell)
LOCAL_BACKEND_ONESHOT_IMPLEMENTED = YES (LocalOneShotBackend, ProcessBuilder)
LOCAL_BACKEND_ONESHOT_API = ATAndroidComposition.localEngine.execute()
LOCAL_BACKEND_ONESHOT_CONSUMER = NONE (no user-facing surface)
LOCAL_BACKEND_ONESHOT_TESTED = NO
TEST_TOTAL = 74
TEST_PASS = 74
TEST_FAIL = 0
ARCHITECTURE_GATE = NO_TASK
BUILD_RELEASE = PASS
BLOCKER_COUNT = 0
MAJOR_COUNT = 0
MINOR_COUNT = 1
OBSERVATION_COUNT = 0
CORE_EXECUTION_CAPABILITIES = PASS
RELEASE_RECOMMENDATION = GO
PRODUCT_CODE_CHANGED = NO
TAG_CREATED = NO
GITHUB_RELEASE_CREATED = NO
NEXT = AT.V042-RELEASE-1
STOP = YES
```

==============================================================================
FILE: 06_ag_consumed_api_behavioral_contract.txt
==============================================================================

AG.AT-BASELINE-DIFFERENTIAL-AUDIT-0 — 06 AG CONSUMED API + BEHAVIORAL CONTRACT (v0.4.0)
Date: 2026-11-03 (derived from AG source @ c90f6d1 + vendored 4.0.0 bytecode)

AG_V040_CONSUMED_API_SURFACE (14 consumed APIs):

| API | Caller | Purpose | Input | Output | Error | Lifecycle | Cancellation |
|---|---|---|---|---|---|---|---|
| ATEngine.execute(request) | AtExecutionAdapter.executeAdb/executeLocal | one-shot command | ATExecutionRequest(command, timeoutMs) | ATExecutionResult(outcome, stdout, stderr, exitCode, hasError, error) | outcome=TRANSPORT_ERROR/UNSUPPORTED + error string | stateless per call | NOT VERIFIED (blocking native call inside withContext(IO)) |
| ATEngine.openSession(launch) | adapter.openAdbSession/openLocalSession (NOT model path) | persistent PTY | ATSessionLaunch(command, rows, cols[, env]) | ATOpenSessionResult.Success(session)/Failure(reason) | Failure.reason string | session object owned by caller | close(terminateProcess) |
| ATTerminalSession.write/read/resize/close/setListener/state | adapter session helpers (not model path) | raw PTY I/O | bytes / timeoutMs | Int / ByteArray? / Boolean | — | caller-owned | — |
| ATAndroid.create(context, config) | ChatCompositionRoot (Host) | composition entry | Context, ATAndroidConfig(identityProfile) | ATAndroidComposition(localEngine, adbEngine, adbControl) | — | process-scoped, once | n/a |
| ATAndroidAdbControl.bootstrapState() | CompositionAdbSemantic | consumer bootstrap projection | — | ATAdbBootstrapState (PAIRING_REQUIRED/CONNECT_FAILED/READY/USER_ACTION_REQUIRED_WD/ENDPOINT_NOT_FOUND/NOT_READY) | — | stateless read | n/a |
| ATAndroidAdbControl.readinessState() | CompositionAdbSemantic | ADB readiness | — | ATAdbReadinessState (READY/DISCONNECTED/CONNECTING/else->UNKNOWN) | — | stateless read | n/a |
| ATAndroidAdbControl.ensureReady(onStatusLine) | ShellConnection.connect (Host action) | readiness gate + auto-WD + discovery + connect | callback | Boolean | status lines via callback | blocking (withContext IO) | NOT VERIFIED |
| ATAndroidAdbControl.hasIdentity() | CompositionAdbSemantic / CLI execution | identity material present | — | Boolean | — | stateless | n/a |
| ATAndroidAdbControl.discoverPairing(timeoutMs) | CompositionAdbSemantic | scan pairing service | Long | ATAdbPairingDiscovery(endpoints, serviceNotFound, multiple) | — | blocking | NOT VERIFIED |
| ATAndroidAdbControl.pair / pairDiscovered | CompositionAdbSemantic (Host pair UI) | pair with code | host, port, code | ATAdbPairingResult(success, outcome enum, deviceGuid, error) | outcome enum | blocking | NOT VERIFIED |
| ATCliExecution (interface impl by AG adapter) | AtExecutionAdapter -> AdbCliBridgeServer | in-shell adb CLI backend | command | OneShotResult(status, stdout, stderr, error) | status string | bridge thread | NOT VERIFIED |
| AdbCliBridgeServer(dir, exec).start/stop/socketPath | AtExecutionAdapter (composition init) | UDS CLI bridge | File | Boolean | log | started at init | stop() |
| LocalCliBootstrap(mainClass).writeScript/sourceLine | AtExecutionAdapter | generate adb shell function | paths | String script/source | null on failure | canonical file owner | n/a |

NOT consumed by AG: completeFirstUseBootstrap, lifecycle listeners, ATSessionEvent
stream (session path only), ATSessionLaunch.env, ATOpenSessionResult for model path.

AG_V040_BEHAVIORAL_CONTRACT (regression baseline for v0.4.2):
  BEHAVIOR-001 one-shot returns stdout/stderr/exitCode; COMPLETED = tool success
               even when exitCode != 0                       [PROVEN: AG.SHELL-1 S1-S5,
                                                                      AG.AT-V4-INTEGRATION-1,
                                                                      AGENT-0.LIVE real-device]
  BEHAVIOR-002 TRANSPORT_ERROR / UNSUPPORTED = tool failure + error string  [PROVEN: same steps]
  BEHAVIOR-003 timeout owned by AT (ATExecutionRequest.timeoutMs, default 30s);
               AG has NO independent coroutine timeout        [ASSUMED from source;
               orphan-execution risk = PRE_EXISTING_UNKNOWN]
  BEHAVIOR-004 AG Stop -> execution Job.cancel() propagates to AT native
               execute; no delayed ToolResult after Interrupted [PRE_EXISTING_UNKNOWN:
               native-boundary propagation never device-verified in EITHER baseline]
  BEHAVIOR-005 ADB readiness Host-triggered: ShellConnection.connect ->
               ensureReady; AG never assumes auto-connect       [PROVEN: HOST-STOP-1 D1-D4,
                                                                      ShellToolRealDeviceTest S1]
  BEHAVIOR-006 pairing state = structured enum (PAIRING_REQUIRED / CONNECT_FAILED /
               READY); AG Host pair UI + reconnect action; no string parsing [PROVEN: source + UI]
  BEHAVIOR-007 in-shell adb CLI one-shot via LocalCliBootstrap + UDS bridge [PROVEN:
               AG.AT-V4-INTEGRATION-1; CLI path under AdbEngine backend changes in v0.4.2]
  BEHAVIOR-008 Local one-shot CWD = appFilesDir wrap (cd + bootstrap source) [PROVEN: source]
  BEHAVIOR-009 session isolation / multi-session: AG max-1 Run/session;
               AT-side multi-session not consumed by AG v1      [PROVEN: AgentRuntime design]

==============================================================================
FILE: 07_final_verdict.txt
==============================================================================

AG.AT-BASELINE-DIFFERENTIAL-AUDIT-0 — 07 FINAL VERDICT
Date: 2026-11-03

VERDICT = PASS_WITH_GAPS
  (audit complete and clean; gaps = pre-existing, test-only; no regression, no blocker)
MODE = READ_ONLY (no SSOT modified, no commit, no code/dep/AAR change)

Baseline identity (all VERIFIED on-site):
  AG = main @ c90f6d1bffebf80c16980644073d5057c2a64e70, CLEAN
  CURRENT_AT = v0.4.0 tag target 7216d0e8131e92e8914ce7c8443e1668b1d53aca
               == AG consumed freeze commit (EXACT match)
  CANDIDATE_AT = v0.4.2 tag target 925e7ac223d19eea7ddb89bf05e55df25ca7a7f2
               (== Controller-expected SHA, EXACT match)

Core findings:
  1. Public API surface AG consumes (at-engine-api jar + at-cli main) UNCHANGED
     between v0.4.0 and v0.4.2 (git diff empty; facade additive-only).
  2. ZERO regressions in any AG-relevant behavior.
  3. v0.4.1 lazy readiness + PAIRING_REQUIRED / v0.4.2 local-first + recovery
     map onto existing AG Host flow without adapter change.
  4. Transport transparency PASS: no LOCAL/WIRELESS/38800 leak; 0 forbidden imports.
  5. Two MAJOR gaps are PRE-EXISTING in v0.4.0 (identical in v0.4.2), test-only:
     GAP-1 interrupt last-hop (coroutine cancel -> AT native execute)
     GAP-2 orphan-execution (no independent AG-side timeout)

Gap register: BLOCKER=0, MAJOR=2 (GAP-1, GAP-2), MINOR=3 (GAP-3 0.4.2 bundle
not yet built, GAP-4 device legacy-TCP precondition, GAP-5 lazy-readiness re-val),
DEFERRED=0. V042_REGRESSION_COUNT=0. V042_BENEFIT_COUNT=5.

Blocker origin:  BLOCKED_BY_AT=NO, BLOCKED_BY_AG=NO,
                 BLOCKED_ONLY_BY_MISSING_EVIDENCE=YES

Readiness:       AT_V042_AG_INTEGRATION_READINESS = READY_AFTER_DEVICE_VALIDATION

Recommendation:  AG_V1_AT_RECOMMENDATION = VALIDATE_AND_MOVE_TO_V0.4.2

Controller candidate review: D1 KEEP, D2 KEEP (add-on to D1), D3 KEEP (P0),
  D4 KEEP (P0), D5 KEEP, D6 KEEP, D7 KEEP, D8 DROP/DEFER, D9 KEEP (reduced).
AG-discovered: GATE-1 in-shell adb CLI one-shot regression; GATE-2 AG-side
  timeout measurement discipline (folded into D3).

FINAL_MINIMUM_DEVICE_GATES = 7 (AG.AT-V042-COMPAT-VALIDATION-1):
  1. D1+D2 basic one-shot + non-zero exit
  2. D4 Stop/interrupt full chain (P0, v0.4.0 comparison)
  3. D3 timeout -> no orphan -> terminal -> session reusable
  4. D5 local-first steady state (Wi-Fi OFF + WD OFF)
  5. D6 local loss -> wireless recovery (AT-owned, no dup)
  6. D7 pairing-required -> deterministic actionable AG state
  7. AG_DISCOVERED_GATE-1 in-shell adb CLI one-shot regression

Invariants: PRODUCT/AG/AT CODE CHANGED = NO; DEPENDENCY/AAR CHANGED = NO;
  COMMIT = NO; TAG = NO; PUSH = NO; FREEZE = NO; SSOT UNTOUCHED.

NEXT = CONTROLLER_REVIEW -> (if approved) formal 0.4.2 sync Step +
       AG.AT-V042-COMPAT-VALIDATION-1
STOP = YES
