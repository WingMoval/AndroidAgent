# AG.UI-BASELINE-AUDIT-1 — VNext UI 实施前基线审计

> 本轮 = 审计 + 事实核验 + 现状→VNext UI 映射 + 实现阶段拆分 + 证据固化。
> **纯审计，不改 production**。所有结论均有源码/构建证据（见 `docs/evidence/ag_ui_baseline_audit_1/` 00-15）。
> **证据合并文件**：`docs/evidence/ag_ui_baseline_audit_1/ALL_EVIDENCE_CONSOLIDATED.md`（16 个分文件 00-15 的单文件合并，便于通读；分文件保留可单独查阅）。

---

## 1. Task Identity

```text
STEP = AG.UI-BASELINE-AUDIT-1
TYPE = UI 基线审计 + Presentation 边界审计 + 实现映射
REPO = D:\codebank\AndCode\AndroidAgent-V2
ARCHITECTURE_GENERATION = V2
UI_GENERATION = VNext
CREATE_ANDROIDAGENT_V3 = NO
PRODUCTION_IMPLEMENTATION = NO
UI_REWRITE = NO
ARCHITECTURE_REOPEN = NO
```

---

## 2. Git Baseline

```text
BRANCH = main
START_HEAD = b7dbc0491eb1bcb0fb07f0daf7f8a4919a9e67bd
FINAL_HEAD = b7dbc0491eb1bcb0fb07f0daf7f8a4919a9e67bd   (本轮 COMMIT=NO，HEAD 不变)
FREEZE_NAME = AG.POST-I-CORE-FREEZE-V1
FREEZE_TAG = AG.POST-I-CORE-FREEZE-V1
FREEZE_TAG_TARGET = f640ed07f655856888c11f3fb3575e6f84106f7c
FREEZE_MATCH = YES（`git rev-parse AG.POST-I-CORE-FREEZE-V1^{}` = 期望值）
FREEZE_TAG_MOVED = NO
REMOTE = NONE（无 remote；PUSH = N/A）
```

Worktree：起始仅 1 个 untracked = 用户设计稿（USER_OWNED_DESIGN_SSOT）。本轮新增 audit doc + evidence（TASK_OWNED）。**无其他未知 dirty change**。HEAD 在 freeze 之后（b7dbc04 > f640ed0）= EXPECTED post-freeze 状态。

---

## 3. VNext Design SSOT

```text
VNEXT_DESIGN_SSOT = docs/design/AndroidAgent_V2_VNext_UI_设计基线.md
LOCATED = YES（实际定位，非假设路径）
SIZE = 592 行
VNEXT_DESIGN_SSOT_MODIFIED = NO（USER_OWNED；本轮只读）
```

该文件是 VNext UI 目标设计的权威 SSOT。本审计把**现状代码**映射到该目标。核心目标锚点：浅色 Codex-like Workspace（§2-3）/ 固定三区分屏（§4-5）/ 卡片化 Transcript（§6-15）/ Codex Composer（§16-18）/ Submit→Stop（§19）/ Sidebar+Session（§20-21）/ ADB（§22）/ Provider 设置（§23）/ Debug 复用（§24）/ V1 必做与不做（§27-28）/ Architecture Guard（§29）/ V3 条件（§32）。

**Deferred（不得列为 V1 blocker，§28）**：Grok turn switching、Focus View、pinch、复杂动画、严格 Redo/Unrevert、任意 Fork、Checkpoint、Export、复杂高亮、Voice、新 Debug 协议、第二 Debug runtime、架构/Gradle 重构。

---

## 4. Frozen Architecture Baseline

```text
AG Engine Architecture   = FROZEN AT PRE-UI BASELINE
TopicSession Foundation  = FROZEN
Pre-UI Git Freeze        = COMPLETE (AG.POST-I-CORE-FREEZE-V1)
FULL_USER_VISIBLE_RESUME = REAL_DEVICE_VERIFIED
```

允许演进：Host / Product / Presentation / UI / Transcript projection / 用户交互。
不允许因 UI 重构重开：AG Core / Agent Runtime / Tool contract / Provider architecture / TopicSession 语义 / Persistence architecture / AT consumer boundary。

---

## 5. UI Tech Stack

```text
UI_TECH_STACK = XML/View based（非 Compose，非 Hybrid）
```

证据（evidence/03）：
- `app/build.gradle.kts`：插件 = `com.android.application` + `kotlin(android)` + KSP。**无 Compose 插件/BOM/compiler**。模块注释自述 "the UI stays deliberately minimal — no Compose, no DI, no Navigation"（Room 仅为 persistence 加入，UI 仍是 plain Views）。
- `res/layout/activity_chat.xml`：`<ScrollView>` 根 + 垂直 `<LinearLayout>`，纯控件（TextView 标题 / Spinner provider / TextView endpoint / Button reconnect / EditText+Button pair / TextView status / View divider / **TextView transcript（textIsSelectable=true）** / EditText input / 横向 LinearLayout 含 send/stop/new_session）。
- `ChatActivity extends AppCompatActivity`，直接引用 view，wiring Spinner onItemSelected + Button onClick。无 Fragment / Navigation / ViewModel / Compose。

**对 VNext 的含义**：现状是随内容增长的整页 ScrollView。VNext 目标（固定 Header / 固定 Viewport / 固定底部 Composer + 内部滚动 + 卡片）需把这个单一 layout 重构成固定三区框架，并把单一 transcript TextView 换成卡片化滚动列表。**这是纯 Presentation 改动**；设计稿框架无关，**不要求迁移 Compose**（默认继续 XML/View，与现状一致）。

---

## 6. Current Host / Activity Architecture

```text
App 启动入口 = ChatActivity（唯一 Activity；LAUNCHER + MAIN）
主要页面     = activity_chat.xml（整页 ScrollView + 单一 transcript TextView）
Composition Root = ChatCompositionRoot（被 SessionHolder.createRoot 构造，process-scoped）
Runtime ownership = ChatCompositionRoot.runtime（AgentRuntime；process-scoped；跨 Activity 重建存活）
Session 持有者   = SessionHolder（process-scoped object）+ AgentRuntime（durable id）
```

真实运行路径（evidence/04）：

