# AndroidAgent 总体架构与能力地图

> 版本：v1.0  
> 状态：**ARCHITECTURE FROZEN / Controller Capability Synthesis**  
> 冻结范围：**架构分类、能力归位、主要责任边界、结构层与逻辑能力域关系**  
> 非冻结范围：具体 API / class / module 名称、存储格式、Human Interaction 表示形式、Search backend 形式、Sandbox 硬隔离实现、RAG 内部结构等仍需后续 Step 决定。  
> 用途：作为 `AndroidAgent_Master_Roadmap.md` 的上游架构依据。  
> 注意：本文档 **不自动开启任何生产 Step**，也不代表所有列出的未来能力都必须实现。

---

# 1. 冻结结论

本轮完成的是：

```text
AG.RESEARCH-1 / 2 / 3
        ↓
成熟 Agent Capability Inventory
        ↓
AndroidAgent Architecture Mapping
        ↓
Capability Completeness Audit
        ↓
AndroidAgent Architecture & Capability Map v1.0
```

最终结论：

```text
ARCHITECTURE_CAPABILITY_MAPPING = PASS
CAPABILITY_COVERAGE             = PASS
MAJOR_DUPLICATE_OWNERSHIP       = RESOLVED
PHYSICAL_ARCHITECTURE           = UNCHANGED
ROADMAP                         = NEEDS_NORMALIZATION
PRODUCTION_CODE                 = UNCHANGED
```

本轮不推翻已经冻结的物理结构：

```text
AndroidAgent = AG Engine + Tool Layer + Host / Product
AG Engine    = AG Core + Agent Runtime
```

本轮新增冻结的是：

> **Tool / State / Environment / Authority / Provider / Host / Extension / Orchestration 等能力到底属于哪里，以及当能力横跨多个结构节点时各自负责哪一部分。**

---

# 2. 研究与架构的关系

已有研究的职责：

```text
AG.RESEARCH-1
= Operational Foundation Survey
  Workspace / File / Network / Sandbox / Permission / Approval

AG.RESEARCH-2
= Mature Agent Full Capability Inventory
  Full breadth capability map + AndroidAgent gap survey

AG.RESEARCH-3
= Network Capability Deep Dive
  Search / Fetch / Download / Realm / Egress / SSRF / Output Bound
```

v1.0 不修改历史 Research 文档。

三者与架构、Roadmap 的关系：

```text
Research
= WHAT EXISTS

Architecture
= WHERE IT BELONGS

Roadmap
= WHEN TO BUILD

Implementation Step
= HOW TO BUILD
```

---

# 3. 状态标记

| 标记 | 含义 |
|---|---|
| ✅ | 已实现并有当前证据 |
| 🟢 | Preflight / Design 已完成，可进入实现 Step |
| 🟡 | 已识别且位置明确，但仍需 Research / Preflight |
| 🔵 | 已识别，后续能力 |
| ⏸ | Future / Deferred，当前不展开 |
| ↗ | 外部系统 / Backend 负责 |

这些符号描述的是当前成熟度，不等价于 Roadmap Priority。

---

# 4. 两种视图：结构架构 vs 能力架构

必须区分：

```text
STRUCTURAL ARCHITECTURE
= 真正存在依赖方向、代码所有权和产品边界的结构

CAPABILITY DOMAINS
= 横跨结构节点的逻辑职责域
```

## 4.1 结构架构

```text
AndroidAgent
│
├─ Host / Product
│
├─ AG Engine
│  ├─ AG Core
│  └─ Agent Runtime
│
├─ Provider Implementations
│
├─ Tool Layer
│  ├─ android_shell
│  ├─ network
│  ├─ file
│  └─ future concrete tools
│
└─ External Capability Backends
   ├─ AT Engine
   ├─ Android Framework / OS
   ├─ App Filesystem / SAF
   └─ Android Network Stack
```

## 4.2 逻辑能力域

```text
State & Context
Operational Environment
Authority & Safety
Observability & Diagnostics
Extension System
Orchestration
```

这些 **不是自动对应新 module**。

禁止为了图形对称直接创建：

```text
:ag-state
:ag-environment
:ag-authority
:ag-observability
:ag-extension
:ag-orchestration
```

只有出现真实消费者、清晰依赖收益和实际实现需求时，才决定是否形成物理模块。

---

# 5. AndroidAgent 总体能力架构图

