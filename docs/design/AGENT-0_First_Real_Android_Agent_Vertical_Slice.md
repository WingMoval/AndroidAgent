# AGENT-0 — First Real Android Agent Vertical Slice (Design SSOT)

> 状态：**DESIGN ONLY**，未实现任何 Tool，未修改任何冻结代码。
> 基线：`2c8306c`（tag `ag-aga-0`）
> 本文档只回答：**第一个真实 Agent 验证应该做什么任务、需要什么最小 Terminal 能力、
> 以及现有 AG Core 是否够用。**

---

## 1. 本步骤要证明的唯一命题

```text
User Goal
   ↓
Model 自主选择 Action A
   ↓
真实 Android Environment
   ↓
Observation A
   ↓
Observation A 改变下一步决策
   ↓
Model 自主选择 Action B
   ↓
真实 Android Environment
   ↓
Observation B
   ↓
Model 决定继续或结束
```

**验收命题**：*如果 Observation A 不同，后续 Action B 的参数应随之不同。*

不是「ShellTool 能执行一条命令」，也不是「模型成功调用了一次工具」。

---

## 2. 现有 Agent 能力基线（来自 `AG.AGENTNESS-AUDIT-0`）

| 能力 | 状态 | 代码位置 |
|---|---|---|
| Agent Loop（跨 turn 重入） | 已实现 + 已测 | `AgentCore.kt:123` `while(true)`；`ToolCalls` 分支 `:158-175` 无 return |
| ToolResult 回灌 history | 已实现 + 已测 | `:172` append → `:136` `history=session.history()` |
| 跨 turn 多次 Tool | 已实现 + 已测 | `T7`：`callCount==2`、`executeCount==2` |
| 同 turn 多 Tool 串行 | 已实现 + 已测 | `:166` `for`；`T3` |
| Tool 失败不终止 Run | 已实现 + 已测 | `:168-172` 追加后继续循环；`T4` |
| **Tool A 结果因果决定 Tool B** | **架构支持，未直接测** | 唯一 Level B 缺口 |

**AG Core blocker：无。** 本设计不要求任何 Core 改动。

---

## 3. Primary Task 选择

> **识别当前 Android 设备的前台应用，并基于实际发现的包名查询该应用信息，最后给出结论。**

用户输入（目标式，非命令式）：

```text
找出当前手机正在前台运行的应用，并告诉我它的包名、版本信息和 APK 路径。
```

**用户不得**提供 package name、具体 shell 命令或执行顺序。

### 3.1 为什么这个任务成立

| 特征 | 论证 |
|---|---|
| **真实环境未知量** | 用户没告诉模型 foreground package。该值只能来自真实设备。 |
| **第二步强制依赖第一步** | `Observation A → package = P`；`Action B` 必须查询 `P`。`P` 无法预知。 |
| **非破坏性** | 只需 read/inspect。不需安装、卸载、删文件、改设置、杀进程、root。 |
| **可重复** | 任意真机都有 foreground app + package manager state + APK path。不依赖人为制造故障。 |
| **结果可验证** | package / versionName / apk path 可用 `pm` 独立复核。 |

### 3.2 候选对比

| Candidate | 真实未知量 | Observation→下一步清晰度 | 安全 | 可重复 | 权限风险 | 结论 |
|---|---|---|---|---|---|---|
| **A. Foreground App** | foreground package | **高** —— 下一步必须带该 package | 只读 | 高 | 低（`dumpsys`/`pm` 为 shell 权限内） | **SELECTED** |
| B. Storage Exploration | 最大目录名 | 中 —— 需 `du` 遍历，耗时长且结果依赖遍历顺序 | 只读 | 中 | 中（可能触碰受限目录，噪声大） | 备选 |
| C. Process Exploration | 目标/当前进程 | 中 —— 需先定义「目标进程」 | 只读 | 中 | 中（`ps` 在新 Android 受限） | 备选 |

**选 A。** 理由：分支因果最清晰（package 是硬字符串，必须出现在下一步参数里）、
最安全、噪声最小、结果最容易独立复核。

---

## 4. 预期 Agent 语义

```text
Action A      : 查询当前前台应用（方式由模型自选）
Observation A : 真实 stdout，含 foreground package = P
Decision B    : 模型据 P 决定下一步查询目标
Action B      : 针对 P 查询 version / path（P 出现在参数里）
Observation B : 真实 stdout，含 P 的 versionName / apk path
termination   : 模型判断信息已足够 → FinalAnswer
```

**不冻结具体 shell 命令。** 可能方式（`dumpsys activity` / `dumpsys window` /
`pm path` / `dumpsys package`）由模型自行选择。成功标准基于**结果与因果关系**，
不基于命令序列一致。

---

## 5. 因果依赖

