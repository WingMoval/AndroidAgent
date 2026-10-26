# AG.TOPIC-SESSION-DURABILITY-SEAM-PREFLIGHT-0 — Core Durability Seam 实现前冻结

## 1. 任务性质

```text
ARCHITECTURE PREFLIGHT / SOURCE AUDIT / CONTRACT DESIGN / FAILURE SEMANTICS FREEZE
DOCS-ONLY / NO PRODUCTION IMPLEMENTATION
```

---

## 2. 源码审计（CURRENT SOURCE VERIFIED）

### 2.1 真实执行顺序（AgentCore.kt）

```text
driveLoop (line 271-378):
  280: session.append(UserMessage(userInput))          [内存; 无 event]
  283: while(true):
    289: sink.emit(ModelTurnStarted(step))             [非 terminal]
    292: modelProvider.complete(...)                    [LLM; suspend]
    326: sink.emit(ModelTurnFinished(step, turn))       [非 terminal]
    329: when(turn):
      FinalAnswer:
    331:   session.append(ModelMessage(turn.content))   [内存]
    332:   sink.emit(AnswerProduced(turn.content))      [非 terminal]
    334:   emitTerminal(RunFinished(Answer))            [terminal]
    335:   return
      ToolCalls:
    343:   session.append(ModelMessage(content, calls)) [内存]
    346:   for call in calls:
    351:     pairing.markStarted(call.id)
    352:     sink.emit(ToolCallStarted(call))           [非 terminal]
    354:     toolRegistry.dispatch(call, policy)        [外部副作用; suspend]
    372:     session.append(ToolResultMessage(...))     [内存]
    373:     sink.emit(ToolCallFinished(call, result))  [非 terminal]
```

### 2.2 AgentEventSink 异常行为（精确审计）

**AgentEvent.kt:53-54**: `fun interface AgentEventSink { fun emit(event: AgentEvent) }` — 同步、非 suspend、无返回值。

**非 terminal 事件**（ModelTurnStarted/ModelTurnFinished/ToolCallStarted/ToolCallFinished/AnswerProduced）：
- 直接调用 `sink.emit(...)`（line 289, 326, 332, 352, 373）
- 异常**不捕获** → 传播到 `executeRun` 的 `catch (e: Exception)` (line 178)
- → `failRun(RunFailure.RuntimeFailure)` → Run 终止

**Terminal 事件**（RunFinished/RunFailed）：
- 通过 `emitTerminal()` (line 394-407)
- `catch (e: CancellationException) { throw e }` — rethrow
- `catch (e: Exception) { }` — **swallowed**

```text
NON_TERMINAL_EVENT_EXCEPTION_BEHAVIOR = PROPAGATES → failRun (Run 终止)
TERMINAL_EVENT_EXCEPTION_BEHAVIOR = SWALLOWED (Run 结果不受影响)
```

### 2.3 现有 Interrupt 语义

- `appendSyntheticToolResults` (line 226-249)：in-flight → `IN_FLIGHT_INTERRUPTED_ERROR`; not-started → `NOT_EXECUTED_DUE_TO_INTERRUPT_ERROR`
- 这是 **user interrupt**（同进程、Run 被取消）的修复
- 与 **crash dangling**（进程死亡、resume 时发现）是**不同场景**
- 两者语义一致（UNKNOWN），但触发时机不同

### 2.4 RunFailure 现有类型

```kotlin
sealed interface RunFailure {
    data class ProviderFailure(val cause: String) : RunFailure
    data object MaxStepsReached : RunFailure  // (实际: data class with maxSteps)
    data class SessionBusy(val sessionId: String) : RunFailure
    data class RuntimeFailure(val cause: String) : RunFailure
}
```

无 PersistenceFailure / DurabilityFailure。

---

## 3. MEMORY_FIRST 审查

### 当前 Freeze 模型

```text
session.append(message)  [内存]
→ persist(message)       [DB]
```

### 问题

```text
append 成功
persist 失败
↓
Core history 有消息（进程内）
DB 没有
↓
Run abort
但 Session 仍在当前进程中存在
↓
后续 Run 的 session.history() 包含 DB 中不存在的消息
↓
LIVE_CORE != DURABLE（同进程内永久分叉）
```

### 结论

```text
MEMORY_FIRST_CAN_PRESERVE_SAME_PROCESS_CONSISTENCY = NO
MESSAGE_ORDERING_MODEL = DURABLE_FIRST
```

---

## 4. DURABLE_FIRST 模型（冻结）

```text
DURABLE_HISTORY = restart source of truth
CORE_HISTORY    = live working copy
UI_TRANSCRIPT   = projection
```

