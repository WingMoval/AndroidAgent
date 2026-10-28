# AG.UI-BASELINE-AUDIT-1 — Consolidated Evidence

> 本文件由 `docs/evidence/ag_ui_baseline_audit_1/` 的 16 个证据文件（00-15）合并而成，
> 便于单文件查阅。各小节标题保留原始文件编号与主题。内容与各分文件一致。

---

## 〔00〕 00_git_baseline.txt

AG.UI-BASELINE-AUDIT-1 鈥?Git Baseline

BRANCH = main
START_HEAD = b7dbc0491eb1bcb0fb07f0daf7f8a4919a9e67bd
FINAL_HEAD = b7dbc0491eb1bcb0fb07f0daf7f8a4919a9e67bd (unchanged; COMMIT = NO this round)

FREEZE_NAME = AG.POST-I-CORE-FREEZE-V1
FREEZE_TAG = AG.POST-I-CORE-FREEZE-V1
FREEZE_TAG_TARGET = f640ed07f655856888c11f3fb3575e6f84106f7c
FREEZE_MATCH = YES (verified via `git rev-parse AG.POST-I-CORE-FREEZE-V1^{}` = expected)
FREEZE_TAG_MOVED = NO

REMOTE = NONE (no remote configured; PUSH = N/A)

WORKTREE_BEFORE = 1 untracked line:
  ?? docs/design/AndroidAgent_V2_VNext_UI_璁捐鍩虹嚎.md   (USER_OWNED_DESIGN_SSOT)
WORKTREE_AFTER = 1 untracked (design SSOT) + this round's audit docs + evidence
  (audit docs + evidence are TASK_OWNED additions; the design SSOT is untouched)