```text
App launch
  ↓ ChatActivity onCreate
Host init
  ↓ DeviceLocalProviderNodeLoader.load(ctx, node)  [读 filesDir/<node>.json]
  ↓ resolveProviderSwitch -> Applied | Rejected
  ↓ applyConfig: SessionHolder.createRoot(...)  [建 ChatCompositionRoot，process-scoped]
  ↓ ensureDebugBridge()  [非 DEBUG 直接 return]
  ↓ connectShell()       [AT Engine 自动连接]
  ↓ lifecycleScope { SessionHolder.initialize() }
Runtime restore/create
  ↓ AgentRuntime.initialize()  [create-or-restore durable TopicSession + 受控恢复]
  ↓ 成功 -> projectCurrentHistory(): runtime.currentHistory() -> transcriptModel.reproject()
Transcript projection
  ↓ SessionHolder.transcriptModel（process-scoped）持有 List<Block>
  ↓ onRunEvent(AgentEvent) / onStreamEvent(kind,text) 变更 blocks
UI render
  ↓ SessionHolder.onUiUpdate -> ChatActivity.renderTranscript()
  ↓   transcript.text = transcriptModel.render()   [单一 TextView]
```

**Activity 是否承担过多状态？** 否——状态在 SessionHolder/Runtime（process-scoped），Activity 是薄消费者（onCreate/onResume 经 onUiUpdate 重绑）。但 ChatActivity（594 行）是单一巨型 controller，混合了 provider 切换 / config apply / debug wiring / AT connect+reconnect / ADB pair / run status / transcript render / Send/Stop/NewSession。**VNext 应把 controller 职责拆到 sidebar/composer/transcript/settings 等聚焦组件**，但**状态所有权（SessionHolder+Runtime）已符合 VNext guard**（DB→Runtime/Core→Presentation projection→UI Cards），卡片化不需要改状态所有权，只改 presentation 层。

---

## 7. Current Transcript Architecture

```text
TRANSCRIPT_RENDERING = 单一 TextView（render() -> 一个 string -> textIsSelectable=true）
```

TranscriptModel.Block（sealed）现有 7 类（evidence/06）：`User` / `Thinking(text,phase)` / `Assistant(text,final)` / `Shell(command)` / `Result(text)` / `Failure(text)` / `Interrupted(text)`。

- **不是整页单一数据，而是已 block 化**：数据层（Block 列表）已经是 7 类语义块，**但渲染层是 `render()` 拼成一个 string 塞进单一 TextView**——无 per-block view、无卡片、无 per-block copy、无 collapse。
- `fromRendered()` 是 **lossy 重建**，仅在 Activity 重建且 holder model 为空时用于 rebind；live holder model 才是权威。
- 线程安全：`blocks` 是普通 ArrayList，用 `lock` 同步（IO dispatcher 写 + UI 线程读）。

**结论：数据已 block 化（可直接映射成 Card），仅渲染未 card 化。** 这是 VNext 卡片化的关键利好——**不需要改 TranscriptModel 的 Block 语义，只需换渲染层**。

---

## 8. Thinking / Assistant Streaming

**THINKING_TO_THOUGHT = SUPPORTED**（evidence/06 已逐行核验，非依赖旧报告）：
- `onReasoningStarted()` 新建 `Thinking(THINKING)`；`onReasoningDelta` 追加（同 block）；`onReasoningFinished()` 把**最近一个仍 THINKING** 的 block 翻成 THOUGHT（**不新建第二个 block**）。
- 完全符合设计 §11（Thinking→Thought 是同一 reasoning block / 同一张 Card 的生命周期变化，不生成重复卡片）。**本轮不得重新实现。**
- 无 reasoning 的 model 不产生 Thinking/Thought block（正确）。
- 恢复的 transcript 无 Thought（reasoning 不持久化，frozen）——正确，不伪造。

**ASSISTANT_STREAMING = SUPPORTED**：
- `onContentStarted/onContentDelta` 每个 model turn 增长**一个** Assistant block（增量流式，符合设计 §12 "必须流式增加，不能等完整 ModelTurn"）。
- `onAnswerProduced` 去重：若答案已流式进 Assistant block，则标记 `final=true` 不追加重复 block；否则新建 fallback。
- **Formal Output != FinalAnswer 正确表达**：中间 Assistant（带 toolCalls）= `final=false` 保留；最终答案 = `final=true`。

---

## 9. Tool / Result Rendering

```text
TOOL_TRANSCRIPT_CURRENT_MODEL = 通用 command block（名为 Shell）+ rendered Result block（shell-payload 感知）
```

- **Tool Action**：`Block.Shell(commandOf(call))`。`commandOf` 从**任意 tool** 的 JSON args 提取 `command`；非 shell tool 显示原始 args。block **名为 Shell 但提取逻辑通用**——卡片化时应改用 `call.name`（tool id）+ arguments，不假设 shell（**纯 presentation 改名/泛化，无 domain 改动**）。
- **Tool Result**：`Block.Result(renderResult(result))`。`renderResult` 解析 android_shell JSON payload（exitCode/stdout/stderr），`!success` 追加 `error:`。tool 级 failure 就近显示在 Result block（符合设计 §14）。
- **数据足够通用，可直接映射成 ToolExecutionCard**（Shell/File/Network 统一渲染）。当前不是"只针对 Shell 写死"——渲染是 shell-payload 感知的，非 shell 走 verbatim；只是 block 命名 + 展示需泛化。
- **Tool 输出有上游 bounds**（evidence/06/11）：file tool 有 `truncated` 标志 + maxBytes；network 有 byte/char cap。UI collapse **不得假装能展开 tool 层已截断的数据** → `FULL_RESULT_AVAILABLE_TO_UI = SOMETIMES`（受 tool cap 限制）。

---

## 10. Current Card Feasibility

期望 `NEEDED DOMAIN CHANGE = NO` —— **全部 6 张目标 Card 都满足**（evidence/06 + 11）。

