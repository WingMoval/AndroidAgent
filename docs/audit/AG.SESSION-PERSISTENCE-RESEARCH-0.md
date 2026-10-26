# AG.SESSION-PERSISTENCE-RESEARCH-0 — 会话持久化与中断恢复架构调研

## 1. 当前 Git 基线及工作区状态

| 项 | 值 |
|---|---|
| AG HEAD | `a6eea61dc209898a01cd0e7f2e82304fff7ed9d4` |
| 分支 | `main` |
| 工作区 | DIRTY（28 files：11 modified + 4 deleted upload/ + 13 untracked） |
| 未提交修改来源 | V4 migration + 4 bug fixes（TranscriptModel 线程安全、userInputSink、CLI bootstrap 路径、executeLocal source）+ 本轮文档 |
| AT freeze tag | `AT.V4-CONSUMER-FREEZE-V1` → `7216d0e` |
| AT worktree | 14 untracked files（本轮交付 docs，只读） |

## 2. 现有 Session 实现的源码证据索引

### 2.1 数据生命周期矩阵

| 数据对象 | 所有者 | 存储位置 | 生命周期 | 恢复行为 | 源码证据 |
|---|---|---|---|---|---|
| Agent Session ID | `AgentRuntime` | 进程内存（`@Volatile var sessionId: String`） | 进程存活期间 | **不恢复**。进程死亡后 ID 丢失，新进程生成新 UUID | `AgentRuntime.kt:90` |
| 消息历史 | `Session`（AG Core） | 进程内存（`mutableListOf<Message>()`） | 进程存活期间 | **不恢复**。`Session` 对象随 `AgentCore` 实例消亡 | `Session.kt:38` |
| 用户消息 | `Session` | 内存 | Run 期间追加，进程死亡后丢失 | 无 | `AgentCore.kt:280` (`session.append(UserMessage)`) |
| LLM 回复 | `Session` | 内存（`ModelMessage`） | 同上 | 无 | `AgentCore.kt:331,343` |
| ToolCall | `ModelMessage.toolCalls` | 内存 | 同上 | 无 | `Message.kt:22-25` |
| ToolResult | `ToolResultMessage` | 内存 | 同上 | 无 | `Message.kt:28-32` |
| Run 状态 | `AgentCore` / `AgentRuntime` | 进程内存（`AtomicBoolean`、`AtomicReference`） | 单个 Run 期间 | 无持久化状态记录 | `AgentRuntime.kt:84-87` |
| Run 结果 | `RunOutcome`（返回值） | 调用者内存 | 瞬时 | 无 | `RunOutcome.kt:6-29` |
| Provider 配置 | `ChatCompositionRoot` | 进程内存（构造参数） | 进程存活期间 | **部分恢复**：`DeviceLocalProviderConfigLoader` 从 `filesDir/provider.local.json` 重新读取 | `DeviceLocalProviderConfig.kt:69-70` |
| Provider 节点选择 | `SessionHolder` | 进程内存 | 进程存活期间 | **不恢复**。`restoreState` 不保存/恢复 providerNode | `SessionHolder.kt:33-34` |
| 上下文构造 | `AgentCore.driveLoop` | 每次 Run 从 `session.history()` 重建 | 无持久化 | 无 | `AgentCore.kt:296` |
| Activity 状态 | `ChatActivity` | `savedInstanceState` Bundle | Activity 重建 | 保存 transcript 渲染文本 + 状态字符串 | `SessionHolder.kt:104-112` |
| SessionHolder 数据 | `SessionHolder`（object 单例） | 进程内存 | 进程存活期间 | **不恢复**。`restoreState` 检查 `root == null` 则返回 false | `SessionHolder.kt:121-123` |
| TranscriptModel | `SessionHolder` | 进程内存（`mutableList<Block>`） | 进程存活期间 | 不恢复（仅 Activity 重建时从 rendered text 重建） | `TranscriptModel.kt:71` |
| AT PTY Session | `DebugBridge.sessions` | 进程内存（`ConcurrentHashMap`） | 进程存活期间 | **不恢复**。进程死亡后 PTY 进程终止，socket 断开 | `DebugBridge.kt:77` |
| ADB Identity | AT V4（AT 侧管理） | 磁盘（`files/at-consumer/identity/`） | 持久 | **恢复**。key 文件在磁盘上，重启后可用 | 上轮验证 |
| ADB 连接 | AT V4（AT 侧管理） | 进程内存 | 进程存活期间 | **不恢复**。重启后需重新连接（NSD discovery + connect） | 上轮验证 |
| CLI Bridge Socket | `AtExecutionAdapter` | 磁盘（UDS socket file） | 进程存活期间 | **不恢复**。socket file 残留但连接断开，需重建 | 上轮验证 |
| DebugBridge | `ChatActivity.sharedDebugBridge` | 进程内存 | 进程存活期间 | **不恢复**。重启后需重新 `start()` | `DebugBridge.kt:118-136` |

