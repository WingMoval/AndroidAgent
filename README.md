# AndroidAgent

**[中文](README.md) | [English](README_EN.md)**

> Android 上的自主 Agent：模型负责思考（Intelligence），AndroidTerminal 负责执行（Execution）。

AndroidAgent（下称 **AG**）是一个运行在 Android 手机上的 AI Agent 应用。它通过 OpenAI 兼容的
Provider 驱动一个自适应工具循环：模型自主决定调用哪些工具、观察结果、继续推理，直到给出最终答案。

## 项目状态

```text
状态：0.2.0 首个公开版本（LOCAL RELEASE CANDIDATE）
阶段：0.x 活跃开发中
License：Apache-2.0
```

这是 AndroidAgent 的第一个公开发布版本。此前的 0.1.x 开发为内部阶段，未公开发布过。

## 核心能力

| 能力 | 说明 |
|------|------|
| **自适应 Agent 循环** | 模型自主进行「思考 → 工具调用 → 观察 → 继续」的循环，直到最终答案 |
| **android_shell 工具** | 通过 ADB 在目标 Android 设备上执行 shell 命令（one-shot 执行语义） |
| **file 工具** | 读取 Agent 自有目录（`filesDir/agent/`）下的文件：read / search / list / stat（只读，无写/删） |
| **network 工具** | HTTPS GET fetch（仅公共网络，完整 SSRF 防护，无 POST / 认证 / Cookie） |
| **TopicSession 持久化** | 会话持久化（Room/SQLite）：冷启动恢复、会话列表、切换会话 |
| **会话重命名 / 删除** | 用户可对会话重命名、删除（级联清理消息） |
| **Stop / Interrupt** | 运行中可随时 Stop；中断产生结构化的 `RunOutcome.Interrupted`，部分 transcript 保留 |
| **ADB 配对 / 就绪 / 管理** | 内置 ADB 管理界面：当前状态、Reconnect、Pair / Re-pair、打开无线调试设置 |
| **Provider 配置** | OpenAI 兼容 Provider（如 OpenRouter）；应用启动时自动加载 `provider.local.json`，缺失/非法则回退手动 UI |
| **流式 Transcript** | 模型推理（Thinking → Thought）、内容流式、工具动作/结果/失败实时可见 |

## 架构概览

```text
AndroidAgent = AG Engine + Tool Layer + Host

AG Engine      = :ag-core（纯 JVM，无 Android 依赖）
                 + :ag-runtime（纯 JVM，host 中立）
Tool Layer     = 具体工具模块，一个能力一个模块
                 :tool-android-shell   （通过 AT Engine 执行 shell）
                 :tool-network         （平台 HttpsURLConnection，无 AT 依赖）
                 :tool-file            （APP_SELF 只读文件）
Host/Product   = :app（AGA）：Composition Root，构造并注册工具
                 + :integration-at（AT 执行的统一消费点）
```

依赖方向冻结：工具只依赖 `:ag-core`；Core / Runtime 永远不依赖任何具体工具、
Provider、Android 或 AT。

### AG = Intelligence，AT = Execution