| TARGET CARD | CURRENT DATA SOURCE | CURRENT BLOCK | CURRENT RENDER | NEEDED PRESENTATION CHANGE | NEEDED DOMAIN CHANGE | RISK |
|---|---|---|---|---|---|---|
| UserCard | user input / debug sink / reproject | `Block.User` | 单一 TextView "User\n<text>" | 卡片 view + adapter + Copy/Retry/Edit/More | **NO** | 低 |
| ThoughtCard | reasoning stream | `Block.Thinking`(phase) | 单一 TextView "Thinking.../Thought\n<text>" | 卡片 + Collapse/Expand/Copy；同 block 生命周期 | **NO** | 低（语义已匹配 §11） |
| AssistantCard | content stream + AnswerProduced | `Block.Assistant`(final) | 单一 TextView "Assistant\n<text>"（流式） | 卡片 + Copy/Select/Retry | **NO** | 低（流式+去重已正确） |
| ToolExecutionCard | ToolCallStarted/Finished | `Block.Shell`+`Block.Result` | 单一 TextView "Shell\n$cmd" + "Result\n<rendered>" | 统一卡片（action+result 视觉组合、语义独立）；改用 call.name+args；Preview/Expand/Collapse | **NO** | 低（泛化命名） |
| FailureCard | RunFailed | `Block.Failure` | 单一 TextView "Failed\n<text>" | 卡片 + Copy/Select | **NO** | 低 |
| InterruptedCard | RunFinished(Interrupted) | `Block.Interrupted` | 单一 TextView "Stopped\n<text>" | 卡片（非 Failure）+ 可重跑 originating Turn | **NO** | 低（Interrupted!=Failure 已正确） |

**没有任何 Card 需要修改 Core/Runtime/Persistence。** 无 BLOCKER。

---

## 11. Copy / Text Selection

```text
COPY_SINGLE_BLOCK   = GAP（当前无 per-block copy；纯 presentation 可补）
NATIVE_TEXT_SELECTION = PARTIAL（整页 TextView selectable；卡片化后需 per-card selection）
COPY_TRANSCRIPT     = 现状=整页可选（单一 TextView）
```

- 当前 `transcript` TextView `textIsSelectable=true` → 整段可选中复制，但**无法 per-block copy**。
- **无 ClipboardManager 使用、无 copy helper**（源码 0 命中）。
- 实现 per-block Copy = **纯 Presentation change**（卡片 copy 按钮 + ClipboardManager helper；block 正文在 TranscriptModel 已可直接取到）。无需任何 domain 改动。

---

## 12. Retry / Re-run

```text
LATEST_TURN_RETRY   = PARTIAL（re-send 组合 = host-only 可行；严格 redo = DEFERRED）
HISTORICAL_TURN_RETRY = DEFERRED（设计 §28 明确不做任意历史 Fork / 严格 Redo）
```

- **Runtime 无 rewind/retract/rollback/retry/resend/removeMessage API**（ag-core/ag-runtime/app 全扫描 0 命中）。`AgentRuntime` 只有 `initialize/newSession/send/interruptActiveRun/currentHistory`。
- 设计稿 ↻ "重做" 第一稿语义 = **Retry / Re-run originating User Turn**（**非**严格 Redo/Unrevert）。
- 因此 "Retry 最新已完成 Turn" 可**用现有能力组合**：把该 User block 的 text 重新 `AgentRuntime.send()` 作为**新 turn**（Host composition，读 TranscriptModel 的 User block → 走现有 send 路径）。**NO Core/Runtime change。**
- 但**严格 retry（丢弃上一个失败/部分 turn 再重做）** 需要 history rewind/retract = **不存在** = 会跨 frozen 边界。设计 §28 已把严格 Redo/Unrevert 列为不做。
- **结论**：V1 的 Retry 实现为 **re-send 组合**（host-only）；严格 redo 标记 DEFERRED。**本轮不改。**

---

## 13. Edit & Resend

```text
EDIT_RESEND = 可纯 Host composition 实现（无 domain change）
```

- 设计语义（§10）：选旧 User Turn → 恢复原 text 到 Composer → 用户编辑 → **重新发送（走现有 valid path）**。**禁止直接改旧 durable UserMessage。**
- 现状：**无 rewind/retract API**；**无 draft restore**；Composer 是普通 EditText（`input`），无 per-turn 关联。
- 设计语义本身是 "恢复到 Composer + 重新发送（**新 message**）"，**不是**修改旧 durable message。因此 = **纯 Host composition**：读 User block text（TranscriptModel）→ set composer text → 用户编辑 → 现有 send 路径产生**新** UserMessage。**NO Core/Runtime/DB change。**
- **V1 Edit & Resend 能只通过现有 capability composition 实现 = YES。**

---

## 14. Stop / Interrupt

```text
STOP_UI_BACKEND_READY = YES
```

真实链路（已核验，非假定 method name）：
- UI Stop 按钮 → `onStopRun()` → **`AgentRuntime.interruptActiveRun()`**（真实 API）。
- 返回 `InterruptResult { Requested / AlreadyInterrupting / NoActiveRun }`。
- 取消经 coroutine cancellation 传播 Core→Provider/Tool→Transport；`send()` 把 USER 子取消转成 Host 可见的 **`RunOutcome.Interrupted`**（**非 Failure**）。
- 终态事件 `RunFinished(Interrupted)` 经 `SessionHolder.onRunEvent` → `TranscriptModel.onInterrupted()`（追加 Interrupted block）+ `isBusy=false`。
- HOST-STOP-1 已在真机验证（D1-D4 PASS）。

**UI 只需在 Composer 做 Idle:↑ / Running:■ 的 toggle，复用现有 `onStopRun`。** 无后端改动。

---

## 15. Interrupted Presentation

```text
用户主动 Stop != Failure —— 当前已足够区分
```

- Run outcome 层：`RunOutcome.Interrupted`（独立于 `RunFailure`）。
- Agent event 层：`RunFinished(outcome=Interrupted)`（终态事件，恰好一个）。
- Transcript projection 层：`Block.Interrupted`（独立 block，渲染 "Stopped\n..."，**不是** "Failed"）。
- Persistence 层：partial history 按 CORE-007 持久化（in-flight tool 追加 synthetic interrupted ToolResult，保留配对不变量）；Interrupted 本身是 run outcome 不是 Message。

**结论：Interrupted 与 Failure 在 outcome/event/persistence/transcript 四层都已区分。UI 只需 projection（InterruptedCard）。无 architecture/semantics gap。**

---

## 16. Conversation Viewport

```text
当前 = 整页 ScrollView（无固定 viewport）→ Header/Composer 随内容滚动
```

