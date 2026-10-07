# AG.ENGINE-BOUNDARY-FREEZE — AG Engine 逻辑边界正式冻结

> 类型：DOCS / GIT / TAG。production code change = 0。
> 前置审计：`docs/audit/AG.CORE-RUNTIME.BOUNDARY-AUDIT.md`
> 审计结论（已 PASS）：
>
> ```text
> AG_CORE_RUNTIME_BOUNDARY_AUDIT_PASS
> BOUNDARY_ALREADY_ACCEPTABLE
> PRODUCTION_NORMALIZATION_REQUIRED = NO
> ```

---

## 1. Baseline

```text
仓库        : D:\codebank\AndCode\AndroidAgent
基线锚点    : ag-stable-1 = 401a365f43a38c82966acc88599b7b789ef362aa
              （95/95 测试 PASS · :app:assembleDebug PASS · production code change = 0）
本冻结      : 在 ag-stable-1 之上仅新增 docs，不改任何生产代码
tag 链      : ag-0 → ag-aga-0 → ag-stable-0/1 → ag-engine-0（本节点）
```

## 2. Audit Provenance

```text
审计文档 : docs/audit/AG.CORE-RUNTIME.BOUNDARY-AUDIT.md（task-owned，随本 commit 纳管）
审计范围 : 5 个 Gradle module、22 个生产 .kt 文件逐一审计
审计方法 : semantic responsibility determines ownership（非 module name）
审计结论 : BOUNDARY_ALREADY_ACCEPTABLE · MISPLACED = 0 · NO PHYSICAL RESTRUCTURE REQUIRED
审计日期 : 2026-10-06
```

## 3. AG Engine 定义（正式冻结）

```text
AG Engine
= Agent Runtime + AG Core
= 逻辑能力边界（LOGICAL CAPABILITY BOUNDARY）
```

**不创建** `:ag-engine` Gradle module、Engine facade、DI 框架、Factory 抽象。
AG Engine 是一个**已审计确认的逻辑边界**，物理上继续由两个现有 module 承载：

```text
Host（AGA / 未来 AGCLI / JVM headless）
  ↓
AG Engine
  ├─ Agent Runtime   （:ag-runtime）
  └─ AG Core         （:ag-core）

侧向 Integration（实现 Core 契约，非纵向主链）
  ├─ Provider 集成（:provider-openai-compatible / -jdk）
  └─ Tool 集成（未来 AndroidShellToolImpl）

Host Services（配置/凭据/持久化/日志/装配）
  当前位于 Host 内部 + JVM 开发侧；未抽 module（单 consumer，Complexity Gate 判 DO NOT CREATE NOW）
```

## 4. AG Core 冻结职责（Agent Semantic Kernel）

```text
AG Core = Agent Semantic Kernel = 智能体语义内核（:ag-core，com.androidagent.core）

冻结职责：
  Agent Loop（Model → Tool → ToolResult → History → Model → Final）
  Session / Run semantics（含单 active Run 独占）
  History 存储与语义
  ModelProvider contract（ModelRequest / ModelTurn）
  Tool contract（Tool / ToolSpec / ToolCall / ToolResult）
  ToolRegistry（注册 + dispatch：policy → lookup → execute）
  Tool policy semantics（ToolApproval / ToolApprovalPolicy）
  Agent events（AgentEvent / AgentEventSink）
  Run outcome（RunOutcome / RunFailure）

冻结约束：
  纯 Kotlin/JVM，零 Android / ADB / JNI / HTTP / 平台引用
  不依赖任何具体 provider / tool 实现（依赖倒置）
  不知道 API key、配置文件、OpenAI、EMG、Android、AT
  suspend 契约：run / complete / execute 均为 suspend
```

## 5. Agent Runtime 冻结职责（Agent Execution Lifecycle Manager）

```text
Agent Runtime = Agent Execution Lifecycle Manager = 智能体运行生命周期管理层
（:ag-runtime，com.androidagent.runtime）

冻结职责：
  current Session selection（currentSessionId）
  Host-facing send()（透传 AgentCore.run）
  newSession()（busy 时拒绝；旧 Session 保留在 Core）
  Host-facing busy state（isBusy，per-Host 一次一 send 守卫）
  Run invocation lifecycle（宿主串行化）

冻结约束：
  纯 Kotlin/JVM，host-neutral（AGA 与未来 AGCLI 均可直接复用）
  无第二套 Agent Loop / History / Tool dispatch / Model parsing / Run semantics
  无 Android / UI / HTTP / provider 实现
  RUNTIME IS THIN BY DESIGN（不扩功能）

与 Core 的 busy 双层关系（冻结）：
  Core SessionBusy  = per-Session 语义独占（最终保护）
  Runtime isBusy    = per-Host 便利守卫
  BOTH ARE VALID · NO DUPLICATION
```

