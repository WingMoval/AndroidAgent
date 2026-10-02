# 步骤 AG.BASELINE-0 — AndroidAgent 独立开发工程初始化与基线冻结

## 1. Metadata

| 项 | 值 |
|---|---|
| 步骤 | `AG.BASELINE-0` |
| 工程 | AndroidAgent（独立开发工程） |
| 根目录 | `D:\codebank\AndCode\AndroidAgent` |
| 类型 | BASELINE / 工程初始化 / **不含任何 Agent 实现** |
| 前置 | `ARCH.P0` 全项目结构审计 |
| 结果 | `AG_BASELINE_0_COMPLETE` |

## 2. Goal

建立一个干净、可版本控制、可测试、适合后续 `AG.0` 开发的 AndroidAgent 工程基线。

本步骤**只做**：repository / build / module / test / docs / git baseline。

本步骤**不做**：任何 Agent 逻辑、任何 Android 能力、任何 AT 集成。

## 3. Repository root

```text
D:\codebank\AndCode\
├─ AndroidTerminal\          AT 开发目录      （本步骤未触碰）
├─ AndroidTerminal-Public\   AT 发布目录      （本步骤未触碰）
├─ AndroidAgent\             AG 开发目录      ← 本步骤创建
└─ AndroidAgent-Public\      AG 发布目录      （不存在，本步骤不创建）
```

四目录规则：

```text
开发目录  ->  明确 Release Step  ->  *-Public
```

禁止在 `-Public` 直接开发；禁止 `-Public` 修 bug 后反抄开发树；禁止两个目录同时作为 source of truth。

## 4. Directory structure

```text
AndroidAgent/
├─ .git/                                git init（默认 master -> main）
├─ .gitignore
├─ README.md
├─ AGENTS.md
├─ settings.gradle.kts                   rootProject.name = "AndroidAgent", include(":ag-core")
├─ build.gradle.kts                      kotlin("jvm") 2.1.0 apply false
├─ gradle.properties
├─ gradlew / gradlew.bat
├─ gradle/wrapper/{gradle-wrapper.jar,gradle-wrapper.properties}   Gradle 8.7
├─ ag-core/
│  ├─ build.gradle.kts                   kotlin("jvm")，JVM target 17
│  └─ src/
│     ├─ main/kotlin/com/androidagent/core/AgCoreMarker.kt
│     └─ test/kotlin/com/androidagent/core/AgCoreBaselineTest.kt
└─ docs/
   ├─ architecture/AndroidAgent_总体架构.md
   └─ implementation/steps/步骤AG.BASELINE-0_AndroidAgent独立开发工程初始化与基线冻结.md
```

**刻意未创建**：`app/`、`android-tools/`、`agc/`、`at-core/`。
均属后续阶段，不预建空架构层。

## 5. Git initialization

```text
git init
git branch -m main          （在首次 commit 前从 master 改为 main）
```

未配置 remote。未 push。未创建 GitHub 仓库。

未创建 `dev/*`、`feature/*`、`release/*` 分支 —— AndroidAgent 本身已是独立
AG repository，当前单线开发只需要 `main`。

## 6. Gradle structure

根工程最小化，只声明一个 module：

```kotlin
// settings.gradle.kts
rootProject.name = "AndroidAgent"
include(":ag-core")
```

一个刻意的决策：**不声明 `google()`**。

```kotlin
dependencyResolutionManagement {
    repositories {
        mavenCentral()      // 无 google()
    }
}
```

理由：AndroidAgent 当前没有任何 Android 依赖，不应在仓库层面预置一个会
默认引入 Android 构件的仓库列表。`google()` 只能作为一次显式评审的决策出现，
而不是已经在列表里等着被继承。

## 7. :ag-core module

| 项 | 值 |
|---|---|
| plugin | `kotlin("jvm")` 2.1.0（**非** Android Library / Application） |
| sourceSets | `src/main/kotlin`、`src/test/kotlin` |
| Java source/target | 17 |
| Kotlin jvmTarget | `JVM_17` |
| 实际构建 JDK | Microsoft OpenJDK 21.0.6 |

未使用 `jvmToolchain(17)`：本机只安装了 JDK 21，toolchain 自动下载会给每次
构建引入网络依赖。真正锁定语言级别的是 bytecode target，因此显式设置
`jvmTarget = JVM_17`。

产物校验：`ag-core.jar` 内 class 文件 bytecode major version = **61（Java 17）**。

依赖：

```text
runtimeClasspath        : kotlin-stdlib:2.1.0 (+ org.jetbrains:annotations:13.0)
testRuntimeClasspath    : 上述 + JUnit 5 (junit-bom 5.11.4)
```

## 8. Package boundary

冻结：

```text
com.androidagent.core
```

禁止：`com.androidterminal.*`、`com.androidagent.app.*`。

AG Core 是产品无关的 Agent runtime，AGA / AGC / 任何未来前端都必须能原样复用。

本步骤创建的唯一生产代码是 `AgCoreMarker`，用于证明 module 可编译且 package
契约正确，**不含任何 Agent 语义**。

## 9. Dependency audit

对 `ag-core/src/main` 与 `ag-core/src/test` 全量扫描：

```text
android.*              0
androidx.*             0
com.androidterminal.*  0
JNI                    0
ADB                    0
AT Core                0
AdbManager             0
AdbClient              0
AdbConnection          0
shell_v2 / TLS         0
TerminalResult         0
MainActivity           0
WirelessAdbDiscovery   0
Activity/Context/View  0
Compose                0
```

文档（README / AGENTS / 架构文档）中出现这些词属于**规则描述**，不是生产依赖。