```text
┌────────────────────────────────────────────────────────────────────────────────────────────────────────────┐
│                                         AndroidAgent 安卓智能体                                           │
└────────────────────────────────────────────────────────────────────────────────────────────────────────────┘

                                                    用户 / User
                                                        │
                                                        ▼
┌────────────────────────────────────────────────────────────────────────────────────────────────────────────┐
│ ① Host / Product                                  宿主 / 产品层                                           │
│                                                                                                            │
│ AGA ✅                                                                                                     │
│ ├─ Chat / Input / Transcript ✅                                                                                  │
│ ├─ Provider 选择 / 配置 ✅                                                                                       │
│ ├─ DebugBridge / Direct Tool Validation ✅                                                                       │
│ ├─ Stop / Cancel UI 🟡                                                                                           │
│ ├─ Retract UI 🟡                                                                                                 │
│ ├─ Approval / Ask UI 🔵                                                                                          │
│ ├─ Execution Mode Selector：PLAN / BUILD 🔵                                                                      │
│ ├─ Human Interaction UI 🟡                                                                                       │
│ ├─ Session List / Resume / Fork / Export UI 🔵                                                                   │
│ ├─ Context Usage / Cost / Health UI 🔵                                                                           │
│ ├─ Headless / Non-interactive Host 🔵                                                                            │
│ ├─ Config Layering / Precedence 🔵                                                                               │
│ ├─ Output Style / Persona 🔵                                                                                     │
│ ├─ Server / SDK / API Surface ⏸                                                                                  │
│ ├─ IDE / Web / Remote Surface ⏸                                                                                  │
│ └─ AGC / further frontend ⏸                                                                                      │
└──────────────────────────────────────────────┬─────────────────────────────────────────────────────────────┘
                                               │
                                               │ input / control / render / configure
                                               ▼
┌────────────────────────────────────────────────────────────────────────────────────────────────────────────┐
│ ② AG Engine                                      智能体引擎                                                │
│                                                                                                            │
│ ┌────────────────────────────────────────────┐       ┌──────────────────────────────────────────────────┐  │
│ │ AG Core（语义核心）                       │       │ Agent Runtime（运行时）                         │  │
│ │                                            │       │                                                  │  │
│ │ Agent Loop ✅                              │       │ currentSession ✅                                │  │
│ │ Session / Run semantics ✅                 │       │ activeRun / single-active ✅                     │  │
│ │ History semantic SSOT ✅                   │       │ send / newSession / busy ✅                      │  │
│ │ Tool Contract / Registry ✅                │       │ lifecycle / cleanup ✅                           │  │
│ │ ModelProvider Contract ✅                  │       │ cancelCurrentRun 🟡                              │  │
│ │ ToolResult → Observation ✅                │       │ cancellation propagation 🟡                      │  │
│ │ RunOutcome / RunFailure ✅                 │       │ foreground/background lifecycle 🔵               │  │
│ │ AgentEvent ✅                              │       │ background Run 🔵                                │  │
│ │ maxSteps ✅                                │       │                                                  │  │
│ │ Approval contract seam ✅                  │       │ Runtime 不拥有第二套 Loop / History               │  │
│ │                                            │       │                                                  │  │
│ │ Cancel outcome semantics 🟡                │       └──────────────────────────────────────────────────┘  │
│ │ History rollback semantics 🟡              │                                                            │
│ │ Human Interaction semantics 🟡             │                                                            │
│ │ Doom-loop / stuck semantics 🔵             │                                                            │
│ └─────────────────────────┬──────────────────┘                                                            │
└───────────────────────────┼────────────────────────────────────────────────────────────────────────────────┘
                            │
             ┌──────────────┴───────────────────────────┐
             │                                          │
             │ ModelProvider Contract                   │ Tool Contract
             ▼                                          ▼

┌──────────────────────────────────────────────┐   ┌──────────────────────────────────────────────────────────┐
│ ③ Provider Layer                            │   │ ④ Tool Layer                                             │
│ 模型提供者 / 模型通信                       │   │ 具体可执行能力                                           │
│                                              │   │                                                          │
│ OpenAI-compatible ✅                        │   │ android_shell ✅                                          │
│ ├─ OpenRouter ✅                            │   │ └─ backend → AT Engine ↗                                 │
│ └─ EMG route/config ✅                      │   │                                                          │
│                                              │   │ network.fetch ✅                                         │
│ Streaming ✅                                │   │ └─ backend → Android Network Stack                       │
│ Failure Classification ✅                   │   │                                                          │
│                                              │   │ file READ v0 🟢                                         │
│ Retryable Taxonomy 🟡                       │   │ ├─ read / search / list / stat                           │
│ Bounded Retry / Backoff 🟡                  │   │ └─ backend → App Filesystem                              │
│ Reconnect 🟡                                │   │                                                          │
│ Partial-stream Recovery 🟡                  │   │ future                                                    │
│ Model Fallback 🔵                           │   │ ├─ file write / edit / patch ⏸                           │
│ Provider Routing ⏸                         │   │ ├─ Accessibility / Framework structured tools ⏸           │
│ Prompt Cache / Provider Cache 🔵            │   │ ├─ LSP / Code Intelligence ⏸                            │
│ Usage Metadata / Health 🔵                  │   │ ├─ Formatter integration ⏸                              │
│                                              │   │ ├─ File watcher ⏸                                       │
└──────────────────────────────────────────────┘   │ └─ Multimodal attachment normalization ⏸                 │
                                                   └──────────────────────────────────────────────────────────┘
                                                                  │
                                                                  ▼
                                                   ┌──────────────────────────────────────────────────────────┐
                                                   │ External Capability Backends                             │
                                                   │                                                          │
                                                   │ AT Engine ↗                                              │
                                                   │ Android Framework / OS ↗                                 │
                                                   │ App Filesystem / SAF ↗                                   │
                                                   │ Android Network Stack ↗                                  │
                                                   └──────────────────────────────────────────────────────────┘


═══════════════════════════════════════════════ 逻辑公共能力域 ════════════════════════════════════════════════


┌────────────────────────────────────────────────────────────────────────────────────────────────────────────┐
│ ⑤ State & Context                             状态与上下文：KNOW                                           │
│                                                                                                            │
│ Conversation History ✅                                                                                     │
│ Session / Run semantic state ✅基础                                                                         │
│                                                                                                            │
│ Active Input Retract 🟡                                                                                     │
│ Session Persistence 🔵                                                                                      │
│ Session Resume 🔵                                                                                           │
│ Conversation Rewind 🔵                                                                                      │
│ Resource / File Undo 🔵                                                                                     │
│ Session Fork 🔵                                                                                             │
│ Checkpoint 🔵                                                                                               │
│ Context Compaction 🟡                                                                                       │
│ Tool-result Pruning 🟡                                                                                      │
│ Context Summary 🟡                                                                                          │
│ Context Pressure / Usage 🟡                                                                                 │
│ Persistent Memory 🔵                                                                                        │
│ Task / Progress State 🔵                                                                                    │
│ External Resource Reference / Run Output Reference 🔵                                                       │
│ Session Serialization / Share Data 🔵                                                                       │
│                                                                                                            │
│ 注：History 的语义 SSOT 在 AG Core；State & Context 表示对 History / Session 的持久化、压缩、恢复、引用等能力域。 │
└────────────────────────────────────────────────────────────────────────────────────────────────────────────┘


┌────────────────────────────────────────────────────────────────────────────────────────────────────────────┐
│ ⑥ Operational Environment                    运行 / 工作环境：HAVE / WHERE                                │
│                                                                                                            │
│ Workspace                                                                                                  │
│ ├─ Primary Root：filesDir/agent/ 🟢                                                                        │
│ ├─ Additional Roots 🔵                                                                                     │
│ ├─ Current Working Directory / CWD 🔵                                                                      │
│ └─ Project / Workspace Identity 🔵                                                                          │
│                                                                                                            │
│ Resource Scope 🔵                                                                                           │
│ Resource Grants 🔵                                                                                          │
│ ├─ added directory grant                                                                                   │
│ └─ SAF URI capability ⏸                                                                                    │
│                                                                                                            │
│ Execution Realm 🔵                                                                                          │
│ ├─ APP                                                                                                     │
│ ├─ AT / ADB shell                                                                                          │
│ └─ future run-as                                                                                           │
│                                                                                                            │
│ Principal / Execution Identity 🔵                                                                           │
│ ├─ APP_SELF UID                                                                                            │
│ ├─ ADB_SHELL uid=2000                                                                                      │
│ └─ RUN_AS_APP target UID                                                                                   │
│                                                                                                            │
│ Workspace Trust State 🔵                                                                                    │
│ Environment / temp / non-secret runtime values 🔵                                                          │
│ Isolated Workspace / Worktree ⏸                                                                            │
│                                                                                                            │
│ 注：Grant、Realm、Principal 三者不再混为一个概念。                                                          │
└────────────────────────────────────────────────────────────────────────────────────────────────────────────┘


┌────────────────────────────────────────────────────────────────────────────────────────────────────────────┐
│ ⑦ Authority & Safety                         权限与安全：MAY                                              │
│                                                                                                            │
│ Approval Policy                                                                                             │
│ ├─ Allow ✅                                                                                                 │
│ ├─ Deny ✅                                                                                                  │
│ └─ Ask 🟡                                                                                                  │
│                                                                                                            │
│ Policy Matching                                                                                             │
│ ├─ Tool-level ✅                                                                                            │
│ ├─ Action-level 🟡                                                                                         │
│ ├─ Argument / Pattern matching 🟡                                                                          │
│ ├─ Path-level 🟡                                                                                           │
│ ├─ Domain-level 🔵                                                                                         │
│ └─ Side-effect-level 🔵                                                                                    │
│                                                                                                            │
│ Approval Persistence 🔵                                                                                     │
│ Policy Source / Tiering（default / workspace / user / managed）🔵                                          │
│ Execution Mode：PLAN / BUILD 🟡                                                                            │
│ Run Budget Policy：step / time / cost caps 🟡/🔵                                                           │
│ Workspace Trust Policy 🔵                                                                                   │
│ Credential Protection 🔵                                                                                    │
│ Protected Resources / Anti-self-escalation 🔵                                                              │
│ Network Egress Policy 🔵                                                                                    │
│ Sandbox Semantics 🟡                                                                                        │
│                                                                                                            │
│ Effective Authority = OS Capability ∩ Agent Policy ∩ User Grant                                            │
│ Effective Sandbox   = OS Boundary ∩ Environment Scope ∩ Authority Policy ∩ Tool/Backend Enforcement        │
└────────────────────────────────────────────────────────────────────────────────────────────────────────────┘


┌────────────────────────────────────────────────────────────────────────────────────────────────────────────┐
│ ⑧ Observability & Diagnostics                可观察性与诊断：SEE                                          │
│                                                                                                            │
│ Provider Truth ✅                                                                                           │
│ Streaming Transcript ✅                                                                                     │
│ Thinking → Thought ✅                                                                                       │
│ Assistant Content ✅                                                                                        │
│ ToolCall / ToolResult / Failure ✅                                                                          │
│ DebugBridge ✅                                                                                              │
│ Direct Tool Validation ✅                                                                                   │
│ Agent Trace ✅                                                                                              │
│                                                                                                            │
│ Token Accounting 🔵                                                                                        │
│ Cost Accounting 🔵                                                                                         │
│ Context Usage / Pressure 🟡                                                                                 │
│ Provider Health 🔵                                                                                         │
│ Tool Health 🔵                                                                                             │
│ Retry / Reconnect Status 🟡                                                                                 │
│ Cancellation Status 🟡                                                                                      │
│ Output truncation / spill reporting 🔵                                                                      │
│ Diagnostics / Doctor 🔵                                                                                    │
│                                                                                                            │
│ 注：Output Bounding 的 enforcement 属于 Tool / Backend；Observability 只负责暴露发生了截断/溢出的事实。       │
└────────────────────────────────────────────────────────────────────────────────────────────────────────────┘


══════════════════════════════════════════════════ 未来能力 ═══════════════════════════════════════════════════


┌──────────────────────────────────────────────────────┐   ┌───────────────────────────────────────────────────┐
│ ⑨ Extension System                                  │   │ ⑩ Orchestration                                   │
│ 扩展系统 ⏸                                          │   │ 编排系统 ⏸                                       │
│                                                      │   │                                                   │
│ Skills                                               │   │ Task Planning / Decomposition                     │
│ MCP                                                  │   │ Task / Semantic Progress State                    │
│ Plugins / Extensions                                 │   │ Subagents                                         │
│ Hooks                                                │   │ Delegation                                        │
│ Custom Commands                                      │   │ Depth / Recursion Control                         │
│ Custom Tools                                         │   │ Context Inheritance / Isolation                   │
│ Rules / Instruction Sources                          │   │ Inter-agent Messaging                             │
│                                                      │   │ Structured Handoff                                │
│                                                      │   │ Failure-aware Continuation                        │
│                                                      │   │ Evidence / Remaining Work                         │
│                                                      │   │ Workflow / Teams                                  │
└──────────────────────────────────────────────────────┘   └───────────────────────────────────────────────────┘


════════════════════════════════════════════════ 组合能力 / 跨边界语义 ═════════════════════════════════════════


Human Interaction 🟡
    Agent → Human → Agent
    Core semantic + Runtime continuation + Host UI
    表示形式仍需 Preflight；当前不强制定义为 Tool。

Search 🔵
    Search Strategy + Search Backend
    Strategy 可由 Skill / Agent reasoning 提供；
    Backend 可以是 Provider-hosted、未来 concrete Tool 或外部服务。
    Search 不是天然等价于 Tool，也不是天然等价于 Skill。

Research 🔵
    Search + Fetch + Reasoning + Evidence
    不创建 research_tool。

Download ⏸
    Fetch + File Write + Workspace / Authority
    不创建独立 Download subsystem。

Artifact / Run Output Reference 🔵
    State & Context 保存引用和来源；
    文件本体位于 Workspace / File；
    当前不创建 Artifact Tool / Manager。

Planning
    Execution Mode PLAN / BUILD → Authority & Safety
    Task Planning / Decomposition → Orchestration
    两者名称相似，但不是同一能力。

Run Control
    Core       → outcome / history semantics
    Runtime    → active-run mechanism / cancellation
    Authority  → budgets / execution mode
    Provider   → request timeout / transport cancellation
    Host       → stop / background controls
    这是一个分布式能力族，不创建 RunControlManager 仅为对称。

Sandbox
    Environment + Authority + Tool/Backend + Android OS 的组合安全模型，
    不等价于一个独立物理模块。
```