- `activity_chat.xml` 根 = `<ScrollView fillViewport=true>`，里面是垂直 LinearLayout。**对话增长会改变整页高度**（Header、provider、status、transcript、input 全部在一个滚动容器里）。
- 目标：Header 固定 / Viewport 占剩余 / Composer 固定底部 / Transcript 内部滚动。
- **最小改造方案**：把根 ScrollView 替换为固定三区框架（`ConstraintLayout` 或垂直 `LinearLayout`：Header `wrap_content` + Viewport `0dp/weight=1` 内含 transcript 滚动容器 + Composer `wrap_content`）。transcript 滚动容器 = 卡片列表（RecyclerView 或 NestedScrollView）。**纯 layout 改动。**

---

## 17. Composer

目标：`＋ 输入……    Model·Effort    ↑/Stop`（固定底部）。

当前主面控件 → VNext 处置：

| 当前控件 | 当前位置 | VNext 处置 |
|---|---|---|
| EditText `input`（hint "Message"） | 滚动页内 | **KEEP**（移入固定 Composer） |
| Button `send` | 滚动页内 | **REPLACE**（Composer 的 ↑ Submit，Idle 态） |
| Button `stop`（默认 disabled） | 滚动页内 | **REPLACE**（Composer 的 ■ Stop，Running 态；与 Submit 同位 toggle） |
| Button `new_session` | 滚动页内 | **MOVE_TO_SIDEBAR**（Sidebar 顶部 "+ New Session"） |
| Spinner `provider_node` + endpoint | 主面顶部 | **MOVE_TO_SETTINGS**（Provider/API Key 设置页） |
| Button `reconnect` + pair | 主面 | **MOVE_TO_SIDEBAR**（Sidebar ADB 块） |
| TextView `status` | 主面 | **MOVE**（run 状态进 Viewport/状态条；AT 状态进 Sidebar ADB 块） |
| ＋ Context/File | 无 | **ADD**（SAF 附件，见 §18） |
| Model·Effort | 无 | **ADD**（Composer 标签，见 §19） |

**不实施**（本轮只审计）。

---

## 18. Attachment / Context

```text
ATTACHMENT_CONTEXT = GAP（SAF picker = presentation；context-injection = 契约决策）
```

- 当前**无** attachment UI、**无** SAF file picker（app 内 39 处 Uri/content 命中来自 AT/transport 代码，**不是**用户文件选择器）。
- FileTool 是 READ-only over `filesDir/agent/`（APP_SELF realm）——**那是 TOOL，不是 attachment-context 输入**。
- 设计 §17：＋ 添加文件/文件夹（Android SAF）；chip 展示；**不把文件正文塞进输入框**。**附件进入模型上下文 ≠ FileTool read。**
- **GAP**：当前无 attachment/context 契约。"附件进入模型上下文" 需要决定注入机制（如 instruction/context block 或新输入通道），**这可能触及 Core（context 如何到达 model）**。按设计 §23，**不得为 UI 临时造一套输入协议** → 标记 GAP，在 CONTROLS 步骤用一个 scoped step 解决（SAF picker UX 是 presentation；context 注入机制是契约决策，若需 Core/RunOptions 增量则是**最小 additive 改动**，非重写）。

---

## 19. Model / Effort Selector

```text
MODEL_SELECTOR = GAP（无 picker；model 来自 config）
EFFORT         = GAP（当前不可配置；provider config/wire 可能需增量；不得伪造）
```

- 当前**无 model selector、无 effort selector**。model 来自 device-local config 文件的 `model` 字段。
- `OpenAiCompatibleConfig` 字段：`baseUrl` / `apiKey` / `model` —— **无 reasoning effort / thinking / temperature 字段**；provider 当前不发 reasoning-effort 参数。
- 设计 §18 要 "Model · Effort"。**effort 可配置需**：(a) `OpenAiCompatibleConfig` 加字段 + wire 请求（reasoning effort）；(b) provider 发出它。**这是 provider config/wire 增量**。
- **不同 Provider 不一定都支持 effort**，UI **不得伪造** provider 不支持的 effort（设计 §24）。
- **结论**：model 标签（读 active config 的 model）= presentation，可在 SHELL 做；effort wiring 是 scoped provider 增量（若 V1 需要）。**本轮不改。**

---

## 20. Provider / API Key Settings

```text
PROVIDER_SETTINGS = partial（node spinner + endpoint 在主面；移入 Settings = presentation）
```

- 当前：Provider Spinner（OPENROUTER/EMG 两 node）+ endpoint 标签在主面。API key 从 device-local config 文件读（**只读**，app 从不写）。当前 UI 无 in-app API key 录入（node Spinner + endpoint）。
- 目标（§23）：Provider/API Key/Endpoint 移入 Settings 页（不常驻主面）；Test Connection + Save。
- **复用现有 config 系统**（`DeviceLocalProviderConfigLoader` + `OpenAiCompatibleConfigLoader.parse`），**不得造第二套**（设计 §25）。
- "Save"（写 config 文件）当前不存在（文件只读）→ 若设计 Save 必需，是 **additive Host write 路径**（非 Core）。"Test Connection" 亦为新 Host 能力。
- **secret handling（审计规则）**：API key 从不 log/echo；loader 把失败映射为 safe 类别（REASON_MISSING/REASON_INVALID），不暴露 raw JSON；只展示 `safeChatCompletionsEndpoint`。**本轮 evidence 无任何 API key / Authorization / secret。**

---

## 21. TopicSession / Session Browser

```text
SESSION_BROWSER = NOT IMPLEMENTED（当前仅 New Session 按钮）
SESSION_TITLE   = 字段存在（nullable，生产从未填值）；rename = GAP
SESSION_LIST    = GAP（需 list-all 查询 —— additive store/DAO 方法）
SESSION_SWITCH  = GAP（无 UI 切换；无 Runtime switchToSession）
```

现有持久化能力（支持 sidebar 的事实，evidence/07）：
- `TopicSessionEntity`：`id / created_at / last_used_at / model / provider / title(nullable)`。
- `TopicSessionStore`（ag-runtime 契约）：`createSession / appendMessage / loadSession(id) / loadLatestSession() / loadMessages(id) / updateModelProviderMetadata`。
- `TopicSessionDao`：`insert / findById / findLatest / updateModelProvider / probe`。