### 2.2 关键源码引用

**Session 所有权（AG Core）：**
```kotlin
// Session.kt:35-39
class Session internal constructor(val id: String) {
    private val monitor = Any()
    private val messages = mutableListOf<Message>()  // 纯内存
    private var activeRun = false
}
```

**AgentCore 持有 Session 映射（纯内存）：**
```kotlin
// AgentCore.kt:108
private val sessions = ConcurrentHashMap<String, Session>()
```

**AgentRuntime 生成 Session ID（UUID，纯内存）：**
```kotlin
// AgentRuntime.kt:60
private val sessionIdFactory: () -> String = { "session-" + UUID.randomUUID() }

// AgentRuntime.kt:89-90
@Volatile
private var sessionId: String = sessionIdFactory()
```

**SessionHolder 明确声明不持久化：**
```kotlin
// SessionHolder.kt:20-21
// This is NOT Session Persistence (STATE-003): the state lives in process memory
// only. If the process dies, the state is gone. That is by design.
```

**restoreState 在进程死亡后返回 false：**
```kotlin
// SessionHolder.kt:121-123
fun restoreState(savedState: Bundle?): Boolean {
    if (savedState == null) return false
    if (root == null) return false // process died; no Session to rebind
```

**Provider 配置从磁盘读取（唯一的持久化数据源）：**
```kotlin
// DeviceLocalProviderConfig.kt:69-70
fun load(context: Context): DeviceLocalProviderConfig =
    load(File(context.filesDir, FILE_NAME))
```

## 3. 三种恢复机制的明确区分

### 3.1 Activity 状态恢复（已实现）

**机制：** `onSaveInstanceState` → `savedInstanceState` Bundle → 新 Activity 实例 `onCreate` 中 `restoreState`

**恢复内容：**
- Transcript 渲染文本（`transcriptModel.render()` → 字符串）
- Provider 状态文本
- AT 状态文本
- Run 状态文本
- Endpoint 标签
- Busy 标志

**不恢复的内容：**
- Session ID（从 holder 读取，holder 存活时有效）
- 消息历史（holder 存活时有效）
- Run 执行状态（holder 存活时有效）

**触发条件：** 旋转、配置变更、窗口尺寸变化（同一进程内）

**限制：** 仅在同一进程内有效。进程死亡后 Bundle 中的字符串仍在，但 `SessionHolder.root == null`，`restoreState` 返回 false。

### 3.2 进程级 Session Continuity（已实现）

**机制：** `SessionHolder`（Kotlin `object` 单例）持有 `ChatCompositionRoot`、`TranscriptModel`、状态字符串

**存活范围：** 同一 Android 进程的生命周期

**覆盖的场景：**
- Activity 重建（旋转、配置变更）→ 新 Activity 实例 rebind 到同一 holder
- 多个 Activity 实例共享同一 Session