AndroidAgent 只负责智能侧：Agent 循环、会话、Provider、工具契约。
Android 执行侧（ADB 连接、配对、shell 执行）由独立的
[AndroidTerminal（AT）](#androidterminal-执行层) 项目提供，AG 通过
**vendored AAR（repo-local Maven）** 消费 AT 的预构建产物，构建自包含，
不需要 AT 源码树。

本仓库 **不包含 AT 源码**，只包含 AT v0.4.4（GAV 4.0.0）的预构建
AAR/JAR 与 native 产物（`third_party/at-v044-consumer-freeze-v1/`）。

## V1 → V2 演进

AndroidAgent 的 V1 与 V2 **不是两个不相关的项目**，而是同一条连续 Git 历史上的演进：

- **V1**：AndroidAgent 早期阶段，Agent 与 Android 执行 / ADB 结合更紧。
- **V2**：在同一 Git 历史上持续演进，形成 `AG = Intelligence` / `AT = Execution`
  的正式架构分离，并通过正式 AT consumer boundary（vendored AAR）消费 Android 执行。

完整 V1 → V2 历史随本仓库一并公开。

## 构建要求

以下要求来自当前真实 Gradle 配置：

| 项目 | 要求 |
|------|------|
| JDK | 21（AGP 8.5.2 要求 JDK 17+；本机验证使用 JDK 21） |
| Android SDK | compileSdk 34 / targetSdk 34 |
| minSdk | 26（Android 8.0+） |
| Gradle | 8.7（wrapper 已内置） |
| AGP | 8.5.2 |
| Kotlin | 2.1.0 |
| ABI | 仅 arm64-v8a（AT native 产物的产品约束） |

> 注意：不需要 Android 15。任何 minSdk 26+ 的 arm64 设备均可安装。

## 构建步骤

```bash
# 1. 配置 Android SDK 位置（写入 local.properties，该文件已被 gitignore）
echo "sdk.dir=C:\\android" > local.properties

# 2. 运行单元测试
.\gradlew.bat :ag-core:test :ag-runtime:test :app:testDebugUnitTest

# 3. 构建 debug APK
.\gradlew.bat :app:assembleDebug
# 产物：app/build/outputs/apk/debug/app-debug.apk
```

### 构建 Release APK（需要独立 signing identity）

Release 构建通过 **环境变量** 读取 signing 配置；缺少任一变量时构建会
**明确失败**，不会回退到 debug 签名：

```powershell
$env:ANDROID_AGENT_KEYSTORE_FILE  = "<path>\AndroidAgent-release.jks"
$env:ANDROID_AGENT_KEYSTORE_PASSWORD = "<store-password>"
$env:ANDROID_AGENT_KEY_ALIAS      = "androidagent-release"
$env:ANDROID_AGENT_KEY_PASSWORD   = "<key-password>"

.\gradlew.bat :app:assembleRelease
# 产物：app/build/outputs/apk/release/app-release.apk
```

## 运行方式

1. 安装 APK 到一台 **arm64** Android 设备（Android 8.0+）。
2. 打开应用，进入 Provider 配置：
   - 自动加载：应用启动时读取 `filesDir/provider.local.json`；
   - 手动配置：在 UI 中填写 baseUrl / apiKey / model 并 Apply。
3. （使用 android_shell 时）在 ADB 管理界面完成目标设备的 ADB 配对，
   或确保设备已处于可连接的就绪状态。
4. 输入任务，观察 Agent 的 Thinking / Tool 调用 / 结果流式 transcript，
   必要时点 Stop。

## Provider 配置

支持 OpenAI 兼容的 chat-completions Provider（流式 + 工具调用）。
仓库内 `config/provider.example.json` 是 **占位符示例**（不含真实密钥）：

```json
{
  "provider": {
    "type": "openai-compatible",
    "baseUrl": "https://api.example.com/v1",
    "apiKey": "REPLACE_ME",
    "model": "example-model",
    "requestTimeoutSeconds": 60
  }
}
```

实际配置放在 `config/provider.local.json`（已被 gitignore），或将内容复制到
设备 `filesDir/provider.local.json` 由应用自动加载。示例中可替换为任意
OpenAI 兼容端点（如 OpenRouter、或你的自建兼容端点）。

**安全约定**：请勿在公开文档、仓库或 issue 中粘贴真实 API Key。

## ADB 使用说明

- `android_shell` 工具通过 AT Engine 以 ADB 方式在目标设备上执行 shell 命令
  （one-shot 语义：每次调用建立独立执行上下文）。
- 应用内置 ADB 管理界面（状态 / Reconnect / Pair / Re-pair / 打开无线调试设置）。
- 身份（ADB key）为 **每应用私有、自动生成**，不复制其他应用的 key。
- 首次使用需要在目标设备上完成无线调试配对。

## 已知限制

当前版本为 0.x 活跃开发阶段，以下限制是**已知且有意保留**的边界：

1. **Local one-shot 子进程可能在 Stop 后存活（G-b1）**：Stop 会取消
   Agent 运行（模型 / 工具调用链路），但已经派发到 AT 的一次性 shell 子进程
   不保证被终止。外部副作用不会回滚。
2. **通用 mutation 审批尚未完整**：工具调用目前按注册即允许的策略执行；
   面向用户的 mutation 审批机制尚未实现。
3. **无通用 browser / search / JS / POST 能力**：network 工具仅做 HTTPS GET
   fetch（公共网络）；无浏览器自动化、搜索、JavaScript 执行、POST / 认证 /
   Cookie。file 工具只读（无写 / 编辑 / 删除）。
4. **每个 Run 的 8-step 上限**：`RunOptions.DEFAULT_MAX_STEPS = 8`，单个
   Run 的工具循环最多 8 步，超出产生 `MaxStepsReached`。
5. **项目仍处于 0.x 阶段**：API / 架构可能在不兼容变更的情况下演进；
   无 Rewind / Redo / Fork / Checkpoint；无 MCP / multi-agent /
   persistence(RAG)。

## Release APK

v0.2.0 的正式 signed release APK 随本轮 release-prep 生成并暂存于本地
`upload/release/v0.2.0/`（该目录不进入 Git，仅作本地暂存）。APK 元数据与
SHA256 见 `docs/releases/v0.2.0.md`。

## License

本项目采用 **Apache License 2.0** 授权（见根目录 `LICENSE`）。
第三方组件（AndroidTerminal AAR、BoringSSL、libc++ 等）的归属与许可证
要求见根目录 `NOTICE` 及 `docs/legal/boringssl/HISTORICAL_BORINGSSL_LICENSE.md`。

## AndroidTerminal 执行层

Android 执行由独立项目 **AndroidTerminal（AT）** 提供：

- 本仓库消费 AT **v0.4.4**（GAV `4.0.0`）的预构建 AAR/JAR 与 native 产物，
  来源为 AT 源码 commit `e2f2d7b`（tag `v0.4.4`）；
- 消费边界为 `com.at.engine.api:at-engine-api:4.0.0`（公开契约），
  组合入口 `com.at.composition.android:at-composition-android:4.0.0`；
- AT 自身采用 Apache-2.0 授权；其 AAR 内静态链接了 pre-2025-02 的 BoringSSL
  （OpenSSL BSD + SSLeay BSD + ISC + MIT），相关归属与许可证文本已按要求
  随本仓库分发（见 `NOTICE`）。
