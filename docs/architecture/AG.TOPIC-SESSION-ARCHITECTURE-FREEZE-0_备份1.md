# AG.TOPIC-SESSION-ARCHITECTURE-FREEZE-0 — TopicSession 总体架构冻结

## 1. 术语冻结

**TopicSession**（后文简称 Session）

> TopicSession 是具有稳定 ID 的长期 Agent 对话上下文，拥有有序 Message History，可以跨多个 Run 持续存在，并可跨进程死亡与 App 重启恢复。

```text
TopicSession != Run
TopicSession != ModelCall
TopicSession != ATTerminalSession
```

不叫 `LLMSession` 的原因：TopicSession 可以跨越 model/provider 选择变更而存活。model/provider 是 Session 的元数据属性，不是身份。

---

## 2. TopicSession vs Run

```text
TopicSession = durable（跨进程、跨重启）
Run          = transient（单次用户输入 → 最终答案/失败/中断）
ModelCall    = transient（单次 provider 请求）
```

```text
TopicSession
├─ UserMessage (seq=0)
│   └─ Run 1
│       ├─ ModelMessage (seq=1, toolCalls=[...])
│       ├─ ToolResultMessage (seq=2)
│       └─ ModelMessage (seq=3, final answer)
├─ UserMessage (seq=4)
│   └─ Run 2
└─ ...
```

冻结：

```text
same TopicSession → at most one active Run
different TopicSession → conceptually independent
V1: 不要求并行多 Session Run
```

---

## 3. 层级归属冻结

### AG Core（`com.androidagent.core`）

拥有：TopicSession domain semantics（Session 类、Message 类型）、ToolCall/ToolResult semantics、ordered history（List<Message>）、Run exclusivity（tryBeginRun/endRun）、Agent Loop（driveLoop）、Core restore seam（接受 List<Message> 注入）。

不知道：Room、SQLite、DAO、Android Context、database path、file I/O。

### Agent Runtime（`com.androidagent.runtime`）

拥有：current TopicSession selection、create TopicSession、restore TopicSession（启动时从 Store 加载 + 注入 Core）、resume lifecycle、begin/end Run lifecycle（send/interruptActiveRun）、startup session initialization、**persistence write orchestration**（调用 TopicSessionStore）。

不直接写 SQL。不持有 DB 连接。

### Persistence Contract（storage-agnostic）

拥有：`TopicSessionStore` 接口定义、durable record mapping、serialization boundary。

位置：`ag-runtime` 模块（与 AgentRuntime 同层，因为 Runtime 是调用者）。不引入 Room 依赖。

### Android Persistence Implementation

拥有：Room、SQLite、Entity、DAO、Database、Migration、`RoomTopicSessionStore`（实现 TopicSessionStore）。

位置：`:app` 模块（Android infrastructure）。

### Host / Presentation

拥有：SessionHolder（process-scoped live holder）、TranscriptModel（presentation projection）、ChatActivity、UI reconstruction。

```text
TranscriptModel != durable SSOT
SessionHolder != persistent storage
```

### AT

```text
AT knows nothing about TopicSession
```

AT 继续只负责 Android execution。TopicSession persistence 与 AT 状态无依赖。

---

## 4. V1 功能边界

### V1 REQUIRED

```text
Create TopicSession
Persist TopicSession
Persist Message history
Restore latest TopicSession
Resume conversation
Process-death recovery
App-restart recovery
Dangling ToolCall detection
Interrupted Tool marking (synthetic ToolResult)
Never auto-replay unknown Tool
Rebuild TranscriptModel from restored history
```

### V1 OPTIONAL

```text
New Topic button
Session title (nullable, AI or user-set)
Delete Session
last_used ordering
```

### POST-V1

```text
Session picker / multi-session UI
rename / archive / search
fork / branch / rewind
compaction
cloud sync / cross-device
export/import
run history
PTY/process restore
reasoning persistence
AgentEvent durability
```

---

## 5. 数据库技术冻结

```text
DATABASE_ENGINE = SQLite
DATABASE_ACCESS_LAYER = Room
DATABASE_LOCATION = Android app-private storage (context.getDatabasePath)
DATABASE_VERSION = 1
```

```text
SQLite = physical database engine
Room   = Android persistence access layer
```

Room 不是 domain model。Room Entity 是 persistence DTO，不是 AG Core domain type。

---

## 6. 数据库层级位置

```text
AG Core
   ↑
   │ TopicSession / Message (domain types)
   │
Agent Runtime
   │
   │ TopicSessionStore (interface, storage-agnostic)
   ↓
RoomTopicSessionStore (:app)
   │
   │ Room (Entity, DAO, Database)
   ↓
SQLite (app-private file)
```

数据库只能存在于 Android-side infrastructure。AG Core 和 Agent Runtime 不依赖 Room。

---

## 7. V1 数据库逻辑模型

```text
DATABASE_LOGICAL_MODEL = HYBRID (2-table)
DATABASE_TABLES_V1 = topic_sessions + messages
```

不增加：

```text
runs / tool_calls / tool_results / message_parts / events / checkpoints / compactions
```

理由：4/4 成熟 Agent 无独立 durable Run；ToolCall/Result 全部作为 message 的 part/line/block 存在，不是独立表。

---

## 8. topic_sessions Schema

```sql
CREATE TABLE topic_sessions (
    id           TEXT PRIMARY KEY,
    created_at   INTEGER NOT NULL,
    last_used_at INTEGER NOT NULL,
    model        TEXT NULL,
    provider     TEXT NULL,
    title        TEXT NULL
);
```

字段决定：

| 字段 | 决定 | 理由 |
|---|---|---|
| `id` | TEXT PK | UUID string；与现有 `sessionIdFactory` 兼容 |
| `created_at` | INTEGER (epoch ms) | 排序 + 元数据 |
| `last_used_at` | INTEGER (epoch ms) | "latest session" 查询路径 |
| `model` | TEXT NULL | 创建时元数据（方案 A）；可 NULL（未设 provider 时） |
| `provider` | TEXT NULL | 同上 |
| `title` | TEXT NULL | V1 OPTIONAL；nullable 不影响结构；为 V1+ UI 预留 |

### model/provider 归属决定

**方案 A：Session creation metadata**（冻结）

```text
model/provider = topic_sessions 列（创建时写入，后续可 UPDATE）
```

不采用方案 B（per-message）：V1 简化；TopicSession identity 不与特定 LLM 强绑定；用户切换 provider 时 UPDATE 列即可。

### Future-compatible 字段决定

