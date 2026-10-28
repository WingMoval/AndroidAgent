# AndroidAgent V2 — VNext UI 设计基线

## 1. 版本与定位
- 继续使用 `AndroidAgent-V2`，当前无需新开 V3。
- UI 世代：VNext；架构世代：不变。
- AG Core、Agent Runtime、Tool contract、Provider architecture、AT consumer boundary、TopicSession/Persistence 均保持冻结。
- 本轮主要演进 Host / Presentation / Android UI / Transcript projection / User interaction。

## 2. 产品形态
AndroidAgent APK 定位为浅色优先的 Codex-like Agent Workspace，而不是传统聊天 App、终端模拟器或设置型 App。

关键词：
- 浅色优先
- 高信息密度
- 低装饰
- 卡片化
- 工作台感
- Agent execution 可观察

## 3. 视觉基线
默认 Light Theme：
- Workspace：柔和灰白
- Card：白色
- Primary text：深灰黑
- Secondary text：中灰
- Divider / Border：浅灰
- 以留白、细边框、圆角、层级、分隔线、字体权重区分内容
- 不使用重阴影、高饱和渐变、巨大聊天气泡、大量彩色标签

Dark Mode 可后续增加 Light / Dark / System，V1 先做好 Light。

## 4. 主界面布局
```text
┌──────────────────────────────────┐
│ ☰  AndroidAgent           ADB ●  │
├──────────────────────────────────┤
│                                  │
│       Conversation Viewport      │
│       固定区域                    │
│       Transcript 内部滚动         │
│                                  │
├──────────────────────────────────┤
│ [附件 chips]                     │
│ ＋ 输入消息……     5.6 · High  ↑ │
└──────────────────────────────────┘
```

- Header 固定
- Conversation Viewport 占据剩余空间
- Composer 固定底部
- 对话增长不会改变整个页面高度，只增加 Viewport 内部内容长度

## 5. Conversation Viewport
第一稿：
- 自然纵向滚动
- 无右侧细进度条
- 无时间轴
- 无分页器
- 用户在底部时自动跟随 streaming 新内容
- 用户已上滑时禁止强制拉回底部
- 可显示轻量 `↓ 最新` 按钮回到底部

未来再增加 Grok 类逐 Turn 导航。

## 6. Transcript 层级
```text
TopicSession
└─ User Turn / Run Group
   ├─ User
   ├─ Thought
   ├─ Assistant
   ├─ Tool Action
   ├─ Tool Result
   ├─ Thought
   ├─ Assistant
   └─ ...
```

Turn Group 只是 Presentation grouping，不创建第二套 Session/Run/Message 领域模型。

## 7. 一级视觉分隔
User / Thought / Assistant 前必须存在明确但克制的分隔线：

```text
──────────────────────────────
User Card

──────────────────────────────
Thought Card

──────────────────────────────
Assistant Card
```

Tool / Result 不使用同级大分隔，它们属于当前 Agent execution process。

## 8. Card 体系
V1 采用 Interactive Transcript Cards：
- UserCard
- ThoughtCard
- AssistantCard
- ToolExecutionCard
- FailureCard
- InterruptedCard

Card 可有 Presentation state：
`expanded / collapsed / selected / preview / streaming / final`

这些都不能成为 Agent SSOT。

## 9. User Card
```text
──────────────────────────────
┌──────────────────────────────┐
│ You                          │
│ 帮我检查当前应用的状态。     │
│                       ⧉  ↻  ⋯│
└──────────────────────────────┘
```

V1 操作：
- ⧉ Copy
- ↻ Retry / Re-run
- ⋯ More
- Android 原生文本选择

### Copy
复制整个 User block 正文，不带内部 metadata；正文支持局部选择复制。

### Retry
UI 可显示“重做”，内部语义是 Retry / Re-run originating User Turn，不是严格的 Redo/Unrevert。
V1 优先支持最新已完成 Turn；运行中隐藏 Retry，只提供 Stop。

### More
V1：
- 复制
- 编辑并重新发送
- 从这里重新开始

未来：
- 从这里 Fork

## 10. Edit & Resend
禁止直接修改数据库中的旧 UserMessage。

正确语义：
```text
选择旧 User Turn
↓
进入对应历史位置
↓
恢复原 User text 到 Composer
↓
用户编辑
↓
重新发送
```

UI 名称使用“编辑并重新发送”。

## 11. Thought Card
运行中：
```text
──────────────────────────────
┌──────────────────────────────┐
│ Thinking...                  │
│ reasoning streaming...       │
└──────────────────────────────┘
```

完成后：
```text
──────────────────────────────
┌──────────────────────────────┐
│ › Thought · 2.8s             │
└──────────────────────────────┘
```