## 6. Integration 边界（明确不属于 AG Engine）

```text
不属于 AG Engine 主体：
  OpenAiCompatibleProvider          → Integration（:provider-openai-compatible）
  JdkOpenAiHttpTransport            → Platform Integration（:provider-jdk）
  AndroidOpenAiHttpTransport        → Platform Integration（:app，单 Android consumer）
  future AndroidShellToolImpl       → Integration（未实现）
  AT Runtime / AT Core              → 另一个产品（AndroidTerminal-V2），仅经 Tool 契约间接使用

原则（冻结）：
  AG Core / Agent Runtime / AGA -X-> AT 实现（AGENTS.md 既有规则，本轮再次确认）
  Concrete Android Tool 是 AndroidAgent 唯一允许连接 Android 执行后端的层
  Integration 实现 Core contract，是侧向能力，不是 Host → Integration → Runtime → Core 纵向链
```

## 7. Host / Host Services 边界

```text
Host（当前 AGA，:app）：
  Android UI / Activity lifecycle / 用户输入 / 展示
  组合根（ChatCompositionRoot：选择实现并装配）
  配置 UI + API Key 运行时输入（仅驻留进程内存，不落盘）

Host Services（概念归属，当前未抽 module）：
  Configuration / Credential / Persistence / Logging / Composition
  当前状态：单 consumer（AGA）→ SHARED_BUT_NOT_YET_EXTRACT → DO NOT CREATE NOW

已知限制（继承 ag-stable-1 基线，非本冻结的失败项）：
  配置零持久化 · API Key 仅内存 · 无 durable Session · 无 AGCLI
```

## 8. NO Physical Restructure 结论

```text
当前物理依赖图（:app → :ag-runtime / :ag-core / :provider；:ag-runtime → :ag-core；
:provider → :ag-core）与 AG Engine 目标逻辑边界一致。

结论：NO PHYSICAL RESTRUCTURE REQUIRED。
不创建 :ag-engine / :host-services / 新 runtime / 新 session manager / DI / Factory。
（Complexity Gate 全项判定见审计文档 §13）
```

## 9. Known Deferred Items（继承，未在本轮解决）

```text
1. dev 配置读取（ConfigLoader/Resolver/fromEnvironment）概念属 Host Services，
   物理在 provider 模块，仅 JVM dev 侧消费 → 未来抽 Host Services 时自然迁移（OBS-1）
2. AndroidOpenAiHttpTransport 在 :app；第二个真实 Android consumer 出现时再抽
   :provider-openai-compatible-android（OBS-2）
3. API Key 持久化 / Keystore / Settings → 未实现（KNOWN LIMITATION，需独立 Step）
4. AGENT-0 S1–S11 完整真机验收（Two-Environment Contrast）未执行
5. 跨轮严格记忆验证 PENDING（继承 AGA-0 基线）
6. AndroidShellTool 实现 → AG.SHELL-1（前置：AT handoff consumer audit 通过）
```

## 10. Rollback / Tag Semantics

```text
tag: ag-engine-0（annotated，指向本 commit）

表示：
  AG Core boundary audited
  Agent Runtime boundary audited
  AG Engine logical boundary frozen
  production normalization not required

不表示：
  AT integration complete
  AndroidShellTool complete
  AGENT-0 complete
  production-ready AndroidAgent

回退：
  回到 ag-stable-1（401a365）即剔除本冻结与审计文档（生产代码两者完全一致）
  回到 ag-aga-0（2c8306c）即回到 AGA-0 冻结点
  本节点不移动/删除任何既有 tag
```

## 11. 历史文档不追溯改写

```text
docs/baseline/AG.BASELINE-FREEZE-0.md 保持其历史含义
（“当前工作基线冻结”，当时未做 Engine 边界审计）。
本文件是 Engine 边界审计后的正式冻结，不冒充当时已完成审计。
```
