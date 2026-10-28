# AG.ARCH-DOC-AUDIT-0 — AndroidAgent 架构文档回顾 / SSOT / 漂移审计

```text
TYPE   = DOCUMENT DISCOVERY / ARCHITECTURE RETROSPECTIVE / SSOT CLASSIFICATION
         / HISTORICAL TRACE / ARCHITECTURE DRIFT AUDIT
REPO   = D:\codebank\AndCode\AndroidAgent-V2
DATE   = 2026-10-28
HEAD   = edfc603eadb0067bd010c04ca26b4bc28de2e685  (main)
BRANCH = main
MODE   = READ-ONLY (仅新增本审计文档 + evidence；零代码/零 Gradle/零 DB 改动)
VERDICT = PASS_WITH_DRIFT (4 × LOW，无 MEDIUM/HIGH/ARCH_BLOCKER)
EVIDENCE = docs/evidence/ag_arch_doc_audit_0/ (00-16)
```

本轮回答四个问题：

```text
1. 一路走到现在，AndroidAgent 的架构到底演化成什么样？
2. 哪些旧设计仍然成立？
3. 哪些已经被 V2 / TopicSession / AT integration / UI 演进替代？
4. 现行代码与我们冻结过的架构是否仍然一致？
```

---

## 1. Git 基线

```text
BRANCH   = main
HEAD     = edfc603eadb0067bd010c04ca26b4bc28de2e685
WORKTREE = 2 untracked（上一轮 closeout 文档，非架构内容）
REMOTE   = none（PUSH = N/A）
```

关键 tag 时间锚点：`ag-0` → `ag-provider-0` → `ag-aga-0` → `ag-stable-1` →
`ag-engine-0` → `AG.V1-KNOWN-GOOD-FREEZE-V1`(13df1db) →
`AG.V2-BASELINE-V1`(c84bf5d) → `AG.POST-I-CORE-FREEZE-V1`(f640ed0) → HEAD。
（完整清单见 evidence 00。）

---

## 2. 文档发现（§3 任务书）

全仓库扫描 `docs/`、根目录、`upload/`、`tools/`，排除 build 产物，
约 **100 个** .md/.txt。完整清单（含大小与乱码文件名说明）见
evidence `01_document_inventory.txt`。

要点：

```text
- 中文文件名（如 AndroidAgent_总体架构.md）在 PowerShell 下显示乱码，
  实际字节为合法 UTF-8，文件完整。
- 3 对 "原始 + _修正1" 副本（TopicSession FREEZE-0 / DURABILITY-PREFLIGHT-0 /
  TOPIC-SESSION-PERSISTENCE-0_FINAL）。SSOT 文本引用的是无后缀文件名；
  副本属 SUPERSEDED。
- docs/v2-research/pre-migration-workspace/ = 迁移前 V1 时代工作区快照
  （13 文件），保留 provenance 用，非现行 SSOT。
- docs/evidence/ 7 个 step evidence 目录 = 实现证据，非架构 SSOT。
```

---

## 3. 文档分类（九态模型）

完整逐文档分类见 evidence `02_ssot_classification.txt`。汇总：