```text
parent_session_id     = 不加入 V1（POST-V1 fork 时 migration 加入）
compaction_boundary   = 不加入 V1（POST-V1 compaction 时 migration 加入）
archived_at           = 不加入 V1（POST-V1 archive 时 migration 加入）
status                = 不加入 V1（V1 无 session 状态机）
```

原则：不为未实现功能提前污染 V1 schema。Room migration 成本极低（ALTER TABLE ADD COLUMN），需要时再加。

---

## 9. messages Schema

```sql
CREATE TABLE messages (
    id               TEXT PRIMARY KEY,
    topic_session_id TEXT NOT NULL,
    sequence         INTEGER NOT NULL,
    type             TEXT NOT NULL,
    payload_json     TEXT NOT NULL,
    created_at       INTEGER NOT NULL,
    FOREIGN KEY (topic_session_id) REFERENCES topic_sessions(id) ON DELETE CASCADE,
    UNIQUE (topic_session_id, sequence)
);

CREATE INDEX idx_messages_session_seq ON messages(topic_session_id, sequence);
CREATE INDEX idx_sessions_last_used ON topic_sessions(last_used_at);
```

| 字段 | 决定 | 理由 |
|---|---|---|
| `id` | TEXT PK | UUID per message；稳定引用 |
| `topic_session_id` | FK → topic_sessions.id, CASCADE | 删除 session 级联删除 messages |
| `sequence` | INTEGER, UNIQUE per session | **显式 sequence**（不依赖 rowid）；domain ordering |
| `type` | TEXT: `USER` / `MODEL` / `TOOL_RESULT` | 对齐 domain 三类型 |
| `payload_json` | TEXT (JSON) | 类型化 payload（见 §10） |
| `created_at` | INTEGER (epoch ms) | 时间戳 |

**MESSAGE_ORDERING = EXPLICIT_SEQUENCE**

```text
sequence 由写入者（Runtime）分配 = 当前 max(sequence) + 1
不依赖 SQLite rowid 作为 domain ordering
```

---

## 10. Message 序列化模型

```text
MESSAGE_PERSISTENCE_MODEL = DOMAIN_ALIGNED
```

不重构现有 AG Message domain。直接映射：

```text
UserMessage       → type=USER,       payload_json={"content":"..."}
ModelMessage      → type=MODEL,      payload_json={"content":"...","toolCalls":[...]}
ToolResultMessage → type=TOOL_RESULT, payload_json={"toolCallId":"...","toolName":"...","success":bool,"output":"...","error":null}
```

### 序列化边界

```text
AG Core domain (Message sealed types)
        ↕  (serializer/deserializer, in ag-runtime or :app)
Persistence DTO (payload_json fields)
        ↕  (Room Entity)
SQLite row (payload_json TEXT)
```

serializer 是纯函数：`Message → (type, payload_json)` 和 `(type, payload_json) → Message`。位置：`ag-runtime`（与 TopicSessionStore contract 同层）或 `:app`（若 serializer 需要 provider 相关逻辑）。V1 放 `ag-runtime`（纯 Kotlin，无 Android 依赖）。

### Provider Wire Format 分离

```text
Persistent Message schema != OpenAI wire format != Provider-specific JSON
```

持久化的是 AG DOMAIN MESSAGE。Provider request/response JSON 不是 durable SSOT。

### 大 Tool Output

V1 行为：

```text
OUTPUT_MUST_BE_BOUNDED = YES
EXACT_LIMIT_OWNER = TOOL_LAYER (不是 TopicSession)
```

TopicSession persistence 复用 canonical bounded ToolResult（AG.OUTPUT-BOUND 路线）。不在此处定义独立 truncation policy。

### Reasoning / Thinking

```text
REASONING_PERSISTENCE = NO (V1)
```

理由：reasoning 是 provider-specific 的中间过程；当前 domain Message 不含 reasoning；4/4 成熟 Agent 的 reasoning 持久化是 optional/非核心。V1 不持久化。

### AgentEvent

```text
AGENT_EVENT_DURABILITY = NO (V1)
```

理由：AgentEvent 是运行时回调（transient observation）；domain Message 已包含所有需要恢复的信息；4/4 成熟 Agent 不需要独立 durable event log（OpenCode 例外但其 projector 模式 V1 不采用）。

---

## 11. TopicSessionStore Contract

位置：`ag-runtime` 模块（storage-agnostic interface）。

**SEMANTICS = FROZEN / API SHAPE = IMPLEMENTATION-LEVEL**

语义要求（冻结）：

```text
createSession: 创建 session 行，原子写入初始元数据
appendMessage: 原子分配 sequence + INSERT message row + UPDATE last_used_at（一个事务）
loadSession: 按 id 加载元数据
loadLatestSession: 按 last_used_at DESC 取第一条
loadMessages: 按 sequence ASC 加载完整有序 history
updateLastUsed: 更新 last_used_at（可内嵌于 appendMessage）
deleteSession: V1 OPTIONAL; 级联删除 messages
listSessions: V1 OPTIONAL; 按 last_used_at DESC
```

**SEQUENCE_ALLOCATION = ATOMIC_WITH_MESSAGE_INSERT**

```text
禁止: SELECT MAX(sequence) → release → INSERT（非原子两步）
要求: one short transaction 内完成 read next sequence + insert message
保留: UNIQUE(topic_session_id, sequence) 作为最终约束
```

**SESSIONSTORE_CONTRACT_OWNER = ag-runtime（storage-agnostic AG side）**
**ROOM_IMPLEMENTATION_OWNER = :app（Android infrastructure）**

---

## 11A. AgentEvent vs Durability Contract（NORMALIZE-1 新增）

### 源码审计结果

**AgentEventSink 真实签名**（`AgentEvent.kt:53-54`）：

```kotlin
fun interface AgentEventSink {
    fun emit(event: AgentEvent)  // 同步、非 suspend、无返回值
}
```

- **SYNCHRONOUS = YES**（`fun`，非 `suspend fun`）
- **SUSPEND = NO**
- **EXCEPTION_PROPAGATION = SWALLOWED**（`AgentCore.emitTerminal` line 404-406: `catch (e: Exception) { }` 吞掉）
- **CALLER_THREAD_OR_COROUTINE = 同协程同步调用**

**AgentEvent 真实 emit 顺序**（`AgentCore.kt` 源码）：

