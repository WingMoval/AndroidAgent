# AGA.OBSERVABILITY-1 — Transcript Thinking→Thought 翻转 + Debug-Originated User 块

> 类型：BUGFIX · HOST/OBSERVABILITY（:app only）。
> 前置：`AGA.OBSERVABILITY-0` PASS（其验证只覆盖"reasoning 是末尾块"的理想场景，
> 未覆盖真机多块场景，遗留两个 bug）。
> 不变量：① 只改 :app（Host/Observability），不动 AG Core / Agent Runtime /
> Provider / Tool / AT；② Thinking 与 Thought 仍是**同一个块**的状态翻转，绝不插第二块；
> ③ User 块对 UI-originated 与 debug-originated 的 Run 一视同仁；④ 不新增 Agent 语义，
> 只做记录与渲染。

---

## 1. 诊断（两个 bug 的根因）

### Bug A — Thinking 停在 Thinking...，不翻转 Thought
```text
现象：真机一轮里，模型先吐 reasoning 再吐 content（→ 可能再 toolcall），
      结束后 Thinking 块仍显示 "Thinking..."，不变 "Thought"。

根因：TranscriptModel.onReasoningFinished()（TranscriptModel.kt:86-91）只翻转
      blocks.lastOrNull()：
        fun onReasoningFinished() {
            val last = blocks.lastOrNull()
            if (last is Block.Thinking) {
                blocks[blocks.size - 1] = last.copy(phase = THOUGHT)
            }
        }
      但真实轮次里 reasoning 之后通常已插入 Assistant（content 流）或 ToolCall 块，
      此时 last 不再是 Thinking 块 -> 条件不成立 -> 该轮 Thinking 块永久停在 THINKING。

为何当轮没暴露：TranscriptModelTest.T3 只测了
      onReasoningStarted -> onReasoningDelta -> onReasoningFinished（reasoning 是末尾块），
      没测 reasoning 之后还有 content/toolcall 的真实多块顺序。
```

### Bug B — debug bridge 发起的 Run 不显示 User 输入行
```text
现象：通过 PC debug bridge（ag_debug.py / L3 驱动脚本）发 prompt 时，
      app 界面 transcript 只有 Thinking 和 Assistant，没有 "User\n<text>" 行。
      （UI 按钮发送则正常有 User 行。）

根因：Block.User 只在 ChatActivity.onSend()（UI 点击发送，line 390）里
      transcriptModel.onUser(text) 插入。
      debug bridge 的 handleSend（DebugBridge.kt:204）走 runtime.send(text)，
      不经过 onSend()；onRunEvent 事件通道也不产生 User 块（User 不是 AgentEvent）。
      -> debug-originated Run 的 transcript 缺 User 行。
```

## 2. 修复方案（冻结）

### Bug A 修复
```text
onReasoningFinished() 改为翻转"最近一个仍处 THINKING 阶段的 Thinking 块"
（从 blocks 末尾向前找第一个 Block.Thinking 且 phase==THINKING），而非 last。
- 命中则把该块 phase 置 THOUGHT。
- 找不到（本就没有 reasoning）则 no-op。
- 绝不插第二块。
理由：一轮里 reasoning 块在其 content/toolcall 块之前，末尾向前找第一个
      THINKING 态 Thinking 块 = 本轮那个块，精确命中。
```

### Bug B 修复
```text
DebugBridge 增加可选构造参数 userInputSink: ((String) -> Unit)? = null。
handleSend 在 accepted 之后、runtime.send 之前，若 userInputSink != null 则调用
userInputSink(text)，由 Host（ChatActivity）在 UI 线程执行
transcriptModel.onUser(text) + renderTranscript()。
- ChatActivity.ensureDebugBridge 构造 bridge 时传该 sink。
- 不新增 AgentEvent、不改 Agent Core / Runtime 语义；仅 Host 侧补一个渲染钩子。
- release 构建 bridge 不存在，天然 no-op。
```

## 3. 改动文件（本 Step）

