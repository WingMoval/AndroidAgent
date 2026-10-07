# AG.SHELL-1.PREFLIGHT — AT Engine Handoff Bundle v0 正式接收检查

> 类型：RECEIPT / PREFLIGHT AUDIT · BUILD COMPATIBILITY · DEPENDENCY BOUNDARY ·
> SECRET / ARTIFACT INTEGRITY。
> 本轮：**NO AG.SHELL 实现 · NO production code change · NO AT change**。
> 目标：在开始 AG.SHELL-1 前，从 AndroidAgent 真实构建环境 + 已冻结架构出发，确认
> `AT Engine Handoff Bundle v0` 可被安全、完整、无越界地接收。
> 产物：唯一 task-owned 文档（本文件）。**NO COMMIT / NO TAG / NO PUSH**。

---

## 1. 已知状态 / 前置

```text
AG Engine（ag-engine-0）= Agent Runtime + AG Core，已冻结
AT.ARTIFACT-HANDOFF-v0 = PASS（AT 侧）
AT_ENGINE_HANDOFF_BUNDLE_V0 = READY
CROSS_REPO_CONSUMPTION = VERIFIED（AT 侧已验）
bundle 位置：D:\codebank\AndCode\AndroidTerminal-V2\dist\at-engine-handoff-v0\
```

---

## 2. Git Preflight

### AndroidAgent

```text
branch  : main
HEAD    : 8b97eb2f9e666a2037b2b319c455e13453645500
ag-engine-0 : d9d9bf5c20cf8e6f1f7ffd9272ea411e11ba4c78（存在，未移动）
status  : CLEAN（仅上轮 Commit B 的消费审计文档已提交；无未知用户变更）
tracked production diff : NONE（git diff --quiet exit=0）
remote  : none
```

### AndroidTerminal-V2（READ ONLY）

```text
branch  : main
HEAD    : 726a441acd5d5a7e24c5015adce57e543d1897f7（未动）
status  : 非 clean —— 含 AT.RUNTIME-2A/2B/2D + AG0 + dist/ bundle（PRE-EXISTING 未提交工作）
at-core/src/main : 0 改动（AT Core 冻结边界保持）
uploda/ : 未触碰
本轮 AT 改动 : 0
```

---

## 3. Bundle Integrity Verification

自带 verifier 实测：

```text
powershell -ExecutionPolicy Bypass -File verify\verify-at-engine-handoff.ps1

PASS = 18   FAIL = 0   exit = 0
```

覆盖：文件存在性 / 两个 AAR 是有效 ZIP / at-runtime 只含 runtime 契约类(46)且无 Core 类
无 .so / at-core 含 Core 类 + 3 个 arm64-v8a native / 5 个 SHA256 全对 / JSON==SHA256SUMS
lockstep。

---

## 4. Metadata / Provenance

```text
bundleId       : at-engine-handoff-v0
bundleVersion  : 0.1.0
artifactKind   : versioned-paired-aar-bundle

runtimeModule  : com.androidterminal.runtime / compileSdk 34 / minSdk 26 / 无 native / 无 Core 类
coreModule     : com.androidterminal       / compileSdk 34 / minSdk 26 / ABI [arm64-v8a] / 含 native

sourceCommit   : 726a441acd5d5a7e24c5015adce57e543d1897f7
sourceDirty    : true（工作树含未提交 2A/2B/2D/AG0 源码）
sourceTreeSha256 : ff0a6432b9600f831b12456734a798fdc3802a08cee488bda4cb154be7f9ce3b
                 （162 个文件的权威内容指纹 —— HEAD alone ≠ source identity，已确认）
atCoreProductionDiff : empty（冻结 Core 0 改动）
builtWith      : JDK 21 / Gradle 8.7 / NDK 27.0.12077973 / CMake 3.22.1 / compile arm64-v8a
buildVariant   : debug
```

**确认**：bundle 的 `sourceTreeSha256` 是 dirty working tree 的内容指纹，未把 `726a441`
单独描述为完整 source identity。provenance 语义正确。

---

## 5. Pair Atomicity