---

# 6. 架构语义速记

```text
THINK        = AG Engine
TALK         = Provider
DO           = Tool
KNOW         = State & Context
HAVE / WHERE = Operational Environment
MAY          = Authority & Safety
SEE          = Observability & Diagnostics
SHOW         = Host / Product
EXTEND       = Extension System
ORCHESTRATE  = Orchestration
EXECUTE      = Tool -> Backend
```

---

# 7. AG Engine

## 7.1 AG Core

AG Core 负责可复用 Agent 语义，而不是平台能力。

```text
AG Core
├─ Agent Loop
├─ Session / Run semantics
├─ History semantic SSOT
├─ ModelProvider contract
├─ Tool contract / registry
├─ ToolResult → Observation
├─ RunOutcome / RunFailure
├─ AgentEvent
├─ maxSteps
└─ approval contract seam
```

当前状态：

| Capability | Status |
|---|---|
| Agent Loop | ✅ |
| Session / Run semantics | ✅ |
| History semantic SSOT | ✅ |
| ModelProvider contract | ✅ |
| Tool contract / registry | ✅ |
| Observation feedback | ✅ |
| RunOutcome / failure model | ✅ |
| AgentEvent | ✅ |
| maxSteps | ✅ |
| allow / deny seam | ✅ |
| Cancel outcome semantics | 🟡 |
| History rollback semantics | 🟡 |
| Human Interaction semantics | 🟡 |
| Doom-loop semantics | 🔵 |

