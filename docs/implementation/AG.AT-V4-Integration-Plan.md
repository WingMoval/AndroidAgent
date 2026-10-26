# AG.AT-V4-INTEGRATION-1 — Implementation Plan (PENDING Controller Approval)

> **状态：待 Controller 审批。本文件不代表已授权开始实施。**
> 前置审计：`docs/audit/AG.AT-V4-Handoff-Audit.md`
> 任务类型：CONSUMER MIGRATION（AT V3 → V4）
> 原则：不重新设计 AG Core / Runtime / Tool；只迁移 AT 消费方式

---

## 1. Goal

将 AG 的 AT 消费方式从 V3（直接依赖 backend / ADB 内部类型 + vendored V3 AARs）迁移到 V4 Consumer Boundary（只依赖 `:at-engine-api` + `:at-composition-android` + `:at-cli`），使用 AT V4 冻结 bundle（`AT.V4-CONSUMER-FREEZE-V1`）。

**不改变：** AG Core / Agent Runtime / Tool contract / Tool 注册方式 / Session / Run 架构。

## 2. Baseline

```
AG HEAD = a6eea61dc209898a01cd0e7f2e82304fff7ed9d4
AG worktree = CLEAN
Session Continuity = c4a58fe + 4d8aec7（已提交，不触碰）

AT V4 FREEZE_TAG = AT.V4-CONSUMER-FREEZE-V1
AT V4 TAG_TARGET = 7216d0e8131e92e8914ce7c8443e1668b1d53aca
AT V4 BUNDLE = dist/AT.V4-CONSUMER-FREEZE-V1/（8 artifacts）
```

## 3. Pre-conditions（Controller 决策项）

**P1: DEBUG vs RELEASE**
V4 bundle 是 debug 制品（`*-debug.aar`）。AG 当前用 release V3 AARs。
- 选项 A：AG 接受 debug 制品（开发/验证阶段）
- 选项 B：AT 侧生成 release bundle（需 AT 侧工作）
- 选项 C：AG 从 AT V4 source 构建 release AARs（需 AT source 访问）

**P2: Artifact Resolution 方式**
V4 bundle 无 POM 文件。
- 选项 A：AG 生成 m2 wrapper（POM + AAR，类似 V3 的 `third_party/at-consumer-freeze-v1/m2`）
- 选项 B：AG 用 Gradle file 依赖（`implementation(files("...aar"))`）
- 选项 C：AT 侧做 Maven publication（AT.ARTIFACT-BUNDLE-0，DEFERRED）

**P3: V3 旧制品清理时机**
`third_party/at-engine-handoff-v0/`（V2，inactive）和 `third_party/at-consumer-freeze-v1/`（V3，当前活跃）的清理时机：
- 选项 A：迁移完成后立即清理
- 选项 B：保留作为迁移证据，后续清理

## 4. Steps

### Step 1: Baseline Freeze
- **输入：** 当前 AG V2 HEAD
- **允许修改范围：** 无
- **操作：** 记录 HEAD / worktree 状态 / Session Continuity 测试基线
- **验收条件：** HEAD = a6eea61；worktree CLEAN；`./gradlew :ag-core:test :ag-runtime:test :tool-android-shell:test` 绿
- **失败停止：** HEAD 不符 / worktree dirty / 测试红 → STOP

### Step 2: Artifact Resolution
- **输入：** AT V4 bundle（`dist/AT.V4-CONSUMER-FREEZE-V1/`）
- **允许修改范围：** `third_party/`（新增 V4 m2 wrapper）+ `settings.gradle.kts`（m2 repository URL）
- **操作：**
  1. 复制 V4 bundle 到 `third_party/at-v4-consumer-freeze-v1/`
  2. 生成 m2 wrapper（POM + AAR，坐标待定 — 建议 `com.at.engine.api:at-engine-api:4.0.0` 等）
  3. 更新 `settings.gradle.kts` 的 m2 repository URL