这不是纸面约定，而是编译期强制：`kotlin("jvm")` 插件下 `android.*` 无法解析，
`google()` 未声明，因此 Android 依赖无法被无声引入。

## 10. Unit-test baseline

AG Core 从第一天具备 JVM unit test 能力。

```text
框架       : JUnit 5 (junit-jupiter, junit-bom 5.11.4)
入口       : ./gradlew :ag-core:test
冒烟测试   : AgCoreBaselineTest
```

`AgCoreBaselineTest` 三个用例，**只验证基础设施本身**：

```text
unit test infrastructure executes
ag core module compiles and is addressable
ag core package contract is frozen
```

未测试任何未来 Agent 行为。

## 11. Build result

全部实际执行并成功：

```text
.\gradlew.bat projects      BUILD SUCCESSFUL   Root project 'AndroidAgent', :ag-core
.\gradlew.bat :ag-core:test BUILD SUCCESSFUL   3 passed, 0 failed, 0 skipped
.\gradlew.bat build         BUILD SUCCESSFUL
```

环境：

```text
Gradle  8.7 (wrapper)
Kotlin  2.1.0
JDK     Microsoft OpenJDK 21.0.6 (LTS)  ->  bytecode target 17
Tests   3 passed / 0 failed / 0 errors / 0 skipped
```

过程中修复一处：`ag-core/build.gradle.kts` 最初使用 `#` 注释，Kotlin script
不支持 `#`（仅 `//`），导致 script compilation error。已改正。同时移除 `.kts`
中的非 ASCII 字符，规避 Windows 控制台/文件编码问题。

## 12. README / AGENTS

`README.md` 写明项目定位、当前状态（**明确声明 Agent 行为尚未实现**）、
四层关系、硬规则、build 命令、仓库角色、依赖现状。

`AGENTS.md` 冻结：仓库角色规则、架构红线（AG Core -X-> AT Core / Android /
ADB-JNI / com.androidterminal.*）、package 边界、开发纪律（禁止顺手重构与
空架构层）、验证要求、Git 纪律。

两份文档都明确：README 不得把未实现功能写成已完成。

## 13. Changed files

首次 baseline commit，共 15 个文件：

```text
.gitignore
AGENTS.md
README.md
ag-core/build.gradle.kts
ag-core/src/main/kotlin/com/androidagent/core/AgCoreMarker.kt
ag-core/src/test/kotlin/com/androidagent/core/AgCoreBaselineTest.kt
build.gradle.kts
docs/architecture/AndroidAgent_总体架构.md
docs/implementation/steps/步骤AG.BASELINE-0_...md
gradle.properties
gradle/wrapper/gradle-wrapper.jar
gradle/wrapper/gradle-wrapper.properties
gradlew
gradlew.bat
settings.gradle.kts
```

commit 前已确认**未**包含：`build/`、`.gradle/`、IDE 元数据、
`local.properties`、日志、密钥、大型生成文件。
（`git add -A --dry-run` 输出恰为上述 15 项。）

Tag：`ag-baseline-0`（轻量）。

## 14. Deferred

明确推迟到后续步骤，本步骤**未**创建、未实现、未预建：

```text
Agent Loop / AgentCore / AgentSession / AgentEvent
ModelProvider（真实 Model / API / HTTP client）
Tool Registry 实现 / Tool 接口
Skill / ApprovalPolicy / memory / RAG
coroutines（kotlinx-coroutines）
serialization / JSON
AGA / app module / Compose / UI
Android Tools（任何具体 tool 实现）
AT Core 集成
AGC / CLI / TUI
plugin system / sub-agent / multi-agent
universal Adapter / ShellBackend
DI framework / event bus / database / Room / network stack
AndroidAgent-Public 首次同步
```

其中 `coroutines` 与 `serialization / JSON` 记录为 **AG.0 dependency decision**：
AG.0 大概率需要，但 BASELINE-0 不因"以后一定会用"而提前引入。

## 15. Final verdict

```text
AG_BASELINE_0_COMPLETE
```

判定依据逐条核对：

```text
AndroidAgent 独立目录成立              YES
Git baseline commit 成立               YES
ag-baseline-0 tag 成立                 YES
:ag-core 是 pure Kotlin/JVM             YES  (kotlin("jvm")，非 Android plugin)
unit test 成功                          YES  (3 passed)
build 成功                             YES
AG Core dependency audit clean         YES  (全部红线 token = 0)
README / AGENTS / architecture doc      YES
step doc                               YES
```

无 open item。

## 16. Handoff to AG.0

### 允许

```text
在 :ag-core 内新增 AG Core v0 源码与 JVM 单元测试
引入 AG.0 依赖决策（coroutines、JSON/serialization 等），需在 AG.0 文档中说明理由
定义 Tool 接口与 Tool Registry 实现（Tool Registry 归属 AG Core）
定义与 GUI/TUI 无关的 event/result contract
```

### 禁止

```text
任何 android.* / androidx.* 引用（会直接编译失败）
任何 com.androidterminal.* 引用
任何 JNI / ADB / AT Core 依赖
在 :ag-core 内实现具体 Android Tool
创建 :app / :android-tools / :agc / :at-core（除非 AG.0 明确开启）
创建或同步 AndroidAgent-Public
修改 AndroidTerminal / AndroidTerminal-Public
```

### 判定标准

`AG.0` 的产出必须是**纯 JVM 可测**的。若某个设计导致 AG Core 无法在 JVM 上
单元测试，说明边界被破坏，应回到 tool 抽象层解决，而不是放宽 AG Core 的依赖红线。

### 版本起点

```text
branch : main
commit : baseline: initialize AndroidAgent project
tag    : ag-baseline-0
```