```text
LIVE_CORE_HISTORY == DURABLE_HISTORY  在每个公开可观察稳定点
```

### D1 — UserMessage

```text
Run admission (tryBeginRun = true)
↓
D1: durability.commit(UserMessage)  [suspend; AWAIT COMMIT]
    → FAIL → DurabilityFailure → Run 终止（无 ModelCall）
    → SUCCESS
↓
session.append(UserMessage)  [内存; 非取消检查点]
↓
first modelProvider.complete()
```

### D2 — ModelMessage (含 ToolCalls)

```text
modelProvider.complete() returns turn
↓
ModelTurnFinished observation (event)
↓
D2: durability.commit(ModelMessage)  [suspend; AWAIT COMMIT]
    → FAIL → DurabilityFailure → Run 终止（无 Tool dispatch）
    → SUCCESS
↓
session.append(ModelMessage)  [内存; 非取消检查点]
↓
[if ToolCalls:] ToolCallStarted → toolRegistry.dispatch()
[if FinalAnswer:] AnswerProduced → RunFinished(Answer)
```

### FinalAnswer

```text
ANSWER_MAY_BE_REPORTED_BEFORE_DURABLE_COMMIT = NO
```

FinalAnswer 的 ModelMessage 同样经过 D2。D2 失败 → 无 Answer outcome → Run 以 DurabilityFailure 终止。

### D3 — ToolResultMessage

```text
toolRegistry.dispatch() returns ToolResult
↓
D3: durability.commit(ToolResultMessage)  [suspend; AWAIT COMMIT]
    → FAIL → DurabilityFailure → Run 终止（无 next ModelCall）
    → SUCCESS
↓
session.append(ToolResultMessage)  [内存; 非取消检查点]
↓
ToolCallFinished observation (event)
↓
next modelProvider.complete()
```

D3 失败时：durable ModelMessage(toolCall) 存在 + durable ToolResult 不存在 = **自然 DANGLING**。Core 和 DB 一致（都无 ToolResult）。

---

## 5. DURABLE_FIRST Crash Analysis

| Window | DB | Core | 结果 |
|---|---|---|---|
| DF1: commit 未开始, crash | 无 | 无 | SAFE（无数据） |
| DF2: commit 失败/回滚 | 无 | 无（未 append） | SAFE（Run 终止） |
| DF3: commit 成功, crash before append | **有** | 消失（进程死） | SAFE（restart 从 DB 恢复） |
| DF4: commit + append 成功, crash | 有 | 有（进程死） | SAFE（restart 从 DB 恢复） |

DF3 是关键：DB 有但 Core 无 → 进程死亡 → restart → `loadMessages` 恢复 → Core 从 DB 重建 → 一致。

---

## 6. Commit Cancellation Contract（NORMALIZE-1 修正：AMBIGUOUS 已否决）

### 否决 TREAT_AS_COMMITTED

```text
AMBIGUOUS_COMMIT_OUTCOME_ALLOWED = NO
COMMIT_RESULT_VISIBLE_TO_CORE = COMMITTED | NOT_COMMITTED ONLY
```

如果 commit 结果不确定但 Core 按 committed append → Core 有 / DB 无 → same-process divergence → 违反 `LIVE_CORE == DURABLE`。

### 最终冻结：CANCELLATION-DEFERRED COMMIT BOUNDARY

```text
CANCELLATION_DURING_COMMIT_POLICY = CANCELLATION_DEFERRED_UNTIL_OUTCOME_KNOWN
COMMIT_SHIELDING_OWNER = BACKEND ADAPTER (ag-runtime / :app)
```

核心原则：

```text
进入 commit 边界后，cancellation 不得导致 unknown commit outcome。
transaction 必须确定性完成（COMMITTED 或 NOT_COMMITTED）。
outcome 已知后，才 honour pending cancellation。
```

### 机制（Candidate B：Backend shields transaction）

```text
Core 调用:
    MessageDurability.commit(sessionId, message)
    
Backend adapter 内部（ag-runtime / :app）:
    1. 检查 cancellation（commit 前）
       → 已取消 → 不进入 transaction → 正常抛 CancellationException（NOT_COMMITTED，无歧义）
    2. 进入 cancellation-shielded transactional section
       → withContext(NonCancellable) { store.appendMessage(...) }
       → transaction 确定性完成
       → 结果: COMMITTED (正常返回) 或 NOT_COMMITTED (DurabilityFailure)
    3. 返回 Core（outcome 确定）

Core 收到:
    正常返回 = COMMITTED → session.append → 然后检查 cancellation
    DurabilityFailure = NOT_COMMITTED → Run 终止
    CancellationException（仅来自 step 1: commit 前）→ 正常 interrupt
```