- **验收条件：** AG build 能解析 V4 artifacts；`./gradlew :integration-at:assembleDebug` 编译通过（即使 source 未迁移，artifact 解析成功）
- **失败停止：** artifact 解析失败 → 检查 POM / AAR 完整性

### Step 3: Adapter Migration
- **输入：** `:integration-at` 当前 source + V4 public API
- **允许修改范围：** `integration-at/src/main/java/.../AtExecutionAdapter.kt` + `integration-at/build.gradle.kts`
- **操作：**
  1. build.gradle.kts：删除 5 个 V3 AAR 依赖 → 改为 `api("...:at-engine-api:4.0.0")` + `api("...:at-composition-android:4.0.0")` + `api("...:at-cli:4.0.0")`
  2. AtExecutionAdapter.kt：
     - 删除 `import com.adb.engine.*`（7 个）
     - 删除 `import com.at.backend.*`（4 个）
     - 删除 `import com.at.engine.ATPersistentBackend`（1 个）
     - 新增 `import com.at.composition.android.*`（ATAndroidComposition / ATAndroidAdbControl / 语义类型）
     - 构造方式：从 `ATEngine(oneShot=..., persistent=...)` 改为接收 `ATAndroidComposition`（由 Host 注入）
     - ADB lifecycle：从直接调 `AdbRuntime` 改为委托 `composition.adbControl`
     - bootstrap：从直接构造 `AdbConsumerBootstrap` 改为委托 `composition.adbControl`
     - session launch：从 `ATPersistentBackend.Launch(...)` 改为 `ATSessionLaunch(...)`
- **验收条件：**
  - adapter 只 import `com.at.engine.*` + `com.at.composition.android.*` + `com.at.cli.*`
  - 零 `com.adb.engine.*` / `com.at.backend.*` import
  - 不直接构造 `ATEngine`
  - 不直接调 `AdbRuntime` / `AdbConsumerBootstrap`
  - `./gradlew :integration-at:assembleDebug` 编译通过
- **失败停止：** 编译失败 → 检查 V4 API 映射（参考审计 §6.2 API Mapping Matrix）

### Step 4: Host Composition
- **输入：** `ChatCompositionRoot` + `ShellConnection` + V4 `ATAndroid.create()`
- **允许修改范围：** `app/src/main/java/.../ChatCompositionRoot.kt` + `ShellConnection.kt`
- **操作：**
  1. ChatCompositionRoot：从 `ShellConnection(applicationContext)` 改为：
     ```kotlin
     val atComposition = ATAndroid.create(applicationContext, ATAndroidConfig(identityProfile = "at-consumer/identity"))
     val adapter = AtExecutionAdapter(atComposition)
     ```
  2. ShellConnection：从内部创建 `AtExecutionAdapter(context, identityDirName)` 改为接收 `AtExecutionAdapter`（由 Host 注入）
  3. adapter 的 `initializeAdb()` 改为 no-op（`ATAndroid.create()` 内部已初始化）
- **验收条件：**
  - Host 调用 `ATAndroid.create(context, config)` 获得 `ATAndroidComposition`
  - adapter 不自行构造 ATEngine
  - `./gradlew :app:assembleDebug` 编译通过
- **失败停止：** composition 创建失败 → 检查 identity / Context

### Step 5: Shell Tool Integration
- **输入：** `:tool-android-shell` 当前 source
- **允许修改范围：** 无（tool 已正确通过 adapter 消费）
- **操作：** 验证 `AndroidShellToolImpl` 编译通过；`ShellResultMapper` 映射正确
- **验收条件：** `./gradlew :tool-android-shell:assembleDebug :tool-android-shell:test` 绿
- **失败停止：** 编译/测试失败 → 检查 ATExecutionResult 类型兼容性

### Step 6: Dependency Closure
- **输入：** 迁移后的 build graph
- **允许修改范围：** 无（只验证）
- **操作：**
  1. 验证 `:ag-core` / `:ag-runtime` / `:tool-android-shell` 零 AT 内部 import
  2. 验证 `:integration-at` 只依赖 `:at-engine-api` + `:at-composition-android` + `:at-cli`
  3. 验证 `:app` 不直接 import AT 内部类型