**不覆盖的场景：**
- 进程被系统杀死（OOM、用户清除、系统重启）
- App 退出后重新打开

**源码证据：**
```kotlin
// SessionHolder.kt:121-123
fun restoreState(savedState: Bundle?): Boolean {
    if (savedState == null) return false
    if (root == null) return false // process died; no Session to rebind
```

### 3.3 持久化 Session 恢复（未实现）

**现状：** 不存在。

**证据：**
- 无 Room / SQLite / DataStore / SharedPreferences 依赖（`app/build.gradle.kts` 中无相关条目）
- 无 Session 数据写入磁盘的代码路径
- `Session` 类的 `messages` 列表无任何序列化/持久化逻辑
- `SessionHolder` 注释明确声明 "state lives in process memory only"
- 唯一的磁盘持久化数据是 Provider 配置文件（`provider.local.json` / `provider.openrouter.json`）和 AT identity 文件（由 AT 侧管理）

## 4. Session / Run / PTY 生命周期关系

### 4.1 对象关系图

```
AgentRuntime (进程内存)
  ├── sessionId: String (UUID, 进程内存)
  ├── activeRun: AtomicReference<ActiveRunControl?> (进程内存)
  └── agentCore: AgentCore (进程内存)
        ├── sessions: ConcurrentHashMap<String, Session> (进程内存)
        │     └── Session
        │           ├── id: String
        │           ├── messages: MutableList<Message> (进程内存)
        │           │     ├── UserMessage(content)
        │           │     ├── ModelMessage(content, toolCalls)
        │           │     └── ToolResultMessage(toolCallId, toolName, result)
        │           └── activeRun: Boolean
        ├── modelProvider: ModelProvider (接口)
        └── toolRegistry: ToolRegistry (接口)

ChatCompositionRoot (进程内存)
  ├── atComposition: ATAndroidComposition (进程内存 + 磁盘 identity)
  ├── atAdapter: AtExecutionAdapter (进程内存)
  │     ├── localEngine: ATEngine (进程内存)
  │     ├── adbEngine: ATEngine (进程内存 + 磁盘 identity)
  │     └── cliBridge: UDS socket (磁盘 file + 进程内存连接)
  ├── shell: ShellConnection (进程内存)
  └── toolRegistry: ToolRegistry
        ├── AndroidShellToolImpl → atAdapter.executeLocal()
        ├── NetworkToolImpl
        └── FileToolImpl

DebugBridge (进程内存)
  ├── sessions: ConcurrentHashMap<Int, ATTerminalSession> (进程内存)
  └── serverSocket: ServerSocket (进程内存)

SessionHolder (进程内存, object 单例)
  ├── root: ChatCompositionRoot? (进程内存)
  ├── transcriptModel: TranscriptModel (进程内存)
  ├── providerNode: ProviderNode (进程内存)
  └── 状态字符串 (进程内存)

磁盘持久化（唯一）:
  ├── files/provider.local.json (Provider 配置)
  ├── files/provider.openrouter.json (Provider 配置)
  ├── files/at-consumer/identity/ (AT ADB identity, AT 侧管理)
  └── files/at-cli-bridge/cli_bridge.sock (UDS socket file)
```

### 4.2 生命周期对照

| 对象 | 创建 | 销毁 | 进程死亡后 |
|---|---|---|---|
| AgentRuntime | `ChatCompositionRoot` 构造时 | 进程死亡 | **丢失** |
| Session | `AgentCore.session(id)` 首次访问 | 进程死亡 | **丢失**（含全部消息历史） |
| Message 列表 | Run 期间逐步追加 | 进程死亡 | **丢失** |
| ActiveRunControl | `AgentRuntime.send()` | Run 结束或进程死亡 | **丢失** |
| ATTerminalSession | `adapter.openLocalSession()` / `openAdbSession()` | `close()` 或进程死亡 | **丢失**（PTY 进程终止） |
| ADB Identity | AT V4 自动生成或配对 | 用户清除 | **保留**（磁盘文件） |
| ADB 连接 | `adbControl.ensureReady()` | 进程死亡或连接断开 | **丢失**（需重新连接） |
| Provider 配置 | `DeviceLocalProviderConfigLoader.load()` | 不销毁 | **保留**（磁盘文件） |
| TranscriptModel | `SessionHolder.createRoot()` / `newSession()` | 进程死亡 | **丢失** |