**为什么 Core 永远看不到 AMBIGUOUS**：
- CancellationException 只在 **commit 前** 检查时抛出（NOT_COMMITTED 确定）
- 一旦进入 `withContext(NonCancellable)`，cancellation 被 defer
- transaction 要么完成（COMMITTED）要么异常（NOT_COMMITTED）
- 返回 Core 时 outcome 100% 确定

### Cancellation State Matrix（最终）

| 场景 | DB | Core | Run Outcome | Resume |
|---|---|---|---|---|
| C0: cancel before commit | 无 | 无 | Interrupted | 无影响 |
| C1: cancel while waiting to enter commit | 无 | 无 | Interrupted | 无影响 |
| C2: cancel after transaction begins (in NonCancellable) | 确定性 COMMITTED 或 NOT_COMMITTED | 依 outcome | 见下 | 见下 |
| C3: cancel after COMMIT, before return | **COMMITTED** | 无（未 append） | append → Interrupted | DB 有 → 恢复 |
| C4: cancel after commit() returns, before append | **COMMITTED** | 无 | append（NonCancellable）→ Interrupted | DB+Core 有 |
| C5: cancel after append | **COMMITTED** | 有 | Interrupted | SAFE |

**C2 展开**：
- transaction 成功 → COMMITTED → append → honour cancel → Interrupted
- transaction 失败 → NOT_COMMITTED → 无 append → Interrupted（cancel 优先）或 DurabilityFailure（storage 错误优先）

### 优先级（cancellation vs storage failure）

```text
if transaction 因 storage 原因确定性失败 → DurabilityFailure（不是 Interrupted）
if cancel before commit boundary → Interrupted
if COMMITTED + pending cancel → append Core → Interrupted
if NOT_COMMITTED + pending cancel → Interrupted（cancel 是原因）
```

### 冻结

```text
COMMIT_OUTCOME_AMBIGUITY_POLICY = AMBIGUOUS_NOT_POSSIBLE (backend shields)
CANCELLATION_DURING_COMMIT_POLICY = CANCELLATION_DEFERRED_UNTIL_OUTCOME_KNOWN
COMMIT_SHIELDING_OWNER = BACKEND ADAPTER (withContext(NonCancellable) inside commit())
POST_COMMIT_PRE_APPEND_POLICY = NORMAL (Session.append is non-suspend, no checkpoint)
POST_COMMIT_MEMORY_APPEND_FAILURE_POLICY = PROCESS_FATAL (no compensating delete; restart from DB)
CORE_ORPHAN_MESSAGE_ALLOWED = NO
```

### 为什么 POST_COMMIT_PRE_APPEND 不需要额外 NonCancellable

```text
Session.append() 源码验证 (Session.kt:44-46):
    internal fun append(message: Message) {
        synchronized(monitor) { messages.add(message) }
    }
    
- 非 suspend
- 无 suspension point
- 无 cancellation checkpoint
- 正常 Kotlin 执行路径: commit returns → append executes immediately（无窗口）
```

唯一需要 NonCancellable 的是 **commit 内部**（backend adapter），不是 Core 的 append。

---

## 7. Cancellation During Persistence（D1/D2/D3 分别，NORMALIZE-1 修正）

| 时点 | Durable | In-Memory | Run Outcome | Resume |
|---|---|---|---|---|
| cancel before commit boundary | 无 | 无 | Interrupted | 无影响 |
| cancel while commit running (in NonCancellable) | 确定性: COMMITTED 或 NOT_COMMITTED | 依 outcome | 见 §6 优先级 | 依 outcome |
| cancel after COMMIT, before return | COMMITTED | 无 | append → Interrupted | DB 有 → 恢复 |
| cancel after commit() returns, before append | COMMITTED | 无 | append → Interrupted | DB+Core 有 |
| cancel after append | COMMITTED | 有 | Interrupted | SAFE |

### D1 具体

```text
D1 cancel before commit → no UserMessage anywhere → Interrupted
D1 COMMITTED + cancel → UserMessage durable + Core append → no ModelCall → Interrupted
```

### D2 具体

```text
D2 not committed + cancel → no ModelMessage Core → no Tool dispatch → Interrupted
D2 COMMITTED + cancel → append ModelMessage → do NOT dispatch Tool → interrupt closeout
  (durable ToolCall + no ToolResult → DANGLING → 同进程 interrupt repair 或 restart repair)
```