AG Core 继续保持：

```text
Android-free
HTTP-free
AT-free
Product-free
Concrete-Tool-free
```

---

## 7.2 Agent Runtime

Runtime 管理“现在正在运行什么”。

```text
Agent Runtime
├─ currentSession
├─ activeRun
├─ send
├─ newSession
├─ busy
├─ cancellation mechanism
└─ lifecycle
```

当前：

| Capability | Status |
|---|---|
| currentSession | ✅ |
| single-active Run | ✅ |
| send / newSession | ✅ |
| busy lifecycle | ✅ |
| finally cleanup | ✅ |
| user-facing cancel mechanism | 🟡 |
| cancellation propagation contract | 🟡 |
| foreground/background lifecycle | 🔵 |
| background Run | 🔵 |

Runtime 不拥有：

```text
第二套 Agent Loop
第二套 History
Concrete Tools
Provider implementation
Android / AT implementation
```

---

# 8. Provider Layer

Provider Layer = ModelProvider contract 的实现侧，不等于 Core 中的 contract。

职责：

```text
HTTP / SSE
Provider-specific protocol
Authentication
Error mapping
Retry / Backoff
Reconnect
Partial stream behavior
Provider routing / fallback
Usage metadata
```

当前：

| Capability | Status |
|---|---|
| OpenAI-compatible provider | ✅ |
| OpenRouter | ✅ |
| EMG route/config | ✅ |
| reasoning/content streaming | ✅ |
| Timeout / Network / HTTP classification | ✅ |
| retryable taxonomy | 🟡 |
| bounded retry / backoff | 🟡 |
| reconnect | 🟡 |
| partial-stream recovery | 🟡 / design-blocked |
| model fallback | 🔵 |
| provider routing | ⏸ |
| usage metadata / health | 🔵 |
| prompt/provider cache integration | 🔵 |

冻结关系：

```text
Reconnect
!= Retry
!= Partial-stream Recovery
```

---

# 9. Tool Layer

Tool Layer 是 Agent 的 **DO**。

当前：

```text
android_shell      ✅
network.fetch      ✅
file READ v0       🟢
```

## 9.1 android_shell

```text
AG Core
↓ Tool contract
android_shell
↓
AT Engine
↓
Android execution
```

严格定义：

```text
android_shell = Tool
AT Engine     = External Capability Backend
```

---

## 9.2 network.fetch

```text
network.fetch
├─ HTTPS
├─ public-network guard
├─ IPv4 / IPv6 SSRF guard
├─ redirect re-validation
├─ bounded read
└─ HTML → text
```