```text
改  app/src/main/java/com/androidagent/app/TranscriptModel.kt
       onReasoningFinished() 末尾向前找第一个 THINKING 态 Thinking 块翻转
改  app/src/main/java/com/androidagent/app/debug/DebugBridge.kt
       + userInputSink 构造参数；handleSend accepted 后回调 userInputSink
改  app/src/main/java/com/androidagent/app/ChatActivity.kt
       ensureDebugBridge 传 userInputSink = { runOnUiThread { onUser + render } }
改  app/src/test/java/com/androidagent/app/TranscriptModelTest.kt
       + T6 reasoning+content 多块后仍翻转
       + T7 reasoning+content+toolcall 后仍翻转
改  （DebugBridge User 钩子由真机 L 层验证；JVM 侧 TranscriptModel.onUser 已覆盖）
```

## 4. 验证层

```text
L1  JVM 单测（TranscriptModel）：
      T6 onReasoningStarted/Delta + onContentStarted/Delta + onReasoningFinished
         => Thinking 块翻 THOUGHT，Assistant 块独立存在，块数=2
      T7 reasoning + content + toolcall 后 onReasoningFinished
         => Thinking 翻 THOUGHT，Assistant/Shell/Result 块各自独立
      T3/T4/T5 原有用例保持绿
回归 :app:test + :ag-core:test + :tool-android-shell:test + :tool-network:test
      + :app:assembleDebug 全绿
L2  真机（UI 发送）：Thinking->Thought 翻转 + User 行存在
L3  真机（debug bridge 发送）：Thinking->Thought 翻转 + User 行存在（本 Step 新增能力）
```

## 5. 禁止范围（本 Step 不做）

```text
不动 AG Core / Agent Runtime / Provider / :tool-* / AT
不新增 AgentEvent / 不改 Tool 契约 / 不动 streaming 协议
不引入第二 Thinking 块 / 不改 User 块语义（仍为 Host 侧记录）
不做其他 UI 重构
```

## 6. 验证证据

```text
L1  JVM 单测（TranscriptModelTest）：
      24 tests · 0 failures（原 21 + 新增 T20/T21/T22）
        T20 reasoning+content 后 onReasoningFinished -> Thinking 翻 THOUGHT，
            Assistant 独立，块数=2
        T21 reasoning+content+toolcall(shell+result) 后翻转 -> Thinking 翻 THOUGHT，
            4 块各自独立
        T22 无 reasoning 时 onReasoningFinished 为 no-op（不建 Thinking 块）
回归  :app:test（92 全绿）+ :ag-core:test + :tool-android-shell:test +
      :tool-network:test + :app:assembleDebug  全绿

真机（APLMUT4312005935 / HONOR ALI-AN00）
  路径 = debug bridge send（ag_debug.py 同源，Bug 暴露的原路径）
  prompt = "用一句话解释什么是 HTTP 重定向（301）。"
  transcript TextView 实测全文（HTML 实体解码）：
      User
      用一句话解释什么是 HTTP 重定向（301）。

      Thought
      The user asks: "用一句话解释什么是 HTTP 重定向（301）..."
      This is a general knowledge question, no device state needed. Answer in
      Chinese, one sentence.

      Assistant
      HTTP 301 重定向是一种服务器返回的"永久性跳转"响应状态码，告诉客户端（如
      浏览器）所请求的资源已永久移动到新 URL，并自动引导用户访问新地址。
  判定：
      Bug A  显示 "Thought"（非 "Thinking..."），其后紧跟 Assistant 块
            -> 多块场景翻转 PASS
      Bug B  存在 "User" 行（debug 发起的 run）
            -> debug-originated User 块 PASS
  UI 路径：onSend() 原有 User 块行为未改动；Thinking->Thought 与 debug 路径
          共享同一 TranscriptModel.onReasoningFinished（T20/T21 已覆盖），无回归。
```

## 7. Verdict

```text
AGA.OBSERVABILITY-1 = PASS
  - Bug A：TranscriptModel 多块场景（reasoning 后已有 content/toolcall）正确翻转
           Thinking->Thought，绝不插第二块。L1 T20/T21/T22 + 真机 PASS。
  - Bug B：debug bridge 发起的 Run 现显示 User 行（userInputSink 钩子），
           与 UI 发送一视同仁。真机 PASS。
  - L1 单测 24/24 绿；回归（app 92 + ag-core + tool-android-shell + tool-network +
    assembleDebug）全绿；真机 debug 路径双 bug 均 PASS。
  - 边界：仅改 :app（TranscriptModel / DebugBridge / ChatActivity / 测试）；
    无 Core / Runtime / Provider / Tool / AT 改动；不新增 AgentEvent / 不改
    streaming 协议 / 不插第二 Thinking 块。
```