## 5. 当前持久化能力的明确结论

| 能力 | 状态 | 证据 |
|---|---|---|
| Agent Session 数据持久化 | **不存在** | 无磁盘写入代码；`Session.messages` 为纯内存 `mutableListOf` |
| Run 状态持久化 | **不存在** | `RunOutcome` 为瞬时返回值；无状态记录存储 |
| 消息历史持久化 | **不存在** | `UserMessage`/`ModelMessage`/`ToolResultMessage` 无序列化路径 |
| ToolCall/ToolResult 持久化 | **不存在** | 同上 |
| Activity 重建恢复 | **已实现** | `savedInstanceState` + `SessionHolder.restoreState()` |
| 进程死亡恢复 | **不存在** | `SessionHolder.root == null` → `restoreState` 返回 false |
| App 重启恢复 | **不存在** | 同进程死亡 |
| PTY 跨进程恢复 | **不存在（不在本轮范围）** | PTY 进程随宿主进程死亡终止 |
| ADB Identity 持久化 | **存在** | 磁盘文件，AT 侧管理 |
| Provider 配置持久化 | **存在** | 磁盘文件，`DeviceLocalProviderConfigLoader` 读取 |

## 6. 中断场景及重复执行风险矩阵

### 场景分析

| # | 场景 | 当前行为 | 丢失信息 | 重复执行风险 | 推荐恢复策略 |
|---|---|---|---|---|---|
| 1 | 用户发送消息后、LLM 返回前 App 被杀 | `UserMessage` 已追加到 `Session.messages`（内存），进程死亡后丢失 | 用户消息 + 后续全部 | **无**（LLM 未被调用） | 恢复后用户重新发送 |
| 2 | LLM 已返回，助手消息尚未保存时 App 被杀 | `ModelMessage` 已追加到 `Session.messages`（`AgentCore.kt:331/343`），进程死亡后丢失 | LLM 回复（含 toolCalls） | **无**（模型调用已完成，费用已产生） | 恢复后需重新调用 LLM（无法避免，因为回复未持久化） |
| 3 | LLM 已生成 ToolCall，工具尚未开始执行 | `ModelMessage(toolCalls)` 已追加；`ToolCallStarted` 事件已发出；工具未执行 | ToolCall 声明 + 后续 | **低**：工具未执行，无副作用。恢复后重新执行安全 | 恢复后从 ToolCall 重新执行 |
| 4 | 工具正在执行时 App 被杀 | `ToolCallStarted` 已发出；工具在 `Dispatchers.IO` 上执行 | 工具执行进度 + 结果 | **高**：工具副作用可能已部分发生（如 `echo > file` 已写入但进程被杀） | **不得自动重放**。标记为 `UNKNOWN`，需用户确认或幂等检查 |
| 5 | 工具已执行成功，但 ToolResult 尚未保存 | `ToolResultMessage` 未追加（`AgentCore.kt:372` 在 `dispatch` 返回后才追加） | ToolResult | **中**：工具副作用已完成，但结果丢失。重新执行会重复副作用 | 检查工具是否幂等；不幂等则标记 `UNKNOWN` |
| 6 | ToolResult 已保存，但下一轮 LLM 请求尚未发起 | `ToolResultMessage` 已追加到 `Session.messages`；下一轮 `modelProvider.complete()` 未调用 | 后续 LLM 回复 | **低**：重新调用 LLM 安全（幂等） | 恢复后从下一轮 LLM 请求继续 |
| 7 | Run 已完成，但 UI 尚未刷新 | `RunOutcome` 已返回；`RunFinished` 事件已发出；UI 未渲染 | 仅 UI 状态 | **无** | 恢复后从 `Session.messages` 重建 UI |
| 8 | 用户主动取消 Run | `RunOutcome.Interrupted`；合成 ToolResult 已追加（`IN_FLIGHT_INTERRUPTED_ERROR` / `NOT_EXECUTED_DUE_TO_INTERRUPT_ERROR`） | 无（已完整记录） | **无**（中断已正确标记） | 恢复后 Session 可继续使用 |
| 9 | App 恢复时 PTY 已消失 | `DebugBridge.sessions` 为空；PTY 进程已终止 | PTY 执行环境 | **无**（PTY 是执行环境，不是数据） | 标记执行环境失效；创建新 PTY（如需要） |
| 10 | App 恢复时无法确定上次命令是否执行成功 | 无持久化记录可查 | 命令执行状态 | **高**：盲目重放可能重复副作用 | 标记 `UNKNOWN`；提供用户确认机制；幂等命令可安全重试 |