**V1 Session Browser 的 gap**：
1. **无 list-all-sessions 查询**：store/DAO 只暴露 `loadLatestSession`（LIMIT 1）+ `loadSession(id)`。sidebar 列表需 `listSessions()`（ORDER BY last_used_at DESC）。这是 **PERSISTENCE/STORE 增量**（**非 schema 改动**——表 + last_used_at 索引已存在，SELECT-all 是 additive）。它触及 ag-runtime `TopicSessionStore` 契约（加方法）+ Room impl + DAO query。**这是唯一跨 frozen TopicSessionStore 契约面的项**（见 §27）。
2. **无 session switch API**：`AgentRuntime` 有 `newSession()` + `initialize()`（create-or-restore latest），**无 `switchToSession(id)`**。切换到旧 session 需加载其 history 并设为 current = Runtime/Host seam 增量。
3. **title 字段存在但生产从未填值**（Runtime 调 `createSession` 时 title=null；无自动生成、无 rename 路径）。设计要 Rename → V1 rename = additive `updateTitle` store/DAO 方法 **或** presentation-derived label。按设计 §27 **不得新增 title 列**（已存在）。
4. **Today/Yesterday/Earlier 分组** = 从 `last_used_at/created_at` **presentation 派生**（**无 schema 改动**，列已存在）。

**关键：这些都不需要 DB SCHEMA 改动（列已存在）。** 需要 additive STORE/DAO 查询 + Runtime/Host switch seam + UI。

---

## 22. ADB UI

```text
ADB_STATUS_READY   = YES
ADB_RECONNECT_READY = YES
```

- 当前：无 header ADB 指示；ADB/AT 状态是主面状态行 "AT: Connected/Not connected/Pairing required..."。有 "Reconnect" 按钮（connectShell）+ "Pair"（6 位码，首用 bootstrap）。ADB 状态**不在 transcript**（正确，是状态行）。
- **数据来源（已核验，UI 不绕过 integration-at）**：`connectShell()` → `root.shell.connect()` → `ShellConnection`（Host）wraps `AtExecutionAdapter`（:integration-at）。状态经 `HostStatusModel.safeAtMessage` 折叠为短 secret-free 行。ADB 语义状态经 `CompositionAdbSemantic`（Host 桥到 `ATAndroidAdbControl`）：`readinessState() {READY/DISCONNECTED/CONNECTING/UNKNOWN}` / `bootstrapState()` / `ensureReady()` / `hasIdentity()` / `discoverPairing()` / `pairDiscovered(code)`。
- `AtExecutionAdapter` 是**唯一 AT 消费点**；AG Core/Runtime **无**直接 AT 依赖（frozen 边界成立）。
- header 圆点 + sidebar Reconnect = **现有能力的纯 presentation 重定位**。无 domain/AT 改动。

---

## 23. Existing DebugBridge

```text
DEBUG_BRIDGE = PRESERVE / REUSE（结构已正确；无需重建）
DEBUG_SEND_PATH    = SHARED（AgentRuntime.send）
DEBUG_OBSERVE_PATH = SHARED event stream（side channel）
DEBUG_STOP_PATH    = NOT IMPLEMENTED（gap；可选 additive 协议扩展）
```

现状（evidence/10）：
- TCP JSONL server，protocol `ag-debug-0`，`DEFAULT_PORT=45678`。debug build 绑 0.0.0.0（LAN PC 直连）；**release build 永不创建 DebugBridge**（`ensureDebugBridge()` 非 DEBUG 直接 return）。
- `runtimeProvider = { SessionHolder.runtime }` = **与 UI 同一个 AgentRuntime**（**非**第二 runtime）。`toolRegistryProvider` = 同一生产 ToolRegistry；`adapterProvider` = 同一 AtExecutionAdapter。
- `eventSink` 接入 Host CompositeSink → **每个 AgentEvent**（UI 或 debug 起源）广播给所有 debug client。`streamSink` 广播 reasoning/content deltas。
- **DEBUG_SEND**：`ClientMessage.Send` → `handleSend` → blank/busy 检查 → `userInputSink`（UI User block）+ `onRunStateChanged`（busy）→ **`runtime.send(text)`**（与 UI 同路径）。**ALREADY_SHARED。**
- **DEBUG_OBSERVE**：`eventSink.emit` → 单线程 send executor 有序广播（off main thread）。是**同一 event stream 的被动 side channel**（喂 UI transcript 的同一条流）。**非**第二 transcript SSOT。结构已正确（Event Stream → {UI ; Debug}）。
- **DEBUG_STOP**：`ClientMessage` 集合**无 stop/interrupt 命令**（ping/send/tool_invoke/session_*/bootstrap_*）。PC 无法停止 active Run。UI Stop（interruptActiveRun）当前 UI-only。加一个 "stop" client 命令（调**同一** `interruptActiveRun()`）= 小 additive 协议扩展（protocol-compatible；旧 client 忽略未知 type）。**非**第二 runtime。
- **DEBUG TOOL INVOKE**（direct，无 LLM）：`ToolInvoke` → `registry.dispatch(call, AllowAll)` → `tool_result_direct`。共享生产 ToolRegistry；非 model-visible。（现有，保留。）

---

## 24. UI Callback ↔ Debug Action Reuse（Deliverable 3）

```text
UI callback  ─┐
              ├─→ Shared Host Action → AgentRuntime
Debug command─┘
```

| UI ACTION | DEBUG ACTION | CURRENT COMMON ENTRY | DUPLICATION | TARGET COMMON ENTRY |
|---|---|---|---|---|
| Send | `send` | `AgentRuntime.send` | **ALREADY_SHARED**（UI onSend 与 Debug handleSend 都调 runtime.send；Debug 经 userInputSink 镜像 User block + busy） | 保持（可选抽 Host Action.Send） |
| Stop | （无） | `AgentRuntime.interruptActiveRun`（仅 UI） | **UI_ONLY**（Debug 无 stop 命令） | 加 Debug `stop` → 同一 interruptActiveRun |
| Retry / Re-run | （无） | 无现有 API（re-send 组合 = host-only） | **NOT_IMPLEMENTED**（两侧皆无） | Host Action.Retry（re-send 组合） |
| Edit & Resend | （无） | 无（host composition） | **NOT_IMPLEMENTED** | Host Action.EditResend（restore+re-send） |
| New Session | （无） | `AgentRuntime.newSession`（仅 UI） | **UI_ONLY** | Host Action.NewSession（可选加 Debug） |
| Switch Session | （无） | 无（需 additive store query + switch seam） | **NOT_IMPLEMENTED** | Host Action.SwitchSession |
| ADB Reconnect | （bootstrap_state/discover/pair 有，reconnect 无直接命令） | `ShellConnection.connect`（仅 UI onReconnect） | **PARTIALLY_SHARED**（Debug 有 bootstrap 查询，无 reconnect 命令） | Host Action.AdbReconnect（可选加 Debug） |
| Provider Switch | （无） | `DeviceLocalProviderNodeLoader` + `resolveProviderSwitch`（仅 UI Spinner） | **UI_ONLY** | Host Action.ProviderSwitch |