WORKTREE_CLASSIFICATION:
  USER_OWNED_DESIGN_SSOT = docs/design/AndroidAgent_V2_VNext_UI_璁捐鍩虹嚎.md (NOT modified)
  TASK_OWNED (new)       = docs/audit/AG.UI-BASELINE-AUDIT-1.md + docs/evidence/ag_ui_baseline_audit_1/*
  PRE_EXISTING / UNKNOWN = NONE (no other dirty changes)

Git log (top 3):
  b7dbc04 docs: normalize post-freeze live SSOT
  f640ed0 freeze: establish post-I pre-UI AG core baseline   <- FREEZE
  a6eea61 AG.AT-FULL-SUBSYSTEM-INTEGRATION-0 ...

HEAD is AFTER the freeze (b7dbc04 > f640ed0) 鈥?EXPECTED post-freeze state.
The freeze tag remains the authoritative immutable Pre-UI core baseline.


---

## 〔01〕 01_design_ssot.txt

AG.UI-BASELINE-AUDIT-1 鈥?VNext Design SSOT

VNEXT_DESIGN_SSOT = docs/design/AndroidAgent_V2_VNext_UI_璁捐鍩虹嚎.md
LOCATED = YES (actual path resolved, not assumed)
SIZE = 592 lines
VNEXT_DESIGN_SSOT_MODIFIED = NO (USER_OWNED_DESIGN_SSOT; read-only this round)

The file is the authoritative VNext UI TARGET DESIGN. This audit maps CURRENT
code to that target. Key target sections used as audit anchors:

  搂1  鐗堟湰涓庡畾浣?鈥?缁х画 V2锛沀I 涓栦唬 VNext锛涙灦鏋勪笉鍙橈紱AG Core/Runtime/Tool/Provider/
       AT/TopicSession 鍐荤粨锛涙湰杞紨杩?Host/Presentation/UI/Transcript/浜や簰
  搂2  浜у搧褰㈡€?鈥?娴呰壊浼樺厛 Codex-like Agent Workspace锛堥潪鑱婂ぉ App/缁堢妯℃嫙鍣級
  搂3  瑙嗚鍩虹嚎 鈥?Light Theme锛沇orkspace 鐏扮櫧 / Card 鐧?/ 鐣欑櫧+缁嗚竟妗?鍦嗚+鍒嗛殧绾?  搂4  涓荤晫闈㈠竷灞€ 鈥?Header 鍥哄畾 / Conversation Viewport 鍥哄畾 / Composer 鍥哄畾搴曢儴
  搂5  Conversation Viewport 鈥?鑷劧绾靛悜婊氬姩锛涘簳閮ㄨ嚜鍔ㄨ窡闅?streaming锛涗笂婊戜笉寮哄埗鎷夊洖
  搂6  Transcript 灞傜骇 鈥?TopicSession > User Turn/Run Group > User/Thought/Assistant/Tool
       Turn Group = Presentation grouping锛堜笉鍒涘缓绗簩濂楅鍩熸ā鍨嬶級
  搂7  涓€绾ц瑙夊垎闅?鈥?User/Thought/Assistant 鍓嶅垎闅旂嚎锛汿ool/Result 灞炲綋鍓?execution
  搂8  Card 浣撶郴 鈥?UserCard/ThoughtCard/AssistantCard/ToolExecutionCard/FailureCard/
       InterruptedCard锛汸resentation state锛坋xpanded/collapsed/...锛変笉鍏?SSOT
  搂9-15 Card 鎿嶄綔鐭╅樀 鈥?Copy/Select/Retry/Edit&Resend/Collapse 绛?  搂16-18 Composer 鈥?锛婥ontext/File銆両nput銆丮odel路effort銆丼ubmit/Stop
  搂19  Submit/Stop 鈥?Idle:鈫?/ Running:鈻狅紱UI=Stop / AG=Interrupt / Runtime=cancellation
  搂20-21 Sidebar + Session Browser 鈥?New/Switch/Current highlight/Rename/Today-Yesterday
  搂22  ADB 鐘舵€?鈥?Header ADB鈼?/ Sidebar Connected-Reconnect
  搂23  Provider/API Key 鈥?璁剧疆椤碉紙涓嶅父椹讳富鐣岄潰锛?  搂24  Debug 閫氶亾 鈥?鐜版湁 DebugBridge 淇濈暀澶嶇敤涓嶉噸寤猴紱UI/Debug 鍚屼竴 action path
  搂27  V1 蹇呴』瀹屾垚 / 搂28 V1 鏄庣‘涓嶅仛 / 搂29 Architecture Guard
  搂31  寤鸿 UI 闃舵 鈥?AUDIT -> SHELL -> TRANSCRIPT-CARDS -> CONTROLS -> DEVICE -> RELEASE
  搂32  V3 寮€鍚潯浠?鈥?CREATE_ANDROIDAGENT_V3 = NO

Deferred (NOT V1 blockers, per 搂28 + 搂25/搂26): Grok turn switching, Focus View,
pinch zoom, 澶嶆潅鍔ㄧ敾, 鐪熸 Redo/Unrevert, 浠绘剰鍘嗗彶 Fork, Checkpoint UI, Export,
澶嶆潅 syntax highlighting, Voice, 鏂?Debug protocol, 绗簩濂?Debug runtime, 鏋舵瀯閲嶆瀯.


---

## 〔02〕 02_ui_surface_inventory.txt

AG.UI-BASELINE-AUDIT-1 鈥?Current UI Surface Inventory

app/src/main (full inventory):
  AndroidManifest.xml
  java/com/androidagent/app/AgentInstructions.kt        (Host-owned Agent instructions, frozen)
  java/com/androidagent/app/ChatActivity.kt             (594 lines; the ONLY Activity)
  java/com/androidagent/app/ChatCompositionRoot.kt      (205 lines; Composition Root)
  java/com/androidagent/app/CompositionAdbSemantic.kt   (82 lines; Host ADB semantic bridge)
  java/com/androidagent/app/DeviceLocalProviderConfig.kt(150 lines; ProviderNode + loader)
  java/com/androidagent/app/HostStatusModel.kt          (90 lines; pure status mapping)
  java/com/androidagent/app/SessionHolder.kt            (337 lines; process-scoped Session)
  java/com/androidagent/app/TranscriptModel.kt          (428 lines; Block model + render)
  java/com/androidagent/app/debug/DebugBridge.kt        (544 lines; TCP JSONL server)
  java/com/androidagent/app/debug/DebugProtocol.kt      (382 lines; pure protocol)
  java/com/androidagent/app/persistence/Daos.kt         (Room DAOs)
  java/com/androidagent/app/persistence/Entities.kt     (Room entities)
  java/com/androidagent/app/persistence/RoomTopicSessionStore.kt
  java/com/androidagent/app/persistence/StoreMessageDurability.kt
  java/com/androidagent/app/persistence/TopicSessionDatabase.kt
  java/com/androidagent/app/provider/AndroidOpenAiHttpTransport.kt
  java/com/androidagent/app/trace/AgentTrace.kt
  java/com/androidagent/app/trace/AgentTraceCollector.kt
  java/com/androidagent/app/trace/TraceFormatter.kt
  res/layout/activity_chat.xml                          (124 lines; the ONLY layout)
  res/values/strings.xml                                (1 string: app_name)
  res/values/themes.xml                                 (1 style: Theme.AndroidAgent)

UI SURFACE FACTS:
  Activities        = 1 (ChatActivity, LAUNCHER + MAIN)
  Fragments         = 0
  Compose           = 0 (no Jetpack Compose)
  XML layouts       = 1 (activity_chat.xml)
  RecyclerView      = NONE
  ScrollView        = 1 (root; whole-page scroll)
  ViewBinding       = NONE (findViewById-style direct references)
  DataBinding       = NONE
  custom Views      = 0
  theme             = Theme.AppCompat.DayNight.DarkActionBar (NOT light-priority)
  launcher icon     = @android:drawable/sym_def_app_icon (DEFAULT system icon)

The app is a single-Activity, single-layout, XML/View-based host. The entire
chat surface (provider selector + ADB pair + status + transcript + input +
Send/Stop/NewSession buttons) is one vertically-stacked LinearLayout inside a
whole-page ScrollView.


---

## 〔03〕 03_ui_tech_stack.txt

AG.UI-BASELINE-AUDIT-1 鈥?UI Tech Stack Verdict

UI_TECH_STACK = XML/View based (NOT Compose, NOT Hybrid)

Evidence:
  - build.gradle.kts: plugins = com.android.application + kotlin(android) + KSP.
    No Compose plugin, no compose BOM, no Compose compiler. The module comment
    itself states: "the UI stays deliberately minimal 鈥?no Compose, no DI, no
    Navigation, no Room, no third-party HTTP client" (Room was later added for
    persistence only; the UI is still plain Views).
  - res/layout/activity_chat.xml: a <ScrollView> root with a <LinearLayout>
    (vertical) containing plain widgets: TextView (title), Spinner
    (provider_node), TextView (provider_endpoint), Button (reconnect),
    EditText (pairing_code) + Button (pair), TextView (status), View (divider),
    TextView (transcript, textIsSelectable=true), EditText (input), and a
    horizontal LinearLayout with Button send / stop / new_session.
  - ChatActivity extends AppCompatActivity; it references views directly
    (findViewById-style), wires Spinner onItemSelected, Button onClick.
  - No Fragment, no Navigation component, no ViewModel, no Compose.

Implication for VNext:
  The current surface is a vertical stack that grows with content (whole-page
  ScrollView). The VNext target (fixed Header / fixed Conversation Viewport /
  fixed bottom Composer with internal transcript scroll + Cards) requires
  restructuring this single layout into a fixed 3-zone frame, and replacing the
  single transcript TextView with a card-based scrollable list (RecyclerView
  or a custom card container). This is a PRESENTATION-ONLY change: no Compose
  migration is required or implied by the design SSOT (which is framework-
  agnostic); it can be done in XML/View to stay consistent with the current
  stack. (Choosing Compose for VNext would itself be a larger decision and is
  NOT required by the design; default = keep XML/View.)


---

## 〔04〕 04_state_ownership_map.txt

AG.UI-BASELINE-AUDIT-1 鈥?State Ownership Map

RUN PATH (app launch -> render):
  App launch
    鈫? ChatActivity (LAUNCHER/MAIN) onCreate
  Host initialization
    鈫? DeviceLocalProviderNodeLoader.load(context, node)  [reads filesDir/<node>.json]
    鈫? resolveProviderSwitch(...) -> Applied | Rejected
    鈫? applyConfig(cfg, node):
    鈫?    SessionHolder.createRoot(cfg, ctx, node, onRunEvent, onDebugEvent, streamObserver)
    鈫?        builds ChatCompositionRoot (process-scoped, via SessionHolder.root)
    鈫?    ensureDebugBridge()  [no-op unless BuildConfig.DEBUG]
    鈫?    connectShell()       [AT Engine auto-connect; updates AT status]
    鈫?    lifecycleScope.launch { SessionHolder.initialize() }
  Runtime restore/create
    鈫? AgentRuntime.initialize()  [create-or-restore durable TopicSession + controlled recovery]
    鈫? on success -> projectCurrentHistory():
    鈫?    runtime.currentHistory() -> transcriptModel.reproject(history)
  Transcript projection
    鈫? SessionHolder.transcriptModel (process-scoped) holds List<Block>
    鈫? events: onRunEvent(AgentEvent) + onStreamEvent(kind, text) mutate blocks
  UI render
    鈫? SessionHolder.onUiUpdate -> ChatActivity.renderTranscript()
    鈫?    transcript.text = transcriptModel.render()  [single TextView]

STATE OWNERSHIP:
  AgentRuntime (process-scoped, in ChatCompositionRoot)
    - owns: active Run Job, interrupt, current session id, currentHistory()
    - AgentCore + Provider + ToolRegistry + messageDurability + topicSessionStore
  SessionHolder (process-scoped object)
    - owns: root (ChatCompositionRoot), transcriptModel, providerNode,
            providerStatusText / atStatusText / runStatusText / endpointLabel / isBusy
    - owns: the process-scoped persistence chain (RoomTopicSessionStore +
            StoreMessageDurability) built once
    - onRunEvent / onStreamEvent operate on SessionHolder.transcriptModel (NOT on Activity)
  TranscriptModel (process-scoped, pure, Android-free)
    - owns: List<Block> (the presentation projection)
    - render() -> String for the single TextView
  ChatActivity (Activity-scoped, thin)
    - owns: view references, busy button state, transient UI fields
    - rebinds to SessionHolder on onCreate/onResume via onUiUpdate
    - does NOT own the Run or the transcript authority (the holder is authoritative)

KEY FACTS:
  - Runtime ownership is in ChatCompositionRoot (constructed by SessionHolder.createRoot),
    process-scoped; survives Activity recreation.
  - Session is held by SessionHolder (process scope) + AgentRuntime (durable id).
  - The Activity is a THIN consumer; it re-renders from SessionHolder.
  - TranscriptModel.render() -> a single TextView (the whole transcript is one selectable
    text block; no per-block views, no cards).
  - fromRendered() is a LOSSY reconstruction used only for Activity-recreation rebind when
    the holder's model is empty; the live holder model is authoritative.

ACTIVITY OVERLOAD:
  ChatActivity is 594 lines and does: provider node switch, config apply, debug bridge
  wiring, AT connect/reconnect, ADB pair, run status, transcript render, Send/Stop/NewSession.
  It is NOT over-owned of state (state lives in SessionHolder/Runtime), but it IS a
  single monolithic controller for many concerns. VNext should split the controller
  responsibilities (sidebar / composer / transcript / settings) into focused components,
  but the STATE ownership (SessionHolder + Runtime) already matches the VNext guard
  (DB -> Runtime/Core -> Presentation projection -> UI Cards). No state-ownership change
  is required for the cards; only the presentation layer changes.


---

## 〔05〕 05_callback_action_map.txt

AG.UI-BASELINE-AUDIT-1 鈥?Callback / Action Map (current call flows)

All UI actions are wired as direct onClick / onItemSelected callbacks in ChatActivity.
The business semantics (busy guard, interrupt, session switch) are mostly delegated to
AgentRuntime / SessionHolder, but the WIRING lives in the Activity callbacks.

SEND  (Button send -> onSend())
  input.text -> trim -> blank check
  runtime null? -> status "No provider configured"
  runtime.isBusy? -> status "run already in progress"
  transcriptModel.onUser(text); renderTranscript(); input.clear; setBusy(true)
  lifecycleScope.launch { outcome = runtime.send(text); logTrace();
                          if Interrupted -> status "Stopped."; setBusy(false) }

STOP  (Button stop -> onStopRun())
  runtime null / !isBusy -> return
  runtime.interruptActiveRun():
     Requested -> status "Stopping...", stop disabled (INTERRUPTING, Host-transient)
     AlreadyInterrupting -> no-op
     NoActiveRun -> setBusy(false)
  (terminal RunFinished(Interrupted) arrives via SessionHolder.onRunEvent -> transcript
   onInterrupted() + isBusy=false; re-enables buttons)

NEW SESSION  (Button new_session -> onNewSession())
  lifecycleScope.launch { SessionHolder.newSession()
     -> runtime.newSession() (durable create + make current)
     -> on success: projectCurrentHistory() (clears old projection), status "New session started."
     -> on failure: status "Cannot start a new session while a run is in progress." }

PROVIDER SWITCH  (Spinner provider_node onItemSelected)
  activeNode -> DeviceLocalProviderNodeLoader.load(context, node)
  resolveProviderSwitch(attempted, load):
     Loaded  -> applyConfig(cfg, node)  [rebuilds root + re-init + reconnect AT]
     Missing/Invalid -> status "rejected", spinner reverts to last valid node
  (failed switch is honest: the active provider never changes on failure)

ADB RECONNECT  (Button reconnect -> onReconnect())
  reconnecting guard; connectShell():
     shellConnection.connect() -> status Connected / Not connected / safeAtMessage(...)

ADB PAIR  (Button pair -> onPair())
  6-digit code -> adapter.pairDiscovered(code) -> on success: clear code + connectShell()
  (code never persisted / logged)

BUSY STATE  (setBusy(busy))
  send.isEnabled = !busy; newSession.isEnabled = !busy; stop.isEnabled = busy
  (SessionHolder.setBusy mirrors it process-wide)

OBSERVATION (process-scoped, not Activity callbacks):
  SessionHolder.onRunEvent(AgentEvent) -> transcriptModel block mutation + notifyUiUpdate
  SessionHolder.onStreamEvent(kind,text) -> transcriptModel streaming mutation + notifyUiUpdate
  ChatActivity.sessionStreamObserver -> bridgeStream (DebugBridge) + SessionHolder.onStreamEvent

DEBUG ORIGINATED RUNS (DebugBridge.handleSend):
  runtime.isBusy? -> busy
  userInputSink(text) -> runOnUiThread { SessionHolder.transcriptModel.onUser(text) }
  onRunStateChanged(true) -> runOnUiThread { setBusy(true) }
  runtime.send(text)  [SAME send path as UI]
  onRunStateChanged(false) in finally

DEBT NOTE (for VNext):
  The action SEMANTICS are mostly in Runtime/SessionHolder (good), but the
  ACTIVITY CALLBACKS still own: busy-button juggling, status-string formatting,
  the send pre-checks (null/busy), and the debug-bridge wiring. VNext "Host Action
  normalization" would extract a thin Host Action layer so UI callbacks AND Debug
  commands share one entry per action (Send/Stop/NewSession/...). This is a
  PRESENTATION/HOST change, NOT a Core/Runtime change. It is HOST_ACTION_NORMALIZATION_NEEDED
  = POSSIBLE (see verdict). No frozen boundary is crossed by doing it.


---

## 〔06〕 06_transcript_block_map.txt

AG.UI-BASELINE-AUDIT-1 鈥?Transcript Block Map

TranscriptModel.Block (sealed interface) 鈥?CURRENT block types:
  User(text)
  Thinking(text, phase: THINKING|THOUGHT)
  Assistant(text, final: Boolean)
  Shell(command)                      <- tool action (named Shell but generic command)
  Result(text)                        <- tool result (rendered text)
  Failure(text)
  Interrupted(text)

RENDER PATH:
  TranscriptModel.render() -> single String -> ONE TextView (textIsSelectable=true).
  The whole transcript is one selectable text block. There are NO per-block views,
  NO cards, NO per-block copy, NO collapse. fromRendered() is a LOSSY rebuild (used
  only for Activity-recreation rebind when holder model empty).

PER-BLOCK MATRIX (design target vs current):

User
  exists: YES (Block.User)
  data source: user input (onSend) / Debug userInputSink / reproject(UserMessage)
  created by: TranscriptModel.onUser (Host) ; reproject (restore)
  updated: never (immutable once added)
  durable: YES (UserMessage in DB)
  presentation-only: NO (durable)
  current render: "User\n<text>" in the single TextView
  streaming: N/A (not streamed)
  stable identity: NO (list index only)
  card-mappable: YES -> UserCard (Copy/Retry/Edit&Resend/More)

Thinking / Thought
  exists: YES (Block.Thinking, phase flips THINKING->THOUGHT)
  data source: ModelStreamObserver reasoning_started/delta/finished
  created by: onReasoningStarted / onReasoningDelta ; flipped by onReasoningFinished
  updated: appended deltas (same block); phase flip on finish
  durable: NO (reasoning NOT persisted 鈥?frozen)
  presentation-only: YES
  current render: "Thinking...\n<text>" or "Thought\n<text>" in the single TextView
  streaming: YES (deltas grow the block)
  stable identity: NO (list index)
  card-mappable: YES -> ThoughtCard (Collapse/Expand/Copy/Select); SAME block lifecycle
    (Thinking->Thought is the same block; design 搂11 matches current semantics)
  NOTE: restored transcript has NO Thought (reasoning not persisted) 鈥?correct.

Assistant
  exists: YES (Block.Assistant, final flag)
  data source: ModelStreamObserver content_started/delta ; AnswerProduced (final)
  created by: onContentStarted/onContentDelta ; onAnswerProduced marks final
  updated: appended deltas (one block per model turn); final flag on answer
  durable: YES (ModelMessage in DB; final answer = no toolCalls)
  presentation-only: NO (durable)
  current render: "Assistant\n<text>" in the single TextView
  streaming: YES (content deltas grow the block incrementally 鈥?design 搂12 requirement met)
  stable identity: NO (list index)
  card-mappable: YES -> AssistantCard (Copy/Select/Retry). FinalAnswer de-duplication
    already correct (onAnswerProduced marks existing block final, no duplicate).
    Intermediate Assistant (with toolCalls) kept as final=false 鈥?matches design.

Tool Action (Shell block)
  exists: YES (Block.Shell(command))
  data source: AgentEvent.ToolCallStarted(call) ; reproject(ModelMessage.toolCalls)
  created by: onToolCallStarted ; reproject
  updated: never
  durable: YES (ModelMessage.toolCalls in DB)
  presentation-only: NO (durable)
  current render: "Shell\n$ <command>" in the single TextView
  streaming: N/A
  stable identity: NO
  card-mappable: YES -> ToolExecutionCard (action half). NOTE: block is NAMED "Shell"
    but commandOf(call) extracts the command from ANY tool's JSON args; for non-shell
    tools it shows raw args. A generic ToolExecutionCard should use call.name (tool id)
    + arguments, not assume shell. (Presentation rename + generic render; no domain change.)

Tool Result (Result block)
  exists: YES (Block.Result(text))
  data source: AgentEvent.ToolCallFinished(call,result) ; reproject(ToolResultMessage)
  created by: onToolCallFinished -> renderResult(result)
  updated: never
  durable: YES (ToolResultMessage in DB)
  presentation-only: NO (durable)
  current render: "Result\n<rendered>" in the single TextView. renderResult() parses the
    android_shell JSON payload (exitCode/stdout/stderr) and appends error on !success.
  streaming: N/A
  stable identity: NO
  card-mappable: YES -> ToolExecutionCard (result half: Preview/Expand/Collapse/Copy).
    Tool-level output is BOUNDED upstream (see 06: file truncated flag, network caps);
    the Result block stores the (possibly tool-truncated) text. UI collapse must not
    pretend to expand data the tool already truncated (FULL_RESULT_AVAILABLE_TO_UI =
    SOMETIMES 鈥?bounded by tool caps).

Failure
  exists: YES (Block.Failure(text))
  data source: AgentEvent.RunFailed(failure)
  created by: onRunFailed -> renderFailure(failure)
  updated: never
  durable: NO (failure is a run outcome, not a Message)
  presentation-only: YES
  current render: "Failed\n<text>" in the single TextView
  card-mappable: YES -> FailureCard (Copy/Select). Tool-level failure is shown inside
    the Tool Result block (renderResult appends "error: ..."); run/provider failure is
    a separate Failure block. Matches design 搂14 (tool failure in-card, run failure standalone).

Interrupted
  exists: YES (Block.Interrupted(text))
  data source: AgentEvent.RunFinished(outcome=Interrupted)
  created by: onInterrupted() (SessionHolder.onRunEvent when outcome is Interrupted)
  updated: never
  durable: NO (interrupted is a run outcome; partial history is durable per CORE-007
    synthetic interrupted ToolResult)
  presentation-only: YES (the block)
  current render: "Stopped\n<text>" in the single TextView
  card-mappable: YES -> InterruptedCard (NOT a Failure). Matches design 搂14/搂19:
    user Stop != Failure; partial transcript preserved; Interrupted appended.

TRANSCRIPT_RENDERING = single TextView (render() -> one string)
THINKING_TO_THOUGHT = SUPPORTED (same block, phase flip 鈥?verified in code)
ASSISTANT_STREAMING = SUPPORTED (content deltas grow one block per turn)
TOOL_RENDERING = generic command + rendered result (shell-payload-aware; rename to generic
  ToolExecutionCard is presentation-only)


---

## 〔07〕 07_session_ui_map.txt

AG.UI-BASELINE-AUDIT-1 鈥?Session UI Map

DESIGN TARGET (VNext 搂20/搂21): Collapsible Sidebar with
  + New Session ; Sessions list ; Today/Yesterday/Earlier grouping ; Current highlight ;
  Rename ; ADB block ; Settings block.

CURRENT STATE:
  - NO sidebar. NO session list. NO session browser.
  - The only session control is a single "New Session" Button on the main surface.
  - "Switch Session" = NOT IMPLEMENTED in the UI (no way to list/select an old session).

PERSISTENCE CAPABILITY (what exists to SUPPORT a sidebar):
  TopicSessionEntity (Room): id, created_at, last_used_at, model, provider, title(nullable)
  TopicSessionStore (ag-runtime contract) methods:
    createSession(id, now, model, provider, title)
    appendMessage(sessionId, message, now)
    loadSession(id) -> TopicSessionRecord?
    loadLatestSession() -> TopicSessionRecord?      (ORDER BY last_used_at DESC LIMIT 1)
    loadMessages(sessionId) -> List<Message>?
    updateModelProviderMetadata(sessionId, model, provider)
  TopicSessionDao (Room): insert, findById, findLatest, updateModelProvider, probe
  MessageDao: insert, findBySession, nextSequence, touchLastUsed, countBySession

GAPS (for a V1 Session Browser):
  1. NO list-all-sessions query. The store/DAO only expose loadLatestSession (LIMIT 1)
     and loadSession(id). A sidebar "Sessions" list needs a listSessions() (e.g.
     ORDER BY last_used_at DESC). This is a PERSISTENCE/STORE addition.
       - It is NOT a schema change (topic_sessions table + last_used_at index already
         exist; a SELECT-all query is additive).
       - It touches the ag-runtime TopicSessionStore contract (add a method) + the
         Room impl + DAO query. This is the ONE item that crosses the frozen
         TopicSessionStore contract boundary. See 11_gap_matrix + verdict.
  2. NO session switch API in the UI/Runtime. AgentRuntime has newSession() and
     initialize() (create-or-restore latest) but NO switchToSession(id). Switching to a
     prior session requires loading that session's history and making it current.
     This is a RUNTIME addition (or a Host composition over currentHistory + a new
     store-backed load). See verdict (may be HOST composition or a small Runtime seam).
  3. title field EXISTS (nullable) in the entity + createSession param, but:
     - it is NEVER SET in production (createSession is called with title=null by the
       Runtime; no title auto-generation, no rename path).
     - NO rename API (no updateTitle in store/DAO).
     - The design wants Rename. V1 rename = add an updateTitle path (store+DAO) OR
       a presentation-derived label. Per design 搂27, do NOT add a title column (it
       already exists); rename is an additive store method or presentation label.
  4. Today/Yesterday/Earlier grouping = PRESENTATION-derived from last_used_at/created_at
     (NO schema change; the columns exist).

SESSION_BROWSER = NOT IMPLEMENTED (only New Session button)
SESSION_TITLE = field exists (nullable, never populated in production); rename = GAP
SESSION_LIST = GAP (needs list-all query 鈥?additive store/DAO method)
SESSION_SWITCH = GAP (no UI switch; no Runtime switchToSession)

NOTE: None of these require a DB SCHEMA change (columns already exist). They require
additive STORE/DAO query methods + a Runtime/Host switch seam + UI. The store-contract
addition (listSessions) is the boundary item to flag (see verdict / gap matrix).


---

## 〔08〕 08_adb_ui_map.txt

AG.UI-BASELINE-AUDIT-1 鈥?ADB UI Map

DESIGN TARGET (VNext 搂22):
  Header: ADB 鈼?(total state only)
  Sidebar: Connected / Connecting / Disconnected + Reconnect + short failure reason.
  ADB = Workspace environment state (NOT in Transcript).

CURRENT STATE:
  - NO header ADB indicator. ADB/AT status is a text line in the main status area:
    "AT: Connected" / "AT: Not connected" / "AT: Pairing required" / etc.
  - "Reconnect" Button exists on the main surface (connectShell()).
  - ADB "Pair" (6-digit code) exists on the main surface (first-use bootstrap).
  - ADB state is NOT in the transcript (correct; it is a status line).

DATA SOURCE (verified 鈥?UI does NOT bypass integration-at):
  ChatActivity.connectShell() -> root.shell.connect()
    ShellConnection (Host) wraps AtExecutionAdapter (:integration-at)
  Status: ShellConnection.connect() returns ready + message
    -> HostStatusModel.safeAtMessage(message) collapses to a short secret-free line
  ADB semantic state is available via CompositionAdbSemantic (Host bridge to ATAndroidAdbControl):
    readinessState() -> AtAdbReadiness {READY, DISCONNECTED, CONNECTING, UNKNOWN}
    bootstrapState() -> AtAdbBootstrap {PAIRING_REQUIRED, CONNECT_FAILED, READY}
    ensureReady(onStatusLine) -> Boolean
    hasIdentity() / discoverPairing() / pairDiscovered(code) / pair(host,port,code)
  The adapter (AtExecutionAdapter) is the SINGLE AT consumption point; AG Core/Runtime
  have NO direct AT dependency (frozen boundary holds).

ADB_STATUS_READY = YES (readinessState + safeAtMessage give Connected/Connecting/Disconnected
  + short failure reason; just needs a header dot + sidebar placement)
ADB_RECONNECT_READY = YES (connectShell()/onReconnect() already re-runs the connect flow;
  just needs relocation to the sidebar)

BOUNDARY CHECK:
  - UI does NOT bypass integration-at: status + reconnect + pair all go through
    ShellConnection -> AtExecutionAdapter -> AT. AG Core/Runtime do not depend on AT.
  - The header dot + sidebar Reconnect are pure PRESENTATION re-placement of existing
    capability. NO domain/AT change required.


---

## 〔09〕 09_provider_ui_map.txt

AG.UI-BASELINE-AUDIT-1 鈥?Provider / Model-UI Map

DESIGN TARGET (VNext 搂18/搂23):
  Main surface shows only "Model 路 Effort" (e.g. "GPT-5.6 路 High").
  Provider / endpoint / API key move to a Settings page (NOT on the main surface).
  No Test Connection / Save on the main surface.

CURRENT STATE:
  - Provider selection = a Spinner (provider_node) on the MAIN surface with two nodes:
    ProviderNode.OPENROUTER ("OpenRouter", provider.openrouter.json) and
    ProviderNode.EMG ("EMG", provider.local.json).
  - Below it: provider_endpoint TextView (the ACTIVE safe endpoint), Reconnect,
    pairing code + Pair, status line. All on the main surface.
  - NO model selector. NO effort/reasoning selector. NO settings page.
  - API key: read from the device-local config file (filesDir/<node>.json) via
    DeviceLocalProviderNodeLoader -> OpenAiCompatibleConfigLoader.parse. The app is
    READ-ONLY to that file (never writes it). There is NO in-app API-key entry UI in
    the current code (the AGA.DEV-CONFIG-0 "manual Apply" flow referenced in AGENTS.md
    is a prior manual-setup path; the current UI is the node Spinner + endpoint label).

CONFIG DATA MODEL (OpenAiCompatibleConfig):
  baseUrl : String
  apiKey  : String (default "")
  model   : String
  (NO reasoning effort / thinking / temperature field exists in the config today)

MODEL SELECTOR:
  model selector = NOT EXISTENT. The model comes from the config file's "model" field.
  VNext "Model 路 Effort" selector = GAP. The config has a model id but no in-app picker
  and NO effort field.

EFFORT:
  effort is NOT CONFIGURABLE today. OpenAiCompatibleConfig has no reasoning-effort /
  thinking field. The provider does not currently send a reasoning-effort parameter.
  - Design 搂18 wants "Model 路 Effort". Adding a configurable effort requires:
      (a) a new field in OpenAiCompatibleConfig + the wire request (reasoning effort),
      (b) the provider to emit it.
    This is a PROVIDER-CONFIG/PROVIDER change. NOT all providers support effort, so the
    UI must NOT fake an effort a provider doesn't support (design 搂24). 
  - Verdict: MODEL_SELECTOR = GAP (presentation + config); EFFORT = GAP that may require
    a provider config/wire addition (flag in gap matrix). For V1, the selector can
    surface the ACTIVE model (read from config) without faking effort; effort wiring is
    a scoped provider change if required.

PROVIDER/API KEY SETTINGS:
  - Storage exists (device-local file, read-only, secret-safe loader). REUSE it; do NOT
    build a second config system (design 搂25).
  - Moving Provider/Endpoint/Key to a Settings page is a PRESENTATION re-placement.
  - "Test Connection" + "Save" (write) are NOT in the current code. Save = writing the
    device-local config file (currently read-only). This is a new Host capability (write
    path) if the design's Save is required 鈥?flag as a scoped Host addition (not Core).

SECRET HANDLING (audit rule):
  - API keys are NEVER logged/echoed. DeviceLocalProviderConfigLoader maps failures to
    safe categories (REASON_MISSING/REASON_INVALID) and never exposes raw JSON.
    safeChatCompletionsEndpoint is the only endpoint identity shown.
  - This round's evidence contains NO API key / Authorization / secret (only safe
    endpoint identities + file names).

PROVIDER_SETTINGS = partial (node spinner + endpoint on main surface; move to Settings = presentation)
MODEL_SELECTOR = GAP (no picker; model from config)
EFFORT = GAP (not configurable; provider config/wire may be needed; do not fake)


---

## 〔10〕 10_debug_bridge_map.txt

AG.UI-BASELINE-AUDIT-1 鈥?DebugBridge Map

DESIGN TARGET (VNext 搂24): PRESERVE / REUSE / DO NOT REBUILD the existing DebugBridge.
  UI callback -> Host Action -> AgentRuntime ; Debug command -> SAME path.
  Correct structure:
    AgentRuntime -> AG Core -> Provider/Tool -> Event Stream -> { UI Transcript ; DebugBridge }
  Forbidden: second Debug AgentRuntime / second history / second execution semantics.

CURRENT DebugBridge (app/src/main/java/com/androidagent/app/debug/DebugBridge.kt):
  - TCP JSONL server, protocol "ag-debug-0", DEFAULT_PORT = 45678.
  - Binds 0.0.0.0 in DEBUG builds (so a LAN PC can reach it without ADB reverse);
    release builds NEVER create a DebugBridge (ensureDebugBridge() returns early when
    !BuildConfig.DEBUG).
  - runtimeProvider = { sharedRuntime } = { SessionHolder.runtime } 鈥?the SAME
    AgentRuntime the UI uses. NOT a second runtime.
  - toolRegistryProvider = { root?.toolRegistryView } 鈥?the SAME production ToolRegistry.
  - userInputSink + onRunStateChanged hooks mirror the UI send path (User block + busy).
  - adapterProvider = { root?.shell?.adapter } 鈥?the SAME AtExecutionAdapter.
  - eventSink (AgentEventSink) is wired into the Host CompositeSink, so EVERY AgentEvent
    (UI-originated OR debug-originated) is broadcast to connected debug clients.
  - streamSink (ModelStreamObserver) broadcasts reasoning/content deltas.

DEBUG_SEND_PATH:
  ClientMessage.Send -> handleSend(text):
    blank? reject ; runtime null? reject ; runtime.isBusy? -> "busy"
    accepted -> userInputSink(text) [UI User block] -> onRunStateChanged(true) [busy]
    -> runtime.send(text)   [SAME AgentRuntime.send as the UI]
    -> onRunStateChanged(false) in finally
  VERDICT: DEBUG_SEND = SHARED with UI send path (both call AgentRuntime.send). ALREADY_SHARED.

DEBUG_OBSERVE_PATH (event/output):
  eventSink.emit(event) -> broadcast(eventToJson(event)) to all clients (single-threaded
    send executor, strictly ordered, off the main thread).
  streamSink (reasoning/content deltas) -> emitStream -> broadcast.
  This is a PASSIVE side channel on the SAME event stream that feeds the UI transcript.
  NOT a second transcript SSOT. ALREADY the correct structure (Event Stream -> {UI ; Debug}).

DEBUG_STOP_PATH:
  NOT IMPLEMENTED. The ClientMessage set has NO stop/interrupt command:
    ping / send / tool_invoke / session_open / session_write / session_read /
    session_resize / session_close / session_state / bootstrap_state /
    bootstrap_discover / bootstrap_pair
  There is no way for a PC debug client to stop an active Run. The UI Stop
  (interruptActiveRun) is UI-only today.
  VERDICT: DEBUG_STOP = DEBUG_ONLY-GAP (UI has Stop; Debug has none). Adding a
  "stop" client command that calls the SAME AgentRuntime.interruptActiveRun() would be
  a small additive protocol extension (protocol-compatible; older clients ignore it).
  This is a PRESENTATION/PROTOCOL addition, NOT a second runtime. Flag in gap matrix.

DEBUG TOOL INVOKE (direct, no LLM):
  ClientMessage.ToolInvoke -> registry.dispatch(call, AllowAll) -> tool_result_direct.
  Shares the production ToolRegistry; never model-visible. (Existing; preserve.)

DEBUG SESSION (AT persistent, debug-only):
  session_open/write/read/resize/close/state -> AtExecutionAdapter. Debug-only
  validation surface. (Existing; preserve.)

RELEASE DEBUG BOUNDARY:
  - ensureDebugBridge() is the single gate: `if (!BuildConfig.DEBUG) return`.
  - Release APK: NO listener, NO debug port, NO external debug ingress, NO debug logs
    from the bridge. (Verified: the bridge is process-scoped and only constructed in
    debug builds.)
  - Debug logs (AgentTrace) are redacted via TraceFormatter before any Log.i.

DEBUG_BRIDGE = PRESERVE / REUSE (correct structure already; no rebuild needed)
DEBUG_SEND_PATH = SHARED (AgentRuntime.send)
DEBUG_OBSERVE_PATH = SHARED event stream (side channel)
DEBUG_STOP_PATH = NOT IMPLEMENTED (gap; small additive protocol extension if wanted)
SECOND_AGENT_RUNTIME = NO
SECOND_UI_DB_PIPELINE = NO


---

## 〔11〕 11_vnext_gap_matrix.txt

AG.UI-BASELINE-AUDIT-1 鈥?VNext Gap Matrix

Severity: BLOCKER / MAJOR / MINOR / POLISH / DEFERRED
Layer: Presentation / Host UI / Host / Host+Store / Host+Runtime / Provider / Debug

Columns: DESIGN_ITEM | CURRENT_STATE | TARGET_STATE | OWNER_LAYER | FILES | SEVERITY | ARCHITECTURE_IMPACT | NEXT_STEP

G1 Light Theme / Workspace
   CURRENT: Theme.AppCompat.DayNight.DarkActionBar; no light-priority workspace palette.
   TARGET:  Light theme; workspace grey-white / card white / subtle dividers.
   LAYER:   Presentation (theme + resources)
   FILES:   res/values/themes.xml (+colors/dimens), activity_chat.xml
   SEVERITY: MAJOR
   ARCH:    NO (resources only)
   NEXT:    Define light theme + card styling in AG.UI-SHELL-1.

G2 Collapsible Sidebar
   CURRENT: No sidebar; single main surface.
   TARGET:  Collapsible sidebar (New Session / Sessions / ADB / Settings).
   LAYER:   Host UI (new layout + controller)
   FILES:   activity_chat.xml (restructure) + new sidebar layout + ChatActivity (or new fragment/controller)
   SEVERITY: MAJOR
   ARCH:    NO (presentation + host wiring only)
   NEXT:    AG.UI-SHELL-1 (sidebar frame) + AG.UI-CONTROLS-1 (session/adb/settings content).

G3 Fixed Conversation Viewport
   CURRENT: Whole-page ScrollView; composer grows with content; no fixed 3-zone frame.
   TARGET:  Header fixed / Viewport fills remaining / Composer fixed; transcript internal scroll.
   LAYER:   Presentation (layout)
   FILES:   activity_chat.xml (replace root ScrollView with a fixed 3-zone LinearLayout/ConstraintLayout)
   SEVERITY: MAJOR
   ARCH:    NO
   NEXT:    AG.UI-SHELL-1.

G4 UserCard
   CURRENT: "User\n<text>" in single TextView.
   TARGET:  UserCard (Copy/Retry/Edit&Resend/More + native selection).
   LAYER:   Presentation (card view + adapter)
   FILES:   new card layout + transcript list adapter + TranscriptModel (already has Block.User)
   SEVERITY: MAJOR
   ARCH:    NO (data exists; presentation only)
   NEXT:    AG.UI-TRANSCRIPT-CARDS-1.

G5 ThoughtCard
   CURRENT: "Thinking.../Thought\n<text>" in single TextView (same block, phase flip).
   TARGET:  ThoughtCard (Collapse/Expand/Copy/Select); Thinking->Thought same card.
   LAYER:   Presentation
   FILES:   new card layout + adapter
   SEVERITY: MAJOR
   ARCH:    NO (semantics already match design 搂11)
   NEXT:    AG.UI-TRANSCRIPT-CARDS-1.

G6 AssistantCard
   CURRENT: "Assistant\n<text>" in single TextView (streams incrementally).
   TARGET:  AssistantCard (Copy/Select/Retry); streaming incremental.
   LAYER:   Presentation
   FILES:   new card layout + adapter
   SEVERITY: MAJOR
   ARCH:    NO (streaming + final de-dup already correct)
   NEXT:    AG.UI-TRANSCRIPT-CARDS-1.

G7 ToolExecutionCard (generic)
   CURRENT: "Shell\n$ <command>" + "Result\n<rendered>" in single TextView; shell-payload-aware
            render; block NAMED Shell but generic command extraction.
   TARGET:  Unified ToolExecutionCard for Shell/File/Network (action+result combined visually,
            independent semantically); Preview/Expand/Collapse/Copy.
   LAYER:   Presentation
   FILES:   new card layout + adapter (use call.name + arguments, not assume shell)
   SEVERITY: MAJOR
   ARCH:    NO (generic ToolCall/ToolResult data exists; rename is presentation)
   NEXT:    AG.UI-TRANSCRIPT-CARDS-1.

G8 FailureCard
   CURRENT: "Failed\n<text>" in single TextView (run failure); tool failure in Result block.
   TARGET:  FailureCard (Copy/Select); tool failure in-card with real diagnosis.
   LAYER:   Presentation
   FILES:   new card layout + adapter
   SEVERITY: MINOR
   ARCH:    NO
   NEXT:    AG.UI-TRANSCRIPT-CARDS-1.

G9 InterruptedCard
   CURRENT: "Stopped\n<text>" in single TextView (NOT a Failure).
   TARGET:  InterruptedCard (can re-run originating Turn).
   LAYER:   Presentation
   FILES:   new card layout + adapter
   SEVERITY: MINOR
   ARCH:    NO (Interrupted != Failure already correct)
   NEXT:    AG.UI-TRANSCRIPT-CARDS-1.

G10 Section Divider (User/Thought/Assistant)
   CURRENT: None (single text block).
   TARGET:  Restrained divider before User/Thought/Assistant; Tool/Result inside execution.
   LAYER:   Presentation (rendering; NOT persisted data)
   FILES:   card list adapter (divider decoration)
   SEVERITY: MINOR
   ARCH:    NO (divider is rendering, derived from block type ordering)
   NEXT:    AG.UI-TRANSCRIPT-CARDS-1.

G11 Copy (single block)
   CURRENT: Whole transcript is one selectable TextView; NO per-block copy; NO ClipboardManager
            usage; NO copy helper.
   TARGET:  Per-block Copy (whole block body, no metadata) + native text selection.
   LAYER:   Host UI / Presentation (ClipboardManager + per-card copy action)
   FILES:   card views (copy button) + ClipboardManager helper
   SEVERITY: MAJOR
   ARCH:    NO (pure presentation; block text already available in TranscriptModel)
   NEXT:    AG.UI-TRANSCRIPT-CARDS-1 (copy per card).
   COPY_SINGLE_BLOCK = GAP (presentation-only to fix)
   NATIVE_TEXT_SELECTION = PARTIAL (whole-TextView selectable today; per-card selection in cards)

G12 Retry / Re-run (latest completed Turn)
   CURRENT: NO retry/resend/rewind/rollback API in ag-core/ag-runtime/app (verified 0 hits).
            AgentRuntime has no rewind. A "retry" today would only re-send a fresh user text.
   TARGET:  鈫?Retry / Re-run originating User Turn (latest completed Turn first; hidden while running).
   LAYER:   Host (composition) 鈥?RE-EVALUATE
   FILES:   ChatActivity / Host Action + TranscriptModel (locate last User block)
   SEVERITY: MAJOR (V1 lists Retry as must-do)
   ARCH:    POSSIBLE 鈥?see note. A "Retry latest Turn" can be COMPOSED from existing
            capability IF the semantics are "re-send the same user text as a NEW turn"
            (append a new User + re-run). That is a Host composition over AgentRuntime.send 鈥?            NO Core/Runtime change. BUT a STRICT retry (drop the prior failed/partial turn and
            redo) would require history rewind/retract = NOT available = would cross the frozen
            boundary. The design's "Retry / Re-run" (NOT strict Redo/Unrevert) maps to the
            re-send composition. Verdict: LATEST_TURN_RETRY = PARTIAL (re-send composition is
            possible presentation/host-only; strict redo is DEFERRED per design 搂28).
   NEXT:    AG.UI-CONTROLS-1 鈥?implement re-send semantics (host composition); document that
            strict redo/unrevert is DEFERRED (design 搂28). No Core/Runtime change for re-send.

G13 Edit & Resend
   CURRENT: NO rewind/retract API; NO draft restore to composer; composer is a plain EditText
            (input) with no per-turn association.
   TARGET:  Select old User Turn -> restore text to Composer -> edit -> re-send via existing path.
            FORBIDDEN: directly modify old durable UserMessage.
   LAYER:   Host (composition) 鈥?RE-EVALUATE
   FILES:   UserCard "More" action + Composer (input) + Host Action
   SEVERITY: MAJOR
   ARCH:    NO 鈥?the design's own semantics are "restore text to composer + re-send" (a NEW
            message), NOT modifying the old durable message. This is a pure HOST composition:
            read the User block text (TranscriptModel) -> set composer text -> user edits ->
            existing send path. NO Core/Runtime/DB change. 
   NEXT:    AG.UI-CONTROLS-1.
   EDIT_RESEND = REALIZABLE as host composition (re-send new message); no domain change.

G14 Stop / Interrupt
   CURRENT: UI Stop -> AgentRuntime.interruptActiveRun() -> RunOutcome.Interrupted ->
            RunFinished(Interrupted) -> TranscriptModel.onInterrupted() (NOT Failure).
            Working (HOST-STOP-1 verified on device).
   TARGET:  Idle:鈫?/ Running:鈻?Stop; UI=Stop / AG=Interrupt / Runtime=cancellation.
   LAYER:   Host (already wired) 鈥?re-place in Composer
   FILES:   Composer submit/stop button (re-place existing onStopRun)
   SEVERITY: MINOR (capability exists; just re-place + idle/running toggle)
   ARCH:    NO
   STOP_UI_BACKEND_READY = YES
   NEXT:    AG.UI-SHELL-1 (composer submit/stop) reuses onStopRun.

G15 Composer (Codex-style)
   CURRENT: Plain EditText + Send/Stop/NewSession buttons in a row; on the scrolling page.
   TARGET:  锛?Context/File + Input + Model路Effort + Submit/Stop, fixed bottom.
   LAYER:   Presentation + Host
   FILES:   composer layout + ChatActivity
   SEVERITY: MAJOR
   ARCH:    NO (input + send/stop exist; re-place + add model/effort label + attachment)
   NEXT:    AG.UI-SHELL-1 (frame) + AG.UI-CONTROLS-1 (attachment/model).

G16 Attachment / Context (File/Folder, SAF)
   CURRENT: NO attachment UI; NO SAF picker in the app (the 39 Uri/content hits are from the
            AT/transport code, not a user file-picker). FileTool is READ-only over
            filesDir/agent/ (APP_SELF realm) 鈥?that is a TOOL, not an attachment-context input.
   TARGET:  锛?add file/folder via Android SAF; chips; do NOT dump file body into input.
            Attachment -> model CONTEXT (NOT equivalent to FileTool read).
   LAYER:   Host + (NEW) attachment context contract
   FILES:   SAF picker (Activity) + attachment model + how attachments enter model context
   SEVERITY: MAJOR
   ARCH:    GAP 鈥?there is NO attachment/context contract today. "Attachment enters model
            context" is NOT the same as FileTool read. Implementing it requires deciding how
            attachment content is injected into the Run (e.g. an instruction/context block or
            a new input channel). This MAY touch Core (how context reaches the model) 鈥?flag
            as a contract decision. For V1, the SAF picker + chips are presentation; the
            "into model context" mechanism is the open item. Per design 搂23, do NOT invent a
            temp input protocol for the UI; mark GAP and resolve in a scoped step.
   NEXT:    Flag in CONTROLS step; resolve the attachment->context mechanism (may be a small
            Core/Run-options addition) BEFORE building the picker UX.
   ATTACHMENT_CONTEXT = GAP (SAF picker = presentation; context-injection = contract decision)

G17 Model / Effort selector
   CURRENT: NO model picker; NO effort (config has no effort field).
   TARGET:  "Model 路 Effort" on main surface; tap to switch model + effort.
   LAYER:   Presentation + Provider config (effort)
   FILES:   composer label + provider config (effort field) + wire
   SEVERITY: MAJOR
   ARCH:    MODEL = presentation (read active model from config). EFFORT = Provider config/wire
            addition (flag). Do NOT fake effort for unsupported providers (design 搂24).
   NEXT:    Model label in SHELL; effort wiring scoped (provider) if required in CONTROLS.

G18 Provider / API Key Settings page
   CURRENT: Provider Spinner + endpoint on main surface; config file read-only; no Save/Test.
   TARGET:  Provider/API Key/Endpoint in Settings; Test Connection + Save.
   LAYER:   Host UI (re-place) + Host (write path for Save)
   FILES:   settings layout + ChatActivity + DeviceLocalProviderConfigLoader (write) if Save
   SEVERITY: MINOR
   ARCH:    NO (re-place existing; Save = additive host write; REUSE existing config loader)
   NEXT:    AG.UI-CONTROLS-1 (settings page). Reuse config system; do NOT build a second one.

G19 ADB indicator (header dot + sidebar)
   CURRENT: "AT: <status>" text line + Reconnect + Pair on main surface.
   TARGET:  Header ADB 鈼?+ sidebar Connected/Connecting/Disconnected + Reconnect + reason.
   LAYER:   Presentation (re-place)
   FILES:   header + sidebar layout + ChatActivity
   SEVERITY: MINOR
   ARCH:    NO (status + reconnect + readinessState all exist; re-place)
   ADB_STATUS_READY = YES ; ADB_RECONNECT_READY = YES
   NEXT:    AG.UI-SHELL-1 (header) + AG.UI-CONTROLS-1 (sidebar ADB block).

G20 DebugBridge
   CURRENT: TCP JSONL 0.0.0.0:45678 (debug builds only); shares AgentRuntime.send + event stream.
   TARGET:  PRESERVE / REUSE / DO NOT REBUILD; UI/Debug same action path.
   LAYER:   Debug (preserve)
   FILES:   DebugBridge.kt (no rebuild)
   SEVERITY: POLISH (no change) 鈥?but see G21 for the optional stop command.
   ARCH:    NO
   DEBUG_BRIDGE = PRESERVE
   NEXT:    Keep as-is; optionally add a "stop" client command (G21).

G21 Debug stop command (optional)
   CURRENT: NO debug stop command (ClientMessage has no stop).
   TARGET:  (design 搂24 wants UI Stop / Debug Stop to share the interrupt path)
   LAYER:   Debug protocol (additive)
   FILES:   DebugProtocol.kt (new ClientMessage.Stop) + DebugBridge.handleStop
   SEVERITY: MINOR (nice-to-have; not a V1 blocker 鈥?UI Stop already works)
   ARCH:    NO (calls the SAME AgentRuntime.interruptActiveRun; protocol-compatible; older
            clients ignore unknown type)
   NEXT:    Optional; can be folded into CONTROLS or a small debug step. Not required for V1.

G22 Session Browser (list/switch/rename)
   CURRENT: Only "New Session" button. No list/switch/rename. title field exists (nullable,
            never populated). No list-all query; no switchToSession.
   TARGET:  + New Session / Sessions list / Today-Yesterday / Current highlight / Rename.
   LAYER:   Host UI + Host+Store (list query) + Host+Runtime (switch)
   FILES:   sidebar + TopicSessionStore.listSessions (additive) + DAO query +
            Runtime/Host switchToSession + rename (updateTitle or presentation label)
   SEVERITY: MAJOR
   ARCH:    BOUNDARY ITEM 鈥?adding TopicSessionStore.listSessions() extends the frozen
            ag-runtime TopicSessionStore CONTRACT (additive method; NO schema change; the
            table + last_used_at index already exist). switchToSession is a Runtime/Host seam.
            This is the one item that touches a frozen contract surface. Per 搂36, flag it:
            it is ADDITIVE (not a rewrite) and does NOT break frozen semantics (D1/D2/D3,
            sequence ownership, ordering). Verdict: FREEZE_REOPEN_REQUIRED = NO for the SCHEMA,
            but the STORE CONTRACT needs a small additive extension 鈥?record as a scoped
            contract addition (not a reopen). See verdict.
   NEXT:    AG.UI-CONTROLS-1 (session browser) 鈥?includes the additive store query + switch seam.

G23 Product identity (icon/version/label)
   CURRENT: applicationId com.androidagent.app; versionCode 1; versionName 0.1.0; label
            "AndroidAgent"; icon = @android:drawable/sym_def_app_icon (DEFAULT); minSdk 26;
            targetSdk 34; theme DayNight.DarkActionBar.
   TARGET:  (for AG.UI-RELEASE-FREEZE-1) real launcher icon, version bump, final label/theme.
   LAYER:   Resources / manifest
   FILES:   AndroidManifest.xml + mipmap icons + build.gradle.kts (version)
   SEVERITY: DEFERRED (explicitly a RELEASE-FREEZE step, not V1 UI)
   ARCH:    NO
   NEXT:    AG.UI-RELEASE-FREEZE-1 (gap list captured here).

G24 Host Action normalization (UI/Debug share entry)
   CURRENT: Action semantics mostly in Runtime/SessionHolder, but Activity callbacks own
            busy juggling + pre-checks + debug wiring; Debug send already shares runtime.send.
   TARGET:  UI callback -> Host Action -> Runtime ; Debug command -> SAME Host Action.
   LAYER:   Host (refactor)
   FILES:   new Host Action layer (Send/Stop/NewSession/...) + ChatActivity + DebugBridge
   SEVERITY: MINOR (cleanliness; not user-visible)
   ARCH:    NO (host-only; does not cross Core/Runtime)
   HOST_ACTION_NORMALIZATION_NEEDED = POSSIBLE (recommended, low risk)
   NEXT:    Optional; can be done in SHELL/CONTROLS as the UI is restructured.

DEFERRED (design 搂28 鈥?NOT V1 blockers; do NOT list as blockers):
  Grok-style Turn switching, Focus View, pinch zoom, complex animation, strict Redo/Unrevert,
  arbitrary historical Fork, Checkpoint UI, Export, complex syntax highlighting, Voice,
  new Debug protocol, second Debug runtime, architecture redesign, Gradle/module redesign.

SUMMARY COUNTS (V1-relevant, excluding DEFERRED):
  BLOCKER = 0
  MAJOR   = G1,G2,G3,G4,G5,G6,G7,G11,G12,G13,G15,G16,G17,G22  (14)
  MINOR   = G8,G9,G10,G14,G18,G19,G21,G24  (8)
  POLISH  = G20  (1)
  DEFERRED = G23 (release identity) + all 搂28 items
  NOTE: G12 (Retry) and G16 (Attachment context) carry the only ARCH=POSSIBLE/GAP notes;
        neither forces a Core/Runtime REWRITE. G22 is the one frozen-CONTRACT additive item.


---

## 〔12〕 12_release_identity_audit.txt

AG.UI-BASELINE-AUDIT-1 鈥?Release / Product Identity Audit

Purpose: capture the product-identity gap list for AG.UI-RELEASE-FREEZE-1 (NOT changed this round).

  applicationId  = com.androidagent.app
  versionCode    = 1
  versionName    = "0.1.0"
  app label      = "AndroidAgent"  (strings.xml app_name)
  launcher icon  = @android:drawable/sym_def_app_icon   (DEFAULT system icon 鈥?must be replaced)
  theme          = Theme.AppCompat.DayNight.DarkActionBar  (NOT the VNext light workspace theme)
  status bar     = default (DarkActionBar)
  navigation bar = default
  minSdk         = 26
  targetSdk      = 34
  compileSdk     = 34
  permissions    = INTERNET, ACCESS_NETWORK_STATE, ACCESS_WIFI_STATE, CHANGE_WIFI_STATE,
                   WRITE_SECURE_SETTINGS (signature|privileged), ACCESS_COARSE_LOCATION (maxSdk 35),
                   ACCESS_FINE_LOCATION (maxSdk 35)
  allowBackup    = false
  supportsRtl    = true
  single APK     = arm64-v8a only (AT Engine handoff bundle v0 is arm64-only)

GAP LIST (for AG.UI-RELEASE-FREEZE-1):
  1. Launcher icon = DEFAULT system icon. Replace with a real product icon (mipmap-*).
  2. versionCode 1 / versionName 0.1.0 = dev baseline. Bump at release freeze.
  3. Theme = DayNight.DarkActionBar. Replace/augment with the VNext LIGHT workspace theme
     (V1 = Light first; Dark/System later).
  4. Status/nav bar styling to match the light workspace (light status bar, etc.).
  5. Final app label review (current "AndroidAgent" is acceptable; confirm).
  6. arm64-only ABI (inherited from AT handoff v0) 鈥?confirm acceptable for release target.
  7. WRITE_SECURE_SETTINGS is a product capability declaration (not granted on fresh install;
     self-granted via pm grant after verified ADB shell). Confirm the release posture.

This round CHANGES NONE of the above (RESOURCE_CHANGED = NO). The list is for the release step.


---

## 〔13〕 13_device_baseline.txt

AG.UI-BASELINE-AUDIT-1 鈥?Device Baseline

DEVICE_BASELINE = DEFERRED_WITH_REASON

Reason:
  `adb devices` (C:\android\platform-tools\adb.exe) returned an EMPTY device list at audit
  time. No device is connected. Capturing a CURRENT DEVICE UI BASELINE (app launch / main UI /
  transcript / provider selector / ADB state / session restore / tool transcript / running
  state) requires a connected device, which is unavailable this round.

Per the task rules, a missing device does NOT fail the audit:
  DEVICE_BASELINE = DEFERRED_WITH_REASON  (device unavailable; will be captured in
  AG.UI-DEVICE-ACCEPTANCE-1 where a device is required anyway).

No production code was modified to attempt a device baseline. The code-level current state
(single-Activity XML/View UI, single-TextView transcript, provider Spinner, AT status line,
New Session button, DebugBridge debug-only) is fully documented from source in this evidence
set (02/03/04/05/06/07/08/09/10), so the device step only needs to CONFIRM on hardware.

The prior round's device (HONOR ALI-AN00, serial APLMUT4312005935) was used for
FULL_USER_VISIBLE_RESUME verification; it is not connected for this audit round.


---

## 〔14〕 14_implementation_split.txt

AG.UI-BASELINE-AUDIT-1 鈥?Implementation Split

Based on the REAL code state (not a mechanical copy of the design's suggested phases).
Principle: the existing real Agent path (AgentRuntime.send / interruptActiveRun / newSession /
persistence / DebugBridge) is NEVER rewritten. VNext UI is a thin Presentation over it.

The design suggests AUDIT -> SHELL -> TRANSCRIPT-CARDS -> CONTROLS -> DEVICE -> RELEASE.
The audit CONFIRMS this ordering is sound, with two refinements:
  (a) The Session Browser (G22) needs a small ADDITIVE store-contract extension
      (listSessions) + a switch seam 鈥?this is a HOST/STORE seam, so it is placed in CONTROLS
      (not SHELL), and flagged as the one frozen-contract additive item.
  (b) Attachment->model-context (G16) needs a contract DECISION before UX; the SAF picker UX
      is in CONTROLS but the context-injection mechanism may be a small scoped Core/Run-options
      addition 鈥?called out explicitly so it is not accidentally invented in the UI.

========================================================================
STEP 1 鈥?AG.UI-SHELL-1
========================================================================
GOAL:
  Light workspace frame: Header (鈽?AndroidAgent + ADB鈼? / fixed Conversation Viewport /
  fixed bottom Composer / collapsible Sidebar frame. Replace the whole-page ScrollView with a
  fixed 3-zone layout. Light theme + card styling foundation. NO cards yet (viewport shows the
  existing transcript projection during transition).

ALLOWED CHANGES:
  - res/values/themes.xml (+ new colors/dimens/values) 鈥?light theme
  - res/layout/activity_chat.xml (restructure to 3-zone frame) + new sidebar/composer layouts
  - ChatActivity (layout wiring; header ADB dot; sidebar open/close)
  - NEW presentation resource files (layouts/drawables/values)
FORBIDDEN CHANGES:
  - No AG Core / Runtime / Tool / Provider / AT change
  - No persistence schema / Room entity / DAO change
  - No AgentRuntime / SessionHolder SEMANTIC change (re-wiring layout refs only)
  - No new AgentEvent / no streaming protocol change
  - No Compose migration (stay XML/View)
  - No product identity (icon/version) change
TESTS:
  - Existing :app tests remain green (assembleDebug + :app:test)
  - New: layout inflation smoke (no crash on create), theme applies
DEVICE GATE:
  - Visual: light theme renders; 3-zone frame fixed (content scroll only inside viewport);
    sidebar collapses/expands; header ADB dot reflects AT status; composer fixed bottom.
EXPECTED RESULT:
  The VNext workspace SKELETON is on device; the transcript still renders via the existing
  projection (single TextView or a placeholder list) 鈥?no card logic yet.

========================================================================
STEP 2 鈥?AG.UI-TRANSCRIPT-CARDS-1
========================================================================
GOAL:
  Card-based transcript: replace the single transcript TextView with a card list
  (RecyclerView or custom container) rendering UserCard / ThoughtCard / AssistantCard /
  ToolExecutionCard / FailureCard / InterruptedCard + section dividers + per-card Copy +
  native text selection + Thought/Result collapse. Streaming projection drives incremental
  card updates.

ALLOWED CHANGES:
  - NEW card layout files + a transcript list adapter in :app
  - TranscriptModel: keep Block model; optionally expose a stable per-block view-key for the
    adapter (PRESENTATION only; no Block semantics change)
  - ChatActivity / SessionHolder: bind the card list to transcriptModel.snapshot() instead of
    render(); keep onUiUpdate notification
  - Copy helper (ClipboardManager)
FORBIDDEN CHANGES:
  - No Block type removal / rename that changes semantics (Shell->generic is a PRESENTATION
    label; the Block can stay or be presentation-renamed without domain impact)
  - No AG Core / Runtime / Tool / Provider / AT / persistence change
  - No change to how blocks are CREATED (onReasoning*/onContent*/onToolCall*/onAnswerProduced/
    onRunFailed/onInterrupted stay as-is)