### D3 具体

```text
D3 COMMITTED + cancel → append ToolResult → Interrupted
D3 NOT_COMMITTED + cancel → no Core ToolResult → durable DANGLING → stop Run
  (external state UNKNOWN; no next Model)
```

---

## 8. Durability Seam 设计（冻结）

### 8.1 候选比较

| 维度 | A: RunOptions lambda | B: Core interface | C: Named port |
|---|---|---|---|
| API clarity | 弱（lambda 语义模糊） | 中 | 强（命名表达意图） |
| Dependency direction | OK（Core 不知道后端） | OK | OK |
| Failure propagation | 弱（lambda throw = 不明确） | 中（interface throw） | 强（typed exception） |
| Cancellation semantics | 需额外约定 | 需额外约定 | 在 contract 中明确 |
| Testability | 好（fake lambda） | 好（fake impl） | 好（fake impl） |
| Misconfiguration risk | 高（Noop lambda 容易误用） | 中 | 低（named + 类型约束） |
| Core 知道 "durability"? | 否（只是 callback） | 是（interface 名） | 是（port 名） |

**选择: Candidate B（Core contract interface）**

理由：
- 比 lambda 语义更明确
- 比 "DurabilityGate/MessageCommitter" 等过度命名更简洁
- Core 知道 "message durability" 是合理的 domain safety contract（不是后端细节）
- 命名避免 Android/Room 关联

### 8.2 冻结 API

```kotlin
// ag-core: MessageDurability.kt（新概念）

/**
 * Per-message durability boundary. Core awaits this before crossing
 * a safety boundary (model call, tool dispatch, next model call).
 * 
 * This is NOT an observation/event. This is a CORRECTNESS GATE:
 * the Core must not proceed until the commit is confirmed.
 * 
 * Implementation is storage-agnostic: could be Room, file, or in-memory.
 * 
 * CANCELLATION CONTRACT (frozen):
 * - The implementation MUST shield the actual commit transaction from
 *   coroutine cancellation (e.g., withContext(NonCancellable) internally).
 * - CancellationException may ONLY be thrown BEFORE the transaction starts
 *   (i.e., the implementation checks cancellation first, then proceeds).
 * - Once the transaction has started, it MUST complete deterministically.
 * - The Core NEVER sees an ambiguous commit outcome.
 */
fun interface MessageDurability {
    /**
     * Durable-commit [message] for [sessionId].
     * 
     * OUTCOMES (exactly two, never ambiguous):
     * - Normal return = COMMITTED (transaction committed; guaranteed)
     * - DurabilityFailure thrown = NOT_COMMITTED (transaction did not commit; guaranteed)
     * 
     * Cancellation:
     * - CancellationException may be thrown ONLY if cancellation was detected
     *   BEFORE the commit transaction was entered. In this case, the message
     *   was NOT committed (NOT_COMMITTED). The Core treats this as a normal
     *   interrupt (no message in Core, no message in DB).
     * - CancellationException MUST NOT be thrown after the transaction has
     *   started. The implementation must ensure the transaction completes.
     * 
     * Requirements:
     * - Must AWAIT actual commit (no fire-and-forget, no background queue)
     * - Must not expose backend exceptions (Room/SQLite) to Core
     * - Normal return guarantees: the message IS in the durable store
     */
    suspend fun commit(sessionId: String, message: Message)
}

/** Thrown when durability commit definitively FAILED (NOT_COMMITTED). */
class DurabilityFailure(
    val boundary: Boundary,
    cause: Throwable? = null,
) : Exception("Durability commit failed at ${boundary.name}", cause) {
    enum class Boundary { USER, MODEL, TOOL_RESULT, FINAL_ANSWER }
}
```

### 8.3 命名

```text
DURABILITY_SEAM_NAME = MessageDurability
DURABILITY_SEAM_API_STYLE = suspend fun interface + typed failure
DURABILITY_SEAM_OWNER = AG Core (contract); Agent Runtime (adapter); :app (implementation)
```

禁止命名：RoomHook / SQLiteWriter / DatabaseSink / DaoCallback / AgentEventSink-like

### 8.4 注入点

**比较**：
- AgentCore constructor: 强绑定；Core 构造时必须提供
- RunOptions: 每次 Run 可不同；灵活但可能误用 Noop
- Session dependency: 违反 "Session 不拥有 storage"

**冻结**:

```text
DURABILITY_SEAM_INJECTION_POINT = AgentCore constructor
NOOP_DEFAULT_ALLOWED = NO（无 constructor default value）
```