| 行号 | 操作 | 说明 |
|---|---|---|
| 280 | `session.append(UserMessage(userInput))` | 内存 append（无 event） |
| 289 | `sink.emit(ModelTurnStarted(step))` | 在 provider 调用前 |
| 292 | `modelProvider.complete(...)` | LLM 调用 |
| 326 | `sink.emit(ModelTurnFinished(step, turn))` | **在 session.append 之前** |
| 331 | `session.append(ModelMessage(turn.content))` | FinalAnswer 路径 |
| 343 | `session.append(ModelMessage(turn.content, calls))` | ToolCalls 路径 |
| 352 | `sink.emit(ToolCallStarted(call))` | 在 dispatch 前 |
| 354 | `toolRegistry.dispatch(call, ...)` | **外部副作用** |
| 372 | `session.append(ToolResultMessage(...))` | 内存 append |
| 373 | `sink.emit(ToolCallFinished(call, result))` | 在 append 之后 |

### 关键发现

1. `ModelTurnFinished`（line 326）在 `session.append(ModelMessage)`（line 331/343）**之前** emit
2. `AgentEventSink.emit` 是**同步非 suspend** —— 无法在 emit 中阻塞等待 Room commit
3. `emitTerminal` 吞掉 sink 异常（line 404-406）—— 即使 sink 失败，Core 继续
4. 没有 "await durable commit before continuing" 的机制

### 冻结

```text
AGENT_EVENT_SINK_AS_DURABILITY_CONTRACT = NO
AGENT_EVENT_SINK_ROLE = OBSERVABILITY_ONLY
AGENT_EVENT_PUBLIC_SEMANTICS = UNCHANGED
```

```text
AgentEvent = semantic observation（UI / trace / debug）
AgentEventSink = observation / tracing contract
TopicSession durability = persistence correctness contract

AGENT_EVENT_SINK != DURABILITY CONTRACT
```

如果实现阶段复用 AgentEvent 作为触发信号：

```text
REUSE_AS_TRIGGER = ALLOWED
DATABASE_CORRECTNESS must NOT depend on best-effort observation delivery
```

### NEW_CORE_PERSISTENCE_SEAM_REQUIRED = YES

当前 Core 无机制在 "durable commit 完成" 与 "cross safety boundary" 之间建立 ordering guarantee。需要最小 Core seam（语义冻结，实现不在本轮）：

```text
DURABILITY_SEAM 语义要求：
1. Core 在跨越安全边界前，必须等待 durable commit 完成
2. 如果 durable commit 失败，Core 必须停止 Run（不继续）
3. Seam 必须是 suspend（可以等待 IO）
4. Seam 必须在 Tool dispatch 之前（ToolCall boundary）
5. Seam 必须在 next modelProvider.complete() 之前（ToolResult boundary）
6. Seam 失败 → Run 终止（structured failure，不是 exception escape）
```

最小概念形式（非最终 API）：

```kotlin
// 语义：per-message durable commit hook（Core 在安全边界前调用）
// 实现：Runtime 注入的 suspend 回调，内部调用 TopicSessionStore.appendMessage
// 失败：抛出特定 exception 或返回 failure → Core 终止 Run
```

**ARCHITECTURE_IMPLEMENTATION_READY = YES**（durability seam 设计已冻结，见 AG.TOPIC-SESSION-DURABILITY-SEAM-PREFLIGHT-0）
**PRODUCTION_IMPLEMENTED = NO**
**NEXT = AG.TOPIC-SESSION-PERSISTENCE-IMPLEMENTATION-0**

---

## 11B. Run Admission vs User Durability（NORMALIZE-1 新增）

### 真实 Run Admission 顺序

```text
AgentRuntime.send(userInput)
    → runActive.compareAndSet(false, true)  [GLOBAL_RUNTIME_GUARD]
    → activeRun.set(control)
    → coroutineScope { async { agentCore.run(...) } }
        → session(id) [computeIfAbsent]
        → session.tryBeginRun()  [PER_SESSION_CORE_GUARD]
            → false → return SessionBusy（无 event、无 append）
            → true:
                → sink.emit(RunStarted)
                → driveLoop:
                    → session.append(UserMessage)  [内存]
                    → modelProvider.complete(...)  [第一个 ModelCall]
```

**RUN_ADMISSION_BOUNDARY = VERIFIED**

两层 guard：
```text
GLOBAL_RUNTIME_GUARD = AgentRuntime.runActive (AtomicBoolean CAS)
PER_SESSION_CORE_GUARD = Session.tryBeginRun() (synchronized CAS)
```

### SAFE_USER_DURABILITY_POINT

问题：如果在 `agentCore.run()` 之前 persist UserMessage，但 Core 返回 `SessionBusy`，则 DB 有 UserMessage 但 Core history 没有。

**冻结方案**：

```text
SAFE_USER_DURABILITY_POINT = AFTER Run admission, BEFORE first ModelCall
```

实现路径（通过 durability seam）：

```text
Core.driveLoop 入口:
    session.append(UserMessage) [内存]
    → durabilityHook.persist(UserMessage)  [suspend; 等待 commit]
        → 成功 → 继续
        → 失败 → Run 终止 (PersistenceFailure)
    → modelProvider.complete(...)
```

这保证：
```text
A. rejected Run writes no durable UserMessage（tryBeginRun 失败 → 不进 driveLoop → 不 persist）
B. accepted UserMessage is durable before first ModelCall（persist 在 complete 之前）
```

**USER_MESSAGE_DURABLE_BEFORE_MODEL = YES**
**NO_DUPLICATE_DURABLE_USERMESSAGE = YES**（Core 内存 append 1 次 + DB persist 1 次 = 各 1 行，无重复）

---

## 12. Core Restore Seam

**CORE_RESTORE_SEAM = FROZEN**

选择 **候选 C 变体**：

```kotlin
// ag-core: Session.kt（修改最小）
class Session internal constructor(val id: String) {
    private val monitor = Any()
    private val messages = mutableListOf<Message>()  // 不变
    private var activeRun = false

    // 新增：批量注入初始 history（restore 专用）
    internal fun restoreHistory(history: List<Message>) {
        synchronized(monitor) {
            messages.clear()
            messages.addAll(history)
        }
    }

    // 现有方法不变:
    fun history(): List<Message>
    internal fun append(message: Message)
    internal fun tryBeginRun(): Boolean
    internal fun endRun()
}
```

```kotlin
// ag-core: AgentCore.kt（修改最小）
class AgentCore(...) {
    // 现有: fun session(id: String): Session
    // 新增:
    fun restoreSession(id: String, initialHistory: List<Message>): Session {
        val session = Session(id)
        session.restoreHistory(initialHistory)
        sessions[id] = session
        return session
    }
}
```

约束：
```text
PURE KOTLIN = YES
NO ROOM = YES
NO ANDROID = YES
NO STORAGE DEPENDENCY = YES
```