展开后：
```text
┌──────────────────────────────┐
│ ˅ Thought · 2.8s             │
│ 完整 reasoning...            │
│                          ⧉   │
└──────────────────────────────┘
```

规则：
- Thinking → Thought 是同一个 reasoning block / 同一张 Card 的生命周期变化
- 不生成两个重复卡片
- Provider 没有 reasoning 时不显示 Thought
- V1 支持 Expand/Collapse、Copy、Native text selection
- 不支持 Edit/Delete/Retry Thought

## 12. Assistant Card
```text
──────────────────────────────
┌──────────────────────────────┐
│ Assistant                    │
│ 我先检查一下当前状态。       │
│                         ⧉ ↻ │
└──────────────────────────────┘
```

V1：
- Copy
- Native selection
- Retry Turn

Assistant formal content 必须流式增加，不能等完整 ModelTurn 后一次性显示。

Assistant 不等于 Final Answer；中间 Assistant output 同样保留。

Assistant 的 Retry 与对应 User Card 的 Retry 指向同一个 originating Turn。

## 13. Tool Execution Card
ToolCall 和 ToolResult：
- 视觉上组合
- 语义上独立

示例：
```text
┌──────────────────────────────┐
│ Shell                        │
│ $ dumpsys activity ...   ⧉  │
│ ──────────────────────────   │
│ ✓ Result · 0.8s · exit 0     │
│ stdout preview...            │
│                    展开  ⧉   │
└──────────────────────────────┘
```

统一使用 ToolExecutionCard 渲染 Shell / File / Network / future tools。

Tool Result V1：
- Preview
- Expand / Collapse
- Copy
- Native text selection
- 大输出默认不无限撑开

## 14. Failure 与 Interrupted
Tool failure 就近显示在 Tool Card 内，必须保留真实诊断原因，不能只显示 Failed。

Run/Provider failure 可独立显示。

用户 Stop：
- 不是 Failure
- 保留 partial Thought / Assistant / ToolCall / completed ToolResult
- 追加 Interrupted 表示本轮被用户中止

## 15. Card 操作矩阵
- User：Copy / Select / Retry / Edit & Resend / More
- Thought：Copy / Select / Collapse / Expand
- Assistant：Copy / Select / Retry
- Tool Action：Copy / Select
- Tool Result：Copy / Select / Collapse / Expand
- Failure：Copy / Select
- Interrupted：可按 originating Turn 重跑

## 16. Bottom Composer
```text
┌────────────────────────────────┐
│ ＋ 输入……    GPT-5.6 · High  ↑ │
└────────────────────────────────┘
```

只保留：
- ＋ Context/File
- Input
- Model / effort selector
- Submit / Stop

Voice / Mic：V1 不做。

## 17. ＋ Context
V1：
- 添加文件
- 添加文件夹
- Android SAF
- 附件以 chip 展示，如 `[AGENTS.md ×]`
- 不把文件正文直接塞进输入框

## 18. Model Selector
主界面只显示模型与 effort，例如：
`GPT-5.6 · High`

点击后切换：
- Model
- Thinking effort

Provider / endpoint / API key 不常驻主界面。

## 19. Submit / Stop
Idle：`↑`
Running：`■ / Stop`

语义分层：
```text
UI        = Stop
AG        = Interrupt
Runtime   = cancellation
```

Stop：
- 结束当前 Run
- 保留 TopicSession
- 保留 partial transcript
- 同一 Session 可继续 send

## 20. Sidebar
完全可收起。

```text
+ New Session

Sessions
────────────
AndroidAgent UI
ADB 调试
分析日志

────────────
ADB
● Connected
Reconnect

────────────
Settings
Provider
API Key
```

核心只放 Session / ADB / Settings。

## 21. Session Browser
V1：
- New Session
- Switch Session
- Current highlight
- Rename
- 可按 Today / Yesterday / Earlier 简单分组

## 22. ADB 状态
Header 只显示总状态：`ADB ●`

Sidebar：
- Connected / Disconnected / Connecting
- Reconnect
- 简短失败原因

ADB 属于 Workspace environment state，不进入 Transcript。

## 23. Provider / API Key
设置页：
- Provider：OpenRouter / EMG / Custom
- API Key
- Endpoint
- Test Connection
- Save

主界面不长期显示调试信息。

## 24. Debug 通道
现有 DebugBridge：
- 保留
- 复用
- 不重建
- 继续作为 developer side channel 用于开发、自动化和真机验收

目标结构：
```text
UI callback
                 → Host Action → AgentRuntime
        /
Debug command
```

UI Send / Debug Send 进入同一个 send path。
UI Stop / Debug Stop 进入同一个 interrupt path。
其他可复用操作同理。

