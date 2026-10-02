# AndroidAgent 总体架构

> 状态：`AG.BASELINE-0` 基线。本文只冻结**当前已确定**的内容。
> 未实现的部分一律标注为"未实现"，不得作为已完成能力描述。

## 1. Project positioning

AndroidAgent 是面向 Android 自主交互的 **Agent 智能 / 运行时**工程。

它不是一个终端应用，也不是一个 ADB 库。它是 Agent 的决策与运行时层，
负责理解用户意图、调用工具、综合结果；Android 的实际执行能力由外部提供。

```text
开发目录 : D:\codebank\AndCode\AndroidAgent          = source of truth
发布目录 : D:\codebank\AndCode\AndroidAgent-Public   = 仅下游导出
```

## 2. AG Core

AG Core 是 **Agent 智能内核**，也是产品无关的运行时。

职责范围（对应已冻结决策 A2）：

```text
用户输入 / 上下文
Model / API 抽象
Agent Loop
任务状态
Skills
Tool Registry
权限 / 审批
结果综合
与 GUI / TUI 无关的 event/result contract
```

当前实现状态：**未实现**。`AG.BASELINE-0` 只建立了 `:ag-core` 这个
pure Kotlin/JVM module 与其单元测试基线；上述能力全部属于 `AG.0`。

AG Core 的边界规则：

```text
AG Core  -X->  AT Core
AG Core  -X->  Android framework (android.* / androidx.*)
AG Core  -X->  ADB / JNI
AG Core  -X->  com.androidterminal.*
```

这是**编译期强制**的，不只是约定：`:ag-core` 使用 `kotlin("jvm")` 插件，
`android.*` 无法解析；`settings.gradle.kts` 中刻意不声明 `google()`。

## 3. AGA

AGA 是 AndroidAgent 的 GUI 产品形态（未来 `AG.apk`）。

当前状态：**未实现**。本工程当前**没有** `:app` module，也不计划在
`AG.BASELINE-0` 预建。AGA 将消费 AG Core + Android Tools。

## 4. AGC

AGC 是未来的 CLI / TUI 前端，与 AGA **共享同一个 AG Core**。

当前状态：**未实现**。是否能让 stock Android 在无 root 条件下承载一个真正
独立的 `androidagent` CLI/TUI，属于独立的 host feasibility 轨道，
**不阻塞** AG Core 与 AGA。

## 5. Tool System

Tool System 是 AG Core 与执行层之间的唯一通道。

```text
Tool Registry   归属 AG Core —— 注册、查找、调用分发、schema
Concrete Tool   归属 Android Tools —— 具体 Android 能力实现
```

核心约束：

```text
AG Core 只知道 Tool 系统
AG Core 不知道 ADB 协议细节
```

因此 AG Core 中**不出现** `AdbClient`、`shell_v2`、WRTE/OKAY、TLS、PTY
等任何执行层概念。

当前状态：**未实现**。`AG.BASELINE-0` 不创建 Tool 接口，也不创建
Tool Registry 实现（属于 `AG.0`）。

## 6. Android Tools

Concrete Tool 的实现层，例如未来的 shell tool、push tool、pull tool。

职责边界：

```text
把 AG Core 的 Tool 调用
        翻译为
具体的 Android 执行请求
```

Android Tools 是**唯一**允许同时依赖 AG Core 与 AT Core 的层。

```text
AG Core      ->  Tool Registry  ->  Android Tools  ->  AT Core
```

当前状态：**未实现**。归属独立 integration 层，后续可先是 package，
在出现第二个真实执行后端时才考虑独立 module —— 单一后端不值得抽象。

## 7. AT Core relationship

AT Core 是 AndroidTerminal 工程的 **Android 执行内核**（ADB 连接 / 传输 /
协议、shell 执行）。

```text
AT Core 属于 AndroidTerminal 工程
AndroidAgent 不 import、不 vendor、不复制 AT Core
```

AG Core **不得**直接依赖 AT Core。若 AG Core 看起来需要它，说明设计错了 ——
正确做法是引入一个 tool。

注意：AndroidTerminal 侧的 AT Core 当前尚未完成物理模块化。
这不影响 AG Core —— AG Core 本来就不依赖它。

## 8. Dependency direction

```text
ALLOWED
  AGA ---------→ AG Core
  AGC ---------→ AG Core
  AG Core ----→ Tool Registry
  Tool Registry → Concrete Tools
  Android Tools → AT Core
  AT Core ----→ adbd / Android

FORBIDDEN
  AG Core       -X-> AT Core
  AG Core       -X-> Android framework
  AG Core       -X-> ADB / JNI
  AT Core       -X-> AG Core
  AGA           -X-> AT Core        (必须经 Android Tools)
```

架构图：

```text
            AGA
             |
             v
          AG Core
             |
             v
        Tool Registry
             |
             v
       Concrete Tools
             |
      Android Tools
             |
             v
       Stable AT Core
             |
             v
          Android

  AGC ---→ AG Core
```

## 9. Development / Public directory relationship

```text
AndroidAgent          开发目录，唯一 source of truth
AndroidAgent-Public   发布目录，只在明确 release 节点由开发目录导出
```

禁止在 `-Public` 直接开发；禁止在 `-Public` 修 bug 后反抄开发树；
禁止两个目录同时充当 source of truth。

第一次 Public 同步应发生在明确 release-ready 节点，例如 AG Core v0.1。

## 10. Current roadmap

```text
AG.BASELINE-0   独立开发工程初始化与基线冻结            DONE
   |
   v
AG.0            AG Core v0 架构与最小 Agent Loop       NEXT
   |
   v
AG.1            AGA v0
```

本工程当前**仅存在** `:ag-core` 一个 module。以下均**不在**本工程基线内，
且未被预建：

```text
:app (AGA)   :android-tools   :agc   :at-core
```

依赖决策同样尚未做出，留待 `AG.0`：

```text
kotlinx-coroutines
JSON / serialization
```

它们不是基线前置条件，不应被提前引入。