TESTS:
  - :app tests green; existing TranscriptModel tests (block creation/render) unchanged
  - New: adapter maps each Block type to the right card; streaming delta updates the current
    card in place; divider appears before User/Thought/Assistant; copy puts block body on
    clipboard; Thought collapses/expands; Result preview/expand.
DEVICE GATE:
  - A real run shows: User card, Thought (collapsible), Assistant (streams), ToolExecution
    (action+result, expand/collapse/copy), Failure/Interrupted where applicable; dividers
    correct; per-card copy works; selection works; long transcript scrolls INSIDE the viewport
    without moving header/composer.
EXPECTED RESULT:
  Interactive Transcript Cards live. The existing Agent path is unchanged (cards are a thin
  projection of TranscriptModel).

========================================================================
STEP 3 鈥?AG.UI-CONTROLS-1
========================================================================
GOAL:
  Card interactions + composer controls + sidebar content: Retry (re-send latest Turn),
  Edit & Resend, Session Browser (New/Switch/Current highlight/Rename/Today-Yesterday),
  ADB sidebar block (status + Reconnect), Provider/API Key Settings page, Model路Effort label,
  锛?Context/File (SAF) attachment.

ALLOWED CHANGES:
  - ChatActivity / Host: Retry (re-send composition), Edit & Resend (restore-to-composer + re-send)
  - Session Browser: sidebar list UI + an ADDITIVE TopicSessionStore.listSessions() + DAO query
    + a Host/Runtime switchToSession(id) seam (load that session's history + make current)
  - Rename: updateTitle store/DAO path OR a presentation-derived label (decision in this step)
  - Settings page (Provider/Endpoint/Key) re-placed; Test Connection + Save (Save = additive
    host write to the device-local config; REUSE the existing loader)
  - Model路Effort label (read active model from config; effort wiring scoped if required)
  - SAF file/folder picker + attachment chips + the attachment->model-context mechanism
  - Optional: DebugBridge "stop" client command (calls the SAME interruptActiveRun)
FORBIDDEN CHANGES:
  - No DB SCHEMA change (title/created_at/last_used_at/model/provider columns already exist;
    only ADDITIVE queries/methods)
  - No Core/Tool/Provider/AT REWRITE. The ONLY contract touches are ADDITIVE:
      * TopicSessionStore.listSessions() (additive method)
      * optional updateTitle (additive)
      * attachment->context mechanism (small scoped decision; if it requires a Core/RunOptions
        addition, it is a minimal additive change, NOT a rewrite 鈥?document it)
  - No second AgentRuntime / second transcript / second DB pipeline
  - Do NOT fake an effort a provider does not support
TESTS:
  - :app tests green; new tests for: listSessions ordering, switchToSession loads correct
    history, retry re-sends (host composition), edit&resend restores text + re-sends (new
    message, old durable UserMessage UNCHANGED), settings save writes config, SAF attachment
    adds a chip, model label reflects active config.
  - Verify old durable UserMessage is NOT modified by Edit & Resend.
DEVICE GATE:
  - Session browser: create/switch/highlight/rename; Today/Yesterday grouping correct.
  - Retry on latest completed turn re-runs; hidden while running.
  - Edit & Resend restores the user text, user edits, re-sends as a new message.
  - ADB sidebar shows Connected/Connecting/Disconnected + Reconnect works.
  - Settings: switch provider, test connection, save; main surface no longer shows provider/endpoint.
  - 锛?file/folder adds a chip; attachment reaches the model context per the chosen mechanism.
EXPECTED RESULT:
  All V1 interactive controls + sidebar + settings are functional on the frozen Agent path.

========================================================================
STEP 4 鈥?AG.UI-DEVICE-ACCEPTANCE-1
========================================================================
GOAL:
  Full real-device acceptance of the VNext UI end-to-end (the device baseline that was
  DEFERRED in the audit is captured here).

ALLOWED CHANGES:
  - Defect fixes discovered on device (presentation/host only; no Core/Runtime/Tool/Provider/AT
    /persistence-semantic changes unless a real defect requires it 鈥?and then a separate step)
FORBIDDEN CHANGES:
  - No architecture change; no frozen-boundary change
TESTS:
  - Full regression (all modules) green
DEVICE GATE:
  - Launch, light workspace, sidebar, cards (all types), streaming, copy/selection, retry,
    edit&resend, session browser, ADB, settings, model label, attachment, Stop/Interrupted,
    debug bridge still works (UI + debug share paths), release build has NO debug listener.
EXPECTED RESULT:
  VNext UI accepted on a real device.

========================================================================
STEP 5 鈥?AG.UI-RELEASE-FREEZE-1
========================================================================
GOAL:
  Product identity + release freeze: real launcher icon, versionCode/versionName bump, final
  label/light theme, status/nav bar styling, ABI confirmation, final product UI freeze.

ALLOWED CHANGES:
  - AndroidManifest.xml (icon), mipmap icons, build.gradle.kts (version), theme finalization
FORBIDDEN CHANGES:
  - No Agent behavior change
TESTS:
  - Full regression + release build (assembleRelease) green; release has no debug listener.
DEVICE GATE:
  - Final product look on device; release APK verified.
EXPECTED RESULT:
  A releasable VNext product UI freeze.

ORDER DEPENDENCIES:
  SHELL -> TRANSCRIPT-CARDS -> CONTROLS -> DEVICE-ACCEPTANCE -> RELEASE-FREEZE.
  (CONTROLS depends on the card list existing; DEVICE-ACCEPTANCE needs all UI in place;
   RELEASE-FREEZE is last.)


---

## 〔15〕 15_final_verdict.txt

AG.UI-BASELINE-AUDIT-1 鈥?Final Verdict

VERDICT = PASS 鈥?VNEXT_UI_BASELINE_AUDIT_COMPLETE_AND_IMPLEMENTATION_READY

ARCHITECTURE VERDICT (expected vs actual):
  AG_CORE_CHANGE_REQUIRED            = NO
  AG_RUNTIME_REDESIGN_REQUIRED       = NO
  TOPIC_SESSION_SCHEMA_CHANGE        = NO
  TOOL_CONTRACT_CHANGE_REQUIRED      = NO
  PROVIDER_ARCH_CHANGE_REQUIRED      = NO   (effort = optional additive config/wire, not arch)
  AT_CONSUMER_CHANGE_REQUIRED        = NO

  UI_PRESENTATION_REWRITE_REQUIRED   = YES
  HOST_ACTION_NORMALIZATION_NEEDED   = POSSIBLE (recommended; host-only, low risk)
  DEBUG_BRIDGE_REWRITE_REQUIRED      = NO (PRESERVE / REUSE)

  ANDROIDAGENT_V3_REQUIRED           = NO
  FREEZE_REOPEN_REQUIRED             = NO

  (One ADDITIVE store-contract extension 鈥?TopicSessionStore.listSessions() 鈥?is required
   for the Session Browser. It is ADDITIVE (no schema change; table + last_used_at index
   already exist; no frozen semantic broken: D1/D2/D3, sequence ownership, ordering intact).
   This is a scoped contract ADDITION, NOT a freeze reopen.)

PASS GATE CHECK (all required = met):
  FREEZE_TAG_UNCHANGED = YES (f640ed07... verified; tag not moved)
  VNEXT_DESIGN_SSOT_LOCATED = YES (docs/design/AndroidAgent_V2_VNext_UI_璁捐鍩虹嚎.md)
  VNEXT_DESIGN_SSOT_MODIFIED = NO
  UI_TECH_STACK_IDENTIFIED = YES (XML/View based)
  UI_SURFACE_INVENTORY_COMPLETE = YES
  TRANSCRIPT_CURRENT_MODEL_IDENTIFIED = YES (7 Block types; single TextView render)
  THINKING_TO_THOUGHT_PATH_IDENTIFIED = YES (same block, phase flip; SUPPORTED)
  ASSISTANT_STREAM_PATH_IDENTIFIED = YES (content deltas; SUPPORTED)
  TOOL_RESULT_PATH_IDENTIFIED = YES (generic command + rendered result; shell-payload-aware)
  CARD_MAPPING_COMPLETE = YES (all 6 target cards map to existing Block types)
  COPY_SELECTION_GAP_IDENTIFIED = YES (whole-TextView selectable; no per-block copy)
  RETRY_PATH_IDENTIFIED = YES (no rewind API; re-send composition = host-only)
  EDIT_RESEND_PATH_IDENTIFIED = YES (host composition; no domain change)
  STOP_INTERRUPT_PATH_IDENTIFIED = YES (interruptActiveRun -> Interrupted; working)
  SESSION_UI_PATH_IDENTIFIED = YES (New Session only; list/switch/rename = additive gaps)
  ADB_UI_PATH_IDENTIFIED = YES (status + reconnect + readinessState; re-place)
  PROVIDER_UI_PATH_IDENTIFIED = YES (node spinner + endpoint; move to settings; model/effort gaps)
  DEBUG_BRIDGE_PATH_IDENTIFIED = YES (send/observe shared; stop = gap)
  UI_DEBUG_ACTION_REUSE_MATRIX = COMPLETE
  SECOND_UI_DB_PIPELINE = NO
  SECOND_AGENT_RUNTIME = NO
  RELEASE_DEBUG_BOUNDARY_AUDITED = YES (BuildConfig.DEBUG gate; release has no listener)
  VNEXT_GAP_MATRIX = COMPLETE (24 gaps)
  IMPLEMENTATION_SPLIT = COMPLETE (5 steps)
  PRODUCTION_CODE_CHANGED = NO
  TEST_CODE_CHANGED = NO
  GRADLE_CHANGED = NO
  RESOURCES_CHANGED = NO
  CREATE_ANDROIDAGENT_V3 = NO

BUILD:
  APP_ASSEMBLE_DEBUG = PASS (BUILD SUCCESSFUL; :app:assembleDebug)
  REGRESSION: HEAD (b7dbc04) is docs-only after the freeze (f640ed0). No production change
    since the freeze, so the frozen 557-test / 0-failure baseline stands; a full re-run is not
    required for a docs/audit-only round. assembleDebug re-verified green this round.

DEVICE:
  DEVICE_BASELINE = DEFERRED_WITH_REASON (no device connected; captured in DEVICE-ACCEPTANCE-1)

GAP COUNTS (V1-relevant, excluding DEFERRED):
  BLOCKER_COUNT  = 0
  MAJOR_COUNT    = 14
  MINOR_COUNT    = 8
  POLISH_COUNT   = 1
  DEFERRED_COUNT = (G23 release identity + all design 搂28 deferred items)

KEY CONCLUSIONS:
  1. The current AndroidAgent ALREADY OWNS the real capabilities VNext needs:
     - block-based TranscriptModel (User/Thinking-Thought/Assistant/Shell-Result/Failure/
       Interrupted) with streaming + final de-dup + restore re-projection
     - AgentRuntime.send / interruptActiveRun / newSession / currentHistory (durable)
     - DebugBridge sharing the SAME send path + event stream (correct structure)
     - ADB status + reconnect + readiness via integration-at (boundary correct)
     - provider config (read-only, secret-safe) + node switch
  2. The VNext UI is a THIN PRESENTATION rewrite over these: fixed viewport + cards +
     dividers + copy/selection + retry/edit (host composition) + session browser (additive
     store query + switch seam) + composer + sidebar + settings. NO Agent rewrite.
  3. The only frozen-surface touch is ADDITIVE: TopicSessionStore.listSessions() (+ optional
     updateTitle) for the Session Browser. No schema change. No freeze reopen.
  4. Retry = re-send composition (host-only); strict Redo/Unrevert = DEFERRED (design 搂28).
     Edit & Resend = restore-to-composer + re-send (host-only; old durable message unchanged).
  5. Attachment->model-context is the one OPEN CONTRACT decision (SAF picker = presentation;
     context-injection mechanism = scoped step; do NOT invent a temp UI protocol).
  6. DebugBridge is PRESERVED/REUSED; an optional "stop" command (calls the SAME
     interruptActiveRun) is a small additive protocol extension (not required for V1).

NEXT = AG.UI-SHELL-1