**重点（业务语义是否藏在 ChatActivity onClick 中）**：
- Send/Stop/NewSession 的**语义**大多在 Runtime/SessionHolder（好），但 **Activity callback 仍拥有**：busy-button 调度、status-string 格式化、send 预检查（null/busy）、debug-bridge wiring。
- **列为 UI VNext implementation debt**（`HOST_ACTION_NORMALIZATION_NEEDED = POSSIBLE`）：抽一个薄 Host Action 层，让 UI callback **与** Debug command 共享每个 action 的一个入口（Send/Stop/NewSession/...）。这是 **PRESENTATION/HOST 改动，不跨 Core/Runtime**。**本轮不抽取。**

---

## 25. Release / Debug Boundary

```text
Release APK 是否监听 debug port   = NO
Release 是否输出 debug logs       = NO（bridge 不创建；AgentTrace 经 TraceFormatter redact 后才 Log.i）
Release 是否可接收 PC debug 命令  = NO
```

- 单一 gate：`ensureDebugBridge()` 内 `if (!BuildConfig.DEBUG) return`。
- DebugBridge 是 **process-scoped**，仅在 debug build 构造。Release：无 listener、无 debug port、无外部 debug ingress、无 bridge debug 日志。
- 本轮只审计，不改变。沿用已有安全规则。

---

## 26. UI Product Identity（为 AG.UI-RELEASE-FREEZE-1 建 gap list）

```text
applicationId = com.androidagent.app
versionCode   = 1
versionName   = "0.1.0"
app label     = "AndroidAgent"
launcher icon = @android:drawable/sym_def_app_icon（默认系统图标 —— 必须替换）
theme         = Theme.AppCompat.DayNight.DarkActionBar（非 VNext 浅色 workspace theme）
minSdk        = 26
targetSdk     = 34
compileSdk    = 34
permissions   = INTERNET / ACCESS_NETWORK_STATE / ACCESS_WIFI_STATE / CHANGE_WIFI_STATE /
                WRITE_SECURE_SETTINGS(sig|priv) / ACCESS_COARSE+FINE_LOCATION(maxSdk 35)
allowBackup   = false
ABI           = arm64-v8a only（继承 AT handoff v0）
```

**Gap list（RELEASE-FREEZE-1 处理，本轮不改）**：① 默认系统图标→真实产品图标；② versionCode 1 / versionName 0.1.0→发布时 bump；③ DayNight.DarkActionBar→VNext 浅色 workspace theme（V1 先 Light）；④ status/nav bar 适配浅色；⑤ 确认 label；⑥ 确认 arm64-only 可接受；⑦ 确认 WRITE_SECURE_SETTINGS 发布姿态。

---

## 27. Design → Current Component Mapping（Deliverable 1 映射表）

| VNext Target | Current Implementation | Data Source | Required Change | Layer | Risk |
|---|---|---|---|---|---|
| Light Workspace | Theme.AppCompat.DayNight.DarkActionBar | res/values/themes.xml | 定义浅色 theme + 卡片样式 | Presentation | 低 |
| Collapsible Sidebar | 无（单一主面） | TopicSession / ADB / Provider | 新增 sidebar layout + controller | Host UI | 中 |
| Fixed Viewport | 整页 ScrollView | activity_chat.xml | 根改固定三区框架；transcript 内部滚动 | Presentation | 中 |
| UserCard | "User\n<text>" 单一 TextView | `Block.User` | 卡片 view + adapter + Copy/Retry/Edit/More | Presentation | 低 |
| ThoughtCard | "Thinking.../Thought\n<text>" 单一 TextView | `Block.Thinking`(phase) | 卡片 + Collapse/Expand/Copy（同 block 生命周期） | Presentation | 低 |
| AssistantCard | "Assistant\n<text>" 单一 TextView（流式） | `Block.Assistant`(final) | 卡片 + Copy/Select/Retry | Presentation | 低 |
| ToolExecutionCard | "Shell\n$cmd"+"Result\n<rendered>" 单一 TextView | `Block.Shell`+`Block.Result` | 统一卡片（call.name+args 泛化；Preview/Expand/Collapse） | Presentation | 低 |
| FailureCard | "Failed\n<text>" 单一 TextView | `Block.Failure` | 卡片 + Copy/Select | Presentation | 低 |
| InterruptedCard | "Stopped\n<text>" 单一 TextView | `Block.Interrupted` | 卡片（非 Failure）+ 可重跑 | Presentation | 低 |
| Section Divider | 无 | block type ordering | 卡片 list divider decoration（rendering，非持久化） | Presentation | 低 |
| Copy (per-block) | 整页 selectable；无 per-block | TranscriptModel block text | 卡片 copy 按钮 + ClipboardManager | Host UI / Presentation | 低 |
| Retry / Re-run | 无 API | TranscriptModel User block + `AgentRuntime.send` | re-send 组合（host-only）；严格 redo DEFERRED | Host | 中 |
| Edit & Resend | 无（composer 无 per-turn） | TranscriptModel User block + composer | restore-to-composer + re-send（新 message） | Host | 低 |
| Stop | UI Stop → `interruptActiveRun`（已工作） | `AgentRuntime` | Composer Idle:↑/Running:■ toggle，复用 onStopRun | Host | 低 |
| Session Browser | 仅 New Session 按钮 | TopicSession（title/created_at/last_used_at） | sidebar list + additive `listSessions` + switch seam + rename | Host + Store(+契约 additive) | 中 |
| ADB Indicator | "AT:<status>" 行 + Reconnect + Pair | `ShellConnection`/`CompositionAdbSemantic` | header 圆点 + sidebar ADB 块（重定位） | Host UI | 低 |
| Model Selector | 无（model 来自 config） | `OpenAiCompatibleConfig.model` | Composer "Model·Effort" 标签（读 active model） | Presentation + Provider config | 中 |
| Provider Settings | Spinner + endpoint 在主面 | device-local config（只读） | 移入 Settings 页；Save=additive host write；复用 loader | Host UI | 低 |
| Attachment (SAF) | 无 picker；无 context 契约 | （无） | SAF picker（presentation）+ context 注入机制（契约决策） | Host + (Core 决策) | 中 |
| DebugBridge | TCP JSONL 0.0.0.0:45678（debug only） | runtime/events | **preserve**；可选加 stop 命令 | Host debug | 低 |