### 状态语义建议

现有 `RunOutcome` 有三种状态：`Answer`、`Interrupted`、`Failure`。

对于持久化恢复场景，建议扩展为：

| 状态 | 含义 | 来源 |
|---|---|---|
| `COMPLETED` | Run 正常完成，最终答案已产生 | `RunOutcome.Answer` |
| `FAILED` | Run 因错误终止 | `RunOutcome.Failure` |
| `INTERRUPTED` | 用户主动中断 | `RunOutcome.Interrupted` |
| `UNKNOWN` | 进程死亡，Run 状态无法确定 | 恢复时推断（无 `RunFinished` 记录） |
| `RETRYABLE` | 经过判断可安全重试 | 恢复逻辑判定（所有已记录的 ToolResult 均完整，或工具幂等） |

**关键原则：** 持久化只能保存已经观察到的事实。`AgentCore` 在工具执行前发出 `ToolCallStarted`，执行后追加 `ToolResultMessage`。如果进程在两者之间死亡，恢复时只能看到 `ToolCallStarted`（如果事件被持久化）而没有 `ToolResultMessage`——这表示 `UNKNOWN`，不是 `FAILED`。

## 7. 持久化方案比较及推荐

### 7.1 现有存储组件

当前项目**无任何持久化依赖**：
- 无 Room / SQLite
- 无 DataStore
- 无 SharedPreferences（代码中无引用）
- 唯一的磁盘 I/O 是 `DeviceLocalProviderConfigLoader`（读 `provider.local.json`）和 AT identity 文件

### 7.2 候选方案比较

| 维度 | Room (SQLite) | JSON 文件 + 手动序列化 | DataStore (Proto) |
|---|---|---|---|
| **事务性** | ✓ 完整 ACID 事务。Message、ToolCall、ToolResult、Run 状态可原子写入 | ✗ 无事务。部分写入导致不一致 | ✓ 单值原子写入，但不适合关系型数据 |
| **崩溃恢复** | ✓ WAL 模式保证崩溃安全。可识别未提交事务 | ✗ 需手动实现（如写临时文件 + rename） | ✓ 单值安全 |
| **数据迁移** | ✓ Migration 框架 + version | △ 手动处理 schema 变更 | △ Proto 兼容性好但迁移复杂 |
| **并发与顺序** | ✓ 行锁 + 事务 | ✗ 需手动加锁 | △ 单值原子 |
| **测试性** | ✓ `Room.testing` + in-memory DB。JVM 测试无需 Android | ✓ 纯文件 I/O，JVM 可测 | △ 需 `TestCoroutines` |
| **性能** | ✓ 索引查询。长对话 O(1) 追加 | △ 全文件读写。长对话 O(n) | △ 全值读写 |
| **兼容性** | ✓ 不影响 AG Core（Host 层实现）。需新增依赖 | ✓ 零新依赖。但序列化逻辑复杂 | ✓ 不影响 AG Core。需新增依赖 |
| **依赖体积** | ~1.5 MB (room-runtime + room-ktx + room-compiler) | 0 | ~200 KB |
| **AG Core 影响** | 无（持久化在 Host/`:app` 层） | 无 | 无 |