状态：✅

注意：

```text
Network Access
!= Network Egress Authority
!= SSRF Guard
!= Output Bound
!= Credential Policy
```

这些是不同能力。

---

## 9.3 file

V0：

```text
file
├─ read
├─ search
├─ list
└─ stat
```

状态：

```text
FILE READ v0 = 🟢 IMPLEMENTATION_READY
```

未来：

```text
write / edit / patch
```

必须先解决 mutating authority granularity。

---

## 9.4 Tool Enhancement Candidates

从成熟 Agent 调研中保留，但不视为当前基础 blocker：

```text
LSP / code intelligence
Formatter integration
File watcher
Image / multimodal attachment normalization
Browser automation
Accessibility structured capability
Android Framework structured capability
Media / Sensor capability
```

是否成为 First-class Tool 由真实需求决定，而不是由成熟 Agent 是否存在同名工具决定。

---

# 10. State & Context

State & Context = Agent 的 **KNOW**。

关键职责：

```text
History 的长期管理
Session / Run 状态
Context window 管理
Persistence / Resume
Rollback / Rewind
Memory
Task state
External resource references
```

冻结分类：

| Capability | Status | Placement |
|---|---:|---|
| Conversation History | ✅ | Core semantic SSOT + State domain |
| Active Input Retract | 🟡 | State + Core rollback + Runtime cancel + Host UI |
| Session Persistence | 🔵 | State |
| Session Resume | 🔵 | State + Host |
| Conversation Rewind | 🔵 | State |
| Resource / File Undo | 🔵 | State + Tool / Workspace |
| Fork | 🔵 | State |
| Checkpoint | 🔵 | State |
| Context Compaction | 🟡 | State / Context |
| Tool-result Pruning | 🟡 | State / Context |
| Context Summary | 🟡 | State / Context |
| Context Pressure | 🟡 | State + Observability |
| Persistent Memory | 🔵 | State |
| Task / Progress State | 🔵 | State; planning logic may live in Orchestration |
| External Resource Reference | 🔵 | State |
| Session serialization/share data | 🔵 | State; Host exposes UX |

## 10.1 History 不重复所有权

```text
AG Core
= History 的语义 SSOT、消息顺序、ToolCall/ToolResult 配对规则

State & Context
= History 的 persistence / compaction / rewind / memory / serialization 能力域
```

不是两套 History。

---

## 10.2 Artifact

当前冻结：

```text
Artifact 不是一级架构层
Artifact 不是默认 Tool
```

如果 Run 生成：

```text
build/app-debug.apk
```

则：

```text
Workspace / File
= APK 本体

State & Context
= RunOutputRef / ExternalResourceReference
  path
  type
  producerRun
  summary / metadata
```

只有未来出现真正的跨 Run 输出生命周期需求时，才判断是否正式引入 `ArtifactRef` 等模型。

---

# 11. Operational Environment

Operational Environment = **HAVE / WHERE**。

冻结概念：

```text
Workspace
Resource Scope
Resource Grant
Execution Realm
Principal
Environment
Workspace Trust State
```

## 11.1 Workspace

```text
Workspace
├─ Primary Root
├─ Additional Roots
├─ CWD
└─ Workspace Identity
```

当前：

```text
Primary Root = filesDir/agent/  🟢
```

这只是 V0 真实边界，不要求立即创建 `WorkspaceManager`。

---

## 11.2 Grant / Realm / Principal 必须分开

v0.2 中这三个概念仍有混淆，v1.0 正式拆开：

```text
Resource Grant
= 被授予了什么资源能力
例如 SAF URI grant / additional root

Execution Realm
= 操作在哪个执行世界发生
例如 APP / AT-ADB / future run-as

Principal
= 实际以谁的身份执行
例如 APP_SELF UID / uid=2000(shell) / target app UID
```

示例：

```text
APP_SELF
Principal = AndroidAgent App UID
Realm     = APP
Grant     = filesDir/agent root

SAF
Principal = AndroidAgent App UID
Realm     = APP
Grant     = URI capability

ADB_SHELL
Principal = uid=2000(shell)
Realm     = AT / ADB
Grant     = OS / SELinux / shell 可见资源集合

RUN_AS_APP
Principal = target debuggable app UID
Realm     = AT / ADB
Grant     = run-as 可见资源集合
```

这样 Authority 才能判断：

```text
WHO + WHERE + WHAT RESOURCE + ACTION + MODE
```

---

# 12. Authority & Safety

Authority & Safety = Agent 的 **MAY**。

冻结职责：

```text
Approval
Policy Matching
Approval Persistence
Execution Mode
Run Budget Policy
Trust Policy
Credential Protection
Protected Resources
Network Egress Policy
Sandbox Semantics
```

## 12.1 Policy Granularity

成熟 Agent 调研证明仅 Tool-level 不够。

目标能力：

```text
Tool
Action
Argument / Pattern
Path
Domain
Side-effect class
Principal / Realm
```

当前：

```text
Tool-level Allow / Deny ✅
其余粒度             🟡 / 🔵
```

---

## 12.2 Policy Tiering

补入 v0.2 漏项：

```text
Default Policy
Workspace Policy
User Policy
Managed / Admin Policy
```

当前不是近期实现目标，但架构上归 `Authority & Safety`。

---

## 12.3 PLAN / BUILD

冻结：

```text
Execution Mode PLAN / BUILD
-> Authority & Safety
```

Host 负责选择 / 展示：

```text
User -> Host selector -> Authority profile
```

Authority 定义真正的 enforcement：

```text
PLAN
├─ read/search/fetch         allowed according to policy
├─ mutating file action      deny/ask
└─ destructive action        deny/ask
```

Prompt 可以改变模型行为，但：