```kotlin
class AgentCore(
    private val modelProvider: ModelProvider,
    private val toolRegistry: ToolRegistry,
    private val messageDurability: MessageDurability,  // 无默认值; 必须显式提供
)
```

- **无默认值**：防止生产 Composition Root 忘记注入 → 编译错误
- 测试显式传 `MessageDurability.Noop`
- 生产 Composition Root 必须注入真实实现
- 不通过 RunOptions（避免 per-run 切换的复杂度）

```text
TOPIC_SESSIONSTORE_DIRECTLY_VISIBLE_TO_CORE = NO
```

Core 只看到 `MessageDurability` 接口。TopicSessionStore 是 Runtime 的 adapter 内部实现。

### 8.5 Ephemeral / No-op（NORMALIZE-1 修正）

```text
NOOP_DURABILITY_ALLOWED = YES（explicit only）
NOOP_DEFAULT_ALLOWED = NO
PRODUCTION_NOOP_ALLOWED = NO
```

```kotlin
// Noop 实现（显式使用; 不是默认值）
val MessageDurability.Noop: MessageDurability = MessageDurability { _, _ -> }

// 测试:
AgentCore(provider, registry, MessageDurability.Noop)  // 显式

// 生产:
AgentCore(provider, toolRegistry, roomDurability)  // 必须注入真实实现
// 如果忘记: 编译错误（无 default）
```

### 8.6 AgentCore 构造点审计

```text
AGENTCORE_CONSTRUCTION_SITES = 53
PRODUCTION_CONSTRUCTION_SITES = 1 (ChatCompositionRoot.kt:125)
TEST_CONSTRUCTION_SITES = 52 (across ag-core, ag-runtime, provider-jdk, app tests)
EPHEMERAL_SITES = 0
```

移除 default 后，52 个测试构造点需显式添加 `MessageDurability.Noop` 参数。1 个生产构造点必须注入真实实现。这是实现 Step 的工作，本轮只审计。

---

## 9. Persistence Failure Type（冻结）

**选择: 新增 `RunFailure.DurabilityFailure`**

```kotlin
// 扩展现有 RunFailure:
sealed interface RunFailure {
    data class ProviderFailure(val cause: String) : RunFailure
    data class MaxStepsReached(val maxSteps: Int) : RunFailure
    data class SessionBusy(val sessionId: String) : RunFailure
    data class RuntimeFailure(val cause: String) : RunFailure
    // 新增:
    data class DurabilityFailure(
        val boundary: String,  // "USER" | "MODEL" | "TOOL_RESULT" | "FINAL_ANSWER"
        val cause: String,     // safe category (NOT SQL text, NOT file path)
    ) : RunFailure
}
```

```text
DURABILITY_FAILURE_TYPE = RunFailure.DurabilityFailure (new subtype)
DURABILITY_FAILURE_CAN_CONTINUE_RUN = NO
```

不暴露：SQLiteException / Room exception / filesystem path / SQL text。
`cause` 是 safe category string（如 `"commit_error"`, `"db_unavailable"`）。

### Failure Semantics

```text
D1 failure: NO MODEL CALL. Run → RunOutcome.Failure(DurabilityFailure("USER", ...))
D2 failure: NO TOOL DISPATCH. Run → RunOutcome.Failure(DurabilityFailure("MODEL", ...))
D3 failure: NO NEXT MODEL CALL. Run → RunOutcome.Failure(DurabilityFailure("TOOL_RESULT", ...))
FINAL failure: NO Answer outcome. Run → RunOutcome.Failure(DurabilityFailure("FINAL_ANSWER", ...))
```

### Cancellation vs DurabilityFailure

```text
user Interrupt → RunOutcome.Interrupted（现有语义不变）
durability failure → RunOutcome.Failure(DurabilityFailure)（新语义）
二者不同终止原因。DB failure 不产生 Interrupted。
```

---

## 10. 与现有 Interrupt 语义的兼容性

```text
RUN_INTERRUPT_RESULT_SEMANTICS = user Stop → synthetic interrupted ToolResults → RunOutcome.Interrupted
CRASH_DANGLING_RESULT_SEMANTICS = process death → resume → DANGLING → SYNTHETIC_INTERRUPTED_UNKNOWN_RESULT
ARE_THEY_SAME = PARTIAL
```

区别：
- **Interrupt**（同进程）: Core 知道哪些 calls started（`InterruptPairing`）→ 区分 IN_FLIGHT vs NOT_EXECUTED
- **Crash dangling**（跨进程）: Core 不知道 → 全部视为 UNKNOWN（INTERRUPTED_UNKNOWN）
- 语义一致（都是 "result unknown, don't replay"），但 granularity 不同
- **不冲突**：两套机制在不同时机触发，不重复