- **验收条件：** 零 forbidden import；零 forbidden direct dependency
- **失败停止：** 发现违规 → 回退 Step 3/4

### Step 7: Build and Tests
- **输入：** 迁移后的完整 build
- **允许修改范围：** 无（只构建 + 测试）
- **操作：** `./gradlew :ag-core:test :ag-runtime:test :tool-android-shell:test :tool-network:test :tool-file:test :app:assembleDebug`
- **验收条件：** 全部绿
- **失败停止：** 测试失败 → 定位回归

### Step 8: Device Validation
- **输入：** 构建好的 AG APK + V4 artifacts
- **允许修改范围：** 无（只设备测试）
- **操作：**
  1. 安装 AG APK（`adb install`）
  2. 首次使用：AG consumer 自己的 identity（`at-consumer/identity`）为空 → PAIRING_REQUIRED
  3. 用户配对（`adb pair <host>:<port> <code>`）— AG 自己的 identity
  4. 执行 `adb shell id` → 验证 uid=2000(shell)
- **验收条件：**
  - AG consumer 用自己的 identity 执行成功
  - 不复制 AT Product identity
  - uid=2000(shell)
- **失败停止：** 设备测试失败 → 检查 identity / ADB 连接

### Step 9: Documentation Closure
- **输入：** 迁移后的状态
- **允许修改范围：** `docs/` / `AGENTS.md` / `settings.gradle.kts` 注释 / `third_party/`（清理旧制品）
- **操作：**
  1. 更新 `AGENTS.md`：AT 版本 V3 → V4；`third_party/at-consumer-freeze-v1` → V4
  2. 更新 `docs/integration/AT_TO_AG_HANDOFF_CURRENT.md`
  3. 更新 `:tool-android-shell/build.gradle.kts` header 注释（删除过期 includeBuild 注释）
  4. 更新 `settings.gradle.kts` 注释
  5. 清理 `third_party/at-engine-handoff-v0/`（V2，inactive）— 如 P3 选项 A
- **验收条件：** 文档一致；无过期 V3 指引
- **失败停止：** 文档不一致 → 修正

## 5. 禁止事项

```
禁止：修改 AG Core / Agent Runtime / Tool contract
禁止：重新设计 AG 架构
禁止：新增 Terminal Emulator / ScreenState / PTY 功能
禁止：复制 AT Product identity
禁止：执行真实配对（除非 Step 8 设备验证需要）
禁止：提交 Git / 创建标签 / push（除非 Controller 授权）
禁止：删除 V3 旧制品（除非 P3 选项 A 且迁移完成）
```

## 6. 风险

| 风险 | 影响 | 缓解 |
|------|------|------|
| V4 debug vs release | AG build 可能不兼容 debug AARs | P1 Controller 决策 |
| V4 无 POM | AG 需生成 m2 wrapper | P2 Controller 决策 |
| V4 API 类型名变更 | adapter 编译失败 | 参考审计 §6.2 API Mapping Matrix |
| identity 迁移 | AG identity 从 V3 到 V4 可能不兼容 | Step 8 设备验证 |

## 7. 验收标准（最终）

```
AG_CORE_BOUNDARY = PASS（零 AT 内部 import）
AG_RUNTIME_BOUNDARY = PASS（零 AT 内部 import）
AT_ADAPTER_BOUNDARY = PASS（只依赖 :at-engine-api + :at-composition-android + :at-cli）
AGA_COMPOSITION_BOUNDARY = PASS（ATAndroid.create() 注入 adapter）
SHELL_TOOL_BOUNDARY = PASS（通过 adapter 消费）
IDENTITY_OWNERSHIP = PASS（AG 自己的 app-private identity）
BUILD = PASS（全部模块编译 + 测试绿）
DEVICE_VALIDATION = PASS（AG consumer 执行成功，uid=2000）
DOCUMENTATION = PASS（无过期 V3 指引）
```

---

**本计划待 Controller 审批。审批前不开始任何实施步骤。**