### 7.3 推荐方案

**推荐：Room (SQLite)**

理由：
1. **事务性是核心需求**：Message、ToolCall、ToolResult、Run 状态必须原子保存。场景 4（工具执行中进程死亡）要求能区分"ToolCall 已记录但 ToolResult 未记录"——这需要一个事务边界。
2. **崩溃恢复是核心需求**：App 被杀是 Android 常态。WAL 模式保证已提交数据不丢失。
3. **数据量增长**：长对话 + 大量 ToolResult 需要索引查询（按 Session ID、时间戳），JSON 文件无法满足。
4. **测试性**：in-memory DB 可在 JVM 单元测试中验证恢复逻辑，无需 Android 设备。
5. **AG Core 不受影响**：持久化在 `:app` 层（Host）实现，AG Core 保持 Android-free。

**备选：JSON 文件**（如果 Controller 拒绝新增依赖）

- 优点：零新依赖
- 缺点：无事务、长对话性能差、崩溃恢复需手动实现
- 缓解：每轮 Run 结束后原子写入（临时文件 + rename）；接受场景 4 的 `UNKNOWN` 状态无法精确判定

## 8. 推荐的数据模型与状态转换

### 8.1 数据模型

```kotlin
// :app 层（Host），不影响 AG Core

@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey val id: String,           // UUID
    val createdAt: Long,                  // epoch millis
    val updatedAt: Long,
    val title: String?,                   // 可选摘要
    val providerNodeId: String,           // 关联 Provider 节点
    val modelId: String,                  // 关联模型
)

@Entity(tableName = "messages",
    foreignKeys = [ForeignKey(SessionEntity::id, onDelete = CASCADE)])
data class MessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long,
    val sessionId: String,
    val sequence: Int,                    // 在 Session 内的顺序
    val type: String,                     // "user" | "model" | "tool_result"
    val content: String,                  // 文本内容
    val toolCallsJson: String?,           // JSON: [{id, name, arguments}]
    val toolCallId: String?,              // 关联的 ToolCall ID（tool_result 类型）
    val toolName: String?,
    val toolSuccess: Boolean?,
    val toolOutput: String?,
    val toolError: String?,
    val createdAt: Long,
)

@Entity(tableName = "runs",
    foreignKeys = [ForeignKey(SessionEntity::id, onDelete = CASCADE)])
data class RunEntity(
    @PrimaryKey val id: String,           // UUID
    val sessionId: String,
    val status: String,                   // "running" | "completed" | "failed" | "interrupted" | "unknown"
    val startedAt: Long,
    val finishedAt: Long?,
    val outcome: String?,                 // "answer" | "failure" | "interrupted"
    val failureCause: String?,
    val answerContent: String?,
    val lastCompletedStep: Int,           // 最后完成的模型轮次
    val lastToolCallId: String?,          // 最后一个 ToolCall（用于恢复判断）
    val lastToolResultId: String?,        // 最后一个 ToolResult
)
```

### 8.2 状态转换