**DURABLE_FIRST 下的 interrupt during D1/D2/D3**：
- D1 cancel: UserMessage 可能已 commit → deterministic outcome → append (if COMMITTED) → Interrupted
- D2 cancel: ModelMessage 可能已 commit → 同上 → 如果 commit 成功，DANGLING 存在（但 interrupt synthetic 会修复）
- D3 cancel: ToolResult 可能已 commit → 同上
- 现有 `appendSyntheticToolResults` 在 interrupt 时运行 → 修复 Core 中的 dangling
- 但 DB 中的 dangling 需要在**下次 restart** 时修复（startup repair）
- 这是正确的：interrupt 修复 Core（同进程），startup repair 修复 DB（跨进程）

---

## 11. AgentEvent 兼容性

```text
AGENT_EVENT_SINK_ROLE = OBSERVABILITY_ONLY (UI / Trace / Debug)
AGENT_EVENT_SINK_AS_DURABILITY_CONTRACT = NO
AGENT_EVENT_PUBLIC_SEMANTICS_CHANGED = NO
```

### 最终 Event 顺序（DURABLE_FIRST 后）

```text
RunStarted
  ↓
[D1: durability.commit(UserMessage)]  ← 新增（无 event）
  [session.append(UserMessage)]
  ↓
ModelTurnStarted
model.complete
ModelTurnFinished
  ↓
[D2: durability.commit(ModelMessage)]  ← 新增（无 event）
  [session.append(ModelMessage)]
  ↓
if ToolCalls:
    ToolCallStarted
    dispatch
    [D3: durability.commit(ToolResultMessage)]  ← 新增（无 event）
    [session.append(ToolResultMessage)]
    ToolCallFinished
if FinalAnswer:
    AnswerProduced
  ↓
RunFinished
```

**Event 顺序不变**：D1/D2/D3 是 Core 内部操作，不产生 AgentEvent。现有 TraceCollector / DebugBridge / TranscriptModel / tests 不受影响。

---

## 12. WAL 不等于 Seam

```text
SQLite WAL 解决: 数据库事务原子性、并发读写、crash recovery（DB 层面）
SQLite WAL 不解决: Core vs DB ordering、Tool side-effect ordering、Run control ordering
WAL != Durability Seam
```

---

## 13. 成熟 Agent 对照

```text
OpenCode:  per-event SQLite transaction; stale running tool → interrupted (not replay)
Codex:     FunctionCall JSONL line BEFORE dispatch; dangling on resume → model decides
Claude:    tool_use before tool_result; "cut off" marker; no auto-replay
Gemini:    $rewindTo + close dangling; no replay
```

```text
AndroidAgent DURABLE_FIRST = derived design (not blind clone)
- 借鉴: commit before side effect (Codex/Claude)
- 借鉴: dangling → UNKNOWN → no replay (all 4)
- 差异: explicit suspend seam in Core (vs event-driven / side-channel)
- 原因: Android 单进程 + Room + coroutine cancellation 需要明确 ordering guarantee
```

---

## 14. Deterministic Test Matrix（下一步实现用）

| # | 场景 | 验证 |
|---|---|---|
| T1 | D1 success | commit called BEFORE ModelCall; UserMessage in DB + Core |
| T2 | D1 failure | no ModelCall; no Core UserMessage; RunOutcome.Failure(DurabilityFailure) |
| T3 | D2 success (ToolCalls) | commit BEFORE dispatch; ModelMessage in DB + Core; tool executed |
| T4 | D2 failure | no Tool dispatch; no Core ModelMessage; RunOutcome.Failure |
| T5 | D3 success | commit BEFORE next ModelCall; ToolResult in DB + Core |
| T6 | D3 failure | no next ModelCall; no Core ToolResult; DANGLING in DB; RunOutcome.Failure |
| T7 | FinalAnswer D2 failure | no AnswerProduced; no RunFinished(Answer); RunOutcome.Failure |
| T8 | Cancel before D1 commit boundary | NOT_COMMITTED; no Core append; no DB; Interrupted |
| T9 | Cancel during protected D1 commit | Deterministic COMMITTED or NOT_COMMITTED; NEVER ambiguous |
| T10 | D1 COMMITTED + pending cancel | Core append; no ModelCall; Interrupted; Core==DB |
| T11 | D2 NOT_COMMITTED | no Core ModelMessage; no Tool dispatch; Interrupted |
| T12 | D2 COMMITTED + pending cancel | Core append; no Tool dispatch; DANGLING; Interrupted |
| T13 | D3 NOT_COMMITTED | no Core ToolResult; no next Model; DANGLING in DB |
| T14 | Event order unchanged | AgentEvent sequence identical to pre-seam |
| T15 | Session reusable after failed Run | Core history == DB history (no orphan); next Run works |
| T16 | Restore after DB-only message | loadMessages → restoreSession → Core has the message |