恢复路径：
```text
Room DAO → List<MessageRecord>
→ serializer: List<MessageRecord> → List<Message>
→ AgentCore.restoreSession(id, messages)
→ Session with full history
→ TranscriptModel re-projection
→ READY
```

---

## 13. 持久化写入协议

### Write Timing（冻结，NORMALIZE-1 修正）

| Message 类型 | 持久化时机 | 保证 | 机制 |
|---|---|---|---|
| UserMessage | **AFTER** Run admission, **BEFORE** first ModelCall | USER_MESSAGE_DURABLE_BEFORE_MODEL = YES | Core durability seam（suspend） |
| ModelMessage (含 ToolCalls) | **BEFORE** tool dispatch（第一个 call 之前） | TOOLCALL_DURABLE_BEFORE_EXTERNAL_SIDE_EFFECT = YES | Core durability seam（suspend） |
| ModelMessage (final answer) | 同 ModelMessage | 同上 | Core durability seam |
| ToolResultMessage | **AFTER** tool completion, **BEFORE** next ModelCall | TOOLRESULT_DURABLE_BEFORE_NEXT_MODEL = YES | Core durability seam（suspend） |

**所有持久化通过 Core durability seam（suspend hook）执行，不通过 AgentEventSink。**

### 真实 Core 执行顺序 + Durability Boundary（源码验证）

```text
[Runtime] send(userInput)
    → runActive.CAS [GLOBAL_GUARD]
    → Core.run(sessionId, userInput)
        → session.tryBeginRun() [CORE_GUARD]
            → REJECTED: return SessionBusy（无 DB write）
            → ADMITTED:
                → driveLoop:
                    → session.append(UserMessage) [内存, line 280]
                    → ★ D1: durabilityHook.persist(UserMessage) [suspend; TX1 COMMIT]
                        → FAIL → Run terminated (PersistenceFailure)
                    → loop:
                        → modelProvider.complete(...) [LLM; no DB]
                        → session.append(ModelMessage) [内存, line 331/343]
                        → if ToolCalls:
                            → ★ D2: durabilityHook.persist(ModelMessage) [suspend; TX2 COMMIT]
                                → FAIL → Run terminated (PersistenceFailure)
                                → Tool NOT dispatched (安全)
                            → for each call:
                                → toolRegistry.dispatch(call) [外部副作用]
                                → session.append(ToolResultMessage) [内存, line 372]
                                → ★ D3: durabilityHook.persist(ToolResultMessage) [suspend; TX3 COMMIT]
                                    → FAIL → Run terminated (PersistenceFailure)
                                    → next ModelCall NOT issued (安全)
                        → if FinalAnswer:
                            → (D2 已覆盖)
                            → return Answer
```

### Streaming Policy

```text
MODEL_STREAM_PARTIAL_PERSISTENCE = NO
```

```text
streaming token → UI only (TranscriptModel)
complete ModelMessage → durable (after stream ends, via D2)
process death during streaming → partial output discarded
```

TranscriptModel 的 partial text 不是 durable state。

### Transaction Boundary

```text
TRANSACTION_BOUNDARY = SHORT_DURABLE_FACT_TRANSACTION
```

```text
禁止: one DB transaction covering LLM + Tool + entire Run
冻结: one durable fact = one short DB transaction (D1/D2/D3 各一个 TX)
```

```text
TX1 (D1): INSERT UserMessage + UPDATE last_used_at → COMMIT
    [LLM call - no DB]
TX2 (D2): INSERT ModelMessage + UPDATE last_used_at → COMMIT
    [Tool execution - no DB]
TX3 (D3): INSERT ToolResultMessage + UPDATE last_used_at → COMMIT
    [Next LLM call - no DB]
```

每个 TX 是单行 INSERT + last_used_at UPDATE，在一个 `withTransaction` 内。

### LAST_USED_UPDATE_RULE

```text
LAST_USED_UPDATE_RULE = ON_SUCCESSFUL_DURABLE_MESSAGE_APPEND
```

每次 D1/D2/D3 成功 commit 时，同一事务内 UPDATE `topic_sessions.last_used_at = now()`。不在 UI open 时更新。

### Core Memory vs Durable DB Ordering

| Message | 顺序 | Crash consequence |
|---|---|---|
| UserMessage | **DURABLE_FIRST**（D1 commit → append） | DF3: DB 有, Core 无 → 进程死 → restart 恢复 → SAFE |
| ModelMessage | **DURABLE_FIRST**（D2 commit → append） | DF3: DB 有, Core 无 → 进程死 → restart 恢复 → SAFE |
| ToolResultMessage | **DURABLE_FIRST**（D3 commit → append） | DF3: DB 有, Core 无 → 进程死 → restart 恢复 → SAFE |

**关键**：在外部副作用（tool dispatch）之前，DB 状态已满足安全要求（D2 已完成）。D3 的 crash window 被 DANGLING 机制覆盖。

---

## 14. Crash State Model（NORMALIZE-1 修正：新增 C0, C5）

### C0: Run rejected before admission

```text
场景: tryBeginRun() 返回 false（SessionBusy）
DB 状态: 无写入（SAFE_USER_DURABILITY_POINT 在 admission 之后）
恢复: N/A（Run 从未开始；无 DB 影响）
```

### C1: UserMessage durable, Model not called

```text
场景: D1 commit 成功；进程在 modelProvider.complete() 前/中死亡
DB 状态: UserMessage 存在；无 ModelMessage
恢复: SAFE
行为: 正常 resume；UserMessage 在 history 中；等待新 input 或继续
```

### C2: Model request in flight, no ModelMessage durable

```text
场景: modelProvider.complete() 进行中；进程死亡
DB 状态: 最后一条 durable msg 是 UserMessage（或更早的 ToolResult）
恢复: SAFE
行为: 前一条 UserMessage 存在；未完成的 model execution 丢弃；
     用户可重新发送或继续对话
```

### C3: ToolCall durable, Tool not started / in flight / possibly completed, ToolResult absent

```text
场景: D2 commit 成功（ModelMessage with ToolCalls）；进程在 tool dispatch 前/中/后但 D3 前死亡
DB 状态: ModelMessage(toolCalls) 存在；无对应 ToolResultMessage
定义: DANGLING_TOOL_CALL
检测: 加载 history 后，扫描每个 MODEL type message 的 toolCalls[]，
      检查每个 toolCall.id 是否有对应 TOOL_RESULT message
      缺失 → DANGLING
修复: 对每个 dangling call 追加 SYNTHETIC_INTERRUPTED_UNKNOWN_RESULT（见 §15）
语义: TOOL_EXECUTION_OUTCOME = UNKNOWN（不是 FAILED）
```