```
                    ┌─────────────────────────────────────────────────┐
                    │              Run 状态转换                        │
                    └─────────────────────────────────────────────────┘

  (无 Run)
      │
      │ 用户发送消息
      ▼
  RUNNING ──────────────────────────────────────────────────────┐
      │                                                         │
      │  LLM 返回 FinalAnswer                                   │ 用户 Stop
      ▼                                                         ▼
  COMPLETED                                                   INTERRUPTED
      │                                                         │
      │  进程死亡（RUNNING 状态未更新为 COMPLETED）                  │
      ▼                                                         ▼
  UNKNOWN（恢复时推断）                                          （已持久化）
      │
      │  恢复逻辑判断
      ├── 所有 ToolResult 完整 + 无副作用工具 → RETRYABLE（从下一轮 LLM 继续）
      ├── 存在未完成的 ToolCall（无 ToolResult）→ UNKNOWN（需用户确认）
      └── 最后一个 ToolResult 完整 → RETRYABLE（从下一轮 LLM 继续）
```

### 8.3 关键恢复逻辑

```
App 启动
  → 从 DB 加载 sessions
  → 对每个 session：
      → 加载 messages（按 sequence 排序）
      → 检查是否有 status=RUNNING 的 run
      → 如果有：
          → 标记为 UNKNOWN（进程死亡时 Run 未完成）
          → 检查 lastToolCallId 是否有对应 ToolResult
              → 有 → RETRYABLE（从下一轮 LLM 继续）
              → 无 → UNKNOWN（工具执行状态不确定）
      → 重建 AgentCore.Session（从 MessageEntity → Message）
      → 重建 TranscriptModel（从 Message → Block）
      → 恢复 Provider 配置（从 provider.local.json）
      → 检查 ADB identity（AT 侧，磁盘文件）
      → 标记 PTY 执行环境为 INACTIVE（不尝试恢复旧 PTY）
```

## 9. 分阶段实施计划

### Phase A：Agent Session 持久化

**前置条件：**
- Controller 批准新增 Room 依赖（或选择 JSON 文件方案）
- AG Core / Runtime 不变

**具体交付：**
1. 定义 `SessionEntity`、`MessageEntity`、`RunEntity` 数据模型
2. 实现 `SessionRepository`（`:app` 层）：
   - `saveSession(sessionId, providerNode, modelId)`
   - `appendMessage(sessionId, message: Message)`
   - `startRun(sessionId, runId)`
   - `completeRun(runId, outcome: RunOutcome)`
   - `loadSession(sessionId): SessionData`
   - `listSessions(): List<SessionSummary>`
3. 在 `ChatCompositionRoot` 中注入 `SessionRepository`
4. 在 `SessionHolder.onRunEvent` 中调用 `SessionRepository` 持久化每个事件
5. 在 `ChatActivity.onCreate` 中从 `SessionRepository` 恢复 Session

**验收标准：**
- 发送消息 → 杀进程 → 重启 → 消息历史完整显示
- 多轮对话 + ToolCall/ToolResult 完整恢复
- 单元测试：JVM in-memory DB 验证 CRUD + 恢复逻辑

**风险：**
- Room 依赖体积（~1.5 MB）
- 首次启动需创建 DB（< 100ms）
- 需要处理 DB 版本迁移（后续数据结构变更）

### Phase B：会话恢复与 Run 中断识别

**前置条件：** Phase A 完成

**具体交付：**
1. 实现 Run 状态恢复逻辑：
   - 启动时扫描 `status=RUNNING` 的 Run
   - 根据 `lastToolCallId` / `lastToolResultId` 判定 `RETRYABLE` vs `UNKNOWN`
   - `RETRYABLE`：重建 Session 历史，从下一轮 LLM 继续
   - `UNKNOWN`：显示"上次任务中断，是否继续？"（UI 确认）
2. 实现 ToolCall 幂等性标记（可选）：
   - `android_shell`：命令是否幂等（`cat` 幂等，`echo > file` 非幂等）
   - 幂等工具 → 可安全重试
   - 非幂等工具 → `UNKNOWN`
3. 实现 Provider 配置恢复（已存在，`DeviceLocalProviderConfigLoader`）

**验收标准：**
- 工具执行中杀进程 → 重启 → 正确识别 `UNKNOWN` 状态
- 不重复执行非幂等工具
- 幂等工具可安全重试