```text
Prompt != Security Boundary
```

### 与 Task Planning 的区别

```text
PLAN / BUILD
= Execution Mode
= “允许做什么”
= Authority

Task Planning
= “任务准备怎么做”
= Orchestration / Agent reasoning
```

两个 `Plan` 不得混淆。

---

## 12.4 Run Budget Policy

RESEARCH-2 的 Run Control 调研还包括：

```text
step limit
timeout
cost / budget cap
```

冻结归位：

```text
maxSteps semantic enforcement
-> AG Core ✅

Provider request timeout
-> Provider

Run-level elapsed-time policy
-> Runtime + Authority

Cost / budget cap
-> Authority policy
   + Provider usage data
   + Observability accounting
```

不创建一个独立 Budget subsystem。

---

# 13. Sandbox

Sandbox 是组合安全模型。

```text
Effective Sandbox
=
Android OS Boundary
∩ Operational Environment
∩ Authority Policy
∩ Tool / Backend Enforcement
```

AndroidAgent 不机械复制：

```text
bubblewrap
Seatbelt
Docker sandbox
```

Android 上已有：

```text
App UID
SELinux
Scoped storage
SAF capabilities
ADB shell principal
target run-as principal
```

因此 v1.0 冻结的是 **Sandbox semantics 和 enforcement 分布**，不是某个 `SandboxManager` class。

---

# 14. Observability & Diagnostics

Observability = **SEE**。

当前强项：

```text
Provider Truth ✅
Streaming Transcript ✅
Thinking → Thought ✅
ToolCall / ToolResult / Failure ✅
DebugBridge ✅
Direct Tool Validation ✅
Agent Trace ✅
```

后续：

```text
Token Accounting
Cost Accounting
Context Usage
Provider / Tool Health
Retry Status
Cancellation Status
Diagnostics / Doctor
```

## 14.1 Output Bounding

冻结所有权：

```text
Tool / Backend
= enforce bound

State & Context
= receives bounded context

Observability
= reports truncation / spill / summary

Host
= renders the fact to user
```

因此不再把 Output Bounding 单独归 Observability。

---

# 15. Human Interaction

Human Interaction 必须保留为成熟 Agent capability。

语义：

```text
Agent -> Human -> Agent
```

与：

```text
User -> System
Stop / Cancel / Pause / Resume
```

完全不同。

架构所有权冻结为：

```text
AG Core
= “需要用户输入”的语义

Agent Runtime
= suspend / continuation lifecycle

Host
= question / choice UI
```

但以下实现形式 **不冻结**：

```text
ask_user Tool
ModelTurn.NeedUserInput
Continuation primitive
```

结论：

```text
HUMAN_INTERACTION_PLACEMENT = FROZEN
HUMAN_INTERACTION_REPRESENTATION = RESEARCH_REQUIRED
```

---

# 16. Search / Research / Download

## 16.1 Search

v1.0 修正一个过强表述：

```text
Search != 天然是 Tool
Search != 天然是 Skill
```

更准确：

```text
Search Capability
=
Search Strategy
+
Search Backend
```

Strategy 可能来自：

```text
Skill
Agent reasoning
Orchestration workflow
```

Backend 可能来自：

```text
Provider-hosted search
Concrete agent-side Tool
External service
```

所以当前：

```text
Search = Composite Capability 🔵
```

不提前冻结 model-facing shape。

---

## 16.2 Research

```text
Research
=
Search
+ Fetch
+ Reasoning
+ Evidence
+ Synthesis
```

不创建：

```text
research_tool
```

---

## 16.3 Download

```text
Download
=
Fetch
+ File Write
+ Workspace Scope
+ Authority
```

不视为新的底层网络 primitive。

---

# 17. Extension System

Extension System 回答：

> Agent 的能力来源如何被扩展。

```text
Skills
MCP
Plugins / Extensions
Hooks
Custom Commands
Custom Tools
Rules / Instruction Sources
```

当前全部：

```text
⏸ Future
```

不设计内部框架。

## 17.1 Memory 不属于 Extension

```text
Persistent Memory
-> State & Context
```

## 17.2 RAG

RAG 暂不建立一级 subsystem。

当前冻结为 Composite Capability：

```text
RAG
=
Retrieval Backend
+ Knowledge Resource
+ Context Injection / State
+ Authority
```

未来如果真实需求证明需要独立 Knowledge subsystem，再开 Step。

---

# 18. Orchestration

Orchestration 回答：

> Agent 如何组织复杂任务、多个执行单元或多个 Agent。

冻结能力地图：

```text
Task Planning / Decomposition
Task / Semantic Progress State
Subagents
Delegation
Depth / Recursion Control
Context Inheritance / Isolation
Inter-agent Messaging
Structured Handoff
Failure-aware Continuation
Evidence
Remaining Work
Workflow
Teams
```

当前全部：

```text
⏸ Future
```

注意：

```text
Task / Progress State
```

数据状态属于 `State & Context`；

而：

```text
Planning / Delegation / Continuation logic
```

属于 `Orchestration`。

---

# 19. Host / Product

Host = **SHOW / USER CONTROL / PRODUCT SURFACE**。

当前：

```text
AGA
Chat UI
Provider selector/config
Transcript
DebugBridge
Composition Root
```

均已存在。

未来能力：

```text
Stop / Retract UI
Approval UI
PLAN / BUILD selector
Human Question UI
Session browser
Resume / Fork / Share / Export
Context usage UI
Cost / Health UI
Background notification
Headless host
AGC
IDE / Web / Remote
Server / SDK / API
```

## 19.1 Config

补入 RESEARCH-2 中 v0.2 未显式覆盖的产品能力：

```text
Config layering / precedence
Managed / Admin config
```

其中：

```text
config storage / parsing
-> Host / Product

policy meaning / precedence enforcement
-> Authority
```