**无 `?` 残留。**

---

## 28. Gap Matrix（Deliverable 2）

完整 24 项见 `docs/evidence/ag_ui_baseline_audit_1/11_vnext_gap_matrix.txt`。摘要（V1 相关，排除 DEFERRED）：

| DESIGN_ITEM | CURRENT_STATE | TARGET_STATE | OWNER_LAYER | FILES | SEVERITY | ARCHITECTURE_IMPACT | NEXT_STEP |
|---|---|---|---|---|---|---|---|
| G1 Light Theme | DayNight.DarkActionBar | 浅色 workspace | Presentation | themes.xml | MAJOR | NO | SHELL-1 |
| G2 Sidebar | 无 | Collapsible sidebar | Host UI | activity_chat.xml + 新 layout | MAJOR | NO | SHELL-1 + CONTROLS-1 |
| G3 Fixed Viewport | 整页 ScrollView | 固定三区 | Presentation | activity_chat.xml | MAJOR | NO | SHELL-1 |
| G4 UserCard | 单一 TextView | UserCard | Presentation | 卡片 layout + adapter | MAJOR | NO | TRANSCRIPT-CARDS-1 |
| G5 ThoughtCard | 单一 TextView | ThoughtCard（collapse） | Presentation | 卡片 layout + adapter | MAJOR | NO | TRANSCRIPT-CARDS-1 |
| G6 AssistantCard | 单一 TextView（流式） | AssistantCard | Presentation | 卡片 layout + adapter | MAJOR | NO | TRANSCRIPT-CARDS-1 |
| G7 ToolExecutionCard | Shell+Result 单一 TextView | 统一 Tool 卡片 | Presentation | 卡片 layout + adapter | MAJOR | NO | TRANSCRIPT-CARDS-1 |
| G8 FailureCard | 单一 TextView | FailureCard | Presentation | 卡片 layout + adapter | MINOR | NO | TRANSCRIPT-CARDS-1 |
| G9 InterruptedCard | 单一 TextView | InterruptedCard | Presentation | 卡片 layout + adapter | MINOR | NO | TRANSCRIPT-CARDS-1 |
| G10 Section Divider | 无 | User/Thought/Assistant 分隔 | Presentation | adapter divider | MINOR | NO | TRANSCRIPT-CARDS-1 |
| G11 Copy per-block | 整页 selectable | per-block copy + native | Host UI | 卡片 + ClipboardManager | MAJOR | NO | TRANSCRIPT-CARDS-1 |
| G12 Retry | 无 API | re-run 最新 Turn | Host（组合） | ChatActivity/Host Action | MAJOR | POSSIBLE（re-send=host-only；严格 redo=DEFERRED） | CONTROLS-1 |
| G13 Edit & Resend | 无 | restore+re-send | Host（组合） | UserCard + Composer | MAJOR | NO | CONTROLS-1 |
| G14 Stop/Interrupt | 已工作 | Composer toggle | Host（重定位） | Composer | MINOR | NO | SHELL-1 |
| G15 Composer | 普通 EditText+按钮 | Codex Composer | Presentation+Host | composer layout | MAJOR | NO | SHELL-1 + CONTROLS-1 |
| G16 Attachment | 无 picker/context | SAF + context 注入 | Host+(Core 决策) | SAF picker + 注入机制 | MAJOR | GAP（契约决策） | CONTROLS-1（先定契约） |
| G17 Model/Effort | 无 picker/effort | "Model·Effort" | Presentation+Provider config | composer label + config | MAJOR | MODEL=pres；EFFORT=provider 增量 | SHELL-1 + CONTROLS-1 |
| G18 Provider Settings | Spinner+endpoint 主面 | Settings 页 | Host UI | settings layout | MINOR | NO | CONTROLS-1 |
| G19 ADB Indicator | AT 状态行+Reconnect | header 圆点+sidebar | Presentation（重定位） | header+sidebar | MINOR | NO | SHELL-1 + CONTROLS-1 |
| G20 DebugBridge | 现有（debug only） | PRESERVE/REUSE | Debug | DebugBridge.kt | POLISH | NO | 保持 |
| G21 Debug stop | 无命令 | 可选 stop 命令 | Debug protocol | DebugProtocol.kt | MINOR | NO | 可选（CONTROLS 或独立） |
| G22 Session Browser | 仅 New Session | list/switch/rename/group | Host+Store(+契约) | sidebar + `listSessions` + switch seam | MAJOR | **STORE 契约 additive**（非 schema） | CONTROLS-1 |
| G23 Product Identity | 默认图标/v1/0.1.0 | 真实图标/version/theme | Resources/manifest | manifest + mipmap + gradle | DEFERRED | NO | RELEASE-FREEZE-1 |
| G24 Host Action norm | 语义在 Runtime，wiring 在 Activity | UI/Debug 共享 entry | Host（重构） | Host Action 层 | MINOR | NO | 可选（SHELL/CONTROLS） |

**计数（V1 相关，排除 DEFERRED）**：BLOCKER=0；MAJOR=14；MINOR=8；POLISH=1；DEFERRED=G23 + 全部 §28 项。

---

## 29. Frozen Boundary Verdict