### C4: ToolResult durable, next model request not yet issued

```text
场景: D3 commit 成功；进程在 next modelProvider.complete() 前死亡
DB 状态: ToolResult 存在；history 闭合
恢复: SAFE
行为: 正常 resume；history 完整闭合；等待新 input
```

### C5: Tool completed, ToolResult DB commit failed（NORMALIZE-1 新增）

```text
场景: tool 执行完成（外部状态可能已改变）；D3 commit 失败
DB 状态: ModelMessage(toolCalls) 存在；ToolResult 未写入
Live Run 行为:
    → PERSISTENCE_FAILURE → Run 立即停止（不继续 next ModelCall）
    → 外部状态: 可能已改变（不可知）
    → 不尝试回滚外部状态
下次恢复:
    → 看到 C3 状态（ToolCall durable, ToolResult absent）
    → DANGLING → SYNTHETIC_INTERRUPTED_UNKNOWN_RESULT
    → 模型看到 UNKNOWN → 自主决定是否检查/重试
```

**C5 是 P3 persistence failure 的 crash 表现形式。**

---

## 15. Dangling Tool 安全规则（NORMALIZE-1 规范化）

```text
DANGLING_TOOL_SEMANTICS = UNKNOWN
SYNTHETIC_RESULT_KIND = INTERRUPTED_UNKNOWN
```

```text
ToolCall durable + matching ToolResult absent
→ TOOL_EXECUTION_OUTCOME = UNKNOWN
→ NEVER AUTO-REPLAY
```

原因：无法区分：
```text
Tool never started（进程在 dispatch 前死亡）
vs
Tool completed external side effect but process died before ToolResult commit
```

### AUTO_TOOL_REPLAY = NO

```text
RUNTIME_AUTO_REPLAY = FORBIDDEN
MODEL_INITIATED_NEW_CALL = ALLOWED
```

注意区分：
- **禁止**: Runtime 自动重放旧 ToolCall
- **允许**: 模型在后续 turn 中自主决定发起新的 ToolCall（这是正常 Agent 行为）

### SYNTHETIC_INTERRUPTED_UNKNOWN_RESULT（正式名称）

恢复时发现 dangling ToolCall → 追加 synthetic `ToolResultMessage`：

```kotlin
ToolResultMessage(
    toolCallId = danglingCall.id,
    toolName = danglingCall.name,
    result = ToolResult(
        success = false,
        output = "",
        error = "INTERRUPTED_UNKNOWN: Previous tool execution was interrupted before " +
                "a durable result was recorded. The tool may or may not have taken effect. " +
                "Do not assume failure. Do not automatically repeat the operation without " +
                "first checking current state or obtaining appropriate confirmation."
    )
)
```

**语义冻结**：
```text
success = false  → 不代表 tool definitely failed
                 → 代表 "no successful result was recorded"
TOOL_EXECUTION_OUTCOME = UNKNOWN
MAY_HAVE_EXECUTED = YES
NOT_CONFIRMED_FAILED = YES
NO_AUTOMATIC_REPLAY = YES
```

**不得**简称 "synthetic failure"。正式名称：`SYNTHETIC_INTERRUPTED_UNKNOWN_RESULT`。

### Model-facing UNKNOWN 语义（冻结）

恢复后送给模型的 ToolResult 必须传达：

```text
1. 前一次 tool 执行在 durable result 记录之前被中断
2. tool 可能已执行，也可能未执行（MAY_HAVE_EXECUTED）
3. 不得假设失败（NOT_CONFIRMED_FAILED）
4. 不得自动重试（NO_AUTOMATIC_REPLAY）
5. 应先检查当前状态或获取确认后再决定
```

### Dangling Repair Ordering（NORMALIZE-1 规范化）

```text
DANGLING_REPAIR_ORDER = DURABLE_REPAIR_FIRST_CORE_HYDRATE_SECOND
```

```text
load durable history (from DB)
→ detect dangling calls
→ construct repaired history (in-memory: original + synthetic results)
→ persist synthetic INTERRUPTED_UNKNOWN results (store.appendMessage → COMMIT)
→ ONLY AFTER successful commit:
    → hydrate Core with repaired durable history (restoreSession)
```

**不得**出现 "Core repaired but DB repair failed" 的分叉。DB repair 成功是 Core hydrate 的前提。

### Synthetic Result Durability

```text
SYNTHETIC_RESULT_PERSISTED = YES
```

修复后的 history 本身再次成为 durable、闭合状态。下次 resume 不会再发现 dangling。

---

## 16. Resume 语义

```text
RESUME_SEMANTICS = REOPEN_HISTORY_REPAIR_AND_CONTINUE
```

```text
TopicSession Resume
= reopen durable conversation history
+ repair interrupted boundaries (dangling tools)
+ hydrate Core TopicSession (restoreSession)
+ rebuild TranscriptModel projection
+ continue accepting new user input
```

明确不是：

```text
NOT: resume old Run
NOT: resume old coroutine
NOT: resume old HTTP request
NOT: resume old Tool process
NOT: resume AT PTY
NOT: resume ADB connection
```

TopicSession restored + ADB currently disconnected = **合法状态**。Tool 真正调用时由 AT 自己恢复 execution readiness。

---

## 17. Startup State Machine

```text
APP START
    ↓
OPEN DATABASE (Room, app-private)
    ├─ DB absent → CREATE (Room auto-creates schema v1)
    └─ DB exists → OPEN
    ↓
LOAD LATEST TOPIC SESSION (ORDER BY last_used_at DESC LIMIT 1)
    ├─ NONE
    │   ↓
    │   CREATE NEW TOPIC SESSION
    │   → store.createSession(newId, model, provider)
    │   → Core: session(newId) (empty history)
    │   → READY
    │
    └─ FOUND (record)
        ↓
        LOAD ORDERED MESSAGES (WHERE topic_session_id=? ORDER BY sequence)
        ↓
        DESERIALIZE → List<Message>
        ├─ decode failure on a row → SESSION_UNRECOVERABLE (see §18)
        ├─ sequence gap → SESSION_UNRECOVERABLE (V1: append-only, no delete)
        └─ success
            ↓
            DETECT DANGLING TOOLCALLS
            ↓
            IF dangling found:
                → construct synthetic INTERRUPTED_UNKNOWN results
                → persist to DB (store.appendMessage → COMMIT)  [DURABLE REPAIR FIRST]
                → append to in-memory list
            ↓
            RESTORE CORE SESSION  [CORE HYDRATE SECOND, only after DB repair committed]
            → AgentCore.restoreSession(id, repairedMessages)
            ↓
            UPDATE last_used_at
            ↓
            BUILD TRANSCRIPT PROJECTION
            → TranscriptModel re-project from restored messages
            ↓
            READY (accepting user input)
```