```text
SHA256SUMS vs 实测 Get-FileHash（全 MATCH）：
  at-runtime.aar        1affba2e...27c22  MATCH
  at-core.aar           67015cfa...6d55e  MATCH
  libandroidterminaladb.so 6cc13843...cc18e4  MATCH
  libc++_shared.so      92ee8da6...ee839  MATCH
  libnetwork_probe.so   6cdcb09a...796c95  MATCH

metadata lockstep：at-engine-handoff.json 记录的 hash == SHA256SUMS（verifier 已查）
=> pair 不可拆：单 AAR 替换/hash mismatch = FAIL（verifier 强制执行）
```

---

## 6. Secret / Identity Leakage — CRITICAL

```text
强私钥标记扫描（-----BEGIN PRIVATE KEY----- / RSA / EC / OPENSSH / DSA、
ssh-rsa AAAA / ssh-ed25519 AAAA），全 bundle 所有文件（含二进制 .so）：
  命中 = 0  -> 无真实 private key material

文件名扫描（adbkey / at_fixed_identity / identity / Wing@ / DESKTOP-PG78M2N ...）：0
AAR ZIP 条目扫描：0
at-engine-handoff.json / README.md 中的 "adbkey"/"identity" 命中 = 说明性文档
  （"No private key committed; test-only authorized adbkey fixture injected at
    validation time and removed afterwards" —— 属允许的说明文字）
verify ps1 中 "adbkey" 命中 = AdbKeyStore 类名（防御性检查：验证 at-runtime.aar 不含 Core 类）
libandroidterminaladb.so 二进制中 "adbkey"/"PRIVATE KEY"/"identity" 命中 =
  ADB 协议字符串常量（key 文件名查找、BoringSSL PEM 错误串），非私钥字节

result: SECRET_LEAKAGE = NONE
禁止出现项（at_fixed_identity / adbkey fixture / Wing@DESKTOP... / PEM / 真实 key bytes）：
  全部 0 命中
```

---

## 7. Artifact Structure

```text
at-runtime.aar（73,033 B）:
  46 个 class，全部 com.androidterminal.runtime.*
  Core 类（com/androidterminal/ 非 runtime）= 0
  .so = 0

at-core.aar（1,277,257 B）:
  7 个 class（含 TerminalRuntime / AdbManager）
  jni/arm64-v8a/:
    libandroidterminaladb.so (2,156,320 B)
    libc++_shared.so        (1,292,896 B)
    libnetwork_probe.so     (11,200 B)

与 AT handoff SSOT 完全一致。
```

---

## 8. AG Build Compatibility（基于实测，非经验）

```text
                      AndroidAgent           AT bundle         兼容?
AGP                  8.5.2                  8.5.2              ✓（同版本）
Gradle               8.7                    8.7                ✓
Kotlin               android 2.1.0          android 1.9.24      ✓（Kotlin 2.x 消费 1.9 编译产物，向前兼容）
compileSdk           34                     34                 ✓
minSdk               26                     26                 ✓
targetSdk            34                     （lib 无关）         ✓
JVM target           17                     17                 ✓
AAR metadata         —                      minCompileSdk=1    ✓（AGP 8.5.2 读取无约束）
                                             minAGP=1.0.0
namespace            com.androidagent.app   com.androidterminal(.runtime)  无碰撞 ✓
native packaging     （当前无）              arm64-v8a + useLegacyPackaging  ✓（见 §9）
libc++_shared 重复   无                     （见 §10）
```

**AGP 8.5.2 与 AT 同版本、Gradle 同 8.7、SDK/JVM 全对齐。Kotlin 版本差（AG 2.1.0 消费 AT
1.9.24 产物）向后兼容，非问题。**

---

## 9. ABI Gate

```text
AT bundle ABI = arm64-v8a only（BoringSSL 静态库仅 arm64 构建）
AndroidAgent 当前：零 native（无 ABI 承诺）

判定：A. ACCEPTABLE FOR V0
  AG.SHELL-1 开发目标本来就是 arm64 Android 真机（AG0 验证机 HONOR ALI-AN00 = arm64-v8a）
记录：AT Engine 集成引入 arm64-v8a runtime requirement —— 与当前产品阶段兼容
实现注意（AG.SHELL-1）：:app 消费 tool module 后需声明 abiFilters arm64-v8a +
  useLegacyPackaging = true（与 AT consumers 一致，保证 System.loadLibrary 解析路径相同）
```