### Fake Durability Harness

```text
ScriptedDurabilityGate (pure JVM):
  - SUCCESS: commit() returns normally
  - FAIL: commit() throws DurabilityFailure
  - SUSPEND: commit() suspends N ms
  - CANCEL_BEFORE_COMMIT: commit() throws CancellationException (NOT_COMMITTED)
  - PROTECTED_COMMIT_WITH_PENDING_CANCEL: cancellation arrives after transaction entry; backend returns deterministic COMMITTED or NOT_COMMITTED, never AMBIGUOUS

配合: ScriptedModelProvider + FakeTool + AgentEventCollector
```

---

## 15. 决策表

| 决策项 | 值 | 状态 |
|---|---|---|
| MESSAGE_ORDERING_MODEL | DURABLE_FIRST | FROZEN |
| MEMORY_FIRST | REJECTED (same-process consistency) | REJECTED |
| DURABLE_FIRST | APPROVED | FROZEN |
| LIVE_CORE_EQUALS_DURABLE_AT_STABLE_BOUNDARY | YES | FROZEN |
| D1_POSITION | AFTER_ADMISSION, BEFORE_FIRST_MODEL_CALL | FROZEN |
| D2_POSITION | AFTER_MODEL_COMPLETE, BEFORE_TOOL_DISPATCH | FROZEN |
| D3_POSITION | AFTER_TOOL_COMPLETE, BEFORE_NEXT_MODEL_CALL | FROZEN |
| FINAL_ANSWER_DURABLE_BEFORE_SUCCESS | YES | FROZEN |
| DURABILITY_SEAM_OWNER | AG Core (contract) / Runtime (adapter) / :app (impl) | FROZEN |
| DURABILITY_SEAM_NAME | MessageDurability | FROZEN |
| DURABILITY_SEAM_API_STYLE | suspend fun interface + typed DurabilityFailure | FROZEN |
| DURABILITY_SEAM_INJECTION_POINT | AgentCore constructor (explicit required; no default) | FROZEN |
| TOPIC_SESSIONSTORE_DIRECTLY_VISIBLE_TO_CORE | NO | FROZEN |
| DURABILITY_SUCCESS_MEANS | COMMITTED (guaranteed) | FROZEN |
| DURABILITY_FAILURE_MEANS | NOT_COMMITTED (guaranteed) | FROZEN |
| DURABILITY_FAILURE_TYPE | RunFailure.DurabilityFailure (new) | FROZEN |
| DURABILITY_FAILURE_CAN_CONTINUE_RUN | NO | FROZEN |
| D1_FAILURE_BEHAVIOR | NO MODEL CALL; Run → Failure | FROZEN |
| D2_FAILURE_BEHAVIOR | NO TOOL DISPATCH; Run → Failure | FROZEN |
| D3_FAILURE_BEHAVIOR | NO NEXT MODEL; DANGLING; Run → Failure | FROZEN |
| FINAL_MESSAGE_FAILURE_BEHAVIOR | NO ANSWER; Run → Failure | FROZEN |
| CANCEL_BEFORE_COMMIT | Normal interrupt; no data; NOT_COMMITTED | FROZEN |
| CANCEL_DURING_COMMIT | CANCELLATION_DEFERRED; deterministic outcome | FROZEN |
| COMMITTED_WITH_PENDING_CANCEL | COMMITTED → append → Interrupted | FROZEN |
| POST_COMMIT_PRE_APPEND_POLICY | NORMAL (append is non-suspend, no checkpoint) | FROZEN |
| COMMIT_OUTCOME_AMBIGUITY_POLICY | AMBIGUOUS_NOT_POSSIBLE (backend shields) | FROZEN |
| AMBIGUOUS_COMMIT_OUTCOME_ALLOWED | NO | FROZEN |
| COMMIT_SHIELDING_OWNER | BACKEND ADAPTER (NonCancellable inside commit) | FROZEN |
| POST_COMMIT_MEMORY_APPEND_FAILURE_POLICY | PROCESS_FATAL (no compensating delete) | FROZEN |
| CORE_ORPHAN_MESSAGE_ALLOWED | NO | FROZEN |
| AGENT_EVENT_SINK_ROLE | OBSERVABILITY_ONLY | FROZEN |
| AGENT_EVENT_PUBLIC_SEMANTICS_CHANGED | NO | FROZEN |
| NON_TERMINAL_EVENT_EXCEPTION_BEHAVIOR | PROPAGATES → failRun | FROZEN |
| TERMINAL_EVENT_EXCEPTION_BEHAVIOR | SWALLOWED | FROZEN |
| RUN_INTERRUPT_RESULT_SEMANTICS | Synthetic (IN_FLIGHT / NOT_EXECUTED) → Interrupted | FROZEN |
| CRASH_DANGLING_RESULT_SEMANTICS | SYNTHETIC_INTERRUPTED_UNKNOWN_RESULT | FROZEN |
| INTERRUPT_AND_DANGLING_CONFLICT | NO (different timing, same direction) | FROZEN |
| NOOP_DURABILITY_ALLOWED | YES (explicit only) | FROZEN |
| NOOP_DEFAULT_ALLOWED | NO (no constructor default) | FROZEN |
| PRODUCTION_NOOP_ALLOWED | NO | FROZEN |
| CORE_ROOM_DEPENDENCY | NONE | FROZEN |
| CORE_SQLITE_DEPENDENCY | NONE | FROZEN |
| AT_CHANGED | NO | FROZEN |
| CORE_RESTORE_SEAM_CHANGED | NO (compatible with DURABLE_FIRST) | FROZEN |
| DATABASE_MODEL_CHANGED | NO | FROZEN |
| TOPIC_SESSION_SCHEMA_CHANGED | NO | FROZEN |
| MESSAGE_SCHEMA_CHANGED | NO | FROZEN |