**风险：**
- 幂等性判断不完整（无法覆盖所有命令）
- 用户确认 UX 需要设计

### Phase C：执行环境重新关联

**前置条件：** Phase B 完成

**具体交付：**
1. 恢复 Agent Session 后，检查 ADB 状态：
   - `adapter.bootstrapState()` → `READY` / `CONNECT_FAILED` / `PAIRING_REQUIRED`
   - 标记执行环境为 `ACTIVE` / `INACTIVE`
2. 不尝试恢复旧 PTY（已消失）
3. 如果用户需要执行 Shell 命令：
   - 创建新的 PTY Session（`adapter.openLocalSession()`）
   - 明确告知用户"这是新的 Shell 环境，之前的状态不保留"
4. ADB identity 检查（磁盘文件存在性）

**验收标准：**
- 重启后 ADB 状态正确反映（READY 或需要重连）
- 不伪造旧 PTY 状态
- 新 PTY 创建成功

**风险：**
- ADB 重连可能需要用户重新配对（identity 文件在但设备端授权可能已过期）
- 不要求 AT 实现跨进程 PTY Resume

### Phase D：端到端验证

**前置条件：** Phase A + B + C 完成

**具体交付：**
1. 自动化测试：
   - 普通对话保存与恢复（JVM + 设备）
   - 多轮消息 + ToolCall/ToolResult 完整恢复
   - Run 执行中断后恢复判断（模拟进程死亡）
   - App 进程死亡与重新启动（`am force-stop` → 重启）
   - PTY 失效后重新关联
   - 防止重复执行的关键测试（非幂等工具 + 杀进程 + 重启 → 不重放）
2. 真机验证：
   - 完整用户流程：对话 → 杀进程 → 重启 → 恢复 → 继续

**验收标准：**
- 所有场景在真机上验证通过
- 无数据丢失（已提交的数据）
- 无重复执行（非幂等工具）
- 性能可接受（恢复时间 < 2s）

**风险：**
- 真机测试覆盖度（不同 Android 版本的进程管理行为）
- 大对话的恢复性能

## 10. 尚未能从源码确认的问题

| # | 问题 | 状态 |
|---|---|---|
| 1 | `AgentTraceCollector` 是否已有持久化能力 | **未确认**。`AgentTraceCollector.kt:15` 注释说 "no persistence"，但未完整阅读该类 |
| 2 | `kotlinx-serialization-json` 依赖是否可用于 Message 序列化 | **已确认存在**（`app/build.gradle.kts`），但未评估是否适合 Session 持久化 |
| 3 | AT V4 的 `ATAndroid` 是否在进程重启后自动重建 ADB 连接 | **未确认**。需检查 AT 源码（只读） |
| 4 | `ShellConnection` 在进程重启后的行为 | **未确认**。需检查是否在 `ChatCompositionRoot.init` 中自动重连 |
| 5 | `provider.openrouter.json` 和 `provider.local.json` 的优先级 | **部分确认**。`ProviderNode` 枚举定义了文件映射，但切换逻辑在 `ChatActivity` 中 |
| 6 | 是否存在 `onProcessDeath` 或 `onTrimMemory` 回调 | **未确认**。代码中未发现 |

## 11. 明确的下一步建议

1. **Controller 决定持久化方案**：Room（推荐）vs JSON 文件（零依赖）
2. **启动 Phase A**：Agent Session 持久化（数据模型 + Repository + 恢复逻辑）
3. **Phase A 完成后**：Phase B（Run 中断识别）+ Phase C（执行环境重新关联）可并行
4. **Phase D**：端到端验证（依赖 A+B+C 全部完成）

**不建议：**
- 在 Phase A 完成前实现 Phase B/C（没有持久化数据就无法恢复）
- 要求 AT 实现跨进程 PTY Resume（超出 AG 范围）
- 在 AG Core 中添加持久化逻辑（违反 Android-free 约束）