---

## 19.2 Output Style

成熟 Agent 中存在 output style / persona 类能力。

冻结：

```text
Output Style / Persona
-> Host / Product configuration
-> may affect prompt/rendering
```

不是独立 Tool，也不是核心语义基础设施。

---

# 20. External Capability Backends

```text
Tool Layer
    │
    ├─ android_shell
    │      └─ AT Engine
    │
    ├─ network.fetch
    │      └─ Android Network Stack
    │
    ├─ file
    │      └─ App Filesystem / future SAF
    │
    └─ future Android Tool
           └─ Android Framework / OS
```

AT 冻结定位：

```text
AT Engine
= Android execution capability backend

android_shell
= model-facing AG Tool
```

严格依赖：

```text
AG Core
   -X-> AT

Agent Runtime
   -X-> AT

AGA
   -X-> AT

Concrete Android Tool
   ---> AT Engine   ALLOWED
```

---

# 21. Run Control 是分布式能力族

RESEARCH-2 使用 `RUN_CONTROL` 作为能力分类是合理的，但它不对应一个单独物理层。

完整归位：

| Run Control capability | Primary placement |
|---|---|
| maxSteps | AG Core ✅ |
| Cancel outcome semantics | AG Core 🟡 |
| Cancel active Run | Agent Runtime 🟡 |
| Stop button | Host 🟡 |
| Provider request timeout | Provider ✅ / configurable |
| Run elapsed timeout | Runtime + Authority 🔵 |
| Cost cap | Authority + Observability + Provider usage 🔵 |
| Background Run | Runtime + Host 🔵 |
| Headless / non-interactive | Host / Product 🔵 |
| Model fallback | Provider 🔵 |
| Isolated workspace/worktree | Operational Environment + Orchestration ⏸ |
| Doom-loop detection | Core / Orchestration 🔵 |

因此：

```text
Run Control = capability family
Run Control != new RunControlManager by default
```

---

# 22. 能力完整性审计

本轮逐项对照 RESEARCH-1 / 2 / 3。

## 22.1 v0.2 已正确覆盖

```text
Agent Loop
Provider abstraction
Tool contract
Observation feedback
Reasoning surface
Workspace
File read/write concepts
Network fetch
Egress / SSRF
Approval
Approval modes
Sandbox
Workspace trust
Credential protection
Cancel
Background run
Session persistence/resume
Rewind/checkpoint
Compaction
Skills
MCP
Plugins
Hooks
Custom commands/tools
Subagents
Delegation
Workflow
Host UI
Observability
Token/cost
Context usage
AT boundary
```

## 22.2 v0.2 漏项，本轮已补入

```text
Policy tiering / managed policy
Argument / pattern matching
Cost / budget cap
Headless / non-interactive Host
Resource / File Undo
Prompt / Provider cache integration
Session share / export data
Config layering / precedence
Managed / Admin config
Server / SDK / API surface
Output style / persona
Formatter integration
File watcher
Image / multimodal attachment normalization
Inter-agent messaging
Context inheritance / isolation
Diagnostics / Doctor
```

## 22.3 v0.2 概念混淆，本轮已拆开

```text
Grant != Realm != Principal

Execution PLAN/BUILD != Task Planning

History in Core != State/Context second History

Workspace Trust State != Trust Policy

Provider Health signal != Observability health presentation

Output Bounding enforcement != Observability reporting

Search Strategy != Search Backend

Artifact reference != Artifact storage

Run Control capability family != physical Run Control layer
```

---

# 23. 能力性质总表

| Capability / Family | Nature |
|---|---|
| Agent Loop | Core semantic |
| Session / Run semantics | Core semantic |
| Active Run lifecycle | Runtime |
| Provider implementation | Provider |
| android_shell / network / file | Concrete Tool |
| Workspace | Operational Environment |
| Resource Grant | Operational Environment |
| Principal / Realm | Operational Environment + Authority input |
| PLAN / BUILD | Authority execution mode |
| Approval | Authority policy |
| Sandbox | Cross-boundary safety model |
| History persistence / compaction | State & Context |
| Memory | State & Context |
| Artifact / Run output ref | State & Context |
| Search | Composite capability |
| Research | Composite workflow capability |
| Download | Composite Tool capability |
| Human Interaction | Cross-boundary interaction primitive |
| Skills | Extension |
| MCP | Extension |
| Plugins / Hooks | Extension |
| Task Planning / Subagents | Orchestration |
| Evidence / Handoff / Remaining Work | Orchestration + State |
| Token / cost / health | Observability |
| AGA / AGC / Headless / Server / IDE | Host / Product |
| AT Engine | External Capability Backend |

---

# 24. 当前项目成熟度

```text
Semantic Agent Kernel
█████████░  基本完成

Provider Foundation
████████░░  基本完成

Host / AGA
████████░░  基础完成

Tool Layer
██████░░░░  shell + fetch 完成，file READ ready

State & Context
██░░░░░░░░  基础语义有，可靠状态管理未建设

Operational Environment
██░░░░░░░░  V0 root 已设计，完整模型未建设

Authority & Safety
███░░░░░░░  基础 Allow/Deny + SSRF 已有，细粒度策略未建设

Observability
███████░░░  当前较成熟

Extension
░░░░░░░░░░  Future

Orchestration
░░░░░░░░░░  Future
```

---

# 25. 当前真正需要专项 Research / Preflight 的问题

架构位置已经冻结，但下列具体语义仍需 Step：

```text
1. Cancel / Retract semantics
2. Provider retry / reconnect / partial-stream recovery
3. Context Compaction
4. Human Interaction representation
5. PLAN / BUILD + Approval policy details
6. Workspace / Principal / Realm / Sandbox operational model
7. Session Persistence / Resume storage model
8. Tool Output Bounding shared policy
9. Cost / token accounting + Run budget enforcement
```