| 项 | 内容 |
|---|---|
| **未知事实** | 当前 foreground package（记为 `P`） |
| **何处学到** | 第一次 shell 的真实 stdout |
| **下一步如何依赖** | 第二次 shell 的参数必须包含 `P`。若 `P` 换成 `P'`，参数必须随之变成含 `P'` |

`ToolCall.arguments` 是 opaque String，provider 原样透传，Tool 自行解析 ——
**这条链路已冻结且无需改动**。

---

## 6. Two-Environment Contrast（关键验收设计）

单次运行不足以证明因果性。因此最终验收**必须**跑两次，**Goal 文本完全相同**：

```text
Environment A : foreground = 某系统 App / Settings
Environment B : foreground = 另一个 App
```

要求：

```text
Observation A1 发现 P1
Observation A2 发现 P2
随后的 package-specific action 分别针对 P1 / P2
```

**这就是对 Level B 缺口最直接的真实证明。**

### 6.1 防「模型猜中」

模型可能已知 `com.androidagent.app`。因此：

- 不能让 AndroidAgent 永远处于前台
- 必须人为准备两种不同前台状态
- **Goal 文本逐字相同**，模型事先不得从 prompt 得到 package
- 判定依据是**实际 trace**（第二次命令参数里的 package），不是最终自然语言

---

## 7. Minimum Terminal Capability

从本任务反推，**capability-level**，不含实现：

| # | Required | 说明 |
|---|---|---|
| T1 | one-shot command execution | 一次执行即返回 |
| T2 | stdout capture | Observation A/B 的来源 |
| T3 | stderr capture | 失败诊断 |
| T4 | process exitCode | 判定命令是否成功 |
| T5 | mechanism-level failure reporting | Terminal 后端不可用 / 连接失败 |

**明确不需要**（本任务反推结论，非照抄）：

| # | Not required | 理由 |
|---|---|---|
| — | streaming | 只需最终 stdout；模型不需要 token 流 |
| — | PTY | 无 TTY 语义需求；纯管道足够 |
| — | interactive stdin | 命令非交互式，无需喂输入 |
| — | background process | 任务只要求「执行并拿结果」 |
| — | file upload/download | 纯读，无回传 |
| — | cwd / env / user | 当前任务无需求 |
| — | retry / rate-limit policy | 属 Provider 层，非 Terminal 能力 |

> 本表是后续 AT integration design 的**输入**，不是实现决定。
> AAR / IPC / Binder / Service / JNI 边界**均不在本轮范围**。

---

## 8. Shell Tool 语义（设计，不实现）

### 8.1 职责边界

```text
ShellTool 负责：  command → 执行 → stdout / stderr / exitCode
ShellTool 不负责：planning · package 解析策略 · 任务完成判断 · 下一命令选择
```

**不得**为 AGENT-0 设计 `GetForegroundAppTool` / `GetVersionTool` /
`InspectForegroundAppAndVersionAndPathTool`。复杂任务被 Tool 内部完成
= Agent 没有经历 feedback-driven decision，会破坏本次验证的意义。

### 8.2 Arguments

```json
{ "command": "..." }
```

仅此一个字段。`cwd` / `env` / `pty` / `stdin` / `encoding` / `retry` **不加入**。

### 8.3 Result Mapping（关键：复用冻结语义）

```text
命令执行完成、exitCode != 0
  → ToolResult.success = true      ← invocation 机制成功
  → exitCode / stdout / stderr 放入 output

Terminal 后端不可用 / 连接失败 / 协议失败 / 执行机制异常
  → ToolResult.success = false
  → error 放原因
```

依据 `Tool.kt:44-56` 已冻结语义：`success` 表示 **invocation 机制**成功，
**不是**领域成功。AG Core 在 `success=false` 时**不终止 Run**
（`AgentCore.kt:168-172` 追加后继续循环），因此模型可观察失败并自行决定下一步。

**结论：该语义足以支持 AGENT-0，无需扩展。**

### 8.4 Output 格式

```text
exitCode: 0
stdout:
...
stderr:
...
```

`empty stdout` / `empty stderr` / `non-zero exit` 三者必须可区分。
本轮**不创建类型**。

---

## 9. Observation 与 Verification

### 9.1 Observation

```text
NO NEW OBSERVATION TYPE REQUIRED FOR AGENT-0
```

依据：`ToolResult → ToolResultMessage → Session history → next ModelRequest`
链路已闭合（`AgentCore.kt:172` → `:136`）。`ToolResultMessage` 足以承担 Observation。

### 9.2 Verification

```text
NO VERIFICATION FRAMEWORK REQUIRED FOR AGENT-0
```

最小 verification 由模型通过**再一次 shell observation** 完成：

```text
发现 P → 查询 P 的 version/path → 结果与 P 对应 → 模型形成结论
```