---

## 10. Duplicate Native Audit

```text
AG 当前 native 存量：0（app 构建输出无 .so，仓内无 vendor .aar）
AG 依赖：androidx core/appcompat/activity/lifecycle = 纯 Java/Kotlin，不含 libc++_shared
其他可能带 libc++_shared 的 native AAR：无

duplicate risk = NONE CURRENTLY
（AG.SHELL-1 引入时唯一 .so 来源 = at-core.aar；不做 pickFirst 绕过，无需去重）
```

---

## 11. Stable API Receipt（javap 实测交付 AAR）

```text
com.androidterminal.runtime.AtRuntime（object singleton）public 方法：
  initialize(android.content.Context)                  ✓
  readinessState(): AtReadinessState                    ✓
  connectAuto(java.lang.String): AtRuntimeDiagnostic    ✓
  execute(java.lang.String, int): AtExecutionResult     ✓（execute$default 提供 timeoutMs=0）
  disconnect(): AtRuntimeDiagnostic                     ✓
  connect(AtConnectionRequest)                          ✓（SUPPORTED 非 normal）

Runtime-owned 类型（at-runtime.aar classes.jar 内实测）：
  AtExecutionResult / AtExecutionOutcome / AtReadinessState / AtRuntimeDiagnostic /
  AtConnectionRequest / AtWirelessDebuggingState / AtTransportState / AtEndpoint /
  AtDiscoveredService   全部 PRESENT（46 类，0 Core）
```

## 12. Forbidden API / Dependency Receipt

```text
AG.SHELL-1 未来无需依赖（确认这些不存在于 at-runtime 契约层 / 或为 INTERNAL-TEST-ONLY）：
  TerminalRuntime / AdbManager / AdbKeyStore       -> 在 at-core（内部），runtime.aar 不含 ✓
  RecoveryCoordinator / EndpointStore / RecoveryPolicy / ConnectionIntent / DiscoveryBackend
    -> at-runtime 内部，public 但标记 INTERNAL / TEST-ONLY，AG 不依赖 ✓
  NsdManager / NsdServiceInfo / _adb-tls-connect / 动态端口 / 动态 endpoint
    -> 全部封装在 AtRuntime 内部（consumer 只供 keyDir）✓
  test-only API（*ForTest / recoverForTest / connectAutoNoEndpointForTest）
    -> public-in-Kotlin 但非 AG 契约，AG 不依赖 ✓

预期 NO CONTRACT GAP —— 确认成立。
```

---

## 13. AG Frozen Boundary Recheck

冻结文档全部存在：AGENTS.md / AG.ENGINE-BOUNDARY-FREEZE.md /
AG.AT-HANDOFF-CONSUMER-AUDIT.md / AG.SHELL-0_INTEGRATION_DESIGN.md。

```text
冻结规则（AGENTS.md L149-151, L35）：
  AG Core       -X-> AT implementation
  Agent Runtime -X-> AT implementation
  AGA           -X-> AT implementation
  Concrete Android Tool = 唯一允许连接 Android-specific execution backend 的层

本轮不重新审 Core/Runtime。边界规则作为 module placement 判定的硬约束。
```

---

## 14. Critical Module Placement —— DECISION

### 候选对比（基于 §13 冻结规则 + §15 gate）

**Option A — Tool 在 `:app`（AG.SHELL-0 D2 原决策）**

```text
:app build.gradle -> at-runtime.aar + at-core.aar
```

对冻结规则「AGA -X-> AT implementation」的冲突判定（不能含糊）：

```text
:app 同时含 AGA Host 代码（ChatActivity/ChatCompositionRoot）与 Tool。
AT AAR 依赖是 module 级事实 -> 整个 :app 物理依赖 AT。
冻结规则要求「Concrete Android Tool 是唯一允许连 AT 的层」；
若 Tool 在 :app，则 :app 模块无法在结构上区分「Host 代码」与「Tool」，
「AGA -X-> AT」只能靠代码审查约定维持，无法被模块边界验证。
=> 与冻结边界在结构层面冲突。NOT ACCEPTABLE。
```

**Option B — 真实 Integration module（选用）**