| 状态 | 数量 | 代表文档 |
|------|------|----------|
| LIVE_SSOT | 4 | AGENTS.md · Master_Roadmap · AT_TO_AG_HANDOFF_CURRENT · TOPIC-SESSION-PERSISTENCE-0_FINAL |
| FROZEN_BASELINE | 8 | Capability_Map_v1.0 · POST-I freeze · TopicSession FREEZE-0 · Durability PREFLIGHT-0 · 总体架构 · BASELINE-FREEZE-0 · ENGINE-BOUNDARY-FREEZE · V1-KNOWN-GOOD-FREEZE |
| CURRENT_SUPPORTING | 2 | VNext UI 设计基线（user-owned）· TRANSCRIPT-CARDS-1 doc+closeout |
| HISTORICAL_POINT_IN_TIME | ~25 | 全部 step docs · 7 份 audit · migration/regression/handoff |
| RESEARCH_ONLY | 3 组 | MATURE-AGENT-SESSION-RESEARCH · SESSION-PERSISTENCE-RESEARCH-0 · v2-research 工作区 |
| SUPERSEDED | 6 | 3 份 _修正 副本 · AT V0/V3 bundle · SHELL-0 design · V2-2 integration doc |
| IMPLEMENTATION_EVIDENCE | 7 目录 | docs/evidence/** · upload/ |
| FUTURE_DESIGN | 2 清单 | POST-I §13 deferred 列表 · TopicSession FREEZE-0 §4 POST-V1 列表 |
| UNKNOWN | 0 | — |

---

## 4. SSOT 层级（当前真实规则）

AGENTS.md "Source of truth (three roles, never merged)" 与 Roadmap §2
（四角色）一致且现行有效，实际层级为：

```text
Level 1 — CURRENT TRUTH
  AGENTS.md                          WHAT IS TRUE NOW（持续归一化，b7dbc04）

Level 2 — FROZEN ARCHITECTURE
  Capability_Map_v1.0                WHERE CAPABILITIES BELONG（FROZEN，roadmap 上游）
  POST-I freeze + TopicSession freeze 层/依赖/ownership/persistence 冻结

Level 3 — CURRENT PLAN / DESIGN
  Master_Roadmap                     WHERE/WHEN GOING（LIVE）
  VNext UI 设计基线                    UI 世代目标（user-owned，只读）
  AT_TO_AG_HANDOFF_CURRENT           AT 快照唯一指针（LIVE）

Level 4 — STEP EVIDENCE
  docs/implementation/* + docs/evidence/*   POINT-IN-TIME，永不回改

Level 5 — HISTORICAL RESEARCH
  docs/research/* + docs/v2-research/*      仅供历史
```

规则核验：

```text
✓ 四角色未合并：当前状态在 AGENTS.md；能力归位在 v1.0；计划在 Roadmap；
  step 证据在 step doc。
✓ Roadmap 明确 "obeys v1.0, never reopens"。
✓ 冻结文档 "never edited to match the roadmap"（改动记入 Roadmap Update Trail）。
✓ V2 后期未推翻该体系（UI 世代未引入新的 SSOT 层级；VNext 设计基线是
  UI 目标而非架构 SSOT）。
⚠ 唯一缺口：AGENTS.md 没有 "UI step registry" 区域，SHELL-1 /
  TRANSCRIPT-CARDS-1 未登记（closeout 判定 NO UPDATE NEEDED；
  建议下次 SSOT 归一化时补一条，见 §12 D3）。
```

---

## 5. 架构演进时间线

完整四阶段时间线（每阶段 what changed / invariant / replaced assumption /
frozen boundary）见 evidence `03_architecture_timeline.txt`。压缩版：

```text
PHASE 0 — V1 时代（AndroidAgent，现 FROZEN_KNOWN_GOOD）
  AG Core v0 → Provider v0 → AGA host → Engine 边界 → SHELL-1(AT v0)
  → Run Control(Interrupt) → HOST-STOP → V1 FREEZE (Strategy B: V2 独立树)

PHASE 1 — V2 迁移
  直接 clone V1 freeze 为 V2 baseline → AT V3 consumer(:integration-at)
  → session continuity

PHASE 2 — TopicSession 持久化（架构最大增量）
  术语冻结(Session/Run/ModelCall) → 层级归属冻结 → 持久化 A-I 阶段
  → Room/SQLite 成为 durable SSOT → TranscriptModel 降级为投影

PHASE 3 — Post-I 冻结 + AT V4
  PRE-UI 架构冻结(当前非 UI 层 canonical freeze) → AT V4 vendored AAR
  (取代 V3；V0/V3 标记 SUPERSEDED)

PHASE 4 — VNext UI（当前）
  UI 基线审计 → Shell + Provider 设置 → Transcript Cards + Session Browser
  (additive Runtime seams; DB schema 未动)
```

**贯穿全部阶段的不变量**（每阶段都被重新验证）：

```text
AG = Intelligence · AT = Execution
ag-core: 无 Android / 无 AT / 无 concrete impl / 无 Room / 无 HTTP
ag-runtime: 无 Android / 无 AT / 无 concrete tool/provider
AT 消费只经 :integration-at
一个能力 = 一个 tool module；无 Tool 框架
Host 保持轻薄：不拥有 Agent 语义
```

**被替换的假设**：Core 零生产依赖 → coroutines；无持久化 → Room durable
SSOT；TranscriptModel 持有 transcript → 仅投影；AT V0 → V3 → V4；
单 TextView transcript → RecyclerView cards（仅 UI 世代变化）。

---

## 6. 基本架构不变量核验（§8 任务书）

以**当前 Gradle 依赖 + import 扫描 + 源码布局**验证（非引用旧文档）：

```text
✓ ag-core  forbidden imports (android/androidx/com.at/com.adb/
  com.androidterminal/com.androidagent.{app,runtime,tool})  = 0 匹配
✓ ag-runtime 同上                                          = 0 匹配
✓ AT AAR 仅被 :integration-at 引用（依赖图实测）
✓ ag-core -> Android / AT / concrete Tool                  = 不存在
✓ ag-runtime -> concrete Tool / provider / AT              = 不存在
✓ UI -> DAO transcript 直连路径                            = 不存在
  （TranscriptModel 无任何 persistence import；Room 引用只出现在
  ChatCompositionRoot/SessionHolder 的 Host 装配层，属 Host 合法所有权）
✓ UI -> AT 实现                                            = 不存在
  （唯一例外 = M1 已跟踪 debt，见 §12 D4/M1）
```

结论：`AG = Intelligence / AT = Execution` 与全部依赖方向禁令**仍然成立**。

---

## 7. 当前物理架构（§9 任务书）

实测 9 个 Gradle module（完整依赖表见 evidence `04_module_dependency_map.txt`）：

```text
:ag-core                       pure JVM · Agent Loop/Session/Run/contracts
:ag-runtime                    pure JVM · 生命周期/会话编排/持久化契约
:provider-openai-compatible    pure JVM · OpenAI 协议 + transport 契约
:provider-openai-compatible-jdk pure JVM · JDK HTTP transport
:tool-android-shell            Android lib · shell tool（经 :integration-at）
:tool-network                  Android lib · fetch（无 AT）
:tool-file                     Android lib · read/search/list/stat（无 AT）
:integration-at                Android lib · 唯一 AT 消费点（AT V4 AAR）
:app                           Android app · AGA Host / Composition Root /
                                Room 持久化实现 / UI / Debug
```

当前架构 ASCII 图（code dependency + runtime call flow 分列）与五条
数据/控制流见 evidence `14_current_architecture.txt` / `15_current_flows.txt`。
核心形状：

```text
                    :app (Host / Composition Root)
      ┌────────┬─────────┼─────────┬──────────┐
      ▼        ▼         ▼         ▼          ▼
 :ag-runtime :ag-core :provider- :tool-*   :integration-at
      │           ▲▲▲▲▲▲▲▲▲        │          │
      └───────────┘(实现 Core 契约)  │          ▼
                        │     (shell 经 api)  AT V4 AARs (vendored)

runtime call:  UI -> HostActions -> Runtime -> Core -> Provider / Tools
durable:       Core --MessageDurability--> Room (Host 实现) = durable SSOT
projection:    Core history + events -> TranscriptModel -> Cards
```

---

## 8. AG Core Review（§10 任务书）

**当前 Core 拥有**（11 个生产文件，`com.androidagent.core`）：

```text
Agent Loop (driveLoop) · Session/Run 语义 · Run exclusivity
Message 模型 (User/Model/ToolResult; ToolCall 内嵌于 ModelMessage)
ModelProvider contract + ModelTurn · Tool contract + ToolRegistry
ToolApproval (allow/deny) · AgentEvent 流 · RunOutcome
MessageDurability contract (storage-agnostic; D1/D2/D3 提交点)
Interrupt 调和语义（synthetic ToolResult 配对不变量）
```

**Core 不知道**：Room/SQLite/DAO、Android、AT、provider wire 格式、
HTTP/JSON、API key、数据库路径（import 扫描 0 匹配，编译期强制）。

**职责迁出/迁入历史**：

```text
迁入 Core（Phase 2/Run-Control，均有 step 授权）：
  + MessageDurability 契约（Phase A）
  + restoreSession()/restoreHistory（Phase）
  + interrupt 调和 + RunFailure.DurabilityFailure
  + coroutines-core 生产依赖（RUN-CONTROL-1 FIX，最小 NonCancellable 需求）
迁出 Core：无。历史上 Core 的职责只增未减，且每次增量都在
  "host-neutral intelligence/domain" 冻结定义内。
```

---

## 9. Agent Runtime Review（§11 任务书）

**当前 Runtime 职责**（4 个生产文件）：

```text
process-scoped Agent 生命周期 · TopicSession 启动 (restore-or-create)
当前 session 选择 · 单 active-Run Job 所有权 · interruptActiveRun()
durable 写编排（经 TopicSessionStore）· dangling repair 编排
Core hydration · currentHistory() 只读 seam
持久化契约：TopicSessionStore / Records / MessageSerializer (storage-neutral)
UI 时代新增（additive）：listSessions() / switchSession()
```

**God Object 检查**（基于职责清单而非文件大小）：

```text
判定 = 非 God Object。
理由：AgentRuntime 的全部职责同属一个领域（process-scoped session/run
编排）。UI 时代新增的 list/switch 是查询/切换 seam，session 选择本就是
Runtime 冻结所有权（POST-I §4 "current session selection"），未跨层移动
任何 ownership。WATCH 条件：若未来 Rewind 语义直接堆进 AgentRuntime
而不经过新的 preflight/契约设计，则需重新评估。
```

---

## 10. Session / Run 回顾（§12 任务书 — Rewind 研究输入）

演进：`Session`(ag-0, 内存) → `TopicSession`(命名+持久化冻结) →
durable Room SSOT → UI 时代 list/switch。

**当前 source of truth 分层**（详细见 evidence `06_session_run_persistence.txt`）：

```text
TopicSession
    ↓ (durable)
Room/SQLite messages (seq 0..N-1)          ← CURRENT DURABLE SSOT
    ↓ (restore: 0 writes / 0 model / 0 tool)
Runtime hydrate + dangling repair
    ↓
AG Core Session (live history)             ← CURRENT LIVE DOMAIN SSOT
    ↓ (currentHistory 只读 + AgentEvent)
TranscriptModel (projection, REPLACE/idempotent)
    ↓
Transcript Cards (UI)                       ← derived state, NOT SSOT
```

恢复/修复冻结不变量：restore 零持久化写、零 model 调用、零 tool 执行；
dangling ToolCall = 受控修复，`DANGLING_AUTO_REPLAY = NO`（跨进程未知
结果 = UNKNOWN）；中断 in-flight tool = `IN_FLIGHT_INTERRUPTED`（诚实标记）。

---

## 11. Persistence / Tool / Provider / AT / Host 分域 Review

### 11.1 Persistence（§13）

```text
定性：分层契约链，不是独立 subsystem。
  Core 契约    = MessageDurability (storage-agnostic)
  Runtime 契约 = TopicSessionStore / Records / MessageSerializer
  Host 实现    = Room/SQLite (entities/DAO/Database/RoomTopicSessionStore/
                 StoreMessageDurability)
已冻结：引擎 SQLite · 访问层 Room · 位置 app-private · DB v1 ·
  seq owner = persistence · D1/D2/D3 提交点 · no-auto-replay。
extension seam（未冻结）：schema 迁移路径（reopen 条件 #2/#3）·
  retention policy · compaction · cloud sync · 多 session UI 深化。
UI 时代对持久化零侵入：DB 仍 v1，仅加 list 查询。
```

### 11.2 Tool（§14）

```text
契约 owner = AG Core（Tool/ToolSpec/ToolCall/ToolResult/Registry）。
实现全部 Core 外，一个能力一个 module（shell/network/file）。
无 :tool-layer 中央模块 / ToolManager / Backend / Factory（反抽象清单
  实测不存在）。
"并非所有 Android 能力都必须经 AT" = 成立（network/file 即直接
  Android API 路径的实证）。
UI 时代无新 tool；ToolResult 语义不变。
```

### 11.3 Provider（§15）

```text
契约 owner = AG Core (ModelProvider/ModelTurn)。
实现 owner = :provider-openai-compatible（唯一 concrete provider）。
transport = jdk 模块（JVM 测试）+ :app AndroidOpenAiHttpTransport（设备）。
Host 配置 owner = :app（DeviceLocalProviderConfig + UI 时代
  ProviderProfile/ModelCatalog/ModelDiscovery，全部 :app 内）。
反向污染检查 = 无：ag-core/ag-runtime/provider-* 对 app 新类型的
  import = 0。
EMG/OpenRouter = provider node（端点配置），不是类。
```

### 11.4 AT / AG 边界（§16）

```text
AT Engine = AndroidTerminal 执行引擎（外部工程：ADB transport、shell/PTY、
  identity/pairing）。AG Engine = 本项目的 Core + Runtime。
连接点 = :integration-at（唯一 AT AAR 消费模块）；:tool-android-shell 是
  其上的 thin one-shot consumer。
当前快照 = AT.V4-CONSUMER-FREEZE-V1 (7216d0e)，vendored AAR，debug variant
  （release 未验证；NEXT sync = POST_V4_RELEASE_VALIDATION）。
model-visible AT 能力 = ONE_SHOT only。
AT 对 TopicSession 一无所知 = 成立。
"AT = external execution subsystem/backend" 定性 = 仍然成立。
M1 debt：:app 三个 host 文件直接 import AT composition 类型
  （ChatCompositionRoot / CompositionAdbSemantic / DebugBridge）——
  POST-I freeze 已列为 DEFERRED NON-BLOCKING，LOW。
```

### 11.5 Host / UI（§17）

```text
分层归属（24 个 :app 生产文件逐一分类，见 evidence 08）：
  Composition Root : ChatCompositionRoot (+ M1 AT imports)
  Process holder   : SessionHolder（非持久存储）
  Runtime bridge   : HostActions（唯一会话控制门面，secret-free）
  Presentation     : ChatActivity / TranscriptModel / TranscriptCardAdapter
  Config/catalog   : ProviderProfile / ModelCatalog / ModelDiscovery /
                     DeviceLocalProviderConfig / SafResource
  Debug            : DebugBridge / DebugProtocol（debug builds only）
  Persistence      : Room 五件套
  Trace            : AgentTrace* 三件套
过载评估（只报告，不重构）：
  ChatActivity = WATCH（UI 两步后变大，仍属 Host 层，动作全部委托
  HostActions，无 Engine 语义所有权）。
  SessionHolder = OK。HostActions = HEALTHY。
  DUPLICATE_OWNER = NO。DUPLICATE_PIPELINE = NO。
```
---

## 12. 漂移矩阵（§18 任务书）

完整矩阵见 evidence `09_drift_matrix.txt` / 矛盾矩阵 `10_contradiction_matrix.txt`。

| # | 类型 | 严重度 | DOC says | CODE/现状 does | 裁定 |
|---|------|--------|----------|----------------|------|
| D1 | DEPENDENCY_DRIFT | **LOW** | 总体架构 §2/§10 + AGENTS.md 行："AG Core 无任何生产依赖，仅 Kotlin stdlib；coroutines 仅测试" | `:ag-core` 生产依赖 `kotlinx-coroutines-core:1.10.2` | 代码正确（RUN-CONTROL-1 FIX step 授权，纯 JVM 无泄漏）；ag-0 时代文档 + AGENTS.md 一行未归一化。待下次 SSOT 归一化修正 |
| D2 | DEPENDENCY_DRIFT | **LOW** | 总体架构 §10：":ag-runtime 不引入 JSON" | `:ag-runtime` 有 `kotlinx-serialization-json:1.8.0`（MessageSerializer）+ coroutines | 代码正确（TopicSession Phase D 授权）；旧文档快照早于 TopicSession。POST-I freeze 已反映新状态 |
| D3 | SCOPE_DRIFT | **LOW** | POST-I freeze 为 PRE-UI 冻结，未提及 UI 层 | UI 世代（shell + cards）已存在 | 预期内：freeze §13 明确将 UI 设计列为 DEFERRED。freeze 对非 UI 层仍 authoritative。AGENTS.md 无 UI step registry 条目 = watch item |
| D4 | SUPERSEDED_BUT_UNMARKED | **LOW** | AGENTS.md "Current integration snapshot" 块：AT.AG-CONSUMER-FREEZE-V1 / c9cee21 / DRAFT | 实际 AT = V4 (7216d0e) / FROZEN | 指针机制有效（AGENTS.md 自身声明以 handoff 文件为准），但块内数值陈旧，有误读风险。待归一化 |

**未发现的漂移类型**：OWNERSHIP_DRIFT = 无；DUPLICATE_OWNER = 无；
DUPLICATE_PIPELINE = 无；DEAD_FUTURE_DESIGN = 无；HIGH/ARCH_BLOCKER = 无。

**总判定**：代码与**较新**冻结文档（POST-I freeze / TopicSession freeze /
V4 handoff）在全部受检边界上一致；漂移全部集中在 **ag-0 时代总体架构文档
+ AGENTS.md 两处陈旧文本**，属文档滞后而非代码越界。

---

## 13. 术语审计（§20 任务书）

canonical 词表、已废弃/易混淆术语清单见 evidence `11_terminology.txt`。
要点：

```text
冻结三层控制词表（CONVERSATION-CONTROL-0）：UI=Stop · AG=Interrupt ·
Runtime=cancellation。Retry=Retry-as-New-Turn（非 Redo）。
Redo=DEFERRED · Rewind/Fork/Checkpoint=NOT IMPLEMENTED。
"AT Core" vs "AT Engine" = 同一系统两个时代的名字（仅术语漂移）。
易混淆：AGC(CLI 前端) ≠ AG Core；"Session" 需看文档时代（ag-0=内存，
post-freeze=TopicSession）；"EMGProvider" 不存在。
```

---

## 14. 值得重读的架构文档（§21 任务书）

完整 8 份推荐 + 忽略说明见 evidence `12_recommended_reread_set.txt`；
"不得作为 SSOT" 清单见 `13_do_not_use_as_ssot.txt`。

```text
1. AGENTS.md                        — 唯一 LIVE "what is true now"（注意 §12
                                        D1/D4 两处陈旧文本）
2. docs/freeze/AG.POST-I-ARCHITECTURE-FREEZE-V1.md
                                     — 当前最完整架构冻结（非 UI 层 canonical）
3. docs/AndroidAgent_Architecture_Capability_Map_v1.0.md
                                     — 能力归位 FROZEN（roadmap 上游）
4. docs/roadmap/AndroidAgent_Master_Roadmap.md
                                     — LIVE 计划（registry/DAG/queue）
5. docs/integration/AT_TO_AG_HANDOFF_CURRENT.md
                                     — AT 快照唯一指针（V4）
6. docs/architecture/AG.TOPIC-SESSION-ARCHITECTURE-FREEZE-0.md
                                     — 会话/运行/持久化语义（Rewind 基础）
7. docs/implementation/AG.TOPIC-SESSION-PERSISTENCE-IMPLEMENTATION-0_FINAL.md
                                     — 持久化实现 SSOT（D1/D2/D3 + 修复规则）
8. docs/architecture/AndroidAgent_总体架构.md
                                     — 基础心智模型（依赖声明部分已过时）
```

**最容易被误当现行架构的文档**（READ FOR HISTORY ONLY）：

```text
- v2-research/pre-migration-workspace/**（V1 时代研究区）
- docs/audit/* 全部 7 份（各自锚定历史 baseline HEAD）
- AG.SHELL-0_INTEGRATION_DESIGN.md（已被 AG.SHELL-1 + V4 取代）
- docs/migration/*、docs/regression/*（已关闭的历史记录）
- 3 份 _修正 副本（SSOT 文本引用的是无后缀原件）
- AT V0/V3 vendored bundle（SUPERSEDED/INACTIVE）
```

---

## 15. 当前架构重建 + 健康评估（§23/§25 任务书）

当前架构 ASCII 图、依赖图、五条数据/控制流见 evidence
`14_current_architecture.txt` / `15_current_flows.txt`。

```text
A. Normal User Send    Host->HostActions->Runtime.send->Core loop
                       (D1/D2/D3 durable commits) -> RunOutcome.Answer
B. Model->Tool->Model  Core dispatch via ToolRegistry; tool modules execute
                       (shell 经 :integration-at->AT；network/file 直接 Android API)
C. Stop/Interrupt      Host Stop -> Runtime.interruptActiveRun() (atomic claim
                       + Job cancel) -> Core finalizes Interrupted + synthetic
                       ToolResult -> RunFinished(Interrupted) 恰好一次
D. Persist/Resume/Switch  persist=A 的 D1/D2/D3; resume=initialize()
                       restore-or-create (0 write/0 model/0 tool);
                       switch=switchSession() load+repair+hydrate (additive)
E. UI Transcript       AgentEvent + currentHistory -> TranscriptModel
                       (projection only) -> TranscriptCardAdapter -> Cards
```

**健康评估**：

| 域 | 状态 | 理由 |
|----|------|------|
| AG Core | HEALTHY | 0 forbidden imports；coroutines 依赖为 step 授权最小集；仅文档漂移 |
| Agent Runtime | HEALTHY | 单领域编排，非 God Object；UI 时代 seam 在冻结所有权内 |
| Provider | HEALTHY | 一契约一实现两 transport；UI 时代 catalog 类型正确留在 Host |
| Tool Layer | HEALTHY | 一能力一模块；无框架；AT 隔离验证通过 |
| Persistence | HEALTHY | 冻结契约链完整；DB v1 未被动；restore 不变量真机验证 |
| AT Integration | HEALTHY | V4 vendored AAR；单一消费点；M1 host-import debt 已跟踪 (LOW) |
| Host / UI | WATCH | ChatActivity 增大（无违规）；HostActions 门面干净；单管线验证 |
| Documentation | **DRIFT (LOW)** | 4 项 LOW（§12）；建议 Controller Review 后做一次小型 SSOT 归一化 |

---

## 16. Rewind 就绪度 — 仅影响面图（§26 任务书）

完整 impact map 见 evidence `16_rewind_impact_map.txt`。未来 Rewind 最可能
触碰的**已冻结边界**（按耦合度排序）：

```text
1. TopicSession durable history (Room DB v1)     — 最高耦合
   seq 连续/owner=persistence/durable SSOT 三条冻结同时受影响；
   仅能经 POST-I §14 冻结重开条件（schema 变更 / 新 durable 域需求）进入。
2. Core Session + 配对不变量（每 ToolCall 必有 ToolResult）
3. Runtime restore/hydrate + "current head" 概念（logical head vs durable tail）
4. MessageDurability 契约（当前 append-only commit）
5. Transcript projection + UI（rewind 决策必须在 Runtime/Core，禁止显示层截断）
6. Run/Interrupt 交互（rewind 到已中断 tool 之前 = 最锋利语义边缘，
   继承 DANGLING_AUTO_REPLAY=NO 的 UNKNOWN-outcome 约束）
7. Host actions + debug bridge（新 seam 必须走同一管线）

任何 rewind 设计若隐含"撤销外部副作用"，违反已冻结的 NOT_PROMISED 立场。
本轮未做 Rewind 设计，未做实现。
```

---

## 17. 不变量遵守声明（§28 任务书）

```text
PRODUCTION_CODE_CHANGED = NO
TEST_CODE_CHANGED       = NO
GRADLE_CHANGED          = NO
DB_CHANGED              = NO
HISTORICAL_DOC_MODIFIED = NO（未修改任何历史/冻结文档）
NEW_FILES               = 本审计文档 + 17 份 evidence（仅新增）
COMMIT = NO · TAG = NO · PUSH = NO
```

---

## 18. Final Report（§31 任务书）

```text
AG.ARCH-DOC-AUDIT-0 FINAL REPORT

VERDICT =
PASS_WITH_DRIFT

BRANCH =
main

HEAD =
edfc603eadb0067bd010c04ca26b4bc28de2e685

WORKTREE_BEFORE =
DIRTY (2 untracked closeout docs, non-architectural)

WORKTREE_AFTER =
DIRTY (same 2 untracked + new audit doc + evidence dir, all untracked)

DOCUMENTS_SCANNED =
~100 (.md/.txt, excluding build artifacts)

ARCHITECTURE_DOCUMENTS_FOUND =
24 core architecture docs + ~20 step/audit docs

LIVE_SSOT_COUNT =
4 (AGENTS.md / Master_Roadmap / AT_TO_AG_HANDOFF_CURRENT /
   TOPIC-SESSION-PERSISTENCE-0_FINAL)

FROZEN_BASELINE_COUNT =
8 (Capability_Map_v1.0 / POST-I freeze / TopicSession FREEZE-0 /
   Durability PREFLIGHT-0 / 总体架构 / BASELINE-FREEZE-0 /
   ENGINE-BOUNDARY-FREEZE / V1-KNOWN-GOOD-FREEZE)

HISTORICAL_COUNT =
~25 (step docs + 7 audits + migration + regression + pre-migration workspace)

SUPERSEDED_COUNT =
6 (3 修正 copies / AT V0+V3 bundles / SHELL-0 design /
   V2-2 integration doc / old handoff copy)

UNKNOWN_COUNT =
0

CURRENT_TOP_LEVEL_ARCHITECTURE =
AndroidAgent = AG Engine (AG Core + Agent Runtime) + Tool Layer
(one module per capability) + Host/Product (:app);
AT consumed only via :integration-at (V4 vendored AAR);
Room/SQLite durable SSOT; TranscriptModel = presentation projection only.

CURRENT_SSOT_HIERARCHY =
L1 AGENTS.md (truth now) > L2 frozen architecture (Capability_Map v1.0 +
POST-I/TopicSession freezes) > L3 live plan/design (Roadmap + VNext design
baseline + AT handoff pointer) > L4 step evidence > L5 historical research

CURRENT_AG_CORE_OWNER_MODEL =
Agent Loop / Session+Run semantics / Message model / ModelProvider+Tool
contracts / ToolRegistry / policy / events / RunOutcome /
MessageDurability contract / interrupt reconciliation. Pure JVM, 0
forbidden imports. Minimal coroutines dep (step-authorized).

CURRENT_RUNTIME_OWNER_MODEL =
process-scoped lifecycle / TopicSession startup (restore-or-create) /
session selection / single-active-Run Job / interruptActiveRun /
durable orchestration / repair orchestration / hydration /
persistence contracts / additive listSessions+switchSession.
Not a God Object (single domain).

CURRENT_SESSION_MODEL =
TopicSession = durable conversation (Room SSOT); Run = transient;
max one active Run per session; newSession != delete;
switch = additive load+repair+hydrate.

CURRENT_PERSISTENCE_MODEL =
layered contract chain: Core MessageDurability -> Runtime
TopicSessionStore/Records/Serializer -> Host Room impl (DB v1);
restore = DB->Runtime/Core->Presentation, one pipeline;
no-auto-replay; seq owner = persistence.

CURRENT_TOOL_MODEL =
Core owns contract+registry; one module per capability
(shell/network/file); no central framework; Host registers;
ToolResult.success = mechanism, not domain.

CURRENT_PROVIDER_MODEL =
Core owns contract; one concrete provider (openai-compatible) +
JDK/Android transports; Host owns config/catalog
(ProviderProfile/ModelCatalog/ModelDiscovery); no reverse pollution.

CURRENT_AT_AG_BOUNDARY =
AG=Intelligence, AT=Execution; single consumption point
(:integration-at on AT V4 AAR 7216d0e); model-visible = one-shot only;
Core/Runtime 0 AT imports; M1 host-import debt tracked (LOW).

CURRENT_HOST_UI_MODEL =
:app = Composition Root + SessionHolder + HostActions (single facade) +
presentation (ChatActivity/TranscriptModel/Cards) + Room impl +
debug bridge + trace; ChatActivity = WATCH (size only);
no duplicate owner / no duplicate pipeline.

ARCHITECTURE_DRIFT_FOUND =
YES

HIGH_SEVERITY_DRIFT =
NONE

MEDIUM_SEVERITY_DRIFT =
NONE

LOW_SEVERITY_DRIFT =
D1 ag-core production dep (coroutines) vs ag-0 doc "no production deps"
   (+ one stale AGENTS.md line)
D2 ag-runtime JSON dep vs ag-0 doc "no JSON in runtime"
D3 POST-I freeze is pre-UI; UI generation exists (expected; deferred list)
D4 AGENTS.md AT snapshot block stale (V3/c9cee21/DRAFT vs actual V4)
   (pointer mechanism still authoritative)

DOCUMENT_CONTRADICTIONS =
4 (C1/C2/C8 = D1/D2 dependency text; C3 = D4 AT snapshot)
  + resolved-historical (C5 TranscriptModel role, C6 Session naming,
  C7 Retry/Redo/Rewind vocabulary = consistent)

TERMINOLOGY_DRIFT =
LOW ("AT Core" vs "AT Engine" same system; "Session" era-dependent;
  AGC vs AG Core conflation risk; EMGProvider does not exist)

DUPLICATE_OWNER_FOUND =
NO

DUPLICATE_PIPELINE_FOUND =
NO

CODE_VS_DOC_DRIFT =
code matches NEWER frozen docs on all checked boundaries;
drift confined to ag-0-era general architecture doc + 2 stale
AGENTS.md text blocks (documentation lag, not code violation)

TOP_DOCUMENTS_TO_REREAD =
1. AGENTS.md
2. docs/freeze/AG.POST-I-ARCHITECTURE-FREEZE-V1.md
3. docs/AndroidAgent_Architecture_Capability_Map_v1.0.md
4. docs/roadmap/AndroidAgent_Master_Roadmap.md
5. docs/integration/AT_TO_AG_HANDOFF_CURRENT.md
6. docs/architecture/AG.TOPIC-SESSION-ARCHITECTURE-FREEZE-0.md
7. docs/implementation/AG.TOPIC-SESSION-PERSISTENCE-IMPLEMENTATION-0_FINAL.md
8. docs/architecture/AndroidAgent_总体架构.md (partial authority)

SUPERSEDED_DOCS_NOT_TO_USE_AS_SSOT =
v2-research/pre-migration-workspace/** · docs/audit/* (7) ·
AG.SHELL-0_INTEGRATION_DESIGN.md · docs/migration/* ·
docs/regression/AG.ADB-LKG-REGRESSION.md · 3 _修正 copies ·
AT V0/V3 bundles · docs/handoff/AG_POST_I_CLOSURE_HANDOFF.md ·
docs/integration/AG.V2-2.AT-INTEGRATION.md

CURRENT_ARCHITECTURE_HEALTH =
AG Core HEALTHY / Runtime HEALTHY / Provider HEALTHY / Tool HEALTHY /
Persistence HEALTHY / AT Integration HEALTHY / Host-UI WATCH /
Documentation DRIFT(LOW)

REWIND_IMPACT_AREAS =
1. Room durable history (DB v1, seq, SSOT)  [highest]
2. Core Session pairing invariant
3. Runtime restore/hydrate + current-head concept
4. MessageDurability append-only contract
5. Transcript projection boundary (decision must not live in UI)
6. Run/Interrupt x rewind (UNKNOWN external outcome)
7. Host actions + debug bridge (single pipeline)

PRODUCTION_CODE_CHANGED =
NO

TEST_CODE_CHANGED =
NO

GRADLE_CHANGED =
NO

COMMIT =
NO

TAG =
NO

PUSH =
NO

NEXT =
CONTROLLER_REVIEW

STOP =
YES
```

---

## 19. Controller Review Addendum (AG.ARCH-DOC-AUDIT-0.NORMALIZE-1)

```text
ORIGINAL_VERDICT          = PASS_WITH_DRIFT
CONTROLLER_REVIEW         = ACCEPTED_WITH_ADDITIONAL_DRIFT
ARCHITECTURE_CODE_HEALTH  = PASS (unchanged; no architecture blocker)
```

Corrections recorded by the Controller and resolved in NORMALIZE-1:

```text
C0. Audit HEAD SHA transcription typo.
    REPORTED_SHA = edfc603eadb0067bd010c04ca26b4bc28de285
    VERIFIED_SHA = edfc603eadb0067bd010c04ca26b4bc28de2e685
    (git rev-parse HEAD is authoritative; the typo was corrected in this
    document during NORMALIZE-1. Real git output in evidence files was
    never altered.)

D5. LIVE_ROADMAP_STATUS_DRIFT (NEW — missed by the original audit).
    The Master Roadmap still listed STATE-003 Session Persistence and
    STATE-006 Session Resume as DISCOVERED ("Session in-memory only;
    storage model NOT frozen") and HOST-010 Session list/browser as
    DISCOVERED / "not started", while TopicSession persistence, resume,
    and the session browser are implemented and real-device verified.
    SEVERITY = MEDIUM (scheduling risk: stale dependency/status data can
    produce an incorrect execution queue; NOT an architecture-code
    violation).
    RESOLVED in NORMALIZE-1: STATE-003 = VERIFIED, STATE-006 = VERIFIED,
    HOST-010 = VERIFIED, coverage matrix + Completed Baseline synced,
    execution queue suspended pending Controller decision.
```

Post-normalization drift classification:

```text
D1 = RESOLVED_IN_LIVE_SSOT   (AGENTS.md dependency text corrected)
D2 = RESOLVED_IN_LIVE_SSOT   (AGENTS.md Runtime dependency text corrected)
D3 = NOT_AN_ERROR            (POST-I freeze is PRE-UI by design; remains
                               authoritative for non-UI architecture)
D4 = RESOLVED                (AGENTS.md AT snapshot now points to the V4
                               canonical handoff values)
D5 = RESOLVED                (Roadmap live statuses normalized)

Historical ag-0 statements (总体架构: "Core only stdlib", "Runtime no
JSON") = PRESERVED_AS_HISTORICAL (point-in-time freeze; superseded by
authorized later Steps; not rewritten).