---

## 18. No-data / Corruption Behavior（NORMALIZE-1 修正）

```text
DATABASE_ABSENT     → CREATE new (Room auto); create new session. RECOVERABLE.
DATABASE_EMPTY      → No sessions; create new session. RECOVERABLE.
LATEST_SESSION_OK   → Normal resume. RECOVERABLE.
MESSAGE_DECODE_FAIL → Single message payload_json unparseable:
                       SESSION_UNRECOVERABLE (V1: start new session;
                       log error; do NOT delete old session data)
INVALID_SEQUENCE    → Gap in sequence:
                       SESSION_UNRECOVERABLE (V1: same as above)
CORRUPT_PAYLOAD     → Same as MESSAGE_DECODE_FAIL
DATABASE_CORRUPT    → SQLite integrity check fails:
                       DATABASE_UNRECOVERABLE (see CORRUPT_DB_POLICY: PRESERVE + FRESH_RECOVERY)

```

原则：
```text
do NOT silently destroy database
do NOT reset entire DB automatically (except confirmed corrupt)
do NOT lose other sessions when one is unrecoverable
```

三级恢复：
```text
RECOVERABLE             → 正常 resume
SESSION_UNRECOVERABLE   → 该 session 不可恢复；创建新 session；旧数据保留
DATABASE_UNRECOVERABLE  → DB 损坏；PRESERVE_CORRUPT_STORE + CONTROLLED_FRESH_STORE_RECOVERY
```

### CORRUPT_DB_POLICY（架构级，NORMALIZE-1 修正）

```text
CORRUPT_DB_POLICY =
PRESERVE_CORRUPT_STORE
+ NO_DESTRUCTIVE_MIGRATION
+ CONTROLLED_FRESH_STORE_RECOVERY
```

- 损坏的 DB 数据**必须保留**（不删除、不覆盖）
- 不使用 destructive migration 作为 recovery
- 创建 fresh store 继续工作（controlled recovery）
- 具体操作（rename/copy/quarantine/-wal/-shm handling/integrity check method）= **IMPLEMENTATION_DETAIL**

### V1_CONTIGUOUS_SEQUENCE_REQUIRED = YES

```text
依据: V1 messages append-only，无 DELETE，无 migration gap。
     sequence 必须严格连续 (0, 1, 2, ...)。Gap = 异常 → SESSION_UNRECOVERABLE。
POST-V1: 若引入 delete/migration，需重新评估。
```

详细 corruption recovery UI（如 "恢复失败，是否开始新对话？"）= V1 OPTIONAL。

---

## 19. 数据库 Migration 策略

```text
DATABASE_VERSION = 1
```

```text
future schema changes require explicit Room Migration
```

```text
DESTRUCTIVE_MIGRATION_ALLOWED = NO (production)
```

禁止 `fallbackToDestructiveMigration()` 用于正式 TopicSession 数据库。

测试环境例外：单元测试可用 in-memory DB 或 `allowMainThreadQueries()`；但 migration 逻辑本身不跳过。

Migration 文件位置：`:app` 模块（与 Room Database 类同层）。

---

## 20. SQLite 配置（NORMALIZE-1 修正）

```text
DATABASE_JOURNAL_MODE = WRITE_AHEAD_LOGGING
WAL_CONFIGURATION = EXPLICIT（实现阶段必须显式设置，不依赖 Room 默认值）
FOREIGN_KEYS = REQUIRED（messages.topic_session_id → topic_sessions.id ON DELETE CASCADE，由 Room schema 保证）
TRANSACTION = Per-durable-fact（Room @Transactional 或显式 withTransaction）
```

不做低价值 SQLite micro-tuning（busy_timeout、cache_size 等使用 Room/SQLite 默认值）。

**不依赖 "Room default always equals X"**。冻结的是我们要求的行为，实现必须显式配置。

---

## 21. 数据库位置 / 安全

```text
LOCATION = context.getDatabasePath("topic_sessions.db")
         = /data/user/0/<package>/databases/topic_sessions.db
         (app-private, 不可被其他 app 访问)
```

禁止：
```text
external public storage
shared filesystem
world-readable permissions
```

数据库不得保存：
```text
provider API key
ADB private key
pairing code
Authorization header
```

**风险记录**：Tool output 若包含 secret（如 `cat /path/to/key`），会进入 `payload_json`。V1 不做 redaction。POST-V1 hardening：tool output redaction pipeline。

---

## 22. SessionHolder 角色

```text
SESSIONHOLDER = PROCESS_SCOPED_LIVE_HOLDER
SESSIONHOLDER_AS_DURABLE_STORE = NO
```

Process death → SessionHolder lost → 正常。重新从 DB / Runtime restore。

SessionHolder 继续持有：
- `ChatCompositionRoot`（Core + Runtime + Tools wiring）
- `TranscriptModel`（live projection）
- 状态字符串

SessionHolder 不持有：
- DB 连接
- 持久化状态
- "source of truth" 数据

---

## 23. TranscriptModel 角色

```text
TRANSCRIPT_MODEL = PRESENTATION_PROJECTION
TRANSCRIPT_MODEL_AS_SSOT = NO
```

```text
TopicSession Message History (durable SSOT)
    ↓ re-project (restore 时)
TranscriptModel (in-memory blocks)
    ↓ render
UI (TextView)
```

不得 persist rendered text 作为 Session truth。恢复时从 Message History 重建 Block 列表。

映射规则（restore 时）：
```text
UserMessage       → Block.User(content)
ModelMessage      → Block.Assistant(content, final=无后续message)
                    + Block.Shell(command) per toolCall
ToolResultMessage → Block.Result(render(result))
RunOutcome.Interrupted (implied by synthetic) → Block.Interrupted
```

---

## 24. New Topic 行为

```text
NEW_TOPIC = V1_OPTIONAL_BUT_CHEAP
```

语义：
```text
create new TopicSession (new UUID)
make it current (Runtime.sessionId = newId)
do NOT delete old TopicSession
do NOT clear DB
do NOT overwrite previous history
```

UI：一个 "New Topic" 按钮（或 menu item）。点击 → `runtime.newSession()`（现有方法）→ Runtime 调用 `store.createSession()` → Core 获得空 Session。

---

## 25. TopicSession 与 AT 边界

```text
AT_TOPIC_SESSION_DEPENDENCY = NONE
```