---

## 16. 实现范围预览（下一步）

```text
ag-core:
  - MessageDurability interface + Noop
  - DurabilityFailure exception
  - RunFailure.DurabilityFailure (new subtype)
  - AgentCore: constructor param + D1/D2/D3 integration
  - Session.restoreHistory + AgentCore.restoreSession (if not yet done)

ag-runtime:
  - TopicSessionStore interface
  - MessageDurability adapter (TopicSessionStore → MessageDurability)
  - AgentRuntime: startup restore orchestration
  - MessageSerializer

app:
  - Room Entity / DAO / Database
  - RoomTopicSessionStore
  - RoomMessageDurability (implements MessageDurability via store)
  - Startup wiring
  - Dangling repair (DURABLE_REPAIR_FIRST)
  - TranscriptModel re-projection
```

---

## 17. 冻结文档同步状态（NORMALIZE-2 一致性清理）

`AG.TOPIC-SESSION-ARCHITECTURE-FREEZE-0.md` 已同步完成：
1. §11B / §13：D1/D2/D3 统一为 DURABLE_FIRST（commit → append）
2. §10：Tool output policy 统一为 OUTPUT_MUST_BE_BOUNDED / EXACT_LIMIT_OWNER = TOOL_LAYER
3. §18：移除具体 `.corrupt-<ts>` rename 冻结；仅保留架构级 CORRUPT_DB_POLICY
4. §8 / §27：model/provider 统一为 LAST_USED_MODEL 语义
5. §27：MessageDurability / DurabilityFailure / deterministic commit / explicit-noop policy 已进入 SSOT
6. sequence allocation、restore-time last_used_at、AgentEvent exception wording 已与最终冻结一致

---

## 18. 最终状态（NORMALIZE-1）

```text
ARCHITECTURE_IMPLEMENTATION_READY = YES
PRODUCTION_IMPLEMENTED = NO
NEW_CORE_PERSISTENCE_SEAM_REQUIRED = YES, DESIGN_FROZEN / IMPLEMENTATION_PENDING
```

```text
RECOMMENDED_NEXT = AG.TOPIC-SESSION-PERSISTENCE-IMPLEMENTATION-0
```

### Stable Boundary Invariant

```text
LIVE_CORE_HISTORY == DURABLE_HISTORY
AT EVERY PUBLICLY OBSERVABLE STABLE BOUNDARY:
  - after successful Run
  - after failed Run (DurabilityFailure)
  - after interrupted Run (user Stop)
  - before next Run on same TopicSession
```

```text
CORE_ORPHAN_MESSAGE_ALLOWED = NO
```

### Database Is Durable SSOT

```text
DURABLE HISTORY = cross-process SSOT
Core Session history = live materialized working copy
```

当发生无法恢复的 live divergence（理论上不应发生，因为 deterministic commit contract 防止）：
```text
do not continue using divergent Session
restart from durable DB
```