这些问题不应再重新讨论“属于哪里”，只需研究“怎么实现”。

---

# 26. Roadmap 必须做的下一轮规范化

现有 Roadmap 的机制继续保留：

```text
Capability Registry
Dependency Graph
Status Model
Priority Model
Execution Queue
Deferred Registry
Intake
Update Protocol
```

但 `AG.ROADMAP-0.REVIEW-1` 应依据 v1.0 做以下修正：

```text
1. Domain / Owner 重新映射到本架构
2. Run Control owner 修正：
   mechanism -> Runtime
   semantics -> Core
   UI -> Host
3. 修复 TOOL-002 / AUTH-005 dependency cycle
4. 将 PUBLIC_NETWORK_ONLY 与 per-domain egress policy 拆开
5. NET-003 不再 hard-require NET-001
6. 拆分 Persistence / Resume
7. 拆分 Fork / Checkpoint
8. 新增 Resource/File Undo
9. STATE-002 Rewind 不再 hard-require Active Retract
10. 补 Run timeout / cost cap / background / headless / doom-loop 等 capability IDs
11. 补 Extension：
    MCP / Plugins / Hooks / Custom Commands / Custom Tools / Rules
12. 补 Orchestration：
    Planning / Task State / Progress / Evidence / Handoff /
    Failure-aware continuation / Inter-agent messaging
13. 补 Human Interaction capability ID
14. 补 Host：
    Stop / Retract / Mode / Approval / Session UI /
    Headless / Config precedence / Server-SDK 等必要 registry entry
15. 状态枚举保持纯净，不使用
    VERIFIED(PUBLIC_NETWORK_ONLY)
    OUT_OF_SCOPE(until Step)
    这类混合状态
16. 修正 P0 定义：
    P0 = target baseline foundational
    不等于“当前完全不可用”
17. Search 改为 composite capability；
    provider search 与 agent-side backend 为 alternatives，不是 hard chain
18. Resource model 使用 Grant / Realm / Principal 分离后的术语
19. File future realm 不承诺“model-facing contract 永不变化”
20. 重新计算 Dependency DAG 与 Execution Queue
```

Roadmap REVIEW-1 是 docs-only normalization，不应修改 production code。

---

# 27. v1.0 冻结规则

以下架构归位从本版本起视为冻结，除非出现新证据：

```text
Workspace
-> Operational Environment

Grant / Realm / Principal
-> Operational Environment
   with Authority consuming them

PLAN / BUILD
-> Authority & Safety

Task Planning
-> Orchestration

Sandbox
-> Environment + Authority + Tool/Backend + OS

Search
-> Composite Capability
   (Strategy + Backend)

Research
-> Search + Fetch + Reasoning + Evidence

Artifact / Run Output
-> State & Context reference
   + Workspace/File storage

Memory
-> State & Context

RAG
-> Composite Retrieval capability
   not yet a first-class subsystem

Human Interaction
-> Core semantic + Runtime continuation + Host UI

AT Engine
-> Tool-facing External Capability Backend

Provider
-> independent model communication implementation layer

Run Control
-> distributed capability family
   not a required physical layer

Observability
-> observes; does not own Tool enforcement
```

---

# 28. 非冻结项

为了避免“架构冻结 = 提前设计所有实现”，以下仍明确不冻结：

```text
Human Interaction 是 Tool 还是 ModelTurn 还是 Continuation
Search 是否最终拥有 first-class model-facing Tool
RAG 是否以后成为独立 Knowledge subsystem
Workspace 是否需要独立 production abstraction
Sandbox 是否需要额外 process / service
Persistence 的数据库 / 文件格式
Checkpoint 的存储实现
Compaction 算法
Provider retry 具体参数
Partial-stream recovery policy
Background Run 是否需要 ForegroundService
MCP / Plugin / Hook 内部框架
Subagent 协议
```

原则：

```text
Freeze responsibility before implementation.
Do not scaffold future internals.
```

---

# 29. 下一步

v1.0 完成后：

```text
CURRENT
Architecture Capability Audit
→ PASS / FROZEN

NEXT
AG.ROADMAP-0.REVIEW-1
→ docs-only Roadmap normalization

THEN
recompute queue

LIKELY FIRST BUILD
AG.FILE-0
```

`AG.FILE-0` 是否仍为第一生产 Step，以 REVIEW-1 重算后的 Roadmap Queue 为准；当前证据仍显示它是最成熟的 Implementation-Ready capability。

---

# 30. Source Documents

本架构综合使用：

```text
AG.RESEARCH-1_Mature_Agent_Operational_Foundation.md
AG.RESEARCH-2_Mature_Agent_Full_Capability_Inventory.md
AG.RESEARCH-3_Network_Capability_Deep_Dive.md
AndroidAgent_Master_Roadmap.md
AGENTS.md / latest frozen project state
AndroidAgent_Architecture_Capability_Map_v0.2.md
```

历史 Research / Step 文档保持 point-in-time snapshot，不回写。

---

# 31. Controller Verdict

```text
STEP                          = AG.ARCH-CAPABILITY-0
ARCHITECTURE_MAP              = PASS
CAPABILITY_COMPLETENESS       = PASS
OWNERSHIP_NORMALIZATION       = PASS
STRUCTURAL_BOUNDARY_DRIFT     = NO
NEW_PRODUCTION_ABSTRACTIONS   = NO
PRODUCTION_CODE_CHANGED       = NO
SUB_AGENTS_USED               = NO

ARCHITECTURE_VERSION          = v1.0
ARCHITECTURE_STATUS           = FROZEN

NEXT                          = AG.ROADMAP-0.REVIEW-1
STOP_BEFORE                   = AG.FILE-0 / any production implementation
```