```text
:tool-android-shell（com.android.library）
  → :ag-core                     （Tool contract）
  → at-runtime.aar + at-core.aar （paired AAR，concrete implementation）
  → androidx.core（applicationContext/keyDir 取用）
:app
  → :tool-android-shell          （注册 Tool）
  → :ag-runtime / :ag-core / :provider-*（不变，无 AT 依赖）
```

```text
Host 代码  -X-> AT implementation     ✓（:app 无 AT AAR）
Concrete Tool module -> AT Engine     ✓（唯一 AT 依赖方，结构可验证）
AG Core / Agent Runtime -X-> AT       ✓（保持不变）
```

### Complexity Gate（§15，逐项）

```text
真实 consumer 存在？           YES —— AndroidShellToolImpl
真实 external dependency 存在？ YES —— AT Engine paired AAR（本 bundle）
独立 module 是否真正隔离被冻结的 Host/Engine boundary？
                               YES —— 不建则 :app 物理依赖 AT，边界不可结构验证
不建 module 是否导致 :app 承担 AT implementation dependency？
                               YES
=> NEW MODULE MAY NOW BE JUSTIFIED
```

与过去「DO NOT CREATE module before a real consumer exists」不冲突：当时（AG.SHELL-0
D2）consumer 是假设、AT 交付是假设（`:app → :at-core` 直连），gate 全 NO；
现在 consumer（AndroidShellToolImpl）与外部依赖（paired AAR）都是真实，gate 全 YES。

### 决策

```text
DECISION = Option B：新建 :tool-android-shell（concrete Android Shell Tool Integration）
本决策正式取代 AG.SHELL-0 D2（原「:app 内，com.androidagent.app.tool」）——
D2 基于旧前提「:app → :at-core 直连 + 无真实 consumer」，本轮按 §14/§15 重新判定。
D2 之外的其他 AG.SHELL-0 决策（D3/D4 生命周期、D5 endpoint、D7 schema、D9 seam）保持有效。
```

---

## 15. Do Not Over-Abstract

```text
即使新建 :tool-android-shell，也只是：
  Concrete Android Shell Tool Integration（唯一 backend = AT Engine）

禁止借机创建：AndroidExecutionBackend / ExecutionProvider / ShellBackend /
UniversalExecutor / Generic AT Adapter Framework。
不引入任何「多后端」抽象。
```

---

## 16. Lifecycle Dependency（§17，仅确认）

```text
Host 拥有 lifecycle policy（何时启动/连接/断开/展示 USER_ACTION_REQUIRED）
Concrete Integration 执行 AT implementation calls

分工（结合 §14 Option B）：
  :tool-android-shell 内：
    AndroidShellToolImpl         implements Tool（只调 AtRuntime.execute）
    ShellConnection（integration-owned lifecycle binding）
                                 封装 AtRuntime.initialize / connectAuto /
                                 readinessState / disconnect
  :app（Host/Composition）：
    ChatCompositionRoot 调用 ShellConnection（integration 自身 API）
    ChatActivity / CR 永不 import com.androidterminal.*
    -> 永不形成 ChatActivity → AtRuntime

精确边界：Host -X-> AT；Host → Integration API；Integration → AtRuntime。
```

---

## 17. ShellTool v0 Schema Receipt

```text
AG Tool contract 直接支持 {"command":"..."}：
  ToolSpec(name, description)              —— 声明
  ToolCall(id, name, arguments)            —— arguments = opaque string，模型传 {"command":"..."}
  ToolResult(success, output, error)       —— 结果
  ToolRegistry.register / dispatch         —— 注册 + 派发
全部存在（ag-core 冻结），无需 AG Core 改动。

AG Core change required = NO
```

---

## 18. Result Mapping Receipt

```text
与 AT bundle API 逐项核对，无变化：
  COMPLETED                     -> ToolResult.success = true
  COMPLETED + exitCode != 0     -> success = true
  TRANSPORT_ERROR               -> success = false
  PROTOCOL_ERROR                -> success = false
  UNSUPPORTED                   -> success = false
  TIMED_OUT / CANCELLED         -> v0 不可达（timeout=0 + 无 cancel），映射保留

automatic connection recovery = AT Runtime（内部）
automatic command replay      = NO（Runtime 只恢复连接，不重放命令）
bundle API 与此完全一致 -> 无 BLOCK
```