现有 Agent Loop + 再次 ToolCall 足以承载。

### 9.3 FinalAnswer ≠ 成功证据

**必须**区分「模型产出了 FinalAnswer」与「Agent 真的完成了任务」。

E2E 验收必须从**真实 Tool trace** 验证：

- foreground package 确实被发现
- 后续查询确实针对该 package
- 返回的 version/path 确实对应该 package

不能只看最终自然语言回答。

---

## 10. Trace 需求

未来 AGENT-0 验收需要观察：

```text
User Goal → Model Turn 1 → ToolCall A(args) → ToolResult A
          → Model Turn 2 → ToolCall B(args) → ToolResult B
          → ... → FinalAnswer
```

### 10.1 现有 AgentEvent 是否足够

| 需要证明 | 现有事件 | 结论 |
|---|---|---|
| 哪个 Tool 被调用 | `ToolCallStarted(call)` `:24` | ✓ |
| Tool args 是什么 | `ToolCallStarted(call: ToolCall)` 含 `arguments` | ✓ |
| ToolResult 是什么 | `ToolCallFinished(call, result)` `:27` | ✓ |
| 调用顺序 | 事件为同步有序回调 | ✓ |
| Run 如何结束 | `RunFinished(outcome)` / `RunFailed(failure)` `:33,44` | ✓ |
| 结果已进入 history | `ToolCallFinished` 在 `session.append` **之后**发出（`:172` append → `:173` emit） | ✓ 可证明 |

**判定：`SUFFICIENT`。**

无需 telemetry / logging framework。测试 harness 只需实现一个
`AgentEventSink` 收集事件即可得到完整因果 trace。

---

## 11. Acceptance Criteria（S1–S11）

| # | 条件 |
|---|---|
| S1 | 用户只给 Goal，不给命令 / package / 顺序 |
| S2 | Model 自主选择第一个 Terminal action |
| S3 | Action 真实作用于 Android 环境 |
| S4 | 真实结果进入 Session history |
| S5 | Model 收到该结果 |
| S6 | Model 至少选择一次后续 action |
| S7 | 后续 action 的参数包含**从上一次真实 observation 获得**的信息 |
| S8 | 不同环境 observation 可产生不同的后续 action 参数 |
| S9 | Host 不规定命令序列 |
| S10 | Agent 最终产出 FinalAnswer |
| S11 | Final answer 与真实设备状态一致 |

**少一项即 `AGENT-0 E2E != PASS`。**

### 11.1 无效证明场景

| Case | 形式 | 为何无效 |
|---|---|---|
| A | 用户说「执行 uname -a」 | 用户已指定 action，无自适应决策 |
| B | 用户说「先执行 A，再执行 B」 | sequence 来自用户 |
| C | Host 写死 command A → command B | workflow 由代码固定 |
| D | 一个复杂 Tool 内部完成 discover→lookup→return | Agent 未经历 feedback-driven decision |

### 11.2 关于「分支」的说明

Agent **不要求**出现 `if X then A else B` 才算 adaptive。

以下已足够证明因果依赖：

```text
Observation : package = com.foo
Next Action : pm path com.foo
换成 package = com.bar
Next Action : pm path com.bar
```

这已是 environment-conditioned action。**不要为了「显得像 Agent」人为制造复杂错误分支。**

---

## 12. New AG Features Required

| 候选 | 需要？ | 依据 |
|---|---|---|
| Planner | **NO** | §4 明确不作为前置条件；现有 Loop 已能推进 |
| TaskState | **NO** | Run 已含 step count / tool executions / final answer（`AGENTNESS-AUDIT-0 §H`） |
| Observation type | **NO** | `ToolResultMessage` 已足够（§9.1） |
| Verification framework | **NO** | 再一次 tool observation 即可（§9.2） |
| Trace / telemetry | **NO** | `AgentEvent` 已 SUFFICIENT（§10.1） |

**AG Core / Agent Runtime 修改需求：NONE。**

若后续发现必须改 Core，必须先证明现有
`Model → Tool → Result → Model` 为何无法表达该需求，并给出源码证据。
不得以「最好有 Planner」作为理由。

---

## 13. Deferred

```text
failure-path 验收（non-zero exit 后换查询方式）  → 候选 AGENT-0.R1
streaming / PTY / interactive / background / file transfer
advanced tools（screenshot / UI / tap / swipe / input）
Skills · semantic progress · evidence · artifact · long-term memory
AT integration mechanism（AAR / IPC / Binder / Service）
Android Keystore / secure settings
```

---

## 14. 本文档完成后可以声称什么

严格限制为：

```text
AndroidAgent has demonstrated a real
environment-grounded, feedback-driven Agent execution loop.
```

**不得**声称「完整自主 Android Agent 已完成」。