TopicSession persistence 无依赖：
```text
AT Engine state
ADB connection
AT Terminal Session
Wireless Debugging session
```

```text
TopicSession restored + ADB disconnected = 合法状态
```

Tool 真正调用时（`AndroidShellToolImpl.execute`），由 `ShellConnection` / `AtExecutionAdapter` 自己恢复 execution readiness（AT 的 NSD discovery + connect）。TopicSession 层不关心。

---

## 26. 架构图

### 主图：TopicSession Persistence

```text
Host / UI (:app)
│
├─ ChatActivity
├─ TranscriptModel (presentation projection)
└─ SessionHolder (process-scoped live holder)
        │
        ▼
Agent Runtime (:ag-runtime)
├─ current TopicSession selection
├─ Run lifecycle (send / interrupt)
├─ Persistence write orchestration
└─ TopicSessionStore (interface)
        │
        ├────────────────→ AG Core (:ag-core)
        │                  ├─ Session (TopicSession domain)
        │                  ├─ Message (User/Model/ToolResult)
        │                  ├─ ToolCall / ToolResult
        │                  ├─ AgentCore (Agent Loop)
        │                  └─ restoreSession() [seam]
        │
        ▼
RoomTopicSessionStore (:app)
        │
        ▼
Room (Entity, DAO, Database v1)
        │
        ▼
SQLite (app-private: databases/topic_sessions.db)
```

### 独立图：Android Execution（与 TopicSession 无关）

```text
AG Core
    ↓ ToolCall (via ToolRegistry.dispatch)
Concrete Android Tool (:tool-android-shell)
    ↓
integration-at (AtExecutionAdapter)
    ↓
AT Engine (ATEngine, AdbRuntime)
    ↓
Android (shell / ADB)
```

两个图独立。TopicSession → AT database 的边**不存在**。

---

## 27. SSOT 决策汇总

| 决策项 | 值 | 状态 |
|---|---|---|
| TOPIC_SESSION_TERM | TopicSession | FROZEN |
| TOPIC_SESSION_DOMAIN_OWNER | AG Core | FROZEN |
| TOPIC_SESSION_LIFECYCLE_OWNER | AG Runtime | FROZEN |
| RUN_DURABILITY | TRANSIENT | FROZEN |
| DATABASE_ENGINE | SQLite | FROZEN |
| DATABASE_ACCESS_LAYER | Room | FROZEN |
| DATABASE_LOCATION | app-private (getDatabasePath) | FROZEN |
| DATABASE_LOGICAL_MODEL | HYBRID (2-table) | FROZEN |
| DATABASE_TABLE_COUNT_V1 | 2 (topic_sessions + messages) | FROZEN |
| MESSAGE_ORDERING | EXPLICIT_SEQUENCE | FROZEN |
| MESSAGE_SERIALIZATION | DOMAIN_ALIGNED JSON | FROZEN |
| SESSIONSTORE_OWNER | ag-runtime (contract) / :app (impl) | FROZEN |
| CORE_RESTORE_SEAM | Session.restoreHistory + AgentCore.restoreSession | FROZEN |
| USER_WRITE_TIMING | BEFORE_MODEL | FROZEN |
| MODEL_WRITE_TIMING | AFTER_STREAM_COMPLETE (ModelTurnFinished) | FROZEN |
| TOOLCALL_WRITE_TIMING | BEFORE_EXTERNAL_SIDE_EFFECT | FROZEN |
| TOOLRESULT_WRITE_TIMING | AFTER_TOOL_BEFORE_NEXT_MODEL | FROZEN |
| TRANSACTION_BOUNDARY | SHORT_DURABLE_FACT_TRANSACTION | FROZEN |
| PARTIAL_STREAM_DURABILITY | NO | FROZEN |
| DANGLING_TOOL_SEMANTICS | UNKNOWN | FROZEN |
| AUTO_TOOL_REPLAY | NO | FROZEN |
| DANGLING_TOOL_REPAIR | SYNTHETIC_TOOL_RESULT (persisted) | FROZEN |
| RESUME_SEMANTICS | REOPEN_HISTORY_REPAIR_AND_CONTINUE | FROZEN |
| PROCESS_DEATH_RECOVERY | REQUIRED (V1) | FROZEN |
| APP_RESTART_RECOVERY | REQUIRED (V1) | FROZEN |
| TRANSCRIPT_MODEL_SSOT | NO | FROZEN |
| SESSIONHOLDER_DURABLE | NO | FROZEN |
| AT_TOPIC_SESSION_DEPENDENCY | NONE | FROZEN |
| PTY_RESUME | OUT_OF_SCOPE | FROZEN |
| DATABASE_VERSION | 1 | FROZEN |
| DESTRUCTIVE_MIGRATION_ALLOWED | NO | FROZEN |
| MODEL_PROVIDER_METADATA | LAST_USED_MODEL (snapshot at creation; UPDATE on switch; not identity) | FROZEN |
| TOOL_OUTPUT_BOUND | OUTPUT_MUST_BE_BOUNDED; EXACT_LIMIT_OWNER = TOOL_LAYER | FROZEN |
| REASONING_PERSISTENCE | NO (V1) | FROZEN |
| AGENT_EVENT_DURABILITY | NO (V1) | FROZEN |
| NEW_TOPIC | V1 OPTIONAL | FROZEN |
| SESSION_TITLE | V1 OPTIONAL (nullable column) | FROZEN |
| FORK / BRANCH / REWIND | POST-V1 | FROZEN |
| COMPACTION | POST-V1 | FROZEN |
| MULTI_SESSION_PICKER | POST-V1 | FROZEN |
| CLOUD_SYNC | POST-V1 | FROZEN |
| AGENT_EVENT_SINK_AS_DURABILITY_CONTRACT | NO (observability only) | FROZEN |
| NEW_CORE_PERSISTENCE_SEAM_REQUIRED | YES | FROZEN |
| RUN_ADMISSION_BOUNDARY | VERIFIED (two-layer guard) | FROZEN |
| SAFE_USER_DURABILITY_POINT | AFTER admission, BEFORE first ModelCall | FROZEN |
| USER_PERSIST_FAILURE_BEHAVIOR | ABORT RUN, DO NOT CALL MODEL | FROZEN |
| TOOLCALL_PERSIST_FAILURE_BEHAVIOR | DO NOT EXECUTE TOOL, ABORT RUN | FROZEN |
| TOOLRESULT_PERSIST_FAILURE_BEHAVIOR | DO NOT CALL NEXT MODEL, RUN STOPS | FROZEN |
| PERSISTENCE_FAILURE_CAN_CONTINUE_RUN | NO | FROZEN |
| PERSISTENCE_FAILURE_OUTCOME | RunFailure (API shape deferred; semantics frozen) | FROZEN |
| SEQUENCE_ALLOCATION | ATOMIC_WITH_MESSAGE_INSERT | FROZEN |
| LAST_USED_UPDATE_RULE | ON_SUCCESSFUL_DURABLE_MESSAGE_APPEND | FROZEN |
| DATABASE_JOURNAL_MODE | WRITE_AHEAD_LOGGING (EXPLICIT) | FROZEN |
| CORRUPT_DB_POLICY | PRESERVE + NO_DESTRUCTIVE + CONTROLLED_FRESH_RECOVERY | FROZEN |
| V1_CONTIGUOUS_SEQUENCE_REQUIRED | YES | FROZEN |
| CORE_MEMORY_VS_DB_ORDERING | DURABLE_FIRST (all); commit → append; D2 before side effect | FROZEN |
| MESSAGE_ORDERING_MODEL | DURABLE_FIRST | FROZEN |
| DURABILITY_SEAM_NAME | MessageDurability (suspend fun interface) | FROZEN |
| DURABILITY_SEAM_INJECTION | AgentCore constructor (default Noop) | FROZEN |
| DURABILITY_FAILURE_TYPE | RunFailure.DurabilityFailure (new) | FROZEN |
| COMMIT_OUTCOME_AMBIGUITY | AMBIGUOUS_NOT_POSSIBLE (backend shields commit) | FROZEN |
| AMBIGUOUS_COMMIT_OUTCOME_ALLOWED | NO | FROZEN |
| COMMIT_SHIELDING_OWNER | BACKEND ADAPTER (NonCancellable inside commit) | FROZEN |
| CORE_ORPHAN_MESSAGE_ALLOWED | NO | FROZEN |
| SYNTHETIC_RESULT_KIND | INTERRUPTED_UNKNOWN (not "failure") | FROZEN |
| MODEL_FACING_UNKNOWN_SEMANTICS | MAY_HAVE_EXECUTED, NOT_CONFIRMED_FAILED, NO_AUTO_REPLAY | FROZEN |
| DANGLING_REPAIR_ORDER | DURABLE_REPAIR_FIRST, CORE_HYDRATE_SECOND | FROZEN |
| TOPIC_SESSIONSTORE_API_SHAPE | IMPLEMENTATION-LEVEL (semantics frozen) | FROZEN |
| MODEL_PROVIDER_METADATA_SEMANTICS | LAST_USED_MODEL (snapshot at creation, update on switch) | FROZEN |
| TOOL_OUTPUT_BOUND_POLICY | OUTPUT_MUST_BE_BOUNDED (reuse canonical Tool output bound) | FROZEN |
| TOOL_OUTPUT_EXACT_LIMIT_OWNER | TOOL LAYER (not TopicSession) | FROZEN |
| IMPLEMENTATION_READY | NO (durability seam required) | FROZEN |