```text
AG_CORE_CHANGE_REQUIRED            = NO
AG_RUNTIME_REDESIGN_REQUIRED       = NO
TOPIC_SESSION_SCHEMA_CHANGE        = NO
TOOL_CONTRACT_CHANGE_REQUIRED      = NO
PROVIDER_ARCH_CHANGE_REQUIRED      = NO（effort = 可选 additive config/wire，非架构）
AT_CONSUMER_CHANGE_REQUIRED        = NO

UI_PRESENTATION_REWRITE_REQUIRED   = YES
HOST_ACTION_NORMALIZATION_NEEDED   = POSSIBLE（推荐；host-only，低风险）
DEBUG_BRIDGE_REWRITE_REQUIRED      = NO（PRESERVE / REUSE）

ANDROIDAGENT_V3_REQUIRED           = NO
FREEZE_REOPEN_REQUIRED             = NO
```

**唯一触及 frozen 契约面的项**：`TopicSessionStore.listSessions()`（+ 可选 `updateTitle`）——为 Session Browser 所需。它是 **ADDITIVE**（**无 schema 改动**；表 + last_used_at 索引已存在；不破坏任何 frozen 语义：D1/D2/D3、sequence ownership、ordering 均不变）。这是**scoped 契约增量，非 freeze reopen**。按 §36 规则记录，不偷偷扩大。

**Attachment→model-context**（G16）是唯一需**契约决策**的开放项（SAF picker = presentation；context 注入机制 = scoped step；若需 Core/RunOptions 增量则为**最小 additive**，非重写）。不得在 UI 临时造输入协议。

---

## 30. Device Baseline

```text
DEVICE_BASELINE = DEFERRED_WITH_REASON
```

`adb devices`（C:\android\platform-tools\adb.exe）在审计时返回**空设备列表**，无设备连接。捕获 CURRENT DEVICE UI BASELINE 需连接设备，本轮不可用。按规则，设备缺失**不导致审计失败**——将在 `AG.UI-DEVICE-ACCEPTANCE-1`（本就需要设备）捕获。未为设备 baseline 改任何 production 代码。代码层现状（单 Activity XML/View UI、单 TextView transcript、provider Spinner、AT 状态行、New Session 按钮、DebugBridge debug-only）已完整从源码记录（evidence 02-10），设备步骤只需在硬件上确认。

---

## 31. Implementation Split（Deliverable 4）

基于真实代码状态（非机械照抄设计建议阶段）。原则：**现有真实 Agent 路径（send/interruptActiveRun/newSession/persistence/DebugBridge）绝不重写**；VNext UI 是其薄 Presentation。设计建议 AUDIT→SHELL→TRANSCRIPT-CARDS→CONTROLS→DEVICE→RELEASE，审计**确认该顺序合理**，并有两处细化：(a) Session Browser（G22）需 additive store 契约 + switch seam，放 CONTROLS（非 SHELL），标记为唯一 frozen-契约 additive 项；(b) Attachment→context（G16）需先契约决策再 UX。

完整每阶段 Goal/Scope/Files/Architecture impact/Tests/Device gate/Dependencies 见 `docs/evidence/ag_ui_baseline_audit_1/14_implementation_split.txt`。

```text
STEP 1  AG.UI-SHELL-1            浅色 workspace 框架：Header(☰+ADB●)/固定 Viewport/固定 Composer/
                                 collapsible Sidebar 框架；整页 ScrollView→固定三区；浅色 theme。
                                 无卡片（viewport 仍走现有 projection）。
STEP 2  AG.UI-TRANSCRIPT-CARDS-1 卡片化 transcript：单 TextView→卡片 list（User/Thought/Assistant/
                                 ToolExecution/Failure/Interrupted）+ divider + per-card Copy +
                                 native selection + Thought/Result collapse；streaming 驱动增量卡片。
STEP 3  AG.UI-CONTROLS-1         卡片交互 + composer 控件 + sidebar 内容：Retry(re-send)/Edit&Resend/
                                 Session Browser(additive listSessions+switch seam+rename)/ADB sidebar/
                                 Provider Settings/Model·Effort 标签/SAF 附件；可选 Debug stop 命令。
STEP 4  AG.UI-DEVICE-ACCEPTANCE-1 真机完整验收（捕获审计 DEFERRED 的 device baseline）。
STEP 5  AG.UI-RELEASE-FREEZE-1   产品身份 + release freeze：真实图标/versionCode·Name/最终 label+浅色
                                 theme/status·nav bar 样式/ABI 确认。
```

**顺序依赖**：SHELL → TRANSCRIPT-CARDS → CONTROLS → DEVICE-ACCEPTANCE → RELEASE-FREEZE（CONTROLS 依赖卡片 list 存在；DEVICE-ACCEPTANCE 需全部 UI 就位；RELEASE-FREEZE 最后）。

---

## 32. Final Verdict

```text
STEP = AG.UI-BASELINE-AUDIT-1
VERDICT = PASS — VNEXT_UI_BASELINE_AUDIT_COMPLETE_AND_IMPLEMENTATION_READY
```

**核心结论**：
1. **现有 AndroidAgent 已拥有 VNext 所需真实能力**：block 化 TranscriptModel（7 类 + streaming + final 去重 + restore re-projection）、`AgentRuntime.send/interruptActiveRun/newSession/currentHistory`（durable）、共享同一 send 路径 + event stream 的 DebugBridge（结构正确）、经 integration-at 的 ADB 状态+reconnect+readiness（边界正确）、provider config（只读 secret-safe）+ node 切换。
2. **VNext UI 是这些能力之上的薄 Presentation 重写**：固定 viewport + 卡片 + divider + copy/selection + retry/edit（host 组合）+ session browser（additive store query + switch seam）+ composer + sidebar + settings。**无需重写 Agent。**
3. **唯一 frozen 契约面触及是 ADDITIVE**：`TopicSessionStore.listSessions()`（+ 可选 updateTitle）。无 schema 改动。无 freeze reopen。
4. **Retry = re-send 组合（host-only）；严格 Redo/Unrevert = DEFERRED（§28）。Edit & Resend = restore-to-composer + re-send（host-only；旧 durable message 不变）。**
5. **Attachment→model-context 是唯一开放契约决策**（SAF picker = presentation；context 注入机制 = scoped step；不得临时造 UI 协议）。
6. **DebugBridge PRESERVE/REUSE**；可选 "stop" 命令（调同一 interruptActiveRun）= 小 additive 协议扩展（非 V1 必需）。

```text
NEXT = AG.UI-SHELL-1
COMMIT = NO（本轮）
TAG    = NO
PUSH   = NO
STOP   = YES（不得开始 UI 实现）
```