禁止：
- 第二套 Debug AgentRuntime
- 第二套 Debug history
- 第二套 Agent execution semantics

正确结构：
```text
                  AgentRuntime
                      │
                 AG Core
                      │
           Provider / Tool Layer
                      │
                 Event Stream
                 /                    Transcript       DebugBridge
```

V1 尽量保持现有 DebugBridge protocol compatible；没有真实 contract gap 时不升级 protocol、不重写 DebugBridge。

Debug 安全：
禁止输出 API key、Authorization、secret-bearing request。
允许输出 provider、model、safe endpoint、session/run id、event type、tool name、failure category、timing。

## 25. 未来导航
V1：普通滚动。

未来：
```text
‹   Turn 4 / 18   ›
```
切换单位是 User Turn / Run Group，不是单个 Thought/Result。

## 26. 未来 Focus View
未来支持长按 Card → Focus / Magnify：

```text
┌──────────────────────────┐
│ Result                ×  │
│ 完整日志 / 文本 / 代码   │
│ Copy      Select All     │
└──────────────────────────┘
```

V1 只预留组件扩展能力，不实现。

## 27. V1 必须完成
```text
LIGHT THEME
Collapsible Sidebar
Fixed Conversation Viewport
User / Thought / Assistant Divider

User Card
Thought Card
Assistant Card
ToolExecution Card
Failure
Interrupted

Copy
Native Text Selection
Retry
Edit & Resend
Thought Collapse
Result Collapse

Codex-style Composer
File / Folder attachment
Model / effort selector
Submit → Stop

Session Browser
ADB status + reconnect
Provider / API Key settings

Existing DebugBridge preserved
UI / Debug reuse same action paths
```

## 28. V1 明确不做
```text
Grok-style Turn switching
Focus View
Pinch zoom
复杂动画

真正 Redo / Unrevert
任意历史 Fork
Checkpoint UI

Export
复杂 syntax highlighting
Voice

新 Debug protocol
第二套 Debug runtime

架构重构
Gradle/module 重构
```

## 29. Architecture Guard
必须保持：
```text
DB
↓
Runtime / Core
↓
Presentation projection
↓
UI Cards
```

禁止 UI Cards 直接维护第二份 conversation truth。

UI 的 collapsed / expanded / selection / preview / focus 都只是 Presentation state。

## 30. Git / Versioning
继续使用：
`AndroidAgent-V2`

当前 `AG.POST-I-CORE-FREEZE-V1` 继续作为 immutable pre-UI architecture baseline。

后续：
```text
AG.POST-I-CORE-FREEZE-V1
        ↓
UI development commits
        ↓
UI acceptance
        ↓
Product / Release Freeze
```

不移动旧 tag。

## 31. 建议 UI 阶段
```text
AG.UI-BASELINE-AUDIT-1
现有 UI / callback / Transcript / DebugBridge 盘点

↓

AG.UI-SHELL-1
浅色 Workspace + Sidebar + fixed viewport + Composer

↓

AG.UI-TRANSCRIPT-CARDS-1
Card 化 + divider + streaming projection + collapse/copy

↓

AG.UI-CONTROLS-1
Retry / Edit & Resend / Stop / Session interactions

↓

AG.UI-DEVICE-ACCEPTANCE-1
真实设备完整验收

↓

AG.UI-RELEASE-FREEZE-1
最终 Product UI + Release freeze
```

## 32. V3 开启条件
当前：
`CREATE_ANDROIDAGENT_V3 = NO`

只有发生真正代际变化时再考虑 V3，例如：
- AG Engine contract 不兼容变化
- AG Core / Agent Runtime 职责重构
- major module graph redesign
- Provider / Tool architecture replacement
- durable model incompatible redesign
- major consumer boundary replacement
- 无法在 V2 内兼容迁移的 breaking architecture

单纯新 UI、新 Card、浅色主题、Sidebar、Composer、Session Browser、Debug callback reuse 都不构成 V3 理由。

## 33. 最终定义
```text
AndroidAgent V2 VNext UI
=
Light Codex-like Agent Workspace
+
Fixed Conversation Viewport
+
Semantic Dividers
+
Interactive Transcript Cards
+
Codex-style Composer
+
Collapsible Session / Status Sidebar
+
Existing Debug Side Channel
```

核心产品原则：
- Agent 工作过程清晰可见
- 长对话不破坏整体页面布局
- 每个语义块可以被直接操作
- Copy / Retry / Edit 真正可用
- Thought / Assistant / Tool 不混淆
- Stop 不关闭 Session
- UI 不拥有 Agent semantics
- Debug 不建立第二条 Agent execution path