---

## 19. Temporary AG-shaped Consumer Build（REQUIRED，实测）

```text
位置：%TEMP%\opencode\ag-p1\consumer\
工具链（镜像 AndroidAgent 真实配置）：AGP 8.5.2 / Kotlin android 2.1.0 / Gradle 8.7 /
  compileSdk 34 / minSdk 26 / targetSdk 34 / jvmTarget 17 /
  packaging useLegacyPackaging=true / abiFilters arm64-v8a
依赖：仅 from bundle 拷贝 at-runtime.aar + at-core.aar（files()，无 AT source / 无 project()）
源码：ShellConsumer.kt 只 import com.androidterminal.runtime.*，调用
  initialize / readinessState / connectAuto(keyDir) / execute("id") / disconnect
构建：:app:assembleDebug = BUILD SUCCESSFUL（39s，exit 0）
```

## 20. Temporary APK Audit

```text
APK 大小      : 2,170 KB
native libs   : lib/arm64-v8a/libandroidterminaladb.so (2,105.8 KB) ✓
                lib/arm64-v8a/libc++_shared.so (1,262.6 KB)        ✓
                lib/arm64-v8a/libnetwork_probe.so (10.9 KB)        ✓
runtime 类    : dex 内含 AtRuntime(71 refs) / AtExecutionResult(49)  ✓
core 类       : dex 内含 TerminalRuntime(17) / AdbManager(30)       ✓
                （Core 类经 at-core.aar 传递打包，符合 §21 目标）
source imports AT Core : com.androidterminal.*（非 runtime）= 0    ✓

=> AG 自己的工具链 + paired AAR 即可完成外部 consumer 编译与打包，无需任何 AT source。
```

---

## 21. 本轮未重复真机 D1–D5

```text
AT 已完成：CROSS_REPO_CONSUMPTION = VERIFIED，D1–D5 = PASS。
本轮 temp consumer 为构建+静态审计（§22/§20 目标），与 AT external consumer 无运行差异
  -> 不重复整机测试。真机 runtime smoke 保留为 AG.SHELL-1 的验收项（AT.ENGINE-AG0
  validationStatus.externalStandaloneConsumer = "build + static audit + APK audit = PASS，
  device acceptance = 剩余门禁"）。
```

---

## 22. Files Changed

```text
新增  docs/audit/AG.SHELL-1.PREFLIGHT.md（本文件，task-owned，未提交，等 Controller Review）
production code = 0 · Gradle changes = 0 · AGENTS.md = 0 · AT repo = 0
NO COMMIT / NO TAG / NO PUSH
```

---

## 23. Blockers

```text
NO_BLOCKER 覆盖全部 §3–§21：
  bundle 完整性 PASS · 无 secret 泄漏 · pair atomicity 成立 ·
  AG build 兼容 PASS · ABI 兼容（arm64 v0 目标）· native 无重复 ·
  Stable API 全 present · 无 contract gap · 冻结边界保持 · module placement 已定
```

---

## 24. Final Verdict

```text
AG_SHELL_1_PREFLIGHT_PASS

AT_ENGINE_BUNDLE
= ACCEPTED

BUNDLE_INTEGRITY
= PASS

SECRET_LEAKAGE
= NONE

AG_BUILD_COMPATIBILITY
= PASS

AG_ARCHITECTURE_BOUNDARY
= PRESERVED

MODULE_PLACEMENT
= DECIDED（Option B :tool-android-shell，取代 D2）

READY_FOR_AG_SHELL_1
= YES
```

```text
NEXT = AG.SHELL-1（实现时：
  - 新建 :tool-android-shell（Android library）：→ :ag-core + paired AT AAR
  - AndroidShellToolImpl（implements Tool，只调 AtRuntime.execute）
  - ShellConnection lifecycle binding（integration 内部封装 initialize/connectAuto/disconnect）
  - :app 组合根注册 Tool 并调用 ShellConnection；:app 不依赖 AT AAR
  - :app 声明 abiFilters arm64-v8a + useLegacyPackaging=true）
不得：复制 bundle 进 production tree / 直接改 :app build.gradle 加 AT AAR / 实现后绕过 Integration
```