---

## 28. 调研问题关闭

| # | 问题 | 决定 | 状态 |
|---|---|---|---|
| 1 | Core restore seam API shape | `Session.restoreHistory(List<Message>)` + `AgentCore.restoreSession(id, history)` | CLOSED |
| 2 | Message persistence granularity | 每条 Message 一行（1:1 映射） | CLOSED |
| 3 | ToolCall/Result durable schema | 在 message payload_json 内（type=MODEL 含 toolCalls[]; type=TOOL_RESULT 含完整 result） | CLOSED |
| 4 | explicit sequence vs rowid | EXPLICIT_SEQUENCE (UNIQUE per session) | CLOSED |
| 5 | Corrupt data behavior | 三级：RECOVERABLE / SESSION_UNRECOVERABLE / DATABASE_UNRECOVERABLE（§18） | CLOSED |
| 6 | Large tool output V1 behavior | OUTPUT_MUST_BE_BOUNDED; limit owned by TOOL_LAYER | CLOSED |
| 7 | Session 数量上限 / 清理 | V1 无限制；POST-V1 retention policy | CLOSED |
| 8 | Reasoning durability | NO (V1) | CLOSED |
| 9 | AgentEvent durability | NO (V1) | CLOSED |
| 10 | Room migration baseline | v1; explicit migrations required; no destructive | CLOSED |

---

## 29. 实现检查清单（Implementation Ready）

实现 Step 需要做的事（本冻结不实现）：

```text
PRE-STEP: DEDICATED_TOPIC_SESSION_DURABILITY_SEAM_PREFLIGHT
  - 设计 Core durability hook 的具体 API（suspend fun / 回调 / 参数）
  - 确定 persistence failure 的 RunFailure 类型（复用 RuntimeFailure or 新增）
  - 验证 seam 不破坏现有 AgentEvent 语义 / TraceCollector / DebugBridge / tests

1. ag-core: Session.restoreHistory() + AgentCore.restoreSession()
2. ag-core: Durability seam（最小 suspend hook in driveLoop）
3. ag-runtime: TopicSessionStore interface + MessageSerializer
4. ag-runtime: AgentRuntime 修改（startup restore + durability hook injection）
5. :app: Room Entity (TopicSessionEntity, MessageEntity)
6. :app: Room DAO (TopicSessionDao, MessageDao)
7. :app: Room Database (TopicSessionDatabase v1, explicit WAL)
8. :app: RoomTopicSessionStore (implements TopicSessionStore, atomic sequence)
9. :app: Startup wiring (ChatActivity → Runtime.initialize(store))
10. :app: Dangling tool repair logic (DURABLE_REPAIR_FIRST order)
11. :app: TranscriptModel re-projection from restored messages
12. Tests: ag-core restore seam (JVM)
13. Tests: ag-core durability seam (JVM; fake store; failure paths)
14. Tests: ag-runtime store contract (JVM, fake store)
15. Tests: :app Room integration (JVM, in-memory SQLite)
16. Tests: :app startup state machine (JVM)
17. Tests: persistence failure → run abort (D1/D2/D3 each)
18. Device: process kill + restart recovery
19. Device: dangling tool scenario (kill during tool execution)
20. Device: C5 scenario (tool completes, persist fails → run stops)
```

---

## 30. 不做什么（Non-goals 确认）

```text
NO: Room implementation (本轮)
NO: DAO / Entity / Database code
NO: Production code changes
NO: Test code changes
NO: Gradle changes
NO: AT changes
NO: Multi-session picker
NO: Fork / Branch / Compaction
NO: Memory / RAG
NO: PTY persistence
NO: Run history
NO: Cloud sync
NO: AgentEvent durability
NO: Reasoning persistence
NO: Background job restoration
```

